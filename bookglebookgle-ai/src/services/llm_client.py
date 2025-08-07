"""
LLM Client for BGBG AI Server
Handles communication with GMS API (SSAFY Anthropic proxy)
"""

import asyncio
import httpx
from typing import Dict, List, Optional, Any
from enum import Enum
from loguru import logger

from src.config.settings import get_settings


class LLMProvider(Enum):
    GMS = "gms"
    MOCK = "mock"


class LLMClient:
    """Client for interacting with GMS API"""
    
    def __init__(self):
        self.settings = get_settings()
        self.gms_available = False  

    def _is_valid_api_key(self, api_key: Optional[str]) -> bool:
        """Check if API key is valid (not empty or placeholder)"""
        if not api_key:
            return False
        
        # Check for common placeholder patterns
        placeholder_patterns = [
            "YOUR_", "ENTER_", "INSERT_", "ADD_", "REPLACE_",
            "_HERE", "_KEY_HERE", "sk-...", "sk-xxx", "your-", "placeholder"
        ]
        
        api_key_upper = api_key.upper()
        for pattern in placeholder_patterns:
            if pattern in api_key_upper:
                return False
        
        # Check minimum length (most API keys are at least 20 characters)
        # Exception for Gemini keys which can be shorter but valid
        if api_key.startswith("AIza"):
            return len(api_key) >= 35  # Gemini API keys are typically 39 characters
        
        return len(api_key) >= 20

    async def initialize(self):
        """Initialize GMS API client"""
        try:
            logger.info("Initializing GMS API client...")
            logger.info(f"🔍 Debug - Mock Responses: {self.settings.ai.MOCK_AI_RESPONSES}")
            
            # Check GMS API key
            if self._is_valid_api_key(self.settings.ai.GMS_API_KEY):
                # Test GMS API connection
                await self._test_gms_connection()
                self.gms_available = True
                logger.info("✅ GMS API client initialized")
            else:
                logger.warning("⚠️ GMS API key not configured or invalid")
                self.gms_available = False
            
            if not self.gms_available:
                logger.warning("❌ GMS API not available - using mock responses")
                logger.info("💡 To use GMS API, configure valid GMS_API_KEY in .env file")
                
        except Exception as e:
            logger.error(f"Failed to initialize GMS API client: {e}")
            self.gms_available = False
            logger.info("❌ Falling back to mock responses")
    
    async def generate_completion(
        self,
        prompt: str,
        system_message: Optional[str] = None,
        max_tokens: int = 1000,
        temperature: float = 0.7,
        provider: Optional[LLMProvider] = None,
        model: Optional[str] = None
    ) -> str:
        """Generate text completion using GMS API"""
        
        if self.settings.ai.MOCK_AI_RESPONSES or not self.gms_available:
            return await self._mock_completion(prompt)
        
        try:
            return await self._gms_completion(prompt, system_message, max_tokens, temperature)
        except Exception as e:
            logger.error(f"GMS API completion failed: {e}")
            logger.warning("Falling back to mock completion")
            return await self._mock_completion(prompt)
    
    async def _test_gms_connection(self):
        """Test GMS API connection"""
        try:
            headers = {
                "Content-Type": "application/json",
                "x-api-key": self.settings.ai.GMS_API_KEY,
                "anthropic-version": "2023-06-01"
            }
            
            test_data = {
                "model": self.settings.ai.GMS_DEV_MODEL,
                "max_tokens": 10,
                "messages": [{"role": "user", "content": "Hi"}]
            }
            
            async with httpx.AsyncClient(timeout=10.0) as client:
                response = await client.post(
                    f"{self.settings.ai.GMS_BASE_URL}/messages",
                    headers=headers,
                    json=test_data
                )
                response.raise_for_status()
                logger.info("✅ GMS API connection test successful")
                
        except Exception as e:
            logger.error(f"❌ GMS API connection test failed: {e}")
            raise
    
    async def _gms_completion(
        self,
        prompt: str,
        system_message: Optional[str],
        max_tokens: int,
        temperature: float
    ) -> str:
        """Generate completion using GMS (SSAFY Anthropic proxy)"""
        try:
            logger.debug("🔄 Using GMS API for completion")
            
            headers = {
                "Content-Type": "application/json",
                "x-api-key": self.settings.ai.GMS_API_KEY,
                "anthropic-version": "2023-06-01"
            }
            
            # 개발/프로덕션 모델 선택
            model = self.settings.ai.GMS_DEV_MODEL if self.settings.DEBUG else self.settings.ai.GMS_PROD_MODEL
            logger.debug(f"🤖 Using GMS model: {model}")
            
            data = {
                "model": model,
                "max_tokens": max_tokens,
                "temperature": temperature,
                "messages": [{"role": "user", "content": prompt}]
            }
            
            if system_message:
                data["system"] = system_message
            
            logger.debug(f"📝 Request data: model={model}, max_tokens={max_tokens}, temperature={temperature}")
            
            async with httpx.AsyncClient(timeout=30.0) as client:
                response = await client.post(
                    f"{self.settings.ai.GMS_BASE_URL}/messages",
                    headers=headers,
                    json=data
                )
                response.raise_for_status()
                result = response.json()
                
                # Anthropic API 응답 파싱
                if "content" in result and len(result["content"]) > 0:
                    content = result["content"][0]["text"]
                    logger.info(f"✅ GMS response received (length: {len(content)} chars)")
                    return content.strip()
                else:
                    logger.error("❌ Invalid GMS API response format")
                    raise ValueError("Invalid response format from GMS API")
                
        except httpx.HTTPStatusError as e:
            logger.error(f"❌ GMS API HTTP error {e.response.status_code}: {e.response.text}")
            raise
        except httpx.TimeoutException:
            logger.error("❌ GMS API timeout")
            raise
        except Exception as e:
            logger.error(f"❌ GMS completion failed: {e}")
            raise
    
    async def _mock_completion(self, prompt: str) -> str:
        """Generate mock completion for development/testing"""
        logger.debug("Using mock LLM completion")
        
        # Simulate processing time
        await asyncio.sleep(0.5)
        
        # Generate mock response based on prompt content
        if "퀴즈" in prompt or "quiz" in prompt.lower():
            return """1. 이 문서의 주요 주제는 무엇인가요?
A) 독서의 중요성
B) AI 기술 발전
C) 교육 방법론
D) 소셜 네트워킹

정답: A) 독서의 중요성
설명: 문서 전반에 걸쳐 독서의 중요성이 강조되고 있습니다."""
        
        elif "첨삭" in prompt or "proofread" in prompt.lower():
            return """수정된 텍스트: [원본 텍스트의 개선된 버전]
주요 수정사항:
- 맞춤법 교정: 2개
- 문법 교정: 1개  
- 문체 개선: 3개"""
        
        elif "토론" in prompt or "discussion" in prompt.lower():
            return """이 주제에 대해 다양한 관점에서 생각해볼 수 있겠네요. 
특히 [구체적 포인트]에 대해서는 어떻게 생각하시나요? 
다른 참여자분들의 의견도 듣고 싶습니다."""
        
        else:
            return f"Mock response for: {prompt[:100]}..."

    async def generate_completion_stream(
        self,
        prompt: str,
        system_message: Optional[str] = None,
        max_tokens: int = 1000,
        temperature: float = 0.7,
        provider: Optional[LLMProvider] = None,
        model: Optional[str] = None
    ):
        """Generate streaming text completion using GMS API (fallback to mock)"""
        
        if self.settings.ai.MOCK_AI_RESPONSES or not self.gms_available:
            async for chunk in self._mock_completion_stream(prompt):
                yield chunk
            return
        
        try:
            # GMS API doesn't support streaming, so we'll return the complete response as a single chunk
            result = await self._gms_completion(prompt, system_message, max_tokens, temperature)
            yield result
        except Exception as e:
            logger.error(f"GMS streaming completion failed: {e}")
            logger.warning("Falling back to mock streaming completion")
            async for chunk in self._mock_completion_stream(prompt):
                yield chunk

    def _get_preferred_provider(self) -> LLMProvider:
        """Get preferred LLM provider based on availability"""
        if self.gms_available:
            return LLMProvider.GMS
        else:
            return LLMProvider.MOCK

    async def _mock_completion_stream(self, prompt: str):
        """Generate mock streaming completion for development/testing"""
        logger.debug("Using mock LLM streaming completion")
        
        # Mock response based on prompt content
        if "토론" in prompt or "discussion" in prompt.lower():
            mock_response = """그 의견 정말 흥미롭네요! 특히 말씀하신 부분에 대해서 더 자세히 들어보고 싶습니다. 다른 참여자분들은 어떻게 생각하시나요?"""
        else:
            mock_response = f"이것은 '{prompt[:50]}...'에 대한 Mock 스트리밍 응답입니다."
        
        # Simulate streaming by yielding chunks
        words = mock_response.split()
        for i in range(0, len(words), 2):  # Send 2 words at a time
            chunk = " ".join(words[i:i+2])
            if i > 0:
                chunk = " " + chunk
            yield chunk
            await asyncio.sleep(0.1)  # Simulate network delay
    


