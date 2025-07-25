package com.ssafy.bookglebookgle.pdf_room.repository

import com.ssafy.bookglebookgle.pdf_room.exception.ValidationErrorException
import com.ssafy.bookglebookgle.pdf_room.response.AnnotationListResponse
import com.ssafy.bookglebookgle.pdf_room.response.DeleteAnnotationResponse
import com.ssafy.bookglebookgle.pdf_room.response.PdfNoteListModel
import com.ssafy.bookglebookgle.pdf_room.response.PdfNotesResponse
import com.ssafy.bookglebookgle.pdf_room.response.RemoveTagResponse
import com.ssafy.bookglebookgle.pdf_room.response.StatusMessageResponse
import com.ssafy.bookglebookgle.pdf_room.response.TagModel
import com.ssafy.bookglebookgle.pdf_room.tools.pdf.viewer.model.BookmarkModel
import com.ssafy.bookglebookgle.pdf_room.tools.pdf.viewer.model.CommentModel
import com.ssafy.bookglebookgle.pdf_room.tools.pdf.viewer.model.Coordinates
import com.ssafy.bookglebookgle.pdf_room.tools.pdf.viewer.model.HighlightModel
import com.ssafy.bookglebookgle.pdf_room.room.Dao
import com.ssafy.bookglebookgle.pdf_room.room.entity.BookmarkEntity
import com.ssafy.bookglebookgle.pdf_room.room.entity.CommentEntity
import com.ssafy.bookglebookgle.pdf_room.room.entity.HighlightEntity
import com.ssafy.bookglebookgle.pdf_room.room.entity.PdfNoteEntity
import com.ssafy.bookglebookgle.pdf_room.room.entity.PdfTagEntity
import com.ssafy.bookglebookgle.pdf_room.state.ResponseState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

