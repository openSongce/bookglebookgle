"""
PDF OCR Service for BGBG AI Server
Handles PDF document processing, OCR, and text extraction with position data
"""

import asyncio
import json
import tempfile
import time
from pathlib import Path
from typing import Dict, List, Optional, Tuple, Any, Union
import hashlib

import pymupdf
from loguru import logger

from src.config.settings import get_settings


class PDFOCRService:
    """Service for PDF OCR processing and text extraction with position data"""
    
    def __init__(self):
        self.settings = get_settings()
        self.temp_dir = Path(tempfile.gettempdir()) / "bgbg_pdf_processing"
        self.temp_dir.mkdir(exist_ok=True)
        
    async def process_pdf_document(
        self,
        document_id: str,
        pdf_data: bytes,
        processing_options: Optional[Dict[str, Any]] = None,
        include_position_data: bool = True,
        language: str = "eng+kor"
    ) -> Dict[str, Any]:
        """Process PDF document with OCR and extract text with position data"""
        
        start_time = time.time()
        
        try:
            logger.info(f"Starting PDF processing for document: {document_id}")
            
            # Validate PDF data
            if not pdf_data or len(pdf_data) == 0:
                raise ValueError("Empty PDF data provided")
            
            # Parse processing options
            options = processing_options or {}
            ocr_enabled = options.get("enable_ocr", True)
            extract_images = options.get("extract_images", False)
            dpi = options.get("ocr_dpi", 300)
            
            # Create temporary PDF file
            temp_pdf_path = await self._save_temp_pdf(pdf_data, document_id)
            
            try:
                # Open PDF document
                doc = pymupdf.open(temp_pdf_path)
                
                # Process each page
                pages_data = []
                full_text_parts = []
                
                for page_num in range(len(doc)):
                    page = doc[page_num]
                    
                    # Process page with OCR if enabled
                    page_data = await self._process_page(
                        page, 
                        page_num + 1,
                        ocr_enabled=ocr_enabled,
                        language=language,
                        dpi=dpi,
                        include_position_data=include_position_data
                    )
                    
                    pages_data.append(page_data)
                    full_text_parts.append(page_data["text"])
                
                doc.close()
                
                # Combine full text
                full_text = "\\n\\n".join(full_text_parts)
                
                # Calculate processing metrics
                processing_time = time.time() - start_time
                avg_confidence = self._calculate_average_confidence(pages_data)
                
                # Build result
                result = {
                    "success": True,
                    "document_id": document_id,
                    "pages": pages_data,
                    "full_text": full_text,
                    "page_count": len(pages_data),
                    "processing_metrics": {
                        "processing_time_seconds": processing_time,
                        "average_confidence": avg_confidence,
                        "total_blocks": sum(len(p["text_blocks"]) for p in pages_data),
                        "ocr_enabled": ocr_enabled
                    }
                }
                
                logger.info(f"PDF processing completed for {document_id}: "
                           f"{len(pages_data)} pages, {processing_time:.2f}s")
                
                return result
                
            finally:
                # Cleanup temporary file
                await self._cleanup_temp_file(temp_pdf_path)
                
        except Exception as e:
            processing_time = time.time() - start_time
            logger.error(f"PDF processing failed for {document_id}: {e}")
            
            return {
                "success": False,
                "error": str(e),
                "document_id": document_id,
                "processing_metrics": {
                    "processing_time_seconds": processing_time,
                    "error_occurred": True
                }
            }
    
    async def _process_page(
        self,
        page: pymupdf.Page,
        page_number: int,
        ocr_enabled: bool = True,
        language: str = "eng+kor",
        dpi: int = 300,
        include_position_data: bool = True
    ) -> Dict[str, Any]:
        """Process individual PDF page"""
        
        try:
            text_blocks = []
            page_text_parts = []
            
            # Get page dimensions
            page_rect = page.rect
            page_width = page_rect.width
            page_height = page_rect.height
            
            if ocr_enabled:
                # Use OCR for text extraction
                textpage = page.get_textpage_ocr(
                    language=language,
                    dpi=dpi,
                    full=True
                )
                
                # Extract text with structure
                text_dict = page.get_text("dict", textpage=textpage)
                
            else:
                # Regular text extraction without OCR
                text_dict = page.get_text("dict")
            
            # Process text blocks
            block_index = 0
            for block in text_dict.get("blocks", []):
                if block.get("type") == 0:  # Text block
                    block_data = await self._process_text_block(
                        block, 
                        page_number, 
                        block_index,
                        include_position_data
                    )
                    
                    if block_data["text"].strip():  # Only add non-empty blocks
                        text_blocks.append(block_data)
                        page_text_parts.append(block_data["text"])
                        block_index += 1
            
            # Combine page text
            page_text = "\\n".join(page_text_parts)
            
            return {
                "page_number": page_number,
                "text": page_text,
                "text_blocks": text_blocks,
                "page_width": page_width,
                "page_height": page_height,
                "metadata": {
                    "ocr_used": ocr_enabled,
                    "total_blocks": len(text_blocks)
                }
            }
            
        except Exception as e:
            logger.error(f"Page {page_number} processing failed: {e}")
            
            return {
                "page_number": page_number,
                "text": "",
                "text_blocks": [],
                "page_width": 0,
                "page_height": 0,
                "metadata": {
                    "error": str(e),
                    "ocr_used": ocr_enabled
                }
            }
    
    async def _process_text_block(
        self,
        block: Dict[str, Any],
        page_number: int,
        block_index: int,
        include_position_data: bool = True
    ) -> Dict[str, Any]:
        """Process individual text block with position data"""
        
        # Extract text from lines and spans
        text_parts = []
        block_bbox = block.get("bbox", [0, 0, 0, 0])
        
        # Collect font information
        font_info = {"fonts": set(), "sizes": set()}
        
        for line in block.get("lines", []):
            line_text = ""
            for span in line.get("spans", []):
                span_text = span.get("text", "")
                line_text += span_text
                
                # Collect font information
                if span.get("font"):
                    font_info["fonts"].add(span["font"])
                if span.get("size"):
                    font_info["sizes"].add(span["size"])
            
            if line_text.strip():
                text_parts.append(line_text.strip())
        
        # Combine text
        combined_text = " ".join(text_parts)
        
        # Build text block data
        text_block_data = {
            "text": combined_text,
            "page_number": page_number,
            "block_index": block_index,
            "block_type": "text"
        }
        
        # Add position data if requested
        if include_position_data:
            text_block_data.update({
                "bbox": {
                    "x0": block_bbox[0],
                    "y0": block_bbox[1],
                    "x1": block_bbox[2],
                    "y1": block_bbox[3]
                },
                "font_info": {
                    "fonts": list(font_info["fonts"]),
                    "sizes": list(font_info["sizes"])
                }
            })
        
        # Calculate confidence (simplified - in real scenario, this would come from OCR engine)
        confidence = 0.95 if len(combined_text) > 10 else 0.8
        text_block_data["confidence"] = confidence
        
        return text_block_data
    
    async def _save_temp_pdf(self, pdf_data: bytes, document_id: str) -> str:
        """Save PDF data to temporary file"""
        
        # Create unique filename
        file_hash = hashlib.md5(pdf_data).hexdigest()[:8]
        temp_filename = f"{document_id}_{file_hash}.pdf"
        temp_path = self.temp_dir / temp_filename
        
        # Write PDF data
        with open(temp_path, "wb") as f:
            f.write(pdf_data)
        
        logger.debug(f"Saved temporary PDF: {temp_path}")
        return str(temp_path)
    
    async def _cleanup_temp_file(self, file_path: str):
        """Clean up temporary PDF file"""
        try:
            Path(file_path).unlink()
            logger.debug(f"Cleaned up temporary PDF: {file_path}")
        except Exception as e:
            logger.warning(f"Failed to cleanup temp file {file_path}: {e}")
    
    def _calculate_average_confidence(self, pages_data: List[Dict[str, Any]]) -> float:
        """Calculate average confidence score across all text blocks"""
        
        total_confidence = 0
        total_blocks = 0
        
        for page in pages_data:
            for block in page.get("text_blocks", []):
                confidence = block.get("confidence", 0)
                total_confidence += confidence
                total_blocks += 1
        
        return total_confidence / total_blocks if total_blocks > 0 else 0.0
    
    async def extract_text_without_ocr(self, pdf_data: bytes) -> str:
        """Quick text extraction without OCR for preview purposes"""
        
        try:
            temp_path = await self._save_temp_pdf(pdf_data, "preview")
            
            try:
                doc = pymupdf.open(temp_path)
                text_parts = []
                
                for page in doc:
                    page_text = page.get_text()
                    if page_text.strip():
                        text_parts.append(page_text)
                
                doc.close()
                return "\\n\\n".join(text_parts)
                
            finally:
                await self._cleanup_temp_file(temp_path)
                
        except Exception as e:
            logger.error(f"Quick text extraction failed: {e}")
            return ""
    
    async def validate_pdf(self, pdf_data: bytes) -> Dict[str, Any]:
        """Validate PDF file and return basic information"""
        
        try:
            temp_path = await self._save_temp_pdf(pdf_data, "validation")
            
            try:
                doc = pymupdf.open(temp_path)
                
                validation_result = {
                    "valid": True,
                    "page_count": len(doc),
                    "has_text": False,
                    "file_size": len(pdf_data),
                    "needs_ocr": True
                }
                
                # Check if document has extractable text
                for page in doc:
                    text = page.get_text()
                    if text and text.strip():
                        validation_result["has_text"] = True
                        validation_result["needs_ocr"] = False
                        break
                
                doc.close()
                return validation_result
                
            finally:
                await self._cleanup_temp_file(temp_path)
                
        except Exception as e:
            logger.error(f"PDF validation failed: {e}")
            return {
                "valid": False,
                "error": str(e),
                "file_size": len(pdf_data)
            }
    
    async def cleanup(self):
        """Cleanup service resources"""
        try:
            # Clean up any remaining temporary files
            if self.temp_dir.exists():
                for temp_file in self.temp_dir.glob("*.pdf"):
                    try:
                        temp_file.unlink()
                    except Exception as e:
                        logger.warning(f"Failed to cleanup {temp_file}: {e}")
            
            logger.info("PDF OCR Service cleanup completed")
            
        except Exception as e:
            logger.error(f"PDF OCR Service cleanup failed: {e}")