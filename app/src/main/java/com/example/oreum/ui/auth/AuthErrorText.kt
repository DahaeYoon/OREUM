package com.example.oreum.ui.auth

import androidx.annotation.StringRes
import com.example.oreum.R
import com.example.oreum.data.model.AuthError

/** 오류 종류 → 화면에 보여 줄 문구 */
@StringRes
fun AuthError.messageRes(): Int = when (this) {
    AuthError.EMAIL_EMPTY -> R.string.error_email_empty
    AuthError.EMAIL_FORMAT -> R.string.error_email_format
    AuthError.PASSWORD_EMPTY -> R.string.error_password_empty
    AuthError.PASSWORD_RULE -> R.string.error_password_rule
    AuthError.PASSWORD_MISMATCH -> R.string.error_password_mismatch
    AuthError.NICKNAME_EMPTY -> R.string.error_nickname_empty
    AuthError.NICKNAME_LENGTH -> R.string.error_nickname_length
    AuthError.TERMS_REQUIRED -> R.string.error_terms_required
    AuthError.WRONG_CREDENTIALS -> R.string.error_wrong_credentials
    AuthError.EMAIL_TAKEN -> R.string.error_email_taken
}
