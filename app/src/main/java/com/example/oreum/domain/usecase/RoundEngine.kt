package com.example.oreum.domain.usecase

import com.example.oreum.data.model.Difficulty
import com.example.oreum.data.model.Goal
import com.example.oreum.data.model.GoalLevel
import com.example.oreum.data.model.ItemKind
import com.example.oreum.data.model.MissReason
import com.example.oreum.data.model.NextProposal
import com.example.oreum.data.model.RetroTag
import com.example.oreum.data.model.RoadmapDraft
import com.example.oreum.data.model.RoadmapStage
import com.example.oreum.data.model.RoundItem
import com.example.oreum.data.model.RoundStatus
import com.example.oreum.data.model.StudyRound
import java.time.Instant
import java.time.ZoneId
import java.util.UUID
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * AI 로드맵 · 구간 생성과 다음 구간 추천 (시뮬레이션: 규칙 기반, 순수 Kotlin).
 * 실제 AI를 연결하면 이 객체만 바꾸면 돼요. 결과는 모두 '제안'이고, 저장소에서 승인해야 확정돼요.
 * 날짜는 모두 epoch day(Long)로 다뤄요.
 */
object RoundEngine {

    /** 새 id (테스트에서 바꿔 끼울 수 있어요) */
    var newId: (prefix: String) -> String = { p -> p + "-" + UUID.randomUUID().toString().take(8) }

    // ---------------- 로드맵 템플릿 ----------------
    private class Template(val match: Regex?, val baseHours: Double, val stages: List<Pair<String, List<String>>>)

    private val TEMPLATES = listOf(
        Template(Regex("리눅스|linux", RegexOption.IGNORE_CASE), 40.0, listOf(
            "기본 명령어 · 파일 시스템" to listOf("리눅스 개요와 설치", "기본 명령어 (ls · cd · cp · mv)", "파일 시스템 구조", "권한과 소유권"),
            "프로세스 · 패키지 관리" to listOf("프로세스와 작업 제어", "rpm · yum 패키지", "dpkg · apt 패키지", "부팅과 서비스"),
            "네트워크 · 시스템 관리" to listOf("네트워크 기초와 명령어", "사용자 · 그룹 관리", "디스크 · RAID · LVM", "로그와 백업"),
            "기출 풀이 · 오답 복습" to listOf("기출 1회 풀이 · 오답", "기출 2회 풀이 · 오답", "취약 단원 최종 점검"),
        )),
        Template(Regex("데이터베이스|DB|SQL", RegexOption.IGNORE_CASE), 15.0, listOf(
            "SQL 기초 · 관계 대수" to listOf("SQL 기본 질의", "관계 대수", "조인과 서브쿼리"),
            "정규화 · 설계" to listOf("함수 종속", "1~3 정규형", "BCNF와 반정규화"),
            "트랜잭션 · 동시성" to listOf("트랜잭션과 ACID", "동시성 제어 · 락", "회복 기법"),
            "총정리 · 기출" to listOf("기출 풀이", "오답 정리 · 최종 점검"),
        )),
        Template(Regex("자료구조|알고리즘"), 18.0, listOf(
            "선형 구조" to listOf("배열 · 연결 리스트", "스택 · 큐"),
            "트리" to listOf("이진 트리 · 순회", "이진 탐색 트리 · 힙"),
            "그래프 · 정렬" to listOf("그래프 탐색 (DFS · BFS)", "최단 경로", "정렬 알고리즘"),
            "총정리 · 기출" to listOf("기출 풀이", "오답 정리 · 최종 점검"),
        )),
        Template(Regex("정보처리|기사|자격증"), 36.0, listOf(
            "소프트웨어 설계" to listOf("요구사항 분석", "UML · 설계 모델", "디자인 패턴"),
            "소프트웨어 개발" to listOf("자료구조 기초", "통합 구현 · 테스트", "인터페이스 구현"),
            "데이터베이스 구축" to listOf("논리 · 물리 설계", "SQL 활용", "정규화"),
            "프로그래밍 언어 활용" to listOf("C · Java 기초 문법", "Python · 운영체제 기초"),
            "정보시스템 구축 관리" to listOf("네트워크 · 보안", "프로젝트 관리"),
            "기출 풀이 · 오답 복습" to listOf("기출 1회 풀이 · 오답", "기출 2회 풀이 · 오답", "취약 과목 최종 점검"),
        )),
        Template(Regex("과제|프로젝트|앱|개발"), 14.0, listOf(
            "요구사항 · 화면 설계" to listOf("요구사항 정리", "화면 흐름 · 와이어프레임"),
            "기능 구현" to listOf("데이터 저장 구현", "화면 · 이벤트 구현", "예외 처리"),
            "테스트 · 제출" to listOf("기능 테스트 · 수정", "보고서 작성 · 제출"),
        )),
    )

