from google.protobuf.internal import containers as _containers
from google.protobuf import descriptor as _descriptor
from google.protobuf import message as _message
from typing import ClassVar as _ClassVar, Iterable as _Iterable, Mapping as _Mapping, Optional as _Optional, Union as _Union

DESCRIPTOR: _descriptor.FileDescriptor

class TextContent(_message.Message):
    __slots__ = ["text", "language", "context"]
    TEXT_FIELD_NUMBER: _ClassVar[int]
    LANGUAGE_FIELD_NUMBER: _ClassVar[int]
    CONTEXT_FIELD_NUMBER: _ClassVar[int]
    text: str
    language: str
    context: str
    def __init__(self, text: _Optional[str] = ..., language: _Optional[str] = ..., context: _Optional[str] = ...) -> None: ...

class User(_message.Message):
    __slots__ = ["user_id", "nickname", "email"]
    USER_ID_FIELD_NUMBER: _ClassVar[int]
    NICKNAME_FIELD_NUMBER: _ClassVar[int]
    EMAIL_FIELD_NUMBER: _ClassVar[int]
    user_id: str
    nickname: str
    email: str
    def __init__(self, user_id: _Optional[str] = ..., nickname: _Optional[str] = ..., email: _Optional[str] = ...) -> None: ...

class QuizRequest(_message.Message):
    __slots__ = ["document_id", "content", "progress_percentage", "question_count", "difficulty_level"]
    DOCUMENT_ID_FIELD_NUMBER: _ClassVar[int]
    CONTENT_FIELD_NUMBER: _ClassVar[int]
    PROGRESS_PERCENTAGE_FIELD_NUMBER: _ClassVar[int]
    QUESTION_COUNT_FIELD_NUMBER: _ClassVar[int]
    DIFFICULTY_LEVEL_FIELD_NUMBER: _ClassVar[int]
    document_id: str
    content: TextContent
    progress_percentage: int
    question_count: int
    difficulty_level: str
    def __init__(self, document_id: _Optional[str] = ..., content: _Optional[_Union[TextContent, _Mapping]] = ..., progress_percentage: _Optional[int] = ..., question_count: _Optional[int] = ..., difficulty_level: _Optional[str] = ...) -> None: ...

class Question(_message.Message):
    __slots__ = ["question_text", "options", "correct_answer_index", "explanation", "category"]
    QUESTION_TEXT_FIELD_NUMBER: _ClassVar[int]
    OPTIONS_FIELD_NUMBER: _ClassVar[int]
    CORRECT_ANSWER_INDEX_FIELD_NUMBER: _ClassVar[int]
    EXPLANATION_FIELD_NUMBER: _ClassVar[int]
    CATEGORY_FIELD_NUMBER: _ClassVar[int]
    question_text: str
    options: _containers.RepeatedScalarFieldContainer[str]
    correct_answer_index: int
    explanation: str
    category: str
    def __init__(self, question_text: _Optional[str] = ..., options: _Optional[_Iterable[str]] = ..., correct_answer_index: _Optional[int] = ..., explanation: _Optional[str] = ..., category: _Optional[str] = ...) -> None: ...

class QuizResponse(_message.Message):
    __slots__ = ["success", "message", "questions", "quiz_id"]
    SUCCESS_FIELD_NUMBER: _ClassVar[int]
    MESSAGE_FIELD_NUMBER: _ClassVar[int]
    QUESTIONS_FIELD_NUMBER: _ClassVar[int]
    QUIZ_ID_FIELD_NUMBER: _ClassVar[int]
    success: bool
    message: str
    questions: _containers.RepeatedCompositeFieldContainer[Question]
    quiz_id: str
    def __init__(self, success: bool = ..., message: _Optional[str] = ..., questions: _Optional[_Iterable[_Union[Question, _Mapping]]] = ..., quiz_id: _Optional[str] = ...) -> None: ...

class ProofreadRequest(_message.Message):
    __slots__ = ["original_text", "context_text", "user"]
    ORIGINAL_TEXT_FIELD_NUMBER: _ClassVar[int]
    CONTEXT_TEXT_FIELD_NUMBER: _ClassVar[int]
    USER_FIELD_NUMBER: _ClassVar[int]
    original_text: TextContent
    context_text: TextContent
    user: User
    def __init__(self, original_text: _Optional[_Union[TextContent, _Mapping]] = ..., context_text: _Optional[_Union[TextContent, _Mapping]] = ..., user: _Optional[_Union[User, _Mapping]] = ...) -> None: ...

