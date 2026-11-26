package com.example.oreum.ui.records

import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.oreum.R
import com.example.oreum.data.model.RoundStatus
import com.example.oreum.data.model.StudyRound
import com.example.oreum.databinding.FragmentRecordsBinding
import com.example.oreum.databinding.ItemRoundHistoryBinding
import com.example.oreum.domain.usecase.RoundRecords
import com.example.oreum.ui.round.RoundDetailSheet
import com.example.oreum.util.DateText
import com.example.oreum.util.Season
import com.example.oreum.util.applySystemBarsPadding
import com.example.oreum.util.onChildClick
import com.example.oreum.util.selectChild
import com.google.android.material.chip.ChipGroup
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.roundToInt

/**
 * 기록 (1차 · 프로토타입 기록 화면)
 * - 목표 고르기 (전체 목표 · 목표별) · 날짜별 / 구간별
 * - 페이스 카드: 공부한 날 · 하루 평균 · 항목 완료율 · 최근 7일 막대 · 예상 대비 실제 한 줄
 * - 날짜별: 그날 공부한 구간 · 시간 (누르면 그날 기록) / 구간별: 구간 · 날짜 · 완료 수 (누르면 구간 상세)
 */
class RecordsFragment : Fragment(R.layout.fragment_records) {

    private var _binding: FragmentRecordsBinding? = null
    private val binding get() = checkNotNull(_binding)
    private val viewModel: RecordsViewModel by viewModels()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentRecordsBinding.bind(view)

        binding.bgMountain.setImageResource(Season.of().mountainBackground)
        binding.appbar.applySystemBarsPadding(top = true, bottom = false)