    private fun general(g: Goal): Template {
        val s = g.subject.ifBlank { g.title.substringBefore(' ') }
        return Template(null, 0.0, listOf(
            "기초 개념 정리" to listOf("$s 기본 개념", "$s 핵심 용어 정리"),
            "핵심 내용 심화" to listOf("$s 핵심 내용 1", "$s 핵심 내용 2"),
            "문제 풀이 · 적용" to listOf("$s 예제 풀이", "$s 응용 문제"),
            "총정리 · 오답 복습" to listOf("오답 정리", "최종 점검"),
        ))
    }

    private fun templateOf(g: Goal): Template {
        val text = "${g.title} ${g.description} ${g.subject}"
        return TEMPLATES.firstOrNull { it.match!!.containsMatchIn(text) } ?: general(g)
    }

    private fun levelFactor(level: GoalLevel) = when (level) {
        GoalLevel.BEGINNER -> 1.25
        GoalLevel.INTERMEDIATE -> 1.0
        GoalLevel.ADVANCED -> 0.8
    }

    // ---------------- 날짜 · 시간 도우미 ----------------
    /** 마감일이 지났는데 아직 끝나지 않은 목표 */
    fun isOverdue(g: Goal, today: Long): Boolean = g.completedAt == null && g.dueAt > 0 && today > dueDay(g)

    fun dueDay(g: Goal): Long = Instant.ofEpochMilli(g.dueAt).atZone(ZoneId.systemDefault()).toLocalDate().toEpochDay()
    private fun dueHour(g: Goal): Int = Instant.ofEpochMilli(g.dueAt).atZone(ZoneId.systemDefault()).hour

    /** 하루 평균 학습 가능 시간 (시간) */
    fun availPerDayHours(g: Goal): Double = (g.weekdayMinutes * 5 + g.weekendMinutes * 2) / 7.0 / 60.0

    /** 마감까지 남은 날 (마감이 낮 12시 이후면 그날도 셈) */
    fun daysLeft(g: Goal, today: Long): Int = max(0, (dueDay(g) - today).toInt() + if (dueHour(g) >= 12) 1 else 0)

    /** 분 단위 10분 반올림 (최소 10분) */
    fun r10(m: Double): Int = max(10, (m / 10.0).roundToInt() * 10)

    /** 분 → "1시간 30분" */
    fun minText(total: Int): String {
        val h = total / 60
        val m = total % 60
        return when {
            h == 0 -> "${m}분"
            m == 0 -> "${h}시간"
            else -> "${h}시간 ${m}분"
        }
    }

    // ---------------- 로드맵 ----------------
    fun genRoadmap(g: Goal, today: Long): List<RoadmapStage> {
        val tp = templateOf(g)
        val w = tp.stages.map { it.second.size }
        val startLeft = max(0L, g.startDate - today)
        val days = max(1L, (dueDay(g) - today) + 1 - startLeft)
        val total = if (tp.baseHours > 0) tp.baseHours * levelFactor(g.level) else max(6.0, availPerDayHours(g) * days * 0.85)
        val sw = w.sum().toDouble()
        return tp.stages.mapIndexed { i, (t, topics) ->
            RoadmapStage(title = t, topics = topics, hours = max(1.0, (total * w[i] / sw * 2).roundToInt() / 2.0))
        }
    }

