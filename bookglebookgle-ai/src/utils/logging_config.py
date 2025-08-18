"""
Logging configuration for BGBG AI Server
"""

import sys
from pathlib import Path
from typing import Optional

from loguru import logger


def setup_logging(log_level: str = "INFO", log_file: Optional[str] = None) -> None:
    """
    Setup logging configuration with loguru
    
    Args:
        log_level: Log level (DEBUG, INFO, WARNING, ERROR, CRITICAL)
        log_file: Optional log file path
    """
    
    # Remove default logger
    logger.remove()
    
    # Console logging with colors
    logger.add(
        sys.stdout,
        level=log_level,
        format="<green>{time:YYYY-MM-DD HH:mm:ss}</green> | "
               "<level>{level: <8}</level> | "
               "<cyan>{name}</cyan>:<cyan>{function}</cyan>:<cyan>{line}</cyan> | "
               "<level>{message}</level>",
        colorize=True,
        backtrace=True,
        diagnose=True
    )
    
    # File logging if specified
    if log_file:
        log_path = Path(log_file)
        log_path.parent.mkdir(parents=True, exist_ok=True)
        
        logger.add(
            log_path,
            level=log_level,
            format="{time:YYYY-MM-DD HH:mm:ss} | {level: <8} | {name}:{function}:{line} | {message}",
            rotation="1 day",
            retention="30 days",
            compression="zip",
            backtrace=True,
            diagnose=True
        )
    
    # Application-specific loggers
    logger.add(
        "logs/ai_server.log",
        level="INFO",
        format="{time:YYYY-MM-DD HH:mm:ss} | {level: <8} | {name}:{function}:{line} | {message}",
        rotation="1 day",
        retention="7 days",
        filter=lambda record: record["name"].startswith("src.")
    )
    
    # Error-only log file
    logger.add(
        "logs/errors.log",
        level="ERROR",
        format="{time:YYYY-MM-DD HH:mm:ss} | {level: <8} | {name}:{function}:{line} | {message}\n{exception}",
        rotation="1 week",
        retention="4 weeks",
        backtrace=True,
        diagnose=True
    )


def get_logger(name: str):
    """Get a logger instance for a specific module"""
    return logger.bind(name=name)