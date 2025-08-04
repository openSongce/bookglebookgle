"""
Conversation Analyzer for BGBG AI Server
Analyzes conversation patterns, participant engagement, and suggests AI interventions
"""

import logging
import statistics
from datetime import datetime, timedelta
from typing import Dict, List, Optional, Any, Tuple, Set
from collections import defaultdict, Counter
from dataclasses import dataclass
from enum import Enum

from src.models.chat_history_models import (
    ChatMessage, 
    ParticipationAnalysis, 
    ConversationPatterns,
    InterventionSuggestion,
    InterventionType,
    AnalysisError
)
from src.models.chat_interfaces import ConversationAnalyzerInterface
from src.config.chat_config import get_analysis_config, get_intervention_config
from src.services.pattern_detector import AdvancedPatternDetector

logger = logging.getLogger(__name__)


class EngagementLevel(str, Enum):
    """Participant engagement levels"""
    VERY_HIGH = "very_high"      # 0.8+
    HIGH = "high"                # 0.6-0.8
    MEDIUM = "medium"            # 0.4-0.6
    LOW = "low"                  # 0.2-0.4
    VERY_LOW = "very_low"        # 0.0-0.2


@dataclass
class ParticipantMetrics:
    """Detailed metrics for a participant"""
    user_id: str
    nickname: str
    message_count: int
    total_characters: int
    avg_message_length: float
    question_count: int
    response_count: int
    last_activity: datetime
    engagement_score: float
    participation_ratio: float
    response_time_avg: float
    topic_contributions: Dict[str, int]
    interaction_partners: Set[str]
    sentiment_trend: str
    communication_style: str


