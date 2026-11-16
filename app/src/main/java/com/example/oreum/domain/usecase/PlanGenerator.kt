package com.example.oreum.domain.usecase

import com.example.oreum.data.model.DraftItem
import com.example.oreum.data.model.Goal
import com.example.oreum.data.model.GoalLevel
import com.example.oreum.data.model.PlanDraft
import com.example.oreum.data.model.Task
import com.example.oreum.util.DateText
import java.time.DayOfWeek
import java.time.LocalDate
import kotlin.math.ceil
import kotlin.math.roundToInt

/**
 * AI 맞춤형 학습 계획 생성 (시뮬레이션 · 규칙 기반, 실제 AI API를 쓰지 않아요).
 * 고려: 기간 · 현재 수준 · 하루 학습 가능 시간 · 예상 학습량 · 다른 목표의 기존 계획 · (반영한 경우) 학습 페이스
 * - 하루 분량을 개념 학습 50% · 실습 · 문제 33% · 복습 퀴즈 17%로 나눠요.
 * - 일요일은 1시간 이내의 복습 · 오답 정리일이에요.
 * - 한 항목이 한 번에 공부할 시간(기본 60분)을 넘으면 나눠요.
 */
object PlanGenerator {

    private class Template(val baseHours: Int, val weeks: List<Pair<String, List<String>?>>)

    private val LINUX_MATCH = Regex("리눅스|linux", RegexOption.IGNORE_CASE)

    private val LINUX = Template(
        baseHours = 40,
        weeks = listOf(
            "기본 명령어 및 파일 시스템" to listOf("리눅스 개요와 설치", "기본 명령어 (ls · cd · cp · mv)", "파일 시스템 구조", "권한과 소유권 (chmod · chown)", "링크와 파일 검색", "vi 편집기"),
            "프로세스 및 패키지 관리" to listOf("프로세스와 ps · top", "작업 제어와 시그널", "rpm · yum 패키지 관리", "dpkg · apt 패키지 관리", "압축과 소스 설치", "부팅과 서비스 관리"),
            "네트워크 및 시스템 관리" to listOf("네트워크 기초와 IP", "네트워크 명령어", "사용자 · 그룹 관리", "디스크와 파일 시스템 관리", "RAID와 LVM", "로그와 백업"),
            "기출문제 풀이 및 오답 복습" to listOf("기출 1회 풀이", "기출 1회 오답 정리", "기출 2회 풀이", "기출 2회 오답 정리", "기출 3회 풀이", "취약 단원 최종 점검"),
        ),
    )

    private val GENERAL = Template(
        baseHours = 0,
        weeks = listOf("기초 개념 정리" to null, "핵심 내용 심화" to null, "문제 풀이 · 적용" to null, "총정리 · 오답 복습" to null),
    )

    private fun levelFactor(level: GoalLevel) = when (level) {
        GoalLevel.BEGINNER -> 1.25
        GoalLevel.INTERMEDIATE -> 1.0
        GoalLevel.ADVANCED -> 0.8
    }

    /** 10분 단위 반올림 (최소 10분) */
    private fun round10(minutes: Double): Int = maxOf(10, (minutes / 10).roundToInt() * 10)

    private fun isWeekend(date: LocalDate) =
        date.dayOfWeek == DayOfWeek.SATURDAY || date.dayOfWeek == DayOfWeek.SUNDAY

    /** 이 목표에 쓸 수 있는 그날의 학습 시간 (분) */
    fun goalCapacity(goal: Goal, date: LocalDate): Int =
        if (isWeekend(date)) goal.weekendMinutes else goal.weekdayMinutes

