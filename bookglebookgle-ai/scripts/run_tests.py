#!/usr/bin/env python3
"""
Simple test runner for BGBG AI Server Chat History System
Runs basic functionality tests without requiring pytest
"""

import asyncio
import sys
import os
import traceback
from datetime import datetime, timedelta
from typing import Dict, List, Any

# Add src to path
sys.path.insert(0, os.path.join(os.path.dirname(__file__), '..', 'src'))

from models.chat_history_models import ChatMessage, ConversationContext, MessageType
from services.redis_connection_manager import RedisConnectionManager
from services.privacy_manager import PrivacyManager
from config.chat_config import get_chat_config


class TestResult:
    """Test result container"""
    def __init__(self, name: str, passed: bool, error: str = None):
        self.name = name
        self.passed = passed
        self.error = error
        self.timestamp = datetime.utcnow()


class SimpleTestRunner:
    """Simple test runner for chat history components"""
    
    def __init__(self):
        self.results: List[TestResult] = []
        self.total_tests = 0
        self.passed_tests = 0
    
    def run_test(self, test_name: str, test_func):
        """Run a single test"""
        self.total_tests += 1
        print(f"🧪 Running: {test_name}")
        
        try:
            if asyncio.iscoroutinefunction(test_func):
                asyncio.run(test_func())
            else:
                test_func()
            
            self.passed_tests += 1
            self.results.append(TestResult(test_name, True))
            print(f"   ✅ PASSED")
            
        except Exception as e:
            error_msg = str(e)
            self.results.append(TestResult(test_name, False, error_msg))
            print(f"   ❌ FAILED: {error_msg}")
            if "--verbose" in sys.argv:
                print(f"   📋 Traceback: {traceback.format_exc()}")
    
    def print_summary(self):
        """Print test summary"""
        print("\n" + "="*60)
        print("🧪 Test Summary")
        print("="*60)
        print(f"Total Tests: {self.total_tests}")
        print(f"Passed: {self.passed_tests}")
        print(f"Failed: {self.total_tests - self.passed_tests}")
        print(f"Success Rate: {(self.passed_tests/self.total_tests*100):.1f}%")
        
        if self.total_tests - self.passed_tests > 0:
            print("\n❌ Failed Tests:")
            for result in self.results:
                if not result.passed:
                    print(f"   - {result.name}: {result.error}")
        
        print("="*60)
        return self.passed_tests == self.total_tests


# Test Functions

def test_chat_message_creation():
    """Test ChatMessage model creation and serialization"""
    message = ChatMessage(
        message_id="test-123",
        session_id="session-456",
        user_id="user-789",
        nickname="TestUser",
        content="Hello, this is a test message!",
        timestamp=datetime.utcnow(),
        message_type=MessageType.USER
    )
    
    # Test serialization
    message_dict = message.to_dict()
    assert message_dict["content"] == "Hello, this is a test message!"
    assert message_dict["message_type"] == "user"
    
    # Test JSON serialization
    json_str = message.to_json()
    assert "Hello, this is a test message!" in json_str
    
    # Test deserialization
    restored_message = ChatMessage.from_json(json_str)
    assert restored_message.content == message.content
    assert restored_message.user_id == message.user_id


def test_conversation_context():
    """Test ConversationContext functionality"""
    context = ConversationContext(
        session_id="test-session",
        recent_messages=[],
        book_context=["Sample book content", "More book content"],
        context_window_size=5
    )
    
    # Add messages
    for i in range(3):
        message = ChatMessage(
            message_id=f"msg-{i}",
            session_id="test-session",
            user_id=f"user-{i}",
            nickname=f"User{i}",
            content=f"Message {i}",
            timestamp=datetime.utcnow(),
            message_type=MessageType.USER
        )
        context.add_message(message)
    
    assert len(context.recent_messages) == 3
    assert len(context.participant_states) == 3
    
    # Test context text generation
    context_text = context.get_recent_messages_text()
    assert "Message 0" in context_text
    assert "Message 2" in context_text


def test_privacy_manager():
    """Test PrivacyManager PII detection"""
    privacy_manager = PrivacyManager()
    
    # Test email detection
    content = "My email is test@example.com and phone is 010-1234-5678"
    masked_content, detections = privacy_manager.mask_sensitive_content(content)
    
    assert len(detections) >= 1  # Should detect at least email
    assert "test@example.com" not in masked_content  # Should be masked
    
    # Test Korean name detection
    korean_content = "안녕하세요, 김철수입니다."
    masked_korean, korean_detections = privacy_manager.mask_sensitive_content(korean_content)
    
    # May or may not detect Korean names depending on implementation
    print(f"   📝 Korean detection result: {len(korean_detections)} detections")


