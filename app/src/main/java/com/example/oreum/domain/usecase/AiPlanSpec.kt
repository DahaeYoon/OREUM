package com.example.oreum.domain.usecase

import com.example.oreum.data.model.Goal
import com.example.oreum.data.model.ItemKind
import com.example.oreum.data.model.NextProposal
import com.example.oreum.data.model.RoadmapDraft
import com.example.oreum.data.model.RoadmapStage
import com.example.oreum.data.model.RoundItem
import com.example.oreum.data.model.RoundStatus
import com.example.oreum.data.model.StudyRound
import com.example.oreum.data.model.StudySession
import java.time.LocalDate
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * 실제 AI(Gemini)에 보낼 요청문 만들기 · 받은 결과 검사 (순수 Kotlin, Firebase 없이 테스트 가능).
 *
 * 역할 나누기
 * - 앱(Kotlin): 남은 날 · 하루 가능 시간 · 실제 페이스 · 진행률 · 못 끝낸 항목 · 권장 분량을 정확히 계산해 AI에 넘겨요.
 * - AI: 로드맵 · 구간 계획 · 추천 이유를 만들어요.
 * - 앱(Kotlin): AI 결과가 형식 · 시간 조건에 맞는지 검사하고 맞춰요. 맞출 수 없으면 실패로 처리해요 (시뮬레이션으로 대신하지 않음).
 *
 * AI는 지난 요청을 기억하지 못해요. 그래서 다음 구간을 요청할 때마다 저장된 기록(지난 구간 결과 · 이전 AI 계획과
 * 사용자가 바꾼 점 · 페이스)을 요약해서 함께 보내요.
 */
object AiPlanSpec {

    /** AI 결과가 형식에 맞지 않을 때 */
    class BadResponse(message: String) : Exception(message)

    /** 모든 요청에 붙는 기본 지침 */
    const val SYSTEM = """너는 대학생의 시험 · 자격증 · 과제 준비를 돕는 학습 코치야.
- 학습 목표를 로드맵(큰 단계)과 '구간'(한 번에 할 학습 분량, 여러 날에 걸쳐 해도 됨)으로 나눠.
- 날짜별 일정은 만들지 마. 시간은 모두 분 단위 숫자야.
- 앱이 계산해 준 숫자(남은 날, 하루 가능 시간, 권장 분량, 못 끝낸 항목)를 그대로 믿고 따라.
- 모든 글은 한국어로, 짧고 구체적으로 써. 항목 이름은 20자 이내, 이유는 한 문장(35자 이내).
- 사용자를 탓하지 말고, 지난 결과에서 근거를 찾아 계획을 조정해.
- 응답은 지정한 JSON 형식으로만 해."""

    // ---------------- 로드맵 + 1구간 ----------------

    fun roadmapPrompt(g: Goal, today: Long): String {
        val left = RoundEngine.daysLeft(g, today)
        val perDay = RoundEngine.availPerDayHours(g)
        val total = perDay * max(1, left)
        val target = RoundEngine.roundTarget(g)
        return buildString {
            appendLine("[목표]")
            appendLine("- 이름: ${g.title}")
            if (g.description.isNotBlank()) appendLine("- 내용: ${g.description}")
            if (g.subject.isNotBlank()) appendLine("- 과목: ${g.subject}")
            appendLine("- 현재 수준: ${g.level.label}")
            appendLine("- 오늘: ${LocalDate.ofEpochDay(today)}, 마감: ${LocalDate.ofEpochDay(RoundEngine.dueDay(g))} (남은 날 ${left}일)")
            appendLine("- 학습 가능 시간: 평일 하루 ${g.weekdayMinutes}분, 주말 하루 ${g.weekendMinutes}분 (평균 하루 ${"%.1f".format(perDay)}시간)")
            appendLine("- 마감까지 쓸 수 있는 시간: 약 ${total.roundToInt()}시간")
            appendLine()
            appendLine("[요청]")
            appendLine("1) 로드맵: 단계 3~6개. 단계마다 title(15자 이내), topics(주제 2~4개, 각 20자 이내), minutes(예상 학습 시간, 분, 30분 단위).")
            appendLine("   전체 minutes 합은 쓸 수 있는 시간(${(total * 60).roundToInt()}분)의 85%를 넘지 않게 하고, 마지막 단계는 총정리 · 기출 · 최종 점검으로.")
            appendLine("2) 1구간: 1단계의 첫 주제부터. 항목(items) 3~6개, 항목마다 title과 minutes(5분 단위).")
            appendLine("   항목 minutes 합은 약 ${target}분(±20%). topic(구간 주제 한 줄), topics(이번에 다루는 로드맵 주제, 1단계 topics 중에서).")
            appendLine("   method(학습 방법 2개), criteria(완료 기준 2~3개, 확인 가능한 문장).")
        }
    }

