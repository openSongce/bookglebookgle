"""
Service Initializer for BGBG AI Server
Manages the initialization of all AI services with proper dependency injection
"""

import asyncio
from dataclasses import dataclass
from typing import Dict, Any, Optional
from loguru import logger

from src.services.llm_client import LLMClient
from src.services.quiz_service import QuizService
from src.services.proofreading_service import ProofreadingService
from src.services.vector_db import VectorDBManager
from src.services.redis_connection_manager import RedisConnectionManager
from src.config.settings import get_settings


@dataclass
class ServiceInitializationStatus:
    """서비스 초기화 상태를 추적하는 데이터 클래스"""
    llm_client: bool = False
    quiz_service: bool = False
    proofreading_service: bool = False
    vector_db: bool = False
    redis_manager: bool = False
    
    @property
    def all_critical_services_ready(self) -> bool:
        """필수 서비스들이 모두 준비되었는지 확인"""
        return self.llm_client and self.vector_db
    
    @property
    def ai_features_ready(self) -> bool:
        """AI 기능들이 준비되었는지 확인"""
        return self.quiz_service and self.proofreading_service
    
    def get_status_summary(self) -> str:
        """초기화 상태 요약 반환"""
        services = {
            "LLM Client": self.llm_client,
            "Quiz Service": self.quiz_service,
            "Proofreading Service": self.proofreading_service,
            "Vector DB": self.vector_db,
            "Redis Manager": self.redis_manager
        }
        
        ready_count = sum(services.values())
        total_count = len(services)
        
        status_details = []
        for name, status in services.items():
            status_icon = "✅" if status else "❌"
            status_details.append(f"{status_icon} {name}")
        
        return f"Services Ready: {ready_count}/{total_count}\n" + "\n".join(status_details)


class ServiceInitializationError(Exception):
    """서비스 초기화 실패 예외"""
    pass


class CriticalServiceFailure(ServiceInitializationError):
    """필수 서비스 초기화 실패"""
    pass


class OptionalServiceFailure(ServiceInitializationError):
    """선택적 서비스 초기화 실패"""
    pass


