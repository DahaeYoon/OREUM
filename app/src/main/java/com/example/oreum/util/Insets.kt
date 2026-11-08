package com.example.oreum.util

import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

/**
 * 상태 표시줄 · 내비게이션 바 · 키보드에 가리지 않도록 안쪽 여백을 더해요.
 * XML에 적은 원래 padding은 그대로 두고 그 위에 더해요.
 */
fun View.applySystemBarsPadding(top: Boolean = true, bottom: Boolean = false) {
    val start = paddingLeft
    val topPad = paddingTop
    val end = paddingRight
    val bottomPad = paddingBottom
    ViewCompat.setOnApplyWindowInsetsListener(this) { v, insets ->
        val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.ime())
        v.setPadding(
            start + bars.left,
            topPad + if (top) bars.top else 0,
            end + bars.right,
            bottomPad + if (bottom) bars.bottom else 0,
        )
        insets
    }
    ViewCompat.requestApplyInsets(this)
}
