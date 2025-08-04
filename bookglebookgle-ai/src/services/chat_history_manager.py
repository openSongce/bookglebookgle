"""
Chat History Manager for BGBG AI Server
Main service class that orchestrates chat history storage and management
"""

import asyncio
import logging
from datetime import datetime, timedelta
from typing import Dict, List, Optional, Any

from src.config.settings import get_settings
from src.config.chat_config import (
    is_chat_history_enabled,
    get_chat_history_config,
    get_ttl_config,
    get_performance_config
)
from src.models.chat_history_models import (
    ChatMessage, 
    ConversationContext, 
    ParticipantState,
    SessionMetadata,
    StorageError,
    ChatHistoryError
)
from src.models.chat_interfaces import ChatHistoryManagerInterface
from .redis_chat_storage import RedisChatStorage

logger = logging.getLogger(__name__)


class ChatHistoryManager:
    """
    Main chat history management service
    Provides high-level interface for chat history operations with business logic
    """
    
    def __init__(self, storage: Optional[ChatHistoryManagerInterface] = None):
        """
        Initialize chat history manager
        
        Args:
            storage: Optional storage implementation (defaults to Redis)
        """
        self.settings = get_settings()
        self._storage = storage or RedisChatStorage()
        self._cleanup_task = None
        self._session_contexts: Dict[str, ConversationContext] = {}
        
        # Configuration
        self.config = get_chat_history_config()
        self.ttl_config = get_ttl_config()
        self.performance_config = get_performance_config()
        
        logger.info("ChatHistoryManager initialized")
    
    async def start(self) -> None:
        """Start the chat history manager and background tasks"""
        if not is_chat_history_enabled():
            logger.info("Chat history feature is disabled")
            return
        
        # Start cleanup task
        cleanup_interval = self.performance_config['session_cleanup_interval_minutes'] * 60
        self._cleanup_task = asyncio.create_task(
            self._periodic_cleanup(cleanup_interval)
        )
        
        logger.info("ChatHistoryManager started with periodic cleanup")
    
    async def stop(self) -> None:
        """Stop the chat history manager and cleanup resources"""
        if self._cleanup_task:
            self._cleanup_task.cancel()
            try:
                await self._cleanup_task
            except asyncio.CancelledError:
                pass
        
        if hasattr(self._storage, 'close'):
            await self._storage.close()
        
        logger.info("ChatHistoryManager stopped")
    
    async def store_message(
        self, 
        session_id: str, 
        message: ChatMessage,
        auto_ttl: bool = True
    ) -> str:
        """
        Store a chat message with automatic TTL management
        
        Args:
            session_id: Discussion session identifier
            message: ChatMessage object to store
            auto_ttl: Whether to automatically set TTL based on activity
            
        Returns:
            str: Message ID of the stored message
            
        Raises:
            ChatHistoryError: If chat history is disabled or storage fails
        """
        if not is_chat_history_enabled():
            raise ChatHistoryError("Chat history feature is disabled")
        
        try:
            # Store message in underlying storage
            message_id = await self._storage.store_message(session_id, message)
            
            # Update in-memory context if exists
            if session_id in self._session_contexts:
                self._session_contexts[session_id].add_message(message)
            
            # Auto-adjust TTL based on activity
            if auto_ttl:
                await self._adjust_session_ttl(session_id)
            
            logger.debug(f"Stored message {message_id} for session {session_id}")
            return message_id
            
        except Exception as e:
            logger.error(f"Failed to store message: {e}")
            raise ChatHistoryError(f"Failed to store message: {e}")
    
    async def get_recent_messages(
        self, 
        session_id: str, 
        limit: Optional[int] = None,
        time_window: Optional[timedelta] = None
    ) -> List[ChatMessage]:
        """
        Get recent messages with intelligent defaults
        
        Args:
            session_id: Discussion session identifier
            limit: Maximum number of messages (defaults to config)
            time_window: Time window for messages (defaults to config)
            
        Returns:
            List[ChatMessage]: List of recent messages
            
        Raises:
            ChatHistoryError: If retrieval fails
        """
        if not is_chat_history_enabled():
            return []
        
        try:
            # Use configuration defaults if not specified
            if limit is None:
                limit = self.config.max_messages
            
            if time_window is None:
                time_window = self.config.time_window
            
            messages = await self._storage.get_recent_messages(
                session_id, limit, time_window
            )
            
            logger.debug(f"Retrieved {len(messages)} recent messages for session {session_id}")
            return messages
            
        except Exception as e:
            logger.error(f"Failed to get recent messages: {e}")
            raise ChatHistoryError(f"Failed to get recent messages: {e}")
    
    async def get_user_messages(
        self, 
        session_id: str, 
        user_id: str, 
        limit: int = 5
    ) -> List[ChatMessage]:
        """
        Get messages from a specific user
        
        Args:
            session_id: Discussion session identifier
            user_id: User identifier
            limit: Maximum number of messages
            
        Returns:
            List[ChatMessage]: List of user messages
        """
        if not is_chat_history_enabled():
            return []
        
        try:
            messages = await self._storage.get_user_messages(session_id, user_id, limit)
            logger.debug(f"Retrieved {len(messages)} messages from user {user_id}")
            return messages
            
        except Exception as e:
            logger.error(f"Failed to get user messages: {e}")
            return []  # Non-critical failure
    
    async def get_conversation_context(
        self, 
        session_id: str,
        book_context: Optional[List[str]] = None
    ) -> ConversationContext:
        """
        Get or create conversation context for a session
        
        Args:
            session_id: Discussion session identifier
            book_context: Optional book context from vector DB
            
        Returns:
            ConversationContext: Current conversation context
        """
        if not is_chat_history_enabled():
            # Return minimal context if disabled
            return ConversationContext(
                session_id=session_id,
                recent_messages=[],
                book_context=book_context or []
            )
        
        try:
            # Check if we have cached context
            if session_id in self._session_contexts:
                context = self._session_contexts[session_id]
                # Update book context if provided
                if book_context:
                    context.book_context = book_context
                return context
            
            # Create new context from storage
            recent_messages = await self.get_recent_messages(session_id)
            
            context = ConversationContext(
                session_id=session_id,
                recent_messages=recent_messages,
                book_context=book_context or [],
                context_window_size=self.config.max_messages
            )
            
            # Build participant states from messages
            for message in recent_messages:
                context.add_message(message)
            
            # Cache context
            self._session_contexts[session_id] = context
            
            logger.debug(f"Created conversation context for session {session_id}")
            return context
            
        except Exception as e:
            logger.error(f"Failed to get conversation context: {e}")
            # Return minimal context on error
            return ConversationContext(
                session_id=session_id,
                recent_messages=[],
                book_context=book_context or []
            )
    
    async def update_conversation_context(
        self, 
        session_id: str, 
        message: ChatMessage
    ) -> ConversationContext:
        """
        Update conversation context with new message
        
        Args:
            session_id: Discussion session identifier
            message: New message to add
            
        Returns:
            ConversationContext: Updated conversation context
        """
        context = await self.get_conversation_context(session_id)
        context.add_message(message)
        
        # Update engagement levels
        context.update_engagement_levels()
        
        return context
    
    async def set_session_ttl(self, session_id: str, ttl_hours: int = 2) -> None:
        """
        Set TTL for session data
        
        Args:
            session_id: Discussion session identifier
            ttl_hours: Hours until expiration
        """
        if not is_chat_history_enabled():
            return
        
        try:
            await self._storage.set_session_ttl(session_id, ttl_hours)
            logger.debug(f"Set TTL {ttl_hours}h for session {session_id}")
            
        except Exception as e:
            logger.error(f"Failed to set session TTL: {e}")
    
    async def get_session_stats(self, session_id: str) -> Dict[str, Any]:
        """
        Get comprehensive session statistics
        
        Args:
            session_id: Discussion session identifier
            
        Returns:
            Dict[str, Any]: Session statistics
        """
        if not is_chat_history_enabled():
            return {"session_id": session_id, "chat_history_enabled": False}
        
        try:
            # Get basic stats from storage
            stats = await self._storage.get_session_stats(session_id)
            
            # Add context information if available
            if session_id in self._session_contexts:
                context = self._session_contexts[session_id]
                stats.update({
                    "context_messages": len(context.recent_messages),
                    "active_topics": context.active_topics,
                    "participant_engagement": {
                        user_id: state.engagement_level 
                        for user_id, state in context.participant_states.items()
                    }
                })
            
            stats["chat_history_enabled"] = True
            return stats
            
        except Exception as e:
            logger.error(f"Failed to get session stats: {e}")
            return {"session_id": session_id, "error": str(e)}
    
    async def cleanup_session(self, session_id: str) -> bool:
        """
        Clean up a specific session
        
        Args:
            session_id: Session to clean up
            
        Returns:
            bool: True if cleanup was successful
        """
        try:
            # Remove from memory cache
            if session_id in self._session_contexts:
                del self._session_contexts[session_id]
            
            # Note: Redis TTL will handle storage cleanup automatically
            logger.info(f"Cleaned up session {session_id}")
            return True
            
        except Exception as e:
            logger.error(f"Failed to cleanup session {session_id}: {e}")
            return False
    
    async def get_active_sessions(self) -> List[str]:
        """
        Get list of active session IDs
        
        Returns:
            List[str]: List of active session IDs
        """
        if not is_chat_history_enabled():
            return []
        
        try:
            if hasattr(self._storage, 'get_active_sessions'):
                return await self._storage.get_active_sessions()
            else:
                # Fallback to memory cache
                return list(self._session_contexts.keys())
                
        except Exception as e:
            logger.error(f"Failed to get active sessions: {e}")
            return []
    
    async def _adjust_session_ttl(self, session_id: str) -> None:
        """
        Automatically adjust session TTL based on activity
        
        Args:
            session_id: Session to adjust TTL for
        """
        try:
            # Get session stats to determine activity level
            stats = await self._storage.get_session_stats(session_id)
            message_count = stats.get('message_count', 0)
            
            # Adjust TTL based on activity
            if message_count > 50:
                # High activity - extend TTL
                ttl_hours = 6
            elif message_count > 20:
                # Medium activity - standard TTL
                ttl_hours = 4
            else:
                # Low activity - shorter TTL
                ttl_hours = 2
            
            await self._storage.set_session_ttl(session_id, ttl_hours)
            
        except Exception as e:
            logger.warning(f"Failed to adjust TTL for session {session_id}: {e}")
    
    async def _periodic_cleanup(self, interval_seconds: int) -> None:
        """
        Periodic cleanup task
        
        Args:
            interval_seconds: Cleanup interval in seconds
        """
        while True:
            try:
                await asyncio.sleep(interval_seconds)
                
                # Clean up expired sessions from memory
                current_time = datetime.utcnow()
                expired_sessions = []
                
                for session_id, context in self._session_contexts.items():
                    # Check if context is too old
                    if current_time - context.last_updated > timedelta(hours=1):
                        expired_sessions.append(session_id)
                
                # Remove expired sessions
                for session_id in expired_sessions:
                    await self.cleanup_session(session_id)
                
                # Clean up expired sessions in storage
                if hasattr(self._storage, 'cleanup_expired_sessions'):
                    cleaned_count = await self._storage.cleanup_expired_sessions()
                    if cleaned_count > 0:
                        logger.info(f"Cleaned up {cleaned_count} expired sessions")
                
                logger.debug("Periodic cleanup completed")
                
            except asyncio.CancelledError:
                logger.info("Periodic cleanup task cancelled")
                break
            except Exception as e:
                logger.error(f"Error in periodic cleanup: {e}")
    
    async def health_check(self) -> Dict[str, Any]:
        """
        Perform health check on chat history system
        
        Returns:
            Dict[str, Any]: Health check results
        """
        health = {
            "chat_history_enabled": is_chat_history_enabled(),
            "storage_healthy": False,
            "active_sessions": 0,
            "cached_contexts": len(self._session_contexts),
            "cleanup_task_running": self._cleanup_task is not None and not self._cleanup_task.done()
        }
        
        if is_chat_history_enabled():
            try:
                # Test storage connection
                test_session = "health-check-test"
                test_message = ChatMessage(
                    message_id="health-test",
                    session_id=test_session,
                    user_id="health-user",
                    nickname="Health Check",
                    content="Health check message",
                    timestamp=datetime.utcnow()
                )
                
                # Try to store and retrieve
                await self._storage.store_message(test_session, test_message)
                messages = await self._storage.get_recent_messages(test_session, limit=1)
                
                health["storage_healthy"] = len(messages) > 0
                health["active_sessions"] = len(await self.get_active_sessions())
                
                # Clean up test data
                await self.cleanup_session(test_session)
                
            except Exception as e:
                health["storage_error"] = str(e)
                logger.error(f"Health check failed: {e}")
        
        return health