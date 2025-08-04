"""
Admin Tools for BGBG AI Server Chat History System
Provides administrative functions for monitoring, management, and maintenance
"""

import asyncio
import logging
from datetime import datetime, timedelta
from typing import Dict, List, Optional, Any, Tuple
from dataclasses import dataclass, asdict
from enum import Enum
import json

from src.config.chat_config import get_chat_config, ChatHistoryConfig
from src.services.redis_memory_manager import RedisMemoryManager
from src.services.redis_cache_manager import RedisCacheManager
from src.services.performance_monitor import PerformanceMonitor
from src.services.privacy_manager import PrivacyManager
from src.services.encryption_manager import EncryptionManager

logger = logging.getLogger(__name__)


class AdminAction(str, Enum):
    """Types of administrative actions"""
    VIEW_STATS = "view_stats"
    CLEANUP_SESSIONS = "cleanup_sessions"
    FORCE_CLEANUP = "force_cleanup"
    ROTATE_KEYS = "rotate_keys"
    EXPORT_DATA = "export_data"
    DELETE_DATA = "delete_data"
    UPDATE_CONFIG = "update_config"
    HEALTH_CHECK = "health_check"
    PERFORMANCE_REPORT = "performance_report"


class AdminLevel(str, Enum):
    """Administrative access levels"""
    READ_ONLY = "read_only"
    OPERATOR = "operator"
    ADMIN = "admin"
    SUPER_ADMIN = "super_admin"


@dataclass
class AdminUser:
    """Administrative user information"""
    user_id: str
    username: str
    admin_level: AdminLevel
    permissions: List[AdminAction]
    created_at: datetime
    last_login: Optional[datetime] = None


@dataclass
class AdminActionLog:
    """Log entry for administrative actions"""
    action_id: str
    admin_user: str
    action: AdminAction
    target: Optional[str]
    parameters: Dict[str, Any]
    result: Dict[str, Any]
    timestamp: datetime
    success: bool
    error_message: Optional[str] = None