    // ---------------- 구간 만들기 ----------------
    private val METHOD_BASE = listOf("개념 정리 → 예제 → 확인 퀴즈 순서로 진행", "항목마다 핵심을 세 줄로 요약")
    private val METHOD_FINAL = listOf("실제 시험처럼 시간을 재고 풀기", "틀린 문제는 오답 노트에 이유와 함께 정리")
    private val FINAL_STAGE = Regex("기출|총정리|최종|테스트 · 제출")

    /** 한 구간 분량 (분): 평일 하루 학습 가능 시간 정도 (60~180분), 결과에 따라 adj로 조정 */
    fun roundTarget(g: Goal, adj: Double = 1.0): Int = r10(min(180, max(60, g.weekdayMinutes)) * adj)

    private fun topicItems(topic: String, mins: Int, final: Boolean, sess: Int): List<RoundItem> {
        val parts = if (final) listOf(topic to 0.7, "오답 정리: $topic" to 0.3)
        else listOf("개념 학습: $topic" to 0.5, "예제 · 문제: $topic" to 0.33, "확인 퀴즈" to 0.17)
        val out = mutableListOf<RoundItem>()
        parts.forEach { (title, f) ->
            var m = r10(mins * f)
            val chunks = mutableListOf<Int>()
            while (m > sess + 5) { chunks += sess; m -= sess }
            chunks += m
            chunks.forEachIndexed { i, c ->
                out += RoundItem(
                    id = newId("ri"),
                    title = if (chunks.size > 1) "$title (${i + 1}/${chunks.size})" else title,
                    minutes = max(10, c),
                )
            }
        }
        return out
    }

    private fun criteriaOf(topic: String, final: Boolean): List<String> =
        if (final) listOf("${topic}을 제한 시간 안에 풀기", "정답률 70% 이상 · 틀린 문제는 이유까지 정리")
        else listOf("‘$topic’ 핵심 개념을 말로 설명할 수 있음", "예제 문제 80% 이상 스스로 풀이", "확인 퀴즈에서 틀린 부분 다시 확인")

    data class NextTopic(val stage: Int, val topic: String)

    /** 다음에 배울 주제: 현재 단계에서 아직 다루지 않은 주제 → 없으면 다음 단계 */
    fun nextTopic(goalId: String, roadmap: List<RoadmapStage>, stage: Int, rounds: List<StudyRound>): NextTopic? {
        val covered = rounds.filter { it.goalId == goalId }.flatMap { it.topics }.toSet()
        for (s in stage until roadmap.size) {
            roadmap[s].topics.firstOrNull { it !in covered }?.let { return NextTopic(s, it) }
        }
        return null
    }

