package com.example.oreum.ui.aiplan

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.oreum.data.model.Goal
import com.example.oreum.data.model.PlanDraft
import com.example.oreum.data.repository.StudyRepository
import com.example.oreum.data.repository.StudyRepositoryProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate

data class PlanReviewUiState(
    val goal: Goal? = null,
    val draft: PlanDraft? = null,
    /** 펼친 주 차 (1부터) */
    val openWeeks: Set<Int> = setOf(1),
)

/** AI 계획 생성 결과 확인 · 수정 · 재생성 · 승인 */
class PlanReviewViewModel(
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val repository: StudyRepository = StudyRepositoryProvider.instance
    val goalId: String = savedStateHandle.get<String>(ARG_GOAL_ID).orEmpty()
    private val openWeeks = MutableStateFlow(setOf(1))

    val uiState: StateFlow<PlanReviewUiState> =
        combine(repository.goals, repository.drafts, openWeeks) { goals, drafts, open ->
            PlanReviewUiState(goal = goals.firstOrNull { it.id == goalId }, draft = drafts[goalId], openWeeks = open)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PlanReviewUiState())

    fun toggleWeek(week: Int) {
        openWeeks.value = if (week in openWeeks.value) openWeeks.value - week else openWeeks.value + week
    }

    /** 다시 생성: 직접 고친 내용은 사라져요 */
    fun regenerate() {
        PlanDrafts.generateFor(goalId, repository)
    }

    /** 승인하고 적용: 적용한 항목 수 */
    fun approve(): Int = repository.approveDraft(goalId, LocalDate.now().toEpochDay())

    /** 지금 남아 있는 (오늘 이후 · 미완료) 할 일이 있으면 승인 시 새 계획으로 바뀐다고 알려 줘요 */
    fun hasRemainingTasks(): Boolean {
        val today = LocalDate.now().toEpochDay()
        return repository.tasks.value.any { it.goalId == goalId && !it.isDone && (it.plannedDate ?: 0) >= today }
    }

    companion object {
        const val ARG_GOAL_ID = "goalId"
    }
}
