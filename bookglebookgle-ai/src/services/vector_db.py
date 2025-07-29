"""
Vector Database Manager for BGBG AI Server
Handles document embeddings, similarity search, and RAG operations
"""

import asyncio
import hashlib
import uuid
from pathlib import Path
from typing import Dict, List, Optional, Tuple, Any

import chromadb
from chromadb.config import Settings
from sentence_transformers import SentenceTransformer
import numpy as np
from loguru import logger

from src.config.settings import get_settings


class VectorDBManager:
    """Manages vector database operations for document embeddings and RAG"""
    
    def __init__(self):
        self.settings = get_settings()
        self.client: Optional[chromadb.Client] = None
        self.embedding_model: Optional[SentenceTransformer] = None
        self.collections: Dict[str, chromadb.Collection] = {}
        
    async def initialize(self):
        """Initialize vector database and embedding model"""
        try:
            logger.info("Initializing Vector Database Manager...")
            
            # Initialize ChromaDB client
            persist_dir = Path(self.settings.vector_db.CHROMA_PERSIST_DIRECTORY)
            persist_dir.mkdir(parents=True, exist_ok=True)
            
            self.client = chromadb.PersistentClient(
                path=str(persist_dir),
                settings=Settings(
                    anonymized_telemetry=False,
                    allow_reset=True
                )
            )
            
            # Initialize embedding model
            await self._initialize_embedding_model()
            
            # Create default collections
            await self._create_default_collections()
            
            logger.info("Vector Database Manager initialized successfully")
            
        except Exception as e:
            logger.error(f"Failed to initialize Vector DB Manager: {e}")
            raise
    
    async def _initialize_embedding_model(self):
        """Initialize sentence transformer model for embeddings"""
        try:
            logger.info("Loading embedding model...")
            
            # Use multilingual model for Korean support
            model_name = "paraphrase-multilingual-MiniLM-L12-v2"
            self.embedding_model = SentenceTransformer(model_name)
            
            logger.info(f"Embedding model loaded: {model_name}")
            
        except Exception as e:
            logger.error(f"Failed to load embedding model: {e}")
            raise
    
    async def _create_default_collections(self):
        """Create default collections for different document types"""
        collections = [
            "documents",      # Main document chunks
            "discussions",    # Discussion context
            "user_data",      # User-specific data
            "pdf_documents"   # PDF documents with position data
        ]
        
        for collection_name in collections:
            try:
                collection = self.client.get_or_create_collection(
                    name=collection_name,
                    metadata={"hnsw:space": "cosine"}
                )
                self.collections[collection_name] = collection
                logger.info(f"Collection '{collection_name}' ready")
                
            except Exception as e:
                logger.error(f"Failed to create collection '{collection_name}': {e}")
    
    async def process_document(self, document_id: str, text: str, metadata: Dict[str, Any] = None) -> List[str]:
        """Process document into chunks and store embeddings"""
        try:
            logger.info(f"Processing document: {document_id}")
            
            # Chunk the document
            chunks = await self._chunk_document(text)
            
            # Generate embeddings for chunks
            chunk_ids = []
            documents = []
            embeddings = []
            metadatas = []
            
            for i, chunk in enumerate(chunks):
                chunk_id = f"{document_id}_chunk_{i}"
                chunk_ids.append(chunk_id)
                documents.append(chunk)
                
                # Generate embedding
                if self.embedding_model is None:
                    await self._initialize_embedding_model()
                
                embedding = self.embedding_model.encode(chunk).tolist()
                embeddings.append(embedding)
                
                # Prepare metadata
                chunk_metadata = {
                    "document_id": document_id,
                    "chunk_index": i,
                    "chunk_length": len(chunk),
                    "text_hash": hashlib.md5(chunk.encode()).hexdigest()
                }
                if metadata:
                    chunk_metadata.update(metadata)
                metadatas.append(chunk_metadata)
            
            # Store in vector database
            collection = self.collections.get("documents")
            if collection:
                collection.upsert(
                    ids=chunk_ids,
                    documents=documents,
                    embeddings=embeddings,
                    metadatas=metadatas
                )
                
                logger.info(f"Stored {len(chunks)} chunks for document {document_id}")
            
            return chunk_ids
            
        except Exception as e:
            logger.error(f"Document processing failed for {document_id}: {e}")
            raise
    
    async def _chunk_document(self, text: str, chunk_size: int = 500, overlap: int = 50) -> List[str]:
        """Split document into overlapping chunks"""
        chunks = []
        words = text.split()
        
        if len(words) <= chunk_size:
            return [text]
        
        start = 0
        while start < len(words):
            end = min(start + chunk_size, len(words))
            chunk = " ".join(words[start:end])
            chunks.append(chunk)
            
            if end >= len(words):
                break
                
            start = end - overlap
        
        return chunks
    
    async def similarity_search(
        self, 
        query: str, 
        collection_name: str = "documents",
        n_results: int = 5,
        filter_metadata: Dict[str, Any] = None
    ) -> List[Dict[str, Any]]:
        """Search for similar document chunks"""
        try:
            # Generate query embedding
            query_embedding = self.embedding_model.encode(query).tolist()
            
            # Search in collection
            collection = self.collections.get(collection_name)
            if not collection:
                raise ValueError(f"Collection '{collection_name}' not found")
            
            results = collection.query(
                query_embeddings=[query_embedding],
                n_results=n_results,
                where=filter_metadata,
                include=["documents", "metadatas", "distances"]
            )
            
            # Format results
            search_results = []
            if results["documents"] and results["documents"][0]:
                for i in range(len(results["documents"][0])):
                    search_results.append({
                        "id": results["ids"][0][i],
                        "document": results["documents"][0][i],
                        "metadata": results["metadatas"][0][i],
                        "distance": results["distances"][0][i],
                        "similarity": 1 - results["distances"][0][i]  # Convert distance to similarity
                    })
            
            return search_results
            
        except Exception as e:
            logger.error(f"Similarity search failed: {e}")
            return []
    
    async def process_ocr_results(self, document_id: str, text_blocks: List[Dict[str, Any]]) -> List[str]:
        """Process OCR results and store them in the vector database."""
        try:
            logger.info(f"Processing {len(text_blocks)} OCR text blocks for document: {document_id}")

            if not text_blocks:
                return []

            chunk_ids = []
            documents = []
            embeddings = []
            metadatas = []

            for i, block in enumerate(text_blocks):
                chunk_id = f"{document_id}_ocr_chunk_{i}"
                text = block.get("text", "") or ""  # Handle None values
                if not text or not text.strip():
                    continue

                chunk_ids.append(chunk_id)
                documents.append(text)

                # Generate embedding (ensure text is string)
                text_for_embedding = str(text) if text is not None else ""
                embedding = self.embedding_model.encode(text_for_embedding).tolist()
                embeddings.append(embedding)

                # Prepare metadata with location info
                chunk_metadata = {
                    "document_id": document_id,
                    "chunk_index": i,
                    "page_number": block.get("page_number"),
                    "x0": block.get("x0"),
                    "y0": block.get("y0"),
                    "x1": block.get("x1"),
                    "y1": block.get("y1"),
                    "text_hash": hashlib.md5((text or "").encode('utf-8')).hexdigest()
                }
                metadatas.append(chunk_metadata)

            # Store in vector database
            collection = self.collections.get("documents")
            if collection and chunk_ids:
                collection.upsert(
                    ids=chunk_ids,
                    documents=documents,
                    embeddings=embeddings,
                    metadatas=metadatas
                )
                logger.info(f"Stored {len(chunk_ids)} OCR chunks for document {document_id}")
            
            return chunk_ids

        except Exception as e:
            logger.error(f"OCR result processing failed for {document_id}: {e}")
            raise
    
    async def get_context_for_discussion(
        self, 
        document_id: str, 
        query: str, 
        max_chunks: int = 3
    ) -> List[str]:
        """Get relevant context chunks for discussion AI"""
        try:
            # Search for relevant chunks
            results = await self.similarity_search(
                query=query,
                collection_name="documents",
                n_results=max_chunks,
                filter_metadata={"document_id": document_id}
            )
            
            # Extract document text sorted by similarity
            context_chunks = [result["document"] for result in results]
            
            logger.info(f"Retrieved {len(context_chunks)} context chunks for discussion")
            return context_chunks
            
        except Exception as e:
            logger.error(f"Failed to get discussion context: {e}")
            return []
    
    async def store_discussion_context(
        self,
        session_id: str,
        messages: List[Dict[str, str]],
        document_context: str
    ) -> str:
        """Store discussion context for future reference"""
        try:
            # Prepare discussion summary
            discussion_text = f"Discussion Context:\n{document_context}\n\nMessages:\n"
            for msg in messages[-10:]:  # Store last 10 messages
                discussion_text += f"{msg.get('sender', 'Unknown')}: {msg.get('message', '')}\n"
            
            # Generate embedding
            embedding = self.embedding_model.encode(discussion_text).tolist()
            
            # Store in discussions collection
            discussion_id = f"discussion_{session_id}_{uuid.uuid4().hex[:8]}"
            collection = self.collections.get("discussions")
            
            if collection:
                collection.upsert(
                    ids=[discussion_id],
                    documents=[discussion_text],
                    embeddings=[embedding],
                    metadatas=[{
                        "session_id": session_id,
                        "message_count": len(messages),
                        "timestamp": asyncio.get_event_loop().time()
                    }]
                )
            
            return discussion_id
            
        except Exception as e:
            logger.error(f"Failed to store discussion context: {e}")
            return ""
    
    async def cleanup_old_discussions(self, max_age_hours: int = 24):
        """Cleanup old discussion contexts"""
        try:
            current_time = asyncio.get_event_loop().time()
            cutoff_time = current_time - (max_age_hours * 3600)
            
            collection = self.collections.get("discussions")
            if collection:
                # Query old discussions
                results = collection.get(
                    where={"timestamp": {"$lt": cutoff_time}},
                    include=["metadatas"]
                )
                
                if results["ids"]:
                    collection.delete(ids=results["ids"])
                    logger.info(f"Cleaned up {len(results['ids'])} old discussion contexts")
            
        except Exception as e:
            logger.error(f"Discussion cleanup failed: {e}")
    
    async def get_document_summary(self, document_id: str) -> Optional[str]:
        """Get document summary for topic generation"""
        try:
            # Get all chunks for the document
            collection = self.collections.get("documents")
            if not collection:
                return None
            
            results = collection.get(
                where={"document_id": document_id},
                include=["documents"]
            )
            
            if results["documents"]:
                # Combine first few chunks for summary
                chunks = results["documents"][:5]  # First 5 chunks
                combined_text = " ".join(chunks)
                
                # Truncate if too long
                if len(combined_text) > 2000:
                    combined_text = combined_text[:2000] + "..."
                
                return combined_text
            
            return None
            
        except Exception as e:
            logger.error(f"Failed to get document summary: {e}")
            return None
    
    async def cleanup(self):
        """Cleanup resources"""
        try:
            logger.info("Cleaning up Vector DB Manager...")
            
            # Cleanup old discussions
            await self.cleanup_old_discussions()
            
            # Close client if needed
            if self.client:
                # ChromaDB doesn't require explicit closing
                self.client = None
            
            logger.info("Vector DB Manager cleanup complete")
            
        except Exception as e:
            logger.error(f"Vector DB cleanup failed: {e}")
    
    async def process_pdf_document(
        self, 
        document_id: str, 
        pages_data: List[Dict[str, Any]], 
        metadata: Dict[str, Any] = None
    ) -> List[str]:
        """Process PDF document with position data and store in vector DB"""
        try:
            logger.info(f"Processing PDF document for vector storage: {document_id}")
            
            chunk_ids = []
            documents = []
            embeddings = []
            metadatas = []
            
            for page_data in pages_data:
                page_number = page_data["page_number"]
                page_text = page_data["text"]
                text_blocks = page_data["text_blocks"]
                
                # Process each text block as a chunk
                for block in text_blocks:
                    if not block["text"].strip():
                        continue
                        
                    # Create unique chunk ID
                    chunk_id = f"{document_id}_p{page_number}_b{block['block_index']}"
                    chunk_ids.append(chunk_id)
                    
                    # Prepare text for embedding
                    block_text = block["text"]
                    documents.append(block_text)
                    
                    # Generate embedding
                    embedding = self.embedding_model.encode(block_text).tolist()
                    embeddings.append(embedding)
                    
                    # Prepare metadata with position information
                    chunk_metadata = {
                        "document_id": document_id,
                        "page_number": page_number,
                        "block_index": block["block_index"],
                        "block_type": block.get("block_type", "text"),
                        "confidence": block.get("confidence", 0.0),
                        "text_length": len(block_text),
                        "text_hash": hashlib.md5(block_text.encode()).hexdigest(),
                        "processing_method": "pdf_ocr"
                    }
                    
                    # Add position data if available
                    if "bbox" in block:
                        bbox = block["bbox"]
                        chunk_metadata.update({
                            "bbox_x0": bbox["x0"],
                            "bbox_y0": bbox["y0"],
                            "bbox_x1": bbox["x1"],
                            "bbox_y1": bbox["y1"],
                            "bbox_width": bbox["x1"] - bbox["x0"],
                            "bbox_height": bbox["y1"] - bbox["y0"]
                        })
                    
                    # Add font information if available
                    if "font_info" in block:
                        font_info = block["font_info"]
                        chunk_metadata["fonts"] = ",".join(font_info.get("fonts", []))
                        chunk_metadata["font_sizes"] = ",".join(map(str, font_info.get("sizes", [])))
                    
                    # Add page dimensions
                    chunk_metadata["page_width"] = page_data.get("page_width", 0)
                    chunk_metadata["page_height"] = page_data.get("page_height", 0)
                    
                    # Merge with additional metadata
                    if metadata:
                        chunk_metadata.update(metadata)
                    
                    metadatas.append(chunk_metadata)
            
            # Store in PDF documents collection
            collection = self.collections.get("pdf_documents")
            if collection and chunk_ids:
                collection.upsert(
                    ids=chunk_ids,
                    documents=documents,
                    embeddings=embeddings,
                    metadatas=metadatas
                )
                
                logger.info(f"Stored {len(chunk_ids)} PDF chunks for document {document_id}")
            
            return chunk_ids
            
        except Exception as e:
            logger.error(f"PDF document processing failed for {document_id}: {e}")
            raise
    
    async def search_pdf_documents(
        self,
        query: str,
        document_id: Optional[str] = None,
        page_number: Optional[int] = None,
        n_results: int = 5,
        include_position: bool = True
    ) -> List[Dict[str, Any]]:
        """Search PDF documents with optional spatial filtering"""
        try:
            # Generate query embedding
            query_embedding = self.embedding_model.encode(query).tolist()
            
            # Build filter conditions
            filter_conditions = {}
            if document_id:
                filter_conditions["document_id"] = document_id
            if page_number is not None:
                filter_conditions["page_number"] = page_number
            
            # Search in PDF documents collection
            collection = self.collections.get("pdf_documents")
            if not collection:
                raise ValueError("PDF documents collection not found")
            
            results = collection.query(
                query_embeddings=[query_embedding],
                n_results=n_results,
                where=filter_conditions if filter_conditions else None,
                include=["documents", "metadatas", "distances"]
            )
            
            # Format results with position data
            search_results = []
            if results["documents"] and results["documents"][0]:
                for i in range(len(results["documents"][0])):
                    result_data = {
                        "id": results["ids"][0][i],
                        "document": results["documents"][0][i],
                        "metadata": results["metadatas"][0][i],
                        "distance": results["distances"][0][i],
                        "similarity": 1 - results["distances"][0][i]
                    }
                    
                    # Add position information if requested
                    if include_position:
                        metadata = results["metadatas"][0][i]
                        if all(key in metadata for key in ["bbox_x0", "bbox_y0", "bbox_x1", "bbox_y1"]):
                            result_data["position"] = {
                                "page_number": metadata.get("page_number"),
                                "bbox": {
                                    "x0": metadata["bbox_x0"],
                                    "y0": metadata["bbox_y0"],
                                    "x1": metadata["bbox_x1"],
                                    "y1": metadata["bbox_y1"]
                                },
                                "page_dimensions": {
                                    "width": metadata.get("page_width", 0),
                                    "height": metadata.get("page_height", 0)
                                }
                            }
                    
                    search_results.append(result_data)
            
            return search_results
            
        except Exception as e:
            logger.error(f"PDF document search failed: {e}")
            return []
    
    async def get_pdf_document_structure(
        self, 
        document_id: str
    ) -> Dict[str, Any]:
        """Get the structure of a PDF document"""
        try:
            collection = self.collections.get("pdf_documents")
            if not collection:
                return {}
            
            # Get all chunks for the document
            results = collection.get(
                where={"document_id": document_id},
                include=["metadatas"]
            )
            
            if not results["ids"]:
                return {}
            
            # Organize by pages
            pages_structure = {}
            total_blocks = 0
            
            for metadata in results["metadatas"]:
                page_num = metadata.get("page_number", 1)
                
                if page_num not in pages_structure:
                    pages_structure[page_num] = {
                        "page_number": page_num,
                        "blocks": [],
                        "page_width": metadata.get("page_width", 0),
                        "page_height": metadata.get("page_height", 0)
                    }
                
                block_info = {
                    "block_index": metadata.get("block_index"),
                    "confidence": metadata.get("confidence", 0),
                    "text_length": metadata.get("text_length", 0)
                }
                
                # Add position if available
                if "bbox_x0" in metadata:
                    block_info["position"] = {
                        "x0": metadata["bbox_x0"],
                        "y0": metadata["bbox_y0"],
                        "x1": metadata["bbox_x1"],
                        "y1": metadata["bbox_y1"]
                    }
                
                pages_structure[page_num]["blocks"].append(block_info)
                total_blocks += 1
            
            # Sort pages and blocks
            for page_data in pages_structure.values():
                page_data["blocks"].sort(key=lambda x: x.get("block_index", 0))
            
            return {
                "document_id": document_id,
                "total_pages": len(pages_structure),
                "total_blocks": total_blocks,
                "pages": dict(sorted(pages_structure.items()))
            }
            
        except Exception as e:
            logger.error(f"Failed to get PDF document structure: {e}")
            return {}
    
    def is_initialized(self) -> bool:
        """Check if the vector DB is properly initialized"""
        return (
            self.client is not None and 
            self.embedding_model is not None and 
            len(self.collections) > 0
        )