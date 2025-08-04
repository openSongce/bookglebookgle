from google.protobuf.internal import containers as _containers
from google.protobuf import descriptor as _descriptor
from google.protobuf import message as _message
from typing import ClassVar as _ClassVar, Iterable as _Iterable, Mapping as _Mapping, Optional as _Optional, Union as _Union

DESCRIPTOR: _descriptor.FileDescriptor

class TextContent(_message.Message):
    __slots__ = ("text", "language", "context")
    TEXT_FIELD_NUMBER: _ClassVar[int]
    LANGUAGE_FIELD_NUMBER: _ClassVar[int]
    CONTEXT_FIELD_NUMBER: _ClassVar[int]
    text: str
    language: str
    context: str
    def __init__(self, text: _Optional[str] = ..., language: _Optional[str] = ..., context: _Optional[str] = ...) -> None: ...

class User(_message.Message):
    __slots__ = ("user_id", "nickname")
    USER_ID_FIELD_NUMBER: _ClassVar[int]
    NICKNAME_FIELD_NUMBER: _ClassVar[int]
    user_id: str
    nickname: str
    def __init__(self, user_id: _Optional[str] = ..., nickname: _Optional[str] = ...) -> None: ...

class QuizRequest(_message.Message):
    __slots__ = ("document_id", "meeting_id", "content", "progress_percentage")
    DOCUMENT_ID_FIELD_NUMBER: _ClassVar[int]
    MEETING_ID_FIELD_NUMBER: _ClassVar[int]
    CONTENT_FIELD_NUMBER: _ClassVar[int]
    PROGRESS_PERCENTAGE_FIELD_NUMBER: _ClassVar[int]
    document_id: str
    meeting_id: str
    content: TextContent
    progress_percentage: int
    def __init__(self, document_id: _Optional[str] = ..., meeting_id: _Optional[str] = ..., content: _Optional[_Union[TextContent, _Mapping]] = ..., progress_percentage: _Optional[int] = ...) -> None: ...

class Question(_message.Message):
    __slots__ = ("question_text", "options", "correct_answer_index")
    QUESTION_TEXT_FIELD_NUMBER: _ClassVar[int]
    OPTIONS_FIELD_NUMBER: _ClassVar[int]
    CORRECT_ANSWER_INDEX_FIELD_NUMBER: _ClassVar[int]
    question_text: str
    options: _containers.RepeatedScalarFieldContainer[str]
    correct_answer_index: int
    def __init__(self, question_text: _Optional[str] = ..., options: _Optional[_Iterable[str]] = ..., correct_answer_index: _Optional[int] = ...) -> None: ...

class QuizResponse(_message.Message):
    __slots__ = ("success", "message", "questions", "quiz_id")
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
    __slots__ = ("original_text", "context_text", "user")
    ORIGINAL_TEXT_FIELD_NUMBER: _ClassVar[int]
    CONTEXT_TEXT_FIELD_NUMBER: _ClassVar[int]
    USER_FIELD_NUMBER: _ClassVar[int]
    original_text: TextContent
    context_text: TextContent
    user: User
    def __init__(self, original_text: _Optional[_Union[TextContent, _Mapping]] = ..., context_text: _Optional[_Union[TextContent, _Mapping]] = ..., user: _Optional[_Union[User, _Mapping]] = ...) -> None: ...

class TextCorrection(_message.Message):
    __slots__ = ("original", "corrected", "correction_type", "explanation", "start_position", "end_position")
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
    __slots__ = ("success", "message", "corrected_text", "corrections", "confidence_score")
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
    __slots__ = ("document_id", "meeting_id", "session_id", "participants", "started_at")
    DOCUMENT_ID_FIELD_NUMBER: _ClassVar[int]
    MEETING_ID_FIELD_NUMBER: _ClassVar[int]
    SESSION_ID_FIELD_NUMBER: _ClassVar[int]
    PARTICIPANTS_FIELD_NUMBER: _ClassVar[int]
    STARTED_AT_FIELD_NUMBER: _ClassVar[int]
    document_id: str
    meeting_id: str
    session_id: str
    participants: _containers.RepeatedCompositeFieldContainer[User]
    started_at: int
    def __init__(self, document_id: _Optional[str] = ..., meeting_id: _Optional[str] = ..., session_id: _Optional[str] = ..., participants: _Optional[_Iterable[_Union[User, _Mapping]]] = ..., started_at: _Optional[int] = ...) -> None: ...

