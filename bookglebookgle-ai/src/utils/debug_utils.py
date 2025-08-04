"""
고급 디버깅 및 로깅 유틸리티
실시간 모니터링, 성능 분석, 오류 추적을 위한 강화된 도구들
"""

import asyncio
import time
import traceback
import json
import logging
from datetime import datetime
from typing import Dict, List, Any, Optional, Callable
from pathlib import Path
from dataclasses import dataclass, asdict
from contextlib import asynccontextmanager
import threading
from collections import deque
import psutil
import platform

# 프로젝트 루트 경로
project_root = Path(__file__).parent.parent.parent

@dataclass
class DebugMetrics:
    """디버깅 메트릭스 데이터 클래스"""
    timestamp: float
    operation: str
    duration: float
    memory_usage: float
    cpu_usage: float
    success: bool
    error_message: Optional[str] = None
    details: Optional[Dict[str, Any]] = None

class DebugLogger:
    """고급 디버깅 로거"""
    
    def __init__(self, name: str, log_file: Optional[str] = None):
        self.name = name
        self.log_file = log_file or f"debug_{name}_{datetime.now().strftime('%Y%m%d_%H%M%S')}.log"
        self.metrics_history = deque(maxlen=1000)  # 최근 1000개의 메트릭스 저장
        self._setup_logger()
        
    def _setup_logger(self):
        """로거 설정"""
        # 로거 생성
        self.logger = logging.getLogger(self.name)
        self.logger.setLevel(logging.DEBUG)
        
        # 핸들러 생성
        formatter = logging.Formatter(
            '%(asctime)s | %(name)s | %(levelname)s | %(message)s',
            datefmt='%Y-%m-%d %H:%M:%S'
        )
        
        # 콘솔 핸들러
        console_handler = logging.StreamHandler()
        console_handler.setLevel(logging.INFO)
        console_handler.setFormatter(formatter)
        self.logger.addHandler(console_handler)
        
        # 파일 핸들러
        file_handler = logging.FileHandler(self.log_file, encoding='utf-8')
        file_handler.setLevel(logging.DEBUG)
        file_handler.setFormatter(formatter)
        self.logger.addHandler(file_handler)
        
        # 성능 로그 파일 별도 저장
        perf_log_file = f"performance_{self.name}_{datetime.now().strftime('%Y%m%d_%H%M%S')}.log"
        self.perf_handler = logging.FileHandler(perf_log_file, encoding='utf-8')
        self.perf_handler.setLevel(logging.INFO)
        perf_formatter = logging.Formatter('%(asctime)s | PERFORMANCE | %(message)s')
        self.perf_handler.setFormatter(perf_formatter)
        
    def log_operation(self, operation: str, duration: float, success: bool, 
                     memory_usage: float = 0, cpu_usage: float = 0, 
                     error_message: Optional[str] = None, details: Optional[Dict] = None):
        """작업 로깅"""
        metrics = DebugMetrics(
            timestamp=time.time(),
            operation=operation,
            duration=duration,
            memory_usage=memory_usage,
            cpu_usage=cpu_usage,
            success=success,
            error_message=error_message,
            details=details
        )
        
        # 메트릭스 기록
        self.metrics_history.append(metrics)
        
        # 로그 기록
        status = "✅" if success else "❌"
        log_msg = f"{status} {operation} - {duration:.3f}s"
        
        if memory_usage > 0:
            log_msg += f" | 메모리: {memory_usage:.1f}MB"
        if cpu_usage > 0:
            log_msg += f" | CPU: {cpu_usage:.1f}%"
        if error_message:
            log_msg += f" | 오류: {error_message}"
            
        if success:
            self.logger.info(log_msg)
        else:
            self.logger.error(log_msg)
            
        # 성능 로그 별도 저장
        self.perf_handler.log(
            logging.INFO,
            json.dumps({
                "timestamp": metrics.timestamp,
                "operation": operation,
                "duration": duration,
                "memory_mb": memory_usage,
                "cpu_percent": cpu_usage,
                "success": success,
                "error": error_message
            }, ensure_ascii=False)
        )
        
    def get_metrics_summary(self) -> Dict[str, Any]:
        """메트릭스 요약 정보 반환"""
        if not self.metrics_history:
            return {}
            
        total_operations = len(self.metrics_history)
        successful_operations = sum(1 for m in self.metrics_history if m.success)
        failed_operations = total_operations - successful_operations
        
        # 성공률 계산
        success_rate = (successful_operations / total_operations) * 100 if total_operations > 0 else 0
        
        # 평균 처리 시간
        avg_duration = sum(m.duration for m in self.metrics_history) / total_operations if total_operations > 0 else 0
        
        # 메모리 사용량 통계
        memory_usages = [m.memory_usage for m in self.metrics_history if m.memory_usage > 0]
        avg_memory = sum(memory_usages) / len(memory_usages) if memory_usages else 0
        max_memory = max(memory_usages) if memory_usages else 0
        
        # 최근 오류 분석
        recent_errors = [m.error_message for m in self.metrics_history if not m.success and m.error_message]
        error_frequency = {}
        for error in recent_errors:
            error_frequency[error] = error_frequency.get(error, 0) + 1
            
        return {
            "total_operations": total_operations,
            "successful_operations": successful_operations,
            "failed_operations": failed_operations,
            "success_rate_percent": success_rate,
            "average_duration_seconds": avg_duration,
            "average_memory_mb": avg_memory,
            "max_memory_mb": max_memory,
            "recent_errors": error_frequency,
            "log_file": self.log_file,
            "performance_log_file": self.perf_handler.baseFilename
        }

