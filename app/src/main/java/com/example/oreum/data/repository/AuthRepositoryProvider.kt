package com.example.oreum.data.repository

import com.example.oreum.data.remote.FirebaseAuthRepository

/**
 * 앱 전체가 같은 AuthRepository 하나를 쓰도록 모아 두는 곳.
 * 1번부터 Firebase Authentication(이메일 · 비밀번호)을 써요.
 * (Firebase 없이 화면만 확인하려면 LocalAuthRepository()로 바꾸면 돼요)
 */
object AuthRepositoryProvider {
    val instance: AuthRepository by lazy { FirebaseAuthRepository() }
}