class DiscussionInitResponse(_message.Message):
    __slots__ = ("success", "message", "discussion_topics", "recommended_topic")
    SUCCESS_FIELD_NUMBER: _ClassVar[int]
    MESSAGE_FIELD_NUMBER: _ClassVar[int]
    DISCUSSION_TOPICS_FIELD_NUMBER: _ClassVar[int]
    RECOMMENDED_TOPIC_FIELD_NUMBER: _ClassVar[int]
    success: bool
    message: str
    discussion_topics: _containers.RepeatedScalarFieldContainer[str]
    recommended_topic: str
    def __init__(self, success: bool = ..., message: _Optional[str] = ..., discussion_topics: _Optional[_Iterable[str]] = ..., recommended_topic: _Optional[str] = ...) -> None: ...

class DiscussionEndRequest(_message.Message):
    __slots__ = ("meeting_id", "session_id", "ended_at")
    MEETING_ID_FIELD_NUMBER: _ClassVar[int]
    SESSION_ID_FIELD_NUMBER: _ClassVar[int]
    ENDED_AT_FIELD_NUMBER: _ClassVar[int]
    meeting_id: str
    session_id: str
    ended_at: int
    def __init__(self, meeting_id: _Optional[str] = ..., session_id: _Optional[str] = ..., ended_at: _Optional[int] = ...) -> None: ...

class DiscussionEndResponse(_message.Message):
    __slots__ = ("success", "message")
    SUCCESS_FIELD_NUMBER: _ClassVar[int]
    MESSAGE_FIELD_NUMBER: _ClassVar[int]
    success: bool
    message: str
    def __init__(self, success: bool = ..., message: _Optional[str] = ...) -> None: ...

class MeetingEndRequest(_message.Message):
    __slots__ = ("meeting_id", "meeting_type", "ended_at", "session_id")
    MEETING_ID_FIELD_NUMBER: _ClassVar[int]
    MEETING_TYPE_FIELD_NUMBER: _ClassVar[int]
    ENDED_AT_FIELD_NUMBER: _ClassVar[int]
    SESSION_ID_FIELD_NUMBER: _ClassVar[int]
    meeting_id: str
    meeting_type: str
    ended_at: int
    session_id: str
    def __init__(self, meeting_id: _Optional[str] = ..., meeting_type: _Optional[str] = ..., ended_at: _Optional[int] = ..., session_id: _Optional[str] = ...) -> None: ...

class MeetingEndResponse(_message.Message):
    __slots__ = ("success", "message", "meeting_type")
    SUCCESS_FIELD_NUMBER: _ClassVar[int]
    MESSAGE_FIELD_NUMBER: _ClassVar[int]
    MEETING_TYPE_FIELD_NUMBER: _ClassVar[int]
    success: bool
    message: str
    meeting_type: str
    def __init__(self, success: bool = ..., message: _Optional[str] = ..., meeting_type: _Optional[str] = ...) -> None: ...

class ChatHistoryMessage(_message.Message):
    __slots__ = ("message_id", "session_id", "sender", "content", "timestamp", "message_type", "metadata")
    class MetadataEntry(_message.Message):
        __slots__ = ("key", "value")
        KEY_FIELD_NUMBER: _ClassVar[int]
        VALUE_FIELD_NUMBER: _ClassVar[int]
        key: str
        value: str
        def __init__(self, key: _Optional[str] = ..., value: _Optional[str] = ...) -> None: ...
    MESSAGE_ID_FIELD_NUMBER: _ClassVar[int]
    SESSION_ID_FIELD_NUMBER: _ClassVar[int]
    SENDER_FIELD_NUMBER: _ClassVar[int]
    CONTENT_FIELD_NUMBER: _ClassVar[int]
    TIMESTAMP_FIELD_NUMBER: _ClassVar[int]
    MESSAGE_TYPE_FIELD_NUMBER: _ClassVar[int]
    METADATA_FIELD_NUMBER: _ClassVar[int]
    message_id: str
    session_id: str
    sender: User
    content: str
    timestamp: int
    message_type: str
    metadata: _containers.ScalarMap[str, str]
    def __init__(self, message_id: _Optional[str] = ..., session_id: _Optional[str] = ..., sender: _Optional[_Union[User, _Mapping]] = ..., content: _Optional[str] = ..., timestamp: _Optional[int] = ..., message_type: _Optional[str] = ..., metadata: _Optional[_Mapping[str, str]] = ...) -> None: ...

