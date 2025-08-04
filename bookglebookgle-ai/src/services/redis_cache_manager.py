"""
Redis Cache Manager for BGBG AI Server
Implements intelligent caching strategies for chat history and context data
"""

import asyncio
import json
import logging
from datetime import datetime, timedelta
from typing import Dict, List, Optional, Any, Union, Tuple
from dataclasses import dataclass, asdict
from enum import Enum
import hashlib

import redis.asyncio as redis
from src.config.settings import get_settings
from src.config.chat_config import get_performance_config, get_ttl_config
from src.models.chat_history_models import ChatMessage, ConversationContext, ParticipantState

logger = logging.getLogger(__name__)


class CacheLevel(str, Enum):
    """Cache levels for different data types"""
    L1_HOT = "l1_hot"           # Frequently accessed data (5 min TTL)
    L2_WARM = "l2_warm"         # Moderately accessed data (30 min TTL)
    L3_COLD = "l3_cold"         # Rarely accessed data (2 hour TTL)


class CacheKeyType(str, Enum):
    """Types of cache keys"""
    MESSAGE = "msg"
    CONTEXT = "ctx"
    PARTICIPANT = "part"
    SESSION_META = "meta"
    ANALYSIS = "analysis"
    SUMMARY = "summary"


@dataclass
class CacheStats:
    """Cache performance statistics"""
    total_requests: int
    cache_hits: int
    cache_misses: int
    hit_rate: float
    total_keys: int
    memory_usage_bytes: int
    evicted_keys: int
    last_reset: datetime


@dataclass
class CacheEntry:
    """Cache entry with metadata"""
    key: str
    data: Any
    cache_level: CacheLevel
    created_at: datetime
    last_accessed: datetime
    access_count: int
    ttl_seconds: int


