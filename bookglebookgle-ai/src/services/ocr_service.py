
"""
OCR Service for processing PDF documents
Enhanced version with multiple OCR engines and intelligent engine selection
Stage 2: Multi-engine integration with language detection
"""
import asyncio
import io
import time
import re
from typing import List, Dict, Any, Tuple, Optional

import fitz  # PyMuPDF
import pytesseract
from PIL import Image, ImageEnhance, ImageFilter
from loguru import logger

# Import enhanced OCR service
from .enhanced_ocr_service import EnhancedOCRService, OCRConfig, OCREngine

# Tesseract-OCR 경로 설정 (사용자 환경에 맞게 수정 필요)
# 예: pytesseract.pytesseract.tesseract_cmd = r'C:\Program Files\Tesseract-OCR\tesseract.exe'

class OcrService:
    """Handles PDF parsing and OCR extraction with enhanced capabilities - Stage 2"""

    def __init__(self):
        # Stage 2: Enhanced multi-engine configuration
        self.config = OCRConfig(
            primary_engine=OCREngine.TESSERACT,  # 안정성 우선
            fallback_engines=[OCREngine.EASYOCR, OCREngine.PADDLEOCR],  # 다중 fallback
            languages=['ko', 'en'],
            confidence_threshold=0.3,  # 0.1 → 0.3 (품질 향상)
            enable_preprocessing=True,  # 전처리 활성화
            max_image_size=1536,
            dpi=200,
            use_gpu=False
        )
        
        self.enhanced_ocr = EnhancedOCRService(self.config)
        
        # Stage 2: Language detection patterns
        self.korean_pattern = re.compile(r'[가-힣]')
        self.english_pattern = re.compile(r'[a-zA-Z]')
        self.number_pattern = re.compile(r'[0-9]')
        
        # Stage 2: Engine selection strategy
        self.engine_strategy = {
            'korean_heavy': OCREngine.PADDLEOCR,  # 한국어 집중 문서
            'english_heavy': OCREngine.EASYOCR,   # 영어 집중 문서
            'mixed': OCREngine.TESSERACT,         # 한영 혼합 문서
            'numbers_heavy': OCREngine.TESSERACT  # 숫자 집중 문서
        }
        
        logger.info(f"🚀 [STAGE 2] Multi-engine OcrService initialized:")
        logger.info(f"   Primary: {self.config.primary_engine.value}")
        logger.info(f"   Fallbacks: {[e.value for e in self.config.fallback_engines]}")
        logger.info(f"   Language detection: Enabled")
        logger.info(f"   Preprocessing: {self.config.enable_preprocessing}")
    
    def _enhance_image_for_ocr(self, image: Image.Image) -> Image.Image:
        """
        이미지 전처리 함수 - 1단계 최적화
        대비 향상, 선명도 개선, 노이즈 제거 적용
        """
        try:
            start_time = time.time()
            
            # Convert to RGB if necessary
            if image.mode != 'RGB':
                image = image.convert('RGB')
            
            # 이미지 크기 제한 (메모리 최적화)
            max_size = 1536  # 계획서에 따른 최적화된 크기
            if max(image.size) > max_size:
                image.thumbnail((max_size, max_size), Image.Resampling.LANCZOS)
                logger.debug(f"Image resized to {image.size}")
            
            # 1. 대비(Contrast) 향상: 1.2배 적용
            enhancer = ImageEnhance.Contrast(image)
            image = enhancer.enhance(1.2)
            
            # 2. 선명도(Sharpness) 개선: 1.1배 적용
            enhancer = ImageEnhance.Sharpness(image)
            image = enhancer.enhance(1.1)
            
            # 3. 노이즈 제거: MedianFilter 적용
            image = image.filter(ImageFilter.MedianFilter(size=3))
            
            processing_time = time.time() - start_time
            logger.debug(f"Image preprocessing completed in {processing_time:.3f}s")
            
            return image
            
        except Exception as e:
            logger.warning(f"Image preprocessing failed: {e}, using original image")
            return image
    
    def _validate_ocr_text(self, text: str, confidence: float = None) -> bool:
        """
        텍스트 품질 검증 로직 - 1단계 최적화
        개선된 품질 기준 적용
        """
        # 기본 텍스트 길이 검사 (50자 → 10자로 완화)
        if len(text.strip()) < 10:
            return False
        
        # 신뢰도 기반 필터링 (있는 경우)
        if confidence is not None and confidence < 0.3:
            return False
        
        # 의미있는 문자 비율 검사 (알파벳, 숫자, 한글)
        meaningful_chars = sum(1 for c in text if c.isalnum() or ord(c) > 127)
        total_chars = len(text.replace(' ', '').replace('\n', ''))
        
        if total_chars > 0 and meaningful_chars / total_chars < 0.3:
            return False
        
        return True
    
    def _detect_document_language(self, sample_text: str) -> str:
        """
        Stage 2: 문서의 주요 언어 감지
        샘플 텍스트를 분석하여 최적 OCR 엔진 전략 결정
        """
        if not sample_text or len(sample_text.strip()) < 10:
            return 'mixed'  # 기본값
        
        # 각 언어 패턴 매칭
        korean_matches = len(self.korean_pattern.findall(sample_text))
        english_matches = len(self.english_pattern.findall(sample_text))
        number_matches = len(self.number_pattern.findall(sample_text))
        
        total_chars = len(sample_text.replace(' ', '').replace('\n', ''))
        
        if total_chars == 0:
            return 'mixed'
        
        # 비율 계산
        korean_ratio = korean_matches / total_chars
        english_ratio = english_matches / total_chars
        number_ratio = number_matches / total_chars
        
        logger.debug(f"Language detection - KR: {korean_ratio:.2f}, EN: {english_ratio:.2f}, NUM: {number_ratio:.2f}")
        
        # 전략 결정 로직
        if korean_ratio > 0.3:
            return 'korean_heavy'
        elif english_ratio > 0.5:
            return 'english_heavy'
        elif number_ratio > 0.3:
            return 'numbers_heavy'
        else:
            return 'mixed'
    
    def _get_optimal_engine(self, language_type: str) -> OCREngine:
        """
        Stage 2: 언어 타입에 따른 최적 OCR 엔진 선택
        """
        optimal_engine = self.engine_strategy.get(language_type, OCREngine.TESSERACT)
        logger.info(f"🎯 Language type '{language_type}' → Optimal engine: {optimal_engine.value}")
        return optimal_engine
    
    async def _try_multiple_engines(self, pdf_stream: bytes, document_id: str, 
                                  primary_engine: OCREngine) -> Dict[str, Any]:
        """
        Stage 2: 다중 엔진 시도 로직 (fallback 메커니즘)
        """
        engines_to_try = [primary_engine] + [e for e in self.config.fallback_engines if e != primary_engine]
        
        for i, engine in enumerate(engines_to_try):
            try:
                logger.info(f"🔧 Trying engine {i+1}/{len(engines_to_try)}: {engine.value}")
                
                # 엔진별 설정 조정
                temp_config = OCRConfig(
                    primary_engine=engine,
                    fallback_engines=[],  # 개별 시도 시에는 fallback 비활성화
                    languages=self.config.languages,
                    confidence_threshold=self.config.confidence_threshold,
                    enable_preprocessing=self.config.enable_preprocessing,
                    max_image_size=self.config.max_image_size,
                    dpi=self.config.dpi,
                    use_gpu=self.config.use_gpu
                )
                
                temp_ocr_service = EnhancedOCRService(temp_config)
                result = await temp_ocr_service.process_pdf_stream(pdf_stream, f"{document_id}_attempt_{i+1}")
                
                if result.get("success", False):
                    # 결과 품질 검증
                    text_blocks = result.get("text_blocks", [])
                    total_text = " ".join([block.get("text", "") for block in text_blocks])
                    
                    if self._validate_ocr_text(total_text):
                        logger.info(f"✅ Success with {engine.value}: {len(text_blocks)} blocks, {len(total_text)} chars")
                        result["engine_used"] = f"{engine.value} (Multi-Engine v2.0)"
                        result["engine_attempt"] = i + 1
                        return result
                    else:
                        logger.warning(f"⚠️ {engine.value} produced low quality result, trying next engine")
                else:
                    logger.warning(f"❌ {engine.value} failed: {result.get('error', 'Unknown error')}")
                    
            except Exception as e:
                logger.error(f"💥 Exception with {engine.value}: {e}")
                continue
        
        # 모든 엔진 실패 시
        return {
            "success": False,
            "error": "All OCR engines failed",
            "document_id": document_id,
            "engines_tried": [e.value for e in engines_to_try]
        }

    async def process_pdf_stream(self, pdf_stream: bytes, document_id: str) -> Dict[str, Any]:
        """
        Stage 2: 다중 OCR 엔진 및 지능형 언어 감지가 적용된 PDF 텍스트 추출
        언어별 최적 엔진 선택 및 fallback 메커니즘 포함
        """
        try:
            start_time = time.time()
            logger.info(f"🚀 [STAGE 2] Starting multi-engine OCR process for document: {document_id}")
            
            # PDF 문서 열기
            pdf_document = None
            try:
                if hasattr(fitz, 'Document'):
                    pdf_document = fitz.Document(stream=pdf_stream, filetype="pdf")
                else:
                    pdf_document = fitz.open(stream=pdf_stream, filetype="pdf")
            except Exception as e:
                logger.error(f"Failed to open PDF: {e}")
                return {"success": False, "error": f"Cannot open PDF: {e}"}
            
            total_pages = len(pdf_document)
            logger.info(f"📄 PDF has {total_pages} pages")

            # Stage 2: 언어 감지를 위한 샘플 텍스트 수집 (첫 1-2 페이지)
            sample_pages = min(2, total_pages)
            sample_text = ""
            
            for page_num in range(sample_pages):
                page = pdf_document.load_page(page_num)
                text_content = page.get_text()
                
                if self._validate_ocr_text(text_content):
                    sample_text += text_content + " "
                else:
                    # 간단한 OCR로 샘플링 (빠른 언어 감지용)
                    try:
                        pix = page.get_pixmap(dpi=150)  # 낮은 DPI로 빠른 처리
                        img = Image.open(io.BytesIO(pix.tobytes()))
                        quick_text = pytesseract.image_to_string(img, lang='kor+eng', config='--psm 6')
                        sample_text += quick_text + " "
                        del img, pix
                    except Exception as e:
                        logger.warning(f"Quick sampling failed for page {page_num + 1}: {e}")
                
                if len(sample_text) > 500:  # 충분한 샘플 수집시 중단
                    break
            
            # Stage 2: 언어 감지 및 최적 엔진 선택
            language_type = self._detect_document_language(sample_text)
            optimal_engine = self._get_optimal_engine(language_type)
            
            logger.info(f"🎯 Document analysis complete:")
            logger.info(f"   📝 Sample text length: {len(sample_text)} chars")
            logger.info(f"   🌐 Language type: {language_type}")
            logger.info(f"   🔧 Optimal engine: {optimal_engine.value}")
            
            # Stage 2: 선택된 엔진으로 전체 문서 처리 (fallback 포함)
            result = await self._try_multiple_engines(pdf_stream, document_id, optimal_engine)
            
            if result.get("success", False):
                # Stage 2: 호환성을 위해 기존 형식으로 변환
                text_blocks = result.get("text_blocks", [])
                full_text = "\n\n".join([block.get("text", "") for block in text_blocks if block.get("text", "").strip()])
                
                # 페이지별 텍스트 구성
                page_texts = []
                page_groups = {}
                
                for block in text_blocks:
                    page_num = block.get("page_number", 1)
                    if page_num not in page_groups:
                        page_groups[page_num] = []
                    page_groups[page_num].append(block.get("text", ""))
                
                for page_num in range(1, total_pages + 1):
                    page_text = "\n".join(page_groups.get(page_num, []))
                    page_texts.append({
                        "page_number": page_num,
                        "text": page_text.strip(),
                        "processing_time": 0  # 전체 처리에 포함됨
                    })
                
                total_time = time.time() - start_time
                
                # Stage 2: 고급 성능 로깅
                logger.info(f"✅ [STAGE 2] Multi-engine OCR completed for {document_id}:")
                logger.info(f"   🕒 Total time: {total_time:.2f}s")
                logger.info(f"   📄 Pages processed: {total_pages}")
                logger.info(f"   🔧 Engine used: {result.get('engine_used', 'Unknown')}")
                logger.info(f"   🏆 Engine attempt: {result.get('engine_attempt', 'N/A')}")
                logger.info(f"   📊 Text blocks: {len(text_blocks)}")
                logger.info(f"   🎯 Total characters: {len(full_text)}")
                logger.info(f"   🌐 Language type: {language_type}")

                return {
                    "success": True,
                    "document_id": document_id,
                    "total_pages": total_pages,
                    "full_text": full_text,
                    "page_texts": page_texts,
                    "engine_used": result.get("engine_used", "Multi-Engine v2.0"),
                    "processing_stats": {
                        "total_time": total_time,
                        "engine_attempt": result.get("engine_attempt", 1),
                        "language_type": language_type,
                        "optimal_engine": optimal_engine.value,
                        "text_blocks_count": len(text_blocks),
                        "total_characters": len(full_text),
                        "stage": "Stage 2 - Multi-Engine"
                    }
                }
            else:
                # Stage 2 실패 시 Stage 1 fallback
                logger.warning(f"⚠️ Stage 2 failed, falling back to Stage 1 processing")
                return await self._stage1_fallback_process(pdf_stream, document_id)

        except Exception as e:
            logger.error(f"Stage 2 OCR processing failed for {document_id}: {e}")
            logger.info(f"🔄 Attempting Stage 1 fallback...")
            try:
                return await self._stage1_fallback_process(pdf_stream, document_id)
            except Exception as e2:
                logger.error(f"Stage 1 fallback also failed: {e2}")
                return {
                    "success": False, 
                    "error": f"Stage 2 error: {str(e)}, Stage 1 fallback error: {str(e2)}",
                    "document_id": document_id
                }
    
    async def _stage1_fallback_process(self, pdf_stream: bytes, document_id: str) -> Dict[str, Any]:
        """
        Stage 1 fallback: 기본 Tesseract 처리 (호환성 보장)
        Stage 2 실패 시 사용되는 안정적인 처리 방식
        """
        try:
            start_time = time.time()
            logger.info(f"🔄 [STAGE 1 FALLBACK] Starting basic OCR process for document: {document_id}")
            
            # PDF 문서 열기
            pdf_document = None
            try:
                if hasattr(fitz, 'Document'):
                    pdf_document = fitz.Document(stream=pdf_stream, filetype="pdf")
                else:
                    pdf_document = fitz.open(stream=pdf_stream, filetype="pdf")
            except Exception as e:
                logger.error(f"Failed to open PDF: {e}")
                return {"success": False, "error": f"Cannot open PDF: {e}"}
            
            total_pages = len(pdf_document)
            logger.info(f"📄 PDF has {total_pages} pages (fallback processing)")

            # 전체 텍스트를 하나의 문자열로 수집
            full_text = ""
            page_texts = []
            ocr_stats = {
                "pages_with_text_layer": 0,
                "pages_ocr_processed": 0,
                "total_preprocessing_time": 0,
                "total_ocr_time": 0
            }
            
            for page_num in range(total_pages):
                page_start_time = time.time()
                page = pdf_document.load_page(page_num)
                
                # 먼저 텍스트 레이어가 있는지 확인
                text_content = page.get_text()
                
                if self._validate_ocr_text(text_content):
                    page_text = text_content
                    ocr_stats["pages_with_text_layer"] += 1
                    logger.info(f"📝 Page {page_num + 1}: Using text layer ({len(text_content)} chars)")
                else:
                    # 텍스트가 없으면 OCR 수행 (이미지 PDF)
                    ocr_start_time = time.time()
                    
                    # 최적화된 DPI 설정: 200
                    pix = page.get_pixmap(dpi=200)
                    img = Image.open(io.BytesIO(pix.tobytes()))
                    
                    # Stage 1 이미지 전처리 적용
                    img = self._enhance_image_for_ocr(img)
                    ocr_stats["total_preprocessing_time"] += time.time() - ocr_start_time
                    
                    # Tesseract OCR 처리
                    ocr_process_start = time.time()
                    page_text = pytesseract.image_to_string(
                        img, 
                        lang='kor+eng',
                        config='--oem 3 --psm 6'
                    )
                    ocr_process_time = time.time() - ocr_process_start
                    ocr_stats["total_ocr_time"] += ocr_process_time
                    ocr_stats["pages_ocr_processed"] += 1
                    
                    # 메모리 정리
                    del img, pix
                    
                    logger.info(f"🔍 Page {page_num + 1}: Fallback OCR processed ({ocr_process_time:.2f}s, {len(page_text)} chars)")
                
                # 텍스트 품질 재검증
                if self._validate_ocr_text(page_text):
                    page_texts.append({
                        "page_number": page_num + 1,
                        "text": page_text.strip(),
                        "processing_time": time.time() - page_start_time
                    })
                    full_text += page_text + "\n\n"
                else:
                    logger.warning(f"Page {page_num + 1}: Text quality too low, skipping")
                    page_texts.append({
                        "page_number": page_num + 1,
                        "text": "",
                        "processing_time": time.time() - page_start_time,
                        "skipped": True
                    })

            # 전체 텍스트 정리
            full_text = full_text.strip()
            total_time = time.time() - start_time
            
            # 성능 로깅
            logger.info(f"✅ [STAGE 1 FALLBACK] OCR completed for {document_id}:")
            logger.info(f"   📊 Total time: {total_time:.2f}s")
            logger.info(f"   📄 Pages processed: {total_pages}")
            logger.info(f"   📝 Text layer pages: {ocr_stats['pages_with_text_layer']}")
            logger.info(f"   🔍 OCR processed pages: {ocr_stats['pages_ocr_processed']}")
            logger.info(f"   🎯 Characters extracted: {len(full_text)}")

            return {
                "success": True,
                "document_id": document_id,
                "total_pages": total_pages,
                "full_text": full_text,
                "page_texts": page_texts,
                "engine_used": "Tesseract Enhanced v1.0 (Fallback)",
                "processing_stats": {
                    "total_time": total_time,
                    "pages_with_text_layer": ocr_stats["pages_with_text_layer"],
                    "pages_ocr_processed": ocr_stats["pages_ocr_processed"],
                    "avg_preprocessing_time": ocr_stats["total_preprocessing_time"] / max(1, ocr_stats["pages_ocr_processed"]),
                    "total_characters": len(full_text),
                    "stage": "Stage 1 - Fallback"
                }
            }

        except Exception as e:
            logger.error(f"Stage 1 fallback processing failed for {document_id}: {e}")
            return {
                "success": False, 
                "error": str(e),
                "document_id": document_id
            }

    async def _legacy_process_pdf_stream(self, pdf_stream: bytes, document_id: str) -> Dict[str, Any]:
        """
        Legacy OCR processing using Tesseract (fallback method)
        """
        try:
            logger.info(f"Starting legacy OCR process for document: {document_id}")
            
            # Open PDF from byte stream (backward compatible)
            pdf_document = None
            try:
                # Try Document constructor first (recommended for newer versions)
                if hasattr(fitz, 'Document'):
                    pdf_document = fitz.Document(stream=pdf_stream, filetype="pdf")
                    logger.debug("Using fitz.Document() constructor")
                else:
                    raise AttributeError("Document class not available")
            except (AttributeError, TypeError) as e:
                logger.debug(f"fitz.Document() failed: {e}, trying fitz.open()")
                try:
                    # Fallback to open function (for older versions)
                    if hasattr(fitz, 'open'):
                        pdf_document = fitz.open(stream=pdf_stream, filetype="pdf")
                        logger.debug("Using fitz.open() function")
                    else:
                        raise AttributeError("open function not available")
                except Exception as e2:
                    logger.error(f"Both fitz.Document() and fitz.open() failed: {e2}")
                    raise Exception(f"Cannot open PDF: {e2}")
            
            if pdf_document is None:
                raise Exception("Failed to open PDF document")
            total_pages = len(pdf_document)
            logger.info(f"PDF has {total_pages} pages (legacy processing).")

            all_text_blocks = []
            
            for page_num in range(total_pages):
                page = pdf_document.load_page(page_num)
                
                # Render page to an image (pixmap)
                pix = page.get_pixmap(dpi=300)
                img = Image.open(io.BytesIO(pix.tobytes()))

                # Use pytesseract to get detailed OCR data
                # lang='kor+eng' for Korean and English
                ocr_data = pytesseract.image_to_data(
                    img, 
                    lang='kor+eng', 
                    output_type=pytesseract.Output.DICT,
                    config='--psm 11' #  Page segmentation mode 11 (sparse text)
                )

                page_blocks = self._parse_tesseract_data(ocr_data, page_num + 1)
                all_text_blocks.extend(page_blocks)

            logger.info(f"Legacy OCR extracted {len(all_text_blocks)} text blocks from document {document_id}.")

            return {
                "success": True,
                "document_id": document_id,
                "total_pages": total_pages,
                "text_blocks": all_text_blocks,
                "engine_used": "TesseractEngine (Legacy)"
            }

        except Exception as e:
            logger.error(f"Error during legacy PDF processing for {document_id}: {e}")
            return {"success": False, "error": str(e)}

    def _parse_tesseract_data(self, data: Dict[str, List], page_number: int) -> List[Dict[str, Any]]:
        """Parses the raw data dictionary from pytesseract into TextBlock format."""
        blocks = []
        n_boxes = len(data['level'])
        
        for i in range(n_boxes):
            # We are interested in text lines, so we look at level 4 (text line)
            if data['level'][i] == 4 and data['text'][i].strip():
                confidence = float(data['conf'][i]) / 100.0 if data['conf'][i] > 0 else 0.0
                
                # Apply confidence threshold
                if confidence >= 0.3:  # Lower threshold for legacy fallback
                    (x, y, w, h) = (data['left'][i], data['top'][i], data['width'][i], data['height'][i])
                    
                    blocks.append({
                        "text": data['text'][i],
                        "page_number": page_number,
                        "x0": x,
                        "y0": y,
                        "x1": x + w,
                        "y1": y + h,
                        "confidence": confidence,
                        "block_type": "text"
                    })
        return blocks

