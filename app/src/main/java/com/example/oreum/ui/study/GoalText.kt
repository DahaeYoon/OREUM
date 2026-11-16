package com.example.oreum.ui.study

import android.content.Context
import android.graphics.Typeface
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import android.text.style.StyleSpan
import androidx.core.content.ContextCompat
import com.example.oreum.R

/** 목표 화면 · 상세 팝업이 함께 쓰는 문구 */
internal fun Context.priorityText(priority: Int): String = getString(
    when (priority) {
        1 -> R.string.study_priority_high
        3 -> R.string.study_priority_low
        else -> R.string.study_priority_mid
    },
)

/** 우선순위 점 색 (높음 코랄 · 보통 노랑 · 낮음 회색) */
private fun priorityDotColor(priority: Int): Int = when (priority) {
    1 -> 0xFFE9785A.toInt()
    2 -> 0xFFF5C76A.toInt()
    else -> 0xFFC5D1CE.toInt()
}

/**
 * 카드 정보 줄: ● 높음 · 주간 · ~ 11/7 · 오늘 1개 (프로토타입 .g4-meta)
 * 점과 글자 색을 한 줄 글자(Span)로 칠해요.
 */
internal fun Context.goalMetaLine(priority: Int, parts: List<String>, today: String?): CharSequence {
    val sb = SpannableStringBuilder()
    fun span(text: String, vararg what: Any) {
        val start = sb.length
        sb.append(text)
        what.forEach { sb.setSpan(it, start, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE) }
    }
    fun sep() = span("  ●  ", ForegroundColorSpan(ContextCompat.getColor(this, R.color.oreum_meta_dot)), RelativeSizeSpan(0.45f))

    span("● ", ForegroundColorSpan(priorityDotColor(priority)), RelativeSizeSpan(0.75f))
    span(
        priorityText(priority),
        StyleSpan(Typeface.BOLD),
        ForegroundColorSpan(if (priority == 1) ContextCompat.getColor(this, R.color.oreum_prio_high_ink) else ContextCompat.getColor(this, R.color.oreum_ink2)),
    )
    parts.forEach { sep(); span(it) }
    if (today != null) {
        sep()
        span(today, StyleSpan(Typeface.BOLD), ForegroundColorSpan(ContextCompat.getColor(this, R.color.oreum_today_green)))
    }
    return sb
}
