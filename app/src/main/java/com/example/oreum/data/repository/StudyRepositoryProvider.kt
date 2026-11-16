package com.example.oreum.data.repository

import com.example.oreum.data.local.LocalStudyRepository

/**
 * 앱 전체가 같은 StudyRepository 하나를 쓰도록 모아 두는 곳.
 * Firestore를 연결하면 여기 한 줄만 바꿔요.
 */
object StudyRepositoryProvider {
    val instance: StudyRepository by lazy { LocalStudyRepository() }
}