class TextCorrection(_message.Message):
    __slots__ = ["original", "corrected", "correction_type", "explanation", "start_position", "end_position"]
    ORIGINAL_FIELD_NUMBER: _ClassVar[int]
    CORRECTED_FIELD_NUMBER: _ClassVar[int]
    CORRECTION_TYPE_FIELD_NUMBER: _ClassVar[int]
    EXPLANATION_FIELD_NUMBER: _ClassVar[int]
    START_POSITION_FIELD_NUMBER: _ClassVar[int]
    END_POSITION_FIELD_NUMBER: _ClassVar[int]
    original: str
    corrected: str
    correction_type: str
    explanation: str
    start_position: int
    end_position: int
    def __init__(self, original: _Optional[str] = ..., corrected: _Optional[str] = ..., correction_type: _Optional[str] = ..., explanation: _Optional[str] = ..., start_position: _Optional[int] = ..., end_position: _Optional[int] = ...) -> None: ...

class ProofreadResponse(_message.Message):
    __slots__ = ["success", "message", "corrected_text", "corrections", "confidence_score"]
    SUCCESS_FIELD_NUMBER: _ClassVar[int]
    MESSAGE_FIELD_NUMBER: _ClassVar[int]
    CORRECTED_TEXT_FIELD_NUMBER: _ClassVar[int]
    CORRECTIONS_FIELD_NUMBER: _ClassVar[int]
    CONFIDENCE_SCORE_FIELD_NUMBER: _ClassVar[int]
    success: bool
    message: str
    corrected_text: str
    corrections: _containers.RepeatedCompositeFieldContainer[TextCorrection]
    confidence_score: float
    def __init__(self, success: bool = ..., message: _Optional[str] = ..., corrected_text: _Optional[str] = ..., corrections: _Optional[_Iterable[_Union[TextCorrection, _Mapping]]] = ..., confidence_score: _Optional[float] = ...) -> None: ...

class DiscussionInitRequest(_message.Message):
    __slots__ = ["document_id", "meeting_id", "full_document", "participants"]
    DOCUMENT_ID_FIELD_NUMBER: _ClassVar[int]
    MEETING_ID_FIELD_NUMBER: _ClassVar[int]
    FULL_DOCUMENT_FIELD_NUMBER: _ClassVar[int]
    PARTICIPANTS_FIELD_NUMBER: _ClassVar[int]
    document_id: str
    meeting_id: str
    full_document: TextContent
    participants: _containers.RepeatedCompositeFieldContainer[User]
    def __init__(self, document_id: _Optional[str] = ..., meeting_id: _Optional[str] = ..., full_document: _Optional[_Union[TextContent, _Mapping]] = ..., participants: _Optional[_Iterable[_Union[User, _Mapping]]] = ...) -> None: ...

class DiscussionInitResponse(_message.Message):
    __slots__ = ["success", "message", "discussion_session_id"]
    SUCCESS_FIELD_NUMBER: _ClassVar[int]
    MESSAGE_FIELD_NUMBER: _ClassVar[int]
    DISCUSSION_SESSION_ID_FIELD_NUMBER: _ClassVar[int]
    success: bool
    message: str
    discussion_session_id: str
    def __init__(self, success: bool = ..., message: _Optional[str] = ..., discussion_session_id: _Optional[str] = ...) -> None: ...

class ChatMessageRequest(_message.Message):
    __slots__ = ["discussion_session_id", "sender", "message", "timestamp"]
    DISCUSSION_SESSION_ID_FIELD_NUMBER: _ClassVar[int]
    SENDER_FIELD_NUMBER: _ClassVar[int]
    MESSAGE_FIELD_NUMBER: _ClassVar[int]
    TIMESTAMP_FIELD_NUMBER: _ClassVar[int]
    discussion_session_id: str
    sender: User
    message: str
    timestamp: int
    def __init__(self, discussion_session_id: _Optional[str] = ..., sender: _Optional[_Union[User, _Mapping]] = ..., message: _Optional[str] = ..., timestamp: _Optional[int] = ...) -> None: ...

