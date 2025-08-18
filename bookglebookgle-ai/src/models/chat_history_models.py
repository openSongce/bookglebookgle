"""
Chat History Context Models for BGBG AI Server
Defines data structures for chat history, conversation context, and participant tracking
"""

from datetime import datetime, timedelta
from enum import Enum
from typing import Dict, List, Optional, Any, Union
from dataclasses import dataclass, field
import json
import uuid

from pydantic import BaseModel, Field, validator


class MessageType(str, Enum):
    """Message type enumeration"""
    USER = "user"
    AI = "ai"
    SYSTEM = "system"


class CommunicationStyle(str, Enum):
    """Communication style enumeration for participant analysis"""
    FORMAL = "formal"
    CASUAL = "casual"
    ANALYTICAL = "analytical"
    CREATIVE = "creative"
    QUESTIONING = "questioning"
    SUPPORTIVE = "supportive"


class InterventionType(str, Enum):
    """AI intervention type enumeration"""
    ENCOURAGE_PARTICIPATION = "encourage_participation"
    TOPIC_REDIRECT = "topic_redirect"
    MEDIATE_CONFLICT = "mediate_conflict"
    PROVIDE_CONTEXT = "provide_context"
    SUMMARIZE_DISCUSSION = "summarize_discussion"


@dataclass
class ChatMessage:
    """
    Enhanced chat message model for chat history context feature
    Stores comprehensive message information including metadata for analysis
    """
    message_id: str
    session_id: str
    user_id: str
    nickname: str
    content: str
    timestamp: datetime
    message_type: MessageType = MessageType.USER
    metadata: Dict[str, Any] = field(default_factory=dict)
    
    # Analysis results (optional, populated by conversation analyzer)
    sentiment: Optional[float] = None  # -1.0 to 1.0
    topics: Optional[List[str]] = None
    intent: Optional[str] = None  
  
    def __post_init__(self):
        """Post-initialization validation and setup"""
        if not self.message_id:
            self.message_id = str(uuid.uuid4())
        
        if isinstance(self.timestamp, str):
            self.timestamp = datetime.fromisoformat(self.timestamp)
        elif isinstance(self.timestamp, (int, float)):
            self.timestamp = datetime.fromtimestamp(self.timestamp)
    
    def to_dict(self) -> Dict[str, Any]:
        """Convert to dictionary for JSON serialization"""
        return {
            'message_id': self.message_id,
            'session_id': self.session_id,
            'user_id': self.user_id,
            'nickname': self.nickname,
            'content': self.content,
            'timestamp': self.timestamp.isoformat(),
            'message_type': self.message_type.value,
            'metadata': self.metadata,
            'sentiment': self.sentiment,
            'topics': self.topics,
            'intent': self.intent
        }
    
    @classmethod
    def from_dict(cls, data: Dict[str, Any]) -> 'ChatMessage':
        """Create ChatMessage from dictionary"""
        return cls(
            message_id=data['message_id'],
            session_id=data['session_id'],
            user_id=data['user_id'],
            nickname=data['nickname'],
            content=data['content'],
            timestamp=datetime.fromisoformat(data['timestamp']),
            message_type=MessageType(data['message_type']),
            metadata=data.get('metadata', {}),
            sentiment=data.get('sentiment'),
            topics=data.get('topics'),
            intent=data.get('intent')
        )
    
    def to_json(self) -> str:
        """Convert to JSON string"""
        return json.dumps(self.to_dict(), ensure_ascii=False)
    
    @classmethod
    def from_json(cls, json_str: str) -> 'ChatMessage':
        """Create ChatMessage from JSON string"""
        data = json.loads(json_str)
        return cls.from_dict(data)
    
    def is_recent(self, time_window: timedelta) -> bool:
        """Check if message is within the specified time window"""
        return datetime.utcnow() - self.timestamp <= time_window
    
    def add_metadata(self, key: str, value: Any) -> None:
        """Add metadata to the message"""
        self.metadata[key] = value
    
    def get_metadata(self, key: str, default: Any = None) -> Any:
        """Get metadata value"""
        return self.metadata.get(key, default)


