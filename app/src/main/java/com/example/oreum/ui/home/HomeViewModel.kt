package com.example.oreum.ui.home

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** 홈 화면 상태. STEP 0에서는 MVVM 연결 확인용 값만 가지고 있어요. */
data class HomeUiState(
    val statusText: String = "연결됨",
)

class HomeViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()
}
