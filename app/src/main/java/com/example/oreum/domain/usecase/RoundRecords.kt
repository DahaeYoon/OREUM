package com.example.oreum.domain.usecase

import com.example.oreum.data.model.Goal
import com.example.oreum.data.model.RoundStatus
import com.example.oreum.data.model.StudyRound
import com.example.oreum.data.model.StudySession

/**
 * 기록 화면 계산 (1차 · 구간 기준, 순수 Kotlin)
 * - 공부한 날: 실제 학습 기록(StudySession)이 있는 날만 (오늘까지)
 * - 최근 7일 막대 · 하루 평균 · 항목 완료율 · 예상 대비 실제 한 줄
 */
object RoundRecords {

    /** 공부한 하루 */
    data class Day(
        val date: Long,
        val minutes: Int,
        /** 그날 공부한 구간 (처음 공부한 순서) */
        val rounds: List<StudyRound>,
        /** 그날 공부한 항목 수 */
        val items: Int,
    )

    /** 최근 마친 구간(최대 3개)의 페이스 */
    data class Pace(
        /** 항목 완료율 0~1 */
        val completion: Float,
        /** 실제 / 예상 시간 */
        val ratio: Float,
        /** 계산에 쓴 구간 수 */
        val count: Int,
    )

    private fun inGoal(goalId: String?, id: String) = goalId == null || goalId == id

    /** 공부한 날 (오래된 순) */
    fun studyDays(rounds: List<StudyRound>, sessions: List<StudySession>, goalId: String?, today: Long): List<Day> {
        val byId = rounds.associateBy { it.id }
        return sessions
            .filter { inGoal(goalId, it.goalId) && it.date <= today && it.minutes > 0 }
            .groupBy { it.date }
            .toSortedMap()
            .map { (date, list) ->
                Day(
                    date = date,
                    minutes = list.sumOf { it.minutes },
                    rounds = list.map { it.roundId }.distinct().mapNotNull { byId[it] },
                    items = list.map { it.itemId }.distinct().size,
                )
            }
    }

    /** 최근 7일 (오늘 포함, 오래된 순) 날짜별 공부 시간(분) */
    fun last7(sessions: List<StudySession>, goalId: String?, today: Long): List<Pair<Long, Int>> =
        (6 downTo 0).map { k ->
            val d = today - k
            d to sessions.filter { it.date == d && inGoal(goalId, it.goalId) }.sumOf { it.minutes }
        }

    /** 최근 마친 구간 3개로 본 페이스 (마친 구간이 없으면 null) */
    fun pace(rounds: List<StudyRound>, goalId: String?): Pace? {
        val done = rounds
            .filter { it.status == RoundStatus.DONE && inGoal(goalId, it.goalId) }
            .sortedBy { it.doneOn ?: 0L }
            .takeLast(3)
        if (done.isEmpty()) return null
        val est = done.sumOf { it.estimatedMinutes }
        val act = done.sumOf { it.actualMinutes }
        val n = done.sumOf { it.items.size }
        val dn = done.sumOf { it.doneCount }
        return Pace(
            completion = if (n > 0) dn.toFloat() / n else 0f,
            ratio = if (est > 0) act.toFloat() / est else 1f,
            count = done.size,
        )
    }

    /** 구간별 목록: 마쳤거나 공부한 날이 있는 구간 (최근에 시작한 순) */
    fun roundList(rounds: List<StudyRound>, goalId: String?): List<StudyRound> =
        rounds
            .filter { inGoal(goalId, it.goalId) && (it.status == RoundStatus.DONE || it.dates.isNotEmpty()) }
            .sortedWith(compareByDescending<StudyRound> { it.dates.firstOrNull() ?: it.startedOn ?: it.createdOn }.thenByDescending { it.no })

    /** 목표 짧은 이름 (예: '데이터베이스 기말고사' → '데이터베이스') */
    fun shortName(goals: List<Goal>, goalId: String): String =
        goals.firstOrNull { it.id == goalId }?.title?.substringBefore(' ').orEmpty()
}
