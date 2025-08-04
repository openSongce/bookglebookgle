"""
PaddleOCR Engine for BGBG AI Server
PaddleOCR 기반 OCR 엔진 - 고성능 다국어 텍스트 인식
"""

import asyncio
import time
import traceback
import threading
from typing import List, Dict, Any, Optional, Tuple
import io
import os
from pathlib import Path

import numpy as np
import fitz  # PyMuPDF
from PIL import Image, ImageEnhance, ImageFilter
import cv2
from loguru import logger

from src.models.ocr_models import OCRBlock, BoundingBox
from src.utils.debug_utils import (
    DebugLogger, DebugContextManager, OCRDebugHelper,
    debug_operation, async_debug_operation, default_debug_logger
)


# 글로벌 PaddleOCR 인스턴스 캐시 (메모리 효율성 및 초기화 시간 단축)
_global_ocr_cache = {}
_cache_lock = threading.Lock()

def get_cached_paddleocr_instance(config_key: str, config_params: dict):
    """
    PaddleOCR 인스턴스 캐시에서 가져오거나 새로 생성
    
    Args:
        config_key: 설정 키 (캐시 식별자)
        config_params: PaddleOCR 초기화 파라미터
        
    Returns:
        PaddleOCR 인스턴스
    """
    global _global_ocr_cache, _cache_lock
    
    with _cache_lock:
        if config_key in _global_ocr_cache:
            logger.info(f"✅ Using cached PaddleOCR instance for config: {config_key}")
            return _global_ocr_cache[config_key]
        
        try:
            from paddleocr import PaddleOCR
            logger.info(f"🔄 Creating new PaddleOCR instance for config: {config_key}")
            ocr_instance = PaddleOCR(**config_params)
            _global_ocr_cache[config_key] = ocr_instance
            logger.info(f"✅ Cached new PaddleOCR instance: {config_key}")
            return ocr_instance
            
        except Exception as e:
            logger.error(f"❌ Failed to create PaddleOCR instance: {e}")
            return None

def clear_paddleocr_cache():
    """PaddleOCR 캐시 정리"""
    global _global_ocr_cache, _cache_lock
    
    with _cache_lock:
        logger.info(f"🧹 Clearing PaddleOCR cache ({len(_global_ocr_cache)} instances)")
        _global_ocr_cache.clear()

class PaddleOCRConfig:
    """PaddleOCR 설정 클래스"""
    
    def __init__(
        self,
        use_angle_cls: bool = True,
        lang: str = 'korean',
        use_gpu: bool = False,
        det_model_dir: Optional[str] = None,
        rec_model_dir: Optional[str] = None,
        cls_model_dir: Optional[str] = None,
        show_log: bool = False,
        use_space_char: bool = True
    ):
        """
        PaddleOCR 설정 초기화
        
        Args:
            use_angle_cls: 텍스트 방향 분류기 사용 여부
            lang: 언어 설정
            use_gpu: GPU 사용 여부
            det_model_dir: 커스텀 텍스트 감지 모델 경로
            rec_model_dir: 커스텀 텍스트 인식 모델 경로  
            cls_model_dir: 커스텀 방향 분류 모델 경로
            show_log: PaddleOCR 로그 출력 여부
            use_space_char: 공백 문자 인식 여부
        """
        self.use_angle_cls = use_angle_cls
        self.lang = lang
        self.use_gpu = use_gpu
        self.det_model_dir = det_model_dir
        self.rec_model_dir = rec_model_dir
        self.cls_model_dir = cls_model_dir
        self.show_log = show_log
        self.use_space_char = use_space_char


