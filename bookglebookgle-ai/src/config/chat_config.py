"""
Chat History Configuration Helper Functions
Provides convenient access to chat history settings and validation
"""

import os
import json
import logging
from datetime import timedelta
from typing import Optional, Dict, Any, List
from dataclasses import dataclass, asdict
from enum import Enum

from src.config.settings import get_settings
from src.models.chat_history_models import ContextConfig

logger = logging.getLogger(__name__)


def get_chat_history_config() -> ContextConfig:
    """
    Get ContextConfig from application settings
    
    Returns:
        ContextConfig: Configuration for conversation context management
    """
    settings = get_settings()
    
    return ContextConfig(
        max_messages=settings.chat_history.CHAT_CONTEXT_WINDOW_SIZE,
        max_tokens=settings.chat_history.CHAT_MAX_TOKENS,
        time_window=timedelta(hours=settings.chat_history.CHAT_TIME_WINDOW_HOURS),
        max_book_chunks=settings.chat_history.CHAT_MAX_BOOK_CHUNKS,
        enable_summarization=settings.chat_history.CHAT_ENABLE_SUMMARIZATION,
        summarization_threshold=settings.chat_history.CHAT_SUMMARIZATION_THRESHOLD
    )


def is_chat_history_enabled() -> bool:
    """
    Check if chat history feature is enabled
    
    Returns:
        bool: True if chat history is enabled
    """
    settings = get_settings()
    return settings.chat_history.CHAT_HISTORY_ENABLED


def get_redis_config() -> dict:
    """
    Get Redis configuration dictionary
    
    Returns:
        dict: Redis connection configuration
    """
    settings = get_settings()
    
    config = {
        'host': settings.database.REDIS_HOST,
        'port': settings.database.REDIS_PORT,
        'db': settings.database.REDIS_DB,
        'max_connections': settings.database.REDIS_MAX_CONNECTIONS,
        'socket_timeout': settings.database.REDIS_SOCKET_TIMEOUT,
        'socket_connect_timeout': settings.database.REDIS_SOCKET_CONNECT_TIMEOUT,
        'decode_responses': True,
        'retry_on_timeout': True
    }
    
    # Add password if configured
    if settings.database.REDIS_PASSWORD:
        config['password'] = settings.database.REDIS_PASSWORD
    
    return config


def get_ttl_config() -> dict:
    """
    Get TTL configuration in seconds
    
    Returns:
        dict: TTL configuration for different data types
    """
    settings = get_settings()
    
    return {
        'message_ttl': settings.chat_history.CHAT_MESSAGE_TTL_HOURS * 3600,
        'context_ttl': settings.chat_history.CHAT_CONTEXT_TTL_HOURS * 3600,
        'participant_ttl': settings.chat_history.CHAT_PARTICIPANT_TTL_HOURS * 3600,
        'meta_ttl': settings.chat_history.CHAT_META_TTL_HOURS * 3600
    }


def get_analysis_config() -> dict:
    """
    Get conversation analysis configuration
    
    Returns:
        dict: Analysis configuration settings
    """
    settings = get_settings()
    
    return {
        'enable_sentiment_analysis': settings.chat_history.CHAT_ENABLE_SENTIMENT_ANALYSIS,
        'enable_topic_detection': settings.chat_history.CHAT_ENABLE_TOPIC_DETECTION,
        'topic_change_threshold': settings.chat_history.CHAT_TOPIC_CHANGE_THRESHOLD,
        'inactive_threshold_minutes': settings.chat_history.CHAT_INACTIVE_THRESHOLD_MINUTES
    }


def get_intervention_config() -> dict:
    """
    Get AI intervention configuration
    
    Returns:
        dict: Intervention configuration settings
    """
    settings = get_settings()
    
    return {
        'enable_ai_intervention': settings.chat_history.CHAT_ENABLE_AI_INTERVENTION,
        'intervention_cooldown_minutes': settings.chat_history.CHAT_INTERVENTION_COOLDOWN_MINUTES,
        'max_interventions_per_hour': settings.chat_history.CHAT_MAX_INTERVENTIONS_PER_HOUR
    }


