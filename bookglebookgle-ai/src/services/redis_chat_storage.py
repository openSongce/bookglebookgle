"""
Redis-based Chat History Storage for BGBG AI Server
Implements chat message storage with TTL-based automatic expiration
"""

import json
import asyncio
from datetime import datetime, timedelta
from typing import Dict, List, Optional, Any
import logging

import redis.asyncio as redis
from redis.asyncio import ConnectionPool, Redis

from src.config.settings import get_settings
from src.models.chat_history_models import ChatMessage, SessionMetadata, StorageError
from src.models.chat_interfaces import ChatHistoryManagerInterface

logger = logging.getLogger(__name__)


class RedisChatStorage(ChatHistoryManagerInterface):
    """
    Redis-based implementation of chat history storage
    Uses Redis Lists for message storage and automatic TTL for cleanup
    """
    
    def __init__(self, redis_client: Optional[Redis] = None):
        """
        Initialize Redis chat storage
        
        Args:
            redis_client: Optional Redis client instance
        """
        self.settings = get_settings()
        self._redis_client = redis_client
        self._connection_pool = None
        
        # Redis key patterns
        self.MESSAGES_KEY = "chat:session:{session_id}:messages"
        self.CONTEXT_KEY = "chat:session:{session_id}:context"
        self.PARTICIPANTS_KEY = "chat:session:{session_id}:participants"
        self.META_KEY = "chat:session:{session_id}:meta"
        self.ACTIVE_SESSIONS_KEY = "chat:active_sessions"
        
        # TTL settings from configuration (convert hours to seconds)
        self.DEFAULT_MESSAGE_TTL = self.settings.chat_history.CHAT_MESSAGE_TTL_HOURS * 3600
        self.DEFAULT_CONTEXT_TTL = self.settings.chat_history.CHAT_CONTEXT_TTL_HOURS * 3600
        self.DEFAULT_PARTICIPANT_TTL = self.settings.chat_history.CHAT_PARTICIPANT_TTL_HOURS * 3600
        self.DEFAULT_META_TTL = self.settings.chat_history.CHAT_META_TTL_HOURS * 3600
    
    async def _get_redis_client(self) -> Redis:
        """Get or create Redis client with connection pool"""
        if self._redis_client is None:
            if self._connection_pool is None:
                self._connection_pool = ConnectionPool(
                    host=self.settings.database.REDIS_HOST,
                    port=self.settings.database.REDIS_PORT,
                    db=self.settings.database.REDIS_DB,
                    password=self.settings.database.REDIS_PASSWORD,
                    max_connections=self.settings.database.REDIS_MAX_CONNECTIONS,
                    socket_timeout=self.settings.database.REDIS_SOCKET_TIMEOUT,
                    socket_connect_timeout=self.settings.database.REDIS_SOCKET_CONNECT_TIMEOUT,
                    decode_responses=True,
                    retry_on_timeout=True
                )
            
            self._redis_client = Redis(connection_pool=self._connection_pool)
        
        return self._redis_client
    
    async def _ensure_connection(self) -> Redis:
        """Ensure Redis connection is available"""
        try:
            redis_client = await self._get_redis_client()
            await redis_client.ping()
            return redis_client
        except Exception as e:
            logger.error(f"Redis connection failed: {e}")
            raise StorageError(f"Failed to connect to Redis: {e}")
    
    async def store_message(self, session_id: str, message: ChatMessage) -> str:
        """
        Store a chat message in Redis with TTL
        
        Args:
            session_id: Discussion session identifier
            message: ChatMessage object to store
            
        Returns:
            str: Message ID of the stored message
            
        Raises:
            StorageError: If message storage fails
        """
        try:
            redis_client = await self._ensure_connection()
            
            # Serialize message to JSON
            message_json = message.to_json()
            
            # Store message in Redis list
            messages_key = self.MESSAGES_KEY.format(session_id=session_id)
            
            # Use pipeline for atomic operations
            pipe = redis_client.pipeline()
            
            # Add message to list
            pipe.lpush(messages_key, message_json)
            
            # Set TTL for messages
            pipe.expire(messages_key, self.DEFAULT_MESSAGE_TTL)
            
            # Add session to active sessions set
            pipe.sadd(self.ACTIVE_SESSIONS_KEY, session_id)
            
            # Update session metadata
            await self._update_session_metadata(pipe, session_id, message)
            
            # Execute pipeline
            await pipe.execute()
            
            logger.debug(f"Stored message {message.message_id} for session {session_id}")
            return message.message_id
            
        except Exception as e:
            logger.error(f"Failed to store message: {e}")
            raise StorageError(f"Failed to store message: {e}")
    
    async def get_recent_messages(
        self, 
        session_id: str, 
        limit: int = 10,
        time_window: Optional[timedelta] = None
    ) -> List[ChatMessage]:
        """
        Retrieve recent messages from Redis
        
        Args:
            session_id: Discussion session identifier
            limit: Maximum number of messages to retrieve
            time_window: Optional time window to filter messages
            
        Returns:
            List[ChatMessage]: List of recent messages (newest first)
            
        Raises:
            StorageError: If message retrieval fails
        """
        try:
            redis_client = await self._ensure_connection()
            
            messages_key = self.MESSAGES_KEY.format(session_id=session_id)
            
            # Get messages from Redis list (newest first)
            message_jsons = await redis_client.lrange(messages_key, 0, limit - 1)
            
            messages = []
            cutoff_time = None
            
            if time_window:
                cutoff_time = datetime.utcnow() - time_window
            
            for message_json in message_jsons:
                try:
                    message = ChatMessage.from_json(message_json)
                    
                    # Filter by time window if specified
                    if cutoff_time and message.timestamp < cutoff_time:
                        continue
                    
                    messages.append(message)
                    
                except Exception as e:
                    logger.warning(f"Failed to deserialize message: {e}")
                    continue
            
            # Return in chronological order (oldest first)
            messages.reverse()
            
            logger.debug(f"Retrieved {len(messages)} messages for session {session_id}")
            return messages
            
        except Exception as e:
            logger.error(f"Failed to retrieve messages: {e}")
            raise StorageError(f"Failed to retrieve messages: {e}")
    
    async def get_user_messages(
        self, 
        session_id: str, 
        user_id: str, 
        limit: int = 5
    ) -> List[ChatMessage]:
        """
        Retrieve messages from a specific user
        
        Args:
            session_id: Discussion session identifier
            user_id: User identifier
            limit: Maximum number of messages to retrieve
            
        Returns:
            List[ChatMessage]: List of user messages
            
        Raises:
            StorageError: If message retrieval fails
        """
        try:
            # Get all recent messages first
            all_messages = await self.get_recent_messages(session_id, limit * 3)  # Get more to filter
            
            # Filter by user_id
            user_messages = [msg for msg in all_messages if msg.user_id == user_id]
            
            # Return limited results
            return user_messages[-limit:] if len(user_messages) > limit else user_messages
            
        except Exception as e:
            logger.error(f"Failed to retrieve user messages: {e}")
            raise StorageError(f"Failed to retrieve user messages: {e}")
    
    async def cleanup_old_messages(
        self, 
        session_id: str, 
        retention_hours: int = 24
    ) -> int:
        """
        Clean up old messages (Redis TTL handles this automatically)
        
        Args:
            session_id: Discussion session identifier
            retention_hours: Hours to retain messages
            
        Returns:
            int: Number of messages cleaned up (always 0 for Redis TTL)
            
        Raises:
            StorageError: If cleanup fails
        """
        try:
            # Redis TTL handles automatic cleanup
            # This method is mainly for interface compliance
            logger.debug(f"Cleanup requested for session {session_id} (handled by Redis TTL)")
            return 0
            
        except Exception as e:
            logger.error(f"Failed to cleanup messages: {e}")
            raise StorageError(f"Failed to cleanup messages: {e}")
    
    async def set_session_ttl(self, session_id: str, ttl_hours: int = 2) -> None:
        """
        Set TTL for all session-related keys
        
        Args:
            session_id: Discussion session identifier
            ttl_hours: Hours until session data expires
            
        Raises:
            StorageError: If TTL setting fails
        """
        try:
            redis_client = await self._ensure_connection()
            ttl_seconds = ttl_hours * 3600
            
            # Set TTL for all session keys
            keys = [
                self.MESSAGES_KEY.format(session_id=session_id),
                self.CONTEXT_KEY.format(session_id=session_id),
                self.PARTICIPANTS_KEY.format(session_id=session_id),
                self.META_KEY.format(session_id=session_id)
            ]
            
            pipe = redis_client.pipeline()
            for key in keys:
                pipe.expire(key, ttl_seconds)
            
            await pipe.execute()
            
            logger.info(f"Set TTL {ttl_hours}h for session {session_id}")
            
        except Exception as e:
            logger.error(f"Failed to set session TTL: {e}")
            raise StorageError(f"Failed to set session TTL: {e}")
    
    async def get_session_stats(self, session_id: str) -> Dict[str, Any]:
        """
        Get statistics for a session
        
        Args:
            session_id: Discussion session identifier
            
        Returns:
            Dict[str, Any]: Session statistics
            
        Raises:
            StorageError: If stats retrieval fails
        """
        try:
            redis_client = await self._ensure_connection()
            
            # Get message count
            messages_key = self.MESSAGES_KEY.format(session_id=session_id)
            message_count = await redis_client.llen(messages_key)
            
            # Get session metadata
            meta_key = self.META_KEY.format(session_id=session_id)
            meta_data = await redis_client.hgetall(meta_key)
            
            # Get participant count from participants key
            participants_key = self.PARTICIPANTS_KEY.format(session_id=session_id)
            participant_data = await redis_client.hgetall(participants_key)
            participant_count = len(participant_data)
            
            # Get TTL information
            messages_ttl = await redis_client.ttl(messages_key)
            
            stats = {
                "session_id": session_id,
                "message_count": message_count,
                "participant_count": participant_count,
                "messages_ttl_seconds": messages_ttl,
                "created_at": meta_data.get("created_at"),
                "last_activity": meta_data.get("last_activity"),
                "status": meta_data.get("status", "unknown")
            }
            
            logger.debug(f"Retrieved stats for session {session_id}: {stats}")
            return stats
            
        except Exception as e:
            logger.error(f"Failed to get session stats: {e}")
            raise StorageError(f"Failed to get session stats: {e}")
    
    async def _update_session_metadata(
        self, 
        pipe: redis.client.Pipeline, 
        session_id: str, 
        message: ChatMessage
    ) -> None:
        """
        Update session metadata in Redis pipeline
        
        Args:
            pipe: Redis pipeline
            session_id: Session identifier
            message: Message being stored
        """
        meta_key = self.META_KEY.format(session_id=session_id)
        
        # Update metadata
        pipe.hset(meta_key, mapping={
            "session_id": session_id,
            "last_activity": datetime.utcnow().isoformat(),
            "status": "active"
        })
        
        # Set created_at if it doesn't exist
        pipe.hsetnx(meta_key, "created_at", datetime.utcnow().isoformat())
        
        # Increment message count
        pipe.hincrby(meta_key, "message_count", 1)
        
        # Set TTL for metadata
        pipe.expire(meta_key, self.DEFAULT_META_TTL)
        
        # Update participant info
        participants_key = self.PARTICIPANTS_KEY.format(session_id=session_id)
        pipe.hset(participants_key, message.user_id, json.dumps({
            "nickname": message.nickname,
            "last_message_time": message.timestamp.isoformat(),
            "user_id": message.user_id
        }))
        pipe.expire(participants_key, self.DEFAULT_PARTICIPANT_TTL)
    
    async def get_active_sessions(self) -> List[str]:
        """
        Get list of active session IDs
        
        Returns:
            List[str]: List of active session IDs
        """
        try:
            redis_client = await self._ensure_connection()
            sessions = await redis_client.smembers(self.ACTIVE_SESSIONS_KEY)
            return list(sessions)
            
        except Exception as e:
            logger.error(f"Failed to get active sessions: {e}")
            raise StorageError(f"Failed to get active sessions: {e}")
    
    async def cleanup_expired_sessions(self) -> int:
        """
        Clean up expired sessions from active sessions set
        
        Returns:
            int: Number of sessions cleaned up
        """
        try:
            redis_client = await self._ensure_connection()
            active_sessions = await self.get_active_sessions()
            
            cleaned_count = 0
            for session_id in active_sessions:
                # Check if session still has data
                messages_key = self.MESSAGES_KEY.format(session_id=session_id)
                exists = await redis_client.exists(messages_key)
                
                if not exists:
                    # Remove from active sessions
                    await redis_client.srem(self.ACTIVE_SESSIONS_KEY, session_id)
                    cleaned_count += 1
                    logger.info(f"Cleaned up expired session: {session_id}")
            
            return cleaned_count
            
        except Exception as e:
            logger.error(f"Failed to cleanup expired sessions: {e}")
            raise StorageError(f"Failed to cleanup expired sessions: {e}")
    
    async def close(self) -> None:
        """Close Redis connection"""
        if self._redis_client:
            await self._redis_client.close()
        if self._connection_pool:
            await self._connection_pool.disconnect()
        
        logger.info("Redis chat storage connection closed")