class ChatMessageResponse(_message.Message):
    __slots__ = ["success", "message", "ai_response", "suggested_topics", "requires_moderation"]
    SUCCESS_FIELD_NUMBER: _ClassVar[int]
    MESSAGE_FIELD_NUMBER: _ClassVar[int]
    AI_RESPONSE_FIELD_NUMBER: _ClassVar[int]
    SUGGESTED_TOPICS_FIELD_NUMBER: _ClassVar[int]
    REQUIRES_MODERATION_FIELD_NUMBER: _ClassVar[int]
    success: bool
    message: str
    ai_response: str
    suggested_topics: _containers.RepeatedScalarFieldContainer[str]
    requires_moderation: bool
    def __init__(self, success: bool = ..., message: _Optional[str] = ..., ai_response: _Optional[str] = ..., suggested_topics: _Optional[_Iterable[str]] = ..., requires_moderation: bool = ...) -> None: ...

class TopicRequest(_message.Message):
    __slots__ = ["document_id", "document_content", "previous_topics"]
    DOCUMENT_ID_FIELD_NUMBER: _ClassVar[int]
    DOCUMENT_CONTENT_FIELD_NUMBER: _ClassVar[int]
    PREVIOUS_TOPICS_FIELD_NUMBER: _ClassVar[int]
    document_id: str
    document_content: TextContent
    previous_topics: _containers.RepeatedScalarFieldContainer[str]
    def __init__(self, document_id: _Optional[str] = ..., document_content: _Optional[_Union[TextContent, _Mapping]] = ..., previous_topics: _Optional[_Iterable[str]] = ...) -> None: ...

class TopicResponse(_message.Message):
    __slots__ = ["success", "message", "discussion_topics", "recommended_topic", "topic_rationale"]
    SUCCESS_FIELD_NUMBER: _ClassVar[int]
    MESSAGE_FIELD_NUMBER: _ClassVar[int]
    DISCUSSION_TOPICS_FIELD_NUMBER: _ClassVar[int]
    RECOMMENDED_TOPIC_FIELD_NUMBER: _ClassVar[int]
    TOPIC_RATIONALE_FIELD_NUMBER: _ClassVar[int]
    success: bool
    message: str
    discussion_topics: _containers.RepeatedScalarFieldContainer[str]
    recommended_topic: str
    topic_rationale: str
    def __init__(self, success: bool = ..., message: _Optional[str] = ..., discussion_topics: _Optional[_Iterable[str]] = ..., recommended_topic: _Optional[str] = ..., topic_rationale: _Optional[str] = ...) -> None: ...

class DocumentRequest(_message.Message):
    __slots__ = ["document_id", "content", "processing_type"]
    DOCUMENT_ID_FIELD_NUMBER: _ClassVar[int]
    CONTENT_FIELD_NUMBER: _ClassVar[int]
    PROCESSING_TYPE_FIELD_NUMBER: _ClassVar[int]
    document_id: str
    content: TextContent
    processing_type: str
    def __init__(self, document_id: _Optional[str] = ..., content: _Optional[_Union[TextContent, _Mapping]] = ..., processing_type: _Optional[str] = ...) -> None: ...

class DocumentChunk(_message.Message):
    __slots__ = ["chunk_id", "text", "start_position", "end_position", "embedding", "metadata"]
    class MetadataEntry(_message.Message):
        __slots__ = ["key", "value"]
        KEY_FIELD_NUMBER: _ClassVar[int]
        VALUE_FIELD_NUMBER: _ClassVar[int]
        key: str
        value: str
        def __init__(self, key: _Optional[str] = ..., value: _Optional[str] = ...) -> None: ...
    CHUNK_ID_FIELD_NUMBER: _ClassVar[int]
    TEXT_FIELD_NUMBER: _ClassVar[int]
    START_POSITION_FIELD_NUMBER: _ClassVar[int]
    END_POSITION_FIELD_NUMBER: _ClassVar[int]
    EMBEDDING_FIELD_NUMBER: _ClassVar[int]
    METADATA_FIELD_NUMBER: _ClassVar[int]
    chunk_id: str
    text: str
    start_position: int
    end_position: int
    embedding: _containers.RepeatedScalarFieldContainer[float]
    metadata: _containers.ScalarMap[str, str]
    def __init__(self, chunk_id: _Optional[str] = ..., text: _Optional[str] = ..., start_position: _Optional[int] = ..., end_position: _Optional[int] = ..., embedding: _Optional[_Iterable[float]] = ..., metadata: _Optional[_Mapping[str, str]] = ...) -> None: ...

