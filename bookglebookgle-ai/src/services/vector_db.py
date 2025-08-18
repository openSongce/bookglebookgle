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
    
    async def get_bookclub_collection(self, meeting_id: str) -> chromadb.Collection:
        """
        Get or create book club specific collection
        독서 모임별 전용 컬렉션 반환 (docs/toron.md 요구사항)
        
        Args:
            meeting_id: 독서 모임 ID
            
        Returns:
            chromadb.Collection: 독서 모임 전용 컬렉션
        """
        try:
            collection_name = f"bookclub_{meeting_id}_documents"
            
            if collection_name not in self.collections:
                collection = self.client.get_or_create_collection(
                    name=collection_name,
                    metadata={
                        "hnsw:space": "cosine", 
                        "meeting_id": meeting_id,
                        "type": "bookclub_reading_material",
                        "created_at": asyncio.get_event_loop().time()
                    }
                )
                self.collections[collection_name] = collection
                logger.info(f"Created book club collection: {collection_name}")
            
            return self.collections[collection_name]
            
        except Exception as e:
            logger.error(f"Failed to get book club collection for {meeting_id}: {e}")
            raise

    async def process_bookclub_document(
        self, 
        meeting_id: str, 
        document_id: str, 
        text: str, 
        section: str = None,
        progress_range: Dict[str, int] = None,
        metadata: Dict[str, Any] = None
    ) -> List[str]:
        """
        모바일 앱 독서 모임용 문서 처리 - 진도율 기반 데이터 저장 포함
        
        Args:
            meeting_id: 독서 모임 ID
            document_id: 문서 ID
            text: 문서 텍스트
            section: 문서 섹션
            progress_range: 진도율 범위 (사용되지 않음 - 자동으로 50%, 100% 생성)
            metadata: 추가 메타데이터
            
        Returns:
            List[str]: 생성된 청크 ID 목록 (일반 청크 + 진도율 청크 포함)
        """
        try:
            logger.info(f"Processing book club document: {document_id} for meeting: {meeting_id}")
            
            # Get book club specific collection
            collection = await self.get_bookclub_collection(meeting_id)
            
            # 1. 일반 청크 생성
            chunks = await self._chunk_document(text)
            
            chunk_ids = []
            documents = []
            embeddings = []
            metadatas = []
            
            # Initialize embedding model if needed
            if self.embedding_model is None:
                await self._initialize_embedding_model()
            
            # 2. 일반 청크 처리
            for i, chunk in enumerate(chunks):
                chunk_id = f"{meeting_id}_{document_id}_{section or 'main'}_{i}"
                chunk_ids.append(chunk_id)
                documents.append(chunk)
                
                embedding = self.embedding_model.encode(chunk).tolist()
                embeddings.append(embedding)
                
                # Prepare metadata for regular chunks
                chunk_metadata = {
                    "document_id": document_id,
                    "meeting_id": meeting_id,
                    "section": section or "main",
                    "chunk_index": i,
                    "chunk_length": len(chunk),
                    "chunk_type": "regular",
                    "text_hash": hashlib.md5(chunk.encode()).hexdigest(),
                    "timestamp": asyncio.get_event_loop().time()
                }
                
                # Merge with additional metadata
                if metadata:
                    chunk_metadata.update(metadata)
                
                metadatas.append(chunk_metadata)
            
            # 3. 진도율 기반 청크 생성 (50%, 100%)
            total_pages = metadata.get("total_pages", 1) if metadata else 1
            
            # 50% 진도율 청크
            half_length = len(text) // 2
            half_text = text[:half_length]
            
            half_chunk_id = f"{meeting_id}_{document_id}_progress_50"
            chunk_ids.append(half_chunk_id)
            documents.append(half_text)
            
            half_embedding = self.embedding_model.encode(half_text).tolist()
            embeddings.append(half_embedding)
            
            half_metadata = {
                "document_id": document_id,
                "meeting_id": meeting_id,
                "chunk_type": "progress",
                "progress_percentage": 50,
                "text_length": len(half_text),
                "total_pages": total_pages,
                "pages_included": max(1, total_pages // 2),
                "timestamp": asyncio.get_event_loop().time()
            }
            if metadata:
                half_metadata.update({k: v for k, v in metadata.items() if k not in ["total_pages"]})
            metadatas.append(half_metadata)
            
            # 100% 진도율 청크 (전체 문서)
            full_chunk_id = f"{meeting_id}_{document_id}_progress_100"
            chunk_ids.append(full_chunk_id)
            documents.append(text)
            
            full_embedding = self.embedding_model.encode(text).tolist()
            embeddings.append(full_embedding)
            
            full_metadata = {
                "document_id": document_id,
                "meeting_id": meeting_id,
                "chunk_type": "progress",
                "progress_percentage": 100,
                "text_length": len(text),
                "total_pages": total_pages,
                "pages_included": total_pages,
                "timestamp": asyncio.get_event_loop().time()
            }
            if metadata:
                full_metadata.update({k: v for k, v in metadata.items() if k not in ["total_pages"]})
            metadatas.append(full_metadata)
            
            # Store in book club specific collection
            collection.upsert(
                ids=chunk_ids,
                documents=documents,
                embeddings=embeddings,
                metadatas=metadatas
            )
            
            logger.info(f"Stored {len(chunks)} chunks for book club document {document_id}")
            return chunk_ids
            
        except Exception as e:
            logger.error(f"Book club document processing failed: {e}")
            raise

    async def get_bookclub_context_for_discussion(
        self, 
        meeting_id: str,
        query: str, 
        max_chunks: int = 3
    ) -> List[str]:
        """
        Get relevant context chunks for book club discussion AI
        독서 모임별 토론 컨텍스트 검색 (토론 진행자 전용)
        
        NOTE: 토론 기능은 진도율 제한 없이 해당 문서의 모든 청크를 검색합니다.
        (regular chunks + progress_50 + progress_100 모두 포함)
        
        Args:
            meeting_id: 독서 모임 ID
            query: 검색 쿼리 (사용자 메시지)
            max_chunks: 최대 검색 결과 수
            
        Returns:
            List[str]: 관련 독서 자료 텍스트 청크들 (진도율 제한 없음)
        """
        try:
            # Get book club collection
            collection = await self.get_bookclub_collection(meeting_id)
            
            # Generate query embedding
            query_embedding = self.embedding_model.encode(query).tolist()
            
            # Search in book club collection - NO progress restriction for discussion
            # 토론은 문서 전체를 대상으로 함 (퀴즈와 달리 진도율 제한 없음)
            results = collection.query(
                query_embeddings=[query_embedding],
                n_results=max_chunks,
                # where 조건 없음 = 모든 청크 타입(regular, progress_50, progress_100) 검색
                include=["documents", "metadatas", "distances"]
            )
            
            # Extract document text sorted by similarity
            context_chunks = []
            if results["documents"] and results["documents"][0]:
                context_chunks = [doc for doc in results["documents"][0]]
            
            logger.info(f"Retrieved {len(context_chunks)} context chunks for book club discussion")
            return context_chunks
            
        except Exception as e:
            logger.error(f"Failed to get book club discussion context: {e}")
            return []

    # Removed store_progress_based_document - now handled in process_bookclub_document

    async def search_by_progress(
        self, 
        meeting_id: str, 
        document_id: str, 
        progress_percentage: int,
        max_chunks: int = 3
    ) -> List[str]:
        """
        진도율에 따른 문서 내용 반환 (퀴즈 생성용)
        
        Args:
            meeting_id: 독서 모임 ID
            document_id: 문서 ID  
            progress_percentage: 진도율 (50 또는 100)
            max_chunks: 최대 반환할 청크 수
            
        Returns:
            List[str]: 해당 진도율의 문서 내용 리스트
        """
        try:
            collection = await self.get_bookclub_collection(meeting_id)
            
            # 진도율에 따른 청크 검색
            progress_chunk_id = f"{meeting_id}_{document_id}_progress_{progress_percentage}"
            
            result = collection.get(
                ids=[progress_chunk_id],
                include=["documents", "metadatas"]
            )
            
            context_chunks = []
            if result["documents"] and result["documents"][0]:
                context_chunks.append(result["documents"][0])
                logger.info(f"📖 Retrieved {progress_percentage}% document content for quiz generation")
            else:
                logger.warning(f"No {progress_percentage}% document found for {document_id}, falling back to regular chunks")
                
                # Fallback: 일반 청크에서 검색
                fallback_result = collection.query(
                    query_texts=[f"document content for {document_id}"],
                    n_results=max_chunks,
                    where={
                        "document_id": document_id,
                        "meeting_id": meeting_id,
                        "chunk_type": "regular"
                    },
                    include=["documents"]
                )
                
                if fallback_result["documents"] and fallback_result["documents"][0]:
                    context_chunks.extend(fallback_result["documents"][0])
                    
            return context_chunks[:max_chunks]
                
        except Exception as e:
            logger.error(f"Failed to get progress-based context: {e}")
            return []

    async def search_by_progress_range(
        self,
        meeting_id: str,
        query: str,
        min_progress: int = 0,
        max_progress: int = 100,
        max_results: int = 3
    ) -> List[Dict[str, Any]]:
        """
        진도율 범위별 검색 (모바일 앱 토론 기능용)
        
        Args:
            meeting_id: 독서 모임 ID
            query: 검색 쿼리
            min_progress: 최소 진도율
            max_progress: 최대 진도율  
            max_results: 최대 결과 수
            
        Returns:
            List[Dict]: 검색 결과
        """
        try:
            collection = await self.get_bookclub_collection(meeting_id)
            query_embedding = self.embedding_model.encode(query).tolist()
            
            # 진도율 범위에 따른 필터 조건
            if max_progress <= 50:
                doc_types = ["half_document"]
            elif min_progress >= 100:
                doc_types = ["full_document"] 
            else:
                doc_types = ["half_document", "full_document"]
            
            all_results = []
            for doc_type in doc_types:
                results = collection.query(
                    query_embeddings=[query_embedding],
                    n_results=max_results,
                    where={"meeting_id": meeting_id, "type": doc_type},
                    include=["documents", "metadatas", "distances"]
                )
                
                if results["documents"] and results["documents"][0]:
                    for i in range(len(results["documents"][0])):
                        all_results.append({
                            "document": results["documents"][0][i],
                            "metadata": results["metadatas"][0][i],
                            "similarity": 1 - results["distances"][0][i],
                            "progress_target": results["metadatas"][0][i].get("progress_target", 0)
                        })
            
            # 유사도순 정렬 후 상위 결과 반환
            all_results.sort(key=lambda x: x["similarity"], reverse=True)
            return all_results[:max_results]
            
        except Exception as e:
            logger.error(f"Progress range search failed: {e}")
            return []



    async def process_document(self, document_id: str, text: str, metadata: Dict[str, Any] = None) -> List[str]:
        """Process document into chunks and store embeddings"""
        try:
            logger.info(f"Processing document: {document_id}")
            
            # Chunk the document
            chunks_with_positions = await self._chunk_document_with_positions(text)
            
            # Generate embeddings for chunks
            chunk_ids = []
            documents = []
            embeddings = []
            metadatas = []
            
            for i, chunk_data in enumerate(chunks_with_positions):
                chunk_text = chunk_data["text"]
                start_pos = chunk_data["start_position"]
                end_pos = chunk_data["end_position"]
                
                chunk_id = f"{document_id}_chunk_{i}"
                chunk_ids.append(chunk_id)
                documents.append(chunk_text)
                
                # Generate embedding
                if self.embedding_model is None:
                    await self._initialize_embedding_model()
                
                embedding = self.embedding_model.encode(chunk_text).tolist()
                embeddings.append(embedding)
                
                # Prepare metadata with position information
                chunk_metadata = {
                    "document_id": document_id,
                    "chunk_index": i,
                    "chunk_length": len(chunk_text),
                    "start_position": start_pos,
                    "end_position": end_pos,
                    "text_hash": hashlib.md5(chunk_text.encode()).hexdigest()
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
                
                logger.info(f"Stored {len(chunks_with_positions)} chunks for document {document_id}")
            
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

    async def _chunk_document_with_positions(self, text: str, chunk_size: int = 500, overlap: int = 50) -> List[Dict[str, Any]]:
        """Split document into overlapping chunks with position information"""
        chunks_with_positions = []
        words = text.split()
        
        if len(words) <= chunk_size:
            return [{
                "text": text,
                "start_position": 0,
                "end_position": len(text)
            }]
        
        start_word_idx = 0
        char_position = 0
        
        while start_word_idx < len(words):
            end_word_idx = min(start_word_idx + chunk_size, len(words))
            chunk_words = words[start_word_idx:end_word_idx]
            chunk_text = " ".join(chunk_words)
            
            # Calculate character positions
            start_char = char_position
            end_char = start_char + len(chunk_text)
            
            chunks_with_positions.append({
                "text": chunk_text,
                "start_position": start_char,
                "end_position": end_char
            })
            
            if end_word_idx >= len(words):
                break
                
            # Update position for next chunk (considering overlap)
            overlap_words = min(overlap, len(chunk_words))
            start_word_idx = end_word_idx - overlap_words
            
            # Recalculate character position for overlapping chunks
            if start_word_idx > 0:
                char_position = start_char + len(" ".join(words[start_word_idx - (end_word_idx - start_word_idx):start_word_idx])) + 1
        
        return chunks_with_positions
    
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
            # Get or create the collection to ensure it exists
            collection = self.client.get_or_create_collection("documents")
            self.collections["documents"] = collection # Update cache
            
            logger.info(f"Searching for document summary with ID: '{document_id}'")
            results = collection.get(
                where={"document_id": document_id},
                include=["documents"]
            )
            
            if results["documents"]:
                logger.info(f"Found {len(results['documents'])} chunks for document '{document_id}'.")
                # Combine first few chunks for summary
                chunks = results["documents"][:5]  # First 5 chunks
                combined_text = " ".join(chunks)
                
                # Truncate if too long
                if len(combined_text) > 2000:
                    combined_text = combined_text[:2000] + "..."
                
                return combined_text
            
            logger.warning(f"Could not find any document chunks for ID: '{document_id}'")
            return None
            
        except Exception as e:
            logger.error(f"Failed to get document summary: {e}")
            return None

    async def store_text_with_metadata(self, document_id: str, text: str, metadata: Dict[str, Any]) -> None:
        """Stores a single text with its metadata in the vector database."""
        try:
            collection = self.collections.get("documents")
            if not collection:
                logger.error("Collection 'documents' not found.")
                return

            embedding = self.embedding_model.encode(text).tolist()
            collection.upsert(
                ids=[document_id],
                documents=[text],
                embeddings=[embedding],
                metadatas=[metadata]
            )
            logger.info(f"Stored text with metadata for document ID: {document_id}")
        except Exception as e:
            logger.error(f"Failed to store text with metadata: {e}")

    async def store_document_with_positions(
        self, 
        document_id: str, 
        ocr_blocks: List[Dict[str, Any]],
        metadata: Dict[str, Any] = None
    ) -> bool:
        """위치 정보가 포함된 문서를 벡터 DB에 저장"""
        try:
            # 페이지별로 텍스트 그룹화
            pages = {}
            for block in ocr_blocks:
                page_num = block.page_number
                if page_num not in pages:
                    pages[page_num] = []
                pages[page_num].append(block)
            
            # 페이지별 임베딩 생성 및 저장
            for page_num, blocks in pages.items():
                page_text = " ".join([block.text for block in blocks])
                
                # 위치 정보를 메타데이터에 포함 (JSON 문자열로 변환)
                import json
                blocks_data = [
                    {
                        "text": block.text,
                        "x0": block.bbox.x0, "y0": block.bbox.y0,
                        "x1": block.bbox.x1, "y1": block.bbox.y1,
                        "confidence": block.confidence
                    } for block in blocks
                ]
                
                page_metadata = {
                    "document_id": document_id,
                    "page_number": page_num,
                    "blocks_count": len(blocks_data),
                    "blocks_json": json.dumps(blocks_data, ensure_ascii=False),
                    **(metadata or {})
                }
                
                # 임베딩 생성 및 저장
                await self.store_text_with_metadata(
                    document_id=f"{document_id}_page_{page_num}",
                    text=page_text,
                    metadata=page_metadata
                )
            
            return True
        except Exception as e:
            logger.error(f"Failed to store document with positions: {e}")
            return False
    
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
    
    async def delete_meeting_collection(self, meeting_id: str) -> bool:
        """독서 모임별 컬렉션 삭제"""
        try:
            collection_name = f"bookclub_{meeting_id}_documents"
            
            logger.info(f"Deleting collection: {collection_name}")
            
            # ChromaDB에서 컬렉션 삭제
            try:
                self.client.delete_collection(name=collection_name)
                logger.info(f"Successfully deleted collection from ChromaDB: {collection_name}")
            except Exception as e:
                # 컬렉션이 존재하지 않는 경우는 경고로만 처리
                if "does not exist" in str(e).lower():
                    logger.warning(f"Collection {collection_name} does not exist, skipping deletion")
                else:
                    raise e
            
            # 메모리 캐시에서도 제거
            if collection_name in self.collections:
                del self.collections[collection_name]
                logger.info(f"Removed collection from memory cache: {collection_name}")
            
            logger.info(f"Successfully deleted meeting collection: {collection_name}")
            return True
            
        except Exception as e:
            logger.error(f"Failed to delete collection for meeting {meeting_id}: {e}")
            return False
    
    async def get_collection_info(self, meeting_id: str) -> Dict[str, Any]:
        """컬렉션 정보 조회 (삭제 전 메타데이터 수집용)"""
        try:
            collection_name = f"bookclub_{meeting_id}_documents"
            
            # 컬렉션이 존재하는지 확인
            try:
                collection = await self.get_bookclub_collection(meeting_id)
                
                # 컬렉션 통계 정보 수집
                count = collection.count()
                
                return {
                    "collection_name": collection_name,
                    "document_count": count,
                    "meeting_id": meeting_id,
                    "exists": True
                }
            except Exception as e:
                logger.warning(f"Collection {collection_name} not found: {e}")
                return {
                    "collection_name": collection_name,
                    "meeting_id": meeting_id,
                    "exists": False,
                    "document_count": 0,
                    "error": str(e)
                }
                
        except Exception as e:
            logger.error(f"Failed to get collection info for meeting {meeting_id}: {e}")
            return {
                "collection_name": f"bookclub_{meeting_id}_documents",
                "meeting_id": meeting_id,
                "exists": False,
                "document_count": 0,
                "error": str(e)
            }
    
    async def list_meeting_collections(self) -> List[str]:
        """독서 모임 관련 컬렉션 목록 조회"""
        try:
            all_collections = self.client.list_collections()
            meeting_collections = [
                col.name for col in all_collections 
                if col.name.startswith("bookclub_") and col.name.endswith("_documents")
            ]
            logger.info(f"Found {len(meeting_collections)} meeting collections")
            return meeting_collections
        except Exception as e:
            logger.error(f"Failed to list meeting collections: {e}")
            return []
    
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

    async def get_page_content(self, document_id: str, page_number: int) -> Optional[str]:
        """Get the text content of a specific page."""
        try:
            collection = self.collections.get("documents")
            if not collection:
                return None
            results = collection.get(
                where={
                    "document_id": document_id,
                    "page_number": page_number
                },
                include=["documents"]
            )
            if results["documents"]:
                return " ".join(results["documents"])
            return None
        except Exception as e:
            logger.error(f"Failed to get page content: {e}")
            return None

    async def get_section_content(self, document_id: str, section: Dict[str, float]) -> Optional[str]:
        """Get the text content of a specific section (bbox)."""
        # This is a simplified implementation. A real implementation would need
        # to query blocks based on their bounding box coordinates.
        return await self.get_document_summary(document_id)
    
    def is_initialized(self) -> bool:
        """Check if the vector DB is properly initialized"""
        return (
            self.client is not None and 
            self.embedding_model is not None and 
            len(self.collections) > 0
        )