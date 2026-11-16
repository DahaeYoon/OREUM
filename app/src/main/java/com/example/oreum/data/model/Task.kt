package com.example.oreum.data.model

/**
 * 목표에 속한 학습 항목(할 일). AI 계획을 승인하면 만들어지고, 직접 추가할 수도 있어요.
 * 진행률 = 완료로 인정된 시간 / 계획한 시간 이라서, 복구로 옮겨도 이미 오른 진행률은 줄지 않아요.
 */
data class Task(
    val id: String = "",
    val ownerId: String = "",
    val goalId: String? = null,
    val title: String = "",
    /** 계획한 학습 시간 (분) */
    val estimatedMinutes: Int = 0,
    /** 처음 계획한 시간 (분). 복구로 시간이 줄어도 원래 값을 남겨요. null이면 estimatedMinutes와 같음 */
    val originalMinutes: Int? = null,
    /** 학습 결과 기록에서 입력한 실제 학습 시간 (분) */
    val actualMinutes: Int? = null,
    /** 마감: 날짜 + 시간 (epoch milliseconds). 없으면 목표 마감을 따라요. */
    val dueAt: Long? = null,
    /** 1 = 가장 높음 */
    val priority: Int = 2,
    val status: TaskStatus = TaskStatus.TODO,
    /** 하기로 정한 날 (epoch day) */
    val plannedDate: Long? = null,
    /** AI 계획의 몇 주 차 항목인지 (1부터) */
    val week: Int = 1,
    /** 같은 날 안에서의 순서 */
    val order: Int = 0,
    /** 못 끝낸 이유 (학습 결과 기록에서 선택) */
    val missReason: MissReason? = null,
    /** 이 미완료 항목을 AI 복구에서 이미 다뤘는지 (true면 '미완료 n개 · AI 복구' 안내에서 빠져요) */
    val recoveryHandled: Boolean = false,
    /** AI 복구로 남은 분량을 다른 날로 넘긴 항목 */
    val carriedOver: Boolean = false,
    /** AI 복구로 새로 만들어진 항목이면 그 제안 id */
    val fromRecoveryId: String? = null,
    val completedAt: Long? = null,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
) {
    val isDone: Boolean get() = status == TaskStatus.DONE
}
