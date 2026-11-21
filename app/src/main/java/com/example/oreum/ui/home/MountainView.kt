package com.example.oreum.ui.home

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.provider.Settings
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.view.animation.PathInterpolator
import androidx.core.graphics.createBitmap
import androidx.core.graphics.withSave
import com.example.oreum.R
import com.example.oreum.data.model.TrailOrder
import com.example.oreum.domain.usecase.HomeMountain
import com.example.oreum.domain.usecase.Trail
import com.example.oreum.util.Season
import kotlin.math.ceil
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.tan

/**
 * 홈의 산 (프로토타입 mountain({ quiet: true, flag: true, cover: true, animate: true })).
 * - 계절 산 그림 + 천천히 흐르는 구름(산 뒤로 지나감)
 * - 산 오르는 순서마다 다른 트랙(흰 실선 · 코랄 점선 · 하늘색 동그라미 · 보라 긴 점선) + 오른 구간은 연한 노랑
 * - 완료한 칸은 흰 동그라미 체크, 지금 위치는 흰 링 + 민트 점(퍼지는 빛), 옆에 '다음 할 일'
 * - 정상 깃발(펄럭임), 정상 도착 시 빛 · 반짝임 · '깃발을 눌러 보세요'
 * - 완료하면 새로 오른 구간이 노랗게 채워지며 점이 길을 따라 올라가요 (1.5초)
 * - 끌어서 둘러보기 · 두 손가락 확대(최대 1.8배) · 두 번 눌러 확대/되돌리기
 * 좌표는 모두 산 그림 단위(853 × 858)로 그리고, 화면에는 프로토타입과 같은 영역(HOME_BOX)을 꽉 차게 맞춰요.
 */
