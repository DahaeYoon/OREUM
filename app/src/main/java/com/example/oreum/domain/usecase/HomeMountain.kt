package com.example.oreum.domain.usecase

import com.example.oreum.data.model.Goal
import com.example.oreum.data.model.PlanStatus
import com.example.oreum.data.model.StudyRound

/**
 * 홈 산 계산 (순수 Kotlin): 로드맵 단계 기준 진행률 · 체크포인트.
 * goalId = null이면 '전체 목표 합쳐 보기' (아직 정상에 오르지 않은 목표들).
 */
object HomeMountain {

    /** 산 위 체크포인트 한 칸 = 로드맵 단계 하나 */
    data class Slot(
        val id: String,
        val goalId: String,
        val title: String,
        val done: Boolean,
        /** 주 차 묶음이면 묶인 할 일 id들 */
        val itemIds: List<String> = emptyList(),
        /** 남은 시간 (분) */
        val remainingMinutes: Int = 0,
        val plannedDate: Long? = null,
    ) {
        val isGroup: Boolean get() = itemIds.isNotEmpty()
    }

    data class Progress(
        val done: Int,
        val total: Int,
        /** 0 ~ 1 (로드맵 진행률) */
        val rate: Float,
        /** 남은 시간 (분) */
        val remainingMinutes: Int,
    ) {
        val percent: Int get() = (rate * 100).toInt()
        val top: Boolean get() = total > 0 && rate >= 1f
    }

    /** 산 진행률: 로드맵 단계 기준 (goalId = null이면 진행 중인 목표 합쳐 보기) */
    fun roundProgress(goals: List<Goal>, rounds: List<StudyRound>, goalId: String?): Progress {
        val p = if (goalId == null) RoadmapProgress.ofAll(goals, rounds)
        else goals.firstOrNull { it.id == goalId }?.let { RoadmapProgress.of(it, rounds) } ?: RoadmapProgress.Progress()
        return Progress(
            done = p.doneStages,
            total = p.totalStages,
            rate = p.rate,
            remainingMinutes = (p.remainingHours * 60).toInt(),
        )
    }

    /** 산 위 체크포인트 = 로드맵 단계 (전체 보기에서는 마감이 가까운 목표부터 단계를 이어 붙여요) */
    fun roundSlots(goals: List<Goal>, rounds: List<StudyRound>, goalId: String?): List<Slot> {
        val gs = if (goalId == null) {
            goals.filter { it.completedAt == null && it.planStatus == PlanStatus.APPROVED }.sortedBy { it.dueAt }
        } else {
            goals.filter { it.id == goalId }
        }
        return gs.flatMap { g ->
            val fr = RoadmapProgress.of(g, rounds).stageFractions
            g.roadmap.mapIndexed { i, st ->
                val f = fr.getOrElse(i) { 0f }
                Slot(
                    id = "st:${g.id}:$i",
                    goalId = g.id,
                    title = "${i + 1}단계 · ${st.title}",
                    done = f >= 1f,
                    remainingMinutes = (st.hours * 60 * (1 - f)).toInt(),
                )
            }
        }
    }
}
