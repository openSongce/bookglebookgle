"""
Tesseract OCR 기반 안전한 OCR 서비스
PaddleOCR 대신 Tesseract를 사용하여 안정적인 텍스트 인식
"""
import os
import time
import asyncio
import io
import gc
from typing import List, Optional, Dict, Any
from dataclasses import dataclass
from concurrent.futures import ProcessPoolExecutor, ThreadPoolExecutor
from multiprocessing import cpu_count
import functools

import cv2
import numpy as np
from PIL import Image, ImageEnhance, ImageFilter
import fitz  # PyMuPDF
from loguru import logger

try:
    import pytesseract
    TESSERACT_AVAILABLE = True
except ImportError:
    TESSERACT_AVAILABLE = False
    logger.error("❌ pytesseract not installed. Install with: pip install pytesseract")


@dataclass
class OCRBlock:
    """OCR 텍스트 블록"""
    text: str
    page_number: int
    x0: float
    y0: float
    x1: float
    y1: float
    confidence: float
    block_type: str = "text_line"


class TesseractOCREngine:
    """Tesseract OCR 엔진"""
    
    def __init__(self):
        self.is_initialized = False
        self.tesseract_config = None
        self.cpu_count = min(cpu_count(), 8)  # 최대 8개 CPU 코어 사용
        self.thread_executor = None
        self.process_executor = None
        self.use_process_pool_threshold = 5  # 5페이지 이상일 때 ProcessPool 사용
        self.stats = {
            'total_pages_processed': 0,
            'total_blocks_extracted': 0,
            'total_processing_time': 0.0,
            'successful_extractions': 0,
            'failed_extractions': 0,
            'parallel_pages_processed': 0,
            'process_pool_used': 0
        }
        logger.info(f"🔧 TesseractOCREngine initialized with {self.cpu_count} CPU cores")

    async def initialize(self) -> bool:
        """Tesseract OCR 엔진 초기화"""
        try:
            if not TESSERACT_AVAILABLE:
                logger.error("❌ pytesseract is not available")
                return False
            
            logger.info("🔄 Initializing Tesseract OCR engine...")
            
            # Tesseract 설정 - 한글+영문 OCR 최적화
            self.tesseract_config = r'--oem 3 --psm 6 -l kor+eng'
            
            # 병렬 처리를 위한 Executor 초기화
            self.thread_executor = ThreadPoolExecutor(max_workers=self.cpu_count * 2)
            # ProcessPoolExecutor는 대용량 PDF에서 GIL 우회로 성능 향상
            self.process_executor = ProcessPoolExecutor(max_workers=self.cpu_count)
            
            # 초기화 테스트
            test_success = await self._test_initialization()
            if not test_success:
                logger.warning("⚠️ Tesseract OCR initialization test failed")
                return False
            
            self.is_initialized = True
            logger.info("✅ Tesseract OCR engine initialized successfully")
            return True
            
        except Exception as e:
            logger.error(f"❌ Tesseract OCR engine initialization failed: {e}")
            return False
    
    async def _process_pages_parallel(self, pdf_document, total_pages: int, use_process_pool: bool = False) -> List[List]:
        """병렬로 여러 페이지 처리 - Executor 타입 선택 가능"""
        try:
            pool_type = "ProcessPool" if use_process_pool else "ThreadPool"
            logger.info(f"🚀 Starting parallel processing of {total_pages} pages with {pool_type}")
            
            if use_process_pool:
                # ProcessPoolExecutor 사용 - CPU 집약적 작업에 최적화
                return await self._process_pages_with_process_pool(pdf_document, total_pages)
            else:
                # ThreadPoolExecutor 사용 - 기존 방식
                return await self._process_pages_with_thread_pool(pdf_document, total_pages)
            
        except Exception as e:
            logger.error(f"❌ Parallel processing failed: {e}")
            # 병렬 처리 실패 시 순차 처리로 폴백
            return await self._process_pages_sequential(pdf_document, total_pages)
    
    async def _process_pages_with_thread_pool(self, pdf_document, total_pages: int) -> List[List]:
        """ThreadPool을 사용한 병렬 처리"""
        tasks = []
        for page_num in range(total_pages):
            task = self._process_page_tesseract(pdf_document, page_num)
            tasks.append(task)
        
        # 병렬 실행
        results = await asyncio.gather(*tasks, return_exceptions=True)
        
        # 결과 처리
        page_blocks_list = []
        for page_num, result in enumerate(results):
            if isinstance(result, Exception):
                logger.error(f"❌ Error processing page {page_num + 1}: {result}")
                page_blocks_list.append([])
            elif isinstance(result, list):
                page_blocks_list.append(result)
            else:
                logger.warning(f"⚠️ Unexpected result type for page {page_num + 1}: {type(result)}")
                page_blocks_list.append([])
        
        return page_blocks_list
    
    async def _process_pages_with_process_pool(self, pdf_document, total_pages: int) -> List[List]:
        """ProcessPool을 사용한 병렬 처리 - CPU 집약적 작업에 최적화"""
        try:
            # PDF 바이트 데이터로 변환 (프로세스 간 전송을 위해)
            pdf_bytes = pdf_document.tobytes()
            
            # ProcessPool에서 실행할 작업 준비
            loop = asyncio.get_event_loop()
            tasks = []
            
            for page_num in range(total_pages):
                task = loop.run_in_executor(
                    self.process_executor, 
                    self._process_single_page_standalone, 
                    pdf_bytes, page_num, self.tesseract_config
                )
                tasks.append(task)
            
            # 병렬 실행
            results = await asyncio.gather(*tasks, return_exceptions=True)
            
            # 결과 처리
            page_blocks_list = []
            for page_num, result in enumerate(results):
                if isinstance(result, Exception):
                    logger.error(f"❌ ProcessPool error on page {page_num + 1}: {result}")
                    page_blocks_list.append([])
                elif isinstance(result, list):
                    page_blocks_list.append(result)
                else:
                    logger.warning(f"⚠️ ProcessPool unexpected result for page {page_num + 1}: {type(result)}")
                    page_blocks_list.append([])
            
            return page_blocks_list
            
        except Exception as e:
            logger.error(f"❌ ProcessPool execution failed: {e}")
            # ProcessPool 실패 시 ThreadPool로 폴백
            logger.info("🔄 Falling back to ThreadPool")
            return await self._process_pages_with_thread_pool(pdf_document, total_pages)
    
    async def _process_pages_sequential(self, pdf_document, total_pages: int) -> List[List]:
        """순차적으로 페이지 처리 (폴백)"""
        logger.info(f"⚠️ Falling back to sequential processing for {total_pages} pages")
        
        page_blocks_list = []
        for page_num in range(total_pages):
            try:
                page_blocks = await self._process_page_tesseract(pdf_document, page_num)
                page_blocks_list.append(page_blocks)
                logger.info(f"✅ Sequential page {page_num + 1}/{total_pages}: {len(page_blocks)} blocks")
                
                # 메모리 정리
                gc.collect()
                await asyncio.sleep(0.1)
                
            except Exception as e:
                logger.error(f"❌ Error processing page {page_num + 1}: {e}")
                page_blocks_list.append([])
        
        return page_blocks_list

    async def _test_initialization(self) -> bool:
        """초기화 테스트"""
        try:
            # 간단한 테스트 이미지 생성
            test_image = Image.new('RGB', (200, 50), color='white')
            
            # 테스트 텍스트 추가 (PIL로 텍스트 그리기)
            from PIL import ImageDraw, ImageFont
            draw = ImageDraw.Draw(test_image)
            try:
                # 기본 폰트 사용
                draw.text((10, 10), "Test 테스트", fill='black')
            except:
                # 폰트 없으면 간단히 점만 찍기
                draw.point((10, 10), fill='black')
            
            loop = asyncio.get_event_loop()
            result = await loop.run_in_executor(
                None,
                self._safe_tesseract_call,
                np.array(test_image)
            )
            
            logger.info("✅ Tesseract OCR initialization test passed")
            return True
            
        except Exception as e:
            logger.error(f"Tesseract OCR initialization test failed: {e}")
            return False

    async def extract_from_pdf(self, pdf_stream: bytes) -> List[OCRBlock]:
        """PDF에서 텍스트 추출 (Tesseract 사용)"""
        if not self.is_initialized:
            raise RuntimeError("Tesseract OCR engine is not initialized")
        
        start_time = time.time()
        ocr_blocks = []
        
        try:
            logger.info("🔍 Starting Tesseract OCR extraction")
            
            # PDF 문서 열기
            pdf_document = fitz.Document(stream=pdf_stream, filetype="pdf")
            total_pages = len(pdf_document)
            logger.info(f"📄 Processing {total_pages} pages with Tesseract")
            
            # 병렬 페이지 처리 - 페이지 수에 따라 최적 방식 선택
            if total_pages > 1:
                use_process_pool = total_pages >= self.use_process_pool_threshold
                pool_type = "ProcessPool" if use_process_pool else "ThreadPool"
                logger.info(f"🚀 Using parallel processing with {self.cpu_count} workers ({pool_type})")
                
                page_blocks_list = await self._process_pages_parallel(pdf_document, total_pages, use_process_pool)
                
                # 페이지 순서대로 병합
                for page_num, page_blocks in enumerate(page_blocks_list):
                    if page_blocks:
                        ocr_blocks.extend(page_blocks)
                        logger.info(f"✅ Page {page_num + 1}/{total_pages}: {len(page_blocks)} blocks ({pool_type})")
                    
                self.stats['parallel_pages_processed'] += total_pages
                if use_process_pool:
                    self.stats['process_pool_used'] += 1
            else:
                # 단일 페이지는 기존 방식으로 처리
                try:
                    page_start_time = time.time()
                    page_blocks = await self._process_page_tesseract(pdf_document, 0)
                    page_time = time.time() - page_start_time
                    
                    ocr_blocks.extend(page_blocks)
                    logger.info(f"✅ Page 1/1: {len(page_blocks)} blocks, {page_time:.1f}s")
                        
                except Exception as e:
                    logger.error(f"❌ Error processing page 1: {e}")
            
            # 메모리 정리
            gc.collect()
            
            pdf_document.close()
            processing_time = time.time() - start_time
            
            # 통계 업데이트
            self._update_stats(total_pages, len(ocr_blocks), processing_time, True)
            
            logger.info(f"✅ Tesseract OCR extraction completed:")
            logger.info(f"   ⏱️ Processing time: {processing_time:.2f}s")
            logger.info(f"   📊 Total blocks: {len(ocr_blocks)}")
            
            return ocr_blocks
            
        except Exception as e:
            processing_time = time.time() - start_time
            self._update_stats(0, 0, processing_time, False)
            logger.error(f"❌ Tesseract OCR extraction failed: {e}")
            raise

    async def _process_page_tesseract(self, pdf_document, page_num: int) -> List[OCRBlock]:
        """Tesseract로 단일 페이지 처리"""
        try:
            page = pdf_document[page_num]
            
            # PDF 페이지를 이미지로 변환 (300 DPI 고해상도)
            mat = fitz.Matrix(300/72, 300/72)  # 300 DPI
            pix = page.get_pixmap(matrix=mat)
            
            # PIL Image로 변환
            img_data = pix.tobytes("png")
            image = Image.open(io.BytesIO(img_data))
            
            page_width = image.width
            page_height = image.height
            
            # 이미지 전처리 (Tesseract 최적화)
            processed_image = self._preprocess_for_tesseract(image)
            image_array = np.array(processed_image)
            
            logger.debug(f"Processing page {page_num + 1}: image size {page_width}x{page_height}")
            
            # Tesseract OCR 실행 - 한글 최적화
            loop = asyncio.get_event_loop()
            try:
                # 첫 번째 시도: PSM 6 (한글/영문 혼합)
                ocr_result = await asyncio.wait_for(
                    loop.run_in_executor(None, self._safe_tesseract_call, image_array),
                    timeout=60.0  # 60초 타임아웃
                )
                
                # 결과가 없거나 부족한 경우 다른 PSM으로 재시도
                if not ocr_result or len(ocr_result) < 3:
                    logger.info(f"📝 Retrying with different PSM settings for page {page_num + 1}")
                    ocr_result_alt = await asyncio.wait_for(
                        loop.run_in_executor(None, self._safe_tesseract_call_alt, image_array),
                        timeout=60.0
                    )
                    if len(ocr_result_alt) > len(ocr_result):
                        ocr_result = ocr_result_alt
                        
            except Exception as tess_err:
                logger.error(f"❌ Tesseract execution failed for page {page_num + 1}: {tess_err}")
                ocr_result = []
            
            logger.debug(f"Page {page_num + 1} Tesseract result: {len(ocr_result) if isinstance(ocr_result, list) else 'N/A'} items")
            
            # 결과 변환
            ocr_blocks = self._convert_tesseract_to_blocks(
                ocr_result, page_num + 1, page_width, page_height
            )
            
            # 메모리 정리
            pix = None
            image.close()
            processed_image.close()
            
            return ocr_blocks
            
        except Exception as e:
            logger.error(f"❌ Error processing page {page_num + 1}: {e}")
            return []

    def _preprocess_for_tesseract(self, image: Image.Image) -> Image.Image:
        """테세랙트에 최적화된 이미지 전처리 - 해상도 기반 동적 최적화"""
        try:
            # RGB 변환
            if image.mode != 'RGB':
                image = image.convert('RGB')
            
            # 이미지를 NumPy 배열로 변환
            img_array = np.array(image)
            width, height = image.width, image.height
            
            # 해상도 기반 동적 전처리 파라미터
            is_high_res = width > 2000 or height > 2000
            is_low_res = width < 800 or height < 600
            
            if is_low_res:
                # 저해상도: 부드러운 전처리 (디테일 보존)
                blur_kernel = (3, 3)  # 의미있는 블러
                clahe_clip = 1.5  # 낮은 클리핑
                clahe_grid = (4, 4)  # 작은 그리드
                sharpen_weight = 0.2  # 약한 샤프닝
            elif is_high_res:
                # 고해상도: 강화된 전처리 (세밀한 처리)
                blur_kernel = (5, 5)  # 더 큰 블러
                clahe_clip = 2.5  # 높은 클리핑
                clahe_grid = (16, 16)  # 큰 그리드
                sharpen_weight = 0.3  # 강한 샤프닝
            else:
                # 중간 해상도: 균형잡힌 전처리
                blur_kernel = (3, 3)
                clahe_clip = 2.0
                clahe_grid = (8, 8)
                sharpen_weight = 0.25
            
            # 1. 해상도에 맞는 노이즈 제거 (가우시안 블러)
            img_array = cv2.GaussianBlur(img_array, blur_kernel, 0)
            
            # 2. 적응형 대비 향상 (CLAHE)
            lab = cv2.cvtColor(img_array, cv2.COLOR_RGB2LAB)
            clahe = cv2.createCLAHE(clipLimit=clahe_clip, tileGridSize=clahe_grid)
            lab[:, :, 0] = clahe.apply(lab[:, :, 0])
            img_array = cv2.cvtColor(lab, cv2.COLOR_LAB2RGB)
            
            # 3. 해상도별 선명도 향상 (언샤프 마스크)
            if not is_low_res or sharpen_weight > 0.15:  # 저해상도에서 과도한 샤프닝 방지
                kernel = np.array([[-1,-1,-1], [-1,9,-1], [-1,-1,-1]])
                sharpened = cv2.filter2D(img_array, -1, kernel)
                img_array = cv2.addWeighted(img_array, 1-sharpen_weight, sharpened, sharpen_weight, 0)
            
            # 4. 모폴로지 연산 (고해상도에서만 적용)
            if is_high_res:
                kernel = np.ones((2, 2), np.uint8)
                img_array = cv2.morphologyEx(img_array, cv2.MORPH_CLOSE, kernel)
            
            # NumPy 배열을 PIL Image로 변환
            image = Image.fromarray(img_array)
            
            # 5. 해상도별 이미지 크기 조정
            if is_high_res:
                max_dimension = 4000  # 고해상도는 더 큰 크기 유지
            elif is_low_res:
                max_dimension = 2000  # 저해상도는 적당한 크기
            else:
                max_dimension = 3000  # 중간 해상도
                
            if max(image.width, image.height) > max_dimension:
                ratio = max_dimension / max(image.width, image.height)
                new_width = int(image.width * ratio)
                new_height = int(image.height * ratio)
                image = image.resize((new_width, new_height), Image.Resampling.LANCZOS)
            
            # 6. 해상도별 최종 조정
            contrast_boost = 1.2 if is_low_res else 1.3
            sharpness_boost = 1.1 if is_low_res else 1.2
            
            enhancer = ImageEnhance.Contrast(image)
            image = enhancer.enhance(contrast_boost)
            
            enhancer = ImageEnhance.Sharpness(image)
            image = enhancer.enhance(sharpness_boost)
            
            enhancer = ImageEnhance.Brightness(image)
            image = enhancer.enhance(1.1)
            
            logger.debug(f"Enhanced image for Tesseract: {image.width}x{image.height} (res_type: {'high' if is_high_res else 'low' if is_low_res else 'mid'})")
            return image
            
        except Exception as e:
            logger.warning(f"⚠️ Image preprocessing failed: {e}")
            if image.mode != 'RGB':
                return image.convert('RGB')
            return image

    def _safe_tesseract_call(self, image_array):
        """안전한 Tesseract 호출"""
        try:
            if image_array is None or image_array.size == 0:
                logger.debug("Empty or invalid image array")
                return []
            
            height, width = image_array.shape[:2]
            logger.debug(f"Tesseract processing image: {width}x{height}")
            
            if height < 10 or width < 10:
                logger.debug(f"Image too small: {width}x{height}")
                return []
            
            # Tesseract로 텍스트와 바운딩 박스 정보 추출
            data = pytesseract.image_to_data(
                image_array, 
                config=self.tesseract_config,
                output_type=pytesseract.Output.DICT
            )
            
            logger.debug(f"Tesseract found {len(data['text'])} text elements")
            
            # 유효한 텍스트만 필터링
            results = []
            for i in range(len(data['text'])):
                text = data['text'][i].strip()
                conf = int(data['conf'][i])
                
                # 한글 인식을 위해 낮은 신뢰도도 포함 (20 이상)
                if text and conf > 20:
                    x = data['left'][i]
                    y = data['top'][i]
                    w = data['width'][i]
                    h = data['height'][i]
                    
                    # 바운딩 박스 좌표 (4개 점 형태로 변환)
                    bbox = [
                        [x, y],
                        [x + w, y],
                        [x + w, y + h],
                        [x, y + h]
                    ]
                    
                    results.append([bbox, [text, conf / 100.0]])  # 신뢰도를 0-1 범위로 변환
            
            logger.debug(f"Filtered {len(results)} valid text blocks")
            return results
            
        except Exception as e:
            logger.error(f"Tesseract call failed: {e}")
            import traceback
            logger.debug(f"Tesseract error traceback: {traceback.format_exc()}")
            return []
    
    def _safe_tesseract_call_alt(self, image_array):
        """한글 최적화를 위한 대체 Tesseract 호출"""
        try:
            if image_array is None or image_array.size == 0:
                return []
            
            height, width = image_array.shape[:2]
            if height < 10 or width < 10:
                return []
            
            # PSM 3 (완전 자동) 또는 PSM 4 (단일 텍스트 컬럼)로 시도
            configs = [
                r'--oem 3 --psm 3 -l kor+eng',  # 완전 자동
                r'--oem 3 --psm 4 -l kor+eng',  # 단일 텍스트 컬럼
                r'--oem 3 --psm 1 -l kor+eng'   # 자동 페이지 분할 + OSD
            ]
            
            best_result = []
            for config in configs:
                try:
                    data = pytesseract.image_to_data(
                        image_array,
                        config=config,
                        output_type=pytesseract.Output.DICT
                    )
                    
                    results = []
                    for i in range(len(data['text'])):
                        text = data['text'][i].strip()
                        conf = int(data['conf'][i])
                        
                        if text and conf > 15:  # 더 낮은 임계값
                            x = data['left'][i]
                            y = data['top'][i]
                            w = data['width'][i]
                            h = data['height'][i]
                            
                            bbox = [
                                [x, y],
                                [x + w, y],
                                [x + w, y + h],
                                [x, y + h]
                            ]
                            
                            results.append([bbox, [text, conf / 100.0]])
                    
                    if len(results) > len(best_result):
                        best_result = results
                        
                except Exception:
                    continue
            
            logger.debug(f"Alternative method found {len(best_result)} text blocks")
            return best_result
            
        except Exception as e:
            logger.error(f"Alternative Tesseract call failed: {e}")
            return []

    def _convert_tesseract_to_blocks(self, ocr_result: List, page_number: int, page_width: int, page_height: int) -> List[OCRBlock]:
        """Tesseract 결과를 OCRBlock으로 변환"""
        try:
            if not ocr_result:
                return []
            
            blocks = []
            
            for line_result in ocr_result:
                if not line_result or len(line_result) != 2:
                    continue
                
                try:
                    bbox_coords = line_result[0]
                    text_info = line_result[1]
                    
                    if isinstance(text_info, dict):
                        # 새로운 딕셔너리 형태 (대체 방식에서 사용)
                        text = text_info.get('text', '')
                        confidence = float(text_info.get('confidence', 0.5))
                    elif isinstance(text_info, (list, tuple)) and len(text_info) >= 2:
                        # 기존 리스트/튜플 형태
                        text = text_info[0]
                        confidence = float(text_info[1])
                    else:
                        text = str(text_info)
                        confidence = 0.5
                    
                    if not text or not text.strip():
                        continue
                    
                    # 바운딩 박스 처리
                    if isinstance(bbox_coords, (list, tuple)) and len(bbox_coords) == 4:
                        if all(isinstance(point, (list, tuple)) and len(point) == 2 for point in bbox_coords):
                            x_coords = [float(point[0]) for point in bbox_coords]
                            y_coords = [float(point[1]) for point in bbox_coords]
                            x1, y1 = min(x_coords), min(y_coords)
                            x2, y2 = max(x_coords), max(y_coords)
                        else:
                            continue
                    else:
                        continue
                    
                    # 좌표 유효성 검증
                    if x1 < 0 or y1 < 0 or x2 <= x1 or y2 <= y1:
                        continue
                    
                    if page_width <= 0 or page_height <= 0:
                        continue
                    
                    # OCRBlock 생성
                    block = OCRBlock(
                        text=text.strip(),
                        page_number=page_number,
                        x0=min(1.0, max(0.0, x1 / page_width)),
                        y0=min(1.0, max(0.0, y1 / page_height)),
                        x1=min(1.0, max(0.0, x2 / page_width)),
                        y1=min(1.0, max(0.0, y2 / page_height)),
                        confidence=confidence,
                        block_type="text_line"
                    )
                    
                    blocks.append(block)
                    
                except Exception as line_error:
                    logger.debug(f"Line processing failed: {line_error}")
                    continue
            
            return blocks
            
        except Exception as e:
            logger.error(f"Tesseract result conversion failed: {e}")
            return []

    def _update_stats(self, pages: int, blocks: int, time_taken: float, success: bool):
        """통계 업데이트"""
        self.stats['total_pages_processed'] += pages
        self.stats['total_blocks_extracted'] += blocks
        self.stats['total_processing_time'] += time_taken
        
        if success:
            self.stats['successful_extractions'] += 1
        else:
            self.stats['failed_extractions'] += 1

    def get_stats(self) -> Dict[str, Any]:
        """통계 반환"""
        return self.stats.copy()

    async def cleanup(self):
        """리소스 정리"""
        try:
            self.is_initialized = False
            
            # Executor 종료
            if self.thread_executor:
                self.thread_executor.shutdown(wait=True)
                self.thread_executor = None
                
            if self.process_executor:
                self.process_executor.shutdown(wait=True)
                self.process_executor = None
            
            gc.collect()
            logger.info("✅ TesseractOCREngine cleanup completed")
        except Exception as e:
            logger.error(f"⚠️ Error during cleanup: {e}")