class RedisCacheManager:
    """
    Advanced Redis caching manager with multi-level caching strategy
    """
    
    def __init__(self, redis_client: Optional[redis.Redis] = None):
        """
        Initialize Redis cache manager
        
        Args:
            redis_client: Optional Redis client (will create if not provided)
        """
        self.settings = get_settings()
        self.performance_config = get_performance_config()
        self.ttl_config = get_ttl_config()
        
        self._redis = redis_client
        
        # Cache configuration
        self.cache_ttls = {
            CacheLevel.L1_HOT: 300,    # 5 minutes
            CacheLevel.L2_WARM: 1800,  # 30 minutes
            CacheLevel.L3_COLD: 7200   # 2 hours
        }
        
        # Key naming patterns
        self.key_patterns = {
            CacheKeyType.MESSAGE: "cache:msg:{session_id}:{message_id}",
            CacheKeyType.CONTEXT: "cache:ctx:{session_id}",
            CacheKeyType.PARTICIPANT: "cache:part:{session_id}:{user_id}",
            CacheKeyType.SESSION_META: "cache:meta:{session_id}",
            CacheKeyType.ANALYSIS: "cache:analysis:{session_id}:{analysis_type}",
            CacheKeyType.SUMMARY: "cache:summary:{session_id}:{summary_type}"
        }
        
        # Cache statistics
        self._stats = CacheStats(
            total_requests=0,
            cache_hits=0,
            cache_misses=0,
            hit_rate=0.0,
            total_keys=0,
            memory_usage_bytes=0,
            evicted_keys=0,
            last_reset=datetime.utcnow()
        )
        
        # Access tracking for intelligent caching
        self._access_tracker: Dict[str, int] = {}
        
        logger.info("RedisCacheManager initialized")
    
    async def start(self) -> None:
        """Start cache manager and background tasks"""
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
            logger.info("✅ Redis cache connection established")
            
            # Start background tasks
            asyncio.create_task(self._stats_update_loop())
            asyncio.create_task(self._cache_optimization_loop())
            
            logger.info("RedisCacheManager started with background tasks")
            
        except Exception as e:
            logger.error(f"Failed to start RedisCacheManager: {e}")
            raise
    
    async def stop(self) -> None:
        """Stop cache manager and close connections"""
        try:
            if self._redis:
                await self._redis.close()
            logger.info("RedisCacheManager stopped")
        except Exception as e:
            logger.error(f"Error stopping RedisCacheManager: {e}")
    
    # Message caching methods
    
    async def cache_message(
        self, 
        session_id: str, 
        message: ChatMessage,
        cache_level: CacheLevel = CacheLevel.L2_WARM
    ) -> bool:
        """
        Cache a chat message
        
        Args:
            session_id: Session identifier
            message: Message to cache
            cache_level: Cache level for TTL determination
            
        Returns:
            bool: True if cached successfully
        """
        try:
            key = self._build_key(CacheKeyType.MESSAGE, session_id=session_id, message_id=message.message_id)
            
            # Serialize message
            message_data = {
                "message_id": message.message_id,
                "session_id": message.session_id,
                "user_id": message.user_id,
                "nickname": message.nickname,
                "content": message.content,
                "timestamp": message.timestamp.isoformat(),
                "message_type": message.message_type.value,
                "metadata": message.metadata,
                "sentiment": message.sentiment,
                "topics": message.topics,
                "intent": message.intent
            }
            
            # Cache with TTL
            ttl = self.cache_ttls[cache_level]
            await self._redis.setex(key, ttl, json.dumps(message_data))
            
            # Update access tracking
            self._track_access(key)
            
            logger.debug(f"Cached message {message.message_id} with {cache_level.value} level")
            return True
            
        except Exception as e:
            logger.error(f"Failed to cache message: {e}")
            return False
    
    async def get_cached_message(self, session_id: str, message_id: str) -> Optional[ChatMessage]:
        """
        Retrieve cached message
        
        Args:
            session_id: Session identifier
            message_id: Message identifier
            
        Returns:
            Optional[ChatMessage]: Cached message or None
        """
        try:
            key = self._build_key(CacheKeyType.MESSAGE, session_id=session_id, message_id=message_id)
            
            # Update statistics
            self._stats.total_requests += 1
            
            # Get from cache
            cached_data = await self._redis.get(key)
            
            if cached_data:
                # Cache hit
                self._stats.cache_hits += 1
                self._track_access(key)
                
                # Deserialize message
                message_data = json.loads(cached_data)
                message = ChatMessage(
                    message_id=message_data["message_id"],
                    session_id=message_data["session_id"],
                    user_id=message_data["user_id"],
                    nickname=message_data["nickname"],
                    content=message_data["content"],
                    timestamp=datetime.fromisoformat(message_data["timestamp"]),
                    message_type=message_data["message_type"],
                    metadata=message_data.get("metadata", {}),
                    sentiment=message_data.get("sentiment"),
                    topics=message_data.get("topics"),
                    intent=message_data.get("intent")
                )
                
                logger.debug(f"Cache hit for message {message_id}")
                return message
            else:
                # Cache miss
                self._stats.cache_misses += 1
                logger.debug(f"Cache miss for message {message_id}")
                return None
                
        except Exception as e:
            logger.error(f"Failed to get cached message: {e}")
            self._stats.cache_misses += 1
            return None
    
    # Context caching methods
    
    async def cache_context(
        self, 
        session_id: str, 
        context: ConversationContext,
        cache_level: CacheLevel = CacheLevel.L1_HOT
    ) -> bool:
        """
        Cache conversation context
        
        Args:
            session_id: Session identifier
            context: Context to cache
            cache_level: Cache level for TTL determination
            
        Returns:
            bool: True if cached successfully
        """
        try:
            key = self._build_key(CacheKeyType.CONTEXT, session_id=session_id)
            
            # Serialize context (lightweight version)
            context_data = {
                "session_id": context.session_id,
                "recent_messages": [msg.to_dict() for msg in context.recent_messages[-5:]],  # Only last 5
                "book_context": context.book_context,
                "conversation_summary": context.conversation_summary,
                "active_topics": context.active_topics,
                "context_window_size": context.context_window_size,
                "total_token_count": context.total_token_count,
                "created_at": context.created_at.isoformat(),
                "last_updated": context.last_updated.isoformat()
            }
            
            # Cache with TTL
            ttl = self.cache_ttls[cache_level]
            await self._redis.setex(key, ttl, json.dumps(context_data))
            
            self._track_access(key)
            logger.debug(f"Cached context for session {session_id}")
            return True
            
        except Exception as e:
            logger.error(f"Failed to cache context: {e}")
            return False
    
    async def get_cached_context(self, session_id: str) -> Optional[Dict[str, Any]]:
        """
        Retrieve cached conversation context
        
        Args:
            session_id: Session identifier
            
        Returns:
            Optional[Dict[str, Any]]: Cached context data or None
        """
        try:
            key = self._build_key(CacheKeyType.CONTEXT, session_id=session_id)
            
            self._stats.total_requests += 1
            cached_data = await self._redis.get(key)
            
            if cached_data:
                self._stats.cache_hits += 1
                self._track_access(key)
                
                context_data = json.loads(cached_data)
                logger.debug(f"Cache hit for context {session_id}")
                return context_data
            else:
                self._stats.cache_misses += 1
                logger.debug(f"Cache miss for context {session_id}")
                return None
                
        except Exception as e:
            logger.error(f"Failed to get cached context: {e}")
            self._stats.cache_misses += 1
            return None
    
    # Participant state caching
    
    async def cache_participant_state(
        self, 
        session_id: str, 
        user_id: str, 
        participant_state: ParticipantState,
        cache_level: CacheLevel = CacheLevel.L2_WARM
    ) -> bool:
        """
        Cache participant state
        
        Args:
            session_id: Session identifier
            user_id: User identifier
            participant_state: Participant state to cache
            cache_level: Cache level for TTL determination
            
        Returns:
            bool: True if cached successfully
        """
        try:
            key = self._build_key(CacheKeyType.PARTICIPANT, session_id=session_id, user_id=user_id)
            
            # Serialize participant state
            state_data = participant_state.to_dict()
            
            # Cache with TTL
            ttl = self.cache_ttls[cache_level]
            await self._redis.setex(key, ttl, json.dumps(state_data))
            
            self._track_access(key)
            logger.debug(f"Cached participant state for {user_id} in session {session_id}")
            return True
            
        except Exception as e:
            logger.error(f"Failed to cache participant state: {e}")
            return False
    
    async def get_cached_participant_state(self, session_id: str, user_id: str) -> Optional[Dict[str, Any]]:
        """
        Retrieve cached participant state
        
        Args:
            session_id: Session identifier
            user_id: User identifier
            
        Returns:
            Optional[Dict[str, Any]]: Cached participant state or None
        """
        try:
            key = self._build_key(CacheKeyType.PARTICIPANT, session_id=session_id, user_id=user_id)
            
            self._stats.total_requests += 1
            cached_data = await self._redis.get(key)
            
            if cached_data:
                self._stats.cache_hits += 1
                self._track_access(key)
                
                state_data = json.loads(cached_data)
                logger.debug(f"Cache hit for participant {user_id}")
                return state_data
            else:
                self._stats.cache_misses += 1
                return None
                
        except Exception as e:
            logger.error(f"Failed to get cached participant state: {e}")
            self._stats.cache_misses += 1
            return None
    
    # Analysis result caching
    
    async def cache_analysis_result(
        self, 
        session_id: str, 
        analysis_type: str, 
        result: Dict[str, Any],
        cache_level: CacheLevel = CacheLevel.L3_COLD
    ) -> bool:
        """
        Cache analysis results (conversation patterns, sentiment, etc.)
        
        Args:
            session_id: Session identifier
            analysis_type: Type of analysis (e.g., 'patterns', 'sentiment')
            result: Analysis result to cache
            cache_level: Cache level for TTL determination
            
        Returns:
            bool: True if cached successfully
        """
        try:
            key = self._build_key(CacheKeyType.ANALYSIS, session_id=session_id, analysis_type=analysis_type)
            
            # Add timestamp to result
            cached_result = {
                **result,
                "cached_at": datetime.utcnow().isoformat(),
                "analysis_type": analysis_type
            }
            
            # Cache with TTL
            ttl = self.cache_ttls[cache_level]
            await self._redis.setex(key, ttl, json.dumps(cached_result))
            
            self._track_access(key)
            logger.debug(f"Cached {analysis_type} analysis for session {session_id}")
            return True
            
        except Exception as e:
            logger.error(f"Failed to cache analysis result: {e}")
            return False
    
    async def get_cached_analysis_result(self, session_id: str, analysis_type: str) -> Optional[Dict[str, Any]]:
        """
        Retrieve cached analysis result
        
        Args:
            session_id: Session identifier
            analysis_type: Type of analysis
            
        Returns:
            Optional[Dict[str, Any]]: Cached analysis result or None
        """
        try:
            key = self._build_key(CacheKeyType.ANALYSIS, session_id=session_id, analysis_type=analysis_type)
            
            self._stats.total_requests += 1
            cached_data = await self._redis.get(key)
            
            if cached_data:
                self._stats.cache_hits += 1
                self._track_access(key)
                
                result = json.loads(cached_data)
                logger.debug(f"Cache hit for {analysis_type} analysis")
                return result
            else:
                self._stats.cache_misses += 1
                return None
                
        except Exception as e:
            logger.error(f"Failed to get cached analysis result: {e}")
            self._stats.cache_misses += 1
            return None
    
    # Cache management methods
    
    async def invalidate_session_cache(self, session_id: str) -> int:
        """
        Invalidate all cache entries for a session
        
        Args:
            session_id: Session identifier
            
        Returns:
            int: Number of keys invalidated
        """
        try:
            # Find all keys for this session
            patterns = [
                f"cache:*:{session_id}",
                f"cache:*:{session_id}:*"
            ]
            
            keys_to_delete = []
            for pattern in patterns:
                keys = await self._redis.keys(pattern)
                keys_to_delete.extend(keys)
            
            # Delete keys
            if keys_to_delete:
                await self._redis.delete(*keys_to_delete)
                logger.info(f"Invalidated {len(keys_to_delete)} cache entries for session {session_id}")
            
            return len(keys_to_delete)
            
        except Exception as e:
            logger.error(f"Failed to invalidate session cache: {e}")
            return 0
    
    async def get_cache_stats(self) -> CacheStats:
        """
        Get current cache statistics
        
        Returns:
            CacheStats: Current cache statistics
        """
        try:
            # Update hit rate
            if self._stats.total_requests > 0:
                self._stats.hit_rate = self._stats.cache_hits / self._stats.total_requests
            
            # Get total cache keys
            cache_keys = await self._redis.keys("cache:*")
            self._stats.total_keys = len(cache_keys)
            
            # Estimate memory usage
            total_memory = 0
            for key in cache_keys[:100]:  # Sample first 100 keys
                try:
                    memory = await self._redis.memory_usage(key)
                    if memory:
                        total_memory += memory
                except Exception:
                    pass
            
            # Extrapolate total memory usage
            if len(cache_keys) > 0:
                avg_memory_per_key = total_memory / min(100, len(cache_keys))
                self._stats.memory_usage_bytes = int(avg_memory_per_key * len(cache_keys))
            
            return self._stats
            
        except Exception as e:
            logger.error(f"Failed to get cache stats: {e}")
            return self._stats
    
    async def optimize_cache(self) -> Dict[str, Any]:
        """
        Optimize cache by promoting/demoting keys based on access patterns
        
        Returns:
            Dict[str, Any]: Optimization results
        """
        try:
            logger.info("🔧 Starting cache optimization")
            
            promoted_keys = 0
            demoted_keys = 0
            
            # Get all cache keys
            cache_keys = await self._redis.keys("cache:*")
            
            for key in cache_keys:
                try:
                    access_count = self._access_tracker.get(key, 0)
                    current_ttl = await self._redis.ttl(key)
                    
                    if current_ttl <= 0:
                        continue
                    
                    # Determine optimal cache level based on access pattern
                    if access_count >= 10:
                        # Promote to hot cache
                        new_ttl = self.cache_ttls[CacheLevel.L1_HOT]
                        if current_ttl < new_ttl:
                            await self._redis.expire(key, new_ttl)
                            promoted_keys += 1
                    elif access_count >= 3:
                        # Keep in warm cache
                        new_ttl = self.cache_ttls[CacheLevel.L2_WARM]
                        await self._redis.expire(key, new_ttl)
                    else:
                        # Demote to cold cache
                        new_ttl = self.cache_ttls[CacheLevel.L3_COLD]
                        if current_ttl > new_ttl:
                            await self._redis.expire(key, new_ttl)
                            demoted_keys += 1
                
                except Exception as e:
                    logger.warning(f"Error optimizing key {key}: {e}")
            
            # Reset access tracker
            self._access_tracker.clear()
            
            result = {
                "promoted_keys": promoted_keys,
                "demoted_keys": demoted_keys,
                "total_keys_processed": len(cache_keys),
                "optimization_time": datetime.utcnow().isoformat()
            }
            
            logger.info(f"✅ Cache optimization completed: {result}")
            return result
            
        except Exception as e:
            logger.error(f"Cache optimization failed: {e}")
            return {"error": str(e)}
    
    # Private methods
    
    def _build_key(self, key_type: CacheKeyType, **kwargs) -> str:
        """Build cache key from pattern and parameters"""
        pattern = self.key_patterns[key_type]
        return pattern.format(**kwargs)
    
    def _track_access(self, key: str) -> None:
        """Track key access for optimization"""
        self._access_tracker[key] = self._access_tracker.get(key, 0) + 1
    
    async def _stats_update_loop(self) -> None:
        """Background task to update cache statistics"""
        while True:
            try:
                await asyncio.sleep(300)  # Update every 5 minutes
                await self.get_cache_stats()
            except asyncio.CancelledError:
                break
            except Exception as e:
                logger.error(f"Error in stats update loop: {e}")
    
    async def _cache_optimization_loop(self) -> None:
        """Background task for cache optimization"""
        while True:
            try:
                await asyncio.sleep(1800)  # Optimize every 30 minutes
                await self.optimize_cache()
            except asyncio.CancelledError:
                break
            except Exception as e:
                logger.error(f"Error in cache optimization loop: {e}")
    
    async def health_check(self) -> Dict[str, Any]:
        """
        Perform health check on cache system
        
        Returns:
            Dict[str, Any]: Health check results
        """
        try:
            # Test Redis connection
            await self._redis.ping()
            connection_healthy = True
        except Exception:
            connection_healthy = False
        
        # Get cache statistics
        stats = await self.get_cache_stats()
        
        return {
            "redis_connection_healthy": connection_healthy,
            "cache_hit_rate": stats.hit_rate,
            "total_cache_keys": stats.total_keys,
            "memory_usage_bytes": stats.memory_usage_bytes,
            "total_requests": stats.total_requests,
            "cache_hits": stats.cache_hits,
            "cache_misses": stats.cache_misses
        }