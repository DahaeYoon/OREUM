package com.example.oreum.domain.usecase

import com.example.oreum.data.model.DailyLog
import com.example.oreum.data.model.Goal
import com.example.oreum.data.model.MissReason
import com.example.oreum.data.model.RetroTag
import com.example.oreum.data.model.Task
import com.example.oreum.util.DateText
import java.time.LocalDate
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * AI 학습 계획 복구 (시뮬레이션 · 규칙 기반, 순수 Kotlin). 프로토타입 buildRecovery와 같은 규칙이에요.
 *
 * 1) 미완료 이유 · 회고 키워드 · 메모로 원인을 정리하고
 * 2) 남은 분량을 이유에 맞게 새 항목으로 나눈 뒤 (어려움 → 기초 복습 + 2번에 나눠서, 집중 → 25분 세션 …)
 * 3) 내일부터 하루 가능 시간 안에 넣고, 넘치는 기존 항목은 다음 날로 밀어요 (마감을 넘으면 '넣을 수 없음').
 *
 * 계산만 하고 원본 계획은 바꾸지 않아요. 사용자가 '복구 승인'을 눌러야 저장소에 적용돼요.
 */
object RecoveryPlanner {

    /** 이유 · 키워드 분류 (프로토타입 RS · TAG_RULE의 k) */
    enum class Kind { TIME, HARD, FOCUS, EVENT, MUCH, ETC, SOLVE, TIRED, REVIEW, TOPIC }

    data class NewItem(
        val goalId: String,
        val title: String,
        val minutes: Int,
        /** 넣을 날 (epoch day). null이면 마감 안에 넣지 못한 항목 */
        val date: Long?,
        val week: Int,
        val kind: Kind,
    )

    data class Move(val taskId: String, val title: String, val from: Long, val to: Long)

    /** 비교 표의 한 줄 */
    data class Line(
        val title: String,
        val minutes: Int,
        val isNew: Boolean = false,
        val moved: Boolean = false,
        /** 기존 계획에서 다른 날로 옮겨지는 항목 */
        val gone: Boolean = false,
    )

    data class DayChange(val date: Long, val before: List<Line>, val after: List<Line>) {
        val beforeMinutes get() = before.sumOf { it.minutes }
        val afterMinutes get() = after.sumOf { it.minutes }
    }

    data class Plan(
        val pending: List<Task>,
        val newItems: List<NewItem>,
        val moves: List<Move>,
        val days: List<DayChange>,
        val unplacedMinutes: Int,
        val goalIds: List<String>,
        /** 미완료 이유 (짧은 글자, 중복 없이) */
        val reasons: List<MissReason>,
        val badTags: List<RetroTag>,
        val goodTags: List<RetroTag>,
        val memos: List<String>,
        val extraNote: String,
        val topic: String?,
        /** 원인 문장 (최대 3개 + 어려웠던 부분) */
        val why: List<String>,
        /** 이렇게 바꿀게요 (최대 3개) */
        val fixes: List<String>,
        /** 미완료가 있던 날의 계획 · 실제 시간 (분) */
        val plannedMinutes: Int,
        val actualMinutes: Int,
        /** 최근 3일 평균 달성률 (기록이 3일보다 적으면 null) */
        val recentRate: Float?,
    ) {
        val remainingMinutes get() = newItems.sumOf { it.minutes }
        val placedItems get() = newItems.filter { it.date != null }
    }

    private val SHORT_WHY = mapOf(
        Kind.TIME to "하루 계획이 쓸 수 있는 시간보다 많았어요",
        Kind.HARD to "한 번에 다루는 범위가 커서 이해에 시간이 더 필요했어요",
        Kind.FOCUS to "한 번에 길게 앉아 있는 방식이 부담이 됐어요",
        Kind.EVENT to "갑작스러운 일정으로 쓸 수 있는 시간이 줄었어요",
        Kind.MUCH to "하루 학습량이 많았어요",
    )

    private val RS_FIX = mapOf(
        Kind.TIME to listOf("남은 학습량을 여유 있는 날로 재분배", "우선순위가 높은 항목을 먼저 배치"),
        Kind.HARD to listOf("학습 단위를 더 작게 나눔", "들어가기 전에 기초 개념 복습을 추가"),
        Kind.FOCUS to listOf("한 번에 25분씩 나누고 5분 휴식", "긴 항목을 여러 세션으로 분할"),
        Kind.EVENT to listOf("앞으로의 학습 가능 시간을 다시 계산", "여유가 있는 날로 일정 재배치"),
        Kind.MUCH to listOf("하루 학습량을 80%로 줄임", "넘치는 분량은 뒤로 미루고 목표 범위 재조정 안내"),
        Kind.ETC to listOf("남은 학습량을 여유 있는 날로 재분배"),
    )

