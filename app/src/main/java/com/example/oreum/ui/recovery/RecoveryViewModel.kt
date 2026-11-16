package com.example.oreum.ui.recovery

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.oreum.data.model.RecoveryProposal
import com.example.oreum.data.model.Task
import com.example.oreum.data.model.TaskChange
import com.example.oreum.data.model.TaskStatus
import com.example.oreum.data.repository.StudyRepository
import com.example.oreum.data.repository.StudyRepositoryProvider
import com.example.oreum.domain.usecase.RecoveryPlanner
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate

data class RecoveryUiState(
    /** null이면 복구할 학습이 없어요 */
    val plan: RecoveryPlanner.Plan? = null,
    val loaded: Boolean = false,
    /** '어려웠던 부분을 적고…'를 눌러 입력칸을 연 상태 */
    val moreOpen: Boolean = false,
    /** 바뀌는 계획: 전체 비교 (기존 | 수정) */
    val compare: Boolean = false,
)

/** STEP 5 AI 학습 계획 복구 (시뮬레이션). 승인하기 전에는 계획을 바꾸지 않아요. */
class RecoveryViewModel(
    private val repository: StudyRepository = StudyRepositoryProvider.instance,
) : ViewModel() {

    private val extraNote = MutableStateFlow("")
    private val moreOpen = MutableStateFlow(false)
    private val compare = MutableStateFlow(false)

    /** 승인한 뒤에는 화면을 그대로 두고 (계획이 바뀌어 '복구할 학습이 없어요'로 깜빡이지 않게) */
    private var frozen: RecoveryUiState? = null

    val uiState: StateFlow<RecoveryUiState> =
        combine(repository.tasks, repository.logs, repository.goals, extraNote, combine(moreOpen, compare) { m, c -> m to c }) { tasks, logs, goals, extra, (more, cmp) ->
            frozen ?: RecoveryUiState(
                plan = RecoveryPlanner.build(tasks, goals, logs, LocalDate.now().toEpochDay(), extra),
                loaded = true,
                moreOpen = more || extra.isNotEmpty(),
                compare = cmp,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RecoveryUiState())

    fun openMore() {
        moreOpen.value = true
    }

    fun toggleCompare() {
        compare.value = !compare.value
    }

    /** 추가 설명을 반영해 다시 분석 (빈 글이면 지움) */
    fun reanalyze(text: String) {
        extraNote.value = text.trim()
    }

    /** 승인하고 적용: 추가한 항목 수 · 옮긴 항목 수를 돌려줘요 */
    fun approve(): Pair<Int, Int>? {
        val state = uiState.value
        val plan = state.plan ?: return null
        frozen = state
        val newTasks = plan.placedItems.map { n ->
            Task(
                goalId = n.goalId,
                title = n.title,
                estimatedMinutes = n.minutes,
                priority = 1,
                status = TaskStatus.TODO,
                plannedDate = n.date,
                week = n.week,
            )
        }
        val proposal = RecoveryProposal(
            goalId = plan.goalIds.first(),
            failDays = plan.pending.mapNotNull { it.plannedDate }.distinct().sorted(),
            reasons = plan.reasons,
            retroTags = plan.badTags + plan.goodTags,
            extraNote = plan.extraNote,
            suggestions = plan.fixes,
            taskChanges = plan.placedItems.map { TaskChange(title = it.title, toDate = it.date ?: 0L, toMinutes = it.minutes) } +
                plan.moves.map { m ->
                    val minutes = repository.task(m.taskId)?.estimatedMinutes ?: 0
                    TaskChange(taskId = m.taskId, title = m.title, fromDate = m.from, toDate = m.to, fromMinutes = minutes, toMinutes = minutes)
                },
            expectedRateBefore = if (plan.plannedMinutes > 0) plan.actualMinutes.toFloat() / plan.plannedMinutes else 0f,
        )
        repository.applyRecovery(
            proposal = proposal,
            carriedTaskIds = plan.pending.map { it.id },
            newTasks = newTasks,
            moves = plan.moves.associate { it.taskId to it.to },
        )
        return newTasks.size to plan.moves.size
    }
}
