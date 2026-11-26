package com.example.oreum.domain.usecase

import com.example.oreum.data.model.Goal
import com.example.oreum.data.model.PlanStatus
import com.example.oreum.data.model.RoundStatus
import com.example.oreum.data.model.StudyRound
import com.example.oreum.data.model.StudySession
import kotlin.math.max
import kotlin.math.min

/**
 * 목표 진행률 (로드맵 단계 기준, 날짜와 무관 · 순수 Kotlin).
 * - 지난 단계 = 100%, 지금 단계 = 이 단계 구간에서 인정한 시간 / 단계 예상 시간 (최대 95%), 다음 단계 = 0%
 * - 인정 시간은 줄지 않아서 진행률도 내려가지 않아요.
 */
object RoadmapProgress {

    data class Progress(
        /** 0.0 ~ 1.0 */
        val rate: Float = 0f,
        val totalStages: Int = 0,
        val doneStages: Int = 0,
        val totalHours: Double = 0.0,
        val gotHours: Double = 0.0,
        /** 단계별 진행 (0.0 ~ 1.0) */
        val stageFractions: List<Float> = emptyList(),
    ) {
        val remainingHours: Double get() = max(0.0, totalHours - gotHours)
        val percent: Int get() = (rate * 100).toInt()
    }

    fun stageFraction(g: Goal, s: Int, rounds: List<StudyRound>): Float {
        val st = g.roadmap.getOrNull(s) ?: return 0f
        if (g.completedAt != null || s < g.stage) return 1f
        if (s > g.stage) return 0f
        val got = rounds.filter { it.goalId == g.id && it.stage == s && it.status != RoundStatus.READY }
            .sumOf { r -> r.items.sumOf { it.creditMinutes } }
        return min(0.95, got / (st.hours * 60)).toFloat()
    }

    fun of(g: Goal, rounds: List<StudyRound>): Progress {
        if (g.roadmap.isEmpty() || g.planStatus != PlanStatus.APPROVED) return Progress()
        val fr = g.roadmap.indices.map { stageFraction(g, it, rounds) }
        return Progress(
            rate = fr.sum() / g.roadmap.size,
            totalStages = g.roadmap.size,
            doneStages = fr.count { it >= 1f },
            totalHours = g.roadmap.sumOf { it.hours },
            gotHours = g.roadmap.indices.sumOf { g.roadmap[it].hours * fr[it] },
            stageFractions = fr,
        )
    }

    /** 여러 목표를 합친 진행률 (홈 '전체 목표'): 단계 수로 가중 평균 */
    fun ofAll(goals: List<Goal>, rounds: List<StudyRound>): Progress {
        val list = goals.filter { it.completedAt == null && it.planStatus == PlanStatus.APPROVED }.map { of(it, rounds) }
        val n = list.sumOf { it.totalStages }
        return Progress(
            rate = if (n > 0) list.sumOf { (it.rate * it.totalStages).toDouble() }.toFloat() / n else 0f,
            totalStages = n,
            doneStages = list.sumOf { it.doneStages },
            totalHours = list.sumOf { it.totalHours },
            gotHours = list.sumOf { it.gotHours },
        )
    }

    /** 마감까지 끝낼 수 있는지 (1차: 목표 상세의 한 줄 표시용, 시뮬레이션) */
    enum class FeasLevel(val label: String) { OK("여유 있어요"), TIGHT("시간이 부족할 수 있어요"), RISK("어려울 수 있어요"), DONE("완료") }

    data class Feasibility(
        val level: FeasLevel,
        val remainingHours: Double,
        val daysLeft: Int,
        /** 하루 평균 페이스 (시간): 최근 7일 중 3일 이상 공부했으면 실제 평균, 아니면 학습 가능 시간의 70% */
        val perDayHours: Double,
        val recentPace: Boolean,
        /** 마감까지 끝내려면 하루에 필요한 시간 */
        val needPerDayHours: Double,
    )

    fun feasibility(g: Goal, rounds: List<StudyRound>, sessions: List<StudySession>, today: Long): Feasibility {
        val rem = of(g, rounds).remainingHours
        val left = RoundEngine.daysLeft(g, today)
        val byDay = (1..7).map { d -> sessions.filter { it.goalId == g.id && it.date == today - d }.sumOf { it.minutes } / 60.0 }
        val studied = byDay.count { it > 0 }
        val recent = studied >= 3
        val perDay = if (recent) byDay.sum() / 7 else RoundEngine.availPerDayHours(g) * 0.7
        val ratio = if (rem > 0) perDay * left / rem else 9.0
        val level = when {
            rem <= 0.0 -> FeasLevel.DONE
            ratio >= 1.1 -> FeasLevel.OK
            ratio >= 0.9 -> FeasLevel.TIGHT
            else -> FeasLevel.RISK
        }
        return Feasibility(level, rem, left, perDay, recent, if (left > 0) rem / left else rem)
    }
}
