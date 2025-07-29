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

from src.services.quiz_service import QuizService
from src.services.proofreading_service import ProofreadingService
from src.services.discussion_service import DiscussionService
from src.services.analytics_service import AnalyticsService
from src.services.ocr_service import OcrService
from src.services.vector_db import VectorDBManager
from src.config.settings import get_settings


class AIServicer(ai_service_pb2_grpc.AIServiceServicer):
    """gRPC servicer for AI operations"""
    
    def __init__(self):
        self.settings = get_settings()
        self.quiz_service = QuizService()
        self.proofreading_service = ProofreadingService()
        self.discussion_service = DiscussionService()
        self.analytics_service = AnalyticsService()
        self.ocr_service = OcrService()
        self.vector_db_manager = VectorDBManager()
        
        # Initialize vector_db in discussion_service
        self.discussion_service.vector_db = self.vector_db_manager
        
        logger.info("AI Servicer initialized")
    
    async def GenerateQuiz(self, request, context):
        """Generate quiz from document content"""
        try:
            logger.info(f"Quiz generation requested for document: {request.document_id}")
            
            if not self.settings.ai.ENABLE_QUIZ_GENERATION:
                context.set_code(grpc.StatusCode.UNAVAILABLE)
                context.set_details("Quiz generation is disabled")
                return None
            
            # Extract request data
            quiz_data = {
                "document_id": request.document_id,
                "content": request.content.text,
                "language": request.content.language,
                "progress_percentage": request.progress_percentage,
                "question_count": request.question_count or 5,
                "difficulty_level": request.difficulty_level or "medium"
            }
            
            # Generate quiz
            result = await self.quiz_service.generate_quiz(quiz_data)
            
            if result["success"]:
                logger.info(f"Successfully generated {len(result['questions'])} questions")
                # Convert to protobuf response (will implement after generating stubs)
                return self._build_quiz_response(result)
            else:
                context.set_code(grpc.StatusCode.INTERNAL)
                context.set_details(result["error"])
                return None
                
        except Exception as e:
            logger.error(f"Quiz generation failed: {e}")
            context.set_code(grpc.StatusCode.INTERNAL)
            context.set_details(f"Internal error: {str(e)}")
            return None
    
    async def ProofreadText(self, request, context):
        """Proofread and correct text"""
        try:
            logger.info(f"Proofreading requested for user: {request.user.user_id}")
            
            if not self.settings.ai.ENABLE_PROOFREADING:
                context.set_code(grpc.StatusCode.UNAVAILABLE)
                context.set_details("Proofreading is disabled")
                return None
            
            # Extract request data
            proofread_data = {
                "original_text": request.original_text.text,
                "context_text": request.context_text.text if request.context_text else None,
                "language": request.original_text.language,
                "user_id": request.user.user_id
            }
            
            # Perform proofreading
            result = await self.proofreading_service.proofread_text(proofread_data)
            
            if result["success"]:
                logger.info(f"Proofreading completed with {len(result['corrections'])} corrections")
                return self._build_proofread_response(result)
            else:
                context.set_code(grpc.StatusCode.INTERNAL)
                context.set_details(result["error"])
                return None
                
        except Exception as e:
            logger.error(f"Proofreading failed: {e}")
            context.set_code(grpc.StatusCode.INTERNAL)
            context.set_details(f"Internal error: {str(e)}")
            return None
    
    async def InitializeDiscussion(self, request, context):
        """Initialize discussion session with document"""
        try:
            logger.info(f"Discussion initialization for meeting: {request.meeting_id}")
            
            if not self.settings.ai.ENABLE_DISCUSSION_AI:
                context.set_code(grpc.StatusCode.UNAVAILABLE)
                context.set_details("Discussion AI is disabled")
                return None
            
            # Extract request data
            init_data = {
                "document_id": request.document_id,
                "meeting_id": request.meeting_id,
                "full_document": request.full_document.text,
                "participants": [
                    {"user_id": p.user_id, "nickname": p.nickname}
                    for p in request.participants
                ]
            }
            
            # Initialize discussion
            result = await self.discussion_service.initialize_discussion(init_data)
            
            if result["success"]:
                logger.info(f"Discussion initialized with session ID: {result['session_id']}")
                return self._build_discussion_init_response(result)
            else:
                context.set_code(grpc.StatusCode.INTERNAL)
                context.set_details(result["error"])
                return None
                
        except Exception as e:
            logger.error(f"Discussion initialization failed: {e}")
            context.set_code(grpc.StatusCode.INTERNAL)
            context.set_details(f"Internal error: {str(e)}")
            return None
    
    async def ProcessChatMessage(self, request_iterator, context):
        """Process streaming chat messages and generate AI responses"""
        try:
            logger.debug("Starting chat message stream processing")

            if not self.settings.ai.ENABLE_DISCUSSION_AI:
                # 스트림 에러 처리
                await context.abort(grpc.StatusCode.UNAVAILABLE, "Discussion AI is disabled")
                return

            # ✅ 스트림 처리: request_iterator를 통해 메시지들을 순차 처리
            async for request in request_iterator:
                try:
                    logger.debug(f"Processing chat message from: {request.sender.nickname}")

                    message_data = {
                        "session_id": request.discussion_session_id,
                        "sender_id": request.sender.user_id,
                        "sender_nickname": request.sender.nickname,
                        "message": request.message,
                        "timestamp": request.timestamp
                    }

                    # discussion_service 호출
                    result = await self.discussion_service.process_chat_message(message_data)

                    # ✅ 스트림 응답: yield를 사용해서 응답 전송
                    response = self._build_chat_response(result)
                    yield response

                except Exception as e:
                    logger.error(f"Error processing individual message: {e}")
                    # 개별 메시지 에러는 에러 응답으로 전송
                    error_response = ai_service_pb2.ChatMessageResponse(
                        success=False,
                        message=f"Message processing error: {str(e)}"
                    )
                    yield error_response

        except Exception as e:
            logger.error(f"Stream processing failed: {e}")
            await context.abort(grpc.StatusCode.INTERNAL, f"Stream error: {str(e)}")
    
    async def GenerateDiscussionTopic(self, request, context):
        """Generate discussion topics from document"""
        try:
            logger.info(f"Topic generation requested for document: {request.document_id}")
            
            topic_data = {
                "document_id": request.document_id,
                "content": request.document_content.text,
                "previous_topics": list(request.previous_topics)
            }
            
            result = await self.discussion_service.generate_topics(topic_data)
            
            return self._build_topic_response(result)
                
        except Exception as e:
            logger.error(f"Topic generation failed: {e}")
            context.set_code(grpc.StatusCode.INTERNAL)
            context.set_details(f"Internal error: {str(e)}")
            return None
    
    async def AnalyzeUserActivity(self, request, context):
        """Analyze user activity patterns"""
        try:
            logger.info(f"User activity analysis for: {request.user_id}")
            
            if not self.settings.ai.ENABLE_USER_ANALYTICS:
                context.set_code(grpc.StatusCode.UNAVAILABLE)
                context.set_details("User analytics is disabled")
                return None
            
            analysis_data = {
                "user_id": request.user_id,
                "start_date": request.start_date,
                "end_date": request.end_date
            }
            
            result = await self.analytics_service.analyze_user_activity(analysis_data)
            
            return self._build_activity_response(result)
                
        except Exception as e:
            logger.error(f"User activity analysis failed: {e}")
            context.set_code(grpc.StatusCode.INTERNAL)
            context.set_details(f"Internal error: {str(e)}")
            return None

    async def ProcessDocument(self, request, context):
        """Process and embed a document"""
        try:
            logger.info(f"Document processing requested for: {request.document_id}")

            if not self.settings.ai.ENABLE_DISCUSSION_AI:
                context.set_code(grpc.StatusCode.UNAVAILABLE)
                context.set_details("Document processing is disabled")
                return None

            doc_id = request.document_id or f"doc_{uuid.uuid4().hex[:10]}"
            text = request.content.text
            
            if not text:
                context.set_code(grpc.StatusCode.INVALID_ARGUMENT)
                context.set_details("Document content cannot be empty")
                return None

            # Process document using VectorDBManager
            chunk_ids = await self.discussion_service.vector_db.process_document(doc_id, text)
            
            logger.info(f"Successfully processed document {doc_id} into {len(chunk_ids)} chunks.")

            return ai_service_pb2.DocumentResponse(
                success=True,
                message=f"Document processed successfully into {len(chunk_ids)} chunks.",
            )

        except Exception as e:
            logger.error(f"Document processing failed: {e}")
            context.set_code(grpc.StatusCode.INTERNAL)
            context.set_details(f"Internal error: {str(e)}")
            return None
    
    async def ProcessPdf(self, request_iterator, context):
        """Process PDF stream, perform OCR, and store results in VectorDB"""
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
            logger.info(f"Received {len(full_pdf_data)} bytes for document {document_id}.")

            # Perform OCR using OcrService
            ocr_result = await self.ocr_service.process_pdf_stream(full_pdf_data, document_id)

            if not ocr_result["success"]:
                context.set_code(grpc.StatusCode.INTERNAL)
                context.set_details(f"OCR processing failed: {ocr_result.get('error', 'Unknown error')}")
                return ai_service_pb2.ProcessPdfResponse(success=False, message=ocr_result.get('error', 'OCR failed'))

            text_blocks = ocr_result.get("text_blocks", [])
            total_pages = ocr_result.get("total_pages", 0)

            # Store OCR results in VectorDB (with error handling)
            if text_blocks:
                try:
                    await self.vector_db_manager.process_ocr_results(document_id, text_blocks)
                    logger.info(f"Stored OCR results for {document_id} in VectorDB.")
                except Exception as vdb_error:
                    logger.warning(f"VectorDB storage failed for {document_id}: {vdb_error}")
                    logger.info("Continuing without VectorDB storage...")
            else:
                logger.warning(f"No text blocks extracted for {document_id}. Skipping VectorDB storage.")

            # Build response
            response_text_blocks = []
            for block in text_blocks:
                response_text_blocks.append(ai_service_pb2.TextBlock(
                    text=block.get("text", ""),
                    page_number=block.get("page_number", 0),
                    x0=block.get("x0", 0.0),
                    y0=block.get("y0", 0.0),
                    x1=block.get("x1", 0.0),
                    y1=block.get("y1", 0.0),
                    block_type=block.get("block_type", "text")
                ))

            return ai_service_pb2.ProcessPdfResponse(
                success=True,
                message="PDF processed and OCR results stored.",
                document_id=document_id,
                total_pages=total_pages,
                text_blocks=response_text_blocks
            )

        except Exception as e:
            logger.error(f"Error in ProcessPdf RPC for document {document_id}: {e}")
            context.set_code(grpc.StatusCode.INTERNAL)
            context.set_details(f"Internal server error: {str(e)}")
            return ai_service_pb2.ProcessPdfResponse(success=False, message=f"Internal error: {str(e)}")

    # Response building methods using protobuf objects
    def _build_quiz_response(self, result: Dict[str, Any]) -> ai_service_pb2.QuizResponse:
        """Build quiz response using protobuf"""
        response = ai_service_pb2.QuizResponse(
            success=result.get("success", True),
            message=result.get("message", "Quiz generated successfully"),
            quiz_id=result.get("quiz_id", str(uuid.uuid4()))
        )
        
        # Add questions if they exist
        if "questions" in result and result["questions"]:
            for q_data in result["questions"]:
                question = ai_service_pb2.Question(
                    question_text=q_data.get("question", ""),
                    correct_answer_index=q_data.get("correct_answer", 0),
                    explanation=q_data.get("explanation", ""),
                    category=q_data.get("category", "general")
                )
                if "options" in q_data:
                    question.options.extend(q_data["options"])
                
                response.questions.append(question)
        
        return response
    
    def _build_proofread_response(self, result: Dict[str, Any]) -> ai_service_pb2.ProofreadResponse:
        """Build proofread response using protobuf"""
        response = ai_service_pb2.ProofreadResponse(
            success=result.get("success", True),
            message=result.get("message", "Proofreading completed"),
            corrected_text=result.get("corrected_text", ""),
            confidence_score=result.get("confidence_score", 0.8)
        )
        
        # Add corrections if they exist
        if "corrections" in result and result["corrections"]:
            for corr_data in result["corrections"]:
                correction = ai_service_pb2.TextCorrection(
                    original=corr_data.get("original", ""),
                    corrected=corr_data.get("corrected", ""),
                    correction_type=corr_data.get("type", "grammar"),
                    explanation=corr_data.get("explanation", ""),
                    start_position=corr_data.get("start_pos", 0),
                    end_position=corr_data.get("end_pos", 0)
                )
                response.corrections.append(correction)
        
        return response
    
    def _build_discussion_init_response(self, result: Dict[str, Any]) -> ai_service_pb2.DiscussionInitResponse:
        """Build discussion init response using protobuf"""
        return ai_service_pb2.DiscussionInitResponse(
            success=result.get("success", True),
            message=result.get("message", "Discussion initialized"),
            discussion_session_id=result.get("session_id", str(uuid.uuid4()))
        )
    
    def _build_chat_response(self, result: Dict[str, Any]) -> ai_service_pb2.ChatMessageResponse:
        """Build chat response using protobuf"""
        response = ai_service_pb2.ChatMessageResponse(
            success=result.get("success", True),
            message=result.get("message", "Message processed"),
            requires_moderation=result.get("requires_moderation", False)
        )
        
        if "ai_response" in result:
            response.ai_response = result["ai_response"]
        
        if "suggested_topics" in result and result["suggested_topics"]:
            response.suggested_topics.extend(result["suggested_topics"])
        
        return response
    
    def _build_topic_response(self, result: Dict[str, Any]) -> ai_service_pb2.TopicResponse:
        """Build topic response using protobuf"""
        response = ai_service_pb2.TopicResponse(
            success=result.get("success", True),
            message=result.get("message", "Topics generated"),
            recommended_topic=result.get("recommended_topic", ""),
            topic_rationale=result.get("topic_rationale", "")
        )
        
        if "topics" in result and result["topics"]:
            response.discussion_topics.extend(result["topics"])
        
        return response
    
    def _build_activity_response(self, result: Dict[str, Any]) -> ai_service_pb2.UserActivityResponse:
        """Build activity response using protobuf"""
        response = ai_service_pb2.UserActivityResponse(
            success=result.get("success", True),
            message=result.get("message", "Activity analyzed")
        )
        
        if "insights" in result and result["insights"]:
            response.insights.extend(result["insights"])
        
        if "metrics" in result and result["metrics"]:
            for key, value in result["metrics"].items():
                response.metrics[key] = float(value)
        
        # Create activity data if provided
        if "activity_data" in result:
            activity = result["activity_data"]
            response.activity_data.CopyFrom(ai_service_pb2.UserActivityData(
                user_id=activity.get("user_id", ""),
                total_reading_time=activity.get("total_reading_time", 0)
            ))
            
            if "document_ids" in activity:
                response.activity_data.document_ids.extend(activity["document_ids"])
            
            if "reading_times" in activity:
                response.activity_data.reading_times.extend(activity["reading_times"])
        
        return response