    /** 회고 키워드 → 분류 · 짧은 원인 · 해결 방법 */
    private data class TagRule(val kind: Kind, val short: String, val fix: String)

    private val TAG_RULE = mapOf(
        RetroTag.HARD_PROBLEMS to TagRule(Kind.SOLVE, "개념을 문제에 적용하는 연습이 부족했어요", "문제를 풀기 전에 예제를 해설과 함께 먼저 보기"),
        RetroTag.DISTRACTED to TagRule(Kind.FOCUS, "집중이 자주 끊겼어요", "25분 공부 + 5분 휴식으로 나눠서 진행"),
        RetroTag.TIRED to TagRule(Kind.TIRED, "컨디션 때문에 효율이 떨어졌어요", "한 번에 30분 이하로 짧게 나누기"),
        RetroTag.NEED_REVIEW to TagRule(Kind.REVIEW, "앞 내용을 다시 볼 시간이 필요해요", "다음 날 시작할 때 20분 복습 추가"),
        RetroTag.TOO_MUCH to TagRule(Kind.MUCH, "하루 분량이 많았어요", "하루 학습량을 80%로 줄이기"),
    )

    fun kindOf(reason: MissReason?): Kind = when (reason) {
        MissReason.NO_TIME -> Kind.TIME
        MissReason.TOO_HARD -> Kind.HARD
        MissReason.NO_FOCUS -> Kind.FOCUS
        MissReason.SUDDEN_EVENT -> Kind.EVENT
        MissReason.TOO_MUCH -> Kind.MUCH
        else -> Kind.ETC
    }

    /** 복구가 필요한 미완료 항목: 이유를 기록했고 아직 복구에서 다루지 않았고, 진행 중인 목표의 지난 · 오늘 항목 */
    fun pending(tasks: List<Task>, goals: List<Goal>, today: Long): List<Task> {
        val active = goals.filter { it.completedAt == null }.map { it.id }.toSet()
        return PlanCalendar.pendingRecovery(tasks)
            .filter { it.goalId in active && (it.plannedDate ?: Long.MAX_VALUE) <= today }
            .sortedWith(compareBy({ it.plannedDate }, { it.order }))
    }

    /** 메모에서 어려웠던 부분 뽑기: '헷갈 · 어려 · 모르겠 …' 앞의 단어 (예: "락 부분이 헷갈렸어요" → 락) */
    fun memoTopic(text: String?): String? {
        if (text.isNullOrBlank()) return null
        val i = Regex("헷갈|어려|이해가 안|이해 안|모르겠|막혔|막힘").find(text)?.range?.first ?: return null
        if (i <= 0) return null
        val filler = Regex("^(너무|좀|조금|오늘|진짜|정말|많이|잘|다|그리고|근데|보다가|하다가)$")
        val words = text.substring(0, i).split(Regex("[\\s,./]+")).filter { it.isNotEmpty() }
            .map { it.replace(Regex("(부분이|부분은|부분|개념이|개념은|개념|문제가|문제는|에서|이|가|은|는|을|를)$"), "") }
            .filter { it.isNotEmpty() && !filler.matches(it) && !Regex("(다가|하고|해서)$").containsMatchIn(it) }
        return words.takeLast(3).joinToString(" ").trim().ifEmpty { null }
    }

    /** 메모에 보이는 습관 힌트: 피곤 · 딴짓 · 시간 부족 */
    fun memoHints(text: String): List<Kind> = buildList {
        if (text.isBlank()) return@buildList
        if (Regex("졸|피곤|잠이").containsMatchIn(text)) add(Kind.TIRED)
        if (Regex("폰|유튜브|딴짓|SNS|인스타", RegexOption.IGNORE_CASE).containsMatchIn(text)) add(Kind.FOCUS)
        if (Regex("시간이? ?(없|부족)|알바|약속|과제가").containsMatchIn(text)) add(Kind.TIME)
    }

    private fun r10(m: Double): Int = max(10, (m / 10).roundToInt() * 10)

    private val TITLE_PREFIX = Regex("^(개념 학습|실습 · 문제): ")

