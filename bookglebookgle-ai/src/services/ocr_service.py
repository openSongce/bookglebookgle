
"""
OCR Service for processing PDF documents
Enhanced version with multiple OCR engines and preprocessing
"""
import asyncio
import io
from typing import List, Dict, Any

import fitz  # PyMuPDF
import pytesseract
from PIL import Image
from loguru import logger

# Import enhanced OCR service
from .enhanced_ocr_service import EnhancedOCRService, OCRConfig, OCREngine

# Tesseract-OCR 경로 설정 (사용자 환경에 맞게 수정 필요)
# 예: pytesseract.pytesseract.tesseract_cmd = r'C:\Program Files\Tesseract-OCR\tesseract.exe'

class OcrService:
    """Handles PDF parsing and OCR extraction with enhanced capabilities"""

    def __init__(self):
        # Use optimized default configuration from enhanced_ocr_service
        self.config = OCRConfig()  # 기본값 사용 (최적화된 설정)
        
        self.enhanced_ocr = EnhancedOCRService(self.config)
        logger.info(f"Enhanced OcrService initialized with optimized config: {self.config.primary_engine.value} primary engine")

    async def process_pdf_stream(self, pdf_stream: bytes, document_id: str) -> Dict[str, Any]:
        """
        이미지 PDF에서 텍스트 추출 - 모바일 앱용 단순화된 OCR 처리
        """
        try:
            logger.info(f"🔍 Starting OCR process for document: {document_id}")
            
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

            # 전체 텍스트를 하나의 문자열로 수집
            full_text = ""
            page_texts = []
            
            for page_num in range(total_pages):
                page = pdf_document.load_page(page_num)
                
                # 먼저 텍스트 레이어가 있는지 확인
                text_content = page.get_text()
                
                if len(text_content.strip()) > 50:  # 충분한 텍스트가 있으면
                    page_text = text_content
                    logger.info(f"📝 Page {page_num + 1}: Using text layer")
                else:
                    # 텍스트가 없으면 OCR 수행 (이미지 PDF)
                    pix = page.get_pixmap(dpi=300)
                    img = Image.open(io.BytesIO(pix.tobytes()))
                    
                    # 단순한 OCR 처리
                    page_text = pytesseract.image_to_string(
                        img, 
                        lang='kor+eng',
                        config='--psm 11'
                    )
                    logger.info(f"🔍 Page {page_num + 1}: OCR processed")
                
                page_texts.append({
                    "page_number": page_num + 1,
                    "text": page_text.strip()
                })
                full_text += page_text + "\n\n"

            # 전체 텍스트 정리
            full_text = full_text.strip()
            
            logger.info(f"✅ OCR completed for {document_id}: {len(full_text)} characters extracted")

            return {
                "success": True,
                "document_id": document_id,
                "total_pages": total_pages,
                "full_text": full_text,  # 전체 텍스트
                "page_texts": page_texts,  # 페이지별 텍스트
                "engine_used": "Tesseract"
            }

        except Exception as e:
            logger.error(f"OCR processing failed for {document_id}: {e}")
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