class GetChatHistoryRequest(_message.Message):
    __slots__ = ("session_id", "limit", "since_timestamp", "user_id")
    SESSION_ID_FIELD_NUMBER: _ClassVar[int]
    LIMIT_FIELD_NUMBER: _ClassVar[int]
    SINCE_TIMESTAMP_FIELD_NUMBER: _ClassVar[int]
    USER_ID_FIELD_NUMBER: _ClassVar[int]
    session_id: str
    limit: int
    since_timestamp: int
    user_id: str
    def __init__(self, session_id: _Optional[str] = ..., limit: _Optional[int] = ..., since_timestamp: _Optional[int] = ..., user_id: _Optional[str] = ...) -> None: ...

class GetChatHistoryResponse(_message.Message):
    __slots__ = ("success", "message", "messages", "total_count", "has_more")
    SUCCESS_FIELD_NUMBER: _ClassVar[int]
    MESSAGE_FIELD_NUMBER: _ClassVar[int]
    MESSAGES_FIELD_NUMBER: _ClassVar[int]
    TOTAL_COUNT_FIELD_NUMBER: _ClassVar[int]
    HAS_MORE_FIELD_NUMBER: _ClassVar[int]
    success: bool
    message: str
    messages: _containers.RepeatedCompositeFieldContainer[ChatHistoryMessage]
    total_count: int
    has_more: bool
    def __init__(self, success: bool = ..., message: _Optional[str] = ..., messages: _Optional[_Iterable[_Union[ChatHistoryMessage, _Mapping]]] = ..., total_count: _Optional[int] = ..., has_more: bool = ...) -> None: ...

class ChatSessionStatsRequest(_message.Message):
    __slots__ = ("session_id",)
    SESSION_ID_FIELD_NUMBER: _ClassVar[int]
    session_id: str
    def __init__(self, session_id: _Optional[str] = ...) -> None: ...

class ParticipantStats(_message.Message):
    __slots__ = ("participant", "message_count", "last_activity", "engagement_level")
    PARTICIPANT_FIELD_NUMBER: _ClassVar[int]
    MESSAGE_COUNT_FIELD_NUMBER: _ClassVar[int]
    LAST_ACTIVITY_FIELD_NUMBER: _ClassVar[int]
    ENGAGEMENT_LEVEL_FIELD_NUMBER: _ClassVar[int]
    participant: User
    message_count: int
    last_activity: int
    engagement_level: float
    def __init__(self, participant: _Optional[_Union[User, _Mapping]] = ..., message_count: _Optional[int] = ..., last_activity: _Optional[int] = ..., engagement_level: _Optional[float] = ...) -> None: ...

class ChatSessionStatsResponse(_message.Message):
    __slots__ = ("success", "message", "session_id", "total_messages", "total_participants", "participant_stats", "session_start_time", "last_activity_time", "chat_history_enabled")
    SUCCESS_FIELD_NUMBER: _ClassVar[int]
    MESSAGE_FIELD_NUMBER: _ClassVar[int]
    SESSION_ID_FIELD_NUMBER: _ClassVar[int]
    TOTAL_MESSAGES_FIELD_NUMBER: _ClassVar[int]
    TOTAL_PARTICIPANTS_FIELD_NUMBER: _ClassVar[int]
    PARTICIPANT_STATS_FIELD_NUMBER: _ClassVar[int]
    SESSION_START_TIME_FIELD_NUMBER: _ClassVar[int]
    LAST_ACTIVITY_TIME_FIELD_NUMBER: _ClassVar[int]
    CHAT_HISTORY_ENABLED_FIELD_NUMBER: _ClassVar[int]
    success: bool
    message: str
    session_id: str
    total_messages: int
    total_participants: int
    participant_stats: _containers.RepeatedCompositeFieldContainer[ParticipantStats]
    session_start_time: int
    last_activity_time: int
    chat_history_enabled: bool
    def __init__(self, success: bool = ..., message: _Optional[str] = ..., session_id: _Optional[str] = ..., total_messages: _Optional[int] = ..., total_participants: _Optional[int] = ..., participant_stats: _Optional[_Iterable[_Union[ParticipantStats, _Mapping]]] = ..., session_start_time: _Optional[int] = ..., last_activity_time: _Optional[int] = ..., chat_history_enabled: bool = ...) -> None: ...

