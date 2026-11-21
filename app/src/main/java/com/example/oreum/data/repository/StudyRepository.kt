package com.example.oreum.data.repository

import com.example.oreum.data.model.Goal
import com.example.oreum.data.model.MissReason
import com.example.oreum.data.model.NextProposal
import com.example.oreum.data.model.RoadmapDraft
import com.example.oreum.data.model.RoundResult
import com.example.oreum.data.model.StudyRound
import com.example.oreum.data.model.StudySession
import kotlinx.coroutines.flow.StateFlow

/**
 * 학습 데이터 창구 (목표 · 구간 · 학습 기록).
 * 화면(ViewModel)은 이 인터페이스만 사용하고, 실제 구현(지금은 LocalStudyRepository,
 * 이후 FirestoreStudyRepository)은 StudyRepositoryProvider에서 바꿔 끼워요.
 */
interface StudyRepository {
    val goals: StateFlow<List<Goal>>

    fun goal(id: String): Goal?

    /** 새 목표 저장. id가 비어 있으면 새로 만들어 돌려줘요. */
    fun addGoal(goal: Goal): Goal

    fun updateGoal(goal: Goal)

    /** 목표와 그 목표의 구간 · 학습 기록 · 초안 · 추천을 함께 지워요. */
    fun deleteGoal(id: String)

    /** completed = true면 완료한 산(오른 산)으로, false면 다시 진행 중으로 */
    fun setGoalCompleted(id: String, completed: Boolean)

    /** 지금 계정의 데이터를 샘플로 채우기 (마이 → 샘플 데이터 불러오기) */
    fun resetSampleData()

    /** 지금 계정의 목표 · 구간 · 학습 기록을 모두 지우기 (새 사용자처럼 빈 상태로) */
    fun clearAll()

    /** 로그인한 계정이 바뀌면 그 계정의 데이터로 바꿔요 (null = 로그아웃) */
    fun switchOwner(ownerId: String?)

    // ======================= 구간 기반 학습 (1차) =======================
    // 날짜는 모두 epoch day(Long). AI 결과는 모두 시뮬레이션이고, 승인해야 확정돼요.

    /** 모든 구간 (승인 전 초안 · 추천은 포함하지 않음) */
    val rounds: StateFlow<List<StudyRound>>

    /** 실제 학습 기록 (어느 날 어떤 항목을 몇 분) */
    val sessions: StateFlow<List<StudySession>>

    /** 승인 전 AI 로드맵 + 1구간 초안 (목표 id → 초안) */
    val roadmapDrafts: StateFlow<Map<String, RoadmapDraft>>

    /** 승인 전 AI 다음 구간 추천 (목표 id → 추천) */
    val proposals: StateFlow<Map<String, NextProposal>>

    fun round(id: String): StudyRound?

    /** AI 로드맵 + 1구간 초안 만들기 (목표는 '승인 대기'가 돼요) */
    fun generateRoadmap(goalId: String, today: Long): RoadmapDraft?

    /** 검토 화면에서 고친 1구간 항목 저장 */
    fun updateRoadmapDraft(draft: RoadmapDraft)

    /** 로드맵 승인: 목표에 로드맵을 넣고 1구간을 바로 시작해요. 시작한 구간을 돌려줘요. */
    fun approveRoadmap(goalId: String, today: Long): StudyRound?

    /** 구간 항목 추가 · 수정 · 삭제 후 저장 */
    fun updateRound(round: StudyRound)

    /** 승인한 구간 시작 (READY → ACTIVE) */
    fun startRound(roundId: String, today: Long)

    /** 항목 완료 체크 · 해제: 완료하면 남은 예상 시간을 오늘 공부한 시간으로 기록해요 */
    fun toggleItem(roundId: String, itemId: String, today: Long): StudyRound?

    /** 오늘 기록 저장: 항목별 완료 · 오늘 공부한 시간 · 못 한 이유 (구간은 그대로 이어져요). draft = 고른 난이도 · 메모 (제출 전 임시) */
    fun saveRoundLog(roundId: String, entries: List<RoundLogEntry>, draft: RoundResult?, today: Long)

    /** 구간 결과 제출: 기록을 저장하고 구간을 마친 뒤, AI 다음 구간 추천을 만들어 돌려줘요 */
    fun submitRound(roundId: String, entries: List<RoundLogEntry>, result: RoundResult, today: Long): NextProposal?

    /** 다음 구간 추천 승인: 다음 구간이 '시작 전'으로 확정되거나, 마지막이면 정상 도착 */
    fun approveProposal(goalId: String, today: Long): ApproveResult?
}

/** 구간 결과 기록에서 항목 하나 */
data class RoundLogEntry(
    val itemId: String,
    val done: Boolean,
    /** 오늘 이 항목을 공부한 시간 (분) */
    val todayMinutes: Int,
    /** 못 끝낸 경우 이유 (끝냈으면 null) */
    val reason: MissReason?,
)

/** 다음 구간 추천 승인 결과 */
sealed interface ApproveResult {
    data class NextReady(val round: StudyRound) : ApproveResult
    data object ReachedTop : ApproveResult
}
