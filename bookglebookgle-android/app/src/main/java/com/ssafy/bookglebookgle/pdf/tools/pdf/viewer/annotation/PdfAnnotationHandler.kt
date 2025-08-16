package com.ssafy.bookglebookgle.pdf.tools.pdf.viewer.annotation

import android.content.Context
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PointF
import android.graphics.RectF
import android.util.TypedValue
import androidx.core.content.res.ResourcesCompat
import com.ssafy.bookglebookgle.R
import com.ssafy.bookglebookgle.pdf.tools.pdf.viewer.model.CommentModel
import com.ssafy.bookglebookgle.pdf.tools.pdf.viewer.model.HighlightModel
import com.ssafy.bookglebookgle.pdf.tools.pdf.viewer.model.PdfAnnotationModel
import com.ssafy.bookglebookgle.pdf.tools.pdf.viewer.util.zoom

/**
 * Canvas 위에 주석 그리기
 * 특정 좌표 클릭했을 때 주석 탐색 및 반응
 * 주석 삭제 처리
 * UI 배지(스탬프) 그리기 등
 * */
class PdfAnnotationHandler(
    context: Context?,
    resource: Resources,
) {
    var annotations = arrayListOf<PdfAnnotationModel>() // 현재 화면에 표시할 모든 주석 데이터
    private var noteColor = Color.parseColor("#AF926A") // 댓글 색상

    private var noteStampBitmap: Bitmap = BitmapFactory.decodeResource(resource, R.drawable.ic_comment) // 댓글 이미지
    private var stampWidth = 15f
    private var stampHeight = 15f
    private var addedNoteStampDetails = HashMap<Int, List<AddedStampDetails>>() // 페이지별로 도장이 표시될 위치 + 관련 댓글 ID 리스트

    /**
     * 댓글 아이콘 크기를 dp -> px로 변환
     * */
    init {
        stampWidth = getDpValue(resource, 15f).toFloat()
        stampHeight = stampWidth
    }

    private val outerCirclePaint = Paint().apply {
        color = Color.WHITE
        style = Paint.Style.FILL_AND_STROKE
    }
    private val innerCirclePaint = Paint().apply {
        color = Color.parseColor("#FF0000")
        style = Paint.Style.FILL_AND_STROKE
    }

    val textSize = getDpValue(resource, 8f)
    val textPaint = Paint().apply {
        color = Color.WHITE
        typeface = context?.let { ResourcesCompat.getFont(it, R.font.jakarta_sans_regular_400) }
    }

    /**
     * PDF 한 페이지 위에 주석(하이라이트, 댓글)을 그리는 메서드
     * */
    fun drawAnnotations(
        paginationPageIndexes: List<Int>, // 현재 보여지는 페이지의 실제 PDF 페이지 번호 목록
        pageOffsets: List<Float>,   // 각 페이지가 현재 캔버스 상에서 시작되는 Y 오프셋 위치
        canvas: Canvas, // 현재 캔버스
        zoom: Float // 현재 줌 레벨
    ) {
        addedNoteStampDetails.clear()

        for (annotation in annotations) {
            val index = paginationPageIndexes.indexOfFirst { it == annotation.paginationPageIndex }
            if (index == -1) continue
            val pageOffset = pageOffsets[index]
            canvas.translate(0f, pageOffset * zoom)
            try {
                when (annotation.type) {
                    PdfAnnotationModel.Type.Note -> drawNoteAnnotations(canvas, zoom, annotation.asNote()!!, index)
                    PdfAnnotationModel.Type.Highlight -> drawHighlights(canvas, zoom, annotation.asHighlight()!!)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            canvas.translate(0f, -pageOffset * zoom)
        }

        // 댓글 수에 맞게 댓글 그리기
        for (i in paginationPageIndexes.indices) {
            val pageOffset = pageOffsets[i]
            canvas.translate(0f, pageOffset * zoom)
            val noteStamps = addedNoteStampDetails[i] ?: emptyList()
            noteStamps.forEach { stampDetail ->
                val count = stampDetail.noteIds.size
                drawNoteStamp(canvas, stampDetail.x, stampDetail.y, zoom, count)
            }
            canvas.translate(0f, -pageOffset * zoom)
        }
    }

    /**
     * 댓글 주석을 그리는 메서드
     * 댓글이 달린 텍스트의 각 줄(segment)에 빨간색 밑줄 그리기
     * 첫 줄의 Y 좌표를 기준으로 해당 위치에 도장을 찍기 위해 위치를 기록
     * */
    private fun drawNoteAnnotations(canvas: Canvas, zoom: Float, note: CommentModel, mainPageIndex: Int) {
        val paint = Paint().apply {
            color = noteColor
            style = Paint.Style.STROKE
            strokeWidth = 2f * zoom
        }
        val selectionDetail = note.charDrawSegments
        selectionDetail.forEachIndexed { index, data ->
            val rect = data.rect.zoom(zoom)

            // zoom 미적용 rect: 실제 위치 계산용
            val originalRect = data.rect

            if (index == 0) {
                val midX = (originalRect.left + originalRect.right) / 2
                val topY = originalRect.top - stampHeight

                val centerX = midX
                val centerY = topY + stampHeight / 2

                // 스탬프 위치 기억
                addNoteStampDetails(centerX, centerY, mainPageIndex, note.id.toInt())

                val destRect = RectF(
                    centerX - stampWidth / 2,
                    topY,
                    centerX + stampWidth / 2,
                    topY + stampHeight
                ).zoom(zoom)

                canvas.drawBitmap(noteStampBitmap, null, destRect, null)
            }

            // 기존 밑줄
            canvas.drawLine(rect.left, rect.bottom, rect.right, rect.bottom, paint)
        }
    }

    /**
     * 댓글 스탬프 위치 관리 - 같은 위치의 댓글들을 그룹화
     */
    private fun addNoteStampDetails(x: Float, y: Float, page: Int, noteId: Int) {
        val alreadyAddedYs = ArrayList(addedNoteStampDetails[page] ?: emptyList())

        // 거리 임계값 설정 (스탬프 크기의 절반 정도)
        val threshold = stampWidth / 2f

        val nearbyStamp = alreadyAddedYs.find { stamp ->
            val dx = stamp.x - x
            val dy = stamp.y - y
            val distance = Math.hypot(dx.toDouble(), dy.toDouble())
            distance <= threshold
        }

        if (nearbyStamp == null) {
            // 새로운 스탬프 위치 추가
            val stampDetails = AddedStampDetails(x, y).also { it.noteIds.add(noteId) }
            alreadyAddedYs.add(stampDetails)
            addedNoteStampDetails[page] = alreadyAddedYs
        } else {
            // 기존 스탬프에 댓글 ID 추가
            if (!nearbyStamp.noteIds.contains(noteId)) {
                nearbyStamp.noteIds.add(noteId)
            }
        }
    }

    /**
     ** 댓글이 있는 위치에 도장 아이콘을 그림
     ** 댓글이 2개 이상인 경우, 도장 위에 댓글 개수 뱃지(원 + 숫자)를 추가
     * */
    private fun drawNoteStamp(canvas: Canvas, x: Float, y: Float, zoom: Float, count: Int) {
        val my = y - stampHeight / 2
        val destRect = RectF(x - stampWidth / 2, my, x + stampWidth / 2, my + stampHeight).zoom(zoom)
        canvas.drawBitmap(noteStampBitmap, null, destRect, Paint())

        if (count > 1) {
            val circleRadius = stampWidth * 0.30f * zoom
            val cX = destRect.right - circleRadius / 2
            val cY = destRect.top + circleRadius / 2
            canvas.drawCircle(cX, cY, circleRadius, outerCirclePaint)
            canvas.drawCircle(cX, cY, circleRadius * 0.80f, innerCirclePaint)

            textPaint.textSize = textSize * zoom * 0.6f
            val textWidth = textPaint.measureText(count.toString())
            val fontMetrics = textPaint.fontMetrics
            val textHeight = fontMetrics.descent - fontMetrics.ascent
            canvas.drawText(count.toString(), cX - (textWidth / 2), cY + (textHeight * 0.25f), textPaint)
        }
    }

    /**
     * 하이라이트 주석을 반투명 배경 색상으로 칠함
     * */
    private fun drawHighlights(canvas: Canvas, zoom: Float, highlight: HighlightModel) {
        val paint = Paint().apply {
            color = Color.parseColor(highlight.color)
            style = Paint.Style.FILL
            alpha = 50
        }
        val selectionDetail = highlight.charDrawSegments
        selectionDetail.forEach { data ->
            canvas.drawRoundRect(data.rect.zoom(zoom), 2f, 2f, paint) // 라운딩 처리된 박스 표시
        }
    }

    /**
     * 특정 좌표에 위치한 주석(하이라이트/댓글)을 찾는 메서드
     * */
    fun findAnnotationOnPoint(paginationPageIndex: Int, point: PointF): PdfAnnotationModel? {
        for (annotation in annotations) {
            if (annotation.paginationPageIndex != paginationPageIndex) continue
            annotation.charDrawSegments.forEach {
                if (it.rect.contains(point.x, point.y)) {
                    return annotation
                }
            }
        }
        return null
    }

    /**
     * 특정 좌표에 하이라이트가 있는지 탐색
     * */
    fun findHighlightOnPoint(point: PointF): HighlightModel? {
        for (annotation in annotations) {
            if (annotation.type != PdfAnnotationModel.Type.Highlight) { continue }
            annotation.charDrawSegments.forEach {
                if (it.rect.contains(point.x, point.y)) {
                    return annotation.asHighlight()
                }
            }
        }
        return null
    }

    /**
     * 특정 좌표에 댓글이 있는지 탐색
     * */
    fun findNoteOnPoint(point: PointF): CommentModel? {
        for (annotation in annotations) {
            if (annotation.type != PdfAnnotationModel.Type.Note) { continue }
            annotation.charDrawSegments.forEach {
                if (it.rect.contains(point.x, point.y)) {
                    return annotation.asNote()
                }
            }
        }
        return null
    }

    /**
     * 도장을 클릭했는지 판단 후 관련 댓글 리스트 반환
     * rawTouchPoint는 이미 PDF 좌표계로 변환된 상태
     * */
    fun findNoteStampOnPoint(
        rawTouchPoint: PointF,
        paginationPageIndex: Int,
    ): List<CommentModel> {
        val resultNotes = ArrayList<CommentModel>()

        // 현재 페이지의 스탬프들을 확인
        val stampDetails = addedNoteStampDetails[paginationPageIndex] ?: emptyList()

        for (stampDetail in stampDetails) {
            // PDF 좌표계에서 직접 비교
            val stampCenterX = stampDetail.x
            val stampCenterY = stampDetail.y - stampHeight / 2f

            // PDF 좌표계에서 스탬프 영역 계산 (zoom 적용 안함)
            val stampRect = RectF(
                stampCenterX - stampWidth / 2,
                stampCenterY,
                stampCenterX + stampWidth / 2,
                stampCenterY + stampHeight
            )

            // PDF 좌표계에서 직접 히트 테스트
            val isCollided = stampRect.contains(rawTouchPoint.x, rawTouchPoint.y)

            // 추가로 원형 충돌 검사도 해보기 (더 정확할 수 있음)
            val dx = rawTouchPoint.x - stampCenterX
            val dy = rawTouchPoint.y - stampCenterY
            val distance = Math.hypot(dx.toDouble(), dy.toDouble())
            val radius = stampWidth / 2f
            val isCollidedCircle = distance <= radius

            if (isCollided || isCollidedCircle) {
                // 해당 스탬프와 연결된 댓글들 찾기
                annotations.filter {
                    it.type == PdfAnnotationModel.Type.Note &&
                            it.paginationPageIndex == paginationPageIndex &&
                            it.asNote() != null &&
                            stampDetail.noteIds.contains(it.asNote()!!.id.toInt())
                }.forEach {
                    resultNotes.add(it.asNote()!!)
                }
                break // 하나 찾으면 종료
            }
        }

        return resultNotes
    }

    /**
     * annotations 리스트에서 해당 주석 ID를 가진 항목 제거
     * 실시간 UI 반영은 호출 측에서 Canvas를 다시 그려야 함
     * */
    fun removeCommentAnnotation(noteIds: List<Long>) {
        annotations.removeAll {
            it.type == PdfAnnotationModel.Type.Note && noteIds.contains(it.asNote()!!.id)
        }
    }
    fun removeHighlightAnnotation(highlightIds: List<Long>) {
        annotations.removeAll {
            it.type == PdfAnnotationModel.Type.Highlight && highlightIds.contains(it.asHighlight()!!.id)
        }
    }

    fun getNoteAnnotation(noteId: Int): CommentModel? {
        return annotations.find { it.type == PdfAnnotationModel.Type.Note && it.asNote() != null && it.asNote()!!.id.toInt() == noteId }?.asNote()
    }

    fun getDpValue(resource: Resources, dpValue: Float): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            dpValue,
            resource.displayMetrics,
        ).toInt()
    }

    /**This function itself will not clear the annotations that are already drawn,
     * you will need to redraw to apply this changes
     * */
    fun clearAllAnnotations() {
        annotations.clear()
    }

    data class AddedStampDetails(
        val x: Float, // 댓글 X 좌표
        val y: Float, // 댓글 Y 좌표
        val noteIds: ArrayList<Int> = arrayListOf(), // 관련 댓글 ID 리스트
    )

    companion object {
        private const val TAG = "AnnotationHandler"
    }
}
