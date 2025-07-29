"""
Document Organizer Service for BGBG AI Server
Handles organization and formatting of processed PDF documents
"""

import asyncio
import json
from datetime import datetime
from pathlib import Path
from typing import Dict, List, Optional, Any, Union
import xml.etree.ElementTree as ET

from loguru import logger

from src.config.settings import get_settings


class DocumentOrganizer:
    """Service for organizing and formatting processed documents"""
    
    def __init__(self):
        self.settings = get_settings()
        self.output_dir = Path("./data/organized_documents")
        self.output_dir.mkdir(parents=True, exist_ok=True)
        
    async def organize_pdf_document(
        self,
        document_id: str,
        processed_document: Dict[str, Any],
        output_format: str = "json",
        include_metadata: bool = True
    ) -> str:
        """Organize processed PDF document into structured format"""
        
        try:
            logger.info(f"Organizing document {document_id} in {output_format} format")
            
            # Prepare organized document data
            organized_data = await self._prepare_document_data(
                document_id,
                processed_document,
                include_metadata
            )
            
            # Generate output based on format
            if output_format.lower() == "json":
                output_path = await self._save_as_json(document_id, organized_data)
            elif output_format.lower() == "markdown":
                output_path = await self._save_as_markdown(document_id, organized_data)
            elif output_format.lower() == "xml":
                output_path = await self._save_as_xml(document_id, organized_data)
            elif output_format.lower() == "txt":
                output_path = await self._save_as_text(document_id, organized_data)
            else:
                raise ValueError(f"Unsupported output format: {output_format}")
            
            logger.info(f"Document organized and saved: {output_path}")
            return str(output_path)
            
        except Exception as e:
            logger.error(f"Document organization failed for {document_id}: {e}")
            raise
    
    async def _prepare_document_data(
        self,
        document_id: str,
        processed_document: Dict[str, Any],
        include_metadata: bool = True
    ) -> Dict[str, Any]:
        """Prepare structured document data"""
        
        # Extract basic information
        pages_data = processed_document.get("pages", [])\n        full_text = processed_document.get("full_text", "")
        processing_metrics = processed_document.get("processing_metrics", {})
        
        # Build organized structure
        organized_data = {
            "document_info": {
                "document_id": document_id,
                "title": self._extract_title(pages_data),
                "page_count": len(pages_data),
                "total_text_blocks": sum(len(p.get("text_blocks", [])) for p in pages_data),
                "processing_date": datetime.now().isoformat(),
                "full_text_length": len(full_text)
            },
            "content": {
                "full_text": full_text,
                "pages": []
            }
        }
        
        # Add metadata if requested
        if include_metadata:
            organized_data["metadata"] = {
                "processing_metrics": processing_metrics,
                "extraction_method": "pdf_ocr",
                "language_detected": self._detect_language(full_text),
                "document_statistics": await self._calculate_document_statistics(pages_data)
            }
        
        # Process each page
        for page_data in pages_data:
            page_info = await self._organize_page_data(page_data, include_metadata)
            organized_data["content"]["pages"].append(page_info)
        
        return organized_data
    
    async def _organize_page_data(
        self,
        page_data: Dict[str, Any],
        include_metadata: bool = True
    ) -> Dict[str, Any]:
        """Organize individual page data"""
        
        page_info = {
            "page_number": page_data.get("page_number", 1),
            "text": page_data.get("text", ""),
            "text_blocks": []
        }
        
        # Add metadata if requested
        if include_metadata:
            page_info["metadata"] = {
                "page_dimensions": {
                    "width": page_data.get("page_width", 0),
                    "height": page_data.get("page_height", 0)
                },
                "block_count": len(page_data.get("text_blocks", [])),
                "ocr_used": page_data.get("metadata", {}).get("ocr_used", False)
            }
        
        # Process text blocks
        for block in page_data.get("text_blocks", []):
            block_info = {
                "block_index": block.get("block_index", 0),
                "text": block.get("text", ""),
                "confidence": block.get("confidence", 0.0)
            }
            
            # Add position data if available and metadata is requested
            if include_metadata and "bbox" in block:
                bbox = block["bbox"]
                block_info["position"] = {
                    "x": bbox.get("x0", 0),
                    "y": bbox.get("y0", 0),
                    "width": bbox.get("x1", 0) - bbox.get("x0", 0),
                    "height": bbox.get("y1", 0) - bbox.get("y0", 0),
                    "coordinates": {
                        "top_left": [bbox.get("x0", 0), bbox.get("y0", 0)],
                        "bottom_right": [bbox.get("x1", 0), bbox.get("y1", 0)]
                    }
                }
            
            # Add font information if available and metadata is requested
            if include_metadata and "font_info" in block:
                font_info = block["font_info"]
                block_info["formatting"] = {
                    "fonts": font_info.get("fonts", []),
                    "sizes": font_info.get("sizes", [])
                }
            
            page_info["text_blocks"].append(block_info)
        
        return page_info
    
    async def _save_as_json(
        self,
        document_id: str,
        organized_data: Dict[str, Any]
    ) -> Path:
        """Save document as JSON format"""
        
        filename = f"{document_id}_organized.json"
        output_path = self.output_dir / filename
        
        with open(output_path, "w", encoding="utf-8") as f:
            json.dump(organized_data, f, ensure_ascii=False, indent=2)
        
        return output_path
    
    async def _save_as_markdown(
        self,
        document_id: str,
        organized_data: Dict[str, Any]
    ) -> Path:
        """Save document as Markdown format"""
        
        filename = f"{document_id}_organized.md"
        output_path = self.output_dir / filename
        
        # Build markdown content
        markdown_content = []
        
        # Document header
        doc_info = organized_data["document_info"]
        markdown_content.extend([
            f"# {doc_info.get('title', document_id)}",
            "",
            f"**Document ID:** {document_id}",
            f"**Pages:** {doc_info.get('page_count', 0)}",
            f"**Processing Date:** {doc_info.get('processing_date', '')}",
            ""
        ])
        
        # Add metadata if available
        if "metadata" in organized_data:
            metadata = organized_data["metadata"]
            markdown_content.extend([
                "## Document Metadata",
                "",
                f"- **Language:** {metadata.get('language_detected', 'Unknown')}",
                f"- **Total Blocks:** {doc_info.get('total_text_blocks', 0)}",
                f"- **Text Length:** {doc_info.get('full_text_length', 0)} characters",
                ""
            ])
            
            # Processing metrics
            if "processing_metrics" in metadata:
                metrics = metadata["processing_metrics"]
                markdown_content.extend([
                    "### Processing Metrics",
                    "",
                    f"- **Processing Time:** {metrics.get('processing_time_seconds', 0):.2f} seconds",
                    f"- **Average Confidence:** {metrics.get('average_confidence', 0):.2f}",
                    f"- **OCR Enabled:** {metrics.get('ocr_enabled', False)}",
                    ""
                ])
        
        # Table of contents
        markdown_content.extend([
            "## Table of Contents",
            ""
        ])
        
        for page in organized_data["content"]["pages"]:
            page_num = page.get("page_number", 1)
            markdown_content.append(f"- [Page {page_num}](#page-{page_num})")
        
        markdown_content.append("")
        
        # Content by pages
        markdown_content.append("## Document Content")
        markdown_content.append("")
        
        for page in organized_data["content"]["pages"]:
            page_num = page.get("page_number", 1)
            page_text = page.get("text", "")
            
            markdown_content.extend([
                f"### Page {page_num}",
                "",
                page_text,
                "",
                "---",
                ""
            ])
        
        # Full text section
        markdown_content.extend([
            "## Full Document Text",
            "",
            organized_data["content"]["full_text"]
        ])
        
        # Write to file
        with open(output_path, "w", encoding="utf-8") as f:
            f.write("\\n".join(markdown_content))
        
        return output_path
    
    async def _save_as_xml(
        self,
        document_id: str,
        organized_data: Dict[str, Any]
    ) -> Path:
        """Save document as XML format"""
        
        filename = f"{document_id}_organized.xml"
        output_path = self.output_dir / filename
        
        # Create XML structure
        root = ET.Element("document")
        root.set("id", document_id)
        
        # Document info
        doc_info = organized_data["document_info"]
        info_elem = ET.SubElement(root, "document_info")
        
        for key, value in doc_info.items():
            if key != "document_id":  # Already in root attribute
                elem = ET.SubElement(info_elem, key)
                elem.text = str(value)
        
        # Metadata
        if "metadata" in organized_data:
            metadata_elem = ET.SubElement(root, "metadata")
            metadata = organized_data["metadata"]
            
            for key, value in metadata.items():
                if isinstance(value, dict):
                    sub_elem = ET.SubElement(metadata_elem, key)
                    for sub_key, sub_value in value.items():
                        sub_sub_elem = ET.SubElement(sub_elem, sub_key)
                        sub_sub_elem.text = str(sub_value)
                else:
                    elem = ET.SubElement(metadata_elem, key)
                    elem.text = str(value)
        
        # Content
        content_elem = ET.SubElement(root, "content")
        
        # Full text
        full_text_elem = ET.SubElement(content_elem, "full_text")
        full_text_elem.text = organized_data["content"]["full_text"]
        
        # Pages
        pages_elem = ET.SubElement(content_elem, "pages")
        
        for page_data in organized_data["content"]["pages"]:
            page_elem = ET.SubElement(pages_elem, "page")
            page_elem.set("number", str(page_data.get("page_number", 1)))
            
            # Page text
            page_text_elem = ET.SubElement(page_elem, "text")
            page_text_elem.text = page_data.get("text", "")
            
            # Text blocks
            blocks_elem = ET.SubElement(page_elem, "text_blocks")
            
            for block in page_data.get("text_blocks", []):
                block_elem = ET.SubElement(blocks_elem, "block")
                block_elem.set("index", str(block.get("block_index", 0)))
                block_elem.set("confidence", str(block.get("confidence", 0.0)))
                
                block_text_elem = ET.SubElement(block_elem, "text")
                block_text_elem.text = block.get("text", "")
                
                # Position data
                if "position" in block:
                    pos_elem = ET.SubElement(block_elem, "position")
                    position = block["position"]
                    
                    pos_elem.set("x", str(position.get("x", 0)))
                    pos_elem.set("y", str(position.get("y", 0)))
                    pos_elem.set("width", str(position.get("width", 0)))
                    pos_elem.set("height", str(position.get("height", 0)))
        
        # Write XML to file
        tree = ET.ElementTree(root)
        ET.indent(tree, space="  ")
        tree.write(output_path, encoding="utf-8", xml_declaration=True)
        
        return output_path
    
    async def _save_as_text(
        self,
        document_id: str,
        organized_data: Dict[str, Any]
    ) -> Path:
        """Save document as plain text format"""
        
        filename = f"{document_id}_organized.txt"
        output_path = self.output_dir / filename
        
        # Build text content
        text_content = []
        
        # Document header
        doc_info = organized_data["document_info"]
        text_content.extend([
            f"Document: {doc_info.get('title', document_id)}",
            f"ID: {document_id}",
            f"Pages: {doc_info.get('page_count', 0)}",
            f"Processing Date: {doc_info.get('processing_date', '')}",
            "=" * 80,
            ""
        ])
        
        # Content by pages
        for page in organized_data["content"]["pages"]:
            page_num = page.get("page_number", 1)
            page_text = page.get("text", "")
            
            text_content.extend([
                f"Page {page_num}:",
                "-" * 40,
                page_text,
                "",
                "=" * 80,
                ""
            ])
        
        # Write to file
        with open(output_path, "w", encoding="utf-8") as f:
            f.write("\\n".join(text_content))
        
        return output_path
    
    def _extract_title(self, pages_data: List[Dict[str, Any]]) -> str:
        """Extract document title from first page content"""
        
        if not pages_data:
            return "Untitled Document"
        
        first_page = pages_data[0]
        text_blocks = first_page.get("text_blocks", [])
        
        if not text_blocks:
            return "Untitled Document"
        
        # Use first text block as title, truncated
        first_text = text_blocks[0].get("text", "").strip()
        
        if not first_text:
            return "Untitled Document"
        
        # Truncate and clean title
        title = first_text.split("\\n")[0]  # First line only
        title = title[:100]  # Max 100 characters
        
        return title if title else "Untitled Document"
    
    def _detect_language(self, text: str) -> str:
        """Simple language detection based on character patterns"""
        
        if not text:
            return "unknown"
        
        # Count Korean characters
        korean_chars = sum(1 for char in text if '가' <= char <= '힣')
        
        # Count English characters
        english_chars = sum(1 for char in text if char.isascii() and char.isalpha())
        
        total_chars = korean_chars + english_chars
        
        if total_chars == 0:
            return "unknown"
        
        korean_ratio = korean_chars / total_chars
        
        if korean_ratio > 0.3:
            return "korean"
        elif english_chars > korean_chars:
            return "english"
        else:
            return "mixed"
    
    async def _calculate_document_statistics(
        self,
        pages_data: List[Dict[str, Any]]
    ) -> Dict[str, Any]:
        """Calculate document statistics"""
        
        stats = {
            "total_pages": len(pages_data),
            "total_blocks": 0,
            "total_characters": 0,
            "total_words": 0,
            "average_confidence": 0.0,
            "blocks_per_page": 0.0,
            "page_dimensions": {
                "average_width": 0.0,
                "average_height": 0.0
            }
        }
        
        if not pages_data:
            return stats
        
        total_confidence = 0
        total_blocks = 0
        total_width = 0
        total_height = 0
        
        for page in pages_data:
            page_blocks = page.get("text_blocks", [])
            stats["total_blocks"] += len(page_blocks)
            
            page_text = page.get("text", "")
            stats["total_characters"] += len(page_text)
            stats["total_words"] += len(page_text.split())
            
            # Collect confidence scores
            for block in page_blocks:
                confidence = block.get("confidence", 0.0)
                total_confidence += confidence
                total_blocks += 1
            
            # Collect page dimensions
            total_width += page.get("page_width", 0)
            total_height += page.get("page_height", 0)
        
        # Calculate averages
        if total_blocks > 0:
            stats["average_confidence"] = total_confidence / total_blocks
            stats["blocks_per_page"] = total_blocks / len(pages_data)
        
        if len(pages_data) > 0:
            stats["page_dimensions"]["average_width"] = total_width / len(pages_data)
            stats["page_dimensions"]["average_height"] = total_height / len(pages_data)
        
        return stats
    
    async def list_organized_documents(self) -> List[Dict[str, Any]]:
        """List all organized documents"""
        
        try:
            documents = []
            
            for file_path in self.output_dir.glob("*_organized.*"):
                file_stat = file_path.stat()
                
                # Extract document ID from filename
                document_id = file_path.stem.replace("_organized", "")
                
                document_info = {
                    "document_id": document_id,
                    "filename": file_path.name,
                    "file_path": str(file_path),
                    "format": file_path.suffix.lower().replace(".", ""),
                    "size_bytes": file_stat.st_size,
                    "modified_date": datetime.fromtimestamp(file_stat.st_mtime).isoformat()
                }
                
                documents.append(document_info)
            
            # Sort by modification date (newest first)
            documents.sort(key=lambda x: x["modified_date"], reverse=True)
            
            return documents
            
        except Exception as e:
            logger.error(f"Failed to list organized documents: {e}")
            return []
    
    async def cleanup_old_documents(self, max_age_days: int = 30):
        """Cleanup old organized documents"""
        
        try:
            cutoff_time = datetime.now().timestamp() - (max_age_days * 24 * 3600)
            cleaned_count = 0
            
            for file_path in self.output_dir.glob("*_organized.*"):
                file_stat = file_path.stat()
                
                if file_stat.st_mtime < cutoff_time:
                    try:
                        file_path.unlink()
                        cleaned_count += 1
                        logger.debug(f"Cleaned up old document: {file_path.name}")
                    except Exception as e:
                        logger.warning(f"Failed to cleanup {file_path.name}: {e}")
            
            if cleaned_count > 0:
                logger.info(f"Cleaned up {cleaned_count} old organized documents")
            
        except Exception as e:
            logger.error(f"Document cleanup failed: {e}")
    
    async def cleanup(self):
        """Cleanup service resources"""
        try:
            # Cleanup old documents
            await self.cleanup_old_documents()
            
            logger.info("Document Organizer cleanup completed")
            
        except Exception as e:
            logger.error(f"Document Organizer cleanup failed: {e}")