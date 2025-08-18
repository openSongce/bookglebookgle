"""
Book Club Discussion Manager for BGBG AI Server
Manages discussion sessions per book club meeting with state lifecycle
"""

from datetime import datetime
from typing import Dict, Optional, Any, List
from loguru import logger

from src.services.vector_db import VectorDBManager


class BookClubDiscussionManager:
    """
    Manages discussion sessions for book club meetings
    Implements the discussion lifecycle as specified in docs/toron.md
    """
    
    def __init__(self, vector_db_manager: VectorDBManager = None):
        # {meeting_id: {session_id: {started_at, ended_at, status, chatbot_active, participants}}}
        self.bookclub_sessions: Dict[str, Dict[str, Dict[str, Any]]] = {}
        self.vector_db = vector_db_manager
        logger.info("BookClubDiscussionManager initialized")

    def start_discussion(
        self, 
        meeting_id: str, 
        session_id: str, 
        started_at: datetime,
        participants: List[Dict[str, str]] = None
    ) -> Dict[str, Any]:
        """
        Start discussion session (토론 시작 요청 처리)
        
        Args:
            meeting_id: 독서 모임 ID
            session_id: 토론 세션 ID  
            started_at: 토론 시작 시간
            participants: 참가자 목록
            
        Returns:
            Dict with status and session info
        """
        try:
            logger.info(f"Starting discussion for meeting {meeting_id}, session {session_id}")
            
            if meeting_id not in self.bookclub_sessions:
                self.bookclub_sessions[meeting_id] = {}

            self.bookclub_sessions[meeting_id][session_id] = {
                'started_at': started_at,
                'ended_at': None,
                'status': 'active',
                'chatbot_active': True,  # AI 토론 진행자 챗봇 활성화
                'participants': participants or [],
                'message_count': 0,
                'last_activity': started_at
            }
            
            logger.info(f"✅ Discussion started - Meeting: {meeting_id}, Session: {session_id}")
            
            return {
                'success': True,
                'meeting_id': meeting_id,
                'session_id': session_id,
                'status': 'active',
                'chatbot_active': True,
                'message': 'AI 토론 진행자가 활성화되었습니다.'
            }
            
        except Exception as e:
            logger.error(f"Failed to start discussion: {e}")
            return {
                'success': False,
                'error': str(e),
                'message': '토론 시작에 실패했습니다.'
            }

    def end_discussion(
        self, 
        meeting_id: str, 
        session_id: str, 
        ended_at: datetime
    ) -> Dict[str, Any]:
        """
        End discussion session (토론 종료 요청 처리)
        
        Args:
            meeting_id: 독서 모임 ID
            session_id: 토론 세션 ID
            ended_at: 토론 종료 시간
            
        Returns:
            Dict with status and session info
        """
        try:
            if not self.is_discussion_active(meeting_id, session_id):
                return {
                    'success': False,
                    'message': '활성화된 토론이 없습니다.',
                    'chatbot_active': False
                }

            self.bookclub_sessions[meeting_id][session_id].update({
                'ended_at': ended_at,
                'status': 'ended',
                'chatbot_active': False  # 챗봇 비활성화 → 일반 채팅으로 전환
            })
            
            logger.info(f"✅ Discussion ended - Meeting: {meeting_id}, Session: {session_id}")
            
            return {
                'success': True,
                'meeting_id': meeting_id,
                'session_id': session_id,
                'status': 'ended',
                'chatbot_active': False,
                'message': 'AI 토론 진행자가 비활성화되었습니다. 일반 채팅으로 전환됩니다.'
            }
            
        except Exception as e:
            logger.error(f"Failed to end discussion: {e}")
            return {
                'success': False,
                'error': str(e),
                'message': '토론 종료에 실패했습니다.'
            }

    def is_discussion_active(self, meeting_id: str, session_id: str) -> bool:
        """
        Check if discussion is currently active
        
        Args:
            meeting_id: 독서 모임 ID
            session_id: 토론 세션 ID
            
        Returns:
            bool: True if discussion is active and chatbot is enabled
        """
        return (
            meeting_id in self.bookclub_sessions and 
            session_id in self.bookclub_sessions[meeting_id] and
            self.bookclub_sessions[meeting_id][session_id].get('status') == 'active' and
            self.bookclub_sessions[meeting_id][session_id].get('chatbot_active') == True
        )

    def is_chatbot_active(self, meeting_id: str, session_id: str) -> bool:
        """
        Check if chatbot is active for the session
        
        Args:
            meeting_id: 독서 모임 ID
            session_id: 토론 세션 ID
            
        Returns:
            bool: True if chatbot is active
        """
        if not self.is_discussion_active(meeting_id, session_id):
            return False
            
        return self.bookclub_sessions[meeting_id][session_id].get('chatbot_active', False)

    def update_activity(self, meeting_id: str, session_id: str):
        """
        Update last activity timestamp
        
        Args:
            meeting_id: 독서 모임 ID
            session_id: 토론 세션 ID
        """
        if self.is_discussion_active(meeting_id, session_id):
            session_data = self.bookclub_sessions[meeting_id][session_id]
            session_data['last_activity'] = datetime.utcnow()
            session_data['message_count'] = session_data.get('message_count', 0) + 1

    async def get_book_material_context(
        self, 
        meeting_id: str, 
        query: str, 
        max_chunks: int = 3
    ) -> List[str]:
        """
        Get relevant book material context for discussion
        독서 자료 컨텍스트 검색 (Vector DB 통합)
        
        Args:
            meeting_id: 독서 모임 ID
            query: 검색 쿼리 (사용자 메시지)
            max_chunks: 최대 검색 결과 수
            
        Returns:
            List[str]: 관련 독서 자료 텍스트 청크들
        """
        try:
            if not self.vector_db:
                logger.warning("Vector DB not available for context search")
                return []

            # 독서 모임별 컬렉션에서 검색 (향후 구현 예정)
            # 현재는 기본 documents 컬렉션에서 meeting_id 필터링 사용
            results = await self.vector_db.similarity_search(
                query=query,
                collection_name="documents",
                n_results=max_chunks,
                filter_metadata={"meeting_id": meeting_id}
            )
            
            # 관련도 높은 순으로 텍스트 추출
            context_chunks = [result["document"] for result in results]
            
            logger.info(f"Retrieved {len(context_chunks)} context chunks for meeting {meeting_id}")
            return context_chunks
            
        except Exception as e:
            logger.error(f"Failed to get book material context: {e}")
            return []

    def get_session_info(self, meeting_id: str, session_id: str) -> Optional[Dict[str, Any]]:
        """
        Get session information
        
        Args:
            meeting_id: 독서 모임 ID
            session_id: 토론 세션 ID
            
        Returns:
            Optional[Dict]: Session info or None if not found
        """
        if (meeting_id in self.bookclub_sessions and 
            session_id in self.bookclub_sessions[meeting_id]):
            return self.bookclub_sessions[meeting_id][session_id].copy()
        return None

    def get_active_sessions(self, meeting_id: str = None) -> Dict[str, Any]:
        """
        Get all active sessions
        
        Args:
            meeting_id: Optional meeting ID to filter by
            
        Returns:
            Dict containing active sessions info
        """
        active_sessions = {}
        
        sessions_to_check = (
            {meeting_id: self.bookclub_sessions[meeting_id]} 
            if meeting_id and meeting_id in self.bookclub_sessions
            else self.bookclub_sessions
        )
        
        for mid, sessions in sessions_to_check.items():
            active_for_meeting = {}
            for sid, session_data in sessions.items():
                if session_data.get('status') == 'active':
                    active_for_meeting[sid] = session_data.copy()
            
            if active_for_meeting:
                active_sessions[mid] = active_for_meeting
        
        return active_sessions

    def cleanup_expired_sessions(self, max_idle_hours: int = 24):
        """
        Clean up expired or idle sessions
        
        Args:
            max_idle_hours: Maximum idle time in hours before cleanup
        """
        try:
            cutoff_time = datetime.utcnow().timestamp() - (max_idle_hours * 3600)
            cleaned_count = 0
            
            for meeting_id in list(self.bookclub_sessions.keys()):
                for session_id in list(self.bookclub_sessions[meeting_id].keys()):
                    session_data = self.bookclub_sessions[meeting_id][session_id]
                    last_activity = session_data.get('last_activity', datetime.utcnow())
                    
                    # Convert datetime to timestamp for comparison
                    if isinstance(last_activity, datetime):
                        last_activity_ts = last_activity.timestamp()
                    else:
                        last_activity_ts = datetime.utcnow().timestamp()
                    
                    if last_activity_ts < cutoff_time:
                        del self.bookclub_sessions[meeting_id][session_id]
                        cleaned_count += 1
                        logger.info(f"Cleaned up expired session: {meeting_id}/{session_id}")
                
                # Remove empty meeting entries
                if not self.bookclub_sessions[meeting_id]:
                    del self.bookclub_sessions[meeting_id]
            
            if cleaned_count > 0:
                logger.info(f"Cleaned up {cleaned_count} expired discussion sessions")
                
        except Exception as e:
            logger.error(f"Session cleanup failed: {e}")

    def get_statistics(self) -> Dict[str, Any]:
        """
        Get discussion manager statistics
        
        Returns:
            Dict containing statistics
        """
        total_meetings = len(self.bookclub_sessions)
        total_sessions = sum(len(sessions) for sessions in self.bookclub_sessions.values())
        active_sessions = 0
        total_messages = 0
        
        for sessions in self.bookclub_sessions.values():
            for session_data in sessions.values():
                if session_data.get('status') == 'active':
                    active_sessions += 1
                total_messages += session_data.get('message_count', 0)
        
        return {
            'total_meetings': total_meetings,
            'total_sessions': total_sessions,
            'active_sessions': active_sessions,
            'total_messages': total_messages,
            'meetings_with_active_discussions': len([
                mid for mid, sessions in self.bookclub_sessions.items()
                if any(s.get('status') == 'active' for s in sessions.values())
            ])
        }