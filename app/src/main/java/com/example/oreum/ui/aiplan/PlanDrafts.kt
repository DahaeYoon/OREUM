package com.example.oreum.ui.aiplan

import com.example.oreum.data.model.RoadmapDraft
import com.example.oreum.data.repository.StudyRepository
import com.example.oreum.data.repository.StudyRepositoryProvider
import java.time.LocalDate

/** 목표 하나에 대해 AI 로드맵 + 1구간 초안을 만들어 저장해요 (시뮬레이션). 목표 화면 · 목표 등록 · 홈에서 함께 사용 */
object PlanDrafts {

    fun generateFor(
        goalId: String,
        repository: StudyRepository = StudyRepositoryProvider.instance,
    ): RoadmapDraft? = repository.generateRoadmap(goalId, LocalDate.now().toEpochDay())
}
