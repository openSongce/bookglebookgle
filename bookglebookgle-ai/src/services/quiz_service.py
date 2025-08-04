"""
Quiz Generation Service for BGBG AI Server
Handles quiz creation from document content with progress-based triggers
"""

import asyncio
import json
import re
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
            
            # Note: VectorDB is managed by ServiceInitializer, not initialized here
            self.vector_db = None  # Will be set by ServiceInitializer if needed
            
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
            
            # 실제 LLM 연동 또는 Mock 선택
            if not self.settings.ai.MOCK_AI_RESPONSES and self.quiz_llm_client:
                # 실제 LLM을 사용한 퀴즈 생성
                llm_questions = await self._generate_llm_questions(quiz_data)
                if llm_questions:
                    mock_questions = llm_questions
                else:
                    logger.warning("LLM quiz generation failed, falling back to mock")
                    mock_questions = self._generate_mock_questions(quiz_data)
            else:
                # Mock 응답 사용
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
    
    def _clean_json_response(self, response: str) -> str:
        """LLM 응답에서 JSON을 정리하고 수정"""
        # 마크다운 코드 블록 제거
        response = re.sub(r'```json\s*|\s*```', '', response)
        
        # 불필요한 텍스트 제거
        response = re.sub(r'^[^\[\{]*', '', response)
        response = re.sub(r'[^}\]]*$', '', response)
        
        # 일반적인 JSON 오류 수정
        response = re.sub(r',(\s*[}\]])', r'\1', response)  # 마지막 쉼표 제거
        response = re.sub(r'(["\w])\s*\n\s*(["\w])', r'\1, \2', response)  # 누락된 쉼표 추가
        
        return response.strip()
    
    def _fix_json_object(self, obj_str: str) -> str:
        """개별 JSON 객체의 일반적인 오류 수정"""
        # 누락된 쉼표 추가 (배열 내)
        obj_str = re.sub(r'"\s*"', '", "', obj_str)
        obj_str = re.sub(r'"\s*]', '"]', obj_str)
        
        # 누락된 쉼표 추가 (객체 속성 간)
        obj_str = re.sub(r'"\s*"([a-zA-Z_])', r'", "\1', obj_str)
        obj_str = re.sub(r'(\d+)\s*"', r'\1, "', obj_str)
        
        return obj_str
    
    def _is_valid_question_structure(self, q: Dict) -> bool:
        """질문 구조가 유효한지 검증"""
        required_keys = ["question", "options", "correct_answer"]
        
        if not all(key in q for key in required_keys):
            return False
        
        options = q.get("options", [])
        if not isinstance(options, list) or len(options) < 2:
            return False
        
        correct_answer = q.get("correct_answer")
        try:
            correct_idx = int(correct_answer)
            if correct_idx < 0 or correct_idx >= len(options):
                return False
        except (ValueError, TypeError):
            return False
        
        return True
    
    def _process_llm_questions(self, questions: List[Dict]) -> List[Dict[str, Any]]:
        """LLM이 생성한 질문들을 표준 형식으로 변환"""
        formatted_questions = []
        
        for q in questions:
            if isinstance(q, dict) and self._is_valid_question_structure(q):
                formatted_questions.append({
                    "question": str(q.get("question", "")).strip(),
                    "options": [str(opt).strip() for opt in q.get("options", [])],
                    "correct_answer": int(q.get("correct_answer", 0)),
                    "explanation": str(q.get("explanation", "")).strip(),
                    "category": "LLM 생성"
                })
        
        return formatted_questions
    
    async def _generate_llm_questions(self, quiz_data: Dict[str, Any]) -> Optional[List[Dict[str, Any]]]:
        """LLM을 사용하여 실제 퀴즈 생성"""
        try:
            content = quiz_data.get("content", "")
            language = quiz_data.get("language", "ko")
            question_count = quiz_data.get("question_count", 5)
            difficulty = quiz_data.get("difficulty_level", "medium")
            progress = quiz_data.get("progress_percentage", 100)
            
            if not content:
                logger.error("No content provided for LLM quiz generation")
                return None
            
            # 시스템 메시지와 프롬프트 구성
            system_message = """당신은 교육 전문가입니다. 주어진 문서 내용을 바탕으로 객관식 퀴즈를 생성해주세요.

CRITICAL: 반드시 유효한 JSON 배열 형식으로만 응답하세요. 다른 텍스트는 포함하지 마세요.

정확한 형식:
[
  {
    "question": "질문 내용",
    "options": ["선택지1", "선택지2", "선택지3", "선택지4"],
    "correct_answer": 0,
    "explanation": "정답 설명"
  }
]

주의사항:
- 모든 문자열은 쌍따옴표로 감싸세요
- 배열 요소 간 쉼표를 빠뜨리지 마세요
- 마지막 요소 뒤에는 쉼표를 붙이지 마세요
- 모든 질문과 선택지는 한국어로 작성하세요"""
            
            prompt = f"""다음 문서 내용을 바탕으로 {question_count}개의 객관식 퀴즈를 생성해주세요.

문서 내용:
{content[:2000]}  # 토큰 제한을 위해 내용 제한

진도율: {progress}%
난이도: {difficulty}
언어: {language}

각 문제는 4개의 선택지를 가져야 하며, 정답은 0-3 중 하나의 인덱스여야 합니다.
반드시 JSON 배열 형태로만 응답하세요."""
            
            # LLM 클라이언트를 통한 퀴즈 생성
            response = await self.llm_client.generate_completion(
                prompt=prompt,
                system_message=system_message,
                max_tokens=2000,
                temperature=0.7
            )
            
            logger.debug(f"LLM Response: {response[:500]}...")  # 디버깅용 로그
            
            # 강화된 JSON 파싱
            
            try:
                # 1단계: 응답 정리
                cleaned_response = self._clean_json_response(response)
                logger.debug(f"Cleaned response: {cleaned_response[:300]}...")
                
                # 2단계: 직접 JSON 파싱 시도
                try:
                    questions = json.loads(cleaned_response)
                    if isinstance(questions, list):
                        logger.info("Successfully parsed JSON array directly")
                        return self._process_llm_questions(questions)
                except json.JSONDecodeError as e:
                    logger.debug(f"Direct JSON parsing failed: {e}")
                
                # 3단계: 배열 추출 시도
                array_match = re.search(r'\[.*?\]', cleaned_response, re.DOTALL)
                if array_match:
                    try:
                        json_str = array_match.group()
                        questions = json.loads(json_str)
                        if isinstance(questions, list):
                            logger.info("Successfully parsed JSON array from match")
                            return self._process_llm_questions(questions)
                    except json.JSONDecodeError as e:
                        logger.debug(f"Array extraction parsing failed: {e}")
                
                # 4단계: 개별 객체 추출 및 배열 구성
                logger.debug("Attempting individual object parsing")
                questions = []
                # 중괄호 균형 맞추기 - 더 정확한 정규식
                objects = re.findall(r'\{[^{}]*(?:\{[^{}]*\}[^{}]*)*\}', cleaned_response)
                
                for i, obj_str in enumerate(objects):
                    try:
                        # 개별 객체 정리
                        fixed_obj = self._fix_json_object(obj_str)
                        question = json.loads(fixed_obj)
                        questions.append(question)
                        logger.debug(f"Successfully parsed object {i+1}")
                    except json.JSONDecodeError as e:
                        logger.warning(f"Failed to parse individual JSON object {i+1}: {e}")
                        logger.debug(f"Failed object: {obj_str}")
                        continue
                
                if questions:
                    logger.info(f"Successfully parsed {len(questions)} questions from individual objects")
                    return self._process_llm_questions(questions)
                else:
                    logger.warning("No valid questions found after all parsing attempts")
                    return None
                    
            except Exception as e:
                logger.error(f"JSON parsing completely failed: {e}")
                logger.debug(f"Original response: {response}")
                return None
                
        except Exception as e:
            logger.error(f"LLM quiz generation failed: {e}")
            return None
    
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