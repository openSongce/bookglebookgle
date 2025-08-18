"""
Simple Monitoring Dashboard Routes for BGBG AI Server
Provides basic monitoring endpoints for chat history system
"""

import asyncio
import logging
from datetime import datetime, timedelta
from typing import Dict, List, Optional, Any

from fastapi import APIRouter, HTTPException, Depends
from fastapi.responses import HTMLResponse
from pydantic import BaseModel

from src.config.chat_config import get_chat_config
from src.services.redis_connection_manager import get_redis_connection_manager
from src.services.admin_tools import ChatHistoryAdminTools

logger = logging.getLogger(__name__)

router = APIRouter(prefix="/monitoring", tags=["monitoring"])

# Global admin tools instance
admin_tools: Optional[ChatHistoryAdminTools] = None


async def get_admin_tools() -> ChatHistoryAdminTools:
    """Get admin tools instance"""
    global admin_tools
    if admin_tools is None:
        admin_tools = ChatHistoryAdminTools()
        await admin_tools.initialize_services()
    return admin_tools


class SystemStatus(BaseModel):
    """System status response model"""
    timestamp: str
    status: str
    services: Dict[str, Any]
    statistics: Dict[str, Any]


@router.get("/", response_class=HTMLResponse)
async def monitoring_dashboard():
    """Simple HTML monitoring dashboard"""
    html_content = """
    <!DOCTYPE html>
    <html>
    <head>
        <title>BGBG AI Server - Monitoring Dashboard</title>
        <meta charset="utf-8">
        <meta name="viewport" content="width=device-width, initial-scale=1">
        <style>
            body { 
                font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
                margin: 0; padding: 20px; background: #f5f5f5; 
            }
            .container { max-width: 1200px; margin: 0 auto; }
            .header { 
                background: white; padding: 20px; border-radius: 8px; 
                margin-bottom: 20px; box-shadow: 0 2px 4px rgba(0,0,0,0.1);
            }
            .grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(300px, 1fr)); gap: 20px; }
            .card { 
                background: white; padding: 20px; border-radius: 8px; 
                box-shadow: 0 2px 4px rgba(0,0,0,0.1);
            }
            .status-healthy { color: #28a745; }
            .status-warning { color: #ffc107; }
            .status-error { color: #dc3545; }
            .metric { display: flex; justify-content: space-between; margin: 10px 0; }
            .metric-label { font-weight: 500; }
            .metric-value { font-family: monospace; }
            .refresh-btn { 
                background: #007bff; color: white; border: none; 
                padding: 10px 20px; border-radius: 4px; cursor: pointer;
            }
            .refresh-btn:hover { background: #0056b3; }
            .timestamp { color: #666; font-size: 0.9em; }
            .loading { text-align: center; padding: 20px; color: #666; }
            .error { color: #dc3545; padding: 20px; text-align: center; }
        </style>
    </head>
    <body>
        <div class="container">
            <div class="header">
                <h1>🤖 BGBG AI Server - Monitoring Dashboard</h1>
                <p>Chat History System Status</p>
                <button class="refresh-btn" onclick="refreshData()">🔄 Refresh</button>
                <span class="timestamp" id="lastUpdate"></span>
            </div>
            
            <div id="content" class="loading">
                Loading system status...
            </div>
        </div>

        <script>
            let refreshInterval;

            async function fetchSystemStatus() {
                try {
                    const response = await fetch('/monitoring/status');
                    const data = await response.json();
                    return data;
                } catch (error) {
                    console.error('Failed to fetch system status:', error);
                    return null;
                }
            }

            function formatBytes(bytes) {
                if (bytes === 0) return '0 B';
                const k = 1024;
                const sizes = ['B', 'KB', 'MB', 'GB'];
                const i = Math.floor(Math.log(bytes) / Math.log(k));
                return parseFloat((bytes / Math.pow(k, i)).toFixed(2)) + ' ' + sizes[i];
            }

            function getStatusClass(status) {
                if (status === 'healthy' || status === 'operational') return 'status-healthy';
                if (status === 'warning' || status === 'degraded') return 'status-warning';
                return 'status-error';
            }

            function renderSystemStatus(data) {
                if (!data) {
                    document.getElementById('content').innerHTML = 
                        '<div class="error">❌ Failed to load system status</div>';
                    return;
                }

                const services = data.services || {};
                const stats = data.statistics || {};

                const html = `
                    <div class="grid">
                        <div class="card">
                            <h3>🔧 System Overview</h3>
                            <div class="metric">
                                <span class="metric-label">Overall Status:</span>
                                <span class="metric-value ${getStatusClass(data.system_status)}">
                                    ${data.system_status || 'unknown'}
                                </span>
                            </div>
                            <div class="metric">
                                <span class="metric-label">Chat History:</span>
                                <span class="metric-value ${data.configuration?.enabled ? 'status-healthy' : 'status-error'}">
                                    ${data.configuration?.enabled ? 'Enabled' : 'Disabled'}
                                </span>
                            </div>
                            <div class="metric">
                                <span class="metric-label">Last Updated:</span>
                                <span class="metric-value">${new Date(data.timestamp).toLocaleString()}</span>
                            </div>
                        </div>

                        <div class="card">
                            <h3>💾 Redis Status</h3>
                            <div class="metric">
                                <span class="metric-label">Status:</span>
                                <span class="metric-value ${getStatusClass(services.memory?.status || 'unknown')}">
                                    ${services.memory?.status || 'unknown'}
                                </span>
                            </div>
                            <div class="metric">
                                <span class="metric-label">Memory Usage:</span>
                                <span class="metric-value">${(services.memory?.usage_percentage * 100 || 0).toFixed(1)}%</span>
                            </div>
                            <div class="metric">
                                <span class="metric-label">Active Sessions:</span>
                                <span class="metric-value">${services.memory?.active_sessions || 0}</span>
                            </div>
                            <div class="metric">
                                <span class="metric-label">Total Keys:</span>
                                <span class="metric-value">${services.memory?.total_keys || 0}</span>
                            </div>
                        </div>

                        <div class="card">
                            <h3>🚀 Performance</h3>
                            <div class="metric">
                                <span class="metric-label">Avg Response Time:</span>
                                <span class="metric-value">${(services.performance?.avg_response_time || 0).toFixed(2)}ms</span>
                            </div>
                            <div class="metric">
                                <span class="metric-label">Total Requests:</span>
                                <span class="metric-value">${services.performance?.total_requests || 0}</span>
                            </div>
                            <div class="metric">
                                <span class="metric-label">Error Rate:</span>
                                <span class="metric-value ${(services.performance?.error_rate || 0) > 5 ? 'status-warning' : 'status-healthy'}">
                                    ${(services.performance?.error_rate || 0).toFixed(2)}%
                                </span>
                            </div>
                        </div>

                        <div class="card">
                            <h3>📊 Cache Performance</h3>
                            <div class="metric">
                                <span class="metric-label">Hit Rate:</span>
                                <span class="metric-value ${(services.cache?.hit_rate || 0) > 0.7 ? 'status-healthy' : 'status-warning'}">
                                    ${((services.cache?.hit_rate || 0) * 100).toFixed(1)}%
                                </span>
                            </div>
                            <div class="metric">
                                <span class="metric-label">Cache Keys:</span>
                                <span class="metric-value">${services.cache?.total_keys || 0}</span>
                            </div>
                            <div class="metric">
                                <span class="metric-label">Memory Usage:</span>
                                <span class="metric-value">${formatBytes(services.cache?.memory_usage_bytes || 0)}</span>
                            </div>
                        </div>

                        <div class="card">
                            <h3>🔐 Health Checks</h3>
                            <div class="metric">
                                <span class="metric-label">Redis Connection:</span>
                                <span class="metric-value ${getStatusClass(services.health?.redis_connection_healthy ? 'healthy' : 'error')}">
                                    ${services.health?.redis_connection_healthy ? 'Healthy' : 'Unhealthy'}
                                </span>
                            </div>
                            <div class="metric">
                                <span class="metric-label">Memory Manager:</span>
                                <span class="metric-value ${getStatusClass(services.health?.memory_manager_running ? 'healthy' : 'error')}">
                                    ${services.health?.memory_manager_running ? 'Running' : 'Stopped'}
                                </span>
                            </div>
                            <div class="metric">
                                <span class="metric-label">Performance Monitor:</span>
                                <span class="metric-value ${getStatusClass(services.health?.performance_monitor_running ? 'healthy' : 'error')}">
                                    ${services.health?.performance_monitor_running ? 'Running' : 'Stopped'}
                                </span>
                            </div>
                        </div>

                        <div class="card">
                            <h3>⚠️ Recent Alerts</h3>
                            <div id="alerts">
                                ${(services.performance?.recent_alerts || []).length > 0 ? 
                                    services.performance.recent_alerts.map(alert => 
                                        `<div class="metric">
                                            <span class="metric-label ${getStatusClass(alert.level)}">${alert.level}:</span>
                                            <span class="metric-value">${alert.message}</span>
                                        </div>`
                                    ).join('') : 
                                    '<div class="metric"><span class="metric-value status-healthy">No recent alerts</span></div>'
                                }
                            </div>
                        </div>
                    </div>
                `;

                document.getElementById('content').innerHTML = html;
                document.getElementById('lastUpdate').textContent = 
                    `Last updated: ${new Date().toLocaleTimeString()}`;
            }

            async function refreshData() {
                document.getElementById('content').innerHTML = '<div class="loading">Refreshing...</div>';
                const data = await fetchSystemStatus();
                renderSystemStatus(data);
            }

            // Initial load
            refreshData();

            // Auto-refresh every 30 seconds
            refreshInterval = setInterval(refreshData, 30000);

            // Cleanup on page unload
            window.addEventListener('beforeunload', () => {
                if (refreshInterval) {
                    clearInterval(refreshInterval);
                }
            });
        </script>
    </body>
    </html>
    """
    return html_content