class ChatMessageRequest(_message.Message):
    __slots__ = ("discussion_session_id", "sender", "message", "timestamp", "use_chat_context", "context_window_size", "store_in_history")
    DISCUSSION_SESSION_ID_FIELD_NUMBER: _ClassVar[int]
    SENDER_FIELD_NUMBER: _ClassVar[int]
    MESSAGE_FIELD_NUMBER: _ClassVar[int]
    TIMESTAMP_FIELD_NUMBER: _ClassVar[int]
    USE_CHAT_CONTEXT_FIELD_NUMBER: _ClassVar[int]
    CONTEXT_WINDOW_SIZE_FIELD_NUMBER: _ClassVar[int]
    STORE_IN_HISTORY_FIELD_NUMBER: _ClassVar[int]
    discussion_session_id: str
    sender: User
    message: str
    timestamp: int
    use_chat_context: bool
    context_window_size: int
    store_in_history: bool
    def __init__(self, discussion_session_id: _Optional[str] = ..., sender: _Optional[_Union[User, _Mapping]] = ..., message: _Optional[str] = ..., timestamp: _Optional[int] = ..., use_chat_context: bool = ..., context_window_size: _Optional[int] = ..., store_in_history: bool = ...) -> None: ...

class ChatMessageResponse(_message.Message):
    __slots__ = ("success", "message", "ai_response", "suggested_topics", "requires_moderation", "context_messages_used", "chat_history_enabled", "recent_context")
    SUCCESS_FIELD_NUMBER: _ClassVar[int]
    MESSAGE_FIELD_NUMBER: _ClassVar[int]
    AI_RESPONSE_FIELD_NUMBER: _ClassVar[int]
    SUGGESTED_TOPICS_FIELD_NUMBER: _ClassVar[int]
    REQUIRES_MODERATION_FIELD_NUMBER: _ClassVar[int]
    CONTEXT_MESSAGES_USED_FIELD_NUMBER: _ClassVar[int]
    CHAT_HISTORY_ENABLED_FIELD_NUMBER: _ClassVar[int]
    RECENT_CONTEXT_FIELD_NUMBER: _ClassVar[int]
    success: bool
    message: str
    ai_response: str
    suggested_topics: _containers.RepeatedScalarFieldContainer[str]
    requires_moderation: bool
    context_messages_used: int
    chat_history_enabled: bool
    recent_context: _containers.RepeatedCompositeFieldContainer[ChatHistoryMessage]
    def __init__(self, success: bool = ..., message: _Optional[str] = ..., ai_response: _Optional[str] = ..., suggested_topics: _Optional[_Iterable[str]] = ..., requires_moderation: bool = ..., context_messages_used: _Optional[int] = ..., chat_history_enabled: bool = ..., recent_context: _Optional[_Iterable[_Union[ChatHistoryMessage, _Mapping]]] = ...) -> None: ...

class DocumentChunk(_message.Message):
    __slots__ = ("chunk_id", "text", "start_position", "end_position", "embedding", "metadata")
    class MetadataEntry(_message.Message):
        __slots__ = ("key", "value")
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

class ProcessDocumentRequest(_message.Message):
    __slots__ = ("document_id", "ocr_results", "file_name", "metadata")
    class MetadataEntry(_message.Message):
        __slots__ = ("key", "value")
        KEY_FIELD_NUMBER: _ClassVar[int]
        VALUE_FIELD_NUMBER: _ClassVar[int]
        key: str
        value: str
        def __init__(self, key: _Optional[str] = ..., value: _Optional[str] = ...) -> None: ...
    DOCUMENT_ID_FIELD_NUMBER: _ClassVar[int]
    OCR_RESULTS_FIELD_NUMBER: _ClassVar[int]
    FILE_NAME_FIELD_NUMBER: _ClassVar[int]
    METADATA_FIELD_NUMBER: _ClassVar[int]
    document_id: str
    ocr_results: _containers.RepeatedCompositeFieldContainer[TextBlock]
    file_name: str
    metadata: _containers.ScalarMap[str, str]
    def __init__(self, document_id: _Optional[str] = ..., ocr_results: _Optional[_Iterable[_Union[TextBlock, _Mapping]]] = ..., file_name: _Optional[str] = ..., metadata: _Optional[_Mapping[str, str]] = ...) -> None: ...