# Specialized LLM clients for different use cases
class QuizLLMClient:
    """Specialized client for quiz generation"""
    
    def __init__(self, llm_client: LLMClient):
        self.llm_client = llm_client
    
    async def generate_quiz(
        self,
        content: str,
        question_count: int = 5,
        difficulty: str = "medium",
        language: str = "ko"
    ) -> List[Dict[str, Any]]:
        """Generate quiz questions from content"""
        
        system_message = f"""당신은 교육 전문가입니다. 주어진 텍스트를 바탕으로 {difficulty} 난이도의 객관식 문제를 {question_count}개 생성해주세요.

응답 형식:
1. 문제: [질문 내용]
   A) 선택지1
   B) 선택지2  
   C) 선택지3
   D) 선택지4
   정답: [A/B/C/D]
   설명: [정답 근거]

문제는 내용의 핵심을 다루고, 이해도를 확인할 수 있도록 구성해주세요."""
        
        prompt = f"""다음 텍스트를 바탕으로 퀴즈를 생성해주세요:

{content[:2000]}...

언어: {language}
난이도: {difficulty}
문제 수: {question_count}"""
        
        response = await self.llm_client.generate_completion(
            prompt=prompt,
            system_message=system_message,
            max_tokens=1500,
            temperature=0.3
        )
        
        # Parse response into structured format
        return self._parse_quiz_response(response)
    
    def _parse_quiz_response(self, response: str) -> List[Dict[str, Any]]:
        """Parse LLM response into structured quiz format"""
        questions = []
        
        # Simple parsing logic (could be improved with more robust parsing)
        lines = response.split('\n')
        current_question = None
        options = []
        correct_answer = None
        explanation = ""
        
        for line in lines:
            line = line.strip()
            
            if line.startswith(('1.', '2.', '3.', '4.', '5.')):
                # Save previous question if exists
                if current_question and options:
                    questions.append({
                        "question": current_question,
                        "options": options,
                        "correct_answer": correct_answer,
                        "explanation": explanation
                    })
                
                # Start new question
                current_question = line.split('.', 1)[1].strip()
                options = []
                correct_answer = None
                explanation = ""
            
            elif line.startswith(('A)', 'B)', 'C)', 'D)')):
                option = line[2:].strip()
                options.append(option)
            
            elif line.startswith('정답:'):
                answer_text = line[3:].strip()
                if answer_text.startswith('A'):
                    correct_answer = 0
                elif answer_text.startswith('B'):
                    correct_answer = 1
                elif answer_text.startswith('C'):
                    correct_answer = 2
                elif answer_text.startswith('D'):
                    correct_answer = 3
            
            elif line.startswith('설명:'):
                explanation = line[3:].strip()
        
        # Save last question
        if current_question and options:
            questions.append({
                "question": current_question,
                "options": options,
                "correct_answer": correct_answer,
                "explanation": explanation
            })
        
        return questions


