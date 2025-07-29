"""
Enhanced OCR Service for processing PDF documents with multiple OCR engines
Supports PaddleOCR, EasyOCR, and Tesseract with image preprocessing
"""

import asyncio
import io
import numpy as np
from abc import ABC, abstractmethod
from typing import List, Dict, Any, Optional, Union
from dataclasses import dataclass
from enum import Enum

from PIL import Image, ImageEnhance, ImageFilter
from loguru import logger

try:
    import fitz  # PyMuPDF
    PYMUPDF_AVAILABLE = True
    # Get version safely
    try:
        version_info = getattr(fitz, 'version', ['unknown'])
        if isinstance(version_info, (list, tuple)) and len(version_info) > 0:
            version = version_info[0]
        else:
            version = str(version_info) if version_info else 'unknown'
    except:
        version = 'unknown'
    logger.info(f"PyMuPDF loaded successfully, version: {version}")
except ImportError as e:
    PYMUPDF_AVAILABLE = False
    logger.error(f"PyMuPDF not available: {e}")
    fitz = None

try:
    import cv2
    OPENCV_AVAILABLE = True
except ImportError:
    OPENCV_AVAILABLE = False
    logger.warning("OpenCV not available, advanced preprocessing disabled")

# OCR Engine imports
try:
    from paddleocr import PaddleOCR
    PADDLEOCR_AVAILABLE = True
except ImportError:
    PADDLEOCR_AVAILABLE = False
    logger.warning("PaddleOCR not available, falling back to other engines")

try:
    import easyocr
    EASYOCR_AVAILABLE = True
except ImportError:
    EASYOCR_AVAILABLE = False
    logger.warning("EasyOCR not available")

try:
    import pytesseract
    TESSERACT_AVAILABLE = True
except ImportError:
    TESSERACT_AVAILABLE = False
    logger.warning("Tesseract not available")


class OCREngine(Enum):
    """Available OCR engines"""
    PADDLEOCR = "paddleocr"
    EASYOCR = "easyocr"
    TESSERACT = "tesseract"


@dataclass
class OCRResult:
    """Standard OCR result format"""
    text: str
    confidence: float
    bbox: List[float]  # [x0, y0, x1, y1]
    page_number: int


@dataclass 
class OCRConfig:
    """OCR configuration settings"""
    primary_engine: OCREngine = OCREngine.TESSERACT  # Balanced: Tesseract 우선
    fallback_engines: List[OCREngine] = None
    languages: List[str] = None  # ['ko', 'en'] for Korean and English
    confidence_threshold: float = 0.1  # 0.5 → 0.1 (더 관대하게)
    enable_preprocessing: bool = False  # True → False (전처리 비활성화)
    max_image_size: int = 1536  # 2048 → 1536
    dpi: int = 200  # 300 → 200
    use_gpu: bool = False  # True → False
    
    def __post_init__(self):
        if self.fallback_engines is None:
            self.fallback_engines = [OCREngine.EASYOCR]  # Tesseract가 주 엔진이므로 EasyOCR만 fallback
        if self.languages is None:
            self.languages = ['en', 'ko']  # 영어 우선, 한국어 보조


class BaseOCREngine(ABC):
    """Abstract base class for OCR engines"""
    
    def __init__(self, config: OCRConfig):
        self.config = config
        self.is_initialized = False
    
    @abstractmethod
    async def initialize(self) -> bool:
        """Initialize the OCR engine"""
        pass
    
    @abstractmethod
    async def recognize_text(self, image: Image.Image, page_number: int) -> List[OCRResult]:
        """Recognize text from an image"""
        pass
    
    @abstractmethod
    def is_available(self) -> bool:
        """Check if the OCR engine is available"""
        pass


