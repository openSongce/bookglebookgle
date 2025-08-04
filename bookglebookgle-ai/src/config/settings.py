"""
Configuration settings for BGBG AI Server
"""

from functools import lru_cache
from pathlib import Path
from typing import Optional

from pydantic import BaseModel, Field
from pydantic_settings import BaseSettings


class DatabaseSettings(BaseModel):
    """Database configuration"""
    MYSQL_HOST: str = Field(default="localhost", description="MySQL host")
    MYSQL_PORT: int = Field(default=3306, description="MySQL port")
    MYSQL_USER: str = Field(default="", description="MySQL username")
    MYSQL_PASSWORD: str = Field(default="", description="MySQL password")
    MYSQL_DATABASE: str = Field(default="bgbg_ai", description="MySQL database name")

    # Redis configuration
    REDIS_HOST: str = Field(default="redis", description="Redis host")
    REDIS_PORT: int = Field(default=6379, description="Redis port")
    REDIS_DB: int = Field(default=0, description="Redis database number")
    REDIS_PASSWORD: Optional[str] = Field(default=None, description="Redis password")
    REDIS_MAX_CONNECTIONS: int = Field(default=20, description="Redis max connections")
    REDIS_SOCKET_TIMEOUT: int = Field(default=5, description="Redis socket timeout in seconds")
    REDIS_SOCKET_CONNECT_TIMEOUT: int = Field(default=5, description="Redis socket connect timeout in seconds")


class AISettings(BaseModel):
    """AI service configuration"""
    OPENAI_API_KEY: Optional[str] = Field(default=None, description="OpenAI API key")
    ANTHROPIC_API_KEY: Optional[str] = Field(default=None, description="Anthropic API key")
    OPENROUTER_API_KEY: Optional[str] = Field(default=None, description="OpenRouter API key")
    OPENROUTER_BASE_URL: str = Field(default="https://openrouter.ai/api/v1", description="OpenRouter base URL")
    OPENROUTER_MODEL: str = Field(default="google/gemma-3n-e2b-it:free", description="Default OpenRouter model")
    
    # Gemini API settings
    GEMINI_API_KEY: Optional[str] = Field(default=None, description="Google Gemini API key")
    GEMINI_MODEL: str = Field(default="gemini-1.5-flash", description="Default Gemini model")
    GEMINI_BASE_URL: str = Field(default="https://generativelanguage.googleapis.com/v1beta", description="Gemini base URL")
    
    # GMS API settings (SSAFY Anthropic proxy)
    GMS_API_KEY: Optional[str] = Field(default=None, description="GMS API key")
    GMS_BASE_URL: str = Field(default="https://gms.ssafy.io/gmsapi/api.anthropic.com/v1", description="GMS API base URL")
    GMS_DEV_MODEL: str = Field(default="claude-3-5-haiku-latest", description="GMS development model")
    GMS_PROD_MODEL: str = Field(default="claude-3-5-sonnet-latest", description="GMS production model")

    # Feature flags
    ENABLE_QUIZ_GENERATION: bool = Field(default=True, description="Enable quiz generation")
    ENABLE_PROOFREADING: bool = Field(default=True, description="Enable proofreading")
    ENABLE_DISCUSSION_AI: bool = Field(default=True, description="Enable discussion AI")
    
    # Model configurations
    KOREAN_MODEL_PATH: str = Field(default="./models/korean", description="Korean model path")
    SPACY_MODEL: str = Field(default="ko_core_news_sm", description="SpaCy model for Korean")
    
    # Mock responses for development
    MOCK_AI_RESPONSES: bool = Field(default=False, description="Use mock AI responses")


class VectorDBSettings(BaseModel):
    """Vector database configuration"""
    CHROMA_PERSIST_DIRECTORY: str = Field(default="./data/chroma", description="Chroma persistence directory")
    PINECONE_API_KEY: Optional[str] = Field(default=None, description="Pinecone API key")
    PINECONE_ENVIRONMENT: Optional[str] = Field(default=None, description="Pinecone environment")


