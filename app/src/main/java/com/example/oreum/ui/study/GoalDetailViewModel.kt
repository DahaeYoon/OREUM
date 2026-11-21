package com.example.oreum.ui.study

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.oreum.data.model.Goal
import com.example.oreum.data.model.NextProposal
import com.example.oreum.data.model.RoundStatus
import com.example.oreum.data.model.StudyRound
import com.example.oreum.data.repository.AiRequests
import com.example.oreum.data.repository.StudyRepository
import com.example.oreum.data.repository.StudyRepositoryProvider
import com.example.oreum.domain.usecase.RoadmapProgress
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate

data class GoalDetailUiState(
    val loaded: Boolean = false,
    val goal: Goal? = null,
    val progress: RoadmapProgress.Progress = RoadmapProgress.Progress(),
    /** AI 로드맵 승인 대기 초안이 있음 */
    val hasDraft: Boolean = false,
    /** 승인 전 AI 다음 구간 추천 */
    val proposal: NextProposal? = null,
    /** 지금 구간 (진행 중 · 시작 전) */
    val current: StudyRound? = null,
    /** 마친 구간 (최근 것부터) */
    val history: List<StudyRound> = emptyList(),
    val feasibility: RoadmapProgress.Feasibility? = null,
    val foldOpen: Boolean = false,
    /** 구간 결과를 제출했는데 다음 구간 추천이 아직 없음 */
    val needProposal: Boolean = false,
    /** AI 로드맵 요청 상태 · 다음 구간 요청 상태 (만드는 중 · 실패) */
    val aiRoadmap: AiRequests.State? = null,
    val aiNext: AiRequests.State? = null,
)

/** 목표 상세: 이번 구간 · 추천 · 로드맵 · 이전 구간 */
class GoalDetailViewModel(
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val repository: StudyRepository = StudyRepositoryProvider.instance
    val goalId: String = savedStateHandle.get<String>(ARG_GOAL_ID).orEmpty()
    private val foldOpen = MutableStateFlow(false)

    private fun today() = LocalDate.now().toEpochDay()

    val uiState: StateFlow<GoalDetailUiState> =
        combine(
            combine(repository.goals, repository.rounds, repository.sessions) { g, r, s -> Triple(g, r, s) },
            repository.roadmapDrafts,
            combine(repository.proposals, AiRequests.states) { p, a -> p to a },
            foldOpen,
        ) { (goals, rounds, sessions), drafts, (proposals, ai), fold ->
            val goal = goals.firstOrNull { it.id == goalId }
            val mine = rounds.filter { it.goalId == goalId }.sortedBy { it.no }
            GoalDetailUiState(
                loaded = true,
                goal = goal,
                progress = goal?.let { RoadmapProgress.of(it, rounds) } ?: RoadmapProgress.Progress(),
                hasDraft = drafts.containsKey(goalId),
                proposal = proposals[goalId],
                current = mine.lastOrNull { it.status != RoundStatus.DONE },
                history = mine.filter { it.status == RoundStatus.DONE }.reversed(),
                feasibility = goal?.takeIf { it.completedAt == null && it.roadmap.isNotEmpty() }
                    ?.let { RoadmapProgress.feasibility(it, rounds, sessions, today()) },
                foldOpen = fold,
                needProposal = goal != null && AiRequests.needsProposal(goal, rounds, proposals),
                aiRoadmap = ai[AiRequests.roadmapKey(goalId)],
                aiNext = ai[AiRequests.nextKey(goalId)],
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), GoalDetailUiState())

    fun toggleFold() {
        foldOpen.value = !foldOpen.value
    }

    fun toggleItem(roundId: String, itemId: String): StudyRound? = repository.toggleItem(roundId, itemId, today())

    fun startRound(roundId: String) = repository.startRound(roundId, today())

    fun deleteGoal() = repository.deleteGoal(goalId)

    companion object {
        const val ARG_GOAL_ID = "goalId"
    }
}