    fun makeRound(
        g: Goal,
        roadmap: List<RoadmapStage>,
        no: Int,
        stage: Int,
        topic: String?,
        today: Long,
        rounds: List<StudyRound>,
        carry: List<RoundItem> = emptyList(),
        pre: List<RoundItem> = emptyList(),
        adj: Double = 1.0,
        sess: Int = 60,
    ): StudyRound {
        val target = roundTarget(g, adj)
        val stageTitle = roadmap.getOrNull(stage)?.title.orEmpty()
        val final = FINAL_STAGE.containsMatchIn(stageTitle)
        var used = (pre + carry).sumOf { it.minutes }
        var items = pre + carry
        val topics = mutableListOf<String>()
        val seen = rounds.filter { it.goalId == g.id }.flatMap { it.topics }.toMutableSet()
        var cur: NextTopic? = topic?.let { NextTopic(stage, it) }
        while (cur != null && used < target * 0.8) {
            val room = max(40, target - used)
            items = items + topicItems(cur.topic, room, final, sess)
            used += room
            topics += cur.topic
            seen += cur.topic
            // 한 구간에 같은 단계의 주제를 최대 2개까지
            val nx = roadmap.getOrNull(cur.stage)?.topics?.firstOrNull { it !in seen }
            cur = if (topics.size < 2 && nx != null && used < target * 0.6) NextTopic(cur.stage, nx) else null
        }
        val main = topics.firstOrNull() ?: stageTitle.ifEmpty { "복습" }
        return StudyRound(
            id = newId("rd"),
            goalId = g.id,
            no = no,
            stage = stage,
            topic = if (topics.isNotEmpty()) topics.joinToString(" · ") else "$main (이어서)",
            topics = topics,
            method = (if (final) METHOD_FINAL else METHOD_BASE) + if (sess < 60) listOf("한 번에 ${sess}분씩 끊어서 공부") else emptyList(),
            criteria = criteriaOf(main, final),
            items = items,
            status = RoundStatus.READY,
            createdOn = today,
        )
    }

    // ---------------- 첫 생성 ----------------
    /** AI 로드맵 + 1구간 계획 (재생성은 2차) */
    fun genDraft(g: Goal, today: Long): RoadmapDraft {
        val rm = genRoadmap(g, today)
        val nt = nextTopic(g.id, rm, 0, emptyList())
        val r = makeRound(g, rm, 1, 0, nt?.topic ?: rm.firstOrNull()?.title, today, emptyList())
        return RoadmapDraft(goalId = g.id, roadmap = rm, round = r, createdOn = today)
    }

    // ---------------- 다음 구간 추천 ----------------
    private fun reasonKey(r: MissReason?) = when (r) {
        MissReason.NO_TIME -> "time"
        MissReason.TOO_HARD -> "hard"
        MissReason.NO_FOCUS -> "focus"
        MissReason.SUDDEN_EVENT -> "event"
        MissReason.TOO_MUCH -> "much"
        else -> "etc"
    }

    private fun tagKey(t: RetroTag) = when (t) {
        RetroTag.HARD_PROBLEMS -> "solve"
        RetroTag.DISTRACTED -> "focus"
        RetroTag.TIRED -> "tired"
        RetroTag.NEED_REVIEW -> "review"
        RetroTag.TOO_MUCH -> "much"
        else -> null
    }

    private val SHORT_WHY = mapOf(
        "time" to "구간 계획이 쓸 수 있는 시간보다 많았어요",
        "hard" to "한 번에 다루는 범위가 커서 이해에 시간이 더 필요했어요",
        "focus" to "한 번에 길게 앉아 있는 방식이 부담이 됐어요",
        "event" to "갑작스러운 일정으로 쓸 수 있는 시간이 줄었어요",
        "much" to "한 구간 학습량이 많았어요",
    )

    /** 메모에서 어려웠던 주제 뽑기 (예: "정규형 예제가 헷갈렸어요" → "정규형 예제") */
    fun memoTopic(txt: String): String? {
        val i = Regex("헷갈|어려|이해가 안|이해 안|모르겠|막혔|막힘").find(txt)?.range?.first ?: return null
        if (i <= 0) return null
        val fill = Regex("^(너무|좀|조금|오늘|진짜|정말|많이|잘|다|그리고|근데|보다가|하다가)$")
        val words = txt.substring(0, i).split(Regex("[\\s,./]+")).filter { it.isNotBlank() }
            .map { it.replace(Regex("(부분이|부분은|부분|개념이|개념은|개념|문제가|문제는|에서|이|가|은|는|을|를)$"), "") }
            .filter { it.isNotBlank() && !fill.matches(it) && !Regex("(다가|하고|해서)$").containsMatchIn(it) }
        return words.takeLast(3).joinToString(" ").trim().ifEmpty { null }
    }

