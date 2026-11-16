package com.example.oreum.data.model

/**
 * AI가 만든 학습 계획 초안 (시뮬레이션). 사용자가 "승인하고 적용"을 누르기 전에는
 * 할 일(Task)로 바뀌지 않아요. 검토 화면에서 항목 이름 · 날짜 · 시간을 고칠 수 있어요.
 */
data class PlanDraft(
    val goalId: String = "",
    val items: List<DraftItem> = emptyList(),
    /** 주 차별 주제 (index 0 = 1주 차) */
    val weekThemes: List<String> = emptyList(),
    /** 수준 · 범위로 계산한 예상 학습량 (분) */
    val neededMinutes: Int = 0,
    /** 기간 안의 학습 가능 시간 합계 (분) */
    val capacityMinutes: Int = 0,
    /** 시간 부족 · 다른 목표와 겹침 경고 */
    val warnings: List<String> = emptyList(),
    /** 다른 목표 계획과 합치면 하루 학습 가능 시간을 넘는 날 (epoch day) */
    val overDays: List<Long> = emptyList(),
    /** 기록 화면의 학습 페이스를 반영했는지 */
    val paceApplied: Boolean = false,
    /** 계획을 만든 날 (epoch day) */
    val createdOn: Long = 0L,
) {
    /** 지금 초안에 들어 있는 항목 시간 합계 (분) */
    val plannedMinutes: Int get() = items.sumOf { it.minutes }
}

/** 계획 초안의 학습 항목 하나 */
data class DraftItem(
    val id: String = "",
    val title: String = "",
    val minutes: Int = 0,
    /** 하기로 한 날 (epoch day) */
    val date: Long = 0L,
    /** 몇 주 차 (1부터) */
    val week: Int = 1,
)
