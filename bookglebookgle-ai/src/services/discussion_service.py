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
from src.config.settings import get_settings
from datetime import datetime


class DiscussionService:
    """Service for discussion AI and chat moderation"""
    
    def __init__(self):
        self.settings = get_settings()
        self.mock_service = MockDiscussionService()
        self.llm_client = LLMClient()
        self.vector_db = None  # Will be initialized later
        self.discussion_manager = None  # Will be initialized later
    
    def initialize_manager(self, vector_db: VectorDBManager):
        """Initialize discussion manager with vector DB"""
        self.vector_db = vector_db
        self.discussion_manager = BookClubDiscussionManager(vector_db)
        logger.info("DiscussionService initialized with BookClubDiscussionManager")
    
    async def start_discussion(
        self, 
        document_id: str,
        meeting_id: str,
        session_id: str, 
        participants: List[Dict[str, str]] = None
    ) -> Dict[str, Any]:
        """
        채팅방 기반 토론 시작 - LLM이 진행자가 되어 토론 주제 생성
        
        Args:
            document_id: 토론할 문서 ID
            meeting_id: 독서 모임 ID  
            session_id: 토론 세션 ID
            participants: 참가자 목록
            
        Returns:
            Dict with success status and discussion topics
        """
        try:
            logger.info(f"🎯 Starting discussion for document: {document_id}")
            
            # 1. 벡터DB에서 독서 모임별 문서 내용 가져오기
            document_content = await self.vector_db.get_bookclub_context_for_discussion(
                meeting_id=meeting_id,
                query="토론 주제 생성을 위한 문서 내용",
                max_chunks=5
            )
            
            # 검색 결과가 있으면 결합, 없으면 기본 검색 시도
            if document_content:
                document_content = " ".join(document_content)
            else:
                document_content = await self.vector_db.get_document_summary(document_id)
            
            if not document_content:
                return {
                    "success": False,
                    "message": "Document not found in vector database",
                    "discussion_topics": [],
                    "recommended_topic": ""
                }

            # 2. LLM을 사용해 토론 주제 생성
            topics_result = await self.generate_discussion_topics(document_content)
            
            if not topics_result["success"]:
                return {
                    "success": False,
                    "message": "Failed to generate discussion topics",
                    "discussion_topics": [],
                    "recommended_topic": ""
                }

            # 3. 토론 세션 활성화 (메모리에 저장)
            if not hasattr(self, 'active_discussions'):
                self.active_discussions = {}
                
            self.active_discussions[session_id] = {
                "meeting_id": meeting_id,
                "document_id": document_id,
                "started_at": datetime.utcnow(),
                "participants": participants or [],
                "chatbot_active": True
            }
            
            logger.info(f"✅ Discussion started for session: {session_id}")
            
            return {
                "success": True,
                "message": "Discussion started and topics generated.",
                "discussion_topics": topics_result["topics"],
                "recommended_topic": topics_result["topics"][0] if topics_result["topics"] else ""
            }
            
        except Exception as e:
            logger.error(f"Failed to start discussion: {e}")
            return {
                "success": False,
                "message": f"Discussion start failed: {str(e)}",
                "discussion_topics": [],
                "recommended_topic": ""
            }

    async def end_discussion(
        self, 
        meeting_id: str, 
        session_id: str
    ) -> Dict[str, Any]:
        """
        채팅방 토론 종료 - 챗봇 비활성화
        
        Args:
            meeting_id: 독서 모임 ID
            session_id: 토론 세션 ID
            
        Returns:
            Dict with success status
        """
        try:
            # 활성 토론에서 제거
            if hasattr(self, 'active_discussions') and session_id in self.active_discussions:
                del self.active_discussions[session_id]
                logger.info(f"✅ Discussion ended for session: {session_id}")
                
                return {
                    "success": True,
                    "message": "Discussion ended successfully"
                }
            else:
                return {
                    "success": False,
                    "message": "Discussion session not found"
                }
            
        except Exception as e:
            logger.error(f"Failed to end discussion: {e}")
            return {
                "success": False,
                "message": f"Discussion end failed: {str(e)}"
            }

    async def initialize_discussion(self, init_data: Dict[str, Any]) -> Dict[str, Any]:
        """Initialize discussion session (legacy method)"""
        return await self.mock_service.initialize_discussion(init_data)
    
    async def process_chat_message(
        self, 
        session_id: str,
        message_data: Dict[str, Any]
    ) -> Dict[str, Any]:
        """
        채팅방에서 토론 메시지 처리 - LLM이 피드백 및 참여 독려
        
        Args:
            session_id: 토론 세션 ID
            message_data: 메시지 데이터 (sender, message, timestamp)
            
        Returns:
            Dict with AI response or None if discussion not active
        """
        try:
            # 토론이 활성화되어 있는지 확인
            if not hasattr(self, 'active_discussions') or session_id not in self.active_discussions:
                return {
                    "success": True,
                    "ai_response": None,  # 토론 비활성화 상태에서는 챗봇 응답 없음
                    "requires_moderation": False
                }
            
            discussion_info = self.active_discussions[session_id]
            message = message_data.get("message", "")
            sender_nickname = message_data.get("sender", {}).get("nickname", "참여자")
            
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
            
            # LLM으로 토론 진행자 응답 생성
            ai_response = await self.generate_discussion_response(
                message=message,
                sender_nickname=sender_nickname,
                document_content=document_content
            )
            
            return {
                "success": True,
                "ai_response": ai_response,
                "suggested_topics": [],
                "requires_moderation": False
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
        Process book club chat message with streaming response
        독서 모임별 토론 메시지 스트리밍 처리
        
        Args:
            meeting_id: 독서 모임 ID
            session_id: 토론 세션 ID
            message_data: 메시지 데이터
            
        Yields:
            str: Streaming AI response chunks
        """
        try:
            # Check if discussion is active and chatbot is enabled
            if not self.discussion_manager or not self.discussion_manager.is_chatbot_active(meeting_id, session_id):
                yield "토론이 활성화되지 않았거나 AI 진행자가 비활성화되었습니다."
                return
            
            # Update activity
            self.discussion_manager.update_activity(meeting_id, session_id)
            
            # Get book material context
            message = message_data.get("message", "")
            context_chunks = await self.discussion_manager.get_book_material_context(
                meeting_id=meeting_id,
                query=message,
                max_chunks=3
            )
            
            # Generate streaming response with book context
            if self.settings.ai.MOCK_AI_RESPONSES:
                mock_response = await self.mock_service.process_chat_message(message_data)
                yield mock_response.get("ai_response", "Mock 토론 진행자 응답입니다.")
            else:
                async for chunk in self._process_with_bookclub_llm_stream(message_data, context_chunks):
                    yield chunk
                    
        except Exception as e:
            logger.error(f"Book club chat streaming failed: {e}")
            yield "AI 토론 진행자 응답 생성 중 오류가 발생했습니다."

    async def process_chat_message_legacy(self, message_data: Dict[str, Any]) -> Dict[str, Any]:
        """Process chat message and generate AI response (legacy method)"""
        # Mock 모드가 비활성화되었을 때 실제 LLM 사용
        if not self.settings.ai.MOCK_AI_RESPONSES:
            return await self._process_with_llm(message_data)
        else:
            return await self.mock_service.process_chat_message(message_data)

    async def process_chat_message_stream(self, message_data: Dict[str, Any]):
        """
        Process chat message and generate AI response as a stream.
        This is a new async generator method.
        """
        if self.settings.ai.MOCK_AI_RESPONSES:
            # Mock 모드일 경우, 기존 Mock 서비스를 스트림처럼 반환
            mock_response = await self.mock_service.process_chat_message(message_data)
            yield mock_response.get("ai_response", "This is a mock response.")
        else:
            # 실제 LLM 스트리밍 호출
            try:
                if not hasattr(self.llm_client, 'openrouter_client') or not self.llm_client.openrouter_client:
                    await self.llm_client.initialize()
                    logger.info("✅ LLM Client initialized in DiscussionService")

                message = message_data.get("message", "")
                sender_nickname = message_data.get("sender_nickname", "사용자")

                system_message = f"""당신은 친근하고 도움이 되는 AI 토론 진행자입니다.
사용자의 메시지에 대해 다음과 같이 응답하세요:
1. 사용자의 의견에 공감하고 인정해주세요
2. 추가적인 관점이나 질문을 제시하여 토론을 활성화하세요  
3. 한국어로 자연스럽게 대화하세요
4. 200자 내외로 간결하게 답변하세요"""

                prompt = f'{sender_nickname}님이 다음과 같이 말했습니다: "{message}"'

                # LLM의 스트리밍 API 호출
                async for chunk in self.llm_client.generate_completion_stream(
                    prompt=prompt,
                    system_message=system_message,
                    max_tokens=300,
                    temperature=0.8
                ):
                    yield chunk

            except Exception as e:
                logger.error(f"LLM stream processing failed: {e}")
                yield "AI 응답 생성 중 오류가 발생했습니다."
    
    async def generate_discussion_topics(self, document_content: str) -> Dict[str, Any]:
        """벡터DB 문서 내용을 기반으로 토론 주제 생성"""
        try:
            if not document_content:
                return {"success": False, "error": "Document content is empty"}

            # LLM 클라이언트 초기화 확인
            if not hasattr(self.llm_client, 'openrouter_client') or not self.llm_client.openrouter_client:
                await self.llm_client.initialize()
                logger.info("LLM Client initialized for topic generation")

            system_message = """당신은 독서 모임의 토론 진행자입니다.
제공된 문서 내용을 바탕으로, 참여자들이 흥미롭게 토론할 수 있는 주제 3개를 제안해주세요.
각 주제는 다음 형식으로 작성해주세요:
1. [첫 번째 토론 주제]
2. [두 번째 토론 주제] 
3. [세 번째 토론 주제]"""

            prompt = f"다음 문서 내용을 바탕으로 토론 주제를 생성해주세요:\n\n{document_content[:1000]}"  # 토큰 제한

            # LLM 호출
            response = await self.llm_client.generate_completion(
                prompt=prompt,
                system_message=system_message,
                max_tokens=300,
                temperature=0.7
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
            # LLM 클라이언트 초기화 확인
            if not hasattr(self.llm_client, 'openrouter_client') or not self.llm_client.openrouter_client:
                await self.llm_client.initialize()

            system_message = """당신은 독서 모임의 친근한 AI 토론 진행자입니다.
참여자의 의견에 공감하고, 토론을 활성화하는 응답을 해주세요.

응답 지침:
1. 참여자의 의견에 공감 표현
2. 추가 질문이나 다른 관점 제시
3. 다른 참여자들의 의견도 듣고 싶다는 표현 포함
4. 150자 내외로 간결하게 작성
5. 친근하고 격려하는 톤으로 작성"""

            prompt = f"""문서 내용: {document_content[:500] if document_content else "문서 내용 없음"}

{sender_nickname}님이 말했습니다: "{message}"

위 발언에 대해 토론 진행자로서 응답해주세요."""

            response = await self.llm_client.generate_completion(
                prompt=prompt,
                system_message=system_message,
                max_tokens=200,
                temperature=0.8
            )

            return response.strip()

        except Exception as e:
            logger.error(f"Discussion response generation failed: {e}")
            return f"{sender_nickname}님의 의견 잘 들었습니다. 다른 분들은 어떻게 생각하시나요?"

    async def generate_location_aware_topics(
        self, 
        document_id: str, 
        target_page: Optional[int] = None,
        target_section: Optional[Dict[str, float]] = None
    ) -> Dict[str, Any]:
        """특정 페이지나 위치를 기반으로 토론 주제 생성"""
        try:
            # 위치 정보를 고려한 문서 검색
            if target_page:
                document_data = await self.vector_db.get_page_content(document_id, target_page)
            elif target_section:
                # 특정 좌표 영역의 텍스트 검색
                document_data = await self.vector_db.get_section_content(
                    document_id, target_section
                )
            else:
                document_data = await self.vector_db.get_document_summary(document_id)
            
            if not document_data:
                return {"success": False, "error": "Document not found"}
            
            # 위치 정보를 포함한 프롬프트 생성
            location_context = ""
            if target_page:
                location_context = f"페이지 {target_page}의 내용을 중심으로"
            elif target_section:
                location_context = f"문서의 특정 영역({target_section})을 중심으로"
            
            prompt = f"""
            {location_context} 다음 문서 내용을 바탕으로 토론 주제를 생성해주세요.
            
            문서 내용:
            {document_data}
            
            요구사항:
            - 구체적이고 토론하기 좋은 3-5개의 주제
            - 독자들이 관심을 가질 만한 질문 형태
            - 다양한 관점에서 접근 가능한 주제
            """
            
            topics = await self.llm_client.generate_topics(prompt)
            
            return {
                "success": True,
                "topics": topics,
                "context": location_context,
                "source_location": {
                    "page": target_page,
                    "section": target_section
                }
            }
            
        except Exception as e:
            logger.error(f"Failed to generate location-aware topics: {e}")
            return {"success": False, "error": str(e)}
    
    async def _process_with_bookclub_llm(self, message_data: Dict[str, Any], context_chunks: List[str]) -> Dict[str, Any]:
        """
        Process message with book club context using LLM
        독서 자료 컨텍스트를 활용한 토론 진행자 응답 생성
        
        Args:
            message_data: 사용자 메시지 데이터
            context_chunks: 독서 자료 컨텍스트 청크들
            
        Returns:
            Dict with AI response based on book content
        """
        try:
            # LLM 클라이언트 초기화
            if not hasattr(self.llm_client, 'openrouter_client') or not self.llm_client.openrouter_client:
                await self.llm_client.initialize()
                logger.info("LLM Client initialized for book club discussion")
            
            message = message_data.get("message", "")
            sender_nickname = message_data.get("sender_nickname", "참여자")
            
            # 독서 자료 컨텍스트가 있는 경우와 없는 경우 구분
            if context_chunks and len(context_chunks) > 0:
                # 독서 자료 기반 토론 진행자 시스템 프롬프트
                system_message = """당신은 독서 모임의 전문 AI 토론 진행자입니다.

역할 및 지침:
1. 제공된 독서 자료 내용을 바탕으로 토론을 활성화하세요
2. 참여자의 의견에 공감하고 격려하며 인정해주세요
3. 독서 내용과 연결된 새로운 관점이나 질문을 제시하세요
4. 다른 참여자들의 참여를 유도하는 질문을 포함하세요
5. 한국어로 자연스럽고 친근하게 대화하세요
6. 200자 내외로 간결하게 답변하세요
7. 독서 자료의 구체적인 내용을 인용하거나 언급할 수 있습니다"""
                
                # 독서 자료 컨텍스트를 포함한 프롬프트
                context_text = "\n\n".join(context_chunks)
                prompt = f"""독서 자료 내용:
{context_text}

토론 상황:
{sender_nickname}님이 다음과 같이 말했습니다: "{message}"

위 독서 자료 내용을 참고하여 토론 진행자로서 응답해주세요."""
            else:
                # 독서 자료가 없는 경우 일반 토론 진행자 모드
                system_message = """당신은 독서 모임의 친근한 AI 토론 진행자입니다.

역할 및 지침:
1. 참여자의 의견에 공감하고 격려해주세요
2. 독서와 관련된 추가적인 관점이나 질문을 제시하세요
3. 다른 참여자들의 참여를 유도하세요
4. 한국어로 자연스럽게 대화하세요
5. 200자 내외로 간결하게 답변하세요"""
                
                prompt = f"{sender_nickname}님이 다음과 같이 말했습니다: \"{message}\""
            
            # LLM 호출
            ai_response = await self.llm_client.generate_completion(
                prompt=prompt,
                system_message=system_message,
                max_tokens=300,
                temperature=0.8
            )
            
            return {
                "success": True,
                "message": "Message processed with book club context",
                "ai_response": ai_response,
                "context_used": len(context_chunks) > 0,
                "context_chunks_count": len(context_chunks),
                "suggested_topics": [],
                "requires_moderation": False
            }
            
        except Exception as e:
            logger.error(f"Book club LLM processing failed: {e}")
            # 실패 시 Mock 서비스로 fallback
            fallback_response = await self.mock_service.process_chat_message(message_data)
            fallback_response["context_used"] = len(context_chunks) > 0
            fallback_response["fallback_used"] = True
            return fallback_response

    async def _process_with_bookclub_llm_stream(self, message_data: Dict[str, Any], context_chunks: List[str]):
        """
        Process message with book club context using streaming LLM
        독서 자료 컨텍스트를 활용한 스트리밍 토론 진행자 응답
        
        Args:
            message_data: 사용자 메시지 데이터
            context_chunks: 독서 자료 컨텍스트 청크들
            
        Yields:
            str: Streaming AI response chunks
        """
        try:
            # LLM 클라이언트 초기화
            if not hasattr(self.llm_client, 'openrouter_client') or not self.llm_client.openrouter_client:
                await self.llm_client.initialize()
                logger.info("LLM Client initialized for book club streaming")
            
            message = message_data.get("message", "")
            sender_nickname = message_data.get("sender_nickname", "참여자")
            
            # 독서 자료 컨텍스트 기반 시스템 프롬프트
            if context_chunks and len(context_chunks) > 0:
                system_message = """당신은 독서 모임의 전문 AI 토론 진행자입니다.

역할 및 지침:
1. 제공된 독서 자료 내용을 바탕으로 토론을 활성화하세요
2. 참여자의 의견에 공감하고 격려하며 인정해주세요
3. 독서 내용과 연결된 새로운 관점이나 질문을 제시하세요
4. 다른 참여자들의 참여를 유도하는 질문을 포함하세요
5. 한국어로 자연스럽고 친근하게 대화하세요
6. 200자 내외로 간결하게 답변하세요
7. 독서 자료의 구체적인 내용을 인용하거나 언급할 수 있습니다"""
                
                context_text = "\n\n".join(context_chunks)
                prompt = f"""독서 자료 내용:
{context_text}

토론 상황:
{sender_nickname}님이 다음과 같이 말했습니다: "{message}"

위 독서 자료 내용을 참고하여 토론 진행자로서 응답해주세요."""
            else:
                system_message = """당신은 독서 모임의 친근한 AI 토론 진행자입니다.

역할 및 지침:
1. 참여자의 의견에 공감하고 격려해주세요
2. 독서와 관련된 추가적인 관점이나 질문을 제시하세요
3. 다른 참여자들의 참여를 유도하세요
4. 한국어로 자연스럽게 대화하세요
5. 200자 내외로 간결하게 답변하세요"""
                
                prompt = f"{sender_nickname}님이 다음과 같이 말했습니다: \"{message}\""
            
            # LLM 스트리밍 호출
            async for chunk in self.llm_client.generate_completion_stream(
                prompt=prompt,
                system_message=system_message,
                max_tokens=300,
                temperature=0.8
            ):
                yield chunk
                
        except Exception as e:
            logger.error(f"Book club LLM streaming failed: {e}")
            # 실패 시 간단한 fallback 메시지
            if context_chunks and len(context_chunks) > 0:
                yield f"{message_data.get('sender_nickname', '')}님의 의견 잘 들었습니다. 독서 자료와 관련해서 더 자세히 이야기해볼까요?"
            else:
                yield f"{message_data.get('sender_nickname', '')}님의 생각에 공감합니다. 다른 분들은 어떻게 생각하시나요?"

    async def _process_with_llm(self, message_data: Dict[str, Any]) -> Dict[str, Any]:
        """실제 LLM을 사용한 채팅 처리"""
        try:
            # LLM 클라이언트 초기화 (첫 호출 시)
            if not hasattr(self.llm_client, 'openrouter_client') or not self.llm_client.openrouter_client:
                await self.llm_client.initialize()
                logger.info("✅ LLM Client initialized in DiscussionService")
            
            message = message_data.get("message", "")
            sender_nickname = message_data.get("sender_nickname", "사용자")
            
            # 시스템 메시지 설정
            system_message = f"""당신은 친근하고 도움이 되는 AI 토론 진행자입니다.
사용자의 메시지에 대해 다음과 같이 응답하세요:
1. 사용자의 의견에 공감하고 인정해주세요
2. 추가적인 관점이나 질문을 제시하여 토론을 활성화하세요  
3. 한국어로 자연스럽게 대화하세요
4. 200자 내외로 간결하게 답변하세요"""
            
            # LLM 호출
            ai_response = await self.llm_client.generate_completion(
                prompt=f"{sender_nickname}님이 다음과 같이 말했습니다: \"{message}\"",
                system_message=system_message,
                max_tokens=300,
                temperature=0.8
            )
            
            return {
                "success": True,
                "message": "Message processed successfully",
                "ai_response": ai_response,
                "suggested_topics": [],
                "requires_moderation": False
            }
            
        except Exception as e:
            logger.error(f"LLM processing failed: {e}")
            # 실패 시 Mock 서비스로 fallback
            return await self.mock_service.process_chat_message(message_data)