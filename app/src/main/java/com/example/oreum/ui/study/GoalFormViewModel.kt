package com.example.oreum.ui.study

import androidx.annotation.StringRes
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import com.example.oreum.R
import com.example.oreum.data.model.Goal
import com.example.oreum.data.model.GoalLevel
import com.example.oreum.data.model.GoalPeriod
import com.example.oreum.data.model.PlanStatus
import com.example.oreum.data.repository.StudyRepository
import com.example.oreum.data.repository.StudyRepositoryProvider
import com.example.oreum.data.repository.AiRequests
import com.example.oreum.util.DateText
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.time.LocalDate

data class GoalFormUiState(
    val isEdit: Boolean = false,
    val title: String = "",
    val description: String = "",
    val period: GoalPeriod = GoalPeriod.MONTHLY,
    val startDate: LocalDate = LocalDate.now(),
    val dueDate: LocalDate = LocalDate.now().plusDays(27),
    val level: GoalLevel = GoalLevel.BEGINNER,
    val weekdayMinutes: Int = 120,
    val weekendMinutes: Int = 180,
    val priority: Int = 1,
    @StringRes val titleError: Int? = null,
    @StringRes val formError: Int? = null,
    /** 저장이 끝나면 토스트 문구와 함께 화면을 닫아요 (onClosed()로 되돌림) */
    @StringRes val savedMessage: Int? = null,
    /** 새 목표를 등록하고 AI 계획 초안을 만들었으면 그 목표 id → 생성 결과 화면으로 이동 */
    val reviewGoalId: String? = null,
)

/** 목표 만들기 · 수정 (goalId 인자가 있으면 수정) */
class GoalFormViewModel(
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val repository: StudyRepository = StudyRepositoryProvider.instance
    private val goalId: String? = savedStateHandle.get<String>(ARG_GOAL_ID)
    private val original: Goal? = goalId?.let { repository.goal(it) }

    private val _uiState = MutableStateFlow(
        original?.let { g ->
            GoalFormUiState(
                isEdit = true,
                title = g.title,
                description = g.description,
                period = g.period,
                startDate = LocalDate.ofEpochDay(g.startDate),
                dueDate = DateText.toDate(g.dueAt),
                level = g.level,
                weekdayMinutes = g.weekdayMinutes,
                weekendMinutes = g.weekendMinutes,
                priority = g.priority,
            )
        } ?: GoalFormUiState(),
    )
    val uiState: StateFlow<GoalFormUiState> = _uiState.asStateFlow()

    fun onTitleChanged(value: String) {
        if (value == _uiState.value.title) return
        _uiState.update { it.copy(title = value, titleError = null) }
    }

    fun onDescriptionChanged(value: String) {
        if (value == _uiState.value.description) return
        _uiState.update { it.copy(description = value) }
    }

    fun setPeriod(value: GoalPeriod) = _uiState.update { it.copy(period = value) }
    fun setLevel(value: GoalLevel) = _uiState.update { it.copy(level = value) }
    fun setPriority(value: Int) = _uiState.update { it.copy(priority = value) }
    fun setStartDate(value: LocalDate) = _uiState.update { it.copy(startDate = value, formError = null) }
    fun setDueDate(value: LocalDate) = _uiState.update { it.copy(dueDate = value, formError = null) }

    /** 평일 30분 ~ 12시간, 30분 단위 */
    fun changeWeekday(deltaMinutes: Int) = _uiState.update {
        it.copy(weekdayMinutes = (it.weekdayMinutes + deltaMinutes).coerceIn(30, 720))
    }

    /** 주말 0 ~ 14시간, 30분 단위 */
    fun changeWeekend(deltaMinutes: Int) = _uiState.update {
        it.copy(weekendMinutes = (it.weekendMinutes + deltaMinutes).coerceIn(0, 840))
    }

    fun save() {
        val s = _uiState.value
        val title = s.title.trim()
        if (title.isEmpty()) {
            _uiState.update { it.copy(titleError = R.string.study_error_title) }
            return
        }
        if (s.dueDate.isBefore(s.startDate)) {
            _uiState.update { it.copy(formError = R.string.study_error_period) }
            return
        }
        if (original == null && s.dueDate.isBefore(LocalDate.now())) {
            _uiState.update { it.copy(formError = R.string.study_error_past) }
            return
        }
        val base = original ?: Goal(planStatus = PlanStatus.NONE)
        val goal = base.copy(
            title = title,
            subject = title.substringBefore(' '),
            description = s.description.trim(),
            period = s.period,
            startDate = s.startDate.toEpochDay(),
            dueAt = DateText.endOfDayMillis(s.dueDate),
            level = s.level,
            weekdayMinutes = s.weekdayMinutes,
            weekendMinutes = s.weekendMinutes,
            priority = s.priority,
        )
        if (original == null) {
            val saved = repository.addGoal(goal)
            // 새 목표는 바로 AI에게 로드맵 + 1구간을 요청하고, 계획 화면에서 확인 · 승인해요 (자동 적용 안 함)
            AiRequests.requestRoadmap(saved.id)
            _uiState.update { it.copy(savedMessage = R.string.toast_goal_added_plan, reviewGoalId = saved.id) }
        } else {
            repository.updateGoal(goal)
            _uiState.update { it.copy(savedMessage = R.string.study_toast_saved) }
        }
    }

    fun onClosed() = _uiState.update { it.copy(savedMessage = null, reviewGoalId = null) }

    companion object {
        const val ARG_GOAL_ID = "goalId"
    }
}
