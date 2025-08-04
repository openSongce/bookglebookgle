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
    단순화된 OCR 서비스 - PaddleOCR 기반, LLM 후처리 없는 고성능 서비스
    기존 복잡한 다중 엔진 구조를 대체하는 최적화된 서비스
    """
    
    def __init__(
        self,
        paddleocr_config: Optional[PaddleOCRConfig] = None,
        enable_llm_postprocessing: bool = False  # 기본값을 False로 변경
    ):
        """
        SimplifiedOCRService 초기화
        
        Args:
            paddleocr_config: PaddleOCR 설정 (None이면 기본 설정)
            enable_llm_postprocessing: LLM 후처리 활성화 여부 (사용하지 않음)
        """
        # 설정 초기화
        self.paddleocr_config = paddleocr_config or PaddleOCRConfig()
        self.enable_llm_postprocessing = False  # 강제로 False로 설정
        
        # 엔진 초기화
        self.ocr_engine = PaddleOCREngine(self.paddleocr_config)
        self.vector_db = VectorDBManager()  # VectorDB 직접 연결
        
        # 성능 모니터링
        self.processing_stats = {
            'total_documents': 0,
            'successful_documents': 0,
            'failed_documents': 0,
            'total_processing_time': 0.0,
            'total_ocr_time': 0.0,
            'total_pages_processed': 0,
            'total_blocks_extracted': 0,
            'total_vectordb_time': 0.0
        }
        
        logger.info(f"🚀 SimplifiedOCRService initialized:")
        logger.info(f"   🔧 PaddleOCR language: {self.paddleocr_config.lang}")
        logger.info(f"   🤖 LLM post-processing: Disabled (직접 VectorDB 저장)")
        logger.info(f"   🖥️ GPU usage: {self.paddleocr_config.use_gpu}")
        logger.info(f"   🔄 Angle classification: {self.paddleocr_config.use_angle_cls}")
    
    async def initialize(self) -> bool:
        """
        서비스 초기화 (비동기 컴포넌트 초기화)
        
        Returns:
            초기화 성공 여부
        """
        try:
            logger.info("🔄 Initializing SimplifiedOCRService components...")
            
            # 1. PaddleOCR 엔진 초기화
            ocr_success = await self.ocr_engine.initialize()
            if not ocr_success:
                logger.error("❌ PaddleOCR engine initialization failed")
                return False
            
            # 2. VectorDB 초기화
            try:
                await self.vector_db.initialize()
                logger.info("✅ VectorDB initialized successfully")
            except Exception as e:
                logger.error(f"❌ VectorDB initialization failed: {e}")
                return False
            
            logger.info("✅ SimplifiedOCRService initialization completed")
            logger.info("📝 Pipeline: PDF → PaddleOCR → VectorDB (LLM 후처리 없음)")
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
        PDF 스트림을 처리하여 PaddleOCR로 텍스트 추출 후 VectorDB에 직접 저장
        
        Args:
            pdf_stream: PDF 바이트 스트림
            document_id: 문서 ID
            enable_llm_postprocessing: LLM 후처리 활성화 여부 (무시됨)
            
        Returns:
            처리 결과 딕셔너리
        """
        # 처리 시작
        start_time = time.time()
        process_memory_start = self._get_memory_usage()
        
        logger.info(f"🚀 Starting PDF processing for document: {document_id}")
        logger.info(f"   📄 PDF size: {len(pdf_stream):,} bytes")
        logger.info(f"   🤖 LLM post-processing: Disabled (new pipeline)")
        
        try:
            # 1단계: PaddleOCR을 사용하여 텍스트 추출
            ocr_result = await self._extract_with_paddleocr(pdf_stream, document_id)
            
            if not ocr_result["success"]:
                return self._create_error_response(
                    document_id, 
                    ocr_result["error"], 
                    start_time
                )
            
            ocr_blocks = ocr_result["ocr_blocks"]
            ocr_time = ocr_result["processing_time"]
            
            # 2단계: LLM 후처리 단계 건너뛰기
            logger.info("🤖 Skipping LLM post-processing as per the new pipeline.")
            
            # 3단계: OCR 결과를 VectorDB에 직접 저장
            vectordb_result = await self._store_in_vectordb(ocr_blocks, document_id)
            vectordb_time = vectordb_result["processing_time"]
            
            if not vectordb_result["success"]:
                return self._create_error_response(
                    document_id, 
                    f"VectorDB storage failed: {vectordb_result['error']}", 
                    start_time
                )
            
            # 4단계: 최종 응답 생성
            final_result = await self._create_final_response(
                document_id=document_id,
                ocr_blocks=ocr_blocks,
                ocr_time=ocr_time,
                vectordb_time=vectordb_time,
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
    
    async def _extract_with_paddleocr(
        self, 
        pdf_stream: bytes, 
        document_id: str
    ) -> Dict[str, Any]:
        """
        PaddleOCR을 사용한 텍스트 추출
        
        Args:
            pdf_stream: PDF 바이트 스트림
            document_id: 문서 ID
            
        Returns:
            OCR 처리 결과
        """
        try:
            ocr_start_time = time.time()
            logger.info(f"🔍 Starting PaddleOCR extraction for document: {document_id}")
            
            # PaddleOCR 엔진으로 텍스트 추출
            ocr_blocks = await self.ocr_engine.extract_from_pdf(pdf_stream, document_id)
            
            ocr_time = time.time() - ocr_start_time
            
            logger.info(f"✅ PaddleOCR extraction completed for {document_id}:")
            logger.info(f"   ⏱️ OCR time: {ocr_time:.2f}s")
            logger.info(f"   📊 Blocks extracted: {len(ocr_blocks)}")
            logger.info(f"   📝 Total characters: {sum(len(block.text) for block in ocr_blocks)}")
            
            return {
                "success": True,
                "ocr_blocks": ocr_blocks,
                "processing_time": ocr_time
            }
            
        except Exception as e:
            logger.error(f"❌ PaddleOCR extraction failed for {document_id}: {e}")
            return {"success": False, "error": str(e)}
    
    async def _store_in_vectordb(
        self, 
        ocr_blocks: List[OCRBlock], 
        document_id: str
    ) -> Dict[str, Any]:
        """
        OCR 결과를 VectorDB에 직접 저장
        
        Args:
            ocr_blocks: OCR 블록 리스트
            document_id: 문서 ID
            
        Returns:
            VectorDB 저장 결과
        """
        try:
            vectordb_start_time = time.time()
            logger.info(f"💾 Starting VectorDB storage for document: {document_id}")
            logger.info(f"   📊 Blocks to store: {len(ocr_blocks)}")
            
            # VectorDB에 저장
            success = await self.vector_db.store_document_with_positions(
                document_id=document_id,
                ocr_blocks=ocr_blocks,
                metadata={"ocr_engine": "paddleocr", "llm_postprocessing_enabled": False}
            )
            
            vectordb_time = time.time() - vectordb_start_time
            
            if success:
                logger.info(f"✅ VectorDB storage completed for {document_id}:")
                logger.info(f"   ⏱️ Storage time: {vectordb_time:.2f}s")
                logger.info(f"   📊 Blocks stored: {len(ocr_blocks)}")
                
                return {
                    "success": True,
                    "processing_time": vectordb_time,
                    "blocks_stored": len(ocr_blocks)
                }
            else:
                raise Exception("VectorDB storage operation failed")
            
        except Exception as e:
            vectordb_time = time.time() - vectordb_start_time
            logger.error(f"❌ VectorDB storage failed for {document_id}: {e}")
            return {
                "success": False, 
                "error": str(e),
                "processing_time": vectordb_time
            }
    
    async def _create_final_response(
        self,
        document_id: str,
        ocr_blocks: List[OCRBlock],
        ocr_time: float,
        vectordb_time: float,
        start_time: float,
        process_memory_start: int,
        pdf_size: int
    ) -> Dict[str, Any]:
        """최종 응답 생성"""
        total_time = time.time() - start_time
        process_memory_peak = self._get_memory_usage()
        memory_used = process_memory_peak - process_memory_start
        
        # ProcessedOCRBlock으로 변환 (API 호환성)
        processed_blocks = self._convert_to_processed_blocks(ocr_blocks)
        
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
        avg_ocr_confidence = (sum(block.confidence for block in processed_blocks) 
                             / len(processed_blocks) if processed_blocks else 0.0)
        
        # 페이지 수 계산
        total_pages = max((block.page_number for block in processed_blocks), default=0)
        
        # ProcessingMetrics 생성
        metrics = ProcessingMetrics(
            document_id=document_id,
            total_pages=total_pages,
            ocr_time=ocr_time,
            llm_processing_time=0.0,  # LLM 사용 안함
            total_time=total_time,
            memory_peak=memory_used,
            text_blocks_count=len(processed_blocks),
            corrections_count=0,  # LLM 교정 없음
            average_ocr_confidence=avg_ocr_confidence,
            average_processing_confidence=avg_ocr_confidence  # OCR 신뢰도와 동일
        )
        
        logger.info(f"🎉 Final processing completed for {document_id}:")
        logger.info(f"   ⏱️ Total time: {total_time:.2f}s")
        logger.info(f"   📄 Pages: {total_pages}")
        logger.info(f"   📊 Blocks: {len(processed_blocks)}")
        logger.info(f"   💾 VectorDB time: {vectordb_time:.2f}s")
        logger.info(f"   💾 Memory used: {memory_used / 1024 / 1024:.1f} MB")
        logger.info(f"   ⚡ Speed: {len(processed_blocks) / total_time:.1f} blocks/sec")
        
        return {
            "success": True,
            "document_id": document_id,
            "total_pages": total_pages,
            "full_text": full_text,
            "page_texts": page_texts,
            "text_blocks": text_blocks,
            "engine_used": f"SimplifiedOCRService v2.0 (PaddleOCR-only)",
            "processing_stats": metrics.to_dict(),
            "llm_postprocessing_enabled": False,
            "vectordb_stored": True,
            "performance_metrics": {
                "pdf_size_bytes": pdf_size,
                "processing_speed_blocks_per_sec": len(processed_blocks) / total_time if total_time > 0 else 0,
                "memory_efficiency_mb_per_page": (memory_used / 1024 / 1024) / max(total_pages, 1),
                "ocr_to_total_time_ratio": ocr_time / total_time if total_time > 0 else 0,
                "vectordb_to_total_time_ratio": vectordb_time / total_time if total_time > 0 else 0
            }
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
                processing_confidence=block.confidence,  # OCR 신뢰도와 동일
                llm_model="none (PaddleOCR only)"
            )
            processed_blocks.append(processed_block)
        
        return processed_blocks
    
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
                "engine_used": "SimplifiedOCRService v2.0 (Failed)",
                "processing_stats": {
                    "total_time": total_time,
                    "error": error_message
                },
                "llm_postprocessing_enabled": False,
                "vectordb_stored": False
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
            self.processing_stats['total_pages_processed'] += result.get('total_pages', 0)
            self.processing_stats['total_blocks_extracted'] += stats.get('text_blocks_count', 0)
            # VectorDB 시간 추가
            perf_metrics = result.get('performance_metrics', {})
            total_time = stats.get('total_time', 0)
            vectordb_ratio = perf_metrics.get('vectordb_to_total_time_ratio', 0)
            self.processing_stats['total_vectordb_time'] += total_time * vectordb_ratio
        else:
            self.processing_stats['failed_documents'] += 1
    
    def get_service_info(self) -> Dict[str, Any]:
        """서비스 정보 반환"""
        return {
            "service_name": "SimplifiedOCRService",
            "version": "2.0.0",
            "ocr_engine": self.ocr_engine.get_engine_info(),
            "llm_postprocessing_enabled": False,
            "vectordb_direct_storage": True,
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
            stats['average_vectordb_time'] = stats['total_vectordb_time'] / stats['successful_documents']
        
        if stats['total_documents'] > 0:
            stats['success_rate'] = stats['successful_documents'] / stats['total_documents']
        
        return stats

    async def cleanup(self):
        """리소스 정리"""
        try:
            if self.ocr_engine:
                await self.ocr_engine.cleanup()
            
            if self.vector_db:
                await self.vector_db.cleanup()
            
            logger.info("✅ SimplifiedOCRService cleanup completed")
            
        except Exception as e:
            logger.error(f"⚠️ Error during SimplifiedOCRService cleanup: {e}")