class PaddleOCREngine(BaseOCREngine):
    """PaddleOCR implementation - best for Korean text"""
    
    def __init__(self, config: OCRConfig):
        super().__init__(config)
        self.ocr_instance = None
    
    def is_available(self) -> bool:
        return PADDLEOCR_AVAILABLE
    
    async def initialize(self) -> bool:
        """Initialize PaddleOCR"""
        if not self.is_available():
            return False
            
        try:
            # Configure for Korean and English
            lang = 'korean' if 'ko' in self.config.languages else 'en'
            
            self.ocr_instance = PaddleOCR(
                use_angle_cls=True,  # Enable angle classification
                lang=lang,
                use_gpu=self.config.use_gpu,
                show_log=False
            )
            
            self.is_initialized = True
            logger.info("PaddleOCR initialized successfully")
            return True
            
        except Exception as e:
            logger.error(f"Failed to initialize PaddleOCR: {e}")
            return False
    
    async def recognize_text(self, image: Image.Image, page_number: int) -> List[OCRResult]:
        """Recognize text using PaddleOCR"""
        if not self.is_initialized:
            if not await self.initialize():
                return []
        
        try:
            # Convert PIL image to numpy array
            img_array = np.array(image)
            
            # Run OCR
            results = self.ocr_instance.ocr(img_array, cls=True)
            
            ocr_results = []
            if results and results[0]:
                for line in results[0]:
                    if len(line) >= 2:
                        bbox_points = line[0]  # 4 corner points
                        text_info = line[1]
                        
                        if isinstance(text_info, (list, tuple)) and len(text_info) >= 2:
                            text = text_info[0]
                            confidence = float(text_info[1])
                            
                            # Convert 4-point bbox to x0, y0, x1, y1 format
                            if len(bbox_points) >= 4:
                                xs = [p[0] for p in bbox_points]
                                ys = [p[1] for p in bbox_points]
                                bbox = [min(xs), min(ys), max(xs), max(ys)]
                                
                                if confidence >= self.config.confidence_threshold:
                                    ocr_results.append(OCRResult(
                                        text=text,
                                        confidence=confidence,
                                        bbox=bbox,
                                        page_number=page_number
                                    ))
            
            logger.info(f"PaddleOCR extracted {len(ocr_results)} text blocks from page {page_number}")
            return ocr_results
            
        except Exception as e:
            logger.error(f"PaddleOCR recognition failed: {e}")
            return []


class EasyOCREngine(BaseOCREngine):
    """EasyOCR implementation - good multilingual support"""
    
    def __init__(self, config: OCRConfig):
        super().__init__(config)
        self.reader = None
    
    def is_available(self) -> bool:
        return EASYOCR_AVAILABLE
    
    async def initialize(self) -> bool:
        """Initialize EasyOCR"""
        if not self.is_available():
            return False
            
        try:
            # Map language codes
            lang_map = {'ko': 'ko', 'en': 'en'}
            languages = [lang_map.get(lang, lang) for lang in self.config.languages]
            
            self.reader = easyocr.Reader(
                languages,
                gpu=self.config.use_gpu
            )
            
            self.is_initialized = True
            logger.info("EasyOCR initialized successfully")
            return True
            
        except Exception as e:
            logger.error(f"Failed to initialize EasyOCR: {e}")
            return False
    
    async def recognize_text(self, image: Image.Image, page_number: int) -> List[OCRResult]:
        """Recognize text using EasyOCR"""
        if not self.is_initialized:
            if not await self.initialize():
                return []
        
        try:
            # Convert PIL image to numpy array
            img_array = np.array(image)
            
            # Run OCR
            results = self.reader.readtext(img_array)
            
            ocr_results = []
            for result in results:
                if len(result) >= 3:
                    bbox_points = result[0]  # 4 corner points
                    text = result[1]
                    confidence = float(result[2])
                    
                    # Convert 4-point bbox to x0, y0, x1, y1 format
                    if len(bbox_points) >= 4:
                        xs = [p[0] for p in bbox_points]
                        ys = [p[1] for p in bbox_points]
                        bbox = [min(xs), min(ys), max(xs), max(ys)]
                        
                        if confidence >= self.config.confidence_threshold:
                            ocr_results.append(OCRResult(
                                text=text,
                                confidence=confidence,
                                bbox=bbox,
                                page_number=page_number
                            ))
            
            logger.info(f"EasyOCR extracted {len(ocr_results)} text blocks from page {page_number}")
            return ocr_results
            
        except Exception as e:
            logger.error(f"EasyOCR recognition failed: {e}")
            return []