    fun parseRoadmap(m: Map<String, Any?>, g: Goal, today: Long): RoadmapDraft {
        val stages = m.maps("stages").mapNotNull { s ->
            val title = s.str("title").take(30)
            if (title.isBlank()) return@mapNotNull null
            val topics = s.strs("topics").map { it.take(40) }.filter { it.isNotBlank() }.take(6).ifEmpty { listOf(title) }
            RoadmapStage(title = title, topics = topics, hours = roundHalf((s.dbl("minutes") / 60.0).coerceIn(0.5, 200.0)))
        }.take(8)
        if (stages.size < 2) throw BadResponse("로드맵 단계가 부족해요")
        val rm = m.map("round") ?: throw BadResponse("1구간 계획이 없어요")
        val target = RoundEngine.roundTarget(g)
        val items = parseItems(rm, target, allowKinds = false)
        val topics = rm.strs("topics").filter { it in stages[0].topics }.ifEmpty { listOf(stages[0].topics.first()) }
        val topic = rm.str("topic").take(40).ifBlank { topics.joinToString(" · ") }
        val round = StudyRound(
            id = RoundEngine.newId("rd"),
            ownerId = g.ownerId,
            goalId = g.id,
            no = 1,
            stage = 0,
            topic = topic,
            topics = topics,
            method = rm.strs("method").map { it.take(60) }.filter { it.isNotBlank() }.take(3),
            criteria = rm.strs("criteria").map { it.take(60) }.filter { it.isNotBlank() }.take(3),
            items = items,
            status = RoundStatus.READY,
            createdOn = today,
            byAi = true,
            aiPlannedCount = items.size,
            aiPlannedMinutes = items.sumOf { it.minutes },
        )
        return RoadmapDraft(goalId = g.id, roadmap = stages, round = round, createdOn = today, byAi = true)
    }

    // ---------------- 다음 구간 ----------------

