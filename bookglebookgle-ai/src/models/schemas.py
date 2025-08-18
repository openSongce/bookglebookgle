"""
Pydantic models and schemas for BGBG AI Server
Defines data structures for API requests and responses
"""

from datetime import datetime
from enum import Enum
from typing import Dict, List, Optional, Any, Union, Tuple

from pydantic import BaseModel, Field, validator


# Enums
class DifficultyLevel(str, Enum):
    EASY = "easy"
    MEDIUM = "medium" 
    HARD = "hard"


class CorrectionType(str, Enum):
    SPELLING = "spelling"
    GRAMMAR = "grammar"
    STYLE = "style"
    FORMATTING = "formatting"
    PARTICLE = "particle"
    ENDING = "ending"
    KOREAN_GRAMMAR = "korean_grammar"


class SessionStatus(str, Enum):
    ACTIVE = "active"
    COMPLETED = "completed"
    TERMINATED = "terminated"


# Base Models
class BaseRequest(BaseModel):
    """Base request model with common fields"""
    request_id: Optional[str] = Field(None, description="Unique request identifier")
    timestamp: Optional[datetime] = Field(default_factory=datetime.utcnow)


class BaseResponse(BaseModel):
    """Base response model with common fields"""
    success: bool = Field(..., description="Request success status")
    message: Optional[str] = Field(None, description="Response message")
    timestamp: datetime = Field(default_factory=datetime.utcnow)


# User Models
class User(BaseModel):
    """User information model"""
    user_id: str = Field(..., description="Unique user identifier")
    nickname: str = Field(..., description="User nickname")
    email: Optional[str] = Field(None, description="User email")


# Text Content Models
class TextContent(BaseModel):
    """Text content with metadata"""
    text: str = Field(..., description="The text content")
    language: str = Field(default="ko", description="Text language code")
    context: Optional[str] = Field(None, description="Additional context")


# Quiz Models
class QuizQuestion(BaseModel):
    """Quiz question model"""
    question_text: str = Field(..., description="Question text")
    options: List[str] = Field(..., description="Multiple choice options")
    correct_answer_index: int = Field(..., description="Index of correct answer")
    explanation: Optional[str] = Field(None, description="Answer explanation")
    category: Optional[str] = Field(default="general", description="Question category")
    
    @validator('correct_answer_index')
    def validate_answer_index(cls, v, values):
        if 'options' in values and v >= len(values['options']):
            raise ValueError('correct_answer_index out of range')
        return v


class QuizGenerationRequest(BaseRequest):
    """Quiz generation request"""
    document_id: str = Field(..., description="Document identifier")
    content: str = Field(..., description="Document content")
    language: str = Field(default="ko", description="Content language")
    progress_percentage: int = Field(..., description="Reading progress (50 or 100)")
    question_count: int = Field(default=5, description="Number of questions to generate")
    difficulty_level: DifficultyLevel = Field(default=DifficultyLevel.MEDIUM)
    
    @validator('progress_percentage')
    def validate_progress(cls, v):
        if v not in [50, 100]:
            raise ValueError('progress_percentage must be 50 or 100')
        return v
    
    @validator('question_count')
    def validate_question_count(cls, v):
        if not 1 <= v <= 10:
            raise ValueError('question_count must be between 1 and 10')
        return v


class QuizResponse(BaseResponse):
    """Quiz generation response"""
    quiz_id: Optional[str] = Field(None, description="Generated quiz identifier")
    questions: List[QuizQuestion] = Field(default_factory=list, description="Generated questions")
    question_count: int = Field(default=0, description="Number of generated questions")


class QuizSubmission(BaseModel):
    """Quiz answer submission"""
    quiz_id: str = Field(..., description="Quiz identifier")
    user_id: str = Field(..., description="User identifier")
    answers: List[int] = Field(..., description="Selected answer indices")
    
    @validator('answers')
    def validate_answers(cls, v):
        if any(answer < 0 for answer in v):
            raise ValueError('Answer indices cannot be negative')
        return v


