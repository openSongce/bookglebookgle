package com.ssafy.bookglebookgle.entity

// OCR 결과 개별 항목
data class OcrResult(
    val pageNumber: Int,
    val text: String,
    val rectX: Double,
    val rectY: Double,
    val rectW: Double,
    val rectH: Double
)

// OCR 전체 응답
data class OcrResponse(
    val groupId: Long,
    val pdfId: Long,
    val ocrResultlist: List<OcrResult>
)

// 텍스트 오버레이용 변환 클래스 (Float 유지)
data class OcrTextOverlay(
    val pageNumber: Int,
    val text: String,
    val rectX: Float,
    val rectY: Float,
    val rectW: Float,
    val rectH: Float,
    val isSelected: Boolean = false
)

// 확장 함수로 변환 기능 추가 - Double → Float 변환
fun OcrResponse.toTextOverlayList(): List<OcrTextOverlay> {
    return this.ocrResultlist.map { ocr ->
        OcrTextOverlay(
            pageNumber = ocr.pageNumber,
            text = ocr.text,
            rectX = ocr.rectX.toFloat(),    // Double → Float 변환
            rectY = ocr.rectY.toFloat(),    // Double → Float 변환
            rectW = ocr.rectW.toFloat(),    // Double → Float 변환
            rectH = ocr.rectH.toFloat()     // Double → Float 변환
        )
    }
}

/**
 * 한 줄로 병합된 OCR 결과
 */
data class OcrLine(
    val pageNumber: Int,
    val text: String,              // 한 줄의 모든 텍스트를 합친 문자열
    val rectX: Double,             // 줄의 시작 X 좌표
    val rectY: Double,             // 줄의 Y 좌표
    val rectW: Double,             // 줄의 전체 너비
    val rectH: Double,             // 줄의 높이
    val originalResults: List<OcrResult>  // 원본 OCR 결과들 (필요시 사용)
)

/**
 * OCR 결과를 줄 단위로 병합하는 확장 함수
 */
fun List<OcrResult>.mergeToLines(
    lineThreshold: Double = 0.01  // Y 좌표 차이가 이 값 이하면 같은 줄로 간주
): List<OcrLine> {
    if (isEmpty()) return emptyList()

    // 페이지별로 그룹화
    val groupedByPage = groupBy { it.pageNumber }
    val result = mutableListOf<OcrLine>()

    groupedByPage.forEach { (pageNumber, pageResults) ->
        // Y 좌표 기준으로 정렬 (위에서 아래로)
        val sortedByY = pageResults.sortedBy { it.rectY }

        // 줄 단위로 그룹화
        val lines = mutableListOf<MutableList<OcrResult>>()
        var currentLine = mutableListOf<OcrResult>()
        var lastY = sortedByY.first().rectY

        for (ocr in sortedByY) {
            // Y 좌표 차이가 임계값보다 크면 새로운 줄
            if (Math.abs(ocr.rectY - lastY) > lineThreshold) {
                if (currentLine.isNotEmpty()) {
                    lines.add(currentLine)
                    currentLine = mutableListOf()
                }
            }
            currentLine.add(ocr)
            lastY = ocr.rectY
        }

        // 마지막 줄 추가
        if (currentLine.isNotEmpty()) {
            lines.add(currentLine)
        }

        // 각 줄을 OcrLine으로 변환
        lines.forEach { lineResults ->
            // X 좌표 기준으로 정렬 (왼쪽에서 오른쪽으로)
            val sortedByX = lineResults.sortedBy { it.rectX }

            if (sortedByX.isNotEmpty()) {
                val mergedLine = mergeLineResults(pageNumber, sortedByX)
                result.add(mergedLine)
            }
        }
    }

    return result.sortedWith(compareBy({ it.pageNumber }, { it.rectY }))
}

/**
 * 같은 줄의 OCR 결과들을 하나로 병합
 */
private fun mergeLineResults(pageNumber: Int, lineResults: List<OcrResult>): OcrLine {
    val sortedResults = lineResults.sortedBy { it.rectX }

    // 텍스트 합치기 (공백 처리 포함)
    val mergedText = buildString {
        sortedResults.forEachIndexed { index, ocr ->
            if (index > 0) {
                val prevOcr = sortedResults[index - 1]
                val gap = ocr.rectX - (prevOcr.rectX + prevOcr.rectW)

                // 글자 간격이 일정 이상이면 공백 추가
                if (gap > 0.015) { // 경험적 임계값
                    append(" ")
                }
            }
            append(ocr.text)
        }
    }

    // 줄의 전체 영역 계산
    val firstResult = sortedResults.first()
    val lastResult = sortedResults.last()

    val lineX = firstResult.rectX
    val lineY = sortedResults.minOf { it.rectY }
    val lineEndX = lastResult.rectX + lastResult.rectW
    val lineW = lineEndX - lineX
    val lineH = sortedResults.maxOf { it.rectH }

    return OcrLine(
        pageNumber = pageNumber,
        text = mergedText,
        rectX = lineX,
        rectY = lineY,
        rectW = lineW,
        rectH = lineH,
        originalResults = sortedResults
    )
}

