"""
Mock Analytics Service for BGBG AI Server Testing
Provides realistic mock responses for user analytics functionality
"""

import random
from typing import Dict, List, Any
from datetime import datetime, timedelta
from loguru import logger


class MockAnalyticsService:
    """Mock service for user activity analysis and insights"""
    
    def __init__(self):
        self.mock_activity_data = {
            "user1": {
                "document_ids": ["doc1", "doc2", "doc3"],
                "reading_times": [45, 30, 60],  # minutes
                "meeting_participations": ["meeting_001", "meeting_002"],
                "quiz_scores": [85.0, 90.0, 78.0],
                "total_reading_time": 135,
                "category_preferences": {"technology": 3, "business": 1, "education": 2}
            },
            "user2": {
                "document_ids": ["doc1", "doc4", "doc5"],
                "reading_times": [25, 40, 35],
                "meeting_participations": ["meeting_001", "meeting_003"],
                "quiz_scores": [92.0, 88.0, 95.0],
                "total_reading_time": 100,
                "category_preferences": {"technology": 2, "science": 2, "education": 1}
            },
            "user3": {
                "document_ids": ["doc2", "doc3", "doc4", "doc5"],
                "reading_times": [50, 35, 45, 40],
                "meeting_participations": ["meeting_002", "meeting_003", "meeting_004"],
                "quiz_scores": [75.0, 82.0, 90.0, 87.0],
                "total_reading_time": 170,
                "category_preferences": {"technology": 4, "business": 2, "science": 1}
            }
        }
        
        self.insight_templates_ko = [
            "사용자는 기술 관련 문서에 가장 많은 관심을 보입니다.",
            "평균 독서 시간이 증가하는 추세입니다.",
            "퀴즈 점수가 꾸준히 향상되고 있습니다.",
            "토론 참여도가 높은 편입니다.",
            "심화 학습 자료 추천을 고려해보세요.",
            "비슷한 관심사를 가진 다른 사용자와의 연결을 추천합니다."
        ]
        
        self.insight_templates_en = [
            "User shows highest engagement with technology-related content.",
            "Average reading time is showing an upward trend.",
            "Quiz performance has been consistently improving.",
            "Discussion participation rate is above average.",
            "Consider recommending advanced learning materials.",
            "Connecting with users of similar interests is recommended."
        ]
    
    async def analyze_user_activity(self, analysis_data: Dict[str, Any]) -> Dict[str, Any]:
        """
        Analyze user activity patterns and generate insights
        
        Args:
            analysis_data: Dict containing:
                - user_id: str
                - start_date: int (timestamp)
                - end_date: int (timestamp)
        
        Returns:
            Dict with activity analysis and insights
        """
        try:
            user_id = analysis_data.get("user_id")
            start_date = analysis_data.get("start_date")
            end_date = analysis_data.get("end_date")
            
            logger.info(f"Analyzing activity for user: {user_id}")
            
            if not user_id:
                return {"success": False, "error": "User ID is required"}
            
            # Get mock data for user or create default
            user_data = self.mock_activity_data.get(user_id, self._generate_default_activity(user_id))
            
            # Generate insights based on user data
            insights = self._generate_user_insights(user_data, user_id)
            
            # Calculate performance metrics
            metrics = self._calculate_metrics(user_data)
            
            # Create activity data object
            activity_data = {
                "user_id": user_id,
                "document_ids": user_data["document_ids"],
                "reading_times": user_data["reading_times"],
                "meeting_participations": user_data["meeting_participations"],
                "quiz_scores": user_data["quiz_scores"],
                "total_reading_time": user_data["total_reading_time"],
                "category_preferences": user_data["category_preferences"]
            }
            
            result = {
                "success": True,
                "message": "User activity analyzed successfully",
                "activity_data": activity_data,
                "insights": insights,
                "metrics": metrics
            }
            
            logger.info(f"Generated {len(insights)} insights for user {user_id}")
            return result
            
        except Exception as e:
            logger.error(f"User activity analysis failed: {e}")
            return {"success": False, "error": f"Analysis failed: {str(e)}"}
    
    async def extract_user_interests(self, interest_data: Dict[str, Any]) -> Dict[str, Any]:
        """
        Extract user interests from activity data
        
        Args:
            interest_data: Dict containing:
                - user_id: str
                - activity_data: Dict
                - recent_searches: List[str]
                - bookmarked_topics: List[str]
        
        Returns:
            Dict with extracted interests and recommendations
        """
        try:
            user_id = interest_data.get("user_id")
            activity_data = interest_data.get("activity_data", {})
            recent_searches = interest_data.get("recent_searches", [])
            bookmarked_topics = interest_data.get("bookmarked_topics", [])
            
            logger.info(f"Extracting interests for user: {user_id}")
            
            # Analyze category preferences from activity data
            category_preferences = activity_data.get("category_preferences", {})
            
            # Extract primary interests (top categories)
            primary_interests = sorted(
                category_preferences.items(), 
                key=lambda x: x[1], 
                reverse=True
            )[:3]
            primary_interests = [interest[0] for interest in primary_interests]
            
            # Generate emerging interests based on recent activity
            emerging_interests = self._identify_emerging_interests(
                recent_searches, bookmarked_topics, category_preferences
            )
            
            # Calculate interest scores
            interest_scores = self._calculate_interest_scores(
                category_preferences, recent_searches, bookmarked_topics
            )
            
            # Generate category recommendations
            recommended_categories = self._recommend_categories(primary_interests)
            
            result = {
                "success": True,
                "message": "User interests extracted successfully",
                "primary_interests": primary_interests,
                "emerging_interests": emerging_interests,
                "interest_scores": interest_scores,
                "recommended_categories": recommended_categories
            }
            
            logger.info(f"Extracted {len(primary_interests)} primary interests for user {user_id}")
            return result
            
        except Exception as e:
            logger.error(f"Interest extraction failed: {e}")
            return {"success": False, "error": f"Interest extraction failed: {str(e)}"}
    
    def _generate_default_activity(self, user_id: str) -> Dict[str, Any]:
        """Generate default activity data for unknown users"""
        return {
            "document_ids": [f"doc{random.randint(1, 5)}" for _ in range(random.randint(1, 4))],
            "reading_times": [random.randint(15, 60) for _ in range(random.randint(1, 4))],
            "meeting_participations": [f"meeting_{random.randint(1, 5):03d}" for _ in range(random.randint(0, 3))],
            "quiz_scores": [random.uniform(60.0, 95.0) for _ in range(random.randint(1, 4))],
            "total_reading_time": random.randint(50, 200),
            "category_preferences": {
                "technology": random.randint(0, 5),
                "business": random.randint(0, 3),
                "science": random.randint(0, 4),
                "education": random.randint(0, 3)
            }
        }
    
    def _generate_user_insights(self, user_data: Dict[str, Any], user_id: str) -> List[str]:
        """Generate personalized insights based on user data"""
        insights = []
        
        # Determine language preference (simplified - assuming Korean for now)
        templates = self.insight_templates_ko
        
        # Analyze reading patterns
        total_time = user_data.get("total_reading_time", 0)
        if total_time > 150:
            insights.append("사용자는 매우 적극적인 독서 패턴을 보입니다.")
        elif total_time > 100:
            insights.append("사용자는 꾸준한 독서 습관을 가지고 있습니다.")
        else:
            insights.append("독서 시간을 늘려보시는 것을 추천합니다.")
        
        # Analyze quiz performance
        quiz_scores = user_data.get("quiz_scores", [])
        if quiz_scores:
            avg_score = sum(quiz_scores) / len(quiz_scores)
            if avg_score >= 90:
                insights.append("퀴즈 성과가 매우 우수합니다.")
            elif avg_score >= 80:
                insights.append("퀴즈 성과가 양호합니다.")
            else:
                insights.append("퀴즈 성과 향상을 위한 추가 학습을 추천합니다.")
        
        # Analyze meeting participation
        meetings = user_data.get("meeting_participations", [])
        if len(meetings) >= 3:
            insights.append("토론 참여도가 높습니다.")
        elif len(meetings) >= 1:
            insights.append("토론 참여를 더 늘려보시는 것을 추천합니다.")
        
        # Analyze category preferences
        categories = user_data.get("category_preferences", {})
        if categories:
            top_category = max(categories, key=categories.get)
            insights.append(f"{top_category} 분야에 특별한 관심을 보입니다.")
        
        # Add random additional insights
        additional = random.sample(templates, min(2, len(templates)))
        insights.extend(additional)
        
        return insights[:6]  # Limit to 6 insights
    
    def _calculate_metrics(self, user_data: Dict[str, Any]) -> Dict[str, float]:
        """Calculate performance metrics"""
        metrics = {}
        
        # Average reading time per document
        reading_times = user_data.get("reading_times", [])
        if reading_times:
            metrics["avg_reading_time"] = sum(reading_times) / len(reading_times)
            metrics["max_reading_time"] = max(reading_times)
            metrics["min_reading_time"] = min(reading_times)
        
        # Average quiz score
        quiz_scores = user_data.get("quiz_scores", [])
        if quiz_scores:
            metrics["avg_quiz_score"] = sum(quiz_scores) / len(quiz_scores)
            metrics["max_quiz_score"] = max(quiz_scores)
            metrics["quiz_improvement"] = (quiz_scores[-1] - quiz_scores[0]) if len(quiz_scores) > 1 else 0.0
        
        # Engagement metrics
        metrics["total_documents"] = len(user_data.get("document_ids", []))
        metrics["total_meetings"] = len(user_data.get("meeting_participations", []))
        metrics["total_reading_time"] = float(user_data.get("total_reading_time", 0))
        
        # Activity diversity score
        categories = user_data.get("category_preferences", {})
        if categories:
            category_count = len([c for c in categories.values() if c > 0])
            metrics["diversity_score"] = category_count / len(categories) if categories else 0.0
        
        return metrics
    
    def _identify_emerging_interests(self, recent_searches: List[str], 
                                   bookmarked_topics: List[str], 
                                   category_preferences: Dict[str, int]) -> List[str]:
        """Identify emerging interests from recent activity"""
        emerging = []
        
        # Mock logic - in reality this would use more sophisticated analysis
        potential_interests = ["AI Ethics", "Blockchain", "Quantum Computing", "Sustainable Technology"]
        
        # Add some based on current preferences
        if category_preferences.get("technology", 0) > 2:
            emerging.extend(["Machine Learning", "Cloud Computing"])
        
        if category_preferences.get("business", 0) > 1:
            emerging.extend(["Digital Transformation", "Innovation Management"])
        
        # Add random emerging interests
        additional = random.sample(potential_interests, min(2, len(potential_interests)))
        emerging.extend(additional)
        
        return list(set(emerging))[:3]  # Return unique, limited list
    
    def _calculate_interest_scores(self, category_preferences: Dict[str, int],
                                 recent_searches: List[str], 
                                 bookmarked_topics: List[str]) -> Dict[str, float]:
        """Calculate confidence scores for interests"""
        scores = {}
        
        # Base scores from category preferences
        total_activity = sum(category_preferences.values()) if category_preferences else 1
        
        for category, count in category_preferences.items():
            scores[category] = count / total_activity
        
        # Boost scores based on recent searches and bookmarks
        for search in recent_searches:
            for category in scores:
                if category.lower() in search.lower():
                    scores[category] = min(1.0, scores[category] + 0.1)
        
        for bookmark in bookmarked_topics:
            for category in scores:
                if category.lower() in bookmark.lower():
                    scores[category] = min(1.0, scores[category] + 0.15)
        
        return scores
    
    def _recommend_categories(self, primary_interests: List[str]) -> List[str]:
        """Recommend new categories based on current interests"""
        recommendations = {
            "technology": ["science", "innovation", "engineering"],
            "business": ["economics", "marketing", "strategy"],
            "science": ["technology", "research", "mathematics"],
            "education": ["psychology", "pedagogy", "training"]
        }
        
        suggested = []
        for interest in primary_interests:
            if interest in recommendations:
                suggested.extend(recommendations[interest])
        
        # Remove duplicates and return limited list
        return list(set(suggested))[:3]