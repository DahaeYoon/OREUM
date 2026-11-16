package com.example.oreum.data.model

/** 할 일 진행 상태 */
enum class TaskStatus { TODO, IN_PROGRESS, DONE }

/** 일정 종류: 캘린더 색 구분에 사용 */
enum class ScheduleKind { CLASS, PERSONAL, EXAM }

/** 목표를 만들 때 고르는 현재 학습 수준 (AI 계획 분량 계산에 사용) */
enum class GoalLevel(val label: String) {
    BEGINNER("기초"),
    INTERMEDIATE("중급"),
    ADVANCED("고급"),
}

/** 목표 기간 구분 (목표 화면 필터) */
enum class GoalPeriod(val label: String) {
    DAILY("일일"),
    WEEKLY("주간"),
    MONTHLY("월간"),
}

/** 목표의 AI 계획 상태: APPROVED가 되기 전에는 계획이 일정에 들어가지 않아요. */
enum class PlanStatus { NONE, DRAFT, APPROVED }

/** 학습 결과 기록에서 고르는 미완료 이유 (AI 복구의 기본 원인) */
enum class MissReason(val label: String, val short: String) {
    NO_TIME("시간이 부족했음", "시간 부족"),
    TOO_HARD("학습 내용이 어려웠음", "내용이 어려움"),
    NO_FOCUS("집중하기 어려웠음", "집중이 어려움"),
    SUDDEN_EVENT("갑작스러운 일정이 발생했음", "갑작스러운 일정"),
    TOO_MUCH("학습 분량이 너무 많았음", "분량이 많음"),
    OTHER("기타", "기타"),
}

/** 학습 결과 기록의 회고 키워드 (여러 개 선택). good = 긍정 키워드 */
enum class RetroTag(val label: String, val good: Boolean) {
    UNDERSTOOD("개념이 잘 이해됨", true),
    HARD_PROBLEMS("문제 풀이가 어려움", false),
    FOCUSED("집중이 잘 됨", true),
    DISTRACTED("자꾸 딴짓함", false),
    TIRED("피곤했음", false),
    NEED_REVIEW("복습이 더 필요함", false),
    RIGHT_AMOUNT("분량이 적당함", true),
    TOO_MUCH("분량이 많았음", false),
}

/** 홈 산에서 남은 할 일을 놓는 순서. 순서마다 트랙 색 · 모양이 달라요. */
enum class TrailOrder(val label: String) {
    /** 하기로 한 날 순 · 흰색 실선 */
    PLAN_DATE("하기로 한 날 순"),
    /** 마감 임박 순 · 코랄 점선 */
    DUE("마감 임박 순"),
    /** 우선순위 순 · 하늘색 동그라미 */
    PRIORITY("우선순위 순"),
    /** 목표별로 묶기 · 보라 긴 점선 */
    BY_GOAL("목표별로 묶기"),
}

/** AI 계획 · 복구 제안 상태: APPLIED가 되기 전에는 원본 계획이 바뀌지 않아요. */
enum class ProposalStatus { PROPOSED, APPLIED, DISCARDED }
