package com.example.oreum.data.model

/** 하루 학습 결과 기록: 회고 키워드 + 한 줄 메모. 항목별 실제 시간 · 미완료 이유는 Task에 저장해요. */
data class DailyLog(
    val id: String = "",
    val ownerId: String = "",
    /** 기록한 날 (epoch day) */
    val date: Long = 0L,
    val tags: List<RetroTag> = emptyList(),
    val memo: String = "",
    val savedAt: Long = 0L,
)

/** 학습 결과 기록에서 항목 하나의 결과 (저장할 때 Task에 반영) */
data class DayLogEntry(
    val taskId: String,
    val done: Boolean,
    val actualMinutes: Int,
    /** 못 끝낸 경우 이유 (끝냈으면 null) */
    val reason: MissReason?,
)
