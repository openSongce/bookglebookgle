package com.ssafy.bookglebookgle.network.api

import com.ssafy.bookglebookgle.entity.pdf.AddBookmarkRequest
import com.ssafy.bookglebookgle.entity.pdf.AddCommentRequest
import com.ssafy.bookglebookgle.entity.pdf.AddHighlightRequest
import com.ssafy.bookglebookgle.entity.pdf.BookmarkResponse
import com.ssafy.bookglebookgle.entity.pdf.CommentResponse
import com.ssafy.bookglebookgle.entity.pdf.HighlightResponse
import com.ssafy.bookglebookgle.entity.pdf.UpdateCommentRequest
import com.ssafy.bookglebookgle.pdf.response.AnnotationListResponse
import com.ssafy.bookglebookgle.pdf.response.DeleteAnnotationResponse
import okhttp3.MultipartBody
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Part
import retrofit2.http.Path

interface PdfApi {
    /**
     * PDF 파일 업로드 API
     * */
    @Multipart
    @POST("pdf/upload")
    suspend fun uploadPdf(
        @Part file: MultipartBody.Part,
    ): Response<Unit>

    /**
     * 특정 그룹의 PDF 파일 목록 조회 API
     * @param groupId 그룹 ID
     * @return PDF 파일 목록
     */
    @GET("group/{groupId}/pdf")
    suspend fun getGroupPdf(
        @Path("groupId") groupId: Long
    ): Response<ResponseBody>

    /**
    * 댓글 추가
    */
    @POST("pdf/{groupId}/comment")
    suspend fun addComment(
        @Path("groupId") groupId: Long,
        @Body request: AddCommentRequest
    ): Response<CommentResponse>

    /**
     * 특정 PDF의 모든 댓글 조회
     */
    @GET("pdf/{groupId}/comments")
    suspend fun getComments(
        @Path("groupId") groupId: Long
    ): Response<List<CommentResponse>>

    /**
     * 댓글 수정
     */
    @PUT("pdf/comment/{commentId}")
    suspend fun updateComment(
        @Path("commentId") commentId: Long,
        @Body request: UpdateCommentRequest
    ): Response<CommentResponse>

    /**
     * 댓글 삭제
     */
    @DELETE("pdf/comment/{commentId}")
    suspend fun deleteComment(
        @Path("commentId") commentId: Long
    ): Response<DeleteAnnotationResponse>

    /**
     * 하이라이트 추가
     */
    @POST("pdf/{groupId}/highlight")
    suspend fun addHighlight(
        @Path("groupId") groupId: Long,
        @Body request: AddHighlightRequest
    ): Response<HighlightResponse>

    /**
     * 특정 PDF의 모든 하이라이트 조회
     */
    @GET("pdf/{groupId}/highlights")
    suspend fun getHighlights(
        @Path("groupId") groupId: Long
    ): Response<List<HighlightResponse>>

    /**
     * 하이라이트 삭제
     */
    @DELETE("pdf/highlight/{highlightId}")
    suspend fun deleteHighlight(
        @Path("highlightId") highlightId: Long
    ): Response<DeleteAnnotationResponse>

    /**
     * 북마크 추가
     */
    @POST("pdf/{groupId}/bookmark")
    suspend fun addBookmark(
        @Path("groupId") groupId: Long,
        @Body request: AddBookmarkRequest
    ): Response<BookmarkResponse>

    /**
     * 특정 PDF의 모든 북마크 조회
     */
    @GET("pdf/{groupId}/bookmarks")
    suspend fun getBookmarks(
        @Path("groupId") groupId: Long
    ): Response<List<BookmarkResponse>>

    /**
     * 북마크 삭제
     */
    @DELETE("pdf/bookmark/{bookmarkId}")
    suspend fun deleteBookmark(
        @Path("bookmarkId") bookmarkId: Long
    ): Response<DeleteAnnotationResponse>

    /**
     * 페이지와 PDF ID로 북마크 삭제
     */
    @DELETE("pdf/{groupId}/bookmark/page/{page}")
    suspend fun deleteBookmarkByPage(
        @Path("groupId") groupId: Long,
        @Path("page") page: Int
    ): Response<DeleteAnnotationResponse>

    /**
     * 특정 PDF의 모든 주석(댓글, 하이라이트, 북마크) 조회
     */
    @GET("pdf/{pdfId}/annotations")
    suspend fun getAllAnnotations(
        @Path("pdfId") pdfId: Long
    ): Response<AnnotationListResponse>

}