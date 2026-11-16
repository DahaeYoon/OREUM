package com.example.oreum.domain.usecase

import com.example.oreum.data.model.DailyLog
import com.example.oreum.data.model.MissReason
import com.example.oreum.data.model.RecoveryProposal
import com.example.oreum.data.model.Task
import com.example.oreum.util.DateText
import java.time.DayOfWeek
import java.time.LocalDate
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * 기록 화면 계산 (순수 Kotlin, 프로토타입 기록 화면과 같은 규칙).
 * 계획 대비 실제 · 달성률 · 반복되는 미완료 원인 · 학습 페이스(시뮬레이션) · 복구 적용 전후.
 * 시간은 모두 분 단위예요.
 */
object RecordsAnalysis {

    /** 하루 기록: 계획 · 실제 시간, 달성률, 미완료 이유, 복구 항목이 있었는지 */
    data class DayRecord(
        val date: Long,
        val planned: Int,
        val actual: Int,
        val reasons: List<MissReason>,
        val hasRecovery: Boolean,
    ) {
        val rate: Float get() = if (planned > 0) min(1f, actual.toFloat() / planned) else 0f
    }

    /** 학습 페이스 (최근 3일 · 시뮬레이션) */
    data class Pace(
        val enough: Boolean,
        /** 기록한 날 수 */
        val days: Int,
        val avgPlanned: Int = 0,
        val avgActual: Int = 0,
        val rate: Float = 0f,
        /** 그 전 3일 달성률 (없으면 null) */
        val prevRate: Float? = null,
        /** 제안: 한 번에 공부할 시간 (분) */
        val session: Int = 0,
        /** 제안: 하루 학습량 비율 (0.5 ~ 1.0) */
        val load: Float = 1f,
    ) {
        /** 달성률 70% 미만이면 학습량을 줄이자고 제안해요 */
        val low: Boolean get() = enough && rate < 0.7f
    }

    data class WeekBar(val start: Long, val planned: Int, val actual: Int) {
        val rate: Float get() = if (planned > 0) actual.toFloat() / planned else 0f
    }

    data class ReasonCount(val reason: MissReason, val count: Int)

    /** 복구 적용 전후 (after = 복구 후 기록한 날의 달성률, 아직 없으면 null) */
    data class RecoveryResult(val appliedOn: Long, val reasons: List<MissReason>, val before: Float, val after: Float?)

    private fun planned(t: Task) = t.originalMinutes ?: t.estimatedMinutes
    private fun actual(t: Task) = if (t.isDone) t.actualMinutes ?: t.estimatedMinutes else t.actualMinutes ?: 0

    fun dayRecord(tasks: List<Task>, date: Long, goalId: String?): DayRecord {
        val items = tasks.filter { it.plannedDate == date && it.goalId != null && (goalId == null || it.goalId == goalId) }
        return DayRecord(
            date = date,
            planned = items.sumOf { planned(it) },
            actual = items.sumOf { actual(it) },
            reasons = items.mapNotNull { it.missReason },
            hasRecovery = items.any { it.fromRecoveryId != null },
        )
    }

    /** 학습 결과를 기록한 날 (오늘까지, 계획이 있던 날만, 오래된 순) */
    fun loggedDays(tasks: List<Task>, logs: List<DailyLog>, today: Long, goalId: String?): List<DayRecord> =
        logs.filter { it.date <= today }.map { it.date }.distinct().sorted()
            .map { dayRecord(tasks, it, goalId) }
            .filter { it.planned > 0 }

    fun rate(days: List<DayRecord>): Float {
        val p = days.sumOf { it.planned }
        return if (p > 0) days.sumOf { it.actual }.toFloat() / p else 0f
    }

    fun pace(days: List<DayRecord>): Pace {
        val last = days.takeLast(3)
        if (last.size < 3) return Pace(enough = false, days = days.size)
        val p = last.sumOf { it.planned } / 3.0
        val a = last.sumOf { it.actual } / 3.0
        val rate = (a / max(p, 1.0)).toFloat()
        val prev = days.dropLast(3).takeLast(3)
        val prevRate = if (prev.isEmpty()) null else prev.sumOf { it.actual }.toFloat() / max(6, prev.sumOf { it.planned })
        val load = ((rate * 10).roundToInt() / 10f + 0.1f).coerceIn(0.5f, 1f)
        return Pace(
            enough = true,
            days = days.size,
            avgPlanned = p.roundToInt(),
            avgActual = a.roundToInt(),
            rate = rate,
            prevRate = prevRate,
            session = if (a >= 75) 45 else 30,
            load = load,
        )
    }

    fun weekStart(date: LocalDate): LocalDate = date.with(DayOfWeek.MONDAY)

    /** 최근 4주 중 기록이 있는 주 */
    fun weekBars(days: List<DayRecord>, today: LocalDate): List<WeekBar> {
        val start = weekStart(today).toEpochDay()
        return (3 downTo 0).mapNotNull { i ->
            val s = start - 7L * i
            val list = days.filter { it.date >= s && it.date < s + 7 }
            if (list.isEmpty()) null else WeekBar(s, list.sumOf { it.planned }, list.sumOf { it.actual })
        }
    }

    fun reasonCounts(days: List<DayRecord>): List<ReasonCount> =
        days.flatMap { it.reasons }.groupingBy { it }.eachCount()
            .map { ReasonCount(it.key, it.value) }
            .sortedByDescending { it.count }

    fun recoveryResults(recoveries: List<RecoveryProposal>, days: List<DayRecord>): List<RecoveryResult> =
        recoveries.map { h ->
            val applied = DateText.toDate(h.appliedAt ?: h.createdAt).toEpochDay()
            val lastFail = h.failDays.maxOrNull() ?: applied
            val after = days.filter { it.date > lastFail && it.date >= applied && it.hasRecovery }
            RecoveryResult(applied, h.reasons, h.expectedRateBefore, if (after.isEmpty()) null else rate(after))
        }
}