@router.get("/status", response_model=SystemStatus)
async def get_system_status():
    """Get comprehensive system status"""
    try:
        tools = await get_admin_tools()
        
        # Get system dashboard (using read-only admin user)
        dashboard = await tools.get_system_dashboard("monitoring_user")
        
        if "error" in dashboard:
            # Create a basic status if admin tools fail
            config = get_chat_config()
            return SystemStatus(
                timestamp=datetime.utcnow().isoformat(),
                status="degraded",
                services={
                    "error": dashboard["error"],
                    "configuration": config.get_runtime_info()
                },
                statistics={}
            )
        
        return SystemStatus(
            timestamp=dashboard["timestamp"],
            status=dashboard["system_status"],
            services=dashboard["services"],
            statistics=dashboard.get("statistics", {})
        )
        
    except Exception as e:
        logger.error(f"Failed to get system status: {e}")
        raise HTTPException(status_code=500, detail=f"Failed to get system status: {e}")


@router.get("/redis")
async def get_redis_status():
    """Get detailed Redis status"""
    try:
        connection_manager = await get_redis_connection_manager()
        health_check = await connection_manager.health_check()
        connection_stats = await connection_manager.get_connection_stats()
        
        return {
            "timestamp": datetime.utcnow().isoformat(),
            "health_check": health_check,
            "connection_stats": {
                "total_connections": connection_stats.total_connections,
                "active_connections": connection_stats.active_connections,
                "idle_connections": connection_stats.idle_connections,
                "failed_connections": connection_stats.failed_connections,
                "connection_errors": connection_stats.connection_errors,
                "status": connection_stats.status.value,
                "response_time_ms": connection_stats.response_time_ms
            }
        }
        
    except Exception as e:
        logger.error(f"Failed to get Redis status: {e}")
        raise HTTPException(status_code=500, detail=f"Failed to get Redis status: {e}")


