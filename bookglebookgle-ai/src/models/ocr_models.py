"""
OCR 관련 데이터 모델
Tesseract OCR + LLM 후처리 시스템을 위한 데이터 구조 정의
"""

from dataclasses import dataclass, field
from typing import List, Optional, Dict, Any
from enum import Enum


class CorrectionType(Enum):
    """텍스트 교정 유형"""
    SPELLING = "spelling"      # 맞춤법 교정
    GRAMMAR = "grammar"        # 문법 교정
    SPACING = "spacing"        # 띄어쓰기 교정
    STYLE = "style"           # 문체 개선
    PUNCTUATION = "punctuation"  # 구두점 교정


@dataclass
class BoundingBox:
    """텍스트 위치 정보 (바운딩 박스)"""
    x0: float  # 좌측 상단 X 좌표
    y0: float  # 좌측 상단 Y 좌표
    x1: float  # 우측 하단 X 좌표
    y1: float  # 우측 하단 Y 좌표
    
    def __post_init__(self):
        """좌표 유효성 검증"""
        if self.x0 >= self.x1 or self.y0 >= self.y1:
            raise ValueError(f"Invalid bounding box coordinates: ({self.x0}, {self.y0}, {self.x1}, {self.y1})")
    
    @property
    def width(self) -> float:
        """바운딩 박스 너비"""
        return self.x1 - self.x0
    
    @property
    def height(self) -> float:
        """바운딩 박스 높이"""
        return self.y1 - self.y0
    
    @property
    def area(self) -> float:
        """바운딩 박스 면적"""
        return self.width * self.height
    
    def to_dict(self) -> Dict[str, float]:
        """딕셔너리 형태로 변환 (API 응답용)"""
        return {
            "x0": self.x0,
            "y0": self.y0,
            "x1": self.x1,
            "y1": self.y1
        }


@dataclass
class TextCorrection:
    """텍스트 교정 정보"""
    original: str                    # 원본 텍스트
    corrected: str                   # 교정된 텍스트
    correction_type: CorrectionType  # 교정 유형
    confidence: float               # 교정 신뢰도 (0.0 ~ 1.0)
    explanation: Optional[str] = None  # 교정 이유 설명
    start_position: Optional[int] = None  # 원본 텍스트 내 시작 위치
    end_position: Optional[int] = None    # 원본 텍스트 내 끝 위치
    
    def __post_init__(self):
        """유효성 검증"""
        if not 0.0 <= self.confidence <= 1.0:
            raise ValueError(f"Confidence must be between 0.0 and 1.0, got {self.confidence}")
        
        if self.start_position is not None and self.end_position is not None:
            if self.start_position >= self.end_position:
                raise ValueError(f"Invalid position range: {self.start_position} >= {self.end_position}")
    
    def to_dict(self) -> Dict[str, Any]:
        """딕셔너리 형태로 변환 (API 응답용)"""
        return {
            "original": self.original,
            "corrected": self.corrected,
            "correction_type": self.correction_type.value,
            "confidence": self.confidence,
            "explanation": self.explanation,
            "start_position": self.start_position,
            "end_position": self.end_position
        }


@dataclass
class OCRBlock:
    """기본 OCR 결과 블록"""
    text: str                    # 추출된 텍스트
    page_number: int            # 페이지 번호 (1부터 시작)
    bbox: BoundingBox          # 위치 정보
    confidence: float          # OCR 신뢰도 (0.0 ~ 1.0)
    block_type: str = "text"   # 블록 유형 (text, image, table 등)
    
    def __post_init__(self):
        """유효성 검증"""
        if self.page_number < 1:
            raise ValueError(f"Page number must be >= 1, got {self.page_number}")
        
        if not 0.0 <= self.confidence <= 1.0:
            raise ValueError(f"Confidence must be between 0.0 and 1.0, got {self.confidence}")
    
    @property
    def is_high_confidence(self) -> bool:
        """높은 신뢰도 여부 (0.7 이상)"""
        return self.confidence >= 0.7
    
    @property
    def text_length(self) -> int:
        """텍스트 길이"""
        return len(self.text.strip())
    
    def to_dict(self) -> Dict[str, Any]:
        """딕셔너리 형태로 변환 (API 응답용)"""
        return {
            "text": self.text,
            "page_number": self.page_number,
            "x0": self.bbox.x0,
            "y0": self.bbox.y0,
            "x1": self.bbox.x1,
            "y1": self.bbox.y1,
            "confidence": self.confidence,
            "block_type": self.block_type
        }


