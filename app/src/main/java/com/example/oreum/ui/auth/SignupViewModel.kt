package com.example.oreum.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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

data class SignupUiState(
    val nickname: String = "",
    val email: String = "",
    val password: String = "",
    val passwordConfirm: String = "",
    val termsAgreed: Boolean = false,
    val nicknameError: AuthError? = null,
    val emailError: AuthError? = null,
    val passwordError: AuthError? = null,
    val passwordConfirmError: AuthError? = null,
    val formError: AuthError? = null,
    val isLoading: Boolean = false,
    /** 가입 성공 시 닉네임 (홈 이동 후 onNavigated()로 비워요) */
    val signedUpNickname: String? = null,
)

class SignupViewModel(
    private val authRepository: AuthRepository = AuthRepositoryProvider.instance,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SignupUiState())
    val uiState: StateFlow<SignupUiState> = _uiState.asStateFlow()

    fun onNicknameChanged(value: String) = _uiState.update {
        if (value == it.nickname) it else it.copy(nickname = value, nicknameError = null, formError = null)
    }

    fun onEmailChanged(value: String) = _uiState.update {
        if (value == it.email) it else it.copy(email = value, emailError = null, formError = null)
    }

    fun onPasswordChanged(value: String) = _uiState.update {
        if (value == it.password) it else it.copy(password = value, passwordError = null, formError = null)
    }

    fun onPasswordConfirmChanged(value: String) = _uiState.update {
        if (value == it.passwordConfirm) it else it.copy(passwordConfirm = value, passwordConfirmError = null, formError = null)
    }

    fun onTermsChanged(agreed: Boolean) = _uiState.update {
        it.copy(termsAgreed = agreed, formError = if (agreed) null else it.formError)
    }

    fun signUp() {
        val s = _uiState.value
        if (s.isLoading) return
        val nicknameError = AuthValidator.nickname(s.nickname)
        val emailError = AuthValidator.email(s.email)
        val passwordError = AuthValidator.newPassword(s.password)
        val confirmError = AuthValidator.passwordConfirm(s.password, s.passwordConfirm)
        val termsError = if (s.termsAgreed) null else AuthError.TERMS_REQUIRED
        if (listOf(nicknameError, emailError, passwordError, confirmError, termsError).any { it != null }) {
            _uiState.update {
                it.copy(
                    nicknameError = nicknameError,
                    emailError = emailError,
                    passwordError = passwordError,
                    passwordConfirmError = confirmError,
                    formError = termsError,
                )
            }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, formError = null) }
            when (val result = authRepository.signUp(s.nickname, s.email, s.password)) {
                is AuthResult.Success -> _uiState.update {
                    it.copy(isLoading = false, password = "", passwordConfirm = "", signedUpNickname = result.user.nickname)
                }
                is AuthResult.Failure -> _uiState.update {
                    if (result.error == AuthError.EMAIL_TAKEN) {
                        it.copy(isLoading = false, emailError = result.error)
                    } else {
                        it.copy(isLoading = false, formError = result.error)
                    }
                }
            }
        }
    }

    fun onNavigated() {
        _uiState.update { it.copy(signedUpNickname = null) }
    }
}