    /**
     * 앱이 먼저 계산한 기준(baseline = RoundEngine.proposeNext 결과)과 저장된 기록을 요약해 AI에 보내요.
     * baseline.final이면 AI를 부르지 않아요 (로드맵을 모두 마친 상태).
     */
    fun nextPrompt(g: Goal, rounds: List<StudyRound>, sessions: List<StudySession>, today: Long, baseline: NextProposal): String {
        val mine = rounds.filter { it.goalId == g.id }.sortedBy { it.no }
        val done = mine.filter { it.status == RoundStatus.DONE }
        val last = done.last()
        val base = baseline.round ?: throw IllegalArgumentException("baseline round 없음")
        val left = RoundEngine.daysLeft(g, today)
        val week = (0..6).sumOf { k -> sessions.filter { it.goalId == g.id && it.date == today - k }.sumOf { it.minutes } }
        val weekDays = (0..6).count { k -> sessions.any { it.goalId == g.id && it.date == today - k && it.minutes > 0 } }
        val progress = RoadmapProgress.of(g, rounds)
        return buildString {
            appendLine("[목표]")
            appendLine("- 이름: ${g.title} (수준: ${g.level.label})")
            appendLine("- 남은 날 ${left}일, 학습 가능 시간: 평일 하루 ${g.weekdayMinutes}분 · 주말 하루 ${g.weekendMinutes}분")
            appendLine("- 로드맵 진행률 ${progress.percent}%, 남은 학습 약 ${progress.remainingHours.roundToInt()}시간")
            appendLine("- 최근 7일 실제 공부: ${weekDays}일, 합계 ${week}분")
            appendLine()
            appendLine("[로드맵] (index: 상태 · 단계 · 주제)")
            g.roadmap.forEachIndexed { i, st ->
                val f = RoadmapProgress.stageFraction(g, i, rounds)
                val state = when {
                    f >= 1f -> "완료"
                    i == g.stage -> "진행 중 ${(f * 100).roundToInt()}%"
                    else -> "예정"
                }
                appendLine("- $i: $state · ${st.title} · ${st.topics.joinToString(", ")}")
            }
            val covered = mine.flatMap { it.topics }.distinct()
            if (covered.isNotEmpty()) appendLine("- 이미 다룬 주제: ${covered.joinToString(", ")}")
            appendLine()
            appendLine("[방금 마친 ${last.no}구간] 주제: ${last.displayTopic}")
            last.items.forEach { i ->
                val state = if (i.done) "완료" else "미완료(${i.reason?.label ?: "이유 없음"})"
                appendLine("- ${i.title.removeSuffix(" (이어서)")}: 예상 ${i.minutes}분 / 실제 ${i.actualMinutes}분 · $state")
            }
            appendLine("- 합계: 예상 ${last.estimatedMinutes}분 / 실제 ${last.actualMinutes}분, 완료 ${last.doneCount}/${last.items.size}")
            last.result?.difficulty?.let { appendLine("- 체감 난이도: ${it.label}") }
            last.result?.memo?.takeIf { it.isNotBlank() }?.let { appendLine("- 사용자 메모: \"$it\"") }
            if (last.byAi) {
                append("- 이 구간은 AI가 제안한 계획(항목 ${last.aiPlannedCount}개, ${last.aiPlannedMinutes}분")
                if (last.aiFixes.isNotEmpty()) append(", 바꾼 점: ${last.aiFixes.joinToString(" · ")}")
                append(")이었고, 사용자가 항목 ${last.items.size}개 · ${last.estimatedMinutes}분으로 ")
                appendLine(if (last.items.size == last.aiPlannedCount && last.estimatedMinutes == last.aiPlannedMinutes) "그대로 진행했어요." else "조정해서 진행했어요.")
                appendLine("  → 지난 AI 판단이 맞았는지 결과로 확인하고, 다음 계획에 반영해.")
            }
            val older = done.dropLast(1)
            if (older.isNotEmpty()) {
                appendLine()
                appendLine("[그 전 구간 요약]")
                older.takeLast(2).forEach { r ->
                    appendLine("- ${r.no}구간 ${r.displayTopic}: 완료 ${r.doneCount}/${r.items.size}, 예상 ${r.estimatedMinutes}분 / 실제 ${r.actualMinutes}분" +
                        (r.result?.difficulty?.let { ", 난이도 ${it.label}" } ?: ""))
                }
                if (older.size > 2) {
                    val o = older.dropLast(2)
                    val n = o.sumOf { it.items.size }
                    val dn = o.sumOf { it.doneCount }
                    appendLine("- 그 이전 ${o.size}개 구간: 항목 완료율 ${if (n > 0) dn * 100 / n else 0}%")
                }
            }
            appendLine()
            appendLine("[앱이 계산한 기준]")
            appendLine("- 다음 구간 번호: ${last.no + 1}")
            appendLine("- 권장 단계 index: ${base.stage} (${g.roadmap.getOrNull(base.stage)?.title.orEmpty()}). 현재 단계(${g.stage}) 또는 다음 단계만 가능")
            val cand = nextTopics(g, rounds)
            if (cand.isNotEmpty()) appendLine("- 다음에 다룰 수 있는 주제: ${cand.joinToString(", ")}")
            val carry = last.items.filter { !it.done }
            if (carry.isNotEmpty()) {
                appendLine("- 반드시 이어서 할 못 끝낸 항목 (kind=CARRY, 줄이거나 나눠도 됨):")
                carry.forEach { i -> appendLine("  · ${i.title.removeSuffix(" (이어서)")}: 남은 약 ${max(10, i.minutes - min(i.actualMinutes, i.minutes))}분 (이유: ${i.reason?.label ?: "없음"})") }
            }
            appendLine("- 권장 분량: 약 ${base.estimatedMinutes}분 (지난 결과를 반영한 값, ±20% 안에서)")
            appendLine()
            appendLine("[요청] ${last.no + 1}구간 계획을 만들어 줘.")
            appendLine("- stage(단계 index), topic(구간 주제 한 줄), topics(이번에 새로 다루는 로드맵 주제, 없으면 빈 목록)")
            appendLine("- items 3~7개: title, minutes(5분 단위), kind(NEW=새 주제, CARRY=못 끝낸 것 이어서, REVIEW=복습)")
            appendLine("- method(학습 방법 2개), criteria(완료 기준 2~3개)")
            appendLine("- why: 이렇게 추천한 이유 2~3개 (지난 결과 숫자 · 메모 · 이유를 근거로)")
            appendLine("- fixes: 지난 구간과 비교해 바꾼 점 1~3개, 15자 이내 짧은 말 (예: \"분량 80%로 줄임\", \"못 끝낸 2개 이어서\", \"2단계 시작\")")
        }
    }

