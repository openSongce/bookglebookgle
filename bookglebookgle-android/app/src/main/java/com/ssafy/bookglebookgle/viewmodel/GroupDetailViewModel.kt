package com.ssafy.bookglebookgle.viewmodel

import androidx.lifecycle.ViewModel
import com.ssafy.bookglebookgle.repository.GroupRepositoryImpl
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class GroupDetailViewModel @Inject constructor(
    private val groupRepositoryImpl: GroupRepositoryImpl
) : ViewModel() {

}