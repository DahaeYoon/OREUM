package com.example.oreum.data.model

/** 그룹장이 앱 안에서 만드는 가입 신청서 질문 (외부 폼 사용 안 함) */
data class ApplicationQuestion(
    val id: String = "",
    val label: String = "",
    val type: QuestionType = QuestionType.SHORT,
    val required: Boolean = false,
    /** CHOICE일 때 선택지 */
    val options: List<String> = emptyList(),
)

/** 질문 하나에 대한 답변 */
data class ApplicationAnswer(
    val questionId: String = "",
    val value: String = "",
)

/**
 * 가입 신청 = 가입 요청(GroupJoinRequest) + 제출한 답변.
 * 요청과 답변은 항상 함께 만들어지고 함께 승인·거절되므로 하나의 문서로 합쳤어요.
 */
data class GroupApplication(
    val id: String = "",
    val groupId: String = "",
    val applicantId: String = "",
    val answers: List<ApplicationAnswer> = emptyList(),
    val status: ApplicationStatus = ApplicationStatus.PENDING,
    val submittedAt: Long = 0L,
    val decidedAt: Long? = null,
)
