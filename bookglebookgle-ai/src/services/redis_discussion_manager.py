"""
Redis-based Discussion Session Manager for BGBG AI Server
Replaces memory-based session management with Redis for persistence and scalability
"""

import json
from datetime import datetime, timedelta
from typing import Dict, Optional, Any, List
from loguru import logger

from src.services.redis_connection_manager import RedisConnectionManager
from src.services.vector_db import VectorDBManager


class RedisDiscussionManager:
    """
    Redis 기반 토론 세션 관리자 - 서버 재시작 및 다중 인스턴스 환경 지원
    """
    
    def __init__(self, redis_manager: RedisConnectionManager = None):
        self.redis_manager = redis_manager
        self.vector_db = None
        self.session_ttl_hours = 24  # 기본 세션 TTL: 24시간
        
    async def initialize(self, vector_db: VectorDBManager):
        """Initialize with vector DB"""
        self.vector_db = vector_db
        logger.info("RedisDiscussionManager initialized")
    
    async def start_discussion(
        self, 
        meeting_id: str,
        session_id: str,
        document_id: str,
        participants: List[Dict[str, str]] = None
    ) -> Dict[str, Any]:
        """
        간단한 토론 세션 시작 - Redis에 기본 세션 정보만 저장
        
        Args:
            meeting_id: 독서 모임 ID
            session_id: 토론 세션 ID
            document_id: 문서 ID
            participants: 참여자 목록
            
        Returns:
            Dict with success status
        """
        try:
            session_data = {
                "meeting_id": meeting_id,
                "document_id": document_id,
                "started_at": datetime.utcnow().isoformat(),
                "participants": participants or [],
                "chatbot_active": True
            }
            
            # Redis에 세션 저장 (24시간 TTL)
            session_key = f"discussion:session:{session_id}"
            ttl_seconds = self.session_ttl_hours * 3600
            
            await self.redis_manager.set_with_ttl(
                session_key, 
                json.dumps(session_data), 
                ttl_seconds
            )
            
            logger.info(f"✅ Simple discussion session started: {session_id}")
            
            return {
                "success": True,
                "message": "Discussion session started successfully"
            }
            
        except Exception as e:
            logger.error(f"Failed to start discussion session: {e}")
            return {
                "success": False,
                "message": f"Failed to start discussion session: {str(e)}"
            }
    
    async def get_discussion_session(self, session_id: str) -> Optional[Dict[str, Any]]:
        """
        토론 세션 정보 조회
        
        Args:
            session_id: 토론 세션 ID
            
        Returns:
            Session data or None if not found
        """
        try:
            session_key = f"discussion:session:{session_id}"
            session_data_json = await self.redis_manager.get_key(session_key)
            
            if session_data_json:
                session_data = json.loads(session_data_json)
                # 활동 시간 업데이트
                await self.update_last_activity(session_id)
                return session_data
            
            return None
            
        except Exception as e:
            logger.error(f"Failed to get discussion session {session_id}: {e}")
            return None
    
    async def update_last_activity(self, session_id: str) -> bool:
        """
        세션 마지막 활동 시간 업데이트
        
        Args:
            session_id: 토론 세션 ID
            
        Returns:
            bool: 업데이트 성공 여부
        """
        try:
            session_data = await self.get_discussion_session_raw(session_id)
            if session_data:
                session_data["last_activity"] = datetime.utcnow().isoformat()
                
                session_key = f"discussion:session:{session_id}"
                ttl_seconds = self.session_ttl_hours * 3600
                
                await self.redis_manager.set_with_ttl(
                    session_key,
                    json.dumps(session_data),
                    ttl_seconds
                )
                return True
            
            return False
            
        except Exception as e:
            logger.error(f"Failed to update last activity for session {session_id}: {e}")
            return False
    
    async def get_discussion_session_raw(self, session_id: str) -> Optional[Dict[str, Any]]:
        """활동 시간 업데이트 없이 세션 정보만 조회"""
        try:
            session_key = f"discussion:session:{session_id}"
            session_data_json = await self.redis_manager.get_key(session_key)
            
            if session_data_json:
                return json.loads(session_data_json)
            return None
            
        except Exception as e:
            logger.error(f"Failed to get raw session data {session_id}: {e}")
            return None
    
    async def is_discussion_active(self, session_id: str) -> bool:
        """
        토론 세션이 활성 상태인지 확인
        
        Args:
            session_id: 토론 세션 ID
            
        Returns:
            bool: 활성 상태 여부
        """
        session_data = await self.get_discussion_session_raw(session_id)
        return bool(session_data and session_data.get("chatbot_active", False))
    
    async def end_discussion(self, session_id: str) -> Dict[str, Any]:
        """
        간단한 토론 세션 종료 - Redis에서 세션 삭제
        
        Args:
            session_id: 토론 세션 ID
            
        Returns:
            Dict with success status
        """
        try:
            session_key = f"discussion:session:{session_id}"
            
            # 세션 존재 확인
            if not await self.redis_manager.redis.exists(session_key):
                return {
                    "success": False,
                    "message": "Discussion session not found"
                }
            
            # 세션 삭제 (단순하게)
            await self.redis_manager.delete_keys(session_key)
            
            logger.info(f"✅ Simple discussion session ended: {session_id}")
            
            return {
                "success": True,
                "message": "Discussion session ended successfully"
            }
            
        except Exception as e:
            logger.error(f"Failed to end discussion session {session_id}: {e}")
            return {
                "success": False,
                "message": f"Failed to end discussion session: {str(e)}"
            }
    
    async def get_active_sessions(self, meeting_id: str) -> List[str]:
        """
        모임의 활성 토론 세션 목록 조회
        
        Args:
            meeting_id: 독서 모임 ID
            
        Returns:
            List of active session IDs
        """
        try:
            active_sessions_key = f"discussion:active_sessions:{meeting_id}"
            session_ids = await self.redis_manager.redis.smembers(active_sessions_key)
            
            # bytes를 string으로 변환
            return [sid.decode('utf-8') if isinstance(sid, bytes) else sid for sid in session_ids]
            
        except Exception as e:
            logger.error(f"Failed to get active sessions for meeting {meeting_id}: {e}")
            return []
    
    async def cleanup_expired_sessions(self) -> int:
        """
        만료된 토론 세션 정리
        
        Returns:
            int: 정리된 세션 수
        """
        try:
            # Redis TTL에 의해 자동 삭제되므로 추가 정리는 불필요
            # 하지만 활성 세션 목록의 불일치를 정리
            
            # 모든 활성 세션 키 찾기
            pattern = "discussion:active_sessions:*"
            keys = []
            cursor = 0
            
            while True:
                cursor, batch_keys = await self.redis_manager.redis.scan(
                    cursor=cursor, match=pattern, count=100
                )
                keys.extend(batch_keys)
                if cursor == 0:
                    break
            
            cleaned_count = 0
            for key in keys:
                if isinstance(key, bytes):
                    key = key.decode('utf-8')
                
                # 각 세션의 실제 존재 여부 확인
                session_ids = await self.redis_manager.redis.smembers(key)
                for session_id in session_ids:
                    if isinstance(session_id, bytes):
                        session_id = session_id.decode('utf-8')
                    
                    session_key = f"discussion:session:{session_id}"
                    if not await self.redis_manager.redis.exists(session_key):
                        await self.redis_manager.redis.srem(key, session_id)
                        cleaned_count += 1
            
            if cleaned_count > 0:
                logger.info(f"🧹 Cleaned up {cleaned_count} expired discussion sessions")
            
            return cleaned_count
            
        except Exception as e:
            logger.error(f"Failed to cleanup expired sessions: {e}")
            return 0
    
    async def get_session_stats(self) -> Dict[str, Any]:
        """
        토론 세션 통계 정보
        
        Returns:
            Dict with session statistics
        """
        try:
            # 전체 활성 세션 수 계산
            pattern = "discussion:session:*"
            total_sessions = 0
            cursor = 0
            
            while True:
                cursor, keys = await self.redis_manager.redis.scan(
                    cursor=cursor, match=pattern, count=100
                )
                total_sessions += len(keys)
                if cursor == 0:
                    break
            
            # 활성 모임 수 계산
            active_meetings_pattern = "discussion:active_sessions:*"
            cursor = 0
            active_meetings = 0
            
            while True:
                cursor, keys = await self.redis_manager.redis.scan(
                    cursor=cursor, match=active_meetings_pattern, count=100
                )
                active_meetings += len(keys)
                if cursor == 0:
                    break
            
            return {
                "total_sessions": total_sessions,
                "active_meetings": active_meetings,
                "session_ttl_hours": self.session_ttl_hours,
                "timestamp": datetime.utcnow().isoformat()
            }
            
        except Exception as e:
            logger.error(f"Failed to get session stats: {e}")
            return {
                "total_sessions": 0,
                "active_meetings": 0,
                "error": str(e)
            }