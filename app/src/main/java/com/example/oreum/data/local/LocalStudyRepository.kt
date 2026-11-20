package com.example.oreum.data.local

import com.example.oreum.data.model.Goal
import com.example.oreum.data.model.PlanStatus
import com.example.oreum.data.model.NextProposal
import com.example.oreum.data.model.RoadmapDraft
import com.example.oreum.data.model.RoundResult
import com.example.oreum.data.model.RoundStatus
import com.example.oreum.data.model.StudyRound
import com.example.oreum.data.model.StudySession
import com.example.oreum.data.repository.ApproveResult
import com.example.oreum.data.repository.RoundLogEntry
import com.example.oreum.data.repository.StudyRepository
import com.example.oreum.domain.usecase.RoundEngine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.time.LocalDate
import java.util.UUID

/**
 * Firestore 연결 전 임시 저장소 (앱 메모리에만 있음).
 * 앱을 처음 열면 샘플 목표 · 구간 · 기록이 들어 있고, 앱을 완전히 종료하면 바뀐 내용은 사라져요.
 */
class LocalStudyRepository(
    private val ownerId: String = SampleData.OWNER_ID,
    private val today: () -> LocalDate = { LocalDate.now() },
) : StudyRepository {

    private val _goals = MutableStateFlow<List<Goal>>(emptyList())

    override val goals: StateFlow<List<Goal>> = _goals.asStateFlow()

    private val _rounds = MutableStateFlow<List<StudyRound>>(emptyList())
    private val _sessions = MutableStateFlow<List<StudySession>>(emptyList())
    private val _roadmapDrafts = MutableStateFlow<Map<String, RoadmapDraft>>(emptyMap())
    private val _proposals = MutableStateFlow<Map<String, NextProposal>>(emptyMap())

    override val rounds: StateFlow<List<StudyRound>> = _rounds.asStateFlow()
    override val sessions: StateFlow<List<StudySession>> = _sessions.asStateFlow()
    override val roadmapDrafts: StateFlow<Map<String, RoadmapDraft>> = _roadmapDrafts.asStateFlow()
    override val proposals: StateFlow<Map<String, NextProposal>> = _proposals.asStateFlow()

    init {
        resetSampleData()
    }

    override fun goal(id: String): Goal? = _goals.value.firstOrNull { it.id == id }

    override fun addGoal(goal: Goal): Goal {
        val now = System.currentTimeMillis()
        val saved = goal.copy(
            id = goal.id.ifEmpty { "g-" + UUID.randomUUID().toString() },
            ownerId = ownerId,
            createdAt = now,
            updatedAt = now,
        )
        _goals.update { it + saved }
        return saved
    }

    override fun updateGoal(goal: Goal) {
        val saved = goal.copy(updatedAt = System.currentTimeMillis())
        _goals.update { list -> list.map { if (it.id == goal.id) saved else it } }
    }

    override fun deleteGoal(id: String) {
        _goals.update { list -> list.filterNot { it.id == id } }
        _rounds.update { list -> list.filterNot { it.goalId == id } }
        _sessions.update { list -> list.filterNot { it.goalId == id } }
        _roadmapDrafts.update { it - id }
        _proposals.update { it - id }
    }

    override fun setGoalCompleted(id: String, completed: Boolean) {
        val now = System.currentTimeMillis()
        _goals.update { list ->
            list.map { if (it.id == id) it.copy(completedAt = if (completed) now else null, updatedAt = now) else it }
        }
    }

    override fun resetSampleData() {
        val rs = RoundSampleData.build(today(), ownerId, SampleData.goals(today(), ownerId))
        _goals.value = rs.goals
        _rounds.value = rs.rounds
        _sessions.value = rs.sessions
        _roadmapDrafts.value = rs.drafts
        _proposals.value = rs.proposals
    }

    // ======================= 구간 기반 학습 (1차) =======================

    override fun round(id: String): StudyRound? = _rounds.value.firstOrNull { it.id == id }

    private fun putRound(r: StudyRound) {
        _rounds.update { list -> if (list.any { it.id == r.id }) list.map { if (it.id == r.id) r else it } else list + r }
    }

    override fun generateRoadmap(goalId: String, today: Long): RoadmapDraft? {
        val g = goal(goalId) ?: return null
        val draft = RoundEngine.genDraft(g, today)
        _roadmapDrafts.update { it + (goalId to draft) }
        if (g.planStatus != PlanStatus.APPROVED) updateGoal(g.copy(planStatus = PlanStatus.DRAFT))
        return draft
    }

    override fun updateRoadmapDraft(draft: RoadmapDraft) {
        _roadmapDrafts.update { it + (draft.goalId to draft) }
    }

    override fun approveRoadmap(goalId: String, today: Long): StudyRound? {
        val g = goal(goalId) ?: return null
        val draft = _roadmapDrafts.value[goalId] ?: return null
        // 아직 끝내지 않은 예전 구간은 새 로드맵으로 바꿔요 (마친 구간 기록은 그대로)
        _rounds.update { list -> list.filterNot { it.goalId == goalId && it.status != RoundStatus.DONE } }
        val prev = _rounds.value.count { it.goalId == goalId }
        val r = draft.round.copy(
            ownerId = ownerId, goalId = goalId, no = prev + 1, status = RoundStatus.ACTIVE,
            approvedOn = today, startedOn = today,
        )
        putRound(r)
        updateGoal(g.copy(roadmap = draft.roadmap, stage = 0, planStatus = PlanStatus.APPROVED))
        _roadmapDrafts.update { it - goalId }
        _proposals.update { it - goalId }
        return r
    }

    override fun updateRound(round: StudyRound) = putRound(round)

    override fun startRound(roundId: String, today: Long) {
        val r = round(roundId) ?: return
        if (r.status == RoundStatus.READY) putRound(r.copy(status = RoundStatus.ACTIVE, startedOn = today))
    }

    /** 오늘 이 항목에 쓴 시간을 minutes로 맞춰요 (다른 날 기록은 그대로) */
    private fun setTodayMinutes(r: StudyRound, itemId: String, minutes: Int, today: Long): StudyRound {
        val others = _sessions.value.filterNot { it.itemId == itemId && it.date == today }
        val added = if (minutes > 0) listOf(StudySession(RoundEngine.newId("ss"), ownerId, r.goalId, r.id, itemId, today, minutes)) else emptyList()
        _sessions.value = others + added
        val actual = _sessions.value.filter { it.itemId == itemId }.sumOf { it.minutes }
        val dates = _sessions.value.filter { it.roundId == r.id }.map { it.date }.distinct().sorted()
        return r.copy(items = r.items.map { if (it.id == itemId) it.copy(actualMinutes = actual) else it }, dates = dates)
    }

    private fun todayMinutes(itemId: String, today: Long): Int =
        _sessions.value.filter { it.itemId == itemId && it.date == today }.sumOf { it.minutes }

    override fun toggleItem(roundId: String, itemId: String, today: Long): StudyRound? {
        var r = round(roundId) ?: return null
        if (r.status == RoundStatus.DONE) return r
        if (r.status == RoundStatus.READY) r = r.copy(status = RoundStatus.ACTIVE, startedOn = today)
        val item = r.items.firstOrNull { it.id == itemId } ?: return r
        r = if (!item.done) {
            val add = maxOf(0, item.minutes - item.actualMinutes)
            val marked = r.copy(items = r.items.map { if (it.id == itemId) it.copy(done = true, reason = null) else it })
            if (add > 0) setTodayMinutes(marked, itemId, todayMinutes(itemId, today) + add, today) else marked
        } else {
            val marked = r.copy(items = r.items.map { if (it.id == itemId) it.copy(done = false) else it })
            val t = todayMinutes(itemId, today)
            if (t > 0) setTodayMinutes(marked, itemId, maxOf(0, t - item.minutes), today) else marked
        }
        putRound(r)
        return r
    }

    private fun applyLog(roundId: String, entries: List<RoundLogEntry>, today: Long): StudyRound? {
        var r = round(roundId) ?: return null
        if (r.status == RoundStatus.READY) r = r.copy(status = RoundStatus.ACTIVE, startedOn = today)
        entries.forEach { e ->
            r = r.copy(items = r.items.map { if (it.id == e.itemId) it.copy(done = e.done, reason = if (e.done) null else e.reason) else it })
            r = setTodayMinutes(r, e.itemId, e.todayMinutes, today)
        }
        putRound(r)
        return r
    }

    override fun saveRoundLog(roundId: String, entries: List<RoundLogEntry>, draft: RoundResult?, today: Long) {
        val r = applyLog(roundId, entries, today) ?: return
        if (draft != null) putRound(r.copy(result = draft))
    }

    override fun submitRound(roundId: String, entries: List<RoundLogEntry>, result: RoundResult, today: Long): NextProposal? {
        val r = applyLog(roundId, entries, today) ?: return null
        putRound(r.copy(status = RoundStatus.DONE, doneOn = today, result = result))
        val g = goal(r.goalId) ?: return null
        val p = RoundEngine.proposeNext(g, _rounds.value, today) ?: return null
        _proposals.update { it + (g.id to p) }
        return p
    }

    override fun approveProposal(goalId: String, today: Long): ApproveResult? {
        val g = goal(goalId) ?: return null
        val p = _proposals.value[goalId] ?: return null
        _proposals.update { it - goalId }
        if (p.final || p.round == null) {
            // 마지막 구간까지 마침 → 정상 도착
            updateGoal(g.copy(stage = g.roadmap.size, completedAt = System.currentTimeMillis()))
            return ApproveResult.ReachedTop
        }
        val r = p.round.copy(ownerId = ownerId, status = RoundStatus.READY, approvedOn = today)
        putRound(r)
        if (r.stage > g.stage) updateGoal(g.copy(stage = r.stage))
        return ApproveResult.NextReady(r)
    }
}
