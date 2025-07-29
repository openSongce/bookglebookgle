"""
Discussion Service for BGBG AI Server
Handles chat moderation and discussion topic generation
"""

from typing import Dict, List, Optional, Any
from loguru import logger

from src.services.mock_discussion_service import MockDiscussionService
from src.services.llm_client import LLMClient
from src.services.vector_db import VectorDBManager
from src.config.settings import get_settings


class DiscussionService:
    """Service for discussion AI and chat moderation"""
    
    def __init__(self):
        self.settings = get_settings()
        self.mock_service = MockDiscussionService()
        self.llm_client = LLMClient()
        self.vector_db = None  # Will be initialized later
    
    async def initialize_discussion(self, init_data: Dict[str, Any]) -> Dict[str, Any]:
        """Initialize discussion session"""
        return await self.mock_service.initialize_discussion(init_data)
    
    async def process_chat_message(self, message_data: Dict[str, Any]) -> Dict[str, Any]:
        """Process chat message and generate AI response"""
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
    
    async def generate_topics(self, topic_data: Dict[str, Any]) -> Dict[str, Any]:
        """Generate discussion topics from document"""
        return await self.mock_service.generate_topics(topic_data)
    
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