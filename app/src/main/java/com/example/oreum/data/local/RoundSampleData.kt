package com.example.oreum.data.local

import com.example.oreum.data.model.Difficulty
import com.example.oreum.data.model.Goal
import com.example.oreum.data.model.GoalLevel
import com.example.oreum.data.model.MissReason
import com.example.oreum.data.model.NextProposal
import com.example.oreum.data.model.PlanStatus
import com.example.oreum.data.model.RoadmapDraft
import com.example.oreum.data.model.RoundItem
import com.example.oreum.data.model.RoundResult
import com.example.oreum.data.model.RoundStatus
import com.example.oreum.data.model.StudyRound
import com.example.oreum.data.model.StudySession
import com.example.oreum.domain.usecase.RoundEngine
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * 구간 구조의 샘플 데이터 (프로토타입과 같은 모습).
 * - g0 데이터베이스: 3구간까지 마침 · 4구간 진행 중
 * - g1 프로그래밍 과제: 2구간 결과 제출 → AI 다음 구간 추천 대기
 * - g2 자료구조: 1구간 마침 · 승인한 2구간은 시작 전
 * - g3 정보처리기사: AI 로드맵 · 1구간 승인 대기
 * - gp1 컴퓨터구조: 이미 오른 산
 */
object RoundSampleData {

    private val PROTOTYPE_TODAY: LocalDate = LocalDate.of(2026, 11, 5)

    data class Bundle(
        val goals: List<Goal>,
        val rounds: List<StudyRound>,
        val sessions: List<StudySession>,
        val drafts: Map<String, RoadmapDraft>,
        val proposals: Map<String, NextProposal>,
    )

