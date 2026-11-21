package com.example.oreum.ui.study

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.oreum.data.model.Goal
import com.example.oreum.data.model.PlanStatus
import com.example.oreum.data.model.RoundStatus
import com.example.oreum.data.model.StudyRound
import com.example.oreum.data.repository.StudyRepository
import com.example.oreum.data.repository.StudyRepositoryProvider
import com.example.oreum.domain.usecase.RoadmapProgress
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/** 목표 카드 하나에 보여 줄 내용: 이름 · D-day / 상태 하나 / 진행률 / 단계 · 남은 학습 */
data class GoalCardUi(
    val goal: Goal,
    val progress: RoadmapProgress.Progress,
    val completed: Boolean,
    /** 승인 대기 초안이 있으면 DRAFT */
    val planStatus: PlanStatus,
    /** 지금 구간 (진행 중 · 시작 전) */
    val current: StudyRound?,
    /** AI 다음 구간 추천이 도착함 */
    val hasProposal: Boolean,
)

data class StudyUiState(
    val showCompleted: Boolean = false,
    val activeCount: Int = 0,
    val completedCount: Int = 0,
    /** 로드맵이 필요한 (로드맵 전 · 승인 대기) 진행 중 목표 수 */
    val needPlanCount: Int = 0,
    val cards: List<GoalCardUi> = emptyList(),
)

class StudyViewModel(
    private val repository: StudyRepository = StudyRepositoryProvider.instance,
) : ViewModel() {

    private val showCompleted = MutableStateFlow(false)

    val uiState: StateFlow<StudyUiState> =
        combine(repository.goals, repository.rounds, repository.roadmapDrafts, repository.proposals, showCompleted) { goals, rounds, drafts, proposals, showDone ->
            val all = goals.map { goal ->
                GoalCardUi(
                    goal = goal,
                    progress = RoadmapProgress.of(goal, rounds),
                    completed = goal.completedAt != null,
                    planStatus = if (drafts.containsKey(goal.id)) PlanStatus.DRAFT else goal.planStatus,
                    current = rounds.filter { it.goalId == goal.id && it.status != RoundStatus.DONE }.maxByOrNull { it.no },
                    hasProposal = proposals.containsKey(goal.id),
                )
            }
            val active = all.filterNot { it.completed }
            val done = all.filter { it.completed }
            val cards = (if (showDone) done else active)
                .sortedWith(
                    if (showDone) compareByDescending<GoalCardUi> { it.goal.completedAt }
                    else compareBy<GoalCardUi> { it.goal.dueAt }.thenBy { it.goal.priority },
                )
            StudyUiState(
                showCompleted = showDone,
                activeCount = active.size,
                completedCount = done.size,
                needPlanCount = active.count { it.planStatus != PlanStatus.APPROVED },
                cards = cards,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StudyUiState())

    fun setShowCompleted(completed: Boolean) {
        showCompleted.value = completed
    }
}