def get_performance_config() -> dict:
    """
    Get performance-related configuration
    
    Returns:
        dict: Performance configuration settings
    """
    settings = get_settings()
    
    return {
        'redis_memory_limit_mb': settings.chat_history.CHAT_REDIS_MEMORY_LIMIT_MB,
        'session_cleanup_interval_minutes': settings.chat_history.CHAT_SESSION_CLEANUP_INTERVAL_MINUTES,
        'context_window_size': settings.chat_history.CHAT_CONTEXT_WINDOW_SIZE,
        'max_tokens': settings.chat_history.CHAT_MAX_TOKENS
    }


def validate_chat_settings() -> list:
    """
    Validate chat history settings and return any issues
    
    Returns:
        list: List of validation issues (empty if all valid)
    """
    settings = get_settings()
    issues = []
    
    # Validate TTL settings
    if settings.chat_history.CHAT_MESSAGE_TTL_HOURS <= 0:
        issues.append("CHAT_MESSAGE_TTL_HOURS must be positive")
    
    if settings.chat_history.CHAT_CONTEXT_TTL_HOURS <= 0:
        issues.append("CHAT_CONTEXT_TTL_HOURS must be positive")
    
    # Validate context settings
    if settings.chat_history.CHAT_CONTEXT_WINDOW_SIZE <= 0:
        issues.append("CHAT_CONTEXT_WINDOW_SIZE must be positive")
    
    if settings.chat_history.CHAT_MAX_TOKENS <= 0:
        issues.append("CHAT_MAX_TOKENS must be positive")
    
    # Validate thresholds
    if not 0.0 <= settings.chat_history.CHAT_TOPIC_CHANGE_THRESHOLD <= 1.0:
        issues.append("CHAT_TOPIC_CHANGE_THRESHOLD must be between 0.0 and 1.0")
    
    # Validate Redis settings
    if settings.database.REDIS_PORT <= 0 or settings.database.REDIS_PORT > 65535:
        issues.append("REDIS_PORT must be between 1 and 65535")
    
    if settings.database.REDIS_MAX_CONNECTIONS <= 0:
        issues.append("REDIS_MAX_CONNECTIONS must be positive")
    
    return issues


def get_environment_variables_template() -> str:
    """
    Get template for environment variables
    
    Returns:
        str: Environment variables template
    """
    return """
# Chat History Configuration
CHAT_HISTORY__CHAT_HISTORY_ENABLED=true

# TTL Settings (in hours)
CHAT_HISTORY__CHAT_MESSAGE_TTL_HOURS=24
CHAT_HISTORY__CHAT_CONTEXT_TTL_HOURS=1
CHAT_HISTORY__CHAT_PARTICIPANT_TTL_HOURS=2
CHAT_HISTORY__CHAT_META_TTL_HOURS=168

# Context Window Settings
CHAT_HISTORY__CHAT_CONTEXT_WINDOW_SIZE=10
CHAT_HISTORY__CHAT_MAX_TOKENS=2000
CHAT_HISTORY__CHAT_TIME_WINDOW_HOURS=2
CHAT_HISTORY__CHAT_MAX_BOOK_CHUNKS=3

# Summarization Settings
CHAT_HISTORY__CHAT_ENABLE_SUMMARIZATION=true
CHAT_HISTORY__CHAT_SUMMARIZATION_THRESHOLD=1500

# Performance Settings
CHAT_HISTORY__CHAT_REDIS_MEMORY_LIMIT_MB=512
CHAT_HISTORY__CHAT_SESSION_CLEANUP_INTERVAL_MINUTES=30
CHAT_HISTORY__CHAT_INACTIVE_THRESHOLD_MINUTES=30

# Analysis Settings
CHAT_HISTORY__CHAT_ENABLE_SENTIMENT_ANALYSIS=true
CHAT_HISTORY__CHAT_ENABLE_TOPIC_DETECTION=true
CHAT_HISTORY__CHAT_TOPIC_CHANGE_THRESHOLD=0.7

# Intervention Settings
CHAT_HISTORY__CHAT_ENABLE_AI_INTERVENTION=true
CHAT_HISTORY__CHAT_INTERVENTION_COOLDOWN_MINUTES=5
CHAT_HISTORY__CHAT_MAX_INTERVENTIONS_PER_HOUR=10

# Redis Configuration
DATABASE__REDIS_HOST=localhost
DATABASE__REDIS_PORT=6379
DATABASE__REDIS_DB=0
DATABASE__REDIS_PASSWORD=
DATABASE__REDIS_MAX_CONNECTIONS=20
DATABASE__REDIS_SOCKET_TIMEOUT=5
DATABASE__REDIS_SOCKET_CONNECT_TIMEOUT=5
"""