@dataclass
class ParticipantState:
    """
    Participant state tracking for personalized responses
    """
    user_id: str
    nickname: str
    last_message_time: datetime
    message_count: int = 0
    engagement_level: float = 0.0  # 0.0 - 1.0
    interests: List[str] = field(default_factory=list)
    communication_style: CommunicationStyle = CommunicationStyle.CASUAL
    
    # Analysis metrics
    avg_message_length: float = 0.0
    question_count: int = 0
    response_count: int = 0
    topic_contributions: Dict[str, int] = field(default_factory=dict)
    
    def __post_init__(self):
        """Post-initialization validation"""
        if isinstance(self.last_message_time, str):
            self.last_message_time = datetime.fromisoformat(self.last_message_time)
        elif isinstance(self.last_message_time, (int, float)):
            self.last_message_time = datetime.fromtimestamp(self.last_message_time)
    
    def update_from_message(self, message: ChatMessage) -> None:
        """Update participant state based on new message"""
        self.last_message_time = message.timestamp
        self.message_count += 1
        
        # Update average message length
        if self.message_count == 1:
            self.avg_message_length = len(message.content)
        else:
            self.avg_message_length = (
                (self.avg_message_length * (self.message_count - 1) + len(message.content)) 
                / self.message_count
            )
        
        # Count questions and responses
        if '?' in message.content or '？' in message.content:
            self.question_count += 1
        
        # Update topic contributions
        if message.topics:
            for topic in message.topics:
                self.topic_contributions[topic] = self.topic_contributions.get(topic, 0) + 1  
  
    def calculate_engagement_level(self, session_start: datetime, total_messages: int) -> float:
        """Calculate engagement level based on participation metrics"""
        if total_messages == 0:
            return 0.0
        
        # Base engagement from message frequency
        message_ratio = self.message_count / total_messages
        
        # Time-based engagement (recent activity)
        time_since_last = datetime.utcnow() - self.last_message_time
        time_factor = max(0.0, 1.0 - (time_since_last.total_seconds() / 3600))  # Decay over 1 hour
        
        # Question engagement (asking questions shows engagement)
        question_factor = min(1.0, self.question_count / max(1, self.message_count))
        
        # Combined engagement score
        engagement = (message_ratio * 0.5 + time_factor * 0.3 + question_factor * 0.2)
        self.engagement_level = min(1.0, engagement)
        
        return self.engagement_level
    
    def to_dict(self) -> Dict[str, Any]:
        """Convert to dictionary for JSON serialization"""
        return {
            'user_id': self.user_id,
            'nickname': self.nickname,
            'last_message_time': self.last_message_time.isoformat(),
            'message_count': self.message_count,
            'engagement_level': self.engagement_level,
            'interests': self.interests,
            'communication_style': self.communication_style.value,
            'avg_message_length': self.avg_message_length,
            'question_count': self.question_count,
            'response_count': self.response_count,
            'topic_contributions': self.topic_contributions
        }
    
    @classmethod
    def from_dict(cls, data: Dict[str, Any]) -> 'ParticipantState':
        """Create ParticipantState from dictionary"""
        return cls(
            user_id=data['user_id'],
            nickname=data['nickname'],
            last_message_time=datetime.fromisoformat(data['last_message_time']),
            message_count=data.get('message_count', 0),
            engagement_level=data.get('engagement_level', 0.0),
            interests=data.get('interests', []),
            communication_style=CommunicationStyle(data.get('communication_style', 'casual')),
            avg_message_length=data.get('avg_message_length', 0.0),
            question_count=data.get('question_count', 0),
            response_count=data.get('response_count', 0),
            topic_contributions=data.get('topic_contributions', {})
        )


