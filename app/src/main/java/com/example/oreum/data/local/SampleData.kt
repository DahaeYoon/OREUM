package com.example.oreum.data.local

import com.example.oreum.data.model.Goal
import com.example.oreum.data.model.GoalLevel
import com.example.oreum.data.model.GoalPeriod
import com.example.oreum.data.model.PlanStatus
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * 프로토타입과 같은 샘플 데이터. 프로토타입 기준일(2026-11-05)과의 날짜 차이만 유지해서
 * 앱을 언제 열어도 "오늘" 기준으로 같은 모습이 보여요.
 */
object SampleData {

    const val OWNER_ID = "local-test"
    private val PROTOTYPE_TODAY: LocalDate = LocalDate.of(2026, 11, 5)

    /** 샘플 목표 4개 (로드맵 · 구간은 RoundSampleData가 채워요) */
    fun goals(today: LocalDate, ownerId: String = OWNER_ID): List<Goal> {
        val shift = today.toEpochDay() - PROTOTYPE_TODAY.toEpochDay()
        fun day(iso: String): Long = LocalDate.parse(iso).toEpochDay() + shift
        fun at(iso: String, time: String): Long = LocalDate.ofEpochDay(day(iso))
            .atTime(LocalTime.parse(time)).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

        fun goal(id: String, subject: String, title: String, type: String, due: Long, period: GoalPeriod, level: GoalLevel, priority: Int, start: String) = Goal(
            id = id, ownerId = ownerId, title = title, subject = subject, description = "$subject $type 준비",
            period = period, startDate = day(start), dueAt = due, level = level, priority = priority,
            weekdayMinutes = 120, weekendMinutes = 180, planStatus = PlanStatus.APPROVED,
        )

        val goals = listOf(
            goal("g0", "데이터베이스", "데이터베이스 기말고사", "시험", at("2026-11-07", "13:00"), GoalPeriod.WEEKLY, GoalLevel.INTERMEDIATE, 1, "2026-10-30"),
            goal("g1", "스마트앱개발프로그래밍", "프로그래밍 과제 제출", "과제", at("2026-11-12", "23:59"), GoalPeriod.WEEKLY, GoalLevel.INTERMEDIATE, 1, "2026-11-01"),
            goal("g2", "자료구조", "자료구조 기말고사", "시험", at("2026-11-15", "10:00"), GoalPeriod.MONTHLY, GoalLevel.INTERMEDIATE, 2, "2026-10-26"),
            goal("g3", "정보처리기사", "정보처리기사 필기 합격", "자격증", at("2026-11-29", "09:00"), GoalPeriod.MONTHLY, GoalLevel.BEGINNER, 3, "2026-10-20"),
        )

        return goals
    }
}
