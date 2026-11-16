package com.example.oreum.domain.usecase

import com.example.oreum.data.model.Goal
import com.example.oreum.data.model.Task

/**
 * 목표 진행률 계산 (순수 Kotlin).
 * 진행률 = 완료로 인정한 시간 / 전체 계획 시간. 복구로 할 일을 옮겨도 이미 완료한 시간은 그대로라 줄지 않아요.
 */
object GoalProgress {

    data class Summary(
        val doneCount: Int,
        val totalCount: Int,
        val doneMinutes: Int,
        val plannedMinutes: Int,
    ) {
        /** 0.0 ~ 1.0 */
        val rate: Float
            get() = if (plannedMinutes <= 0) 0f else (doneMinutes.toFloat() / plannedMinutes).coerceIn(0f, 1f)
        val percent: Int get() = (rate * 100).toInt()
    }

    /**
     * 복구로 넘긴 항목(carriedOver)은 처음 계획 시간을 전체에 남기고, 그때 한 시간만 완료로 인정해요.
     * 복구로 새로 생긴 항목(fromRecoveryId)은 그 남은 분량을 채우는 것이라 전체 시간에는 더하지 않고, 끝내면 완료 시간에만 더해요.
     * 그래서 복구를 승인해도 진행률이 줄지 않아요.
     */
    fun of(goalId: String, tasks: List<Task>): Summary = ofTasks(tasks.filter { it.goalId == goalId })

    /** 여러 목표의 할 일을 함께 계산 (홈 '전체 목표') */
    fun ofTasks(mine: List<Task>): Summary {
        val counted = mine.filter { !it.carriedOver }
        val planned = mine.filter { it.fromRecoveryId == null }.sumOf { it.originalMinutes ?: it.estimatedMinutes }
        val doneMinutes = mine.sumOf { credit(it) }
        return Summary(
            doneCount = counted.count { it.isDone },
            totalCount = counted.size,
            doneMinutes = minOf(doneMinutes, planned),
            plannedMinutes = planned,
        )
    }

    /** 완료로 인정한 시간: 끝냈으면 계획 시간 전체(복구 항목은 그 항목 시간), 못 끝냈으면 실제로 한 만큼 */
    private fun credit(t: Task): Int = when {
        t.isDone && (t.carriedOver || t.fromRecoveryId != null) -> t.estimatedMinutes
        t.isDone -> t.originalMinutes ?: t.estimatedMinutes
        t.carriedOver -> 0
        else -> minOf(t.actualMinutes ?: 0, t.estimatedMinutes)
    }

    /** 오른 산(완료한 목표): 직접 완료로 표시했거나 할 일을 모두 끝낸 목표 */
    fun isCompleted(goal: Goal, tasks: List<Task>): Boolean {
        if (goal.completedAt != null) return true
        val summary = of(goal.id, tasks)
        return summary.totalCount > 0 && summary.doneCount == summary.totalCount
    }
}
