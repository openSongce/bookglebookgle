"""
Tesseract OCR 기반 gRPC 서버
안정적인 텍스트 인식을 위해 Tesseract 사용
"""
import asyncio
import io
import socket
import subprocess
import sys
from typing import AsyncIterator
from concurrent import futures

import grpc
from grpc import aio
from loguru import logger

# Tesseract OCR 사용
from tesseract_ocr import initialize_tesseract_ocr, process_pdf_tesseract
from ai_service_pb2 import (
    ProcessPdfResponse, ProcessPdfRequest, TextBlock, PdfInfo
)
from ai_service_pb2_grpc import AIServiceServicer, add_AIServiceServicer_to_server
from google.protobuf.empty_pb2 import Empty


def check_port_availability(port):
    """포트 사용 가능성 체크"""
    try:
        with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as sock:
            sock.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
            result = sock.bind(('0.0.0.0', port))
            logger.info(f"✅ Port {port} is available")
            return True
    except OSError as e:
        logger.warning(f"⚠️ Port {port} is in use: {e}")
        return False


def kill_port_process(port):
    """포트 사용 프로세스 종료 (Windows 호환)"""
    try:
        logger.info(f"🔍 Finding process using port {port}...")
        result = subprocess.run(['netstat', '-ano'], capture_output=True, text=True)
        lines = result.stdout.split('\n')
        
        pids_to_kill = []
        for line in lines:
            if f':{port}' in line and 'LISTENING' in line:
                parts = line.split()
                if len(parts) >= 5:
                    pid = parts[-1]
                    if pid not in pids_to_kill:
                        pids_to_kill.append(pid)
        
        if not pids_to_kill:
            logger.info(f"No process found using port {port}")
            return True
        
        for pid in pids_to_kill:
            try:
                logger.info(f"🔪 Attempting to kill process PID {pid} using port {port}")
                # Windows에서 taskkill 명령 실행
                kill_result = subprocess.run(
                    ['taskkill', '/PID', pid, '/F'], 
                    capture_output=True, 
                    text=True,
                    shell=True
                )
                
                if kill_result.returncode == 0:
                    logger.info(f"✅ Successfully killed process PID {pid}")
                else:
                    logger.warning(f"⚠️ Failed to kill process PID {pid}: {kill_result.stderr}")
                    
            except Exception as kill_error:
                logger.error(f"❌ Error killing PID {pid}: {kill_error}")
        
        # 잠시 대기 후 포트 상태 재확인
        import time
        time.sleep(2)
        return True
        
    except Exception as e:
        logger.error(f"❌ Error in kill_port_process: {e}")
        return False


