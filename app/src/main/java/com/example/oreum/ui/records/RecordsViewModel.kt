package com.example.oreum.ui.records

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.oreum.data.model.Goal
import com.example.oreum.data.model.StudyRound
import com.example.oreum.data.repository.StudyRepository
import com.example.oreum.data.repository.StudyRepositoryProvider
import com.example.oreum.domain.usecase.RoundRecords
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
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

/** 기록 (1차): 날짜별 · 구간별 목록 + 페이스 카드. 모두 실제 학습 기록(구간 · 학습 시간) 기준이에요. */
class RecordsViewModel(
    private val repository: StudyRepository = StudyRepositoryProvider.instance,
) : ViewModel() {

    private val selection = MutableStateFlow(RecordsSelection())

    val uiState: StateFlow<RecordsUiState> =
        combine(repository.goals, repository.rounds, repository.sessions, selection) { goals, rounds, sessions, sel0 ->
            val sel = if (sel0.goalId != null && goals.none { it.id == sel0.goalId }) sel0.copy(goalId = null) else sel0
            val today = LocalDate.now().toEpochDay()
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

    fun setView(view: RecordsView) = selection.update { it.copy(view = view) }

    fun setGoal(goalId: String?) = selection.update { it.copy(goalId = goalId) }
}
