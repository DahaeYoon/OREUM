package com.example.oreum.data.repository

import com.example.oreum.data.model.Goal
import com.example.oreum.data.model.NextProposal
import com.example.oreum.data.model.RoadmapDraft
import com.example.oreum.data.model.StudyRound
import com.example.oreum.data.model.StudySession
import com.example.oreum.data.remote.GeminiPlanner

/**
 * 실제 AI로 계획 만들기 창구. 화면(ViewModel)은 이 인터페이스만 써요 (지금 구현: GeminiPlanner).
 * 실패하면 시뮬레이션으로 대신 만들지 않고 실패 이유를 돌려줘요 → 화면에서 '다시 시도'.
 */
interface AiPlanner {
    /** AI 로드맵 + 1구간 계획 */
    suspend fun roadmap(goal: Goal, today: Long): AiResult<RoadmapDraft>

    /** 저장된 기록을 보고 다음 구간 추천 (baseline = 앱이 계산한 기준) */
    suspend fun nextRound(
        goal: Goal,
        rounds: List<StudyRound>,
        sessions: List<StudySession>,
        today: Long,
        baseline: NextProposal,
    ): AiResult<NextProposal>
}

/** AI 결과: 성공 또는 실패 이유 */
sealed interface AiResult<out T> {
    data class Ok<T>(val value: T) : AiResult<T>
    data class Fail(val reason: AiFailure) : AiResult<Nothing>
}

/** AI 실패 이유 (화면 문구는 strings.xml) */
enum class AiFailure {
    /** 인터넷 연결 · 시간 초과 */
    NETWORK,
    /** 짧은 시간에 요청이 많아 잠시 막힘 (분당 사용량 한도) → 1분쯤 기다리면 풀려요 */
    BUSY,
    /** 오늘 쓸 수 있는 사용량을 다 씀 (하루 한도) → 매일 오후 5시(한국 시간)에 다시 채워져요 */
    DAILY_LIMIT,
    /** 앱 인증(App Check) · AI 설정 문제 */
    SETUP,
    /** AI 답이 형식에 맞지 않음 */
    BAD_RESPONSE,
    /** 그 밖의 오류 */
    UNKNOWN,
}

object AiPlannerProvider {
    val instance: AiPlanner by lazy { GeminiPlanner() }
}
