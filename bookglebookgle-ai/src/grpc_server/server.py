"""
gRPC Server implementation for BGBG AI Service
"""

import asyncio
import socket
import time
from concurrent.futures import ThreadPoolExecutor
from typing import Optional

import grpc
from grpc import aio
from loguru import logger

from src.config.settings import get_settings
from src.grpc_server.ai_servicer import AIServicer
from src.grpc_server.generated import ai_service_pb2_grpc


class GRPCServer:
    """gRPC server for AI services"""
    
    def __init__(self):
        self.settings = get_settings()
        self.server: Optional[aio.Server] = None
        
    def _is_port_available(self, host: str, port: int) -> bool:
        """Check if port is available for binding"""
        try:
            with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as sock:
                sock.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
                result = sock.bind((host, port))
                return True
        except OSError:
            return False
    
    def _find_available_port(self, start_port: int, max_attempts: int = 10) -> int:
        """Find an available port starting from start_port"""
        host = "0.0.0.0" if self.settings.SERVER_HOST == "0.0.0.0" else "127.0.0.1"
        
        for port in range(start_port, start_port + max_attempts):
            if self._is_port_available(host, port):
                return port
        
        raise RuntimeError(f"No available port found in range {start_port}-{start_port + max_attempts - 1}")
    
    async def start(self) -> None:
        """Start the gRPC server with robust port binding"""
        max_retries = 3
        retry_delay = 2
        
        for attempt in range(max_retries):
            try:
                # Check if configured port is available
                desired_port = self.settings.SERVER_PORT
                host = "0.0.0.0" if self.settings.SERVER_HOST == "0.0.0.0" else "127.0.0.1"
                
                if not self._is_port_available(host, desired_port):
                    logger.warning(f"Port {desired_port} is not available, searching for alternative...")
                    # Try to find an available port near the desired one
                    available_port = self._find_available_port(desired_port, 10)
                    logger.info(f"Using alternative port: {available_port}")
                    actual_port = available_port
                else:
                    actual_port = desired_port
                
                # Create server with thread pool
                self.server = aio.server(
                    ThreadPoolExecutor(max_workers=10),
                    options=[
                        ("grpc.keepalive_time_ms", 30000),
                        ("grpc.keepalive_timeout_ms", 5000),
                        ("grpc.keepalive_permit_without_calls", True),
                        ("grpc.http2.max_pings_without_data", 0),
                        ("grpc.http2.min_time_between_pings_ms", 10000),
                        ("grpc.http2.min_ping_interval_without_data_ms", 300000),
                        ("grpc.max_message_length", self.settings.grpc.GRPC_MAX_MESSAGE_LENGTH),
                        ("grpc.max_receive_message_length", self.settings.grpc.GRPC_MAX_MESSAGE_LENGTH),
                        ("grpc.so_reuseaddr", 1),  # Allow port reuse
                    ]
                )
                
                # Add AI service
                ai_servicer = AIServicer()
                ai_service_pb2_grpc.add_AIServiceServicer_to_server(ai_servicer, self.server)
                
                # Configure listening address
                listen_addr = f"{self.settings.SERVER_HOST}:{actual_port}"
                
                # Try to bind to port
                try:
                    self.server.add_insecure_port(listen_addr)
                except Exception as bind_error:
                    logger.error(f"Failed to bind to {listen_addr}: {bind_error}")
                    if attempt < max_retries - 1:
                        logger.info(f"Retrying in {retry_delay} seconds... (attempt {attempt + 1}/{max_retries})")
                        await asyncio.sleep(retry_delay)
                        continue
                    else:
                        raise
                
                # Start server
                await self.server.start()
                logger.info(f"✅ gRPC server successfully started on {listen_addr}")
                
                # Update settings if we used a different port
                if actual_port != desired_port:
                    logger.warning(f"⚠️  Server started on port {actual_port} instead of configured port {desired_port}")
                    logger.info(f"📋 Update your client configurations to use port {actual_port}")
                
                return  # Success, exit retry loop
                
            except Exception as e:
                logger.error(f"Failed to start gRPC server (attempt {attempt + 1}/{max_retries}): {e}")
                if attempt < max_retries - 1:
                    logger.info(f"Retrying in {retry_delay} seconds...")
                    await asyncio.sleep(retry_delay)
                else:
                    logger.error("All retry attempts failed. Server startup aborted.")
                    raise
            
    async def stop(self) -> None:
        """Stop the gRPC server gracefully"""
        if self.server:
            logger.info("🛑 Stopping gRPC server...")
            try:
                await self.server.stop(grace=10)  # Reduced grace period
                logger.info("✅ gRPC server stopped gracefully")
            except Exception as e:
                logger.error(f"Error during server shutdown: {e}")
                # Force stop if graceful shutdown fails
                try:
                    await self.server.stop(grace=0)
                    logger.info("🚨 gRPC server force stopped")
                except Exception as force_error:
                    logger.error(f"Failed to force stop server: {force_error}")
            finally:
                self.server = None
            
    async def wait_for_termination(self) -> None:
        """Wait for server termination"""
        if self.server:
            await self.server.wait_for_termination()