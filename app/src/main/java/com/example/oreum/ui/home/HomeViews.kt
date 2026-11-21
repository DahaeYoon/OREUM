package com.example.oreum.ui.home

import android.animation.ValueAnimator
import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.view.animation.PathInterpolator
import android.widget.FrameLayout
import androidx.core.widget.NestedScrollView
import kotlin.math.abs

/**
 * 홈 아래 패널 칸 (1차는 카드 한 장). 카드가 여러 장이면 가로로 50dp 넘게 밀어 넘길 수 있어요.
 */
class HomePager @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : FrameLayout(context, attrs) {

    var page = 0
        private set
    var onPageChanged: ((Int) -> Unit)? = null

    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private var downX = 0f
    private var downY = 0f
    private var dragging = false
    private var animator: ValueAnimator? = null
    private var offset = 0f // 0 = 첫 카드, 1 = 둘째 카드

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        applyOffset()
    }

    private fun applyOffset() {
        val w = width.toFloat()
        for (i in 0 until childCount) getChildAt(i).translationX = (i - offset) * w
        onOffset?.invoke(offset)
    }

    /** 카드가 움직일 때 (배경 유리 다시 그리기용) */
    var onOffset: ((Float) -> Unit)? = null

    fun goTo(index: Int, animate: Boolean = true) {
        val target = index.coerceIn(0, childCount - 1)
        animator?.cancel()
        val changed = target != page
        page = target
        if (!animate) {
            offset = target.toFloat()
            applyOffset()
        } else {
            val from = offset
            animator = ValueAnimator.ofFloat(from, target.toFloat()).apply {
                duration = 380
                interpolator = PathInterpolator(0.3f, 0.7f, 0.2f, 1f)
                addUpdateListener { offset = it.animatedValue as Float; applyOffset() }
                start()
            }
        }
        if (changed) onPageChanged?.invoke(page)
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> { downX = ev.x; downY = ev.y; dragging = false }
            MotionEvent.ACTION_MOVE -> {
                val dx = ev.x - downX
                val dy = ev.y - downY
                if (abs(dx) > touchSlop && abs(dx) > abs(dy)) {
                    dragging = true
                    parent?.requestDisallowInterceptTouchEvent(true)
                    return true
                }
            }
        }
        return false
    }

    override fun onTouchEvent(ev: MotionEvent): Boolean {
        val w = width.toFloat().coerceAtLeast(1f)
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                // 카드 바깥(위쪽 빈 곳)은 뒤의 산이 받도록
                val card = getChildAt(page) ?: return false
                if (ev.y < card.top + 14 * resources.displayMetrics.density) return false
                downX = ev.x; downY = ev.y; return true
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = ev.x - downX
                if (!dragging && abs(dx) > touchSlop) dragging = true
                if (dragging) {
                    offset = (page - dx / w).coerceIn(-0.15f, childCount - 0.85f)
                    applyOffset()
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                val dx = ev.x - downX
                val limit = 50 * resources.displayMetrics.density
                val target = when {
                    dx < -limit -> page + 1
                    dx > limit -> page - 1
                    else -> page
                }
                dragging = false
                goTo(target)
            }
        }
        return true
    }

    override fun onDetachedFromWindow() {
        animator?.cancel()
        super.onDetachedFromWindow()
    }
}

/** 최대 높이가 있는 스크롤 (오늘 계획 목록이 길 때 카드가 화면을 덮지 않게) */
class MaxHeightScrollView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : NestedScrollView(context, attrs) {
    var maxHeightPx: Int = Int.MAX_VALUE
        set(value) { field = value; requestLayout() }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val spec = if (maxHeightPx < Int.MAX_VALUE) MeasureSpec.makeMeasureSpec(maxHeightPx, MeasureSpec.AT_MOST) else heightMeasureSpec
        super.onMeasure(widthMeasureSpec, spec)
    }
}
