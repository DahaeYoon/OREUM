package com.example.oreum.data.repository

import com.example.oreum.data.local.LocalStudyRepository

/**
 * 앱 전체가 같은 StudyRepository 하나를 쓰도록 모아 두는 곳.
 * 지금은 계정별 메모리 저장소(새 계정은 빈 상태)예요. Firestore를 연결하면 여기 한 줄만 바꿔요.
 * 로그인한 계정은 MainActivity가 switchOwner로 알려 줘요.
 */
object StudyRepositoryProvider {
    val instance: StudyRepository by lazy { LocalStudyRepository(ownerId = "", withSample = false) }
}