@dataclass
class ProcessedOCRBlock(OCRBlock):
    """LLM 후처리된 OCR 블록"""
    original_text: str = ""                 # 원본 OCR 텍스트
    corrections: List[TextCorrection] = None # 적용된 교정 목록
    processing_confidence: float = 0.0      # 후처리 신뢰도 (0.0 ~ 1.0)
    llm_model: str = "gemini-2.0-flash"    # 사용된 LLM 모델
    processing_time: Optional[float] = None # 후처리 소요 시간 (초)
    
    def __post_init__(self):
        """부모 클래스 검증 + 추가 검증"""
        super().__post_init__()
        
        if self.corrections is None:
            self.corrections = []
        
        if not 0.0 <= self.processing_confidence <= 1.0:
            raise ValueError(f"Processing confidence must be between 0.0 and 1.0, got {self.processing_confidence}")
    
    @property
    def has_corrections(self) -> bool:
        """교정 사항이 있는지 여부"""
        return len(self.corrections) > 0
    
    @property
    def correction_count(self) -> int:
        """교정 개수"""
        return len(self.corrections)
    
    @property
    def improvement_score(self) -> float:
        """개선 점수 (교정 신뢰도 평균)"""
        if not self.corrections:
            return 0.0
        return sum(c.confidence for c in self.corrections) / len(self.corrections)
    
    def get_corrections_by_type(self, correction_type: CorrectionType) -> List[TextCorrection]:
        """특정 유형의 교정 목록 반환"""
        return [c for c in self.corrections if c.correction_type == correction_type]
    
    def to_dict(self) -> Dict[str, Any]:
        """딕셔너리 형태로 변환 (API 응답용)"""
        base_dict = super().to_dict()
        base_dict.update({
            "original_text": self.original_text,
            "corrections": [c.to_dict() for c in self.corrections],
            "processing_confidence": self.processing_confidence,
            "llm_model": self.llm_model,
            "processing_time": self.processing_time,
            "has_corrections": self.has_corrections,
            "correction_count": self.correction_count,
            "improvement_score": self.improvement_score
        })
        return base_dict


@dataclass
class TesseractConfig:
    """Tesseract OCR 설정"""
    languages: List[str] = field(default_factory=lambda: ['kor', 'eng'])  # 지원 언어
    psm_mode: int = 6                    # Page Segmentation Mode (6: uniform block of text)
    oem_mode: int = 3                    # OCR Engine Mode (3: Default, based on what is available)
    dpi: int = 200                       # 이미지 DPI
    confidence_threshold: float = 0.3     # 최소 신뢰도 임계값
    enable_preprocessing: bool = True     # 이미지 전처리 활성화
    max_image_size: int = 1536           # 최대 이미지 크기 (픽셀)
    
    def __post_init__(self):
        """설정 유효성 검증"""
        if not self.languages:
            raise ValueError("At least one language must be specified")
        
        if not 1 <= self.psm_mode <= 13:
            raise ValueError(f"PSM mode must be between 1 and 13, got {self.psm_mode}")
        
        if not 0 <= self.oem_mode <= 3:
            raise ValueError(f"OEM mode must be between 0 and 3, got {self.oem_mode}")
        
        if self.dpi < 72:
            raise ValueError(f"DPI must be at least 72, got {self.dpi}")
        
        if not 0.0 <= self.confidence_threshold <= 1.0:
            raise ValueError(f"Confidence threshold must be between 0.0 and 1.0, got {self.confidence_threshold}")
        
        if self.max_image_size < 256:
            raise ValueError(f"Max image size must be at least 256, got {self.max_image_size}")
    
    @property
    def tesseract_lang_string(self) -> str:
        """Tesseract 언어 설정 문자열 생성"""
        return '+'.join(self.languages)
    
    @property
    def tesseract_config_string(self) -> str:
        """Tesseract 설정 문자열 생성"""
        return f'--oem {self.oem_mode} --psm {self.psm_mode}'
    
    def to_dict(self) -> Dict[str, Any]:
        """딕셔너리 형태로 변환"""
        return {
            "languages": self.languages,
            "psm_mode": self.psm_mode,
            "oem_mode": self.oem_mode,
            "dpi": self.dpi,
            "confidence_threshold": self.confidence_threshold,
            "enable_preprocessing": self.enable_preprocessing,
            "max_image_size": self.max_image_size,
            "tesseract_lang_string": self.tesseract_lang_string,
            "tesseract_config_string": self.tesseract_config_string
        }


