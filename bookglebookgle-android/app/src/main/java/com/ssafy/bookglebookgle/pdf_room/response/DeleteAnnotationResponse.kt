package com.ssafy.bookglebookgle.pdf_room.response

// 주석(댓글, 하이라이트, 북마크 등)을 삭제한 후 어떤 ID들이 삭제되었는지 응답받는 모델
data class DeleteAnnotationResponse(val deletedIds: List<Long>)