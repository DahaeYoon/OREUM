package com.example.oreum.ui.home

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.animation.PathInterpolator
import android.widget.FrameLayout
import androidx.core.widget.NestedScrollView
import com.example.oreum.data.model.TrailOrder
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * 홈 아래 카드 두 장(산 · 오늘 계획)을 옆으로 넘기는 칸. 산 그림은 그대로 두고 카드만 넘어가요.
 * 가로로 50dp 넘게 밀면 다음 · 이전 카드로.
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

/** 오늘의 학습 진행률 고리 (프로토타입 .ring: 민트 · 회색 고리 + 가운데 흰 원 + 퍼센트) */
class RingView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {
    var percent: Int = 0
        set(value) { field = value.coerceIn(0, 100); contentDescription = "$field%"; invalidate() }

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create("sans-serif-black", Typeface.NORMAL)
        color = 0xFF3E8E7A.toInt()
    }
    private val oval = RectF()

    override fun onDraw(canvas: Canvas) {
        val d = resources.displayMetrics.density
        val cx = width / 2f
        val cy = height / 2f
        val r = minOf(width, height) / 2f
        oval.set(cx - r, cy - r, cx + r, cy + r)
        paint.color = 0xFFE7ECEA.toInt()
        canvas.drawOval(oval, paint)
        paint.color = 0xFF69B5A1.toInt()
        canvas.drawArc(oval, -90f, 360f * percent / 100f, true, paint)
        paint.color = 0xFFFFFFFF.toInt()
        canvas.drawCircle(cx, cy, r - 3 * d, paint)
        text.textSize = android.util.TypedValue.applyDimension(android.util.TypedValue.COMPLEX_UNIT_SP, 10.5f, resources.displayMetrics)
        canvas.drawText("$percent%", cx, cy - (text.ascent() + text.descent()) / 2f, text)
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

/** 산 오르는 순서의 트랙 모양 견본 (프로토타입 trailSwatch) */
class TrailSwatchView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {
    var order: TrailOrder = TrailOrder.PLAN_DATE
        set(value) { field = value; invalidate() }

    private val bg = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; color = 0x80FFFFFF.toInt() }
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()
    private val path = Path().apply { moveTo(4f, 12f); cubicTo(12f, 3f, 18f, 3f, 24f, 9f); cubicTo(30f, 15f, 36f, 15f, 44f, 6f) }

    override fun onDraw(canvas: Canvas) {
        val d = resources.displayMetrics.density
        rect.set(0f, 0f, width.toFloat(), height.toFloat())
        // 160도 방향 그라데이션 (#A9D3BE → #7DB79E)
        val a = Math.toRadians(160.0 - 90.0)
        val dx = (cos(a) * width / 2).toFloat()
        val dy = (sin(a) * height / 2).toFloat()
        bg.shader = LinearGradient(width / 2f - dx, height / 2f - dy, width / 2f + dx, height / 2f + dy, 0xFFA9D3BE.toInt(), 0xFF7DB79E.toInt(), Shader.TileMode.CLAMP)
        canvas.drawRoundRect(rect, 7 * d, 7 * d, bg)
        ring.strokeWidth = d
        rect.inset(d / 2, d / 2)
        canvas.drawRoundRect(rect, 7 * d, 7 * d, ring)

        // 48 × 18 견본을 가운데에
        val s = minOf(width / 48f, height / 18f) * 0.82f
        canvas.save()
        canvas.translate((width - 48 * s) / 2f, (height - 18 * s) / 2f)
        canvas.scale(s, s)
        when (order) {
            TrailOrder.PRIORITY -> {
                fill.color = 0xFF6FB4E6.toInt()
                listOf(5f to 11f, 14f to 5.5f, 24f to 9f, 34f to 12.5f, 43f to 7f).forEach { (x, y) -> canvas.drawCircle(x, y, 2.6f, fill) }
            }
            else -> {
                val (color, dash) = when (order) {
                    TrailOrder.DUE -> 0xFFF09C84.toInt() to floatArrayOf(4.5f, 3f)
                    TrailOrder.BY_GOAL -> 0xFFA996E2.toInt() to floatArrayOf(9f, 4f)
                    else -> 0xFFFFFFFF.toInt() to null
                }
                stroke.color = color
                stroke.strokeWidth = 4.2f
                stroke.strokeCap = if (dash != null) Paint.Cap.BUTT else Paint.Cap.ROUND
                stroke.pathEffect = dash?.let { android.graphics.DashPathEffect(it, 0f) }
                canvas.drawPath(path, stroke)
            }
        }
        canvas.restore()
    }
}
