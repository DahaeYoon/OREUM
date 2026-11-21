package com.example.oreum.data.repository

import com.example.oreum.data.model.Goal
import com.example.oreum.data.model.PlanStatus
import com.example.oreum.data.model.RoundStatus
import com.example.oreum.data.model.StudyRound
import com.example.oreum.domain.usecase.RoundEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * AI 요청을 앱 전체에서 한 곳에서 관리해요.
 * - 화면을 닫아도 요청은 이어지고, 끝나면 결과를 저장해요 (승인 전 초안 · 추천으로만 저장, 자동 적용 안 함).
 * - 같은 목표에 같은 요청을 두 번 보내지 않아요 (사용량 아끼기).
 * - 실패하면 시뮬레이션으로 대신하지 않고 '실패' 상태로 남겨 두고, 화면에서 '다시 시도'를 눌러야 다시 보내요.
 */
object AiRequests {

    sealed interface State {
        data object Running : State
        /** at = 실패한 시각 (분당 한도면 이때부터 잠시 기다려요) */
        data class Failed(val reason: AiFailure, val at: Long = System.currentTimeMillis()) : State
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val _states = MutableStateFlow<Map<String, State>>(emptyMap())

    /** 요청 key → 상태 (진행 중 · 실패). 성공하면 지워져요 */
    val states: StateFlow<Map<String, State>> = _states.asStateFlow()

    fun roadmapKey(goalId: String) = "roadmap:$goalId"
    fun nextKey(goalId: String) = "next:$goalId"

    private val repo get() = StudyRepositoryProvider.instance
    private val planner get() = AiPlannerProvider.instance
    private fun today() = LocalDate.now().toEpochDay()

    /** 로드맵 + 1구간이 필요한 목표인지 (아직 승인 전이고 초안도 없음) */
    fun needsRoadmap(goal: Goal, drafts: Map<String, *>): Boolean =
        goal.completedAt == null && goal.planStatus != PlanStatus.APPROVED && !drafts.containsKey(goal.id)

    /** 다음 구간 추천이 필요한 목표인지 (마지막 구간을 제출했고, 추천 · 진행 중 구간이 없음) */
    fun needsProposal(goal: Goal, rounds: List<StudyRound>, proposals: Map<String, *>): Boolean {
        if (goal.completedAt != null || goal.planStatus != PlanStatus.APPROVED || proposals.containsKey(goal.id)) return false
        val mine = rounds.filter { it.goalId == goal.id }
        return mine.any { it.status == RoundStatus.DONE } && mine.none { it.status != RoundStatus.DONE }
    }

    /** AI 로드맵 + 1구간 만들기 (이미 진행 중이거나 필요 없으면 아무것도 안 해요) */
    fun requestRoadmap(goalId: String) {
        val key = roadmapKey(goalId)
        if (_states.value[key] == State.Running) return
        val goal = repo.goal(goalId) ?: return
        if (!needsRoadmap(goal, repo.roadmapDrafts.value)) return clear(key)
        set(key, State.Running)
        scope.launch {
            when (val r = planner.roadmap(goal, today())) {
                is AiResult.Ok -> {
                    // 기다리는 동안 목표가 지워졌거나 계정이 바뀌었으면 저장하지 않아요
                    val now = repo.goal(goalId)
                    if (now != null && needsRoadmap(now, repo.roadmapDrafts.value)) repo.saveRoadmapDraft(r.value)
                    clear(key)
                }
                is AiResult.Fail -> set(key, State.Failed(r.reason))
            }
        }
    }

    /**
     * 다음 구간 추천 만들기.
     * 로드맵을 모두 마쳐서 더 할 구간이 없으면 AI를 부르지 않고 '정상 도착' 안내만 저장해요.
     */
    fun requestNext(goalId: String) {
        val key = nextKey(goalId)
        if (_states.value[key] == State.Running) return
        val goal = repo.goal(goalId) ?: return
        val rounds = repo.rounds.value.filter { it.goalId == goalId }
        if (!needsProposal(goal, rounds, repo.proposals.value)) return clear(key)
        val today = today()
        val baseline = RoundEngine.proposeNext(goal, rounds, today) ?: return clear(key)
        if (baseline.final) {
            repo.saveProposal(baseline)
            return clear(key)
        }
        val sessions = repo.sessions.value.filter { it.goalId == goalId }
        set(key, State.Running)
        scope.launch {
            when (val r = planner.nextRound(goal, rounds, sessions, today, baseline)) {
                is AiResult.Ok -> {
                    val now = repo.goal(goalId)
                    if (now != null && needsProposal(now, repo.rounds.value, repo.proposals.value)) repo.saveProposal(r.value)
                    clear(key)
                }
                is AiResult.Fail -> set(key, State.Failed(r.reason))
            }
        }
    }

    private fun set(key: String, state: State) = _states.update { it + (key to state) }
    private fun clear(key: String) = _states.update { it - key }
}
