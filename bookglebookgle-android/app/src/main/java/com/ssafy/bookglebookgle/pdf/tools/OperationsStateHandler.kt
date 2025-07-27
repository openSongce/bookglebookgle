package com.ssafy.bookglebookgle.pdf.tools

import com.ssafy.bookglebookgle.pdf.exception.ValidationErrorException
import com.ssafy.bookglebookgle.pdf.state.ResponseState


import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

class OperationsStateHandler(
    private val scope: CoroutineScope
) {
    private val _state = MutableLiveData<ResponseState>()
    val state: LiveData<ResponseState> = _state

    private var action: (suspend()-> ResponseState)? = null
    fun load(action: suspend () -> ResponseState) {
        this.action = action
        scope.launch {
            try {
                _state.postValue(ResponseState.Loading)
                val response = action()
                _state.postValue(response)
            } catch (e: ValidationErrorException) {
                _state.postValue(ResponseState.ValidationError(e.errorCode, e.message ?: "Something went wrong"))
            } catch (e: Exception) {
                _state.postValue(ResponseState.Failed(e.message ?: "Something went wrong"))
            }
        }
    }

    fun retry() {
        if (action!= null) {
            load(action!!)
        }
    }
}