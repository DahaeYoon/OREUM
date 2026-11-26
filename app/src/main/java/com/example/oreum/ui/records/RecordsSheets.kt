package com.example.oreum.ui.records

import android.content.res.ColorStateList
import android.os.Bundle
import android.text.TextUtils
import android.view.Gravity
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.core.view.isVisible
import com.example.oreum.R
import com.example.oreum.ui.common.ListSheet
import com.example.oreum.util.DateText
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

/** 어떤 목표의 기록을 볼까요? (전체 목표 · 목표별) */
class RecordsGoalSheet : ListSheet() {

    private val selected: String? get() = requireArguments().getString(ARG_GOAL)

    override fun build() {
        binding.textTitle.setText(R.string.records_pick_title)
        binding.btnClose.isVisible = false
        val card = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundResource(R.drawable.bg_home_card14)
            setPadding(dp(14), dp(2), dp(14), dp(2))
        }
        val options = listOf<Pair<String?, String>>(null to getString(R.string.records_all_goals)) +
            repository.goals.value.map { it.id to it.title }
        options.forEachIndexed { i, (id, title) ->
            if (i > 0) card.addView(divider(), LinearLayout.LayoutParams(-1, dp(1)))
            val on = id == selected
            val row = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                minimumHeight = dp(48)
                setOnClickListener {
                    (parentFragment as? RecordsFragment)?.selectGoal(id)
                    dismiss()
                }
            }
            row.addView(text(title, 15f, color(if (on) R.color.oreum_forest_deep else R.color.oreum_dark_text), bold = true).apply {
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
            }, LinearLayout.LayoutParams(0, -2, 1f))
            if (on) {
                row.addView(ImageView(requireContext()).apply {
                    setImageResource(R.drawable.ic_check)
                    imageTintList = ColorStateList.valueOf(color(R.color.oreum_forest_deep))
                }, LinearLayout.LayoutParams(dp(16), dp(16)))
            }
            card.addView(row)
        }
        binding.list.addView(card)
    }

    companion object {
        const val TAG = "RecordsGoalSheet"
        private const val ARG_GOAL = "goalId"

        fun newInstance(goalId: String?) = RecordsGoalSheet().apply {
            arguments = Bundle().apply { putString(ARG_GOAL, goalId) }
        }
    }
}

/** 그날 학습 기록: 공부한 항목 · 목표 · 구간 · 시간 (실제로 공부한 날짜 기준) */
class RecordsDaySheet : ListSheet() {

    override fun build() {
        val date = requireArguments().getLong(ARG_DATE)
        val goalId = requireArguments().getString(ARG_GOAL)
        val day = LocalDate.ofEpochDay(date)
        val sessions = repository.sessions.value.filter { it.date == date && (goalId == null || it.goalId == goalId) && it.minutes > 0 }
        val rounds = repository.rounds.value.associateBy { it.id }
        val goals = repository.goals.value.associateBy { it.id }

        binding.textTitle.text = getString(
            R.string.records_day_title,
            day.monthValue,
            day.dayOfMonth,
            day.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.KOREAN),
        )
        binding.textCaption.isVisible = true
        binding.textCaption.text = getString(R.string.records_day_cap2, DateText.minutes(sessions.sumOf { it.minutes }))

        val card = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundResource(R.drawable.bg_home_card14)
            setPadding(dp(14), dp(2), dp(14), dp(2))
        }
        sessions.forEachIndexed { i, s ->
            if (i > 0) card.addView(divider(), LinearLayout.LayoutParams(-1, dp(1)))
            val r = rounds[s.roundId]
            val item = r?.items?.firstOrNull { it.id == s.itemId }
            val row = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, dp(10), 0, dp(10))
            }
            val texts = LinearLayout(requireContext()).apply { orientation = LinearLayout.VERTICAL }
            texts.addView(text(item?.title?.removeSuffix(" (이어서)") ?: "-", 14.5f, color(R.color.oreum_dark_text), bold = true).apply {
                maxLines = 2
                ellipsize = TextUtils.TruncateAt.END
            })
            texts.addView(
                text(getString(R.string.records_day_item_cap, goals[s.goalId]?.title.orEmpty(), r?.no ?: 0), 13f, color(R.color.oreum_ink2)),
                fill(2),
            )
            row.addView(texts, LinearLayout.LayoutParams(0, -2, 1f))
            row.addView(text(DateText.minutes(s.minutes), 14f, color(R.color.oreum_ink2)), wrap().apply { marginStart = dp(8) })
            card.addView(row)
        }
        binding.list.addView(card)
    }

    companion object {
        const val TAG = "RecordsDaySheet"
        private const val ARG_DATE = "date"
        private const val ARG_GOAL = "goalId"

        fun newInstance(date: Long, goalId: String?) = RecordsDaySheet().apply {
            arguments = Bundle().apply {
                putLong(ARG_DATE, date)
                putString(ARG_GOAL, goalId)
            }
        }
    }
}
