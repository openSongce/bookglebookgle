"""
Chat History Context Interfaces for BGBG AI Server
Defines abstract interfaces for chat history management, context building, and conversation analysis
"""

from abc import ABC, abstractmethod
from datetime import datetime, timedelta
from typing import Dict, List, Optional, Any

from .chat_history_models import (
    ChatMessage, 
    ConversationContext, 
    ParticipantState,
    ContextConfig,
    TopicChangeResult,
    ParticipationAnalysis,
    ConversationPatterns,
    InterventionSuggestion
)


class ChatHistoryManagerInterface(ABC):
    """
    Abstract interface for chat history storage and management
    Defines the contract for storing, retrieving, and managing chat messages
    """
    
    @abstractmethod
    async def store_message(self, session_id: str, message: ChatMessage) -> str:
        """
        Store a chat message in the history
        
        Args:
            session_id: Discussion session identifier
            message: ChatMessage object to store
            
        Returns:
            str: Message ID of the stored message
            
        Raises:
            StorageError: If message storage fails
        """
        pass
    
    @abstractmethod
    async def get_recent_messages(
        self, 
        session_id: str, 
        limit: int = 10,
        time_window: Optional[timedelta] = None
    ) -> List[ChatMessage]:
        """
        Retrieve recent messages from chat history
        
        Args:
            session_id: Discussion session identifier
            limit: Maximum number of messages to retrieve
            time_window: Optional time window to filter messages
            
        Returns:
            List[ChatMessage]: List of recent messages
            
        Raises:
            StorageError: If message retrieval fails
        """
        pass
    
    @abstractmethod
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
        pass
    
    @abstractmethod
    async def cleanup_old_messages(
        self, 
        session_id: str, 
        retention_hours: int = 24
    ) -> int:
        """
        Clean up old messages based on retention policy
        
        Args:
            session_id: Discussion session identifier
            retention_hours: Hours to retain messages
            
        Returns:
            int: Number of messages cleaned up
            
        Raises:
            StorageError: If cleanup fails
        """
        pass
    
    @abstractmethod
    async def set_session_ttl(self, session_id: str, ttl_hours: int = 2) -> None:
        """
        Set TTL (Time To Live) for session data
        
        Args:
            session_id: Discussion session identifier
            ttl_hours: Hours until session data expires
            
        Raises:
            StorageError: If TTL setting fails
        """
        pass
    
    @abstractmethod
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
        pass


class ContextManagerInterface(ABC):
    """
    Abstract interface for conversation context management
    Defines the contract for building and managing conversation contexts
    """
    
    @abstractmethod
    async def build_context(
        self, 
        session_id: str, 
        current_message: str,
        context_config: ContextConfig
    ) -> ConversationContext:
        """
        Build conversation context for AI response generation
        
        Args:
            session_id: Discussion session identifier
            current_message: Current message being processed
            context_config: Configuration for context building
            
        Returns:
            ConversationContext: Built conversation context
            
        Raises:
            ContextBuildError: If context building fails
        """
        pass
    
    @abstractmethod
    async def summarize_context(
        self, 
        messages: List[ChatMessage], 
        max_tokens: int = 500
    ) -> str:
        """
        Summarize conversation context when it becomes too long
        
        Args:
            messages: List of messages to summarize
            max_tokens: Maximum tokens for summary
            
        Returns:
            str: Summarized context
            
        Raises:
            ContextBuildError: If summarization fails
        """
        pass
    
    @abstractmethod
    async def detect_topic_change(
        self, 
        recent_messages: List[ChatMessage]
    ) -> TopicChangeResult:
        """
        Detect if the conversation topic has changed
        
        Args:
            recent_messages: Recent messages to analyze
            
        Returns:
            TopicChangeResult: Topic change detection result
            
        Raises:
            AnalysisError: If topic detection fails
        """
        pass
    
    @abstractmethod
    async def calculate_tokens(self, context: ConversationContext) -> int:
        """
        Calculate token count for the conversation context
        
        Args:
            context: Conversation context to analyze
            
        Returns:
            int: Estimated token count
            
        Raises:
            ContextBuildError: If token calculation fails
        """
        pass
    
    @abstractmethod
    async def optimize_context(
        self, 
        context: ConversationContext, 
        max_tokens: int
    ) -> ConversationContext:
        """
        Optimize context to fit within token limits
        
        Args:
            context: Original conversation context
            max_tokens: Maximum allowed tokens
            
        Returns:
            ConversationContext: Optimized context
            
        Raises:
            ContextBuildError: If optimization fails
        """
        pass


class ConversationAnalyzerInterface(ABC):
    """
    Abstract interface for conversation analysis
    Defines the contract for analyzing conversation patterns and participant behavior
    """
    
    @abstractmethod
    async def analyze_participation(
        self, 
        session_id: str
    ) -> ParticipationAnalysis:
        """
        Analyze participant engagement and activity patterns
        
        Args:
            session_id: Discussion session identifier
            
        Returns:
            ParticipationAnalysis: Analysis of participant engagement
            
        Raises:
            AnalysisError: If participation analysis fails
        """
        pass
    
    @abstractmethod
    async def detect_conversation_patterns(
        self, 
        messages: List[ChatMessage]
    ) -> ConversationPatterns:
        """
        Detect patterns in conversation flow
        
        Args:
            messages: Messages to analyze for patterns
            
        Returns:
            ConversationPatterns: Detected conversation patterns
            
        Raises:
            AnalysisError: If pattern detection fails
        """
        pass
    
    @abstractmethod
    async def suggest_intervention(
        self, 
        analysis: ParticipationAnalysis,
        patterns: ConversationPatterns
    ) -> InterventionSuggestion:
        """
        Suggest AI intervention based on analysis
        
        Args:
            analysis: Participation analysis results
            patterns: Conversation pattern analysis
            
        Returns:
            InterventionSuggestion: Suggested intervention
            
        Raises:
            AnalysisError: If intervention suggestion fails
        """
        pass
    
    @abstractmethod
    async def analyze_sentiment_trend(
        self, 
        messages: List[ChatMessage]
    ) -> Dict[str, Any]:
        """
        Analyze sentiment trends in the conversation
        
        Args:
            messages: Messages to analyze for sentiment
            
        Returns:
            Dict[str, Any]: Sentiment analysis results
            
        Raises:
            AnalysisError: If sentiment analysis fails
        """
        pass
    
    @abstractmethod
    async def detect_off_topic_discussion(
        self, 
        messages: List[ChatMessage],
        book_context: List[str]
    ) -> bool:
        """
        Detect if discussion has gone off-topic from the book
        
        Args:
            messages: Recent messages to analyze
            book_context: Book content for comparison
            
        Returns:
            bool: True if discussion is off-topic
            
        Raises:
            AnalysisError: If off-topic detection fails
        """
        pass