@router.get("/config")
async def get_configuration():
    """Get current system configuration"""
    try:
        config = get_chat_config()
        return {
            "timestamp": datetime.utcnow().isoformat(),
            "configuration": config.to_dict(),
            "runtime_info": config.get_runtime_info()
        }
        
    except Exception as e:
        logger.error(f"Failed to get configuration: {e}")
        raise HTTPException(status_code=500, detail=f"Failed to get configuration: {e}")


@router.get("/health")
async def health_check():
    """Simple health check endpoint"""
    try:
        # Basic health checks
        config = get_chat_config()
        
        # Test Redis connection
        redis_healthy = False
        try:
            connection_manager = await get_redis_connection_manager()
            client = await connection_manager.get_client()
            if client:
                await client.ping()
                redis_healthy = True
        except Exception:
            pass
        
        status = "healthy" if redis_healthy and config.enabled else "degraded"
        
        return {
            "status": status,
            "timestamp": datetime.utcnow().isoformat(),
            "chat_history_enabled": config.enabled,
            "redis_healthy": redis_healthy,
            "version": "1.0.0"
        }
        
    except Exception as e:
        logger.error(f"Health check failed: {e}")
        return {
            "status": "unhealthy",
            "timestamp": datetime.utcnow().isoformat(),
            "error": str(e)
        }


@router.post("/cleanup")
async def trigger_cleanup():
    """Trigger manual cleanup (admin operation)"""
    try:
        tools = await get_admin_tools()
        
        # Perform cleanup (using operator-level admin user)
        result = await tools.cleanup_inactive_sessions("cleanup_user", force=False)
        
        return {
            "timestamp": datetime.utcnow().isoformat(),
            "cleanup_result": result
        }
        
    except Exception as e:
        logger.error(f"Manual cleanup failed: {e}")
        raise HTTPException(status_code=500, detail=f"Cleanup failed: {e}")


# Initialize admin tools with basic users for monitoring
async def initialize_monitoring():
    """Initialize monitoring with basic admin users"""
    try:
        tools = await get_admin_tools()
        
        # Add monitoring users
        tools.add_admin_user("monitoring_user", "monitoring", tools.AdminLevel.READ_ONLY)
        tools.add_admin_user("cleanup_user", "cleanup", tools.AdminLevel.OPERATOR)
        
        logger.info("Monitoring system initialized")
        
    except Exception as e:
        logger.error(f"Failed to initialize monitoring: {e}")


# Initialize on module load
asyncio.create_task(initialize_monitoring())