    fun build(today: LocalDate, ownerId: String, baseGoals: List<Goal>): Bundle {
        val t = today.toEpochDay()
        val shift = t - PROTOTYPE_TODAY.toEpochDay()
        fun day(iso: String): Long = LocalDate.parse(iso).toEpochDay() + shift
        fun at(iso: String, time: String): Long = LocalDate.ofEpochDay(day(iso))
            .atTime(LocalTime.parse(time)).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

        val goals = baseGoals.associateBy { it.id }.toMutableMap()
        val rounds = mutableListOf<StudyRound>()
        val sessions = mutableListOf<StudySession>()
        val drafts = mutableMapOf<String, RoadmapDraft>()
        val proposals = mutableMapOf<String, NextProposal>()

        /** 항목: 제목, 예상 분, 완료, 실제 분, 못 한 이유 */
        data class I(val title: String, val min: Int, val done: Boolean, val actual: Int? = null, val reason: MissReason? = null)

        fun round(
            g: Goal, no: Int, stage: Int, topics: List<String>, list: List<I>, status: RoundStatus,
            diff: Difficulty? = null, memo: String = "", started: String? = null, doneOn: String? = null, approved: String? = null,
            plan: List<Triple<String, Int, Int>> = emptyList(),
        ): StudyRound {
            val base = RoundEngine.makeRound(g, g.roadmap, no, stage, topics.first(), t, rounds)
            val items = list.map { RoundItem(RoundEngine.newId("ri"), it.title, it.min, it.done, it.actual ?: if (it.done) it.min else 0, it.reason) }
            val r = base.copy(
                ownerId = ownerId, topics = topics, topic = topics.joinToString(" · "), items = items, status = status,
                result = if (status == RoundStatus.DONE) RoundResult(diff, memo) else null,
                startedOn = started?.let(::day), doneOn = doneOn?.let(::day), approvedOn = approved?.let(::day),
                dates = plan.map { day(it.first) }.distinct().sorted(),
            )
            plan.forEach { (d, idx, min) ->
                sessions += StudySession(RoundEngine.newId("ss"), ownerId, g.id, r.id, items[idx].id, day(d), min)
            }
            rounds += r
            return r
        }

        fun approved(id: String, stage: Int): Goal? = goals[id]?.let { g ->
            g.copy(roadmap = RoundEngine.genRoadmap(g, t), stage = stage, planStatus = PlanStatus.APPROVED).also { goals[id] = it }
        }

        approved("g0", 1)?.let { g ->
            round(g, 1, 0, listOf("SQL 기본 질의", "관계 대수"), listOf(
                I("개념 학습: SQL 기본 질의", 60, true, 55), I("예제 · 문제: SQL 기본 질의", 40, true, 45),
                I("개념 학습: 관계 대수", 50, true, 60), I("확인 퀴즈", 20, true, 15),
            ), RoundStatus.DONE, Difficulty.OK, started = "2026-10-30", doneOn = "2026-10-31",
                plan = listOf(Triple("2026-10-30", 0, 55), Triple("2026-10-30", 1, 45), Triple("2026-10-31", 2, 60), Triple("2026-10-31", 3, 15)))
            round(g, 2, 0, listOf("조인과 서브쿼리"), listOf(
                I("개념 학습: 조인과 서브쿼리", 60, true, 70), I("예제 · 문제: 조인과 서브쿼리", 50, false, 20, MissReason.NO_FOCUS),
                I("확인 퀴즈", 20, true, 20),
            ), RoundStatus.DONE, Difficulty.HARD, "조인 응용 문제가 헷갈렸어요", "2026-11-01", "2026-11-02",
                plan = listOf(Triple("2026-11-01", 0, 70), Triple("2026-11-02", 1, 20), Triple("2026-11-02", 2, 20)))
            round(g, 3, 1, listOf("함수 종속", "1~3 정규형"), listOf(
                I("복습: 조인 응용 문제", 20, true, 25), I("예제 · 문제: 조인과 서브쿼리 (이어서)", 25, true, 25),
                I("개념 학습: 함수 종속", 40, true, 45), I("개념 학습: 1~3 정규형", 50, false, 30, MissReason.NO_TIME),
                I("확인 퀴즈", 20, false, 0, MissReason.NO_TIME),
            ), RoundStatus.DONE, Difficulty.OK, started = "2026-11-03", doneOn = "2026-11-04",
                plan = listOf(Triple("2026-11-03", 0, 25), Triple("2026-11-03", 1, 25), Triple("2026-11-04", 2, 45), Triple("2026-11-04", 3, 30)))
            round(g, 4, 1, listOf("BCNF와 반정규화"), listOf(
                I("개념 학습: 1~3 정규형 (이어서)", 20, true, 20), I("확인 퀴즈: 정규화", 15, false),
                I("개념 학습: BCNF와 반정규화", 45, false), I("예제 · 문제: BCNF와 반정규화", 30, false),
            ), RoundStatus.ACTIVE, started = "2026-11-05", approved = "2026-11-04",
                plan = listOf(Triple("2026-11-05", 0, 20)))
        }

        approved("g1", 0)?.let { g ->
            round(g, 1, 0, listOf("요구사항 정리"), listOf(I("요구사항 정리", 60, true, 50), I("기능 목록 작성", 30, true, 30)),
                RoundStatus.DONE, Difficulty.EASY, started = "2026-11-01", doneOn = "2026-11-01",
                plan = listOf(Triple("2026-11-01", 0, 50), Triple("2026-11-01", 1, 30)))
            round(g, 2, 0, listOf("화면 흐름 · 와이어프레임"), listOf(
                I("개념 학습: 화면 흐름 · 와이어프레임", 50, true, 60), I("와이어프레임 그리기", 40, true, 50),
                I("확인 퀴즈", 15, false, 0, MissReason.NO_TIME),
            ), RoundStatus.DONE, Difficulty.OK, started = "2026-11-03", doneOn = "2026-11-04",
                plan = listOf(Triple("2026-11-03", 0, 60), Triple("2026-11-04", 1, 50)))
            RoundEngine.proposeNext(g, rounds, t)?.let { proposals[g.id] = it }
        }

        approved("g2", 0)?.let { g ->
            round(g, 1, 0, listOf("배열 · 연결 리스트"), listOf(
                I("개념 학습: 배열 · 연결 리스트", 60, true, 60), I("예제 · 문제: 배열 · 연결 리스트", 40, true, 35),
                I("확인 퀴즈", 20, true, 20),
            ), RoundStatus.DONE, Difficulty.OK, started = "2026-10-26", doneOn = "2026-10-27",
                plan = listOf(Triple("2026-10-26", 0, 60), Triple("2026-10-27", 1, 35), Triple("2026-10-27", 2, 20)))
            rounds += RoundEngine.makeRound(g, g.roadmap, 2, 0, "스택 · 큐", t, rounds)
                .copy(ownerId = ownerId, approvedOn = day("2026-11-04"))
        }

        goals["g3"]?.let { g ->
            drafts[g.id] = RoundEngine.genDraft(g, t)
            goals[g.id] = g.copy(planStatus = PlanStatus.DRAFT, roadmap = emptyList(), stage = 0)
        }

        // 이미 오른 산 하나
        val gp1 = Goal(
            id = "gp1", ownerId = ownerId, title = "컴퓨터구조 중간고사", subject = "컴퓨터구조", description = "컴퓨터구조 시험 준비",
            startDate = day("2026-10-12"), dueAt = at("2026-10-22", "10:00"), level = GoalLevel.INTERMEDIATE, priority = 2,
            planStatus = PlanStatus.APPROVED, completedAt = at("2026-10-21", "18:00"),
        ).let { it.copy(roadmap = RoundEngine.genRoadmap(it, t)) }.let { it.copy(stage = it.roadmap.size) }
        goals[gp1.id] = gp1

        return Bundle(goals.values.toList(), rounds, sessions, drafts, proposals)
    }
}
