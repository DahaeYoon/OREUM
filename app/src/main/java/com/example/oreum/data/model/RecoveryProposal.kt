package com.example.oreum.data.model

/**
 * 경로 재탐색 대안. 계산 결과만 담고, 사용자가 "적용하기"를 눌러
 * status = APPLIED가 될 때에만 원본 Schedule · Task를 바꿔요.
 */
data class RecoveryProposal(
    val id: String = "",
    val ownerId: String = "",
    val goalId: String = "",
    val type: RecoveryType = RecoveryType.MOVE_SCHEDULES,
    /** 적용 전 부족 시간 (분) */
    val shortageBeforeMinutes: Int = 0,
    /** 적용 후 남는 부족 시간 (분) */
    val shortageAfterMinutes: Int = 0,
    val securedMinutes: Int = 0,
    /** 대안 1: 옮길 일정 목록 */
    val scheduleChanges: List<ScheduleChange> = emptyList(),
    /** 대안 2: 바뀐 실행 순서 (Task id 순서) */
    val taskOrderBefore: List<String> = emptyList(),
    val taskOrderAfter: List<String> = emptyList(),
    val status: ProposalStatus = ProposalStatus.PROPOSED,
    val createdAt: Long = 0L,
    val appliedAt: Long? = null,
)

/** 일정 하나의 변경 전 → 변경 후 */
data class ScheduleChange(
    val scheduleId: String = "",
    val fromStartAt: Long = 0L,
    val fromEndAt: Long = 0L,
    val toStartAt: Long = 0L,
    val toEndAt: Long = 0L,
)