@dataclass
class ConversationContext:
    """
    Conversation context for AI response generation
    Combines chat history with book content and participant analysis
    """
    session_id: str
    recent_messages: List[ChatMessage]
    book_context: List[str]  # From vector DB
    conversation_summary: Optional[str] = None
    active_topics: List[str] = field(default_factory=list)
    participant_states: Dict[str, ParticipantState] = field(default_factory=dict)
    context_window_size: int = 10
    total_token_count: int = 0
    
    # Context metadata
    created_at: datetime = field(default_factory=datetime.utcnow)
    last_updated: datetime = field(default_factory=datetime.utcnow)
    
    def add_message(self, message: ChatMessage) -> None:
        """Add a new message to the context"""
        self.recent_messages.append(message)
        
        # Maintain window size
        if len(self.recent_messages) > self.context_window_size:
            self.recent_messages = self.recent_messages[-self.context_window_size:]
        
        # Update participant state
        if message.user_id not in self.participant_states:
            self.participant_states[message.user_id] = ParticipantState(
                user_id=message.user_id,
                nickname=message.nickname,
                last_message_time=message.timestamp
            )
        
        self.participant_states[message.user_id].update_from_message(message)
        self.last_updated = datetime.utcnow()
    
    def get_recent_messages_text(self, limit: Optional[int] = None) -> str:
        """Get recent messages as formatted text for LLM context"""
        messages = self.recent_messages
        if limit:
            messages = messages[-limit:]
        
        formatted_messages = []
        for msg in messages:
            timestamp_str = msg.timestamp.strftime("%H:%M")
            formatted_messages.append(f"[{timestamp_str}] {msg.nickname}: {msg.content}")
        
        return "\n".join(formatted_messages)
    
    def get_participant_summary(self) -> str:
        """Get summary of participant states for LLM context"""
        if not self.participant_states:
            return "참여자 정보 없음"
        
        summaries = []
        for state in self.participant_states.values():
            engagement_desc = "높음" if state.engagement_level > 0.7 else "보통" if state.engagement_level > 0.3 else "낮음"
            summaries.append(
                f"- {state.nickname}: 메시지 {state.message_count}개, 참여도 {engagement_desc}"
            )
        
        return "\n".join(summaries)
    
    def get_book_context_text(self) -> str:
        """Get book context as formatted text for LLM"""
        if not self.book_context:
            return "관련 문서 내용 없음"
        
        return "\n\n".join([f"문서 내용 {i+1}:\n{context}" for i, context in enumerate(self.book_context)])
    
    def update_engagement_levels(self) -> None:
        """Update engagement levels for all participants"""
        total_messages = sum(state.message_count for state in self.participant_states.values())
        
        for state in self.participant_states.values():
            state.calculate_engagement_level(self.created_at, total_messages)
    
    def get_inactive_participants(self, inactive_threshold_minutes: int = 30) -> List[str]:
        """Get list of participants who haven't been active recently"""
        cutoff_time = datetime.utcnow() - timedelta(minutes=inactive_threshold_minutes)
        inactive = []
        
        for user_id, state in self.participant_states.items():
            if state.last_message_time < cutoff_time:
                inactive.append(user_id)
        
        return inactive
    
    def to_dict(self) -> Dict[str, Any]:
        """Convert to dictionary for JSON serialization"""
        return {
            'session_id': self.session_id,
            'recent_messages': [msg.to_dict() for msg in self.recent_messages],
            'book_context': self.book_context,
            'conversation_summary': self.conversation_summary,
            'active_topics': self.active_topics,
            'participant_states': {k: v.to_dict() for k, v in self.participant_states.items()},
            'context_window_size': self.context_window_size,
            'total_token_count': self.total_token_count,
            'created_at': self.created_at.isoformat(),
            'last_updated': self.last_updated.isoformat()
        }
    
    @classmethod
    def from_dict(cls, data: Dict[str, Any]) -> 'ConversationContext':
        """Create ConversationContext from dictionary"""
        return cls(
            session_id=data['session_id'],
            recent_messages=[ChatMessage.from_dict(msg) for msg in data['recent_messages']],
            book_context=data['book_context'],
            conversation_summary=data.get('conversation_summary'),
            active_topics=data.get('active_topics', []),
            participant_states={
                k: ParticipantState.from_dict(v) 
                for k, v in data.get('participant_states', {}).items()
            },
            context_window_size=data.get('context_window_size', 10),
            total_token_count=data.get('total_token_count', 0),
            created_at=datetime.fromisoformat(data['created_at']),
            last_updated=datetime.fromisoformat(data['last_updated'])
        )


