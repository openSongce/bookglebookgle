"""
LLM Client for BGBG AI Server
Handles communication with various LLM providers (OpenAI, Anthropic)
"""

import asyncio
from typing import Dict, List, Optional, Any, Union
from enum import Enum

import openai
import anthropic
from loguru import logger

from src.config.settings import get_settings


class LLMProvider(Enum):
    OPENAI = "openai"
    ANTHROPIC = "anthropic"
    OPENROUTER = "openrouter"
    MOCK = "mock"


class LLMClient:
    """Client for interacting with various LLM providers"""
    
    def __init__(self):
        self.settings = get_settings()
        self.openai_client: Optional[openai.AsyncOpenAI] = None
        self.anthropic_client: Optional[anthropic.AsyncAnthropic] = None
        self.openrouter_client: Optional[openai.AsyncOpenAI] = None  

    async def initialize(self):
        """Initialize LLM clients"""
        try:
            logger.info("Initializing LLM clients...")
            logger.info(f"🔍 Debug - Mock Responses: {self.settings.ai.MOCK_AI_RESPONSES}")
            
            # Initialize OpenAI client
            if self.settings.ai.OPENAI_API_KEY:
                self.openai_client = openai.AsyncOpenAI(
                    api_key=self.settings.ai.OPENAI_API_KEY
                )
                logger.info("OpenAI client initialized")
            
            # Initialize Anthropic client
            if self.settings.ai.ANTHROPIC_API_KEY:
                self.anthropic_client = anthropic.AsyncAnthropic(
                    api_key=self.settings.ai.ANTHROPIC_API_KEY
                )
                logger.info("Anthropic client initialized")

            if self.settings.ai.OPENROUTER_API_KEY:
                self.openrouter_client = openai.AsyncOpenAI(
                    api_key=self.settings.ai.OPENROUTER_API_KEY,
                    base_url=self.settings.ai.OPENROUTER_BASE_URL
            )
                logger.info("OpenRouter client initialized")
            
            if not any([self.openai_client, self.anthropic_client, self.openrouter_client]):
                logger.warning("No LLM clients initialized - using mock responses")
                
        except Exception as e:
            logger.error(f"Failed to initialize LLM clients: {e}")
            raise
    
    async def generate_completion(
        self,
        prompt: str,
        system_message: Optional[str] = None,
        max_tokens: int = 1000,
        temperature: float = 0.7,
        provider: Optional[LLMProvider] = None,
        model: Optional[str] = None
    ) -> str:
        """Generate text completion using specified LLM provider"""
        
        if self.settings.ai.MOCK_AI_RESPONSES:
            return await self._mock_completion(prompt)
        
        # Determine provider
        if not provider:
            provider = self._get_preferred_provider()
        
        try:
            if provider == LLMProvider.OPENAI and self.openai_client:
                return await self._openai_completion(prompt, system_message, max_tokens, temperature)
            elif provider == LLMProvider.ANTHROPIC and self.anthropic_client:
                return await self._anthropic_completion(prompt, system_message, max_tokens, temperature)
            elif provider == LLMProvider.OPENROUTER and self.openrouter_client:
                return await self._openrouter_completion(prompt, system_message, max_tokens, temperature, model)
            else:
                logger.warning("Falling back to mock completion")
                return await self._mock_completion(prompt)
                
        except Exception as e:
            logger.error(f"LLM completion failed with {provider.value}: {e}")
            return await self._mock_completion(prompt)
    
    async def _openai_completion(
        self,
        prompt: str,
        system_message: Optional[str],
        max_tokens: int,
        temperature: float
    ) -> str:
        """Generate completion using OpenAI"""
        try:
            messages = []
            
            if system_message:
                messages.append({"role": "system", "content": system_message})
            
            messages.append({"role": "user", "content": prompt})
            
            response = await self.openai_client.chat.completions.create(
                model="gpt-3.5-turbo",
                messages=messages,
                max_tokens=max_tokens,
                temperature=temperature
            )
            
            return response.choices[0].message.content.strip()
            
        except Exception as e:
            logger.error(f"OpenAI completion failed: {e}")
            raise
    
    async def _anthropic_completion(
        self,
        prompt: str,
        system_message: Optional[str],
        max_tokens: int,
        temperature: float
    ) -> str:
        """Generate completion using Anthropic Claude"""
        try:
            full_prompt = prompt
            if system_message:
                full_prompt = f"{system_message}\n\n{prompt}"
            
            response = await self.anthropic_client.messages.create(
                model="claude-3-haiku-20240307",
                system=system_message if system_message else "",
                messages=[{"role": "user", "content": prompt}],
                max_tokens=max_tokens,
                temperature=temperature
            )
            
            return response.content[0].text.strip()
            
        except Exception as e:
            logger.error(f"Anthropic completion failed: {e}")
            raise
    
    async def _openrouter_completion(
        self,
        prompt: str,
        system_message: Optional[str],
        max_tokens: int,
        temperature: float,
        model: Optional[str] = None
    ) -> str:
        """Generate completion using OpenRouter"""
        try:
            logger.info("Using OpenRouter for completion")
            messages = []
        
            if system_message:
                messages.append({"role": "system", "content": system_message})
        
            messages.append({"role": "user", "content": prompt})
        
            # 모델 선택: 파라미터로 지정되면 사용, 아니면 기본값
            selected_model = model or self.settings.ai.OPENROUTER_MODEL
            logger.info(f"Using model: {selected_model}")
        
            response = await self.openrouter_client.chat.completions.create(
                model=selected_model,
                messages=messages,
                max_tokens=max_tokens,
                temperature=temperature,
                # OpenRouter 특정 헤더 (선택사항)
                extra_headers={
                    "HTTP-Referer": "https://your-app.com",  # 선택사항
                    "X-Title": "BGBG AI Server"  # 선택사항
                }
            )
            
            result = response.choices[0].message.content.strip()
            logger.info(f"OpenRouter response received (length: {len(result)} chars)")
            return result
        
        except Exception as e:
            logger.error(f"OpenRouter completion failed: {e}")
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
    
    def _get_preferred_provider(self) -> LLMProvider:
        """Get preferred LLM provider based on availability"""
        if self.openrouter_client:
            return LLMProvider.OPENROUTER
        elif self.openai_client:
            return LLMProvider.OPENAI
        elif self.anthropic_client:
            return LLMProvider.ANTHROPIC
        else:
            return LLMProvider.MOCK


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