class PdfRepositoryImpl @Inject constructor(
    private val dao: Dao,
) : PDFRepository {

    /**
     * 새로운 PDF를 데이터베이스에 추가
     * PdfNoteEntity로 변환 후 dao.addPdfNote()를 호출하여 추가
     * */
    override suspend fun addNewPdf(
        filePath: String,
        title: String,
        about: String?,
        tagId: Long?
    ): ResponseState {
        return withContext(Dispatchers.IO) {
            try {
                val pdfEntry = PdfNoteEntity(
                    null,
                    title,
                    filePath,
                    about,
                    tagId,
                    System.currentTimeMillis()
                )

                val id = dao.addPdfNote(pdfEntry) // Todo: 실제 데이터베이스 API 호출로 변경
                if (id != -1L){
                    val tagModel = tagId?.let { dao.getTagById(it) }?.let {
                        TagModel(it.id ?: -1, it.title, it.colorCode)
                    }
                    val model = PdfNoteListModel(
                        id,
                        title,
                        tagModel,
                        about,
                        filePath,
                        pdfEntry.updateAt
                    )
                    return@withContext ResponseState.Success<PdfNoteListModel>(model)
                }
                throw java.lang.Exception("PDF 추가 실패")
            } catch (e: Exception) {
                return@withContext ResponseState.Failed(e.message ?: "PDF 추가 에러")
            }
        }
    }

    /**
     * 모든 PDF 목록을 가져오는 함수
     * 가져온 데이터를 PdfNoteListModel 리스트에 저장
     * */
    override suspend fun getAllPdfs(): ResponseState {
        return withContext(Dispatchers.IO) {
            try {
                val notes = dao.getAllPdfNotes() // Todo: 실제 데이터베이스 API 호출로 변경
                val pdfNotes = notes.map {
                    val tagModel = it.tagId?.let {tagId-> dao.getTagById(tagId) }?.let { tag->
                        TagModel(tag.id ?: -1, tag.title, tag.colorCode)
                    }
                    PdfNoteListModel(
                        it.id?:-1,
                        it.title,
                        tagModel,
                        it.about,
                        it.filePath,
                        it.updateAt
                    )
                }
                return@withContext ResponseState.Success<PdfNotesResponse>(PdfNotesResponse(pdfNotes))

            } catch (e: Exception) {
                return@withContext ResponseState.Failed(e.message ?: "PDF 조회 에러")
            }
        }
    }

    /**
     * 해당 id의 PDF를 찾아 관련된 댓글/하이라이트/북마크를 모두 삭제
     * */
    override suspend fun deletePdf(pdfId: Long): ResponseState {
        return withContext(Dispatchers.IO) {
            try {
                val pdf = dao.getPdfById(pdfId)?: throw Exception("이미 삭제된 PDF 입니다.") // Todo: 실제 데이터베이스 API 호출로 변경
                dao.deleteAllCommentsByPdfId(pdfId)
                dao.deleteAllHighlightsByPdfId(pdfId)
                dao.deleteAllBookmarksWithPdfId(pdfId)
//                val pdfFile = File(pdf.filePath) Todo: 실제 파일 삭제 필요 시 주석 해제
//                pdfFile.delete()
                dao.deletePdfById(pdfId)
                return@withContext ResponseState.Success<StatusMessageResponse>(StatusMessageResponse("PDF 삭제 성공"))
            } catch (e: Exception) {
                return@withContext ResponseState.Failed(e.message ?: "PDF 삭제 에러")
            }
        }
    }

    /**
     * 새로운 태그를 추가하는 함수
     * */
    override suspend fun addTag(title: String, color: String): ResponseState {
        return withContext(Dispatchers.IO) {
            try {
                val allTags = dao.getAllTags() // Todo: 실제 데이터베이스 API 호출로 변경
                val alreadyExistName = allTags.indexOfFirst { it.title == title } != -1
                if (alreadyExistName) {
                    throw ValidationErrorException(1,"이미 같은 이름의 TAG가 존재합니다.")
                }

                val tagEntity = PdfTagEntity(null,title,color)
                val id = dao.addPdfTag(tagEntity) // Todo: 실제 데이터베이스 API 호출로 변경
                return@withContext ResponseState.Success<TagModel>(TagModel(id,title,color))
            } catch (e: Exception) {
                return@withContext ResponseState.Failed(e.message ?: "TAG 추가 에러")
            }
        }
    }

    /**
     * 모든 태그를 가져오는 함수
     * */
    override suspend fun getAllTags(): ResponseState {
        return withContext(Dispatchers.IO) {
            try {
                val tags = dao.getAllTags().map { // Todo: 실제 데이터베이스 API 호출로 변경
                    TagModel(it.id?:-1,it.title,it.colorCode)
                }
                return@withContext ResponseState.Success<List<TagModel>>(tags)
            } catch (e: Exception) {
                return@withContext ResponseState.Failed(e.message ?: "TAG 조회 에러")
            }
        }
    }

    /**
     * 해당 ID의 태그 삭제
     * */
    override suspend fun removeTagById(tagId: Long): ResponseState {
        return withContext(Dispatchers.IO) {
            try {
                dao.removeTagById(tagId) // Todo: 실제 데이터베이스 API 호출로 변경
                return@withContext ResponseState.Success<RemoveTagResponse>(RemoveTagResponse(tagId))
            } catch (e: Exception) {
                return@withContext ResponseState.Failed(e.message ?: "TAG 삭제 에러")
            }
        }
    }

    /**
     * 댓글 추가 함수
     * */
    override suspend fun addComment(
        pdfId: Long,
        snippet: String, // PDF에서 선택된 텍스트
        text: String, // 댓글 내용
        page: Int, // 댓글이 작성된 페이지
        coordinates: Coordinates // 댓글이 작성된 위치 좌표
    ): ResponseState {
        return withContext(Dispatchers.IO) {
            try {
                val commentEntity = CommentEntity(
                    null,
                    pdfId,
                    snippet,
                    text,
                    page,
                    System.currentTimeMillis(),
                    coordinates
                )
                val id = dao.insertComment(commentEntity) // Todo: 실제 데이터베이스 API 호출로 변경
                return@withContext ResponseState.Success<CommentModel>(
                    CommentModel(
                        id,
                        snippet,
                        text,
                        page,
                        commentEntity.updatedAt,
                        coordinates
                    )
                )
            } catch (e: Exception) {
                return@withContext ResponseState.Failed(e.message ?: "댓글 추가 에러")
            }
        }
    }

    /**
     * 특정 PDF에 대한 모든 댓글을 가져오는 함수
     * */
    override suspend fun getAllComments(pdfId: Long): ResponseState {
        return withContext(Dispatchers.IO) {
            try {
                val comments = dao.getCommentsOfPdf(pdfId).map { // Todo: 실제 데이터베이스 API 호출로 변경
                    CommentModel(
                        it.id?:-1L,
                        it.snippet,
                        it.text,
                        it.page,
                        it.updatedAt,
                        it.coordinates
                    )
                }
                return@withContext ResponseState.Success<List<CommentModel>>(comments)
            } catch (e: Exception) {
                return@withContext ResponseState.Failed(e.message ?: "댓글 조회 에러")
            }
        }
    }

    /**
     * 여러개의 댓글 ID를 받아 모두 삭제
     * */
    override suspend fun deleteComments(commentIds: List<Long>): ResponseState {
        return withContext(Dispatchers.IO) {
            try {
                dao.deleteCommentsWithIds(commentIds) // Todo: 실제 데이터베이스 API 호출로 변경
                return@withContext ResponseState.Success<DeleteAnnotationResponse>(DeleteAnnotationResponse(commentIds))
            } catch (e: Exception) {
                return@withContext ResponseState.Failed(e.message ?: "댓글 삭제 에러")
            }
        }
    }

    /**
     * 해당 댓글 ID의 내용 수정
     * */
    override suspend fun updateComment(commentId: Long, newText: String): ResponseState {
        return withContext(Dispatchers.IO) {
            try {
                val comment = dao.getCommentWithId(commentId) ?: throw Exception("존재하지 않는 댓글입니다.") // Todo: 실제 데이터베이스 API 호출로 변경
                val updatedAt = System.currentTimeMillis()
                dao.updateComment(commentId,newText, updatedAt) // Todo: 실제 데이터베이스 API 호출로 변경
                return@withContext ResponseState.Success<CommentModel>(
                    CommentModel(
                    comment.id?:-1,
                    comment.snippet,
                    newText,
                    comment.page,
                    updatedAt,
                    comment.coordinates
                )
                )
            } catch (e: Exception) {
                return@withContext ResponseState.Failed(e.message ?: "댓글 수정 에러")
            }
        }
    }

    /**
     * 하이라이트 추가
     * */
    override suspend fun addHighlight(
        pdfId: Long,
        snippet: String,
        color: String,
        page: Int,
        coordinates: Coordinates
    ): ResponseState {
        return withContext(Dispatchers.IO) {
            try {
                val highlightEntity = HighlightEntity(
                    null,
                    pdfId,
                    snippet,
                    color,
                    page,
                    System.currentTimeMillis(),
                    coordinates
                )
                val id = dao.insertHighlight(highlightEntity) // Todo: 실제 데이터베이스 API 호출로 변경
                return@withContext ResponseState.Success<HighlightModel>(
                    HighlightModel(
                        id,
                        snippet,
                        color,
                        page,
                        highlightEntity.updatedAt,
                        coordinates
                    )
                )
            } catch (e: Exception) {
                return@withContext ResponseState.Failed(e.message ?: "하이라이트 추가 에러")
            }
        }
    }

    /**
     * 특정 PDF에 대한 모든 하이라이트를 가져오는 함수
     * */
    override suspend fun getAllHighlight(pdfId: Long): ResponseState {
        return withContext(Dispatchers.IO) {
            try {
                val highlights = dao.getHighlightsOfPdf(pdfId).map { // Todo: 실제 데이터베이스 API 호출로 변경
                    HighlightModel(
                        it.id?:-1L,
                        it.snippet,
                        it.color,
                        it.page,
                        it.updatedAt,
                        it.coordinates
                    )
                }
                return@withContext ResponseState.Success<List<HighlightModel>>(highlights)
            } catch (e: Exception) {
                return@withContext ResponseState.Failed(e.message ?: "하이라이트 조회 에러")
            }
        }
    }

    /**
     * 특정 ID에 해당하는 하이라이트 삭제
     * */
    override suspend fun deleteHighlight(highlightIds: List<Long>): ResponseState {
        return withContext(Dispatchers.IO) {
            try {
                dao.deleteHighlightsWithIds(highlightIds) // Todo: 실제 데이터베이스 API 호출로 변경
                return@withContext ResponseState.Success<DeleteAnnotationResponse>(DeleteAnnotationResponse(highlightIds))
            } catch (e: Exception) {
                return@withContext ResponseState.Failed(e.message ?: "하이라이트 삭제 에러")
            }
        }
    }

    /**
     * 북마크 추가
     * */
    override suspend fun addBookmark(
        pdfId: Long,
        page: Int
    ): ResponseState {
        return withContext(Dispatchers.IO) {
            try {
                val bookmarkEntity = BookmarkEntity(
                    null,
                    pdfId,
                    page,
                    System.currentTimeMillis()
                )
                val id = dao.insertBookmark(bookmarkEntity) // Todo: 실제 데이터베이스 API 호출로 변경
                return@withContext ResponseState.Success<BookmarkModel>(
                    BookmarkModel(
                        id,
                        page,
                        bookmarkEntity.updatedAt,
                    )
                )
            } catch (e: Exception) {
                return@withContext ResponseState.Failed(e.message ?: "북마크 추가 에러")
            }
        }
    }

    /**
     * 특정 PDF의 북마크 전체 조회
     * */
    override suspend fun getAllBookmark(pdfId: Long): ResponseState {
        return withContext(Dispatchers.IO) {
            try {
                val bookmarks = dao.getBookmarksOfPdf(pdfId).map { // Todo: 실제 데이터베이스 API 호출로 변경
                    BookmarkModel(
                        it.id?:-1L,
                        it.page,
                        it.updatedAt
                    )
                }
                return@withContext ResponseState.Success<List<BookmarkModel>>(bookmarks)
            } catch (e: Exception) {
                return@withContext ResponseState.Failed(e.message ?: "북마크 조회 에러")
            }
        }
    }

    /**
     * 여러 북마크 삭제
     * */
    override suspend fun deleteBookmarks(bookmarkIds: List<Long>): ResponseState {
        return withContext(Dispatchers.IO) {
            try {
                dao.deleteBookmarksWithIds(bookmarkIds) // Todo: 실제 데이터베이스 API 호출로 변경
                return@withContext ResponseState.Success<Nothing>(null)
            } catch (e: Exception) {
                return@withContext ResponseState.Failed(e.message ?: "북마크 삭제 에러")
            }
        }
    }

    /**
     * 특정 페이지와 PDF ID에 해당하는 북마크 삭제
     * */
    override suspend fun deleteBookmarkWithPageAndPdfId(page: Int, pdfId: Long): ResponseState {
        return withContext(Dispatchers.IO) {
            try {
                val bookmarksIds = dao.getBookmarksWithPageAndPdfId(page, pdfId).map { it.id?:-1 } // Todo: 실제 데이터베이스 API 호출로 변경
                dao.deleteBookmarksWithIds(bookmarksIds) // Todo: 실제 데이터베이스 API 호출로 변경
                return@withContext ResponseState.Success<DeleteAnnotationResponse>(DeleteAnnotationResponse(bookmarksIds))
            } catch (e: Exception) {
                return@withContext ResponseState.Failed(e.message ?: "북마크 삭제 에러")
            }
        }
    }

    /**
     * 특정 PDF에 대한 모든 주석(댓글, 하이라이트, 북마크)을 가져오는 함수
     * */
    override suspend fun getAllAnnotations(pdfId: Long): ResponseState {
        return withContext(Dispatchers.IO) {
            try {

                val comments = dao.getCommentsOfPdf(pdfId).map { // Todo: 실제 데이터베이스 API 호출로 변경
                    CommentModel(
                        it.id ?: -1L,
                        it.snippet,
                        it.text,
                        it.page,
                        it.updatedAt,
                        it.coordinates
                    )
                }

                val highlights = dao.getHighlightsOfPdf(pdfId).map { // Todo: 실제 데이터베이스 API 호출로 변경
                    HighlightModel(
                        it.id ?: -1L,
                        it.snippet,
                        it.color,
                        it.page,
                        it.updatedAt,
                        it.coordinates
                    )
                }

                val bookmarks = dao.getBookmarksOfPdf(pdfId).map { // Todo: 실제 데이터베이스 API 호출로 변경
                    BookmarkModel(
                        it.id ?: -1L,
                        it.page,
                        it.updatedAt
                    )
                }

                return@withContext ResponseState.Success<AnnotationListResponse>(
                    AnnotationListResponse(
                        ArrayList(comments),
                        ArrayList(highlights),
                        ArrayList(bookmarks),
                    )
                )
            } catch (e: Exception) {
                return@withContext ResponseState.Failed(e.message ?: "주석 조회 에러")
            }
        }
    }
}