class ConversationAnalyzer(ConversationAnalyzerInterface):
    """
    Advanced conversation analysis for participant engagement and patterns
    """
    
    def __init__(self, chat_history_manager: Optional[Any] = None):
        """
        Initialize conversation analyzer
        
        Args:
            chat_history_manager: Chat history manager for data access
        """
        self.chat_history = chat_history_manager
        self.analysis_config = get_analysis_config()
        self.intervention_config = get_intervention_config()
        
        # Thresholds for analysis
        self.ENGAGEMENT_THRESHOLDS = {
            EngagementLevel.VERY_HIGH: 0.8,
            EngagementLevel.HIGH: 0.6,
            EngagementLevel.MEDIUM: 0.4,
            EngagementLevel.LOW: 0.2,
            EngagementLevel.VERY_LOW: 0.0
        }
        
        # Advanced pattern detector
        self.pattern_detector = AdvancedPatternDetector()
        
        # Intervention cooldown tracking
        self.last_interventions: Dict[str, datetime] = {}
        
        logger.info("ConversationAnalyzer initialized with AdvancedPatternDetector")
    
    async def analyze_participation(self, session_id: str) -> ParticipationAnalysis:
        """
        Analyze participant engagement and activity patterns
        
        Args:
            session_id: Discussion session identifier
            
        Returns:
            ParticipationAnalysis: Analysis of participant engagement
            
        Raises:
            AnalysisError: If participation analysis fails
        """
        try:
            if not self.chat_history:
                raise AnalysisError("Chat history manager not available")
            
            # Get recent messages for analysis
            messages = await self.chat_history.get_recent_messages(session_id, limit=50)
            
            if not messages:
                return ParticipationAnalysis(
                    session_id=session_id,
                    total_participants=0,
                    active_participants=0,
                    inactive_participants=[],
                    dominant_participants=[],
                    engagement_distribution={},
                    last_activity_times={}
                )
            
            # Calculate participant metrics
            participant_metrics = self._calculate_participant_metrics(messages)
            
            # Analyze participation patterns
            analysis = self._analyze_participation_patterns(participant_metrics, messages)
            
            logger.debug(f"Participation analysis completed for session {session_id}")
            return analysis
            
        except Exception as e:
            logger.error(f"Participation analysis failed: {e}")
            raise AnalysisError(f"Participation analysis failed: {e}")
    
    def _calculate_participant_metrics(self, messages: List[ChatMessage]) -> Dict[str, ParticipantMetrics]:
        """Calculate detailed metrics for each participant"""
        participant_data = defaultdict(lambda: {
            'messages': [],
            'total_chars': 0,
            'questions': 0,
            'responses': 0,
            'topics': defaultdict(int),
            'interactions': set(),
            'response_times': []
        })
        
        # Collect raw data
        for i, msg in enumerate(messages):
            data = participant_data[msg.user_id]
            data['messages'].append(msg)
            data['total_chars'] += len(msg.content)
            data['nickname'] = msg.nickname
            
            # Count questions
            if '?' in msg.content or '？' in msg.content:
                data['questions'] += 1
            
            # Detect responses
            if i > 0:
                prev_msg = messages[i-1]
                time_diff = (msg.timestamp - prev_msg.timestamp).total_seconds() / 60
                
                if time_diff <= 5 and prev_msg.user_id != msg.user_id:
                    data['responses'] += 1
                    data['interactions'].add(prev_msg.user_id)
                    data['response_times'].append(time_diff)
        
        # Calculate metrics
        metrics = {}
        total_messages = len(messages)
        
        for user_id, data in participant_data.items():
            user_messages = data['messages']
            message_count = len(user_messages)
            
            # Calculate engagement score
            engagement_score = self._calculate_engagement_score(
                user_messages, message_count, total_messages, data
            )
            
            metrics[user_id] = ParticipantMetrics(
                user_id=user_id,
                nickname=data['nickname'],
                message_count=message_count,
                total_characters=data['total_chars'],
                avg_message_length=data['total_chars'] / message_count if message_count > 0 else 0,
                question_count=data['questions'],
                response_count=data['responses'],
                last_activity=user_messages[-1].timestamp if user_messages else datetime.min,
                engagement_score=engagement_score,
                participation_ratio=message_count / total_messages,
                response_time_avg=statistics.mean(data['response_times']) if data['response_times'] else 0,
                topic_contributions=dict(data['topics']),
                interaction_partners=data['interactions'],
                sentiment_trend="neutral",
                communication_style="casual"
            )
        
        return metrics 
   
    def _calculate_engagement_score(
        self, 
        user_messages: List[ChatMessage], 
        message_count: int, 
        total_messages: int,
        user_data: Dict[str, Any]
    ) -> float:
        """Calculate engagement score for a participant"""
        if not user_messages:
            return 0.0
        
        # Base score from message frequency
        frequency_score = min(1.0, message_count / max(1, total_messages * 0.3))
        
        # Recency score
        last_message_time = user_messages[-1].timestamp
        time_since_last = (datetime.utcnow() - last_message_time).total_seconds() / 3600
        recency_score = max(0.0, 1.0 - (time_since_last / 24))
        
        # Interaction score
        interaction_score = min(1.0, (user_data['questions'] + user_data['responses']) / max(1, message_count))
        
        # Length score
        avg_length = user_data['total_chars'] / message_count
        length_score = min(1.0, avg_length / 50)
        
        # Weighted combination
        engagement = (
            frequency_score * 0.3 +
            recency_score * 0.3 +
            interaction_score * 0.25 +
            length_score * 0.15
        )
        
        return min(1.0, engagement)
    
    def _analyze_participation_patterns(
        self, 
        metrics: Dict[str, ParticipantMetrics], 
        messages: List[ChatMessage]
    ) -> ParticipationAnalysis:
        """Analyze participation patterns and create analysis result"""
        if not metrics:
            return ParticipationAnalysis(
                session_id="unknown",
                total_participants=0,
                active_participants=0,
                inactive_participants=[],
                dominant_participants=[],
                engagement_distribution={},
                last_activity_times={}
            )
        
        # Determine activity thresholds
        inactive_threshold = datetime.utcnow() - timedelta(
            minutes=self.analysis_config.get('inactive_threshold_minutes', 30)
        )
        
        # Categorize participants
        active_participants = []
        inactive_participants = []
        engagement_distribution = {}
        last_activity_times = {}
        
        for user_id, metric in metrics.items():
            engagement_distribution[user_id] = metric.engagement_score
            last_activity_times[user_id] = metric.last_activity
            
            if metric.last_activity > inactive_threshold:
                active_participants.append(user_id)
            else:
                inactive_participants.append(user_id)
        
        # Find dominant participants
        sorted_participants = sorted(
            metrics.items(), 
            key=lambda x: x[1].engagement_score, 
            reverse=True
        )
        
        dominant_count = max(1, len(sorted_participants) // 5)
        dominant_participants = [p[0] for p in sorted_participants[:dominant_count]]
        
        # Get session ID from messages
        session_id = messages[0].session_id if messages else "unknown"
        
        return ParticipationAnalysis(
            session_id=session_id,
            total_participants=len(metrics),
            active_participants=len(active_participants),
            inactive_participants=inactive_participants,
            dominant_participants=dominant_participants,
            engagement_distribution=engagement_distribution,
            last_activity_times=last_activity_times
        )
    
    async def detect_conversation_patterns(
        self, 
        messages: List[ChatMessage]
    ) -> ConversationPatterns:
        """Detect patterns in conversation flow"""
        try:
            if not messages:
                return ConversationPatterns(
                    is_repetitive=False,
                    is_stagnant=False,
                    has_conflict=False,
                    off_topic=False,
                    dominant_topics=[],
                    sentiment_trend="neutral",
                    interaction_density=0.0
                )
            
            # Detect specific patterns
            is_repetitive = self._detect_repetitive_pattern(messages)
            is_stagnant = self._detect_stagnation(messages)
            has_conflict = self._detect_conflict_indicators(messages)
            
            # Extract dominant topics
            dominant_topics = self._extract_dominant_topics(messages)
            
            # Analyze sentiment trend
            sentiment_trend = self._analyze_sentiment_trend(messages)
            
            # Calculate interaction density
            interaction_density = self._calculate_interaction_density(messages)
            
            return ConversationPatterns(
                is_repetitive=is_repetitive,
                is_stagnant=is_stagnant,
                has_conflict=has_conflict,
                off_topic=False,  # Placeholder
                dominant_topics=dominant_topics,
                sentiment_trend=sentiment_trend,
                interaction_density=interaction_density
            )
            
        except Exception as e:
            logger.error(f"Pattern detection failed: {e}")
            raise AnalysisError(f"Pattern detection failed: {e}")
    
    def _detect_repetitive_pattern(self, messages: List[ChatMessage]) -> bool:
        """Detect if conversation has repetitive patterns"""
        if len(messages) < 6:
            return False
        
        content_similarity_count = 0
        
        for i in range(len(messages) - 2):
            current_content = messages[i].content.lower()
            
            for j in range(i + 1, min(i + 4, len(messages))):
                other_content = messages[j].content.lower()
                
                common_words = set(current_content.split()) & set(other_content.split())
                if len(common_words) > len(current_content.split()) * 0.5:
                    content_similarity_count += 1
        
        return content_similarity_count > len(messages) * 0.3
    
    def _detect_stagnation(self, messages: List[ChatMessage]) -> bool:
        """Detect if conversation has stagnated"""
        if len(messages) < 3:
            return False
        
        # Check message frequency
        start_time = messages[0].timestamp
        end_time = messages[-1].timestamp
        duration_minutes = (end_time - start_time).total_seconds() / 60
        message_frequency = len(messages) / max(1, duration_minutes)
        
        # Check interaction density
        interaction_density = self._calculate_interaction_density(messages)
        
        # Consider stagnant if low frequency and low interaction
        return message_frequency < 0.5 and interaction_density < 0.3
    
    def _detect_conflict_indicators(self, messages: List[ChatMessage]) -> bool:
        """Detect indicators of conflict in conversation"""
        conflict_keywords = [
            '아니', '틀렸', '잘못', '반대', '동의하지', '그렇지않', '아닙니다',
            '하지만', '그러나', '문제는', '이상해', '말이안돼'
        ]
        
        conflict_count = 0
        
        for msg in messages:
            content = msg.content.lower()
            
            for keyword in conflict_keywords:
                if keyword in content:
                    conflict_count += 1
                    break
            
            if content.count('!') > 2 or content.count('?') > 2:
                conflict_count += 1
        
        return conflict_count > len(messages) * 0.2
    
    def _extract_dominant_topics(self, messages: List[ChatMessage]) -> List[str]:
        """Extract dominant topics from messages"""
        all_words = []
        for msg in messages:
            words = msg.content.lower().split()
            all_words.extend(words)
        
        word_count = Counter(all_words)
        stop_words = {'이', '그', '저', '것', '수', '있', '하', '되', '같', '또', '더'}
        
        topics = [
            word for word, count in word_count.most_common(10)
            if len(word) > 1 and word not in stop_words
        ]
        
        return topics[:5]
    
    def _analyze_sentiment_trend(self, messages: List[ChatMessage]) -> str:
        """Analyze overall sentiment trend"""
        positive_keywords = ['좋', '훌륭', '멋진', '감동', '인상적', '재미있', '흥미로']
        negative_keywords = ['나쁘', '별로', '아쉽', '실망', '지루', '이상해', '문제']
        
        positive_count = 0
        negative_count = 0
        
        for msg in messages:
            content = msg.content.lower()
            
            for keyword in positive_keywords:
                if keyword in content:
                    positive_count += 1
                    break
            
            for keyword in negative_keywords:
                if keyword in content:
                    negative_count += 1
                    break
        
        if positive_count > negative_count * 1.5:
            return "positive"
        elif negative_count > positive_count * 1.5:
            return "negative"
        else:
            return "neutral"
    
    def _calculate_interaction_density(self, messages: List[ChatMessage]) -> float:
        """Calculate interaction density"""
        if len(messages) < 2:
            return 0.0
        
        interactions = 0
        total_possible = len(messages) - 1
        
        for i in range(1, len(messages)):
            current_msg = messages[i]
            prev_msg = messages[i-1]
            
            time_diff = (current_msg.timestamp - prev_msg.timestamp).total_seconds() / 60
            
            if (current_msg.user_id != prev_msg.user_id and time_diff <= 10):
                interactions += 1
        
        return interactions / total_possible
    
    async def suggest_intervention(
        self, 
        analysis: ParticipationAnalysis,
        patterns: ConversationPatterns
    ) -> InterventionSuggestion:
        """Suggest AI intervention based on analysis"""
        try:
            if not self.intervention_config.get('enable_ai_intervention', True):
                return InterventionSuggestion(
                    intervention_type=InterventionType.PROVIDE_CONTEXT,
                    priority=1,
                    target_participants=[],
                    suggested_message="AI 개입이 비활성화되어 있습니다.",
                    reasoning="AI intervention disabled",
                    confidence=0.0
                )
            
            # Check for conflict (highest priority)
            if patterns.has_conflict:
                return InterventionSuggestion(
                    intervention_type=InterventionType.MEDIATE_CONFLICT,
                    priority=5,
                    target_participants=analysis.dominant_participants[:2],
                    suggested_message="다양한 관점이 있을 수 있습니다. 서로의 의견을 존중하며 토론을 계속해보세요.",
                    reasoning="Conflict detected in conversation",
                    confidence=0.8
                )
            
            # Check for low participation
            if len(analysis.inactive_participants) > 0:
                return InterventionSuggestion(
                    intervention_type=InterventionType.ENCOURAGE_PARTICIPATION,
                    priority=4,
                    target_participants=analysis.inactive_participants,
                    suggested_message="아직 의견을 나누지 않은 분들의 생각도 궁금합니다. 어떻게 생각하시나요?",
                    reasoning=f"{len(analysis.inactive_participants)} participants are inactive",
                    confidence=0.7
                )
            
            # Check for stagnation
            if patterns.is_stagnant:
                return InterventionSuggestion(
                    intervention_type=InterventionType.TOPIC_REDIRECT,
                    priority=3,
                    target_participants=[],
                    suggested_message="새로운 관점에서 접근해볼까요? 이 부분에 대해서는 어떻게 생각하시나요?",
                    reasoning="Conversation appears stagnant",
                    confidence=0.6
                )
            
            # Default: provide context
            return InterventionSuggestion(
                intervention_type=InterventionType.PROVIDE_CONTEXT,
                priority=1,
                target_participants=[],
                suggested_message="흥미로운 토론이 진행되고 있네요. 계속해서 의견을 나누어 주세요.",
                reasoning="General encouragement",
                confidence=0.5
            )
            
        except Exception as e:
            logger.error(f"Intervention suggestion failed: {e}")
            raise AnalysisError(f"Intervention suggestion failed: {e}")
    
    async def analyze_sentiment_trend(self, messages: List[ChatMessage]) -> Dict[str, Any]:
        """Analyze sentiment trends in the conversation"""
        try:
            if not messages:
                return {
                    'overall_sentiment': 'neutral',
                    'sentiment_score': 0.0,
                    'trend': 'stable',
                    'participant_sentiments': {}
                }
            
            message_sentiments = []
            participant_sentiments = defaultdict(list)
            
            for msg in messages:
                sentiment_score = self._calculate_message_sentiment(msg.content)
                message_sentiments.append(sentiment_score)
                participant_sentiments[msg.user_id].append(sentiment_score)
            
            overall_score = statistics.mean(message_sentiments)
            overall_sentiment = self._score_to_sentiment(overall_score)
            trend = self._analyze_sentiment_trend_direction(message_sentiments)
            
            participant_avg_sentiments = {
                user_id: statistics.mean(scores)
                for user_id, scores in participant_sentiments.items()
            }
            
            return {
                'overall_sentiment': overall_sentiment,
                'sentiment_score': overall_score,
                'trend': trend,
                'participant_sentiments': participant_avg_sentiments,
                'message_count': len(messages)
            }
            
        except Exception as e:
            logger.error(f"Sentiment analysis failed: {e}")
            raise AnalysisError(f"Sentiment analysis failed: {e}")
    
    def _calculate_message_sentiment(self, content: str) -> float:
        """Calculate sentiment score for a message"""
        positive_words = [
            '좋', '훌륭', '멋진', '감동', '인상적', '재미있', '흥미로', '완벽', '최고',
            '사랑', '행복', '기쁘', '즐거', '만족', '고마', '감사', '축하'
        ]
        
        negative_words = [
            '나쁘', '별로', '아쉽', '실망', '지루', '이상해', '문제', '싫', '화나',
            '슬프', '우울', '걱정', '두려', '무서', '힘들', '어려', '복잡'
        ]
        
        content_lower = content.lower()
        
        positive_count = sum(1 for word in positive_words if word in content_lower)
        negative_count = sum(1 for word in negative_words if word in content_lower)
        
        content_words = len(content.split())
        if content_words == 0:
            return 0.0
        
        positive_ratio = positive_count / content_words
        negative_ratio = negative_count / content_words
        
        sentiment_score = positive_ratio - negative_ratio
        return max(-1.0, min(1.0, sentiment_score * 10))
    
    def _score_to_sentiment(self, score: float) -> str:
        """Convert sentiment score to category"""
        if score > 0.2:
            return 'positive'
        elif score < -0.2:
            return 'negative'
        else:
            return 'neutral'
    
    def _analyze_sentiment_trend_direction(self, scores: List[float]) -> str:
        """Analyze if sentiment is improving, declining, or stable"""
        if len(scores) < 3:
            return 'stable'
        
        mid_point = len(scores) // 2
        first_half_avg = statistics.mean(scores[:mid_point])
        second_half_avg = statistics.mean(scores[mid_point:])
        
        difference = second_half_avg - first_half_avg
        
        if difference > 0.1:
            return 'improving'
        elif difference < -0.1:
            return 'declining'
        else:
            return 'stable'
    
    async def detect_off_topic_discussion(
        self, 
        messages: List[ChatMessage],
        book_context: List[str]
    ) -> bool:
        """Detect if discussion has gone off-topic from the book"""
        try:
            if not messages or not book_context:
                return False
            
            # Extract keywords from book context
            book_keywords = set()
            for context in book_context:
                words = context.lower().split()
                book_keywords.update(word for word in words if len(word) > 2)
            
            # Extract keywords from recent messages
            message_keywords = set()
            for msg in messages[-5:]:
                words = msg.content.lower().split()
                message_keywords.update(word for word in words if len(word) > 2)
            
            # Calculate overlap
            if not book_keywords or not message_keywords:
                return False
            
            overlap = len(book_keywords.intersection(message_keywords))
            total_unique = len(book_keywords.union(message_keywords))
            
            similarity = overlap / total_unique if total_unique > 0 else 0.0
            return similarity < 0.1
            
        except Exception as e:
            logger.error(f"Off-topic detection failed: {e}")
            raise AnalysisError(f"Off-topic detection failed: {e}")
    
    async def analyze_advanced_patterns(
        self, 
        session_id: str,
        messages: Optional[List[ChatMessage]] = None
    ) -> Dict[str, Any]:
        """
        Perform advanced pattern analysis on conversation
        
        Args:
            session_id: Session identifier
            messages: Optional messages (will fetch if not provided)
            
        Returns:
            Dict[str, Any]: Advanced pattern analysis results
        """
        try:
            # Get messages if not provided
            if messages is None:
                if not self.chat_history:
                    return {"error": "No chat history manager available"}
                messages = await self.chat_history.get_recent_messages(session_id, limit=100)
            
            if not messages:
                return {"error": "No messages found for analysis"}
            
            # Perform advanced pattern analysis
            pattern_result = self.pattern_detector.analyze_conversation_patterns(messages, session_id)
            
            # Convert to dictionary for JSON serialization
            return {
                "session_id": session_id,
                "conversation_phase": pattern_result.conversation_phase,
                "interaction_pattern": pattern_result.interaction_pattern,
                "rhythm": pattern_result.rhythm,
                "engagement_trend": pattern_result.engagement_trend,
                "dominant_behaviors": pattern_result.dominant_behaviors,
                "pattern_confidence": pattern_result.pattern_confidence,
                "recommendations": pattern_result.recommendations,
                "analysis_timestamp": datetime.utcnow().isoformat()
            }
            
        except Exception as e:
            logger.error(f"Advanced pattern analysis failed: {e}")
            return {"error": f"Advanced pattern analysis failed: {e}"}
    
    def get_conversation_insights(self, session_id: str) -> Dict[str, Any]:
        """
        Get comprehensive conversation insights combining all analysis types
        
        Args:
            session_id: Session identifier
            
        Returns:
            Dict[str, Any]: Comprehensive insights
        """
        try:
            insights = {
                "session_id": session_id,
                "timestamp": datetime.utcnow().isoformat(),
                "insights": {}
            }
            
            # Get pattern analysis from cache if available
            pattern_result = self.pattern_detector.get_pattern_history(session_id)
            if pattern_result:
                insights["insights"]["patterns"] = {
                    "conversation_phase": pattern_result.conversation_phase,
                    "interaction_pattern": pattern_result.interaction_pattern,
                    "rhythm": pattern_result.rhythm,
                    "engagement_trend": pattern_result.engagement_trend,
                    "dominant_behaviors": pattern_result.dominant_behaviors,
                    "confidence": pattern_result.pattern_confidence
                }
                
                insights["insights"]["recommendations"] = pattern_result.recommendations
            
            return insights
            
        except Exception as e:
            logger.error(f"Failed to get conversation insights: {e}")
            return {"error": f"Failed to get conversation insights: {e}"}
    
    def clear_analysis_cache(self, session_id: Optional[str] = None):
        """
        Clear analysis cache for session or all sessions
        
        Args:
            session_id: Optional session ID to clear (clears all if None)
        """
        try:
            self.pattern_detector.clear_pattern_cache(session_id)
            
            if session_id:
                self.last_interventions.pop(session_id, None)
                logger.info(f"Cleared analysis cache for session {session_id}")
            else:
                self.last_interventions.clear()
                logger.info("Cleared all analysis cache")
                
        except Exception as e:
            logger.error(f"Failed to clear analysis cache: {e}")