class ProofreadingLLMClient:
    """Specialized client for text proofreading"""
    
    def __init__(self, llm_client: LLMClient):
        self.llm_client = llm_client
    
    async def proofread_text(
        self,
        text: str,
        context: Optional[str] = None,
        language: str = "ko"
    ) -> Dict[str, Any]:
        """Proofread text and provide corrections"""
        
        system_message = """당신은 한국어 문법 및 문체 전문가입니다. 
주어진 텍스트의 맞춤법, 문법, 문체를 교정하고 개선사항을 제시해주세요.

응답 형식:
수정된 텍스트: [교정된 전체 텍스트]

주요 수정사항:
1. [수정 유형]: [원본] → [수정본] (이유: [설명])
2. [수정 유형]: [원본] → [수정본] (이유: [설명])

전체 평가: [텍스트 품질에 대한 종합적 평가]"""
        
        prompt = f"""다음 텍스트를 교정해주세요:

원본 텍스트:
{text}

{"컨텍스트: " + context if context else ""}

언어: {language}"""
        
        response = await self.llm_client.generate_completion(
            prompt=prompt,
            system_message=system_message,
            max_tokens=1000,
            temperature=0.2
        )
        
        return self._parse_proofread_response(response, text)
    
    def _parse_proofread_response(self, response: str, original_text: str) -> Dict[str, Any]:
        """Parse proofreading response"""
        # Simple parsing - could be enhanced
        lines = response.split('\n')
        
        corrected_text = original_text
        corrections = []
        
        for line in lines:
            if line.startswith('수정된 텍스트:'):
                corrected_text = line[7:].strip()
            elif '→' in line:
                # Extract correction
                parts = line.split('→')
                if len(parts) == 2:
                    original = parts[0].strip()
                    corrected = parts[1].split('(')[0].strip()
                    reason = ""
                    if '이유:' in line:
                        reason = line.split('이유:')[1].strip().rstrip(')')
                    
                    corrections.append({
                        "original": original,
                        "corrected": corrected,
                        "reason": reason,
                        "type": "grammar"  # Could be more specific
                    })
        
        return {
            "corrected_text": corrected_text,
            "corrections": corrections,
            "confidence_score": 0.85  # Mock confidence score
        }


