"""
BGBG AI Server - Main Entry Point

This is the main gRPC server that handles AI operations for the BGBG platform.
Core features:
1. Document processing (Text/PDF) with vector DB storage (50%, 100% progress)
2. OCR processing with PaddleOCR and position data
3. Real-time discussion facilitation with LLM (claude-3-5-haiku/sonnet)
4. Quiz generation (multiple choice with correct answers)
5. Text proofreading with grammar/spelling suggestions
6. Redis caching for chat history
7. GMS API integration for LLM services
"""

import asyncio
import signal
import sys
from pathlib import Path

from loguru import logger

from src.config.settings import get_settings
from src.grpc_server.server import GRPCServer
from src.services.service_initializer import ServiceInitializer
from src.utils.logging_config import setup_logging
from src.utils.port_utils import ensure_port_free, print_ports_report


async def initialize_services():
    """Initialize all required services using ServiceInitializer"""
    settings = get_settings()
    
    # Setup logging
    setup_logging(settings.LOG_LEVEL)
    logger.info("🚀 Starting BGBG AI Server...")
    
    # Check and prepare ports
    logger.info("🔍 Checking required ports...")
    required_ports = [settings.SERVER_PORT]
    print_ports_report(required_ports)
    
    # Initialize all services using ServiceInitializer
    logger.info("⚙️ Initializing services with ServiceInitializer...")
    
    service_initializer = ServiceInitializer()
    try:
        services = await service_initializer.initialize_all_services()
        logger.info("✅ All services initialized successfully")
        
        # Add service_initializer to services for cleanup
        services['service_initializer'] = service_initializer
        
        return services
        
    except Exception as e:
        logger.error(f"❌ Service initialization failed: {e}")
        # Return partial services for graceful degradation
        partial_services = service_initializer.get_initialized_services()
        partial_services['service_initializer'] = service_initializer
        logger.info("🔄 Returning partial services for graceful degradation")
        return partial_services


async def start_grpc_server(services):
    """Start gRPC server with initialized services"""
    settings = get_settings()
    logger.info("🚀 Starting gRPC server...")
    
    grpc_server = GRPCServer(
        vector_db_manager=services['vector_db'],
        redis_manager=services['redis_manager'],
        llm_client=services['llm_client'],
        quiz_service=services.get('quiz_service'),
        proofreading_service=services.get('proofreading_service')
    )
    
    try:
        await grpc_server.start()
        logger.info(f"✅ gRPC Server started on port {settings.SERVER_PORT}")
        return grpc_server
    except Exception as e:
        logger.error(f"❌ Failed to start gRPC server: {e}")
        logger.info("🔄 Attempting to clean up and retry...")
        ensure_port_free(settings.SERVER_PORT, kill_if_needed=True)
        await asyncio.sleep(2)
        await grpc_server.start()
        logger.info(f"✅ gRPC Server started on port {settings.SERVER_PORT} (retry)")
        return grpc_server


async def cleanup_services(services, grpc_server):
    """Cleanup all services"""
    logger.info("🛑 Shutting down BGBG AI Server...")
    
    try:
        if grpc_server:
            await grpc_server.stop()
            logger.info("✅ gRPC server stopped")
    except Exception as e:
        logger.error(f"⚠️ Error stopping gRPC server: {e}")
    
    try:
        if services.get('service_initializer'):
            await services['service_initializer'].cleanup_services()
            logger.info("✅ All services cleaned up via ServiceInitializer")
    except Exception as e:
        logger.error(f"⚠️ Error cleaning up services: {e}")
        
        # Fallback cleanup
        try:
            if services.get('vector_db'):
                if hasattr(services['vector_db'], 'cleanup'):
                    await services['vector_db'].cleanup()
                logger.info("✅ VectorDB cleaned up")
        except Exception as e:
            logger.error(f"⚠️ Error cleaning up VectorDB: {e}")
        
        try:
            if services.get('redis_manager'):
                await services['redis_manager'].cleanup()
                logger.info("✅ Redis connections cleaned up")
        except Exception as e:
            logger.error(f"⚠️ Error cleaning up Redis: {e}")
    
    logger.info("✅ Server shutdown complete")


async def main_async():
    """Main async function"""
    services = None
    grpc_server = None
    
    try:
        # Initialize services
        services = await initialize_services()
        
        # Start gRPC server
        grpc_server = await start_grpc_server(services)
        
        # Setup signal handlers for graceful shutdown
        def signal_handler(signum, frame):
            logger.info(f"Received signal {signum}, shutting down...")
            raise KeyboardInterrupt()
        
        signal.signal(signal.SIGINT, signal_handler)
        signal.signal(signal.SIGTERM, signal_handler)
        
        logger.info("🎯 BGBG AI Server is running. Press Ctrl+C to stop.")
        
        # Keep the server running
        try:
            while True:
                await asyncio.sleep(1)
        except KeyboardInterrupt:
            logger.info("Received shutdown signal")
    
    except Exception as e:
        logger.error(f"❌ Server startup failed: {e}")
        raise
    finally:
        if services or grpc_server:
            await cleanup_services(services or {}, grpc_server)


def main():
    """Main function to run the gRPC server"""
    try:
        asyncio.run(main_async())
    except KeyboardInterrupt:
        logger.info("✅ Server stopped by user")
    except Exception as e:
        logger.error(f"❌ Server error: {e}")
        sys.exit(1)


if __name__ == "__main__":
    main()