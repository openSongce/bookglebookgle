package com.ssafy.bookglebookgle.repository

import android.util.Log
import com.ssafy.bookglebookgle.entity.pdf.AddBookmarkRequest
import com.ssafy.bookglebookgle.entity.pdf.AddCommentRequest
import com.ssafy.bookglebookgle.entity.pdf.AddHighlightRequest
import com.ssafy.bookglebookgle.entity.pdf.CoordinatesRequest
import com.ssafy.bookglebookgle.entity.pdf.UpdateCommentRequest
import com.ssafy.bookglebookgle.network.api.PdfApi
import com.ssafy.bookglebookgle.pdf.response.AnnotationListResponse
import com.ssafy.bookglebookgle.pdf.response.DeleteAnnotationResponse
import com.ssafy.bookglebookgle.pdf.tools.pdf.viewer.model.BookmarkModel
import com.ssafy.bookglebookgle.pdf.tools.pdf.viewer.model.CommentModel
import com.ssafy.bookglebookgle.pdf.tools.pdf.viewer.model.Coordinates
import com.ssafy.bookglebookgle.pdf.tools.pdf.viewer.model.HighlightModel
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import java.io.InputStream
import javax.inject.Inject

private const val TAG = "싸피_PdfRepositoryImpl"
class PdfRepositoryImpl @Inject constructor(
    private val pdfApi: PdfApi
) : PdfRepository {

    override suspend fun uploadPdf(file: File): Boolean {
        return try {
            val requestFile = file.asRequestBody("application/pdf".toMediaTypeOrNull())
            val filePart = MultipartBody.Part.createFormData("file", file.name, requestFile)

            val response = pdfApi.uploadPdf(filePart)
            Log.d("PdfUpload", "서버 응답 코드: ${response.code()}")
            Log.d("PdfUpload", "서버 응답 메시지: ${response.message()}")

            if (response.isSuccessful) {
                Log.d("PdfUpload", "업로드 성공")
                true
            } else {
                Log.d("PdfUpload", "업로드 실패 ${response.code()} ${response.message()}")
                false
            }
        } catch (e: Exception) {
            Log.e("PdfUpload", "예외 발생", e)
            false
        }
    }

    override suspend fun getGroupPdf(groupId: Long): InputStream? {
        return try {
            val response = pdfApi.getGroupPdf(groupId)
            if (response.isSuccessful) {
                response.body()?.byteStream()
            } else {
                Log.d(TAG, "PDF 다운로드 실패 - 응답코드: ${response.code()}, 메시지: ${response.message()}")
                null
            }
        } catch (e: Exception) {
            Log.d(TAG, "PDF 다운로드 중 예외 발생: ${e.message}", e)
            null
        }
    }

    // 댓글 관리
    override suspend fun addComment(
        groupId: Long,
        snippet: String,
        text: String,
        page: Int,
        coordinates: Coordinates
    ): CommentModel? {
        return try {
            val request = AddCommentRequest(
                snippet = snippet,
                text = text,
                page = page,
                coordinates = CoordinatesRequest(
                    startX = coordinates.startX,
                    startY = coordinates.startY,
                    endX = coordinates.endX,
                    endY = coordinates.endY
                )
            )

            val response = pdfApi.addComment(groupId, request)
            if (response.isSuccessful) {
                response.body()?.let { commentResponse ->
                    CommentModel(
                        id = commentResponse.id,
                        snippet = commentResponse.snippet,
                        text = commentResponse.text,
                        page = commentResponse.page,
                        updatedAt = commentResponse.updatedAt,
                        coordinates = Coordinates(
                            startX = commentResponse.coordinates.startX,
                            startY = commentResponse.coordinates.startY,
                            endX = commentResponse.coordinates.endX,
                            endY = commentResponse.coordinates.endY
                        )
                    )
                }
            } else {
                Log.e(TAG, "댓글 추가 실패: ${response.code()} ${response.message()}")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "댓글 추가 중 예외 발생", e)
            null
        }
    }

    override suspend fun getComments(groupId: Long): List<CommentModel>? {
        return try {
            val response = pdfApi.getComments(groupId)
            if (response.isSuccessful) {
                response.body()?.map { commentResponse ->
                    CommentModel(
                        id = commentResponse.id,
                        snippet = commentResponse.snippet,
                        text = commentResponse.text,
                        page = commentResponse.page,
                        updatedAt = commentResponse.updatedAt,
                        coordinates = Coordinates(
                            startX = commentResponse.coordinates.startX,
                            startY = commentResponse.coordinates.startY,
                            endX = commentResponse.coordinates.endX,
                            endY = commentResponse.coordinates.endY
                        )
                    )
                }
            } else {
                Log.e(TAG, "댓글 조회 실패: ${response.code()} ${response.message()}")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "댓글 조회 중 예외 발생", e)
            null
        }
    }

    override suspend fun updateComment(commentId: Long, newText: String): CommentModel? {
        return try {
            val request = UpdateCommentRequest(text = newText)
            val response = pdfApi.updateComment(commentId, request)

            if (response.isSuccessful) {
                response.body()?.let { commentResponse ->
                    CommentModel(
                        id = commentResponse.id,
                        snippet = commentResponse.snippet,
                        text = commentResponse.text,
                        page = commentResponse.page,
                        updatedAt = commentResponse.updatedAt,
                        coordinates = Coordinates(
                            startX = commentResponse.coordinates.startX,
                            startY = commentResponse.coordinates.startY,
                            endX = commentResponse.coordinates.endX,
                            endY = commentResponse.coordinates.endY
                        )
                    )
                }
            } else {
                Log.e(TAG, "댓글 수정 실패: ${response.code()} ${response.message()}")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "댓글 수정 중 예외 발생", e)
            null
        }
    }

    override suspend fun deleteComment(commentId: Long): DeleteAnnotationResponse? {
        return try {
            val response = pdfApi.deleteComment(commentId)
            if (response.isSuccessful) {
                response.body()
            } else {
                Log.e(TAG, "댓글 삭제 실패: ${response.code()} ${response.message()}")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "댓글 삭제 중 예외 발생", e)
            null
        }
    }

    // 하이라이트 관리
    override suspend fun addHighlight(
        groupId: Long,
        snippet: String,
        color: String,
        page: Int,
        coordinates: Coordinates
    ): HighlightModel? {
        return try {
            val request = AddHighlightRequest(
                snippet = snippet,
                color = color,
                page = page,
                coordinates = CoordinatesRequest(
                    startX = coordinates.startX,
                    startY = coordinates.startY,
                    endX = coordinates.endX,
                    endY = coordinates.endY
                )
            )

            val response = pdfApi.addHighlight(groupId, request)
            if (response.isSuccessful) {
                response.body()?.let { highlightResponse ->
                    HighlightModel(
                        id = highlightResponse.id,
                        snippet = highlightResponse.snippet,
                        color = highlightResponse.color,
                        page = highlightResponse.page,
                        updatedAt = highlightResponse.updatedAt,
                        coordinates = Coordinates(
                            startX = highlightResponse.coordinates.startX,
                            startY = highlightResponse.coordinates.startY,
                            endX = highlightResponse.coordinates.endX,
                            endY = highlightResponse.coordinates.endY
                        )
                    )
                }
            } else {
                Log.e(TAG, "하이라이트 추가 실패: ${response.code()} ${response.message()}")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "하이라이트 추가 중 예외 발생", e)
            null
        }
    }

    override suspend fun getHighlights(groupId: Long): List<HighlightModel>? {
        return try {
            val response = pdfApi.getHighlights(groupId)
            if (response.isSuccessful) {
                response.body()?.map { highlightResponse ->
                    HighlightModel(
                        id = highlightResponse.id,
                        snippet = highlightResponse.snippet,
                        color = highlightResponse.color,
                        page = highlightResponse.page,
                        updatedAt = highlightResponse.updatedAt,
                        coordinates = Coordinates(
                            startX = highlightResponse.coordinates.startX,
                            startY = highlightResponse.coordinates.startY,
                            endX = highlightResponse.coordinates.endX,
                            endY = highlightResponse.coordinates.endY
                        )
                    )
                }
            } else {
                Log.e(TAG, "하이라이트 조회 실패: ${response.code()} ${response.message()}")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "하이라이트 조회 중 예외 발생", e)
            null
        }
    }

    override suspend fun deleteHighlight(highlightId: Long): DeleteAnnotationResponse? {
        return try {
            val response = pdfApi.deleteHighlight(highlightId)
            if (response.isSuccessful) {
                response.body()
            } else {
                Log.e(TAG, "하이라이트 삭제 실패: ${response.code()} ${response.message()}")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "하이라이트 삭제 중 예외 발생", e)
            null
        }
    }

    // 북마크 관리
    override suspend fun addBookmark(groupId: Long, page: Int): BookmarkModel? {
        return try {
            val request = AddBookmarkRequest(page = page)
            val response = pdfApi.addBookmark(groupId, request)

            if (response.isSuccessful) {
                response.body()?.let { bookmarkResponse ->
                    BookmarkModel(
                        id = bookmarkResponse.id,
                        page = bookmarkResponse.page,
                        updatedAt = bookmarkResponse.updatedAt
                    )
                }
            } else {
                Log.e(TAG, "북마크 추가 실패: ${response.code()} ${response.message()}")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "북마크 추가 중 예외 발생", e)
            null
        }
    }

    override suspend fun getBookmarks(groupId: Long): List<BookmarkModel>? {
        return try {
            val response = pdfApi.getBookmarks(groupId)
            if (response.isSuccessful) {
                response.body()?.map { bookmarkResponse ->
                    BookmarkModel(
                        id = bookmarkResponse.id,
                        page = bookmarkResponse.page,
                        updatedAt = bookmarkResponse.updatedAt
                    )
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
            val response = pdfApi.deleteBookmark(bookmarkId)
            if (response.isSuccessful) {
                response.body()
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
            val response = pdfApi.deleteBookmarkByPage(groupId, page)
            if (response.isSuccessful) {
                response.body()
            } else {
                Log.e(TAG, "페이지별 북마크 삭제 실패: ${response.code()} ${response.message()}")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "페이지별 북마크 삭제 중 예외 발생", e)
            null
        }
    }

    // 전체 주석 조회
    override suspend fun getAllAnnotations(pdfId: Long): AnnotationListResponse? {
        return try {
            val response = pdfApi.getAllAnnotations(pdfId)
            if (response.isSuccessful) {
                response.body()?.let { annotationResponse ->
                    AnnotationListResponse(
                        comments = annotationResponse.comments.map { commentResponse ->
                            CommentModel(
                                id = commentResponse.id,
                                snippet = commentResponse.snippet,
                                text = commentResponse.text,
                                page = commentResponse.page,
                                updatedAt = commentResponse.updatedAt,
                                coordinates = commentResponse.coordinates?.let {
                                    Coordinates(
                                        startX = it.startX,
                                        startY = commentResponse.coordinates.startY,
                                        endX = commentResponse.coordinates.endX,
                                        endY = commentResponse.coordinates.endY
                                    )
                                }
                            )
                        } as ArrayList<CommentModel>,
                        highlights = annotationResponse.highlights.map { highlightResponse ->
                            HighlightModel(
                                id = highlightResponse.id,
                                snippet = highlightResponse.snippet,
                                color = highlightResponse.color,
                                page = highlightResponse.page,
                                updatedAt = highlightResponse.updatedAt,
                                coordinates = highlightResponse.coordinates?.let {
                                    Coordinates(
                                        startX = it.startX,
                                        startY = highlightResponse.coordinates.startY,
                                        endX = highlightResponse.coordinates.endX,
                                        endY = highlightResponse.coordinates.endY
                                    )
                                }
                            )
                        } as ArrayList<HighlightModel>,
                        bookmarks = annotationResponse.bookmarks.map { bookmarkResponse ->
                            BookmarkModel(
                                id = bookmarkResponse.id,
                                page = bookmarkResponse.page,
                                updatedAt = bookmarkResponse.updatedAt
                            )
                        } as ArrayList<BookmarkModel>
                    )
                }
            } else {
                Log.e(TAG, "전체 주석 조회 실패: ${response.code()} ${response.message()}")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "전체 주석 조회 중 예외 발생", e)
            null
        }
    }

}
