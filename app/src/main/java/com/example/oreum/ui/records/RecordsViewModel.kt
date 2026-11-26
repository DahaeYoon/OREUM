package com.example.oreum.ui.records

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.oreum.data.model.Goal
import com.example.oreum.data.model.StudyRound
import com.example.oreum.data.repository.AuthRepositoryProvider
import com.example.oreum.data.repository.StudyRepositoryProvider
import com.example.oreum.domain.usecase.RoundRecords
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import java.time.LocalDate

enum class RecordsView { DAY, ROUND }

data class RecordsSelection(val view: RecordsView = RecordsView.DAY, val goalId: String? = null)

data class RecordsUiState(
    val loaded: Boolean = false,
    val selection: RecordsSelection = RecordsSelection(),
    val goals: List<Goal> = emptyList(),
    /** 공부한 날 (오래된 순) */
    val days: List<RoundRecords.Day> = emptyList(),
    /** 최근 7일 (오래된 순): 날짜 · 분 */
    val week: List<Pair<Long, Int>> = emptyList(),
    val pace: RoundRecords.Pace? = null,
    /** 구간별 목록 (최근 순) */
    val rounds: List<StudyRound> = emptyList(),
    val today: Long = 0L,
)

data class RecordsStreakUiState(val date: Long, val streak: RoundRecords.Streak)

/** 기록 (1차): 날짜별 · 구간별 목록 + 페이스 카드. 모두 실제 학습 기록(구간 · 학습 시간) 기준이에요. */
class RecordsViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = StudyRepositoryProvider.instance
    private val auth = AuthRepositoryProvider.instance
    private val preferences = application.getSharedPreferences("study_rest_days", Context.MODE_PRIVATE)
    private val selection = MutableStateFlow(RecordsSelection())
    private val currentDay = MutableStateFlow(LocalDate.now().toEpochDay())
    private val restDayChanges = MutableStateFlow(0)
    private val userId = auth.currentUser.map { it?.id }.distinctUntilChanged()

    val streakState = combine(repository.sessions, userId, currentDay, restDayChanges) { sessions, id, today, _ ->
        id?.let { RecordsStreakUiState(today, RoundRecords.streak(sessions, restDays(it), today)) }
    }.distinctUntilChanged()

    val uiState: StateFlow<RecordsUiState> =
        combine(repository.goals, repository.rounds, repository.sessions, selection, currentDay) { goals, rounds, sessions, sel0, today ->
            val sel = if (sel0.goalId != null && goals.none { it.id == sel0.goalId }) sel0.copy(goalId = null) else sel0
            RecordsUiState(
                loaded = true,
                selection = sel,
                goals = goals,
                days = RoundRecords.studyDays(rounds, sessions, sel.goalId, today),
                week = RoundRecords.last7(sessions, sel.goalId, today),
                pace = RoundRecords.pace(rounds, sel.goalId),
                rounds = RoundRecords.roundList(rounds, sel.goalId),
                today = today,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RecordsUiState())

    private fun restDays(id: String): Set<Long> =
        preferences.getStringSet(id, emptySet()).orEmpty().mapNotNull { it.toLongOrNull() }.toSet()

    fun refreshDate() {
        currentDay.value = LocalDate.now().toEpochDay()
    }

    fun setRestingToday(date: Long, resting: Boolean) {
        refreshDate()
        if (date != currentDay.value) return
        val id = auth.currentUser.value?.id ?: return
        if (resting && repository.sessions.value.any { it.date == date && it.minutes > 0 }) return
        val days = restDays(id)
        val updated = if (resting) days + date else days - date
        if (updated == days) return
        preferences.edit().putStringSet(id, updated.map { it.toString() }.toSet()).apply()
        restDayChanges.update { it + 1 }
    }

    fun setView(view: RecordsView) = selection.update { it.copy(view = view) }

    fun setGoal(goalId: String?) = selection.update { it.copy(goalId = goalId) }
}
