"""
Mock Discussion Service for BGBG AI Server Testing
Provides realistic mock responses for discussion functionality
"""

import uuid
from typing import Dict, List, Any
from datetime import datetime
from loguru import logger


class MockDiscussionService:
    """Mock service for discussion AI and chat moderation"""
    
    def __init__(self):
        self.active_sessions: Dict[str, Dict[str, Any]] = {}
        self.sample_topics_ko = [
            "이 기술이 미래에 어떤 변화를 가져올까요?",
            "실제 적용 사례에 대해 토론해보세요",
            "장단점을 비교 분석해보면 어떨까요?",
            "관련된 윤리적 이슈는 무엇이 있을까요?",
            "다른 기술과의 차이점은 무엇인가요?"
        ]
        self.sample_topics_en = [
            "How might this technology change the future?",
            "What are some real-world applications?",
            "What are the pros and cons?",
            "What ethical considerations should we discuss?",
            "How does this compare to other technologies?"
        ]
    
    async def initialize_discussion(self, init_data: Dict[str, Any]) -> Dict[str, Any]:
        """
        Initialize discussion session
        
        Args:
            init_data: Dict containing:
                - document_id: str
                - meeting_id: str
                - full_document: str
                - participants: List[Dict]
        
        Returns:
            Dict with session_id and initialization status
        """
        try:
            logger.info(f"Initializing discussion for meeting: {init_data.get('meeting_id')}")
            
            document_id = init_data.get("document_id")
            meeting_id = init_data.get("meeting_id")
            participants = init_data.get("participants", [])
            
            if not document_id or not meeting_id:
                return {"success": False, "error": "Missing required fields"}
            
            # Generate session ID
            session_id = f"session_{meeting_id}_{str(uuid.uuid4())[:8]}"
            
            # Store session data
            session_data = {
                "session_id": session_id,
                "document_id": document_id,
                "meeting_id": meeting_id,
                "participants": participants,
                "created_at": datetime.utcnow().isoformat(),
                "message_count": 0,
                "last_activity": datetime.utcnow().isoformat()
            }
            
            self.active_sessions[session_id] = session_data
            
            logger.info(f"Discussion session created: {session_id}")
            
            return {
                "success": True,
                "session_id": session_id,
                "message": f"Discussion initialized with {len(participants)} participants"
            }
            
        except Exception as e:
            logger.error(f"Discussion initialization failed: {e}")
            return {"success": False, "error": f"Initialization failed: {str(e)}"}
    
    async def process_chat_message(self, message_data: Dict[str, Any]) -> Dict[str, Any]:
        """
        Process chat message and generate AI response
        
        Args:
            message_data: Dict containing:
                - session_id: str
                - sender_id: str
                - sender_nickname: str
                - message: str
                - timestamp: int
        
        Returns:
            Dict with AI response and suggestions
        """
        try:
            session_id = message_data.get("session_id")
            message = message_data.get("message", "")
            sender_nickname = message_data.get("sender_nickname", "Anonymous")
            
            # 세션이 없으면 자동으로 생성 (테스트 편의성)
            if not session_id or session_id not in self.active_sessions:
                logger.info(f"Creating auto session for: {session_id}")
                auto_session = {
                    "session_id": session_id or "auto-session",
                    "document_id": "auto-doc",
                    "meeting_id": "auto-meeting",
                    "participants": [{"user_id": "auto-user", "nickname": sender_nickname}],
                    "message_count": 0,
                    "created_at": datetime.utcnow().isoformat(),
                    "last_activity": datetime.utcnow().isoformat()
                }
                self.active_sessions[session_id or "auto-session"] = auto_session
            
            # Update session activity
            session = self.active_sessions[session_id]
            session["message_count"] += 1
            session["last_activity"] = datetime.utcnow().isoformat()
            
            # Simple AI response logic based on message content
            ai_response = self._generate_ai_response(message, sender_nickname)
            
            # Check if moderation is needed
            requires_moderation = self._check_moderation_needed(message)
            
            # Generate suggested topics
            suggested_topics = self._generate_suggested_topics()
            
            result = {
                "success": True,
                "message": "Message processed successfully",
                "ai_response": ai_response,
                "suggested_topics": suggested_topics,
                "requires_moderation": requires_moderation
            }
            
            logger.debug(f"Processed message in session {session_id}")
            return result
            
        except Exception as e:
            logger.error(f"Chat message processing failed: {e}")
            return {"success": False, "error": f"Processing failed: {str(e)}"}
    
    async def generate_topics(self, topic_data: Dict[str, Any]) -> Dict[str, Any]:
        """
        Generate discussion topics from document
        
        Args:
            topic_data: Dict containing:
                - document_id: str
                - content: str
                - previous_topics: List[str]
        
        Returns:
            Dict with generated topics
        """
        try:
            document_id = topic_data.get("document_id")
            content = topic_data.get("content", "")
            previous_topics = topic_data.get("previous_topics", [])
            
            logger.info(f"Generating topics for document: {document_id}")
            
            # Determine language from content
            has_korean = any(ord(char) >= 0xAC00 and ord(char) <= 0xD7A3 for char in content[:100])
            language = "ko" if has_korean else "en"
            
            # Select topic templates based on language
            topic_templates = self.sample_topics_ko if language == "ko" else self.sample_topics_en
            
            # Filter out previous topics (simple text matching)
            available_topics = [
                topic for topic in topic_templates 
                if not any(prev in topic for prev in previous_topics)
            ]
            
            # If no new topics, use all templates
            if not available_topics:
                available_topics = topic_templates
            
            # Select topics (max 3-5)
            import random
            selected_count = min(4, len(available_topics))
            selected_topics = random.sample(available_topics, selected_count)
            
            # Choose recommended topic
            recommended_topic = selected_topics[0] if selected_topics else "자유로운 토론을 시작해보세요!"
            
            result = {
                "success": True,
                "topics": selected_topics,
                "recommended_topic": recommended_topic,
                "topic_rationale": "문서 내용을 바탕으로 흥미로운 토론 주제를 선별했습니다." if language == "ko" 
                                  else "Selected engaging discussion topics based on document content.",
                "message": f"Generated {len(selected_topics)} discussion topics"
            }
            
            logger.info(f"Generated {len(selected_topics)} topics for document {document_id}")
            return result
            
        except Exception as e:
            logger.error(f"Topic generation failed: {e}")
            return {"success": False, "error": f"Topic generation failed: {str(e)}"}
    
    def _generate_ai_response(self, message: str, sender_nickname: str) -> str:
        """Generate contextual AI response"""
        message_lower = message.lower()
        
        # Korean responses
        korean_responses = {
            "질문": f"{sender_nickname}님의 질문이 흥미롭네요. 다른 분들의 의견도 들어보면 좋겠습니다.",
            "생각": f"{sender_nickname}님의 견해에 공감합니다. 추가로 고려할 점이 있을까요?",
            "의견": "다양한 관점에서 접근해보는 것이 좋겠네요.",
            "문제": "이 문제에 대한 해결 방안을 함께 모색해보시죠.",
            "예시": "구체적인 사례를 통해 더 깊이 있게 논의해보면 어떨까요?"
        }
        
        # English responses
        english_responses = {
            "question": f"Great question, {sender_nickname}! I'd love to hear what others think about this.",
            "think": f"That's an interesting perspective, {sender_nickname}. What other aspects should we consider?",
            "opinion": "It's valuable to explore different viewpoints on this topic.",
            "problem": "Let's work together to find potential solutions to this challenge.",
            "example": "Concrete examples could help us dive deeper into this discussion."
        }
        
        # Detect language and generate response
        has_korean = any(ord(char) >= 0xAC00 and ord(char) <= 0xD7A3 for char in message)
        
        if has_korean:
            for keyword, response in korean_responses.items():
                if keyword in message:
                    return response
            return f"{sender_nickname}님의 의견에 감사합니다. 이 주제에 대해 더 자세히 이야기해보시죠."
        else:
            for keyword, response in english_responses.items():
                if keyword in message_lower:
                    return response
            return f"Thank you for your input, {sender_nickname}. Let's explore this topic further."
    
    def _check_moderation_needed(self, message: str) -> bool:
        """Check if message needs moderation (simple mock logic)"""
        # Simple keywords that might require moderation
        flagged_keywords = ["spam", "inappropriate", "offensive", "스팸", "부적절"]
        
        message_lower = message.lower()
        return any(keyword in message_lower for keyword in flagged_keywords)
    
    def _generate_suggested_topics(self) -> List[str]:
        """Generate suggested follow-up topics"""
        import random
        
        suggestions = [
            "이 주제와 관련된 다른 관점은?",
            "실제 적용 사례에 대해 토론해보세요",
            "장점과 단점을 비교해보시죠",
            "What other perspectives should we consider?",
            "How might this apply in practice?",
            "What are the trade-offs?"
        ]
        
        return random.sample(suggestions, min(2, len(suggestions)))