class TesseractEngine(BaseOCREngine):
    """Tesseract OCR implementation - fallback option"""
    
    def __init__(self, config: OCRConfig):
        super().__init__(config)
    
    def is_available(self) -> bool:
        return TESSERACT_AVAILABLE
    
    async def initialize(self) -> bool:
        """Initialize Tesseract"""
        if not self.is_available():
            return False
            
        self.is_initialized = True
        logger.info("Tesseract initialized successfully")
        return True
    
    async def recognize_text(self, image: Image.Image, page_number: int) -> List[OCRResult]:
        """Recognize text using Tesseract"""
        if not self.is_initialized:
            if not await self.initialize():
                return []
        
        try:
            # Configure languages for Tesseract
            lang_str = '+'.join(['kor' if lang == 'ko' else 'eng' for lang in self.config.languages])
            
            # Get detailed OCR data
            ocr_data = pytesseract.image_to_data(
                image,
                lang=lang_str,
                output_type=pytesseract.Output.DICT,
                config='--psm 11'
            )
            
            ocr_results = []
            n_boxes = len(ocr_data['level'])
            
            for i in range(n_boxes):
                if ocr_data['level'][i] == 5 and ocr_data['text'][i].strip():  # Word level
                    confidence = float(ocr_data['conf'][i]) / 100.0  # Convert to 0-1 range
                    
                    if confidence >= self.config.confidence_threshold:
                        x, y, w, h = (ocr_data['left'][i], ocr_data['top'][i], 
                                     ocr_data['width'][i], ocr_data['height'][i])
                        
                        ocr_results.append(OCRResult(
                            text=ocr_data['text'][i],
                            confidence=confidence,
                            bbox=[x, y, x + w, y + h],
                            page_number=page_number
                        ))
            
            logger.info(f"Tesseract extracted {len(ocr_results)} text blocks from page {page_number}")
            return ocr_results
            
        except Exception as e:
            logger.error(f"Tesseract recognition failed: {e}")
            return []


class ImagePreprocessor:
    """Image preprocessing utilities to improve OCR accuracy"""
    
    @staticmethod
    def enhance_image(image: Image.Image, config: OCRConfig) -> Image.Image:
        """Apply image enhancements to improve OCR accuracy"""
        try:
            # Convert to RGB if necessary
            if image.mode != 'RGB':
                image = image.convert('RGB')
            
            # Resize if too large
            max_size = config.max_image_size
            if max(image.size) > max_size:
                image.thumbnail((max_size, max_size), Image.Resampling.LANCZOS)
            
            # Enhance contrast
            enhancer = ImageEnhance.Contrast(image)
            image = enhancer.enhance(1.2)
            
            # Enhance sharpness
            enhancer = ImageEnhance.Sharpness(image)
            image = enhancer.enhance(1.1)
            
            # Apply slight noise reduction
            image = image.filter(ImageFilter.MedianFilter(size=3))
            
            return image
            
        except Exception as e:
            logger.warning(f"Image preprocessing failed: {e}")
            return image
    
    @staticmethod
    def preprocess_with_opencv(image: Image.Image) -> Image.Image:
        """Advanced preprocessing using OpenCV"""
        if not OPENCV_AVAILABLE:
            logger.warning("OpenCV not available, skipping advanced preprocessing")
            return image
            
        try:
            # Convert PIL to OpenCV format
            cv_image = cv2.cvtColor(np.array(image), cv2.COLOR_RGB2BGR)
            
            # Convert to grayscale
            gray = cv2.cvtColor(cv_image, cv2.COLOR_BGR2GRAY)
            
            # Apply adaptive thresholding
            thresh = cv2.adaptiveThreshold(
                gray, 255, cv2.ADAPTIVE_THRESH_GAUSSIAN_C, cv2.THRESH_BINARY, 11, 2
            )
            
            # Noise removal
            kernel = np.ones((1, 1), np.uint8)
            opening = cv2.morphologyEx(thresh, cv2.MORPH_OPEN, kernel, iterations=1)
            
            # Convert back to PIL
            processed_image = Image.fromarray(opening).convert('RGB')
            return processed_image
            
        except Exception as e:
            logger.warning(f"OpenCV preprocessing failed: {e}")
            return image


