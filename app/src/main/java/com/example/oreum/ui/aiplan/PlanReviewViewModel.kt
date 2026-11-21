package com.example.oreum.ui.aiplan

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.oreum.data.model.Goal
import com.example.oreum.data.model.RoadmapDraft
import com.example.oreum.data.model.StudyRound
import com.example.oreum.data.repository.AiRequests
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
    /** 초안이 없을 때 AI 요청 상태 (만드는 중 · 실패). null = 요청 없음 */
    val ai: AiRequests.State? = null,
)

/** AI 로드맵 · 1구간 계획 확인 · 항목 수정 · 승인. 초안이 없으면 AI에게 요청해요 (실패하면 다시 시도) */
class PlanReviewViewModel(
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val repository: StudyRepository = StudyRepositoryProvider.instance
    val goalId: String = savedStateHandle.get<String>(ARG_GOAL_ID).orEmpty()
    private val openStages = MutableStateFlow(emptySet<Int>())
    private val foldOpen = MutableStateFlow(false)

    val uiState: StateFlow<PlanReviewUiState> =
        combine(repository.goals, repository.roadmapDrafts, openStages, foldOpen, AiRequests.states) { goals, drafts, open, fold, ai ->
            PlanReviewUiState(
                goal = goals.firstOrNull { it.id == goalId },
                draft = drafts[goalId],
                openStages = open,
                foldOpen = fold,
                ai = ai[AiRequests.roadmapKey(goalId)],
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PlanReviewUiState())

    init {
        // 초안이 없으면 AI에게 요청 (이미 만드는 중이면 그대로 기다려요 · 실패 상태면 '다시 시도'를 눌러야 다시 보내요)
        if (AiRequests.states.value[AiRequests.roadmapKey(goalId)] == null) AiRequests.requestRoadmap(goalId)
    }

    private fun today() = LocalDate.now().toEpochDay()

    fun retry() = AiRequests.requestRoadmap(goalId)

    fun toggleStage(index: Int) {
        openStages.value = if (index in openStages.value) openStages.value - index else openStages.value + index
    }

    fun toggleFold() {
        foldOpen.value = !foldOpen.value
    }

    /** 승인하고 시작: 1구간이 바로 진행 중이 돼요 */
    fun approve(): StudyRound? = repository.approveRoadmap(goalId, today())

    companion object {
        const val ARG_GOAL_ID = "goalId"
    }
}