class PaddleOCREngine:
    """
    PaddleOCR 기반 텍스트 추출 엔진
    고성능 다국어 OCR을 제공하며 한국어 최적화
    """
    
    def __init__(self, config: Optional[PaddleOCRConfig] = None):
        """
        PaddleOCREngine 초기화
        
        Args:
            config: PaddleOCR 설정 객체
        """
        self.config = config or PaddleOCRConfig()
        self.ocr_instance = None
        self.is_initialized = False
        
        # 디버깅 도구 초기화
        self.debug_logger = DebugLogger("paddleocr_engine")
        self.debug_helper = OCRDebugHelper(self.debug_logger)
        
        # 성능 통계
        self.stats = {
            'total_pages_processed': 0,
            'total_blocks_extracted': 0,
            'total_processing_time': 0.0,
            'successful_extractions': 0,
            'failed_extractions': 0,
            'average_confidence': 0.0
        }
        
        logger.info(f"🚀 PaddleOCREngine initialized:")
        logger.info(f"   🌐 Language: {self.config.lang}")
        logger.info(f"   🔄 Angle classification: {self.config.use_angle_cls}")
        logger.info(f"   🖥️ Use GPU: {self.config.use_gpu}")
        logger.info(f"   🔧 Debug logging: enabled")

    async def initialize(self) -> bool:
        """
        PaddleOCR 엔진 초기화 (비동기)
        
        Returns:
            초기화 성공 여부
        """
        try:
            logger.info("🔄 Initializing PaddleOCR engine...")
            
            # PaddleOCR 임포트 및 초기화 (CPU 집약적이므로 스레드풀에서 실행)
            loop = asyncio.get_event_loop()
            self.ocr_instance = await loop.run_in_executor(
                None, 
                self._initialize_paddleocr
            )
            
            if self.ocr_instance is None:
                logger.error("❌ PaddleOCR initialization failed")
                return False
            
            self.is_initialized = True
            logger.info("✅ PaddleOCR engine initialized successfully")
            
            # 초기화 테스트
            test_success = await self._test_initialization()
            if not test_success:
                logger.warning("⚠️ PaddleOCR initialization test failed")
                return False
            
            return True
            
        except Exception as e:
            logger.error(f"❌ PaddleOCR engine initialization failed: {e}")
            logger.error(f"Traceback: {traceback.format_exc()}")
            return False

    def _initialize_paddleocr(self):
        """동기적으로 PaddleOCR 인스턴스 생성 (캐시 사용으로 성능 최적화)"""
        try:
            import os
            
            # CPU 모드 강제 설정 (GPU 관련 오류 방지)
            os.environ['CUDA_VISIBLE_DEVICES'] = ''
            
            logger.info("🔄 Attempting fast PaddleOCR initialization with caching...")
            
            # 가벼운 모델 우선 사용 (초기화 시간 단축)
            param_sets = [
                # 세트 1: 가장 가벼운 모바일 모델 (한국어)
                {
                    'use_angle_cls': False,  # 각도 분류기 비활성화로 속도 향상
                    'lang': 'korean',
                    'det_model_dir': None,  # 기본 모바일 모델 사용
                    'rec_model_dir': None,  # 기본 모바일 모델 사용
                    'use_space_char': True,
                    'drop_score': 0.3  # 낮은 신뢰도 결과 필터링
                },
                # 세트 2: 영어 모바일 모델 (fallback)
                {
                    'use_angle_cls': False,
                    'lang': 'en',
                    'drop_score': 0.3
                },
                # 세트 3: 최소 파라미터 (3.x 호환)
                {
                    'lang': 'korean'
                },
                # 세트 4: 기본 설정
                {}
            ]
            
            for i, params in enumerate(param_sets, 1):
                try:
                    # 캐시 키 생성 (파라미터 기반)
                    cache_key = f"paddleocr_{hash(str(sorted(params.items())))}"
                    
                    logger.info(f"🔧 Trying cached parameter set {i}: {list(params.keys()) if params else 'minimal'}")
                    
                    # 캐시에서 인스턴스 가져오기 (타임아웃과 함께)
                    import signal
                    import threading
                    import time
                    
                    ocr_instance = None
                    exception_holder = [None]
                    
                    def init_worker():
                        try:
                            nonlocal ocr_instance
                            ocr_instance = get_cached_paddleocr_instance(cache_key, params)
                        except Exception as e:
                            exception_holder[0] = e
                    
                    # 별도 스레드에서 초기화 (타임아웃 관리)
                    init_thread = threading.Thread(target=init_worker)
                    init_thread.daemon = True
                    init_thread.start()
                    
                    # 60초 타임아웃으로 초기화 대기 (캐시 사용 시 더 빠름)
                    init_thread.join(timeout=60.0)
                    
                    if init_thread.is_alive():
                        logger.warning(f"⏰ Parameter set {i} initialization timed out (60s)")
                        continue
                    
                    if exception_holder[0]:
                        raise exception_holder[0]
                    
                    if ocr_instance is None:
                        logger.warning(f"⚠️ Parameter set {i} returned None")
                        continue
                    
                    logger.info(f"✅ PaddleOCR initialized successfully with cached parameter set {i}")
                    logger.info(f"🖥️ Using CPU mode (CUDA_VISIBLE_DEVICES='')")
                    logger.info(f"🌐 Language: {params.get('lang', 'auto')}")
                    logger.info(f"🔄 Angle classification: {params.get('use_angle_cls', 'default')}")
                    logger.info(f"💾 Cache key: {cache_key}")
                    
                    # 최신 버전 확인
                    try:
                        if hasattr(ocr_instance, 'predict'):
                            logger.info("✅ Using latest PaddleOCR 3.x+ API (predict method)")
                        else:
                            logger.warning("⚠️ Using legacy PaddleOCR API (ocr method)")
                    except:
                        logger.info("✅ PaddleOCR instance created successfully")
                    
                    return ocr_instance
                    
                except Exception as e:
                    logger.warning(f"⚠️ Fast parameter set {i} failed: {str(e)}")
                    if i < len(param_sets):
                        logger.info(f"🔄 Trying next parameter set...")
                    continue
            
            # 모든 파라미터 세트가 실패한 경우
            raise Exception("All fast parameter sets failed during PaddleOCR initialization")
            
        except ImportError as e:
            logger.error(f"PaddleOCR import failed: {e}")
            logger.error("Please install latest PaddleOCR: pip install paddleocr")
            return None
        except Exception as e:
            logger.error(f"PaddleOCR initialization error: {e}")
            return None

    async def _test_initialization(self) -> bool:
        """PaddleOCR 초기화 테스트"""
        try:
            # 간단한 테스트 이미지 생성 (흰 배경에 검은 텍스트)
            test_image = Image.new('RGB', (200, 50), color='white')
            
            # 테스트 OCR 실행
            loop = asyncio.get_event_loop()
            result = await loop.run_in_executor(
                None,
                self.ocr_instance.ocr,
                np.array(test_image)
            )
            
            logger.info("✅ PaddleOCR initialization test passed")
            return True
            
        except Exception as e:
            logger.error(f"PaddleOCR initialization test failed: {e}")
            return False

    async def extract_from_pdf(self, pdf_stream: bytes, document_id: str) -> List[OCRBlock]:
        """
        PDF에서 텍스트 추출
        
        Args:
            pdf_stream: PDF 바이트 스트림
            document_id: 문서 ID
            
        Returns:
            OCR 블록 리스트
        """
        if not self.is_initialized:
            raise RuntimeError("PaddleOCR engine is not initialized")
        
        start_time = time.time()
        ocr_blocks = []
        
        try:
            logger.info(f"🔍 Starting PaddleOCR extraction for document: {document_id}")
            
            # PDF 문서 열기
            if hasattr(fitz, 'Document'):
                pdf_document = fitz.Document(stream=pdf_stream, filetype="pdf")
            else:
                pdf_document = fitz.open(stream=pdf_stream, filetype="pdf")
            
            total_pages = len(pdf_document)
            logger.info(f"📄 Processing {total_pages} pages")
            
            # 배치 단위로 페이지 처리 (메모리 효율성)
            batch_size = 2  # 한 번에 2페이지씩 처리 (메모리 최적화)
            
            for batch_start in range(0, total_pages, batch_size):
                batch_end = min(batch_start + batch_size, total_pages)
                logger.info(f"📄 Processing batch {batch_start//batch_size + 1}: pages {batch_start + 1}-{batch_end}")
                
                # 배치 내 페이지들 처리
                for page_num in range(batch_start, batch_end):
                    try:
                        page_start_time = time.time()
                        page_blocks = await self._process_page(pdf_document, page_num)
                        page_time = time.time() - page_start_time
                        
                        ocr_blocks.extend(page_blocks)
                        
                        logger.info(f"✅ Page {page_num + 1}/{total_pages}: {len(page_blocks)} blocks, {page_time:.1f}s")
                        
                        # 메모리 정리를 위한 작은 지연
                        if page_num % 3 == 0:  # 3페이지마다
                            await asyncio.sleep(0.1)
                            
                    except Exception as e:
                        logger.error(f"❌ Error processing page {page_num + 1}: {e}")
                        continue  # 개별 페이지 오류는 건너뛰고 계속 진행
                
                # 배치 완료 후 진행 상황 로그
                total_blocks = len(ocr_blocks)
                elapsed = time.time() - start_time
                logger.info(f"🔄 Batch completed. Total blocks so far: {total_blocks}, Elapsed: {elapsed:.1f}s")
            
            # PDF 문서 정리
            pdf_document.close()
            
            processing_time = time.time() - start_time
            
            # 통계 업데이트
            self._update_stats(total_pages, len(ocr_blocks), processing_time, True)
            
            logger.info(f"✅ PaddleOCR extraction completed for {document_id}:")
            logger.info(f"   ⏱️ Processing time: {processing_time:.2f}s")
            logger.info(f"   📊 Total blocks: {len(ocr_blocks)}")
            logger.info(f"   📝 Total characters: {sum(len(block.text) for block in ocr_blocks)}")
            logger.info(f"   🎯 Average confidence: {np.mean([block.confidence for block in ocr_blocks]):.3f}")
            
            return ocr_blocks
            
        except Exception as e:
            processing_time = time.time() - start_time
            self._update_stats(0, 0, processing_time, False)
            logger.error(f"❌ PaddleOCR extraction failed for {document_id}: {e}")
            raise

    async def _process_page(self, pdf_document, page_num: int, page_width: int = None, page_height: int = None) -> List[OCRBlock]:
        """단일 페이지 처리 (메모리 효율적)"""
        pix = None
        image = None
        image_array = None
        
        try:
            # PDF 문서에서 페이지 처리
            if hasattr(pdf_document, '__getitem__'):
                page = pdf_document[page_num]
                
                # 페이지를 이미지로 변환 (성능 최적화)
                # OCR 품질을 유지하면서 처리 속도 향상을 위해 150 DPI 사용
                mat = fitz.Matrix(150/72, 150/72)  # 150 DPI 변환 행렬 (성능 최적화)
                pix = page.get_pixmap(matrix=mat)
                
                # PIL Image로 변환
                img_data = pix.tobytes("png")
                image = Image.open(io.BytesIO(img_data))
                
                # 이미지 크기 확인 및 필요시 리사이징 (성능 최적화)
                max_pixels = 1500000  # 1.5M 픽셀로 제한 (더 작게)
                if image.width * image.height > max_pixels:
                    ratio = (max_pixels / (image.width * image.height)) ** 0.5
                    new_width = int(image.width * ratio)
                    new_height = int(image.height * ratio)
                    logger.debug(f"📏 Resizing page {page_num + 1}: {image.width}x{image.height} → {new_width}x{new_height}")
                    # 더 빠른 리샘플링 방법 사용
                    image = image.resize((new_width, new_height), Image.Resampling.BILINEAR)
                
                # PDF에서 추출한 이미지의 크기 사용
                page_width = image.width
                page_height = image.height
            else:
                # 이미지 객체 직접 처리 (테스트용)
                image = pdf_document
                # 전달된 페이지 크기 사용
                if page_width is None:
                    page_width = image.width
                if page_height is None:
                    page_height = image.height
            
            logger.debug(f"📄 페이지 이미지: ({page_width}, {page_height}), {image.mode}")
            
            # 이미지 전처리로 OCR 정확도 향상 (안전한 모드)
            try:
                processed_image = self._preprocess_image(image)
                image_array = np.array(processed_image)
                logger.debug(f"🔍 Page {page_num + 1}: Image preprocessing completed, shape: {image_array.shape}")
            except Exception as preprocess_error:
                logger.warning(f"⚠️ Page {page_num + 1}: Image preprocessing failed: {preprocess_error}, using original")
                image_array = np.array(image)
            
            # PaddleOCR로 텍스트 추출 (타임아웃 포함)
            loop = asyncio.get_event_loop()
            try:
                logger.debug(f"🔍 Page {page_num + 1}: Starting OCR processing")
                ocr_result = await asyncio.wait_for(
                    loop.run_in_executor(
                        None,
                        self._safe_ocr_call,
                        image_array
                    ),
                    timeout=60.0  # 페이지당 60초 타임아웃 (초기화 지연 대응)
                )
                logger.debug(f"🔍 Page {page_num + 1}: OCR completed, result type: {type(ocr_result)}, length: {len(ocr_result) if hasattr(ocr_result, '__len__') else 'N/A'}")
                
            except asyncio.TimeoutError:
                logger.warning(f"⏰ Page {page_num + 1} processing timed out (60s), skipping")
                return []
            except Exception as ocr_error:
                logger.error(f"❌ Page {page_num + 1}: OCR processing failed: {ocr_error}")
                return []
            
            # 결과를 OCRBlock으로 변환
            try:
                logger.debug(f"🔍 Page {page_num + 1}: Converting OCR result to blocks")
                ocr_blocks = self._convert_to_ocr_blocks(ocr_result, page_num + 1, page_width, page_height)
                logger.debug(f"🔍 Page {page_num + 1}: Conversion completed, {len(ocr_blocks)} blocks created")
            except Exception as convert_error:
                logger.error(f"❌ Page {page_num + 1}: OCR result conversion failed: {convert_error}")
                logger.error(f"🔍 Page {page_num + 1}: OCR result preview: {str(ocr_result)[:200]}...")
                return []
            
            return ocr_blocks
            
        except Exception as e:
            logger.error(f"❌ Error processing page {page_num + 1}: {e}")
            return []
        
        finally:
            # 메모리 정리 (확실한 해제)
            try:
                if pix is not None:
                    pix = None
                if image is not None:
                    image.close()
                    image = None
                if image_array is not None:
                    del image_array
                    image_array = None
            except:
                pass  # 정리 중 오류는 무시

    def _preprocess_image(self, image: Image.Image) -> Image.Image:
        """
        이미지 전처리로 OCR 정확도 향상 (PaddleOCR 3.1.0+ RGB 형식 유지)
        
        Args:
            image: 원본 PIL 이미지
            
        Returns:
            전처리된 PIL 이미지 (RGB 형식 유지)
        """
        try:
            # PaddleOCR 3.1.0은 RGB 형식을 요구하므로 RGB로 변환
            if image.mode != 'RGB':
                image = image.convert('RGB')
            
            # 1. 대비 향상 (텍스트와 배경 구분도 향상)
            enhancer = ImageEnhance.Contrast(image)
            image = enhancer.enhance(1.2)  # 대비 20% 향상
            
            # 2. 선명도 향상 (흐린 텍스트 개선)
            enhancer = ImageEnhance.Sharpness(image)
            image = enhancer.enhance(1.1)  # 선명도 10% 향상
            
            # 3. 밝기 조정 (너무 어두운 이미지 보정)
            enhancer = ImageEnhance.Brightness(image)
            image = enhancer.enhance(1.05)  # 밝기 5% 향상
            
            # 4. OpenCV를 사용한 고급 전처리 (RGB 채널 유지)
            image_array = np.array(image)
            
            # 각 채널별로 가우시안 블러 적용 (노이즈 제거)
            for i in range(3):  # R, G, B 채널
                image_array[:, :, i] = cv2.GaussianBlur(image_array[:, :, i], (3, 3), 0)
            
            # 그레이스케일로 변환하여 이진화 처리 후 다시 RGB로 변환
            gray = cv2.cvtColor(image_array, cv2.COLOR_RGB2GRAY)
            
            # 적응적 임계값 처리 (다양한 조명 조건 대응)
            binary = cv2.adaptiveThreshold(
                gray, 255, cv2.ADAPTIVE_THRESH_GAUSSIAN_C, 
                cv2.THRESH_BINARY, 11, 2
            )
            
            # 모폴로지 연산으로 텍스트 연결성 개선
            kernel = np.ones((2, 2), np.uint8)
            binary = cv2.morphologyEx(binary, cv2.MORPH_CLOSE, kernel)
            
            # 이진화된 결과를 RGB로 변환 (PaddleOCR 3.1.0 호환성)
            rgb_binary = cv2.cvtColor(binary, cv2.COLOR_GRAY2RGB)
            
            # PIL 이미지로 변환
            processed_image = Image.fromarray(rgb_binary)
            
            return processed_image
            
        except Exception as e:
            logger.warning(f"⚠️ Image preprocessing failed: {e}, using original image")
            # 원본 이미지를 RGB로 변환하여 반환
            if image.mode != 'RGB':
                return image.convert('RGB')
            return image
    
    def _safe_ocr_call(self, image_array):
        """안전한 PaddleOCR 호출 래퍼 (PaddleOCR 3.1.0+ 대응)"""
        try:
            # 이미지 배열 유효성 검사
            if image_array is None or image_array.size == 0:
                logger.debug("📄 Empty or invalid image array")
                return []
            
            # 이미지 크기 및 형태 검사
            if len(image_array.shape) < 2:
                logger.debug(f"📄 Invalid image shape: {image_array.shape}")
                return []
            
            # 이미지가 너무 작은 경우
            height, width = image_array.shape[:2]
            if height < 10 or width < 10:
                logger.debug(f"📄 Image too small: {width}x{height}")
                return []
            
            # PaddleOCR 호출 (최신 predict 메서드 사용)
            if hasattr(self.ocr_instance, 'predict'):
                # PaddleOCR 3.x+ 새 방식
                logger.debug(f"📄 Using PaddleOCR predict method")
                result = self.ocr_instance.predict(image_array)
                logger.debug(f"📄 PaddleOCR predict result type: {type(result)}")
                
                # 결과 처리
                if result is None:
                    logger.debug(f"📄 PaddleOCR predict returned None")
                    return []
                
                # OCRResult 객체인 경우
                if hasattr(result, '__class__') and 'OCRResult' in str(type(result)):
                    logger.debug(f"📄 Got OCRResult object, converting to list")
                    return [result]
                # 리스트인 경우
                elif isinstance(result, list):
                    logger.debug(f"📄 Got list result with {len(result)} items")
                    return result
                # 딕셔너리인 경우 (일부 버전)
                elif isinstance(result, dict):
                    logger.debug(f"📄 Got dict result, converting to OCRResult-like object")
                    # 딕셔너리를 간단한 OCRResult 객체로 변환
                    class SimpleOCRResult:
                        def __init__(self, data):
                            self.rec_texts = data.get('rec_texts', [])
                            self.rec_scores = data.get('rec_scores', [])
                            self.rec_polys = data.get('rec_polys', [])
                            self.rec_boxes = data.get('rec_boxes', [])
                    return [SimpleOCRResult(result)]
                else:
                    logger.debug(f"📄 Unexpected predict result type: {type(result)}")
                    return []
            else:
                # 구 방식 (fallback)
                logger.debug(f"📄 Using legacy PaddleOCR ocr method")
                result = self.ocr_instance.ocr(image_array)
                logger.debug(f"📄 PaddleOCR ocr result type: {type(result)}")
                
                if isinstance(result, list) and len(result) > 0:
                    return result
                else:
                    logger.debug(f"📄 Empty or invalid ocr result: {result}")
                    return []
            
        except Exception as e:
            logger.debug(f"📄 Safe OCR call failed: {e}")
            # 상세한 오류 정보 로깅
            import traceback
            logger.debug(f"📄 Error traceback: {traceback.format_exc()}")
            # PaddleOCR 내부 오류는 빈 결과로 처리
            return []

    def _convert_to_ocr_blocks(
        self, 
        ocr_result: Any, 
        page_number: int, 
        page_width: int, 
        page_height: int
    ) -> List[OCRBlock]:
        """PaddleOCR 결과를 OCRBlock으로 변환 (3.1.0+ 호환)"""
        try:
            if not ocr_result:
                logger.warning(f"페이지 {page_number}: OCR 결과가 비어있습니다")
                return []
            
            # PaddleOCR 3.1.0+ 결과 구조 처리
            if isinstance(ocr_result, list) and len(ocr_result) > 0:
                # 첫 번째 요소가 OCRResult 객체인 경우
                first_result = ocr_result[0]
                
                # OCRResult 객체 확인 (타입 이름으로 확인)
                result_type_name = type(first_result).__name__
                logger.debug(f"페이지 {page_number}: 첫 번째 결과 타입: {result_type_name}")
                
                if 'OCRResult' in result_type_name:
                    logger.info(f"페이지 {page_number}: PaddleOCR 3.1.0+ OCRResult 객체 감지")
                    return self._convert_ocr_result_format(
                        first_result, page_number, page_width, page_height
                    )
                
                # hasattr로 OCRResult 속성 확인
                elif hasattr(first_result, 'rec_texts') and hasattr(first_result, 'rec_scores'):
                    logger.info(f"페이지 {page_number}: OCRResult 속성을 가진 객체 감지")
                    return self._convert_ocr_result_format(
                        first_result, page_number, page_width, page_height
                    )
                
                # 기존 리스트 형태 (하위 호환성)
                elif isinstance(first_result, list):
                    logger.info(f"페이지 {page_number}: 기존 리스트 형태 결과 감지")
                    return self._convert_legacy_format(
                        first_result, page_number, page_width, page_height
                    )
                
                # None 또는 빈 결과
                elif first_result is None:
                    logger.warning(f"페이지 {page_number}: 첫 번째 결과가 None입니다")
                    return []
                    
                # 알 수 없는 형태지만 딕셔너리처럼 동작하는지 확인
                else:
                    logger.debug(f"페이지 {page_number}: 알 수 없는 형태, 딕셔너리 방식 시도: {type(first_result)}")
                    try:
                        # getattr로 속성 확인
                        rec_texts = getattr(first_result, 'rec_texts', None)
                        if rec_texts is not None:
                            logger.info(f"페이지 {page_number}: getattr로 OCRResult 속성 확인됨")
                            return self._convert_ocr_result_format(
                                first_result, page_number, page_width, page_height
                            )
                        else:
                            logger.warning(f"페이지 {page_number}: rec_texts 속성이 없습니다")
                    except Exception as attr_error:
                        logger.debug(f"페이지 {page_number}: 속성 접근 실패: {attr_error}")
            
            logger.warning(f"페이지 {page_number}: 알 수 없는 OCR 결과 형태: {type(ocr_result)}")
            if hasattr(ocr_result, '__dict__'):
                logger.debug(f"페이지 {page_number}: 객체 속성들: {list(vars(ocr_result).keys())}")
            return []
            
        except Exception as e:
            logger.error(f"페이지 {page_number}: OCR 블록 변환 실패: {e}")
            logger.error(f"결과 타입: {type(ocr_result)}")
            return []

    def _convert_ocr_result_format(self, ocr_result_obj, page_number: int, page_width: int, page_height: int) -> List[OCRBlock]:
        """PaddleOCR 3.1.0+ OCRResult 객체를 OCRBlock으로 변환"""
        try:
            # 딕셔너리 형태의 OCRResult 객체에서 데이터 추출
            if isinstance(ocr_result_obj, dict):
                rec_texts = ocr_result_obj.get('rec_texts', [])
                rec_scores = ocr_result_obj.get('rec_scores', [])
                rec_polys = ocr_result_obj.get('rec_polys', [])
                rec_boxes = ocr_result_obj.get('rec_boxes', None)
            else:
                # 객체 속성으로부터 데이터 추출
                rec_texts = getattr(ocr_result_obj, 'rec_texts', [])
                rec_scores = getattr(ocr_result_obj, 'rec_scores', [])
                rec_polys = getattr(ocr_result_obj, 'rec_polys', [])
                rec_boxes = getattr(ocr_result_obj, 'rec_boxes', None)
            
            # 데이터 길이 확인
            text_count = len(rec_texts)
            score_count = len(rec_scores)
            poly_count = len(rec_polys)
            
            logger.info(f"페이지 {page_number}: 텍스트 {text_count}개, 점수 {score_count}개, 좌표 {poly_count}개")
            
            # 디버깅: rec_boxes와 rec_polys의 실제 내용 확인
            if rec_boxes is not None and len(rec_boxes) > 0:
                logger.debug(f"페이지 {page_number}: rec_boxes 타입: {type(rec_boxes[0])}")
                logger.debug(f"페이지 {page_number}: rec_boxes 샘플: {rec_boxes[0]}")
            
            if rec_polys is not None and len(rec_polys) > 0:
                logger.debug(f"페이지 {page_number}: rec_polys 타입: {type(rec_polys[0])}")
                logger.debug(f"페이지 {page_number}: rec_polys 샘플: {rec_polys[0]}")
            
            if text_count == 0:
                logger.warning(f"페이지 {page_number}: 추출된 텍스트가 없습니다")
                return []
            
            # 데이터 길이 일치 확인
            min_length = min(text_count, score_count, poly_count)
            if text_count != score_count or text_count != poly_count:
                logger.warning(f"페이지 {page_number}: 데이터 길이 불일치 - 최소값 {min_length} 사용")
            
            blocks = []
            for i in range(min_length):
                try:
                    text = rec_texts[i] if i < len(rec_texts) else ""
                    confidence = rec_scores[i] if i < len(rec_scores) else 0.0
                    
                    # 디버깅: 루프 변수와 데이터 확인
                    logger.debug(f"페이지 {page_number}: 루프 i={i}, text='{text[:30]}...', confidence={confidence}")
                    
                    # 빈 텍스트 스킵
                    if not text or not text.strip():
                        continue
                    
                    # 좌표 처리 - rec_boxes가 우선적으로 사용됨
                    bbox_points = None
                    if rec_boxes is not None and i < len(rec_boxes):
                        # rec_boxes 처리 (numpy 배열 또는 다른 형태)
                        bbox_points = rec_boxes[i]
                        logger.debug(f"페이지 {page_number}: rec_boxes[{i}] 타입: {type(bbox_points)}, 값: {bbox_points}")
                        
                        # numpy 배열인 경우 리스트로 변환
                        if hasattr(bbox_points, 'tolist'):
                            bbox_points = bbox_points.tolist()
                            logger.debug(f"페이지 {page_number}: rec_boxes[{i}]를 리스트로 변환: {bbox_points}")
                    elif rec_polys is not None and i < len(rec_polys):
                        # rec_polys 사용 (다각형 좌표)
                        bbox_points = rec_polys[i]
                        logger.debug(f"페이지 {page_number}: rec_polys[{i}] = {bbox_points}")
                        
                        # numpy 배열인 경우 리스트로 변환
                        if hasattr(bbox_points, 'tolist'):
                            bbox_points = bbox_points.tolist()
                    
                    if bbox_points is not None:
                        try:
                            # numpy 배열인 경우 리스트로 변환
                            if hasattr(bbox_points, 'tolist'):
                                bbox_list = bbox_points.tolist()
                            else:
                                bbox_list = bbox_points
                            
                            # bbox_list가 문자열인 경우 처리
                            if isinstance(bbox_list, str):
                                logger.warning(f"페이지 {page_number}, 라인 {i}: 좌표가 문자열 형식입니다: {bbox_list[:50]}...")
                                continue
                                
                            # 좌표 정규화 및 블록 생성
                            block = self._process_bbox_and_create_block(
                                text, confidence, bbox_list, page_number, page_width, page_height
                            )
                            if block:
                                blocks.append(block)
                                
                        except Exception as bbox_error:
                            logger.warning(f"페이지 {page_number}, 라인 {i}: 좌표 처리 실패: {bbox_error}")
                            continue
                    else:
                        logger.warning(f"페이지 {page_number}, 라인 {i}: 좌표 정보 없음")
                        continue
                        
                except Exception as line_error:
                    logger.warning(f"페이지 {page_number}, 라인 {i}: 처리 실패: {line_error}")
                    continue
            
            logger.info(f"페이지 {page_number}: {len(blocks)}개 블록 생성")
            return blocks
            
        except Exception as e:
            logger.error(f"페이지 {page_number}: OCRResult 변환 실패: {e}")
            import traceback
            logger.error(f"Traceback: {traceback.format_exc()}")
            return []

    def _convert_legacy_format(self, legacy_result: List, page_number: int, page_width: int, page_height: int) -> List[OCRBlock]:
        """기존 PaddleOCR 형태 (리스트) 결과를 OCRBlock으로 변환"""
        try:
            if not legacy_result:
                logger.warning(f"페이지 {page_number}: 기존 형태 결과가 비어있습니다")
                return []
            
            blocks = []
            for i, line in enumerate(legacy_result):
                try:
                    if not line or len(line) < 2:
                        continue
                    
                    # 기존 형태: [좌표, (텍스트, 신뢰도)]
                    bbox = line[0]
                    text_info = line[1]
                    
                    if isinstance(text_info, (list, tuple)) and len(text_info) >= 2:
                        text = text_info[0]
                        confidence = text_info[1]
                    else:
                        text = str(text_info)
                        confidence = 0.5
                    
                    # 빈 텍스트 스킵
                    if not text or not text.strip():
                        continue
                    
                    # 블록 생성
                    block = self._process_bbox_and_create_block(
                        text, confidence, bbox, page_number, page_width, page_height
                    )
                    if block:
                        blocks.append(block)
                        
                except Exception as line_error:
                    logger.warning(f"페이지 {page_number}, 기존 형태 라인 {i}: 처리 실패: {line_error}")
                    continue
            
            logger.info(f"페이지 {page_number}: 기존 형태에서 {len(blocks)}개 블록 생성")
            return blocks
            
        except Exception as e:
            logger.error(f"페이지 {page_number}: 기존 형태 변환 실패: {e}")
            return []
    
    def _process_bbox_and_create_block(
        self,
        text: str,
        confidence: float,
        bbox_points,
        page_number: int,
        page_width: int,
        page_height: int
    ) -> Optional[OCRBlock]:
        """바운딩 박스를 처리하고 OCRBlock을 생성"""
        try:
            # 바운딩 박스 형태 정규화
            if isinstance(bbox_points, (list, tuple)):
                if len(bbox_points) == 4:
                    # [x1, y1, x2, y2] 형태인 경우
                    if all(isinstance(coord, (int, float)) for coord in bbox_points):
                        x1, y1, x2, y2 = bbox_points
                    # [[x1, y1], [x2, y2], [x3, y3], [x4, y4]] 형태인 경우
                    elif all(isinstance(point, (list, tuple)) and len(point) == 2 for point in bbox_points):
                        x_coords = [float(point[0]) for point in bbox_points]
                        y_coords = [float(point[1]) for point in bbox_points]
                        x1, y1 = min(x_coords), min(y_coords)
                        x2, y2 = max(x_coords), max(y_coords)
                    else:
                        logger.debug(f"📄 Page {page_number} - invalid bbox format: {bbox_points}")
                        return None
                else:
                    logger.debug(f"📄 Page {page_number} - bbox length not 4: {len(bbox_points)}")
                    return None
            else:
                logger.debug(f"📄 Page {page_number} - bbox not list/tuple: {type(bbox_points)}")
                logger.debug(f"📄 Page {page_number} - bbox content: {bbox_points}")
                return None
            
            # 좌표 유효성 검증
            if x1 < 0 or y1 < 0 or x2 <= x1 or y2 <= y1:
                logger.debug(f"📄 Page {page_number} - invalid coordinates: ({x1}, {y1}, {x2}, {y2})")
                return None
            
            # 페이지 크기 유효성 검증
            if page_width <= 0 or page_height <= 0:
                logger.debug(f"📄 Page {page_number} - invalid page dimensions: {page_width}x{page_height}")
                return None
            
            # 좌표를 0-1 범위로 정규화
            normalized_bbox = BoundingBox(
                x0=min(1.0, max(0.0, x1 / page_width)),
                y0=min(1.0, max(0.0, y1 / page_height)),
                x1=min(1.0, max(0.0, x2 / page_width)),
                y1=min(1.0, max(0.0, y2 / page_height))
            )
            
            # OCRBlock 생성
            ocr_block = OCRBlock(
                text=text.strip(),
                page_number=page_number,
                bbox=normalized_bbox,
                confidence=confidence,
                block_type="text_line"
            )
            
            return ocr_block
            
        except Exception as e:
            logger.debug(f"📄 Page {page_number} - error processing bbox: {e}")
            return None

    def _convert_alternative_ocr_format(
        self, 
        ocr_result, 
        page_number: int, 
        page_width: int, 
        page_height: int
    ) -> List[OCRBlock]:
        """대체 OCR 결과 형태 처리 ([bbox_points, text, confidence] 형태)"""
        ocr_blocks = []
        
        for i, item in enumerate(ocr_result):
            try:
                if not item or len(item) != 3:
                    continue
                    
                bbox_points, text, confidence = item
                
                # 기본 유효성 검사
                if not text or not isinstance(text, str) or not text.strip():
                    continue
                    
                if not isinstance(confidence, (int, float)):
                    confidence = 0.5
                    
                # 바운딩 박스 처리
                if not isinstance(bbox_points, (list, tuple)) or len(bbox_points) != 4:
                    continue
                    
                # 좌표 처리
                try:
                    x_coords = [float(point[0]) for point in bbox_points]
                    y_coords = [float(point[1]) for point in bbox_points]
                    
                    x1, y1 = min(x_coords), min(y_coords)
                    x2, y2 = max(x_coords), max(y_coords)
                    
                    if x1 < 0 or y1 < 0 or x2 <= x1 or y2 <= y1 or page_width <= 0 or page_height <= 0:
                        continue
                        
                    normalized_bbox = BoundingBox(
                        x1=min(1.0, max(0.0, x1 / page_width)),
                        y1=min(1.0, max(0.0, y1 / page_height)),
                        x2=min(1.0, max(0.0, x2 / page_width)),
                        y2=min(1.0, max(0.0, y2 / page_height))
                    )
                    
                    ocr_block = OCRBlock(
                        text=text.strip(),
                        page_number=page_number,
                        bbox=normalized_bbox,
                        confidence=confidence,
                        block_type="text_line"
                    )
                    
                    ocr_blocks.append(ocr_block)
                    logger.debug(f"✅ Page {page_number}: Alt format - parsed line {i}: '{text[:30]}...' (confidence: {confidence:.3f})")
                    
                except Exception as coord_error:
                    logger.debug(f"📄 Page {page_number}: Alt format - coordinate error on line {i}: {coord_error}")
                    continue
                    
            except Exception as item_error:
                logger.debug(f"📄 Page {page_number}: Alt format - error processing item {i}: {item_error}")
                continue
                
        logger.debug(f"📄 Page {page_number}: Alt format - final result: {len(ocr_blocks)} blocks")
        return ocr_blocks

    def _update_stats(self, pages: int, blocks: int, time_taken: float, success: bool):
        """통계 업데이트"""
        self.stats['total_pages_processed'] += pages
        self.stats['total_blocks_extracted'] += blocks
        self.stats['total_processing_time'] += time_taken
        
        if success:
            self.stats['successful_extractions'] += 1
        else:
            self.stats['failed_extractions'] += 1

    def get_engine_info(self) -> Dict[str, Any]:
        """엔진 정보 반환"""
        return {
            "engine_name": "PaddleOCREngine",
            "version": "1.0.0",
            "language": self.config.lang,
            "use_gpu": self.config.use_gpu,
            "use_angle_cls": self.config.use_angle_cls,
            "is_initialized": self.is_initialized,
            "statistics": self.stats.copy()
        }

    def get_processing_stats(self) -> Dict[str, Any]:
        """처리 통계 반환"""
        stats = self.stats.copy()
        
        # 계산된 통계 추가
        total_extractions = stats['successful_extractions'] + stats['failed_extractions']
        if total_extractions > 0:
            stats['success_rate'] = stats['successful_extractions'] / total_extractions
        
        if stats['successful_extractions'] > 0:
            stats['average_processing_time'] = stats['total_processing_time'] / stats['successful_extractions']
            stats['average_pages_per_extraction'] = stats['total_pages_processed'] / stats['successful_extractions']
            stats['average_blocks_per_page'] = (stats['total_blocks_extracted'] / stats['total_pages_processed'] 
                                              if stats['total_pages_processed'] > 0 else 0)
        
        return stats

    async def cleanup(self):
        """리소스 정리"""
        try:
            if self.ocr_instance:
                # PaddleOCR 인스턴스 정리
                self.ocr_instance = None
            
            self.is_initialized = False
            logger.info("✅ PaddleOCREngine cleanup completed")
            
        except Exception as e:
            logger.error(f"⚠️ Error during PaddleOCREngine cleanup: {e}")