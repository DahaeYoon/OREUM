package com.example.oreum.data.model

/**
 * 사용자 프로필. id는 Firebase Authentication uid와 같은 값을 써요.
 * 비밀번호는 저장하지 않아요 (인증은 Firebase가 담당).
 */
data class User(
    val id: String = "",
    val nickname: String = "",
    val email: String = "",
    val interests: List<String> = emptyList(),
    /** 평일·주말 기본 공부 가능 시간대 (위험 계산의 "빈 시간" 기준) */
    val weekdayAvailability: List<TimeRange> = emptyList(),
    val weekendAvailability: List<TimeRange> = emptyList(),
    /** 하루 학습 가능 시간 (분): 평일 · 주말. AI 계획이 하루 분량을 정할 때 써요. */
    val weekdayStudyMinutes: Int = 360,
    val weekendStudyMinutes: Int = 480,
    /** 기록 화면에서 '다음 AI 계획에 반영하기'를 누르면 저장되는 실제 페이스 (계획 대비 실제 비율). null이면 반영 안 함 */
    val paceRatio: Float? = null,
    /** 페이스 반영 시 한 번에 공부할 시간 (분). null이면 기본값 */
    val paceSessionMinutes: Int? = null,
    /** 홈 산의 남은 할 일 순서 */
    val trailOrder: TrailOrder = TrailOrder.PLAN_DATE,
    /** 학습 알림: 오늘 계획 알림 · 기록 알림 */
    val notifyDailyPlan: Boolean = true,
    val notifyLogReminder: Boolean = true,
    val createdAt: Long = 0L,
)

/** 하루 중 시간대. 분 단위(0 ~ 1440)라서 날짜와 무관하게 반복돼요. 예) 19:00 = 1140 */
data class TimeRange(
    val startMinute: Int = 0,
    val endMinute: Int = 0,
)
