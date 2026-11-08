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
    val createdAt: Long = 0L,
)

/** 하루 중 시간대. 분 단위(0 ~ 1440)라서 날짜와 무관하게 반복돼요. 예) 19:00 = 1140 */
data class TimeRange(
    val startMinute: Int = 0,
    val endMinute: Int = 0,
)
