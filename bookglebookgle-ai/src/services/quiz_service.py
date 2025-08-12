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

from src.services.llm_client import LLMClient, QuizLLMClient, LLMProvider
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
            
            # Initialize LLM client only if not already set by ServiceInitializer
            if self.llm_client is None:
                logger.info("🔧 Creating new LLM client for Quiz Service")
                self.llm_client = LLMClient()
                await self.llm_client.initialize()
                self.quiz_llm_client = QuizLLMClient(self.llm_client)
            else:
                logger.info("🔄 Using existing LLM client for Quiz Service")
                # quiz_llm_client는 ServiceInitializer에서 이미 설정됨
            
            # Note: VectorDB is managed by ServiceInitializer, not initialized here
            # self.vector_db is already set by ServiceInitializer - do not reset to None!
            
            logger.info("Quiz Service initialized successfully")
            
        except Exception as e:
            logger.error(f"Failed to initialize Quiz Service: {e}")
            raise
    
    async def generate_quiz(self, quiz_data: Dict[str, Any]) -> Dict[str, Any]:
        """
        Generate quiz questions from document content using VectorDB
        
        Args:
            quiz_data: Dict containing:
                - document_id: str
                - meeting_id: str
                - progress_percentage: int (50 or 100)
        
        Returns:
            Dict with success status, quiz_id, and questions
        """
        try:
            logger.info(f"Generating quiz for document: {quiz_data.get('document_id')}")
            
            # DEBUG: VectorDB 상태 확인
            logger.info(f"🔍 DEBUG - VectorDB status: {'Available' if self.vector_db else 'None'}")
            if self.vector_db:
                logger.info(f"🔍 DEBUG - VectorDB type: {type(self.vector_db)}")
            
            # Validate input
            if not self._validate_quiz_request(quiz_data):
                return {"success": False, "error": "Invalid quiz request data"}
            
            # VectorDB에서 진도율별 문서 내용 검색 (fallback 포함)
            combined_content = ""
            if self.vector_db:
                try:
                    content_chunks = await self.vector_db.search_by_progress(
                        meeting_id=quiz_data["meeting_id"],
                        document_id=quiz_data["document_id"],
                        progress_percentage=quiz_data["progress_percentage"],
                        max_chunks=3
                    )
                    
                    if content_chunks:
                        # VectorDB에서 이미 올바른 진도율 청크를 반환하므로 추가 필터링 불필요
                        combined_content = "\n\n".join(content_chunks)
                        logger.info(f"Retrieved {len(content_chunks)} chunks from VectorDB for {quiz_data['progress_percentage']}% progress")
                    else:
                        logger.warning(f"No content found in VectorDB for document {quiz_data['document_id']} at {quiz_data['progress_percentage']}% progress")
                except Exception as e:
                    logger.warning(f"VectorDB search failed: {e}")
            else:
                logger.warning("VectorDB not available, using fallback content")
            
            # Fallback: VectorDB에서 내용을 가져올 수 없을 때 기본 내용 사용
            if not combined_content:
                progress = quiz_data["progress_percentage"]
                if progress == 50:
                    combined_content = f"문서 전반부(50% 진도) 내용: 기본 개념과 도입부 설명이 포함되어 있습니다. 주요 용어와 기초 이론을 다루며, 이해하기 쉬운 예시들로 구성되어 있습니다."
                else:  # 100%
                    combined_content = f"문서 전체(100% 진도) 내용: 기본 개념부터 심화 내용까지 포괄적으로 다룹니다. 이론적 배경, 실무 적용 사례, 결론 및 요약이 포함되어 있습니다."
                logger.info(f"Using fallback content for {progress}% progress")
            
            logger.info(f"📝 Content prepared, length: {len(combined_content)} characters")
            
            # 진도율별 컨텐츠 품질 확인
            progress = quiz_data["progress_percentage"]
            if combined_content and len(combined_content) > 50:
                logger.info(f"✅ Using VectorDB content for {progress}% progress quiz")
                content_source = "vectordb"
            else:
                logger.info(f"⚠️ VectorDB content insufficient, using fallback for {progress}% progress")
                content_source = "fallback"
            
            quiz_data_with_content = {
                **quiz_data,
                "content": combined_content,
                "content_source": content_source,
                "language": "ko",
                "question_count": 2,  # 2개로 조정
                "difficulty_level": "medium"
            }
            
            logger.info(f"🎯 Starting question generation process...")
            logger.info(f"🔍 DEBUG - MOCK_AI_RESPONSES: {self.settings.ai.MOCK_AI_RESPONSES}")
            logger.info(f"🔍 DEBUG - quiz_llm_client available: {'Yes' if self.quiz_llm_client else 'No'}")
            
            # 실제 LLM 연동 또는 Mock 선택
            if not self.settings.ai.MOCK_AI_RESPONSES and self.quiz_llm_client:
                logger.info("🤖 Using real LLM for quiz generation...")
                # 실제 LLM을 사용한 퀴즈 생성
                try:
                    llm_questions = await self._generate_llm_questions(quiz_data_with_content)
                    logger.info(f"🎯 LLM generation completed, result: {'Success' if llm_questions else 'Failed'}")
                    if llm_questions:
                        mock_questions = llm_questions
                    else:
                        logger.warning("LLM quiz generation failed, falling back to mock")
                        mock_questions = self._generate_mock_questions(quiz_data_with_content)
                except Exception as e:
                    logger.error(f"LLM generation error: {e}")
                    logger.info("🔄 Falling back to mock questions...")
                    mock_questions = self._generate_mock_questions(quiz_data_with_content)
            else:
                logger.info("🎭 Using mock questions...")
                # Mock 응답 사용
                mock_questions = self._generate_mock_questions(quiz_data_with_content)
            
            logger.info(f"✅ Questions generated: {len(mock_questions) if mock_questions else 0}")
            
            # Validate generated questions
            validated_questions = self._validate_questions(mock_questions)
            logger.info(f"✅ Questions validated: {len(validated_questions) if validated_questions else 0}")
            
            if not validated_questions:
                return {"success": False, "error": "Failed to generate valid questions"}
            
            # Create quiz record
            quiz_id = self._generate_quiz_id(quiz_data["document_id"])
            quiz_record = {
                "quiz_id": quiz_id,
                "document_id": quiz_data["document_id"],
                "meeting_id": quiz_data["meeting_id"],
                "progress_percentage": quiz_data["progress_percentage"],
                "questions": validated_questions,
                "created_at": datetime.utcnow().isoformat(),
                "difficulty_level": "medium",
                "language": "ko",
                "question_count": 4
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
        required_fields = ["document_id", "meeting_id", "progress_percentage"]
        
        for field in required_fields:
            if field not in quiz_data or not quiz_data[field]:
                logger.error(f"Missing required field: {field}")
                return False
        
        # Validate progress percentage
        progress = quiz_data.get("progress_percentage")
        if progress not in [50, 100]:
            logger.error(f"Invalid progress percentage: {progress}. Must be 50 or 100")
            return False
        
        return True
    
    def _generate_mock_questions(self, quiz_data: Dict[str, Any]) -> List[Dict[str, Any]]:
        """Generate mock quiz questions for testing"""
        content = quiz_data.get("content", "")
        progress = quiz_data.get("progress_percentage", 100)
        question_count = 4  # 고정값
        
        # Mock questions based on progress percentage
        if progress == 50:
            mock_questions = [
                {
                    "question": "문서 전반부(50% 진도)의 주요 내용은 무엇입니까?",
                    "options": ["기본 개념 소개", "심화 내용", "결론 및 요약", "참고 자료"],
                    "correct_answer": 0,
                    "explanation": "문서 전반부는 주로 기본 개념을 소개하는 내용입니다.",
                    "category": "전반부 내용"
                },
                {
                    "question": "문서에서 처음 등장하는 핵심 용어는?",
                    "options": ["기본 용어", "전문 용어", "고급 용어", "결론 용어"],
                    "correct_answer": 0,
                    "explanation": "문서 초반부에는 기본적인 용어들이 주로 등장합니다.",
                    "category": "용어 이해"
                },
                {
                    "question": "문서 전반부에서 강조하는 주요 포인트는?",
                    "options": ["기초 이해", "실무 적용", "고급 기법", "최종 평가"],
                    "correct_answer": 0,
                    "explanation": "전반부에서는 기초적인 이해를 강조합니다.",
                    "category": "학습 목표"
                },
                {
                    "question": "문서 전반부의 구성 방식은?",
                    "options": ["단계별 설명", "무작위 배치", "결론 우선", "참고자료 위주"],
                    "correct_answer": 0,
                    "explanation": "전반부는 단계적으로 내용을 설명하는 구성입니다.",
                    "category": "구성 방식"
                }
            ]
        else:  # progress == 100
            mock_questions = [
                {
                    "question": "전체 문서(100% 진도)의 핵심 메시지는?",
                    "options": ["전체적 이해", "부분적 지식", "기초 개념", "세부 사항"],
                    "correct_answer": 0,
                    "explanation": "전체 문서를 통해 포괄적인 이해를 목표로 합니다.",
                    "category": "전체 요약"
                },
                {
                    "question": "문서 후반부에서 다루는 고급 내용은?",
                    "options": ["심화 개념", "기본 개념", "도입부", "목차"],
                    "correct_answer": 0,
                    "explanation": "후반부에는 심화된 개념들이 주로 다뤄집니다.",
                    "category": "후반부 내용"
                },
                {
                    "question": "문서의 결론 부분에서 제시하는 것은?",
                    "options": ["종합적 정리", "새로운 시작", "기초 설명", "용어 정의"],
                    "correct_answer": 0,
                    "explanation": "결론 부분에서는 전체 내용을 종합적으로 정리합니다.",
                    "category": "결론"
                },
                {
                    "question": "전체 문서를 통해 얻을 수 있는 최종 이해는?",
                    "options": ["완전한 이해", "부분적 지식", "기초 수준", "입문 수준"],
                    "correct_answer": 0,
                    "explanation": "전체 문서를 통해 주제에 대한 완전한 이해를 얻을 수 있습니다.",
                    "category": "최종 목표"
                }
            ]
        
        logger.info(f"🎯 Generated {len(mock_questions)} mock questions for {progress}% progress")
        # Return fixed 2 questions (간소화)
        return mock_questions[:2]
    
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
            logger.info("🤖 Starting LLM quiz generation...")
            content = quiz_data.get("content", "")
            language = quiz_data.get("language", "ko")
            question_count = quiz_data.get("question_count", 5)
            difficulty = quiz_data.get("difficulty_level", "medium")
            progress = quiz_data.get("progress_percentage", 100)
            
            if not content:
                logger.error("No content provided for LLM quiz generation")
                return None
            
            logger.info(f"📊 Quiz parameters: {question_count} questions, {difficulty} difficulty, {progress}% progress")
            logger.info(f"📝 Content length: {len(content)} characters")
            
            # 시스템 메시지와 프롬프트 구성 (간소화)
            system_message = """문서 기반 객관식 퀴즈를 JSON으로 생성하세요.

형식: [{"question":"질문","options":["A","B","C","D"],"correct_answer":0,"explanation":"설명"}]

JSON만 응답하세요."""
            
            # 진도율별 컨텐츠 처리 (차별화)
            if progress == 50:
                # 50% 진도: 앞부분 위주 + 진도율 명시
                content_preview = content[:600] if len(content) > 600 else content
                progress_context = "문서 전반부(50% 진도)의 기본 개념과 도입부 내용"
            else:  # 100%
                # 100% 진도: 뒷부분 포함 + 전체 맥락
                if len(content) > 600:
                    # 앞부분 300자 + 뒷부분 300자로 전체 맥락 포함
                    front_part = content[:300]
                    back_part = content[-300:]
                    content_preview = f"{front_part}...[중간 생략]...{back_part}"
                else:
                    content_preview = content
                progress_context = "문서 전체(100% 완독)의 종합적 내용과 결론"
            
            prompt = f"""내용: {content_preview}

맥락: {progress_context}
진도: {progress}%
2개 문제 생성"""
            
            logger.info("🔗 Calling LLM client...")
            
            # LLM 클라이언트를 통한 퀴즈 생성 (최적화된 파라미터)
            response = await self.llm_client.generate_completion(
                prompt=prompt,
                system_message=system_message,
                max_tokens=1200,  # 토큰 수 줄임 (토론과 비슷한 수준)
                temperature=0.5,  # 온도 낮춤 (더 일관된 응답)
                provider=LLMProvider.GMS
            )
            
            logger.info(f"✅ LLM response received, length: {len(response) if response else 0} characters")
            logger.debug(f"LLM Response preview: {response[:500] if response else 'None'}...")  # 디버깅용 로그
            
            if not response:
                logger.error("Empty response from LLM")
                return None
            
            # 강화된 JSON 파싱
            logger.info("🔄 Starting JSON parsing...")
            
            try:
                # 1단계: 응답 정리
                cleaned_response = self._clean_json_response(response)
                logger.debug(f"Cleaned response preview: {cleaned_response[:300] if cleaned_response else 'None'}...")
                
                # 2단계: 직접 JSON 파싱 시도
                try:
                    questions = json.loads(cleaned_response)
                    if isinstance(questions, list):
                        logger.info("✅ Successfully parsed JSON array directly")
                        return self._process_llm_questions(questions)
                except json.JSONDecodeError as e:
                    logger.debug(f"Direct JSON parsing failed: {e}")
                
                # 3단계: 배열 추출 시도
                logger.info("🔍 Attempting array extraction...")
                array_match = re.search(r'\[.*?\]', cleaned_response, re.DOTALL)
                if array_match:
                    try:
                        json_str = array_match.group()
                        questions = json.loads(json_str)
                        if isinstance(questions, list):
                            logger.info("✅ Successfully parsed JSON array from match")
                            return self._process_llm_questions(questions)
                    except json.JSONDecodeError as e:
                        logger.debug(f"Array extraction parsing failed: {e}")
                
                # 4단계: 개별 객체 추출 및 배열 구성
                logger.info("🔧 Attempting individual object parsing...")
                questions = []
                # 중괄호 균형 맞추기 - 더 정확한 정규식
                objects = re.findall(r'\{[^{}]*(?:\{[^{}]*\}[^{}]*)*\}', cleaned_response)
                
                for i, obj_str in enumerate(objects):
                    try:
                        # 개별 객체 정리
                        fixed_obj = self._fix_json_object(obj_str)
                        question = json.loads(fixed_obj)
                        questions.append(question)
                        logger.debug(f"✅ Successfully parsed object {i+1}")
                    except json.JSONDecodeError as e:
                        logger.warning(f"Failed to parse individual JSON object {i+1}: {e}")
                        logger.debug(f"Failed object: {obj_str}")
                        continue
                
                if questions:
                    logger.info(f"✅ Successfully parsed {len(questions)} questions from individual objects")
                    return self._process_llm_questions(questions)
                else:
                    logger.warning("❌ No valid questions found after all parsing attempts")
                    return None
                    
            except Exception as e:
                logger.error(f"JSON parsing completely failed: {e}")
                logger.debug(f"Original response: {response}")
                return None
                
        except Exception as e:
            logger.error(f"LLM quiz generation failed: {e}")
            import traceback
            logger.debug(f"Traceback: {traceback.format_exc()}")
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

    async def cleanup_meeting_quizzes(self, meeting_id: str) -> Dict[str, Any]:
        """
        특정 미팅과 관련된 모든 퀴즈 삭제
        
        Args:
            meeting_id: 삭제할 미팅 ID
            
        Returns:
            Dict with cleanup result
        """
        try:
            logger.info(f"Starting quiz cleanup for meeting: {meeting_id}")
            
            quiz_ids_to_remove = []
            
            # 해당 미팅의 퀴즈들 찾기
            for quiz_id, quiz in self.active_quizzes.items():
                if quiz.get("meeting_id") == meeting_id:
                    quiz_ids_to_remove.append(quiz_id)
            
            # 퀴즈 삭제
            cleaned_count = 0
            for quiz_id in quiz_ids_to_remove:
                del self.active_quizzes[quiz_id]
                cleaned_count += 1
                logger.debug(f"Removed quiz: {quiz_id}")
            
            logger.info(f"✅ Cleaned up {cleaned_count} quizzes for meeting: {meeting_id}")
            
            return {
                "success": True,
                "meeting_id": meeting_id,
                "cleaned_count": cleaned_count,
                "message": f"Successfully cleaned up {cleaned_count} quizzes"
            }
            
        except Exception as e:
            logger.error(f"Quiz cleanup failed for meeting {meeting_id}: {e}")
            return {
                "success": False,
                "meeting_id": meeting_id,
                "error": str(e),
                "message": f"Quiz cleanup failed: {str(e)}"
            }