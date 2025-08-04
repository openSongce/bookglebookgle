"""
Redis Memory Manager for BGBG AI Server
Monitors and manages Redis memory usage for chat history feature
"""

import asyncio
import logging
from datetime import datetime, timedelta
from typing import Dict, List, Optional, Any, Tuple
from dataclasses import dataclass
from enum import Enum

import redis.asyncio as redis
from src.config.settings import get_settings
from src.config.chat_config import get_performance_config, get_ttl_config

logger = logging.getLogger(__name__)


class MemoryStatus(str, Enum):
    """Redis memory status levels"""
    HEALTHY = "healthy"      # < 70% usage
    WARNING = "warning"      # 70-85% usage
    CRITICAL = "critical"    # 85-95% usage
    EMERGENCY = "emergency"  # > 95% usage


@dataclass
class MemoryStats:
    """Redis memory statistics"""
    used_memory: int
    max_memory: int
    used_memory_percentage: float
    memory_status: MemoryStatus
    active_sessions: int
    total_keys: int
    expired_keys_cleaned: int
    last_cleanup_time: datetime


@dataclass
class SessionInfo:
    """Session information for cleanup decisions"""
    session_id: str
    last_activity: datetime
    message_count: int
    memory_usage_bytes: int
    ttl_remaining: int


