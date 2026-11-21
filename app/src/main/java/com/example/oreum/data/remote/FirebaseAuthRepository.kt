package com.example.oreum.data.remote

import com.example.oreum.data.model.AuthError
import com.example.oreum.data.model.User
import com.example.oreum.data.repository.AuthRepository
import com.example.oreum.data.repository.AuthResult
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.UserProfileChangeRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Firebase Authentication (이메일 · 비밀번호) 로그인.
 * - 비밀번호는 앱에 저장하지 않아요. Firebase가 안전하게 보관 · 확인해요.
 * - 닉네임은 Firebase 사용자 프로필의 displayName에 저장해요. (사용자 정보 문서는 2번 Firestore 단계에서 추가)
 * - 로그인 상태는 Firebase가 기기에 기억해 두어서, 앱을 다시 열어도 currentUser가 바로 채워져요.
 */
class FirebaseAuthRepository(
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
) : AuthRepository {

    private val _currentUser = MutableStateFlow(auth.currentUser?.toUser())
    override val currentUser: StateFlow<User?> = _currentUser.asStateFlow()

    init {
        // 로그아웃 · 계정 만료 등 Firebase 쪽 상태 변화를 따라가요
        auth.addAuthStateListener { fa ->
            val fu = fa.currentUser
            if (fu == null) {
                _currentUser.value = null
            } else if (_currentUser.value?.id != fu.uid) {
                _currentUser.value = fu.toUser()
            }
        }
    }

    override suspend fun signIn(email: String, password: String): AuthResult = try {
        val fu = auth.signInWithEmailAndPassword(email.trim(), password).await().user
            ?: throw IllegalStateException("no user")
        val user = fu.toUser()
        _currentUser.value = user
        AuthResult.Success(user)
    } catch (e: Exception) {
        AuthResult.Failure(e.toAuthError())
    }

    override suspend fun signUp(nickname: String, email: String, password: String): AuthResult = try {
        val fu = auth.createUserWithEmailAndPassword(email.trim(), password).await().user
            ?: throw IllegalStateException("no user")
        // 닉네임 저장 (실패해도 가입은 된 상태라, 닉네임은 화면에 바로 보이도록 직접 채워요)
        runCatching { fu.updateProfile(profile(nickname.trim())).await() }
        val user = fu.toUser().copy(nickname = nickname.trim())
        _currentUser.value = user
        AuthResult.Success(user)
    } catch (e: Exception) {
        AuthResult.Failure(e.toAuthError())
    }

    override suspend fun sendPasswordReset(email: String): AuthError? = try {
        // 메일을 한국어로 보내요. 가입하지 않은 이메일이어도 (보안상) 오류 없이 끝날 수 있어요
        auth.setLanguageCode("ko")
        auth.sendPasswordResetEmail(email.trim()).await()
        null
    } catch (e: Exception) {
        when (e) {
            // 가입하지 않은 이메일도 '보냈어요'와 같은 문구로 안내해요 (가입 여부를 알려 주지 않도록)
            is FirebaseAuthInvalidUserException -> null
            is FirebaseAuthInvalidCredentialsException -> AuthError.EMAIL_FORMAT
            else -> e.toAuthError()
        }
    }

    override suspend fun updateNickname(nickname: String): AuthError? = try {
        val fu = auth.currentUser ?: throw IllegalStateException("no user")
        val name = nickname.trim()
        fu.updateProfile(profile(name)).await()
        _currentUser.value = (_currentUser.value ?: fu.toUser()).copy(nickname = name)
        null
    } catch (e: Exception) {
        e.toAuthError()
    }

    override fun updateUser(user: User) {
        val fu = auth.currentUser
        if (fu != null && fu.uid == user.id && fu.displayName != user.nickname) {
            fu.updateProfile(profile(user.nickname))
        }
        _currentUser.value = user
    }

    override fun signOut() {
        auth.signOut()
        _currentUser.value = null
    }

    private fun profile(nickname: String) = UserProfileChangeRequest.Builder().setDisplayName(nickname).build()

    private fun FirebaseUser.toUser() = User(
        id = uid,
        nickname = displayName.orEmpty(),
        email = email.orEmpty(),
        createdAt = metadata?.creationTimestamp ?: 0L,
    )

    /** Firebase 오류 → 화면 문구용 오류 종류 */
    private fun Exception.toAuthError(): AuthError = when (this) {
        // 약한 비밀번호는 '잘못된 정보' 오류의 한 종류라서 먼저 확인해요
        is FirebaseAuthWeakPasswordException -> AuthError.PASSWORD_RULE
        is FirebaseAuthUserCollisionException -> AuthError.EMAIL_TAKEN
        is FirebaseAuthInvalidUserException, is FirebaseAuthInvalidCredentialsException -> AuthError.WRONG_CREDENTIALS
        is FirebaseNetworkException -> AuthError.NETWORK
        is FirebaseTooManyRequestsException -> AuthError.TOO_MANY_REQUESTS
        else -> AuthError.UNKNOWN
    }
}
