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
    }
}
