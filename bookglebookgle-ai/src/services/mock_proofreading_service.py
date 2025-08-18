"""
Mock Proofreading Service for BGBG AI Server Testing
Provides realistic mock responses for proofreading functionality
"""

import re
from typing import Dict, List, Any
from loguru import logger


class MockProofreadingService:
    """Mock service for text proofreading and correction"""
    
    def __init__(self):
        # Common Korean typos and corrections
        self.korean_corrections = {
            "담고있습니다": "담고 있습니다",
            "발전하고잇습니다": "발전하고 있습니다", 
            "절약할수": "절약할 수",
            "사용하면서도": "사용하면서도",
            "이용한것": "이용한 것",
            "수있습니다": "수 있습니다"
        }
        
        # Common English corrections
        self.english_corrections = {
            "seperate": "separate",
            "recieve": "receive", 
            "definately": "definitely",
            "occured": "occurred",
            "existance": "existence"
        }
    
    async def proofread_text(self, proofread_data: Dict[str, Any]) -> Dict[str, Any]:
        """
        Mock proofreading function that finds and corrects common errors
        
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
            logger.info(f"Mock proofreading for user: {proofread_data.get('user_id')}")
            
            original_text = proofread_data.get("original_text", "")
            language = proofread_data.get("language", "ko")
            
            if not original_text:
                return {
                    "success": False,
                    "error": "No text provided for proofreading"
                }
            
            # Determine which corrections to use based on language
            corrections_dict = self.korean_corrections if language == "ko" else self.english_corrections
            
            corrected_text = original_text
            corrections = []
            
            # Find and correct errors
            for wrong, correct in corrections_dict.items():
                if wrong in corrected_text:
                    # Find positions of all occurrences
                    start_pos = 0
                    while True:
                        pos = corrected_text.find(wrong, start_pos)
                        if pos == -1:
                            break
                        
                        corrections.append({
                            "original": wrong,
                            "corrected": correct,
                            "type": "spacing" if " " in correct and " " not in wrong else "spelling",
                            "explanation": self._get_correction_explanation(wrong, correct, language),
                            "start_pos": pos,
                            "end_pos": pos + len(wrong)
                        })
                        
                        # Replace this occurrence
                        corrected_text = corrected_text[:pos] + correct + corrected_text[pos + len(wrong):]
                        start_pos = pos + len(correct)
            
            # Add some mock grammar corrections based on patterns
            additional_corrections = self._find_pattern_errors(original_text, language)
            corrections.extend(additional_corrections)
            
            # Calculate confidence score based on number of corrections
            confidence_score = max(0.6, 1.0 - (len(corrections) * 0.1))
            
            result = {
                "success": True,
                "corrected_text": corrected_text,
                "corrections": corrections,
                "confidence_score": confidence_score,
                "message": f"Found {len(corrections)} corrections"
            }
            
            logger.info(f"Mock proofreading completed: {len(corrections)} corrections found")
            return result
            
        except Exception as e:
            logger.error(f"Mock proofreading failed: {e}")
            return {"success": False, "error": f"Proofreading failed: {str(e)}"}
    
    def _get_correction_explanation(self, wrong: str, correct: str, language: str) -> str:
        """Generate explanation for the correction"""
        if language == "ko":
            if " " in correct and " " not in wrong:
                return f"'{wrong}'는 '{correct}'로 띄어 써야 합니다."
            else:
                return f"'{wrong}'의 올바른 표기는 '{correct}'입니다."
        else:
            return f"'{wrong}' should be spelled as '{correct}'"
    
    def _find_pattern_errors(self, text: str, language: str) -> List[Dict[str, Any]]:
        """Find additional pattern-based errors"""
        corrections = []
        
        if language == "ko":
            # Check for missing spaces before particles
            patterns = [
                (r'([가-힣])는([가-힣])', r'\1는 \2', "조사 앞에 띄어쓰기가 필요합니다"),
                (r'([가-힣])에서([가-힣])', r'\1에서 \2', "조사 뒤에 띄어쓰기가 필요합니다"),
            ]
        else:
            # English pattern corrections
            patterns = [
                (r'\bi\b', 'I', "Personal pronoun 'I' should be capitalized"),
                (r'([a-z])\.([A-Z])', r'\1. \2', "Space needed after period"),
            ]
        
        for pattern, replacement, explanation in patterns:
            matches = list(re.finditer(pattern, text))
            for match in matches:
                corrections.append({
                    "original": match.group(),
                    "corrected": re.sub(pattern, replacement, match.group()),
                    "type": "grammar",
                    "explanation": explanation,
                    "start_pos": match.start(),
                    "end_pos": match.end()
                })
        
        return corrections