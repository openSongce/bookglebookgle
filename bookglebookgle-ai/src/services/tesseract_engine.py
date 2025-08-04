"""
Tesseract 전용 OCR 엔진
최적화된 Tesseract OCR 처리를 위한 전용 엔진 클래스
"""

import asyncio
import io
import time
from typing import List, Dict, Any, Optional, Tuple
import traceback

import pytesseract
from PIL import Image, ImageEnhance, ImageFilter
import fitz  # PyMuPDF
from loguru import logger

from src.models.ocr_models import (
    OCRBlock, BoundingBox, TesseractConfig, 
    create_ocr_block_from_tesseract_data, merge_ocr_blocks
)


class TesseractEngine:
    """최적화된 Tesseract OCR 엔진"""
    
    def __init__(self, config: Optional[TesseractConfig] = None):
        """
        Tesseract 엔진 초기화
        
        Args:
            config: Tesseract 설정 (None이면 기본 설정 사용)
        """
        self.config = config or TesseractConfig()
        self.is_initialized = False
        self._initialization_error = None
        
        logger.info(f"🔧 TesseractEngine initialized with config:")
        logger.info(f"   Languages: {self.config.languages}")
        logger.info(f"   PSM Mode: {self.config.psm_mode}")
        logger.info(f"   OEM Mode: {self.config.oem_mode}")
        logger.info(f"   DPI: {self.config.dpi}")
        logger.info(f"   Confidence Threshold: {self.config.confidence_threshold}")
        logger.info(f"   Preprocessing: {self.config.enable_preprocessing}")
    
    async def initialize(self) -> bool:
        """
        Tesseract 엔진 초기화 및 가용성 확인
        
        Returns:
            초기화 성공 여부
        """
        try:
            # Tesseract 설치 확인
            version = pytesseract.get_tesseract_version()
            logger.info(f"✅ Tesseract version: {version}")
            
            # 언어 팩 확인
            available_langs = pytesseract.get_languages()
            logger.info(f"📚 Available languages: {available_langs}")
            
            # 설정된 언어들이 사용 가능한지 확인
            missing_langs = []
            for lang in self.config.languages:
                if lang not in available_langs:
                    missing_langs.append(lang)
            
            if missing_langs:
                logger.warning(f"⚠️ Missing language packs: {missing_langs}")
                # 한국어가 없으면 영어만 사용
                if 'kor' in missing_langs and 'eng' in available_langs:
                    logger.info("🔄 Falling back to English only")
                    self.config.languages = ['eng']
                elif missing_langs == self.config.languages:
                    raise Exception(f"No configured languages available: {missing_langs}")
            
            # 테스트 OCR 수행
            test_image = Image.new('RGB', (100, 50), color='white')
            test_result = pytesseract.image_to_string(
                test_image,
                lang=self.config.tesseract_lang_string,
                config=self.config.tesseract_config_string
            )
            
            self.is_initialized = True
            logger.info("✅ TesseractEngine initialization completed successfully")
            return True
            
        except Exception as e:
            self._initialization_error = str(e)
            logger.error(f"❌ TesseractEngine initialization failed: {e}")
            logger.error(f"Traceback: {traceback.format_exc()}")
            return False
    
    def is_available(self) -> bool:
        """Tesseract 엔진 사용 가능 여부 확인"""
        return self.is_initialized and self._initialization_error is None
    
    async def extract_text_with_positions(
        self, 
        image: Image.Image, 
        page_number: int
    ) -> List[OCRBlock]:
        """
        이미지에서 텍스트와 위치 정보 추출
        
        Args:
            image: PIL 이미지 객체
            page_number: 페이지 번호
            
        Returns:
            OCRBlock 리스트
        """
        if not self.is_available():
            if not await self.initialize():
                raise RuntimeError(f"TesseractEngine not available: {self._initialization_error}")
        
        try:
            start_time = time.time()
            
            # 이미지 전처리
            if self.config.enable_preprocessing:
                image = self._preprocess_image(image)
            
            # Tesseract OCR 수행
            ocr_data = pytesseract.image_to_data(
                image,
                lang=self.config.tesseract_lang_string,
                config=self.config.tesseract_config_string,
                output_type=pytesseract.Output.DICT
            )
            
            # OCR 결과를 OCRBlock으로 변환
            ocr_blocks = self._parse_tesseract_data(ocr_data, page_number)
            
            # 인접한 블록 병합 (선택사항)
            merged_blocks = merge_ocr_blocks(ocr_blocks, merge_threshold=10.0)
            
            processing_time = time.time() - start_time
            
            logger.info(f"📄 Page {page_number} OCR completed:")
            logger.info(f"   ⏱️ Processing time: {processing_time:.3f}s")
            logger.info(f"   📊 Raw blocks: {len(ocr_blocks)}")
            logger.info(f"   🔗 Merged blocks: {len(merged_blocks)}")
            logger.info(f"   📝 Total characters: {sum(len(block.text) for block in merged_blocks)}")
            
            return merged_blocks
            
        except Exception as e:
            logger.error(f"❌ OCR extraction failed for page {page_number}: {e}")
            logger.error(f"Traceback: {traceback.format_exc()}")
            raise
    
    def _preprocess_image(self, image: Image.Image) -> Image.Image:
        """
        OCR 정확도 향상을 위한 최적화된 이미지 전처리
        메모리 효율성과 처리 속도를 고려한 단계별 처리
        
        Args:
            image: 원본 이미지
            
        Returns:
            전처리된 이미지
        """
        try:
            start_time = time.time()
            original_size = image.size
            
            # 1단계: 색상 모드 최적화
            if image.mode not in ['RGB', 'L']:
                image = image.convert('RGB')
                logger.debug(f"🎨 Color mode converted to RGB")
            
            # 2단계: 메모리 효율적인 크기 조정
            if max(image.size) > self.config.max_image_size:
                # 비율 유지하면서 크기 조정
                ratio = self.config.max_image_size / max(image.size)
                new_size = (int(image.size[0] * ratio), int(image.size[1] * ratio))
                image = image.resize(new_size, Image.Resampling.LANCZOS)
                logger.debug(f"🔄 Image resized: {original_size} → {new_size}")
            
            # 3단계: 적응적 품질 향상
            # 이미지 크기에 따라 처리 강도 조정
            enhancement_factor = self._calculate_enhancement_factor(image.size)
            
            # 대비 향상 (적응적)
            enhancer = ImageEnhance.Contrast(image)
            image = enhancer.enhance(1.0 + (0.2 * enhancement_factor))
            
            # 선명도 개선 (적응적)
            enhancer = ImageEnhance.Sharpness(image)
            image = enhancer.enhance(1.0 + (0.1 * enhancement_factor))
            
            # 4단계: 노이즈 제거 (이미지 크기에 따라 필터 크기 조정)
            filter_size = 3 if max(image.size) > 800 else 1
            if filter_size > 1:
                image = image.filter(ImageFilter.MedianFilter(size=filter_size))
            
            processing_time = time.time() - start_time
            
            # 성능 통계 로깅
            size_reduction = (1 - (image.size[0] * image.size[1]) / (original_size[0] * original_size[1])) * 100
            logger.debug(f"🎨 Image preprocessing completed:")
            logger.debug(f"   ⏱️ Time: {processing_time:.3f}s")
            logger.debug(f"   📏 Size reduction: {size_reduction:.1f}%")
            logger.debug(f"   🔧 Enhancement factor: {enhancement_factor:.2f}")
            
            return image
            
        except Exception as e:
            logger.warning(f"⚠️ Image preprocessing failed: {e}, using original image")
            return image
    
    def _calculate_enhancement_factor(self, image_size: Tuple[int, int]) -> float:
        """
        이미지 크기에 따른 향상 계수 계산
        작은 이미지일수록 더 강한 향상 적용
        
        Args:
            image_size: 이미지 크기 (width, height)
            
        Returns:
            향상 계수 (0.5 ~ 1.5)
        """
        total_pixels = image_size[0] * image_size[1]
        
        # 기준: 1024x768 = 786,432 픽셀
        base_pixels = 786432
        
        if total_pixels < base_pixels * 0.25:  # 매우 작은 이미지
            return 1.5
        elif total_pixels < base_pixels * 0.5:  # 작은 이미지
            return 1.2
        elif total_pixels < base_pixels * 2:  # 보통 이미지
            return 1.0
        else:  # 큰 이미지
            return 0.8
    
    async def _optimize_memory_usage(self):
        """메모리 사용량 최적화"""
        try:
            import gc
            gc.collect()  # 가비지 컬렉션 강제 실행
            logger.debug("🧹 Memory cleanup completed")
        except Exception as e:
            logger.debug(f"⚠️ Memory cleanup failed: {e}")
    
    def get_processing_stats(self) -> Dict[str, Any]:
        """처리 통계 정보 반환"""
        return {
            "config": self.config.to_dict(),
            "is_initialized": self.is_initialized,
            "preprocessing_enabled": self.config.enable_preprocessing,
            "max_image_size": self.config.max_image_size,
            "dpi": self.config.dpi,
            "confidence_threshold": self.config.confidence_threshold
        }
    
    def _parse_tesseract_data(self, data: Dict[str, List], page_number: int) -> List[OCRBlock]:
        """
        Tesseract 원시 데이터를 OCRBlock 형태로 변환
        향상된 위치 정보 처리 및 텍스트 품질 검증 포함
        
        Args:
            data: pytesseract.image_to_data 결과
            page_number: 페이지 번호
            
        Returns:
            OCRBlock 리스트
        """
        blocks = []
        n_boxes = len(data['level'])
        
        # 통계 정보 수집
        total_candidates = 0
        filtered_by_confidence = 0
        filtered_by_quality = 0
        
        for i in range(n_boxes):
            # 단어 레벨 (level 5) 우선, 텍스트 라인 레벨 (level 4) 보조
            if data['level'][i] in [4, 5] and data['text'][i].strip():
                total_candidates += 1
                text = data['text'][i].strip()
                confidence = float(data['conf'][i])
                
                # 1차 신뢰도 필터링
                if confidence < (self.config.confidence_threshold * 100):  # Tesseract는 0-100 범위
                    filtered_by_confidence += 1
                    continue
                
                # 2차 텍스트 품질 검증
                if not self._validate_ocr_text(text, confidence):
                    filtered_by_quality += 1
                    continue
                
                # 3차 위치 정보 유효성 검증
                left, top, width, height = data['left'][i], data['top'][i], data['width'][i], data['height'][i]
                if width <= 0 or height <= 0:
                    logger.debug(f"⚠️ Invalid bbox dimensions: {width}x{height}")
                    continue
                
                try:
                    # 향상된 OCR 블록 생성
                    ocr_block = self._create_enhanced_ocr_block(
                        text=text,
                        page_number=page_number,
                        left=left,
                        top=top,
                        width=width,
                        height=height,
                        confidence=confidence,
                        level=data['level'][i]
                    )
                    blocks.append(ocr_block)
                    
                except Exception as e:
                    logger.warning(f"⚠️ Failed to create OCR block: {e}")
                    continue
        
        # 통계 로깅
        logger.debug(f"📊 OCR parsing statistics:")
        logger.debug(f"   📝 Total candidates: {total_candidates}")
        logger.debug(f"   🎯 Confidence filtered: {filtered_by_confidence}")
        logger.debug(f"   🔍 Quality filtered: {filtered_by_quality}")
        logger.debug(f"   ✅ Final blocks: {len(blocks)}")
        
        return blocks
    
    def _create_enhanced_ocr_block(
        self,
        text: str,
        page_number: int,
        left: int,
        top: int,
        width: int,
        height: int,
        confidence: float,
        level: int
    ) -> OCRBlock:
        """
        향상된 OCR 블록 생성 (위치 정보 정확도 개선)
        
        Args:
            text: 추출된 텍스트
            page_number: 페이지 번호
            left, top, width, height: 바운딩 박스 정보
            confidence: 신뢰도
            level: Tesseract 레벨 (4: 라인, 5: 단어)
            
        Returns:
            OCRBlock 객체
        """
        # 바운딩 박스 좌표 정규화 및 검증
        x0 = max(0, float(left))
        y0 = max(0, float(top))
        x1 = x0 + max(1, float(width))  # 최소 1픽셀 너비 보장
        y1 = y0 + max(1, float(height))  # 최소 1픽셀 높이 보장
        
        bbox = BoundingBox(x0=x0, y0=y0, x1=x1, y1=y1)
        
        # 블록 타입 결정
        block_type = "word" if level == 5 else "line"
        
        # 신뢰도 정규화 (0-100 → 0-1)
        normalized_confidence = confidence / 100.0 if confidence > 1.0 else confidence
        
        return OCRBlock(
            text=text,
            page_number=page_number,
            bbox=bbox,
            confidence=normalized_confidence,
            block_type=block_type
        )
    
    def _validate_ocr_text(self, text: str, confidence: float) -> bool:
        """
        OCR 텍스트 품질 검증
        
        Args:
            text: OCR 추출 텍스트
            confidence: OCR 신뢰도
            
        Returns:
            텍스트 품질 적합 여부
        """
        # 기본 길이 검사
        if len(text.strip()) < 2:
            return False
        
        # 신뢰도 검사
        if confidence < (self.config.confidence_threshold * 100):
            return False
        
        # 의미있는 문자 비율 검사
        meaningful_chars = sum(1 for c in text if c.isalnum() or ord(c) > 127)
        total_chars = len(text.replace(' ', '').replace('\n', ''))
        
        if total_chars > 0 and meaningful_chars / total_chars < 0.3:
            return False
        
        return True
    
    async def process_pdf_pages(
        self, 
        pdf_document: fitz.Document,
        page_range: Optional[Tuple[int, int]] = None
    ) -> List[OCRBlock]:
        """
        PDF 문서의 여러 페이지를 처리
        
        Args:
            pdf_document: PyMuPDF 문서 객체
            page_range: 처리할 페이지 범위 (start, end), None이면 전체
            
        Returns:
            모든 페이지의 OCRBlock 리스트
        """
        total_pages = len(pdf_document)
        
        if page_range:
            start_page, end_page = page_range
            start_page = max(0, start_page)
            end_page = min(total_pages, end_page)
        else:
            start_page, end_page = 0, total_pages
        
        logger.info(f"📚 Processing PDF pages {start_page + 1} to {end_page} (total: {total_pages})")
        
        all_blocks = []
        
        for page_num in range(start_page, end_page):
            try:
                page_start_time = time.time()
                page = pdf_document.load_page(page_num)
                
                # 먼저 텍스트 레이어 확인
                text_content = page.get_text()
                
                if self._has_valid_text_layer(text_content):
                    # 텍스트 레이어가 있으면 OCR 생략
                    logger.info(f"📝 Page {page_num + 1}: Using existing text layer")
                    # 텍스트 레이어를 OCRBlock으로 변환 (위치 정보는 근사치)
                    text_blocks = self._convert_text_layer_to_blocks(text_content, page_num + 1, page)
                    all_blocks.extend(text_blocks)
                else:
                    # 이미지 PDF이므로 OCR 수행
                    pix = page.get_pixmap(dpi=self.config.dpi)
                    img = Image.open(io.BytesIO(pix.tobytes()))
                    
                    # OCR 처리
                    page_blocks = await self.extract_text_with_positions(img, page_num + 1)
                    all_blocks.extend(page_blocks)
                    
                    # 메모리 정리
                    del img, pix
                
                page_time = time.time() - page_start_time
                logger.info(f"✅ Page {page_num + 1} completed in {page_time:.2f}s")
                
            except Exception as e:
                logger.error(f"❌ Failed to process page {page_num + 1}: {e}")
                continue
        
        logger.info(f"🎉 PDF processing completed: {len(all_blocks)} total blocks extracted")
        return all_blocks
    
    def _has_valid_text_layer(self, text_content: str) -> bool:
        """텍스트 레이어가 유효한지 확인"""
        if not text_content or len(text_content.strip()) < 10:
            return False
        
        # 의미있는 문자 비율 확인
        meaningful_chars = sum(1 for c in text_content if c.isalnum() or ord(c) > 127)
        total_chars = len(text_content.replace(' ', '').replace('\n', ''))
        
        if total_chars > 0 and meaningful_chars / total_chars < 0.3:
            return False
        
        return True
    
    def _convert_text_layer_to_blocks(
        self, 
        text_content: str, 
        page_number: int, 
        page: fitz.Page
    ) -> List[OCRBlock]:
        """
        텍스트 레이어를 OCRBlock으로 변환
        
        Args:
            text_content: 페이지 텍스트 내용
            page_number: 페이지 번호
            page: PyMuPDF 페이지 객체
            
        Returns:
            OCRBlock 리스트
        """
        blocks = []
        
        # 텍스트를 줄 단위로 분할
        lines = [line.strip() for line in text_content.split('\n') if line.strip()]
        
        # 페이지 크기 정보
        page_rect = page.rect
        page_height = page_rect.height
        page_width = page_rect.width
        
        # 각 줄에 대해 근사적인 위치 정보 생성
        line_height = page_height / max(len(lines), 1)
        
        for i, line_text in enumerate(lines):
            if len(line_text.strip()) < 2:
                continue
            
            # 근사적인 바운딩 박스 계산
            y0 = i * line_height
            y1 = (i + 1) * line_height
            x0 = 0
            x1 = page_width  # 전체 너비 사용
            
            try:
                bbox = BoundingBox(x0=x0, y0=y0, x1=x1, y1=y1)
                
                block = OCRBlock(
                    text=line_text,
                    page_number=page_number,
                    bbox=bbox,
                    confidence=0.95,  # 텍스트 레이어는 높은 신뢰도
                    block_type="text_layer"
                )
                
                blocks.append(block)
                
            except Exception as e:
                logger.warning(f"⚠️ Failed to create text layer block: {e}")
                continue
        
        logger.debug(f"📄 Converted {len(blocks)} text layer blocks")
        return blocks
    
    def get_engine_info(self) -> Dict[str, Any]:
        """엔진 정보 반환"""
        try:
            version = pytesseract.get_tesseract_version() if self.is_initialized else "Unknown"
            available_langs = pytesseract.get_languages() if self.is_initialized else []
        except:
            version = "Unknown"
            available_langs = []
        
        return {
            "engine_name": "TesseractEngine",
            "version": str(version),
            "is_initialized": self.is_initialized,
            "is_available": self.is_available(),
            "config": self.config.to_dict(),
            "available_languages": available_langs,
            "initialization_error": self._initialization_error
        }