class EnhancedOCRService:
    """Enhanced OCR service with multiple engine support and preprocessing"""
    
    def __init__(self, config: OCRConfig = None):
        self.config = config or OCRConfig()
        self.engines: Dict[OCREngine, BaseOCREngine] = {}
        self.preprocessor = ImagePreprocessor()
        self._initialize_engines()
    
    def _initialize_engines(self):
        """Initialize available OCR engines"""
        engine_classes = {
            OCREngine.PADDLEOCR: PaddleOCREngine,
            OCREngine.EASYOCR: EasyOCREngine,
            OCREngine.TESSERACT: TesseractEngine
        }
        
        for engine_type, engine_class in engine_classes.items():
            engine = engine_class(self.config)
            if engine.is_available():
                self.engines[engine_type] = engine
                logger.info(f"Registered OCR engine: {engine_type.value}")
    
    async def _get_working_engine(self) -> Optional[BaseOCREngine]:
        """Get the first working OCR engine"""
        # Try primary engine first
        if self.config.primary_engine in self.engines:
            engine = self.engines[self.config.primary_engine]
            if await engine.initialize():
                return engine
        
        # Try fallback engines
        for engine_type in self.config.fallback_engines:
            if engine_type in self.engines:
                engine = self.engines[engine_type]
                if await engine.initialize():
                    return engine
        
        return None
    
    async def process_pdf_stream(self, pdf_stream: bytes, document_id: str) -> Dict[str, Any]:
        """Process PDF with enhanced OCR"""
        try:
            logger.info(f"Starting enhanced OCR process for document: {document_id}")
            
            # Get working OCR engine
            engine = await self._get_working_engine()
            if not engine:
                return {"success": False, "error": "No OCR engines available"}
            
            # Check PyMuPDF availability
            if not PYMUPDF_AVAILABLE or fitz is None:
                return {"success": False, "error": "PyMuPDF not available"}
            
            # Open PDF (backward compatible)
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
                    return {"success": False, "error": f"Cannot open PDF: {e2}"}
            
            if pdf_document is None:
                return {"success": False, "error": "Failed to open PDF document"}
            total_pages = len(pdf_document)
            logger.info(f"PDF has {total_pages} pages, using {engine.__class__.__name__}")
            
            all_text_blocks = []
            
            for page_num in range(total_pages):
                page = pdf_document.load_page(page_num)
                
                # Render page to image with higher DPI for better quality
                pix = page.get_pixmap(dpi=self.config.dpi)
                img = Image.open(io.BytesIO(pix.tobytes()))
                
                # Apply preprocessing if enabled
                if self.config.enable_preprocessing:
                    img = self.preprocessor.enhance_image(img, self.config)
                
                # Perform OCR
                ocr_results = await engine.recognize_text(img, page_num + 1)
                
                # Convert OCR results to standard format
                for result in ocr_results:
                    all_text_blocks.append({
                        "text": result.text,
                        "page_number": result.page_number,
                        "x0": result.bbox[0],
                        "y0": result.bbox[1],
                        "x1": result.bbox[2],
                        "y1": result.bbox[3],
                        "confidence": result.confidence,
                        "block_type": "text"
                    })
            
            logger.info(f"Enhanced OCR extracted {len(all_text_blocks)} text blocks from document {document_id}")
            
            return {
                "success": True,
                "document_id": document_id,
                "total_pages": total_pages,
                "text_blocks": all_text_blocks,
                "engine_used": engine.__class__.__name__
            }
            
        except Exception as e:
            logger.error(f"Enhanced OCR processing failed for {document_id}: {e}")
            return {"success": False, "error": str(e)}