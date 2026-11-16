package com.example.oreum.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.oreum.data.model.DailyLog
import com.example.oreum.data.model.Goal
import com.example.oreum.data.model.PlanStatus
import com.example.oreum.data.model.Task
import com.example.oreum.data.model.TrailOrder
import com.example.oreum.data.repository.AuthRepository
import com.example.oreum.data.repository.AuthRepositoryProvider
import com.example.oreum.data.repository.StudyRepository
import com.example.oreum.data.repository.StudyRepositoryProvider
import com.example.oreum.domain.usecase.HomeMountain
import com.example.oreum.domain.usecase.PlanCalendar
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import kotlin.math.min

/** 홈에서 고른 산 (앱이 켜져 있는 동안 기억). null = 전체 목표 합쳐 보기 */
object HomePrefs {
    var goalId: String? = null
    var picked: Boolean = false
}

/** 산 페이지의 카드 종류 */
sealed interface NextCard {
    data class NoPlan(val goalId: String) : NextCard
    data class Draft(val goalId: String) : NextCard
    data class Next(val task: Task, val goalTitle: String, val isToday: Boolean) : NextCard
    data object Empty : NextCard
    data object Top : NextCard
}

data class TodayItem(val task: Task, val goalTitle: String)

data class HomeUiState(
    val loaded: Boolean = false,
    val nickname: String = "",
    /** null = 전체 목표 */
    val goal: Goal? = null,
    /** 마감 표시에 쓰는 목표 (전체일 때는 가장 가까운 남은 목표) */
    val dueGoal: Goal? = null,
    val progress: HomeMountain.Progress = HomeMountain.Progress(0, 0, 0f, 0),
    val slots: List<HomeMountain.Slot> = emptyList(),
    val order: TrailOrder = TrailOrder.PLAN_DATE,
    val next: NextCard = NextCard.Empty,
    val today: List<TodayItem> = emptyList(),
    val todayPlanned: Int = 0,
    val todayCredited: Int = 0,
    val pendingRecovery: Int = 0,
    val unloggedDays: List<Long> = emptyList(),
    val anyGoalWithoutPlan: Boolean = false,
    val goals: List<Goal> = emptyList(),
    val tasks: List<Task> = emptyList(),
    val logs: List<DailyLog> = emptyList(),
) {
    val todayPercent: Int get() = if (todayPlanned > 0) todayCredited * 100 / todayPlanned else 0
}