class DocumentResponse(_message.Message):
    __slots__ = ["success", "message", "chunks", "analysis_results"]
    class AnalysisResultsEntry(_message.Message):
        __slots__ = ["key", "value"]
        KEY_FIELD_NUMBER: _ClassVar[int]
        VALUE_FIELD_NUMBER: _ClassVar[int]
        key: str
        value: str
        def __init__(self, key: _Optional[str] = ..., value: _Optional[str] = ...) -> None: ...
    SUCCESS_FIELD_NUMBER: _ClassVar[int]
    MESSAGE_FIELD_NUMBER: _ClassVar[int]
    CHUNKS_FIELD_NUMBER: _ClassVar[int]
    ANALYSIS_RESULTS_FIELD_NUMBER: _ClassVar[int]
    success: bool
    message: str
    chunks: _containers.RepeatedCompositeFieldContainer[DocumentChunk]
    analysis_results: _containers.ScalarMap[str, str]
    def __init__(self, success: bool = ..., message: _Optional[str] = ..., chunks: _Optional[_Iterable[_Union[DocumentChunk, _Mapping]]] = ..., analysis_results: _Optional[_Mapping[str, str]] = ...) -> None: ...

class UserActivityData(_message.Message):
    __slots__ = ["user_id", "document_ids", "reading_times", "meeting_participations", "quiz_scores", "total_reading_time", "category_preferences"]
    class CategoryPreferencesEntry(_message.Message):
        __slots__ = ["key", "value"]
        KEY_FIELD_NUMBER: _ClassVar[int]
        VALUE_FIELD_NUMBER: _ClassVar[int]
        key: str
        value: int
        def __init__(self, key: _Optional[str] = ..., value: _Optional[int] = ...) -> None: ...
    USER_ID_FIELD_NUMBER: _ClassVar[int]
    DOCUMENT_IDS_FIELD_NUMBER: _ClassVar[int]
    READING_TIMES_FIELD_NUMBER: _ClassVar[int]
    MEETING_PARTICIPATIONS_FIELD_NUMBER: _ClassVar[int]
    QUIZ_SCORES_FIELD_NUMBER: _ClassVar[int]
    TOTAL_READING_TIME_FIELD_NUMBER: _ClassVar[int]
    CATEGORY_PREFERENCES_FIELD_NUMBER: _ClassVar[int]
    user_id: str
    document_ids: _containers.RepeatedScalarFieldContainer[str]
    reading_times: _containers.RepeatedScalarFieldContainer[int]
    meeting_participations: _containers.RepeatedScalarFieldContainer[str]
    quiz_scores: _containers.RepeatedScalarFieldContainer[float]
    total_reading_time: int
    category_preferences: _containers.ScalarMap[str, int]
    def __init__(self, user_id: _Optional[str] = ..., document_ids: _Optional[_Iterable[str]] = ..., reading_times: _Optional[_Iterable[int]] = ..., meeting_participations: _Optional[_Iterable[str]] = ..., quiz_scores: _Optional[_Iterable[float]] = ..., total_reading_time: _Optional[int] = ..., category_preferences: _Optional[_Mapping[str, int]] = ...) -> None: ...

class UserActivityRequest(_message.Message):
    __slots__ = ["user_id", "start_date", "end_date"]
    USER_ID_FIELD_NUMBER: _ClassVar[int]
    START_DATE_FIELD_NUMBER: _ClassVar[int]
    END_DATE_FIELD_NUMBER: _ClassVar[int]
    user_id: str
    start_date: int
    end_date: int
    def __init__(self, user_id: _Optional[str] = ..., start_date: _Optional[int] = ..., end_date: _Optional[int] = ...) -> None: ...

class UserActivityResponse(_message.Message):
    __slots__ = ["success", "message", "activity_data", "insights", "metrics"]
    class MetricsEntry(_message.Message):
        __slots__ = ["key", "value"]
        KEY_FIELD_NUMBER: _ClassVar[int]
        VALUE_FIELD_NUMBER: _ClassVar[int]
        key: str
        value: float
        def __init__(self, key: _Optional[str] = ..., value: _Optional[float] = ...) -> None: ...
    SUCCESS_FIELD_NUMBER: _ClassVar[int]
    MESSAGE_FIELD_NUMBER: _ClassVar[int]
    ACTIVITY_DATA_FIELD_NUMBER: _ClassVar[int]
    INSIGHTS_FIELD_NUMBER: _ClassVar[int]
    METRICS_FIELD_NUMBER: _ClassVar[int]
    success: bool
    message: str
    activity_data: UserActivityData
    insights: _containers.RepeatedScalarFieldContainer[str]
    metrics: _containers.ScalarMap[str, float]
    def __init__(self, success: bool = ..., message: _Optional[str] = ..., activity_data: _Optional[_Union[UserActivityData, _Mapping]] = ..., insights: _Optional[_Iterable[str]] = ..., metrics: _Optional[_Mapping[str, float]] = ...) -> None: ...

