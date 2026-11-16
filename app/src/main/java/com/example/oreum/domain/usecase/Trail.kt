package com.example.oreum.domain.usecase

import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/**
 * 홈 산의 등산로 좌표 (프로토타입 TRAIL_CTRL · trailAt과 같은 계산, 순수 Kotlin).
 * 좌표는 산 그림(853 × 858) 기준 단위예요. 진행률 0 = 산 아래 출발점, 1 = 정상 깃발.
 */
object Trail {

    /** 산 그림 크기 (단위) */
    const val MW = 853f
    const val MH = 858f
    private const val OX = 44f
    private const val OY = 100f

    private val CTRL: List<FloatArray> = listOf(
        70 to 955, 200 to 925, 330 to 880, 430 to 840, 491 to 810, 470 to 782, 400 to 756, 310 to 727,
        262 to 697, 276 to 659, 320 to 636, 400 to 615, 470 to 590, 508 to 548, 560 to 524, 625 to 500,
        650 to 475, 630 to 452, 585 to 432, 547 to 405, 560 to 375, 588 to 345, 590 to 315, 585 to 290,
    ).map { (x, y) -> floatArrayOf(x - OX, y - OY) }

    /** 원본 그림의 체크포인트 자리 (진행률 20 · 40 · 60 · 90% 지점에 고정) */
    private val ANCHOR_IDX = intArrayOf(4, 9, 13, 19)

    private val pts: List<FloatArray>
    private val len: FloatArray
    private val idx: IntArray

    init {
        // Catmull-Rom 곡선으로 조절점 사이를 12칸씩 나눠요
        val out = mutableListOf<FloatArray>()
        val index = IntArray(CTRL.size)
        for (i in 0 until CTRL.size - 1) {
            val p0 = CTRL[max(0, i - 1)]
            val p1 = CTRL[i]
            val p2 = CTRL[i + 1]
            val p3 = CTRL[min(CTRL.size - 1, i + 2)]
            index[i] = out.size
            for (k in 0 until 12) {
                val t = k / 12f
                val t2 = t * t
                val t3 = t2 * t
                out += FloatArray(2) { d ->
                    0.5f * (2 * p1[d] + (-p0[d] + p2[d]) * t + (2 * p0[d] - 5 * p1[d] + 4 * p2[d] - p3[d]) * t2 +
                        (-p0[d] + 3 * p1[d] - 3 * p2[d] + p3[d]) * t3)
                }
            }
        }
        index[CTRL.size - 1] = out.size
        out += CTRL.last()
        pts = out
        idx = index
        len = FloatArray(out.size)
        for (i in 1 until out.size) len[i] = len[i - 1] + hypot(out[i][0] - out[i - 1][0], out[i][1] - out[i - 1][1])
    }

    private val anchors: List<Pair<Float, Int>> = listOf(
        0f to 0,
        0.2f to idx[ANCHOR_IDX[0]],
        0.4f to idx[ANCHOR_IDX[1]],
        0.6f to idx[ANCHOR_IDX[2]],
        0.9f to idx[ANCHOR_IDX[3]],
        1f to pts.size - 1,
    )

    /** 진행률 p (0 ~ 1) → 길 위의 위치 (x, y) */
    fun at(progress: Float): FloatArray {
        val p = progress.coerceIn(0f, 1f)
        var j = 0
        while (j < anchors.size - 2 && p > anchors[j + 1].first) j++
        val (pa, ia) = anchors[j]
        val (pb, ib) = anchors[j + 1]
        val target = len[ia] + (len[ib] - len[ia]) * ((p - pa) / (if (pb - pa == 0f) 1f else pb - pa))
        var i = ia
        while (i < ib && len[i + 1] < target) i++
        val seg = len[min(i + 1, len.size - 1)] - len[i]
        val k = (target - len[i]) / (if (seg == 0f) 1f else seg)
        val a = pts[i]
        val b = pts[min(i + 1, pts.size - 1)]
        return floatArrayOf(a[0] + (b[0] - a[0]) * k, a[1] + (b[1] - a[1]) * k)
    }

    /** 실제 길 폭 (아래는 넓고 위로 갈수록 좁아짐) */
    fun roadWidth(y: Float): Float = 9f + max(0f, y - 190f) / (MH - 190f) * 34f
}
