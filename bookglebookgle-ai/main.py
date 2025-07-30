"""
BGBG AI Server - Main Entry Point

This is the main FastAPI server that handles AI operations for the BGBG platform.
Features include quiz generation, text proofreading, and discussion facilitation.
"""

import asyncio
import logging
from contextlib import asynccontextmanager
from pathlib import Path

import uvicorn
from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware
from loguru import logger

from src.config.settings import get_settings
from src.grpc_server.server import GRPCServer
from src.api.routes import router as api_router
from src.services.vector_db import VectorDBManager
from src.services.llm_client import LLMClient
from src.utils.logging_config import setup_logging
from src.utils.port_utils import ensure_port_free, print_ports_report


@asynccontextmanager
async def lifespan(app: FastAPI):
    """Application lifespan management with robust port handling"""
    settings = get_settings()
    
    # Setup logging
    setup_logging(settings.LOG_LEVEL)
    logger.info("🚀 Starting BGBG AI Server...")
    
    # Check and prepare ports
    logger.info("🔍 Checking required ports...")
    required_ports = [settings.SERVER_PORT, 8789]  # gRPC and FastAPI ports
    print_ports_report(required_ports)
    
    # 1. Initialize services that need async operations
    logger.info("⚙️  Initializing services...")
    vector_db = VectorDBManager()
    await vector_db.initialize()
    logger.info("✅ VectorDB Manager initialized.")
    
    # Initialize LLM Client (if it has async init)
    llm_client = LLMClient()
    await llm_client.initialize()
    logger.info("✅ LLM Client initialized.")
    
    # 2. Start gRPC server, injecting the initialized vector_db
    logger.info("🚀 Starting gRPC server...")
    grpc_server = GRPCServer(vector_db_manager=vector_db)
    try:
        await grpc_server.start()
    except Exception as e:
        logger.error(f"❌ Failed to start gRPC server: {e}")
        logger.info("🔄 Attempting to clean up and retry...")
        # Clean up the port again and retry once
        ensure_port_free(settings.SERVER_PORT, kill_if_needed=True)
        await asyncio.sleep(2)  # Wait a bit
        await grpc_server.start()  # Retry
    
    logger.info(f"✅ FastAPI Server will start on port 8789")
    logger.info(f"✅ gRPC Server started on port {settings.SERVER_PORT}")
    
    yield
    
    # Cleanup
    logger.info("🛑 Shutting down BGBG AI Server...")
    try:
        await grpc_server.stop()
        logger.info("✅ Server shutdown completed")
    except Exception as e:
        logger.error(f"⚠️  Error during shutdown: {e}")
    await vector_db.cleanup()
    logger.info("Server shutdown complete")


def create_app() -> FastAPI:
    """Create and configure FastAPI application"""
    settings = get_settings()
    
    app = FastAPI(
        title="BGBG AI Server",
        description="AI-powered features for BGBG reading platform",
        version="1.0.0",
        debug=settings.DEBUG,
        lifespan=lifespan
    )
    
    # CORS middleware
    app.add_middleware(
        CORSMiddleware,
        allow_origins=["*"] if settings.DEBUG else [
            "http://localhost:3000"
        ],
        allow_credentials=True,
        allow_methods=["*"],
        allow_headers=["*"],
    )
    
    # Include API routes
    app.include_router(api_router, prefix="/api/v1")
    
    @app.get("/")
    async def root():
        return {
            "service": "BGBG AI Server",
            "version": "1.0.0",
            "status": "running"
        }
    
    @app.get("/health")
    async def health_check():
        return {"status": "healthy", "service": "BGBG AI Server"}
    
    return app


app = create_app()

def main():
    """Main function to run the server"""
    settings = get_settings()
    
    # FastAPI를 다른 포트에서 실행 (gRPC는 50505, FastAPI는 8789)
    uvicorn.run(
        "main:app",
        host=settings.SERVER_HOST,
        port=8789,  # FastAPI 전용 포트
        log_level=settings.LOG_LEVEL.lower(),
        reload=settings.DEBUG,
        access_log=True
    )


if __name__ == "__main__":
    main()