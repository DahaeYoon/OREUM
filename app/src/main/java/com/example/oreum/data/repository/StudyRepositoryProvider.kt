package com.example.oreum.data.repository

import com.example.oreum.data.remote.FirestoreStudyRepository

/**
 * 앱 전체가 같은 StudyRepository 하나를 쓰도록 모아 두는 곳.
 * 2번부터 Firestore에 계정별로 저장해요 (users/{uid}/...). 로그인한 계정은 MainActivity가 switchOwner로 알려 줘요.
 * (Firebase 없이 화면만 확인하려면 LocalStudyRepository(ownerId = "", withSample = false)로 바꾸면 돼요)
 */
object StudyRepositoryProvider {
    val instance: StudyRepository by lazy { FirestoreStudyRepository() }
}
