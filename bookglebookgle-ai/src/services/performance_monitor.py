"""
Performance Monitor for BGBG AI Server Chat History System
Monitors response times, memory usage, and system performance metrics
"""

import asyncio
import logging
import time
import psutil
from datetime import datetime, timedelta
from typing import Dict, List, Optional, Any, Callable
from dataclasses import dataclass, field
from enum import Enum
from collections import deque, defaultdict
import statistics

from src.config.settings import get_settings
from src.config.chat_config import get_performance_config

logger = logging.getLogger(__name__)


class MetricType(str, Enum):
    """Types of performance metrics"""
    RESPONSE_TIME = "response_time"
    MEMORY_USAGE = "memory_usage"
    CPU_USAGE = "cpu_usage"
    REDIS_OPERATIONS = "redis_ops"
    LLM_REQUESTS = "llm_requests"
    CACHE_PERFORMANCE = "cache_perf"
    ERROR_RATE = "error_rate"


class AlertLevel(str, Enum):
    """Alert severity levels"""
    INFO = "info"
    WARNING = "warning"
    CRITICAL = "critical"
    EMERGENCY = "emergency"


@dataclass
class PerformanceMetric:
    """Individual performance metric"""
    metric_type: MetricType
    value: float
    timestamp: datetime
    metadata: Dict[str, Any] = field(default_factory=dict)


@dataclass
class PerformanceAlert:
    """Performance alert"""
    alert_level: AlertLevel
    metric_type: MetricType
    message: str
    value: float
    threshold: float
    timestamp: datetime
    metadata: Dict[str, Any] = field(default_factory=dict)


@dataclass
class SystemStats:
    """System performance statistics"""
    cpu_percent: float
    memory_percent: float
    memory_used_mb: float
    memory_available_mb: float
    disk_usage_percent: float
    network_io_bytes: int
    process_count: int
    timestamp: datetime