def test_chat_config():
    """Test chat configuration system"""
    config = get_chat_config()
    
    assert hasattr(config, 'enabled')
    assert hasattr(config, 'message_ttl_hours')
    assert hasattr(config, 'context_window_size')
    
    # Test validation
    issues = config.validate()
    assert isinstance(issues, list)
    
    # Test runtime info
    runtime_info = config.get_runtime_info()
    assert 'configuration_level' in runtime_info
    assert 'features_enabled' in runtime_info


async def test_redis_connection_manager():
    """Test Redis connection manager (requires Redis to be running)"""
    try:
        manager = RedisConnectionManager()
        
        # Test initialization
        success = await manager.initialize()
        if not success:
            raise Exception("Failed to initialize Redis connection")
        
        # Test basic operations
        client = await manager.get_client()
        if client is None:
            raise Exception("Failed to get Redis client")
        
        # Test set/get
        test_key = "test:chat_history"
        test_value = "test_value"
        
        await manager.set_with_ttl(test_key, test_value, 60)
        retrieved_value = await manager.get_key(test_key)
        
        if retrieved_value != test_value:
            raise Exception(f"Value mismatch: expected {test_value}, got {retrieved_value}")
        
        # Test TTL
        ttl = await manager.get_ttl(test_key)
        if ttl <= 0:
            raise Exception(f"Invalid TTL: {ttl}")
        
        # Cleanup
        await manager.delete_keys(test_key)
        await manager.shutdown()
        
    except Exception as e:
        if "Connection refused" in str(e) or "Redis" in str(e):
            raise Exception("Redis server not available - please start Redis first")
        raise


def test_message_processing_flow():
    """Test complete message processing flow"""
    privacy_manager = PrivacyManager()
    
    # Create test message
    original_message = ChatMessage(
        message_id="flow-test",
        session_id="flow-session",
        user_id="flow-user",
        nickname="FlowUser",
        content="Hi, my email is user@test.com",
        timestamp=datetime.utcnow(),
        message_type=MessageType.USER
    )
    
    # Process for privacy
    processed_message = privacy_manager.process_chat_message(original_message)
    
    # Verify processing
    assert processed_message.message_id == original_message.message_id
    assert processed_message.metadata.get("privacy_processed") == True
    
    # Content should be masked
    if "user@test.com" not in processed_message.content:
        print("   📝 Email was successfully masked")
    else:
        print("   ⚠️  Email was not masked (privacy detection may be disabled)")


def test_performance_simulation():
    """Test performance with multiple messages"""
    import time
    
    start_time = time.time()
    
    # Create multiple messages
    messages = []
    for i in range(100):
        message = ChatMessage(
            message_id=f"perf-{i}",
            session_id="perf-session",
            user_id=f"user-{i % 10}",  # 10 different users
            nickname=f"User{i % 10}",
            content=f"Performance test message {i}",
            timestamp=datetime.utcnow(),
            message_type=MessageType.USER
        )
        messages.append(message)
    
    # Create context and add all messages
    context = ConversationContext(
        session_id="perf-session",
        recent_messages=[],
        book_context=[],
        context_window_size=50
    )
    
    for message in messages:
        context.add_message(message)
    
    end_time = time.time()
    processing_time = end_time - start_time
    
    assert len(context.recent_messages) <= 50  # Should respect window size
    assert len(context.participant_states) == 10  # 10 different users
    
    print(f"   📊 Processed 100 messages in {processing_time:.3f}s")
    
    if processing_time > 1.0:
        print("   ⚠️  Performance warning: processing took longer than expected")


# Main test execution
def main():
    """Run all tests"""
    print("🚀 BGBG AI Server - Chat History System Tests")
    print("=" * 60)
    
    runner = SimpleTestRunner()
    
    # Basic model tests
    runner.run_test("ChatMessage Creation", test_chat_message_creation)
    runner.run_test("ConversationContext", test_conversation_context)
    runner.run_test("Chat Configuration", test_chat_config)
    
    # Privacy tests
    runner.run_test("Privacy Manager", test_privacy_manager)
    runner.run_test("Message Processing Flow", test_message_processing_flow)
    
    # Performance tests
    runner.run_test("Performance Simulation", test_performance_simulation)
    
    # Redis tests (may fail if Redis not available)
    if "--skip-redis" not in sys.argv:
        runner.run_test("Redis Connection Manager", test_redis_connection_manager)
    else:
        print("🔄 Skipping Redis tests (--skip-redis flag)")
    
    # Print summary
    success = runner.print_summary()
    
    if success:
        print("🎉 All tests passed!")
        sys.exit(0)
    else:
        print("💥 Some tests failed!")
        sys.exit(1)


if __name__ == "__main__":
    main()