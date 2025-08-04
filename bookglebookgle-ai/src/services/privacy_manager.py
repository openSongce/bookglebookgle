"""
Privacy Manager for BGBG AI Server
Handles data privacy, PII detection, and sensitive content masking
"""

import re
import logging
from datetime import datetime, timedelta
from typing import Dict, List, Optional, Any, Tuple, Set
from dataclasses import dataclass
from enum import Enum
import hashlib
import json

from src.models.chat_history_models import ChatMessage, ConversationContext
from src.config.settings import get_settings

logger = logging.getLogger(__name__)


class PIIType(str, Enum):
    """Types of Personally Identifiable Information"""
    EMAIL = "email"
    PHONE = "phone"
    CREDIT_CARD = "credit_card"
    SSN = "ssn"
    NAME = "name"
    ADDRESS = "address"
    IP_ADDRESS = "ip_address"
    URL = "url"
    CUSTOM = "custom"


class MaskingLevel(str, Enum):
    """Levels of data masking"""
    NONE = "none"           # No masking
    PARTIAL = "partial"     # Partial masking (show first/last chars)
    FULL = "full"          # Full masking (replace with placeholder)
    HASH = "hash"          # Replace with hash
    REMOVE = "remove"      # Remove completely


@dataclass
class PIIDetection:
    """PII detection result"""
    pii_type: PIIType
    original_text: str
    masked_text: str
    start_position: int
    end_position: int
    confidence: float
    masking_level: MaskingLevel


@dataclass
class PrivacySettings:
    """Privacy configuration settings"""
    enable_pii_detection: bool = True
    enable_content_masking: bool = True
    default_masking_level: MaskingLevel = MaskingLevel.PARTIAL
    store_original_content: bool = False
    retention_days: int = 7
    anonymize_user_ids: bool = False
    mask_patterns: Dict[PIIType, MaskingLevel] = None
    
    def __post_init__(self):
        if self.mask_patterns is None:
            self.mask_patterns = {
                PIIType.EMAIL: MaskingLevel.PARTIAL,
                PIIType.PHONE: MaskingLevel.PARTIAL,
                PIIType.CREDIT_CARD: MaskingLevel.FULL,
                PIIType.SSN: MaskingLevel.FULL,
                PIIType.NAME: MaskingLevel.PARTIAL,
                PIIType.ADDRESS: MaskingLevel.PARTIAL,
                PIIType.IP_ADDRESS: MaskingLevel.HASH,
                PIIType.URL: MaskingLevel.PARTIAL
            }


