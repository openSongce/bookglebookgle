"""
REST API routes for BGBG AI Server
Provides HTTP endpoints for testing and monitoring AI services
"""

from typing import Dict, List, Optional

from fastapi import APIRouter, HTTPException, Depends
from pydantic import BaseModel
from loguru import logger

from src.config.settings import get_settings
from src.services.quiz_service import QuizService
from src.services.proofreading_service import ProofreadingService
from src.services.discussion_service import DiscussionService
from src.services.vector_db import VectorDBManager


router = APIRouter()


# Request/Response Models
class QuizGenerationRequest(BaseModel):
    document_id: str
    content: str
    language: str = "ko"
    progress_percentage: int
    question_count: int = 5
    difficulty_level: str = "medium"


class ProofreadingRequest(BaseModel):
    text: str
    context: Optional[str] = None
    language: str = "ko"
    user_id: str


class DiscussionInitRequest(BaseModel):
    document_id: str
    meeting_id: str
    document_content: str
    participants: List[Dict[str, str]]


class ChatMessageRequest(BaseModel):
    session_id: str
    sender_id: str
    sender_nickname: str
    message: str


class HealthResponse(BaseModel):
    status: str
    version: str
    services: Dict[str, bool]


class RAGQueryRequest(BaseModel):
    query: str
    document_id: Optional[str] = None
    max_results: int = 3


# Health and Status Endpoints
@router.get("/health", response_model=HealthResponse)
async def health_check():
    """Health check endpoint"""
    settings = get_settings()
    
    return HealthResponse(
        status="healthy",
        version="1.0.0",
        services={
            "quiz_generation": settings.ai.ENABLE_QUIZ_GENERATION,
            "proofreading": settings.ai.ENABLE_PROOFREADING,
            "discussion_ai": settings.ai.ENABLE_DISCUSSION_AI,
            "user_analytics": settings.ai.ENABLE_USER_ANALYTICS,
        }
    )


@router.get("/status")
async def service_status():
    """Detailed service status"""
    settings = get_settings()
    
    return {
        "server": {
            "status": "running",
            "host": settings.SERVER_HOST,
            "port": settings.SERVER_PORT,
            "debug": settings.DEBUG,
        },
        "ai_services": {
            "openai_configured": bool(settings.ai.OPENAI_API_KEY),
            "anthropic_configured": bool(settings.ai.ANTHROPIC_API_KEY),
            "mock_responses": settings.ai.MOCK_AI_RESPONSES,
        },
        "features": {
            "quiz_generation": settings.ai.ENABLE_QUIZ_GENERATION,
            "proofreading": settings.ai.ENABLE_PROOFREADING,
            "discussion_ai": settings.ai.ENABLE_DISCUSSION_AI,
            "user_analytics": settings.ai.ENABLE_USER_ANALYTICS,
        }
    }


# AI Service Testing Endpoints (for development)
@router.post("/test/quiz")
async def test_quiz_generation(request: QuizGenerationRequest):
    """Test quiz generation endpoint"""
    try:
        quiz_service = QuizService()
        
        quiz_data = {
            "document_id": request.document_id,
            "content": request.content,
            "language": request.language,
            "progress_percentage": request.progress_percentage,
            "question_count": request.question_count,
            "difficulty_level": request.difficulty_level,
        }
        
        result = await quiz_service.generate_quiz(quiz_data)
        
        if result["success"]:
            return {
                "success": True,
                "message": "Quiz generated successfully",
                "quiz_id": result.get("quiz_id"),
                "questions": result.get("questions", []),
                "question_count": len(result.get("questions", [])),
            }
        else:
            raise HTTPException(status_code=500, detail=result.get("error", "Quiz generation failed"))
            
    except Exception as e:
        logger.error(f"Quiz generation test failed: {e}")
        raise HTTPException(status_code=500, detail=str(e))


@router.post("/test/proofread")
async def test_proofreading(request: ProofreadingRequest):
    """Test proofreading endpoint"""
    try:
        proofreading_service = ProofreadingService()
        
        proofread_data = {
            "original_text": request.text,
            "context_text": request.context,
            "language": request.language,
            "user_id": request.user_id,
        }
        
        result = await proofreading_service.proofread_text(proofread_data)
        
        if result["success"]:
            return {
                "success": True,
                "message": "Text proofread successfully",
                "corrected_text": result.get("corrected_text"),
                "corrections": result.get("corrections", []),
                "correction_count": len(result.get("corrections", [])),
                "confidence_score": result.get("confidence_score", 0.0),
            }
        else:
            raise HTTPException(status_code=500, detail=result.get("error", "Proofreading failed"))
            
    except Exception as e:
        logger.error(f"Proofreading test failed: {e}")
        raise HTTPException(status_code=500, detail=str(e))


