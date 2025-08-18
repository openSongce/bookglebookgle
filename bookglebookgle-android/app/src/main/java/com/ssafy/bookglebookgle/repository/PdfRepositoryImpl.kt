package com.ssafy.bookglebookgle.repository

import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.ssafy.bookglebookgle.entity.OcrResult
import com.ssafy.bookglebookgle.entity.pdf.AddBookmarkRequest
import com.ssafy.bookglebookgle.entity.pdf.AddCommentRequest
import com.ssafy.bookglebookgle.entity.pdf.AddHighlightRequest
import com.ssafy.bookglebookgle.entity.pdf.BookmarkResponse
import com.ssafy.bookglebookgle.entity.pdf.CommentResponse
import com.ssafy.bookglebookgle.entity.pdf.CoordinatesRequest
import com.ssafy.bookglebookgle.entity.pdf.HighlightResponse
import com.ssafy.bookglebookgle.entity.pdf.UpdateCommentRequest
import com.ssafy.bookglebookgle.network.api.PdfApi
import com.ssafy.bookglebookgle.pdf.response.AnnotationListResponse
import com.ssafy.bookglebookgle.pdf.response.DeleteAnnotationResponse
import com.ssafy.bookglebookgle.pdf.tools.pdf.viewer.model.BookmarkModel
import com.ssafy.bookglebookgle.pdf.tools.pdf.viewer.model.CommentModel
import com.ssafy.bookglebookgle.pdf.tools.pdf.viewer.model.Coordinates
import com.ssafy.bookglebookgle.pdf.tools.pdf.viewer.model.HighlightModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.ResponseBody
import retrofit2.Response
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.util.zip.ZipInputStream
import javax.inject.Inject

private const val TAG = "싸피_PdfRepositoryImpl"