    fun parseNext(m: Map<String, Any?>, g: Goal, rounds: List<StudyRound>, today: Long, baseline: NextProposal): NextProposal {
        val base = baseline.round ?: throw IllegalArgumentException("baseline round 없음")
        val last = rounds.filter { it.goalId == g.id && it.status == RoundStatus.DONE }.maxBy { it.no }
        val maxStage = min(g.stage + 1, g.roadmap.size - 1)
        val stage = (m.intOrNull("stage") ?: base.stage).coerceIn(g.stage, max(g.stage, maxStage))
        val allowed = (g.roadmap.getOrNull(stage)?.topics.orEmpty() + g.roadmap.getOrNull(g.stage)?.topics.orEmpty()).toSet()
        val topics = m.strs("topics").filter { it in allowed }.distinct().take(3)
        var items = parseItems(m, base.estimatedMinutes, allowKinds = true)
        // 못 끝낸 항목은 꼭 이어서: AI가 빠뜨렸으면 앱이 계산한 이어서 항목을 앞에 넣어요
        val baseCarry = base.items.filter { it.kind == ItemKind.CARRY }
        if (baseCarry.isNotEmpty() && items.none { it.kind == ItemKind.CARRY }) items = baseCarry + items
        if (items.size > 10) items = items.take(10)
        val fixes = m.strs("fixes").map { it.trim().take(20) }.filter { it.isNotBlank() }.take(3)
        val why = m.strs("why").map { it.trim().take(60) }.filter { it.isNotBlank() }.take(3)
        if (why.isEmpty()) throw BadResponse("추천 이유가 없어요")
        val topic = m.str("topic").take(40).ifBlank { topics.joinToString(" · ").ifBlank { last.displayTopic + " (이어서)" } }
        val round = StudyRound(
            id = RoundEngine.newId("rd"),
            ownerId = g.ownerId,
            goalId = g.id,
            no = last.no + 1,
            stage = stage,
            topic = topic,
            topics = topics,
            method = m.strs("method").map { it.take(60) }.filter { it.isNotBlank() }.take(3).ifEmpty { base.method },
            criteria = m.strs("criteria").map { it.take(60) }.filter { it.isNotBlank() }.take(3).ifEmpty { base.criteria },
            items = items,
            status = RoundStatus.READY,
            createdOn = today,
            byAi = true,
            aiFixes = fixes,
            aiPlannedCount = items.size,
            aiPlannedMinutes = items.sumOf { it.minutes },
        )
        return baseline.copy(round = round, final = false, why = why, fixes = fixes.ifEmpty { listOf("지금 페이스 유지") }, createdOn = today, byAi = true)
    }

    /** 다음에 다룰 수 있는 로드맵 주제 (아직 다루지 않은 것, 최대 4개) */
    private fun nextTopics(g: Goal, rounds: List<StudyRound>): List<String> {
        val covered = rounds.filter { it.goalId == g.id }.flatMap { it.topics }.toSet()
        return (g.stage until g.roadmap.size).flatMap { g.roadmap[it].topics }.filter { it !in covered }.take(4)
    }

    /** 항목 검사: 이름 · 시간(5분 단위, 10~240분) · 종류, 합계가 권장의 1.6배를 넘으면 비율대로 줄여요 */
    private fun parseItems(m: Map<String, Any?>, target: Int, allowKinds: Boolean): List<RoundItem> {
        var list = m.maps("items").mapNotNull { i ->
            val title = i.str("title").trim().take(40)
            if (title.isBlank()) return@mapNotNull null
            val kind = if (allowKinds) ItemKind.entries.firstOrNull { it.name == i.str("kind").uppercase() } ?: ItemKind.NEW else ItemKind.NEW
            RoundItem(id = RoundEngine.newId("ri"), title = title, minutes = round5(i.dbl("minutes").coerceIn(10.0, 240.0)), kind = kind)
        }.take(10)
        if (list.isEmpty()) throw BadResponse("구간 항목이 없어요")
        val sum = list.sumOf { it.minutes }
        val cap = target * 1.6
        if (sum > cap) {
            val k = cap / sum
            list = list.map { it.copy(minutes = round5((it.minutes * k).coerceAtLeast(10.0))) }
        }
        return list
    }

    private fun round5(v: Double): Int = max(10, (v / 5.0).roundToInt() * 5)
    private fun roundHalf(v: Double): Double = max(0.5, (v * 2).roundToInt() / 2.0)

    // ---------------- JSON(Map) 읽기 도우미 ----------------
    private fun Map<String, Any?>.str(k: String) = (this[k] as? String)?.trim() ?: ""
    private fun Map<String, Any?>.dbl(k: String) = (this[k] as? Number)?.toDouble() ?: (this[k] as? String)?.toDoubleOrNull() ?: 0.0
    private fun Map<String, Any?>.intOrNull(k: String) = (this[k] as? Number)?.toInt() ?: (this[k] as? String)?.toIntOrNull()
    private fun Map<String, Any?>.strs(k: String) = (this[k] as? List<*>)?.mapNotNull { (it as? String)?.trim() } ?: emptyList()

    @Suppress("UNCHECKED_CAST")
    private fun Map<String, Any?>.map(k: String) = this[k] as? Map<String, Any?>

    @Suppress("UNCHECKED_CAST")
    private fun Map<String, Any?>.maps(k: String) = (this[k] as? List<*>)?.mapNotNull { it as? Map<String, Any?> } ?: emptyList()
}
