"""
Tailscale OCR Client - EC2에서 로컬 OCR 서비스로 연결
무조건 Tailscale 100.127.241.36:57575 로컬 서비스 사용, fallback 없음
"""

import asyncio
import io
from typing import Dict, Any, List, Optional, AsyncIterator
import grpc
from loguru import logger
import fitz

from src.config.settings import get_settings
from src.models.ocr_models import OCRBlock, ProcessedOCRBlock, ProcessingMetrics, BoundingBox


# gRPC protobuf imports - try multiple paths
ProcessPdfRequest = None
ProcessPdfResponse = None
PdfInfo = None
AIServiceStub = None

try:
    # EC2에서 사용하는 기본 protobuf 경로 (ai_service.proto에서 생성)
    from src.grpc_server.generated.ai_service_pb2 import (
        ProcessPdfRequest as EcProcessPdfRequest, 
        ProcessPdfResponse as EcProcessPdfResponse, 
        PdfInfo as EcPdfInfo
    )
    from src.grpc_server.generated.ai_service_pb2_grpc import AIServiceStub as EcAIServiceStub
    
    # EC2 protobuf 클래스를 사용
    ProcessPdfRequest = EcProcessPdfRequest
    ProcessPdfResponse = EcProcessPdfResponse  
    PdfInfo = EcPdfInfo
    AIServiceStub = EcAIServiceStub
    logger.info("✅ Using EC2 protobuf classes from src.grpc_server.generated")
    
except ImportError as e:
    logger.error(f"❌ Failed to import EC2 protobuf classes: {e}")
    try:
        # 로컬 OCR 서비스 protobuf 경로 (동일한 ai_service.proto)
        from local_ocr_service.ai_service_pb2 import (
            ProcessPdfRequest, ProcessPdfResponse, PdfInfo
        )
        from local_ocr_service.ai_service_pb2_grpc import AIServiceStub
        logger.info("✅ Using local OCR protobuf classes as fallback")
    except ImportError as e2:
        logger.error(f"❌ Failed to import local OCR protobuf classes: {e2}")
        logger.warning("❌ No protobuf classes available")
        ProcessPdfRequest = None
        ProcessPdfResponse = None
        PdfInfo = None
        AIServiceStub = None