# Configuration Models
class ContextConfig(BaseModel):
    """Configuration for conversation context management"""
    max_messages: int = Field(default=10, description="Maximum messages in context window")
    max_tokens: int = Field(default=2000, description="Maximum tokens for context")
    time_window: timedelta = Field(default=timedelta(hours=2), description="Time window for recent messages")
    max_book_chunks: int = Field(default=3, description="Maximum book context chunks")
    enable_summarization: bool = Field(default=True, description="Enable context summarization")
    summarization_threshold: int = Field(default=1500, description="Token threshold for summarization")
    
    class Config:
        arbitrary_types_allowed = True


# Analysis Result Models
class TopicChangeResult(BaseModel):
    """Result of topic change detection"""
    topic_changed: bool = Field(..., description="Whether topic has changed")
    previous_topic: Optional[str] = Field(None, description="Previous main topic")
    current_topic: Optional[str] = Field(None, description="Current main topic")
    confidence: float = Field(..., description="Confidence score 0.0-1.0")
    change_point: Optional[datetime] = Field(None, description="When topic changed")
    
    class Config:
        arbitrary_types_allowed = True


class ParticipationAnalysis(BaseModel):
    """Analysis of participant engagement and patterns"""
    session_id: str = Field(..., description="Session identifier")
    total_participants: int = Field(..., description="Total number of participants")
    active_participants: int = Field(..., description="Currently active participants")
    inactive_participants: List[str] = Field(..., description="Inactive participant IDs")
    dominant_participants: List[str] = Field(..., description="Dominant participant IDs")
    engagement_distribution: Dict[str, float] = Field(..., description="Engagement levels by participant")
    last_activity_times: Dict[str, datetime] = Field(..., description="Last activity by participant")
    
    class Config:
        arbitrary_types_allowed = True


class ConversationPatterns(BaseModel):
    """Detected conversation patterns"""
    is_repetitive: bool = Field(..., description="Whether conversation is repetitive")
    is_stagnant: bool = Field(..., description="Whether conversation has stagnated")
    has_conflict: bool = Field(..., description="Whether there's conflict detected")
    off_topic: bool = Field(..., description="Whether discussion is off-topic")
    dominant_topics: List[str] = Field(..., description="Most discussed topics")
    sentiment_trend: str = Field(..., description="Overall sentiment trend")
    interaction_density: float = Field(..., description="Interaction density score")


class InterventionSuggestion(BaseModel):
    """AI intervention suggestion"""
    intervention_type: InterventionType = Field(..., description="Type of intervention needed")
    priority: int = Field(..., description="Priority level 1-5")
    target_participants: List[str] = Field(..., description="Target participant IDs")
    suggested_message: str = Field(..., description="Suggested intervention message")
    reasoning: str = Field(..., description="Reasoning for intervention")
    confidence: float = Field(..., description="Confidence in suggestion")


# Session Management Models
class SessionMetadata(BaseModel):
    """Session metadata for Redis storage"""
    session_id: str = Field(..., description="Session identifier")
    meeting_id: str = Field(..., description="Meeting identifier")
    document_id: str = Field(..., description="Document identifier")
    created_at: datetime = Field(..., description="Session creation time")
    last_activity: datetime = Field(..., description="Last activity time")
    participant_count: int = Field(..., description="Number of participants")
    message_count: int = Field(..., description="Total message count")
    status: str = Field(default="active", description="Session status")
    
    class Config:
        arbitrary_types_allowed = True


# Error Models for Chat History
class ChatHistoryError(Exception):
    """Base exception for chat history related errors"""
    pass


class ContextBuildError(ChatHistoryError):
    """Exception raised when context building fails"""
    pass


class StorageError(ChatHistoryError):
    """Exception raised when storage operations fail"""
    pass


class AnalysisError(ChatHistoryError):
    """Exception raised when conversation analysis fails"""
    pass