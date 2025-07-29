"""
Proofreading Service for BGBG AI Server
Handles text correction, grammar analysis, and diff generation
"""

import hashlib
from typing import Dict, List, Optional, Any
from loguru import logger

from src.services.mock_proofreading_service import MockProofreadingService
from src.config.settings import get_settings


class ProofreadingService:
    """Service for AI-powered text proofreading and correction"""
    
    def __init__(self):
        self.settings = get_settings()
        self.mock_service = MockProofreadingService()
        self.correction_cache: Dict[str, Dict[str, Any]] = {}
    
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
            
            # Use mock service for testing
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
    
    def _generate_cache_key(self, text: str) -> str:
        """Generate cache key for text"""
        return hashlib.md5(text.encode('utf-8')).hexdigest()