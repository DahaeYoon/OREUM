package com.example.oreum.data.model

/** 할 일 · 그룹에 붙이는 짧은 메모 (그룹 공지도 groupId로 같은 모델 사용) */
data class Memo(
    val id: String = "",
    val authorId: String = "",
    val taskId: String? = null,
    val groupId: String? = null,
    val content: String = "",
    val createdAt: Long = 0L,
)
