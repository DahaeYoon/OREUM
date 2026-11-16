package com.example.oreum.ui.aiplan

import com.example.oreum.data.model.PlanDraft
import com.example.oreum.data.repository.AuthRepositoryProvider
import com.example.oreum.data.repository.StudyRepository
import com.example.oreum.data.repository.StudyRepositoryProvider
import com.example.oreum.domain.usecase.PlanGenerator
import java.time.LocalDate
import java.util.UUID

/** 목표 하나에 대해 AI 계획 초안을 만들어 저장해요 (시뮬레이션). 목표 화면 · 목표 등록 · 재생성에서 함께 사용 */
object PlanDrafts {

    fun generateFor(
        goalId: String,
        repository: StudyRepository = StudyRepositoryProvider.instance,
    ): PlanDraft? {
        val goal = repository.goal(goalId) ?: return null
        val user = AuthRepositoryProvider.instance.currentUser.value
        val draft = PlanGenerator.generate(
            goal = goal,
            today = LocalDate.now(),
            otherTasks = repository.tasks.value,
            userWeekdayMinutes = user?.weekdayStudyMinutes ?: 360,
            userWeekendMinutes = user?.weekendStudyMinutes ?: 480,
            paceRatio = user?.paceRatio,
            paceSessionMinutes = user?.paceSessionMinutes,
            newId = { "dp-" + UUID.randomUUID().toString() },
        )
        repository.saveDraft(draft)
        return draft
    }
}