/** STEP 7 홈: 산(진행률 · 트랙 · 다음 할 일) + 오늘 계획 */
class HomeViewModel(
    private val repository: StudyRepository = StudyRepositoryProvider.instance,
    private val auth: AuthRepository = AuthRepositoryProvider.instance,
) : ViewModel() {

    private val selected = MutableStateFlow(HomePrefs.goalId)

    init {
        // 처음 들어오면 첫 번째 진행 중인 목표의 산
        if (!HomePrefs.picked) {
            HomePrefs.goalId = repository.goals.value.firstOrNull { it.completedAt == null }?.id
            HomePrefs.picked = true
            selected.value = HomePrefs.goalId
        }
    }

    /** 완료 애니메이션 시작점 (화면이 한 번 읽고 비워요) */
    var animateFrom: Float? = null
        private set

    val uiState: StateFlow<HomeUiState> =
        combine(
            combine(repository.goals, repository.tasks, repository.logs) { g, t, l -> Triple(g, t, l) },
            repository.drafts,
            auth.currentUser,
            selected,
        ) { (goals, tasks, logs), drafts, user, sel0 ->
            val sel = sel0?.takeIf { id -> goals.any { it.id == id } }
            val goal = goals.firstOrNull { it.id == sel }
            val order = user?.trailOrder ?: TrailOrder.PLAN_DATE
            val today = LocalDate.now().toEpochDay()
            val progress = HomeMountain.progress(tasks, goals, sel)
            val slots = HomeMountain.slots(tasks, goals, sel, order)
            val title = { id: String? -> goals.firstOrNull { it.id == id }?.title.orEmpty() }

            val next: NextCard = when {
                goal != null && drafts.containsKey(goal.id) -> NextCard.Draft(goal.id)
                goal != null && goal.planStatus == PlanStatus.NONE -> NextCard.NoPlan(goal.id)
                else -> {
                    val nx = HomeMountain.nextTask(tasks, goals, sel, today)
                    when {
                        nx != null -> NextCard.Next(nx, title(nx.goalId), nx.plannedDate == today)
                        HomeMountain.goalTasks(tasks, goals, sel).isEmpty() -> NextCard.Empty
                        else -> NextCard.Top
                    }
                }
            }
            val active = goals.filter { it.completedAt == null }
            val dueGoal = goal ?: active.filter { HomeMountain.progress(tasks, goals, it.id).remainingMinutes > 0 }.minByOrNull { it.dueAt }
                ?: active.minByOrNull { it.dueAt }

            val todayTasks = PlanCalendar.dayItems(tasks, today, null).filterNot { it.carriedOver }
            HomeUiState(
                loaded = true,
                nickname = user?.nickname.orEmpty(),
                goal = goal,
                dueGoal = dueGoal,
                progress = progress,
                slots = slots,
                order = order,
                next = next,
                today = todayTasks.map { TodayItem(it, title(it.goalId)) },
                todayPlanned = todayTasks.sumOf { it.estimatedMinutes },
                todayCredited = todayTasks.sumOf { credited(it) },
                pendingRecovery = PlanCalendar.pendingRecovery(tasks).count { t -> active.any { it.id == t.goalId } },
                unloggedDays = PlanCalendar.unloggedDays(tasks, logs, today),
                anyGoalWithoutPlan = goals.any { it.planStatus != PlanStatus.APPROVED },
                goals = goals,
                tasks = tasks,
                logs = logs,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    private fun credited(t: Task): Int = if (t.isDone) t.estimatedMinutes else min(t.actualMinutes ?: 0, t.estimatedMinutes)

    fun selectGoal(goalId: String?) {
        HomePrefs.goalId = goalId
        HomePrefs.picked = true
        selected.value = goalId
    }

    fun setOrder(order: TrailOrder) {
        val user = auth.currentUser.value ?: return
        auth.updateUser(user.copy(trailOrder = order))
    }

    fun takeAnimateFrom(): Float? = animateFrom.also { animateFrom = null }

    private fun currentRate(): Float = HomeMountain.progress(repository.tasks.value, repository.goals.value, selected.value).rate

    /** 산 페이지 '완료': 앞뒤 진행률을 돌려줘요 (정상 도착이면 reachedTop = true) */
    fun completeNext(taskId: String): CompleteResult? {
        val task = repository.task(taskId) ?: return null
        val before = currentRate()
        repository.setTaskDone(taskId, true)
        val after = currentRate()
        animateFrom = before
        val top = before < 1f && after >= 1f
        val gid = selected.value
        if (top && gid != null) repository.setGoalCompleted(gid, true)
        return CompleteResult(task.title, before, after, top)
    }

    /** 오늘 계획 체크 · 체크 해제 */
    fun toggleToday(taskId: String): CompleteResult? {
        val task = repository.task(taskId) ?: return null
        val sel = selected.value
        val gid = sel ?: task.goalId
        val before = gid?.let { HomeMountain.progress(repository.tasks.value, repository.goals.value, it).rate } ?: 0f
        val wasSel = currentRate()
        repository.setTaskDone(taskId, !task.isDone)
        if (sel == null || task.goalId == sel) animateFrom = wasSel
        val after = gid?.let { HomeMountain.progress(repository.tasks.value, repository.goals.value, it).rate } ?: 0f
        val goal = task.goalId?.let { repository.goal(it) }
        var top = false
        if (!task.isDone && before < 1f && after >= 1f && gid != null && (sel == null || task.goalId == sel)) {
            // 마지막 할 일을 끝냈으면 그 목표의 산으로 (정상 도착)
            repository.setGoalCompleted(gid, true)
            if (sel == null) selectGoal(gid)
            top = true
        }
        // 완료를 취소해 100% 아래로 내려가면 다시 등반 중으로
        if (task.isDone && goal?.completedAt != null && after < 1f) repository.setGoalCompleted(goal.id, false)
        return CompleteResult(task.title, before, after, top, nowDone = !task.isDone)
    }

    /** 체크포인트에서 '완료 취소' */
    fun undo(taskId: String): CompleteResult? {
        val task = repository.task(taskId) ?: return null
        if (!task.isDone || task.carriedOver) return null
        val before = currentRate()
        repository.setTaskDone(taskId, false)
        animateFrom = null
        val goal = task.goalId?.let { repository.goal(it) }
        val after = currentRate()
        if (goal?.completedAt != null && HomeMountain.progress(repository.tasks.value, repository.goals.value, goal.id).rate < 1f) {
            repository.setGoalCompleted(goal.id, false)
        }
        return CompleteResult(task.title, before, after, false, nowDone = false)
    }

    data class CompleteResult(val title: String, val before: Float, val after: Float, val reachedTop: Boolean, val nowDone: Boolean = true)
}