class RedisMemoryManager:
    """
    Redis memory management for chat history system
    Monitors usage, performs cleanup, and maintains performance
    """
    
    def __init__(self, redis_client: Optional[redis.Redis] = None):
        """
        Initialize Redis memory manager
        
        Args:
            redis_client: Optional Redis client (will create if not provided)
        """
        self.settings = get_settings()
        self.performance_config = get_performance_config()
        self.ttl_config = get_ttl_config()
        
        self._redis = redis_client
        self._monitoring_task = None
        self._cleanup_task = None
        
        # Memory thresholds
        self.memory_thresholds = {
            MemoryStatus.HEALTHY: 0.70,
            MemoryStatus.WARNING: 0.85,
            MemoryStatus.CRITICAL: 0.95,
            MemoryStatus.EMERGENCY: 1.0
        }
        
        # Cleanup statistics
        self._last_cleanup = datetime.utcnow()
        self._cleanup_stats = {
            "sessions_cleaned": 0,
            "keys_expired": 0,
            "memory_freed_bytes": 0
        }
        
        logger.info("RedisMemoryManager initialized")
    
    async def start(self) -> None:
        """Start memory monitoring and cleanup tasks"""
        try:
            # Initialize Redis connection if not provided
            if not self._redis:
                self._redis = redis.Redis(
                    host=self.settings.database.REDIS_HOST,
                    port=self.settings.database.REDIS_PORT,
                    password=self.settings.database.REDIS_PASSWORD,
                    decode_responses=True,
                    socket_connect_timeout=5,
                    socket_keepalive=True,
                    socket_keepalive_options={}
                )
            
            # Test connection
            await self._redis.ping()
            logger.info("✅ Redis connection established for memory management")
            
            # Start monitoring task
            monitor_interval = self.performance_config.get('memory_check_interval_seconds', 60)
            self._monitoring_task = asyncio.create_task(
                self._memory_monitoring_loop(monitor_interval)
            )
            
            # Start cleanup task
            cleanup_interval = self.performance_config.get('cleanup_interval_seconds', 300)
            self._cleanup_task = asyncio.create_task(
                self._cleanup_loop(cleanup_interval)
            )
            
            logger.info("RedisMemoryManager started with monitoring and cleanup tasks")
            
        except Exception as e:
            logger.error(f"Failed to start RedisMemoryManager: {e}")
            raise
    
    async def stop(self) -> None:
        """Stop memory management tasks and close connections"""
        try:
            # Cancel tasks
            if self._monitoring_task:
                self._monitoring_task.cancel()
                try:
                    await self._monitoring_task
                except asyncio.CancelledError:
                    pass
            
            if self._cleanup_task:
                self._cleanup_task.cancel()
                try:
                    await self._cleanup_task
                except asyncio.CancelledError:
                    pass
            
            # Close Redis connection
            if self._redis:
                await self._redis.close()
            
            logger.info("RedisMemoryManager stopped")
            
        except Exception as e:
            logger.error(f"Error stopping RedisMemoryManager: {e}")
    
    async def get_memory_stats(self) -> MemoryStats:
        """
        Get current Redis memory statistics
        
        Returns:
            MemoryStats: Current memory statistics
        """
        try:
            # Get Redis memory info
            info = await self._redis.info('memory')
            
            used_memory = info.get('used_memory', 0)
            max_memory = info.get('maxmemory', 0)
            
            # Calculate usage percentage
            if max_memory > 0:
                usage_percentage = used_memory / max_memory
            else:
                # If no max memory set, use system memory as reference
                usage_percentage = 0.0
            
            # Determine memory status
            memory_status = self._determine_memory_status(usage_percentage)
            
            # Get session and key counts
            active_sessions = await self._count_active_sessions()
            total_keys = await self._redis.dbsize()
            
            return MemoryStats(
                used_memory=used_memory,
                max_memory=max_memory,
                used_memory_percentage=usage_percentage,
                memory_status=memory_status,
                active_sessions=active_sessions,
                total_keys=total_keys,
                expired_keys_cleaned=self._cleanup_stats["keys_expired"],
                last_cleanup_time=self._last_cleanup
            )
            
        except Exception as e:
            logger.error(f"Failed to get memory stats: {e}")
            return MemoryStats(
                used_memory=0,
                max_memory=0,
                used_memory_percentage=0.0,
                memory_status=MemoryStatus.HEALTHY,
                active_sessions=0,
                total_keys=0,
                expired_keys_cleaned=0,
                last_cleanup_time=self._last_cleanup
            )
    
    async def cleanup_inactive_sessions(self, force: bool = False) -> int:
        """
        Clean up inactive sessions based on activity and TTL
        
        Args:
            force: Whether to force cleanup regardless of memory status
            
        Returns:
            int: Number of sessions cleaned up
        """
        try:
            logger.info("🧹 Starting inactive session cleanup")
            
            # Get current memory status
            memory_stats = await self.get_memory_stats()
            
            # Determine cleanup aggressiveness based on memory status
            if not force:
                if memory_stats.memory_status == MemoryStatus.HEALTHY:
                    # Only clean up very old sessions
                    max_age_hours = self.ttl_config.get('session_ttl_hours', 2) * 2
                elif memory_stats.memory_status == MemoryStatus.WARNING:
                    # Clean up moderately old sessions
                    max_age_hours = self.ttl_config.get('session_ttl_hours', 2)
                else:
                    # Aggressive cleanup for critical/emergency status
                    max_age_hours = self.ttl_config.get('session_ttl_hours', 2) // 2
            else:
                # Force cleanup - very aggressive
                max_age_hours = 1
            
            # Find inactive sessions
            inactive_sessions = await self._find_inactive_sessions(max_age_hours)
            
            # Clean up sessions
            cleaned_count = 0
            memory_freed = 0
            
            for session_info in inactive_sessions:
                try:
                    # Calculate memory usage before cleanup
                    memory_before = await self._estimate_session_memory(session_info.session_id)
                    
                    # Clean up session
                    await self._cleanup_session(session_info.session_id)
                    
                    cleaned_count += 1
                    memory_freed += memory_before
                    
                    logger.debug(f"Cleaned up session {session_info.session_id}")
                    
                except Exception as e:
                    logger.warning(f"Failed to cleanup session {session_info.session_id}: {e}")
            
            # Update cleanup statistics
            self._cleanup_stats["sessions_cleaned"] += cleaned_count
            self._cleanup_stats["memory_freed_bytes"] += memory_freed
            self._last_cleanup = datetime.utcnow()
            
            if cleaned_count > 0:
                logger.info(f"✅ Cleaned up {cleaned_count} inactive sessions, freed ~{memory_freed} bytes")
            else:
                logger.debug("No inactive sessions found for cleanup")
            
            return cleaned_count
            
        except Exception as e:
            logger.error(f"Session cleanup failed: {e}")
            return 0
    
    async def health_check(self) -> Dict[str, Any]:
        """
        Perform health check on Redis memory system
        
        Returns:
            Dict[str, Any]: Health check results
        """
        try:
            # Test Redis connection
            await self._redis.ping()
            connection_healthy = True
        except Exception as e:
            connection_healthy = False
            logger.error(f"Redis connection health check failed: {e}")
        
        # Get memory statistics
        memory_stats = await self.get_memory_stats()
        
        # Check task status
        monitoring_running = self._monitoring_task and not self._monitoring_task.done()
        cleanup_running = self._cleanup_task and not self._cleanup_task.done()
        
        return {
            "redis_connection_healthy": connection_healthy,
            "memory_status": memory_stats.memory_status.value,
            "memory_usage_percentage": memory_stats.used_memory_percentage,
            "active_sessions": memory_stats.active_sessions,
            "total_keys": memory_stats.total_keys,
            "monitoring_task_running": monitoring_running,
            "cleanup_task_running": cleanup_running,
            "last_cleanup": self._last_cleanup.isoformat(),
            "cleanup_stats": self._cleanup_stats.copy()
        }
    
    async def force_cleanup(self) -> Dict[str, Any]:
        """
        Force immediate cleanup of all expired and inactive sessions
        
        Returns:
            Dict[str, Any]: Cleanup results
        """
        try:
            logger.info("🚨 Force cleanup initiated")
            
            # Get memory stats before cleanup
            stats_before = await self.get_memory_stats()
            
            # Force cleanup inactive sessions
            sessions_cleaned = await self.cleanup_inactive_sessions(force=True)
            
            # Force expire keys with TTL
            expired_keys = await self._force_expire_keys()
            
            # Get memory stats after cleanup
            stats_after = await self.get_memory_stats()
            
            memory_freed = stats_before.used_memory - stats_after.used_memory
            
            result = {
                "success": True,
                "sessions_cleaned": sessions_cleaned,
                "keys_expired": expired_keys,
                "memory_freed_bytes": memory_freed,
                "memory_before": stats_before.used_memory,
                "memory_after": stats_after.used_memory,
                "memory_status_before": stats_before.memory_status.value,
                "memory_status_after": stats_after.memory_status.value
            }
            
            logger.info(f"✅ Force cleanup completed: {result}")
            return result
            
        except Exception as e:
            logger.error(f"Force cleanup failed: {e}")
            return {
                "success": False,
                "error": str(e),
                "sessions_cleaned": 0,
                "keys_expired": 0,
                "memory_freed_bytes": 0
            }
    
    # Private methods
    
    async def _memory_monitoring_loop(self, interval_seconds: int) -> None:
        """Memory monitoring background task"""
        while True:
            try:
                await asyncio.sleep(interval_seconds)
                
                # Get current memory stats
                stats = await self.get_memory_stats()
                
                # Log memory status
                if stats.memory_status != MemoryStatus.HEALTHY:
                    logger.warning(
                        f"Redis memory status: {stats.memory_status.value} "
                        f"({stats.used_memory_percentage:.1%} used)"
                    )
                
                # Trigger cleanup if memory is critical
                if stats.memory_status in [MemoryStatus.CRITICAL, MemoryStatus.EMERGENCY]:
                    logger.warning("🚨 Critical memory usage detected, triggering cleanup")
                    await self.cleanup_inactive_sessions(force=True)
                
            except asyncio.CancelledError:
                logger.info("Memory monitoring task cancelled")
                break
            except Exception as e:
                logger.error(f"Error in memory monitoring loop: {e}")
    
    async def _cleanup_loop(self, interval_seconds: int) -> None:
        """Cleanup background task"""
        while True:
            try:
                await asyncio.sleep(interval_seconds)
                
                # Perform regular cleanup
                await self.cleanup_inactive_sessions()
                
            except asyncio.CancelledError:
                logger.info("Cleanup task cancelled")
                break
            except Exception as e:
                logger.error(f"Error in cleanup loop: {e}")
    
    def _determine_memory_status(self, usage_percentage: float) -> MemoryStatus:
        """Determine memory status based on usage percentage"""
        if usage_percentage >= self.memory_thresholds[MemoryStatus.EMERGENCY]:
            return MemoryStatus.EMERGENCY
        elif usage_percentage >= self.memory_thresholds[MemoryStatus.CRITICAL]:
            return MemoryStatus.CRITICAL
        elif usage_percentage >= self.memory_thresholds[MemoryStatus.WARNING]:
            return MemoryStatus.WARNING
        else:
            return MemoryStatus.HEALTHY
    
    async def _count_active_sessions(self) -> int:
        """Count active chat sessions"""
        try:
            # Count keys matching chat session pattern
            pattern = "chat_session:*"
            keys = await self._redis.keys(pattern)
            return len(keys)
        except Exception as e:
            logger.error(f"Failed to count active sessions: {e}")
            return 0
    
    async def _find_inactive_sessions(self, max_age_hours: int) -> List[SessionInfo]:
        """Find inactive sessions for cleanup"""
        try:
            inactive_sessions = []
            cutoff_time = datetime.utcnow() - timedelta(hours=max_age_hours)
            
            # Get all session keys
            pattern = "chat_session:*"
            session_keys = await self._redis.keys(pattern)
            
            for key in session_keys:
                try:
                    # Extract session ID
                    session_id = key.split(":", 1)[1]
                    
                    # Get session info
                    session_data = await self._redis.hgetall(key)
                    if not session_data:
                        continue
                    
                    # Check last activity
                    last_activity_str = session_data.get("last_activity")
                    if last_activity_str:
                        last_activity = datetime.fromisoformat(last_activity_str)
                        if last_activity < cutoff_time:
                            # Get additional info
                            message_count = int(session_data.get("message_count", 0))
                            ttl = await self._redis.ttl(key)
                            memory_usage = await self._estimate_session_memory(session_id)
                            
                            inactive_sessions.append(SessionInfo(
                                session_id=session_id,
                                last_activity=last_activity,
                                message_count=message_count,
                                memory_usage_bytes=memory_usage,
                                ttl_remaining=ttl
                            ))
                
                except Exception as e:
                    logger.warning(f"Error processing session key {key}: {e}")
            
            # Sort by last activity (oldest first)
            inactive_sessions.sort(key=lambda x: x.last_activity)
            
            return inactive_sessions
            
        except Exception as e:
            logger.error(f"Failed to find inactive sessions: {e}")
            return []
    
    async def _cleanup_session(self, session_id: str) -> None:
        """Clean up a specific session"""
        try:
            # Delete all keys related to this session
            patterns = [
                f"chat_session:{session_id}",
                f"chat_messages:{session_id}",
                f"chat_context:{session_id}",
                f"participant_state:{session_id}:*"
            ]
            
            for pattern in patterns:
                keys = await self._redis.keys(pattern)
                if keys:
                    await self._redis.delete(*keys)
            
        except Exception as e:
            logger.error(f"Failed to cleanup session {session_id}: {e}")
            raise
    
    async def _estimate_session_memory(self, session_id: str) -> int:
        """Estimate memory usage of a session"""
        try:
            total_memory = 0
            
            # Get memory usage of session-related keys
            patterns = [
                f"chat_session:{session_id}",
                f"chat_messages:{session_id}",
                f"chat_context:{session_id}",
                f"participant_state:{session_id}:*"
            ]
            
            for pattern in patterns:
                keys = await self._redis.keys(pattern)
                for key in keys:
                    try:
                        # Use MEMORY USAGE command if available
                        memory = await self._redis.memory_usage(key)
                        if memory:
                            total_memory += memory
                    except Exception:
                        # Fallback: estimate based on key length and type
                        key_type = await self._redis.type(key)
                        if key_type == "string":
                            value = await self._redis.get(key)
                            total_memory += len(str(value)) if value else 0
                        elif key_type == "hash":
                            hash_data = await self._redis.hgetall(key)
                            total_memory += sum(len(str(k)) + len(str(v)) for k, v in hash_data.items())
                        elif key_type == "list":
                            list_len = await self._redis.llen(key)
                            total_memory += list_len * 50  # Rough estimate
            
            return total_memory
            
        except Exception as e:
            logger.warning(f"Failed to estimate memory for session {session_id}: {e}")
            return 0
    
    async def _force_expire_keys(self) -> int:
        """Force expire keys that have TTL"""
        try:
            expired_count = 0
            
            # Get all keys with TTL
            all_keys = await self._redis.keys("*")
            
            for key in all_keys:
                try:
                    ttl = await self._redis.ttl(key)
                    if 0 < ttl <= 60:  # Keys expiring within 1 minute
                        await self._redis.delete(key)
                        expired_count += 1
                except Exception:
                    continue
            
            self._cleanup_stats["keys_expired"] += expired_count
            return expired_count
            
        except Exception as e:
            logger.error(f"Failed to force expire keys: {e}")
            return 0