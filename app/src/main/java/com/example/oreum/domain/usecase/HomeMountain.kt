package com.example.oreum.domain.usecase

import com.example.oreum.data.model.Goal
import com.example.oreum.data.model.Task
import com.example.oreum.data.model.TrailOrder
import kotlin.math.ceil
import kotlin.math.min

/**
 * 홈 산 계산 (순수 Kotlin, 프로토타입 repo.goalTasks · progress · slots와 같은 규칙).
 * goalId = null이면 '전체 목표 합쳐 보기' (아직 정상에 오르지 않은 목표들).
 */
object HomeMountain {

    /** 산 위 체크포인트 한 칸 = 할 일 하나 (할 일이 12개보다 많으면 주 차 묶음) */
    data class Slot(
        val id: String,
        val goalId: String,
        val title: String,
        val done: Boolean,
        /** 주 차 묶음이면 묶인 할 일 id들 */
        val itemIds: List<String> = emptyList(),
        /** 남은 시간 (분) */
        val remainingMinutes: Int = 0,
        val plannedDate: Long? = null,
    ) {
        val isGroup: Boolean get() = itemIds.isNotEmpty()
    }

    data class Progress(
        val done: Int,
        val total: Int,
        /** 0 ~ 1 (완료로 인정한 시간 / 계획한 시간) */
        val rate: Float,
        /** 남은 시간 (분) */
        val remainingMinutes: Int,
    ) {
        val percent: Int get() = (rate * 100).toInt()
        val top: Boolean get() = total > 0 && rate >= 1f
    }

    /** 이 산에 올라가는 할 일 (복구로 넘기고 남은 시간이 0인 항목은 빼요) */
    fun goalTasks(tasks: List<Task>, goals: List<Goal>, goalId: String?): List<Task> {
        val open = goals.filter { it.completedAt == null }.map { it.id }.toSet()
        return tasks.filter { t ->
            val gid = t.goalId ?: return@filter false
            (if (goalId == null) gid in open else gid == goalId) && !(t.carriedOver && t.estimatedMinutes == 0)
        }
    }

    /** 진행률: 목표 화면과 같은 계산 (복구해도 줄지 않음) */
    fun progress(tasks: List<Task>, goals: List<Goal>, goalId: String?): Progress {
        val open = goals.filter { it.completedAt == null }.map { it.id }.toSet()
        // 진행률은 복구로 넘긴 항목(처음 계획 시간)까지 포함해 계산해요
        val scope = tasks.filter { t -> val gid = t.goalId; gid != null && (if (goalId == null) gid in open else gid == goalId) }
        val list = goalTasks(tasks, goals, goalId)
        val s = GoalProgress.ofTasks(scope)
        return Progress(
            done = list.count { it.isDone },
            total = list.size,
            rate = s.rate,
            remainingMinutes = (s.plannedMinutes - s.doneMinutes).coerceAtLeast(0),
        )
    }

    private fun day(t: Task, goals: List<Goal>): Long = t.plannedDate ?: dueDay(t, goals)
    private fun dueDay(t: Task, goals: List<Goal>): Long {
        val due = t.dueAt ?: goals.firstOrNull { it.id == t.goalId }?.dueAt ?: Long.MAX_VALUE
        return if (due == Long.MAX_VALUE) Long.MAX_VALUE else com.example.oreum.util.DateText.toDate(due).toEpochDay()
    }

    private fun comparator(order: TrailOrder, goals: List<Goal>): Comparator<Task> = when (order) {
        TrailOrder.PLAN_DATE -> compareBy<Task>({ day(it, goals) }, { it.priority }, { dueDay(it, goals) })
        TrailOrder.DUE -> compareBy<Task>({ dueDay(it, goals) }, { it.priority }, { day(it, goals) })
        TrailOrder.PRIORITY -> compareBy<Task>({ it.priority }, { day(it, goals) }, { dueDay(it, goals) })
        TrailOrder.BY_GOAL -> compareBy<Task>(
            { goals.firstOrNull { g -> g.id == it.goalId }?.dueAt ?: Long.MAX_VALUE },
            { it.goalId },
            { day(it, goals) },
            { it.priority },
        )
    }