class QuizResult(BaseModel):
    """Quiz evaluation result"""
    question_index: int = Field(..., description="Question index")
    question: str = Field(..., description="Question text")
    user_answer: int = Field(..., description="User's answer index")
    correct_answer: int = Field(..., description="Correct answer index")
    is_correct: bool = Field(..., description="Whether answer is correct")
    explanation: Optional[str] = Field(None, description="Answer explanation")


class QuizEvaluationResponse(BaseResponse):
    """Quiz evaluation response"""
    quiz_id: str = Field(..., description="Quiz identifier")
    score: float = Field(..., description="Score percentage")
    correct_count: int = Field(..., description="Number of correct answers")
    total_questions: int = Field(..., description="Total number of questions")
    results: List[QuizResult] = Field(..., description="Detailed results")


# Proofreading Models
class TextCorrection(BaseModel):
    """Text correction detail"""
    original: str = Field(..., description="Original text")
    corrected: str = Field(..., description="Corrected text")
    correction_type: CorrectionType = Field(..., description="Type of correction")
    explanation: str = Field(..., description="Correction explanation")
    start_position: Optional[int] = Field(None, description="Start position in text")
    end_position: Optional[int] = Field(None, description="End position in text")
    confidence: float = Field(default=0.8, description="Correction confidence")
    source: str = Field(default="ai", description="Correction source")


class ProofreadingRequest(BaseRequest):
    """Proofreading request"""
    original_text: str = Field(..., description="Text to proofread")
    context_text: Optional[str] = Field(None, description="Context text")
    language: str = Field(default="ko", description="Text language")
    user_id: str = Field(..., description="User identifier")
    
    @validator('original_text')
    def validate_text_length(cls, v):
        if len(v) > 10000:
            raise ValueError('Text too long (max 10000 characters)')
        if len(v) < 5:
            raise ValueError('Text too short (min 5 characters)')
        return v


class ProofreadingResponse(BaseResponse):
    """Proofreading response"""
    corrected_text: str = Field(..., description="Corrected text")
    original_text: str = Field(..., description="Original text")
    corrections: List[TextCorrection] = Field(..., description="List of corrections")
    confidence_score: float = Field(..., description="Overall confidence score")
    diff_html: Optional[str] = Field(None, description="HTML diff visualization")
    statistics: Dict[str, Any] = Field(default_factory=dict, description="Correction statistics")


# Discussion Models
class DiscussionInitRequest(BaseRequest):
    """Discussion initialization request"""
    document_id: str = Field(..., description="Document identifier")
    meeting_id: str = Field(..., description="Meeting identifier")
    full_document: str = Field(..., description="Full document text")
    participants: List[User] = Field(..., description="Discussion participants")
    
    @validator('full_document')
    def validate_document_length(cls, v):
        if len(v) < 100:
            raise ValueError('Document too short for meaningful discussion')
        return v


class DiscussionInitResponse(BaseResponse):
    """Discussion initialization response"""
    session_id: str = Field(..., description="Discussion session identifier")
    participant_count: int = Field(..., description="Number of participants")
    chunk_count: int = Field(default=0, description="Number of document chunks processed")


class ChatMessage(BaseModel):
    """Chat message model"""
    sender_id: str = Field(..., description="Message sender ID")
    sender_nickname: str = Field(..., description="Message sender nickname")
    message: str = Field(..., description="Message content")
    timestamp: float = Field(..., description="Message timestamp")


class ChatMessageRequest(BaseRequest):
    """Chat message processing request"""
    session_id: str = Field(..., description="Discussion session ID")
    sender: User = Field(..., description="Message sender")
    message: str = Field(..., description="Message content")


class ChatMessageResponse(BaseResponse):
    """Chat message processing response"""
    ai_response: Optional[str] = Field(None, description="AI moderator response")
    suggested_topics: List[str] = Field(default_factory=list, description="Suggested topics")
    requires_moderation: bool = Field(default=False, description="Whether moderation is needed")


