package com.example.oreum.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.oreum.data.local.LocalAuthRepository
import com.example.oreum.data.model.AuthError
import com.example.oreum.data.repository.AuthRepository
import com.example.oreum.data.repository.AuthRepositoryProvider
import com.example.oreum.data.repository.AuthResult
import com.example.oreum.domain.usecase.AuthValidator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class LoginUiState(
    val email: String = "",
    val password: String = "",
    val emailError: AuthError? = null,
    val passwordError: AuthError? = null,
    val formError: AuthError? = null,
    val isLoading: Boolean = false,
    /** true가 되면 화면이 홈으로 이동하고 onNavigated()로 되돌려요 */
    val loggedIn: Boolean = false,
)

class LoginViewModel(
    private val authRepository: AuthRepository = AuthRepositoryProvider.instance,
) : ViewModel() {

    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    fun onEmailChanged(value: String) {
        if (value == _uiState.value.email) return
        _uiState.update { it.copy(email = value, emailError = null, formError = null) }
    }

    fun onPasswordChanged(value: String) {
        if (value == _uiState.value.password) return
        _uiState.update { it.copy(password = value, passwordError = null, formError = null) }
    }

    /** 테스트 계정 자동 입력 */
    fun fillTestAccount() {
        _uiState.update {
            it.copy(
                email = LocalAuthRepository.TEST_EMAIL,
                password = LocalAuthRepository.TEST_PASSWORD,
                emailError = null,
                passwordError = null,
                formError = null,
            )
        }
    }

    fun login() {
        val state = _uiState.value
        if (state.isLoading) return
        val emailError = AuthValidator.email(state.email)
        val passwordError = AuthValidator.loginPassword(state.password)
        if (emailError != null || passwordError != null) {
            _uiState.update { it.copy(emailError = emailError, passwordError = passwordError) }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, formError = null) }
            when (val result = authRepository.signIn(state.email, state.password)) {
                is AuthResult.Success -> _uiState.update { it.copy(isLoading = false, password = "", loggedIn = true) }
                is AuthResult.Failure -> _uiState.update { it.copy(isLoading = false, formError = result.error) }
            }
        }
    }

    fun onNavigated() {
        _uiState.update { it.copy(loggedIn = false) }
    }
}
