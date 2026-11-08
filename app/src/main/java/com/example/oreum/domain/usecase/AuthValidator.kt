package com.example.oreum.domain.usecase

import com.example.oreum.data.model.AuthError

/** 입력값 검사 규칙 (Android · Firebase와 무관한 순수 Kotlin이라 단위 테스트하기 쉬워요) */
object AuthValidator {

    private val EMAIL_REGEX = Regex("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")

    fun email(value: String): AuthError? = when {
        value.isBlank() -> AuthError.EMAIL_EMPTY
        !EMAIL_REGEX.matches(value.trim()) -> AuthError.EMAIL_FORMAT
        else -> null
    }

    /** 로그인할 때는 비어 있는지만 확인 */
    fun loginPassword(value: String): AuthError? =
        if (value.isEmpty()) AuthError.PASSWORD_EMPTY else null

    /** 가입할 때: 8자 이상, 영문과 숫자를 함께 */
    fun newPassword(value: String): AuthError? = when {
        value.isEmpty() -> AuthError.PASSWORD_EMPTY
        value.length < 8 || value.none { it.isLetter() } || value.none { it.isDigit() } -> AuthError.PASSWORD_RULE
        else -> null
    }

    fun passwordConfirm(password: String, confirm: String): AuthError? = when {
        confirm.isEmpty() -> AuthError.PASSWORD_EMPTY
        password != confirm -> AuthError.PASSWORD_MISMATCH
        else -> null
    }

    fun nickname(value: String): AuthError? = when {
        value.isBlank() -> AuthError.NICKNAME_EMPTY
        value.trim().length !in 2..10 -> AuthError.NICKNAME_LENGTH
        else -> null
    }
}
