package com.example.oreum.data.model

/** 그룹 유형: 스터디와 프로젝트는 같은 Group 모델을 쓰고 유형만 달라요. */
enum class GroupType { STUDY, PROJECT }

/** 할 일 진행 상태 (그룹 업무의 미시작 → 진행 중 → 완료와 개인 할 일 공통) */
enum class TaskStatus { TODO, IN_PROGRESS, DONE }

/** 일정 종류: 캘린더 색 구분 · 위험 계산에 사용 */
enum class ScheduleKind { CLASS, PERSONAL, STUDY_GROUP, PROJECT, EXAM }

/** 그룹 안에서의 권한 */
enum class MemberRole { LEADER, MEMBER }

/** 가입 신청 상태 */
enum class ApplicationStatus { PENDING, APPROVED, REJECTED }

/** 가입 신청서 질문 형식 */
enum class QuestionType { SHORT, LONG, CHOICE }

/** 경로 재탐색 대안 종류 */
enum class RecoveryType {
    /** 대안 1: 이동 가능한 일정을 옮겨 공부 시간 확보 */
    MOVE_SCHEDULES,
    /** 대안 2: 우선순위가 높은 할 일을 먼저 배치 */
    PRIORITY_FIRST,
}

/** 대안 상태: APPLIED가 되기 전에는 원본 일정이 바뀌지 않아요. */
enum class ProposalStatus { PROPOSED, APPLIED, DISCARDED }