        binding.segView.onChildClick { viewModel.setView(if (it == 0) RecordsView.DAY else RecordsView.ROUND) }
        binding.btnGoal.setOnClickListener {
            if (childFragmentManager.findFragmentByTag(RecordsGoalSheet.TAG) == null) {
                RecordsGoalSheet.newInstance(viewModel.uiState.value.selection.goalId).show(childFragmentManager, RecordsGoalSheet.TAG)
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.refreshDate()
                launch { viewModel.uiState.collect(::render) }
                launch { viewModel.streakState.collect(::renderStreak) }
                while (true) {
                    delay(60_000)
                    viewModel.refreshDate()
                }
            }
        }
    }

    /** 목표 고르기 시트에서 불러요 */
    fun selectGoal(goalId: String?) = viewModel.setGoal(goalId)

    private fun renderStreak(state: RecordsStreakUiState?) {
        binding.streakCard.isVisible = state != null
        if (state == null) return
        val streak = state.streak
        binding.streakDays.text = getString(R.string.records_days_value, streak.days)
        binding.streakMessage.setText(when {
            streak.studiedToday -> R.string.records_streak_studied
            streak.restingToday -> R.string.records_streak_resting
            streak.days == 0 -> R.string.records_streak_first
            else -> R.string.records_streak_hint
        })
        binding.btnRestDay.isVisible = !streak.studiedToday || streak.restingToday
        binding.btnRestDay.setText(if (streak.restingToday) R.string.records_rest_cancel else R.string.records_rest_today)
        binding.btnRestDay.setOnClickListener { viewModel.setRestingToday(state.date, !streak.restingToday) }
    }

    private fun render(state: RecordsUiState) {
        if (!state.loaded) return
        val sel = state.selection
        binding.btnGoal.text = state.goals.firstOrNull { it.id == sel.goalId }?.title ?: getString(R.string.records_all_goals)
        binding.segView.selectChild(if (sel.view == RecordsView.DAY) 0 else 1)

        val days = state.days
        binding.groupEmpty.isVisible = days.isEmpty()
        binding.groupContent.isVisible = days.isNotEmpty()
        if (days.isEmpty()) return

        // 숫자 3개
        binding.kpiDays.text = getString(R.string.records_days_value, days.size)
        binding.kpiAvg.text = DateText.minutes((days.sumOf { it.minutes }.toDouble() / days.size).roundToInt())
        binding.kpiRate.text = state.pace?.let { getString(R.string.round_percent, (it.completion * 100).roundToInt()) } ?: "-"

        renderBars(state.week, state.today)
        binding.textPace.text = paceLine(state.pace)

        // 목록
        binding.list.removeAllViews()
        val all = sel.goalId == null
        if (sel.view == RecordsView.DAY) {
            days.reversed().forEachIndexed { i, d ->
                if (i > 0) binding.list.addView(divider())
                binding.list.addView(dayRow(d, state, all))
            }
        } else {
            state.rounds.forEachIndexed { i, r ->
                if (i > 0) binding.list.addView(divider())
                binding.list.addView(roundRow(r, state, all))
            }
        }
    }

    /** 최근 7일 막대: 오늘은 진하게 */
    private fun renderBars(week: List<Pair<Long, Int>>, today: Long) {
        val bars = binding.bars
        bars.removeAllViews()
        val max = maxOf(60, week.maxOfOrNull { it.second } ?: 0)
        val trackH = dp(56)
        week.forEach { (date, min) ->
            val isToday = date == today
            val day = LocalDate.ofEpochDay(date)
            val col = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
                contentDescription = getString(R.string.records_week_day_time, day.monthValue, day.dayOfMonth, DateText.minutes(min))
            }
            val track = FrameLayout(requireContext())
            val h = if (min > 0) maxOf((trackH * 0.06f).toInt(), (trackH * min.toFloat() / max).toInt()) else (trackH * 0.06f).toInt()
            track.addView(View(requireContext()).apply {
                background = GradientDrawable().apply {
                    cornerRadius = dp(6).toFloat()
                    setColor(if (isToday) 0xFF3E8E7A.toInt() else if (min > 0) 0xFFA8D8C5.toInt() else 0x33285041)
                }
            }, FrameLayout.LayoutParams(dp(22), h, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL))
            col.addView(track, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, trackH))
            col.addView(TextView(requireContext()).apply {
                text = if (isToday) getString(R.string.records_week_today) else day.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.KOREAN)
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                textSize = 13f
                gravity = Gravity.CENTER
                setTextColor(color(if (isToday) R.color.oreum_dark_text else R.color.oreum_ink2))
                if (isToday) setTypeface(typeface, Typeface.BOLD)
            }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(4) })
            bars.addView(col, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        }
    }

    /** 예상 대비 실제 한 줄 (최근 마친 구간 3개 기준) */
    private fun paceLine(p: RoundRecords.Pace?): CharSequence {
        if (p == null) return getString(R.string.records_pace_none)
        val (pre, bold, post) = when {
            p.ratio > 1.1f -> Triple(getString(R.string.records_pace_pre, p.count), getString(R.string.records_pace_more, ((p.ratio - 1) * 100).roundToInt()), getString(R.string.records_pace_more_post))
            p.ratio < 0.9f -> Triple(getString(R.string.records_pace_pre, p.count), getString(R.string.records_pace_fast, ((1 - p.ratio) * 100).roundToInt()), getString(R.string.records_pace_fast_post))
            else -> Triple(getString(R.string.records_pace_ok_pre, p.count), getString(R.string.records_pace_ok), getString(R.string.records_pace_ok_post))
        }
        return SpannableStringBuilder().apply {
            append(pre)
            val s = length
            append(bold)
            setSpan(StyleSpan(Typeface.BOLD), s, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            setSpan(ForegroundColorSpan(color(R.color.oreum_dark_text)), s, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            append(post)
        }
    }

    /** 날짜별 한 줄: 날짜 · 그날 공부한 구간 · 시간 */
    private fun dayRow(d: RoundRecords.Day, state: RecordsUiState, all: Boolean): View {
        val date = LocalDate.ofEpochDay(d.date)
        val row = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(12), 0, dp(12))
            isClickable = true
            setOnClickListener {
                if (childFragmentManager.findFragmentByTag(RecordsDaySheet.TAG) == null) {
                    RecordsDaySheet.newInstance(d.date, state.selection.goalId).show(childFragmentManager, RecordsDaySheet.TAG)
                }
            }
        }
        val dateCol = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
        }
        dateCol.addView(TextView(requireContext()).apply {
            text = date.dayOfMonth.toString()
            textSize = 20f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(color(R.color.oreum_dark_text))
        })
        dateCol.addView(TextView(requireContext()).apply {
            text = getString(R.string.records_day_month, date.monthValue, date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.KOREAN))
            textSize = 13f
            setTextColor(color(R.color.oreum_ink2))
        })
        dateCol.minimumWidth = dp(56)
        row.addView(dateCol, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT))

        val chips = ChipGroup(requireContext()).apply {
            chipSpacingHorizontal = dp(4)
            chipSpacingVertical = dp(4)
        }
        d.rounds.forEach { r ->
            val label = if (all) getString(R.string.records_goal_round, RoundRecords.shortName(state.goals, r.goalId), r.no)
            else getString(R.string.round_no, r.no)
            chips.addView(TextView(requireContext()).apply {
                text = label
                textSize = 12f
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(color(R.color.oreum_ink2))
                setPadding(dp(8), dp(4), dp(8), dp(4))
                background = GradientDrawable().apply {
                    cornerRadius = dp(8).toFloat()
                    setColor(0x1F285041)
                }
            })
        }
        row.addView(chips, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = dp(6) })

        row.addView(TextView(requireContext()).apply {
            text = DateText.minutes(d.minutes)
            textSize = 14.5f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(color(R.color.oreum_dark_text))
        }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { marginStart = dp(8) })
        return row
    }

    /** 구간별 한 줄: 번호 · 주제 · (목표) · 날짜 · 완료 수 / 진행 중 */
    private fun roundRow(r: StudyRound, state: RecordsUiState, all: Boolean): View {
        val hb = ItemRoundHistoryBinding.inflate(layoutInflater, binding.list, false)
        hb.textNo.text = getString(R.string.round_no, r.no)
        hb.textTopic.text = r.displayTopic
        val first = r.dates.firstOrNull() ?: r.startedOn ?: r.createdOn
        hb.textSub.text = buildString {
            if (all) append(RoundRecords.shortName(state.goals, r.goalId)).append(" · ")
            append(DateText.monthDay(LocalDate.ofEpochDay(first)))
            if (r.dates.size > 1) append(getString(R.string.records_round_days, r.dates.size))
        }
        when {
            r.status != RoundStatus.DONE -> {
                hb.textDone.setText(R.string.round_status_active)
                hb.textDone.background = GradientDrawable().apply { cornerRadius = dp(999).toFloat(); setColor(0xFFE3EEFB.toInt()) }
                hb.textDone.setTextColor(0xFF3A6EA8.toInt())
            }
            r.allDone -> {
                hb.textDone.text = getString(R.string.round_done_count, r.doneCount, r.items.size)
                hb.textDone.setBackgroundResource(R.drawable.bg_badge)
                hb.textDone.setTextColor(0xFF2E7A5F.toInt())
            }
            else -> {
                hb.textDone.text = getString(R.string.round_done_count, r.doneCount, r.items.size)
                hb.textDone.setBackgroundResource(R.drawable.bg_dday_hot)
                hb.textDone.setTextColor(color(R.color.oreum_coral_ink))
            }
        }
        hb.root.setOnClickListener {
            if (childFragmentManager.findFragmentByTag(RoundDetailSheet.TAG) == null) {
                RoundDetailSheet.newInstance(r.id).show(childFragmentManager, RoundDetailSheet.TAG)
            }
        }
        return hb.root
    }

    private fun divider() = View(requireContext()).apply {
        setBackgroundColor(0x14285041)
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(1))
    }

    private fun color(res: Int) = requireContext().getColor(res)
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}
