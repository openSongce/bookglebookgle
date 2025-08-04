"""
Topic Analyzer for BGBG AI Server
Advanced topic detection and change analysis for conversation context
"""

import logging
import re
import math
from datetime import datetime, timedelta
from typing import Dict, List, Optional, Any, Tuple, Set
from collections import defaultdict, Counter
from dataclasses import dataclass
from enum import Enum

from src.models.chat_history_models import ChatMessage, TopicChangeResult
from src.config.chat_config import get_analysis_config

logger = logging.getLogger(__name__)


class TopicExtractionMethod(str, Enum):
    """Methods for topic extraction"""
    KEYWORD_FREQUENCY = "keyword_frequency"
    SEMANTIC_SIMILARITY = "semantic_similarity"
    HYBRID = "hybrid"
    RULE_BASED = "rule_based"


class TopicChangeType(str, Enum):
    """Types of topic changes"""
    GRADUAL_SHIFT = "gradual_shift"      # 점진적 주제 변화
    SUDDEN_CHANGE = "sudden_change"      # 급격한 주제 변화
    RETURN_TO_PREVIOUS = "return_to_previous"  # 이전 주제로 복귀
    NEW_SUBTOPIC = "new_subtopic"        # 새로운 하위 주제
    TOPIC_DRIFT = "topic_drift"          # 주제 표류


@dataclass
class TopicSegment:
    """Represents a topic segment in conversation"""
    start_index: int
    end_index: int
    topic_keywords: List[str]
    confidence: float
    message_count: int
    participants: Set[str]
    start_time: datetime
    end_time: datetime


@dataclass
class TopicTransition:
    """Represents a transition between topics"""
    from_segment: TopicSegment
    to_segment: TopicSegment
    transition_point: int
    change_type: TopicChangeType
    confidence: float
    trigger_message: Optional[ChatMessage] = None


