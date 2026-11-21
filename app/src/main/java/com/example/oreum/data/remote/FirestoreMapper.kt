package com.example.oreum.data.remote

import com.example.oreum.data.model.Difficulty
import com.example.oreum.data.model.Goal
import com.example.oreum.data.model.GoalLevel
import com.example.oreum.data.model.GoalPeriod
import com.example.oreum.data.model.ItemKind
import com.example.oreum.data.model.MissReason
import com.example.oreum.data.model.NextProposal
import com.example.oreum.data.model.PlanStatus
import com.example.oreum.data.model.RetroTag
import com.example.oreum.data.model.RoadmapDraft
import com.example.oreum.data.model.RoadmapStage
import com.example.oreum.data.model.RoundItem
import com.example.oreum.data.model.RoundResult
import com.example.oreum.data.model.RoundStatus
import com.example.oreum.data.model.StudyRound
import com.example.oreum.data.model.StudySession

/**
 * 데이터 클래스 ↔ Firestore 문서(Map) 변환 (순수 Kotlin, Firebase 없이도 테스트 가능).
 * - 모델은 val(읽기 전용)이라 Firestore 자동 변환 대신 필드를 직접 적어요. 필드를 추가하면 여기도 함께 고쳐요.
 * - 계산용 속성(예: estimatedMinutes)은 저장하지 않아요.
 * - enum은 이름(문자열)으로, 날짜는 숫자(epoch day · epoch millis)로 저장해요.
 */
object FirestoreMapper {

    // ---------------- 저장 (객체 → Map) ----------------
    fun goal(g: Goal): Map<String, Any?> = mapOf(
        "id" to g.id, "ownerId" to g.ownerId, "title" to g.title, "description" to g.description, "subject" to g.subject,
        "period" to g.period.name, "startDate" to g.startDate, "dueAt" to g.dueAt, "level" to g.level.name,
        "weekdayMinutes" to g.weekdayMinutes, "weekendMinutes" to g.weekendMinutes, "priority" to g.priority,
        "planStatus" to g.planStatus.name, "roadmap" to g.roadmap.map(::stage), "stage" to g.stage,
        "completedAt" to g.completedAt, "createdAt" to g.createdAt, "updatedAt" to g.updatedAt,
    )

    fun stage(s: RoadmapStage): Map<String, Any?> = mapOf("title" to s.title, "topics" to s.topics, "hours" to s.hours)

    fun item(i: RoundItem): Map<String, Any?> = mapOf(
        "id" to i.id, "title" to i.title, "minutes" to i.minutes, "done" to i.done,
        "actualMinutes" to i.actualMinutes, "reason" to i.reason?.name, "kind" to i.kind.name,
    )

    fun result(r: RoundResult): Map<String, Any?> = mapOf(
        "difficulty" to r.difficulty?.name, "memo" to r.memo, "tags" to r.tags.map { it.name },
    )

    fun round(r: StudyRound): Map<String, Any?> = mapOf(
        "id" to r.id, "ownerId" to r.ownerId, "goalId" to r.goalId, "no" to r.no, "stage" to r.stage,
        "topic" to r.topic, "topics" to r.topics, "method" to r.method, "criteria" to r.criteria,
        "items" to r.items.map(::item), "status" to r.status.name, "createdOn" to r.createdOn,
        "approvedOn" to r.approvedOn, "startedOn" to r.startedOn, "doneOn" to r.doneOn, "dates" to r.dates,
        "result" to r.result?.let(::result), "byAi" to r.byAi, "aiFixes" to r.aiFixes,
        "aiPlannedCount" to r.aiPlannedCount, "aiPlannedMinutes" to r.aiPlannedMinutes,
    )

    fun session(s: StudySession): Map<String, Any?> = mapOf(
        "id" to s.id, "ownerId" to s.ownerId, "goalId" to s.goalId, "roundId" to s.roundId,
        "itemId" to s.itemId, "date" to s.date, "minutes" to s.minutes,
    )

    fun draft(d: RoadmapDraft): Map<String, Any?> = mapOf(
        "goalId" to d.goalId, "roadmap" to d.roadmap.map(::stage), "round" to round(d.round), "createdOn" to d.createdOn,
        "byAi" to d.byAi,
    )

    fun proposal(p: NextProposal): Map<String, Any?> = mapOf(
        "goalId" to p.goalId, "fromRoundId" to p.fromRoundId, "round" to p.round?.let(::round), "final" to p.final,
        "why" to p.why, "fixes" to p.fixes, "lastNo" to p.lastNo, "lastDifficulty" to p.lastDifficulty?.name,
        "lastReasons" to p.lastReasons.map { it.name }, "lastMemo" to p.lastMemo,
        "lastEstimatedMinutes" to p.lastEstimatedMinutes, "lastActualMinutes" to p.lastActualMinutes,
        "lastDoneCount" to p.lastDoneCount, "lastTotal" to p.lastTotal, "createdOn" to p.createdOn,
        "byAi" to p.byAi,
    )

    // ---------------- 불러오기 (Map → 객체) ----------------
    fun toGoal(m: Map<String, Any?>) = Goal(
        id = m.str("id"), ownerId = m.str("ownerId"), title = m.str("title"), description = m.str("description"),
        subject = m.str("subject"), period = m.enum("period", GoalPeriod.MONTHLY), startDate = m.long("startDate"),
        dueAt = m.long("dueAt"), level = m.enum("level", GoalLevel.BEGINNER), weekdayMinutes = m.int("weekdayMinutes", 120),
        weekendMinutes = m.int("weekendMinutes", 180), priority = m.int("priority", 2),
        planStatus = m.enum("planStatus", PlanStatus.NONE), roadmap = m.maps("roadmap").map(::toStage),
        stage = m.int("stage"), completedAt = m.longOrNull("completedAt"), createdAt = m.long("createdAt"),
        updatedAt = m.long("updatedAt"),
    )