def _process_single_page_standalone(pdf_bytes: bytes, page_num: int, tesseract_config: str) -> List:
    """ProcessPool에서 사용할 독립적인 페이지 처리 함수"""
    try:
        import fitz
        import pytesseract
        import numpy as np
        from PIL import Image, ImageEnhance
        import cv2
        import io
        
        # PDF 문서 열기
        pdf_document = fitz.Document(stream=pdf_bytes, filetype="pdf")
        page = pdf_document[page_num]
        
        # PDF 페이지를 이미지로 변환 (300 DPI)
        mat = fitz.Matrix(300/72, 300/72)
        pix = page.get_pixmap(matrix=mat)
        
        # PIL Image로 변환
        img_data = pix.tobytes("png")
        image = Image.open(io.BytesIO(img_data))
        
        page_width = image.width
        page_height = image.height
        
        # 해상도 기반 동적 전처리
        processed_image = _preprocess_for_tesseract_standalone(image, page_width, page_height)
        image_array = np.array(processed_image)
        
        # Tesseract OCR 실행
        try:
            data = pytesseract.image_to_data(
                image_array, 
                config=tesseract_config,
                output_type=pytesseract.Output.DICT
            )
            
            # 결과 처리
            results = []
            for i in range(len(data['text'])):
                text = data['text'][i].strip()
                conf = int(data['conf'][i])
                
                if text and conf > 20:
                    x = data['left'][i]
                    y = data['top'][i]
                    w = data['width'][i]
                    h = data['height'][i]
                    
                    # 바운딩 박스 좌표
                    bbox = [
                        [x, y],
                        [x + w, y],
                        [x + w, y + h],
                        [x, y + h]
                    ]
                    
                    # OCRBlock 형태로 변환
                    from dataclasses import asdict
                    block = OCRBlock(
                        text=text.strip(),
                        page_number=page_num + 1,
                        x0=min(1.0, max(0.0, x / page_width)),
                        y0=min(1.0, max(0.0, y / page_height)),
                        x1=min(1.0, max(0.0, (x + w) / page_width)),
                        y1=min(1.0, max(0.0, (y + h) / page_height)),
                        confidence=conf / 100.0,
                        block_type="text_line"
                    )
                    results.append(block)
            
            # 메모리 정리
            pdf_document.close()
            image.close()
            processed_image.close()
            
            return results
            
        except Exception as tess_err:
            return []
            
    except Exception as e:
        return []

