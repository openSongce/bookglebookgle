"""
Advanced Summarizer for BGBG AI Server
Provides intelligent conversation summarization with multiple strategies
"""

import logging
import re
from datetime import datetime, timedelta
from typing import Dict, List, Optional, Any, Tuple
from enum import Enum

from src.models.chat_history_models import ChatMessage, ConversationContext

logger = logging.getLogger(__name__)


class SummaryType(str, Enum):
    """Types of summaries that can be generated"""
    BRIEF = "brief"           # Short overview
    DETAILED = "detailed"     # Comprehensive summary
    TOPICAL = "topical"       # Topic-focused summary
    PARTICIPANT = "participant"  # Participant-focused summary
    TIMELINE = "timeline"     # Chronological summary


class SummaryStrategy(str, Enum):
    """Summarization strategies"""
    EXTRACTIVE = "extractive"    # Extract key sentences
    ABSTRACTIVE = "abstractive"  # Generate new summary text
    HYBRID = "hybrid"           # Combination approach
    TEMPLATE = "template"       # Template-based summary


class AdvancedSummarizer:
    """
    Advanced conversation summarizer with multiple strategies
    """
    
    def __init__(self, llm_client: Optional[Any] = None):
        """
        Initialize advanced summarizer
        
        Args:
            llm_client: Optional LLM client for abstractive summarization
        """
        self.llm_client = llm_client
        
        # Korean stop words for better processing
        self.korean_stop_words = {
            '이', '그', '저', '것', '수', '있', '하', '되', '같', '또', '더', '잘', '좀', '정말',
            '그런', '이런', '저런', '어떤', '무엇', '누구', '언제', '어디', '왜', '어떻게',
            '그래서', '하지만', '그러나', '따라서', '그리고', '또한', '즉', '예를 들어'
        }
        
        logger.info("AdvancedSummarizer initialized")
    
    async def summarize_conversation(
        self,
        messages: List[ChatMessage],
        summary_type: SummaryType = SummaryType.BRIEF,
        strategy: SummaryStrategy = SummaryStrategy.HYBRID,
        max_tokens: int = 200,
        context: Optional[str] = None
    ) -> Dict[str, Any]:
        """
        Generate comprehensive conversation summary
        
        Args:
            messages: Messages to summarize
            summary_type: Type of summary to generate
            strategy: Summarization strategy to use
            max_tokens: Maximum tokens for summary
            context: Additional context (book content, etc.)
            
        Returns:
            Dict[str, Any]: Summary result with metadata
        """
        if not messages:
            return {
                'summary': "대화 내용 없음",
                'type': summary_type,
                'strategy': strategy,
                'metadata': {'message_count': 0}
            }
        
        try:
            # Analyze conversation first
            analysis = self._analyze_conversation(messages)
            
            # Generate summary based on type and strategy
            if strategy == SummaryStrategy.ABSTRACTIVE and self.llm_client:
                summary = await self._generate_abstractive_summary(
                    messages, summary_type, max_tokens, context, analysis
                )
            elif strategy == SummaryStrategy.EXTRACTIVE:
                summary = self._generate_extractive_summary(
                    messages, summary_type, max_tokens, analysis
                )
            elif strategy == SummaryStrategy.TEMPLATE:
                summary = self._generate_template_summary(
                    messages, summary_type, analysis
                )
            else:  # HYBRID or fallback
                summary = await self._generate_hybrid_summary(
                    messages, summary_type, max_tokens, context, analysis
                )
            
            return {
                'summary': summary,
                'type': summary_type,
                'strategy': strategy,
                'metadata': {
                    'message_count': len(messages),
                    'participant_count': analysis['participant_count'],
                    'time_span': analysis['time_span_minutes'],
                    'key_topics': analysis['key_topics'][:3],
                    'dominant_participants': analysis['dominant_participants'][:2]
                }
            }
            
        except Exception as e:
            logger.error(f"Summarization failed: {e}")
            # Fallback to simple summary
            return {
                'summary': self._generate_fallback_summary(messages),
                'type': summary_type,
                'strategy': 'fallback',
                'metadata': {'error': str(e)}
            }
    
    def _analyze_conversation(self, messages: List[ChatMessage]) -> Dict[str, Any]:
        """
        Analyze conversation to extract key information
        
        Args:
            messages: Messages to analyze
            
        Returns:
            Dict[str, Any]: Analysis results
        """
        if not messages:
            return {}
        
        # Basic statistics
        participants = {}
        all_content = []
        
        for msg in messages:
            if msg.user_id not in participants:
                participants[msg.user_id] = {
                    'nickname': msg.nickname,
                    'message_count': 0,
                    'total_length': 0,
                    'questions': 0
                }
            
            participants[msg.user_id]['message_count'] += 1
            participants[msg.user_id]['total_length'] += len(msg.content)
            
            if '?' in msg.content or '？' in msg.content:
                participants[msg.user_id]['questions'] += 1
            
            all_content.append(msg.content)
        
        # Time span
        time_span = (messages[-1].timestamp - messages[0].timestamp).total_seconds() / 60
        
        # Extract key topics
        key_topics = self._extract_key_topics(all_content)
        
        # Find dominant participants
        dominant_participants = sorted(
            participants.items(),
            key=lambda x: x[1]['message_count'],
            reverse=True
        )
        
        return {
            'participant_count': len(participants),
            'participants': participants,
            'time_span_minutes': time_span,
            'key_topics': key_topics,
            'dominant_participants': [p[1]['nickname'] for p in dominant_participants],
            'total_messages': len(messages),
            'avg_message_length': sum(len(msg.content) for msg in messages) / len(messages)
        }
    
    def _extract_key_topics(self, content_list: List[str]) -> List[str]:
        """
        Extract key topics from conversation content
        
        Args:
            content_list: List of message contents
            
        Returns:
            List[str]: Key topics
        """
        # Combine all content
        all_text = " ".join(content_list).lower()
        
        # Extract meaningful words (Korean and English)
        words = re.findall(r'[가-힣a-zA-Z]+', all_text)
        
        # Filter out stop words and short words
        filtered_words = [
            word for word in words 
            if len(word) > 1 and word not in self.korean_stop_words
        ]
        
        # Count word frequency
        word_count = {}
        for word in filtered_words:
            word_count[word] = word_count.get(word, 0) + 1
        
        # Get top topics
        top_topics = sorted(word_count.items(), key=lambda x: x[1], reverse=True)
        return [topic[0] for topic in top_topics[:10]]
    
    async def _generate_abstractive_summary(
        self,
        messages: List[ChatMessage],
        summary_type: SummaryType,
        max_tokens: int,
        context: Optional[str],
        analysis: Dict[str, Any]
    ) -> str:
        """
        Generate abstractive summary using LLM
        
        Args:
            messages: Messages to summarize
            summary_type: Type of summary
            max_tokens: Maximum tokens
            context: Additional context
            analysis: Conversation analysis
            
        Returns:
            str: Generated summary
        """
        # Format messages for LLM
        conversation_text = self._format_messages_for_llm(messages)
        
        # Create type-specific prompt
        prompt = self._create_summary_prompt(
            conversation_text, summary_type, context, analysis, max_tokens
        )
        
        try:
            summary = await self.llm_client.generate_completion(
                prompt=prompt,
                max_tokens=max_tokens,
                temperature=0.3
            )
            return summary.strip()
            
        except Exception as e:
            logger.warning(f"LLM summarization failed: {e}")
            return self._generate_template_summary(messages, summary_type, analysis)
    
    def _generate_extractive_summary(
        self,
        messages: List[ChatMessage],
        summary_type: SummaryType,
        max_tokens: int,
        analysis: Dict[str, Any]
    ) -> str:
        """
        Generate extractive summary by selecting key sentences
        
        Args:
            messages: Messages to summarize
            summary_type: Type of summary
            max_tokens: Maximum tokens
            analysis: Conversation analysis
            
        Returns:
            str: Extractive summary
        """
        # Score messages based on various factors
        scored_messages = []
        
        for msg in messages:
            score = 0
            
            # Length factor (medium length preferred)
            length_score = min(len(msg.content) / 50, 1.0)
            if length_score > 0.5:
                score += length_score
            
            # Question factor (questions are important)
            if '?' in msg.content or '？' in msg.content:
                score += 0.5
            
            # Topic relevance (contains key topics)
            for topic in analysis.get('key_topics', [])[:5]:
                if topic in msg.content.lower():
                    score += 0.3
            
            # Position factor (first and last messages are important)
            position = messages.index(msg)
            if position == 0 or position == len(messages) - 1:
                score += 0.3
            
            scored_messages.append((msg, score))
        
        # Sort by score and select top messages
        scored_messages.sort(key=lambda x: x[1], reverse=True)
        
        # Select messages within token limit
        selected_messages = []
        current_tokens = 0
        
        for msg, score in scored_messages:
            msg_tokens = len(msg.content) // 4  # Rough token estimate
            if current_tokens + msg_tokens <= max_tokens:
                selected_messages.append(msg)
                current_tokens += msg_tokens
            else:
                break
        
        # Sort selected messages chronologically
        selected_messages.sort(key=lambda x: x.timestamp)
        
        # Format as summary
        if summary_type == SummaryType.TIMELINE:
            return self._format_timeline_summary(selected_messages)
        else:
            return self._format_extractive_summary(selected_messages, analysis)
    
    def _generate_template_summary(
        self,
        messages: List[ChatMessage],
        summary_type: SummaryType,
        analysis: Dict[str, Any]
    ) -> str:
        """
        Generate template-based summary
        
        Args:
            messages: Messages to summarize
            summary_type: Type of summary
            analysis: Conversation analysis
            
        Returns:
            str: Template-based summary
        """
        participants = analysis.get('dominant_participants', [])[:3]
        topics = analysis.get('key_topics', [])[:3]
        message_count = analysis.get('total_messages', 0)
        time_span = analysis.get('time_span_minutes', 0)
        
        if summary_type == SummaryType.BRIEF:
            return (f"{len(participants)}명의 참여자가 {message_count}개 메시지로 "
                   f"{', '.join(topics[:2])} 등에 대해 {int(time_span)}분간 토론했습니다.")
        
        elif summary_type == SummaryType.PARTICIPANT:
            participant_info = []
            for participant in participants:
                participant_info.append(f"{participant}")
            return f"주요 참여자: {', '.join(participant_info)}. 총 {message_count}개 메시지 교환."
        
        elif summary_type == SummaryType.TOPICAL:
            return f"주요 논의 주제: {', '.join(topics)}. {message_count}개 메시지에서 다양한 관점 제시."
        
        else:  # DETAILED or default
            return (f"{len(participants)}명이 참여한 {int(time_span)}분간의 토론에서 "
                   f"{', '.join(topics)} 등의 주제로 {message_count}개 메시지가 교환되었습니다. "
                   f"주요 참여자: {', '.join(participants[:2])}.")
    
    async def _generate_hybrid_summary(
        self,
        messages: List[ChatMessage],
        summary_type: SummaryType,
        max_tokens: int,
        context: Optional[str],
        analysis: Dict[str, Any]
    ) -> str:
        """
        Generate hybrid summary combining multiple approaches
        
        Args:
            messages: Messages to summarize
            summary_type: Type of summary
            max_tokens: Maximum tokens
            context: Additional context
            analysis: Conversation analysis
            
        Returns:
            str: Hybrid summary
        """
        # Try abstractive first if LLM is available
        if self.llm_client:
            try:
                abstractive = await self._generate_abstractive_summary(
                    messages, summary_type, max_tokens // 2, context, analysis
                )
                
                # Combine with template summary for structure
                template = self._generate_template_summary(messages, summary_type, analysis)
                
                return f"{abstractive} {template}"
                
            except Exception as e:
                logger.warning(f"Hybrid abstractive failed: {e}")
        
        # Fallback to extractive + template
        extractive = self._generate_extractive_summary(
            messages, summary_type, max_tokens // 2, analysis
        )
        template = self._generate_template_summary(messages, summary_type, analysis)
        
        return f"{template} 주요 내용: {extractive}"
    
    def _create_summary_prompt(
        self,
        conversation_text: str,
        summary_type: SummaryType,
        context: Optional[str],
        analysis: Dict[str, Any],
        max_tokens: int
    ) -> str:
        """
        Create LLM prompt for summarization
        
        Args:
            conversation_text: Formatted conversation
            summary_type: Type of summary
            context: Additional context
            analysis: Conversation analysis
            max_tokens: Maximum tokens
            
        Returns:
            str: LLM prompt
        """
        base_prompt = f"""다음은 독서 모임에서 진행된 토론 내용입니다.

대화 내용:
{conversation_text}"""
        
        if context:
            base_prompt += f"\n\n관련 도서 내용:\n{context}"
        
        if summary_type == SummaryType.BRIEF:
            instruction = f"이 토론을 {max_tokens//4}자 이내로 간단히 요약해주세요."
        elif summary_type == SummaryType.DETAILED:
            instruction = f"이 토론의 주요 논점과 참여자들의 의견을 {max_tokens//4}자 이내로 상세히 요약해주세요."
        elif summary_type == SummaryType.TOPICAL:
            instruction = f"이 토론에서 다뤄진 주요 주제들을 중심으로 {max_tokens//4}자 이내로 요약해주세요."
        elif summary_type == SummaryType.PARTICIPANT:
            instruction = f"각 참여자의 주요 의견을 중심으로 {max_tokens//4}자 이내로 요약해주세요."
        else:  # TIMELINE
            instruction = f"토론의 흐름을 시간순으로 {max_tokens//4}자 이내로 요약해주세요."
        
        return f"{base_prompt}\n\n{instruction}"
    
    def _format_messages_for_llm(self, messages: List[ChatMessage]) -> str:
        """Format messages for LLM processing"""
        formatted = []
        for msg in messages:
            time_str = msg.timestamp.strftime("%H:%M")
            formatted.append(f"[{time_str}] {msg.nickname}: {msg.content}")
        return "\n".join(formatted)
    
    def _format_timeline_summary(self, messages: List[ChatMessage]) -> str:
        """Format messages as timeline summary"""
        if not messages:
            return "타임라인 정보 없음"
        
        timeline = []
        for msg in messages:
            time_str = msg.timestamp.strftime("%H:%M")
            timeline.append(f"{time_str} {msg.nickname}: {msg.content[:50]}...")
        
        return " → ".join(timeline)
    
    def _format_extractive_summary(
        self, 
        messages: List[ChatMessage], 
        analysis: Dict[str, Any]
    ) -> str:
        """Format extractive summary"""
        if not messages:
            return "추출된 내용 없음"
        
        key_points = []
        for msg in messages:
            key_points.append(f"{msg.nickname}: {msg.content}")
        
        return " | ".join(key_points)
    
    def _generate_fallback_summary(self, messages: List[ChatMessage]) -> str:
        """Generate simple fallback summary"""
        if not messages:
            return "대화 내용 없음"
        
        participants = list(set(msg.nickname for msg in messages))
        return f"{len(participants)}명이 {len(messages)}개 메시지로 토론했습니다."