package com.example.oreum.ui.round

import android.content.res.ColorStateList
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.TextUtils
import android.text.style.AbsoluteSizeSpan
import android.text.style.ForegroundColorSpan
import android.view.Gravity
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.core.view.isVisible
import com.example.oreum.R
import com.example.oreum.data.model.RoundStatus
import com.example.oreum.data.model.StudyRound
import com.example.oreum.ui.common.ListSheet
import com.example.oreum.util.DateText
import java.time.LocalDate

/**
 * 구간 상세 (프로토타입 roundDetail): 상태 · 날짜 / n구간 · 단계 이름 / 주제
 * 완료 · 실제 / 예상 · 체감 난이도 → 항목 목록 → 메모 → 닫기
 */
class RoundDetailSheet : ListSheet() {

    override fun build() {
        val r = repository.round(requireArguments().getString(ARG_ROUND).orEmpty())
        if (r == null) {
            dismiss()
            return
        }
        val g = repository.goals.value.firstOrNull { it.id == r.goalId }

        // 상태 칩 · 날짜
        binding.rowHead.isVisible = true
        val done = r.status == RoundStatus.DONE
        binding.rowHead.addView(text(getString(statusText(r)), 11.5f, if (done) 0xFF2E7A5F.toInt() else color(R.color.oreum_ink2), bold = true).apply {
            setPadding(dp(8), dp(3), dp(8), dp(3))
            background = GradientDrawable().apply {
                cornerRadius = dp(999).toFloat()
                setColor(if (done) 0xFFE1F4EC.toInt() else 0xCCFFFFFF.toInt())
            }
        }, wrap())
        binding.rowHead.addView(android.view.View(requireContext()), LinearLayout.LayoutParams(0, 1, 1f))
        binding.rowHead.addView(text(dateSpan(r), 12f, color(R.color.oreum_ink3)), wrap())

        // n구간 · 단계 이름 / 주제
        val stage = g?.roadmap?.getOrNull(r.stage)?.title
        binding.textTitle.text = if (stage.isNullOrBlank()) getString(R.string.round_no, r.no)
        else getString(R.string.round_detail_title, r.no, stage)
        binding.textTitle.textSize = 19f
        binding.textCaption.isVisible = true
        binding.textCaption.text = r.displayTopic

        val list = binding.list

        // 숫자 3칸
        val kpis = LinearLayout(requireContext()).apply { orientation = LinearLayout.HORIZONTAL }
        kpis.addView(kpi(getString(R.string.round_detail_done), getString(R.string.round_done_count, r.doneCount, r.items.size)), LinearLayout.LayoutParams(0, -2, 1f))
        val time = SpannableStringBuilder(DateText.minutes(r.actualMinutes)).apply {
            val s = length
            append(" / ").append(DateText.minutes(r.estimatedMinutes))
            setSpan(AbsoluteSizeSpan(11, true), s, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            setSpan(ForegroundColorSpan(color(R.color.oreum_ink3)), s, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        kpis.addView(kpi(getString(R.string.round_detail_time), time), LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = dp(6) })
        kpis.addView(kpi(getString(R.string.round_detail_difficulty), r.result?.takeIf { done }?.difficulty?.label ?: "-"), LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = dp(6) })
        list.addView(kpis, fill())

        // 항목
        val items = LinearLayout(requireContext()).apply { orientation = LinearLayout.VERTICAL }
        r.items.forEachIndexed { i, item ->
            if (i > 0) items.addView(divider(), LinearLayout.LayoutParams(-1, dp(1)))
            val row = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, dp(10), 0, dp(10))
            }
            row.addView(ImageView(requireContext()).apply {
                setBackgroundResource(if (item.done) R.drawable.bg_home_check_on else R.drawable.bg_home_check_off)
                if (item.done) {
                    setImageResource(R.drawable.ic_check)
                    imageTintList = ColorStateList.valueOf(0xFFFFFFFF.toInt())
                    setPadding(dp(3), dp(3), dp(3), dp(3))
                }
            }, LinearLayout.LayoutParams(dp(22), dp(22)))
            row.addView(text(item.title.removeSuffix(" (이어서)"), 14f, color(if (item.done) R.color.oreum_ink3 else R.color.oreum_dark_text), bold = true).apply {
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
                if (item.done) paintFlags = paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
            }, LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = dp(10) })
            row.addView(text(DateText.minutes(item.minutes), 12.5f, color(R.color.oreum_ink3)), wrap().apply { marginStart = dp(8) })
            items.addView(row)
        }
        list.addView(items, fill(10))

        // 메모 (결과를 제출한 구간만)
        val memo = r.result?.takeIf { done }?.memo.orEmpty()
        if (memo.isNotBlank()) {
            list.addView(text(getString(R.string.next_memo_quote, memo), 12.5f, color(R.color.oreum_ink3)), fill(6))
        }
    }

    private fun kpi(label: String, value: CharSequence) = LinearLayout(requireContext()).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(12), dp(10), dp(12), dp(10))
        background = GradientDrawable().apply {
            cornerRadius = dp(12).toFloat()
            setColor(0xFFE6F2EC.toInt())
        }
        addView(text(label, 11.5f, color(R.color.oreum_ink2)))
        addView(text(value, 15f, color(R.color.oreum_forest_deep), bold = true).apply {
            maxLines = 1
            setTypeface(typeface, Typeface.BOLD)
        }, fill(2))
    }

    private fun statusText(r: StudyRound) = when (r.status) {
        RoundStatus.DONE -> R.string.round_status_done
        RoundStatus.ACTIVE -> R.string.round_status_active
        RoundStatus.READY -> R.string.round_status_ready
    }

    private fun dateSpan(r: StudyRound): String {
        val d = r.dates
        if (d.isEmpty()) return r.doneOn?.let { DateText.monthDay(LocalDate.ofEpochDay(it)) }.orEmpty()
        val first = DateText.monthDay(LocalDate.ofEpochDay(d.first()))
        return if (d.size == 1) first
        else getString(R.string.round_date_span, first, DateText.monthDay(LocalDate.ofEpochDay(d.last())), d.size)
    }

    companion object {
        const val TAG = "RoundDetailSheet"
        private const val ARG_ROUND = "roundId"

        fun newInstance(roundId: String) = RoundDetailSheet().apply {
            arguments = Bundle().apply { putString(ARG_ROUND, roundId) }
        }
    }
}
