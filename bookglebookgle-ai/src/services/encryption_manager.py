"""
Encryption Manager for BGBG AI Server
Handles data encryption, key management, and secure storage for chat history
"""

import os
import base64
import logging
from datetime import datetime, timedelta
from typing import Dict, List, Optional, Any, Tuple, Union
from dataclasses import dataclass
from enum import Enum
import json
import hashlib

from cryptography.fernet import Fernet
from cryptography.hazmat.primitives import hashes, serialization
from cryptography.hazmat.primitives.kdf.pbkdf2 import PBKDF2HMAC
from cryptography.hazmat.primitives.asymmetric import rsa, padding
from cryptography.hazmat.backends import default_backend

from src.models.chat_history_models import ChatMessage, ConversationContext
from src.config.settings import get_settings

logger = logging.getLogger(__name__)


class EncryptionLevel(str, Enum):
    """Levels of encryption"""
    NONE = "none"           # No encryption
    BASIC = "basic"         # Symmetric encryption (Fernet)
    ADVANCED = "advanced"   # Asymmetric encryption (RSA)
    HYBRID = "hybrid"       # Hybrid encryption (RSA + AES)


class KeyType(str, Enum):
    """Types of encryption keys"""
    MASTER = "master"       # Master key for key encryption
    SESSION = "session"     # Session-specific keys
    USER = "user"          # User-specific keys
    CONTENT = "content"    # Content encryption keys


@dataclass
class EncryptionKey:
    """Encryption key information"""
    key_id: str
    key_type: KeyType
    encryption_level: EncryptionLevel
    key_data: bytes
    created_at: datetime
    expires_at: Optional[datetime] = None
    metadata: Dict[str, Any] = None
    
    def __post_init__(self):
        if self.metadata is None:
            self.metadata = {}


@dataclass
class EncryptedData:
    """Encrypted data container"""
    encrypted_content: bytes
    key_id: str
    encryption_level: EncryptionLevel
    iv: Optional[bytes] = None
    salt: Optional[bytes] = None
    metadata: Dict[str, Any] = None
    
    def __post_init__(self):
        if self.metadata is None:
            self.metadata = {}