class DocumentResponse(_message.Message):
    __slots__ = ("success", "message", "chunks", "analysis_results")
    class AnalysisResultsEntry(_message.Message):
        __slots__ = ("key", "value")
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

class ProcessPdfRequest(_message.Message):
    __slots__ = ("info", "chunk")
    INFO_FIELD_NUMBER: _ClassVar[int]
    CHUNK_FIELD_NUMBER: _ClassVar[int]
    info: PdfInfo
    chunk: bytes
    def __init__(self, info: _Optional[_Union[PdfInfo, _Mapping]] = ..., chunk: _Optional[bytes] = ...) -> None: ...

class PdfInfo(_message.Message):
    __slots__ = ("document_id", "file_name", "meeting_id", "metadata")
    class MetadataEntry(_message.Message):
        __slots__ = ("key", "value")
        KEY_FIELD_NUMBER: _ClassVar[int]
        VALUE_FIELD_NUMBER: _ClassVar[int]
        key: str
        value: str
        def __init__(self, key: _Optional[str] = ..., value: _Optional[str] = ...) -> None: ...
    DOCUMENT_ID_FIELD_NUMBER: _ClassVar[int]
    FILE_NAME_FIELD_NUMBER: _ClassVar[int]
    MEETING_ID_FIELD_NUMBER: _ClassVar[int]
    METADATA_FIELD_NUMBER: _ClassVar[int]
    document_id: str
    file_name: str
    meeting_id: str
    metadata: _containers.ScalarMap[str, str]
    def __init__(self, document_id: _Optional[str] = ..., file_name: _Optional[str] = ..., meeting_id: _Optional[str] = ..., metadata: _Optional[_Mapping[str, str]] = ...) -> None: ...

class TextBlock(_message.Message):
    __slots__ = ("text", "page_number", "x0", "y0", "x1", "y1", "block_type", "confidence")
    TEXT_FIELD_NUMBER: _ClassVar[int]
    PAGE_NUMBER_FIELD_NUMBER: _ClassVar[int]
    X0_FIELD_NUMBER: _ClassVar[int]
    Y0_FIELD_NUMBER: _ClassVar[int]
    X1_FIELD_NUMBER: _ClassVar[int]
    Y1_FIELD_NUMBER: _ClassVar[int]
    BLOCK_TYPE_FIELD_NUMBER: _ClassVar[int]
    CONFIDENCE_FIELD_NUMBER: _ClassVar[int]
    text: str
    page_number: int
    x0: float
    y0: float
    x1: float
    y1: float
    block_type: str
    confidence: float
    def __init__(self, text: _Optional[str] = ..., page_number: _Optional[int] = ..., x0: _Optional[float] = ..., y0: _Optional[float] = ..., x1: _Optional[float] = ..., y1: _Optional[float] = ..., block_type: _Optional[str] = ..., confidence: _Optional[float] = ...) -> None: ...

class ProcessPdfResponse(_message.Message):
    __slots__ = ("success", "message", "document_id", "total_pages", "text_blocks")
    SUCCESS_FIELD_NUMBER: _ClassVar[int]
    MESSAGE_FIELD_NUMBER: _ClassVar[int]
    DOCUMENT_ID_FIELD_NUMBER: _ClassVar[int]
    TOTAL_PAGES_FIELD_NUMBER: _ClassVar[int]
    TEXT_BLOCKS_FIELD_NUMBER: _ClassVar[int]
    success: bool
    message: str
    document_id: str
    total_pages: int
    text_blocks: _containers.RepeatedCompositeFieldContainer[TextBlock]
    def __init__(self, success: bool = ..., message: _Optional[str] = ..., document_id: _Optional[str] = ..., total_pages: _Optional[int] = ..., text_blocks: _Optional[_Iterable[_Union[TextBlock, _Mapping]]] = ...) -> None: ...

class ErrorDetails(_message.Message):
    __slots__ = ("error_code", "error_message", "error_category")
    ERROR_CODE_FIELD_NUMBER: _ClassVar[int]
    ERROR_MESSAGE_FIELD_NUMBER: _ClassVar[int]
    ERROR_CATEGORY_FIELD_NUMBER: _ClassVar[int]
    error_code: str
    error_message: str
    error_category: str
    def __init__(self, error_code: _Optional[str] = ..., error_message: _Optional[str] = ..., error_category: _Optional[str] = ...) -> None: ...
