package com.example.oreum.data.local

import com.example.oreum.data.model.AuthError
import com.example.oreum.data.model.User
import com.example.oreum.data.repository.AuthRepository
import com.example.oreum.data.repository.AuthResult
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

/**
 * Firebase 연결 전 임시 인증 (앱 메모리에만 있음).
 * - 앱을 완전히 종료하면 새로 가입한 계정은 사라져요.
 * - 실제 서비스 인증이 아니므로 화면에도 "임시 계정" 안내를 표시해요.
 */
class LocalAuthRepository : AuthRepository {

    private data class Account(val user: User, val password: String)

    private val accounts = mutableMapOf(
        TEST_EMAIL to Account(
            user = User(id = "local-test", nickname = "윤다해", email = TEST_EMAIL),
            password = TEST_PASSWORD,
        ),
    )

    private val _currentUser = MutableStateFlow<User?>(null)
    override val currentUser: StateFlow<User?> = _currentUser.asStateFlow()

    override suspend fun signIn(email: String, password: String): AuthResult {
        delay(FAKE_NETWORK_DELAY_MS)
        val account = accounts[email.trim().lowercase()]
        if (account == null || account.password != password) {
            return AuthResult.Failure(AuthError.WRONG_CREDENTIALS)
        }
        _currentUser.value = account.user
        return AuthResult.Success(account.user)
    }

    override suspend fun signUp(nickname: String, email: String, password: String): AuthResult {
        delay(FAKE_NETWORK_DELAY_MS)
        val key = email.trim().lowercase()
        if (accounts.containsKey(key)) return AuthResult.Failure(AuthError.EMAIL_TAKEN)
        val user = User(
            id = "local-" + UUID.randomUUID().toString(),
            nickname = nickname.trim(),
            email = key,
            createdAt = System.currentTimeMillis(),
        )
        accounts[key] = Account(user, password)
        _currentUser.value = user
        return AuthResult.Success(user)
    }

    override fun signOut() {
        _currentUser.value = null
    }

    companion object {
        const val TEST_EMAIL = "test@oreum.app"
        const val TEST_PASSWORD = "oreum1234"
        private const val FAKE_NETWORK_DELAY_MS = 500L
    }
}
