package com.example.oreum.data.model

/** 스터디 · 프로젝트 공통 그룹 */
data class Group(
    val id: String = "",
    val type: GroupType = GroupType.STUDY,
    val name: String = "",
    val description: String = "",
    val goal: String = "",
    val category: String = "",
    /** 온라인 / 오프라인 */
    val mode: String = "",
    val capacity: Int = 4,
    /** true면 스터디 찾기에 보이고 가입 신청을 받아요 */
    val isRecruiting: Boolean = true,
    val leaderId: String = "",
    /** 빠른 조회용 (Firestore 쿼리: whereArrayContains) */
    val memberIds: List<String> = emptyList(),
    val dueAt: Long? = null,
    val milestones: List<Milestone> = emptyList(),
    val questions: List<ApplicationQuestion> = emptyList(),
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
)

/** 그룹 멤버 (역할 분담은 PROJECT에서 사용) */
data class GroupMember(
    val groupId: String = "",
    val userId: String = "",
    val role: MemberRole = MemberRole.MEMBER,
    /** 프로젝트 담당 업무 이름 (예: UI 설계) */
    val duty: String = "",
    val joinedAt: Long = 0L,
)

/** 프로젝트 마일스톤 */
data class Milestone(
    val id: String = "",
    val title: String = "",
    val dueAt: Long = 0L,
    val isDone: Boolean = false,
)
