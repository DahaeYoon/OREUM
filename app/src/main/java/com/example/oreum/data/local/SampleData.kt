package com.example.oreum.data.local

import com.example.oreum.data.model.DailyLog
import com.example.oreum.data.model.Goal
import com.example.oreum.data.model.GoalLevel
import com.example.oreum.data.model.GoalPeriod
import com.example.oreum.data.model.MissReason
import com.example.oreum.data.model.PlanStatus
import com.example.oreum.data.model.Task
import com.example.oreum.data.model.TaskStatus
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

    data class Bundle(val goals: List<Goal>, val tasks: List<Task>, val logs: List<DailyLog>)

    fun build(today: LocalDate, ownerId: String = OWNER_ID): Bundle {
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

        /** hours = 계획 시간, actual = 실제 학습 시간 (시간 단위 → 분으로 바꿔 저장) */
        fun task(
            id: String, goalId: String, title: String, hours: Double, priority: Int, plan: String,
            done: Boolean, actual: Double? = null, reason: MissReason? = null,
        ): Task {
            val minutes = (hours * 60).toInt()
            return Task(
                id = id, ownerId = ownerId, goalId = goalId, title = title, estimatedMinutes = minutes,
                actualMinutes = actual?.let { (it * 60).toInt() } ?: if (done) minutes else null,
                priority = priority, plannedDate = day(plan),
                status = if (done) TaskStatus.DONE else TaskStatus.TODO,
                missReason = reason,
                // 샘플의 지난 미완료 항목은 이미 복구를 거친 것으로 둬요 (프로토타입과 같음)
                recoveryHandled = reason != null,
            )
        }

        val tasks = listOf(
            task("x4", "g0", "SQL 기초 복습", 1.5, 2, "2026-10-30", true, 1.5),
            task("x3", "g0", "관계 대수 정리", 2.0, 2, "2026-10-31", true, 2.1),
            task("d1", "g0", "ERD 설계 정리", 2.0, 2, "2026-11-01", true, 2.0),
            task("d2", "g0", "정규화 1~3NF 정리", 2.0, 1, "2026-11-02", true, 1.2),
            task("d3", "g0", "SELECT 기본 문법 연습", 1.0, 2, "2026-11-02", false, 0.3, MissReason.NO_FOCUS),
            task("x1", "g0", "조인 응용 문제", 1.0, 2, "2026-11-03", false, 0.2, MissReason.NO_FOCUS),
            task("d4", "g0", "조인 · 서브쿼리 연습", 2.0, 1, "2026-11-03", true, 1.3),
            task("x2", "g0", "트랜잭션 예제 풀이", 2.0, 2, "2026-11-04", false, 0.5, MissReason.NO_TIME),
            task("d5", "g0", "트랜잭션 · ACID 정리", 1.0, 2, "2026-11-04", true, 1.0),
            task("d6", "g0", "인덱스 · B+트리 정리", 1.5, 2, "2026-11-05", true),
            task("d7", "g0", "동시성 제어 · 락 정리", 2.0, 1, "2026-11-05", false),
            task("d8", "g0", "회복 기법 정리", 1.5, 2, "2026-11-06", false),
            task("d9", "g0", "기출 문제 1회 풀기", 2.0, 1, "2026-11-06", false),
            task("t1", "g1", "요구사항 정리", 1.0, 1, "2026-11-03", true),
            task("t2", "g1", "화면 레이아웃 XML 작성", 2.0, 1, "2026-11-09", false),
            task("t3", "g1", "일정 계산 로직 구현", 2.0, 1, "2026-11-10", false),
            task("t4", "g1", "테스트 및 버그 수정", 2.0, 2, "2026-11-11", false),
            task("t5", "g1", "결과 보고서 작성", 1.0, 3, "2026-11-12", false),
            task("t6", "g2", "트리·그래프 단원 정리", 2.0, 1, "2026-11-02", true),
            task("t7", "g2", "기출 문제 2회 풀기", 3.0, 1, "2026-11-12", false),
            task("t8", "g3", "1과목 요약 노트", 3.0, 2, "2026-11-01", true, 2.5),
            task("t9", "g3", "기출 3회 풀기", 3.0, 1, "2026-11-20", false),
        )

        val logs = listOf("2026-10-30", "2026-10-31", "2026-11-01", "2026-11-02", "2026-11-03", "2026-11-04")
            .map { DailyLog(id = "log-$it", ownerId = ownerId, date = day(it)) }

        return Bundle(goals, tasks, logs)
    }
}