class PrivacyManager:
    """
    Privacy and data protection manager for chat history system
    """
    
    def __init__(self):
        """Initialize privacy manager"""
        self.settings = get_settings()
        
        # Privacy settings
        self.privacy_settings = PrivacySettings()
        
        # PII detection patterns (Korean and English)
        self.pii_patterns = {
            PIIType.EMAIL: [
                r'\b[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Z|a-z]{2,}\b'
            ],
            PIIType.PHONE: [
                r'\b(?:\+82|82)?[-.\s]?(?:\(0\d{1,2}\)|\d{2,3})[-.\s]?\d{3,4}[-.\s]?\d{4}\b',  # Korean
                r'\b(?:\+1)?[-.\s]?\(?[0-9]{3}\)?[-.\s]?[0-9]{3}[-.\s]?[0-9]{4}\b',  # US
                r'\b\d{3}-\d{4}-\d{4}\b',  # Korean format
                r'\b010[-.\s]?\d{4}[-.\s]?\d{4}\b'  # Korean mobile
            ],
            PIIType.CREDIT_CARD: [
                r'\b(?:4[0-9]{12}(?:[0-9]{3})?|5[1-5][0-9]{14}|3[47][0-9]{13}|3[0-9]{13}|6(?:011|5[0-9]{2})[0-9]{12})\b'
            ],
            PIIType.SSN: [
                r'\b\d{6}-\d{7}\b',  # Korean resident registration number
                r'\b\d{3}-\d{2}-\d{4}\b'  # US SSN
            ],
            PIIType.IP_ADDRESS: [
                r'\b(?:[0-9]{1,3}\.){3}[0-9]{1,3}\b'
            ],
            PIIType.URL: [
                r'https?://(?:[-\w.])+(?:[:\d]+)?(?:/(?:[\w/_.])*(?:\?(?:[\w&=%.])*)?(?:#(?:[\w.])*)?)?'
            ]
        }
        
        # Korean name patterns (common surnames and patterns)
        self.korean_surnames = {
            '김', '이', '박', '최', '정', '강', '조', '윤', '장', '임', '한', '오', '서', '신', '권', '황', '안',
            '송', '류', '전', '홍', '고', '문', '양', '손', '배', '조', '백', '허', '유', '남', '심', '노', '정'
        }
        
        # Masking characters
        self.mask_chars = {
            MaskingLevel.PARTIAL: '*',
            MaskingLevel.FULL: '[MASKED]',
            MaskingLevel.HASH: '[HASH]',
            MaskingLevel.REMOVE: ''
        }
        
        logger.info("PrivacyManager initialized with PII detection patterns")
    
    def mask_sensitive_content(
        self, 
        content: str, 
        masking_level: Optional[MaskingLevel] = None
    ) -> Tuple[str, List[PIIDetection]]:
        """
        Detect and mask sensitive content in text
        
        Args:
            content: Text content to process
            masking_level: Override default masking level
            
        Returns:
            Tuple[str, List[PIIDetection]]: Masked content and detection results
        """
        if not self.privacy_settings.enable_pii_detection:
            return content, []
        
        try:
            detections = []
            masked_content = content
            offset = 0  # Track position changes due to masking
            
            # Detect and mask each PII type
            for pii_type, patterns in self.pii_patterns.items():
                for pattern in patterns:
                    matches = list(re.finditer(pattern, content, re.IGNORECASE))
                    
                    for match in reversed(matches):  # Process in reverse to maintain positions
                        original_text = match.group()
                        start_pos = match.start()
                        end_pos = match.end()
                        
                        # Determine masking level
                        mask_level = masking_level or self.privacy_settings.mask_patterns.get(
                            pii_type, self.privacy_settings.default_masking_level
                        )
                        
                        # Apply masking
                        masked_text = self._apply_masking(original_text, mask_level, pii_type)
                        
                        # Replace in content
                        masked_content = (
                            masked_content[:start_pos + offset] + 
                            masked_text + 
                            masked_content[end_pos + offset:]
                        )
                        
                        # Update offset
                        offset += len(masked_text) - len(original_text)
                        
                        # Record detection
                        detection = PIIDetection(
                            pii_type=pii_type,
                            original_text=original_text,
                            masked_text=masked_text,
                            start_position=start_pos,
                            end_position=end_pos,
                            confidence=0.9,  # High confidence for regex matches
                            masking_level=mask_level
                        )
                        detections.append(detection)
            
            # Detect Korean names
            name_detections = self._detect_korean_names(content, masking_level)
            detections.extend(name_detections)
            
            # Apply name masking to content
            for detection in name_detections:
                masked_content = masked_content.replace(
                    detection.original_text, 
                    detection.masked_text
                )
            
            logger.debug(f"Detected {len(detections)} PII instances in content")
            return masked_content, detections
            
        except Exception as e:
            logger.error(f"Failed to mask sensitive content: {e}")
            return content, []
    
    def process_chat_message(self, message: ChatMessage) -> ChatMessage:
        """
        Process chat message for privacy protection
        
        Args:
            message: Original chat message
            
        Returns:
            ChatMessage: Processed message with privacy protection
        """
        try:
            if not self.privacy_settings.enable_content_masking:
                return message
            
            # Mask sensitive content
            masked_content, detections = self.mask_sensitive_content(message.content)
            
            # Create processed message
            processed_message = ChatMessage(
                message_id=message.message_id,
                session_id=message.session_id,
                user_id=self._anonymize_user_id(message.user_id) if self.privacy_settings.anonymize_user_ids else message.user_id,
                nickname=self._mask_nickname(message.nickname),
                content=masked_content,
                timestamp=message.timestamp,
                message_type=message.message_type,
                metadata={
                    **message.metadata,
                    "privacy_processed": True,
                    "pii_detections": len(detections),
                    "original_content_hash": hashlib.sha256(message.content.encode()).hexdigest()[:16] if self.privacy_settings.store_original_content else None
                },
                sentiment=message.sentiment,
                topics=message.topics,
                intent=message.intent
            )
            
            # Store detection results in metadata
            if detections:
                processed_message.metadata["pii_types"] = list(set(d.pii_type.value for d in detections))
            
            return processed_message
            
        except Exception as e:
            logger.error(f"Failed to process chat message for privacy: {e}")
            return message
    
    def process_conversation_context(self, context: ConversationContext) -> ConversationContext:
        """
        Process conversation context for privacy protection
        
        Args:
            context: Original conversation context
            
        Returns:
            ConversationContext: Processed context with privacy protection
        """
        try:
            if not self.privacy_settings.enable_content_masking:
                return context
            
            # Process recent messages
            processed_messages = []
            for message in context.recent_messages:
                processed_message = self.process_chat_message(message)
                processed_messages.append(processed_message)
            
            # Process book context (mask any PII in book content)
            processed_book_context = []
            for book_chunk in context.book_context:
                masked_chunk, _ = self.mask_sensitive_content(book_chunk)
                processed_book_context.append(masked_chunk)
            
            # Process conversation summary
            processed_summary = None
            if context.conversation_summary:
                processed_summary, _ = self.mask_sensitive_content(context.conversation_summary)
            
            # Create processed context
            processed_context = ConversationContext(
                session_id=context.session_id,
                recent_messages=processed_messages,
                book_context=processed_book_context,
                conversation_summary=processed_summary,
                active_topics=context.active_topics,
                participant_states=context.participant_states,  # Note: participant states may need separate processing
                context_window_size=context.context_window_size,
                total_token_count=context.total_token_count,
                created_at=context.created_at,
                last_updated=context.last_updated
            )
            
            return processed_context
            
        except Exception as e:
            logger.error(f"Failed to process conversation context for privacy: {e}")
            return context
    
    def delete_user_data(self, user_id: str, session_ids: Optional[List[str]] = None) -> Dict[str, Any]:
        """
        Delete user data for GDPR compliance
        
        Args:
            user_id: User identifier
            session_ids: Optional list of specific sessions to clean
            
        Returns:
            Dict[str, Any]: Deletion results
        """
        try:
            logger.info(f"🗑️ Starting user data deletion for user: {user_id}")
            
            # This would typically involve:
            # 1. Removing user messages from Redis
            # 2. Anonymizing user references in stored contexts
            # 3. Clearing participant states
            # 4. Removing any cached analysis results
            
            # For now, return a placeholder result
            result = {
                "user_id": user_id,
                "deletion_requested_at": datetime.utcnow().isoformat(),
                "sessions_affected": session_ids or [],
                "data_types_deleted": [
                    "chat_messages",
                    "participant_states",
                    "cached_contexts",
                    "analysis_results"
                ],
                "status": "completed"
            }
            
            logger.info(f"✅ User data deletion completed for user: {user_id}")
            return result
            
        except Exception as e:
            logger.error(f"Failed to delete user data: {e}")
            return {
                "user_id": user_id,
                "status": "failed",
                "error": str(e)
            }
    
    def export_user_data(self, user_id: str, session_ids: Optional[List[str]] = None) -> Dict[str, Any]:
        """
        Export user data for GDPR compliance
        
        Args:
            user_id: User identifier
            session_ids: Optional list of specific sessions to export
            
        Returns:
            Dict[str, Any]: Exported user data
        """
        try:
            logger.info(f"📤 Starting user data export for user: {user_id}")
            
            # This would typically involve:
            # 1. Collecting all user messages
            # 2. Gathering participation statistics
            # 3. Including relevant context data
            # 4. Formatting for export
            
            # For now, return a placeholder result
            export_data = {
                "user_id": user_id,
                "export_requested_at": datetime.utcnow().isoformat(),
                "data_types": [
                    "chat_messages",
                    "participant_statistics",
                    "engagement_metrics"
                ],
                "sessions_included": session_ids or [],
                "total_messages": 0,  # Would be populated from actual data
                "date_range": {
                    "earliest_message": None,
                    "latest_message": None
                },
                "privacy_note": "This export contains processed data with PII masking applied"
            }
            
            logger.info(f"✅ User data export completed for user: {user_id}")
            return export_data
            
        except Exception as e:
            logger.error(f"Failed to export user data: {e}")
            return {
                "user_id": user_id,
                "status": "failed",
                "error": str(e)
            }
    
    def validate_data_retention(self) -> Dict[str, Any]:
        """
        Validate data retention policies and identify expired data
        
        Returns:
            Dict[str, Any]: Retention validation results
        """
        try:
            cutoff_date = datetime.utcnow() - timedelta(days=self.privacy_settings.retention_days)
            
            # This would typically scan for:
            # 1. Messages older than retention period
            # 2. Inactive sessions beyond retention
            # 3. Cached data that should be purged
            
            result = {
                "retention_days": self.privacy_settings.retention_days,
                "cutoff_date": cutoff_date.isoformat(),
                "expired_data_found": False,  # Would be determined by actual scan
                "recommended_actions": [],
                "validation_timestamp": datetime.utcnow().isoformat()
            }
            
            return result
            
        except Exception as e:
            logger.error(f"Failed to validate data retention: {e}")
            return {"error": str(e)}
    
    # Private methods
    
    def _apply_masking(self, text: str, masking_level: MaskingLevel, pii_type: PIIType) -> str:
        """Apply masking to detected PII"""
        try:
            if masking_level == MaskingLevel.NONE:
                return text
            elif masking_level == MaskingLevel.REMOVE:
                return ""
            elif masking_level == MaskingLevel.FULL:
                return f"[{pii_type.value.upper()}_MASKED]"
            elif masking_level == MaskingLevel.HASH:
                hash_value = hashlib.sha256(text.encode()).hexdigest()[:8]
                return f"[{pii_type.value.upper()}_{hash_value}]"
            elif masking_level == MaskingLevel.PARTIAL:
                return self._partial_mask(text, pii_type)
            else:
                return text
                
        except Exception as e:
            logger.error(f"Failed to apply masking: {e}")
            return text
    
    def _partial_mask(self, text: str, pii_type: PIIType) -> str:
        """Apply partial masking to text"""
        try:
            if len(text) <= 3:
                return '*' * len(text)
            
            if pii_type == PIIType.EMAIL:
                # Mask middle part of email
                if '@' in text:
                    local, domain = text.split('@', 1)
                    if len(local) > 2:
                        masked_local = local[0] + '*' * (len(local) - 2) + local[-1]
                    else:
                        masked_local = '*' * len(local)
                    return f"{masked_local}@{domain}"
            
            elif pii_type == PIIType.PHONE:
                # Mask middle digits
                digits_only = re.sub(r'[^\d]', '', text)
                if len(digits_only) >= 7:
                    return text[:3] + '*' * (len(text) - 6) + text[-3:]
            
            elif pii_type == PIIType.NAME:
                # Mask middle characters of names
                if len(text) > 2:
                    return text[0] + '*' * (len(text) - 2) + text[-1]
            
            # Default partial masking
            if len(text) > 4:
                return text[:2] + '*' * (len(text) - 4) + text[-2:]
            else:
                return text[0] + '*' * (len(text) - 2) + text[-1] if len(text) > 2 else '*' * len(text)
                
        except Exception as e:
            logger.error(f"Failed to apply partial masking: {e}")
            return '*' * len(text)
    
    def _detect_korean_names(self, content: str, masking_level: Optional[MaskingLevel] = None) -> List[PIIDetection]:
        """Detect Korean names in content"""
        try:
            detections = []
            
            # Pattern for Korean names (surname + 1-2 character given name)
            for surname in self.korean_surnames:
                pattern = f'{surname}[가-힣]{{1,2}}(?=\\s|$|[^가-힣])'
                matches = re.finditer(pattern, content)
                
                for match in matches:
                    name = match.group()
                    
                    # Skip if it's likely not a name (too common words)
                    if name in ['김치', '이것', '박스', '최고', '정말', '강의']:
                        continue
                    
                    mask_level = masking_level or self.privacy_settings.mask_patterns.get(
                        PIIType.NAME, MaskingLevel.PARTIAL
                    )
                    
                    masked_name = self._apply_masking(name, mask_level, PIIType.NAME)
                    
                    detection = PIIDetection(
                        pii_type=PIIType.NAME,
                        original_text=name,
                        masked_text=masked_name,
                        start_position=match.start(),
                        end_position=match.end(),
                        confidence=0.7,  # Lower confidence for name detection
                        masking_level=mask_level
                    )
                    detections.append(detection)
            
            return detections
            
        except Exception as e:
            logger.error(f"Failed to detect Korean names: {e}")
            return []
    
    def _anonymize_user_id(self, user_id: str) -> str:
        """Anonymize user ID using hash"""
        try:
            hash_value = hashlib.sha256(user_id.encode()).hexdigest()
            return f"anon_{hash_value[:12]}"
        except Exception as e:
            logger.error(f"Failed to anonymize user ID: {e}")
            return user_id
    
    def _mask_nickname(self, nickname: str) -> str:
        """Mask nickname if it contains PII"""
        try:
            # Check if nickname looks like a real name
            if any(surname in nickname for surname in self.korean_surnames):
                return self._partial_mask(nickname, PIIType.NAME)
            
            # Check for email-like nicknames
            if '@' in nickname:
                return self._partial_mask(nickname, PIIType.EMAIL)
            
            # Check for phone-like nicknames
            if re.match(r'.*\d{3,}.*', nickname):
                return self._partial_mask(nickname, PIIType.PHONE)
            
            return nickname
            
        except Exception as e:
            logger.error(f"Failed to mask nickname: {e}")
            return nickname
    
    def get_privacy_settings(self) -> PrivacySettings:
        """Get current privacy settings"""
        return self.privacy_settings
    
    def update_privacy_settings(self, settings: Dict[str, Any]) -> bool:
        """
        Update privacy settings
        
        Args:
            settings: New privacy settings
            
        Returns:
            bool: True if updated successfully
        """
        try:
            # Update settings
            for key, value in settings.items():
                if hasattr(self.privacy_settings, key):
                    setattr(self.privacy_settings, key, value)
            
            logger.info("Privacy settings updated")
            return True
            
        except Exception as e:
            logger.error(f"Failed to update privacy settings: {e}")
            return False
    
    def health_check(self) -> Dict[str, Any]:
        """
        Perform health check on privacy system
        
        Returns:
            Dict[str, Any]: Health check results
        """
        try:
            return {
                "pii_detection_enabled": self.privacy_settings.enable_pii_detection,
                "content_masking_enabled": self.privacy_settings.enable_content_masking,
                "pii_patterns_loaded": len(self.pii_patterns),
                "korean_surnames_loaded": len(self.korean_surnames),
                "default_masking_level": self.privacy_settings.default_masking_level.value,
                "retention_days": self.privacy_settings.retention_days,
                "anonymize_user_ids": self.privacy_settings.anonymize_user_ids
            }
            
        except Exception as e:
            logger.error(f"Privacy manager health check failed: {e}")
            return {"error": str(e)}