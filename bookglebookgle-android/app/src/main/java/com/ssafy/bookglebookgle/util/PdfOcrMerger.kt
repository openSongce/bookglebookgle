package com.ssafy.bookglebookgle.util

import android.content.Context
import android.graphics.RectF
import android.util.Log
import com.ssafy.bookglebookgle.entity.OcrLine
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream.AppendMode
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.font.PDType0Font
import com.tom_roush.pdfbox.pdmodel.graphics.state.PDExtendedGraphicsState
import com.tom_roush.pdfbox.util.Matrix
import java.io.File
import com.tom_roush.pdfbox.pdmodel.graphics.state.RenderingMode

object PdfOcrMerger {

    private const val TAG = "PdfOcrMerger"

    /**
     * @param originalPdf  서버에서 받은 원본 PDF 파일
     * @param ocrLines     줄 단위 병합된 OCR 라인 (smartMergeToLines 결과 권장)
     * @param outFile      출력 파일 경로 (cacheDir 등)
     * @param fontAsset    한글 포함 폰트(TTF) 에셋 경로 (예: "fonts/NotoSansCJK-Regular.ttf")
     * @param baselineFactor 텍스트 박스 높이 대비 베이스라인 위치(0~1). 대략 0.8 ~ 0.9 권장.
     * @param fontSize     기본 폰트 사이즈(점 단위). 가로 스케일로 늘려서 상자 폭에 맞춤.
     */
    fun merge(
        context: Context,
        originalPdf: File,
        ocrLines: List<OcrLine>,
        outFile: File,
        fontAsset: String = "fonts/NotoSansCJK-Regular.ttf",
        baselineFactor: Float = 0.85f,
        fontSize: Float = 10f,
        yNudgePt: Float = 0.7f,
        yNudgeRatio: Float? = null
    ): File {
        require(originalPdf.exists()) { "Original PDF not found: ${originalPdf.path}" }
        PDFBoxResourceLoader.init(context)

        PDDocument.load(originalPdf).use { doc ->
            // 폰트 임베드 (CJK 텍스트용)
            val fontStream = context.assets.open(fontAsset)
            val font = PDType0Font.load(doc, fontStream, true)

            // 페이지별로 OCR 라인 분배
            val linesByPage = ocrLines.groupBy { it.pageNumber }

            for ((pageIdx, page) in doc.pages.withIndex()) {
                val pdPage = page as PDPage
                val mediaBox: PDRectangle = pdPage.mediaBox
                val pageW = mediaBox.width
                val pageH = mediaBox.height

                val pageNumber1Based = pageIdx + 1
                val lines = linesByPage[pageNumber1Based] ?: emptyList()
                if (lines.isEmpty()) continue

                PDPageContentStream(doc, pdPage, AppendMode.APPEND, /*compress*/true, /*resetContext*/true).use { cs ->

                    val gs = PDExtendedGraphicsState().apply {
                        nonStrokingAlphaConstant = 0f   // fill alpha 0
                        strokingAlphaConstant    = 0f   // stroke alpha 0 (안 써도 무방)
                    }
                    cs.setGraphicsStateParameters(gs)
                    // 보이지 않는 텍스트로 추가
                    // RenderingMode.NEITHER == fill/stroke 하지 않음(=안 보임) 이지만 텍스트는 PDF에 존재
                    cs.setRenderingMode(RenderingMode.FILL)
                    cs.setNonStrokingColor(0, 0, 0) // 색은 의미 없지만 기본값 유지

                    for (line in lines) {
                        val box = toPageRect(line, pageW, pageH)

                        val text = line.text.trim()
                        if (text.isEmpty()) continue

                        val computedNudge = yNudgeRatio?.let { box.height() * it } ?: yNudgePt

                        val baseY = (box.top - box.height() * baselineFactor) + computedNudge

                        // 텍스트 폭 측정 (폰트 공간에서 width/1000 * fontSize)
                        val rawWidth = try {
                            font.getStringWidth(text) / 1000f * fontSize
                        } catch (e: Exception) {
                            Log.w(TAG, "getStringWidth failed: ${e.message}")
                            // 폭 추정치(문자수 * 0.6em)
                            text.length * fontSize * 0.6f
                        }
                        val targetWidth = box.width()

                        // 가로 스케일링: Matrix(a,b,c,d,tx,ty)에서 a 성분으로 x 스케일을 준다
                        val scaleX = if (rawWidth > 0f) targetWidth / rawWidth else 1f

                        cs.beginText()
                        cs.setFont(font, fontSize)
                        // 좌표계: PDF는 좌하단 원점. box.left는 이미 좌하단 기준으로 변환됨.
                        cs.setTextMatrix(Matrix(scaleX, 0f, 0f, 1f, box.left, baseY))
                        cs.showText(text)
                        cs.endText()
                    }
                }
            }

            // 저장
            outFile.parentFile?.mkdirs()
            doc.save(outFile)
        }

        return outFile
    }

    /**
     * OcrLine의 (rectX, rectY, rectW, rectH)를 PDF 좌표계 RectF로 변환.
     * - 입력이 0~1 범위면 정규화로 보고 페이지 크기에 맞게 확장
     * - Y는 상단 기준이라고 가정 → PDF 좌표(하단 기준)로 변환
     */
    private fun toPageRect(line: OcrLine, pageW: Float, pageH: Float): RectF {
        val isNormalized = line.rectX <= 1.5 && line.rectY <= 1.5 && line.rectW <= 2.0 && line.rectH <= 2.0

        val x = if (isNormalized) (line.rectX * pageW).toFloat() else line.rectX.toFloat()
        val w = if (isNormalized) (line.rectW * pageW).toFloat() else line.rectW.toFloat()

        // rectY가 "상단" 기준이라고 가정
        val topY = if (isNormalized) (line.rectY * pageH).toFloat() else line.rectY.toFloat()
        val h    = if (isNormalized) (line.rectH * pageH).toFloat() else line.rectH.toFloat()

        // PDF 좌표로: top -> bottom-left
        val yBottom = pageH - (topY + h)
        return RectF(x, yBottom, x + w, yBottom + h)
    }
}