class EncryptionManager:
    """
    Advanced encryption manager for chat history data protection
    """
    
    def __init__(self):
        """Initialize encryption manager"""
        self.settings = get_settings()
        
        # Encryption configuration
        self.default_encryption_level = EncryptionLevel.BASIC
        self.key_rotation_days = 30
        self.max_key_age_days = 90
        
        # Key storage (in production, this should be in a secure key store)
        self._keys: Dict[str, EncryptionKey] = {}
        self._master_key: Optional[bytes] = None
        
        # Initialize master key
        self._initialize_master_key()
        
        logger.info("EncryptionManager initialized")
    
    def _initialize_master_key(self) -> None:
        """Initialize or load master encryption key"""
        try:
            # In production, this should be loaded from a secure key store
            # For now, we'll generate or load from environment
            master_key_env = os.getenv('BGBG_MASTER_KEY')
            
            if master_key_env:
                # Load from environment (base64 encoded)
                self._master_key = base64.b64decode(master_key_env.encode())
                logger.info("Master key loaded from environment")
            else:
                # Generate new master key
                self._master_key = Fernet.generate_key()
                logger.warning("Generated new master key - store securely in production!")
                logger.info(f"Master key (base64): {base64.b64encode(self._master_key).decode()}")
            
        except Exception as e:
            logger.error(f"Failed to initialize master key: {e}")
            # Fallback: generate temporary key
            self._master_key = Fernet.generate_key()
    
    def generate_key(
        self, 
        key_type: KeyType, 
        encryption_level: EncryptionLevel = EncryptionLevel.BASIC,
        expires_in_days: Optional[int] = None
    ) -> str:
        """
        Generate a new encryption key
        
        Args:
            key_type: Type of key to generate
            encryption_level: Level of encryption
            expires_in_days: Optional expiration in days
            
        Returns:
            str: Key ID
        """
        try:
            key_id = f"{key_type.value}_{datetime.utcnow().strftime('%Y%m%d_%H%M%S')}_{os.urandom(4).hex()}"
            
            # Generate key based on encryption level
            if encryption_level == EncryptionLevel.BASIC:
                key_data = Fernet.generate_key()
            elif encryption_level in [EncryptionLevel.ADVANCED, EncryptionLevel.HYBRID]:
                # Generate RSA key pair
                private_key = rsa.generate_private_key(
                    public_exponent=65537,
                    key_size=2048,
                    backend=default_backend()
                )
                key_data = private_key.private_bytes(
                    encoding=serialization.Encoding.PEM,
                    format=serialization.PrivateFormat.PKCS8,
                    encryption_algorithm=serialization.NoEncryption()
                )
            else:
                raise ValueError(f"Unsupported encryption level: {encryption_level}")
            
            # Set expiration
            expires_at = None
            if expires_in_days:
                expires_at = datetime.utcnow() + timedelta(days=expires_in_days)
            elif key_type == KeyType.SESSION:
                expires_at = datetime.utcnow() + timedelta(days=1)  # Sessions expire in 1 day
            
            # Create key object
            encryption_key = EncryptionKey(
                key_id=key_id,
                key_type=key_type,
                encryption_level=encryption_level,
                key_data=key_data,
                created_at=datetime.utcnow(),
                expires_at=expires_at
            )
            
            # Store key (encrypted with master key)
            self._keys[key_id] = encryption_key
            
            logger.info(f"Generated {encryption_level.value} key: {key_id}")
            return key_id
            
        except Exception as e:
            logger.error(f"Failed to generate key: {e}")
            raise
    
    def encrypt_message(
        self, 
        message: ChatMessage, 
        encryption_level: Optional[EncryptionLevel] = None,
        key_id: Optional[str] = None
    ) -> EncryptedData:
        """
        Encrypt a chat message
        
        Args:
            message: Message to encrypt
            encryption_level: Level of encryption to use
            key_id: Specific key to use (optional)
            
        Returns:
            EncryptedData: Encrypted message data
        """
        try:
            # Determine encryption level
            enc_level = encryption_level or self.default_encryption_level
            
            if enc_level == EncryptionLevel.NONE:
                # No encryption - return as-is
                message_json = message.to_json()
                return EncryptedData(
                    encrypted_content=message_json.encode(),
                    key_id="none",
                    encryption_level=enc_level
                )
            
            # Get or generate key
            if not key_id:
                key_id = self.generate_key(KeyType.CONTENT, enc_level)
            
            # Serialize message
            message_data = {
                "message_id": message.message_id,
                "session_id": message.session_id,
                "user_id": message.user_id,
                "nickname": message.nickname,
                "content": message.content,
                "timestamp": message.timestamp.isoformat(),
                "message_type": message.message_type.value,
                "metadata": message.metadata,
                "sentiment": message.sentiment,
                "topics": message.topics,
                "intent": message.intent
            }
            
            message_json = json.dumps(message_data, ensure_ascii=False)
            plaintext = message_json.encode('utf-8')
            
            # Encrypt based on level
            if enc_level == EncryptionLevel.BASIC:
                encrypted_content = self._encrypt_symmetric(plaintext, key_id)
                return EncryptedData(
                    encrypted_content=encrypted_content,
                    key_id=key_id,
                    encryption_level=enc_level
                )
            
            elif enc_level == EncryptionLevel.ADVANCED:
                encrypted_content = self._encrypt_asymmetric(plaintext, key_id)
                return EncryptedData(
                    encrypted_content=encrypted_content,
                    key_id=key_id,
                    encryption_level=enc_level
                )
            
            elif enc_level == EncryptionLevel.HYBRID:
                encrypted_content, session_key_encrypted = self._encrypt_hybrid(plaintext, key_id)
                return EncryptedData(
                    encrypted_content=encrypted_content,
                    key_id=key_id,
                    encryption_level=enc_level,
                    metadata={"session_key": base64.b64encode(session_key_encrypted).decode()}
                )
            
            else:
                raise ValueError(f"Unsupported encryption level: {enc_level}")
                
        except Exception as e:
            logger.error(f"Failed to encrypt message: {e}")
            raise
    
    def decrypt_message(self, encrypted_data: EncryptedData) -> ChatMessage:
        """
        Decrypt an encrypted message
        
        Args:
            encrypted_data: Encrypted message data
            
        Returns:
            ChatMessage: Decrypted message
        """
        try:
            if encrypted_data.encryption_level == EncryptionLevel.NONE:
                # No encryption - decode directly
                message_json = encrypted_data.encrypted_content.decode()
                message_data = json.loads(message_json)
            else:
                # Decrypt based on level
                if encrypted_data.encryption_level == EncryptionLevel.BASIC:
                    plaintext = self._decrypt_symmetric(encrypted_data.encrypted_content, encrypted_data.key_id)
                elif encrypted_data.encryption_level == EncryptionLevel.ADVANCED:
                    plaintext = self._decrypt_asymmetric(encrypted_data.encrypted_content, encrypted_data.key_id)
                elif encrypted_data.encryption_level == EncryptionLevel.HYBRID:
                    session_key_encrypted = base64.b64decode(encrypted_data.metadata["session_key"].encode())
                    plaintext = self._decrypt_hybrid(encrypted_data.encrypted_content, encrypted_data.key_id, session_key_encrypted)
                else:
                    raise ValueError(f"Unsupported encryption level: {encrypted_data.encryption_level}")
                
                message_json = plaintext.decode('utf-8')
                message_data = json.loads(message_json)
            
            # Reconstruct ChatMessage
            message = ChatMessage(
                message_id=message_data["message_id"],
                session_id=message_data["session_id"],
                user_id=message_data["user_id"],
                nickname=message_data["nickname"],
                content=message_data["content"],
                timestamp=datetime.fromisoformat(message_data["timestamp"]),
                message_type=message_data["message_type"],
                metadata=message_data.get("metadata", {}),
                sentiment=message_data.get("sentiment"),
                topics=message_data.get("topics"),
                intent=message_data.get("intent")
            )
            
            return message
            
        except Exception as e:
            logger.error(f"Failed to decrypt message: {e}")
            raise
    
    def encrypt_context(
        self, 
        context: ConversationContext, 
        encryption_level: Optional[EncryptionLevel] = None
    ) -> EncryptedData:
        """
        Encrypt conversation context
        
        Args:
            context: Context to encrypt
            encryption_level: Level of encryption to use
            
        Returns:
            EncryptedData: Encrypted context data
        """
        try:
            # Serialize context (lightweight version for encryption)
            context_data = {
                "session_id": context.session_id,
                "recent_messages": [msg.to_dict() for msg in context.recent_messages],
                "book_context": context.book_context,
                "conversation_summary": context.conversation_summary,
                "active_topics": context.active_topics,
                "context_window_size": context.context_window_size,
                "total_token_count": context.total_token_count,
                "created_at": context.created_at.isoformat(),
                "last_updated": context.last_updated.isoformat()
            }
            
            context_json = json.dumps(context_data, ensure_ascii=False)
            plaintext = context_json.encode('utf-8')
            
            # Use session-level encryption for contexts
            enc_level = encryption_level or EncryptionLevel.BASIC
            key_id = self.generate_key(KeyType.SESSION, enc_level)
            
            if enc_level == EncryptionLevel.BASIC:
                encrypted_content = self._encrypt_symmetric(plaintext, key_id)
            else:
                # For contexts, we'll use basic encryption for performance
                encrypted_content = self._encrypt_symmetric(plaintext, key_id)
            
            return EncryptedData(
                encrypted_content=encrypted_content,
                key_id=key_id,
                encryption_level=enc_level
            )
            
        except Exception as e:
            logger.error(f"Failed to encrypt context: {e}")
            raise
    
    def decrypt_context(self, encrypted_data: EncryptedData) -> Dict[str, Any]:
        """
        Decrypt conversation context
        
        Args:
            encrypted_data: Encrypted context data
            
        Returns:
            Dict[str, Any]: Decrypted context data
        """
        try:
            # Decrypt content
            plaintext = self._decrypt_symmetric(encrypted_data.encrypted_content, encrypted_data.key_id)
            context_json = plaintext.decode('utf-8')
            context_data = json.loads(context_json)
            
            return context_data
            
        except Exception as e:
            logger.error(f"Failed to decrypt context: {e}")
            raise
    
    def rotate_keys(self, key_type: Optional[KeyType] = None) -> Dict[str, Any]:
        """
        Rotate encryption keys
        
        Args:
            key_type: Specific key type to rotate (optional)
            
        Returns:
            Dict[str, Any]: Rotation results
        """
        try:
            logger.info("🔄 Starting key rotation")
            
            rotated_keys = []
            expired_keys = []
            
            # Find keys that need rotation
            cutoff_date = datetime.utcnow() - timedelta(days=self.key_rotation_days)
            
            for key_id, key in list(self._keys.items()):
                # Skip if filtering by key type
                if key_type and key.key_type != key_type:
                    continue
                
                # Check if key needs rotation
                needs_rotation = (
                    key.created_at < cutoff_date or
                    (key.expires_at and key.expires_at < datetime.utcnow())
                )
                
                if needs_rotation:
                    # Generate new key
                    new_key_id = self.generate_key(key.key_type, key.encryption_level)
                    rotated_keys.append({
                        "old_key_id": key_id,
                        "new_key_id": new_key_id,
                        "key_type": key.key_type.value
                    })
                    
                    # Mark old key as expired
                    expired_keys.append(key_id)
            
            # Clean up expired keys (in production, ensure no data depends on them)
            for key_id in expired_keys:
                if key_id in self._keys:
                    del self._keys[key_id]
            
            result = {
                "rotated_keys": len(rotated_keys),
                "expired_keys": len(expired_keys),
                "rotation_details": rotated_keys,
                "rotation_timestamp": datetime.utcnow().isoformat()
            }
            
            logger.info(f"✅ Key rotation completed: {result}")
            return result
            
        except Exception as e:
            logger.error(f"Key rotation failed: {e}")
            return {"error": str(e)}
    
    def get_key_info(self, key_id: str) -> Optional[Dict[str, Any]]:
        """
        Get information about an encryption key
        
        Args:
            key_id: Key identifier
            
        Returns:
            Optional[Dict[str, Any]]: Key information (without sensitive data)
        """
        try:
            if key_id not in self._keys:
                return None
            
            key = self._keys[key_id]
            
            return {
                "key_id": key.key_id,
                "key_type": key.key_type.value,
                "encryption_level": key.encryption_level.value,
                "created_at": key.created_at.isoformat(),
                "expires_at": key.expires_at.isoformat() if key.expires_at else None,
                "is_expired": key.expires_at and key.expires_at < datetime.utcnow(),
                "metadata": key.metadata
            }
            
        except Exception as e:
            logger.error(f"Failed to get key info: {e}")
            return None
    
    def list_keys(self, key_type: Optional[KeyType] = None) -> List[Dict[str, Any]]:
        """
        List encryption keys
        
        Args:
            key_type: Filter by key type (optional)
            
        Returns:
            List[Dict[str, Any]]: List of key information
        """
        try:
            keys_info = []
            
            for key_id, key in self._keys.items():
                if key_type and key.key_type != key_type:
                    continue
                
                key_info = self.get_key_info(key_id)
                if key_info:
                    keys_info.append(key_info)
            
            return keys_info
            
        except Exception as e:
            logger.error(f"Failed to list keys: {e}")
            return []
    
    # Private encryption methods
    
    def _encrypt_symmetric(self, plaintext: bytes, key_id: str) -> bytes:
        """Encrypt using symmetric encryption (Fernet)"""
        try:
            key = self._keys[key_id]
            fernet = Fernet(key.key_data)
            return fernet.encrypt(plaintext)
        except Exception as e:
            logger.error(f"Symmetric encryption failed: {e}")
            raise
    
    def _decrypt_symmetric(self, ciphertext: bytes, key_id: str) -> bytes:
        """Decrypt using symmetric encryption (Fernet)"""
        try:
            key = self._keys[key_id]
            fernet = Fernet(key.key_data)
            return fernet.decrypt(ciphertext)
        except Exception as e:
            logger.error(f"Symmetric decryption failed: {e}")
            raise
    
    def _encrypt_asymmetric(self, plaintext: bytes, key_id: str) -> bytes:
        """Encrypt using asymmetric encryption (RSA)"""
        try:
            key = self._keys[key_id]
            private_key = serialization.load_pem_private_key(
                key.key_data, password=None, backend=default_backend()
            )
            public_key = private_key.public_key()
            
            # RSA can only encrypt small amounts of data
            # For larger data, we'd need to use hybrid encryption
            max_chunk_size = 190  # For 2048-bit RSA key
            
            if len(plaintext) <= max_chunk_size:
                return public_key.encrypt(
                    plaintext,
                    padding.OAEP(
                        mgf=padding.MGF1(algorithm=hashes.SHA256()),
                        algorithm=hashes.SHA256(),
                        label=None
                    )
                )
            else:
                raise ValueError("Data too large for RSA encryption - use hybrid encryption")
                
        except Exception as e:
            logger.error(f"Asymmetric encryption failed: {e}")
            raise
    
    def _decrypt_asymmetric(self, ciphertext: bytes, key_id: str) -> bytes:
        """Decrypt using asymmetric encryption (RSA)"""
        try:
            key = self._keys[key_id]
            private_key = serialization.load_pem_private_key(
                key.key_data, password=None, backend=default_backend()
            )
            
            return private_key.decrypt(
                ciphertext,
                padding.OAEP(
                    mgf=padding.MGF1(algorithm=hashes.SHA256()),
                    algorithm=hashes.SHA256(),
                    label=None
                )
            )
            
        except Exception as e:
            logger.error(f"Asymmetric decryption failed: {e}")
            raise
    
    def _encrypt_hybrid(self, plaintext: bytes, key_id: str) -> Tuple[bytes, bytes]:
        """Encrypt using hybrid encryption (RSA + AES)"""
        try:
            # Generate session key for AES
            session_key = Fernet.generate_key()
            
            # Encrypt data with AES
            fernet = Fernet(session_key)
            encrypted_data = fernet.encrypt(plaintext)
            
            # Encrypt session key with RSA
            key = self._keys[key_id]
            private_key = serialization.load_pem_private_key(
                key.key_data, password=None, backend=default_backend()
            )
            public_key = private_key.public_key()
            
            encrypted_session_key = public_key.encrypt(
                session_key,
                padding.OAEP(
                    mgf=padding.MGF1(algorithm=hashes.SHA256()),
                    algorithm=hashes.SHA256(),
                    label=None
                )
            )
            
            return encrypted_data, encrypted_session_key
            
        except Exception as e:
            logger.error(f"Hybrid encryption failed: {e}")
            raise
    
    def _decrypt_hybrid(self, ciphertext: bytes, key_id: str, encrypted_session_key: bytes) -> bytes:
        """Decrypt using hybrid encryption (RSA + AES)"""
        try:
            # Decrypt session key with RSA
            key = self._keys[key_id]
            private_key = serialization.load_pem_private_key(
                key.key_data, password=None, backend=default_backend()
            )
            
            session_key = private_key.decrypt(
                encrypted_session_key,
                padding.OAEP(
                    mgf=padding.MGF1(algorithm=hashes.SHA256()),
                    algorithm=hashes.SHA256(),
                    label=None
                )
            )
            
            # Decrypt data with AES
            fernet = Fernet(session_key)
            return fernet.decrypt(ciphertext)
            
        except Exception as e:
            logger.error(f"Hybrid decryption failed: {e}")
            raise
    
    def health_check(self) -> Dict[str, Any]:
        """
        Perform health check on encryption system
        
        Returns:
            Dict[str, Any]: Health check results
        """
        try:
            # Count keys by type and status
            key_counts = {}
            expired_keys = 0
            
            for key in self._keys.values():
                key_type = key.key_type.value
                key_counts[key_type] = key_counts.get(key_type, 0) + 1
                
                if key.expires_at and key.expires_at < datetime.utcnow():
                    expired_keys += 1
            
            return {
                "master_key_initialized": self._master_key is not None,
                "total_keys": len(self._keys),
                "key_counts_by_type": key_counts,
                "expired_keys": expired_keys,
                "default_encryption_level": self.default_encryption_level.value,
                "key_rotation_days": self.key_rotation_days
            }
            
        except Exception as e:
            logger.error(f"Encryption manager health check failed: {e}")
            return {"error": str(e)}