/**
 * OCR 결과를 더 정교하게 줄 단위로 병합
 */
fun List<OcrResult>.smartMergeToLines(): List<OcrLine> {
    if (isEmpty()) return emptyList()

    return groupBy { it.pageNumber }.flatMap { (pageNumber, pageResults) ->
        processPageResults(pageNumber, pageResults)
    }.sortedWith(compareBy({ it.pageNumber }, { it.rectY }))
}

private fun processPageResults(pageNumber: Int, results: List<OcrResult>): List<OcrLine> {
    // 1단계: Y 좌표 기준으로 클러스터링
    val yClusters = clusterByY(results)

    // 2단계: 각 Y 클러스터 내에서 X 좌표 기준으로 정렬 및 병합
    return yClusters.map { cluster ->
        val sortedByX = cluster.sortedBy { it.rectX }
        mergeLineResults(pageNumber, sortedByX)
    }
}

private fun clusterByY(results: List<OcrResult>): List<List<OcrResult>> {
    if (results.isEmpty()) return emptyList()

    val sortedByY = results.sortedBy { it.rectY }
    val clusters = mutableListOf<MutableList<OcrResult>>()
    var currentCluster = mutableListOf<OcrResult>()

    sortedByY.forEach { ocr ->
        if (currentCluster.isEmpty()) {
            currentCluster.add(ocr)
        } else {
            val avgY = currentCluster.map { it.rectY }.average()
            val avgHeight = currentCluster.map { it.rectH }.average()

            // 동적 임계값: 평균 높이의 50%
            val threshold = avgHeight * 0.5

            if (Math.abs(ocr.rectY - avgY) <= threshold) {
                currentCluster.add(ocr)
            } else {
                clusters.add(currentCluster)
                currentCluster = mutableListOf(ocr)
            }
        }
    }

    if (currentCluster.isNotEmpty()) {
        clusters.add(currentCluster)
    }

    return clusters
}

/**
 * 줄 단위 OCR 결과를 텍스트 오버레이로 변환
 */
fun List<OcrLine>.toTextOverlayList(): List<OcrTextOverlay> {
    return map { line ->
        OcrTextOverlay(
            pageNumber = line.pageNumber,
            text = line.text,
            rectX = line.rectX.toFloat(),
            rectY = line.rectY.toFloat(),
            rectW = line.rectW.toFloat(),
            rectH = line.rectH.toFloat(),
            isSelected = false
        )
    }
}

/**
 * 디버깅용: 줄 병합 결과 로깅
 */
fun List<OcrLine>.logMergedLines(tag: String = "OcrLineMerger") {
    forEachIndexed { index, line ->
        android.util.Log.d(tag, """
            줄 $index:
            - 페이지: ${line.pageNumber}
            - 텍스트: "${line.text}"
            - 좌표: (%.3f, %.3f, %.3f, %.3f)
            - 원본 개수: ${line.originalResults.size}개
        """.trimIndent().format(line.rectX, line.rectY, line.rectW, line.rectH))
    }
}

/**
 * 성능 최적화된 병합 (대량 OCR 결과용)
 */
fun List<OcrResult>.optimizedMergeToLines(): List<OcrLine> {
    if (isEmpty()) return emptyList()

    // 페이지별 병렬 처리
    return groupBy { it.pageNumber }
        .map { (pageNumber, pageResults) ->
            pageNumber to processPageResultsOptimized(pageNumber, pageResults)
        }
        .sortedBy { it.first }
        .flatMap { it.second }
}

private fun processPageResultsOptimized(pageNumber: Int, results: List<OcrResult>): List<OcrLine> {
    // KD-Tree나 Grid 기반 클러스터링을 위한 준비
    // 현재는 단순화된 버전으로 구현
    return results
        .sortedBy { it.rectY }
        .fold(mutableListOf<MutableList<OcrResult>>()) { acc, ocr ->
            val lastGroup = acc.lastOrNull()

            if (lastGroup == null ||
                Math.abs(ocr.rectY - lastGroup.map { it.rectY }.average()) > 0.01) {
                acc.add(mutableListOf(ocr))
            } else {
                lastGroup.add(ocr)
            }
            acc
        }
        .map { group ->
            mergeLineResults(pageNumber, group.sortedBy { it.rectX })
        }
}