    /**
     * @param otherTasks 이미 있는 할 일 (다른 목표 계획과 합친 하루 시간 확인용)
     * @param userWeekdayMinutes · userWeekendMinutes 모든 목표를 합친 하루 학습 가능 시간 (마이 화면 설정)
     * @param paceRatio 기록 화면에서 반영한 페이스 (계획 대비 실제 비율). null이면 반영 안 함
     */
    fun generate(
        goal: Goal,
        today: LocalDate,
        otherTasks: List<Task>,
        userWeekdayMinutes: Int,
        userWeekendMinutes: Int,
        paceRatio: Float?,
        paceSessionMinutes: Int? = null,
        newId: () -> String,
    ): PlanDraft {
        val template = if (LINUX_MATCH.containsMatchIn(goal.title + goal.description)) LINUX else GENERAL
        val start = maxOf(LocalDate.ofEpochDay(goal.startDate), today)
        val end = maxOf(DateText.toDate(goal.dueAt), start)
        val days = generateSequence(start) { it.plusDays(1) }.takeWhile { !it.isAfter(end) }.toList()
        val weekCount = maxOf(1, ceil(days.size / 7.0).toInt())
        val load = (paceRatio ?: 1f).toDouble().coerceIn(0.4, 1.2)
        val session = paceSessionMinutes ?: if (paceRatio != null) 50 else 60

        val items = mutableListOf<DraftItem>()
        var capacityTotal = 0
        days.forEachIndexed { i, day ->
            val weekIndex = minOf(weekCount - 1, i / 7)
            val buffer = day.dayOfWeek == DayOfWeek.SUNDAY
            val capacity = goalCapacity(goal, day)
            capacityTotal += capacity
            val dayMinutes = if (buffer) minOf(capacity * load, 60.0) else capacity * load
            if (dayMinutes < 20) return@forEachIndexed

            val (theme, topics) = template.weeks[minOf(template.weeks.size - 1, weekIndex * template.weeks.size / weekCount)]
            val k = days.take(i + 1).withIndex()
                .count { (j, d) -> minOf(weekCount - 1, j / 7) == weekIndex && d.dayOfWeek != DayOfWeek.SUNDAY } - 1
            val topic = topics?.get(k.coerceIn(0, topics.size - 1))
                ?: "${goal.subject.ifBlank { goal.title }} $theme ${k + 1}"
            val parts = if (buffer) {
                listOf("이번 주 복습 · 오답 정리" to 1.0)
            } else {
                listOf("개념 학습: $topic" to 0.5, "실습 · 문제: $topic" to 0.33, "복습 퀴즈" to 0.17)
            }
            parts.forEach { (title, share) ->
                var left = round10(dayMinutes * share)
                val chunks = mutableListOf<Int>()
                while (left > session + 5) {
                    chunks += session
                    left -= session
                }
                chunks += left
                chunks.forEachIndexed { ci, minutes ->
                    items += DraftItem(
                        id = newId(),
                        title = if (chunks.size > 1) "$title (${ci + 1}/${chunks.size})" else title,
                        minutes = minutes,
                        date = day.toEpochDay(),
                        week = weekIndex + 1,
                    )
                }
            }
        }

        val planned = items.sumOf { it.minutes }
        val needed = if (template.baseHours > 0) (template.baseHours * 60 * levelFactor(goal.level)).roundToInt()
        else (planned * 0.9).roundToInt()

        val warnings = mutableListOf<String>()
        if (needed > capacityTotal) {
            warnings += "예상 학습량 ${DateText.minutes(needed)}이 기간 안의 학습 가능 시간 ${DateText.minutes(capacityTotal)}보다 많아요. 기간을 늘리거나 범위를 줄이는 것을 권해요."
        }
        val otherByDay = otherTasks.filter { it.goalId != goal.id && !it.isDone && it.plannedDate != null }
            .groupBy { it.plannedDate!! }
            .mapValues { (_, list) -> list.sumOf { it.estimatedMinutes } }
        val overDays = items.groupBy { it.date }.filter { (day, list) ->
            val date = LocalDate.ofEpochDay(day)
            val userCap = if (isWeekend(date)) userWeekendMinutes else userWeekdayMinutes
            (otherByDay[day] ?: 0) + list.sumOf { it.minutes } > userCap
        }.keys.sorted()
        if (overDays.isNotEmpty()) {
            warnings += "다른 목표의 기존 계획과 합치면 ${overDays.size}일이 하루 학습 가능 시간(평일 ${DateText.minutes(userWeekdayMinutes)} · 주말 ${DateText.minutes(userWeekendMinutes)})을 넘어요."
        }

        val themes = List(weekCount) { wi ->
            template.weeks[minOf(template.weeks.size - 1, wi * template.weeks.size / weekCount)].first
        }
        return PlanDraft(
            goalId = goal.id,
            items = items,
            weekThemes = themes,
            neededMinutes = needed,
            capacityMinutes = capacityTotal,
            warnings = warnings,
            overDays = overDays,
            paceApplied = paceRatio != null,
            createdOn = today.toEpochDay(),
        )
    }
}
