package com.ssafy.bookglebookgle.pdf.state

sealed class ResponseState {
    data object Loading: ResponseState() // 요청 처리 중인 상태
    class Success<T>(val response: T?) : ResponseState() // 요청이 성공적으로 완료된 상태
    class ValidationError(val errorCode: Int,val error: String) : ResponseState() // 유효성 검사 실패
    class Failed(val error: String) : ResponseState() // 일반적인 실패 상황
}