@router.post("/test/discussion/init")
async def test_discussion_init(request: DiscussionInitRequest):
    """Test discussion initialization endpoint"""
    try:
        discussion_service = DiscussionService()
        
        init_data = {
            "document_id": request.document_id,
            "meeting_id": request.meeting_id,
            "full_document": request.document_content,
            "participants": request.participants,
        }
        
        result = await discussion_service.initialize_discussion(init_data)
        
        if result["success"]:
            return {
                "success": True,
                "message": "Discussion initialized successfully",
                "session_id": result.get("session_id"),
                "participant_count": len(request.participants),
            }
        else:
            raise HTTPException(status_code=500, detail=result.get("error", "Discussion initialization failed"))
            
    except Exception as e:
        logger.error(f"Discussion initialization test failed: {e}")
        raise HTTPException(status_code=500, detail=str(e))


@router.post("/test/discussion/message")
async def test_chat_message(request: ChatMessageRequest):
    """Test chat message processing endpoint"""
    try:
        discussion_service = DiscussionService()
        
        message_data = {
            "session_id": request.session_id,
            "sender_id": request.sender_id,
            "sender_nickname": request.sender_nickname,
            "message": request.message,
            "timestamp": None,  # Will be set by service
        }
        
        result = await discussion_service.process_chat_message(message_data)
        
        return {
            "success": True,
            "message": "Message processed successfully",
            "ai_response": result.get("ai_response"),
            "suggested_topics": result.get("suggested_topics", []),
            "requires_moderation": result.get("requires_moderation", False),
        }
            
    except Exception as e:
        logger.error(f"Chat message processing test failed: {e}")
        raise HTTPException(status_code=500, detail=str(e))


# Configuration Endpoints
@router.get("/config")
async def get_configuration():
    """Get current configuration (non-sensitive)"""
    settings = get_settings()
    
    return {
        "server": {
            "debug": settings.DEBUG,
            "log_level": settings.LOG_LEVEL,
        },
        "features": {
            "quiz_generation": settings.ai.ENABLE_QUIZ_GENERATION,
            "proofreading": settings.ai.ENABLE_PROOFREADING,
            "discussion_ai": settings.ai.ENABLE_DISCUSSION_AI,
            "user_analytics": settings.ai.ENABLE_USER_ANALYTICS,
        },
        "ai": {
            "mock_responses": settings.ai.MOCK_AI_RESPONSES,
            "korean_model": settings.ai.SPACY_MODEL,
        },
        "cache": {
            "ttl": settings.CACHE_TTL,
        }
    }


# RAG Test Endpoint
@router.post("/test/rag")
async def test_rag_query(request: RAGQueryRequest):
    """Test RAG (Retrieval-Augmented Generation) with Vector DB"""
    try:
        logger.info(f"RAG query: {request.query}")
        
        # Vector DB 초기화
        vector_db = VectorDBManager()
        await vector_db.initialize()
        
        # 유사성 검색 실행
        search_results = await vector_db.similarity_search(
            query=request.query,
            collection_name="documents",
            n_results=request.max_results,
            filter_metadata={"document_id": request.document_id} if request.document_id else None
        )
        
        if not search_results:
            return {
                "success": False,
                "message": "관련 문서를 찾을 수 없습니다.",
                "query": request.query,
                "results": []
            }
        
        # Discussion Service를 통해 RAG 응답 생성
        discussion_service = DiscussionService()
        
        # 검색된 컨텍스트를 하나의 문자열로 결합
        context_chunks = [result["document"] for result in search_results]
        combined_context = "\n\n".join(context_chunks)
        
        # RAG 기반 응답 생성
        if not get_settings().ai.MOCK_AI_RESPONSES:
            # 실제 LLM으로 RAG 응답 생성
            if not hasattr(discussion_service.llm_client, 'openrouter_client') or not discussion_service.llm_client.openrouter_client:
                await discussion_service.llm_client.initialize()
            
            system_message = """당신은 그림형제 동화 전문가입니다. 주어진 문서 내용을 바탕으로 사용자의 질문에 정확하고 자세하게 답변하세요.

답변 시 주의사항:
1. 제공된 문서 내용만을 근거로 답변하세요
2. 문서에 없는 내용은 추측하지 마세요
3. 한국어로 자연스럽게 답변하세요
4. 구체적인 예시나 인용구가 있다면 활용하세요"""

            prompt = f"""사용자 질문: {request.query}

관련 문서 내용:
{combined_context}

위 문서 내용을 바탕으로 사용자의 질문에 답변해주세요."""

            rag_response = await discussion_service.llm_client.generate_completion(
                prompt=prompt,
                system_message=system_message,
                max_tokens=500,
                temperature=0.3
            )
        else:
            rag_response = f"'{request.query}'에 대한 Mock RAG 응답입니다. 검색된 {len(search_results)}개의 관련 문서를 바탕으로 답변합니다."
        
        await vector_db.cleanup()
        
        return {
            "success": True,
            "message": "RAG 검색 및 응답 생성 완료",
            "query": request.query,
            "rag_response": rag_response,
            "retrieved_chunks": len(search_results),
            "search_results": [
                {
                    "similarity": result["similarity"],
                    "content_preview": result["document"][:200] + "..." if len(result["document"]) > 200 else result["document"],
                    "metadata": result["metadata"]
                }
                for result in search_results
            ]
        }
        
    except Exception as e:
        logger.error(f"RAG test failed: {e}")
        raise HTTPException(status_code=500, detail=str(e))