class PdfRepositoryImpl @Inject constructor(
    private val pdfApi: PdfApi
) : PdfRepository {

    data class PdfWithOcrData(
        val inputStream: InputStream?,
        val ocrResults: List<OcrResult>? = null,
        val fileName: String
    )

    // 교체: Content-Disposition 전체에서 안전하게 파일명 추출
    private fun pickNiceFileName(resp: Response<ResponseBody>, groupId: Long, contentType: String): String {
        val cds = resp.headers().values("Content-Disposition") // 모든 값(복수 헤더 대응)
        extractFilenameFromCDs(cds)?.let { return it }
        return if (contentType.contains("zip")) "group-$groupId.zip" else "group-$groupId.pdf"
    }

    private fun extractFilenameFromCDs(cds: List<String>): String? {
        for (raw in cds) {
            val cd = raw.replace("\r","").replace("\n","").trim()

            // 1) filename* (RFC 5987)  예: UTF-8''%EA%B0%80%EB%82%98.pdf
            Regex("""filename\*=\s*([^;]+)""", RegexOption.IGNORE_CASE).find(cd)?.let { m ->
                return decodeRfc5987(m.groupValues[1])
            }

            // 2) 일반 filename="..."
            Regex("""filename\s*=\s*("?)([^";]+)\1""", RegexOption.IGNORE_CASE).find(cd)?.let { m ->
                return m.groupValues[2].trim()
            }
        }
        return null
    }

    private fun decodeRfc5987(token: String): String {
        var v = token.trim().trim('"','\'','\u201C','\u201D').replace("+","%20")
        val idx = v.indexOf("''")
        val encoded = if (idx >= 0) v.substring(idx + 2) else v
        return try { java.net.URLDecoder.decode(encoded, "UTF-8") } catch (_: Exception) { encoded }
    }



    override suspend fun uploadPdf(file: File): Boolean {
        return try {
            Log.d(TAG, "=== PDF 업로드 시작 ===")
            Log.d(TAG, "파일명: ${file.name}, 크기: ${file.length()} bytes")

            val requestFile = file.asRequestBody("application/pdf".toMediaTypeOrNull())
            val filePart = MultipartBody.Part.createFormData("file", file.name, requestFile)

            val response = pdfApi.uploadPdf(filePart)
            Log.d(TAG, "서버 응답 코드: ${response.code()}")
            Log.d(TAG, "서버 응답 메시지: ${response.message()}")

            if (response.isSuccessful) {
                Log.d(TAG, "PDF 업로드 성공")
                true
            } else {
                Log.e(TAG, "PDF 업로드 실패 - 코드: ${response.code()}, 메시지: ${response.message()}")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "PDF 업로드 중 예외 발생", e)
            false
        }
    }

    /**
     * 그룹 PDF 조회 (OCR 처리 여부에 따라 응답 형태 결정)
     * - imageBased=true인 그룹: ZIP 형태로 PDF + OCR JSON 반환
     * - imageBased=false인 그룹: PDF 파일만 반환
     */
    override suspend fun getGroupPdf(groupId: Long, cacheDir: File): PdfWithOcrData? {
        return try {
            withContext(Dispatchers.IO) {
                Log.d(TAG, "=== 그룹 PDF 다운로드 시작 ===")
                Log.d(TAG, "그룹 ID: $groupId")

                val response = pdfApi.getGroupPdf(
                    groupId = groupId,
                    accept = "application/zip",
                )
                Log.d(TAG, "응답 코드: ${response.code()}")

                if (response.isSuccessful) {
                    val contentType = response.headers()["Content-Type"]?.lowercase().orEmpty()
                    val contentDisposition = response.headers()["Content-Disposition"].orEmpty()
                    val fileName = pickNiceFileName(response, groupId, contentType)

                    Log.d("HTTP", "CT=$contentType, CD=$contentDisposition, headers=${response.headers()}")

                    Log.d(TAG, "Content-Type: $contentType")
                    Log.d(TAG, "파일명: $fileName")

                    // Content-Type에 따라 처리 분기
                    when {
                        contentType.contains("application/zip") -> {
                            Log.d(TAG, "ZIP 파일로 인식 - OCR 데이터 포함")
                            processZipToInputStream(response, cacheDir, groupId)
                        }
                        contentType.contains("application/pdf") -> {
                            Log.d(TAG, "PDF 파일로 인식 - OCR 데이터 없음")
                            val inputStream = response.body()!!.byteStream()
                            PdfWithOcrData(
                                inputStream = inputStream,
                                ocrResults = null,
                                fileName = fileName.removeSuffix(".pdf")
                            )
                        }
                        else -> {
                            Log.e(TAG, "지원하지 않는 Content-Type: $contentType")
                            null
                        }
                    }
                } else {
                    Log.e(TAG, "PDF 다운로드 실패 - 응답코드: ${response.code()}, 메시지: ${response.message()}")
                    null
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "PDF 다운로드 중 예외 발생", e)
            null
        }
    }

    /**
     * ZIP 파일을 InputStream으로 처리 (PDF + OCR JSON 추출)
     */
    private fun processZipToInputStream(response: Response<ResponseBody>, cacheDir: File, groupId: Long): PdfWithOcrData? {
        return try {
            Log.d(TAG, "=== ZIP 파일을 InputStream으로 처리 시작 ===")

            // 임시로 ZIP 파일 저장
            val tempZipFile = File(cacheDir, "temp_group_$groupId.zip")
            response.body()!!.use { body ->
                body.byteStream().use { input ->
                    tempZipFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
            }

            val extractDir = File(cacheDir, "group-$groupId-extracted")
            if (!extractDir.exists()) {
                extractDir.mkdirs()
            }

            var pdfFile: File? = null
            var ocrResults: List<OcrResult>? = null
            var fileName = "PDF"

            ZipInputStream(FileInputStream(tempZipFile)).use { zipStream ->
                var entry = zipStream.nextEntry

                while (entry != null) {
                    Log.d(TAG, "ZIP 엔트리 발견: ${entry.name}")

                    if (!entry.isDirectory) {
                        val outputFile = File(extractDir, entry.name)
                        outputFile.parentFile?.mkdirs()

                        outputFile.outputStream().use { output ->
                            zipStream.copyTo(output)
                        }

                        when {
                            entry.name.endsWith(".pdf", ignoreCase = true) -> {
                                pdfFile = outputFile
                                fileName = entry.name.removeSuffix(".pdf")
                                Log.d(TAG, "PDF 파일 추출: ${outputFile.absolutePath}")
                            }
                            entry.name.endsWith(".json", ignoreCase = true) &&
                                    entry.name.contains("ocr", ignoreCase = true) -> {
                                ocrResults = parseOcrJson(outputFile)
                                Log.d(TAG, "OCR JSON 파일 파싱 완료: ${ocrResults?.size}개 결과")
                            }
                        }
                    }

                    zipStream.closeEntry()
                    entry = zipStream.nextEntry
                }
            }

            // 임시 ZIP 파일 정리
            tempZipFile.delete()

            if (pdfFile != null) {
                Log.d(TAG, "ZIP 처리 완료 - PDF: ${pdfFile!!.name}, OCR: ${ocrResults?.size ?: 0}개")

                // PDF 파일을 InputStream으로 변환
                val pdfInputStream = FileInputStream(pdfFile!!)

                PdfWithOcrData(
                    inputStream = pdfInputStream,
                    ocrResults = ocrResults,
                    fileName = fileName
                )
            } else {
                Log.e(TAG, "ZIP에서 PDF 파일을 찾을 수 없음")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "ZIP 파일 처리 중 예외 발생", e)
            null
        }
    }

    /**
     * OCR JSON 파일 파싱
     */
    private fun parseOcrJson(jsonFile: File): List<OcrResult>? {
        return try {
            Log.d(TAG, "=== OCR JSON 파싱 시작 ===")
            Log.d(TAG, "JSON 파일: ${jsonFile.absolutePath}")
            Log.d(TAG, "JSON 파일 크기: ${jsonFile.length()} bytes")

            val jsonContent = jsonFile.readText()
            Log.d(TAG, "JSON 내용 길이: ${jsonContent.length}")
            Log.d(TAG, "JSON 미리보기: ${jsonContent.take(200)}...")

            val gson = Gson()
            val listType = object : TypeToken<List<OcrResult>>() {}.type
            val results = gson.fromJson<List<OcrResult>>(jsonContent, listType)

            Log.d(TAG, "OCR 결과 파싱 완료: ${results?.size}개")
            results?.forEachIndexed { index, result ->
                Log.d(TAG, "OCR[$index]: 페이지=${result.pageNumber}, 텍스트=${result.text.take(50)}...")
            }

            results
        } catch (e: Exception) {
            Log.e(TAG, "OCR JSON 파싱 실패", e)
            null
        }
    }

    /**
     * 응답 바디를 파일로 다운로드 (더 이상 사용하지 않음)
     */
    @Deprecated("InputStream 방식으로 변경됨")
    private fun downloadFile(response: Response<ResponseBody>, cacheDir: File, fileName: String): File {
        val outputFile = File(cacheDir, fileName)

        response.body()!!.use { body ->
            body.byteStream().use { input ->
                outputFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
        }

        return outputFile
    }

    /**
     * Content-Disposition 헤더에서 파일명 추출
     */
    private fun parseFilename(contentDisposition: String): String? {
        if (contentDisposition.isBlank()) return null

        val regex = Regex("""filename\*?=("[^"]+"|[^;]+)""", RegexOption.IGNORE_CASE)
        val match = regex.find(contentDisposition) ?: return null
        return match.groupValues[1].trim('"')
    }



    // 북마크 관리
    override suspend fun addBookmark(groupId: Long, page: Int): BookmarkModel? {
        return try {
            Log.d(TAG, "=== 북마크 추가 요청 ===")
            Log.d(TAG, "그룹 ID: $groupId, 페이지: $page")

            val request = AddBookmarkRequest(page = page)
            val response = pdfApi.addBookmark(groupId, request)
            Log.d(TAG, "응답 코드: ${response.code()}")

            if (response.isSuccessful) {
                response.body()?.let { bookmarkResponse ->
                    Log.d(TAG, "북마크 추가 성공: $bookmarkResponse")

                    BookmarkModel(
                        id = bookmarkResponse.id,
                        page = bookmarkResponse.page
                    )
                }
            } else {
                Log.e(TAG, "북마크 추가 실패: ${response.code()} ${response.message()}")
                response.errorBody()?.string()?.let { Log.e(TAG, "에러: $it") }
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "북마크 추가 중 예외 발생", e)
            null
        }
    }

    override suspend fun getBookmarks(groupId: Long): List<BookmarkModel>? {
        return try {
            Log.d(TAG, "=== 북마크 조회 요청 ===")
            Log.d(TAG, "그룹 ID: $groupId")

            val response = pdfApi.getBookmarks(groupId)
            Log.d(TAG, "응답 코드: ${response.code()}")

            if (response.isSuccessful) {
                response.body()?.let { bookmarkList ->
                    Log.d(TAG, "북마크 조회 성공 - 북마크 수: ${bookmarkList.size}")

                    bookmarkList.map { bookmarkResponse ->
                        BookmarkModel(
                            id = bookmarkResponse.id,
                            page = bookmarkResponse.page
                        )
                    }
                }
            } else {
                Log.e(TAG, "북마크 조회 실패: ${response.code()} ${response.message()}")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "북마크 조회 중 예외 발생", e)
            null
        }
    }

    override suspend fun deleteBookmark(bookmarkId: Long): DeleteAnnotationResponse? {
        return try {
            Log.d(TAG, "=== 북마크 삭제 요청 (ID 기준) ===")
            Log.d(TAG, "북마크 ID: $bookmarkId")

            val response = pdfApi.deleteBookmark(bookmarkId)
            Log.d(TAG, "응답 코드: ${response.code()}")

            if (response.isSuccessful) {
                response.body()?.let { apiDeleteResponse ->
                    Log.d(TAG, "북마크 삭제 성공 - 삭제된 ID들: ${apiDeleteResponse.deletedIds}")

                    // API Response를 내부 모델로 변환
                    DeleteAnnotationResponse(
                        deletedIds = apiDeleteResponse.deletedIds
                    )
                }
            } else {
                Log.e(TAG, "북마크 삭제 실패: ${response.code()} ${response.message()}")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "북마크 삭제 중 예외 발생", e)
            null
        }
    }

    override suspend fun deleteBookmarkByPage(groupId: Long, page: Int): DeleteAnnotationResponse? {
        return try {
            Log.d(TAG, "=== 북마크 삭제 요청 (페이지 기준) ===")
            Log.d(TAG, "그룹 ID: $groupId, 페이지: $page")

            val response = pdfApi.deleteBookmarkByPage(groupId, page)
            Log.d(TAG, "응답 코드: ${response.code()}")

            if (response.isSuccessful) {
                response.body()?.let { apiDeleteResponse ->
                    Log.d(TAG, "북마크 삭제 성공 (페이지 기준) - 삭제된 ID들: ${apiDeleteResponse.deletedIds}")

                    // API Response를 내부 모델로 변환
                    DeleteAnnotationResponse(
                        deletedIds = apiDeleteResponse.deletedIds
                    )
                }
            } else {
                Log.e(TAG, "북마크 삭제 실패 (페이지 기준): ${response.code()} ${response.message()}")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "북마크 삭제 중 예외 발생 (페이지 기준)", e)
            null
        }
    }

    // 전체 주석 조회
    override suspend fun getAllAnnotations(pdfId: Long): AnnotationListResponse? {
        return try {
            Log.d(TAG, "=== 전체 주석 조회 요청 ===")
            Log.d(TAG, "PDF ID: $pdfId")

            // 1) 댓글, 하이라이트, 북마크 각각 호출
            val commentsResp   = pdfApi.getComments(pdfId)
            Log.d(TAG, "댓글 응답 코드: ${commentsResp.code()}")
            val highlightsResp = pdfApi.getHighlights(pdfId)
            Log.d(TAG, "하이라이트 응답 코드: ${highlightsResp.code()}")
//            val bookmarksResp  = pdfApi.getBookmarks(pdfId)
//            Log.d(TAG, "북마크 응답 코드: ${bookmarksResp.code()}")

            // 2) 세 API가 모두 성공일 때만 합치기
            if (commentsResp.isSuccessful &&
                highlightsResp.isSuccessful // &&
//                bookmarksResp.isSuccessful
            ) {
                val commentsBody   = commentsResp.body()   ?: emptyList<CommentResponse>()
                val highlightsBody = highlightsResp.body() ?: emptyList<HighlightResponse>()
//                val bookmarksBody  = bookmarksResp.body()  ?: emptyList<BookmarkResponse>()

                // 3) DTO → 내부 모델 변환
                val convertedComments = commentsBody.map { dto ->
                    CommentModel(
                        id          = dto.id,
                        page        = dto.page,
                        snippet     = dto.snippet,
                        text        = dto.text,
                        coordinates = Coordinates(
                            startX = dto.startX,
                            startY = dto.startY,
                            endX   = dto.endX,
                            endY   = dto.endY
                        ),
                        userId = dto.userId.toString()
                    )
                }
                Log.d(TAG, "댓글 변환 완료: ${convertedComments.size}개")

                val convertedHighlights = highlightsBody.map { dto ->
                    HighlightModel(
                        id          = dto.id,
                        page        = dto.page,
                        snippet     = dto.snippet,
                        color       = dto.color,
                        coordinates = Coordinates(
                            startX = dto.startX,
                            startY = dto.startY,
                            endX   = dto.endX,
                            endY   = dto.endY
                        ),
                        userId = dto.userId.toString()
                    )
                }
                Log.d(TAG, "하이라이트 변환 완료: ${convertedHighlights.size}개")

//                val convertedBookmarks = bookmarksBody.map { dto ->
//                    BookmarkModel(
//                        id   = dto.id,
//                        page = dto.page
//                    )
//                }
//                Log.d(TAG, "북마크 변환 완료: ${convertedBookmarks.size}개")

                // 4) AnnotationListResponse로 묶어서 반환
                AnnotationListResponse(
                    comments   = ArrayList(convertedComments),
                    highlights = ArrayList(convertedHighlights),
//                    bookmarks  = ArrayList(convertedBookmarks)
                )
            } else {
                Log.e(TAG, "하나 이상의 API 호출 실패: " +
                        "comments=${commentsResp.code()}, " +
                        "highlights=${highlightsResp.code()}, " // +
//                        "bookmarks=${bookmarksResp.code()}"
                )
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "전체 주석 조회 중 예외 발생", e)
            null
        }
    }


    private fun extractFileNameFromHeaders(response: Response<ResponseBody>): String {
        Log.d(TAG, "=== PDF 파일명 추출 ===")

        val contentDisposition = response.headers()["Content-Disposition"]
        Log.d(TAG, "Content-Disposition: $contentDisposition")

        return if (contentDisposition != null) {
            val fileNameRegex = """filename[*]?=['"]?([^'";]+)['"]?""".toRegex()
            val matchResult = fileNameRegex.find(contentDisposition)

            if (matchResult != null) {
                val rawFileName = matchResult.groupValues[1]
                try {
                    java.net.URLDecoder.decode(rawFileName, "UTF-8")
                } catch (e: Exception) {
                    rawFileName
                }
            } else {
                "PDF"
            }
        } else {
            "PDF"
        }
    }
}

//package com.ssafy.bookglebookgle.repository
//
//import android.util.Log
//import com.ssafy.bookglebookgle.entity.pdf.AddBookmarkRequest
//import com.ssafy.bookglebookgle.entity.pdf.AddCommentRequest
//import com.ssafy.bookglebookgle.entity.pdf.AddHighlightRequest
//import com.ssafy.bookglebookgle.entity.pdf.CoordinatesRequest
//import com.ssafy.bookglebookgle.entity.pdf.UpdateCommentRequest
//import com.ssafy.bookglebookgle.network.api.PdfApi
//import com.ssafy.bookglebookgle.pdf.response.AnnotationListResponse
//import com.ssafy.bookglebookgle.pdf.response.DeleteAnnotationResponse
//import com.ssafy.bookglebookgle.pdf.tools.pdf.viewer.model.BookmarkModel
//import com.ssafy.bookglebookgle.pdf.tools.pdf.viewer.model.CommentModel
//import com.ssafy.bookglebookgle.pdf.tools.pdf.viewer.model.Coordinates
//import com.ssafy.bookglebookgle.pdf.tools.pdf.viewer.model.HighlightModel
//import okhttp3.MediaType.Companion.toMediaTypeOrNull
//import okhttp3.MultipartBody
//import okhttp3.RequestBody.Companion.asRequestBody
//import okhttp3.ResponseBody
//import retrofit2.Response
//import java.io.File
//import java.io.InputStream
//import javax.inject.Inject
//
//private const val TAG = "싸피_PdfRepositoryImpl"
//class PdfRepositoryImpl @Inject constructor(
//    private val pdfApi: PdfApi
//) : PdfRepository {
//
//    data class PdfData(
//        val inputStream: InputStream?,
//        val fileName: String
//    )
//
//    override suspend fun uploadPdf(file: File): Boolean {
//        return try {
//            Log.d(TAG, "=== PDF 업로드 시작 ===")
//            Log.d(TAG, "파일명: ${file.name}, 크기: ${file.length()} bytes")
//
//            val requestFile = file.asRequestBody("application/pdf".toMediaTypeOrNull())
//            val filePart = MultipartBody.Part.createFormData("file", file.name, requestFile)
//
//            val response = pdfApi.uploadPdf(filePart)
//            Log.d(TAG, "서버 응답 코드: ${response.code()}")
//            Log.d(TAG, "서버 응답 메시지: ${response.message()}")
//
//            if (response.isSuccessful) {
//                Log.d(TAG, "PDF 업로드 성공")
//                true
//            } else {
//                Log.e(TAG, "PDF 업로드 실패 - 코드: ${response.code()}, 메시지: ${response.message()}")
//                false
//            }
//        } catch (e: Exception) {
//            Log.e(TAG, "PDF 업로드 중 예외 발생", e)
//            false
//        }
//    }
//
//    override suspend fun getGroupPdf(groupId: Long): PdfData? {
//        return try {
//            Log.d(TAG, "=== 그룹 PDF 다운로드 시작 ===")
//            Log.d(TAG, "그룹 ID: $groupId")
//
//            val response = pdfApi.getGroupPdf(groupId)
//            Log.d(TAG, "응답 코드: ${response.code()}")
//
//            if (response.isSuccessful) {
//                val fileName = extractFileNameFromHeaders(response)
//                val inputStream = response.body()!!.byteStream()
//                Log.d(TAG, "PDF 다운로드 성공 - 파일명: $fileName")
//
//                PdfData(inputStream, fileName)
//            } else {
//                Log.e(TAG, "PDF 다운로드 실패 - 응답코드: ${response.code()}, 메시지: ${response.message()}")
//                null
//            }
//        } catch (e: Exception) {
//            Log.e(TAG, "PDF 다운로드 중 예외 발생", e)
//            null
//        }
//    }
//
//    // 댓글 관리
//    override suspend fun addComment(
//        groupId: Long,
//        snippet: String,
//        text: String,
//        page: Int,
//        coordinates: Coordinates
//    ): CommentModel? {
//        return try {
//            val request = AddCommentRequest(
//                snippet = snippet,
//                text = text,
//                page = page,
//                coordinates = CoordinatesRequest(
//                    startX = coordinates.startX,
//                    startY = coordinates.startY,
//                    endX = coordinates.endX,
//                    endY = coordinates.endY
//                )
//            )
//
//            val response = pdfApi.addComment(groupId, request)
//            if (response.isSuccessful) {
//                response.body()?.let { commentResponse ->
//                    CommentModel(
//                        id = commentResponse.id,
//                        snippet = commentResponse.snippet,
//                        text = commentResponse.text,
//                        page = commentResponse.page,
//                        updatedAt = commentResponse.updatedAt,
//                        coordinates = Coordinates(
//                            startX = commentResponse.coordinates.startX,
//                            startY = commentResponse.coordinates.startY,
//                            endX = commentResponse.coordinates.endX,
//                            endY = commentResponse.coordinates.endY
//                        )
//                    )
//                }
//            } else {
//                Log.e(TAG, "댓글 추가 실패: ${response.code()} ${response.message()}")
//                null
//            }
//        } catch (e: Exception) {
//            Log.e(TAG, "댓글 추가 중 예외 발생", e)
//            null
//        }
//    }
//
//    override suspend fun getComments(groupId: Long): List<CommentModel>? {
//        return try {
//            val response = pdfApi.getComments(groupId)
//            if (response.isSuccessful) {
//                response.body()?.map { commentResponse ->
//                    CommentModel(
//                        id = commentResponse.id,
//                        snippet = commentResponse.snippet,
//                        text = commentResponse.text,
//                        page = commentResponse.page,
//                        updatedAt = commentResponse.updatedAt,
//                        coordinates = Coordinates(
//                            startX = commentResponse.coordinates.startX,
//                            startY = commentResponse.coordinates.startY,
//                            endX = commentResponse.coordinates.endX,
//                            endY = commentResponse.coordinates.endY
//                        )
//                    )
//                }
//            } else {
//                Log.e(TAG, "댓글 조회 실패: ${response.code()} ${response.message()}")
//                null
//            }
//        } catch (e: Exception) {
//            Log.e(TAG, "댓글 조회 중 예외 발생", e)
//            null
//        }
//    }
//
//    override suspend fun updateComment(commentId: Long, newText: String): CommentModel? {
//        return try {
//            val request = UpdateCommentRequest(text = newText)
//            val response = pdfApi.updateComment(commentId, request)
//
//            if (response.isSuccessful) {
//                response.body()?.let { commentResponse ->
//                    CommentModel(
//                        id = commentResponse.id,
//                        snippet = commentResponse.snippet,
//                        text = commentResponse.text,
//                        page = commentResponse.page,
//                        updatedAt = commentResponse.updatedAt,
//                        coordinates = Coordinates(
//                            startX = commentResponse.coordinates.startX,
//                            startY = commentResponse.coordinates.startY,
//                            endX = commentResponse.coordinates.endX,
//                            endY = commentResponse.coordinates.endY
//                        )
//                    )
//                }
//            } else {
//                Log.e(TAG, "댓글 수정 실패: ${response.code()} ${response.message()}")
//                null
//            }
//        } catch (e: Exception) {
//            Log.e(TAG, "댓글 수정 중 예외 발생", e)
//            null
//        }
//    }
//
//    override suspend fun deleteComment(commentId: Long): DeleteAnnotationResponse? {
//        return try {
//            val response = pdfApi.deleteComment(commentId)
//            if (response.isSuccessful) {
//                response.body()
//            } else {
//                Log.e(TAG, "댓글 삭제 실패: ${response.code()} ${response.message()}")
//                null
//            }
//        } catch (e: Exception) {
//            Log.e(TAG, "댓글 삭제 중 예외 발생", e)
//            null
//        }
//    }
//
//    // 하이라이트 관리
//    override suspend fun addHighlight(
//        groupId: Long,
//        snippet: String,
//        color: String,
//        page: Int,
//        coordinates: Coordinates
//    ): HighlightModel? {
//        return try {
//            val request = AddHighlightRequest(
//                snippet = snippet,
//                color = color,
//                page = page,
//                coordinates = CoordinatesRequest(
//                    startX = coordinates.startX,
//                    startY = coordinates.startY,
//                    endX = coordinates.endX,
//                    endY = coordinates.endY
//                )
//            )
//
//            val response = pdfApi.addHighlight(groupId, request)
//            if (response.isSuccessful) {
//                response.body()?.let { highlightResponse ->
//                    HighlightModel(
//                        id = highlightResponse.id,
//                        snippet = highlightResponse.snippet,
//                        color = highlightResponse.color,
//                        page = highlightResponse.page,
//                        updatedAt = highlightResponse.updatedAt,
//                        coordinates = Coordinates(
//                            startX = highlightResponse.coordinates.startX,
//                            startY = highlightResponse.coordinates.startY,
//                            endX = highlightResponse.coordinates.endX,
//                            endY = highlightResponse.coordinates.endY
//                        )
//                    )
//                }
//            } else {
//                Log.e(TAG, "하이라이트 추가 실패: ${response.code()} ${response.message()}")
//                null
//            }
//        } catch (e: Exception) {
//            Log.e(TAG, "하이라이트 추가 중 예외 발생", e)
//            null
//        }
//    }
//
//    override suspend fun getHighlights(groupId: Long): List<HighlightModel>? {
//        return try {
//            val response = pdfApi.getHighlights(groupId)
//            if (response.isSuccessful) {
//                response.body()?.map { highlightResponse ->
//                    HighlightModel(
//                        id = highlightResponse.id,
//                        snippet = highlightResponse.snippet,
//                        color = highlightResponse.color,
//                        page = highlightResponse.page,
//                        updatedAt = highlightResponse.updatedAt,
//                        coordinates = Coordinates(
//                            startX = highlightResponse.coordinates.startX,
//                            startY = highlightResponse.coordinates.startY,
//                            endX = highlightResponse.coordinates.endX,
//                            endY = highlightResponse.coordinates.endY
//                        )
//                    )
//                }
//            } else {
//                Log.e(TAG, "하이라이트 조회 실패: ${response.code()} ${response.message()}")
//                null
//            }
//        } catch (e: Exception) {
//            Log.e(TAG, "하이라이트 조회 중 예외 발생", e)
//            null
//        }
//    }
//
//    override suspend fun deleteHighlight(highlightId: Long): DeleteAnnotationResponse? {
//        return try {
//            val response = pdfApi.deleteHighlight(highlightId)
//            if (response.isSuccessful) {
//                response.body()
//            } else {
//                Log.e(TAG, "하이라이트 삭제 실패: ${response.code()} ${response.message()}")
//                null
//            }
//        } catch (e: Exception) {
//            Log.e(TAG, "하이라이트 삭제 중 예외 발생", e)
//            null
//        }
//    }
//
//    // 북마크 관리
//    override suspend fun addBookmark(groupId: Long, page: Int): BookmarkModel? {
//        return try {
//            val request = AddBookmarkRequest(page = page)
//            val response = pdfApi.addBookmark(groupId, request)
//
//            if (response.isSuccessful) {
//                response.body()?.let { bookmarkResponse ->
//                    BookmarkModel(
//                        id = bookmarkResponse.id,
//                        page = bookmarkResponse.page,
//                        updatedAt = bookmarkResponse.updatedAt
//                    )
//                }
//            } else {
//                Log.e(TAG, "북마크 추가 실패: ${response.code()} ${response.message()}")
//                null
//            }
//        } catch (e: Exception) {
//            Log.e(TAG, "북마크 추가 중 예외 발생", e)
//            null
//        }
//    }
//
//    override suspend fun getBookmarks(groupId: Long): List<BookmarkModel>? {
//        return try {
//            val response = pdfApi.getBookmarks(groupId)
//            if (response.isSuccessful) {
//                response.body()?.map { bookmarkResponse ->
//                    BookmarkModel(
//                        id = bookmarkResponse.id,
//                        page = bookmarkResponse.page,
//                        updatedAt = bookmarkResponse.updatedAt
//                    )
//                }
//            } else {
//                Log.e(TAG, "북마크 조회 실패: ${response.code()} ${response.message()}")
//                null
//            }
//        } catch (e: Exception) {
//            Log.e(TAG, "북마크 조회 중 예외 발생", e)
//            null
//        }
//    }
//
//    override suspend fun deleteBookmark(bookmarkId: Long): DeleteAnnotationResponse? {
//        return try {
//            val response = pdfApi.deleteBookmark(bookmarkId)
//            if (response.isSuccessful) {
//                response.body()
//            } else {
//                Log.e(TAG, "북마크 삭제 실패: ${response.code()} ${response.message()}")
//                null
//            }
//        } catch (e: Exception) {
//            Log.e(TAG, "북마크 삭제 중 예외 발생", e)
//            null
//        }
//    }
//
//    override suspend fun deleteBookmarkByPage(groupId: Long, page: Int): DeleteAnnotationResponse? {
//        return try {
//            val response = pdfApi.deleteBookmarkByPage(groupId, page)
//            if (response.isSuccessful) {
//                response.body()
//            } else {
//                Log.e(TAG, "페이지별 북마크 삭제 실패: ${response.code()} ${response.message()}")
//                null
//            }
//        } catch (e: Exception) {
//            Log.e(TAG, "페이지별 북마크 삭제 중 예외 발생", e)
//            null
//        }
//    }
//
//    // 전체 주석 조회
//    override suspend fun getAllAnnotations(pdfId: Long): AnnotationListResponse? {
//        return try {
//            val response = pdfApi.getAllAnnotations(pdfId)
//            if (response.isSuccessful) {
//                response.body()?.let { annotationResponse ->
//                    AnnotationListResponse(
//                        comments = annotationResponse.comments.map { commentResponse ->
//                            CommentModel(
//                                id = commentResponse.id,
//                                snippet = commentResponse.snippet,
//                                text = commentResponse.text,
//                                page = commentResponse.page,
//                                updatedAt = commentResponse.updatedAt,
//                                coordinates = commentResponse.coordinates?.let {
//                                    Coordinates(
//                                        startX = it.startX,
//                                        startY = commentResponse.coordinates.startY,
//                                        endX = commentResponse.coordinates.endX,
//                                        endY = commentResponse.coordinates.endY
//                                    )
//                                }
//                            )
//                        } as ArrayList<CommentModel>,
//                        highlights = annotationResponse.highlights.map { highlightResponse ->
//                            HighlightModel(
//                                id = highlightResponse.id,
//                                snippet = highlightResponse.snippet,
//                                color = highlightResponse.color,
//                                page = highlightResponse.page,
//                                updatedAt = highlightResponse.updatedAt,
//                                coordinates = highlightResponse.coordinates?.let {
//                                    Coordinates(
//                                        startX = it.startX,
//                                        startY = highlightResponse.coordinates.startY,
//                                        endX = highlightResponse.coordinates.endX,
//                                        endY = highlightResponse.coordinates.endY
//                                    )
//                                }
//                            )
//                        } as ArrayList<HighlightModel>,
//                        bookmarks = annotationResponse.bookmarks.map { bookmarkResponse ->
//                            BookmarkModel(
//                                id = bookmarkResponse.id,
//                                page = bookmarkResponse.page,
//                                updatedAt = bookmarkResponse.updatedAt
//                            )
//                        } as ArrayList<BookmarkModel>
//                    )
//                }
//            } else {
//                Log.e(TAG, "전체 주석 조회 실패: ${response.code()} ${response.message()}")
//                null
//            }
//        } catch (e: Exception) {
//            Log.e(TAG, "전체 주석 조회 중 예외 발생", e)
//            null
//        }
//    }
//
//    private fun extractFileNameFromHeaders(response: Response<ResponseBody>): String {
//        Log.d(TAG, "=== PDF 다운로드 응답 헤더 분석 시작 ===")
//
//        // 모든 헤더 출력
//        Log.d(TAG, "전체 헤더 목록:")
//        for (header in response.headers()) {
//            Log.d(TAG, "  ${header.first}: ${header.second}")
//        }
//
//        val contentDisposition = response.headers()["Content-Disposition"]
//        Log.d(TAG, "Content-Disposition 헤더: '$contentDisposition'")
//
//        return if (contentDisposition != null) {
//            Log.d(TAG, "Content-Disposition 헤더가 존재함, 파일명 추출 시도")
//
//            val fileNameRegex = """filename[*]?=['"]?([^'";]+)['"]?""".toRegex()
//            val matchResult = fileNameRegex.find(contentDisposition)
//
//            if (matchResult != null) {
//                val rawFileName = matchResult.groupValues[1]
//                Log.d(TAG, "정규식 매칭 성공 - 추출된 원본 파일명: '$rawFileName'")
//
//                val decodedFileName = try {
//                    val decoded = java.net.URLDecoder.decode(rawFileName, "UTF-8")
//                    Log.d(TAG, "URL 디코딩 성공: '$rawFileName' → '$decoded'")
//                    decoded
//                } catch (e: Exception) {
//                    Log.w(TAG, "URL 디코딩 실패, 원본 사용: '$rawFileName'", e)
//                    rawFileName
//                }
//
//                Log.d(TAG, "최종 추출된 파일명: '$decodedFileName'")
//                decodedFileName
//            } else {
//                Log.w(TAG, "정규식 매칭 실패 - Content-Disposition: '$contentDisposition'")
//                "PDF"
//            }
//        } else {
//            Log.w(TAG, "Content-Disposition 헤더가 없음, 기본값 사용")
//            "PDF"
//        }
//    }
//
//}