class ChatHistoryAdminTools:
    """
    Administrative tools for chat history system management
    """
    
    def __init__(self):
        """Initialize admin tools"""
        self.config = get_chat_config()
        
        # Initialize service managers
        self.memory_manager: Optional[RedisMemoryManager] = None
        self.cache_manager: Optional[RedisCacheManager] = None
        self.performance_monitor: Optional[PerformanceMonitor] = None
        self.privacy_manager: Optional[PrivacyManager] = None
        self.encryption_manager: Optional[EncryptionManager] = None
        
        # Admin users and permissions
        self.admin_users: Dict[str, AdminUser] = {}
        self.action_log: List[AdminActionLog] = []
        
        # Action permissions mapping
        self.action_permissions = {
            AdminLevel.READ_ONLY: [
                AdminAction.VIEW_STATS,
                AdminAction.HEALTH_CHECK,
                AdminAction.PERFORMANCE_REPORT
            ],
            AdminLevel.OPERATOR: [
                AdminAction.VIEW_STATS,
                AdminAction.HEALTH_CHECK,
                AdminAction.PERFORMANCE_REPORT,
                AdminAction.CLEANUP_SESSIONS
            ],
            AdminLevel.ADMIN: [
                AdminAction.VIEW_STATS,
                AdminAction.HEALTH_CHECK,
                AdminAction.PERFORMANCE_REPORT,
                AdminAction.CLEANUP_SESSIONS,
                AdminAction.FORCE_CLEANUP,
                AdminAction.EXPORT_DATA,
                AdminAction.UPDATE_CONFIG
            ],
            AdminLevel.SUPER_ADMIN: list(AdminAction)  # All actions
        }
        
        logger.info("ChatHistoryAdminTools initialized")
    
    async def initialize_services(self) -> None:
        """Initialize service managers"""
        try:
            self.memory_manager = RedisMemoryManager()
            await self.memory_manager.start()
            
            self.cache_manager = RedisCacheManager()
            await self.cache_manager.start()
            
            self.performance_monitor = PerformanceMonitor()
            await self.performance_monitor.start()
            
            self.privacy_manager = PrivacyManager()
            self.encryption_manager = EncryptionManager()
            
            logger.info("✅ Admin tools services initialized")
            
        except Exception as e:
            logger.error(f"Failed to initialize admin services: {e}")
            raise
    
    async def shutdown_services(self) -> None:
        """Shutdown service managers"""
        try:
            if self.memory_manager:
                await self.memory_manager.stop()
            
            if self.cache_manager:
                await self.cache_manager.stop()
            
            if self.performance_monitor:
                await self.performance_monitor.stop()
            
            if self.encryption_manager:
                await self.encryption_manager.stop()
            
            logger.info("Admin tools services shut down")
            
        except Exception as e:
            logger.error(f"Error shutting down admin services: {e}")
    
    # User management
    
    def add_admin_user(
        self, 
        user_id: str, 
        username: str, 
        admin_level: AdminLevel
    ) -> bool:
        """
        Add administrative user
        
        Args:
            user_id: User identifier
            username: Username
            admin_level: Administrative level
            
        Returns:
            bool: True if added successfully
        """
        try:
            permissions = self.action_permissions.get(admin_level, [])
            
            admin_user = AdminUser(
                user_id=user_id,
                username=username,
                admin_level=admin_level,
                permissions=permissions,
                created_at=datetime.utcnow()
            )
            
            self.admin_users[user_id] = admin_user
            
            logger.info(f"Added admin user: {username} ({admin_level.value})")
            return True
            
        except Exception as e:
            logger.error(f"Failed to add admin user: {e}")
            return False
    
    def check_permission(self, user_id: str, action: AdminAction) -> bool:
        """
        Check if user has permission for action
        
        Args:
            user_id: User identifier
            action: Administrative action
            
        Returns:
            bool: True if user has permission
        """
        if user_id not in self.admin_users:
            return False
        
        admin_user = self.admin_users[user_id]
        return action in admin_user.permissions
    
    # Dashboard and statistics
    
    async def get_system_dashboard(self, admin_user_id: str) -> Dict[str, Any]:
        """
        Get comprehensive system dashboard
        
        Args:
            admin_user_id: Admin user identifier
            
        Returns:
            Dict[str, Any]: System dashboard data
        """
        try:
            if not self.check_permission(admin_user_id, AdminAction.VIEW_STATS):
                return {"error": "Insufficient permissions"}
            
            dashboard = {
                "timestamp": datetime.utcnow().isoformat(),
                "system_status": "operational",
                "configuration": self.config.get_runtime_info(),
                "services": {},
                "statistics": {},
                "alerts": []
            }
            
            # Memory statistics
            if self.memory_manager:
                memory_stats = await self.memory_manager.get_memory_stats()
                dashboard["services"]["memory"] = {
                    "status": memory_stats.memory_status.value,
                    "usage_percentage": memory_stats.used_memory_percentage,
                    "active_sessions": memory_stats.active_sessions,
                    "total_keys": memory_stats.total_keys
                }
            
            # Cache statistics
            if self.cache_manager:
                cache_stats = await self.cache_manager.get_cache_stats()
                dashboard["services"]["cache"] = {
                    "hit_rate": cache_stats.hit_rate,
                    "total_keys": cache_stats.total_keys,
                    "memory_usage_bytes": cache_stats.memory_usage_bytes,
                    "total_requests": cache_stats.total_requests
                }
            
            # Performance statistics
            if self.performance_monitor:
                perf_summary = self.performance_monitor.get_performance_summary(60)
                dashboard["services"]["performance"] = {
                    "avg_response_time": perf_summary.get("request_stats", {}).get("avg_response_time", 0),
                    "total_requests": perf_summary.get("request_stats", {}).get("total_requests", 0),
                    "error_rate": self.performance_monitor.get_error_rate(60),
                    "recent_alerts": perf_summary.get("recent_alerts", [])
                }
            
            # System health checks
            health_checks = await self._perform_health_checks()
            dashboard["services"]["health"] = health_checks
            
            # Recent admin actions
            dashboard["recent_actions"] = [
                {
                    "action": log.action.value,
                    "admin_user": log.admin_user,
                    "timestamp": log.timestamp.isoformat(),
                    "success": log.success
                }
                for log in self.action_log[-10:]  # Last 10 actions
            ]
            
            return dashboard
            
        except Exception as e:
            logger.error(f"Failed to get system dashboard: {e}")
            return {"error": str(e)}
    
    async def get_session_statistics(self, admin_user_id: str) -> Dict[str, Any]:
        """
        Get detailed session statistics
        
        Args:
            admin_user_id: Admin user identifier
            
        Returns:
            Dict[str, Any]: Session statistics
        """
        try:
            if not self.check_permission(admin_user_id, AdminAction.VIEW_STATS):
                return {"error": "Insufficient permissions"}
            
            # This would typically query Redis for session data
            # For now, return placeholder statistics
            
            stats = {
                "total_sessions": 0,
                "active_sessions": 0,
                "inactive_sessions": 0,
                "sessions_by_age": {
                    "0-1h": 0,
                    "1-6h": 0,
                    "6-24h": 0,
                    "1d+": 0
                },
                "message_statistics": {
                    "total_messages": 0,
                    "messages_per_session_avg": 0,
                    "messages_per_hour": 0
                },
                "participant_statistics": {
                    "total_participants": 0,
                    "avg_participants_per_session": 0,
                    "most_active_participants": []
                }
            }
            
            # Log action
            await self._log_admin_action(
                admin_user_id, AdminAction.VIEW_STATS, "session_statistics", {}, stats, True
            )
            
            return stats
            
        except Exception as e:
            logger.error(f"Failed to get session statistics: {e}")
            return {"error": str(e)}
    
    # Maintenance operations
    
    async def cleanup_inactive_sessions(
        self, 
        admin_user_id: str, 
        max_age_hours: Optional[int] = None,
        force: bool = False
    ) -> Dict[str, Any]:
        """
        Clean up inactive sessions
        
        Args:
            admin_user_id: Admin user identifier
            max_age_hours: Maximum age in hours (optional)
            force: Whether to force cleanup
            
        Returns:
            Dict[str, Any]: Cleanup results
        """
        try:
            action = AdminAction.FORCE_CLEANUP if force else AdminAction.CLEANUP_SESSIONS
            
            if not self.check_permission(admin_user_id, action):
                return {"error": "Insufficient permissions"}
            
            if not self.memory_manager:
                return {"error": "Memory manager not available"}
            
            # Perform cleanup
            cleaned_sessions = await self.memory_manager.cleanup_inactive_sessions(force)
            
            result = {
                "cleaned_sessions": cleaned_sessions,
                "force_cleanup": force,
                "max_age_hours": max_age_hours,
                "timestamp": datetime.utcnow().isoformat()
            }
            
            # Log action
            await self._log_admin_action(
                admin_user_id, action, "session_cleanup", 
                {"max_age_hours": max_age_hours, "force": force}, 
                result, True
            )
            
            logger.info(f"Admin cleanup completed by {admin_user_id}: {cleaned_sessions} sessions")
            return result
            
        except Exception as e:
            logger.error(f"Admin cleanup failed: {e}")
            await self._log_admin_action(
                admin_user_id, action, "session_cleanup", 
                {"max_age_hours": max_age_hours, "force": force}, 
                {}, False, str(e)
            )
            return {"error": str(e)}
    
    async def rotate_encryption_keys(self, admin_user_id: str) -> Dict[str, Any]:
        """
        Rotate encryption keys
        
        Args:
            admin_user_id: Admin user identifier
            
        Returns:
            Dict[str, Any]: Key rotation results
        """
        try:
            if not self.check_permission(admin_user_id, AdminAction.ROTATE_KEYS):
                return {"error": "Insufficient permissions"}
            
            if not self.encryption_manager:
                return {"error": "Encryption manager not available"}
            
            # Perform key rotation
            result = await self.encryption_manager.rotate_keys()
            
            # Log action
            await self._log_admin_action(
                admin_user_id, AdminAction.ROTATE_KEYS, "encryption_keys", {}, result, True
            )
            
            logger.info(f"Key rotation completed by {admin_user_id}")
            return result
            
        except Exception as e:
            logger.error(f"Key rotation failed: {e}")
            await self._log_admin_action(
                admin_user_id, AdminAction.ROTATE_KEYS, "encryption_keys", {}, {}, False, str(e)
            )
            return {"error": str(e)}
    
    async def optimize_cache(self, admin_user_id: str) -> Dict[str, Any]:
        """
        Optimize cache performance
        
        Args:
            admin_user_id: Admin user identifier
            
        Returns:
            Dict[str, Any]: Optimization results
        """
        try:
            if not self.check_permission(admin_user_id, AdminAction.CLEANUP_SESSIONS):
                return {"error": "Insufficient permissions"}
            
            if not self.cache_manager:
                return {"error": "Cache manager not available"}
            
            # Perform cache optimization
            result = await self.cache_manager.optimize_cache()
            
            # Log action
            await self._log_admin_action(
                admin_user_id, AdminAction.CLEANUP_SESSIONS, "cache_optimization", {}, result, True
            )
            
            logger.info(f"Cache optimization completed by {admin_user_id}")
            return result
            
        except Exception as e:
            logger.error(f"Cache optimization failed: {e}")
            return {"error": str(e)}
    
    # Configuration management
    
    async def update_configuration(
        self, 
        admin_user_id: str, 
        config_updates: Dict[str, Any]
    ) -> Dict[str, Any]:
        """
        Update system configuration
        
        Args:
            admin_user_id: Admin user identifier
            config_updates: Configuration updates to apply
            
        Returns:
            Dict[str, Any]: Update results
        """
        try:
            if not self.check_permission(admin_user_id, AdminAction.UPDATE_CONFIG):
                return {"error": "Insufficient permissions"}
            
            # Validate and apply updates
            validation_issues = self.config.update_from_dict(config_updates)
            
            result = {
                "updates_applied": config_updates,
                "validation_issues": validation_issues,
                "success": len(validation_issues) == 0,
                "timestamp": datetime.utcnow().isoformat()
            }
            
            # Log action
            await self._log_admin_action(
                admin_user_id, AdminAction.UPDATE_CONFIG, "system_config", 
                config_updates, result, result["success"]
            )
            
            if result["success"]:
                logger.info(f"Configuration updated by {admin_user_id}")
            else:
                logger.warning(f"Configuration update by {admin_user_id} had issues: {validation_issues}")
            
            return result
            
        except Exception as e:
            logger.error(f"Configuration update failed: {e}")
            await self._log_admin_action(
                admin_user_id, AdminAction.UPDATE_CONFIG, "system_config", 
                config_updates, {}, False, str(e)
            )
            return {"error": str(e)}
    
    async def get_configuration(self, admin_user_id: str) -> Dict[str, Any]:
        """
        Get current system configuration
        
        Args:
            admin_user_id: Admin user identifier
            
        Returns:
            Dict[str, Any]: Current configuration
        """
        try:
            if not self.check_permission(admin_user_id, AdminAction.VIEW_STATS):
                return {"error": "Insufficient permissions"}
            
            config_dict = self.config.to_dict()
            
            # Log action
            await self._log_admin_action(
                admin_user_id, AdminAction.VIEW_STATS, "system_config", {}, 
                {"config_retrieved": True}, True
            )
            
            return config_dict
            
        except Exception as e:
            logger.error(f"Failed to get configuration: {e}")
            return {"error": str(e)}
    
    # Data management
    
    async def export_user_data(
        self, 
        admin_user_id: str, 
        target_user_id: str,
        session_ids: Optional[List[str]] = None
    ) -> Dict[str, Any]:
        """
        Export user data for GDPR compliance
        
        Args:
            admin_user_id: Admin user identifier
            target_user_id: User whose data to export
            session_ids: Optional specific sessions to export
            
        Returns:
            Dict[str, Any]: Export results
        """
        try:
            if not self.check_permission(admin_user_id, AdminAction.EXPORT_DATA):
                return {"error": "Insufficient permissions"}
            
            if not self.privacy_manager:
                return {"error": "Privacy manager not available"}
            
            # Perform data export
            export_result = self.privacy_manager.export_user_data(target_user_id, session_ids)
            
            # Log action
            await self._log_admin_action(
                admin_user_id, AdminAction.EXPORT_DATA, target_user_id, 
                {"session_ids": session_ids}, export_result, True
            )
            
            logger.info(f"User data exported by {admin_user_id} for user {target_user_id}")
            return export_result
            
        except Exception as e:
            logger.error(f"Data export failed: {e}")
            await self._log_admin_action(
                admin_user_id, AdminAction.EXPORT_DATA, target_user_id, 
                {"session_ids": session_ids}, {}, False, str(e)
            )
            return {"error": str(e)}
    
    async def delete_user_data(
        self, 
        admin_user_id: str, 
        target_user_id: str,
        session_ids: Optional[List[str]] = None
    ) -> Dict[str, Any]:
        """
        Delete user data for GDPR compliance
        
        Args:
            admin_user_id: Admin user identifier
            target_user_id: User whose data to delete
            session_ids: Optional specific sessions to delete
            
        Returns:
            Dict[str, Any]: Deletion results
        """
        try:
            if not self.check_permission(admin_user_id, AdminAction.DELETE_DATA):
                return {"error": "Insufficient permissions"}
            
            if not self.privacy_manager:
                return {"error": "Privacy manager not available"}
            
            # Perform data deletion
            deletion_result = self.privacy_manager.delete_user_data(target_user_id, session_ids)
            
            # Log action
            await self._log_admin_action(
                admin_user_id, AdminAction.DELETE_DATA, target_user_id, 
                {"session_ids": session_ids}, deletion_result, True
            )
            
            logger.info(f"User data deleted by {admin_user_id} for user {target_user_id}")
            return deletion_result
            
        except Exception as e:
            logger.error(f"Data deletion failed: {e}")
            await self._log_admin_action(
                admin_user_id, AdminAction.DELETE_DATA, target_user_id, 
                {"session_ids": session_ids}, {}, False, str(e)
            )
            return {"error": str(e)}
    
    # Health and monitoring
    
    async def comprehensive_health_check(self, admin_user_id: str) -> Dict[str, Any]:
        """
        Perform comprehensive system health check
        
        Args:
            admin_user_id: Admin user identifier
            
        Returns:
            Dict[str, Any]: Health check results
        """
        try:
            if not self.check_permission(admin_user_id, AdminAction.HEALTH_CHECK):
                return {"error": "Insufficient permissions"}
            
            health_results = await self._perform_health_checks()
            
            # Log action
            await self._log_admin_action(
                admin_user_id, AdminAction.HEALTH_CHECK, "system", {}, health_results, True
            )
            
            return health_results
            
        except Exception as e:
            logger.error(f"Health check failed: {e}")
            return {"error": str(e)}
    
    async def get_performance_report(
        self, 
        admin_user_id: str, 
        time_window_minutes: int = 60
    ) -> Dict[str, Any]:
        """
        Get detailed performance report
        
        Args:
            admin_user_id: Admin user identifier
            time_window_minutes: Time window for report
            
        Returns:
            Dict[str, Any]: Performance report
        """
        try:
            if not self.check_permission(admin_user_id, AdminAction.PERFORMANCE_REPORT):
                return {"error": "Insufficient permissions"}
            
            if not self.performance_monitor:
                return {"error": "Performance monitor not available"}
            
            # Get performance summary
            performance_summary = self.performance_monitor.get_performance_summary(time_window_minutes)
            
            # Get slow operations
            slow_operations = self.performance_monitor.get_slow_operations(threshold_seconds=2.0)
            
            report = {
                "time_window_minutes": time_window_minutes,
                "generated_at": datetime.utcnow().isoformat(),
                "performance_summary": performance_summary,
                "slow_operations": slow_operations,
                "recommendations": self._generate_performance_recommendations(performance_summary)
            }
            
            # Log action
            await self._log_admin_action(
                admin_user_id, AdminAction.PERFORMANCE_REPORT, "system", 
                {"time_window_minutes": time_window_minutes}, 
                {"report_generated": True}, True
            )
            
            return report
            
        except Exception as e:
            logger.error(f"Performance report failed: {e}")
            return {"error": str(e)}
    
    # Private methods
    
    async def _perform_health_checks(self) -> Dict[str, Any]:
        """Perform health checks on all services"""
        health_results = {
            "overall_status": "healthy",
            "timestamp": datetime.utcnow().isoformat(),
            "services": {}
        }
        
        issues = []
        
        # Memory manager health
        if self.memory_manager:
            try:
                memory_health = await self.memory_manager.health_check()
                health_results["services"]["memory"] = memory_health
                if not memory_health.get("redis_connection_healthy", False):
                    issues.append("Redis connection unhealthy")
            except Exception as e:
                health_results["services"]["memory"] = {"error": str(e)}
                issues.append(f"Memory manager health check failed: {e}")
        
        # Cache manager health
        if self.cache_manager:
            try:
                cache_health = await self.cache_manager.health_check()
                health_results["services"]["cache"] = cache_health
                if not cache_health.get("redis_connection_healthy", False):
                    issues.append("Cache Redis connection unhealthy")
            except Exception as e:
                health_results["services"]["cache"] = {"error": str(e)}
                issues.append(f"Cache manager health check failed: {e}")
        
        # Performance monitor health
        if self.performance_monitor:
            try:
                perf_health = await self.performance_monitor.health_check()
                health_results["services"]["performance"] = perf_health
                if perf_health.get("recent_critical_alerts", 0) > 0:
                    issues.append("Recent critical performance alerts")
            except Exception as e:
                health_results["services"]["performance"] = {"error": str(e)}
                issues.append(f"Performance monitor health check failed: {e}")
        
        # Privacy manager health
        if self.privacy_manager:
            try:
                privacy_health = self.privacy_manager.health_check()
                health_results["services"]["privacy"] = privacy_health
            except Exception as e:
                health_results["services"]["privacy"] = {"error": str(e)}
                issues.append(f"Privacy manager health check failed: {e}")
        
        # Encryption manager health
        if self.encryption_manager:
            try:
                encryption_health = self.encryption_manager.health_check()
                health_results["services"]["encryption"] = encryption_health
                if not encryption_health.get("master_key_initialized", False):
                    issues.append("Master encryption key not initialized")
            except Exception as e:
                health_results["services"]["encryption"] = {"error": str(e)}
                issues.append(f"Encryption manager health check failed: {e}")
        
        # Determine overall status
        if issues:
            health_results["overall_status"] = "degraded" if len(issues) < 3 else "unhealthy"
            health_results["issues"] = issues
        
        return health_results
    
    def _generate_performance_recommendations(self, performance_summary: Dict[str, Any]) -> List[str]:
        """Generate performance recommendations based on metrics"""
        recommendations = []
        
        try:
            # Check response time
            response_time_stats = performance_summary.get("response_time", {})
            avg_response_time = response_time_stats.get("avg", 0)
            
            if avg_response_time > 2.0:
                recommendations.append("Average response time is high - consider optimizing slow operations")
            
            # Check error rate
            error_rate = performance_summary.get("request_stats", {}).get("error_rate", 0)
            if error_rate > 5.0:
                recommendations.append("Error rate is elevated - investigate failing operations")
            
            # Check memory usage
            memory_stats = performance_summary.get("system_stats", {})
            memory_percent = memory_stats.get("memory_percent", 0)
            
            if memory_percent > 80:
                recommendations.append("Memory usage is high - consider cleanup or scaling")
            
            # Check cache performance
            cache_stats = performance_summary.get("cache_performance", {})
            if cache_stats.get("count", 0) > 0:
                cache_avg = cache_stats.get("avg", 0)
                if cache_avg > 0.1:
                    recommendations.append("Cache operations are slow - check Redis performance")
            
            if not recommendations:
                recommendations.append("System performance is within normal parameters")
        
        except Exception as e:
            logger.error(f"Failed to generate recommendations: {e}")
            recommendations.append("Unable to generate recommendations due to analysis error")
        
        return recommendations
    
    async def _log_admin_action(
        self,
        admin_user_id: str,
        action: AdminAction,
        target: Optional[str],
        parameters: Dict[str, Any],
        result: Dict[str, Any],
        success: bool,
        error_message: Optional[str] = None
    ) -> None:
        """Log administrative action"""
        try:
            action_log = AdminActionLog(
                action_id=f"{action.value}_{datetime.utcnow().strftime('%Y%m%d_%H%M%S')}",
                admin_user=admin_user_id,
                action=action,
                target=target,
                parameters=parameters,
                result=result,
                timestamp=datetime.utcnow(),
                success=success,
                error_message=error_message
            )
            
            self.action_log.append(action_log)
            
            # Keep only last 1000 actions
            if len(self.action_log) > 1000:
                self.action_log = self.action_log[-1000:]
        
        except Exception as e:
            logger.error(f"Failed to log admin action: {e}")
    
    def get_admin_action_log(
        self, 
        admin_user_id: str, 
        limit: int = 50
    ) -> List[Dict[str, Any]]:
        """
        Get administrative action log
        
        Args:
            admin_user_id: Admin user identifier
            limit: Maximum number of entries to return
            
        Returns:
            List[Dict[str, Any]]: Action log entries
        """
        try:
            if not self.check_permission(admin_user_id, AdminAction.VIEW_STATS):
                return []
            
            # Convert to dictionaries for JSON serialization
            log_entries = []
            for log_entry in self.action_log[-limit:]:
                log_dict = asdict(log_entry)
                log_dict["timestamp"] = log_entry.timestamp.isoformat()
                log_dict["action"] = log_entry.action.value
                log_entries.append(log_dict)
            
            return log_entries
            
        except Exception as e:
            logger.error(f"Failed to get action log: {e}")
            return []