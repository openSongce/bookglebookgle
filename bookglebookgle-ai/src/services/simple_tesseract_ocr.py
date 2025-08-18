"""
Simple Tesseract OCR Service
Tesseract 단일 엔진을 사용한 간소화된 OCR 서비스
LLM 후처리를 통한 텍스트 정확도 향상 지원
"""

import asyncio
import io
import time
from typing import Dict, List, Any, Optional, Tuple
from dataclasses import dataclass

import fitz  # PyMuPDF
import pytesseract
from PIL import Image, ImageEnhance, ImageFilter, ImageOps
from loguru import logger

from src.services.llm_client import LLMClient


@dataclass
class OCRBlock:
    """OCR 결과 블록"""
    text: str
    page_number: int
    x0: float
    y0: float
    x1: float
    y1: float
    confidence: float
    block_type: str = "text"


@dataclass
class OCRConfig:
    """Tesseract OCR 설정"""
    languages: str = 'kor+eng'  # 한국어 + 영어
    oem: int = 3  # LSTM OCR Engine Mode
    psm: int = 6  # Uniform