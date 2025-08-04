"""
단순화된 OCR 서비스
PaddleOCR 전용 OCR 서비스 - LLM 후처리 없이 고성능 텍스트 추출
기존의 복잡한 다중 엔진 구조를 대체하는 최적화된 서비스
"""

import asyncio
import time
import traceback
from typing import Dict, Any, List, Optional, Tuple
import psutil
import os

import fitz  # PyMuPDF
from loguru import logger

from src.models.ocr_models import (
    OCRBlock, ProcessedOCRBlock, ProcessingMetrics
)
from src.services.paddleocr_engine import PaddleOCREngine, PaddleOCRConfig
from src.services.vector_db import VectorDBManager


class SimplifiedOCRService:
    """
    단순화된 OCR 서비스 - Tesseract + LLM 후처리 통합
    기존 복잡한 다중 엔진 구조를 대체하는 최적화된 서비스
    """
    
    def __init__(
        self,
        tesseract_config: Optional[TesseractConfig] = None,
        enable_llm_postprocessing: bool = True
    ):
        """
        SimplifiedOCRService 초기화
        
        Args:
            tesseract_config: Tesseract 설정 (None이면 기본 설정)
            enable_llm_postprocessing: LLM 후처리 활성화 여부
        """
        # 설정 초기화
        self.tesseract_config = tesseract_config or TesseractConfig()
        self.enable_llm_postprocessing = enable_llm_postprocessing
        
        # 엔진 초기화
        self.tesseract_engine = TesseractEngine(self.tesseract_config)
        self.llm_client = None
        self.text_post_processor = None
        
        # 성능 모니터링
        self.processing_stats = {
            'total_documents': 0,
            'successful_documents': 0,
            'failed_documents': 0,
            'total_processing_time': 0.0,
            'total_ocr_time': 0.0,
            'total_llm_time': 0.0,
            'total_pages_processed': 0,
            'total_blocks_extracted': 0,
            'total_corrections_made': 0
        }
        
        logger.info(f"🚀 SimplifiedOCRService initialized:")
        logger.info(f"   🔧 Tesseract config: {self.tesseract_config.languages}")
        logger.info(f"   🤖 LLM post-processing: {'Enabled' if enable_llm_postprocessing else 'Disabled'}")
        logger.info(f"   📊 DPI: {self.tesseract_config.dpi}")
        logger.info(f"   🎯 Confidence threshold: {self.tesseract_config.confidence_threshold}")
    
    async def initialize(self) -> bool:
        """
        서비스 초기화 (비동기 컴포넌트 초기화)
        
        Returns:
            초기화 성공 여부
        """
        try:
            logger.info("🔄 Initializing SimplifiedOCRService components...")
            
            # 1. Tesseract 엔진 초기화
            tesseract_success = await self.tesseract_engine.initialize()
            if not tesseract_success:
                logger.error("❌ Tesseract engine initialization failed")
                return False
            
            # 2. LLM 후처리 초기화 (활성화된 경우)
            if self.enable_llm_postprocessing:
                try:
                    self.llm_client = LLMClient()
                    await self.llm_client.initialize()
                    
                    self.text_post_processor = TextPostProcessor(self.llm_client)
                    
                    logger.info("✅ LLM post-processor initialized")
                except Exception as e:
                    logger.warning(f"⚠️ LLM post-processor initialization failed: {e}")
                    logger.info("🔄 Continuing with OCR-only mode")
                    self.enable_llm_postprocessing = False
            
            logger.info("✅ SimplifiedOCRService initialization completed")
            return True
            
        except Exception as e:
            logger.error(f"❌ SimplifiedOCRService initialization failed: {e}")
            logger.error(f"Traceback: {traceback.format_exc()}")
            return False
    
    async def process_pdf_stream(
        self, 
        pdf_stream: bytes, 
        document_id: str,
        enable_llm_postprocessing: Optional[bool] = None
    ) -> Dict[str, Any]:
        """
        PDF 스트림을 처리하여 OCR + LLM 후처리 수행
        
        Args:
            pdf_stream: PDF 바이트 스트림
            document_id: 문서 ID
            enable_llm_postprocessing: LLM 후처리 활성화 여부 (None이면 기본 설정 사용)
            
        Returns:
            처리 결과 딕셔너리
        """
        # 처리 시작
        start_time = time.time()
        process_memory_start = self._get_memory_usage()
        
        # LLM 후처리 설정 결정
        use_llm = (enable_llm_postprocessing 
                  if enable_llm_postprocessing is not None 
                  else self.enable_llm_postprocessing)
        
        logger.info(f"🚀 Starting PDF processing for document: {document_id}")
        logger.info(f"   📄 PDF size: {len(pdf_stream):,} bytes")
        logger.info(f"   🤖 LLM post-processing: {'Enabled' if use_llm else 'Disabled'}")
        
        try:
            # 1단계: PDF 파싱 및 OCR 처리
            ocr_result = await self._extract_with_tesseract(pdf_stream, document_id)
            
            if not ocr_result["success"]:
                return self._create_error_response(
                    document_id, 
                    ocr_result["error"], 
                    start_time
                )
            
            ocr_blocks = ocr_result["ocr_blocks"]
            ocr_time = ocr_result["processing_time"]
            
            # 2단계: LLM 후처리 (활성화된 경우)
            if use_llm and self.text_post_processor and ocr_blocks:
                llm_result = await self._postprocess_with_llm(ocr_blocks, document_id)
                processed_blocks = llm_result["processed_blocks"]
                llm_time = llm_result["processing_time"]
            else:
                # LLM 후처리 없이 OCRBlock을 ProcessedOCRBlock으로 변환
                processed_blocks = self._convert_to_processed_blocks(ocr_blocks)
                llm_time = 0.0
            
            # 3단계: 결과 통합 및 형식 변환
            final_result = await self._create_final_response(
                document_id=document_id,
                processed_blocks=processed_blocks,
                ocr_time=ocr_time,
                llm_time=llm_time,
                start_time=start_time,
                process_memory_start=process_memory_start,
                pdf_size=len(pdf_stream)
            )
            
            # 통계 업데이트
            self._update_processing_stats(final_result, success=True)
            
            return final_result
            
        except Exception as e:
            logger.error(f"❌ PDF processing failed for {document_id}: {e}")
            logger.error(f"Traceback: {traceback.format_exc()}")
            
            error_result = self._create_error_response(document_id, str(e), start_time)
            self._update_processing_stats(error_result, success=False)
            
            return error_result
    
    async def _extract_with_tesseract(
        self, 
        pdf_stream: bytes, 
        document_id: str
    ) -> Dict[str, Any]:
        """
        Tesseract를 사용한 텍스트 추출
        
        Args:
            pdf_stream: PDF 바이트 스트림
            document_id: 문서 ID
            
        Returns:
            OCR 처리 결과
        """
        try:
            ocr_start_time = time.time()
            logger.info(f"🔍 Starting Tesseract OCR for document: {document_id}")
            
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
            
            # Tesseract 엔진으로 페이지 처리
            ocr_blocks = await self.tesseract_engine.process_pdf_pages(pdf_document)
            
            # PDF 문서 정리
            pdf_document.close()
            
            ocr_time = time.time() - ocr_start_time
            
            logger.info(f"✅ Tesseract OCR completed for {document_id}:")
            logger.info(f"   ⏱️ OCR time: {ocr_time:.2f}s")
            logger.info(f"   📊 Blocks extracted: {len(ocr_blocks)}")
            logger.info(f"   📝 Total characters: {sum(len(block.text) for block in ocr_blocks)}")
            
            return {
                "success": True,
                "ocr_blocks": ocr_blocks,
                "total_pages": total_pages,
                "processing_time": ocr_time
            }
            
        except Exception as e:
            logger.error(f"❌ Tesseract OCR failed for {document_id}: {e}")
            return {"success": False, "error": str(e)}
    
    async def _postprocess_with_llm(
        self, 
        ocr_blocks: List[OCRBlock], 
        document_id: str
    ) -> Dict[str, Any]:
        """
        LLM을 사용한 텍스트 후처리
        
        Args:
            ocr_blocks: OCR 블록 리스트
            document_id: 문서 ID
            
        Returns:
            LLM 후처리 결과
        """
        try:
            llm_start_time = time.time()
            logger.info(f"🤖 Starting LLM post-processing for document: {document_id}")
            logger.info(f"   📊 Blocks to process: {len(ocr_blocks)}")
            
            # TextPostProcessor로 후처리 수행
            processed_blocks = await self.text_post_processor.process_text_blocks(
                ocr_blocks, 
                enable_batch_processing=True
            )
            
            llm_time = time.time() - llm_start_time
            
            # 후처리 통계
            total_corrections = sum(len(block.corrections) for block in processed_blocks)
            avg_confidence = (sum(block.processing_confidence for block in processed_blocks) 
                            / len(processed_blocks) if processed_blocks else 0.0)
            
            logger.info(f"✅ LLM post-processing completed for {document_id}:")
            logger.info(f"   ⏱️ LLM time: {llm_time:.2f}s")
            logger.info(f"   🔧 Total corrections: {total_corrections}")
            logger.info(f"   🎯 Average confidence: {avg_confidence:.3f}")
            
            return {
                "success": True,
                "processed_blocks": processed_blocks,
                "processing_time": llm_time,
                "total_corrections": total_corrections,
                "average_confidence": avg_confidence
            }
            
        except Exception as e:
            logger.error(f"❌ LLM post-processing failed for {document_id}: {e}")
            # 실패 시 원본 블록들을 ProcessedOCRBlock으로 변환
            processed_blocks = self._convert_to_processed_blocks(ocr_blocks)
            return {
                "success": False,
                "processed_blocks": processed_blocks,
                "processing_time": 0.0,
                "error": str(e)
            }
    
    def _convert_to_processed_blocks(self, ocr_blocks: List[OCRBlock]) -> List[ProcessedOCRBlock]:
        """OCRBlock을 ProcessedOCRBlock으로 변환 (LLM 후처리 없이)"""
        processed_blocks = []
        
        for block in ocr_blocks:
            processed_block = ProcessedOCRBlock(
                text=block.text,
                page_number=block.page_number,
                bbox=block.bbox,
                confidence=block.confidence,
                block_type=block.block_type,
                original_text=block.text,
                corrections=[],  # 교정 없음
                processing_confidence=1.0,  # LLM 처리 없으므로 최대값
                llm_model="none (OCR only)"
            )
            processed_blocks.append(processed_block)
        
        return processed_blocks
    
    async def _create_final_response(
        self,
        document_id: str,
        processed_blocks: List[ProcessedOCRBlock],
        ocr_time: float,
        llm_time: float,
        start_time: float,
        process_memory_start: int,
        pdf_size: int
    ) -> Dict[str, Any]:
        """최종 응답 생성"""
        total_time = time.time() - start_time
        process_memory_peak = self._get_memory_usage()
        memory_used = process_memory_peak - process_memory_start
        
        # 페이지별 텍스트 구성 (기존 API 호환성)
        page_texts = self._create_page_texts(processed_blocks)
        
        # 전체 텍스트 구성
        full_text = "\n\n".join([
            block.text for block in processed_blocks 
            if block.text.strip()
        ])
        
        # 텍스트 블록 형태로 변환 (기존 API 호환성)
        text_blocks = [block.to_dict() for block in processed_blocks]
        
        # 처리 통계 생성
        total_corrections = sum(len(block.corrections) for block in processed_blocks)
        avg_ocr_confidence = (sum(block.confidence for block in processed_blocks) 
                             / len(processed_blocks) if processed_blocks else 0.0)
        avg_processing_confidence = (sum(block.processing_confidence for block in processed_blocks) 
                                   / len(processed_blocks) if processed_blocks else 0.0)
        
        # 페이지 수 계산
        total_pages = max((block.page_number for block in processed_blocks), default=0)
        
        # ProcessingMetrics 생성
        metrics = ProcessingMetrics(
            document_id=document_id,
            total_pages=total_pages,
            ocr_time=ocr_time,
            llm_processing_time=llm_time,
            total_time=total_time,
            memory_peak=memory_used,
            text_blocks_count=len(processed_blocks),
            corrections_count=total_corrections,
            average_ocr_confidence=avg_ocr_confidence,
            average_processing_confidence=avg_processing_confidence
        )
        
        logger.info(f"🎉 Final processing completed for {document_id}:")
        logger.info(f"   ⏱️ Total time: {total_time:.2f}s")
        logger.info(f"   📄 Pages: {total_pages}")
        logger.info(f"   📊 Blocks: {len(processed_blocks)}")
        logger.info(f"   🔧 Corrections: {total_corrections}")
        logger.info(f"   💾 Memory used: {memory_used / 1024 / 1024:.1f} MB")
        logger.info(f"   ⚡ Speed: {len(processed_blocks) / total_time:.1f} blocks/sec")
        
        return {
            "success": True,
            "document_id": document_id,
            "total_pages": total_pages,
            "full_text": full_text,
            "page_texts": page_texts,
            "text_blocks": text_blocks,
            "engine_used": f"SimplifiedOCRService v1.0 (Tesseract + {'Gemini-2.0-flash' if llm_time > 0 else 'OCR-only'})",
            "processing_stats": metrics.to_dict(),
            "llm_postprocessing_enabled": llm_time > 0,
            "performance_metrics": {
                "pdf_size_bytes": pdf_size,
                "processing_speed_blocks_per_sec": len(processed_blocks) / total_time if total_time > 0 else 0,
                "memory_efficiency_mb_per_page": (memory_used / 1024 / 1024) / max(total_pages, 1),
                "ocr_to_total_time_ratio": ocr_time / total_time if total_time > 0 else 0,
                "llm_to_total_time_ratio": llm_time / total_time if total_time > 0 else 0
            }
        }
    
    def _create_page_texts(self, processed_blocks: List[ProcessedOCRBlock]) -> List[Dict[str, Any]]:
        """페이지별 텍스트 구성 (기존 API 호환성)"""
        page_groups = {}
        
        # 페이지별로 블록 그룹화
        for block in processed_blocks:
            page_num = block.page_number
            if page_num not in page_groups:
                page_groups[page_num] = []
            page_groups[page_num].append(block.text)
        
        # 페이지별 텍스트 생성
        page_texts = []
        for page_num in sorted(page_groups.keys()):
            page_text = "\n".join(page_groups[page_num])
            page_texts.append({
                "page_number": page_num,
                "text": page_text.strip(),
                "processing_time": 0  # 전체 처리에 포함됨
            })
        
        return page_texts
    
    def _create_error_response(
        self, 
        document_id: str, 
        error_message: str, 
        start_time: float
    ) -> Dict[str, Any]:
        """오류 응답 생성"""
        total_time = time.time() - start_time
        
        return {
            "success": False,
            "document_id": document_id,
            "error": error_message,
            "total_pages": 0,
            "full_text": "",
            "page_texts": [],
            "text_blocks": [],
            "engine_used": "SimplifiedOCRService v1.0 (Failed)",
            "processing_stats": {
                "total_time": total_time,
                "error": error_message
            }
        }
    
    def _get_memory_usage(self) -> int:
        """현재 프로세스의 메모리 사용량 반환 (바이트)"""
        try:
            process = psutil.Process(os.getpid())
            return process.memory_info().rss
        except:
            return 0
    
    def _update_processing_stats(self, result: Dict[str, Any], success: bool):
        """처리 통계 업데이트"""
        self.processing_stats['total_documents'] += 1
        
        if success:
            self.processing_stats['successful_documents'] += 1
            stats = result.get('processing_stats', {})
            self.processing_stats['total_processing_time'] += stats.get('total_time', 0)
            self.processing_stats['total_ocr_time'] += stats.get('ocr_time', 0)
            self.processing_stats['total_llm_time'] += stats.get('llm_processing_time', 0)
            self.processing_stats['total_pages_processed'] += result.get('total_pages', 0)
            self.processing_stats['total_blocks_extracted'] += stats.get('text_blocks_count', 0)
            self.processing_stats['total_corrections_made'] += stats.get('corrections_count', 0)
        else:
            self.processing_stats['failed_documents'] += 1
    
    def get_service_info(self) -> Dict[str, Any]:
        """서비스 정보 반환"""
        return {
            "service_name": "SimplifiedOCRService",
            "version": "1.0.0",
            "tesseract_engine": self.tesseract_engine.get_engine_info(),
            "llm_postprocessing_enabled": self.enable_llm_postprocessing,
            "text_post_processor": (self.text_post_processor.get_processor_info() 
                                  if self.text_post_processor else None),
            "processing_statistics": self.processing_stats.copy()
        }
    
    def get_processing_statistics(self) -> Dict[str, Any]:
        """처리 통계 반환"""
        stats = self.processing_stats.copy()
        
        # 추가 계산된 통계
        if stats['successful_documents'] > 0:
            stats['average_processing_time'] = stats['total_processing_time'] / stats['successful_documents']
            stats['average_pages_per_document'] = stats['total_pages_processed'] / stats['successful_documents']
            stats['average_blocks_per_document'] = stats['total_blocks_extracted'] / stats['successful_documents']
            stats['average_corrections_per_document'] = stats['total_corrections_made'] / stats['successful_documents']
        
        if stats['total_documents'] > 0:
            stats['success_rate'] = stats['successful_documents'] / stats['total_documents']
        
        return stats