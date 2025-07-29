"""
Quiz Generation Service for BGBG AI Server
Handles quiz creation from document content with progress-based triggers
"""

import asyncio
import json
import uuid
from datetime import datetime
from typing import Dict, List, Optional, Any

from loguru import logger

from src.services.llm_client import LLMClient, QuizLLMClient
from src.services.vector_db import VectorDBManager
from src.config.settings import get_settings


class QuizService:
    """Service for generating quizzes from document content"""
    
    def __init__(self):
        self.settings = get_settings()
        self.llm_client: Optional[LLMClient] = None
        self.quiz_llm_client: Optional[QuizLLMClient] = None
        self.vector_db: Optional[VectorDBManager] = None
        self.active_quizzes: Dict[str, Dict[str, Any]] = {}
    
    async def initialize(self):
        """Initialize quiz service dependencies"""
        try:
            logger.info("Initializing Quiz Service...")
            
            # Initialize LLM client
            self.llm_client = LLMClient()
            await self.llm_client.initialize()
            
            self.quiz_llm_client = QuizLLMClient(self.llm_client)
            
            # Initialize Vector DB (optional for quiz generation)
            self.vector_db = VectorDBManager()
            if not self.vector_db.is_initialized():
                await self.vector_db.initialize()
            
            logger.info("Quiz Service initialized successfully")
            
        except Exception as e:
            logger.error(f"Failed to initialize Quiz Service: {e}")
            raise
    
    async def generate_quiz(self, quiz_data: Dict[str, Any]) -> Dict[str, Any]:
        """
        Generate quiz questions from document content
        
        Args:
            quiz_data: Dict containing:
                - document_id: str
                - content: str  
                - language: str (default: "ko")
                - progress_percentage: int (50 or 100)
                - question_count: int (default: 5)
                - difficulty_level: str (easy/medium/hard)
        
        Returns:
            Dict with success status, quiz_id, and questions
        """
        try:
            logger.info(f"Generating quiz for document: {quiz_data.get('document_id')}")
            
            # Validate input
            if not self._validate_quiz_request(quiz_data):
                return {"success": False, "error": "Invalid quiz request data"}
            
            # For testing purposes, generate mock questions
            # In production, this would use LLM services
            mock_questions = self._generate_mock_questions(quiz_data)
            
            # Validate generated questions
            validated_questions = self._validate_questions(mock_questions)
            
            if not validated_questions:
                return {"success": False, "error": "Failed to generate valid questions"}
            
            # Create quiz record
            quiz_id = self._generate_quiz_id(quiz_data["document_id"])
            quiz_record = {
                "quiz_id": quiz_id,
                "document_id": quiz_data["document_id"],
                "progress_percentage": quiz_data.get("progress_percentage"),
                "questions": validated_questions,
                "created_at": datetime.utcnow().isoformat(),
                "difficulty_level": quiz_data.get("difficulty_level", "medium"),
                "language": quiz_data.get("language", "ko")
            }
            
            # Store quiz for future reference
            self.active_quizzes[quiz_id] = quiz_record
            
            logger.info(f"Successfully generated quiz with {len(validated_questions)} questions")
            
            return {
                "success": True,
                "quiz_id": quiz_id,
                "questions": validated_questions,
                "message": f"Quiz generated with {len(validated_questions)} questions"
            }
            
        except Exception as e:
            logger.error(f"Quiz generation failed: {e}")
            return {"success": False, "error": f"Quiz generation failed: {str(e)}"}
    
    async def _extract_content_by_progress(self, quiz_data: Dict[str, Any]) -> str:
        """Extract relevant content based on progress percentage"""
        try:
            content = quiz_data.get("content", "")
            progress_percentage = quiz_data.get("progress_percentage", 100)
            
            if not content:
                return ""
            
            # Split content based on progress
            if progress_percentage == 50:
                # First half of the document
                mid_point = len(content) // 2
                return content[:mid_point]
            elif progress_percentage == 100:
                # Second half of the document  
                mid_point = len(content) // 2
                return content[mid_point:]
            else:
                # Full document for other percentages
                return content
                
        except Exception as e:
            logger.error(f"Content extraction failed: {e}")
            return quiz_data.get("content", "")
    
    def _validate_quiz_request(self, quiz_data: Dict[str, Any]) -> bool:
        """Validate quiz generation request"""
        required_fields = ["document_id", "content"]
        
        for field in required_fields:
            if field not in quiz_data or not quiz_data[field]:
                logger.error(f"Missing required field: {field}")
                return False
        
        # Validate progress percentage
        progress = quiz_data.get("progress_percentage")
        if progress and progress not in [50, 100]:
            logger.error(f"Invalid progress percentage: {progress}")
            return False
        
        # Validate question count
        question_count = quiz_data.get("question_count", 5)
        if not isinstance(question_count, int) or question_count < 1 or question_count > 10:
            logger.error(f"Invalid question count: {question_count}")
            return False
        
        return True
    
    def _generate_mock_questions(self, quiz_data: Dict[str, Any]) -> List[Dict[str, Any]]:
        """Generate mock quiz questions for testing"""
        content = quiz_data.get("content", "")
        language = quiz_data.get("language", "ko")
        question_count = quiz_data.get("question_count", 5)
        difficulty = quiz_data.get("difficulty_level", "medium")
        
        # Mock questions based on content type and language
        if language == "ko":
            mock_questions = [
                {
                    "question": "이 문서의 주요 주제는 무엇입니까?",
                    "options": ["인공지능과 머신러닝", "웹 개발", "데이터베이스 설계", "클라우드 컴퓨팅"],
                    "correct_answer": 0,
                    "explanation": "문서는 주로 AI와 머신러닝에 관한 내용을 다루고 있습니다.",
                    "category": "주요 개념"
                },
                {
                    "question": "딥러닝은 무엇의 하위 분야입니까?",
                    "options": ["데이터베이스", "머신러닝", "웹 개발", "네트워킹"],
                    "correct_answer": 1,
                    "explanation": "딥러닝은 머신러닝의 한 분야입니다.",
                    "category": "기술 분류"
                },
                {
                    "question": "AI 기술이 활용되는 분야가 아닌 것은?",
                    "options": ["자율주행차", "의료 진단", "전통 농업", "금융 서비스"],
                    "correct_answer": 2,
                    "explanation": "전통 농업은 AI 기술 활용이 상대적으로 제한적입니다.",
                    "category": "응용 분야"
                },
                {
                    "question": "머신러닝의 주요 유형이 아닌 것은?",
                    "options": ["지도학습", "비지도학습", "강화학습", "수동학습"],
                    "correct_answer": 3,
                    "explanation": "수동학습은 머신러닝의 유형이 아닙니다.",
                    "category": "학습 유형"
                },
                {
                    "question": "CNN이 주로 사용되는 분야는?",
                    "options": ["자연어 처리", "이미지 처리", "음성 인식", "데이터 분석"],
                    "correct_answer": 1,
                    "explanation": "CNN(Convolutional Neural Network)은 주로 이미지 처리에 사용됩니다.",
                    "category": "신경망 유형"
                }
            ]
        else:  # English
            mock_questions = [
                {
                    "question": "What is the main topic of this document?",
                    "options": ["Database design", "Web development", "Machine Learning", "Network security"],
                    "correct_answer": 2,
                    "explanation": "The document primarily discusses machine learning concepts.",
                    "category": "Main Concepts"
                },
                {
                    "question": "What does ACID stand for in databases?",
                    "options": ["Atomicity, Consistency, Isolation, Durability", "Access, Control, Index, Data", "Authentication, Configuration, Integration, Deployment", "Application, Cache, Interface, Database"],
                    "correct_answer": 0,
                    "explanation": "ACID represents the four properties of database transactions.",
                    "category": "Database Concepts"
                },
                {
                    "question": "Which is NOT a software architecture pattern?",
                    "options": ["MVC", "Microservices", "Event-driven", "Database-first"],
                    "correct_answer": 3,
                    "explanation": "Database-first is a development approach, not an architecture pattern.",
                    "category": "Architecture Patterns"
                },
                {
                    "question": "What does CQRS stand for?",
                    "options": ["Command Query Response Segregation", "Command Query Responsibility Segregation", "Complex Query Result System", "Centralized Query Resource Service"],
                    "correct_answer": 1,
                    "explanation": "CQRS stands for Command Query Responsibility Segregation.",
                    "category": "Design Patterns"
                },
                {
                    "question": "Which is a benefit of microservices architecture?",
                    "options": ["Reduced complexity", "Single point of failure", "Independent deployment", "Centralized data"],
                    "correct_answer": 2,
                    "explanation": "Microservices allow for independent deployment of services.",
                    "category": "Architecture Benefits"
                }
            ]
        
        # Return only the requested number of questions
        return mock_questions[:question_count]
    
    def _validate_questions(self, questions: List[Dict[str, Any]]) -> List[Dict[str, Any]]:
        """Validate and filter generated questions"""
        validated = []
        
        for i, question in enumerate(questions):
            try:
                # Check required fields
                if not all(key in question for key in ["question", "options", "correct_answer"]):
                    logger.warning(f"Question {i+1} missing required fields")
                    continue
                
                # Validate options
                options = question.get("options", [])
                if not isinstance(options, list) or len(options) < 2:
                    logger.warning(f"Question {i+1} has invalid options")
                    continue
                
                # Validate correct answer
                correct_answer = question.get("correct_answer")
                if not isinstance(correct_answer, int) or correct_answer < 0 or correct_answer >= len(options):
                    logger.warning(f"Question {i+1} has invalid correct answer")
                    continue
                
                # Clean up question text
                clean_question = {
                    "question_text": str(question["question"]).strip(),
                    "options": [str(opt).strip() for opt in options],
                    "correct_answer_index": int(correct_answer),
                    "explanation": str(question.get("explanation", "")).strip(),
                    "category": question.get("category", "general")
                }
                
                validated.append(clean_question)
                
            except Exception as e:
                logger.warning(f"Failed to validate question {i+1}: {e}")
                continue
        
        logger.info(f"Validated {len(validated)} out of {len(questions)} questions")
        return validated
    
    def _generate_quiz_id(self, document_id: str) -> str:
        """Generate unique quiz ID"""
        timestamp = datetime.utcnow().strftime("%Y%m%d_%H%M%S")
        unique_id = str(uuid.uuid4())[:8]
        return f"quiz_{document_id}_{timestamp}_{unique_id}"
    
    async def get_quiz_by_id(self, quiz_id: str) -> Optional[Dict[str, Any]]:
        """Retrieve quiz by ID"""
        return self.active_quizzes.get(quiz_id)
    
    async def submit_quiz_answers(
        self,
        quiz_id: str,
        user_id: str,
        answers: List[int]
    ) -> Dict[str, Any]:
        """
        Submit and evaluate quiz answers
        
        Args:
            quiz_id: Quiz identifier
            user_id: User identifier
            answers: List of selected answer indices
        
        Returns:
            Dict with evaluation results
        """
        try:
            logger.info(f"Processing quiz submission for user {user_id}")
            
            quiz = self.active_quizzes.get(quiz_id)
            if not quiz:
                return {"success": False, "error": "Quiz not found"}
            
            questions = quiz.get("questions", [])
            if len(answers) != len(questions):
                return {"success": False, "error": "Answer count mismatch"}
            
            # Evaluate answers
            results = []
            correct_count = 0
            
            for i, (question, user_answer) in enumerate(zip(questions, answers)):
                correct_answer = question["correct_answer_index"]
                is_correct = user_answer == correct_answer
                
                if is_correct:
                    correct_count += 1
                
                results.append({
                    "question_index": i,
                    "question": question["question_text"],
                    "user_answer": user_answer,
                    "correct_answer": correct_answer,
                    "is_correct": is_correct,
                    "explanation": question.get("explanation", "")
                })
            
            # Calculate score
            score = (correct_count / len(questions)) * 100
            
            # Store submission record
            submission_record = {
                "quiz_id": quiz_id,
                "user_id": user_id,
                "answers": answers,
                "results": results,
                "score": score,
                "submitted_at": datetime.utcnow().isoformat()
            }
            
            logger.info(f"Quiz submission evaluated: {correct_count}/{len(questions)} correct ({score:.1f}%)")
            
            return {
                "success": True,
                "quiz_id": quiz_id,
                "score": score,
                "correct_count": correct_count,
                "total_questions": len(questions),
                "results": results,
                "message": f"Quiz completed with score {score:.1f}%"
            }
            
        except Exception as e:
            logger.error(f"Quiz submission processing failed: {e}")
            return {"success": False, "error": f"Submission processing failed: {str(e)}"}
    
    async def get_quiz_statistics(self, document_id: str) -> Dict[str, Any]:
        """Get quiz statistics for a document"""
        try:
            # Filter quizzes for the document
            document_quizzes = [
                quiz for quiz in self.active_quizzes.values()
                if quiz.get("document_id") == document_id
            ]
            
            if not document_quizzes:
                return {"success": False, "error": "No quizzes found for document"}
            
            stats = {
                "document_id": document_id,
                "total_quizzes": len(document_quizzes),
                "quiz_types": {
                    "50_percent": len([q for q in document_quizzes if q.get("progress_percentage") == 50]),
                    "100_percent": len([q for q in document_quizzes if q.get("progress_percentage") == 100])
                },
                "difficulty_distribution": {},
                "recent_quizzes": []
            }
            
            # Difficulty distribution
            for quiz in document_quizzes:
                difficulty = quiz.get("difficulty_level", "medium")
                stats["difficulty_distribution"][difficulty] = \
                    stats["difficulty_distribution"].get(difficulty, 0) + 1
            
            # Recent quizzes (last 5)
            sorted_quizzes = sorted(
                document_quizzes,
                key=lambda q: q.get("created_at", ""),
                reverse=True
            )
            
            stats["recent_quizzes"] = [
                {
                    "quiz_id": quiz["quiz_id"],
                    "created_at": quiz.get("created_at"),
                    "question_count": len(quiz.get("questions", [])),
                    "progress_percentage": quiz.get("progress_percentage"),
                    "difficulty_level": quiz.get("difficulty_level")
                }
                for quiz in sorted_quizzes[:5]
            ]
            
            return {"success": True, **stats}
            
        except Exception as e:
            logger.error(f"Failed to get quiz statistics: {e}")
            return {"success": False, "error": str(e)}
    
    async def cleanup_old_quizzes(self, max_age_hours: int = 24):
        """Clean up old quiz records"""
        try:
            current_time = datetime.utcnow()
            cleaned_count = 0
            
            quiz_ids_to_remove = []
            
            for quiz_id, quiz in self.active_quizzes.items():
                created_at_str = quiz.get("created_at")
                if created_at_str:
                    created_at = datetime.fromisoformat(created_at_str)
                    age_hours = (current_time - created_at).total_seconds() / 3600
                    
                    if age_hours > max_age_hours:
                        quiz_ids_to_remove.append(quiz_id)
            
            # Remove old quizzes
            for quiz_id in quiz_ids_to_remove:
                del self.active_quizzes[quiz_id]
                cleaned_count += 1
            
            if cleaned_count > 0:
                logger.info(f"Cleaned up {cleaned_count} old quizzes")
            
        except Exception as e:
            logger.error(f"Quiz cleanup failed: {e}")