class ConfigurationLevel(str, Enum):
    """Configuration levels for different environments"""
    DEVELOPMENT = "development"
    TESTING = "testing"
    STAGING = "staging"
    PRODUCTION = "production"


@dataclass
class ChatHistoryConfig:
    """
    Comprehensive chat history configuration management
    """
    # Core settings
    enabled: bool = True
    configuration_level: ConfigurationLevel = ConfigurationLevel.DEVELOPMENT
    
    # TTL settings (in hours)
    message_ttl_hours: int = 24
    context_ttl_hours: int = 1
    participant_ttl_hours: int = 2
    meta_ttl_hours: int = 168  # 1 week
    
    # Context window settings
    context_window_size: int = 10
    max_tokens: int = 2000
    time_window_hours: int = 2
    max_book_chunks: int = 3
    
    # Summarization settings
    enable_summarization: bool = True
    summarization_threshold: int = 1500
    
    # Performance settings
    redis_memory_limit_mb: int = 512
    session_cleanup_interval_minutes: int = 30
    inactive_threshold_minutes: int = 30
    
    # Analysis settings
    enable_sentiment_analysis: bool = True
    enable_topic_detection: bool = True
    topic_change_threshold: float = 0.7
    
    # Intervention settings
    enable_ai_intervention: bool = True
    intervention_cooldown_minutes: int = 5
    max_interventions_per_hour: int = 10
    
    # Privacy settings
    enable_pii_detection: bool = True
    enable_content_masking: bool = True
    anonymize_user_ids: bool = False
    data_retention_days: int = 7
    
    # Encryption settings
    enable_encryption: bool = False
    encryption_level: str = "basic"
    key_rotation_days: int = 30
    
    # Monitoring settings
    enable_performance_monitoring: bool = True
    monitor_interval_seconds: int = 30
    alert_thresholds: Dict[str, float] = None
    
    def __post_init__(self):
        """Post-initialization setup"""
        if self.alert_thresholds is None:
            self.alert_thresholds = {
                "response_time_warning": 2.0,
                "response_time_critical": 5.0,
                "memory_usage_warning": 70.0,
                "memory_usage_critical": 85.0,
                "error_rate_warning": 5.0,
                "error_rate_critical": 10.0
            }
    
    @classmethod
    def from_environment(cls) -> 'ChatHistoryConfig':
        """
        Create configuration from environment variables
        
        Returns:
            ChatHistoryConfig: Configuration loaded from environment
        """
        try:
            settings = get_settings()
            
            config = cls(
                enabled=settings.chat_history.CHAT_HISTORY_ENABLED,
                message_ttl_hours=settings.chat_history.CHAT_MESSAGE_TTL_HOURS,
                context_ttl_hours=settings.chat_history.CHAT_CONTEXT_TTL_HOURS,
                participant_ttl_hours=settings.chat_history.CHAT_PARTICIPANT_TTL_HOURS,
                meta_ttl_hours=settings.chat_history.CHAT_META_TTL_HOURS,
                context_window_size=settings.chat_history.CHAT_CONTEXT_WINDOW_SIZE,
                max_tokens=settings.chat_history.CHAT_MAX_TOKENS,
                time_window_hours=settings.chat_history.CHAT_TIME_WINDOW_HOURS,
                max_book_chunks=settings.chat_history.CHAT_MAX_BOOK_CHUNKS,
                enable_summarization=settings.chat_history.CHAT_ENABLE_SUMMARIZATION,
                summarization_threshold=settings.chat_history.CHAT_SUMMARIZATION_THRESHOLD,
                redis_memory_limit_mb=settings.chat_history.CHAT_REDIS_MEMORY_LIMIT_MB,
                session_cleanup_interval_minutes=settings.chat_history.CHAT_SESSION_CLEANUP_INTERVAL_MINUTES,
                inactive_threshold_minutes=settings.chat_history.CHAT_INACTIVE_THRESHOLD_MINUTES,
                enable_sentiment_analysis=settings.chat_history.CHAT_ENABLE_SENTIMENT_ANALYSIS,
                enable_topic_detection=settings.chat_history.CHAT_ENABLE_TOPIC_DETECTION,
                topic_change_threshold=settings.chat_history.CHAT_TOPIC_CHANGE_THRESHOLD,
                enable_ai_intervention=settings.chat_history.CHAT_ENABLE_AI_INTERVENTION,
                intervention_cooldown_minutes=settings.chat_history.CHAT_INTERVENTION_COOLDOWN_MINUTES,
                max_interventions_per_hour=settings.chat_history.CHAT_MAX_INTERVENTIONS_PER_HOUR
            )
            
            # Determine configuration level from environment
            env_level = os.getenv('CHAT_CONFIG_LEVEL', 'development').lower()
            if env_level in [level.value for level in ConfigurationLevel]:
                config.configuration_level = ConfigurationLevel(env_level)
            
            # Load additional settings from environment
            config.enable_pii_detection = os.getenv('CHAT_ENABLE_PII_DETECTION', 'true').lower() == 'true'
            config.enable_content_masking = os.getenv('CHAT_ENABLE_CONTENT_MASKING', 'true').lower() == 'true'
            config.anonymize_user_ids = os.getenv('CHAT_ANONYMIZE_USER_IDS', 'false').lower() == 'true'
            config.data_retention_days = int(os.getenv('CHAT_DATA_RETENTION_DAYS', '7'))
            config.enable_encryption = os.getenv('CHAT_ENABLE_ENCRYPTION', 'false').lower() == 'true'
            config.encryption_level = os.getenv('CHAT_ENCRYPTION_LEVEL', 'basic')
            config.key_rotation_days = int(os.getenv('CHAT_KEY_ROTATION_DAYS', '30'))
            config.enable_performance_monitoring = os.getenv('CHAT_ENABLE_PERFORMANCE_MONITORING', 'true').lower() == 'true'
            config.monitor_interval_seconds = int(os.getenv('CHAT_MONITOR_INTERVAL_SECONDS', '30'))
            
            return config
            
        except Exception as e:
            logger.error(f"Failed to load configuration from environment: {e}")
            return cls()  # Return default configuration
    
    @classmethod
    def from_file(cls, config_path: str) -> 'ChatHistoryConfig':
        """
        Load configuration from JSON file
        
        Args:
            config_path: Path to configuration file
            
        Returns:
            ChatHistoryConfig: Configuration loaded from file
        """
        try:
            with open(config_path, 'r', encoding='utf-8') as f:
                config_data = json.load(f)
            
            return cls(**config_data)
            
        except Exception as e:
            logger.error(f"Failed to load configuration from file {config_path}: {e}")
            return cls()
    
    def to_file(self, config_path: str) -> bool:
        """
        Save configuration to JSON file
        
        Args:
            config_path: Path to save configuration
            
        Returns:
            bool: True if saved successfully
        """
        try:
            config_dict = asdict(self)
            
            # Convert enum to string
            config_dict['configuration_level'] = self.configuration_level.value
            
            with open(config_path, 'w', encoding='utf-8') as f:
                json.dump(config_dict, f, indent=2, ensure_ascii=False)
            
            logger.info(f"Configuration saved to {config_path}")
            return True
            
        except Exception as e:
            logger.error(f"Failed to save configuration to {config_path}: {e}")
            return False
    
    def validate(self) -> List[str]:
        """
        Validate configuration settings
        
        Returns:
            List[str]: List of validation issues (empty if valid)
        """
        issues = []
        
        # Validate TTL settings
        if self.message_ttl_hours <= 0:
            issues.append("message_ttl_hours must be positive")
        
        if self.context_ttl_hours <= 0:
            issues.append("context_ttl_hours must be positive")
        
        if self.participant_ttl_hours <= 0:
            issues.append("participant_ttl_hours must be positive")
        
        # Validate context settings
        if self.context_window_size <= 0:
            issues.append("context_window_size must be positive")
        
        if self.max_tokens <= 0:
            issues.append("max_tokens must be positive")
        
        if self.time_window_hours <= 0:
            issues.append("time_window_hours must be positive")
        
        # Validate thresholds
        if not 0.0 <= self.topic_change_threshold <= 1.0:
            issues.append("topic_change_threshold must be between 0.0 and 1.0")
        
        # Validate performance settings
        if self.redis_memory_limit_mb <= 0:
            issues.append("redis_memory_limit_mb must be positive")
        
        if self.session_cleanup_interval_minutes <= 0:
            issues.append("session_cleanup_interval_minutes must be positive")
        
        # Validate intervention settings
        if self.intervention_cooldown_minutes <= 0:
            issues.append("intervention_cooldown_minutes must be positive")
        
        if self.max_interventions_per_hour <= 0:
            issues.append("max_interventions_per_hour must be positive")
        
        # Validate privacy settings
        if self.data_retention_days <= 0:
            issues.append("data_retention_days must be positive")
        
        # Validate encryption settings
        if self.encryption_level not in ['none', 'basic', 'advanced', 'hybrid']:
            issues.append("encryption_level must be one of: none, basic, advanced, hybrid")
        
        if self.key_rotation_days <= 0:
            issues.append("key_rotation_days must be positive")
        
        # Validate monitoring settings
        if self.monitor_interval_seconds <= 0:
            issues.append("monitor_interval_seconds must be positive")
        
        return issues
    
    def apply_environment_level_overrides(self) -> None:
        """Apply configuration overrides based on environment level"""
        if self.configuration_level == ConfigurationLevel.DEVELOPMENT:
            # Development settings - more verbose, shorter TTLs
            self.message_ttl_hours = min(self.message_ttl_hours, 4)
            self.enable_performance_monitoring = True
            self.monitor_interval_seconds = 10
            
        elif self.configuration_level == ConfigurationLevel.TESTING:
            # Testing settings - minimal TTLs, all features enabled
            self.message_ttl_hours = 1
            self.context_ttl_hours = 1
            self.enable_encryption = False  # Disable for faster tests
            self.enable_performance_monitoring = False
            
        elif self.configuration_level == ConfigurationLevel.STAGING:
            # Staging settings - production-like but with more monitoring
            self.enable_performance_monitoring = True
            self.monitor_interval_seconds = 15
            
        elif self.configuration_level == ConfigurationLevel.PRODUCTION:
            # Production settings - optimized for performance and security
            self.enable_encryption = True
            self.enable_pii_detection = True
            self.enable_content_masking = True
            self.monitor_interval_seconds = 60
    
    def get_context_config(self) -> ContextConfig:
        """
        Convert to ContextConfig for backward compatibility
        
        Returns:
            ContextConfig: Context configuration
        """
        return ContextConfig(
            max_messages=self.context_window_size,
            max_tokens=self.max_tokens,
            time_window=timedelta(hours=self.time_window_hours),
            max_book_chunks=self.max_book_chunks,
            enable_summarization=self.enable_summarization,
            summarization_threshold=self.summarization_threshold
        )
    
    def to_dict(self) -> Dict[str, Any]:
        """
        Convert configuration to dictionary
        
        Returns:
            Dict[str, Any]: Configuration as dictionary
        """
        config_dict = asdict(self)
        config_dict['configuration_level'] = self.configuration_level.value
        return config_dict
    
    def update_from_dict(self, updates: Dict[str, Any]) -> List[str]:
        """
        Update configuration from dictionary
        
        Args:
            updates: Dictionary of updates to apply
            
        Returns:
            List[str]: List of validation issues after update
        """
        try:
            # Apply updates
            for key, value in updates.items():
                if hasattr(self, key):
                    # Handle enum conversion
                    if key == 'configuration_level' and isinstance(value, str):
                        value = ConfigurationLevel(value)
                    
                    setattr(self, key, value)
                else:
                    logger.warning(f"Unknown configuration key: {key}")
            
            # Validate after updates
            return self.validate()
            
        except Exception as e:
            logger.error(f"Failed to update configuration: {e}")
            return [f"Update failed: {e}"]
    
    def get_runtime_info(self) -> Dict[str, Any]:
        """
        Get runtime information about the configuration
        
        Returns:
            Dict[str, Any]: Runtime configuration information
        """
        return {
            "configuration_level": self.configuration_level.value,
            "enabled": self.enabled,
            "features_enabled": {
                "summarization": self.enable_summarization,
                "sentiment_analysis": self.enable_sentiment_analysis,
                "topic_detection": self.enable_topic_detection,
                "ai_intervention": self.enable_ai_intervention,
                "pii_detection": self.enable_pii_detection,
                "content_masking": self.enable_content_masking,
                "encryption": self.enable_encryption,
                "performance_monitoring": self.enable_performance_monitoring
            },
            "ttl_settings": {
                "message_ttl_hours": self.message_ttl_hours,
                "context_ttl_hours": self.context_ttl_hours,
                "participant_ttl_hours": self.participant_ttl_hours,
                "meta_ttl_hours": self.meta_ttl_hours
            },
            "performance_settings": {
                "context_window_size": self.context_window_size,
                "max_tokens": self.max_tokens,
                "redis_memory_limit_mb": self.redis_memory_limit_mb,
                "session_cleanup_interval_minutes": self.session_cleanup_interval_minutes
            }
        }