class TopicGenerationRequest(BaseRequest):
    """Topic generation request"""
    document_id: str = Field(..., description="Document identifier")
    content: str = Field(..., description="Document content")
    previous_topics: List[str] = Field(default_factory=list, description="Previously used topics")


class TopicGenerationResponse(BaseResponse):
    """Topic generation response"""
    topics: List[str] = Field(..., description="Generated topics")
    recommended_topic: Optional[str] = Field(None, description="Recommended main topic")
    topic_count: int = Field(..., description="Number of generated topics")



# Session and Statistics Models
class DiscussionSession(BaseModel):
    """Discussion session model"""
    session_id: str = Field(..., description="Session identifier")
    document_id: str = Field(..., description="Document identifier")
    meeting_id: str = Field(..., description="Meeting identifier")
    participants: List[User] = Field(..., description="Session participants")
    status: SessionStatus = Field(..., description="Session status")
    created_at: datetime = Field(..., description="Creation timestamp")
    last_activity: datetime = Field(..., description="Last activity timestamp")
    message_count: int = Field(default=0, description="Total message count")


class SessionStatistics(BaseModel):
    """Session statistics model"""
    session_id: str = Field(..., description="Session identifier")
    total_messages: int = Field(..., description="Total messages")
    participant_count: int = Field(..., description="Number of participants")
    duration_minutes: float = Field(..., description="Session duration in minutes")
    participant_stats: Dict[str, Dict[str, Any]] = Field(..., description="Per-participant statistics")
    status: SessionStatus = Field(..., description="Session status")


# Health and Configuration Models
class ServiceHealth(BaseModel):
    """Service health status"""
    status: str = Field(..., description="Health status")
    version: str = Field(..., description="Service version")
    services: Dict[str, bool] = Field(..., description="Service availability status")


class ServiceConfiguration(BaseModel):
    """Service configuration"""
    server: Dict[str, Any] = Field(..., description="Server configuration")
    features: Dict[str, bool] = Field(..., description="Feature flags")
    ai: Dict[str, Any] = Field(..., description="AI configuration")
    cache: Dict[str, Any] = Field(..., description="Cache configuration")


# Error Models
class ErrorDetail(BaseModel):
    """Error detail model"""
    error_code: str = Field(..., description="Error code")
    error_message: str = Field(..., description="Error message")
    error_category: str = Field(..., description="Error category")
    additional_info: Dict[str, Any] = Field(default_factory=dict, description="Additional error info")


class ValidationError(BaseModel):
    """Validation error model"""
    field: str = Field(..., description="Field with validation error")
    message: str = Field(..., description="Validation error message")
    value: Any = Field(..., description="Invalid value")


class OcrBlock(BaseModel):
    """개별 OCR 블록의 정보를 담는 모델 (gRPC TextBlock과 일치)"""
    text: str = Field(..., description="추출된 텍스트")
    page_number: int = Field(..., description="페이지 번호 (1부터 시작)")
    x0: float = Field(..., description="좌상단 X 좌표")
    y0: float = Field(..., description="좌상단 Y 좌표") 
    x1: float = Field(..., description="우하단 X 좌표")
    y1: float = Field(..., description="우하단 Y 좌표")
    confidence: float = Field(default=0.0, description="OCR 신뢰도 (0.0-1.0)")
    block_type: str = Field(default="text", description="블록 타입 (text, image, etc.)")

    @property
    def bbox(self) -> Tuple[float, float, float, float]:
        """하위 호환성을 위한 bbox 속성"""
        return (self.x0, self.y0, self.x1, self.y1)
        

        
class ProcessDocumentRequest(BaseModel):
    """위치 정보가 포함된 OCR 결과를 받는 요청 모델"""
    document_id: str = Field(..., description="문서 식별자")
    ocr_results: List[OcrBlock] = Field(..., description="OCR 추출 결과")
    file_name: Optional[str] = Field(None, description="원본 파일명")
    metadata: Dict[str, Any] = Field(default_factory=dict, description="추가 메타데이터")