    fun toStage(m: Map<String, Any?>) = RoadmapStage(title = m.str("title"), topics = m.strs("topics"), hours = m.dbl("hours"))

    fun toItem(m: Map<String, Any?>) = RoundItem(
        id = m.str("id"), title = m.str("title"), minutes = m.int("minutes"), done = m.bool("done"),
        actualMinutes = m.int("actualMinutes"), reason = m.enumOrNull<MissReason>("reason"), kind = m.enum("kind", ItemKind.NEW),
    )

    fun toResult(m: Map<String, Any?>) = RoundResult(
        difficulty = m.enumOrNull<Difficulty>("difficulty"), memo = m.str("memo"),
        tags = m.strs("tags").mapNotNull { n -> RetroTag.entries.firstOrNull { it.name == n } },
    )

    fun toRound(m: Map<String, Any?>) = StudyRound(
        id = m.str("id"), ownerId = m.str("ownerId"), goalId = m.str("goalId"), no = m.int("no", 1), stage = m.int("stage"),
        topic = m.str("topic"), topics = m.strs("topics"), method = m.strs("method"), criteria = m.strs("criteria"),
        items = m.maps("items").map(::toItem), status = m.enum("status", RoundStatus.READY), createdOn = m.long("createdOn"),
        approvedOn = m.longOrNull("approvedOn"), startedOn = m.longOrNull("startedOn"), doneOn = m.longOrNull("doneOn"),
        dates = m.longs("dates"), result = m.map("result")?.let(::toResult), byAi = m.bool("byAi"),
        aiFixes = m.strs("aiFixes"), aiPlannedCount = m.int("aiPlannedCount"), aiPlannedMinutes = m.int("aiPlannedMinutes"),
    )

    fun toSession(m: Map<String, Any?>) = StudySession(
        id = m.str("id"), ownerId = m.str("ownerId"), goalId = m.str("goalId"), roundId = m.str("roundId"),
        itemId = m.str("itemId"), date = m.long("date"), minutes = m.int("minutes"),
    )

    fun toDraft(m: Map<String, Any?>) = RoadmapDraft(
        goalId = m.str("goalId"), roadmap = m.maps("roadmap").map(::toStage),
        round = m.map("round")?.let(::toRound) ?: StudyRound(), createdOn = m.long("createdOn"),
        byAi = m.bool("byAi"),
    )

    fun toProposal(m: Map<String, Any?>) = NextProposal(
        goalId = m.str("goalId"), fromRoundId = m.str("fromRoundId"), round = m.map("round")?.let(::toRound),
        final = m.bool("final"), why = m.strs("why"), fixes = m.strs("fixes"), lastNo = m.int("lastNo"),
        lastDifficulty = m.enumOrNull<Difficulty>("lastDifficulty"),
        lastReasons = m.strs("lastReasons").mapNotNull { n -> MissReason.entries.firstOrNull { it.name == n } },
        lastMemo = m.str("lastMemo"), lastEstimatedMinutes = m.int("lastEstimatedMinutes"),
        lastActualMinutes = m.int("lastActualMinutes"), lastDoneCount = m.int("lastDoneCount"),
        lastTotal = m.int("lastTotal"), createdOn = m.long("createdOn"), byAi = m.bool("byAi"),
    )

    // ---------------- 읽기 도우미 (Firestore 숫자는 Long · Double로 와요) ----------------
    private fun Map<String, Any?>.str(k: String) = this[k] as? String ?: ""
    private fun Map<String, Any?>.long(k: String) = (this[k] as? Number)?.toLong() ?: 0L
    private fun Map<String, Any?>.longOrNull(k: String) = (this[k] as? Number)?.toLong()
    private fun Map<String, Any?>.int(k: String, def: Int = 0) = (this[k] as? Number)?.toInt() ?: def
    private fun Map<String, Any?>.dbl(k: String) = (this[k] as? Number)?.toDouble() ?: 0.0
    private fun Map<String, Any?>.bool(k: String) = this[k] as? Boolean ?: false
    private fun Map<String, Any?>.strs(k: String) = (this[k] as? List<*>)?.filterIsInstance<String>() ?: emptyList()
    private fun Map<String, Any?>.longs(k: String) = (this[k] as? List<*>)?.mapNotNull { (it as? Number)?.toLong() } ?: emptyList()

    @Suppress("UNCHECKED_CAST")
    private fun Map<String, Any?>.map(k: String) = this[k] as? Map<String, Any?>

    @Suppress("UNCHECKED_CAST")
    private fun Map<String, Any?>.maps(k: String) = (this[k] as? List<*>)?.mapNotNull { it as? Map<String, Any?> } ?: emptyList()

    private inline fun <reified E : Enum<E>> Map<String, Any?>.enum(k: String, def: E): E = enumOrNull<E>(k) ?: def
    private inline fun <reified E : Enum<E>> Map<String, Any?>.enumOrNull(k: String): E? =
        (this[k] as? String)?.let { n -> enumValues<E>().firstOrNull { it.name == n } }
}
