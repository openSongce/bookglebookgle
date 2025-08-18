"""
Stream Connection Models for BGBG AI Server
Defines data structures for gRPC stream connection tracking and management
"""

from datetime import datetime
from enum import Enum
from typing import Dict, Any, Optional
from dataclasses import dataclass, field
import uuid
import grpc


class StreamStatus(str, Enum):
    """Stream connection status enumeration"""
    ACTIVE = "active"
    DISCONNECTING = "disconnecting"
    DISCONNECTED = "disconnected"
    ERROR = "error"


@dataclass
class StreamContext:
    """
    Stream connection context for tracking gRPC streaming connections
    Used by StreamConnectionManager to manage active ProcessChatMessage streams
    """
    session_id: str
    stream_id: str
    context: grpc.ServicerContext
    created_at: datetime
    last_activity: datetime
    user_info: Dict[str, str]
    status: StreamStatus = StreamStatus.ACTIVE
    metadata: Dict[str, Any] = field(default_factory=dict)
    
    def __post_init__(self):
        """Post-initialization validation and setup"""
        if not self.stream_id:
            self.stream_id = str(uuid.uuid4())
        
        if isinstance(self.created_at, str):
            self.created_at = datetime.fromisoformat(self.created_at)
        elif isinstance(self.created_at, (int, float)):
            self.created_at = datetime.fromtimestamp(self.created_at)
            
        if isinstance(self.last_activity, str):
            self.last_activity = datetime.fromisoformat(self.last_activity)
        elif isinstance(self.last_activity, (int, float)):
            self.last_activity = datetime.fromtimestamp(self.last_activity)
    
    def update_activity(self) -> None:
        """Update last activity timestamp"""
        self.last_activity = datetime.utcnow()
    
    def is_active(self) -> bool:
        """Check if stream is currently active"""
        return self.status == StreamStatus.ACTIVE and self.context.is_active()
    
    def get_user_id(self) -> str:
        """Get user ID from user info"""
        return self.user_info.get('user_id', 'unknown')
    
    def get_nickname(self) -> str:
        """Get user nickname from user info"""
        return self.user_info.get('nickname', 'Unknown User')
    
    def add_metadata(self, key: str, value: Any) -> None:
        """Add metadata to the stream context"""
        self.metadata[key] = value
    
    def get_metadata(self, key: str, default: Any = None) -> Any:
        """Get metadata value"""
        return self.metadata.get(key, default)
    
    def to_dict(self) -> Dict[str, Any]:
        """Convert to dictionary for logging and serialization (excluding grpc context)"""
        return {
            'session_id': self.session_id,
            'stream_id': self.stream_id,
            'created_at': self.created_at.isoformat(),
            'last_activity': self.last_activity.isoformat(),
            'user_info': self.user_info,
            'status': self.status.value,
            'metadata': self.metadata,
            'is_active': self.context.is_active() if self.context else False
        }
    
    async def disconnect(self, reason: str = "Session ended") -> bool:
        """
        Disconnect the stream gracefully
        
        Args:
            reason: Reason for disconnection
            
        Returns:
            bool: True if disconnection was successful
        """
        try:
            if self.context and self.context.is_active():
                self.status = StreamStatus.DISCONNECTING
                await self.context.abort(grpc.StatusCode.CANCELLED, reason)
                self.status = StreamStatus.DISCONNECTED
                return True
            else:
                self.status = StreamStatus.DISCONNECTED
                return True
        except Exception:
            self.status = StreamStatus.ERROR
            return False


@dataclass
class StreamRegistryEntry:
    """
    Entry for stream registry tracking multiple streams per session
    """
    session_id: str
    streams: Dict[str, StreamContext] = field(default_factory=dict)
    created_at: datetime = field(default_factory=datetime.utcnow)
    last_updated: datetime = field(default_factory=datetime.utcnow)
    
    def add_stream(self, stream_context: StreamContext) -> None:
        """Add a stream to this session"""
        self.streams[stream_context.stream_id] = stream_context
        self.last_updated = datetime.utcnow()
    
    def remove_stream(self, stream_id: str) -> Optional[StreamContext]:
        """Remove a stream from this session"""
        if stream_id in self.streams:
            stream_context = self.streams.pop(stream_id)
            self.last_updated = datetime.utcnow()
            return stream_context
        return None
    
    def get_active_streams(self) -> Dict[str, StreamContext]:
        """Get all active streams for this session"""
        return {
            stream_id: stream_ctx 
            for stream_id, stream_ctx in self.streams.items() 
            if stream_ctx.is_active()
        }
    
    def get_stream_count(self) -> int:
        """Get total number of streams"""
        return len(self.streams)
    
    def get_active_stream_count(self) -> int:
        """Get number of active streams"""
        return len(self.get_active_streams())
    
    def to_dict(self) -> Dict[str, Any]:
        """Convert to dictionary for logging and serialization"""
        return {
            'session_id': self.session_id,
            'stream_count': self.get_stream_count(),
            'active_stream_count': self.get_active_stream_count(),
            'streams': {
                stream_id: stream_ctx.to_dict() 
                for stream_id, stream_ctx in self.streams.items()
            },
            'created_at': self.created_at.isoformat(),
            'last_updated': self.last_updated.isoformat()
        }


class StreamConnectionError(Exception):
    """Base exception for stream connection related errors"""
    pass


class StreamRegistrationError(StreamConnectionError):
    """Exception raised when stream registration fails"""
    pass


class StreamDisconnectionError(StreamConnectionError):
    """Exception raised when stream disconnection fails"""
    pass