class InterestExtractionRequest(_message.Message):
    __slots__ = ["user_id", "activity_data", "recent_searches", "bookmarked_topics"]
    USER_ID_FIELD_NUMBER: _ClassVar[int]
    ACTIVITY_DATA_FIELD_NUMBER: _ClassVar[int]
    RECENT_SEARCHES_FIELD_NUMBER: _ClassVar[int]
    BOOKMARKED_TOPICS_FIELD_NUMBER: _ClassVar[int]
    user_id: str
    activity_data: UserActivityData
    recent_searches: _containers.RepeatedScalarFieldContainer[str]
    bookmarked_topics: _containers.RepeatedScalarFieldContainer[str]
    def __init__(self, user_id: _Optional[str] = ..., activity_data: _Optional[_Union[UserActivityData, _Mapping]] = ..., recent_searches: _Optional[_Iterable[str]] = ..., bookmarked_topics: _Optional[_Iterable[str]] = ...) -> None: ...

class InterestExtractionResponse(_message.Message):
    __slots__ = ["success", "message", "primary_interests", "emerging_interests", "interest_scores", "recommended_categories"]
    class InterestScoresEntry(_message.Message):
        __slots__ = ["key", "value"]
        KEY_FIELD_NUMBER: _ClassVar[int]
        VALUE_FIELD_NUMBER: _ClassVar[int]
        key: str
        value: float
        def __init__(self, key: _Optional[str] = ..., value: _Optional[float] = ...) -> None: ...
    SUCCESS_FIELD_NUMBER: _ClassVar[int]
    MESSAGE_FIELD_NUMBER: _ClassVar[int]
    PRIMARY_INTERESTS_FIELD_NUMBER: _ClassVar[int]
    EMERGING_INTERESTS_FIELD_NUMBER: _ClassVar[int]
    INTEREST_SCORES_FIELD_NUMBER: _ClassVar[int]
    RECOMMENDED_CATEGORIES_FIELD_NUMBER: _ClassVar[int]
    success: bool
    message: str
    primary_interests: _containers.RepeatedScalarFieldContainer[str]
    emerging_interests: _containers.RepeatedScalarFieldContainer[str]
    interest_scores: _containers.ScalarMap[str, float]
    recommended_categories: _containers.RepeatedScalarFieldContainer[str]
    def __init__(self, success: bool = ..., message: _Optional[str] = ..., primary_interests: _Optional[_Iterable[str]] = ..., emerging_interests: _Optional[_Iterable[str]] = ..., interest_scores: _Optional[_Mapping[str, float]] = ..., recommended_categories: _Optional[_Iterable[str]] = ...) -> None: ...

class ErrorDetails(_message.Message):
    __slots__ = ["error_code", "error_message", "error_category", "additional_info"]
    class AdditionalInfoEntry(_message.Message):
        __slots__ = ["key", "value"]
        KEY_FIELD_NUMBER: _ClassVar[int]
        VALUE_FIELD_NUMBER: _ClassVar[int]
        key: str
        value: str
        def __init__(self, key: _Optional[str] = ..., value: _Optional[str] = ...) -> None: ...
    ERROR_CODE_FIELD_NUMBER: _ClassVar[int]
    ERROR_MESSAGE_FIELD_NUMBER: _ClassVar[int]
    ERROR_CATEGORY_FIELD_NUMBER: _ClassVar[int]
    ADDITIONAL_INFO_FIELD_NUMBER: _ClassVar[int]
    error_code: str
    error_message: str
    error_category: str
    additional_info: _containers.ScalarMap[str, str]
    def __init__(self, error_code: _Optional[str] = ..., error_message: _Optional[str] = ..., error_category: _Optional[str] = ..., additional_info: _Optional[_Mapping[str, str]] = ...) -> None: ...