class ServiceInitializer:
    """AI 서비스들의 초기화를 관리하는 클래스"""
    
    def __init__(self):
        self.settings = get_settings()
        self.status = ServiceInitializationStatus()
        self.services: Dict[str, Any] = {}
    
    async def initialize_all_services(self) -> Dict[str, Any]:
        """모든 서비스를 올바른 순서로 초기화"""
        logger.info("🔧 Starting comprehensive service initialization...")
        
        try:
            # 1. 기본 서비스들 초기화 (병렬)
            basic_services = await self._initialize_basic_services()
            
            # 2. LLM 기반 서비스들 초기화 (순차) - basic_services 전달
            ai_services = await self._initialize_ai_services(
                basic_services.get('llm_client'),
                basic_services  # basic_services 전달
            )
            
            # 3. 모든 서비스 통합
            all_services = {**basic_services, **ai_services}
            self.services = all_services
            
            # 4. 초기화 상태 로깅
            logger.info("📊 Service Initialization Summary:")
            logger.info(f"\n{self.status.get_status_summary()}")
            
            # 5. 필수 서비스 확인
            if not self.status.all_critical_services_ready:
                raise CriticalServiceFailure("Critical services failed to initialize")
            
            logger.info("✅ All services initialized successfully")
            return all_services
            
        except Exception as e:
            logger.error(f"❌ Service initialization failed: {e}")
            logger.info(f"📊 Final Status:\n{self.status.get_status_summary()}")
            raise
    
    async def _initialize_basic_services(self) -> Dict[str, Any]:
        """기본 서비스들을 병렬로 초기화"""
        logger.info("🔧 Initializing basic services...")
        
        services = {}
        
        # Redis Manager 초기화 (선택적)
        try:
            logger.info("🔗 Initializing Redis Connection Manager...")
            redis_manager = RedisConnectionManager()
            await redis_manager.initialize()
            services['redis_manager'] = redis_manager
            self.status.redis_manager = True
            logger.info("✅ Redis Connection Manager initialized")
        except Exception as e:
            logger.warning(f"⚠️ Redis Connection Manager failed: {e}")
            logger.info("💡 Continuing without Redis (chat history will be limited)")
            services['redis_manager'] = None
            self.status.redis_manager = False
        
        # Vector DB Manager 초기화 (필수)
        try:
            logger.info("🗄️ Initializing Vector DB Manager...")
            vector_db = VectorDBManager()
            await vector_db.initialize()
            services['vector_db'] = vector_db
            self.status.vector_db = True
            logger.info("✅ Vector DB Manager initialized")
        except Exception as e:
            logger.error(f"❌ Vector DB Manager failed: {e}")
            services['vector_db'] = None
            self.status.vector_db = False
            raise CriticalServiceFailure(f"Vector DB initialization failed: {e}")
        
        # LLM Client 초기화 (필수)
        try:
            logger.info("🤖 Initializing LLM Client...")
            llm_client = LLMClient()
            await llm_client.initialize()
            services['llm_client'] = llm_client
            self.status.llm_client = True
            logger.info("✅ LLM Client initialized")
        except Exception as e:
            logger.error(f"❌ LLM Client failed: {e}")
            services['llm_client'] = None
            self.status.llm_client = False
            raise CriticalServiceFailure(f"LLM Client initialization failed: {e}")
        
        return services
    
    async def _initialize_ai_services(
        self, 
        llm_client: Optional[LLMClient],
        basic_services: Dict[str, Any] = None
    ) -> Dict[str, Any]:
        """LLM 기반 AI 서비스들을 초기화"""
        logger.info("🧠 Initializing AI services...")
        
        services = {}
        
        if not llm_client:
            logger.warning("⚠️ LLM Client not available, skipping AI services")
            services['quiz_service'] = None
            services['proofreading_service'] = None
            return services
        
        # VectorDB 가져오기 (QuizService에서 필요) - basic_services에서 직접 가져오기
        vector_db = basic_services.get('vector_db') if basic_services else None
        
        # Quiz Service 초기화
        try:
            logger.info("📝 Initializing Quiz Service...")
            quiz_service = QuizService()
            # LLM Client 재사용하여 초기화
            quiz_service.llm_client = llm_client
            if llm_client:
                from src.services.llm_client import QuizLLMClient
                quiz_service.quiz_llm_client = QuizLLMClient(llm_client)
            
            # VectorDB 주입 (중요!)
            quiz_service.vector_db = vector_db
            logger.info(f"📊 QuizService VectorDB injection: {'✅ Success' if vector_db else '❌ VectorDB not available'}")
            
            await quiz_service.initialize()
            services['quiz_service'] = quiz_service
            self.status.quiz_service = True
            logger.info("✅ Quiz Service initialized")
        except Exception as e:
            logger.warning(f"⚠️ Quiz Service failed: {e}")
            logger.info("💡 Quiz generation will use mock data")
            services['quiz_service'] = None
            self.status.quiz_service = False
        
        # Proofreading Service 초기화
        try:
            logger.info("✏️ Initializing Proofreading Service...")
            proofreading_service = ProofreadingService()
            # LLM Client 재사용하여 초기화
            proofreading_service.llm_client = llm_client
            if llm_client:
                from src.services.llm_client import ProofreadingLLMClient
                proofreading_service.proofreading_llm_client = ProofreadingLLMClient(llm_client)
            await proofreading_service.initialize()
            services['proofreading_service'] = proofreading_service
            self.status.proofreading_service = True
            logger.info("✅ Proofreading Service initialized")
        except Exception as e:
            logger.warning(f"⚠️ Proofreading Service failed: {e}")
            logger.info("💡 Text proofreading will use mock data")
            services['proofreading_service'] = None
            self.status.proofreading_service = False
        
        return services
    
    def get_initialization_status(self) -> ServiceInitializationStatus:
        """현재 초기화 상태 반환"""
        return self.status
    
    def get_initialized_services(self) -> Dict[str, Any]:
        """초기화된 서비스들 반환"""
        return self.services.copy()
    
    async def cleanup_services(self):
        """서비스들 정리"""
        logger.info("🧹 Cleaning up services...")
        
        try:
            if self.services.get('vector_db'):
                # VectorDB cleanup if available
                if hasattr(self.services['vector_db'], 'cleanup'):
                    await self.services['vector_db'].cleanup()
                logger.info("✅ Vector DB cleaned up")
        except Exception as e:
            logger.error(f"⚠️ Error cleaning up Vector DB: {e}")
        
        try:
            if self.services.get('redis_manager'):
                await self.services['redis_manager'].cleanup()
                logger.info("✅ Redis connections cleaned up")
        except Exception as e:
            logger.error(f"⚠️ Error cleaning up Redis: {e}")
        
        logger.info("✅ Service cleanup complete")