class TesseractOCRServicer(AIServiceServicer):
    """Tesseract OCR gRPC 서비스"""
    
    def __init__(self):
        self.initialized = False
        self.total_requests = 0
        self.successful_requests = 0
        
        logger.info("🔧 TesseractOCRServicer created")

    async def ProcessPdf(self, request_iterator: AsyncIterator[ProcessPdfRequest], context) -> ProcessPdfResponse:
        """PDF OCR 처리 (Tesseract 사용)"""
        import time
        start_time = time.time()
        self.total_requests += 1
        
        try:
            logger.info("🔧 Tesseract ProcessPdf request received")
            
            if not self.initialized:
                logger.error("❌ Tesseract OCR engine not initialized")
                return ProcessPdfResponse(
                    success=False,
                    message="Tesseract OCR engine not initialized",
                    document_id="",
                    total_pages=0,
                    text_blocks=[]
                )
            
            # 요청 스트림에서 데이터 수집
            pdf_data = io.BytesIO()
            document_id = ""
            total_chunks = 0
            
            async for request in request_iterator:
                if request.HasField('info'):
                    document_id = request.info.document_id
                    logger.info(f"📋 Processing document: {document_id}")
                elif request.HasField('chunk'):
                    pdf_data.write(request.chunk)
                    total_chunks += 1
            
            logger.info(f"📦 Received {total_chunks} chunks for document {document_id}")
            
            if total_chunks == 0:
                return ProcessPdfResponse(
                    success=False,
                    message="No PDF data received",
                    document_id=document_id,
                    total_pages=0,
                    text_blocks=[]
                )
            
            # Tesseract OCR 처리
            pdf_bytes = pdf_data.getvalue()
            pdf_data.close()
            
            logger.info(f"🔧 Starting Tesseract OCR processing for {len(pdf_bytes)} bytes")
            text_blocks_data = await process_pdf_tesseract(pdf_bytes)
            
            # gRPC TextBlock으로 변환
            text_blocks = []
            total_pages = 0
            
            for block_data in text_blocks_data:
                try:
                    bbox = block_data.get('bbox')
                    if not isinstance(bbox, dict):
                        # bbox가 없거나 딕셔너리가 아니면 기본값 사용
                        x0, y0, x1, y1 = 0.0, 0.0, 1.0, 1.0
                        logger.warning(f"⚠️ Missing or invalid bbox in block: {block_data.get('text', '')[:30]}...")
                    else:
                        x0 = bbox.get('x0', 0.0)
                        y0 = bbox.get('y0', 0.0)
                        x1 = bbox.get('x1', 1.0)
                        y1 = bbox.get('y1', 1.0)

                    text_block = TextBlock(
                        text=block_data.get('text', ''),
                        page_number=block_data.get('page_number', 0),
                        x0=x0,
                        y0=y0,
                        x1=x1,
                        y1=y1,
                        block_type=block_data.get('block_type', 'text'),
                        confidence=block_data.get('confidence', 0.0)
                    )
                    text_blocks.append(text_block)
                    
                    if 'page_number' in block_data:
                        total_pages = max(total_pages, block_data['page_number'])

                except (KeyError, TypeError) as e:
                    logger.error(f"❌ Error processing block data: {block_data}, error: {e}")
                    continue # 문제가 있는 블록은 건너뛰기
            
            processing_time = time.time() - start_time
            self.successful_requests += 1
            
            logger.info(f"✅ Tesseract OCR completed: {len(text_blocks)} blocks from {total_pages} pages in {processing_time:.2f}s")
            
            response = ProcessPdfResponse(
                success=True,
                message=f"Tesseract processing: {total_pages} pages in {processing_time:.2f}s",
                document_id=document_id,
                total_pages=total_pages,
                text_blocks=text_blocks
            )
            logger.debug(f"📦 Returning response with {len(response.text_blocks)} text blocks.")
            return response
            
        except Exception as e:
            logger.error(f"❌ ProcessPdf failed: {e}")
            # document_id가 정의되지 않은 경우를 안전하게 처리
            safe_document_id = ""
            try:
                safe_document_id = document_id
            except NameError:
                pass
                
            return ProcessPdfResponse(
                success=False,
                message=f"Tesseract OCR processing failed: {str(e)}",
                document_id=safe_document_id,
                total_pages=0,
                text_blocks=[]
            )

    async def ProcessPdfStream(self, request_iterator: AsyncIterator[ProcessPdfRequest], context) -> Empty:
        """PDF OCR 처리 (fire-and-forget)"""
        try:
            logger.info("📄 ProcessPdfStream request received")
            result = await self.ProcessPdf(request_iterator)
            
            if result.success:
                logger.info(f"✅ Background Tesseract OCR completed for {result.document_id}")
            else:
                logger.error(f"❌ Background Tesseract OCR failed: {result.message}")
            
            return Empty()
            
        except Exception as e:
            logger.error(f"❌ ProcessPdfStream failed: {e}")
            return Empty()

    async def initialize(self):
        """Tesseract OCR 서비스 초기화"""
        try:
            logger.info("🔧 Initializing Tesseract OCR servicer...")
            success = await initialize_tesseract_ocr()
            
            if success:
                self.initialized = True
                logger.info("✅ Tesseract OCR servicer initialized successfully")
            else:
                logger.error("❌ Tesseract OCR servicer initialization failed")
                
            return success
            
        except Exception as e:
            logger.error(f"❌ Tesseract OCR servicer initialization error: {e}")
            return False
    
    def get_stats(self):
        """서비스 통계"""
        success_rate = (self.successful_requests / self.total_requests * 100) if self.total_requests > 0 else 0
        return {
            'total_requests': self.total_requests,
            'successful_requests': self.successful_requests,
            'success_rate_percent': success_rate,
            'service_initialized': self.initialized
        }


