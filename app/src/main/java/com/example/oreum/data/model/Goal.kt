package com.example.oreum.data.model

/** 학습 목표 = 오를 산 하나. AI 로드맵의 단계를 구간(StudyRound)으로 나눠 정상까지 올라가요. */
data class Goal(
    val id: String = "",
    val ownerId: String = "",
    /** 1. 목표 이름 */
    val title: String = "",
    /** 2. 목표 내용 (예: 리눅스마스터 2급 시험 범위 전체) */
    val description: String = "",
    val subject: String = "",
    /** 3. 기간 구분 (일일 · 주간 · 월간 목표) */
    val period: GoalPeriod = GoalPeriod.MONTHLY,
    /** 3. 기간: 시작일 (epoch day) */
    val startDate: Long = 0L,
    /** 3. 기간: 마감 (epoch milliseconds) */
    val dueAt: Long = 0L,
    /** 4. 현재 학습 수준 */
    val level: GoalLevel = GoalLevel.BEGINNER,
    /** 5. 이 목표에 쓸 하루 학습 가능 시간 (분): 평일 · 주말 */
    val weekdayMinutes: Int = 120,
    val weekendMinutes: Int = 180,
    /** 6. 우선순위 (1 = 높음, 2 = 보통, 3 = 낮음) */
    val priority: Int = 2,
    /** AI 계획 상태 */
    val planStatus: PlanStatus = PlanStatus.NONE,
    /** 승인한 AI 로드맵 (단계 · 주제 · 예상 시간, 날짜 없음) */
    val roadmap: List<RoadmapStage> = emptyList(),
    /** 지금 진행 중인 로드맵 단계 index (roadmap.size면 모든 단계를 마침) */
    val stage: Int = 0,
    /** 모든 할 일을 마친 시각. null이면 진행 중인 산 */
    val completedAt: Long? = null,
    /** 마감일이 지나 '여기까지로 마치기'로 끝낸 목표 (정상 도착이 아니라 마감 종료) */
    val endedAtDue: Boolean = false,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
)
