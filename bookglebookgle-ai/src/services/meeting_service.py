"""
Meeting Service for BGBG AI Server
모든 모임 타입의 종료를 처리하는 통합 서비스
"""

import asyncio
from typing import Dict, Any, Optional

from loguru import logger

from src.config.settings import get_settings
from src.services.vector_db import VectorDBManager
from src.services.vector_db_cleanup_service import VectorDBCleanupService
from src.services.discussion_service import DiscussionService


class MeetingService:
    """모든 모임 타입의 종료를 처리하는 통합 서비스"""
    
    def __init__(self):
        self.settings = get_settings()
        self.cleanup_service: Optional[VectorDBCleanupService] = None
        self.discussion_service: Optional[DiscussionService] = None
        
    async def initialize(self, vector_db: VectorDBManager, discussion_service: DiscussionService):
        """
        서비스 초기화
        
        Args:
            vector_db: VectorDBManager 인스턴스
            discussion_service: DiscussionService 인스턴스
        """
        try:
            self.cleanup_service = VectorDBCleanupService(vector_db, self.settings)
            self.discussion_service = discussion_service
            
            logger.info("MeetingService initialized successfully")
            
        except Exception as e:
            logger.error(f"Failed to initialize MeetingService: {e}")
            raise
    
    async def end_meeting(self, meeting_id: str, meeting_type: str, **kwargs) -> Dict[str, Any]:
        """
        모임 종료 처리 (모든 모임 타입 지원)
        
        Args:
            meeting_id: 독서 모임 ID
            meeting_type: 모임 타입 ("discussion", "quiz", "proofreading")
            **kwargs: 모임 타입별 추가 파라미터
            
        Returns:
            Dict with meeting end result
        """
        try:
            logger.info(f"🏁 Ending {meeting_type} meeting: {meeting_id}")
            
            # 모임 타입 검증
            if meeting_type not in self.settings.vector_db.SUPPORTED_MEETING_TYPES:
                error_msg = f"Unsupported meeting type: {meeting_type}"
                logger.error(error_msg)
                return {
                    "success": False,
                    "message": error_msg,
                    "meeting_type": meeting_type
                }
            
            # 모임 타입별 종료 처리
            result = await self._end_meeting_core(meeting_id, meeting_type, **kwargs)
            
            if not result["success"]:
                return result
            
            # 모든 모임 타입에 대해 벡터 DB 정리 (비동기)
            if self.cleanup_service and self.cleanup_service.is_cleanup_enabled():
                asyncio.create_task(
                    self._safe_cleanup_with_delay(meeting_id, meeting_type)
                )
                logger.info(f"🧹 Vector DB cleanup scheduled for {meeting_type} meeting: {meeting_id}")
            else:
                logger.info(f"Vector DB cleanup disabled for {meeting_type} meeting: {meeting_id}")
            
            return {
                "success": True,
                "message": f"{meeting_type.title()} meeting ended successfully",
                "meeting_type": meeting_type,
                "meeting_id": meeting_id
            }
            
        except Exception as e:
            error_msg = f"{meeting_type.title()} meeting end failed: {str(e)}"
            logger.error(error_msg)
            return {
                "success": False,
                "message": error_msg,
                "meeting_type": meeting_type,
                "error": str(e)
            }
    
    async def _end_meeting_core(self, meeting_id: str, meeting_type: str, **kwargs) -> Dict[str, Any]:
        """
        모임 타입별 핵심 종료 로직
        
        Args:
            meeting_id: 독서 모임 ID
            meeting_type: 모임 타입
            **kwargs: 추가 파라미터
            
        Returns:
            Dict with core end result
        """
        try:
            if meeting_type == "discussion":
                # 토론 모임 종료
                session_id = kwargs.get("session_id")
                if not session_id:
                    return {
                        "success": False,
                        "message": "session_id is required for discussion meetings"
                    }
                
                if not self.discussion_service:
                    return {
                        "success": False,
                        "message": "DiscussionService not initialized"
                    }
                
                result = await self.discussion_service.end_discussion(meeting_id, session_id)
                logger.info(f"✅ Discussion meeting ended: {meeting_id}, session: {session_id}")
                return result
                
            elif meeting_type == "quiz":
                # 퀴즈 모임 종료 (현재는 로그만)
                logger.info(f"✅ Quiz meeting ended: {meeting_id}")
                return {
                    "success": True,
                    "message": "Quiz meeting ended successfully"
                }
                
            elif meeting_type == "proofreading":
                # 첨삭 모임 종료 (현재는 로그만)
                logger.info(f"✅ Proofreading meeting ended: {meeting_id}")
                return {
                    "success": True,
                    "message": "Proofreading meeting ended successfully"
                }
                
            else:
                # 지원하지 않는 모임 타입
                error_msg = f"Unsupported meeting type: {meeting_type}"
                logger.error(error_msg)
                return {
                    "success": False,
                    "message": error_msg
                }
                
        except Exception as e:
            logger.error(f"Core meeting end failed for {meeting_type} meeting {meeting_id}: {e}")
            return {
                "success": False,
                "message": f"Core meeting end failed: {str(e)}"
            }
    
    async def _safe_cleanup_with_delay(self, meeting_id: str, meeting_type: str):
        """
        안전한 정리 작업 (지연 시간 포함)
        
        Args:
            meeting_id: 독서 모임 ID
            meeting_type: 모임 타입
        """
        try:
            # 설정된 지연 시간만큼 대기
            delay = self.settings.vector_db.CLEANUP_DELAY_SECONDS
            logger.info(f"Waiting {delay} seconds before cleanup for {meeting_type} meeting {meeting_id}")
            await asyncio.sleep(delay)
            
            # 정리 작업 수행
            result = await self.cleanup_service.cleanup_meeting_collection(meeting_id, meeting_type)
            
            if result["success"]:
                logger.info(f"✅ Vector DB cleanup completed for {meeting_type} meeting {meeting_id}: "
                           f"deleted {result.get('documents_deleted', 0)} documents")
            else:
                logger.error(f"❌ Vector DB cleanup failed for {meeting_type} meeting {meeting_id}: "
                           f"{result.get('error', 'Unknown error')}")
                
        except Exception as e:
            logger.error(f"Vector DB cleanup failed for {meeting_type} meeting {meeting_id}: {e}")
            # 정리 실패는 모임 종료에 영향을 주지 않음
    
    async def get_meeting_status(self, meeting_id: str) -> Dict[str, Any]:
        """
        모임 상태 조회
        
        Args:
            meeting_id: 독서 모임 ID
            
        Returns:
            Dict with meeting status
        """
        try:
            if not self.cleanup_service:
                return {
                    "success": False,
                    "message": "CleanupService not initialized"
                }
            
            meeting_status = await self.cleanup_service.get_meeting_status(meeting_id)
            
            return {
                "success": True,
                "meeting_id": meeting_status.meeting_id,
                "is_active": meeting_status.is_active,
                "last_activity": meeting_status.last_activity.isoformat(),
                "participant_count": meeting_status.participant_count,
                "has_vector_collection": meeting_status.has_vector_collection
            }
            
        except Exception as e:
            logger.error(f"Failed to get meeting status for {meeting_id}: {e}")
            return {
                "success": False,
                "message": f"Failed to get meeting status: {str(e)}"
            }
    
    async def manual_cleanup_meeting(self, meeting_id: str, force: bool = False) -> Dict[str, Any]:
        """
        수동 모임 정리
        
        Args:
            meeting_id: 독서 모임 ID
            force: 강제 정리 여부
            
        Returns:
            Dict with cleanup result
        """
        try:
            logger.info(f"Manual cleanup requested for meeting {meeting_id}, force: {force}")
            
            if not self.cleanup_service:
                return {
                    "success": False,
                    "message": "CleanupService not initialized"
                }
            
            result = await self.cleanup_service.manual_cleanup(meeting_id, force)
            
            if result["success"]:
                logger.info(f"✅ Manual cleanup completed for meeting {meeting_id}")
            else:
                logger.error(f"❌ Manual cleanup failed for meeting {meeting_id}: {result.get('error')}")
            
            return result
            
        except Exception as e:
            logger.error(f"Manual cleanup failed for meeting {meeting_id}: {e}")
            return {
                "success": False,
                "message": f"Manual cleanup failed: {str(e)}"
            }
    
    def is_initialized(self) -> bool:
        """서비스 초기화 상태 확인"""
        return (
            self.cleanup_service is not None and 
            self.discussion_service is not None
        )
    
    async def list_active_meetings(self) -> Dict[str, Any]:
        """
        활성 모임 목록 조회
        
        Returns:
            Dict with active meetings list
        """
        try:
            if not self.cleanup_service:
                return {
                    "success": False,
                    "message": "CleanupService not initialized"
                }
            
            # 벡터 DB에서 모임 컬렉션 목록 조회
            collections = await self.cleanup_service.vector_db.list_meeting_collections()
            
            active_meetings = []
            for collection_name in collections:
                # bookclub_{meeting_id}_documents 형식에서 meeting_id 추출
                if collection_name.startswith("bookclub_") and collection_name.endswith("_documents"):
                    meeting_id = collection_name[9:-10]  # "bookclub_"와 "_documents" 제거
                    
                    # 각 모임의 상태 확인
                    status_result = await self.get_meeting_status(meeting_id)
                    if status_result["success"]:
                        active_meetings.append({
                            "meeting_id": meeting_id,
                            "collection_name": collection_name,
                            "is_active": status_result["is_active"],
                            "has_vector_collection": status_result["has_vector_collection"]
                        })
            
            logger.info(f"Found {len(active_meetings)} active meetings")
            return {
                "success": True,
                "active_meetings": active_meetings,
                "total_count": len(active_meetings)
            }
            
        except Exception as e:
            logger.error(f"Failed to list active meetings: {e}")
            return {
                "success": False,
                "message": f"Failed to list active meetings: {str(e)}"
            }