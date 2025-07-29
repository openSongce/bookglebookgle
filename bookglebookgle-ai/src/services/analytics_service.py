"""
Analytics Service for BGBG AI Server
Handles user activity analysis and interest extraction
"""

from typing import Dict, List, Optional, Any
from loguru import logger

from src.services.mock_analytics_service import MockAnalyticsService
from src.config.settings import get_settings


class AnalyticsService:
    """Service for user analytics and insights"""
    
    def __init__(self):
        self.settings = get_settings()
        self.mock_service = MockAnalyticsService()
    
    async def analyze_user_activity(self, analysis_data: Dict[str, Any]) -> Dict[str, Any]:
        """Analyze user activity patterns"""
        return await self.mock_service.analyze_user_activity(analysis_data)
    
    async def extract_user_interests(self, interest_data: Dict[str, Any]) -> Dict[str, Any]:
        """Extract user interests from activity data"""
        return await self.mock_service.extract_user_interests(interest_data)