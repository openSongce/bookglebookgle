"""
Vector Database Cleanup Service
독서 모임 종료 시 벡터 데이터베이스 정리를 담당하는 서비스
"""

import asyncio
from dataclasses import dataclass
from datetime import datetime
from typing import Dict, Any, Optional

from loguru import logger

from src.config.settings import get_settings
from src.services.vector_db import VectorDBManager


@dataclass
class CleanupResult:
    """정리 작업 결과를 나타내는 데이터 모델"""
    success: bool
    meeting_id: str
    meeting_type: str
    collection_name: str
    documents_deleted: int
    error_message: Optional[str] = None
    cleanup_timestamp: datetime = None
    cleanup_duration_ms: int = 0

    def __post_init__(self):
        if self.cleanup_timestamp is None:
            self.cleanup_timestamp = datetime.now()


@dataclass
class MeetingStatus:
    """독서 모임 상태를 추적하는 데이터 모델"""
    meeting_id: str
    is_active: bool
    last_activity: datetime
    participant_count: int
    has_vector_collection: bool


class VectorDBCleanupService:
    """벡터 데이터베이스 정리 전용 서비스"""
    
    def __init__(self, vector_db_manager: VectorDBManager, settings=None):
        self.vector_db = vector_db_manager
        self.settings = settings or get_settings()
        
    async def cleanup_meeting_collection(self, meeting_id: str, meeting_type: str) -> Dict[str, Any]:
        """
        독서 모임별 컬렉션 정리
        
        Args:
            meeting_id: 독서 모임 ID
            meeting_type: 모임 타입 (discussion, quiz, proofreading)
            
        Returns:
            Dict with cleanup result
        """
        start_time = datetime.now()
        
        try:
            logger.info(f"Starting vector DB cleanup for {meeting_type} meeting: {meeting_id}")
            
            # 모임 검증
            validation_result = await self.validate_meeting_for_cleanup(meeting_id, meeting_type)
            
            if not validation_result["valid"]:
                error_msg = validation_result["reason"]
                logger.error(error_msg)
                return self._create_error_result(meeting_id, meeting_type, error_msg, start_time)
            
            if not validation_result["can_proceed"]:
                logger.info(f"Cleanup skipped for meeting {meeting_id}: {validation_result['reason']}")
                return self._create_success_result(
                    meeting_id, meeting_type, f"bookclub_{meeting_id}_documents", 0, start_time
                )
            
            # 컬렉션 정보 수집 (삭제 전)
            collection_info = await self.vector_db.get_collection_info(meeting_id)
            logger.info(f"Collection info before cleanup: {collection_info}")
            
            # 재시도 로직으로 컬렉션 삭제
            success = await self._delete_with_retry(meeting_id)
            
            if success:
                duration_ms = int((datetime.now() - start_time).total_seconds() * 1000)
                documents_deleted = collection_info.get("document_count", 0)
                
                logger.info(f"✅ Vector DB cleanup completed for {meeting_type} meeting {meeting_id}: "
                           f"documents_deleted={documents_deleted}, duration={duration_ms}ms")
                
                return self._create_success_result(
                    meeting_id, meeting_type, collection_info["collection_name"], 
                    documents_deleted, start_time, duration_ms
                )
            else:
                error_msg = "Failed to delete collection after retries"
                logger.error(f"❌ Vector DB cleanup failed for {meeting_type} meeting {meeting_id}: {error_msg}")
                return self._create_error_result(meeting_id, meeting_type, error_msg, start_time)
                
        except Exception as e:
            error_msg = str(e)
            logger.error(f"❌ Vector DB cleanup failed for {meeting_type} meeting {meeting_id}: {error_msg}")
            return self._create_error_result(meeting_id, meeting_type, error_msg, start_time)
    
    async def _delete_with_retry(self, meeting_id: str) -> bool:
        """재시도 로직으로 컬렉션 삭제"""
        max_attempts = self.settings.vector_db.CLEANUP_RETRY_ATTEMPTS
        retry_delay = self.settings.vector_db.CLEANUP_RETRY_DELAY
        
        for attempt in range(1, max_attempts + 1):
            try:
                success = await self.vector_db.delete_meeting_collection(meeting_id)
                if success:
                    if attempt > 1:
                        logger.info(f"Collection deletion succeeded on attempt {attempt}")
                    return True
                else:
                    logger.warning(f"Collection deletion failed on attempt {attempt}")
                    
            except Exception as e:
                logger.warning(f"Collection deletion attempt {attempt} failed: {e}")
            
            # 마지막 시도가 아니면 대기
            if attempt < max_attempts:
                logger.info(f"Retrying collection deletion in {retry_delay} seconds...")
                await asyncio.sleep(retry_delay)
        
        logger.error(f"Collection deletion failed after {max_attempts} attempts")
        return False
    
    async def manual_cleanup(self, meeting_id: str, force: bool = False) -> Dict[str, Any]:
        """
        관리자용 수동 정리 기능
        
        Args:
            meeting_id: 독서 모임 ID
            force: 강제 정리 여부 (활성 모임도 정리)
            
        Returns:
            Dict with cleanup result
        """
        start_time = datetime.now()
        
        try:
            logger.info(f"Starting manual cleanup for meeting: {meeting_id}, force: {force}")
            
            # 강제 모드가 아닌 경우 모임 상태 확인
            if not force:
                is_active = await self.is_meeting_active(meeting_id)
                if is_active:
                    error_msg = f"Meeting {meeting_id} is still active. Use force=True to override."
                    logger.warning(error_msg)
                    return self._create_error_result(meeting_id, "manual", error_msg, start_time)
            
            # 일반 정리 로직 사용 (meeting_type은 manual로 설정)
            result = await self.cleanup_meeting_collection(meeting_id, "manual")
            
            logger.info(f"Manual cleanup completed for meeting {meeting_id}: success={result['success']}")
            return result
            
        except Exception as e:
            error_msg = str(e)
            logger.error(f"Manual cleanup failed for meeting {meeting_id}: {error_msg}")
            return self._create_error_result(meeting_id, "manual", error_msg, start_time)
    
    async def is_meeting_active(self, meeting_id: str) -> bool:
        """
        독서 모임 활성 상태 확인
        
        Args:
            meeting_id: 독서 모임 ID
            
        Returns:
            bool: 모임 활성 상태
        """
        try:
            # 컬렉션 존재 여부 확인
            collection_info = await self.vector_db.get_collection_info(meeting_id)
            
            if not collection_info.get("exists", False):
                logger.info(f"Meeting {meeting_id} is inactive: no collection found")
                return False
            
            # 추가 활성 상태 확인 로직들
            meeting_status = await self.get_meeting_status(meeting_id)
            
            # 컬렉션이 존재하고 문서가 있으면 활성으로 간주
            is_active = meeting_status.has_vector_collection and meeting_status.participant_count >= 0
            
            logger.info(f"Meeting {meeting_id} active status: {is_active} "
                       f"(collection: {meeting_status.has_vector_collection}, "
                       f"participants: {meeting_status.participant_count})")
            return is_active
            
        except Exception as e:
            logger.error(f"Failed to check meeting status for {meeting_id}: {e}")
            # 오류 시 안전하게 활성으로 간주 (실수 삭제 방지)
            return True
    
    async def get_meeting_status(self, meeting_id: str) -> MeetingStatus:
        """
        독서 모임의 상세 상태 정보 조회
        
        Args:
            meeting_id: 독서 모임 ID
            
        Returns:
            MeetingStatus: 모임 상태 정보
        """
        try:
            # 컬렉션 정보 조회
            collection_info = await self.vector_db.get_collection_info(meeting_id)
            has_collection = collection_info.get("exists", False)
            
            # 기본 상태 정보 생성
            meeting_status = MeetingStatus(
                meeting_id=meeting_id,
                is_active=has_collection,
                last_activity=datetime.now(),  # 현재는 현재 시간으로 설정
                participant_count=1 if has_collection else 0,  # 기본값
                has_vector_collection=has_collection
            )
            
            logger.debug(f"Meeting status for {meeting_id}: {meeting_status}")
            return meeting_status
            
        except Exception as e:
            logger.error(f"Failed to get meeting status for {meeting_id}: {e}")
            # 오류 시 기본 상태 반환
            return MeetingStatus(
                meeting_id=meeting_id,
                is_active=True,  # 안전하게 활성으로 설정
                last_activity=datetime.now(),
                participant_count=0,
                has_vector_collection=False
            )
    
    async def validate_meeting_for_cleanup(self, meeting_id: str, meeting_type: str) -> Dict[str, Any]:
        """
        정리 작업 전 모임 검증
        
        Args:
            meeting_id: 독서 모임 ID
            meeting_type: 모임 타입
            
        Returns:
            Dict with validation result
        """
        try:
            logger.info(f"Validating meeting {meeting_id} ({meeting_type}) for cleanup")
            
            # 모임 타입 검증
            if meeting_type not in self.settings.vector_db.SUPPORTED_MEETING_TYPES:
                return {
                    "valid": False,
                    "reason": f"Unsupported meeting type: {meeting_type}",
                    "can_proceed": False
                }
            
            # 모임 상태 확인
            meeting_status = await self.get_meeting_status(meeting_id)
            
            # 컬렉션이 없으면 정리할 것이 없음
            if not meeting_status.has_vector_collection:
                return {
                    "valid": True,
                    "reason": "No collection to cleanup",
                    "can_proceed": False,
                    "meeting_status": meeting_status
                }
            
            # 정리 가능
            return {
                "valid": True,
                "reason": "Meeting ready for cleanup",
                "can_proceed": True,
                "meeting_status": meeting_status
            }
            
        except Exception as e:
            logger.error(f"Failed to validate meeting {meeting_id} for cleanup: {e}")
            return {
                "valid": False,
                "reason": f"Validation error: {str(e)}",
                "can_proceed": False
            }
    
    def is_cleanup_enabled(self) -> bool:
        """설정 기반 정리 기능 활성화 여부 확인"""
        enabled = self.settings.vector_db.ENABLE_CLEANUP_ON_MEETING_END
        logger.debug(f"Vector DB cleanup enabled: {enabled}")
        return enabled
    
    def _create_success_result(
        self, 
        meeting_id: str, 
        meeting_type: str, 
        collection_name: str, 
        documents_deleted: int, 
        start_time: datetime,
        duration_ms: int = None
    ) -> Dict[str, Any]:
        """성공 결과 생성"""
        if duration_ms is None:
            duration_ms = int((datetime.now() - start_time).total_seconds() * 1000)
            
        result = CleanupResult(
            success=True,
            meeting_id=meeting_id,
            meeting_type=meeting_type,
            collection_name=collection_name,
            documents_deleted=documents_deleted,
            cleanup_timestamp=start_time,
            cleanup_duration_ms=duration_ms
        )
        
        return {
            "success": result.success,
            "meeting_id": result.meeting_id,
            "meeting_type": result.meeting_type,
            "collection_name": result.collection_name,
            "documents_deleted": result.documents_deleted,
            "cleanup_timestamp": result.cleanup_timestamp.isoformat(),
            "cleanup_duration_ms": result.cleanup_duration_ms
        }
    
    def _create_error_result(
        self, 
        meeting_id: str, 
        meeting_type: str, 
        error_message: str, 
        start_time: datetime
    ) -> Dict[str, Any]:
        """오류 결과 생성"""
        duration_ms = int((datetime.now() - start_time).total_seconds() * 1000)
        
        result = CleanupResult(
            success=False,
            meeting_id=meeting_id,
            meeting_type=meeting_type,
            collection_name=f"bookclub_{meeting_id}_documents",
            documents_deleted=0,
            error_message=error_message,
            cleanup_timestamp=start_time,
            cleanup_duration_ms=duration_ms
        )
        
        return {
            "success": result.success,
            "meeting_id": result.meeting_id,
            "meeting_type": result.meeting_type,
            "error": result.error_message,
            "cleanup_timestamp": result.cleanup_timestamp.isoformat(),
            "cleanup_duration_ms": result.cleanup_duration_ms
        }