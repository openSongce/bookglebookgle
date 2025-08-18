"""
Redis Connection Manager for BGBG AI Server
Handles Redis connection pooling, health checks, and failover
"""

import asyncio
import logging
from datetime import datetime, timedelta
from typing import Dict, List, Optional, Any
from dataclasses import dataclass
from enum import Enum
import time

import redis.asyncio as redis
from redis.asyncio.connection import ConnectionPool
from redis.exceptions import ConnectionError, TimeoutError, RedisError

from src.config.settings import get_settings
from src.config.chat_config import get_redis_config

logger = logging.getLogger(__name__)


class ConnectionStatus(str, Enum):
    """Redis connection status"""
    HEALTHY = "healthy"
    DEGRADED = "degraded"
    UNHEALTHY = "unhealthy"
    DISCONNECTED = "disconnected"


@dataclass
class ConnectionStats:
    """Redis connection statistics"""
    total_connections: int
    active_connections: int
    idle_connections: int
    failed_connections: int
    connection_errors: int
    last_health_check: datetime
    status: ConnectionStatus
    response_time_ms: float


class RedisConnectionManager:
    """
    Advanced Redis connection manager with pooling and health monitoring
    """
    
    def __init__(self):
        """Initialize Redis connection manager"""
        self.settings = get_settings()
        self.redis_config = get_redis_config()
        
        # Connection pools
        self._primary_pool: Optional[ConnectionPool] = None
        self._redis_client: Optional[redis.Redis] = None
        
        # Health monitoring
        self._health_check_task: Optional[asyncio.Task] = None
        self._connection_stats = ConnectionStats(
            total_connections=0,
            active_connections=0,
            idle_connections=0,
            failed_connections=0,
            connection_errors=0,
            last_health_check=datetime.utcnow(),
            status=ConnectionStatus.DISCONNECTED,
            response_time_ms=0.0
        )
        
        # Retry configuration
        self.max_retries = 3
        self.retry_delay = 1.0
        self.health_check_interval = 30  # seconds
        
        logger.info("RedisConnectionManager initialized")
    
    async def initialize(self) -> bool:
        """
        Initialize Redis connection pool
        
        Returns:
            bool: True if initialization successful
        """
        try:
            logger.info("🔗 Initializing Redis connection pool...")
            
            # Create connection pool
            self._primary_pool = ConnectionPool(
                host=self.redis_config['host'],
                port=self.redis_config['port'],
                db=self.redis_config.get('db', 0),
                password=self.redis_config.get('password'),
                max_connections=self.redis_config.get('max_connections', 20),
                socket_timeout=self.redis_config.get('socket_timeout', 5),
                socket_connect_timeout=self.redis_config.get('socket_connect_timeout', 5),
                decode_responses=self.redis_config.get('decode_responses', True),
                retry_on_timeout=self.redis_config.get('retry_on_timeout', True),
                health_check_interval=30
            )
            
            # Create Redis client
            self._redis_client = redis.Redis(connection_pool=self._primary_pool)
            
            # Test connection
            await self._test_connection()
            
            # Start health monitoring
            self._health_check_task = asyncio.create_task(self._health_check_loop())
            
            logger.info("✅ Redis connection pool initialized successfully")
            return True
            
        except Exception as e:
            logger.error(f"❌ Failed to initialize Redis connection: {e}")
            return False
    
    async def cleanup(self) -> None:
        """Cleanup Redis connection manager (alias for shutdown)"""
        await self.shutdown()
    
    async def shutdown(self) -> None:
        """Shutdown Redis connection manager"""
        try:
            logger.info("🔌 Shutting down Redis connection manager...")
            
            # Cancel health check task
            if self._health_check_task:
                self._health_check_task.cancel()
                try:
                    await self._health_check_task
                except asyncio.CancelledError:
                    pass
            
            # Close Redis client
            if self._redis_client:
                await self._redis_client.close()
            
            # Close connection pool
            if self._primary_pool:
                await self._primary_pool.disconnect()
            
            self._connection_stats.status = ConnectionStatus.DISCONNECTED
            logger.info("✅ Redis connection manager shut down")
            
        except Exception as e:
            logger.error(f"Error during Redis shutdown: {e}")
    
    async def get_client(self) -> Optional[redis.Redis]:
        """
        Get Redis client with automatic reconnection
        
        Returns:
            Optional[redis.Redis]: Redis client or None if unavailable
        """
        if not self._redis_client:
            logger.warning("Redis client not initialized")
            return None
        
        # Check if connection is healthy
        if self._connection_stats.status == ConnectionStatus.UNHEALTHY:
            logger.warning("Redis connection is unhealthy, attempting reconnection...")
            if not await self._attempt_reconnection():
                return None
        
        return self._redis_client
    
    async def execute_with_retry(
        self, 
        operation: str, 
        *args, 
        **kwargs
    ) -> Any:
        """
        Execute Redis operation with automatic retry
        
        Args:
            operation: Redis operation name
            *args: Operation arguments
            **kwargs: Operation keyword arguments
            
        Returns:
            Any: Operation result
            
        Raises:
            RedisError: If operation fails after all retries
        """
        client = await self.get_client()
        if not client:
            raise ConnectionError("Redis client not available")
        
        last_error = None
        
        for attempt in range(self.max_retries + 1):
            try:
                # Get the operation method
                method = getattr(client, operation)
                
                # Execute operation
                start_time = time.time()
                result = await method(*args, **kwargs)
                response_time = (time.time() - start_time) * 1000
                
                # Update stats on success
                self._connection_stats.response_time_ms = response_time
                
                return result
                
            except (ConnectionError, TimeoutError) as e:
                last_error = e
                self._connection_stats.connection_errors += 1
                
                if attempt < self.max_retries:
                    logger.warning(f"Redis operation '{operation}' failed (attempt {attempt + 1}), retrying in {self.retry_delay}s: {e}")
                    await asyncio.sleep(self.retry_delay)
                    
                    # Try to reconnect
                    await self._attempt_reconnection()
                else:
                    logger.error(f"Redis operation '{operation}' failed after {self.max_retries + 1} attempts: {e}")
                    self._connection_stats.status = ConnectionStatus.UNHEALTHY
                    
            except Exception as e:
                logger.error(f"Unexpected error in Redis operation '{operation}': {e}")
                raise
        
        raise last_error or RedisError(f"Operation '{operation}' failed")
    
    async def get_connection_stats(self) -> ConnectionStats:
        """
        Get current connection statistics
        
        Returns:
            ConnectionStats: Current connection statistics
        """
        try:
            if self._primary_pool:
                # Update pool statistics
                pool_stats = self._primary_pool.connection_kwargs
                self._connection_stats.total_connections = self._primary_pool.max_connections
                
                # Try to get active connections (if available)
                try:
                    created_connections = len(self._primary_pool._created_connections)
                    available_connections = len(self._primary_pool._available_connections)
                    
                    self._connection_stats.active_connections = created_connections - available_connections
                    self._connection_stats.idle_connections = available_connections
                except AttributeError:
                    # Fallback if internal attributes are not available
                    pass
            
            return self._connection_stats
            
        except Exception as e:
            logger.error(f"Failed to get connection stats: {e}")
            return self._connection_stats
    
    async def health_check(self) -> Dict[str, Any]:
        """
        Perform comprehensive health check
        
        Returns:
            Dict[str, Any]: Health check results
        """
        try:
            health_result = {
                "timestamp": datetime.utcnow().isoformat(),
                "status": "unknown",
                "connection_pool": {},
                "redis_info": {},
                "performance": {},
                "errors": []
            }
            
            # Test basic connectivity
            start_time = time.time()
            try:
                client = await self.get_client()
                if client:
                    await client.ping()
                    response_time = (time.time() - start_time) * 1000
                    
                    health_result["status"] = "healthy"
                    health_result["performance"]["ping_time_ms"] = response_time
                    
                    # Get Redis info
                    info = await client.info()
                    health_result["redis_info"] = {
                        "version": info.get("redis_version", "unknown"),
                        "uptime_seconds": info.get("uptime_in_seconds", 0),
                        "connected_clients": info.get("connected_clients", 0),
                        "used_memory_human": info.get("used_memory_human", "unknown"),
                        "total_commands_processed": info.get("total_commands_processed", 0)
                    }
                    
                else:
                    health_result["status"] = "unhealthy"
                    health_result["errors"].append("Redis client not available")
                    
            except Exception as e:
                health_result["status"] = "unhealthy"
                health_result["errors"].append(f"Connection test failed: {e}")
            
            # Connection pool stats
            stats = await self.get_connection_stats()
            health_result["connection_pool"] = {
                "total_connections": stats.total_connections,
                "active_connections": stats.active_connections,
                "idle_connections": stats.idle_connections,
                "failed_connections": stats.failed_connections,
                "connection_errors": stats.connection_errors,
                "status": stats.status.value
            }
            
            # Update internal stats
            self._connection_stats.last_health_check = datetime.utcnow()
            if health_result["status"] == "healthy":
                self._connection_stats.status = ConnectionStatus.HEALTHY
            else:
                self._connection_stats.status = ConnectionStatus.UNHEALTHY
            
            return health_result
            
        except Exception as e:
            logger.error(f"Health check failed: {e}")
            return {
                "timestamp": datetime.utcnow().isoformat(),
                "status": "error",
                "errors": [str(e)]
            }
    
    # Private methods
    
    async def _test_connection(self) -> bool:
        """Test Redis connection"""
        try:
            if self._redis_client:
                await self._redis_client.ping()
                self._connection_stats.status = ConnectionStatus.HEALTHY
                logger.info("✅ Redis connection test successful")
                return True
        except Exception as e:
            logger.error(f"❌ Redis connection test failed: {e}")
            self._connection_stats.status = ConnectionStatus.UNHEALTHY
            self._connection_stats.connection_errors += 1
        
        return False
    
    async def _attempt_reconnection(self) -> bool:
        """Attempt to reconnect to Redis"""
        try:
            logger.info("🔄 Attempting Redis reconnection...")
            
            # Close existing connections
            if self._redis_client:
                await self._redis_client.close()
            
            if self._primary_pool:
                await self._primary_pool.disconnect()
            
            # Reinitialize
            return await self.initialize()
            
        except Exception as e:
            logger.error(f"Reconnection attempt failed: {e}")
            return False
    
    async def _health_check_loop(self) -> None:
        """Background health check loop"""
        while True:
            try:
                await asyncio.sleep(self.health_check_interval)
                
                # Perform health check
                health_result = await self.health_check()
                
                # Log status changes
                current_status = health_result.get("status", "unknown")
                if current_status != self._connection_stats.status.value:
                    logger.info(f"Redis connection status changed: {self._connection_stats.status.value} -> {current_status}")
                
                # Auto-reconnect if unhealthy
                if current_status == "unhealthy":
                    logger.warning("Redis connection unhealthy, attempting auto-reconnection...")
                    await self._attempt_reconnection()
                
            except asyncio.CancelledError:
                logger.info("Redis health check loop cancelled")
                break
            except Exception as e:
                logger.error(f"Error in Redis health check loop: {e}")
    
    # Utility methods for common operations
    
    async def set_with_ttl(self, key: str, value: str, ttl_seconds: int) -> bool:
        """Set key with TTL"""
        try:
            result = await self.execute_with_retry('setex', key, ttl_seconds, value)
            return result is not None
        except Exception as e:
            logger.error(f"Failed to set key with TTL: {e}")
            return False
    
    async def get_key(self, key: str) -> Optional[str]:
        """Get key value"""
        try:
            return await self.execute_with_retry('get', key)
        except Exception as e:
            logger.error(f"Failed to get key: {e}")
            return None
    
    async def delete_keys(self, *keys: str) -> int:
        """Delete multiple keys"""
        try:
            return await self.execute_with_retry('delete', *keys)
        except Exception as e:
            logger.error(f"Failed to delete keys: {e}")
            return 0
    
    async def exists_key(self, key: str) -> bool:
        """Check if key exists"""
        try:
            result = await self.execute_with_retry('exists', key)
            return result > 0
        except Exception as e:
            logger.error(f"Failed to check key existence: {e}")
            return False
    
    async def get_ttl(self, key: str) -> int:
        """Get key TTL"""
        try:
            return await self.execute_with_retry('ttl', key)
        except Exception as e:
            logger.error(f"Failed to get key TTL: {e}")
            return -1
    
    async def scan_keys(self, pattern: str, count: int = 100) -> List[str]:
        """Scan keys matching pattern"""
        try:
            keys = []
            cursor = 0
            
            while True:
                cursor, batch = await self.execute_with_retry('scan', cursor, match=pattern, count=count)
                keys.extend(batch)
                
                if cursor == 0:
                    break
            
            return keys
        except Exception as e:
            logger.error(f"Failed to scan keys: {e}")
            return []


# Global connection manager instance
_connection_manager: Optional[RedisConnectionManager] = None


async def get_redis_connection_manager() -> RedisConnectionManager:
    """
    Get global Redis connection manager instance
    
    Returns:
        RedisConnectionManager: Global connection manager
    """
    global _connection_manager
    
    if _connection_manager is None:
        _connection_manager = RedisConnectionManager()
        await _connection_manager.initialize()
    
    return _connection_manager


async def shutdown_redis_connection_manager() -> None:
    """Shutdown global Redis connection manager"""
    global _connection_manager
    
    if _connection_manager:
        await _connection_manager.shutdown()
        _connection_manager = None