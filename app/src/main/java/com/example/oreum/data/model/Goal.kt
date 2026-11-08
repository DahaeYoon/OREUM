package com.example.oreum.data.model

/** 개인 학습 목표 = 오를 산 하나. 할 일(Task)이 모여 정상(마감)까지 가요. */
data class Goal(
    val id: String = "",
    val ownerId: String = "",
    val title: String = "",
    val subject: String = "",
    /** 마감: 날짜 + 시간 (epoch milliseconds) */
    val dueAt: Long = 0L,
    /** 모든 할 일을 마친 시각. null이면 진행 중인 산 */
    val completedAt: Long? = null,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
)