    /**
     * 산 아래부터 정상까지 놓이는 칸: 완료한 할 일 → 남은 할 일(고른 순서대로).
     * 할 일이 12개보다 많으면 '목표 · 주 차' 단위로 묶어요.
     */
    fun slots(tasks: List<Task>, goals: List<Goal>, goalId: String?, order: TrailOrder): List<Slot> {
        val ts = goalTasks(tasks, goals, goalId).sortedWith(compareBy<Task>({ it.plannedDate ?: Long.MAX_VALUE }, { it.order }))
        if (ts.size <= 12) {
            val done = ts.filter { it.isDone }
            val todo = ts.filter { !it.isDone }.sortedWith(comparator(order, goals))
            return (done + todo).map { t ->
                Slot(t.id, t.goalId.orEmpty(), t.title, t.isDone, remainingMinutes = remaining(t), plannedDate = t.plannedDate)
            }
        }
        // 목표마다 AI 계획의 주 차가 여러 개면 주 차로, 아니면 날짜 순으로 8묶음 정도로 나눠요 (프로토타입과 같은 규칙)
        val weeksByGoal = ts.groupBy { it.goalId }.mapValues { (_, l) -> l.map { it.week }.distinct().size > 1 }
        val per = ceil(ts.size / 8.0).toInt().coerceAtLeast(1)
        val groups = LinkedHashMap<String, MutableList<Task>>()
        ts.forEachIndexed { i, t ->
            val key = if (weeksByGoal[t.goalId] == true) "${t.goalId}:${t.week}" else "${t.goalId}:b${i / per}"
            groups.getOrPut(key) { mutableListOf() } += t
        }
        return groups.map { (key, list) ->
            val byWeek = weeksByGoal[list.first().goalId] == true
            val first = list.first().plannedDate
            val last = list.last().plannedDate
            Slot(
                id = "wk:$key",
                goalId = list.first().goalId.orEmpty(),
                title = if (byWeek) "${list.first().week}주 차" else "${md(first)}~${md(last)}",
                done = list.all { it.isDone },
                itemIds = list.map { it.id },
                remainingMinutes = list.sumOf { remaining(it) },
                plannedDate = first,
            )
        }.sortedWith(compareBy<Slot>({ !it.done }, { it.plannedDate ?: Long.MAX_VALUE }))
    }

    private fun md(day: Long?): String = day?.let { java.time.LocalDate.ofEpochDay(it).let { d -> "${d.monthValue}/${d.dayOfMonth}" } } ?: "-"

    private fun remaining(t: Task): Int =
        if (t.isDone) 0 else (t.estimatedMinutes - min(t.actualMinutes ?: 0, t.estimatedMinutes)).coerceAtLeast(0)

    /** 산 페이지 '다음 할 일': 오늘 할 일 → 앞으로의 할 일 → 지난 날 남은 할 일 순 */
    fun nextTask(tasks: List<Task>, goals: List<Goal>, goalId: String?, today: Long): Task? {
        val open = goals.filter { it.completedAt == null }.map { it.id }.toSet()
        val mine = tasks.filter { t ->
            val gid = t.goalId ?: return@filter false
            (if (goalId == null) gid in open else gid == goalId) && !t.isDone && !t.carriedOver
        }
        val byDate = compareBy<Task>({ it.plannedDate ?: Long.MAX_VALUE }, { it.order })
        return mine.filter { it.plannedDate == today }.minByOrNull { it.order }
            ?: mine.filter { (it.plannedDate ?: -1) >= today }.sortedWith(byDate).firstOrNull()
            ?: mine.sortedWith(byDate).firstOrNull()
    }
}