class PerformanceMonitor:
    """
    Comprehensive performance monitoring system for chat history features
    """
    
    def __init__(self):
        """Initialize performance monitor"""
        self.settings = get_settings()
        self.performance_config = get_performance_config()
        
        # Metric storage (in-memory with size limits)
        self.max_metrics_per_type = 1000
        self.metrics: Dict[MetricType, deque] = {
            metric_type: deque(maxlen=self.max_metrics_per_type)
            for metric_type in MetricType
        }
        
        # Alert storage
        self.alerts: deque = deque(maxlen=500)
        
        # Performance thresholds
        self.thresholds = {
            MetricType.RESPONSE_TIME: {
                AlertLevel.WARNING: 2.0,    # 2 seconds
                AlertLevel.CRITICAL: 5.0,   # 5 seconds
                AlertLevel.EMERGENCY: 10.0  # 10 seconds
            },
            MetricType.MEMORY_USAGE: {
                AlertLevel.WARNING: 70.0,   # 70%
                AlertLevel.CRITICAL: 85.0,  # 85%
                AlertLevel.EMERGENCY: 95.0  # 95%
            },
            MetricType.CPU_USAGE: {
                AlertLevel.WARNING: 80.0,   # 80%
                AlertLevel.CRITICAL: 90.0,  # 90%
                AlertLevel.EMERGENCY: 95.0  # 95%
            },
            MetricType.ERROR_RATE: {
                AlertLevel.WARNING: 5.0,    # 5%
                AlertLevel.CRITICAL: 10.0,  # 10%
                AlertLevel.EMERGENCY: 20.0  # 20%
            }
        }
        
        # Monitoring tasks
        self._monitoring_task = None
        self._alert_task = None
        
        # Request tracking
        self._active_requests: Dict[str, float] = {}
        self._request_stats = {
            "total_requests": 0,
            "successful_requests": 0,
            "failed_requests": 0,
            "avg_response_time": 0.0
        }
        
        # System monitoring
        self._system_stats_history: deque = deque(maxlen=100)
        
        logger.info("PerformanceMonitor initialized")
    
    async def start(self) -> None:
        """Start performance monitoring tasks"""
        try:
            # Start system monitoring
            monitor_interval = self.performance_config.get('monitor_interval_seconds', 30)
            self._monitoring_task = asyncio.create_task(
                self._system_monitoring_loop(monitor_interval)
            )
            
            # Start alert processing
            alert_interval = self.performance_config.get('alert_check_interval_seconds', 60)
            self._alert_task = asyncio.create_task(
                self._alert_processing_loop(alert_interval)
            )
            
            logger.info("PerformanceMonitor started with monitoring tasks")
            
        except Exception as e:
            logger.error(f"Failed to start PerformanceMonitor: {e}")
            raise
    
    async def stop(self) -> None:
        """Stop performance monitoring tasks"""
        try:
            if self._monitoring_task:
                self._monitoring_task.cancel()
                try:
                    await self._monitoring_task
                except asyncio.CancelledError:
                    pass
            
            if self._alert_task:
                self._alert_task.cancel()
                try:
                    await self._alert_task
                except asyncio.CancelledError:
                    pass
            
            logger.info("PerformanceMonitor stopped")
            
        except Exception as e:
            logger.error(f"Error stopping PerformanceMonitor: {e}")
    
    # Metric recording methods
    
    def record_response_time(
        self, 
        operation: str, 
        response_time: float, 
        success: bool = True,
        metadata: Optional[Dict[str, Any]] = None
    ) -> None:
        """
        Record response time for an operation
        
        Args:
            operation: Operation name
            response_time: Response time in seconds
            success: Whether operation was successful
            metadata: Additional metadata
        """
        try:
            metric = PerformanceMetric(
                metric_type=MetricType.RESPONSE_TIME,
                value=response_time,
                timestamp=datetime.utcnow(),
                metadata={
                    "operation": operation,
                    "success": success,
                    **(metadata or {})
                }
            )
            
            self.metrics[MetricType.RESPONSE_TIME].append(metric)
            
            # Update request statistics
            self._request_stats["total_requests"] += 1
            if success:
                self._request_stats["successful_requests"] += 1
            else:
                self._request_stats["failed_requests"] += 1
            
            # Update average response time
            recent_times = [
                m.value for m in list(self.metrics[MetricType.RESPONSE_TIME])[-100:]
            ]
            self._request_stats["avg_response_time"] = statistics.mean(recent_times) if recent_times else 0.0
            
            # Check for alerts
            self._check_threshold_alert(MetricType.RESPONSE_TIME, response_time, metadata={"operation": operation})
            
        except Exception as e:
            logger.error(f"Failed to record response time: {e}")
    
    def record_memory_usage(self, memory_percent: float, metadata: Optional[Dict[str, Any]] = None) -> None:
        """
        Record memory usage metric
        
        Args:
            memory_percent: Memory usage percentage
            metadata: Additional metadata
        """
        try:
            metric = PerformanceMetric(
                metric_type=MetricType.MEMORY_USAGE,
                value=memory_percent,
                timestamp=datetime.utcnow(),
                metadata=metadata or {}
            )
            
            self.metrics[MetricType.MEMORY_USAGE].append(metric)
            self._check_threshold_alert(MetricType.MEMORY_USAGE, memory_percent, metadata)
            
        except Exception as e:
            logger.error(f"Failed to record memory usage: {e}")
    
    def record_redis_operation(
        self, 
        operation: str, 
        duration: float, 
        success: bool = True,
        metadata: Optional[Dict[str, Any]] = None
    ) -> None:
        """
        Record Redis operation performance
        
        Args:
            operation: Redis operation name
            duration: Operation duration in seconds
            success: Whether operation was successful
            metadata: Additional metadata
        """
        try:
            metric = PerformanceMetric(
                metric_type=MetricType.REDIS_OPERATIONS,
                value=duration,
                timestamp=datetime.utcnow(),
                metadata={
                    "operation": operation,
                    "success": success,
                    **(metadata or {})
                }
            )
            
            self.metrics[MetricType.REDIS_OPERATIONS].append(metric)
            
        except Exception as e:
            logger.error(f"Failed to record Redis operation: {e}")
    
    def record_llm_request(
        self, 
        provider: str, 
        duration: float, 
        tokens_used: int,
        success: bool = True,
        metadata: Optional[Dict[str, Any]] = None
    ) -> None:
        """
        Record LLM request performance
        
        Args:
            provider: LLM provider name
            duration: Request duration in seconds
            tokens_used: Number of tokens used
            success: Whether request was successful
            metadata: Additional metadata
        """
        try:
            metric = PerformanceMetric(
                metric_type=MetricType.LLM_REQUESTS,
                value=duration,
                timestamp=datetime.utcnow(),
                metadata={
                    "provider": provider,
                    "tokens_used": tokens_used,
                    "success": success,
                    **(metadata or {})
                }
            )
            
            self.metrics[MetricType.LLM_REQUESTS].append(metric)
            
        except Exception as e:
            logger.error(f"Failed to record LLM request: {e}")
    
    def record_cache_performance(
        self, 
        operation: str, 
        hit: bool, 
        duration: float,
        metadata: Optional[Dict[str, Any]] = None
    ) -> None:
        """
        Record cache performance metric
        
        Args:
            operation: Cache operation name
            hit: Whether it was a cache hit
            duration: Operation duration in seconds
            metadata: Additional metadata
        """
        try:
            metric = PerformanceMetric(
                metric_type=MetricType.CACHE_PERFORMANCE,
                value=duration,
                timestamp=datetime.utcnow(),
                metadata={
                    "operation": operation,
                    "cache_hit": hit,
                    **(metadata or {})
                }
            )
            
            self.metrics[MetricType.CACHE_PERFORMANCE].append(metric)
            
        except Exception as e:
            logger.error(f"Failed to record cache performance: {e}")
    
    # Context managers for automatic timing
    
    class TimedOperation:
        """Context manager for automatic operation timing"""
        
        def __init__(self, monitor: 'PerformanceMonitor', operation: str, metric_type: MetricType = MetricType.RESPONSE_TIME):
            self.monitor = monitor
            self.operation = operation
            self.metric_type = metric_type
            self.start_time = None
            self.success = True
            self.metadata = {}
        
        def __enter__(self):
            self.start_time = time.time()
            return self
        
        def __exit__(self, exc_type, exc_val, exc_tb):
            if self.start_time:
                duration = time.time() - self.start_time
                self.success = exc_type is None
                
                if self.metric_type == MetricType.RESPONSE_TIME:
                    self.monitor.record_response_time(
                        self.operation, duration, self.success, self.metadata
                    )
                elif self.metric_type == MetricType.REDIS_OPERATIONS:
                    self.monitor.record_redis_operation(
                        self.operation, duration, self.success, self.metadata
                    )
        
        def add_metadata(self, **kwargs):
            """Add metadata to the timed operation"""
            self.metadata.update(kwargs)
    
    def time_operation(self, operation: str, metric_type: MetricType = MetricType.RESPONSE_TIME) -> TimedOperation:
        """
        Create a timed operation context manager
        
        Args:
            operation: Operation name
            metric_type: Type of metric to record
            
        Returns:
            TimedOperation: Context manager for timing
        """
        return self.TimedOperation(self, operation, metric_type)
    
    # Statistics and reporting methods
    
    def get_performance_summary(self, time_window_minutes: int = 60) -> Dict[str, Any]:
        """
        Get performance summary for the specified time window
        
        Args:
            time_window_minutes: Time window in minutes
            
        Returns:
            Dict[str, Any]: Performance summary
        """
        try:
            cutoff_time = datetime.utcnow() - timedelta(minutes=time_window_minutes)
            summary = {}
            
            for metric_type in MetricType:
                metrics = [
                    m for m in self.metrics[metric_type]
                    if m.timestamp >= cutoff_time
                ]
                
                if metrics:
                    values = [m.value for m in metrics]
                    summary[metric_type.value] = {
                        "count": len(values),
                        "avg": statistics.mean(values),
                        "min": min(values),
                        "max": max(values),
                        "median": statistics.median(values),
                        "p95": self._percentile(values, 95),
                        "p99": self._percentile(values, 99)
                    }
                else:
                    summary[metric_type.value] = {
                        "count": 0,
                        "avg": 0.0,
                        "min": 0.0,
                        "max": 0.0,
                        "median": 0.0,
                        "p95": 0.0,
                        "p99": 0.0
                    }
            
            # Add request statistics
            summary["request_stats"] = self._request_stats.copy()
            
            # Add system stats
            if self._system_stats_history:
                latest_stats = self._system_stats_history[-1]
                summary["system_stats"] = {
                    "cpu_percent": latest_stats.cpu_percent,
                    "memory_percent": latest_stats.memory_percent,
                    "memory_used_mb": latest_stats.memory_used_mb,
                    "disk_usage_percent": latest_stats.disk_usage_percent,
                    "process_count": latest_stats.process_count
                }
            
            # Add recent alerts
            recent_alerts = [
                {
                    "level": alert.alert_level.value,
                    "metric": alert.metric_type.value,
                    "message": alert.message,
                    "timestamp": alert.timestamp.isoformat()
                }
                for alert in list(self.alerts)[-10:]  # Last 10 alerts
            ]
            summary["recent_alerts"] = recent_alerts
            
            return summary
            
        except Exception as e:
            logger.error(f"Failed to get performance summary: {e}")
            return {"error": str(e)}
    
    def get_slow_operations(self, threshold_seconds: float = 2.0, limit: int = 10) -> List[Dict[str, Any]]:
        """
        Get slowest operations above threshold
        
        Args:
            threshold_seconds: Minimum response time threshold
            limit: Maximum number of operations to return
            
        Returns:
            List[Dict[str, Any]]: Slow operations
        """
        try:
            slow_ops = []
            
            for metric in self.metrics[MetricType.RESPONSE_TIME]:
                if metric.value >= threshold_seconds:
                    slow_ops.append({
                        "operation": metric.metadata.get("operation", "unknown"),
                        "response_time": metric.value,
                        "timestamp": metric.timestamp.isoformat(),
                        "success": metric.metadata.get("success", True),
                        "metadata": metric.metadata
                    })
            
            # Sort by response time (slowest first)
            slow_ops.sort(key=lambda x: x["response_time"], reverse=True)
            
            return slow_ops[:limit]
            
        except Exception as e:
            logger.error(f"Failed to get slow operations: {e}")
            return []
    
    def get_error_rate(self, time_window_minutes: int = 60) -> float:
        """
        Calculate error rate for the specified time window
        
        Args:
            time_window_minutes: Time window in minutes
            
        Returns:
            float: Error rate as percentage
        """
        try:
            cutoff_time = datetime.utcnow() - timedelta(minutes=time_window_minutes)
            
            total_requests = 0
            failed_requests = 0
            
            for metric in self.metrics[MetricType.RESPONSE_TIME]:
                if metric.timestamp >= cutoff_time:
                    total_requests += 1
                    if not metric.metadata.get("success", True):
                        failed_requests += 1
            
            if total_requests == 0:
                return 0.0
            
            error_rate = (failed_requests / total_requests) * 100
            
            # Record error rate metric
            self.record_metric(MetricType.ERROR_RATE, error_rate)
            
            return error_rate
            
        except Exception as e:
            logger.error(f"Failed to calculate error rate: {e}")
            return 0.0
    
    def record_metric(self, metric_type: MetricType, value: float, metadata: Optional[Dict[str, Any]] = None) -> None:
        """
        Record a generic metric
        
        Args:
            metric_type: Type of metric
            value: Metric value
            metadata: Additional metadata
        """
        try:
            metric = PerformanceMetric(
                metric_type=metric_type,
                value=value,
                timestamp=datetime.utcnow(),
                metadata=metadata or {}
            )
            
            self.metrics[metric_type].append(metric)
            self._check_threshold_alert(metric_type, value, metadata)
            
        except Exception as e:
            logger.error(f"Failed to record metric: {e}")
    
    # Private methods
    
    async def _system_monitoring_loop(self, interval_seconds: int) -> None:
        """System monitoring background task"""
        while True:
            try:
                await asyncio.sleep(interval_seconds)
                
                # Collect system statistics
                cpu_percent = psutil.cpu_percent(interval=1)
                memory = psutil.virtual_memory()
                disk = psutil.disk_usage('/')
                network = psutil.net_io_counters()
                
                stats = SystemStats(
                    cpu_percent=cpu_percent,
                    memory_percent=memory.percent,
                    memory_used_mb=memory.used / (1024 * 1024),
                    memory_available_mb=memory.available / (1024 * 1024),
                    disk_usage_percent=disk.percent,
                    network_io_bytes=network.bytes_sent + network.bytes_recv,
                    process_count=len(psutil.pids()),
                    timestamp=datetime.utcnow()
                )
                
                self._system_stats_history.append(stats)
                
                # Record metrics
                self.record_metric(MetricType.CPU_USAGE, cpu_percent)
                self.record_metric(MetricType.MEMORY_USAGE, memory.percent)
                
            except asyncio.CancelledError:
                logger.info("System monitoring task cancelled")
                break
            except Exception as e:
                logger.error(f"Error in system monitoring loop: {e}")
    
    async def _alert_processing_loop(self, interval_seconds: int) -> None:
        """Alert processing background task"""
        while True:
            try:
                await asyncio.sleep(interval_seconds)
                
                # Calculate and check error rate
                error_rate = self.get_error_rate(time_window_minutes=10)
                
                # Process any pending alerts
                await self._process_alerts()
                
            except asyncio.CancelledError:
                logger.info("Alert processing task cancelled")
                break
            except Exception as e:
                logger.error(f"Error in alert processing loop: {e}")
    
    def _check_threshold_alert(
        self, 
        metric_type: MetricType, 
        value: float, 
        metadata: Optional[Dict[str, Any]] = None
    ) -> None:
        """Check if metric value exceeds thresholds and create alerts"""
        try:
            if metric_type not in self.thresholds:
                return
            
            thresholds = self.thresholds[metric_type]
            
            # Determine alert level
            alert_level = None
            threshold_value = None
            
            if value >= thresholds.get(AlertLevel.EMERGENCY, float('inf')):
                alert_level = AlertLevel.EMERGENCY
                threshold_value = thresholds[AlertLevel.EMERGENCY]
            elif value >= thresholds.get(AlertLevel.CRITICAL, float('inf')):
                alert_level = AlertLevel.CRITICAL
                threshold_value = thresholds[AlertLevel.CRITICAL]
            elif value >= thresholds.get(AlertLevel.WARNING, float('inf')):
                alert_level = AlertLevel.WARNING
                threshold_value = thresholds[AlertLevel.WARNING]
            
            if alert_level:
                # Create alert
                alert = PerformanceAlert(
                    alert_level=alert_level,
                    metric_type=metric_type,
                    message=f"{metric_type.value} exceeded {alert_level.value} threshold: {value:.2f} > {threshold_value:.2f}",
                    value=value,
                    threshold=threshold_value,
                    timestamp=datetime.utcnow(),
                    metadata=metadata or {}
                )
                
                self.alerts.append(alert)
                
                # Log alert
                log_level = logging.WARNING if alert_level == AlertLevel.WARNING else logging.ERROR
                logger.log(log_level, f"🚨 Performance Alert: {alert.message}")
        
        except Exception as e:
            logger.error(f"Failed to check threshold alert: {e}")
    
    async def _process_alerts(self) -> None:
        """Process and potentially act on alerts"""
        try:
            # Count recent alerts by level
            recent_time = datetime.utcnow() - timedelta(minutes=5)
            recent_alerts = [alert for alert in self.alerts if alert.timestamp >= recent_time]
            
            alert_counts = defaultdict(int)
            for alert in recent_alerts:
                alert_counts[alert.alert_level] += 1
            
            # Log alert summary if there are many alerts
            if alert_counts[AlertLevel.CRITICAL] >= 3:
                logger.error(f"🚨 Multiple critical alerts in last 5 minutes: {dict(alert_counts)}")
            elif alert_counts[AlertLevel.WARNING] >= 5:
                logger.warning(f"⚠️ Multiple warning alerts in last 5 minutes: {dict(alert_counts)}")
        
        except Exception as e:
            logger.error(f"Failed to process alerts: {e}")
    
    def _percentile(self, values: List[float], percentile: int) -> float:
        """Calculate percentile of values"""
        if not values:
            return 0.0
        
        sorted_values = sorted(values)
        index = (percentile / 100) * (len(sorted_values) - 1)
        
        if index.is_integer():
            return sorted_values[int(index)]
        else:
            lower = sorted_values[int(index)]
            upper = sorted_values[int(index) + 1]
            return lower + (upper - lower) * (index - int(index))
    
    async def health_check(self) -> Dict[str, Any]:
        """
        Perform health check on performance monitoring system
        
        Returns:
            Dict[str, Any]: Health check results
        """
        try:
            # Check if monitoring tasks are running
            monitoring_running = self._monitoring_task and not self._monitoring_task.done()
            alert_running = self._alert_task and not self._alert_task.done()
            
            # Get recent performance summary
            summary = self.get_performance_summary(time_window_minutes=10)
            
            # Count recent critical alerts
            recent_time = datetime.utcnow() - timedelta(minutes=10)
            critical_alerts = len([
                alert for alert in self.alerts 
                if alert.timestamp >= recent_time and alert.alert_level == AlertLevel.CRITICAL
            ])
            
            return {
                "monitoring_task_running": monitoring_running,
                "alert_task_running": alert_running,
                "total_metrics_collected": sum(len(metrics) for metrics in self.metrics.values()),
                "recent_critical_alerts": critical_alerts,
                "avg_response_time": self._request_stats["avg_response_time"],
                "total_requests": self._request_stats["total_requests"],
                "error_rate": self.get_error_rate(time_window_minutes=10),
                "system_stats": summary.get("system_stats", {})
            }
            
        except Exception as e:
            logger.error(f"Performance monitor health check failed: {e}")
            return {"error": str(e)}