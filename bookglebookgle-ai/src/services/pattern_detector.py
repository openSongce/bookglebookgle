"""
Advanced Pattern Detector for BGBG AI Server
Detects sophisticated conversation patterns and behavioral analysis
"""

import logging
import statistics
from datetime import datetime, timedelta
from typing import Dict, List, Optional, Any, Tuple, Set
from collections import defaultdict, Counter, deque
from dataclasses import dataclass
from enum import Enum

from src.models.chat_history_models import ChatMessage

logger = logging.getLogger(__name__)


class ConversationPhase(str, Enum):
    """Phases of conversation development"""
    OPENING = "opening"           # 대화 시작 단계
    EXPLORATION = "exploration"   # 탐색 단계
    DEEPENING = "deepening"      # 심화 단계
    SYNTHESIS = "synthesis"      # 종합 단계
    CLOSING = "closing"          # 마무리 단계


class InteractionPattern(str, Enum):
    """Types of interaction patterns"""
    PING_PONG = "ping_pong"           # 두 명이 주고받기
    ROUND_ROBIN = "round_robin"       # 순서대로 발언
    STAR = "star"                     # 한 명이 중심이 되어 대화
    MESH = "mesh"                     # 모든 사람이 서로 대화
    CHAIN = "chain"                   # 연쇄적 대화
    CLUSTER = "cluster"               # 그룹별 대화


class ConversationRhythm(str, Enum):
    """Conversation rhythm patterns"""
    STEADY = "steady"                 # 일정한 리듬
    ACCELERATING = "accelerating"     # 가속화
    DECELERATING = "decelerating"     # 감속화
    BURST = "burst"                   # 폭발적
    SPORADIC = "sporadic"             # 산발적


@dataclass
class PatternAnalysisResult:
    """Result of pattern analysis"""
    conversation_phase: ConversationPhase
    interaction_pattern: InteractionPattern
    rhythm: ConversationRhythm
    engagement_trend: str
    dominant_behaviors: List[str]
    pattern_confidence: float
    recommendations: List[str]


@dataclass
class ConversationSegment:
    """A segment of conversation for analysis"""
    start_time: datetime
    end_time: datetime
    messages: List[ChatMessage]
    participants: Set[str]
    message_frequency: float
    interaction_density: float
    topic_coherence: float