class PerformanceMonitor:
    """실시간 성능 모니터"""
    
    def __init__(self, name: str, interval: float = 1.0):
        self.name = name
        self.interval = interval
        self.is_running = False
        self.monitor_thread = None
        self.metrics_callback: Optional[Callable] = None
        self._setup_system_info()
        
    def _setup_system_info(self):
        """시스템 정보 설정"""
        self.system_info = {
            "platform": platform.system(),
            "platform_version": platform.version(),
            "processor": platform.processor(),
            "python_version": platform.python_version(),
            "cpu_count": psutil.cpu_count(),
            "total_memory_gb": psutil.virtual_memory().total / (1024**3)
        }
        
    def set_metrics_callback(self, callback: Callable[[Dict[str, Any]], None]):
        """메트릭스 콜백 설정"""
        self.metrics_callback = callback
        
    def start(self):
        """모니터링 시작"""
        if self.is_running:
            return
            
        self.is_running = True
        self.monitor_thread = threading.Thread(target=self._monitor_loop, daemon=True)
        self.monitor_thread.start()
        print(f"🔍 {self.name} 성능 모니터링 시작")
        
    def stop(self):
        """모니터링 중지"""
        self.is_running = False
        if self.monitor_thread:
            self.monitor_thread.join(timeout=5)
        print(f"🛑 {self.name} 성능 모니터링 중지")
        
    def _monitor_loop(self):
        """모니터링 루프"""
        while self.is_running:
            try:
                # 시스템 메트릭스 수집
                metrics = self._collect_system_metrics()
                
                # 콜백 호출
                if self.metrics_callback:
                    self.metrics_callback(metrics)
                    
                # 대기
                time.sleep(self.interval)
                
            except Exception as e:
                print(f"⚠️ 모니터링 오류: {e}")
                
    def _collect_system_metrics(self) -> Dict[str, Any]:
        """시스템 메트릭스 수집"""
        try:
            # CPU 사용량
            cpu_percent = psutil.cpu_percent(interval=None)
            
            # 메모리 사용량
            memory = psutil.virtual_memory()
            memory_percent = memory.percent
            memory_used_gb = memory.used / (1024**3)
            memory_total_gb = memory.total / (1024**3)
            
            # 디스크 사용량
            disk = psutil.disk_usage('/')
            disk_percent = disk.percent
            disk_used_gb = disk.used / (1024**3)
            disk_total_gb = disk.total / (1024**3)
            
            # 네트워크 통계
            net_io = psutil.net_io_counters()
            bytes_sent = net_io.bytes_sent
            bytes_recv = net_io.bytes_recv
            
            return {
                "timestamp": time.time(),
                "cpu_percent": cpu_percent,
                "memory_percent": memory_percent,
                "memory_used_gb": memory_used_gb,
                "memory_total_gb": memory_total_gb,
                "disk_percent": disk_percent,
                "disk_used_gb": disk_used_gb,
                "disk_total_gb": disk_total_gb,
                "bytes_sent": bytes_sent,
                "bytes_recv": bytes_recv,
                "system_info": self.system_info
            }
            
        except Exception as e:
            return {
                "timestamp": time.time(),
                "error": str(e),
                "system_info": self.system_info
            }