class TopicAnalyzer:
    """
    Advanced topic analysis and change detection
    """
    
    def __init__(self, embedding_model: Optional[Any] = None):
        """
        Initialize topic analyzer
        
        Args:
            embedding_model: Optional sentence transformer for semantic analysis
        """
        self.embedding_model = embedding_model
        self.config = get_analysis_config()
        
        # Korean book discussion specific keywords
        self.book_discussion_keywords = {
            '주인공', '등장인물', '캐릭터', '인물', '작가', '저자', '작품', '소설', '책',
            '줄거리', '스토리', '이야기', '내용', '장면', '상황', '배경', '설정',
            '주제', '메시지', '의미', '교훈', '감동', '인상', '느낌', '생각',
            '선택', '결정', '행동', '갈등', '문제', '해결', '결말', '끝',
            '관계', '사랑', '우정', '가족', '사회', '현실', '이상', '꿈'
        }
        
        # Common Korean stop words
        self.stop_words = {
            '이', '그', '저', '것', '수', '있', '하', '되', '같', '또', '더', '잘', '좀', '정말',
            '그런', '이런', '저런', '어떤', '무엇', '누구', '언제', '어디', '왜', '어떻게',
            '그래서', '하지만', '그러나', '따라서', '그리고', '또한', '즉', '예를', '들어',
            '아니', '않', '없', '못', '안', '말', '거', '게', '네', '요', '습니다', '입니다'
        }
        
        # Topic change indicators
        self.topic_change_indicators = {
            '그런데', '그건', '그리고', '또', '다른', '새로운', '이제', '이번에는',
            '한편', '반면', '그보다', '그것보다', '다시', '돌아가서', '아까',
            '그래서', '따라서', '결국', '마지막으로', '끝으로', '정리하면'
        }
        
        logger.info("TopicAnalyzer initialized")
    
    async def analyze_topic_changes(
        self,
        messages: List[ChatMessage],
        window_size: int = 5,
        method: TopicExtractionMethod = TopicExtractionMethod.HYBRID
    ) -> List[TopicTransition]:
        """
        Analyze topic changes throughout the conversation
        
        Args:
            messages: Messages to analyze
            window_size: Size of sliding window for analysis
            method: Topic extraction method to use
            
        Returns:
            List[TopicTransition]: Detected topic transitions
        """
        if len(messages) < window_size * 2:
            return []
        
        try:
            # Extract topic segments
            segments = await self._extract_topic_segments(messages, window_size, method)
            
            # Detect transitions between segments
            transitions = self._detect_transitions(segments, messages)
            
            # Classify transition types
            classified_transitions = self._classify_transitions(transitions)
            
            logger.debug(f"Detected {len(classified_transitions)} topic transitions")
            return classified_transitions
            
        except Exception as e:
            logger.error(f"Topic change analysis failed: {e}")
            return []
    
    async def detect_topic_change(
        self,
        recent_messages: List[ChatMessage],
        comparison_window: int = 3
    ) -> TopicChangeResult:
        """
        Detect if topic has changed in recent messages
        
        Args:
            recent_messages: Recent messages to analyze
            comparison_window: Number of messages to compare
            
        Returns:
            TopicChangeResult: Topic change detection result
        """
        if len(recent_messages) < comparison_window * 2:
            return TopicChangeResult(
                topic_changed=False,
                confidence=0.0
            )
        
        try:
            # Split messages into recent and previous groups
            recent_group = recent_messages[-comparison_window:]
            previous_group = recent_messages[-(comparison_window * 2):-comparison_window]
            
            # Extract topics from each group
            recent_topics = await self._extract_topics_from_messages(recent_group)
            previous_topics = await self._extract_topics_from_messages(previous_group)
            
            # Calculate topic similarity
            similarity = self._calculate_topic_similarity(previous_topics, recent_topics)
            
            # Determine if topic changed
            threshold = self.config.get('topic_change_threshold', 0.7)
            topic_changed = similarity < threshold
            
            # Find change point if topic changed
            change_point = None
            if topic_changed:
                change_point = await self._find_change_point(recent_messages, comparison_window)
            
            result = TopicChangeResult(
                topic_changed=topic_changed,
                previous_topic=previous_topics[0] if previous_topics else None,
                current_topic=recent_topics[0] if recent_topics else None,
                confidence=1.0 - similarity,
                change_point=change_point
            )
            
            logger.debug(f"Topic change detection: {topic_changed} (similarity: {similarity:.2f})")
            return result
            
        except Exception as e:
            logger.error(f"Topic change detection failed: {e}")
            return TopicChangeResult(topic_changed=False, confidence=0.0)
    
    async def _extract_topic_segments(
        self,
        messages: List[ChatMessage],
        window_size: int,
        method: TopicExtractionMethod
    ) -> List[TopicSegment]:
        """
        Extract topic segments from conversation
        
        Args:
            messages: Messages to analyze
            window_size: Window size for segmentation
            method: Extraction method
            
        Returns:
            List[TopicSegment]: Topic segments
        """
        segments = []
        
        for i in range(0, len(messages) - window_size + 1, window_size // 2):
            window_messages = messages[i:i + window_size]
            
            # Extract topics for this window
            if method == TopicExtractionMethod.SEMANTIC_SIMILARITY and self.embedding_model:
                topics = await self._extract_topics_semantic(window_messages)
            elif method == TopicExtractionMethod.RULE_BASED:
                topics = self._extract_topics_rule_based(window_messages)
            elif method == TopicExtractionMethod.HYBRID:
                topics = await self._extract_topics_hybrid(window_messages)
            else:  # KEYWORD_FREQUENCY
                topics = self._extract_topics_frequency(window_messages)
            
            if topics:
                # Calculate confidence based on topic coherence
                confidence = self._calculate_segment_confidence(window_messages, topics)
                
                segment = TopicSegment(
                    start_index=i,
                    end_index=min(i + window_size - 1, len(messages) - 1),
                    topic_keywords=topics,
                    confidence=confidence,
                    message_count=len(window_messages),
                    participants=set(msg.user_id for msg in window_messages),
                    start_time=window_messages[0].timestamp,
                    end_time=window_messages[-1].timestamp
                )
                
                segments.append(segment)
        
        return segments
    
    async def _extract_topics_from_messages(self, messages: List[ChatMessage]) -> List[str]:
        """
        Extract topics from a group of messages
        
        Args:
            messages: Messages to extract topics from
            
        Returns:
            List[str]: Extracted topics
        """
        if not messages:
            return []
        
        # Combine all message content
        combined_text = " ".join([msg.content for msg in messages])
        
        # Use frequency-based extraction as default
        return self._extract_topics_from_text(combined_text)
    
    def _extract_topics_from_text(self, text: str) -> List[str]:
        """
        Extract topics from text using frequency analysis
        
        Args:
            text: Text to analyze
            
        Returns:
            List[str]: Extracted topics
        """
        # Tokenize and clean
        words = re.findall(r'[가-힣a-zA-Z]+', text.lower())
        
        # Filter words
        filtered_words = [
            word for word in words
            if len(word) > 1 and word not in self.stop_words
        ]
        
        # Count frequencies
        word_freq = Counter(filtered_words)
        
        # Boost book discussion keywords
        for word in word_freq:
            if word in self.book_discussion_keywords:
                word_freq[word] *= 2
        
        # Get top topics
        top_topics = [word for word, count in word_freq.most_common(5)]
        return top_topics
    
    def _extract_topics_frequency(self, messages: List[ChatMessage]) -> List[str]:
        """
        Extract topics using frequency analysis
        
        Args:
            messages: Messages to analyze
            
        Returns:
            List[str]: Extracted topics
        """
        combined_text = " ".join([msg.content for msg in messages])
        return self._extract_topics_from_text(combined_text)
    
    async def _extract_topics_semantic(self, messages: List[ChatMessage]) -> List[str]:
        """
        Extract topics using semantic similarity (requires embedding model)
        
        Args:
            messages: Messages to analyze
            
        Returns:
            List[str]: Extracted topics
        """
        if not self.embedding_model:
            return self._extract_topics_frequency(messages)
        
        try:
            # Get message embeddings
            texts = [msg.content for msg in messages]
            embeddings = self.embedding_model.encode(texts)
            
            # Cluster similar messages (simplified clustering)
            # In production, use proper clustering algorithms
            
            # For now, fall back to frequency method
            return self._extract_topics_frequency(messages)
            
        except Exception as e:
            logger.warning(f"Semantic topic extraction failed: {e}")
            return self._extract_topics_frequency(messages)
    
    def _extract_topics_rule_based(self, messages: List[ChatMessage]) -> List[str]:
        """
        Extract topics using rule-based approach
        
        Args:
            messages: Messages to analyze
            
        Returns:
            List[str]: Extracted topics
        """
        topics = []
        
        for msg in messages:
            content = msg.content.lower()
            
            # Look for book discussion patterns
            if any(keyword in content for keyword in ['주인공', '등장인물', '캐릭터']):
                topics.append('인물분석')
            
            if any(keyword in content for keyword in ['줄거리', '스토리', '내용']):
                topics.append('줄거리')
            
            if any(keyword in content for keyword in ['주제', '메시지', '의미']):
                topics.append('주제의식')
            
            if any(keyword in content for keyword in ['작가', '저자', '글쓰기']):
                topics.append('작가론')
            
            if any(keyword in content for keyword in ['감동', '인상', '느낌']):
                topics.append('감상')
            
            # Look for question patterns
            if '?' in content or '？' in content:
                topics.append('질문')
        
        # Remove duplicates and return top topics
        unique_topics = list(set(topics))
        return unique_topics[:5]
    
    async def _extract_topics_hybrid(self, messages: List[ChatMessage]) -> List[str]:
        """
        Extract topics using hybrid approach
        
        Args:
            messages: Messages to analyze
            
        Returns:
            List[str]: Extracted topics
        """
        # Combine frequency and rule-based approaches
        freq_topics = self._extract_topics_frequency(messages)
        rule_topics = self._extract_topics_rule_based(messages)
        
        # Merge and prioritize
        all_topics = freq_topics + rule_topics
        topic_scores = Counter(all_topics)
        
        # Get top topics
        top_topics = [topic for topic, score in topic_scores.most_common(5)]
        return top_topics
    
    def _calculate_topic_similarity(self, topics1: List[str], topics2: List[str]) -> float:
        """
        Calculate similarity between two topic lists
        
        Args:
            topics1: First topic list
            topics2: Second topic list
            
        Returns:
            float: Similarity score (0.0 to 1.0)
        """
        if not topics1 and not topics2:
            return 1.0
        
        if not topics1 or not topics2:
            return 0.0
        
        # Convert to sets for Jaccard similarity
        set1 = set(topics1)
        set2 = set(topics2)
        
        intersection = len(set1.intersection(set2))
        union = len(set1.union(set2))
        
        return intersection / union if union > 0 else 0.0
    
    def _calculate_segment_confidence(
        self,
        messages: List[ChatMessage],
        topics: List[str]
    ) -> float:
        """
        Calculate confidence score for a topic segment
        
        Args:
            messages: Messages in segment
            topics: Extracted topics
            
        Returns:
            float: Confidence score
        """
        if not topics or not messages:
            return 0.0
        
        # Count topic mentions in messages
        topic_mentions = 0
        total_words = 0
        
        for msg in messages:
            words = re.findall(r'[가-힣a-zA-Z]+', msg.content.lower())
            total_words += len(words)
            
            for topic in topics:
                if topic in msg.content.lower():
                    topic_mentions += 1
        
        # Calculate confidence based on topic coherence
        if total_words == 0:
            return 0.0
        
        coherence = topic_mentions / len(messages)  # Average mentions per message
        consistency = len(set(msg.user_id for msg in messages)) / len(messages)  # Participant diversity
        
        confidence = (coherence * 0.7 + consistency * 0.3)
        return min(1.0, confidence)
    
    def _detect_transitions(
        self,
        segments: List[TopicSegment],
        messages: List[ChatMessage]
    ) -> List[TopicTransition]:
        """
        Detect transitions between topic segments
        
        Args:
            segments: Topic segments
            messages: Original messages
            
        Returns:
            List[TopicTransition]: Detected transitions
        """
        transitions = []
        
        for i in range(len(segments) - 1):
            current_segment = segments[i]
            next_segment = segments[i + 1]
            
            # Calculate topic similarity between segments
            similarity = self._calculate_topic_similarity(
                current_segment.topic_keywords,
                next_segment.topic_keywords
            )
            
            # If similarity is low, it's a potential transition
            if similarity < 0.5:
                transition_point = next_segment.start_index
                trigger_message = messages[transition_point] if transition_point < len(messages) else None
                
                transition = TopicTransition(
                    from_segment=current_segment,
                    to_segment=next_segment,
                    transition_point=transition_point,
                    change_type=TopicChangeType.SUDDEN_CHANGE,  # Will be refined later
                    confidence=1.0 - similarity,
                    trigger_message=trigger_message
                )
                
                transitions.append(transition)
        
        return transitions
    
    def _classify_transitions(self, transitions: List[TopicTransition]) -> List[TopicTransition]:
        """
        Classify the type of each transition
        
        Args:
            transitions: Transitions to classify
            
        Returns:
            List[TopicTransition]: Classified transitions
        """
        for transition in transitions:
            # Analyze transition characteristics
            from_topics = set(transition.from_segment.topic_keywords)
            to_topics = set(transition.to_segment.topic_keywords)
            
            # Check for return to previous topic
            # (This would require tracking topic history)
            
            # Check for gradual vs sudden change
            if transition.confidence > 0.8:
                transition.change_type = TopicChangeType.SUDDEN_CHANGE
            elif transition.confidence > 0.5:
                transition.change_type = TopicChangeType.GRADUAL_SHIFT
            else:
                transition.change_type = TopicChangeType.NEW_SUBTOPIC
            
            # Check for topic drift (low confidence in both segments)
            if (transition.from_segment.confidence < 0.3 and 
                transition.to_segment.confidence < 0.3):
                transition.change_type = TopicChangeType.TOPIC_DRIFT
        
        return transitions
    
    async def _find_change_point(
        self,
        messages: List[ChatMessage],
        window_size: int
    ) -> Optional[datetime]:
        """
        Find the exact point where topic changed
        
        Args:
            messages: Messages to analyze
            window_size: Window size for analysis
            
        Returns:
            Optional[datetime]: Change point timestamp
        """
        if len(messages) < window_size * 2:
            return None
        
        # Look for topic change indicators in recent messages
        recent_messages = messages[-window_size:]
        
        for msg in reversed(recent_messages):
            content = msg.content.lower()
            
            # Check for explicit topic change indicators
            if any(indicator in content for indicator in self.topic_change_indicators):
                return msg.timestamp
        
        # If no explicit indicator, return timestamp of middle message
        middle_index = len(messages) - window_size // 2
        return messages[middle_index].timestamp if middle_index < len(messages) else None
    
    def get_topic_summary(self, segments: List[TopicSegment]) -> Dict[str, Any]:
        """
        Generate summary of topic analysis
        
        Args:
            segments: Topic segments to summarize
            
        Returns:
            Dict[str, Any]: Topic summary
        """
        if not segments:
            return {
                'total_segments': 0,
                'dominant_topics': [],
                'topic_diversity': 0.0,
                'average_segment_length': 0.0
            }
        
        # Collect all topics
        all_topics = []
        for segment in segments:
            all_topics.extend(segment.topic_keywords)
        
        # Calculate statistics
        topic_counts = Counter(all_topics)
        dominant_topics = [topic for topic, count in topic_counts.most_common(5)]
        
        total_messages = sum(segment.message_count for segment in segments)
        avg_segment_length = total_messages / len(segments)
        
        # Topic diversity (number of unique topics / total topics)
        unique_topics = len(set(all_topics))
        topic_diversity = unique_topics / len(all_topics) if all_topics else 0.0
        
        return {
            'total_segments': len(segments),
            'dominant_topics': dominant_topics,
            'topic_diversity': topic_diversity,
            'average_segment_length': avg_segment_length,
            'total_messages_analyzed': total_messages,
            'unique_topics_count': unique_topics
        }