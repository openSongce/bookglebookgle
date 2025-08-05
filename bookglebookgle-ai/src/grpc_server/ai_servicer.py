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
from src.services.simplified_ocr_service import SimplifiedOCRService
from src.services.vector_db import VectorDBManager
from src.services.meeting_service import MeetingService
from src.config.settings import get_settings


class AIServicer(ai_service_pb2_grpc.AIServiceServicer):
    """gRPC servicer for AI operations"""
    
    def __init__(self, vector_db_manager: VectorDBManager, redis_manager=None, llm_client=None, 
                 quiz_service=None, proofreading_service=None):
        self.settings = get_settings()
        self.discussion_service = DiscussionService()
        self.meeting_service = MeetingService()  # 새로 추가
        
        # Initialize SimplifiedOCRService with LLM post-processing enabled
        self.ocr_service = SimplifiedOCRService(enable_llm_postprocessing=True)
        
        # Use the initialized services passed from the outside
        self.vector_db_manager = vector_db_manager
        self.redis_manager = redis_manager
        self.llm_client = llm_client
        self.quiz_service = quiz_service
        self.proofreading_service = proofreading_service
        
        logger.info("AI Servicer initialized with all injected dependencies.")
    
    async def initialize_services(self):
        """Initialize all services asynchronously"""
        try:
            # Initialize discussion service with vector DB
            await self.discussion_service.initialize_manager(self.vector_db_manager)
            
            # Initialize meeting service with vector DB and discussion service
            await self.meeting_service.initialize(self.vector_db_manager, self.discussion_service)
            
            # Initialize OCR service
            ocr_success = await self.initialize_ocr_service()
            
            # Log service availability status
            self._log_service_status()
            
            logger.info("✅ All AI services initialized successfully")
            return ocr_success
        except Exception as e:
            logger.error(f"❌ Service initialization error: {e}")
            return False
    
    def _log_service_status(self):
        """Log the status of all injected services"""
        services_status = {
            "Vector DB Manager": self.vector_db_manager is not None,
            "Redis Manager": self.redis_manager is not None,
            "LLM Client": self.llm_client is not None,
            "Quiz Service": self.quiz_service is not None,
            "Proofreading Service": self.proofreading_service is not None,
            "Discussion Service": True,  # Always available
            "OCR Service": True  # Always available
        }
        
        logger.info("📊 AI Servicer Service Status:")
        for service_name, is_available in services_status.items():
            status_icon = "✅" if is_available else "❌"
            logger.info(f"  {status_icon} {service_name}")
        
        # Log feature availability
        ai_features_available = self.quiz_service is not None and self.proofreading_service is not None
        feature_status = "✅ Enabled" if ai_features_available else "⚠️ Using Mock Data"
        logger.info(f"🧠 AI Features (Quiz/Proofreading): {feature_status}")
    
    async def initialize_ocr_service(self):
        """Initialize the OCR service asynchronously"""
        try:
            success = await self.ocr_service.initialize()
            if success:
                logger.info("✅ SimplifiedOCRService initialized successfully")
            else:
                logger.error("❌ SimplifiedOCRService initialization failed")
            return success
        except Exception as e:
            logger.error(f"❌ SimplifiedOCRService initialization error: {e}")
            return False
    
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
        """모바일 앱 채팅 메시지 처리 - 채팅 기록 컨텍스트 지원"""
        try:
            logger.debug("📱 Starting mobile chat stream processing with chat history support")

            if not self.settings.ai.ENABLE_DISCUSSION_AI:
                await context.abort(grpc.StatusCode.UNAVAILABLE, "Discussion AI is disabled")
                return

            # 채팅 기록 컨텍스트를 지원하는 스트림 처리
            async for request in request_iterator:
                try:
                    logger.debug(f"💬 Processing message from: {request.sender.nickname}")

                    # 채팅 기록 컨텍스트 옵션 처리
                    use_chat_context = getattr(request, 'use_chat_context', True)
                    context_window_size = getattr(request, 'context_window_size', 5)
                    store_in_history = getattr(request, 'store_in_history', True)

                    message_data = {
                        "session_id": request.discussion_session_id,
                        "sender": {
                            "nickname": request.sender.nickname, 
                            "user_id": request.sender.user_id
                        },
                        "message": request.message,
                        "timestamp": request.timestamp,
                        "use_chat_context": use_chat_context,
                        "context_window_size": context_window_size,
                        "store_in_history": store_in_history
                    }

                    # 채팅 기록 컨텍스트를 활용한 채팅 처리
                    result = await self.discussion_service.process_chat_message(
                        session_id=request.discussion_session_id,
                        message_data=message_data
                    )

                    # 채팅 기록 정보를 포함한 응답 생성
                    response = ai_service_pb2.ChatMessageResponse(
                        success=result.get("success", True),
                        message=result.get("message", "Message processed"),
                        ai_response=result.get("ai_response", ""),
                        requires_moderation=result.get("requires_moderation", False),
                        context_messages_used=result.get("chat_context_used", 0),
                        chat_history_enabled=True
                    )
                    
                    # 최근 컨텍스트 메시지 추가 (선택적)
                    if use_chat_context and result.get("recent_context"):
                        for ctx_msg in result.get("recent_context", []):
                            history_msg = ai_service_pb2.ChatHistoryMessage(
                                message_id=ctx_msg.get("message_id", ""),
                                session_id=ctx_msg.get("session_id", ""),
                                content=ctx_msg.get("content", ""),
                                timestamp=ctx_msg.get("timestamp", 0),
                                message_type=ctx_msg.get("message_type", "USER")
                            )
                            # sender 정보 설정
                            if "sender" in ctx_msg:
                                history_msg.sender.user_id = ctx_msg["sender"].get("user_id", "")
                                history_msg.sender.nickname = ctx_msg["sender"].get("nickname", "")
                            
                            response.recent_context.append(history_msg)
                    
                    yield response

                except Exception as e:
                    logger.error(f"Error processing message: {e}")
                    error_response = ai_service_pb2.ChatMessageResponse(
                        success=False,
                        message=f"처리 중 오류가 발생했습니다: {str(e)}",
                        chat_history_enabled=True
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
    
    async def EndMeeting(self, request, context):
        """통합 모임 종료 API - 모든 모임 타입 지원"""
        try:
            logger.info(f"🏁 Ending {request.meeting_type} meeting: {request.meeting_id}")
            
            # MeetingService 초기화 확인
            if not self.meeting_service.is_initialized():
                context.set_code(grpc.StatusCode.UNAVAILABLE)
                context.set_details("MeetingService is not initialized")
                return None
            
            # 모임 타입별 파라미터 준비
            kwargs = {}
            if request.meeting_type == "discussion" and request.HasField("session_id"):
                kwargs["session_id"] = request.session_id
            
            # 통합 모임 종료 처리
            result = await self.meeting_service.end_meeting(
                meeting_id=request.meeting_id,
                meeting_type=request.meeting_type,
                **kwargs
            )
            
            if result["success"]:
                logger.info(f"✅ {request.meeting_type.title()} meeting ended: {request.meeting_id}")
                return ai_service_pb2.MeetingEndResponse(
                    success=True,
                    message=result.get("message", f"{request.meeting_type.title()} meeting ended successfully"),
                    meeting_type=request.meeting_type
                )
            else:
                logger.error(f"❌ Failed to end {request.meeting_type} meeting {request.meeting_id}: {result.get('message')}")
                context.set_code(grpc.StatusCode.INTERNAL)
                context.set_details(result.get("message", "Failed to end meeting"))
                return None
                
        except Exception as e:
            logger.error(f"Failed to end {request.meeting_type} meeting {request.meeting_id}: {e}")
            context.set_code(grpc.StatusCode.INTERNAL)
            context.set_details(f"Internal error: {str(e)}")
            return None
    
    async def GetChatHistory(self, request, context):
        """채팅 기록 조회"""
        try:
            logger.debug(f"📜 Chat history requested for session: {request.session_id}")

            session_id = request.session_id
            limit = getattr(request, 'limit', 10)
            since_timestamp = getattr(request, 'since_timestamp', None)
            user_id = getattr(request, 'user_id', None)

            if not session_id:
                context.set_code(grpc.StatusCode.INVALID_ARGUMENT)
                context.set_details("Session ID is required")
                return None

            try:
                # 채팅 기록 매니저에서 메시지 조회
                if user_id:
                    # 특정 사용자의 메시지만 조회
                    messages = await self.discussion_service.chat_history_manager.get_user_messages(
                        session_id, user_id, limit
                    )
                else:
                    # 최근 메시지 조회
                    messages = await self.discussion_service.chat_history_manager.get_recent_messages(
                        session_id, limit
                    )

                # since_timestamp 필터링
                if since_timestamp:
                    messages = [
                        msg for msg in messages 
                        if msg.timestamp.timestamp() > since_timestamp
                    ]

                # 응답 메시지 생성
                response_messages = []
                for msg in messages:
                    history_msg = ai_service_pb2.ChatHistoryMessage(
                        message_id=msg.message_id,
                        session_id=msg.session_id,
                        content=msg.content,
                        timestamp=int(msg.timestamp.timestamp()),
                        message_type=msg.message_type.value
                    )
                    
                    # sender 정보 설정
                    history_msg.sender.user_id = msg.user_id
                    history_msg.sender.nickname = msg.nickname
                    
                    # metadata 설정
                    if msg.metadata:
                        for key, value in msg.metadata.items():
                            history_msg.metadata[key] = str(value)
                    
                    response_messages.append(history_msg)

                return ai_service_pb2.GetChatHistoryResponse(
                    success=True,
                    message="Chat history retrieved successfully",
                    messages=response_messages,
                    total_count=len(response_messages),
                    has_more=len(response_messages) >= limit
                )

            except Exception as e:
                logger.error(f"Failed to get chat history: {e}")
                return ai_service_pb2.GetChatHistoryResponse(
                    success=False,
                    message=f"Failed to retrieve chat history: {str(e)}",
                    messages=[],
                    total_count=0,
                    has_more=False
                )

        except Exception as e:
            logger.error(f"GetChatHistory failed: {e}")
            context.set_code(grpc.StatusCode.INTERNAL)
            context.set_details(f"Internal error: {str(e)}")
            return None
    
    async def GetChatSessionStats(self, request, context):
        """채팅 세션 통계 조회"""
        try:
            logger.debug(f"📊 Chat session stats requested for: {request.session_id}")

            session_id = request.session_id
            if not session_id:
                context.set_code(grpc.StatusCode.INVALID_ARGUMENT)
                context.set_details("Session ID is required")
                return None

            try:
                # 세션 통계 조회
                stats = await self.discussion_service.get_chat_history_stats(session_id)
                
                if "error" in stats:
                    return ai_service_pb2.ChatSessionStatsResponse(
                        success=False,
                        message=f"Failed to get session stats: {stats['error']}",
                        session_id=session_id,
                        chat_history_enabled=False
                    )

                # 참여자 통계 생성
                participant_stats = []
                if "participant_engagement" in stats:
                    for user_id, engagement in stats["participant_engagement"].items():
                        participant_stat = ai_service_pb2.ParticipantStats(
                            message_count=stats.get("participant_message_counts", {}).get(user_id, 0),
                            last_activity=int(stats.get("last_activity_times", {}).get(user_id, 0)),
                            engagement_level=engagement
                        )
                        participant_stat.participant.user_id = user_id
                        participant_stat.participant.nickname = stats.get("participant_nicknames", {}).get(user_id, user_id)
                        participant_stats.append(participant_stat)

                return ai_service_pb2.ChatSessionStatsResponse(
                    success=True,
                    message="Session stats retrieved successfully",
                    session_id=session_id,
                    total_messages=stats.get("message_count", 0),
                    total_participants=stats.get("participant_count", 0),
                    participant_stats=participant_stats,
                    session_start_time=int(stats.get("created_at", 0)),
                    last_activity_time=int(stats.get("last_activity", 0)),
                    chat_history_enabled=stats.get("chat_history_enabled", False)
                )

            except Exception as e:
                logger.error(f"Failed to get session stats: {e}")
                return ai_service_pb2.ChatSessionStatsResponse(
                    success=False,
                    message=f"Failed to retrieve session stats: {str(e)}",
                    session_id=session_id,
                    chat_history_enabled=False
                )

        except Exception as e:
            logger.error(f"GetChatSessionStats failed: {e}")
            context.set_code(grpc.StatusCode.INTERNAL)
            context.set_details(f"Internal error: {str(e)}")
            return None
            
    # 사용자 분석 기능은 모바일 앱에서 불필요하므로 제거

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

            # QuizService를 통한 퀴즈 생성
            quiz_data = {
                "document_id": doc_id,
                "content": " ".join(content_chunks),
                "progress_percentage": progress,
                "question_count": 5,
                "difficulty_level": "medium",
                "language": "ko"
            }
            
            # QuizService 초기화 확인
            if not self.quiz_service:
                logger.warning("QuizService not initialized, using mock data")
                # Fallback to basic mock quiz
                questions = [
                    ai_service_pb2.Question(
                        question_text="문서의 주요 내용은 무엇인가요?",
                        options=["개념 A", "개념 B", "개념 C", "개념 D"],
                        correct_answer_index=0
                    )
                ]
                quiz_id = f"quiz_{uuid.uuid4().hex[:8]}"
            else:
                # 실제 QuizService를 통한 퀴즈 생성
                result = await self.quiz_service.generate_quiz(quiz_data)
                
                if result["success"]:
                    questions = []
                    for q in result["questions"]:
                        questions.append(ai_service_pb2.Question(
                            question_text=q["question_text"],
                            options=q["options"],
                            correct_answer_index=q["correct_answer_index"]
                        ))
                    quiz_id = result["quiz_id"]
                else:
                    return ai_service_pb2.QuizResponse(
                        success=False,
                        message=result.get("error", "Quiz generation failed")
                    )
            
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

            # ProofreadingService를 통한 실제 첨삭 처리
            proofread_data = {
                "original_text": original_text,
                "context_text": context_text,
                "language": request.original_text.language or "ko",
                "user_id": request.user.user_id
            }
            
            # ProofreadingService 초기화 확인
            if not self.proofreading_service:
                logger.warning("ProofreadingService not initialized, using fallback")
                # Fallback response
                return ai_service_pb2.ProofreadResponse(
                    success=True,
                    message="Proofreading service not available (using fallback)",
                    corrected_text=original_text,
                    corrections=[],
                    confidence_score=0.5
                )
            
            # 실제 ProofreadingService를 통한 첨삭 처리
            result = await self.proofreading_service.proofread_text(proofread_data)
            
            if result["success"]:
                corrections = []
                for correction in result.get("corrections", []):
                    corrections.append(ai_service_pb2.TextCorrection(
                        original=correction.get("original", ""),
                        corrected=correction.get("corrected", ""),
                        correction_type=correction.get("type", "grammar"),
                        explanation=correction.get("explanation", ""),
                        start_position=correction.get("start_position", 0),
                        end_position=correction.get("end_position", 0)
                    ))
                
                return ai_service_pb2.ProofreadResponse(
                    success=True,
                    message="Text proofreading completed",
                    corrected_text=result.get("corrected_text", original_text),
                    corrections=corrections,
                    confidence_score=result.get("confidence_score", 0.85)
                )
            else:
                return ai_service_pb2.ProofreadResponse(
                    success=False,
                    message=result.get("error", "Proofreading failed")
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
        meeting_id = None
        pdf_data_chunks = []

        try:
            async for request in request_iterator:
                if request.HasField("info"):
                    # First message should contain PdfInfo
                    document_id = request.info.document_id or str(uuid.uuid4())
                    file_name = request.info.file_name
                    meeting_id = request.info.meeting_id  # 직접 필드에서 가져오기
                    metadata = dict(request.info.metadata)
                    logger.info(f"Received PDF info for document: {document_id}, file: {file_name}, meeting: {meeting_id}")
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

            if not meeting_id:
                context.set_code(grpc.StatusCode.INVALID_ARGUMENT)
                context.set_details("meeting_id is required.")
                return ai_service_pb2.ProcessPdfResponse(success=False, message="meeting_id missing.")

            full_pdf_data = b"".join(pdf_data_chunks)

            logger.info(f"📄 Processing PDF with response for meeting {meeting_id}, document: {document_id}")

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
                logger.info(f"✅ PDF processed and stored in VectorDB: {len(chunk_ids)} chunks created for meeting {meeting_id}")
            except Exception as e:
                logger.error(f"Failed to store PDF in vector DB: {e}")
                context.set_code(grpc.StatusCode.INTERNAL)
                context.set_details(f"Failed to store in vector DB: {str(e)}")
                return ai_service_pb2.ProcessPdfResponse(
                    success=False, 
                    message=f"VectorDB storage failed: {str(e)}",
                    document_id=document_id
                )

            # 실제 OCR 블록별 응답 - 위치 정보 포함
            response_text_blocks = []
            
            # OCR 결과에서 실제 블록 정보 사용 (타입 안전성 검사 포함)
            text_blocks_data = ocr_result.get("text_blocks", [])
            if not isinstance(text_blocks_data, list):
                logger.warning(f"text_blocks is not a list: {type(text_blocks_data)}")
                text_blocks_data = []
                
            for text_block in text_blocks_data:
                if not isinstance(text_block, dict):
                    logger.warning(f"text_block is not a dict: {type(text_block)}")
                    continue
                    
                try:
                    response_text_blocks.append(ai_service_pb2.TextBlock(
                        text=str(text_block.get("text", "")),
                        page_number=int(text_block.get("page_number", 0)),
                        x0=float(text_block.get("x0", 0.0)),
                        y0=float(text_block.get("y0", 0.0)), 
                        x1=float(text_block.get("x1", 0.0)),
                        y1=float(text_block.get("y1", 0.0)),
                        block_type=str(text_block.get("block_type", "text")),
                        confidence=float(text_block.get("confidence", 0.0))
                    ))
                except (ValueError, TypeError) as e:
                    logger.warning(f"Failed to convert text_block data: {e}, block: {text_block}")
                    continue

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

    async def ProcessPdfStream(self, request_iterator, context):
        """Process PDF stream with fire-and-forget approach (no response data)"""
        document_id = None
        file_name = None
        metadata = {}
        meeting_id = None
        pdf_data_chunks = []

        try:
            async for request in request_iterator:
                if request.HasField("info"):
                    # First message should contain PdfInfo
                    document_id = request.info.document_id or str(uuid.uuid4())
                    file_name = request.info.file_name
                    meeting_id = request.info.meeting_id
                    metadata = dict(request.info.metadata)
                    logger.info(f"Received PDF info for fire-and-forget processing: {document_id}, file: {file_name}, meeting: {meeting_id}")
                elif request.HasField("chunk"):
                    # Subsequent messages contain PDF data chunks
                    pdf_data_chunks.append(request.chunk)
                else:
                    logger.warning("Received unknown message type in ProcessPdfStream stream.")

            if not document_id:
                context.set_code(grpc.StatusCode.INVALID_ARGUMENT)
                context.set_details("Document ID not provided in PdfInfo.")
                return

            if not pdf_data_chunks:
                context.set_code(grpc.StatusCode.INVALID_ARGUMENT)
                context.set_details("No PDF data chunks received.")
                return

            if not meeting_id:
                context.set_code(grpc.StatusCode.INVALID_ARGUMENT)
                context.set_details("meeting_id is required.")
                return

            full_pdf_data = b"".join(pdf_data_chunks)

            logger.info(f"📄 Processing PDF fire-and-forget for meeting {meeting_id}, document: {document_id}")

            # OCR 처리
            ocr_result = await self.ocr_service.process_pdf_stream(full_pdf_data, document_id)

            if not ocr_result["success"]:
                logger.error(f"OCR processing failed for {document_id}: {ocr_result.get('error', 'Unknown error')}")
                context.set_code(grpc.StatusCode.INTERNAL)
                context.set_details(f"OCR processing failed: {ocr_result.get('error', 'Unknown error')}")
                return

            # OCR 결과 추출 (응답용 데이터는 생성하지 않음)
            full_text = ocr_result.get("full_text", "")
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
                        "processing_type": "ocr_stream",
                        **metadata
                    }
                )
                logger.info(f"✅ PDF processed and stored in VectorDB (fire-and-forget): {len(chunk_ids)} chunks created for meeting {meeting_id}")
            except Exception as e:
                logger.error(f"Failed to store PDF in vector DB (fire-and-forget): {e}")
                context.set_code(grpc.StatusCode.INTERNAL)
                context.set_details(f"Failed to store in vector DB: {str(e)}")
                return

            # Fire-and-forget: 응답 데이터 생성하지 않고 Empty 반환
            from google.protobuf.empty_pb2 import Empty
            return Empty()

        except Exception as e:
            logger.error(f"Error in ProcessPdfStream RPC for document {document_id}: {e}")
            context.set_code(grpc.StatusCode.INTERNAL)
            context.set_details(f"Internal server error: {str(e)}")
            return

    async def cleanup(self):
        """Clean up resources when shutting down"""
        try:
            if hasattr(self, 'discussion_service'):
                await self.discussion_service.cleanup()
                logger.info("DiscussionService cleaned up")
        except Exception as e:
            logger.error(f"Error during AIServicer cleanup: {e}")
    
    # 불필요한 response builder 메서드들 제거 (퀴즈, 교정, 사용자 분석 관련)