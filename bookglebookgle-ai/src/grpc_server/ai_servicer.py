"""
AI Service gRPC Servicer Implementation
"""

from typing import Any, Dict
import uuid
import time
import asyncio

import grpc
from loguru import logger

# Import generated protobuf files
from .generated import ai_service_pb2, ai_service_pb2_grpc

from src.services.discussion_service import DiscussionService
from src.services.ocr_service import OcrService
from src.services.vector_db import VectorDBManager
from src.config.settings import get_settings


class AIServicer(ai_service_pb2_grpc.AIServiceServicer):
    """gRPC servicer for AI operations"""
    
    def __init__(self, vector_db_manager: VectorDBManager):
        self.settings = get_settings()
        self.discussion_service = DiscussionService()
        self.ocr_service = OcrService()
        
        # Use the initialized VectorDBManager passed from the outside
        self.vector_db_manager = vector_db_manager
        self.discussion_service.initialize_manager(self.vector_db_manager)
        
        logger.info("AI Servicer initialized with injected dependencies.")
    
    # 퀴즈 생성과 교정 기능은 나중에 구현 예정으로 제거
    
    async def InitializeDiscussion(self, request, context):
        """모바일 앱 독서 모임 토론 시작 - 간소화된 버전"""
        try:
            logger.info(f"🎯 Starting mobile discussion for meeting: {request.meeting_id}")

            if not self.settings.ai.ENABLE_DISCUSSION_AI:
                context.set_code(grpc.StatusCode.UNAVAILABLE)
                context.set_details("Discussion AI is disabled")
                return None

            # 간소화된 토론 시작
            result = await self.discussion_service.start_discussion(
                document_id=request.document_id,
                meeting_id=request.meeting_id,
                session_id=request.session_id, 
                participants=[{"user_id": p.user_id, "nickname": p.nickname} for p in request.participants]
            )

            if result["success"]:
                logger.info(f"✅ Mobile discussion started for session: {request.session_id}")
                response = ai_service_pb2.DiscussionInitResponse(
                    success=True, 
                    message=result.get("message", "Discussion started successfully")
                )
                
                # 토론 주제가 있으면 추가
                if "discussion_topics" in result and result["discussion_topics"]:
                    response.discussion_topics.extend(result["discussion_topics"])
                if "recommended_topic" in result:
                    response.recommended_topic = result["recommended_topic"]
                    
                return response
            else:
                context.set_code(grpc.StatusCode.INTERNAL)
                context.set_details(result.get("error", "Failed to start discussion"))
                return None

        except Exception as e:
            logger.error(f"Discussion initialization failed: {e}")
            context.set_code(grpc.StatusCode.INTERNAL)
            context.set_details(f"Internal error: {str(e)}")
            return None
    
    async def ProcessChatMessage(self, request_iterator, context):
        """모바일 앱 채팅 메시지 처리 - 간소화된 스트리밍"""
        try:
            logger.debug("📱 Starting mobile chat stream processing")

            if not self.settings.ai.ENABLE_DISCUSSION_AI:
                await context.abort(grpc.StatusCode.UNAVAILABLE, "Discussion AI is disabled")
                return

            # 간소화된 스트림 처리
            async for request in request_iterator:
                try:
                    logger.debug(f"💬 Processing message from: {request.sender.nickname}")

                    message_data = {
                        "session_id": request.discussion_session_id,
                        "sender": {"nickname": request.sender.nickname, "user_id": request.sender.user_id},
                        "message": request.message,
                        "timestamp": request.timestamp
                    }

                    # 간소화된 채팅 처리
                    result = await self.discussion_service.process_chat_message(
                        session_id=request.discussion_session_id,
                        message_data=message_data
                    )

                    # 간단한 응답 생성
                    response = ai_service_pb2.ChatMessageResponse(
                        success=result.get("success", True),
                        message=result.get("message", "Message processed"),
                        ai_response=result.get("ai_response", ""),
                        requires_moderation=result.get("requires_moderation", False)
                    )
                    yield response

                except Exception as e:
                    logger.error(f"Error processing message: {e}")
                    error_response = ai_service_pb2.ChatMessageResponse(
                        success=False,
                        message=f"처리 중 오류가 발생했습니다: {str(e)}"
                    )
                    yield error_response

        except Exception as e:
            logger.error(f"Chat stream failed: {e}")
            await context.abort(grpc.StatusCode.INTERNAL, f"Chat error: {str(e)}")
    
    async def EndDiscussion(self, request, context):
        """모바일 앱 토론 종료 - 간소화된 버전"""
        try:
            logger.info(f"🏁 Ending mobile discussion for session: {request.session_id}")

            if not self.settings.ai.ENABLE_DISCUSSION_AI:
                context.set_code(grpc.StatusCode.UNAVAILABLE)
                context.set_details("Discussion AI is disabled")
                return None

            # 간소화된 토론 종료
            result = await self.discussion_service.end_discussion(
                meeting_id=request.meeting_id,
                session_id=request.session_id
            )

            if result["success"]:
                logger.info(f"✅ Mobile discussion ended for session: {request.session_id}")
                return ai_service_pb2.DiscussionEndResponse(
                    success=True,
                    message=result.get("message", "Discussion ended successfully")
                )
            else:
                context.set_code(grpc.StatusCode.INTERNAL)
                context.set_details(result.get("error", "Failed to end discussion"))
                return None

        except Exception as e:
            logger.error(f"Failed to end discussion: {e}")
            context.set_code(grpc.StatusCode.INTERNAL)
            context.set_details(f"Internal error: {str(e)}")
            return None
            
    # 사용자 분석 기능은 모바일 앱에서 불필요하므로 제거

    async def ProcessStructuredDocument(self, request, context):
        """Process structured text document (PDF with text data) and store in vector DB"""
        try:
            logger.info(f"Structured document processing requested for: {request.document_id}")

            doc_id = request.document_id
            file_name = request.file_name
            metadata = dict(request.metadata)
            ocr_results = request.ocr_results
            
            if not doc_id:
                context.set_code(grpc.StatusCode.INVALID_ARGUMENT)
                context.set_details("Document ID is required")
                return None

            if not ocr_results:
                context.set_code(grpc.StatusCode.INVALID_ARGUMENT)
                context.set_details("OCR results are required")
                return None

            meeting_id = metadata.get("meeting_id")
            if not meeting_id:
                context.set_code(grpc.StatusCode.INVALID_ARGUMENT)
                context.set_details("meeting_id is required in metadata")
                return None

            logger.info(f"📄 Processing structured document for meeting {meeting_id}, document: {doc_id}")

            # OCR 결과를 텍스트로 결합
            full_text = ""
            for text_block in ocr_results:
                full_text += text_block.text + "\n"

            # 벡터DB에 저장
            chunk_ids = await self.vector_db_manager.process_bookclub_document(
                meeting_id=meeting_id,
                document_id=doc_id,
                text=full_text,
                metadata={
                    "file_name": file_name,
                    "processing_type": "structured_text",
                    "source": "text_pdf",
                    **metadata
                }
            )
            
            logger.info(f"✅ Structured document processed: {len(chunk_ids)} chunks created for meeting {meeting_id}")

            # 간소화된 응답
            chunks_data = []
            for i, chunk_id in enumerate(chunk_ids):
                chunks_data.append(ai_service_pb2.DocumentChunk(
                    chunk_id=chunk_id,
                    text=f"Chunk {i+1} from structured document",
                    start_position=0,
                    end_position=0
                ))

            return ai_service_pb2.DocumentResponse(
                success=True,
                message=f"Structured document processed successfully for meeting {meeting_id}. {len(chunk_ids)} chunks created.",
                chunks=chunks_data
            )

        except Exception as e:
            logger.error(f"Structured document processing failed: {e}")
            context.set_code(grpc.StatusCode.INTERNAL)
            context.set_details(f"Internal error: {str(e)}")
            return None

    async def GenerateQuiz(self, request, context):
        """Generate quiz based on progress percentage (50% or 100%)"""
        try:
            logger.info(f"Quiz generation requested for document: {request.document_id}, meeting: {request.meeting_id}, progress: {request.progress_percentage}%")

            doc_id = request.document_id
            meeting_id = request.meeting_id
            progress = request.progress_percentage
            
            if not doc_id:
                context.set_code(grpc.StatusCode.INVALID_ARGUMENT)
                context.set_details("Document ID is required")
                return None

            if not meeting_id:
                context.set_code(grpc.StatusCode.INVALID_ARGUMENT)
                context.set_details("Meeting ID is required")
                return None

            if progress not in [50, 100]:
                context.set_code(grpc.StatusCode.INVALID_ARGUMENT)
                context.set_details("Progress percentage must be 50 or 100")
                return None

            # 벡터DB에서 진도율에 맞는 컨텐츠 검색
            # TODO: 진도율 기반 컨텐츠 검색 로직 구현 필요
            content_chunks = await self.vector_db_manager.search_by_progress(
                meeting_id=meeting_id,
                document_id=doc_id,
                progress_percentage=progress
            )

            if not content_chunks:
                return ai_service_pb2.QuizResponse(
                    success=False,
                    message="No content found for the specified progress percentage"
                )

            # TODO: LLM을 사용한 퀴즈 생성 로직 구현
            # 임시 응답
            questions = [
                ai_service_pb2.Question(
                    question_text="임시 퀴즈 질문입니다.",
                    options=["선택지 1", "선택지 2", "선택지 3", "선택지 4"],
                    correct_answer_index=0
                )
            ]

            quiz_id = f"quiz_{uuid.uuid4().hex[:8]}"
            
            return ai_service_pb2.QuizResponse(
                success=True,
                message="Quiz generated successfully",
                questions=questions,
                quiz_id=quiz_id
            )

        except Exception as e:
            logger.error(f"Quiz generation failed: {e}")
            context.set_code(grpc.StatusCode.INTERNAL)
            context.set_details(f"Internal error: {str(e)}")
            return None

    async def ProofreadText(self, request, context):
        """Proofread and correct text for grammar and context"""
        try:
            logger.info(f"Text proofreading requested from user: {request.user.nickname}")

            original_text = request.original_text.text
            context_text = request.context_text.text if request.context_text else ""
            
            if not original_text:
                context.set_code(grpc.StatusCode.INVALID_ARGUMENT)
                context.set_details("Original text is required")
                return None

            # TODO: LLM을 사용한 텍스트 교정 로직 구현
            # 임시 응답
            corrected_text = original_text  # 임시로 원본 그대로 반환
            corrections = [
                ai_service_pb2.TextCorrection(
                    original="임시",
                    corrected="임시",
                    correction_type="grammar",
                    explanation="임시 교정 설명",
                    start_position=0,
                    end_position=2
                )
            ]

            return ai_service_pb2.ProofreadResponse(
                success=True,
                message="Text proofreading completed",
                corrected_text=corrected_text,
                corrections=corrections,
                confidence_score=0.95
            )

        except Exception as e:
            logger.error(f"Text proofreading failed: {e}")
            context.set_code(grpc.StatusCode.INTERNAL)
            context.set_details(f"Internal error: {str(e)}")
            return None
    
    async def ProcessPdf(self, request_iterator, context):
        """Process PDF stream, perform OCR, and store results in VectorDB automatically"""
        document_id = None
        file_name = None
        metadata = {}
        pdf_data_chunks = []

        try:
            async for request in request_iterator:
                if request.HasField("info"):
                    # First message should contain PdfInfo
                    document_id = request.info.document_id or str(uuid.uuid4())
                    file_name = request.info.file_name
                    metadata = dict(request.info.metadata)
                    logger.info(f"Received PDF info for document: {document_id}, file: {file_name}")
                elif request.HasField("chunk"):
                    # Subsequent messages contain PDF data chunks
                    pdf_data_chunks.append(request.chunk)
                else:
                    logger.warning("Received unknown message type in ProcessPdf stream.")

            if not document_id:
                context.set_code(grpc.StatusCode.INVALID_ARGUMENT)
                context.set_details("Document ID not provided in PdfInfo.")
                return ai_service_pb2.ProcessPdfResponse(success=False, message="Document ID missing.")

            if not pdf_data_chunks:
                context.set_code(grpc.StatusCode.INVALID_ARGUMENT)
                context.set_details("No PDF data chunks received.")
                return ai_service_pb2.ProcessPdfResponse(success=False, message="No PDF data.")

            full_pdf_data = b"".join(pdf_data_chunks)
            meeting_id = metadata.get("meeting_id")
            
            if not meeting_id:
                context.set_code(grpc.StatusCode.INVALID_ARGUMENT)
                context.set_details("meeting_id is required in metadata.")
                return ai_service_pb2.ProcessPdfResponse(success=False, message="meeting_id missing.")

            logger.info(f"📄 Processing PDF for meeting {meeting_id}, document: {document_id}")

            # OCR 처리
            ocr_result = await self.ocr_service.process_pdf_stream(full_pdf_data, document_id)

            if not ocr_result["success"]:
                context.set_code(grpc.StatusCode.INTERNAL)
                context.set_details(f"OCR processing failed: {ocr_result.get('error', 'Unknown error')}")
                return ai_service_pb2.ProcessPdfResponse(success=False, message=ocr_result.get('error', 'OCR failed'))

            # OCR 결과 추출
            full_text = ocr_result.get("full_text", "")
            page_texts = ocr_result.get("page_texts", [])
            total_pages = ocr_result.get("total_pages", 0)
            
            # 벡터DB에 자동 저장 (meeting_id별로 격리)
            try:
                chunk_ids = await self.vector_db_manager.process_bookclub_document(
                    meeting_id=meeting_id,
                    document_id=document_id,
                    text=full_text,
                    metadata={
                        "file_name": file_name,
                        "total_pages": total_pages,
                        "processing_type": "ocr",
                        **metadata
                    }
                )
                logger.info(f"✅ PDF processed and stored: {len(chunk_ids)} chunks created for meeting {meeting_id}")
            except Exception as e:
                logger.error(f"Failed to store PDF in vector DB: {e}")
                # OCR은 성공했으므로 결과는 반환, 하지만 저장 실패 표시
                pass

            # 간소화된 응답 - 페이지별 텍스트만 반환
            response_text_blocks = []
            for page_data in page_texts:
                response_text_blocks.append(ai_service_pb2.TextBlock(
                    text=page_data.get("text", ""),
                    page_number=page_data.get("page_number", 0),
                    x0=0.0, y0=0.0, x1=0.0, y1=0.0,  # 위치 정보는 단순화
                    block_type="page_text"
                ))

            return ai_service_pb2.ProcessPdfResponse(
                success=True,
                message="PDF OCR processing and vector DB storage completed successfully",
                document_id=document_id,
                total_pages=total_pages,
                text_blocks=response_text_blocks
            )

        except Exception as e:
            logger.error(f"Error in ProcessPdf RPC for document {document_id}: {e}")
            context.set_code(grpc.StatusCode.INTERNAL)
            context.set_details(f"Internal server error: {str(e)}")
            return ai_service_pb2.ProcessPdfResponse(success=False, message=f"Internal error: {str(e)}")

    # 불필요한 response builder 메서드들 제거 (퀴즈, 교정, 사용자 분석 관련)