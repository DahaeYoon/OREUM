package com.example.oreum.ui.common

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.view.View
import androidx.core.graphics.createBitmap

/**
 * 프로토타입의 유리 팝업(.glass-sheet)을 그대로 그리는 배경.
 * CSS: background rgba(255,255,255,.45) + backdrop-filter: blur(22px) saturate(1.15) + 뒤 화면 12% 어둡게
 *
 * 안드로이드 기기마다 '창 뒤 흐림' 지원이 달라서, 팝업이 열릴 때 뒤 화면을 한 번 찍어(GlassBackdrop)
 * 직접 흐리게 만든 뒤 팝업 자리에 맞춰 그려요. 그래서 어느 기기에서나 같은 모습이에요.
 */
class GlassBackgroundDrawable(
    private val host: View,
    private val backdrop: GlassBackdrop?,
    private val radiusPx: Float,
    private val strokePx: Float,
    /** true = 위쪽만 둥글게 (아래에서 올라오는 팝업), false = 네 모서리 모두 (가운데 팝업) */
    private val topOnly: Boolean = true,
    /** 유리 색 (기본 흰 45%) */
    private val fillColor: Int = 0x73FFFFFF,
    /** 흐린 뒤 화면 위에 덮는 어둡게 (기본 12%) */
    private val dimColor: Int = 0x1F1E322D,
    /** 테두리 색 (기본 흰 80%) */
    private val strokeColor: Int = 0xCCFFFFFF.toInt(),
    /** false면 아래쪽 테두리를 그리지 않아요 (하단 탭과 이어지는 카드) */
    private val strokeBottom: Boolean = true,
) : Drawable() {

    private val bitmapPaint = Paint(Paint.FILTER_BITMAP_FLAG).apply {
        // saturate(1.15)
        colorFilter = ColorMatrixColorFilter(ColorMatrix().apply { setSaturation(1.15f) })
    }
    private val dimPaint = Paint().apply { color = dimColor }
    private val glassPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = fillColor }
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = strokePx
        color = strokeColor
    }
    private val path = Path()
    private val rect = RectF()
    private val location = IntArray(2)

    override fun draw(canvas: Canvas) {
        val b = bounds
        rect.set(b.left.toFloat(), b.top.toFloat(), b.right.toFloat(), b.bottom.toFloat())
        path.reset()
        path.addRoundRect(rect, radii(), Path.Direction.CW)

        canvas.save()
        canvas.clipPath(path)
        backdrop?.let { bd ->
            host.getLocationOnScreen(location)
            canvas.save()
            // 화면 전체 사진을 팝업 위치만큼 옮겨서, 팝업 바로 뒤에 있던 부분이 보이게
            canvas.translate(-location[0].toFloat() + bd.offsetX, -location[1].toFloat() + bd.offsetY)
            canvas.scale(bd.scale, bd.scale)
            canvas.drawBitmap(bd.bitmap, 0f, 0f, bitmapPaint)
            canvas.restore()
            canvas.drawRect(rect, dimPaint)
        }
        canvas.drawRect(rect, glassPaint)
        canvas.restore()

        val half = strokePx / 2
        rect.inset(half, half)
        if (!strokeBottom) rect.bottom += strokePx * 2
        path.reset()
        path.addRoundRect(rect, radii(), Path.Direction.CW)
        canvas.save()
        canvas.clipRect(bounds)
        canvas.drawPath(path, strokePaint)
        canvas.restore()
    }

    private fun radii(): FloatArray {
        val bottom = if (topOnly) 0f else radiusPx
        return floatArrayOf(radiusPx, radiusPx, radiusPx, radiusPx, bottom, bottom, bottom, bottom)
    }

    override fun setAlpha(alpha: Int) {}
    override fun setColorFilter(colorFilter: ColorFilter?) {}

    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}

/** 팝업 뒤 화면을 작게 찍어 흐리게 만든 사진 (화면 좌표 기준) */
class GlassBackdrop(val bitmap: Bitmap, val scale: Float, val offsetX: Float, val offsetY: Float) {

    companion object {
        /** 화면을 1/4 크기로 찍어 blur(22dp)만큼 흐리게 해요 */
        fun capture(root: View, blurDp: Float = 22f): GlassBackdrop? {
            if (root.width == 0 || root.height == 0) return null
            val down = 4
            val w = (root.width / down).coerceAtLeast(1)
            val h = (root.height / down).coerceAtLeast(1)
            val bmp = createBitmap(w, h)
            val canvas = Canvas(bmp)
            canvas.scale(1f / down, 1f / down)
            root.draw(canvas)
            val radius = (blurDp * root.resources.displayMetrics.density / down).toInt().coerceAtLeast(1)
            BoxBlur.blur(bmp, radius)
            val loc = IntArray(2)
            root.getLocationOnScreen(loc)
            return GlassBackdrop(bmp, down.toFloat(), loc[0].toFloat(), loc[1].toFloat())
        }
    }
}

/** 가우스 흐림에 가까운 박스 흐림 3번 (작은 사진이라 금방 끝나요) */
private object BoxBlur {
    fun blur(bitmap: Bitmap, radius: Int) {
        val w = bitmap.width
        val h = bitmap.height
        val src = IntArray(w * h)
        bitmap.getPixels(src, 0, w, 0, 0, w, h)
        val tmp = IntArray(w * h)
        repeat(3) {
            pass(src, tmp, w, h, radius, horizontal = true)
            pass(tmp, src, w, h, radius, horizontal = false)
        }
        bitmap.setPixels(src, 0, w, 0, 0, w, h)
    }

    private fun pass(input: IntArray, output: IntArray, w: Int, h: Int, r: Int, horizontal: Boolean) {
        val lines = if (horizontal) h else w
        val len = if (horizontal) w else h
        val div = r * 2 + 1
        for (line in 0 until lines) {
            var sa = 0; var sr = 0; var sg = 0; var sb = 0
            fun idx(i: Int): Int {
                val c = i.coerceIn(0, len - 1)
                return if (horizontal) line * w + c else c * w + line
            }
            for (i in -r..r) {
                val p = input[idx(i)]
                sa += p ushr 24; sr += (p shr 16) and 0xFF; sg += (p shr 8) and 0xFF; sb += p and 0xFF
            }
            for (i in 0 until len) {
                output[idx(i)] = ((sa / div) shl 24) or ((sr / div) shl 16) or ((sg / div) shl 8) or (sb / div)
                val add = input[idx(i + r + 1)]
                val sub = input[idx(i - r)]
                sa += (add ushr 24) - (sub ushr 24)
                sr += ((add shr 16) and 0xFF) - ((sub shr 16) and 0xFF)
                sg += ((add shr 8) and 0xFF) - ((sub shr 8) and 0xFF)
                sb += (add and 0xFF) - (sub and 0xFF)
            }
        }
    }
}
