package com.example.oreum.ui.study

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.oreum.data.model.Goal
import com.example.oreum.data.model.GoalPeriod
import com.example.oreum.data.model.PlanStatus
import com.example.oreum.data.repository.StudyRepository
import com.example.oreum.data.repository.StudyRepositoryProvider
import com.example.oreum.domain.usecase.GoalProgress
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate

/** 목표 카드 하나에 보여 줄 내용 (프로토타입 g4 카드) */
data class GoalCardUi(
    val goal: Goal,
    val progress: GoalProgress.Summary,
    val completed: Boolean,
    /** 승인 대기 초안이 있으면 DRAFT */
    val planStatus: PlanStatus,
    /** 오늘 남은 할 일 수 */
    val todayCount: Int,
)

data class StudyUiState(
    /** null = 전체 */
    val period: GoalPeriod? = null,
    val showCompleted: Boolean = false,
    val activeCount: Int = 0,
    val completedCount: Int = 0,
    /** 계획이 필요한 (계획 전 · 승인 대기) 진행 중 목표 수 */
    val needPlanCount: Int = 0,
    val cards: List<GoalCardUi> = emptyList(),
)

private data class StudyFilter(val period: GoalPeriod?, val showCompleted: Boolean)

class StudyViewModel(
    private val repository: StudyRepository = StudyRepositoryProvider.instance,
) : ViewModel() {

    private val filter = MutableStateFlow(StudyFilter(period = null, showCompleted = false))

    val uiState: StateFlow<StudyUiState> =
        combine(repository.goals, repository.tasks, repository.drafts, filter) { goals, tasks, drafts, f ->
            val today = LocalDate.now().toEpochDay()
            val all = goals.map { goal ->
                GoalCardUi(
                    goal = goal,
                    progress = GoalProgress.of(goal.id, tasks),
                    completed = GoalProgress.isCompleted(goal, tasks),
                    planStatus = if (drafts.containsKey(goal.id)) PlanStatus.DRAFT else goal.planStatus,
                    todayCount = tasks.count { it.goalId == goal.id && it.plannedDate == today && !it.isDone && !it.carriedOver },
                )
            }
            val active = all.filterNot { it.completed }
            val done = all.filter { it.completed }
            val cards = (if (f.showCompleted) done else active)
                .filter { f.period == null || it.goal.period == f.period }
                .sortedWith(
                    if (f.showCompleted) compareByDescending<GoalCardUi> { it.goal.dueAt }
                    else compareBy<GoalCardUi> { it.goal.dueAt }.thenBy { it.goal.priority },
                )
            StudyUiState(
                period = f.period,
                showCompleted = f.showCompleted,
                activeCount = active.size,
                completedCount = done.size,
                needPlanCount = active.count { it.planStatus != PlanStatus.APPROVED },
                cards = cards,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StudyUiState())

    fun setPeriod(period: GoalPeriod?) {
        filter.value = filter.value.copy(period = period)
    }

    fun setShowCompleted(completed: Boolean) {
        filter.value = filter.value.copy(showCompleted = completed)
    }
}
