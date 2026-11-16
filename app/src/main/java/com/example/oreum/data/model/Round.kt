package com.example.oreum.data.model

/**
 * 구간 기반 적응형 학습 (1차 개발 범위)
 * - AI는 ① 전체 로드맵(단계 · 주제 · 예상 시간, 날짜 없음) ② 지금 할 구간의 상세 계획만 만들어요.
 * - 구간 결과를 제출하면 그 결과로 다음 구간을 제안하고, 사용자가 승인해야 확정돼요.
 * - 구간은 날짜와 달라요: 여러 날에 걸쳐 할 수 있고, 실제로 공부한 날짜 · 시간(StudySession)만 기록해요.
 */

/** 로드맵의 큰 덩어리 하나 (예: 2단계 · 정규화 · 설계) */
data class RoadmapStage(
    val title: String = "",
    /** 이 단계에서 다룰 주제들 */
    val topics: List<String> = emptyList(),
    /** 예상 학습 시간 (시간, 0.5 단위) */
    val hours: Double = 0.0,
) {
    val scope: String get() = topics.joinToString(" · ")
}

/** 구간 상태: READY = 승인했지만 아직 시작 전, ACTIVE = 진행 중, DONE = 결과 제출 */
enum class RoundStatus { READY, ACTIVE, DONE }

/** 구간 항목 종류: 새 주제 · 지난 구간에서 이어서 · 복습 */
enum class ItemKind { NEW, CARRY, REVIEW }

/** 구간 결과 제출 때 고르는 체감 난이도 */
enum class Difficulty(val label: String) {
    EASY("쉬움"),
    OK("적당"),
    HARD("어려움"),
}

/** 구간 안의 학습 항목 하나 */
data class RoundItem(
    val id: String = "",
    val title: String = "",
    /** 예상 시간 (분) */
    val minutes: Int = 0,
    val done: Boolean = false,
    /** 실제로 공부한 시간 합계 (분) = 이 항목의 StudySession 합 */
    val actualMinutes: Int = 0,
    /** 못 끝낸 이유 (결과 제출 때 선택) */
    val reason: MissReason? = null,
    val kind: ItemKind = ItemKind.NEW,
) {
    /** 진행률에 인정하는 시간: 끝냈으면 예상 시간 전체, 아니면 실제로 한 만큼 */
    val creditMinutes: Int get() = if (done) minutes else minOf(actualMinutes, minutes)
}

/** 구간 결과 (제출할 때 저장) */
data class RoundResult(
    val difficulty: Difficulty? = null,
    val memo: String = "",
    /** 회고 태그는 2차 기능이라 1차 화면에서는 비워 둬요 (엔진은 지원) */
    val tags: List<RetroTag> = emptyList(),
)

/** 학습 구간 하나 (예: 3구간 · 함수 종속 · 1~3 정규형) */
data class StudyRound(
    val id: String = "",
    val ownerId: String = "",
    val goalId: String = "",
    /** 몇 번째 구간 (1부터) */
    val no: Int = 1,
    /** 로드맵 단계 index (0부터) */
    val stage: Int = 0,
    /** 화면에 보이는 주제 (예: "함수 종속 · 1~3 정규형") */
    val topic: String = "",
    /** 이 구간에서 새로 다루는 로드맵 주제 */
    val topics: List<String> = emptyList(),
    val method: List<String> = emptyList(),
    val criteria: List<String> = emptyList(),
    val items: List<RoundItem> = emptyList(),
    val status: RoundStatus = RoundStatus.READY,
    /** 날짜는 모두 epoch day */
    val createdOn: Long = 0L,
    val approvedOn: Long? = null,
    val startedOn: Long? = null,
    val doneOn: Long? = null,
    /** 실제로 공부한 날 (StudySession 기준, 오름차순) */
    val dates: List<Long> = emptyList(),
    val result: RoundResult? = null,
) {
    val estimatedMinutes: Int get() = items.sumOf { it.minutes }
    val actualMinutes: Int get() = items.sumOf { it.actualMinutes }
    val doneCount: Int get() = items.count { it.done }
    val allDone: Boolean get() = items.isNotEmpty() && items.all { it.done }
    /** 화면용 주제: "(이어서)" 꼬리표는 빼요 */
    val displayTopic: String get() = topic.removeSuffix(" (이어서)")
}

/** 실제 학습 기록: 어느 날, 어떤 구간의 어떤 항목을 몇 분 공부했는지 */
data class StudySession(
    val id: String = "",
    val ownerId: String = "",
    val goalId: String = "",
    val roundId: String = "",
    val itemId: String = "",
    /** 공부한 날 (epoch day) */
    val date: Long = 0L,
    val minutes: Int = 0,
)

/** AI가 처음 만든 로드맵 + 1구간 계획 (승인 전 초안, 시뮬레이션) */
data class RoadmapDraft(
    val goalId: String = "",
    val roadmap: List<RoadmapStage> = emptyList(),
    val round: StudyRound = StudyRound(),
    val createdOn: Long = 0L,
    /** 재생성할 때마다 바뀌는 1구간 구성 번호 */
    val variant: Int = 0,
)

/** 구간 결과를 보고 AI가 제안한 다음 구간 (승인 전, 시뮬레이션) */
data class NextProposal(
    val goalId: String = "",
    val fromRoundId: String = "",
    /** 다음 구간 계획. final = true면 null */
    val round: StudyRound? = null,
    /** 로드맵을 모두 마쳐서 더 할 구간이 없음 → 승인하면 정상 도착 */
    val final: Boolean = false,
    /** 추천 이유 (짧은 문장) */
    val why: List<String> = emptyList(),
    /** 이렇게 바꿨어요 (짧은 문장) */
    val fixes: List<String> = emptyList(),
    /** 지난 구간 요약 */
    val lastNo: Int = 0,
    val lastDifficulty: Difficulty? = null,
    val lastReasons: List<MissReason> = emptyList(),
    val lastMemo: String = "",
    val lastEstimatedMinutes: Int = 0,
    val lastActualMinutes: Int = 0,
    val lastDoneCount: Int = 0,
    val lastTotal: Int = 0,
    val createdOn: Long = 0L,
)
