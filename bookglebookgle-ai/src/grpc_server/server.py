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
from src.services.vector_db import VectorDBManager  # Import VectorDBManager


class GRPCServer:
    """Manages the gRPC server lifecycle"""
    
    def __init__(self, vector_db_manager: VectorDBManager):
        self.settings = get_settings()
        self.server = None
        self.vector_db_manager = vector_db_manager  # Store the initialized manager
        logger.info("gRPC Server object created.")
        
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
    
    async def start(self):
        """Start the gRPC server with retry logic"""
        for attempt in range(1, self.settings.SERVER_START_RETRIES + 1):
            try:
                logger.info(f"Attempting to start gRPC server (attempt {attempt}/{self.settings.SERVER_START_RETRIES})...")
                
                self.server = grpc.aio.server(
                    ThreadPoolExecutor(max_workers=self.settings.SERVER_WORKERS)
                )
                
                # Pass the initialized vector_db_manager to the servicer
                ai_servicer = AIServicer(self.vector_db_manager)
                ai_service_pb2_grpc.add_AIServiceServicer_to_server(ai_servicer, self.server)
                
                self.server.add_insecure_port(f"[::]:{self.settings.SERVER_PORT}")
                await self.server.start()
                
                logger.info(f"✅ gRPC Server started successfully on port {self.settings.SERVER_PORT}")
                return  # Success, exit the loop

            except Exception as e:
                logger.error(f"Failed to start gRPC server (attempt {attempt}/{self.settings.SERVER_START_RETRIES}): {e}")
                if attempt < self.settings.SERVER_START_RETRIES:
                    retry_delay = self.settings.SERVER_RETRY_DELAY
                    logger.info(f"Retrying in {retry_delay} seconds...")
                    await asyncio.sleep(retry_delay)
        
        logger.error("All retry attempts failed. Server startup aborted.")
        raise RuntimeError("Failed to start gRPC server after multiple retries.")
            
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