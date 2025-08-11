"""
Discussion Service for BGBG AI Server
Handles chat moderation and discussion topic generation
"""

from typing import Dict, List, Optional, Any
from loguru import logger

from src.services.mock_discussion_service import MockDiscussionService
from src.services.llm_client import LLMClient
from src.services.vector_db import VectorDBManager
from src.services.bookclub_discussion_manager import BookClubDiscussionManager
from src.services.chat_history_manager import ChatHistoryManager
from src.models.chat_history_models import ChatMessage, MessageType
from src.config.settings import get_settings
from datetime import datetime


class DiscussionService:
    """Service for discussion AI and chat moderation"""
    
    def __init__(self):
        self.settings = get_settings()
        self.mock_service = MockDiscussionService()
        self.llm_client = LLMClient()
        self.vector_db = None  # Will be initialized later
        self.discussion_manager = None
        self.chat_history_manager = ChatHistoryManager()
        self.active_streams = {}  # 세션별 활성 스트림 관리

    async def initialize_manager(self, vector_db: VectorDBManager):
        self.vector_db = vector_db
        self.discussion_manager = BookClubDiscussionManager(vector_db)
        await self.chat_history_manager.start()
        logger.info("DiscussionService initialized with BookClubDiscussionManager and ChatHistoryManager")

    async def register_stream(self, session_id: str, context: Any):
        if session_id not in self.active_streams:
            self.active_streams[session_id] = []
        self.active_streams[session_id].append(context)
        logger.info(f"Stream registered for session {session_id}. Total streams: {len(self.active_streams[session_id])}")

    async def unregister_stream(self, session_id: str, context: Any):
        if session_id in self.active_streams and context in self.active_streams[session_id]:
            self.active_streams[session_id].remove(context)
            logger.info(f"Stream unregistered for session {session_id}. Remaining streams: {len(self.active_streams[session_id])}")
            if not self.active_streams[session_id]:
                del self.active_streams[session_id]

    async def start_discussion(
        self,
        document_id: str,
        meeting_id: str,
        session_id: str,
        participants: List[Dict[str, str]] = None
    ) -> Dict[str, Any]:
        try:
            logger.info(f"🎯 Starting discussion for document: {document_id}")
            if self.vector_db is None:
                logger.error("VectorDB not initialized.")
                return {"success": False, "message": "Vector database not initialized."}

            document_content = await self.vector_db.get_bookclub_context_for_discussion(
                meeting_id=meeting_id, query="토론 주제 생성을 위한 문서 내용", max_chunks=5
            )
            if not document_content:
                document_content = await self.vector_db.get_document_summary(document_id)
            if not document_content:
                return {"success": False, "message": "Document not found in vector database."}

            topics_result = await self.generate_discussion_topics(" ".join(document_content) if document_content else "")
            if not topics_result["success"]:
                return {"success": False, "message": "Failed to generate discussion topics."}

            if not hasattr(self, 'active_discussions'):
                self.active_discussions = {}
            self.active_discussions[session_id] = {
                "meeting_id": meeting_id,
                "document_id": document_id,
                "started_at": datetime.utcnow(),
                "participants": participants or [],
                "chatbot_active": True
            }
            self.active_streams[session_id] = []  # 스트림 리스트 초기화
            logger.info(f"✅ Discussion started for session: {session_id}")
            return {
                "success": True,
                "message": "Discussion started and topics generated.",
                "discussion_topics": topics_result["topics"],
                "recommended_topic": topics_result["topics"][0] if topics_result["topics"] else ""
            }
        except Exception as e:
            logger.error(f"Failed to start discussion: {e}")
            return {"success": False, "message": f"Discussion start failed: {str(e)}"}

    async def end_discussion(
        self,
        meeting_id: str,
        session_id: str
    ) -> Dict[str, Any]:
        try:
            # 활성 스트림 종료
            if session_id in self.active_streams:
                for context in self.active_streams[session_id]:
                    if not context.done():
                        context.cancel()
                        logger.info(f"Cancelled stream for session {session_id}")
                del self.active_streams[session_id]

            # 활성 토론에서 제거
            if hasattr(self, 'active_discussions') and session_id in self.active_discussions:
                del self.active_discussions[session_id]
                logger.info(f"✅ Discussion ended for session: {session_id}")
                return {"success": True, "message": "Discussion ended successfully"}
            else:
                return {"success": False, "message": "Discussion session not found"}
        except Exception as e:
            logger.error(f"Failed to end discussion: {e}")
            return {"success": False, "message": f"Discussion end failed: {str(e)}"}

    
    async def process_chat_message(
        self, 
        session_id: str,
        message_data: Dict[str, Any]
    ) -> Dict[str, Any]:
        """
        채팅방에서 토론 메시지 처리 - LLM이 피드백 및 참여 독려 (채팅 기록 통합)
        
        Args:
            session_id: 토론 세션 ID
            message_data: 메시지 데이터 (sender, message, timestamp)
            
        Returns:
            Dict with AI response or None if discussion not active
        """
        try:
            # 사용자 메시지를 채팅 기록에 저장
            message = message_data.get("message", "")
            sender_info = message_data.get("sender", {})
            sender_nickname = sender_info.get("nickname", "참여자")
            sender_user_id = sender_info.get("user_id", sender_nickname)
            
            # ChatMessage 객체 생성
            chat_message = ChatMessage(
                message_id="",  # auto-generated
                session_id=session_id,
                user_id=sender_user_id,
                nickname=sender_nickname,
                content=message,
                timestamp=datetime.utcnow(),
                message_type=MessageType.USER
            )
            
            # 채팅 기록에 저장
            try:
                await self.chat_history_manager.store_message(session_id, chat_message)
                logger.debug(f"Stored user message in chat history for session {session_id}")
            except Exception as e:
                logger.warning(f"Failed to store message in chat history: {e}")
            
            # 토론이 활성화되어 있는지 확인
            if not hasattr(self, 'active_discussions') or session_id not in self.active_discussions:
                return {
                    "success": True,
                    "ai_response": None,  # 토론 비활성화 상태에서는 챗봇 응답 없음
                    "requires_moderation": False
                }
            
            discussion_info = self.active_discussions[session_id]
            
            # 채팅 기록에서 최근 대화 컨텍스트 가져오기 및 AI 응답 필요성 판단
            try:
                recent_messages = await self.chat_history_manager.get_recent_messages(
                    session_id, limit=10
                )
                
                # 최근 메시지에서 사용자 메시지만 카운트 (AI 메시지 제외)
                user_messages_since_ai = []
                for msg in reversed(recent_messages):
                    if msg.message_type == MessageType.AI:
                        break  # 마지막 AI 응답 이후부터 카운트
                    if msg.message_type == MessageType.USER:
                        user_messages_since_ai.append(msg)
                
                # 참여자 수에 따른 AI 응답 대기 채팅 수 계산
                participants_count = len(discussion_info.get("participants", []))
                if participants_count <= 1:
                    required_messages = 1  # 1명 이하: 1개 메시지 후 응답
                elif participants_count <= 3:
                    required_messages = 2  # 2-3명: 2개 메시지 후 응답
                else:
                    required_messages = 3  # 4명 이상: 3개 메시지 후 응답 (최대)
                
                # AI 응답이 필요한지 판단
                should_respond = len(user_messages_since_ai) >= required_messages
                
                logger.debug(f"Participants: {participants_count}, Required messages: {required_messages}, Current user messages: {len(user_messages_since_ai)}")
                
                chat_context = "\n".join([
                    f"{msg.nickname}: {msg.content}" 
                    for msg in recent_messages[-7:]  # 최근 7개 메시지만 사용
                ])
            except Exception as e:
                logger.warning(f"Failed to get chat history context: {e}")
                chat_context = f"{sender_nickname}: {message}"
                # Redis 접근 실패 시 기본적으로 응답하지 않음
                should_respond = False
                user_messages_since_ai = []
                required_messages = 0  # Redis 실패시 기본값
            
            # 벡터DB에서 독서 모임별 문서 내용 가져와서 맥락 제공
            document_id = discussion_info["document_id"]
            meeting_id = discussion_info["meeting_id"]
            document_content = await self.vector_db.get_bookclub_context_for_discussion(
                meeting_id=meeting_id,
                query=message,
                max_chunks=3
            )
            
            # 검색된 내용을 문자열로 결합
            if document_content:
                document_content = " ".join(document_content)
            else:
                document_content = await self.vector_db.get_document_summary(document_id)
            
            # AI 응답 생성 여부 결정
            ai_response = None
            if should_respond:
                # LLM으로 토론 진행자 응답 생성 (채팅 기록 컨텍스트 포함)
                ai_response = await self.generate_discussion_response_with_context(
                    message=message,
                    sender_nickname=sender_nickname,
                    document_content=document_content,
                    chat_context=chat_context
                )
                logger.debug(f"AI response generated after {len(user_messages_since_ai)} user messages (required: {required_messages})")
            else:
                logger.debug(f"AI response skipped - only {len(user_messages_since_ai)} user messages since last AI response (required: {required_messages})")
            
            # AI 응답도 채팅 기록에 저장
            if ai_response:
                try:
                    ai_message = ChatMessage(
                        message_id="",  # auto-generated
                        session_id=session_id,
                        user_id="ai_moderator",
                        nickname="AI 토론 진행자",
                        content=ai_response,
                        timestamp=datetime.utcnow(),
                        message_type=MessageType.AI
                    )
                    await self.chat_history_manager.store_message(session_id, ai_message)
                    logger.debug(f"Stored AI response in chat history for session {session_id}")
                except Exception as e:
                    logger.warning(f"Failed to store AI response in chat history: {e}")
            
            return {
                "success": True,
                "ai_response": ai_response,
                "suggested_topics": [],
                "requires_moderation": False,
                "chat_context_used": len(recent_messages) if 'recent_messages' in locals() else 0
            }
            
        except Exception as e:
            logger.error(f"Chat message processing failed: {e}")
            return {
                "success": False,
                "ai_response": "토론 진행자 응답 생성 중 오류가 발생했습니다.",
                "error": str(e)
            }

    async def process_bookclub_chat_message_stream(
        self,
        meeting_id: str,
        session_id: str, 
        message_data: Dict[str, Any]
    ):
        """
        Process book club chat message with streaming response (채팅 기록 통합)
        독서 모임별 토론 메시지 스트리밍 처리
        
        Args:
            meeting_id: 독서 모임 ID
            session_id: 토론 세션 ID
            message_data: 메시지 데이터
            
        Yields:
            str: Streaming AI response chunks
        """
        try:
            # 사용자 메시지를 채팅 기록에 저장
            message = message_data.get("message", "")
            sender_info = message_data.get("sender", {})
            sender_nickname = sender_info.get("nickname", "참여자")
            sender_user_id = sender_info.get("user_id", sender_nickname)
            
            # ChatMessage 객체 생성 및 저장
            chat_message = ChatMessage(
                message_id="",  # auto-generated
                session_id=session_id,
                user_id=sender_user_id,
                nickname=sender_nickname,
                content=message,
                timestamp=datetime.utcnow(),
                message_type=MessageType.USER
            )
            
            try:
                await self.chat_history_manager.store_message(session_id, chat_message)
                logger.debug(f"Stored user message in chat history for streaming session {session_id}")
            except Exception as e:
                logger.warning(f"Failed to store message in chat history: {e}")
            
            # Check if discussion is active
            if not hasattr(self, 'active_discussions') or session_id not in self.active_discussions:
                yield "토론이 활성화되지 않았습니다. 토론을 먼저 시작해주세요."
                return
            
            # 토론 세션 정보 확인
            discussion_info = self.active_discussions[session_id]
            if not discussion_info.get("chatbot_active", True):
                yield "AI 토론 진행자가 비활성화되었습니다."
                return
            
            # 채팅 기록에서 최근 대화 컨텍스트 가져오기 및 AI 응답 필요성 판단
            try:
                recent_messages = await self.chat_history_manager.get_recent_messages(
                    session_id, limit=10
                )
                
                # 최근 메시지에서 사용자 메시지만 카운트 (AI 메시지 제외)
                user_messages_since_ai = []
                for msg in reversed(recent_messages):
                    if msg.message_type == MessageType.AI:
                        break  # 마지막 AI 응답 이후부터 카운트
                    if msg.message_type == MessageType.USER:
                        user_messages_since_ai.append(msg)
                
                # 참여자 수에 따른 AI 응답 대기 채팅 수 계산
                participants_count = len(discussion_info.get("participants", []))
                if participants_count <= 1:
                    required_messages = 1  # 1명 이하: 1개 메시지 후 응답
                elif participants_count <= 3:
                    required_messages = 2  # 2-3명: 2개 메시지 후 응답
                else:
                    required_messages = 3  # 4명 이상: 3개 메시지 후 응답 (최대)
                
                # AI 응답이 필요한지 판단
                should_respond = len(user_messages_since_ai) >= required_messages
                
                logger.debug(f"Streaming - Participants: {participants_count}, Required messages: {required_messages}, Current user messages: {len(user_messages_since_ai)}")
                
                chat_context_chunks = [
                    f"{msg.nickname}: {msg.content}" 
                    for msg in recent_messages[-5:]  # 최근 5개 메시지만 사용
                ]
            except Exception as e:
                logger.warning(f"Failed to get chat history context for streaming: {e}")
                chat_context_chunks = [f"{sender_nickname}: {message}"]
                # Redis 접근 실패 시 기본적으로 응답하지 않음
                should_respond = False
                user_messages_since_ai = []
                required_messages = 0  # Redis 실패시 기본값
            
            # Get book material context from VectorDB
            context_chunks = []
            if self.vector_db:
                try:
                    context_chunks = await self.vector_db.get_bookclub_context_for_discussion(
                        meeting_id=meeting_id,
                        query=message,
                        max_chunks=3
                    )
                except Exception as e:
                    logger.warning(f"Failed to get book context: {e}")
                    context_chunks = []
            
            # AI 응답이 필요한 경우에만 스트리밍 응답 생성
            if not should_respond:
                logger.debug(f"AI streaming response skipped - only {len(user_messages_since_ai)} user messages since last AI response (required: {required_messages})")
                return
            
            logger.debug(f"AI streaming response generated after {len(user_messages_since_ai)} user messages (required: {required_messages})")
            
            # Generate streaming response with book context and chat history
            if self.settings.ai.MOCK_AI_RESPONSES:
                mock_response = await self.mock_service.process_chat_message(message_data)
                yield mock_response.get("ai_response", "Mock 토론 진행자 응답입니다.")
            else:
                # AI 응답을 수집하여 채팅 기록에 저장
                ai_response_chunks = []
                async for chunk in self._process_with_bookclub_llm_stream_with_context(
                    message_data, context_chunks, chat_context_chunks
                ):
                    ai_response_chunks.append(chunk)
                    yield chunk
                
                # 완성된 AI 응답을 채팅 기록에 저장
                if ai_response_chunks:
                    try:
                        full_ai_response = "".join(ai_response_chunks)
                        ai_message = ChatMessage(
                            message_id="",  # auto-generated
                            session_id=session_id,
                            user_id="ai_moderator",
                            nickname="AI 토론 진행자",
                            content=full_ai_response,
                            timestamp=datetime.utcnow(),
                            message_type=MessageType.AI
                        )
                        await self.chat_history_manager.store_message(session_id, ai_message)
                        logger.debug(f"Stored AI streaming response in chat history for session {session_id}")
                    except Exception as e:
                        logger.warning(f"Failed to store AI streaming response in chat history: {e}")
                    
        except Exception as e:
            logger.error(f"Book club chat streaming failed: {e}")
            yield "AI 토론 진행자 응답 생성 중 오류가 발생했습니다."


    
    async def generate_discussion_topics(self, document_content: str) -> Dict[str, Any]:
        """벡터DB 문서 내용을 기반으로 토론 주제 생성"""
        try:
            if not document_content:
                return {"success": False, "error": "Document content is empty"}

            # LLM 클라이언트 초기화 확인 (GMS API용)
            if not hasattr(self.llm_client, 'gms_client') or not self.llm_client.gms_client:
                await self.llm_client.initialize()
                logger.info("LLM Client initialized for topic generation")

            system_message = """당신은 독서 모임의 토론 진행자입니다.
제공된 문서 내용을 바탕으로, 참여자들이 흥미롭게 토론할 수 있는 주제를 제안해주세요.
각 주제는 다음 형식으로 작성해주세요:
1. [첫 번째 토론 주제]
2. [두 번째 토론 주제] 
3. [세 번째 토론 주제]"""

            prompt = f"다음 문서 내용을 바탕으로 토론 주제를 생성해주세요:\n\n{document_content[:1000]}"  # 토큰 제한

            # LLM 호출 (GMS API 사용)
            from src.services.llm_client import LLMProvider
            response = await self.llm_client.generate_completion(
                prompt=prompt,
                system_message=system_message,
                max_tokens=500,
                temperature=0.7,
                provider=LLMProvider.GMS
            )

            # 응답에서 주제 추출
            topics = []
            for line in response.strip().split('\n'):
                if line.strip() and (line.strip().startswith(('1.', '2.', '3.')) or len(topics) < 3):
                    topic = line.strip()
                    if '. ' in topic:
                        topic = topic.split('. ', 1)[1]
                    topics.append(topic)
            
            if not topics:
                # 기본 주제 제공
                topics = [
                    "이 문서에서 가장 인상 깊었던 부분은 무엇인가요?",
                    "작가의 주장에 대해 어떻게 생각하시나요?",
                    "이 내용을 실생활에 어떻게 적용할 수 있을까요?"
                ]

            return {
                "success": True,
                "topics": topics[:3]  # 최대 3개
            }

        except Exception as e:
            logger.error(f"Topic generation failed: {e}")
            return {"success": False, "error": str(e)}

    async def generate_discussion_response(self, message: str, sender_nickname: str, document_content: str) -> str:
        """채팅 메시지에 대한 토론 진행자 응답 생성"""
        try:
            # LLM 클라이언트 초기화 확인 (GMS API용)
            if not hasattr(self.llm_client, 'gms_client') or not self.llm_client.gms_client:
                await self.llm_client.initialize()

            system_message = """당신은 독서 모임의 친근한 AI 토론 진행자입니다.
참여자의 의견에 공감하고, 토론을 활성화하는 응답을 해주세요.

응답 지침:
1. 최근 대화 맥락을 고려하여 자연스럽게 응답
2. 참여자의 의견에 구체적으로 공감하고 인정
3. 독서 내용과 연결된 새로운 관점이나 질문 제시
4. 다른 참여자들의 참여를 자연스럽게 유도
5. 150자 내외로 간결하면서도 의미있게 작성
6. 친근하고 격려하는 톤 유지
7. 대화가 반복되지 않도록 새로운 각도에서 접근
8. **중요**: 매 메시지마다 응답하지 말고, 참여자들의 메시지가 2-3개 쌓인 후에만 의미있는 피드백 제공. 너무 적극적으로 개입하지 말 것
9. 최근 대화가 2~3개 이하로 짧다면, 대화 시작을 돕는 배경/오픈 질문을 포함하고, 아직 참여하지 않은 분들을 부드럽게 초대
10. 최근 10개 내 참여 빈도가 낮은 사람(메시지 1회 이하)이 있다면, 이름을 직접 거론하지 않고 모두에게 참여를 권유하는 일반 메시지를 덧붙이세요
11. 위의 10개의 규칙을 절대로 위배하지 않기"""

            prompt = f"""문서 내용: {document_content[:500] if document_content else "문서 내용 없음"}

{sender_nickname}님이 말했습니다: "{message}"

위 발언에 대해 토론 진행자로서 응답해주세요."""

            # GMS API를 사용한 토론 진행자 응답 생성
            from src.services.llm_client import LLMProvider
            response = await self.llm_client.generate_completion(
                prompt=prompt,
                system_message=system_message,
                max_tokens=500,
                temperature=0.8,
                provider=LLMProvider.GMS
            )

            return response.strip()

        except Exception as e:
            logger.error(f"Discussion response generation failed: {e}")
            return f"{sender_nickname}님의 의견 잘 들었습니다. 다른 분들은 어떻게 생각하시나요?"
    
    async def generate_discussion_response_with_context(
        self, 
        message: str, 
        sender_nickname: str, 
        document_content: str,
        chat_context: str
    ) -> str:
        """채팅 기록 컨텍스트를 포함한 토론 진행자 응답 생성"""
        try:
            # LLM 클라이언트 초기화 확인 (GMS API용)
            if not hasattr(self.llm_client, 'gms_client') or not self.llm_client.gms_client:
                await self.llm_client.initialize()

            system_message = """당신은 독서 모임의 전문 AI 토론 진행자입니다.
최근 대화 흐름을 파악하고, 참여자의 의견에 맞춤형 응답을 해주세요.

응답 지침:
1. 최근 대화 맥락을 고려하여 자연스럽게 응답
2. 참여자의 의견에 구체적으로 공감하고 인정
3. 독서 내용과 연결된 새로운 관점이나 질문 제시
4. 다른 참여자들의 참여를 자연스럽게 유도
5. 150자 내외로 간결하면서도 의미있게 작성
6. 친근하고 격려하는 톤 유지
7. 대화가 반복되지 않도록 새로운 각도에서 접근
8. **중요**: 매 메시지마다 응답하지 말고, 참여자들의 메시지가 2-3개 쌓인 후에만 의미있는 피드백 제공. 너무 적극적으로 개입하지 말 것
9. 최근 대화가 2~3개 이하로 짧다면, 대화 시작을 돕는 배경/오픈 질문을 포함하고, 아직 참여하지 않은 분들을 부드럽게 초대
10. 최근 10개 내 참여 빈도가 낮은 사람(메시지 1회 이하)이 있다면, 이름을 직접 거론하지 않고 모두에게 참여를 권유하는 일반 메시지를 덧붙이세요
11. 위의 10개의 규칙을 절대로 위배하지 않기"""

            prompt = f"""독서 자료 내용:
{document_content[:500] if document_content else "독서 자료 내용 없음"}

최근 대화 흐름:
{chat_context}

현재 상황:
{sender_nickname}님이 방금 말했습니다: "{message}"

위 대화 맥락을 고려하여 토론 진행자로서 자연스럽고 의미있는 응답을 해주세요."""

            # GMS API를 사용한 컨텍스트 기반 토론 진행자 응답 생성
            from src.services.llm_client import LLMProvider
            response = await self.llm_client.generate_completion(
                prompt=prompt,
                system_message=system_message,
                max_tokens=500,
                temperature=0.8,
                provider=LLMProvider.GMS
            )

            return response.strip()

        except Exception as e:
            logger.error(f"Context-aware discussion response generation failed: {e}")
            # Fallback to basic response
            return await self.generate_discussion_response(message, sender_nickname, document_content)

    

    
    async def _process_with_bookclub_llm_stream_with_context(
        self, 
        message_data: Dict[str, Any], 
        context_chunks: List[str],
        chat_context_chunks: List[str]
    ):
        """
        Process message with book club context and chat history using streaming LLM
        독서 자료 컨텍스트와 채팅 기록을 활용한 스트리밍 토론 진행자 응답
        
        Args:
            message_data: 사용자 메시지 데이터
            context_chunks: 독서 자료 컨텍스트 청크들
            chat_context_chunks: 채팅 기록 컨텍스트 청크들
            
        Yields:
            str: Streaming AI response chunks
        """
        try:
            # LLM 클라이언트 초기화
            if not hasattr(self.llm_client, 'openrouter_client') or not self.llm_client.openrouter_client:
                await self.llm_client.initialize()
                logger.info("LLM Client initialized for enhanced book club streaming")
            
            message = message_data.get("message", "")
            sender_nickname = message_data.get("sender_nickname", "참여자")
            
            # 채팅 기록과 독서 자료를 모두 고려한 시스템 프롬프트
            system_message = """당신은 독서 모임의 전문 AI 토론 진행자입니다.
최근 대화 흐름과 독서 자료를 모두 고려하여 맞춤형 응답을 해주세요.

역할 및 지침:
1. 최근 대화 맥락을 고려하여 자연스럽게 응답
2. 참여자의 의견에 구체적으로 공감하고 인정
3. 독서 내용과 연결된 새로운 관점이나 질문 제시
4. 다른 참여자들의 참여를 자연스럽게 유도
5. 150자 내외로 간결하면서도 의미있게 작성
6. 친근하고 격려하는 톤 유지
7. 대화가 반복되지 않도록 새로운 각도에서 접근
8. **중요**: 매 메시지마다 응답하지 말고, 참여자들의 메시지가 2-3개 쌓인 후에만 의미있는 피드백 제공. 너무 적극적으로 개입하지 말 것
9. 최근 대화가 2~3개 이하로 짧다면, 대화 시작을 돕는 배경/오픈 질문을 포함하고, 아직 참여하지 않은 분들을 부드럽게 초대
10. 최근 10개 내 참여 빈도가 낮은 사람(메시지 1회 이하)이 있다면, 이름을 직접 거론하지 않고 모두에게 참여를 권유하는 일반 메시지를 덧붙이세요
11. 위의 10개의 규칙을 절대로 위배하지 않기"""
            
            # 프롬프트 구성
            book_context_text = "\n\n".join(context_chunks) if context_chunks else "독서 자료 내용 없음"
            chat_context_text = "\n".join(chat_context_chunks) if chat_context_chunks else f"{sender_nickname}: {message}"
            
            prompt = f"""독서 자료 내용:
{book_context_text}

최근 대화 흐름:
{chat_context_text}

현재 상황:
{sender_nickname}님이 방금 말했습니다: "{message}"

위 맥락을 모두 고려하여 토론 진행자로서 자연스럽고 의미있는 응답을 해주세요."""
            
            # GMS API 스트리밍 호출
            from src.services.llm_client import LLMProvider
            async for chunk in self.llm_client.generate_completion_stream(
                prompt=prompt,
                system_message=system_message,
                max_tokens=500,
                temperature=0.8,
                provider=LLMProvider.GMS
            ):
                yield chunk
                
        except Exception as e:
            logger.error(f"Enhanced book club LLM streaming failed: {e}")
            # Fallback to simple message
            sender_nickname = message_data.get('sender_nickname', '참여자')
            if context_chunks and len(context_chunks) > 0:
                yield f"{sender_nickname}님의 의견 잘 들었습니다. 독서 자료와 관련해서 더 자세히 이야기해볼까요?"
            else:
                yield f"{sender_nickname}님의 생각에 공감합니다. 다른 분들은 어떻게 생각하시나요?"

    
    async def cleanup(self):
        """Clean up resources when shutting down"""
        try:
            if hasattr(self, 'chat_history_manager'):
                await self.chat_history_manager.stop()
                logger.info("ChatHistoryManager stopped")
        except Exception as e:
            logger.error(f"Error during DiscussionService cleanup: {e}")
    
    async def get_chat_history_stats(self, session_id: str) -> Dict[str, Any]:
        """
        Get chat history statistics for a session
        
        Args:
            session_id: Session identifier
            
        Returns:
            Dict[str, Any]: Chat history statistics
        """
        try:
            return await self.chat_history_manager.get_session_stats(session_id)
        except Exception as e:
            logger.error(f"Failed to get chat history stats: {e}")
            return {"session_id": session_id, "error": str(e)}

    async def cleanup_meeting_discussions(self, meeting_id: str) -> Dict[str, Any]:
        """
        특정 미팅과 관련된 모든 토론 데이터 삭제
        
        Args:
            meeting_id: 삭제할 미팅 ID
            
        Returns:
            Dict with cleanup result
        """
        try:
            logger.info(f"Starting discussion cleanup for meeting: {meeting_id}")
            
            cleanup_results = {
                "active_discussions_cleaned": 0,
                "active_streams_cleaned": 0,
                "chat_history_sessions_cleaned": 0
            }
            
            # 1. active_discussions에서 해당 미팅 삭제
            if hasattr(self, 'active_discussions'):
                sessions_to_remove = []
                for session_id, discussion in self.active_discussions.items():
                    if discussion.get("meeting_id") == meeting_id:
                        sessions_to_remove.append(session_id)
                
                for session_id in sessions_to_remove:
                    del self.active_discussions[session_id]
                    cleanup_results["active_discussions_cleaned"] += 1
                    logger.debug(f"Removed active discussion for session: {session_id}")
            
            # 2. active_streams에서 해당 미팅의 스트림들 정리
            sessions_to_remove = []
            for session_id, streams in self.active_streams.items():
                # 세션 ID를 통해 미팅과 연관된 스트림인지 확인
                # (세션별로 미팅 정보를 직접 확인할 수 없으므로, active_discussions 정보 활용)
                if hasattr(self, 'active_discussions'):
                    # 이미 삭제된 토론 세션의 스트림들 정리
                    if session_id not in self.active_discussions:
                        sessions_to_remove.append(session_id)
                
                # 활성 스트림들 취소
                for context in streams:
                    if not context.done():
                        context.cancel()
                        logger.debug(f"Cancelled stream for session {session_id}")
            
            for session_id in sessions_to_remove:
                if session_id in self.active_streams:
                    cleanup_results["active_streams_cleaned"] += len(self.active_streams[session_id])
                    del self.active_streams[session_id]
                    logger.debug(f"Removed active streams for session: {session_id}")
            
            # 3. 채팅 히스토리 정리 (미팅 ID로 연관된 세션들)
            if self.chat_history_manager:
                try:
                    # 모든 세션에서 해당 미팅과 관련된 세션들 찾아서 정리
                    # (실제 구현에서는 chat_history_manager에 미팅별 정리 메소드가 필요할 수 있음)
                    
                    # 현재는 active_discussions에서 찾은 세션들만 정리
                    for session_id in sessions_to_remove:
                        try:
                            # 세션 데이터 정리 - ChatHistoryManager에 메소드가 있는지 확인
                            if hasattr(self.chat_history_manager, 'clear_session_history'):
                                result = await self.chat_history_manager.clear_session_history(session_id)
                                if result:
                                    cleanup_results["chat_history_sessions_cleaned"] += 1
                                    logger.debug(f"Cleared chat history for session: {session_id}")
                            elif hasattr(self.chat_history_manager, 'delete_session_messages'):
                                # 대체 메소드 시도
                                result = await self.chat_history_manager.delete_session_messages(session_id)
                                if result:
                                    cleanup_results["chat_history_sessions_cleaned"] += 1
                                    logger.debug(f"Deleted session messages for session: {session_id}")
                            else:
                                logger.debug(f"No cleanup method available for chat history in session: {session_id}")
                        except Exception as e:
                            logger.warning(f"Failed to clear chat history for session {session_id}: {e}")
                except Exception as e:
                    logger.warning(f"Chat history cleanup failed: {e}")
            
            total_cleaned = sum(cleanup_results.values())
            logger.info(f"✅ Discussion cleanup completed for meeting {meeting_id}: "
                       f"{cleanup_results['active_discussions_cleaned']} discussions, "
                       f"{cleanup_results['active_streams_cleaned']} streams, "
                       f"{cleanup_results['chat_history_sessions_cleaned']} chat sessions")
            
            return {
                "success": True,
                "meeting_id": meeting_id,
                "cleanup_results": cleanup_results,
                "total_cleaned": total_cleaned,
                "message": f"Successfully cleaned up {total_cleaned} discussion items"
            }
            
        except Exception as e:
            logger.error(f"Discussion cleanup failed for meeting {meeting_id}: {e}")
            return {
                "success": False,
                "meeting_id": meeting_id,
                "error": str(e),
                "message": f"Discussion cleanup failed: {str(e)}"
            }