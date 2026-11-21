package com.example.oreum.ui.common

import androidx.annotation.StringRes
import androidx.core.view.isVisible
import com.example.oreum.R
import com.example.oreum.data.repository.AiFailure
import com.example.oreum.data.repository.AiRequests
import com.example.oreum.databinding.ViewAiStateBinding

/** AI 요청 상태 화면 (만드는 중 · 실패 + 다시 시도). state = null이면 숨겨요 */
object AiStateUi {

    @StringRes
    fun reasonText(reason: AiFailure): Int = when (reason) {
        AiFailure.NETWORK -> R.string.ai_fail_network
        AiFailure.BUSY -> R.string.ai_fail_busy
        AiFailure.DAILY_LIMIT -> R.string.ai_fail_daily
        AiFailure.SETUP -> R.string.ai_fail_setup
        AiFailure.BAD_RESPONSE -> R.string.ai_fail_bad
        AiFailure.UNKNOWN -> R.string.ai_fail_unknown
    }

    fun render(
        b: ViewAiStateBinding,
        state: AiRequests.State?,
        @StringRes loadingTitle: Int,
        @StringRes loadingBody: Int,
        onRetry: () -> Unit,
        onLater: () -> Unit,
    ) {
        b.root.isVisible = state != null
        val failed = state is AiRequests.State.Failed
        b.progress.isVisible = state == AiRequests.State.Running
        b.iconFail.isVisible = failed
        b.btnAiRetry.isVisible = failed
        when (state) {
            null -> Unit
            AiRequests.State.Running -> {
                b.textAiTitle.setText(loadingTitle)
                b.textAiBody.setText(loadingBody)
            }
            is AiRequests.State.Failed -> {
                b.textAiTitle.setText(R.string.ai_fail_title)
                b.textAiBody.setText(reasonText(state.reason))
            }
        }
        b.btnAiRetry.setOnClickListener { onRetry() }
        b.btnAiLater.setOnClickListener { onLater() }

        // 분당 한도: 1분이 지나기 전에는 '다시 시도'를 막고 남은 시간을 보여 줘요 (또 보내도 다시 막히기만 해요)
        b.btnAiRetry.removeCallbacks(b.btnAiRetry.getTag(R.id.btn_ai_retry) as? Runnable)
        val waitUntil = (state as? AiRequests.State.Failed)?.takeIf { it.reason == AiFailure.BUSY }?.let { it.at + BUSY_WAIT_MS }
        if (waitUntil == null) {
            b.btnAiRetry.isEnabled = true
            b.btnAiRetry.setText(R.string.ai_retry)
            return
        }
        val tick = object : Runnable {
            override fun run() {
                val left = ((waitUntil - System.currentTimeMillis() + 999) / 1000).toInt()
                if (left <= 0) {
                    b.btnAiRetry.isEnabled = true
                    b.btnAiRetry.setText(R.string.ai_retry)
                } else {
                    b.btnAiRetry.isEnabled = false
                    b.btnAiRetry.text = b.root.context.getString(R.string.ai_retry_wait, left)
                    b.btnAiRetry.postDelayed(this, 1000)
                }
            }
        }
        b.btnAiRetry.setTag(R.id.btn_ai_retry, tick)
        tick.run()
    }

    /** 분당 한도에 걸렸을 때 기다리는 시간 */
    private const val BUSY_WAIT_MS = 60_000L
}