class ChatHistorySettings(BaseModel):
    """Chat history and context configuration"""
    # Feature toggle
    CHAT_HISTORY_ENABLED: bool = Field(default=True, description="Enable chat history context feature")
    
    # TTL settings (in hours)
    CHAT_MESSAGE_TTL_HOURS: int = Field(default=24, description="Chat message TTL in hours")
    CHAT_CONTEXT_TTL_HOURS: int = Field(default=1, description="Chat context TTL in hours")
    CHAT_PARTICIPANT_TTL_HOURS: int = Field(default=2, description="Chat participant TTL in hours")
    CHAT_META_TTL_HOURS: int = Field(default=168, description="Chat metadata TTL in hours (7 days)")
    
    # Context window settings
    CHAT_CONTEXT_WINDOW_SIZE: int = Field(default=10, description="Maximum messages in context window")
    CHAT_MAX_TOKENS: int = Field(default=2000, description="Maximum tokens for context")
    CHAT_TIME_WINDOW_HOURS: int = Field(default=2, description="Time window for recent messages in hours")
    CHAT_MAX_BOOK_CHUNKS: int = Field(default=3, description="Maximum book context chunks")
    
    # Summarization settings
    CHAT_ENABLE_SUMMARIZATION: bool = Field(default=True, description="Enable context summarization")
    CHAT_SUMMARIZATION_THRESHOLD: int = Field(default=1500, description="Token threshold for summarization")
    
    # Performance settings
    CHAT_REDIS_MEMORY_LIMIT_MB: int = Field(default=512, description="Redis memory limit in MB")
    CHAT_SESSION_CLEANUP_INTERVAL_MINUTES: int = Field(default=30, description="Session cleanup interval in minutes")
    CHAT_INACTIVE_THRESHOLD_MINUTES: int = Field(default=30, description="Inactive participant threshold in minutes")
    
    # Analysis settings
    CHAT_ENABLE_SENTIMENT_ANALYSIS: bool = Field(default=True, description="Enable sentiment analysis")
    CHAT_ENABLE_TOPIC_DETECTION: bool = Field(default=True, description="Enable topic change detection")
    CHAT_TOPIC_CHANGE_THRESHOLD: float = Field(default=0.7, description="Topic change detection threshold")
    
    # Intervention settings
    CHAT_ENABLE_AI_INTERVENTION: bool = Field(default=True, description="Enable AI intervention suggestions")
    CHAT_INTERVENTION_COOLDOWN_MINUTES: int = Field(default=5, description="AI intervention cooldown in minutes")
    CHAT_MAX_INTERVENTIONS_PER_HOUR: int = Field(default=10, description="Maximum AI interventions per hour")


class GRPCSettings(BaseModel):
    """gRPC server configuration"""
    GRPC_MAX_MESSAGE_LENGTH: int = Field(default=4194304, description="Max gRPC message length (4MB)")
    GRPC_MAX_CONNECTION_IDLE: int = Field(default=30, description="Max connection idle time in seconds")


class Settings(BaseSettings):
    """Main application settings"""
    
    # Server settings
    DEBUG: bool = True
    SERVER_HOST: str = "0.0.0.0"
    SERVER_PORT: int = 50505
    SERVER_WORKERS: int = 10
    SERVER_START_RETRIES: int = 3
    SERVER_RETRY_DELAY: int = 2  # seconds
    LOG_LEVEL: str = Field(default="INFO", description="Log level")
    
    # Cache configuration
    CACHE_TTL: int = Field(default=3600, description="Cache TTL in seconds")
    
    # Monitoring
    PROMETHEUS_PORT: int = Field(default=9090, description="Prometheus metrics port")
    ENABLE_METRICS: bool = Field(default=True, description="Enable Prometheus metrics")
    
    # Sub-configurations
    database: DatabaseSettings = Field(default_factory=DatabaseSettings)
    ai: AISettings = Field(default_factory=AISettings)
    vector_db: VectorDBSettings = Field(default_factory=VectorDBSettings)
    chat_history: ChatHistorySettings = Field(default_factory=ChatHistorySettings)
    grpc: GRPCSettings = Field(default_factory=GRPCSettings)
    
    class Config:
        env_file = ".env"
        env_file_encoding = "utf-8"
        extra = "allow"
        
        # Nested model configuration
        env_nested_delimiter = "__"


@lru_cache()
def get_settings() -> Settings:
    """Get cached application settings"""
    return Settings()


def get_project_root() -> Path:
    """Get project root directory"""
    return Path(__file__).parent.parent.parent