package com.example.oreum.ui.calendar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.oreum.data.model.DailyLog
import com.example.oreum.data.model.Goal
import com.example.oreum.data.model.Task
import com.example.oreum.data.repository.AuthRepositoryProvider
import com.example.oreum.data.repository.StudyRepository
import com.example.oreum.data.repository.StudyRepositoryProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import java.time.LocalDate

enum class PlanView { WEEK, MONTH }

/** 계획 화면에서 고른 것: 주/월 · 선택한 날 · 목표 탭 (null = 전체) */
data class PlanSelection(
    val view: PlanView = PlanView.WEEK,
    val selected: LocalDate = LocalDate.now(),
    val goalId: String? = null,
)

data class PlanUiState(
    val selection: PlanSelection = PlanSelection(),
    /** 진행 중인 목표 (탭 · 안내에 사용) */
    val goals: List<Goal> = emptyList(),
    val tasks: List<Task> = emptyList(),
    val logs: List<DailyLog> = emptyList(),
    /** 승인 대기 초안이 있는 목표 id */
    val draftGoalIds: Set<String> = emptySet(),
    val userWeekdayMinutes: Int = 360,
    val userWeekendMinutes: Int = 480,
)

/** STEP 4 계획: 주 · 월 달력, 목표 탭, 할 일 체크 */
class PlanViewModel(
    private val repository: StudyRepository = StudyRepositoryProvider.instance,
) : ViewModel() {

    private val selection = MutableStateFlow(PlanSelection())

    val uiState: StateFlow<PlanUiState> =
        combine(repository.goals, repository.tasks, repository.logs, repository.roadmapDrafts, selection) { goals, tasks, logs, drafts, sel ->
            val active = goals.filter { it.completedAt == null }
            val user = AuthRepositoryProvider.instance.currentUser.value
            PlanUiState(
                // 고른 목표가 사라졌으면 전체로
                selection = if (sel.goalId != null && active.none { it.id == sel.goalId }) sel.copy(goalId = null) else sel,
                goals = active,
                tasks = tasks,
                logs = logs,
                draftGoalIds = drafts.keys.filter { id -> goals.any { it.id == id } }.toSet(),
                userWeekdayMinutes = user?.weekdayStudyMinutes ?: 360,
                userWeekendMinutes = user?.weekendStudyMinutes ?: 480,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PlanUiState())

    /** 다른 화면에서 '계획 보기'로 넘어왔으면 그 목표 탭을 열어요 */
    fun applyFocus() {
        val id = PlanFocus.goalId
        val date = PlanFocus.date
        if (id == null && date == null) return
        PlanFocus.goalId = null
        PlanFocus.date = null
        selection.update { it.copy(goalId = id ?: it.goalId, selected = date ?: LocalDate.now()) }
    }

    fun setView(view: PlanView) = selection.update { it.copy(view = view) }
    fun select(date: LocalDate) = selection.update { it.copy(selected = date) }
    fun setGoal(goalId: String?) = selection.update { it.copy(goalId = goalId) }
    fun shiftWeek(weeks: Long) = selection.update { it.copy(selected = it.selected.plusWeeks(weeks)) }

    /** 달 이동: 이번 달이면 오늘, 아니면 그 달 1일을 골라요 */
    fun shiftMonth(months: Long) = selection.update {
        val first = it.selected.withDayOfMonth(1).plusMonths(months)
        val today = LocalDate.now()
        it.copy(selected = if (first.year == today.year && first.month == today.month) today else first)
    }

    /** 체크 동그라미: 완료 ↔ 완료 취소. 바뀐 뒤 완료 여부를 돌려줘요 */
    fun toggleTask(taskId: String): Boolean? {
        val task = repository.task(taskId) ?: return null
        if (task.carriedOver) return null
        repository.setTaskDone(taskId, !task.isDone)
        return !task.isDone
    }
}
