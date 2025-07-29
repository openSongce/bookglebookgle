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

    REDIS_HOST: str = Field(default="localhost", description="Redis host")
    REDIS_PORT: int = Field(default=6379, description="Redis port")
    REDIS_DB: int = Field(default=0, description="Redis database number")


class AISettings(BaseModel):
    """AI service configuration"""
    OPENAI_API_KEY: Optional[str] = Field(default=None, description="OpenAI API key")
    ANTHROPIC_API_KEY: Optional[str] = Field(default=None, description="Anthropic API key")
    OPENROUTER_API_KEY: Optional[str] = Field(default=None, description="OpenRouter API key")
    OPENROUTER_BASE_URL: str = Field(default="https://openrouter.ai/api/v1", description="OpenRouter base URL")
    OPENROUTER_MODEL: str = Field(default="google/gemma-3n-e2b-it:free", description="Default OpenRouter model")

    # Feature flags
    ENABLE_QUIZ_GENERATION: bool = Field(default=True, description="Enable quiz generation")
    ENABLE_PROOFREADING: bool = Field(default=True, description="Enable proofreading")
    ENABLE_DISCUSSION_AI: bool = Field(default=True, description="Enable discussion AI")
    ENABLE_USER_ANALYTICS: bool = Field(default=True, description="Enable user analytics")
    
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


class GRPCSettings(BaseModel):
    """gRPC server configuration"""
    GRPC_MAX_MESSAGE_LENGTH: int = Field(default=4194304, description="Max gRPC message length (4MB)")
    GRPC_MAX_CONNECTION_IDLE: int = Field(default=30, description="Max connection idle time in seconds")


class Settings(BaseSettings):
    """Main application settings"""
    
    # Server configuration
    SERVER_HOST: str = Field(default="0.0.0.0", description="Server host")
    SERVER_PORT: int = Field(default=50052, description="Server port")
    DEBUG: bool = Field(default=False, description="Debug mode")
    LOG_LEVEL: str = Field(default="INFO", description="Log level")
    
    # Cache configuration
    CACHE_TTL: int = Field(default=3600, description="Cache TTL in seconds")
    
    # Monitoring
    PROMETHEUS_PORT: int = Field(default=8000, description="Prometheus metrics port")
    ENABLE_METRICS: bool = Field(default=True, description="Enable Prometheus metrics")
    
    # Sub-configurations
    database: DatabaseSettings = Field(default_factory=DatabaseSettings)
    ai: AISettings = Field(default_factory=AISettings)
    vector_db: VectorDBSettings = Field(default_factory=VectorDBSettings)
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