package com.example.oreum.data.model

/**
 * 개인 할 일과 그룹 업무를 하나의 모델로 관리해요.
 * - 개인 할 일: goalId 사용, groupId = null
 * - 그룹 업무: groupId 사용, assigneeId = 담당자
 */
data class Task(
    val id: String = "",
    val ownerId: String = "",
    val goalId: String? = null,
    val groupId: String? = null,
    val assigneeId: String? = null,
    val title: String = "",
    /** 사용자가 직접 입력한 예상 소요시간 (분). AI가 추정하지 않아요. */
    val estimatedMinutes: Int = 0,
    /** 마감: 날짜 + 시간 (epoch milliseconds). 없으면 목표 마감을 따라요. */
    val dueAt: Long? = null,
    /** 1 = 가장 높음 */
    val priority: Int = 2,
    val status: TaskStatus = TaskStatus.TODO,
    /** 하기로 정한 날 (예: 오늘 할 일). epoch day */
    val plannedDate: Long? = null,
    val completedAt: Long? = null,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
) {
    val isDone: Boolean get() = status == TaskStatus.DONE
}
