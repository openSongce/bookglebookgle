"""
Proofreading Service for BGBG AI Server
Handles text correction, grammar analysis, and diff generation
"""

import hashlib
from typing import Dict, List, Optional, Any
from loguru import logger

from src.services.mock_proofreading_service import MockProofreadingService
from src.services.llm_client import LLMClient, ProofreadingLLMClient
from src.config.settings import get_settings


class ProofreadingService:
    """Service for AI-powered text proofreading and correction"""
    
    def __init__(self):
        self.settings = get_settings()
        self.mock_service = MockProofreadingService()
        self.llm_client: Optional[LLMClient] = None
        self.proofreading_llm_client: Optional[ProofreadingLLMClient] = None
        self.correction_cache: Dict[str, Dict[str, Any]] = {}
    
    async def initialize(self):
        """Initialize proofreading service dependencies"""
        try:
            logger.info("Initializing Proofreading Service...")
            
            # Initialize LLM client
            if not self.settings.ai.MOCK_AI_RESPONSES:
                self.llm_client = LLMClient()
                await self.llm_client.initialize()
                self.proofreading_llm_client = ProofreadingLLMClient(self.llm_client)
                logger.info("LLM client initialized for proofreading")
            else:
                logger.info("Using mock responses for proofreading")
                
        except Exception as e:
            logger.error(f"Failed to initialize Proofreading Service: {e}")
            self.llm_client = None
    
    async def proofread_text(self, proofread_data: Dict[str, Any]) -> Dict[str, Any]:
        """
        Proofread and correct text with contextual analysis
        
        Args:
            proofread_data: Dict containing:
                - original_text: str
                - context_text: Optional[str] 
                - language: str (default: "ko")
                - user_id: str
        
        Returns:
            Dict with corrected text, corrections, and confidence score
        """
        try:
            logger.info(f"Starting proofreading for user: {proofread_data.get('user_id')}")
            
            # Validate input
            if not self._validate_proofread_request(proofread_data):
                return {"success": False, "error": "Invalid proofreading request data"}
            
            original_text = proofread_data["original_text"]
            
            # Check cache first
            cache_key = self._generate_cache_key(original_text)
            if cache_key in self.correction_cache:
                logger.info("Using cached proofreading result")
                return self.correction_cache[cache_key]
            
            # 실제 LLM 연동 또는 Mock 선택
            if not self.settings.ai.MOCK_AI_RESPONSES and self.proofreading_llm_client:
                # ProofreadingLLMClient 사용하여 실제 첨삭
                result = await self._process_with_llm(proofread_data)
            else:
                # Mock 서비스 사용 (개발/테스트용)
                result = await self.mock_service.proofread_text(proofread_data)
            
            # Cache the result
            self.correction_cache[cache_key] = result
            
            logger.info(f"Proofreading completed with {len(result.get('corrections', []))} corrections")
            return result
            
        except Exception as e:
            logger.error(f"Proofreading failed: {e}")
            return {"success": False, "error": f"Proofreading failed: {str(e)}"}
    
    def _validate_proofread_request(self, proofread_data: Dict[str, Any]) -> bool:
        """Validate proofreading request data"""
        if not proofread_data.get("original_text"):
            logger.error("Missing original_text in proofread request")
            return False
        
        if not proofread_data.get("user_id"):
            logger.error("Missing user_id in proofread request")
            return False
        
        return True
    
    async def _process_with_llm(self, proofread_data: Dict[str, Any]) -> Dict[str, Any]:
        """LLM을 사용한 실제 첨삭 처리"""
        try:
            original_text = proofread_data["original_text"]
            context_text = proofread_data.get("context_text", "")
            language = proofread_data.get("language", "ko")
            
            # 시스템 메시지 구성
            system_message = """당신은 전문적인 텍스트 교정 전문가입니다. 
주어진 텍스트의 문법, 맞춤법, 문체를 검토하고 개선 사항을 제안해주세요.
응답은 다음 JSON 형식으로 제공해주세요:

{
  "corrected_text": "교정된 전체 텍스트",
  "corrections": [
    {
      "original": "원본 텍스트",
      "corrected": "교정된 텍스트", 
      "type": "grammar|spelling|style|clarity",
      "explanation": "교정 이유 설명"
    }
  ],
  "confidence_score": 0.95
}"""
            
            # 프롬프트 구성
            prompt = f"""다음 텍스트를 교정해주세요:

원본 텍스트:
{original_text}

맥락 정보:
{context_text}

언어: {language}

문법, 맞춤법, 문체의 오류를 찾아 교정하고, 각 수정사항에 대한 설명을 제공해주세요."""
            
            # LLM 호출
            response = await self.llm_client.generate_completion(
                prompt=prompt,
                system_message=system_message,
                max_tokens=1500,
                temperature=0.3  # 교정 작업이므로 낮은 temperature 사용
            )
            
            # JSON 응답 파싱
            import json
            import re
            
            json_match = re.search(r'\{.*\}', response, re.DOTALL)
            if json_match:
                try:
                    result_data = json.loads(json_match.group())
                    
                    # 응답 포맷 변환
                    formatted_result = {
                        "success": True,
                        "corrected_text": result_data.get("corrected_text", original_text),
                        "corrections": [
                            {
                                "original": corr.get("original", ""),
                                "corrected": corr.get("corrected", ""),
                                "type": corr.get("type", "grammar"),
                                "explanation": corr.get("explanation", ""),
                                "start_position": 0,  # 정확한 위치 계산은 향후 개선
                                "end_position": len(corr.get("original", ""))
                            }
                            for corr in result_data.get("corrections", [])
                        ],
                        "confidence_score": result_data.get("confidence_score", 0.85)
                    }
                    
                    logger.info(f"LLM proofreading completed with {len(formatted_result['corrections'])} corrections")
                    return formatted_result
                    
                except json.JSONDecodeError as e:
                    logger.error(f"Failed to parse LLM response as JSON: {e}")
                    
            # JSON 파싱 실패 시 기본 응답
            return {
                "success": True,
                "corrected_text": original_text,
                "corrections": [],
                "confidence_score": 0.5
            }
            
        except Exception as e:
            logger.error(f"LLM proofreading failed: {e}")
            return {
                "success": False,
                "error": f"LLM proofreading failed: {str(e)}"
            }
    
    def _generate_cache_key(self, text: str) -> str:
        """Generate cache key for text"""
        return hashlib.md5(text.encode('utf-8')).hexdigest()