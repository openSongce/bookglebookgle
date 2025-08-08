package com.ssafy.bookglebookgle.util

import android.content.Context
import android.util.Log
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import java.io.File

object PdfOcrChecker {
    private const val TAG = "PdfOcrChecker"

    /**
     * PDF 파일이 OCR이 필요한지 확인합니다.
     * @param context Android Context
     * @param pdfFile PDF 파일
     * @return true: OCR 필요 (이미지 기반 PDF), false: OCR 불필요 (텍스트 추출 가능)
     */
    fun isOcrRequired(context: Context, pdfFile: File): Boolean {
        return try {
            // PDFBox 초기화
            PDFBoxResourceLoader.init(context)

            val document = PDDocument.load(pdfFile)
            val stripper = PDFTextStripper()

            // 첫 3페이지만 확인 (성능 최적화)
            val totalPages = document.numberOfPages
            val pagesToCheck = minOf(3, totalPages)

            var totalTextLength = 0
            var hasSignificantText = false

            for (pageNum in 1..pagesToCheck) {
                stripper.startPage = pageNum
                stripper.endPage = pageNum

                val pageText = stripper.getText(document)
                val cleanText = pageText.trim()
                    .replace(Regex("\\s+"), " ") // 연속된 공백을 하나로
                    .replace(Regex("[\\r\\n]+"), "") // 개행 문자 제거

                totalTextLength += cleanText.length

                Log.d(TAG, "페이지 $pageNum 텍스트 길이: ${cleanText.length}")
                Log.d(TAG, "페이지 $pageNum 텍스트 샘플: ${cleanText.take(100)}")

                // 페이지당 최소 50자 이상의 의미있는 텍스트가 있는지 확인
                if (cleanText.length >= 50) {
                    hasSignificantText = true
                }
            }

            document.close()

            // 평균 페이지당 텍스트 길이 계산
            val avgTextPerPage = totalTextLength.toDouble() / pagesToCheck

            Log.d(TAG, "총 텍스트 길이: $totalTextLength")
            Log.d(TAG, "확인한 페이지 수: $pagesToCheck")
            Log.d(TAG, "페이지당 평균 텍스트 길이: $avgTextPerPage")
            Log.d(TAG, "의미있는 텍스트 존재: $hasSignificantText")

            // OCR 필요 여부 판단 기준:
            // 1. 의미있는 텍스트가 있는 페이지가 하나도 없거나
            // 2. 페이지당 평균 텍스트 길이가 30자 미만인 경우
            val ocrRequired = !hasSignificantText || avgTextPerPage < 30

            Log.d(TAG, "OCR 필요 여부: $ocrRequired")

            ocrRequired

        } catch (e: Exception) {
            Log.e(TAG, "PDF 텍스트 추출 중 오류 발생", e)
            // 오류 발생 시 안전하게 OCR 필요로 간주
            true
        }
    }

    /**
     * PDF 파일의 상세 정보를 분석합니다.
     * @param context Android Context
     * @param pdfFile PDF 파일
     * @return PdfAnalysisResult 분석 결과
     */
    fun analyzePdf(context: Context, pdfFile: File): PdfAnalysisResult {
        return try {
            PDFBoxResourceLoader.init(context)

            val document = PDDocument.load(pdfFile)
            val totalPages = document.numberOfPages
            val stripper = PDFTextStripper()

            var totalTextLength = 0
            var textPages = 0
            var imagePages = 0

            // 모든 페이지 분석 (최대 10페이지까지만)
            val pagesToAnalyze = minOf(10, totalPages)

            for (pageNum in 1..pagesToAnalyze) {
                stripper.startPage = pageNum
                stripper.endPage = pageNum

                val pageText = stripper.getText(document)
                val cleanText = pageText.trim()
                    .replace(Regex("\\s+"), " ")
                    .replace(Regex("[\\r\\n]+"), "")

                totalTextLength += cleanText.length

                if (cleanText.length >= 50) {
                    textPages++
                } else {
                    imagePages++
                }
            }

            document.close()

            val avgTextPerPage = totalTextLength.toDouble() / pagesToAnalyze
            val textPageRatio = textPages.toDouble() / pagesToAnalyze

            PdfAnalysisResult(
                totalPages = totalPages,
                analyzedPages = pagesToAnalyze,
                totalTextLength = totalTextLength,
                avgTextPerPage = avgTextPerPage,
                textPages = textPages,
                imagePages = imagePages,
                textPageRatio = textPageRatio,
                isOcrRequired = textPageRatio < 0.5 || avgTextPerPage < 30
            )

        } catch (e: Exception) {
            Log.e(TAG, "PDF 분석 중 오류 발생", e)
            PdfAnalysisResult(
                totalPages = 0,
                analyzedPages = 0,
                totalTextLength = 0,
                avgTextPerPage = 0.0,
                textPages = 0,
                imagePages = 0,
                textPageRatio = 0.0,
                isOcrRequired = true,
                error = e.message
            )
        }
    }

    /**
     * 빠른 텍스트 추출 가능 여부 확인 (첫 페이지만)
     * @param context Android Context
     * @param pdfFile PDF 파일
     * @return true: 텍스트 추출 가능, false: OCR 필요
     */
    fun canExtractTextQuickly(context: Context, pdfFile: File): Boolean {
        return try {
            PDFBoxResourceLoader.init(context)

            val document = PDDocument.load(pdfFile)
            val stripper = PDFTextStripper()

            // 첫 페이지만 확인
            stripper.startPage = 1
            stripper.endPage = 1

            val firstPageText = stripper.getText(document)
            val cleanText = firstPageText.trim()
                .replace(Regex("\\s+"), " ")
                .replace(Regex("[\\r\\n]+"), "")

            document.close()

            // 첫 페이지에 최소 20자 이상의 텍스트가 있으면 텍스트 추출 가능으로 판단
            cleanText.length >= 20

        } catch (e: Exception) {
            Log.e(TAG, "빠른 텍스트 추출 확인 중 오류 발생", e)
            false
        }
    }
}

/**
 * PDF 분석 결과 데이터 클래스
 */
data class PdfAnalysisResult(
    val totalPages: Int,
    val analyzedPages: Int,
    val totalTextLength: Int,
    val avgTextPerPage: Double,
    val textPages: Int,
    val imagePages: Int,
    val textPageRatio: Double,
    val isOcrRequired: Boolean,
    val error: String? = null
)