def _preprocess_for_tesseract_standalone(image: Image, width: int, height: int):
    """ProcessPool용 독립적인 이미지 전처리 함수 - 해상도 기반 최적화"""
    try:
        # RGB 변환
        if image.mode != 'RGB':
            image = image.convert('RGB')
        
        # 이미지를 NumPy 배열로 변환
        img_array = np.array(image)
        
        # 해상도 기반 동적 전처리 파라미터
        is_high_res = width > 2000 or height > 2000
        is_low_res = width < 800 or height < 600
        
        if is_low_res:
            # 저해상도: 부드러운 전처리
            blur_kernel = (3, 3)
            clahe_clip = 1.5
            clahe_grid = (4, 4)
        elif is_high_res:
            # 고해상도: 강화된 전처리
            blur_kernel = (5, 5)
            clahe_clip = 2.5
            clahe_grid = (16, 16)
        else:
            # 중간 해상도: 균형잡힌 전처리
            blur_kernel = (3, 3)
            clahe_clip = 2.0
            clahe_grid = (8, 8)
        
        # 1. 해상도에 맞는 노이즈 제거
        img_array = cv2.GaussianBlur(img_array, blur_kernel, 0)
        
        # 2. 적응형 대비 향상
        lab = cv2.cvtColor(img_array, cv2.COLOR_RGB2LAB)
        clahe = cv2.createCLAHE(clipLimit=clahe_clip, tileGridSize=clahe_grid)
        lab[:, :, 0] = clahe.apply(lab[:, :, 0])
        img_array = cv2.cvtColor(lab, cv2.COLOR_LAB2RGB)
        
        # 3. 해상도별 선명화 조정
        if not is_low_res:  # 저해상도에서는 과도한 선명화 방지
            kernel = np.array([[-1,-1,-1], [-1,9,-1], [-1,-1,-1]])
            sharpened = cv2.filter2D(img_array, -1, kernel)
            weight = 0.2 if is_low_res else 0.3
            img_array = cv2.addWeighted(img_array, 1-weight, sharpened, weight, 0)
        
        # 4. 모폴로지 연산 (고해상도에서만)
        if is_high_res:
            kernel = np.ones((2, 2), np.uint8)
            img_array = cv2.morphologyEx(img_array, cv2.MORPH_CLOSE, kernel)
        
        # NumPy 배열을 PIL Image로 변환
        image = Image.fromarray(img_array)
        
        # 5. 최적 크기로 조정
        max_dimension = 4000 if is_high_res else 3000
        if max(image.width, image.height) > max_dimension:
            ratio = max_dimension / max(image.width, image.height)
            new_width = int(image.width * ratio)
            new_height = int(image.height * ratio)
            image = image.resize((new_width, new_height), Image.Resampling.LANCZOS)
        
        # 6. 최종 조정
        enhancer = ImageEnhance.Contrast(image)
        image = enhancer.enhance(1.2 if is_low_res else 1.3)
        
        enhancer = ImageEnhance.Sharpness(image)
        image = enhancer.enhance(1.1 if is_low_res else 1.2)
        
        enhancer = ImageEnhance.Brightness(image)
        image = enhancer.enhance(1.1)
        
        return image
        
    except Exception as e:
        if image.mode != 'RGB':
            return image.convert('RGB')
        return image


