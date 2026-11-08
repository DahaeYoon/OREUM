package com.example.oreum.data.model

/**
 * 캘린더 일정 (수업, 아르바이트, 그룹 모임 등).
 * - isFixed: 반복 수업처럼 바꿀 수 없는 일정
 * - isMovable: 경로 재탐색 대안 1에서 옮겨도 된다고 사용자가 허락한 일정
 */
data class Schedule(
    val id: String = "",
    val ownerId: String = "",
    val groupId: String? = null,
    val title: String = "",
    val kind: ScheduleKind = ScheduleKind.PERSONAL,
    val startAt: Long = 0L,
    val endAt: Long = 0L,
    val isFixed: Boolean = false,
    val isMovable: Boolean = false,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
)