    private fun memoHints(txt: String): List<String> = buildList {
        if (Regex("졸|피곤|잠이").containsMatchIn(txt)) add("tired")
        if (Regex("폰|유튜브|딴짓|SNS|인스타", RegexOption.IGNORE_CASE).containsMatchIn(txt)) add("focus")
        if (Regex("시간이? ?(없|부족)|알바|약속|과제가").containsMatchIn(txt)) add("time")
    }

    /** 완료율 70% 미만인 구간이 최근 몇 번 이어졌는지 */
    fun missStreak(goalId: String, rounds: List<StudyRound>): Int {
        val done = rounds.filter { it.goalId == goalId && it.status == RoundStatus.DONE }.sortedBy { it.no }
        var n = 0
        for (r in done.asReversed()) {
            val rate = if (r.items.isEmpty()) 1.0 else r.doneCount / r.items.size.toDouble()
            if (rate < 0.7) n++ else break
        }
        return n
    }

    /**
     * 마지막으로 제출한 구간 결과를 보고 다음 구간을 제안해요.
     * rounds에는 이 목표의 구간이 모두 들어 있어야 해요 (방금 제출한 구간 = DONE).
     */
    fun proposeNext(g: Goal, rounds: List<StudyRound>, today: Long, extra: String = ""): NextProposal? {
        val mine = rounds.filter { it.goalId == g.id }.sortedBy { it.no }
        val last = mine.lastOrNull { it.status == RoundStatus.DONE } ?: return null
        val res = last.result
        val memo = listOf(res?.memo.orEmpty(), extra).filter { it.isNotBlank() }.joinToString(" / ")
        val topic = memoTopic(extra) ?: memoTopic(res?.memo.orEmpty())
        val mods = ((res?.tags ?: emptyList()).mapNotNull { tagKey(it) } + memoHints(memo)).toSet()
        val est = last.estimatedMinutes
        val act = last.actualMinutes
        val doneN = last.doneCount
        val miss = last.items.filter { !it.done }
        val ks = miss.map { reasonKey(it.reason) }.distinct()
        val sess = if ("tired" in mods) 30 else 25
        val split = "focus" in mods || "tired" in mods || "focus" in ks

        // 분량 조정: 결과 · 체감 난이도 · 실제/예상 시간
        var adj = 1.0
        val rate = if (last.items.isEmpty()) 1.0 else doneN / last.items.size.toDouble()
        val tr = if (est > 0) act / est.toDouble() else 1.0
        if ("much" in ks || "time" in ks || "much" in mods || tr > 1.25) adj *= 0.8
        if (res?.difficulty == Difficulty.EASY && rate >= 1.0 && tr <= 1.0) adj *= 1.15
        if (res?.difficulty == Difficulty.HARD) adj *= 0.9
        if (missStreak(g.id, rounds) >= 2) adj *= 0.85

        // 못 끝낸 만큼 이어서 하기
        val carry = mutableListOf<RoundItem>()
        miss.forEach { i ->
            val remM = max(10, i.minutes - min(i.actualMinutes, i.minutes))
            val k = reasonKey(i.reason)
            val base = i.title.replace(Regex(" \\(이어서\\)| · 세션 \\d+.*$"), "")
            when {
                k == "hard" && remM >= 30 -> {
                    val rv = r10(remM * 0.3)
                    carry += RoundItem(newId("ri"), "기초 개념 복습: ${topic ?: base.substringAfter(": ")}", rv, kind = ItemKind.CARRY)
                    carry += RoundItem(newId("ri"), "$base (이어서 · 나눠서)", max(10, remM - rv), kind = ItemKind.CARRY)
                }
                (split || k == "focus") && remM > sess -> {
                    val n = ceil(remM / sess.toDouble()).toInt()
                    for (s in 0 until n) carry += RoundItem(
                        newId("ri"), "$base · 세션 ${s + 1} (${sess}분 + 휴식 5분)",
                        if (s < n - 1) sess else remM - sess * (n - 1), kind = ItemKind.CARRY,
                    )
                }
                else -> carry += RoundItem(newId("ri"), "$base (이어서)", remM, kind = ItemKind.CARRY)
            }
        }
        // 같은 '기초 개념 복습'은 하나로 합쳐요
        val merged = mutableListOf<RoundItem>()
        carry.forEach { c ->
            val i = merged.indexOfFirst { it.title == c.title && c.title.startsWith("기초 개념 복습") }
            if (i >= 0) merged[i] = merged[i].copy(minutes = merged[i].minutes + c.minutes) else merged += c
        }
        carry.clear(); carry += merged
        val first = topic ?: last.topics.firstOrNull() ?: last.displayTopic
        val pre = mutableListOf<RoundItem>()
        if ("review" in mods || res?.difficulty == Difficulty.HARD) pre += RoundItem(newId("ri"), "복습: $first", 20, kind = ItemKind.REVIEW)
        if ("solve" in mods) pre += RoundItem(newId("ri"), "예제 해설 먼저 보기: $first", 15, kind = ItemKind.REVIEW)

        // 단계 진행: 이번 단계 주제를 다 다뤘으면 다음 단계로
        val nxt = nextTopic(g.id, g.roadmap, g.stage, rounds)
        val stage = if (nxt != null && nxt.stage > g.stage) nxt.stage else g.stage
        if (nxt == null && carry.isEmpty() && pre.isEmpty()) {
            return NextProposal(goalId = g.id, fromRoundId = last.id, final = true, lastNo = last.no, createdOn = today)
        }
        var r = makeRound(g, g.roadmap, last.no + 1, stage, nxt?.topic, today, rounds, carry, pre, adj)
        // 못 끝낸 항목만으로 구간이 찼으면 새 주제 없이 같은 단계에서 이어서 해요
        if (r.topics.isEmpty()) {
            val lt = last.topics.firstOrNull() ?: last.displayTopic
            r = r.copy(stage = g.stage, topic = "$lt (이어서)", criteria = criteriaOf(lt, false))
        }

        val why = mutableListOf<String>()
        if (topic != null) why += "‘$topic’ 부분이 특히 어려웠어요"
        why += "${last.no}구간 항목 ${last.items.size}개 중 ${doneN}개를 끝냈어요"
        ks.forEach { k -> SHORT_WHY[k]?.let { why += it } }
        if (res?.difficulty == Difficulty.HARD) why += "체감 난이도가 높았어요"
        if (res?.difficulty == Difficulty.EASY && rate >= 1.0) why += "계획한 분량을 무리 없이 끝냈어요"

        val fixes = mutableListOf<String>()
        if (carry.isNotEmpty()) fixes += "못 끝낸 ${miss.size}개 이어서"
        if (pre.isNotEmpty()) fixes += pre.joinToString(" · ") { it.title.substringBefore(":") } + " 먼저"
        if (adj < 0.95) fixes += "분량 ${(adj * 100).roundToInt()}%로 줄임"
        if (adj > 1.05) fixes += "분량 ${(adj * 100).roundToInt()}%로 늘림"
        if (split) fixes += "${sess}분씩 나눠서"
        if (r.stage > g.stage) fixes += "${r.stage + 1}단계 ‘${g.roadmap[r.stage].title}’ 시작"
        if (fixes.isEmpty()) fixes += "지금 페이스 유지"

        return NextProposal(
            goalId = g.id,
            fromRoundId = last.id,
            round = r,
            why = why.distinct().take(4),
            fixes = fixes.take(4),
            lastNo = last.no,
            lastDifficulty = res?.difficulty,
            lastReasons = miss.mapNotNull { it.reason }.distinct(),
            lastMemo = res?.memo.orEmpty(),
            lastEstimatedMinutes = est,
            lastActualMinutes = act,
            lastDoneCount = doneN,
            lastTotal = last.items.size,
            createdOn = today,
        )
    }
}