class MountainView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    data class State(
        val progress: Float = 0f,
        val slots: List<HomeMountain.Slot> = emptyList(),
        val top: Boolean = false,
        val order: TrailOrder = TrailOrder.PLAN_DATE,
    )

    /** 깃발을 눌렀을 때 */
    var onFlagClick: (() -> Unit)? = null

    /** 완료한 체크포인트를 눌렀을 때 (slots의 위치) */
    var onCheckpointClick: ((Int) -> Unit)? = null

    /** 산이 차지하는 영역의 아래쪽 여백 (아래 카드에 가리는 부분, 프로토타입 156px) */
    var artBottomInset: Float = 156 * resources.displayMetrics.density
        set(value) { field = value; invalidate() }

    private var state = State()
    private var season = Season.of()
    private val density = resources.displayMetrics.density
    private val reducedMotion: Boolean =
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f

    // ---------- 그림 ----------
    private var base: Bitmap? = null
    private var fg: Bitmap? = null
    private val bitmapPaint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
    private val skyPaint = Paint()
    private val groundPaint = Paint()
    private val clouds = mutableListOf<Cloud>()

    private class Cloud(val bitmap: Bitmap, val width: Float, val y: Float, val k: Float, val dur: Float, val x0: Float, val alpha: Int)

    // ---------- 트랙 · 표시 ----------
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textAlign = Paint.Align.CENTER
    }
    private val flagPath = Path().apply {
        moveTo(3f, -90f); cubicTo(22f, -98f, 36f, -80f, 60f, -88f); lineTo(52f, -70f); lineTo(60f, -52f)
        cubicTo(36f, -44f, 22f, -62f, 3f, -54f); close()
    }
    private val sparkPath = Path()
    private val tmpRect = RectF()

    // ---------- 화면 맞춤 · 둘러보기 ----------
    private val baseMatrix = Matrix()
    private val drawMatrix = Matrix()
    private val inverse = Matrix()
    private var panX = 0f
    private var panY = 0f
    private var zoom = 1f
    private var boxY = HOME_BOX_Y
    private var panAnimator: ValueAnimator? = null

    // ---------- 완료 애니메이션 ----------
    private var animFrom: Float? = null
    private var animStart = 0L
    private val startTime = System.nanoTime()

    init {
        loadSeason()
        isClickable = true
    }

    private fun loadSeason() {
        val (b, f) = when (season) {
            Season.SPRING -> R.drawable.mt_base_spring to R.drawable.mt_fg_spring
            Season.SUMMER -> R.drawable.mt_base_summer to R.drawable.mt_fg_summer
            Season.AUTUMN -> R.drawable.mt_base_autumn to R.drawable.mt_fg_autumn
            Season.WINTER -> R.drawable.mt_base_winter to R.drawable.mt_fg_winter
        }
        val opts = BitmapFactory.Options().apply { inScaled = false }
        base = BitmapFactory.decodeResource(resources, b, opts)
        fg = BitmapFactory.decodeResource(resources, f, opts)
        // 늘린 하늘 · 땅 색 (프로토타입 필터 saturate(.95) brightness(1.08)를 적용한 색)
        val (skyTop, skyBottom, ground) = when (season) {
            Season.SPRING -> Triple(0xFF89D8FF, 0xFFB7F0FF, 0xFFBAE69C)
            Season.SUMMER -> Triple(0xFF44B6FC, 0xFF73D4FF, 0xFFA2D677)
            Season.AUTUMN -> Triple(0xFF93C6F6, 0xFFB8DFFF, 0xFFD4BA79)
            Season.WINTER -> Triple(0xFF9CD1FA, 0xFFBDE7FF, 0xFFE2F7FF)
        }
        skyPaint.shader = LinearGradient(0f, -900f, 0f, 12f, skyTop.toInt(), skyBottom.toInt(), Shader.TileMode.CLAMP)
        groundPaint.color = ground.toInt()
        buildClouds()
    }

    /** 구름 6종을 미리 그려 둬요 (프로토타입 CLOUD_SHAPES · CLOUD_SET) */
    private fun buildClouds() {
        clouds.forEach { it.bitmap.recycle() }
        clouds.clear()
        val winter = season == Season.WINTER
        val bottom = if (winter) 0xFFDCE6EE.toInt() else 0xFFE4EEF5.toInt()
        val shade = if (winter) 0xFFC9D7E2.toInt() else 0xFFCFE0EC.toInt()
        val px = 2f // 그림 단위당 픽셀
        CLOUD_SET.forEach { (shape, y, k, dur, x0) ->
            val parts = CLOUD_SHAPES.getValue(shape)
            val w = CLOUD_WIDTH.getValue(shape)
            val bmp = createBitmap(((w + 10) * px).toInt(), ((CLOUD_TOP + 76) * px).toInt())
            val c = Canvas(bmp)
            c.scale(px, px)
            c.translate(0f, CLOUD_TOP) // 위로 튀어나온 동그라미가 잘리지 않게
            val p = Paint(Paint.ANTI_ALIAS_FLAG)
            // 그림자 (아래로 4)
            p.color = shade
            parts.forEach { part -> c.withSave { translate(0f, 4f); part.draw(this, p) } }
            // 위는 흰색, 아래로 갈수록 연한 하늘색 (도형마다 따로)
            parts.forEach { part ->
                val (top, bot) = part.verticalBounds()
                p.shader = LinearGradient(0f, top, 0f, bot, intArrayOf(0xFFFFFFFF.toInt(), 0xFFFFFFFF.toInt(), bottom), floatArrayOf(0f, 0.35f, 1f), Shader.TileMode.CLAMP)
                part.draw(c, p)
                p.shader = null
            }
            val alpha = ((if (shape == "wisp") 0.75f else 0.97f) * 255).roundToInt()
            clouds += Cloud(bmp, w * k, y.toFloat(), k, dur.toFloat(), x0.toFloat(), alpha)
        }
    }

    fun setState(newState: State, animateFrom: Float? = null) {
        val old = state
        state = newState
        val newBoxY = if (newState.top) HOME_BOX_DONE_Y else HOME_BOX_Y
        if (newBoxY != boxY) {
            boxY = newBoxY
            updateBaseMatrix()
        }
        if (animateFrom != null && animateFrom < newState.progress && !reducedMotion && old != newState) {
            animFrom = animateFrom
            animStart = System.nanoTime()
        }
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        updateBaseMatrix()
    }

    /** viewBox(HOME_BOX)를 산 영역에 꽉 차게 (가운데 맞춤 · 넘치는 쪽은 잘라냄) */
    private fun updateBaseMatrix() {
        val aw = width.toFloat()
        val ah = height - artBottomInset
        if (aw <= 0 || ah <= 0) return
        val s = max(aw / BOX_W, ah / BOX_H)
        baseMatrix.reset()
        baseMatrix.postTranslate(-BOX_X, -boxY)
        baseMatrix.postScale(s, s)
        baseMatrix.postTranslate((aw - BOX_W * s) / 2f, (ah - BOX_H * s) / 2f)
    }

    private fun artHeight() = height - artBottomInset

    private fun currentMatrix(): Matrix {
        drawMatrix.set(baseMatrix)
        // 프로토타입: translate(x, y) scale(s), 기준점 50% 72%
        drawMatrix.postScale(zoom, zoom, width / 2f, artHeight() * 0.72f)
        drawMatrix.postTranslate(panX, panY)
        return drawMatrix
    }

    override fun onDraw(canvas: Canvas) {
        val now = (System.nanoTime() - startTime) / 1e9f
        canvas.withSave {
            concat(currentMatrix())
            drawScene(this, now)
        }
        if (!reducedMotion && isShown) postInvalidateOnAnimation()
    }

    private fun drawScene(c: Canvas, now: Float) {
        // 1) 하늘 · 땅 · 산 그림
        c.drawRect(-400f, -900f, 1253f, 12f, skyPaint)
        c.drawRect(-400f, Trail.MH - 4f, 1253f, Trail.MH + 696f, groundPaint)
        base?.let { tmpRect.set(0f, 0f, Trail.MW, Trail.MH); c.drawBitmap(it, null, tmpRect, bitmapPaint) }
        // 2) 구름: 산 그림 위에 그린 뒤 산 부분(fg)을 다시 덮어 구름이 산 뒤로 지나가요
        drawClouds(c, now)
        fg?.let { tmpRect.set(0f, 0f, Trail.MW, Trail.MH); c.drawBitmap(it, null, tmpRect, bitmapPaint) }
        // 3) 트랙 · 체크포인트 · 지금 위치 · 이름표
        drawTrail(c, now)
        // 4) 정상 깃발
        drawFlag(c, now)
    }

    private fun drawClouds(c: Canvas, now: Float) {
        clouds.forEach { cl ->
            val from = -cl.width - 160f
            val to = Trail.MW + 160f
            val x = if (reducedMotion) cl.x0 else {
                val begin = -((cl.x0 - from) / (to - from)) * cl.dur
                val f = (((now - begin) / cl.dur) % 1f + 1f) % 1f
                from + (to - from) * f
            }
            bitmapPaint.alpha = cl.alpha
            val bw = cl.bitmap.width / 2f * cl.k
            val bh = cl.bitmap.height / 2f * cl.k
            val top = cl.y - CLOUD_TOP * cl.k
            tmpRect.set(x, top, x + bw, top + bh)
            c.drawBitmap(cl.bitmap, null, tmpRect, bitmapPaint)
        }
        bitmapPaint.alpha = 255
    }

    // ---------- 트랙 ----------
    private data class TrackStyle(val color: Int, val opacity: Float, val on: Int, val off: Int, val width: Float = 0.62f, val dot: Boolean = false, val base: Float = 0f)

    private fun styleOf(order: TrailOrder) = when (order) {
        TrailOrder.PLAN_DATE -> TrackStyle(0xFFFFFFFF.toInt(), 0.42f, 1, 0)
        TrailOrder.DUE -> TrackStyle(0xFFF09C84.toInt(), 0.85f, 2, 1, width = 0.5f, base = 0.16f)
        TrailOrder.PRIORITY -> TrackStyle(0xFF6FB4E6.toInt(), 0.9f, 1, 3, dot = true, base = 0.16f)
        TrailOrder.BY_GOAL -> TrackStyle(0xFFA996E2.toInt(), 0.85f, 5, 2, width = 0.5f, base = 0.16f)
    }

    /** a ~ b 구간을 0.005 간격의 짧은 선으로 (원근감: 아래는 넓게, 위는 좁게). 겹친 마디가 진해지지 않게 한 겹으로 투명도 적용 */
    private fun segment(c: Canvas, a: Float, b: Float, widthK: Float, opacity: Float, color: Int, reveal: ((Int, Int) -> Float)? = null) {
        if (b <= a) return
        val save = c.saveLayerAlpha(LAYER_BOUNDS, (opacity * 255).roundToInt())
        linePaint.color = color
        linePaint.strokeCap = Paint.Cap.ROUND
        val n = max(1, ceil((b - a) / STEP).toInt())
        var q = a
        var i = 0
        while (q < b - 1e-6f) {
            val p1 = Trail.at(q)
            val p2 = Trail.at(min(b, q + STEP))
            val alpha = reveal?.invoke(i, n) ?: 1f
            if (alpha > 0f) {
                linePaint.alpha = (alpha * 255).roundToInt()
                linePaint.strokeWidth = Trail.roadWidth(p1[1]) * widthK
                c.drawLine(p1[0], p1[1], p2[0], p2[1], linePaint)
            }
            q += STEP
            i++
        }
        linePaint.alpha = 255
        c.restoreToCount(save)
    }

    private fun drawTrail(c: Canvas, now: Float) {
        val st = state
        val p = st.progress.coerceIn(0f, 1f)
        val ts = styleOf(st.order)
        val qa = animFrom
        val animT = if (qa != null) ((System.nanoTime() - animStart) / 1e9f) else 0f
        if (qa != null && animT > 2.8f) animFrom = null

        // 산 오르는 순서마다 다른 트랙
        if (ts.base > 0f) segment(c, 0f, 1f, 0.62f, ts.base, 0xFFFFFFFF.toInt())
        run {
            val save = c.saveLayerAlpha(LAYER_BOUNDS, (ts.opacity * 255).roundToInt())
            linePaint.color = ts.color
            linePaint.strokeCap = if (ts.off > 0) Paint.Cap.BUTT else Paint.Cap.ROUND
            fillPaint.color = ts.color
            var q = 0f
            var i = 0
            while (q < 1f - 1e-6f) {
                if (i % (ts.on + ts.off) < ts.on) {
                    val p1 = Trail.at(q)
                    if (ts.dot) {
                        c.drawCircle(p1[0], p1[1], Trail.roadWidth(p1[1]) * 0.26f, fillPaint)
                    } else {
                        val p2 = Trail.at(min(1f, q + STEP))
                        linePaint.strokeWidth = Trail.roadWidth(p1[1]) * ts.width
                        c.drawLine(p1[0], p1[1], p2[0], p2[1], linePaint)
                    }
                }
                q += STEP
                i++
            }
            c.restoreToCount(save)
        }
        // 오른 구간: 연한 노랑. 방금 완료했으면 새로 오른 구간이 아래부터 채워져요
        if (p > 0f) segment(c, 0f, min(1f, qa ?: p), 0.62f, 0.9f, DONE_YELLOW)
        if (qa != null) {
            val k = (animT / ANIM_SEC).coerceIn(0f, 1f)
            segment(c, qa, p, 0.62f, 0.9f, DONE_YELLOW) { i, n ->
                val start = min(0.99f, (i + 1f) / n)
                ((k - start) / (1f - start)).coerceIn(0f, 1f)
            }
        }

        // 체크포인트: 완료 = 흰 동그라미 체크, 남은 곳 = 반투명 흰 링
        val cps = st.slots.size
        if (cps > 0) {
            val step = max(1, ceil((cps - 1) / 12f).toInt())
            val cur = (p * cps).roundToInt()
            for (k in 1 until cps) {
                if (k % step != 0 && k != cur) continue
                val t = k.toFloat() / cps
                val (x, y) = Trail.at(t).let { it[0] to it[1] }
                val r = 8.5f + Trail.roadWidth(y) * 0.15f
                if (t <= p + 1e-6f) {
                    fillPaint.color = 0xEBFFFFFF.toInt()
                    c.drawCircle(x, y, r * 1.05f, fillPaint)
                    linePaint.color = 0xFF5BAE92.toInt()
                    linePaint.strokeWidth = r * 0.26f
                    linePaint.strokeCap = Paint.Cap.ROUND
                    linePaint.strokeJoin = Paint.Join.ROUND
                    val path = Path().apply {
                        moveTo(x - r * 0.45f, y + r * 0.02f)
                        rLineTo(r * 0.3f, r * 0.3f)
                        rLineTo(r * 0.58f, -r * 0.6f)
                    }
                    c.drawPath(path, linePaint)
                } else {
                    fillPaint.color = 0x73FFFFFF
                    c.drawCircle(x, y, r, fillPaint)
                    fillPaint.color = 0x80FFFFFF.toInt()
                    c.drawCircle(x, y, r * 0.42f, fillPaint)
                }
            }

            // 지금 위치: 흰 링 + 민트 점 + 퍼지는 빛 (방금 완료했으면 길을 따라 올라감)
            if (p < 1f || qa != null) {
                val final = Trail.at(p)
                val r0 = 15f + Trail.roadWidth(final[1]) * 0.2f
                val pos = if (qa != null) along(qa, p, (animT / ANIM_SEC).coerceIn(0f, 1f)) else final
                if (!reducedMotion) {
                    val f = (now % 2.4f) / 2.4f
                    fillPaint.color = 0xFFFFFFFF.toInt()
                    fillPaint.alpha = ((0.55f * (1f - f)) * 255).roundToInt()
                    c.drawCircle(pos[0], pos[1], r0 + (r0 * 2.3f - r0) * f, fillPaint)
                    fillPaint.alpha = 255
                }
                fillPaint.color = 0xFFFFFFFF.toInt()
                c.drawCircle(pos[0], pos[1], r0, fillPaint)
                fillPaint.color = MINT
                c.drawCircle(pos[0], pos[1], r0 * 0.5f, fillPaint)
            }

            // '다음 할 일' 이름표 (지금 위치 바로 옆)
            val nextK = cur + 1
            if (nextK in 1..cps && !st.slots[nextK - 1].done) {
                val (x, y) = Trail.at(p).let { it[0] to it[1] }
                val h = 32f
                val w = 90f
                val tx = if (x > 430f) max(20f, x - 38f - w) else min(800f - w, x + 38f)
                val ty = y - h / 2f
                tmpRect.set(tx, ty, tx + w, ty + h)
                fillPaint.color = 0xF0FFFFFF.toInt()
                c.drawRoundRect(tmpRect, h / 2, h / 2, fillPaint)
                linePaint.color = MINT
                linePaint.strokeWidth = 2f
                c.drawRoundRect(tmpRect, h / 2, h / 2, linePaint)
                textPaint.color = 0xFF3E8E7A.toInt()
                textPaint.textSize = 15f
                c.drawText(context.getString(R.string.home_next_label), tx + w / 2, ty + 21.5f, textPaint)
            }

        }
    }

    /** a → b 구간을 길이에 맞춰 일정한 속도로 따라가는 위치 (k = 0 ~ 1) */
    private fun along(a: Float, b: Float, k: Float): FloatArray {
        val pts = mutableListOf<FloatArray>()
        var q = a
        while (q < b) { pts += Trail.at(q); q += 0.003f }
        pts += Trail.at(b)
        var total = 0f
        for (i in 1 until pts.size) total += hypot(pts[i][0] - pts[i - 1][0], pts[i][1] - pts[i - 1][1])
        var target = total * k
        for (i in 1 until pts.size) {
            val d = hypot(pts[i][0] - pts[i - 1][0], pts[i][1] - pts[i - 1][1])
            if (target <= d && d > 0f) {
                val f = target / d
                return floatArrayOf(pts[i - 1][0] + (pts[i][0] - pts[i - 1][0]) * f, pts[i - 1][1] + (pts[i][1] - pts[i - 1][1]) * f)
            }
            target -= d
        }
        return pts.last()
    }

    // ---------- 정상 깃발 ----------
    private fun drawFlag(c: Canvas, now: Float) {
        val (fx, fy) = Trail.at(1f).let { it[0] to it[1] }
        c.withSave {
            translate(fx, fy)
            if (state.top) {
                fillPaint.shader = RadialGradient(0f, 0f, 70f, 0xD9FFFFFF.toInt(), 0x00FFFFFF, Shader.TileMode.CLAMP)
                drawCircle(0f, 0f, 70f, fillPaint)
                fillPaint.shader = null
                fillPaint.color = 0xFFF5C76A.toInt()
                listOf(Triple(-8f, -112f, 12f), Triple(44f, -114f, 10f), Triple(82f, -60f, 8f)).forEach { (x, y, r) ->
                    sparkPath.reset()
                    sparkPath.moveTo(x, y - r)
                    sparkPath.quadTo(x, y, x + r, y)
                    sparkPath.quadTo(x, y, x, y + r)
                    sparkPath.quadTo(x, y, x - r, y)
                    sparkPath.quadTo(x, y, x, y - r)
                    sparkPath.close()
                    drawPath(sparkPath, fillPaint)
                }
            }
            fillPaint.color = 0x2E263D39
            tmpRect.set(-14f, -3f, 14f, 7f)
            drawOval(tmpRect, fillPaint)
            fillPaint.color = 0xFF7A5A3A.toInt()
            tmpRect.set(-3f, -92f, 3f, 2f)
            drawRoundRect(tmpRect, 3f, 3f, fillPaint)
            // 펄럭임: skewY 0 → -4 → 0 → 3 → 0도 (2.6초)
            val deg = if (reducedMotion) 0f else skewAt((now % 2.6f) / 2.6f)
            withSave {
                val m = Matrix().apply { setSkew(0f, tan(Math.toRadians(deg.toDouble())).toFloat()) }
                concat(m)
                fillPaint.color = 0xFFE28C72.toInt()
                drawPath(flagPath, fillPaint)
            }
            if (state.top) drawFlagTip(this)
        }
    }

    private fun skewAt(f: Float): Float {
        val v = floatArrayOf(0f, -4f, 0f, 3f, 0f)
        val seg = (f * 4).coerceAtMost(3.999f)
        val i = seg.toInt()
        return v[i] + (v[i + 1] - v[i]) * (seg - i)
    }

    /** 정상 도착: '깃발을 눌러 보세요' 말풍선 */
    private fun drawFlagTip(c: Canvas) {
        fillPaint.color = 0xF0FFFFFF.toInt()
        fillPaint.setShadowLayer(6f, 0f, 3f, 0x2E3C5A50)
        tmpRect.set(-140f, -100f, -22f, -38f)
        c.drawRoundRect(tmpRect, 18f, 18f, fillPaint)
        fillPaint.clearShadowLayer()
        val tri = Path().apply { moveTo(-24f, -76f); lineTo(-10f, -68f); lineTo(-24f, -60f); close() }
        c.drawPath(tri, fillPaint)
        textPaint.color = 0xFF3E8E7A.toInt()
        textPaint.textSize = 19f
        c.drawText(context.getString(R.string.home_flag_tip_1), -81f, -75f, textPaint)
        c.drawText(context.getString(R.string.home_flag_tip_2), -81f, -51f, textPaint)
    }

    // ---------- 터치: 둘러보기 · 확대 · 깃발 / 체크포인트 ----------
    private val scaleDetector = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            zoom = (zoom * detector.scaleFactor).coerceIn(1f, 1.8f)
            clampPan()
            invalidate()
            return true
        }
    })

    private val gestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onDown(e: MotionEvent): Boolean {
            panAnimator?.cancel()
            return true
        }

        override fun onScroll(e1: MotionEvent?, e2: MotionEvent, distanceX: Float, distanceY: Float): Boolean {
            if (scaleDetector.isInProgress) return true
            panX -= distanceX
            panY -= distanceY
            clampPan()
            invalidate()
            return true
        }

        override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
            hitTest(e.x, e.y)
            return true
        }

        override fun onDoubleTap(e: MotionEvent): Boolean {
            if (zoom > 1.05f) animatePan(0f, 0f, 1f)
            else animatePan(-(e.x - width / 2f) * 0.6f, -(e.y - artHeight() * 0.72f) * 0.6f, 1.6f)
            return true
        }
    })

    override fun onTouchEvent(event: MotionEvent): Boolean {
        scaleDetector.onTouchEvent(event)
        gestureDetector.onTouchEvent(event)
        if (event.actionMasked == MotionEvent.ACTION_DOWN) parent?.requestDisallowInterceptTouchEvent(true)
        return true
    }

    private fun clampPan() {
        val w = width.toFloat()
        val h = artHeight()
        val limX = (12 + (zoom - 1) * (w / density) * 0.46f) * density
        // 확대하면 지금 위치(산 아래쪽)까지 끌어올릴 수 있게 위 · 아래로 넉넉히 움직여요
        val up = (30 * density) + (zoom - 1) * h * 0.6f
        val down = (24 * density) + (zoom - 1) * h * 0.4f
        // 지금 위치를 보여 주려고 옮긴 곳은 범위를 넓혀서 허용해요 (산길 아래쪽은 원래 보이는 영역 밖에 있어요)
        val f = allowPan
        panX = panX.coerceIn(min(-limX, f?.x ?: 0f), max(limX, f?.x ?: 0f))
        panY = panY.coerceIn(min(-up, f?.y ?: 0f), max(down, f?.y ?: 0f))
    }

    /** 지금 위치를 보여 주려고 옮긴 위치 (끌어서 둘러볼 때도 여기까지는 허용) */
    private var allowPan: PointF? = null

    private fun animatePan(x: Float, y: Float, s: Float, onEnd: (() -> Unit)? = null) {
        panAnimator?.cancel()
        val x0 = panX
        val y0 = panY
        val s0 = zoom
        panAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 450
            interpolator = PathInterpolator(0.2f, 0.8f, 0.2f, 1f)
            addUpdateListener {
                val f = it.animatedValue as Float
                zoom = s0 + (s - s0) * f
                panX = x0 + (x - x0) * f
                panY = y0 + (y - y0) * f
                clampPan()
                invalidate()
            }
            if (onEnd != null) addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) = onEnd()
            })
            start()
        }
    }

    /** 처음 위치 · 크기로 되돌리기 */
    fun resetView() = animatePan(0f, 0f, 1f) { allowPan = null }

    /** 확대 s · 이동 0일 때 산길 위치(progress)가 화면 어디에 오는지 */
    private fun screenOf(progress: Float, s: Float): FloatArray {
        val m = Matrix(baseMatrix).apply { postScale(s, s, width / 2f, artHeight() * 0.72f) }
        val pt = Trail.at(progress.coerceIn(0f, 1f)).copyOf()
        m.mapPoints(pt)
        return pt
    }

    /**
     * 지금 위치가 아래 패널에 가리거나 화면 밖이면, 산을 조금 작게 보여 줘서 지금 위치와 정상이 함께 보이게 해요.
     * (산길 아래쪽은 원래 보이는 영역 밖에 있어서, 위로 끌어올리기만 하면 정상이 잘려요)
     * 가리지 않으면 처음 모습(1배) 그대로. visibleBottom = 아래 패널에 가리지 않는 곳의 아래 끝 (이 뷰 기준 px)
     */
    fun ensureVisible(progress: Float, visibleBottom: Float, force: Boolean = false) {
        if (width == 0 || height == 0 || (!force && zoom > 1.05f)) return
        // 되돌리기 애니메이션을 먼저 멈춰요 (멈출 때 allowPan을 비우므로, 그 뒤에 새로 정해요)
        panAnimator?.cancel()
        val m = 44 * density
        val p0 = screenOf(progress, 1f)
        if (p0[0] in m..(width - m) && p0[1] <= visibleBottom - m) {
            allowPan = null
            animatePan(0f, 0f, 1f)
            return
        }
        val v = FloatArray(9).also { baseMatrix.getValues(it) }
        val k = v[Matrix.MSCALE_X]
        val dot = Trail.at(progress.coerceIn(0f, 1f))
        // 정상 깃발 끝(그림 단위 y ≈ 100)부터 지금 위치까지 세로로 들어가는 크기. 그림 폭보다 작아지지는 않게
        val top = 12 * density
        val minS = (width / (Trail.MW * k)).coerceAtMost(1f)
        val sc = ((visibleBottom - m - top) / ((dot[1] - FLAG_TOP_Y) * k)).coerceIn(minS, 1f)
        // 가로: 그림 밖(빈 곳)이 보이지 않는 범위에서 지금 위치가 왼쪽 30%쯤에 오게
        val viewW = width / (k * sc)
        val left = (dot[0] - viewW * 0.3f).coerceIn(0f, max(0f, Trail.MW - viewW))
        val mm = Matrix(baseMatrix).apply { postScale(sc, sc, width / 2f, artHeight() * 0.72f) }
        val pts = floatArrayOf(left, dot[1])
        mm.mapPoints(pts)
        val tx = -pts[0]
        val ty = visibleBottom - m - pts[1]
        allowPan = PointF(tx, ty)
        animatePan(tx, ty, sc)
    }

    /**
     * 지금 위치(progress)를 확대해서 보여 줘요 (완료할 때 점이 움직이는 모습이 잘 보이도록).
     * visibleBottom = 아래 패널에 가리지 않는 곳의 아래 끝 (이 뷰 기준 px). 점이 그 위 가운데쯤 와요.
     */
    fun focusOn(progress: Float, visibleBottom: Float, scale: Float = 1.7f) {
        if (width == 0 || height == 0) return
        val pt = screenOf(progress, scale)
        val tx = width / 2f - pt[0]
        val ty = visibleBottom * 0.58f - pt[1]
        panAnimator?.cancel()
        allowPan = PointF(tx, ty)
        animatePan(tx, ty, scale)
    }

    private fun hitTest(sx: Float, sy: Float) {
        currentMatrix().invert(inverse)
        val pt = floatArrayOf(sx, sy)
        inverse.mapPoints(pt)
        val (x, y) = pt[0] to pt[1]
        // 깃발 (정상)
        val f = Trail.at(1f)
        if (x in f[0] - 34f..f[0] + 78f && y in f[1] - 116f..f[1] + 16f) {
            onFlagClick?.invoke()
            return
        }
        // 완료한 체크포인트
        val cps = state.slots.size
        val p = state.progress
        for (k in 1 until cps) {
            val t = k.toFloat() / cps
            if (t > p + 1e-6f) continue
            val c = Trail.at(t)
            val r = (8.5f + Trail.roadWidth(c[1]) * 0.15f) * 2.2f
            if (hypot(x - c[0], y - c[1]) <= r) {
                onCheckpointClick?.invoke(k - 1)
                return
            }
        }
    }

    override fun onDetachedFromWindow() {
        panAnimator?.cancel()
        super.onDetachedFromWindow()
    }

    // ---------- 구름 도형 ----------
    private sealed class Part {
        abstract fun draw(c: Canvas, p: Paint)
        abstract fun verticalBounds(): Pair<Float, Float>

        class Circle(val cx: Float, val cy: Float, val r: Float) : Part() {
            override fun draw(c: Canvas, p: Paint) = c.drawCircle(cx, cy, r, p)
            override fun verticalBounds() = (cy - r) to (cy + r)
        }

        class Ellipse(val cx: Float, val cy: Float, val rx: Float, val ry: Float) : Part() {
            override fun draw(c: Canvas, p: Paint) = c.drawOval(cx - rx, cy - ry, cx + rx, cy + ry, p)
            override fun verticalBounds() = (cy - ry) to (cy + ry)
        }

        class Rect(val x: Float, val y: Float, val w: Float, val h: Float, val rx: Float) : Part() {
            override fun draw(c: Canvas, p: Paint) = c.drawRoundRect(x, y, x + w, y + h, rx, rx, p)
            override fun verticalBounds() = y to (y + h)
        }
    }

    private companion object {
        const val BOX_X = 80f
        const val BOX_W = 658f
        const val BOX_H = 1035f
        const val HOME_BOX_Y = -360f
        const val HOME_BOX_DONE_Y = -430f
        const val STEP = 0.005f
        const val ANIM_SEC = 1.5f
        /** 정상 깃발 끝의 높이 (그림 단위 y) */
        const val FLAG_TOP_Y = 100f
        const val CLOUD_TOP = 24f
        private val LAYER_BOUNDS = RectF(-200f, -300f, 1053f, 1100f)
        private val DONE_YELLOW = 0xFFF5E29B.toInt()
        private val MINT = 0xFF69B5A1.toInt()

        private val CLOUD_WIDTH = mapOf("cumulus" to 190f, "stratus" to 210f, "puff" to 100f, "tower" to 170f, "wisp" to 200f, "twin" to 170f)

        private val CLOUD_SHAPES: Map<String, List<Part>> = mapOf(
            "cumulus" to listOf(Part.Circle(40f, 34f, 28f), Part.Circle(82f, 22f, 36f), Part.Circle(126f, 30f, 30f), Part.Circle(162f, 40f, 20f), Part.Rect(12f, 34f, 168f, 26f, 13f)),
            "stratus" to listOf(Part.Ellipse(105f, 44f, 102f, 12f), Part.Circle(62f, 36f, 15f), Part.Circle(96f, 30f, 19f), Part.Circle(134f, 36f, 13f)),
            "puff" to listOf(Part.Circle(30f, 32f, 16f), Part.Circle(52f, 24f, 20f), Part.Circle(74f, 32f, 14f), Part.Rect(14f, 32f, 74f, 14f, 7f)),
            "tower" to listOf(Part.Circle(58f, 44f, 24f), Part.Circle(90f, 24f, 32f), Part.Circle(118f, 16f, 20f), Part.Circle(124f, 42f, 24f), Part.Rect(34f, 44f, 116f, 22f, 11f)),
            "wisp" to listOf(Part.Ellipse(80f, 32f, 70f, 7f), Part.Ellipse(124f, 42f, 72f, 6f), Part.Ellipse(60f, 44f, 40f, 5f)),
            "twin" to listOf(Part.Circle(28f, 34f, 14f), Part.Circle(46f, 28f, 17f), Part.Rect(14f, 32f, 50f, 12f, 6f), Part.Circle(112f, 26f, 18f), Part.Circle(136f, 32f, 14f), Part.Rect(96f, 30f, 56f, 14f, 7f)),
        )

        /** [모양, y, 크기, 한 바퀴 시간(초), 시작 x] */
        private val CLOUD_SET: List<CloudSpec> = listOf(
            CloudSpec("cumulus", 40, 1.0f, 150, 90), CloudSpec("stratus", 150, 0.9f, 190, 560), CloudSpec("puff", 96, 0.9f, 120, 380),
            CloudSpec("tower", 190, 0.85f, 170, 720), CloudSpec("wisp", 18, 0.9f, 210, 300), CloudSpec("twin", 230, 0.8f, 140, 150),
            CloudSpec("puff", 250, 0.7f, 130, 640), CloudSpec("cumulus", -250, 1.1f, 180, 250), CloudSpec("twin", -150, 0.9f, 160, 600),
            CloudSpec("stratus", -60, 1.0f, 200, 140), CloudSpec("puff", -200, 0.8f, 120, 470),
        )
    }

    private data class CloudSpec(val shape: String, val y: Int, val k: Float, val dur: Int, val x0: Int)
}
