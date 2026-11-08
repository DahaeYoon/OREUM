package com.example.oreum.data.repository

import com.example.oreum.data.local.LocalAuthRepository

/**
 * 앱 전체가 같은 AuthRepository 하나를 쓰도록 모아 두는 곳.
 * Firebase Authentication을 연결하면 여기 한 줄만 FirebaseAuthRepository로 바꿔요.
 */
object AuthRepositoryProvider {
    val instance: AuthRepository by lazy { LocalAuthRepository() }
}