# 전역 Tesseract OCR 엔진 인스턴스
tesseract_ocr_engine = TesseractOCREngine()


async def initialize_tesseract_ocr():
    """Tesseract OCR 엔진 초기화"""
    global tesseract_ocr_engine
    success = await tesseract_ocr_engine.initialize()
    if not success:
        logger.error("❌ Failed to initialize Tesseract OCR engine")
        return False
    return True


async def process_pdf_tesseract(pdf_bytes: bytes) -> List[Dict]:
    """PDF OCR 처리 (Tesseract 사용)"""
    try:
        ocr_blocks = await tesseract_ocr_engine.extract_from_pdf(pdf_bytes)
        
        # OCRBlock을 딕셔너리로 변환 (EC2 서버 호환)
        text_blocks = []
        for block in ocr_blocks:
            text_blocks.append({
                'text': block.text,
                'page_number': block.page_number,
                'bbox': {
                    'x0': block.x0,
                    'y0': block.y0,
                    'x1': block.x1,
                    'y1': block.y1
                },
                'confidence': block.confidence,
                'block_type': block.block_type
            })
        
        return text_blocks
        
    except Exception as e:
        logger.error(f"❌ Tesseract PDF OCR processing failed: {e}")
        raise


if __name__ == "__main__":
    # 테스트용 실행
    async def test_tesseract_ocr():
        logger.info("🧪 Testing Tesseract OCR engine...")
        
        success = await initialize_tesseract_ocr()
        if not success:
            logger.error("❌ Tesseract OCR engine initialization failed")
            return
        
        logger.info("✅ Tesseract OCR engine ready for processing")
        logger.info(f"📊 Stats: {tesseract_ocr_engine.get_stats()}")
    
    asyncio.run(test_tesseract_ocr())