# Global configuration instance
_chat_config: Optional[ChatHistoryConfig] = None


def get_chat_config() -> ChatHistoryConfig:
    """
    Get global chat history configuration instance
    
    Returns:
        ChatHistoryConfig: Global configuration instance
    """
    global _chat_config
    
    if _chat_config is None:
        _chat_config = ChatHistoryConfig.from_environment()
        _chat_config.apply_environment_level_overrides()
    
    return _chat_config


def reload_chat_config() -> ChatHistoryConfig:
    """
    Reload chat history configuration from environment
    
    Returns:
        ChatHistoryConfig: Reloaded configuration
    """
    global _chat_config
    
    _chat_config = ChatHistoryConfig.from_environment()
    _chat_config.apply_environment_level_overrides()
    
    logger.info("Chat history configuration reloaded")
    return _chat_config


def update_chat_config(updates: Dict[str, Any]) -> List[str]:
    """
    Update global chat configuration
    
    Args:
        updates: Dictionary of configuration updates
        
    Returns:
        List[str]: List of validation issues
    """
    config = get_chat_config()
    issues = config.update_from_dict(updates)
    
    if not issues:
        logger.info("Chat configuration updated successfully")
    else:
        logger.warning(f"Chat configuration update had issues: {issues}")
    
    return issues