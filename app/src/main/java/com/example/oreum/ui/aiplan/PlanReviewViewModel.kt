package com.example.oreum.ui.aiplan

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.oreum.data.model.Goal
import com.example.oreum.data.model.RoadmapDraft
import com.example.oreum.data.model.StudyRound
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
    val draft: RoadmapDraft? = null,
    /** 범위를 펼친 로드맵 단계 index */
    val openStages: Set<Int> = emptySet(),
    /** 학습 방법 · 완료 기준 펼침 */
    val foldOpen: Boolean = false,
)

/** AI 로드맵 · 1구간 계획 확인 · 항목 수정 · 재생성 · 승인 (시뮬레이션) */
class PlanReviewViewModel(
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val repository: StudyRepository = StudyRepositoryProvider.instance
    val goalId: String = savedStateHandle.get<String>(ARG_GOAL_ID).orEmpty()
    private val openStages = MutableStateFlow(emptySet<Int>())
    private val foldOpen = MutableStateFlow(false)

    val uiState: StateFlow<PlanReviewUiState> =
        combine(repository.goals, repository.roadmapDrafts, openStages, foldOpen) { goals, drafts, open, fold ->
            PlanReviewUiState(goal = goals.firstOrNull { it.id == goalId }, draft = drafts[goalId], openStages = open, foldOpen = fold)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PlanReviewUiState())

    private fun today() = LocalDate.now().toEpochDay()

    fun toggleStage(index: Int) {
        openStages.value = if (index in openStages.value) openStages.value - index else openStages.value + index
    }

    fun toggleFold() {
        foldOpen.value = !foldOpen.value
    }

    /** 재생성: 로드맵은 그대로, 1구간 구성만 바뀌어요 (직접 고친 항목은 사라짐) */
    fun regenerate(): RoadmapDraft? = repository.regenerateRoadmap(goalId, today())

    /** 승인하고 시작: 1구간이 바로 진행 중이 돼요 */
    fun approve(): StudyRound? = repository.approveRoadmap(goalId, today())

    companion object {
        const val ARG_GOAL_ID = "goalId"
    }
}