@dataclass
class ProcessingMetrics:
    """OCR 처리 성능 메트릭"""
    document_id: str                    # 문서 ID
    total_pages: int                    # 총 페이지 수
    ocr_time: float                     # OCR 처리 시간 (초)
    llm_processing_time: float          # LLM 후처리 시간 (초)
    total_time: float                   # 전체 처리 시간 (초)
    memory_peak: int                    # 최대 메모리 사용량 (바이트)
    text_blocks_count: int              # 추출된 텍스트 블록 수
    corrections_count: int              # 총 교정 개수
    average_ocr_confidence: float       # 평균 OCR 신뢰도
    average_processing_confidence: float # 평균 후처리 신뢰도
    
    def __post_init__(self):
        """메트릭 유효성 검증"""
        if self.total_pages < 1:
            raise ValueError(f"Total pages must be >= 1, got {self.total_pages}")
        
        if self.ocr_time < 0 or self.llm_processing_time < 0 or self.total_time < 0:
            raise ValueError("Processing times must be non-negative")
        
        if self.text_blocks_count < 0 or self.corrections_count < 0:
            raise ValueError("Counts must be non-negative")
    
    @property
    def ocr_time_per_page(self) -> float:
        """페이지당 OCR 처리 시간"""
        return self.ocr_time / self.total_pages
    
    @property
    def llm_time_per_page(self) -> float:
        """페이지당 LLM 처리 시간"""
        return self.llm_processing_time / self.total_pages
    
    @property
    def total_time_per_page(self) -> float:
        """페이지당 전체 처리 시간"""
        return self.total_time / self.total_pages
    
    @property
    def corrections_per_block(self) -> float:
        """블록당 교정 개수"""
        if self.text_blocks_count == 0:
            return 0.0
        return self.corrections_count / self.text_blocks_count
    
    @property
    def llm_processing_ratio(self) -> float:
        """전체 시간 대비 LLM 처리 시간 비율"""
        if self.total_time == 0:
            return 0.0
        return self.llm_processing_time / self.total_time
    
    def to_dict(self) -> Dict[str, Any]:
        """딕셔너리 형태로 변환 (로깅/모니터링용)"""
        return {
            "document_id": self.document_id,
            "total_pages": self.total_pages,
            "ocr_time": self.ocr_time,
            "llm_processing_time": self.llm_processing_time,
            "total_time": self.total_time,
            "memory_peak": self.memory_peak,
            "text_blocks_count": self.text_blocks_count,
            "corrections_count": self.corrections_count,
            "average_ocr_confidence": self.average_ocr_confidence,
            "average_processing_confidence": self.average_processing_confidence,
            "ocr_time_per_page": self.ocr_time_per_page,
            "llm_time_per_page": self.llm_time_per_page,
            "total_time_per_page": self.total_time_per_page,
            "corrections_per_block": self.corrections_per_block,
            "llm_processing_ratio": self.llm_processing_ratio
        }


# 유틸리티 함수들

def create_ocr_block_from_tesseract_data(
    text: str,
    page_number: int,
    left: int,
    top: int,
    width: int,
    height: int,
    confidence: float
) -> OCRBlock:
    """Tesseract 데이터로부터 OCRBlock 생성"""
    bbox = BoundingBox(
        x0=float(left),
        y0=float(top),
        x1=float(left + width),
        y1=float(top + height)
    )
    
    return OCRBlock(
        text=text.strip(),
        page_number=page_number,
        bbox=bbox,
        confidence=confidence / 100.0 if confidence > 1.0 else confidence  # 0-100 범위를 0-1로 변환
    )


def merge_ocr_blocks(blocks: List[OCRBlock], merge_threshold: float = 10.0) -> List[OCRBlock]:
    """인접한 OCR 블록들을 병합 (같은 줄의 단어들)"""
    if not blocks:
        return []
    
    # 페이지별로 그룹화
    pages = {}
    for block in blocks:
        if block.page_number not in pages:
            pages[block.page_number] = []
        pages[block.page_number].append(block)
    
    merged_blocks = []
    
    for page_number, page_blocks in pages.items():
        # Y 좌표로 정렬 (위에서 아래로)
        page_blocks.sort(key=lambda b: b.bbox.y0)
        
        current_line = []
        current_y = None
        
        for block in page_blocks:
            # 새로운 줄인지 확인
            if current_y is None or abs(block.bbox.y0 - current_y) > merge_threshold:
                # 이전 줄 처리
                if current_line:
                    merged_blocks.append(_merge_line_blocks(current_line))
                
                # 새로운 줄 시작
                current_line = [block]
                current_y = block.bbox.y0
            else:
                # 같은 줄에 추가
                current_line.append(block)
        
        # 마지막 줄 처리
        if current_line:
            merged_blocks.append(_merge_line_blocks(current_line))
    
    return merged_blocks


def _merge_line_blocks(line_blocks: List[OCRBlock]) -> OCRBlock:
    """같은 줄의 블록들을 하나로 병합"""
    if len(line_blocks) == 1:
        return line_blocks[0]
    
    # X 좌표로 정렬 (왼쪽에서 오른쪽으로)
    line_blocks.sort(key=lambda b: b.bbox.x0)
    
    # 텍스트 병합
    merged_text = ' '.join(block.text for block in line_blocks if block.text.strip())
    
    # 바운딩 박스 병합
    min_x0 = min(block.bbox.x0 for block in line_blocks)
    min_y0 = min(block.bbox.y0 for block in line_blocks)
    max_x1 = max(block.bbox.x1 for block in line_blocks)
    max_y1 = max(block.bbox.y1 for block in line_blocks)
    
    merged_bbox = BoundingBox(x0=min_x0, y0=min_y0, x1=max_x1, y1=max_y1)
    
    # 평균 신뢰도 계산
    avg_confidence = sum(block.confidence for block in line_blocks) / len(line_blocks)
    
    return OCRBlock(
        text=merged_text,
        page_number=line_blocks[0].page_number,
        bbox=merged_bbox,
        confidence=avg_confidence
    )