    fun build(
        tasks: List<Task>,
        goals: List<Goal>,
        logs: List<DailyLog>,
        today: Long,
        extraNote: String = "",
    ): Plan? {
        val pend = pending(tasks, goals, today)
        if (pend.isEmpty()) return null
        val pendIds = pend.map { it.id }.toSet()
        val from = today + 1

        // 회고: 미완료가 있던 날의 키워드 · 메모 + 추가 설명
        val failDays = pend.mapNotNull { it.plannedDate }.distinct()
        val dayLogs = logs.filter { it.date in failDays }.sortedBy { it.date }
        val tags = LinkedHashSet<RetroTag>().apply { dayLogs.forEach { addAll(it.tags) } }.toList()
        val memos = dayLogs.map { it.memo.trim() }.filter { it.isNotEmpty() }
        val extra = extraNote.trim()
        val topic = memoTopic(extra) ?: memoTopic(memos.joinToString(" / "))
        val hints = memoHints((memos + extra).joinToString(" / "))
        val mods = (tags.mapNotNull { TAG_RULE[it]?.kind } + hints).toSet()

        val sess = if (Kind.TIRED in mods) 30 else 25
        val splitOn = Kind.FOCUS in mods || Kind.TIRED in mods
        fun cap(goal: Goal?, day: Long, kind: Kind): Double {
            val base = if (goal == null) 120 else PlanCalendar.capacity(LocalDate.ofEpochDay(day), goal, 0, 0)
            return base * if (kind == Kind.MUCH || Kind.MUCH in mods) 0.8 else 1.0
        }

        // 1) 미완료 항목마다 이유에 맞게 남은 분량을 새 항목으로
        val newItems = mutableListOf<NewItem>()
        pend.forEach { t ->
            val k = kindOf(t.missReason)
            val gid = t.goalId ?: return@forEach
            val est = t.estimatedMinutes
            val rem = max(10, est - min(t.actualMinutes ?: 0, est))
            val base = topic ?: t.title.replace(TITLE_PREFIX, "")
            val parts = mutableListOf<Triple<String, Int, Int>>()
            when {
                k == Kind.HARD && rem >= 30 -> {
                    val rv = r10(rem * 0.3)
                    val p1 = r10((rem - rv) / 2.0)
                    val p2 = max(10, rem - rv - p1)
                    parts += Triple("기초 개념 복습: $base", rv, 0)
                    parts += Triple("${t.title} (나눠서 1/2)", p1, 0)
                    parts += Triple("${t.title} (나눠서 2/2)", p2, 1)
                }
                (k == Kind.FOCUS || splitOn) && rem > sess -> {
                    val s = if (k == Kind.FOCUS && Kind.TIRED !in mods) 25 else sess
                    val n = ceil(rem / s.toDouble()).toInt()
                    val rest = if (k == Kind.FOCUS || Kind.FOCUS in mods) " + 휴식 5분" else ""
                    repeat(n) { i ->
                        parts += Triple("${t.title} · 세션 ${i + 1} (${s}분$rest)", if (i < n - 1) s else rem - s * (n - 1), i / 2)
                    }
                }
                else -> parts += Triple("${t.title} (이어서)", rem, 0)
            }
            if (Kind.SOLVE in mods && k != Kind.HARD) parts.add(0, Triple("예제 해설 먼저 보기: $base", 15, 0))
            parts.forEach { (title, m, off) ->
                newItems += NewItem(gid, title, max(10, m), if (k == Kind.EVENT) null else from + off, t.week, k)
            }
        }
        // 회고 키워드 · 메모로 생기는 추가 항목 (맨 앞)
        val g0 = pend.first().goalId!!
        val hasHard = newItems.any { it.kind == Kind.HARD }
        if (Kind.REVIEW in mods) {
            newItems.add(0, NewItem(g0, "복습: ${topic ?: "지난 학습 내용"}", 20, from, pend.first().week, Kind.REVIEW))
        } else if (topic != null && !hasHard) {
            newItems.add(0, NewItem(g0, "개념 확인: $topic", 10, from, pend.first().week, Kind.TOPIC))
        }

        // 2) 날짜 배치: '갑작스러운 일정'은 여유 있는 날을 찾고, 넘치는 기존 항목은 다음 날로 밀기
        class Slot(val id: String, val minutes: Int, val title: String, val orig: Long?, val fixed: Boolean, val isNew: Boolean)

        val placed = newItems.toMutableList()
        val moves = mutableListOf<Move>()
        var unplacedMinutes = 0
        val goalIds = newItems.map { it.goalId }.distinct()
        goalIds.forEach { gid ->
            val goal = goals.firstOrNull { it.id == gid }
            val end = goal?.let { DateText.toDate(it.dueAt).toEpochDay() } ?: (from - 1)
            val days = if (end >= from) (from..end).toList() else emptyList()
            val future = tasks.filter { q ->
                q.goalId == gid && !q.isDone && !q.carriedOver && q.id !in pendIds && (q.plannedDate ?: -1) >= from
            }.sortedBy { it.order }
            val load = days.associateWith { d ->
                future.filter { it.plannedDate == d }
                    .map { Slot(it.id, it.estimatedMinutes, it.title, d, fixed = false, isNew = false) }.toMutableList()
            }
            val mineIdx = placed.indices.filter { placed[it].goalId == gid }
            val kind = placed[mineIdx.first()].kind
            mineIdx.forEach { idx ->
                val n = placed[idx]
                val d = n.date ?: days.firstOrNull { q -> load.getValue(q).sumOf { it.minutes } + n.minutes <= cap(goal, q, kind) } ?: from
                val list = load[d]
                if (list == null) {
                    placed[idx] = n.copy(date = null)
                    unplacedMinutes += n.minutes
                } else {
                    list.add(0, Slot("new-$idx", n.minutes, n.title, null, fixed = true, isNew = true))
                    placed[idx] = n.copy(date = d)
                }
            }
            // 넘치면 그날의 마지막 기존 항목을 다음 날로 (마감을 넘으면 넣을 수 없음)
            days.forEachIndexed { i, d ->
                val list = load.getValue(d)
                var guard = 30
                while (list.sumOf { it.minutes } > cap(goal, d, kind) && guard-- > 0) {
                    val at = list.indexOfLast { !it.fixed }
                    if (at < 0) break
                    val x = list.removeAt(at)
                    if (i + 1 < days.size) load.getValue(days[i + 1]).add(0, x) else unplacedMinutes += x.minutes
                }
            }
            days.forEach { d ->
                load.getValue(d).forEach { x -> if (!x.isNew && x.orig != d) moves += Move(x.id, x.title, x.orig!!, d) }
            }
        }

        // 3) 비교: 바뀐 날짜들의 기존 계획 / 수정 계획
        val moveTo = moves.associate { it.taskId to it.to }
        val touched = (placed.mapNotNull { it.date } + moves.flatMap { listOf(it.from, it.to) }).distinct().sorted()
        fun inGoal(q: Task) = q.goalId in goalIds && !q.carriedOver && q.id !in pendIds
        val dayChanges = touched.map { d ->
            val before = tasks.filter { inGoal(it) && it.plannedDate == d }.sortedBy { it.order }
                .map { Line(it.title, it.estimatedMinutes, gone = it.id in moveTo) }
            val after = placed.filter { it.date == d }.map { Line(it.title, it.minutes, isNew = true) } +
                tasks.filter { inGoal(it) && (moveTo[it.id] ?: it.plannedDate) == d }.sortedBy { it.order }
                    .map { Line(it.title, it.estimatedMinutes, moved = it.id in moveTo) }
            DayChange(d, before, after)
        }

        // 원인 · 해결 방법 문장
        val kinds = pend.map { kindOf(it.missReason) }.distinct()
        val badTags = tags.filter { it in TAG_RULE }
        val goodTags = tags.filter { it.good }
        val why = (kinds.mapNotNull { SHORT_WHY[it] } + badTags.map { TAG_RULE.getValue(it).short }).distinct().take(3).toMutableList()
        if (topic != null) why.add(0, "‘$topic’ 부분이 특히 어려웠어요")
        val topicFix = topic?.let {
            val what = when {
                Kind.REVIEW in mods -> "복습 20분"
                placed.any { n -> n.kind == Kind.HARD } -> "기초 개념 복습"
                else -> "개념 확인 10분"
            }
            "‘$it’ ${what}을 다음 날 맨 앞에"
        }
        val fixes = (listOfNotNull(topicFix) + badTags.map { TAG_RULE.getValue(it).fix } + kinds.flatMap { RS_FIX[it].orEmpty() })
            .distinct().take(3)

        // 계획 · 실제 시간 (미완료가 있던 날, 복구 대상 목표만)
        fun dayPlanned(d: Long, gid: String?) = tasks.filter { it.plannedDate == d && it.goalId != null && (gid == null || it.goalId == gid) }
        fun planned(t: Task) = t.originalMinutes ?: t.estimatedMinutes
        fun actual(t: Task) = if (t.isDone) t.actualMinutes ?: t.estimatedMinutes else t.actualMinutes ?: 0
        val dayTasks = failDays.flatMap { d -> goalIds.flatMap { dayPlanned(d, it) } }
        val p = dayTasks.sumOf { planned(it) }
        val a = dayTasks.sumOf { actual(it) }
        val recent = logs.filter { it.date <= today }.sortedBy { it.date }.mapNotNull { log ->
            val L = dayPlanned(log.date, null)
            val pl = L.sumOf { planned(it) }
            if (pl > 0) min(1f, L.sumOf { actual(it) }.toFloat() / pl) else null
        }.takeLast(3)

        return Plan(
            pending = pend,
            newItems = placed,
            moves = moves,
            days = dayChanges,
            unplacedMinutes = unplacedMinutes,
            goalIds = goalIds,
            reasons = pend.mapNotNull { it.missReason }.distinct(),
            badTags = badTags,
            goodTags = goodTags,
            memos = memos,
            extraNote = extra,
            topic = topic,
            why = why,
            fixes = fixes,
            plannedMinutes = p,
            actualMinutes = a,
            recentRate = if (recent.size >= 3) recent.average().toFloat() else null,
        )
    }
}