async def serve():
    """Tesseract gRPC 서버 시작"""
    logger.info("🔧 Starting Tesseract OCR gRPC server...")
    
    # 서비스 인스턴스 생성 및 초기화
    servicer = TesseractOCRServicer()
    init_success = await servicer.initialize()
    
    if not init_success:
        logger.error("❌ Failed to initialize Tesseract OCR service")
        return
    
    # gRPC 서버 설정 - 병렬 처리 최적화
    from multiprocessing import cpu_count
    max_workers = min(cpu_count(), 8)  # CPU 코어 수에 맞춰 조정, 최대 8개
    
    server = aio.server(
        futures.ThreadPoolExecutor(max_workers=max_workers),
        options=[
            ('grpc.keepalive_time_ms', 60000),
            ('grpc.keepalive_timeout_ms', 10000), 
            ('grpc.keepalive_permit_without_calls', True),
            ('grpc.max_receive_message_length', 100 * 1024 * 1024),  # 100MB - 고해상도 이미지 처리
            ('grpc.max_send_message_length', 100 * 1024 * 1024),     # 100MB
            ('grpc.so_reuseport', 1),  # 포트 재사용
        ]
    )
    add_AIServiceServicer_to_server(servicer, server)
    
    # 포트 준비 (EC2 서버와 호환)
    port = 4738  # 새로운 포트 사용
    listen_addr = f'0.0.0.0:{port}'
    
    logger.info(f"🔍 Checking port {port} availability...")
    
    if not check_port_availability(port):
        logger.warning(f"⚠️ Port {port} is in use, attempting to free it...")
        kill_port_process(port)
        
        # 포트 해제 대기
        import time
        for i in range(5):  # 최대 5번 시도
            time.sleep(1)
            if check_port_availability(port):
                logger.info(f"✅ Port {port} is now available")
                break
            logger.info(f"⏳ Waiting for port {port} to be freed... ({i+1}/5)")
        else:
            logger.error(f"❌ Could not free port {port} after 5 attempts")
            return
    
    try:
        logger.info(f"🔄 Binding to {listen_addr}...")
        server.add_insecure_port(listen_addr)
        
        logger.info(f"🌐 Starting Tesseract server...")
        await server.start()
        
        logger.info(f"✅ Enhanced Tesseract OCR server started on {listen_addr}")
        logger.info(f"📡 Accessible via Tailscale at: 100.127.241.36:{port}")
        logger.info(f"🔧 Using Tesseract OCR with parallel processing ({max_workers} workers)")
        logger.info("🚀 Enhanced features: 300 DPI, advanced image preprocessing, parallel CPU processing")
        
    except Exception as e:
        logger.error(f"❌ Failed to start server: {e}")
        return
    
    logger.info("✅ Tesseract OCR gRPC server is running")
    logger.info("🔧 Stable text recognition with Tesseract")
    
    # 서버 상태 모니터링
    async def log_stats():
        while True:
            await asyncio.sleep(300)  # 5분마다
            stats = servicer.get_stats()
            logger.info(f"📊 Server Stats: {stats}")
    
    asyncio.create_task(log_stats())
    
    try:
        await server.wait_for_termination()
    except KeyboardInterrupt:
        logger.info("🛑 Shutting down server...")
        await server.stop(grace=5)
        logger.info("✅ Server shutdown complete")


if __name__ == '__main__':
    # 로깅 설정
    logger.add("tesseract_ocr_service.log", rotation="50 MB", level="INFO")
    
    try:
        asyncio.run(serve())
    except KeyboardInterrupt:
        logger.info("👋 Tesseract OCR service terminated")
    except Exception as e:
        logger.error(f"❌ Server error: {e}")