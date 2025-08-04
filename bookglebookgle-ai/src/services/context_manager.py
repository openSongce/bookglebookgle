"""
Context Manager for BGBG AI Server
Manages conversation context building, token counting, and optimization
"""

import asyncio
import logging
import re
from datetime import datetime, timedelta
from typing import Dict, List, Optional, Any

from src.config.settings import get_settings
from src.config.chat_config import get_chat_history_config, get_analysis_config
from src.models.chat_history_models import (
    ChatMessage, 
    ConversationContext, 
    ContextConfig,
    TopicChangeResult,
    ContextBuildError
)
from src.models.chat_interfaces import ContextManagerInterface
from src.services.vector_db import VectorDBManager
from src.services.token_manager import TokenManager, TokenizerType
from src.services.advanced_summarizer import AdvancedSummarizer, SummaryType, SummaryStrategy
from src.services.topic_analyzer import TopicAnalyzer, TopicExtractionMethod

# Optional import for LLM client
try:
    from src.services.llm_client import LLMClient
except ImportError:
    LLMClient = None

logger = logging.getLogger(__name__)


class ContextManager(ContextManagerInterface):
    """
    Context manager for building and optimizing conversation contexts
    Combines chat history with book content and manages token limits
    """
    
    def __init__(
        self, 
        vector_db: Optional[VectorDBManager] = None,
        llm_client: Optional[Any] = None,  # Use Any to avoid type issues when LLMClient is None
        tokenizer_type: TokenizerType = TokenizerType.GENERIC
    ):
        """
        Initialize context manager
        
        Args:
            vector_db: Vector database manager for book context
            llm_client: LLM client for summarization
            tokenizer_type: Type of tokenizer to use for token counting
        """
        self.settings = get_settings()
        self.vector_db = vector_db
        self.llm_client = llm_client
        
        # Configuration
        self.config = get_chat_history_config()
        self.analysis_config = get_analysis_config()
        
        # Advanced token management
        self.token_manager = TokenManager(tokenizer_type)
        
        # Advanced summarization
        self.summarizer = AdvancedSummarizer(llm_client)
        
        # Advanced topic analysis
        self.topic_analyzer = TopicAnalyzer(embedding_model=None)  # Can be enhanced with embedding model
        
        logger.info(f"ContextManager initialized with {tokenizer_type} tokenizer")
    
    async def build_context(
        self, 
        session_id: str, 
        current_message: str,
        context_config: ContextConfig,
        meeting_id: Optional[str] = None,
        recent_messages: Optional[List[ChatMessage]] = None
    ) -> ConversationContext:
        """
        Build comprehensive conversation context
        
        Args:
            session_id: Discussion session identifier
            current_message: Current message being processed
            context_config: Configuration for context building
            meeting_id: Meeting ID for book context retrieval
            recent_messages: Pre-fetched recent messages (optional)
            
        Returns:
            ConversationContext: Built conversation context
            
        Raises:
            ContextBuildError: If context building fails
        """
        try:
            logger.debug(f"Building context for session {session_id}")
            
            # Get book context from vector DB
            book_context = []
            if self.vector_db and meeting_id:
                book_context = await self._get_book_context(
                    meeting_id, 
                    current_message, 
                    context_config.max_book_chunks
                )
            
            # Use provided messages or create empty list
            if recent_messages is None:
                recent_messages = []
            
            # Create initial context
            context = ConversationContext(
                session_id=session_id,
                recent_messages=recent_messages,
                book_context=book_context,
                context_window_size=context_config.max_messages
            )
            
            # Build participant states from messages
            for message in recent_messages:
                context.add_message(message)
            
            # Calculate token count using advanced token manager
            context.total_token_count, token_breakdown = self.token_manager.count_context_tokens(context)
            
            # Optimize if needed using advanced optimization
            if context.total_token_count > context_config.max_tokens:
                context, optimization_info = self.token_manager.optimize_for_token_limit(
                    context, 
                    context_config.max_tokens
                )
                context.total_token_count = optimization_info['final_tokens']
                
                logger.debug(f"Context optimized: {optimization_info['strategies_applied']}")
            
            # Generate summary if enabled and needed
            if (context_config.enable_summarization and 
                context.total_token_count > context_config.summarization_threshold):
                
                # Use advanced summarizer
                summary_result = await self.summarizer.summarize_conversation(
                    recent_messages,
                    summary_type=SummaryType.BRIEF,
                    strategy=SummaryStrategy.HYBRID,
                    max_tokens=context_config.summarization_threshold // 2,
                    context=" ".join(book_context) if book_context else None
                )
                context.conversation_summary = summary_result['summary']
            
            # Detect active topics using advanced analyzer
            context.active_topics = await self.topic_analyzer._extract_topics_from_messages(recent_messages)
            
            logger.debug(f"Built context with {len(context.recent_messages)} messages, "
                        f"{len(context.book_context)} book chunks, "
                        f"{context.total_token_count} tokens")
            
            return context
            
        except Exception as e:
            logger.error(f"Failed to build context: {e}")
            raise ContextBuildError(f"Failed to build context: {e}")
    
    async def summarize_context(
        self, 
        messages: List[ChatMessage], 
        max_tokens: int = 500,
        summary_type: SummaryType = SummaryType.BRIEF,
        strategy: SummaryStrategy = SummaryStrategy.HYBRID
    ) -> str:
        """
        Summarize conversation context using advanced summarizer
        
        Args:
            messages: List of messages to summarize
            max_tokens: Maximum tokens for summary
            summary_type: Type of summary to generate
            strategy: Summarization strategy
            
        Returns:
            str: Summarized context
            
        Raises:
            ContextBuildError: If summarization fails
        """
        try:
            summary_result = await self.summarizer.summarize_conversation(
                messages,
                summary_type=summary_type,
                strategy=strategy,
                max_tokens=max_tokens
            )
            
            logger.debug(f"Generated advanced summary: {summary_result['metadata']}")
            return summary_result['summary']
            
        except Exception as e:
            logger.warning(f"Advanced summarization failed, using fallback: {e}")
            return await self._simple_summarize(messages, max_tokens)
    
    async def detect_topic_change(
        self, 
        recent_messages: List[ChatMessage],
        comparison_window: int = 3
    ) -> TopicChangeResult:
        """
        Detect if conversation topic has changed using advanced topic analyzer
        
        Args:
            recent_messages: Recent messages to analyze
            comparison_window: Number of messages to compare
            
        Returns:
            TopicChangeResult: Topic change detection result
            
        Raises:
            AnalysisError: If topic detection fails
        """
        try:
            # Use advanced topic analyzer
            result = await self.topic_analyzer.detect_topic_change(
                recent_messages, 
                comparison_window
            )
            
            logger.debug(f"Advanced topic change detection: changed={result.topic_changed}, "
                        f"confidence={result.confidence:.2f}")
            return result
            
        except Exception as e:
            logger.error(f"Topic change detection failed: {e}")
            return TopicChangeResult(
                topic_changed=False,
                confidence=0.0
            )
    
    async def calculate_tokens(self, context: ConversationContext) -> int:
        """
        Calculate estimated token count for context using advanced token manager
        
        Args:
            context: Conversation context to analyze
            
        Returns:
            int: Estimated token count
            
        Raises:
            ContextBuildError: If token calculation fails
        """
        try:
            total_tokens, breakdown = self.token_manager.count_context_tokens(context)
            
            logger.debug(f"Token breakdown: {breakdown}, total: {total_tokens}")
            return total_tokens
            
        except Exception as e:
            logger.error(f"Token calculation failed: {e}")
            raise ContextBuildError(f"Token calculation failed: {e}")
    
    async def optimize_context(
        self, 
        context: ConversationContext, 
        max_tokens: int
    ) -> ConversationContext:
        """
        Optimize context to fit within token limits using advanced token manager
        
        Args:
            context: Original conversation context
            max_tokens: Maximum allowed tokens
            
        Returns:
            ConversationContext: Optimized context
            
        Raises:
            ContextBuildError: If optimization fails
        """
        try:
            optimized_context, optimization_info = self.token_manager.optimize_for_token_limit(
                context, 
                max_tokens
            )
            
            logger.debug(f"Context optimization: {optimization_info}")
            return optimized_context
            
        except Exception as e:
            logger.error(f"Context optimization failed: {e}")
            raise ContextBuildError(f"Context optimization failed: {e}")
    
    async def _get_book_context(
        self, 
        meeting_id: str, 
        query: str, 
        max_chunks: int
    ) -> List[str]:
        """
        Get book context from vector database
        
        Args:
            meeting_id: Meeting identifier
            query: Search query
            max_chunks: Maximum chunks to retrieve
            
        Returns:
            List[str]: Book context chunks
        """
        try:
            if not self.vector_db:
                return []
            
            # Use existing vector DB method
            context_chunks = await self.vector_db.get_bookclub_context_for_discussion(
                meeting_id=meeting_id,
                query=query,
                max_chunks=max_chunks
            )
            
            logger.debug(f"Retrieved {len(context_chunks)} book context chunks")
            return context_chunks
            
        except Exception as e:
            logger.warning(f"Failed to get book context: {e}")
            return []
    
    async def _extract_topics(self, messages: List[ChatMessage]) -> List[str]:
        """
        Extract topics from messages using simple keyword extraction
        
        Args:
            messages: Messages to analyze
            
        Returns:
            List[str]: Extracted topics
        """
        try:
            if not messages:
                return []
            
            # Combine all message content
            text = " ".join([msg.content for msg in messages])
            
            # Simple keyword extraction (in production, use more sophisticated NLP)
            # Remove common words and extract meaningful terms
            common_words = {
                '이', '그', '저', '것', '수', '있', '하', '되', '같', '또', '더', '잘', '좀', '정말',
                '그런', '이런', '저런', '어떤', '무엇', '누구', '언제', '어디', '왜', '어떻게',
                'the', 'a', 'an', 'and', 'or', 'but', 'in', 'on', 'at', 'to', 'for', 'of', 'with', 'by'
            }
            
            # Extract words (Korean and English)
            words = re.findall(r'[가-힣a-zA-Z]+', text.lower())
            
            # Filter and count
            word_count = {}
            for word in words:
                if len(word) > 1 and word not in common_words:
                    word_count[word] = word_count.get(word, 0) + 1
            
            # Get top topics
            topics = sorted(word_count.items(), key=lambda x: x[1], reverse=True)
            return [topic[0] for topic in topics[:5]]  # Top 5 topics
            
        except Exception as e:
            logger.warning(f"Topic extraction failed: {e}")
            return []
    
    async def _simple_summarize(self, messages: List[ChatMessage], max_tokens: int) -> str:
        """
        Simple fallback summarization without LLM
        
        Args:
            messages: Messages to summarize
            max_tokens: Maximum tokens for summary
            
        Returns:
            str: Simple summary
        """
        if not messages:
            return "대화 내용 없음"
        
        # Get participant names
        participants = list(set(msg.nickname for msg in messages))
        
        # Get topics
        topics = await self._extract_topics(messages)
        
        # Create simple summary
        summary_parts = []
        
        if participants:
            summary_parts.append(f"참여자: {', '.join(participants[:3])}")
            if len(participants) > 3:
                summary_parts.append(f" 외 {len(participants) - 3}명")
        
        if topics:
            summary_parts.append(f"주요 주제: {', '.join(topics[:3])}")
        
        summary_parts.append(f"총 {len(messages)}개 메시지")
        
        return ". ".join(summary_parts)
    
    def _format_messages_for_llm(self, messages: List[ChatMessage]) -> str:
        """
        Format messages for LLM processing
        
        Args:
            messages: Messages to format
            
        Returns:
            str: Formatted message text
        """
        formatted_messages = []
        for msg in messages:
            timestamp = msg.timestamp.strftime("%H:%M")
            formatted_messages.append(f"[{timestamp}] {msg.nickname}: {msg.content}")
        
        return "\n".join(formatted_messages)