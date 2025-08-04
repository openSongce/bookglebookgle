"""
위치 정보 매핑 유틸리티
LLM 후처리 후에도 원본 OCR의 위치 정보를 정확히 보존하는 기능 제공
"""

import re
from typing import List, Dict, Tuple, Optional
from difflib import SequenceMatcher
from loguru import logger

from src.models.ocr_models import OCRBlock, ProcessedOCRBlock, BoundingBox, TextCorrection


class PositionMapper:
    """위치 정보 보존 및 매핑 클래스"""
    
    @staticmethod
    def preserve_positions(
        original_blocks: List[OCRBlock],
        corrected_texts: List[str]
    ) -> List[OCRBlock]:
        """
        교정된 텍스트에 원본 위치 정보를 매핑
        
        Args:
            original_blocks: 원본 OCR 블록 리스트
            corrected_texts: LLM으로 교정된 텍스트 리스트
            
        Returns:
            위치 정보가 보존된 교정 블록 리스트
        """
        if len(original_blocks) != len(corrected_texts):
            logger.warning(
                f"Block count mismatch: {len(original_blocks)} original vs {len(corrected_texts)} corrected"
            )
            # 길이가 다른 경우 최소 길이로 맞춤
            min_length = min(len(original_blocks), len(corrected_texts))
            original_blocks = original_blocks[:min_length]
            corrected_texts = corrected_texts[:min_length]
        
        preserved_blocks = []
        
        for original_block, corrected_text in zip(original_blocks, corrected_texts):
            try:
                preserved_block = PositionMapper._map_single_block(original_block, corrected_text)
                preserved_blocks.append(preserved_block)
            except Exception as e:
                logger.error(f"Failed to map position for block: {e}")
                # 실패 시 원본 블록 유지
                preserved_blocks.append(original_block)
        
        return preserved_blocks
    
    @staticmethod
    def _map_single_block(original_block: OCRBlock, corrected_text: str) -> OCRBlock:
        """단일 블록의 위치 정보 매핑"""
        # 텍스트가 동일하면 원본 그대로 반환
        if original_block.text.strip() == corrected_text.strip():
            return original_block
        
        # 교정된 텍스트로 새 블록 생성 (위치 정보는 원본 유지)
        return OCRBlock(
            text=corrected_text,
            page_number=original_block.page_number,
            bbox=original_block.bbox,  # 원본 위치 정보 보존
            confidence=original_block.confidence,
            block_type=original_block.block_type
        )
    
    @staticmethod
    def handle_text_changes(
        original_text: str,
        corrected_text: str,
        bbox: BoundingBox
    ) -> List[BoundingBox]:
        """
        텍스트 변경 시 위치 정보 조정
        
        Args:
            original_text: 원본 텍스트
            corrected_text: 교정된 텍스트
            bbox: 원본 바운딩 박스
            
        Returns:
            조정된 바운딩 박스 리스트
        """
        # 텍스트 길이 비율 계산
        original_length = len(original_text.strip())
        corrected_length = len(corrected_text.strip())
        
        if original_length == 0:
            return [bbox]
        
        # 길이 비율에 따른 위치 조정
        length_ratio = corrected_length / original_length
        
        # 텍스트가 크게 변경되지 않은 경우 원본 위치 유지
        if 0.8 <= length_ratio <= 1.2:
            return [bbox]
        
        # 텍스트가 크게 줄어든 경우 (50% 이하)
        if length_ratio < 0.5:
            new_width = bbox.width * length_ratio
            return [BoundingBox(
                x0=bbox.x0,
                y0=bbox.y0,
                x1=bbox.x0 + new_width,
                y1=bbox.y1
            )]
        
        # 텍스트가 크게 늘어난 경우 (200% 이상)
        if length_ratio > 2.0:
            # 원본 박스를 확장
            new_width = bbox.width * min(length_ratio, 3.0)  # 최대 3배까지만 확장
            return [BoundingBox(
                x0=bbox.x0,
                y0=bbox.y0,
                x1=bbox.x0 + new_width,
                y1=bbox.y1
            )]
        
        # 기본적으로 원본 위치 유지
        return [bbox]
    
    @staticmethod
    def create_processed_blocks(
        original_blocks: List[OCRBlock],
        corrected_texts: List[str],
        corrections_list: List[List[TextCorrection]],
        processing_confidences: List[float],
        llm_model: str = "gemini-2.0-flash",
        processing_times: Optional[List[float]] = None
    ) -> List[ProcessedOCRBlock]:
        """
        원본 블록들로부터 ProcessedOCRBlock 리스트 생성
        
        Args:
            original_blocks: 원본 OCR 블록 리스트
            corrected_texts: 교정된 텍스트 리스트
            corrections_list: 각 블록별 교정 내역 리스트
            processing_confidences: 각 블록별 처리 신뢰도 리스트
            llm_model: 사용된 LLM 모델명
            processing_times: 각 블록별 처리 시간 리스트 (선택사항)
            
        Returns:
            ProcessedOCRBlock 리스트
        """
        if not all(len(lst) == len(original_blocks) for lst in [
            corrected_texts, corrections_list, processing_confidences
        ]):
            raise ValueError("All input lists must have the same length as original_blocks")
        
        processed_blocks = []
        
        for i, (original_block, corrected_text, corrections, proc_confidence) in enumerate(
            zip(original_blocks, corrected_texts, corrections_list, processing_confidences)
        ):
            processing_time = processing_times[i] if processing_times else None
            
            processed_block = ProcessedOCRBlock(
                text=corrected_text,
                page_number=original_block.page_number,
                bbox=original_block.bbox,  # 원본 위치 정보 보존
                confidence=original_block.confidence,
                block_type=original_block.block_type,
                original_text=original_block.text,
                corrections=corrections,
                processing_confidence=proc_confidence,
                llm_model=llm_model,
                processing_time=processing_time
            )
            
            processed_blocks.append(processed_block)
        
        return processed_blocks
    
    @staticmethod
    def analyze_text_changes(original_text: str, corrected_text: str) -> Dict[str, any]:
        """
        원본과 교정된 텍스트 간의 변경사항 분석
        
        Args:
            original_text: 원본 텍스트
            corrected_text: 교정된 텍스트
            
        Returns:
            변경사항 분석 결과
        """
        # 기본 통계
        original_length = len(original_text)
        corrected_length = len(corrected_text)
        length_change = corrected_length - original_length
        length_ratio = corrected_length / original_length if original_length > 0 else 0
        
        # 유사도 계산
        similarity = SequenceMatcher(None, original_text, corrected_text).ratio()
        
        # 단어 수 변화
        original_words = len(original_text.split())
        corrected_words = len(corrected_text.split())
        word_change = corrected_words - original_words
        
        # 변경 유형 분석
        change_types = []
        
        if length_change != 0:
            change_types.append("length_change")
        
        if original_words != corrected_words:
            change_types.append("word_count_change")
        
        # 띄어쓰기 변경 감지
        if original_text.replace(" ", "") == corrected_text.replace(" ", ""):
            change_types.append("spacing_only")
        
        # 대소문자 변경 감지
        if original_text.lower() == corrected_text.lower():
            change_types.append("case_change")
        
        return {
            "original_length": original_length,
            "corrected_length": corrected_length,
            "length_change": length_change,
            "length_ratio": length_ratio,
            "similarity": similarity,
            "original_words": original_words,
            "corrected_words": corrected_words,
            "word_change": word_change,
            "change_types": change_types,
            "is_significant_change": similarity < 0.8 or abs(length_ratio - 1.0) > 0.3
        }
    
    @staticmethod
    def split_block_by_corrections(
        original_block: OCRBlock,
        corrections: List[TextCorrection]
    ) -> List[OCRBlock]:
        """
        교정 내역에 따라 블록을 분할 (필요한 경우)
        
        Args:
            original_block: 원본 OCR 블록
            corrections: 교정 내역 리스트
            
        Returns:
            분할된 블록 리스트 (분할이 불필요하면 원본 블록 반환)
        """
        # 위치 정보가 있는 교정만 처리
        positioned_corrections = [
            c for c in corrections 
            if c.start_position is not None and c.end_position is not None
        ]
        
        if not positioned_corrections:
            return [original_block]
        
        # 위치별로 정렬
        positioned_corrections.sort(key=lambda c: c.start_position)
        
        # 겹치는 교정이 있는지 확인
        for i in range(len(positioned_corrections) - 1):
            if positioned_corrections[i].end_position > positioned_corrections[i + 1].start_position:
                logger.warning("Overlapping corrections detected, returning original block")
                return [original_block]
        
        blocks = []
        current_pos = 0
        text = original_block.text
        
        for correction in positioned_corrections:
            # 교정 이전 부분
            if current_pos < correction.start_position:
                before_text = text[current_pos:correction.start_position]
                if before_text.strip():
                    before_bbox = PositionMapper._calculate_partial_bbox(
                        original_block.bbox,
                        current_pos,
                        correction.start_position,
                        len(text)
                    )
                    blocks.append(OCRBlock(
                        text=before_text,
                        page_number=original_block.page_number,
                        bbox=before_bbox,
                        confidence=original_block.confidence,
                        block_type=original_block.block_type
                    ))
            
            # 교정된 부분
            corrected_bbox = PositionMapper._calculate_partial_bbox(
                original_block.bbox,
                correction.start_position,
                correction.end_position,
                len(text)
            )
            blocks.append(OCRBlock(
                text=correction.corrected,
                page_number=original_block.page_number,
                bbox=corrected_bbox,
                confidence=original_block.confidence,
                block_type=original_block.block_type
            ))
            
            current_pos = correction.end_position
        
        # 마지막 부분
        if current_pos < len(text):
            after_text = text[current_pos:]
            if after_text.strip():
                after_bbox = PositionMapper._calculate_partial_bbox(
                    original_block.bbox,
                    current_pos,
                    len(text),
                    len(text)
                )
                blocks.append(OCRBlock(
                    text=after_text,
                    page_number=original_block.page_number,
                    bbox=after_bbox,
                    confidence=original_block.confidence,
                    block_type=original_block.block_type
                ))
        
        return blocks if blocks else [original_block]
    
    @staticmethod
    def _calculate_partial_bbox(
        original_bbox: BoundingBox,
        start_pos: int,
        end_pos: int,
        total_length: int
    ) -> BoundingBox:
        """텍스트 위치에 따른 부분 바운딩 박스 계산"""
        if total_length == 0:
            return original_bbox
        
        # 비율 계산
        start_ratio = start_pos / total_length
        end_ratio = end_pos / total_length
        
        # X 좌표 계산 (가로 방향 분할)
        width = original_bbox.width
        x0 = original_bbox.x0 + (width * start_ratio)
        x1 = original_bbox.x0 + (width * end_ratio)
        
        return BoundingBox(
            x0=x0,
            y0=original_bbox.y0,
            x1=x1,
            y1=original_bbox.y1
        )
    
    @staticmethod
    def validate_position_mapping(
        original_blocks: List[OCRBlock],
        processed_blocks: List[ProcessedOCRBlock]
    ) -> Dict[str, any]:
        """
        위치 매핑의 정확성 검증
        
        Args:
            original_blocks: 원본 OCR 블록 리스트
            processed_blocks: 처리된 OCR 블록 리스트
            
        Returns:
            검증 결과
        """
        if len(original_blocks) != len(processed_blocks):
            return {
                "valid": False,
                "error": "Block count mismatch",
                "original_count": len(original_blocks),
                "processed_count": len(processed_blocks)
            }
        
        position_matches = 0
        page_matches = 0
        confidence_preserved = 0
        
        for orig, proc in zip(original_blocks, processed_blocks):
            # 페이지 번호 확인
            if orig.page_number == proc.page_number:
                page_matches += 1
            
            # 위치 정보 확인 (바운딩 박스)
            if (orig.bbox.x0 == proc.bbox.x0 and 
                orig.bbox.y0 == proc.bbox.y0 and
                orig.bbox.x1 == proc.bbox.x1 and 
                orig.bbox.y1 == proc.bbox.y1):
                position_matches += 1
            
            # 신뢰도 보존 확인
            if orig.confidence == proc.confidence:
                confidence_preserved += 1
        
        total_blocks = len(original_blocks)
        
        return {
            "valid": position_matches == total_blocks and page_matches == total_blocks,
            "position_accuracy": position_matches / total_blocks,
            "page_accuracy": page_matches / total_blocks,
            "confidence_preservation": confidence_preserved / total_blocks,
            "total_blocks": total_blocks,
            "position_matches": position_matches,
            "page_matches": page_matches,
            "confidence_preserved": confidence_preserved
        }