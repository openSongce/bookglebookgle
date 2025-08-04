"""
Token Manager for BGBG AI Server
Advanced token counting and management for different LLM providers
"""

import re
import logging
from typing import Dict, List, Optional, Any, Tuple
from enum import Enum

from src.models.chat_history_models import ChatMessage, ConversationContext

logger = logging.getLogger(__name__)


class TokenizerType(str, Enum):
    """Supported tokenizer types"""
    OPENAI = "openai"
    ANTHROPIC = "anthropic"
    GEMINI = "gemini"
    GENERIC = "generic"


class TokenManager:
    """
    Advanced token counting and management
    Provides accurate token estimation for different LLM providers
    """
    
    def __init__(self, tokenizer_type: TokenizerType = TokenizerType.GENERIC):
        """
        Initialize token manager
        
        Args:
            tokenizer_type: Type of tokenizer to use
        """
        self.tokenizer_type = tokenizer_type
        
        # Token estimation ratios for different languages and providers
        self.token_ratios = {
            TokenizerType.OPENAI: {
                'korean': 2.5,      # Korean characters per token
                'english': 4.0,     # English characters per token
                'mixed': 3.0        # Mixed content
            },
            TokenizerType.ANTHROPIC: {
                'korean': 2.8,
                'english': 4.2,
                'mixed': 3.2
            },
            TokenizerType.GEMINI: {
                'korean': 2.2,
                'english': 3.8,
                'mixed': 2.8
            },
            TokenizerType.GENERIC: {
                'korean': 2.5,
                'english': 4.0,
                'mixed': 3.0
            }
        }
        
        # Try to load actual tokenizer if available
        self.tokenizer = None
        self._load_tokenizer()
        
        logger.info(f"TokenManager initialized with {tokenizer_type} tokenizer")
    
    def _load_tokenizer(self) -> None:
        """Load actual tokenizer if available"""
        try:
            if self.tokenizer_type == TokenizerType.OPENAI:
                try:
                    import tiktoken
                    self.tokenizer = tiktoken.get_encoding("cl100k_base")
                    logger.info("Loaded OpenAI tiktoken tokenizer")
                except ImportError:
                    logger.info("tiktoken not available, using estimation")
            
            # Add other tokenizers as needed
            
        except Exception as e:
            logger.warning(f"Failed to load tokenizer: {e}")
    
    def count_tokens(self, text: str) -> int:
        """
        Count tokens in text using the most accurate method available
        
        Args:
            text: Text to count tokens for
            
        Returns:
            int: Number of tokens
        """
        if not text:
            return 0
        
        # Use actual tokenizer if available
        if self.tokenizer:
            try:
                return len(self.tokenizer.encode(text))
            except Exception as e:
                logger.warning(f"Tokenizer failed, falling back to estimation: {e}")
        
        # Fall back to estimation
        return self._estimate_tokens(text)
    
    def _estimate_tokens(self, text: str) -> int:
        """
        Estimate tokens using character-based heuristics
        
        Args:
            text: Text to estimate tokens for
            
        Returns:
            int: Estimated number of tokens
        """
        if not text:
            return 0
        
        # Detect language composition
        korean_chars = len(re.findall(r'[가-힣]', text))
        english_chars = len(re.findall(r'[a-zA-Z]', text))
        total_chars = len(text)
        
        # Determine dominant language
        if korean_chars > english_chars:
            ratio = self.token_ratios[self.tokenizer_type]['korean']
        elif english_chars > korean_chars:
            ratio = self.token_ratios[self.tokenizer_type]['english']
        else:
            ratio = self.token_ratios[self.tokenizer_type]['mixed']
        
        # Calculate tokens with some overhead for special tokens
        base_tokens = total_chars / ratio
        overhead = max(1, int(base_tokens * 0.1))  # 10% overhead
        
        return int(base_tokens + overhead)
    
    def count_message_tokens(self, message: ChatMessage) -> int:
        """
        Count tokens for a single message including metadata
        
        Args:
            message: Message to count tokens for
            
        Returns:
            int: Number of tokens
        """
        # Count content tokens
        content_tokens = self.count_tokens(message.content)
        
        # Add tokens for metadata (nickname, timestamp, etc.)
        metadata_text = f"{message.nickname}: "
        metadata_tokens = self.count_tokens(metadata_text)
        
        # Add small overhead for message structure
        structure_overhead = 3
        
        return content_tokens + metadata_tokens + structure_overhead
    
    def count_context_tokens(self, context: ConversationContext) -> Tuple[int, Dict[str, int]]:
        """
        Count tokens for entire conversation context with breakdown
        
        Args:
            context: Conversation context to analyze
            
        Returns:
            Tuple[int, Dict[str, int]]: Total tokens and breakdown by component
        """
        breakdown = {
            'messages': 0,
            'book_context': 0,
            'summary': 0,
            'participants': 0,
            'metadata': 0
        }
        
        # Count message tokens
        for message in context.recent_messages:
            breakdown['messages'] += self.count_message_tokens(message)
        
        # Count book context tokens
        for chunk in context.book_context:
            breakdown['book_context'] += self.count_tokens(chunk)
        
        # Count summary tokens
        if context.conversation_summary:
            breakdown['summary'] = self.count_tokens(context.conversation_summary)
        
        # Count participant summary tokens
        participant_summary = context.get_participant_summary()
        breakdown['participants'] = self.count_tokens(participant_summary)
        
        # Count metadata tokens (topics, etc.)
        metadata_text = " ".join(context.active_topics)
        breakdown['metadata'] = self.count_tokens(metadata_text)
        
        total_tokens = sum(breakdown.values())
        
        return total_tokens, breakdown
    
    def optimize_for_token_limit(
        self, 
        context: ConversationContext, 
        max_tokens: int,
        preserve_recent: int = 2
    ) -> Tuple[ConversationContext, Dict[str, Any]]:
        """
        Optimize context to fit within token limit with detailed strategy
        
        Args:
            context: Context to optimize
            max_tokens: Maximum allowed tokens
            preserve_recent: Number of recent messages to always preserve
            
        Returns:
            Tuple[ConversationContext, Dict[str, Any]]: Optimized context and optimization info
        """
        optimization_info = {
            'original_tokens': 0,
            'final_tokens': 0,
            'strategies_applied': [],
            'removed_components': {
                'messages': 0,
                'book_chunks': 0,
                'summary_created': False
            }
        }
        
        # Get initial token count
        original_tokens, breakdown = self.count_context_tokens(context)
        optimization_info['original_tokens'] = original_tokens
        
        if original_tokens <= max_tokens:
            optimization_info['final_tokens'] = original_tokens
            return context, optimization_info
        
        logger.info(f"Optimizing context from {original_tokens} to {max_tokens} tokens")
        
        # Strategy 1: Remove least relevant book chunks
        while (original_tokens > max_tokens and 
               len(context.book_context) > 1):
            
            removed_chunk = context.book_context.pop()
            removed_tokens = self.count_tokens(removed_chunk)
            original_tokens -= removed_tokens
            
            optimization_info['removed_components']['book_chunks'] += 1
            
            if 'reduce_book_context' not in optimization_info['strategies_applied']:
                optimization_info['strategies_applied'].append('reduce_book_context')
        
        # Strategy 2: Remove older messages (preserve recent ones)
        messages_to_preserve = min(preserve_recent, len(context.recent_messages))
        
        while (original_tokens > max_tokens and 
               len(context.recent_messages) > messages_to_preserve):
            
            removed_message = context.recent_messages.pop(0)
            removed_tokens = self.count_message_tokens(removed_message)
            original_tokens -= removed_tokens
            
            # Update participant states
            if removed_message.user_id in context.participant_states:
                state = context.participant_states[removed_message.user_id]
                state.message_count = max(0, state.message_count - 1)
            
            optimization_info['removed_components']['messages'] += 1
            
            if 'remove_old_messages' not in optimization_info['strategies_applied']:
                optimization_info['strategies_applied'].append('remove_old_messages')
        
        # Strategy 3: Create summary if still too large
        if (original_tokens > max_tokens and 
            len(context.recent_messages) > messages_to_preserve and
            not context.conversation_summary):
            
            # Summarize older messages
            messages_to_summarize = context.recent_messages[:-messages_to_preserve]
            
            if messages_to_summarize:
                # Create a simple summary (in production, use LLM)
                summary = self._create_emergency_summary(messages_to_summarize)
                context.conversation_summary = summary
                
                # Keep only recent messages
                context.recent_messages = context.recent_messages[-messages_to_preserve:]
                
                # Recalculate tokens
                original_tokens, _ = self.count_context_tokens(context)
                
                optimization_info['removed_components']['summary_created'] = True
                optimization_info['strategies_applied'].append('create_summary')
        
        # Strategy 4: Aggressive reduction if still too large
        if original_tokens > max_tokens:
            # Remove all but essential components
            if len(context.book_context) > 0:
                context.book_context = context.book_context[:1]  # Keep only most relevant
                optimization_info['strategies_applied'].append('aggressive_book_reduction')
            
            if len(context.recent_messages) > 1:
                context.recent_messages = context.recent_messages[-1:]  # Keep only last message
                optimization_info['strategies_applied'].append('aggressive_message_reduction')
        
        # Final token count
        final_tokens, _ = self.count_context_tokens(context)
        optimization_info['final_tokens'] = final_tokens
        
        logger.info(f"Context optimized: {original_tokens} → {final_tokens} tokens "
                   f"(strategies: {optimization_info['strategies_applied']})")
        
        return context, optimization_info
    
    def _create_emergency_summary(self, messages: List[ChatMessage]) -> str:
        """
        Create emergency summary when LLM is not available
        
        Args:
            messages: Messages to summarize
            
        Returns:
            str: Emergency summary
        """
        if not messages:
            return "대화 내용 없음"
        
        # Extract key information
        participants = list(set(msg.nickname for msg in messages))
        message_count = len(messages)
        
        # Extract key topics (simple keyword extraction)
        all_text = " ".join([msg.content for msg in messages])
        words = re.findall(r'[가-힣a-zA-Z]+', all_text.lower())
        
        # Count word frequency
        word_count = {}
        for word in words:
            if len(word) > 1:
                word_count[word] = word_count.get(word, 0) + 1
        
        # Get top keywords
        top_words = sorted(word_count.items(), key=lambda x: x[1], reverse=True)[:3]
        keywords = [word[0] for word in top_words]
        
        # Create summary
        summary_parts = [
            f"참여자 {len(participants)}명이 {message_count}개 메시지로 대화",
            f"주요 키워드: {', '.join(keywords)}" if keywords else "다양한 주제 논의"
        ]
        
        return ". ".join(summary_parts)
    
    def get_token_budget_recommendation(
        self, 
        total_limit: int,
        context_type: str = "discussion"
    ) -> Dict[str, int]:
        """
        Get recommended token budget allocation
        
        Args:
            total_limit: Total token limit
            context_type: Type of context (discussion, quiz, etc.)
            
        Returns:
            Dict[str, int]: Recommended token allocation
        """
        if context_type == "discussion":
            # Discussion context allocation
            return {
                'messages': int(total_limit * 0.4),      # 40% for chat messages
                'book_context': int(total_limit * 0.35), # 35% for book content
                'summary': int(total_limit * 0.15),      # 15% for summary
                'participants': int(total_limit * 0.05), # 5% for participant info
                'metadata': int(total_limit * 0.05)      # 5% for metadata
            }
        elif context_type == "quiz":
            # Quiz generation context allocation
            return {
                'book_context': int(total_limit * 0.7),  # 70% for book content
                'messages': int(total_limit * 0.2),      # 20% for recent discussion
                'summary': int(total_limit * 0.05),      # 5% for summary
                'participants': int(total_limit * 0.03), # 3% for participant info
                'metadata': int(total_limit * 0.02)      # 2% for metadata
            }
        else:
            # Generic allocation
            return {
                'messages': int(total_limit * 0.5),
                'book_context': int(total_limit * 0.3),
                'summary': int(total_limit * 0.1),
                'participants': int(total_limit * 0.05),
                'metadata': int(total_limit * 0.05)
            }
    
    def analyze_token_efficiency(
        self, 
        context: ConversationContext
    ) -> Dict[str, Any]:
        """
        Analyze token usage efficiency
        
        Args:
            context: Context to analyze
            
        Returns:
            Dict[str, Any]: Efficiency analysis
        """
        total_tokens, breakdown = self.count_context_tokens(context)
        
        analysis = {
            'total_tokens': total_tokens,
            'breakdown': breakdown,
            'efficiency_scores': {},
            'recommendations': []
        }
        
        # Calculate efficiency scores
        if total_tokens > 0:
            for component, tokens in breakdown.items():
                analysis['efficiency_scores'][component] = tokens / total_tokens
        
        # Generate recommendations
        if breakdown['book_context'] > total_tokens * 0.5:
            analysis['recommendations'].append("Consider reducing book context chunks")
        
        if breakdown['messages'] > total_tokens * 0.6:
            analysis['recommendations'].append("Consider summarizing older messages")
        
        if breakdown['summary'] > total_tokens * 0.2:
            analysis['recommendations'].append("Summary might be too detailed")
        
        return analysis