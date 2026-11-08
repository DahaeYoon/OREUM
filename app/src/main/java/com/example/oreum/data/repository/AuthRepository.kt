package com.example.oreum.data.repository

import com.example.oreum.data.model.AuthError
import com.example.oreum.data.model.User
import kotlinx.coroutines.flow.StateFlow

/** 로그인 결과 */
sealed interface AuthResult {
    data class Success(val user: User) : AuthResult
    data class Failure(val error: AuthError) : AuthResult
}

/**
 * 인증 창구. 화면(ViewModel)은 이 인터페이스만 사용하고,
 * 실제 구현(지금은 LocalAuthRepository, 이후 FirebaseAuthRepository)은 바꿔 끼울 수 있어요.
 */
interface AuthRepository {
    /** 지금 로그인한 사용자 (없으면 null) */
    val currentUser: StateFlow<User?>

    suspend fun signIn(email: String, password: String): AuthResult

    suspend fun signUp(nickname: String, email: String, password: String): AuthResult

    fun signOut()
}