class DiscussionLLMClient:
    """Specialized client for discussion facilitation"""
    
    def __init__(self, llm_client: LLMClient):
        self.llm_client = llm_client
    
    async def generate_moderator_response(
        self,
        message: str,
        context: List[str],
        conversation_history: List[Dict[str, str]]
    ) -> str:
        """Generate AI moderator response for discussion"""
        
        system_message = """당신은 독서 토론을 진행하는 AI 사회자입니다. 
참여자들의 토론을 활성화하고, 깊이 있는 대화로 이끌어주세요.

역할:
- 토론 주제에서 벗어나지 않도록 가이드
- 참여가 적은 사람들의 발언 유도
- 다양한 관점 제시
- 문서 내용을 바탕으로 근거 제시
- 건설적인 토론 분위기 조성

응답은 자연스럽고 친근하게, 100자 이내로 간결하게 작성해주세요."""
        
        context_str = "\n".join(context[:3]) if context else ""
        history_str = "\n".join([f"{msg.get('sender', '')}: {msg.get('message', '')}" 
                                for msg in conversation_history[-5:]])
        
        prompt = f"""현재 메시지: {message}

문서 관련 내용:
{context_str}

최근 대화 내용:
{history_str}

위 상황에서 토론을 더 활성화할 수 있는 AI 사회자의 응답을 생성해주세요."""
        
        response = await self.llm_client.generate_completion(
            prompt=prompt,
            system_message=system_message,
            max_tokens=200,
            temperature=0.8
        )
        
        return response.strip()
    
    async def generate_discussion_topics(
        self,
        document_content: str,
        previous_topics: List[str] = None
    ) -> List[str]:
        """Generate discussion topics from document content"""
        
        system_message = """문서 내용을 분석하여 흥미롭고 생각해볼 만한 토론 주제를 제시해주세요.
주제는 참여자들이 다양한 의견을 나눌 수 있도록 개방형으로 구성해주세요."""
        
        previous_str = f"\n기존 논의된 주제들 (피해주세요):\n{previous_topics}" if previous_topics else ""
        
        prompt = f"""다음 문서를 바탕으로 토론 주제 3개를 제시해주세요:

{document_content[:1500]}...

{previous_str}

형식:
1. [토론 주제]
2. [토론 주제]  
3. [토론 주제]"""
        
        response = await self.llm_client.generate_completion(
            prompt=prompt,
            system_message=system_message,
            max_tokens=300,
            temperature=0.7
        )
        
        # Parse topics
        topics = []
        for line in response.split('\n'):
            if line.strip() and any(line.startswith(f"{i}.") for i in range(1, 6)):
                topic = line.split('.', 1)[1].strip()
                topics.append(topic)
        
        return topics