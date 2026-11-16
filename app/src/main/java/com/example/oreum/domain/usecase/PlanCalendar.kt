package com.example.oreum.domain.usecase

import com.example.oreum.data.model.DailyLog
import com.example.oreum.data.model.Goal
import com.example.oreum.data.model.Task
import java.time.DayOfWeek
import java.time.LocalDate

/** 계획 화면 계산 (순수 Kotlin): 날짜별 할 일 · 하루 가능 시간 · 미완료 · 기록 안 한 날 · 항목 상태 */
object PlanCalendar {

    /** 그날 할 일 (goalId = null이면 전체 목표) */
    fun dayItems(tasks: List<Task>, date: Long, goalId: String?): List<Task> =
        tasks.filter { it.goalId != null && it.plannedDate == date && (goalId == null || it.goalId == goalId) }
            .sortedBy { it.order }

    fun isWeekend(date: LocalDate) = date.dayOfWeek == DayOfWeek.SATURDAY || date.dayOfWeek == DayOfWeek.SUNDAY

    /** 하루 학습 가능 시간 (분): 전체 = 마이의 하루 학습 가능 시간, 목표 하나 = 그 목표의 평일 · 주말 시간 */
    fun capacity(date: LocalDate, goal: Goal?, userWeekdayMinutes: Int, userWeekendMinutes: Int): Int = when {
        goal != null -> if (isWeekend(date)) goal.weekendMinutes else goal.weekdayMinutes
        isWeekend(date) -> userWeekendMinutes
        else -> userWeekdayMinutes
    }

    /** 완료로 인정한 시간 (분): 완료하면 계획 시간 전체, 복구로 넘긴 항목은 그때 한 시간만 */
    fun credited(task: Task): Int = when {
        task.carriedOver -> if (task.isDone) task.estimatedMinutes else 0
        !task.isDone -> minOf(task.actualMinutes ?: 0, task.estimatedMinutes)
        else -> task.originalMinutes ?: task.estimatedMinutes
    }

    /** AI 복구가 필요한 미완료 항목: 못 끝낸 이유를 기록했고 아직 복구에서 다루지 않은 항목 */
    fun pendingRecovery(tasks: List<Task>): List<Task> =
        tasks.filter { !it.isDone && !it.carriedOver && it.missReason != null && !it.recoveryHandled }

    /** 지난 7일 중 계획이 있었는데 학습 결과를 기록하지 않은 날 (epoch day, 오래된 순) */
    fun unloggedDays(tasks: List<Task>, logs: List<DailyLog>, today: Long): List<Long> {
        val logged = logs.map { it.date }.toSet()
        return tasks.asSequence()
            .filter { it.goalId != null && !it.carriedOver }
            .mapNotNull { it.plannedDate }
            .filter { it < today && it >= today - 7 && it !in logged }
            .distinct().sorted().toList()
    }

    /** 항목 상태 글자: 완료 · 복구됨 · 미완료 · 일부 수행 · 예정 */
    fun stateText(task: Task, today: Long, logs: List<DailyLog>): String {
        val date = task.plannedDate ?: today
        val loggedToday = date == today && logs.any { it.date == today }
        return when {
            task.carriedOver -> "복구됨"
            task.isDone -> "완료"
            date < today || loggedToday -> if ((task.actualMinutes ?: 0) > 0) "일부 수행" else "미완료"
            else -> "예정"
        }
    }
}
