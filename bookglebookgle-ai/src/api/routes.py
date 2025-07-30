
"""
REST API routes for BGBG AI Server
Provides HTTP endpoints for testing and monitoring AI services
"""

from typing import Dict, List, Optional, Any

from fastapi import APIRouter, HTTPException, Depends
from pydantic import BaseModel
from loguru import logger

from src.config.settings import get_settings
from src.services.discussion_service import DiscussionService
from src.services.vector_db import VectorDBManager
from src.models.schemas import OcrBlock, ProcessDocumentRequest


router = APIRouter()

# Dependency Injection for services
def get_vector_db():
    # This could be a more sophisticated dependency management system
    return VectorDBManager()

def get_discussion_service():
    return DiscussionService()

# 모바일 앱용 간단한 요청 모델들


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
            "discussion_ai": settings.ai.ENABLE_DISCUSSION_AI,
            "ocr_processing": True,
            "vector_db": True,
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
            "discussion_ai": settings.ai.ENABLE_DISCUSSION_AI,
            "ocr_processing": True,
            "vector_db": True,
        }
    }


# Core API Endpoints (모바일 앱 gRPC 연동용)


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
            "discussion_ai": settings.ai.ENABLE_DISCUSSION_AI,
            "ocr_processing": True,
            "vector_db": True,
        },
        "ai": {
            "mock_responses": settings.ai.MOCK_AI_RESPONSES,
            "korean_model": settings.ai.SPACY_MODEL,
        },
        "cache": {
            "ttl": settings.CACHE_TTL,
        }
    }


# 간단한 벡터DB 테스트 엔드포인트 (개발용)
@router.post("/test/search")
async def test_vector_search(request: RAGQueryRequest):
    """벡터DB 검색 테스트 (모바일 앱 개발용)"""
    try:
        vector_db = VectorDBManager()
        await vector_db.initialize()
        
        search_results = await vector_db.similarity_search(
            query=request.query,
            collection_name="documents",
            n_results=request.max_results,
            filter_metadata={"document_id": request.document_id} if request.document_id else None
        )
        
        await vector_db.cleanup()
        
        return {
            "success": True,
            "query": request.query,
            "results_count": len(search_results),
            "results": [
                {
                    "similarity": result["similarity"],
                    "content_preview": result["document"][:200] + "..." if len(result["document"]) > 200 else result["document"]
                }
                for result in search_results
            ]
        }
        
    except Exception as e:
        logger.error(f"Vector search test failed: {e}")
        raise HTTPException(status_code=500, detail=str(e))

@router.post("/process-structured-document")
async def process_structured_document(
    request: ProcessDocumentRequest,
    vector_db: VectorDBManager = Depends(get_vector_db) 
) -> Dict[str, Any]:
    """구조화된 OCR 데이터를 처리하여 벡터 DB에 저장"""
    try:
        # OCR 결과를 OcrBlock 모델 리스트로 변환
        ocr_blocks = [OcrBlock(**block) for block in request.ocr_results]

        # 벡터 DB에 저장
        success = await vector_db.store_document_with_positions(
            document_id=request.document_id,
            ocr_blocks=ocr_blocks,
            metadata=request.metadata
        )
        
        if success:
            return {
                "success": True,
                "message": "Document processed and stored successfully",
                "document_id": request.document_id,
                "blocks_count": len(request.ocr_results)
            }
        else:
            raise HTTPException(status_code=500, detail="Failed to store document")
            
    except Exception as e:
        logger.error(f"Error processing structured document: {e}")
        raise HTTPException(status_code=500, detail=str(e))

# 모바일 앱 진도율 기반 API 엔드포인트들

# ProgressDocumentRequest removed - now handled automatically by ProcessPdf and ProcessDocument
# ProgressQuizRequest는 나중에 퀴즈 기능 구현 시 추가 예정

# Removed /mobile/store-progress-document endpoint - now handled automatically in ProcessPdf and ProcessDocument

# 진도율 기반 퀴즈 생성 API는 나중에 구현 예정
# 위치 기반 토론 주제 생성은 모바일 앱에서 불필요하므로 제거
