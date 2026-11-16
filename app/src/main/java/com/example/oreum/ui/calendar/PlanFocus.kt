package com.example.oreum.ui.calendar

/**
 * 다른 화면에서 계획 탭으로 넘어올 때 보여 줄 목표를 잠깐 맡겨 두는 곳.
 * (목표 상세의 '계획 보기', AI 계획 승인 후) 계획 화면이 열리면서 한 번 읽고 비워요.
 */
object PlanFocus {
    var goalId: String? = null

    /** 계획 탭에서 고를 날 (AI 복구 승인 후 내일) */
    var date: java.time.LocalDate? = null
}
