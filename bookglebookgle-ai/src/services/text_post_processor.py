"""
Gemini 2.0 Flash 기반 텍스트 후처리 서비스
OCR 결과 텍스트의 품질을 LLM을 통해 향상시키는 서비스
"""

import asyncio
import time
import json
import re
from typing import List, Dict, Any, Optional, Tuple
from dataclasses import asdict

from loguru import logger

from src.models.ocr_models import (
    OCRBlock, ProcessedOCRBlock, TextCorrection, CorrectionType
)
from src.services.llm_client import LLMClient, LLMProvider
from src.utils.position_mapper import PositionMapper


class TextPostProcessor:
    """Gemini 2.0 Flash 기반 텍스트 후처리 클래스"""
    
    def __init__(self, llm_client: LLMClient):
        """
        텍스트 후처리기 초기화
        
        Args:
            llm_client: LLM 클라이언트 인스턴스
        """
        self.llm_client = llm_client
        self.model = "gemini-2.0-flash"
        self.batch_size = 100  # 배치 처리 크기 (API 호출 감소를 위해 10 -> 100으로 상향)
        self.max_retries = 3  # 최대 재시도 횟수
        
        # Gemini 2.0 Flash 전용 시스템 프롬프트
        self.system_prompt = """당신은 OCR 텍스트 교정 전문가입니다. 
다음 규칙에 따라 텍스트를 교정해주세요:

📋 교정 규칙:
1. 맞춤법과 띄어쓰기 오류를 정확히 수정
2. 문법적으로 자연스럽게 개선
3. 원본 텍스트의 의미는 절대 변경하지 말 것
4. 원본 텍스트 길이와 비슷하게 유지 (±20% 이내)
5. 한국어와 영어 혼재 문서 처리 가능
6. 숫자, 기호, 특수문자는 신중하게 처리

📤 응답 형식:
반드시 다음 JSON 형식으로 응답해주세요:
{
  "corrected_text": "교정된 텍스트",
  "corrections": [
    {
      "original": "원본 단어/구문",
      "corrected": "교정된 단어/구문", 
      "type": "spelling|grammar|spacing|style|punctuation",
      "confidence": 0.95,
      "explanation": "교정 이유"
    }
  ],
  "confidence": 0.92
}

⚠️ 주의사항:
- 교정이 불필요한 경우 corrections 배열을 빈 배열로 반환
- confidence는 0.0~1.0 사이의 값
- 응답은 반드시 유효한 JSON 형식이어야 함"""
        
        logger.info(f"🤖 TextPostProcessor initialized:")
        logger.info(f"   🎯 Model: {self.model}")
        logger.info(f"   📦 Batch size: {self.batch_size}")
        logger.info(f"   🔄 Max retries: {self.max_retries}")
    
    async def process_text_blocks(
        self, 
        ocr_blocks: List[OCRBlock],
        enable_batch_processing: bool = True
    ) -> List[ProcessedOCRBlock]:
        """
        OCR 블록들을 배치로 후처리
        
        Args:
            ocr_blocks: 원본 OCR 블록 리스트
            enable_batch_processing: 배치 처리 활성화 여부
            
        Returns:
            ProcessedOCRBlock 리스트
        """
        if not ocr_blocks:
            logger.warning("⚠️ No OCR blocks to process")
            return []
        
        start_time = time.time()
        logger.info(f"🚀 Starting text post-processing for {len(ocr_blocks)} blocks")
        
        try:
            if enable_batch_processing and len(ocr_blocks) > 1:
                processed_blocks = await self._process_blocks_in_batches(ocr_blocks)
            else:
                processed_blocks = await self._process_blocks_individually(ocr_blocks)
            
            total_time = time.time() - start_time
            
            # 처리 통계 로깅
            total_corrections = sum(len(block.corrections) for block in processed_blocks)
            avg_confidence = sum(block.processing_confidence for block in processed_blocks) / len(processed_blocks)
            
            logger.info(f"✅ Text post-processing completed:")
            logger.info(f"   ⏱️ Total time: {total_time:.2f}s")
            logger.info(f"   📊 Blocks processed: {len(processed_blocks)}")
            logger.info(f"   🔧 Total corrections: {total_corrections}")
            logger.info(f"   🎯 Average confidence: {avg_confidence:.3f}")
            logger.info(f"   ⚡ Speed: {len(processed_blocks)/total_time:.1f} blocks/sec")
            
            return processed_blocks
            
        except Exception as e:
            logger.error(f"❌ Text post-processing failed: {e}")
            # 실패 시 원본 블록들을 ProcessedOCRBlock으로 변환하여 반환
            return self._create_fallback_processed_blocks(ocr_blocks, str(e))
    
    async def _process_blocks_in_batches(self, ocr_blocks: List[OCRBlock]) -> List[ProcessedOCRBlock]:
        """
        최적화된 배치 단위 블록 처리
        동적 배치 크기 조정 및 병렬 처리 지원
        """
        processed_blocks = []
        
        # 동적 배치 크기 결정
        optimal_batch_size = self._calculate_optimal_batch_size(ocr_blocks)
        
        # 블록들을 최적화된 배치로 분할
        batches = self._create_smart_batches(ocr_blocks, optimal_batch_size)
        
        logger.info(f"📦 Processing {len(batches)} batches (optimal size: {optimal_batch_size})")
        
        # 배치별 처리 통계
        batch_stats = {
            'total_batches': len(batches),
            'successful_batches': 0,
            'failed_batches': 0,
            'total_processing_time': 0,
            'total_corrections': 0
        }
        
        for batch_idx, batch in enumerate(batches):
            try:
                batch_start_time = time.time()
                
                # 배치 전처리 (텍스트 품질 확인)
                filtered_batch = self._preprocess_batch(batch)
                
                if not filtered_batch:
                    logger.warning(f"⚠️ Batch {batch_idx + 1} is empty after preprocessing")
                    continue
                
                # 배치 텍스트 추출
                batch_texts = [block.text for block in filtered_batch]
                
                # LLM으로 배치 교정 (재시도 로직 포함)
                correction_results = await self._correct_text_batch_with_retry(
                    batch_texts, batch_idx + 1
                )
                
                # ProcessedOCRBlock 생성
                batch_processed = self._create_processed_blocks_from_results(
                    filtered_batch, correction_results
                )
                
                processed_blocks.extend(batch_processed)
                
                # 통계 업데이트
                batch_time = time.time() - batch_start_time
                batch_corrections = sum(len(block.corrections) for block in batch_processed)
                
                batch_stats['successful_batches'] += 1
                batch_stats['total_processing_time'] += batch_time
                batch_stats['total_corrections'] += batch_corrections
                
                logger.info(f"✅ Batch {batch_idx + 1}/{len(batches)} completed:")
                logger.info(f"   ⏱️ Time: {batch_time:.2f}s")
                logger.info(f"   🔧 Corrections: {batch_corrections}")
                logger.info(f"   📊 Blocks: {len(batch_processed)}")
                
            except Exception as e:
                logger.error(f"❌ Batch {batch_idx + 1} processing failed: {e}")
                batch_stats['failed_batches'] += 1
                
                # 실패한 배치는 fallback 처리
                fallback_blocks = self._create_fallback_processed_blocks(batch, str(e))
                processed_blocks.extend(fallback_blocks)
        
        # 최종 통계 로깅
        self._log_batch_processing_stats(batch_stats)
        
        return processed_blocks
    
    def _calculate_optimal_batch_size(self, ocr_blocks: List[OCRBlock]) -> int:
        """
        OCR 블록 특성에 따른 최적 배치 크기 계산
        
        Args:
            ocr_blocks: OCR 블록 리스트
            
        Returns:
            최적 배치 크기
        """
        total_blocks = len(ocr_blocks)
        avg_text_length = sum(len(block.text) for block in ocr_blocks) / total_blocks
        
        # 텍스트 길이에 따른 배치 크기 조정 (더 공격적으로 설정)
        if avg_text_length < 50:  # 짧은 텍스트
            optimal_size = min(100, total_blocks)
        elif avg_text_length < 150:  # 보통 텍스트
            optimal_size = min(50, total_blocks)
        elif avg_text_length < 300:  # 긴 텍스트
            optimal_size = min(25, total_blocks)
        else:  # 매우 긴 텍스트
            optimal_size = min(10, total_blocks)
        
        # 최소/최대 제한
        optimal_size = max(1, min(optimal_size, self.batch_size))
        
        logger.debug(f"📊 Batch size optimization:")
        logger.debug(f"   📝 Avg text length: {avg_text_length:.1f}")
        logger.debug(f"   📦 Optimal batch size: {optimal_size}")
        
        return optimal_size
    
    def _create_smart_batches(
        self, 
        ocr_blocks: List[OCRBlock], 
        batch_size: int
    ) -> List[List[OCRBlock]]:
        """
        스마트 배치 생성 (유사한 특성의 블록들을 그룹화)
        
        Args:
            ocr_blocks: OCR 블록 리스트
            batch_size: 배치 크기
            
        Returns:
            최적화된 배치 리스트
        """
        # 블록들을 특성별로 정렬 (페이지 번호, 텍스트 길이 순)
        sorted_blocks = sorted(
            ocr_blocks, 
            key=lambda b: (b.page_number, len(b.text), b.confidence)
        )
        
        # 기본 배치 분할
        batches = [
            sorted_blocks[i:i + batch_size] 
            for i in range(0, len(sorted_blocks), batch_size)
        ]
        
        return batches
    
    def _preprocess_batch(self, batch: List[OCRBlock]) -> List[OCRBlock]:
        """
        배치 전처리 (품질이 낮은 블록 필터링)
        
        Args:
            batch: 원본 배치
            
        Returns:
            필터링된 배치
        """
        filtered_batch = []
        
        for block in batch:
            # 최소 텍스트 길이 확인
            if len(block.text.strip()) < 2:
                logger.debug(f"⚠️ Skipping block with too short text: '{block.text}'")
                continue
            
            # 신뢰도 확인
            if block.confidence < 0.1:
                logger.debug(f"⚠️ Skipping block with low confidence: {block.confidence}")
                continue
            
            # 특수 문자만 있는 블록 제외
            if re.match(r'^[^\w\s가-힣]+$', block.text.strip()):
                logger.debug(f"⚠️ Skipping block with only special characters: '{block.text}'")
                continue
            
            filtered_batch.append(block)
        
        logger.debug(f"📋 Batch preprocessing: {len(batch)} → {len(filtered_batch)} blocks")
        return filtered_batch
    
    async def _correct_text_batch_with_retry(
        self, 
        text_batch: List[str], 
        batch_number: int
    ) -> List[Dict[str, Any]]:
        """
        재시도 로직이 강화된 배치 텍스트 교정
        
        Args:
            text_batch: 교정할 텍스트 리스트
            batch_number: 배치 번호 (로깅용)
            
        Returns:
            교정 결과 리스트
        """
        last_error = None
        
        for attempt in range(self.max_retries):
            try:
                # 배치 크기에 따른 타임아웃 조정
                timeout_seconds = 30 + (len(text_batch) * 2)
                
                # 비동기 타임아웃과 함께 교정 수행
                correction_task = self._correct_text_batch(text_batch)
                correction_results = await asyncio.wait_for(
                    correction_task, 
                    timeout=timeout_seconds
                )
                
                if correction_results and len(correction_results) == len(text_batch):
                    logger.debug(f"✅ Batch {batch_number} correction successful (attempt {attempt + 1})")
                    return correction_results
                else:
                    logger.warning(f"⚠️ Batch {batch_number} incomplete results (attempt {attempt + 1})")
                    last_error = "Incomplete correction results"
                
            except asyncio.TimeoutError:
                last_error = f"Timeout after {timeout_seconds}s"
                logger.warning(f"⏰ Batch {batch_number} timeout (attempt {attempt + 1})")
                
            except Exception as e:
                last_error = str(e)
                logger.warning(f"⚠️ Batch {batch_number} correction failed (attempt {attempt + 1}): {e}")
            
            # 재시도 전 대기 (지수 백오프)
            if attempt < self.max_retries - 1:
                wait_time = 0.5 * (2 ** attempt)  # 0.5s, 1s, 2s
                await asyncio.sleep(wait_time)
        
        logger.error(f"❌ Batch {batch_number} failed after {self.max_retries} attempts: {last_error}")
        return self._create_fallback_correction_results(text_batch)
    
    def _log_batch_processing_stats(self, stats: Dict[str, Any]):
        """배치 처리 통계 로깅"""
        total_batches = stats['total_batches']
        successful_batches = stats['successful_batches']
        failed_batches = stats['failed_batches']
        total_time = stats['total_processing_time']
        total_corrections = stats['total_corrections']
        
        success_rate = (successful_batches / total_batches * 100) if total_batches > 0 else 0
        avg_time_per_batch = (total_time / successful_batches) if successful_batches > 0 else 0
        
        logger.info(f"📊 Batch processing statistics:")
        logger.info(f"   ✅ Success rate: {success_rate:.1f}% ({successful_batches}/{total_batches})")
        logger.info(f"   ❌ Failed batches: {failed_batches}")
        logger.info(f"   ⏱️ Avg time per batch: {avg_time_per_batch:.2f}s")
        logger.info(f"   🔧 Total corrections: {total_corrections}")
        logger.info(f"   ⚡ Processing speed: {successful_batches/total_time:.1f} batches/sec")
    
    async def _process_blocks_individually(self, ocr_blocks: List[OCRBlock]) -> List[ProcessedOCRBlock]:
        """개별 블록 단위로 처리"""
        processed_blocks = []
        
        logger.info(f"🔄 Processing blocks individually")
        
        for idx, block in enumerate(ocr_blocks):
            try:
                block_start_time = time.time()
                
                # 단일 텍스트 교정
                correction_results = await self._correct_text_batch([block.text])
                
                # ProcessedOCRBlock 생성
                if correction_results:
                    processed_block = self._create_processed_blocks_from_results(
                        [block], correction_results
                    )[0]
                else:
                    processed_block = self._create_fallback_processed_blocks([block], "No correction result")[0]
                
                processed_blocks.append(processed_block)
                
                block_time = time.time() - block_start_time
                logger.debug(f"✅ Block {idx + 1}/{len(ocr_blocks)} completed in {block_time:.3f}s")
                
            except Exception as e:
                logger.error(f"❌ Block {idx + 1} processing failed: {e}")
                fallback_block = self._create_fallback_processed_blocks([block], str(e))[0]
                processed_blocks.append(fallback_block)
        
        return processed_blocks
    
    async def _correct_text_batch(self, text_batch: List[str]) -> List[Dict[str, Any]]:
        """
        텍스트 배치를 Gemini 2.0 Flash로 교정
        
        Args:
            text_batch: 교정할 텍스트 리스트
            
        Returns:
            교정 결과 리스트
        """
        if not text_batch:
            return []
        
        # 배치 프롬프트 생성
        batch_prompt = self._create_batch_prompt(text_batch)
        
        # LLM 호출 (재시도 로직 포함)
        for attempt in range(self.max_retries):
            try:
                response = await self._call_gemini_flash(
                    prompt=batch_prompt,
                    max_tokens=2000,  # 배치 처리를 위해 토큰 수 증가
                    temperature=0.2   # 일관성을 위해 낮은 temperature
                )
                
                # 응답 파싱
                correction_results = self._parse_correction_response(response, len(text_batch))
                
                if correction_results:
                    logger.debug(f"✅ Batch correction successful (attempt {attempt + 1})")
                    return correction_results
                else:
                    logger.warning(f"⚠️ Empty correction results (attempt {attempt + 1})")
                
            except Exception as e:
                logger.warning(f"⚠️ Correction attempt {attempt + 1} failed: {e}")
                if attempt == self.max_retries - 1:
                    logger.error(f"❌ All correction attempts failed")
                    return self._create_fallback_correction_results(text_batch)
                
                # 재시도 전 잠시 대기
                await asyncio.sleep(0.5 * (attempt + 1))
        
        return self._create_fallback_correction_results(text_batch)
    
    def _create_batch_prompt(self, text_batch: List[str]) -> str:
        """배치 처리를 위한 프롬프트 생성"""
        if len(text_batch) == 1:
            return f"""다음 OCR 텍스트를 교정해주세요:

텍스트: "{text_batch[0]}"

위에서 설명한 JSON 형식으로 응답해주세요."""
        
        # 다중 텍스트 배치 처리
        numbered_texts = []
        for i, text in enumerate(text_batch, 1):
            numbered_texts.append(f"{i}. \"{text}\"")
        
        texts_str = "\n".join(numbered_texts)
        
        return f"""다음 {len(text_batch)}개의 OCR 텍스트를 각각 교정해주세요:

{texts_str}

각 텍스트에 대해 별도의 JSON 객체로 응답하되, 전체를 JSON 배열로 감싸주세요:
[
  {{ "corrected_text": "...", "corrections": [...], "confidence": 0.95 }},
  {{ "corrected_text": "...", "corrections": [...], "confidence": 0.92 }},
  ...
]"""
    
    async def _call_gemini_flash(
        self,
        prompt: str,
        max_tokens: int = 1000,
        temperature: float = 0.2
    ) -> str:
        """
        Gemini 2.0 Flash 모델 호출
        
        Args:
            prompt: 입력 프롬프트
            max_tokens: 최대 토큰 수
            temperature: 생성 온도
            
        Returns:
            LLM 응답 텍스트
        """
        try:
            response = await self.llm_client.generate_completion(
                prompt=prompt,
                system_message=self.system_prompt,
                max_tokens=max_tokens,
                temperature=temperature,
                provider=LLMProvider.GEMINI,
                model=self.model
            )
            
            return response.strip()
            
        except Exception as e:
            logger.error(f"❌ Gemini Flash API call failed: {e}")
            raise
    
    def _parse_correction_response(self, response: str, expected_count: int) -> List[Dict[str, Any]]:
        """
        LLM 응답을 파싱하여 교정 결과 추출
        
        Args:
            response: LLM 응답 텍스트
            expected_count: 예상되는 결과 개수
            
        Returns:
            교정 결과 리스트
        """
        try:
            # JSON 응답 파싱 시도
            if response.startswith('[') and response.endswith(']'):
                # 배열 형태 응답
                results = json.loads(response)
                if isinstance(results, list) and len(results) == expected_count:
                    return self._validate_correction_results(results)
            else:
                # 단일 객체 응답
                result = json.loads(response)
                if isinstance(result, dict) and expected_count == 1:
                    return self._validate_correction_results([result])
            
            logger.warning(f"⚠️ Unexpected response format or count mismatch")
            return []
            
        except json.JSONDecodeError as e:
            logger.warning(f"⚠️ JSON parsing failed: {e}")
            # JSON 파싱 실패 시 텍스트에서 추출 시도
            return self._extract_from_text_response(response, expected_count)
        
        except Exception as e:
            logger.error(f"❌ Response parsing failed: {e}")
            return []
    
    def _validate_correction_results(self, results: List[Dict[str, Any]]) -> List[Dict[str, Any]]:
        """교정 결과 유효성 검증"""
        validated_results = []
        
        for result in results:
            try:
                # 필수 필드 확인
                if not all(key in result for key in ['corrected_text', 'corrections', 'confidence']):
                    logger.warning(f"⚠️ Missing required fields in correction result")
                    continue
                
                # 신뢰도 범위 확인
                confidence = float(result['confidence'])
                if not 0.0 <= confidence <= 1.0:
                    logger.warning(f"⚠️ Invalid confidence value: {confidence}")
                    result['confidence'] = max(0.0, min(1.0, confidence))
                
                # corrections 배열 검증
                corrections = result['corrections']
                if not isinstance(corrections, list):
                    logger.warning(f"⚠️ Invalid corrections format")
                    result['corrections'] = []
                
                validated_results.append(result)
                
            except Exception as e:
                logger.warning(f"⚠️ Result validation failed: {e}")
                continue
        
        return validated_results
    
    def _extract_from_text_response(self, response: str, expected_count: int) -> List[Dict[str, Any]]:
        """텍스트 응답에서 교정 정보 추출 (fallback)"""
        logger.info("🔄 Attempting to extract correction info from text response")
        
        # 간단한 패턴 매칭으로 교정된 텍스트 추출
        results = []
        
        # "교정된 텍스트:" 또는 "corrected:" 패턴 찾기
        corrected_patterns = [
            r'교정된?\s*텍스트\s*[:：]\s*["\']?([^"\'\n]+)["\']?',
            r'corrected[_\s]*text\s*[:：]\s*["\']?([^"\'\n]+)["\']?',
            r'수정된?\s*텍스트\s*[:：]\s*["\']?([^"\'\n]+)["\']?'
        ]
        
        for pattern in corrected_patterns:
            matches = re.findall(pattern, response, re.IGNORECASE | re.MULTILINE)
            if matches:
                for match in matches[:expected_count]:
                    results.append({
                        'corrected_text': match.strip(),
                        'corrections': [],
                        'confidence': 0.7  # 기본 신뢰도
                    })
                break
        
        return results[:expected_count]
    
    def _create_fallback_correction_results(self, text_batch: List[str]) -> List[Dict[str, Any]]:
        """교정 실패 시 fallback 결과 생성"""
        return [
            {
                'corrected_text': text,
                'corrections': [],
                'confidence': 0.5  # 낮은 신뢰도로 설정
            }
            for text in text_batch
        ]
    
    def _create_processed_blocks_from_results(
        self,
        original_blocks: List[OCRBlock],
        correction_results: List[Dict[str, Any]]
    ) -> List[ProcessedOCRBlock]:
        """교정 결과로부터 ProcessedOCRBlock 생성"""
        processed_blocks = []
        
        for block, result in zip(original_blocks, correction_results):
            try:
                # TextCorrection 객체들 생성
                corrections = []
                for corr_data in result.get('corrections', []):
                    try:
                        correction = TextCorrection(
                            original=corr_data.get('original', ''),
                            corrected=corr_data.get('corrected', ''),
                            correction_type=CorrectionType(corr_data.get('type', 'grammar')),
                            confidence=float(corr_data.get('confidence', 0.8)),
                            explanation=corr_data.get('explanation', '')
                        )
                        corrections.append(correction)
                    except Exception as e:
                        logger.warning(f"⚠️ Failed to create TextCorrection: {e}")
                        continue
                
                # ProcessedOCRBlock 생성
                processed_block = ProcessedOCRBlock(
                    text=result['corrected_text'],
                    page_number=block.page_number,
                    bbox=block.bbox,  # 원본 위치 정보 보존
                    confidence=block.confidence,
                    block_type=block.block_type,
                    original_text=block.text,
                    corrections=corrections,
                    processing_confidence=float(result['confidence']),
                    llm_model=self.model
                )
                
                processed_blocks.append(processed_block)
                
            except Exception as e:
                logger.error(f"❌ Failed to create ProcessedOCRBlock: {e}")
                # 실패 시 fallback 블록 생성
                fallback_block = self._create_fallback_processed_blocks([block], str(e))[0]
                processed_blocks.append(fallback_block)
        
        return processed_blocks
    
    def _create_fallback_processed_blocks(
        self, 
        original_blocks: List[OCRBlock], 
        error_message: str
    ) -> List[ProcessedOCRBlock]:
        """처리 실패 시 fallback ProcessedOCRBlock 생성"""
        fallback_blocks = []
        
        for block in original_blocks:
            fallback_block = ProcessedOCRBlock(
                text=block.text,  # 원본 텍스트 유지
                page_number=block.page_number,
                bbox=block.bbox,
                confidence=block.confidence,
                block_type=block.block_type,
                original_text=block.text,
                corrections=[],  # 교정 없음
                processing_confidence=0.0,  # 처리 실패 표시
                llm_model=f"{self.model} (failed: {error_message[:50]})"
            )
            fallback_blocks.append(fallback_block)
        
        return fallback_blocks
    
    def get_processor_info(self) -> Dict[str, Any]:
        """후처리기 정보 반환"""
        return {
            "processor_name": "TextPostProcessor",
            "llm_model": self.model,
            "batch_size": self.batch_size,
            "max_retries": self.max_retries,
            "system_prompt_length": len(self.system_prompt),
            "llm_client_available": self.llm_client is not None
        }  
  
    def analyze_correction_quality(
        self, 
        processed_blocks: List[ProcessedOCRBlock]
    ) -> Dict[str, Any]:
        """
        교정 품질 분석 및 평가
        
        Args:
            processed_blocks: 처리된 OCR 블록 리스트
            
        Returns:
            품질 분석 결과
        """
        if not processed_blocks:
            return {"error": "No processed blocks to analyze"}
        
        # 기본 통계
        total_blocks = len(processed_blocks)
        blocks_with_corrections = sum(1 for block in processed_blocks if block.has_corrections)
        total_corrections = sum(block.correction_count for block in processed_blocks)
        
        # 교정 유형별 통계
        correction_type_stats = {}
        confidence_scores = []
        processing_confidences = []
        text_length_changes = []
        
        for block in processed_blocks:
            processing_confidences.append(block.processing_confidence)
            
            # 텍스트 길이 변화 분석
            original_length = len(block.original_text)
            corrected_length = len(block.text)
            if original_length > 0:
                length_change_ratio = corrected_length / original_length
                text_length_changes.append(length_change_ratio)
            
            # 교정 유형별 분석
            for correction in block.corrections:
                correction_type = correction.correction_type.value
                if correction_type not in correction_type_stats:
                    correction_type_stats[correction_type] = {
                        'count': 0,
                        'total_confidence': 0.0,
                        'examples': []
                    }
                
                correction_type_stats[correction_type]['count'] += 1
                correction_type_stats[correction_type]['total_confidence'] += correction.confidence
                confidence_scores.append(correction.confidence)
                
                # 예시 수집 (최대 3개)
                if len(correction_type_stats[correction_type]['examples']) < 3:
                    correction_type_stats[correction_type]['examples'].append({
                        'original': correction.original,
                        'corrected': correction.corrected,
                        'confidence': correction.confidence
                    })
        
        # 평균 계산
        avg_processing_confidence = sum(processing_confidences) / len(processing_confidences)
        avg_correction_confidence = sum(confidence_scores) / len(confidence_scores) if confidence_scores else 0.0
        avg_length_change = sum(text_length_changes) / len(text_length_changes) if text_length_changes else 1.0
        
        # 교정 유형별 평균 신뢰도 계산
        for type_name, stats in correction_type_stats.items():
            if stats['count'] > 0:
                stats['avg_confidence'] = stats['total_confidence'] / stats['count']
            else:
                stats['avg_confidence'] = 0.0
        
        # 품질 점수 계산 (0-100)
        quality_score = self._calculate_quality_score(
            avg_processing_confidence,
            avg_correction_confidence,
            blocks_with_corrections / total_blocks,
            avg_length_change
        )
        
        return {
            "overall_stats": {
                "total_blocks": total_blocks,
                "blocks_with_corrections": blocks_with_corrections,
                "correction_rate": blocks_with_corrections / total_blocks,
                "total_corrections": total_corrections,
                "avg_corrections_per_block": total_corrections / total_blocks,
                "quality_score": quality_score
            },
            "confidence_analysis": {
                "avg_processing_confidence": avg_processing_confidence,
                "avg_correction_confidence": avg_correction_confidence,
                "processing_confidence_range": {
                    "min": min(processing_confidences),
                    "max": max(processing_confidences)
                },
                "correction_confidence_range": {
                    "min": min(confidence_scores) if confidence_scores else 0.0,
                    "max": max(confidence_scores) if confidence_scores else 0.0
                }
            },
            "text_change_analysis": {
                "avg_length_change_ratio": avg_length_change,
                "length_preserved_blocks": sum(1 for ratio in text_length_changes if 0.9 <= ratio <= 1.1),
                "significantly_changed_blocks": sum(1 for ratio in text_length_changes if ratio < 0.8 or ratio > 1.2)
            },
            "correction_type_breakdown": correction_type_stats
        }
    
    def _calculate_quality_score(
        self,
        processing_confidence: float,
        correction_confidence: float,
        correction_rate: float,
        length_change_ratio: float
    ) -> float:
        """
        종합 품질 점수 계산
        
        Args:
            processing_confidence: 평균 처리 신뢰도
            correction_confidence: 평균 교정 신뢰도
            correction_rate: 교정 비율
            length_change_ratio: 평균 길이 변화 비율
            
        Returns:
            품질 점수 (0-100)
        """
        # 가중치 설정
        weights = {
            'processing_confidence': 0.4,
            'correction_confidence': 0.3,
            'correction_rate': 0.2,
            'length_preservation': 0.1
        }
        
        # 길이 보존 점수 (1.0에 가까울수록 높은 점수)
        length_preservation_score = 1.0 - abs(length_change_ratio - 1.0)
        length_preservation_score = max(0.0, min(1.0, length_preservation_score))
        
        # 교정 비율 점수 (적절한 교정 비율: 0.2-0.8)
        if 0.2 <= correction_rate <= 0.8:
            correction_rate_score = 1.0
        elif correction_rate < 0.2:
            correction_rate_score = correction_rate / 0.2
        else:  # correction_rate > 0.8
            correction_rate_score = max(0.0, 1.0 - (correction_rate - 0.8) / 0.2)
        
        # 종합 점수 계산
        quality_score = (
            processing_confidence * weights['processing_confidence'] +
            correction_confidence * weights['correction_confidence'] +
            correction_rate_score * weights['correction_rate'] +
            length_preservation_score * weights['length_preservation']
        ) * 100
        
        return round(quality_score, 2)
    
    def generate_correction_report(
        self, 
        processed_blocks: List[ProcessedOCRBlock]
    ) -> str:
        """
        교정 결과 리포트 생성
        
        Args:
            processed_blocks: 처리된 OCR 블록 리스트
            
        Returns:
            텍스트 형태의 리포트
        """
        quality_analysis = self.analyze_correction_quality(processed_blocks)
        
        if "error" in quality_analysis:
            return f"리포트 생성 실패: {quality_analysis['error']}"
        
        overall = quality_analysis["overall_stats"]
        confidence = quality_analysis["confidence_analysis"]
        text_change = quality_analysis["text_change_analysis"]
        correction_types = quality_analysis["correction_type_breakdown"]
        
        report_lines = [
            "=" * 60,
            "📊 OCR 텍스트 후처리 결과 리포트",
            "=" * 60,
            "",
            "📈 전체 통계:",
            f"  • 총 블록 수: {overall['total_blocks']:,}개",
            f"  • 교정된 블록 수: {overall['blocks_with_corrections']:,}개 ({overall['correction_rate']:.1%})",
            f"  • 총 교정 수: {overall['total_corrections']:,}개",
            f"  • 블록당 평균 교정 수: {overall['avg_corrections_per_block']:.2f}개",
            f"  • 종합 품질 점수: {overall['quality_score']:.1f}/100",
            "",
            "🎯 신뢰도 분석:",
            f"  • 평균 처리 신뢰도: {confidence['avg_processing_confidence']:.3f}",
            f"  • 평균 교정 신뢰도: {confidence['avg_correction_confidence']:.3f}",
            f"  • 처리 신뢰도 범위: {confidence['processing_confidence_range']['min']:.3f} ~ {confidence['processing_confidence_range']['max']:.3f}",
            "",
            "📝 텍스트 변화 분석:",
            f"  • 평균 길이 변화 비율: {text_change['avg_length_change_ratio']:.3f}",
            f"  • 길이 보존된 블록: {text_change['length_preserved_blocks']:,}개",
            f"  • 크게 변경된 블록: {text_change['significantly_changed_blocks']:,}개",
            "",
            "🔧 교정 유형별 분석:"
        ]
        
        # 교정 유형별 상세 정보
        for correction_type, stats in correction_types.items():
            type_name_kr = {
                'spelling': '맞춤법',
                'grammar': '문법',
                'spacing': '띄어쓰기',
                'style': '문체',
                'punctuation': '구두점'
            }.get(correction_type, correction_type)
            
            report_lines.extend([
                f"  • {type_name_kr} 교정:",
                f"    - 횟수: {stats['count']:,}회",
                f"    - 평균 신뢰도: {stats['avg_confidence']:.3f}",
            ])
            
            if stats['examples']:
                report_lines.append("    - 예시:")
                for example in stats['examples']:
                    report_lines.append(f"      '{example['original']}' → '{example['corrected']}' (신뢰도: {example['confidence']:.3f})")
            
            report_lines.append("")
        
        # 품질 평가 및 권장사항
        report_lines.extend([
            "💡 품질 평가 및 권장사항:",
            self._generate_quality_recommendations(quality_analysis),
            "",
            "=" * 60
        ])
        
        return "\n".join(report_lines)
    
    def _generate_quality_recommendations(self, quality_analysis: Dict[str, Any]) -> str:
        """품질 분석 결과를 바탕으로 권장사항 생성"""
        overall = quality_analysis["overall_stats"]
        confidence = quality_analysis["confidence_analysis"]
        text_change = quality_analysis["text_change_analysis"]
        
        recommendations = []
        
        # 품질 점수 기반 평가
        quality_score = overall["quality_score"]
        if quality_score >= 90:
            recommendations.append("✅ 매우 우수한 교정 품질입니다.")
        elif quality_score >= 80:
            recommendations.append("✅ 양호한 교정 품질입니다.")
        elif quality_score >= 70:
            recommendations.append("⚠️ 보통 수준의 교정 품질입니다. 일부 개선이 필요합니다.")
        else:
            recommendations.append("❌ 교정 품질이 낮습니다. 설정 조정이 필요합니다.")
        
        # 신뢰도 기반 권장사항
        if confidence["avg_processing_confidence"] < 0.7:
            recommendations.append("⚠️ 처리 신뢰도가 낮습니다. LLM 모델 또는 프롬프트 조정을 고려하세요.")
        
        if confidence["avg_correction_confidence"] < 0.8:
            recommendations.append("⚠️ 교정 신뢰도가 낮습니다. 더 보수적인 교정 설정을 고려하세요.")
        
        # 교정 비율 기반 권장사항
        correction_rate = overall["correction_rate"]
        if correction_rate > 0.9:
            recommendations.append("⚠️ 교정 비율이 매우 높습니다. OCR 품질 또는 교정 임계값을 확인하세요.")
        elif correction_rate < 0.1:
            recommendations.append("⚠️ 교정 비율이 매우 낮습니다. 교정 민감도를 높이는 것을 고려하세요.")
        
        # 텍스트 변화 기반 권장사항
        avg_length_change = text_change["avg_length_change_ratio"]
        if avg_length_change > 1.3:
            recommendations.append("⚠️ 텍스트가 크게 늘어나고 있습니다. 과도한 교정을 확인하세요.")
        elif avg_length_change < 0.7:
            recommendations.append("⚠️ 텍스트가 크게 줄어들고 있습니다. 중요한 내용이 손실되지 않았는지 확인하세요.")
        
        return "\n  ".join(recommendations) if recommendations else "  특별한 권장사항이 없습니다."
    
    def track_correction_patterns(
        self, 
        processed_blocks: List[ProcessedOCRBlock]
    ) -> Dict[str, Any]:
        """
        교정 패턴 추적 및 분석
        
        Args:
            processed_blocks: 처리된 OCR 블록 리스트
            
        Returns:
            교정 패턴 분석 결과
        """
        patterns = {
            'common_corrections': {},  # 자주 발생하는 교정
            'error_patterns': {},      # 자주 발생하는 오류 패턴
            'confidence_patterns': {}, # 신뢰도별 패턴
            'page_patterns': {}        # 페이지별 패턴
        }
        
        for block in processed_blocks:
            page_num = block.page_number
            
            # 페이지별 패턴 초기화
            if page_num not in patterns['page_patterns']:
                patterns['page_patterns'][page_num] = {
                    'total_blocks': 0,
                    'corrected_blocks': 0,
                    'total_corrections': 0,
                    'avg_confidence': 0.0
                }
            
            page_stats = patterns['page_patterns'][page_num]
            page_stats['total_blocks'] += 1
            
            if block.has_corrections:
                page_stats['corrected_blocks'] += 1
                page_stats['total_corrections'] += len(block.corrections)
            
            page_stats['avg_confidence'] += block.processing_confidence
            
            # 교정별 패턴 분석
            for correction in block.corrections:
                # 공통 교정 패턴
                correction_key = f"{correction.original} → {correction.corrected}"
                if correction_key not in patterns['common_corrections']:
                    patterns['common_corrections'][correction_key] = {
                        'count': 0,
                        'type': correction.correction_type.value,
                        'avg_confidence': 0.0,
                        'total_confidence': 0.0
                    }
                
                common_corr = patterns['common_corrections'][correction_key]
                common_corr['count'] += 1
                common_corr['total_confidence'] += correction.confidence
                common_corr['avg_confidence'] = common_corr['total_confidence'] / common_corr['count']
                
                # 오류 패턴 (원본 텍스트 기준)
                error_pattern = correction.original.lower().strip()
                if len(error_pattern) > 1:  # 단일 문자 제외
                    if error_pattern not in patterns['error_patterns']:
                        patterns['error_patterns'][error_pattern] = {
                            'count': 0,
                            'corrections': set(),
                            'types': set()
                        }
                    
                    error_pat = patterns['error_patterns'][error_pattern]
                    error_pat['count'] += 1
                    error_pat['corrections'].add(correction.corrected)
                    error_pat['types'].add(correction.correction_type.value)
                
                # 신뢰도별 패턴
                confidence_range = self._get_confidence_range(correction.confidence)
                if confidence_range not in patterns['confidence_patterns']:
                    patterns['confidence_patterns'][confidence_range] = {
                        'count': 0,
                        'types': {}
                    }
                
                conf_pat = patterns['confidence_patterns'][confidence_range]
                conf_pat['count'] += 1
                
                corr_type = correction.correction_type.value
                if corr_type not in conf_pat['types']:
                    conf_pat['types'][corr_type] = 0
                conf_pat['types'][corr_type] += 1
        
        # 페이지별 평균 신뢰도 계산
        for page_num, page_stats in patterns['page_patterns'].items():
            if page_stats['total_blocks'] > 0:
                page_stats['avg_confidence'] /= page_stats['total_blocks']
        
        # 결과 정리 (set을 list로 변환)
        for error_pattern in patterns['error_patterns'].values():
            error_pattern['corrections'] = list(error_pattern['corrections'])
            error_pattern['types'] = list(error_pattern['types'])
        
        return patterns
    
    def _get_confidence_range(self, confidence: float) -> str:
        """신뢰도를 범위로 분류"""
        if confidence >= 0.9:
            return "very_high"
        elif confidence >= 0.8:
            return "high"
        elif confidence >= 0.7:
            return "medium"
        elif confidence >= 0.6:
            return "low"
        else:
            return "very_low"