class TailscaleOCRClient:
    """
    Tailscale 로컬 OCR 서비스 클라이언트
    EC2 Docker에서 무조건 로컬 OCR 서비스(100.127.241.36:57575)만 사용
    """
    
    def __init__(self):
        self.settings = get_settings()
        self.channel = None
        self.stub = None
        self.initialized = False
        
        # Tailscale 설정
        self.host = self.settings.local_ocr.TAILSCALE_HOST
        self.port = 4738  # 고정 포트 4738 사용
        self.timeout = self.settings.local_ocr.CONNECTION_TIMEOUT
        self.retry_attempts = self.settings.local_ocr.RETRY_ATTEMPTS
        self.retry_delay = self.settings.local_ocr.RETRY_DELAY
        
        logger.info(f"🌐 TailscaleOCRClient created - Target: {self.host}:{self.port}")
        
        # 성능 통계
        self.stats = {
            'total_requests': 0,
            'successful_requests': 0,
            'failed_requests': 0,
            'total_processing_time': 0.0,
            'total_pages_processed': 0,
            'connection_errors': 0
        }

    async def initialize(self) -> bool:
        """
        Tailscale OCR 클라이언트 초기화
        
        Returns:
            초기화 성공 여부 (실패 시 서버 시작 불가)
        """
        try:
            logger.info(f"🔄 Initializing Tailscale OCR connection to {self.host}:{self.port}...")
            
            # gRPC 채널 옵션 - Tailscale 네트워크 최적화
            options = [
                ('grpc.keepalive_time_ms', 60000),  # 60초로 늘려 PING 빈도 줄임
                ('grpc.keepalive_timeout_ms', 10000), # 타임아웃도 10초로 조정
                ('grpc.keepalive_permit_without_calls', True),
                ('grpc.http2.max_pings_without_data', 0),
                ('grpc.http2.min_time_between_pings_ms', 5000),
                ('grpc.max_receive_message_length', 200 * 1024 * 1024),  # 200MB
                ('grpc.max_send_message_length', 200 * 1024 * 1024),     # 200MB
            ]
            
            # Tailscale 전용 연결 (100.127.241.36:4738)
            target = f"{self.host}:{self.port}"  # 100.127.241.36:4738
            
            logger.info(f"🔄 Connecting to Tailscale OCR: {target}")
            self.channel = grpc.aio.insecure_channel(target, options=options)
            
            if AIServiceStub is None:
                logger.error("❌ gRPC protobuf classes not available")
                logger.error("💡 Make sure protobuf files are generated:")
                logger.error("   - Check src/grpc_server/generated/ directory")
                logger.error("   - Run: python -m grpc_tools.protoc if needed")
                return False
            
            self.stub = AIServiceStub(self.channel)
            
            # 연결 테스트
            await self._test_connection()
            
            logger.info(f"✅ Successfully connected to {target}")
            
            self.initialized = True
            logger.info(f"🚀 Ready for ultra-fast OCR processing via Tailscale")
            
            return True
            
        except Exception as e:
            logger.error(f"❌ Tailscale OCR client initialization failed: {e}")
            if self.channel:
                await self.channel.close()
                self.channel = None
                self.stub = None
            
            # EC2 서버 시작을 막기 위해 False 반환
            logger.error("🚨 EC2 서버는 로컬 OCR 서비스 없이 시작할 수 없습니다!")
            return False

    async def _test_connection(self):
        """Tailscale OCR 서비스 연결 테스트"""
        try:
            logger.info("🧪 Testing Tailscale OCR connection...")
            
            # 간단한 테스트 PDF 생성
            test_pdf = self._create_test_pdf()
            
            # gRPC 스트리밍 요청 생성
            async def request_generator():
                # PDF 정보 전송
                yield ProcessPdfRequest(info=PdfInfo(document_id="connection_test"))
                
                # PDF 데이터 청크로 전송
                chunk_size = 1024 * 1024  # 1MB 청크
                for i in range(0, len(test_pdf), chunk_size):
                    chunk = test_pdf[i:i+chunk_size]
                    yield ProcessPdfRequest(chunk=chunk)
            
            # 연결 테스트 실행
            response = await asyncio.wait_for(
                self.stub.ProcessPdf(request_generator()),
                timeout=self.timeout
            )
            
            if response.success:
                logger.info(f"✅ Tailscale OCR connection test successful")
                logger.info(f"   📄 Test result: {response.total_pages} pages processed")
            else:
                raise Exception(f"OCR test failed: {response.message}")
                
        except asyncio.TimeoutError:
            raise Exception(f"Connection timeout after {self.timeout}s")
        except Exception as e:
            raise Exception(f"Connection test failed: {e}")

    def _create_test_pdf(self) -> bytes:
        """연결 테스트용 간단한 PDF 생성"""
        import fitz
        
        doc = fitz.Document()
        page = doc.new_page()
        page.insert_text((50, 50), "Tailscale OCR Connection Test", fontsize=12)
        page.insert_text((50, 70), "테일스케일 OCR 연결 테스트", fontsize=12)
        
        pdf_bytes = doc.tobytes()
        doc.close()
        
        return pdf_bytes

    async def process_pdf_stream(
        self, 
        pdf_stream: bytes, 
        document_id: str,
        enable_llm_postprocessing: Optional[bool] = None
    ) -> Dict[str, Any]:
        """
        PDF 스트림을 Tailscale OCR 서비스로 처리
        
        Args:
            pdf_stream: PDF 바이트 스트림
            document_id: 문서 ID
            enable_llm_postprocessing: 사용하지 않음 (호환성용)
            
        Returns:
            OCR 처리 결과
        """
        import time
        
        start_time = time.time()
        self.stats['total_requests'] += 1
        
        if not self.initialized:
            error_msg = "Tailscale OCR client not initialized"
            logger.error(f"❌ {error_msg}")
            self.stats['failed_requests'] += 1
            raise Exception(error_msg)
        
        try:
            logger.info(f"🚀 Processing PDF via Tailscale OCR: {len(pdf_stream)} bytes")
            
            # 재시도 로직
            last_error = None
            for attempt in range(self.retry_attempts):
                try:
                    result = await self._process_with_retry(pdf_stream, document_id, attempt)
                    
                    processing_time = time.time() - start_time
                    self.stats['successful_requests'] += 1
                    self.stats['total_processing_time'] += processing_time
                    self.stats['total_pages_processed'] += result['total_pages']
                    
                    pages_per_second = result['total_pages'] / processing_time if processing_time > 0 else 0
                    
                    logger.info(f"✅ Tailscale OCR processing completed:")
                    logger.info(f"   ⚡ Speed: {pages_per_second:.1f} pages/sec")
                    logger.info(f"   📊 Pages: {result['total_pages']}")
                    logger.info(f"   📝 Blocks: {len(result['ocr_blocks'])}")
                    logger.info(f"   ⏱️ Time: {processing_time:.2f}s")
                    
                    return result
                    
                except Exception as e:
                    last_error = e
                    self.stats['connection_errors'] += 1
                    
                    if attempt < self.retry_attempts - 1:
                        wait_time = self.retry_delay * (attempt + 1)
                        logger.warning(f"⚠️ Tailscale OCR attempt {attempt + 1} failed: {e}")
                        logger.info(f"🔄 Retrying in {wait_time}s...")
                        await asyncio.sleep(wait_time)
                    else:
                        break
            
            # 모든 재시도 실패
            error_msg = f"All {self.retry_attempts} Tailscale OCR attempts failed. Last error: {last_error}"
            logger.error(f"❌ {error_msg}")
            self.stats['failed_requests'] += 1
            
            # EC2에서는 fallback 없이 에러를 발생시킴
            raise Exception(error_msg)
            
        except Exception as e:
            processing_time = time.time() - start_time
            logger.error(f"❌ Tailscale OCR processing failed: {e}")
            self.stats['failed_requests'] += 1
            raise

    import fitz

    async def _process_with_retry(self, pdf_stream: bytes, document_id: str, attempt: int) -> Dict[str, Any]:
        """재시도 가능한 OCR 처리"""
        try:
            # 페이지 수에 따라 동적으로 타임아웃 계산
            pdf_doc = fitz.open(stream=pdf_stream, filetype="pdf")
            page_count = len(pdf_doc)
            pdf_doc.close()

            base_timeout = self.timeout # 기본 30초
            # 페이지당 5초 추가, 최소 30초 보장
            dynamic_timeout = max(base_timeout, page_count * 5)
            # 재시도 시 타임아웃 증가
            current_timeout = dynamic_timeout + (attempt * 10)

            logger.info(f"Attempt {attempt + 1}: Calculated timeout is {current_timeout:.2f}s for a {page_count}-page file.")

        except Exception as e:
            logger.warning(f"Could not determine page count from PDF stream: {e}. Falling back to default timeout.")
            current_timeout = self.timeout + (attempt * 10)

        async def request_generator():
            # PDF 정보 전송
            yield ProcessPdfRequest(info=PdfInfo(document_id=document_id))
            
            # PDF 데이터를 청크로 나누어 전송
            chunk_size = 2 * 1024 * 1024  # 2MB 청크 (Tailscale 최적화)
            for i in range(0, len(pdf_stream), chunk_size):
                chunk = pdf_stream[i:i+chunk_size]
                yield ProcessPdfRequest(chunk=chunk)
        
        try:
            # Tailscale OCR 서비스 호출
            response = await asyncio.wait_for(
                self.stub.ProcessPdf(request_generator()),
                timeout=current_timeout
            )
            
            if not response.success:
                raise Exception(f"Tailscale OCR failed: {response.message}")
            
            # gRPC 응답을 내부 형식으로 변환
            ocr_blocks = []
            for text_block in response.text_blocks:
                ocr_block = OCRBlock(
                    text=text_block.text,
                    page_number=text_block.page_number,
                    bbox=BoundingBox(
                        x0=text_block.x0,
                        y0=text_block.y0,
                        x1=text_block.x1,
                        y1=text_block.y1,
                    ),
                    confidence=text_block.confidence,
                    block_type=text_block.block_type
                )
                ocr_blocks.append(ocr_block)
            
            # 페이지별 텍스트 및 전체 텍스트 생성
            page_texts = {}
            for block in ocr_blocks:
                if block.page_number not in page_texts:
                    page_texts[block.page_number] = []
                page_texts[block.page_number].append(block.text)
            
            full_text = "\n\n".join([" ".join(texts) for _, texts in sorted(page_texts.items())])
            page_texts_list = [" ".join(texts) for _, texts in sorted(page_texts.items())]

            return {
                'success': True,
                'message': f'Tailscale OCR processing completed: {response.message}',
                'document_id': document_id,
                'total_pages': response.total_pages,
                'ocr_blocks': ocr_blocks,
                'full_text': full_text,
                'page_texts': page_texts_list,
                'processing_metrics': ProcessingMetrics(
                    document_id=document_id,
                    total_pages=response.total_pages,
                    ocr_time=0.0,
                    llm_processing_time=0.0,
                    total_time=0.0,
                    memory_peak=0,
                    text_blocks_count=len(ocr_blocks),
                    corrections_count=0,
                    average_ocr_confidence=0.0,
                    average_processing_confidence=0.0
                ),
                'tailscale_ultra_fast': True  # Tailscale 처리 표시
            }
            
        except asyncio.TimeoutError:
            raise Exception(f"Tailscale OCR timeout after {self.timeout + (attempt * 10)}s")
        except grpc.RpcError as e:
            if e.code() == grpc.StatusCode.UNAVAILABLE:
                raise Exception(f"Tailscale OCR service unavailable: {e.details()}")
            else:
                raise Exception(f"Tailscale OCR gRPC error: {e.details()}")

    async def cleanup(self):
        """클라이언트 정리"""
        try:
            if self.channel:
                logger.info("🧹 Closing Tailscale OCR connection...")
                await self.channel.close()
                logger.info("✅ Tailscale OCR connection closed")
                
            self.channel = None
            self.stub = None
            self.initialized = False
            
        except Exception as e:
            logger.error(f"⚠️ Error during Tailscale OCR cleanup: {e}")

    def get_stats(self) -> Dict[str, Any]:
        """클라이언트 통계 반환"""
        success_rate = (
            (self.stats['successful_requests'] / self.stats['total_requests'] * 100)
            if self.stats['total_requests'] > 0 else 0
        )
        
        avg_processing_time = (
            (self.stats['total_processing_time'] / self.stats['successful_requests'])
            if self.stats['successful_requests'] > 0 else 0
        )
        
        avg_pages_per_second = (
            (self.stats['total_pages_processed'] / self.stats['total_processing_time'])
            if self.stats['total_processing_time'] > 0 else 0
        )
        
        return {
            'client_type': 'TailscaleOCRClient',
            'target_address': f"{self.host}:{self.port}",
            'initialized': self.initialized,
            'total_requests': self.stats['total_requests'],
            'successful_requests': self.stats['successful_requests'],
            'failed_requests': self.stats['failed_requests'],
            'success_rate_percent': success_rate,
            'total_pages_processed': self.stats['total_pages_processed'],
            'average_processing_time_seconds': avg_processing_time,
            'average_pages_per_second': avg_pages_per_second,
            'connection_errors': self.stats['connection_errors'],
            'ultra_fast_mode': True,
            'fallback_enabled': False  # EC2에서는 fallback 없음
        }