class AdvancedPatternDetector:
    """
    Advanced pattern detection for conversation analysis
    """
    
    def __init__(self):
        """Initialize pattern detector"""
        self.conversation_history: Dict[str, List[ChatMessage]] = {}
        self.pattern_cache: Dict[str, PatternAnalysisResult] = {}
        
        # Pattern detection thresholds
        self.BURST_THRESHOLD = 5  # messages per minute
        self.SILENCE_THRESHOLD = 300  # seconds
        self.INTERACTION_WINDOW = 180  # seconds for interaction analysis
        
        logger.info("AdvancedPatternDetector initialized")
    
    def analyze_conversation_patterns(
        self, 
        messages: List[ChatMessage],
        session_id: str
    ) -> PatternAnalysisResult:
        """
        Comprehensive pattern analysis of conversation
        
        Args:
            messages: Messages to analyze
            session_id: Session identifier
            
        Returns:
            PatternAnalysisResult: Comprehensive analysis result
        """
        if not messages:
            return PatternAnalysisResult(
                conversation_phase=ConversationPhase.OPENING,
                interaction_pattern=InteractionPattern.STAR,
                rhythm=ConversationRhythm.STEADY,
                engagement_trend="stable",
                dominant_behaviors=[],
                pattern_confidence=0.0,
                recommendations=[]
            )
        
        try:
            # Update conversation history
            self.conversation_history[session_id] = messages
            
            # Segment conversation for analysis
            segments = self._segment_conversation(messages)
            
            # Analyze different aspects
            phase = self._detect_conversation_phase(messages, segments)
            interaction_pattern = self._detect_interaction_pattern(messages)
            rhythm = self._detect_conversation_rhythm(messages)
            engagement_trend = self._analyze_engagement_trend(messages)
            behaviors = self._detect_dominant_behaviors(messages)
            
            # Calculate overall confidence
            confidence = self._calculate_pattern_confidence(
                messages, phase, interaction_pattern, rhythm
            )
            
            # Generate recommendations
            recommendations = self._generate_pattern_recommendations(
                phase, interaction_pattern, rhythm, engagement_trend
            )
            
            result = PatternAnalysisResult(
                conversation_phase=phase,
                interaction_pattern=interaction_pattern,
                rhythm=rhythm,
                engagement_trend=engagement_trend,
                dominant_behaviors=behaviors,
                pattern_confidence=confidence,
                recommendations=recommendations
            )
            
            # Cache result
            self.pattern_cache[session_id] = result
            
            logger.debug(f"Pattern analysis completed for session {session_id}")
            return result
            
        except Exception as e:
            logger.error(f"Pattern analysis failed: {e}")
            return PatternAnalysisResult(
                conversation_phase=ConversationPhase.EXPLORATION,
                interaction_pattern=InteractionPattern.MESH,
                rhythm=ConversationRhythm.STEADY,
                engagement_trend="stable",
                dominant_behaviors=[],
                pattern_confidence=0.0,
                recommendations=["패턴 분석 중 오류가 발생했습니다."]
            )
    
    def _segment_conversation(self, messages: List[ChatMessage]) -> List[ConversationSegment]:
        """
        Segment conversation into meaningful chunks
        
        Args:
            messages: Messages to segment
            
        Returns:
            List[ConversationSegment]: Conversation segments
        """
        if not messages:
            return []
        
        segments = []
        segment_duration = timedelta(minutes=5)  # 5-minute segments
        
        current_start = messages[0].timestamp
        current_messages = []
        
        for msg in messages:
            # Check if we need to start a new segment
            if msg.timestamp - current_start > segment_duration:
                if current_messages:
                    # Create segment from current messages
                    segment = self._create_segment(current_messages, current_start)
                    segments.append(segment)
                
                # Start new segment
                current_start = msg.timestamp
                current_messages = [msg]
            else:
                current_messages.append(msg)
        
        # Add final segment
        if current_messages:
            segment = self._create_segment(current_messages, current_start)
            segments.append(segment)
        
        return segments
    
    def _create_segment(
        self, 
        messages: List[ChatMessage], 
        start_time: datetime
    ) -> ConversationSegment:
        """
        Create a conversation segment from messages
        
        Args:
            messages: Messages in segment
            start_time: Segment start time
            
        Returns:
            ConversationSegment: Created segment
        """
        if not messages:
            return ConversationSegment(
                start_time=start_time,
                end_time=start_time,
                messages=[],
                participants=set(),
                message_frequency=0.0,
                interaction_density=0.0,
                topic_coherence=0.0
            )
        
        end_time = messages[-1].timestamp
        participants = set(msg.user_id for msg in messages)
        
        # Calculate message frequency
        duration_minutes = (end_time - start_time).total_seconds() / 60
        message_frequency = len(messages) / max(1, duration_minutes)
        
        # Calculate interaction density
        interaction_density = self._calculate_segment_interaction_density(messages)
        
        # Calculate topic coherence (simplified)
        topic_coherence = self._calculate_topic_coherence(messages)
        
        return ConversationSegment(
            start_time=start_time,
            end_time=end_time,
            messages=messages,
            participants=participants,
            message_frequency=message_frequency,
            interaction_density=interaction_density,
            topic_coherence=topic_coherence
        )
    
    def _detect_conversation_phase(
        self, 
        messages: List[ChatMessage], 
        segments: List[ConversationSegment]
    ) -> ConversationPhase:
        """
        Detect current phase of conversation
        
        Args:
            messages: All messages
            segments: Conversation segments
            
        Returns:
            ConversationPhase: Detected phase
        """
        if not messages:
            return ConversationPhase.OPENING
        
        total_duration = (messages[-1].timestamp - messages[0].timestamp).total_seconds() / 60
        message_count = len(messages)
        
        # Analyze conversation characteristics
        question_ratio = sum(1 for msg in messages if '?' in msg.content) / len(messages)
        avg_message_length = statistics.mean(len(msg.content) for msg in messages)
        
        # Phase detection logic
        if total_duration < 5:  # First 5 minutes
            return ConversationPhase.OPENING
        elif question_ratio > 0.3:  # High question ratio indicates exploration
            return ConversationPhase.EXPLORATION
        elif avg_message_length > 100:  # Long messages indicate deepening
            return ConversationPhase.DEEPENING
        elif total_duration > 30:  # After 30 minutes, likely synthesis or closing
            # Check if conversation is winding down
            recent_frequency = self._get_recent_message_frequency(messages, minutes=5)
            overall_frequency = message_count / total_duration
            
            if recent_frequency < overall_frequency * 0.5:
                return ConversationPhase.CLOSING
            else:
                return ConversationPhase.SYNTHESIS
        else:
            return ConversationPhase.EXPLORATION
    
    def _detect_interaction_pattern(self, messages: List[ChatMessage]) -> InteractionPattern:
        """
        Detect interaction pattern between participants
        
        Args:
            messages: Messages to analyze
            
        Returns:
            InteractionPattern: Detected pattern
        """
        if len(messages) < 3:
            return InteractionPattern.STAR
        
        # Analyze interaction sequences
        participants = list(set(msg.user_id for msg in messages))
        
        if len(participants) == 1:
            return InteractionPattern.STAR
        elif len(participants) == 2:
            return InteractionPattern.PING_PONG
        
        # Analyze interaction matrix
        interaction_matrix = defaultdict(lambda: defaultdict(int))
        
        for i in range(1, len(messages)):
            prev_user = messages[i-1].user_id
            curr_user = messages[i].user_id
            
            if prev_user != curr_user:
                interaction_matrix[prev_user][curr_user] += 1
        
        # Determine pattern based on interaction distribution
        total_interactions = sum(
            sum(targets.values()) for targets in interaction_matrix.values()
        )
        
        if total_interactions == 0:
            return InteractionPattern.STAR
        
        # Check for round-robin pattern
        if self._is_round_robin_pattern(messages, participants):
            return InteractionPattern.ROUND_ROBIN
        
        # Check for star pattern (one central participant)
        if self._is_star_pattern(interaction_matrix, participants):
            return InteractionPattern.STAR
        
        # Check for mesh pattern (everyone talks to everyone)
        if self._is_mesh_pattern(interaction_matrix, participants):
            return InteractionPattern.MESH
        
        # Default to chain pattern
        return InteractionPattern.CHAIN
    
    def _detect_conversation_rhythm(self, messages: List[ChatMessage]) -> ConversationRhythm:
        """
        Detect conversation rhythm pattern
        
        Args:
            messages: Messages to analyze
            
        Returns:
            ConversationRhythm: Detected rhythm
        """
        if len(messages) < 5:
            return ConversationRhythm.STEADY
        
        # Calculate message intervals
        intervals = []
        for i in range(1, len(messages)):
            interval = (messages[i].timestamp - messages[i-1].timestamp).total_seconds()
            intervals.append(interval)
        
        # Analyze rhythm patterns
        recent_intervals = intervals[-5:]  # Last 5 intervals
        early_intervals = intervals[:5]    # First 5 intervals
        
        recent_avg = statistics.mean(recent_intervals)
        early_avg = statistics.mean(early_intervals)
        
        # Check for burst pattern (very short intervals)
        if any(interval < 30 for interval in recent_intervals):  # Less than 30 seconds
            return ConversationRhythm.BURST
        
        # Check for acceleration/deceleration
        if recent_avg < early_avg * 0.7:  # Getting faster
            return ConversationRhythm.ACCELERATING
        elif recent_avg > early_avg * 1.5:  # Getting slower
            return ConversationRhythm.DECELERATING
        
        # Check for sporadic pattern (high variance)
        if len(intervals) > 3:
            variance = statistics.variance(intervals)
            mean_interval = statistics.mean(intervals)
            
            if variance > mean_interval * mean_interval:  # High variance
                return ConversationRhythm.SPORADIC
        
        return ConversationRhythm.STEADY
    
    def _analyze_engagement_trend(self, messages: List[ChatMessage]) -> str:
        """
        Analyze engagement trend over time
        
        Args:
            messages: Messages to analyze
            
        Returns:
            str: Engagement trend (increasing, decreasing, stable, volatile)
        """
        if len(messages) < 6:
            return "stable"
        
        # Divide messages into time windows
        window_size = len(messages) // 3
        
        early_messages = messages[:window_size]
        middle_messages = messages[window_size:2*window_size]
        late_messages = messages[2*window_size:]
        
        # Calculate engagement metrics for each window
        early_engagement = self._calculate_window_engagement(early_messages)
        middle_engagement = self._calculate_window_engagement(middle_messages)
        late_engagement = self._calculate_window_engagement(late_messages)
        
        # Analyze trend
        engagements = [early_engagement, middle_engagement, late_engagement]
        
        # Check for consistent increase/decrease
        if all(engagements[i] < engagements[i+1] for i in range(len(engagements)-1)):
            return "increasing"
        elif all(engagements[i] > engagements[i+1] for i in range(len(engagements)-1)):
            return "decreasing"
        
        # Check for volatility
        if max(engagements) - min(engagements) > statistics.mean(engagements) * 0.5:
            return "volatile"
        
        return "stable"
    
    def _detect_dominant_behaviors(self, messages: List[ChatMessage]) -> List[str]:
        """
        Detect dominant behavioral patterns in conversation
        
        Args:
            messages: Messages to analyze
            
        Returns:
            List[str]: Dominant behaviors
        """
        behaviors = []
        
        if not messages:
            return behaviors
        
        # Analyze question-asking behavior
        question_count = sum(1 for msg in messages if '?' in msg.content)
        if question_count > len(messages) * 0.3:
            behaviors.append("질문 중심 대화")
        
        # Analyze agreement/disagreement patterns
        agreement_keywords = ['맞', '동의', '그렇', '좋', '옳']
        disagreement_keywords = ['아니', '틀렸', '반대', '다르']
        
        agreement_count = sum(
            1 for msg in messages 
            if any(keyword in msg.content.lower() for keyword in agreement_keywords)
        )
        disagreement_count = sum(
            1 for msg in messages 
            if any(keyword in msg.content.lower() for keyword in disagreement_keywords)
        )
        
        if agreement_count > disagreement_count * 2:
            behaviors.append("합의 지향적")
        elif disagreement_count > agreement_count:
            behaviors.append("비판적 토론")
        
        # Analyze message length patterns
        avg_length = statistics.mean(len(msg.content) for msg in messages)
        if avg_length > 150:
            behaviors.append("상세한 설명")
        elif avg_length < 50:
            behaviors.append("간결한 대화")
        
        # Analyze response speed
        response_times = []
        for i in range(1, len(messages)):
            if messages[i].user_id != messages[i-1].user_id:
                time_diff = (messages[i].timestamp - messages[i-1].timestamp).total_seconds()
                response_times.append(time_diff)
        
        if response_times:
            avg_response_time = statistics.mean(response_times)
            if avg_response_time < 60:  # Less than 1 minute
                behaviors.append("빠른 응답")
            elif avg_response_time > 300:  # More than 5 minutes
                behaviors.append("신중한 응답")
        
        return behaviors[:5]  # Return top 5 behaviors
    
    def _calculate_pattern_confidence(
        self,
        messages: List[ChatMessage],
        phase: ConversationPhase,
        interaction_pattern: InteractionPattern,
        rhythm: ConversationRhythm
    ) -> float:
        """
        Calculate confidence in pattern detection
        
        Args:
            messages: Messages analyzed
            phase: Detected phase
            interaction_pattern: Detected interaction pattern
            rhythm: Detected rhythm
            
        Returns:
            float: Confidence score (0.0 to 1.0)
        """
        if not messages:
            return 0.0
        
        confidence_factors = []
        
        # Message count factor (more messages = higher confidence)
        message_count_factor = min(1.0, len(messages) / 20)
        confidence_factors.append(message_count_factor)
        
        # Time span factor (longer conversations = higher confidence)
        time_span = (messages[-1].timestamp - messages[0].timestamp).total_seconds() / 60
        time_factor = min(1.0, time_span / 30)  # 30 minutes for full confidence
        confidence_factors.append(time_factor)
        
        # Participant diversity factor
        participant_count = len(set(msg.user_id for msg in messages))
        diversity_factor = min(1.0, participant_count / 5)  # 5 participants for full confidence
        confidence_factors.append(diversity_factor)
        
        # Pattern consistency factor (simplified)
        consistency_factor = 0.8  # Placeholder - could be enhanced
        confidence_factors.append(consistency_factor)
        
        return statistics.mean(confidence_factors)
    
    def _generate_pattern_recommendations(
        self,
        phase: ConversationPhase,
        interaction_pattern: InteractionPattern,
        rhythm: ConversationRhythm,
        engagement_trend: str
    ) -> List[str]:
        """
        Generate recommendations based on detected patterns
        
        Args:
            phase: Conversation phase
            interaction_pattern: Interaction pattern
            rhythm: Conversation rhythm
            engagement_trend: Engagement trend
            
        Returns:
            List[str]: Recommendations
        """
        recommendations = []
        
        # Phase-based recommendations
        if phase == ConversationPhase.OPENING:
            recommendations.append("대화가 시작 단계입니다. 참여자들의 관심을 끌 수 있는 흥미로운 질문을 제시해보세요.")
        elif phase == ConversationPhase.EXPLORATION:
            recommendations.append("탐색 단계입니다. 다양한 관점을 수집하고 있으니 더 많은 의견을 들어보세요.")
        elif phase == ConversationPhase.DEEPENING:
            recommendations.append("심화 단계입니다. 구체적인 예시나 경험을 공유하도록 유도해보세요.")
        elif phase == ConversationPhase.SYNTHESIS:
            recommendations.append("종합 단계입니다. 지금까지의 논의를 정리하고 핵심 포인트를 도출해보세요.")
        elif phase == ConversationPhase.CLOSING:
            recommendations.append("마무리 단계입니다. 결론을 도출하거나 다음 토론 주제를 제안해보세요.")
        
        # Interaction pattern recommendations
        if interaction_pattern == InteractionPattern.STAR:
            recommendations.append("한 명이 대화를 주도하고 있습니다. 다른 참여자들의 참여를 유도해보세요.")
        elif interaction_pattern == InteractionPattern.PING_PONG:
            recommendations.append("두 명이 주로 대화하고 있습니다. 다른 참여자들도 참여할 수 있도록 해보세요.")
        elif interaction_pattern == InteractionPattern.MESH:
            recommendations.append("모든 참여자가 활발히 소통하고 있습니다. 좋은 토론 분위기를 유지하세요.")
        
        # Rhythm-based recommendations
        if rhythm == ConversationRhythm.BURST:
            recommendations.append("대화가 매우 활발합니다. 잠시 정리 시간을 가져보는 것이 좋겠습니다.")
        elif rhythm == ConversationRhythm.DECELERATING:
            recommendations.append("대화 속도가 느려지고 있습니다. 새로운 관점이나 질문으로 활력을 불어넣어보세요.")
        elif rhythm == ConversationRhythm.SPORADIC:
            recommendations.append("대화가 불규칙합니다. 일정한 리듬을 만들어보세요.")
        
        # Engagement trend recommendations
        if engagement_trend == "decreasing":
            recommendations.append("참여도가 감소하고 있습니다. 흥미로운 새 주제나 질문을 제시해보세요.")
        elif engagement_trend == "volatile":
            recommendations.append("참여도가 불안정합니다. 안정적인 토론 환경을 조성해보세요.")
        
        return recommendations[:3]  # Return top 3 recommendations
    
    # Helper methods
    def _calculate_segment_interaction_density(self, messages: List[ChatMessage]) -> float:
        """Calculate interaction density for a segment"""
        if len(messages) < 2:
            return 0.0
        
        interactions = 0
        for i in range(1, len(messages)):
            if messages[i].user_id != messages[i-1].user_id:
                interactions += 1
        
        return interactions / (len(messages) - 1)
    
    def _calculate_topic_coherence(self, messages: List[ChatMessage]) -> float:
        """Calculate topic coherence for a segment (simplified)"""
        if not messages:
            return 0.0
        
        # Simple keyword overlap analysis
        all_words = []
        for msg in messages:
            words = msg.content.lower().split()
            all_words.extend(words)
        
        if not all_words:
            return 0.0
        
        word_counts = Counter(all_words)
        # Coherence based on repeated keywords
        repeated_words = sum(1 for count in word_counts.values() if count > 1)
        
        return min(1.0, repeated_words / len(set(all_words)))
    
    def _get_recent_message_frequency(self, messages: List[ChatMessage], minutes: int) -> float:
        """Get message frequency for recent time window"""
        if not messages:
            return 0.0
        
        cutoff_time = messages[-1].timestamp - timedelta(minutes=minutes)
        recent_messages = [msg for msg in messages if msg.timestamp >= cutoff_time]
        
        return len(recent_messages) / minutes
    
    def _is_round_robin_pattern(self, messages: List[ChatMessage], participants: List[str]) -> bool:
        """Check if conversation follows round-robin pattern"""
        if len(participants) < 3 or len(messages) < len(participants) * 2:
            return False
        
        # Check if participants take turns in order
        participant_order = []
        current_speaker = None
        
        for msg in messages:
            if msg.user_id != current_speaker:
                participant_order.append(msg.user_id)
                current_speaker = msg.user_id
        
        # Check for repeating patterns
        pattern_length = len(participants)
        if len(participant_order) < pattern_length * 2:
            return False
        
        # Simple pattern detection
        for i in range(pattern_length, len(participant_order) - pattern_length):
            if participant_order[i:i+pattern_length] == participant_order[0:pattern_length]:
                return True
        
        return False
    
    def _is_star_pattern(self, interaction_matrix: dict, participants: List[str]) -> bool:
        """Check if conversation follows star pattern (one central participant)"""
        if len(participants) < 3:
            return False
        
        # Find participant with most outgoing interactions
        outgoing_counts = {
            p: sum(interaction_matrix[p].values()) 
            for p in participants
        }
        
        max_outgoing = max(outgoing_counts.values()) if outgoing_counts else 0
        total_interactions = sum(outgoing_counts.values())
        
        # Star pattern if one participant dominates outgoing interactions
        return max_outgoing > total_interactions * 0.6
    
    def _is_mesh_pattern(self, interaction_matrix: dict, participants: List[str]) -> bool:
        """Check if conversation follows mesh pattern (everyone talks to everyone)"""
        if len(participants) < 3:
            return False
        
        # Count how many participant pairs have interactions
        interacting_pairs = 0
        total_possible_pairs = len(participants) * (len(participants) - 1)
        
        for from_user in participants:
            for to_user in participants:
                if from_user != to_user and interaction_matrix[from_user][to_user] > 0:
                    interacting_pairs += 1
        
        # Mesh pattern if most pairs interact
        return interacting_pairs > total_possible_pairs * 0.6
    
    def _calculate_window_engagement(self, messages: List[ChatMessage]) -> float:
        """Calculate engagement score for a window of messages"""
        if not messages:
            return 0.0
        
        # Simple engagement calculation based on message count and length
        message_count = len(messages)
        avg_length = statistics.mean(len(msg.content) for msg in messages)
        participant_count = len(set(msg.user_id for msg in messages))
        
        # Normalize and combine factors
        count_factor = min(1.0, message_count / 10)
        length_factor = min(1.0, avg_length / 100)
        diversity_factor = min(1.0, participant_count / 5)
        
        return (count_factor + length_factor + diversity_factor) / 3
    
    def get_pattern_history(self, session_id: str) -> Optional[PatternAnalysisResult]:
        """Get cached pattern analysis for session"""
        return self.pattern_cache.get(session_id)
    
    def clear_pattern_cache(self, session_id: Optional[str] = None):
        """Clear pattern cache for session or all sessions"""
        if session_id:
            self.pattern_cache.pop(session_id, None)
            self.conversation_history.pop(session_id, None)
        else:
            self.pattern_cache.clear()
            self.conversation_history.clear()
    
    def get_pattern_summary(self, session_id: str) -> Dict[str, Any]:
        """Get summary of pattern analysis for session"""
        if session_id not in self.pattern_cache:
            return {"error": "No pattern analysis available for session"}
        
        result = self.pattern_cache[session_id]
        
        return {
            "session_id": session_id,
            "conversation_phase": result.conversation_phase,
            "interaction_pattern": result.interaction_pattern,
            "rhythm": result.rhythm,
            "engagement_trend": result.engagement_trend,
            "dominant_behaviors": result.dominant_behaviors,
            "confidence": result.pattern_confidence,
            "recommendations": result.recommendations,
            "analysis_timestamp": datetime.utcnow().isoformat()
        }