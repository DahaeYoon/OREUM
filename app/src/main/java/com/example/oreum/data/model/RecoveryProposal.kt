package com.example.oreum.data.model

/**
 * AI 학습 계획 복구 제안 (시뮬레이션). 계산 결과만 담고, 사용자가 "복구 적용"을 눌러
 * status = APPLIED가 될 때에만 원본 Task를 바꿔요. 이미 완료한 진행률은 줄이지 않아요.
 */
data class RecoveryProposal(
    val id: String = "",
    val ownerId: String = "",
    val goalId: String = "",
    /** 미완료가 있던 날 (epoch day) · 기록 화면의 복구 전후 비교에 사용 */
    val failDays: List<Long> = emptyList(),
    /** 기본 원인: 미완료 이유 */
    val reasons: List<MissReason> = emptyList(),
    /** 기본 원인: 회고 키워드 */
    val retroTags: List<RetroTag> = emptyList(),
    /** 사용자가 적은 추가 설명 (구체적인 복구 방법에 반영) */
    val extraNote: String = "",
    /** 화면에 보여 줄 복구 방법 문장들 */
    val suggestions: List<String> = emptyList(),
    /** 할 일 하나의 변경 전 → 변경 후 */
    val taskChanges: List<TaskChange> = emptyList(),
    /** 복구 전 · 후 마감까지 예상 달성률 (0.0 ~ 1.0) */
    val expectedRateBefore: Float = 0f,
    val expectedRateAfter: Float = 0f,
    val status: ProposalStatus = ProposalStatus.PROPOSED,
    val createdAt: Long = 0L,
    val appliedAt: Long? = null,
)

/** 복구 제안에서 할 일 하나의 변경 전 → 변경 후. taskId가 null이면 새로 추가되는 항목 */
data class TaskChange(
    val taskId: String? = null,
    val title: String = "",
    val fromDate: Long? = null,
    val toDate: Long = 0L,
    val fromMinutes: Int = 0,
    val toMinutes: Int = 0,
)