class DebugContextManager:
    """디버깅 컨텍스트 매니저"""
    
    def __init__(self, operation_name: str, debug_logger: DebugLogger):
        self.operation_name = operation_name
        self.debug_logger = debug_logger
        self.start_time = None
        self.start_memory = None
        
    def __enter__(self):
        """컨텍스트 시작"""
        self.start_time = time.time()
        
        # 메모리 사용량 측정
        try:
            process = psutil.Process()
            self.start_memory = process.memory_info().rss / 1024 / 1024  # MB
        except:
            self.start_memory = 0
            
        return self
        
    def __exit__(self, exc_type, exc_val, exc_tb):
        """컨텍스트 종료"""
        end_time = time.time()
        duration = end_time - self.start_time
        
        # 메모리 사용량 측정
        try:
            process = psutil.Process()
            end_memory = process.memory_info().rss / 1024 / 1024  # MB
            memory_usage = end_memory - self.start_memory
        except:
            memory_usage = 0
            
        # CPU 사용량 측정
        try:
            cpu_usage = psutil.cpu_percent(interval=0.1)
        except:
            cpu_usage = 0
            
        # 성공 여부 확인
        success = exc_type is None
        
        # 오류 메시지
        error_message = None
        if not success:
            error_message = f"{exc_type.__name__}: {exc_val}"
            
        # 로깅
        self.debug_logger.log_operation(
            operation=self.operation_name,
            duration=duration,
            success=success,
            memory_usage=memory_usage,
            cpu_usage=cpu_usage,
            error_message=error_message,
            details={
                "exception_type": str(exc_type) if exc_type else None,
                "exception_value": str(exc_val) if exc_val else None,
                "traceback": traceback.format_exc() if exc_tb else None
            }
        )
        
        # 예외가 발생한 경우 다시 발생시킴
        if not success:
            raise

class OCRDebugHelper:
    """OCR 디버깅 헬퍼"""
    
    def __init__(self, debug_logger: DebugLogger):
        self.debug_logger = debug_logger
        
    @asynccontextmanager
    async def debug_ocr_operation(self, operation_name: str):
        """OCR 작업 디버깅 컨텍스트"""
        start_time = time.time()
        
        try:
            yield
            success = True
            error_message = None
        except Exception as e:
            success = False
            error_message = str(e)
            raise
        finally:
            duration = time.time() - start_time
            
            self.debug_logger.log_operation(
                operation=operation_name,
                duration=duration,
                success=success,
                error_message=error_message,
                details={
                    "operation_type": "ocr",
                    "async": True
                }
            )
            
    def log_ocr_details(self, operation: str, details: Dict[str, Any]):
        """OCR 상세 정보 로깅"""
        self.debug_logger.logger.info(f"📊 {operation} 상세: {json.dumps(details, ensure_ascii=False, indent=2)}")
        
    def analyze_ocr_performance(self, ocr_results: List[Any]) -> Dict[str, Any]:
        """OCR 성능 분석"""
        if not ocr_results:
            return {"error": "OCR 결과 없음"}
            
        total_blocks = len(ocr_results)
        total_text_length = sum(len(str(getattr(result, 'text', ''))) for result in ocr_results)
        avg_confidence = sum(getattr(result, 'confidence', 0) for result in ocr_results) / total_blocks
        
        # 페이지별 분석
        pages = {}
        for result in ocr_results:
            page_num = getattr(result, 'page_number', 1)
            if page_num not in pages:
                pages[page_num] = {"count": 0, "confidence": 0}
            pages[page_num]["count"] += 1
            pages[page_num]["confidence"] += getattr(result, 'confidence', 0)
            
        # 페이지별 평균 신뢰도 계산
        for page_num in pages:
            pages[page_num]["confidence"] /= pages[page_num]["count"]
            
        return {
            "total_blocks": total_blocks,
            "total_text_length": total_text_length,
            "average_confidence": avg_confidence,
            "pages_analysis": pages,
            "blocks_per_page": {page: data["count"] for page, data in pages.items()}
        }

# 전역 디버깅 인스턴스
default_debug_logger = DebugLogger("default")
default_performance_monitor = PerformanceMonitor("system")
default_ocr_debug_helper = OCRDebugHelper(default_debug_logger)

# 데코레이터
def debug_operation(operation_name: str):
    """디버깅 데코레이터"""
    def decorator(func):
        def wrapper(*args, **kwargs):
            with DebugContextManager(operation_name, default_debug_logger):
                return func(*args, **kwargs)
        return wrapper
    return decorator

def async_debug_operation(operation_name: str):
    """비동기 디버깅 데코레이터"""
    def decorator(func):
        async def wrapper(*args, **kwargs):
            async with default_ocr_debug_helper.debug_ocr_operation(operation_name):
                return await func(*args, **kwargs)
        return wrapper
    return decorator

# 유틸리티 함수
def save_debug_report(metrics_summary: Dict[str, Any], filename: str = None):
    """디버그 보고서 저장"""
    if not filename:
        filename = f"debug_report_{datetime.now().strftime('%Y%m%d_%H%M%S')}.json"
        
    with open(filename, 'w', encoding='utf-8') as f:
        json.dump(metrics_summary, f, indent=2, ensure_ascii=False)
        
    print(f"📄 디버그 보고서 저장: {filename}")
    return filename

def start_system_monitoring(callback: Optional[Callable] = None):
    """시스템 모니터링 시작"""
    if callback:
        default_performance_monitor.set_metrics_callback(callback)
    default_performance_monitor.start()
    return default_performance_monitor

def stop_system_monitoring():
    """시스템 모니터링 중지"""
    default_performance_monitor.stop()