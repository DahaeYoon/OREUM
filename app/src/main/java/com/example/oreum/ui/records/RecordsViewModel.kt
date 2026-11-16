package com.example.oreum.ui.records

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.oreum.data.model.DailyLog
import com.example.oreum.data.model.Goal
import com.example.oreum.data.model.User
import com.example.oreum.data.repository.AuthRepository
import com.example.oreum.data.repository.AuthRepositoryProvider
import com.example.oreum.data.repository.StudyRepository
import com.example.oreum.data.repository.StudyRepositoryProvider
import com.example.oreum.domain.usecase.RecordsAnalysis
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import java.time.LocalDate

enum class RecordsView { DAY, WEEK }

data class RecordsSelection(val view: RecordsView = RecordsView.DAY, val goalId: String? = null)

data class RecordsUiState(
    val selection: RecordsSelection = RecordsSelection(),
    val goals: List<Goal> = emptyList(),
    /** 기록한 날 (오래된 순) */
    val days: List<RecordsAnalysis.DayRecord> = emptyList(),
    val logs: List<DailyLog> = emptyList(),
    val pace: RecordsAnalysis.Pace = RecordsAnalysis.Pace(enough = false, days = 0),
    val weeks: List<RecordsAnalysis.WeekBar> = emptyList(),
    val reasons: List<RecordsAnalysis.ReasonCount> = emptyList(),
    val recoveries: List<RecordsAnalysis.RecoveryResult> = emptyList(),
    /** 다음 AI 계획에 반영 중인 페이스 (null이면 반영 안 함) */
    val appliedLoad: Float? = null,
    val appliedSession: Int? = null,
)

/** STEP 6 기록: 일일 · 주간, 계획 대비 실제, 반복되는 미완료 원인, 학습 페이스 분석(시뮬레이션) · 다음 계획에 반영 */
class RecordsViewModel(
    private val repository: StudyRepository = StudyRepositoryProvider.instance,
    private val auth: AuthRepository = AuthRepositoryProvider.instance,
) : ViewModel() {

    private val selection = MutableStateFlow(RecordsSelection())

    val uiState: StateFlow<RecordsUiState> =
        combine(
            combine(repository.tasks, repository.logs, repository.goals) { t, l, g -> Triple(t, l, g) },
            repository.recoveries,
            auth.currentUser,
            selection,
        ) { (tasks, logs, goals), recoveries, user, sel0 ->
            val sel = if (sel0.goalId != null && goals.none { it.id == sel0.goalId }) sel0.copy(goalId = null) else sel0
            val today = LocalDate.now()
            val days = RecordsAnalysis.loggedDays(tasks, logs, today.toEpochDay(), sel.goalId)
            RecordsUiState(
                selection = sel,
                goals = goals,
                days = days,
                logs = logs,
                pace = RecordsAnalysis.pace(days),
                weeks = RecordsAnalysis.weekBars(days, today),
                reasons = RecordsAnalysis.reasonCounts(days),
                recoveries = RecordsAnalysis.recoveryResults(
                    recoveries.filter { sel.goalId == null || it.goalId == sel.goalId },
                    days,
                ),
                appliedLoad = user?.paceRatio,
                appliedSession = user?.paceSessionMinutes,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RecordsUiState())

    fun setView(view: RecordsView) = selection.update { it.copy(view = view) }

    fun setGoal(goalId: String?) = selection.update { it.copy(goalId = goalId) }

    /** 다음 AI 계획 생성에 지금 페이스(하루 학습량 · 한 번에 공부할 시간)를 반영 */
    fun applyPace(): Boolean {
        val pace = uiState.value.pace
        val user = auth.currentUser.value ?: return false
        if (!pace.enough) return false
        auth.updateUser(user.copy(paceRatio = pace.load, paceSessionMinutes = pace.session))
        return true
    }

    fun clearPace() {
        val user: User = auth.currentUser.value ?: return
        auth.updateUser(user.copy(paceRatio = null, paceSessionMinutes = null))
    }
}
