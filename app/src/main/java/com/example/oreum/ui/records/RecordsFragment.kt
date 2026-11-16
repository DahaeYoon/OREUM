package com.example.oreum.ui.records

import android.content.res.ColorStateList
import android.graphics.Typeface
import android.os.Bundle
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.TextUtils
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import android.text.style.StyleSpan
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.ColorInt
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.example.oreum.R
import com.example.oreum.databinding.FragmentRecordsBinding
import com.example.oreum.domain.usecase.RecordsAnalysis
import com.example.oreum.ui.calendar.DayLogSheet
import com.example.oreum.ui.calendar.RecoveryOpener
import com.example.oreum.util.DateText
import com.example.oreum.util.Season
import com.example.oreum.util.applySystemBarsPadding
import com.example.oreum.util.onChildClick
import com.example.oreum.util.selectChild
import com.google.android.material.chip.ChipGroup
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

/**
 * STEP 6 기록 (프로토타입 기록 화면)
 * 목표 칩 · 일일/주간 → 요약 3칸 · 계획 대비 실제 그래프 · AI 학습 페이스(시뮬레이션) · 반복되는 미완료 원인 · 복구 전후 · 일일 기록.
 * 일일 기록 줄을 누르면 그날 학습 결과 기록을 다시 열어 고칠 수 있어요.
 */
class RecordsFragment : Fragment(R.layout.fragment_records), RecoveryOpener {

    private var _binding: FragmentRecordsBinding? = null
    private val binding get() = checkNotNull(_binding)
    private val viewModel: RecordsViewModel by viewModels()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentRecordsBinding.bind(view)

        binding.bgMountain.setImageResource(Season.of().mountainBackground)
        binding.appbar.applySystemBarsPadding(top = true, bottom = false)

        binding.segView.onChildClick { viewModel.setView(if (it == 0) RecordsView.DAY else RecordsView.WEEK) }
        binding.btnApplyPace.setOnClickListener {
            if (viewModel.applyPace()) toast(getString(R.string.toast_records_pace_applied))
        }
        binding.btnClearPace.setOnClickListener {
            viewModel.clearPace()
            toast(getString(R.string.toast_records_pace_cleared))
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect(::render)
            }
        }
    }

    private fun render(state: RecordsUiState) {
        val sel = state.selection
        renderGoalChips(state)
        binding.segView.selectChild(if (sel.view == RecordsView.DAY) 0 else 1)

        val days = state.days
        binding.groupEmpty.isVisible = days.isEmpty()
        binding.groupContent.isVisible = days.isNotEmpty()
        if (days.isEmpty()) return

        val today = LocalDate.now()
        val weekStart = RecordsAnalysis.weekStart(today).toEpochDay()
        val thisWeek = days.filter { it.date >= weekStart }
        val dayView = sel.view == RecordsView.DAY

        // 요약 3칸
        binding.kpiRateLabel.setText(if (dayView) R.string.records_kpi_rate_day else R.string.records_kpi_rate_week)
        binding.kpiRate.text = percent(if (dayView) days.last().rate else RecordsAnalysis.rate(thisWeek))
        binding.kpiAvg.text = DateText.minutes((days.sumOf { it.actual }.toDouble() / days.size).roundToInt())
        val kl = if (dayView) days.takeLast(7) else thisWeek
        binding.kpiSumLabel.setText(if (dayView) R.string.records_kpi_sum_day else R.string.records_kpi_sum_week)
        binding.kpiSum.text = SpannableStringBuilder().apply {
            append("${(kl.sumOf { it.actual } / 60.0).roundToInt()} / ${(kl.sumOf { it.planned } / 60.0).roundToInt()}")
            val s = length
            append(getString(R.string.records_hours_unit))
            setSpan(RelativeSizeSpan(10f / 14f), s, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            setSpan(ForegroundColorSpan(color(R.color.oreum_ink3)), s, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }

        // 그래프
        binding.barsDay.isVisible = dayView
        binding.barsWeek.isVisible = !dayView
        if (dayView) {
            val last = days.takeLast(7)
            binding.textChartTitle.text = getString(R.string.records_chart_day, last.size)
            renderDayBars(last)
            binding.textChartNote.isVisible = last.any { it.hasRecovery }
        } else {
            binding.textChartTitle.setText(R.string.records_chart_week)
            renderWeekBars(state.weeks)
            binding.textChartNote.isVisible = false
        }

        renderPace(state)
        renderReasons(state.reasons)
        renderRecoveries(state.recoveries)
        binding.cardDays.isVisible = dayView
        if (dayView) renderDays(state)
    }

    private fun renderGoalChips(state: RecordsUiState) {
        val chips = binding.chipsGoal
        val entries = listOf<Pair<String?, String>>(null to getString(R.string.records_filter_all)) + state.goals.map { it.id to it.title }
        chips.removeAllViews()
        entries.forEachIndexed { i, (id, title) ->
            val chip = layoutInflater.inflate(R.layout.item_filter_chip, chips, false) as TextView
            chip.text = title
            chip.isSelected = id == state.selection.goalId
            chip.setOnClickListener { viewModel.setGoal(id) }
            chips.addView(chip, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dp(30)).apply {
                if (i > 0) marginStart = dp(6)
            })
        }
    }

    /** 일일: 날마다 계획(연한 막대) 안에 실제(진한 막대) + 달성률 · 날짜 */
    private fun renderDayBars(list: List<RecordsAnalysis.DayRecord>) {
        val box = binding.barsDay
        box.removeAllViews()
        val max = maxOf(1, list.maxOf { maxOf(it.planned, it.actual) })
        val area = dp(122)
        list.forEachIndexed { i, r ->
            val col = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
            }
            val bar = FrameLayout(requireContext())
            bar.addView(View(requireContext()).apply { setBackgroundResource(R.drawable.bg_bar_plan) },
                FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, (area * r.planned / max)).apply { gravity = Gravity.BOTTOM })
            bar.addView(View(requireContext()).apply { setBackgroundResource(R.drawable.bg_bar_actual) },
                FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, (area * r.actual / max)).apply {
                    gravity = Gravity.BOTTOM
                    marginStart = dp(7); marginEnd = dp(7)
                })
            col.addView(bar, LinearLayout.LayoutParams(dp(30), 0, 1f))
            col.addView(text(percent(r.rate), 10f, color(R.color.oreum_dark_text), bold = true),
                LinearLayout.LayoutParams(-2, -2).apply { topMargin = dp(3) })
            val date = SpannableStringBuilder()
            if (r.hasRecovery) {
                date.append("● ")
                date.setSpan(ForegroundColorSpan(MINT), 0, date.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            date.append(DateText.monthDay(LocalDate.ofEpochDay(r.date)))
            col.addView(text(date, 9.5f, color(R.color.oreum_ink3)), LinearLayout.LayoutParams(-2, -2).apply { topMargin = dp(3) })
            box.addView(col, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f).apply {
                if (i > 0) marginStart = dp(6)
            })
        }
    }

    /** 주간: 주마다 가로 막대 (계획 바탕 위 실제) */
    private fun renderWeekBars(weeks: List<RecordsAnalysis.WeekBar>) {
        val box = binding.barsWeek
        box.removeAllViews()
        val max = maxOf(1, weeks.maxOfOrNull { maxOf(it.planned, it.actual) } ?: 1)
        weeks.forEach { w ->
            val label = getString(R.string.records_week_label, DateText.monthDay(LocalDate.ofEpochDay(w.start)))
            box.addView(hbarRow(label, color(R.color.oreum_dark_text)) { track ->
                track.addView(View(requireContext()).apply { setBackgroundResource(R.drawable.bg_hbar_plan) },
                    FrameLayout.LayoutParams(0, FrameLayout.LayoutParams.MATCH_PARENT))
                track.addView(View(requireContext()).apply { setBackgroundResource(R.drawable.bg_hbar_actual) },
                    FrameLayout.LayoutParams(0, FrameLayout.LayoutParams.MATCH_PARENT).apply { topMargin = dp(3); bottomMargin = dp(3) })
                track.post {
                    val width = track.width
                    (track.getChildAt(0).layoutParams).width = width * w.planned / max
                    (track.getChildAt(1).layoutParams).width = width * w.actual / max
                    track.requestLayout()
                }
                percent(w.rate)
            })
        }
    }

    private fun renderPace(state: RecordsUiState) {
        val pace = state.pace
        val ink = color(R.color.oreum_dark_text)
        if (!pace.enough) {
            binding.textPaceTitle.text = getString(R.string.records_pace_short_title, pace.days)
            binding.textPaceBody.setText(R.string.records_pace_short_body)
            binding.boxSuggest.isVisible = false
            binding.btnApplyPace.isVisible = false
            binding.rowPaceApplied.isVisible = false
            binding.textPaceCaption.isVisible = false
            return
        }
        binding.textPaceTitle.setText(R.string.records_pace_title)
        binding.textPaceBody.text = SpannableStringBuilder().apply {
            append(getString(R.string.records_pace_body_1))
            bold(DateText.minutes(pace.avgPlanned), ink)
            append(getString(R.string.records_pace_body_2))
            bold(DateText.minutes(pace.avgActual), ink)
            append(getString(R.string.records_pace_body_3, percentInt(pace.rate)))
            pace.prevRate?.let { append(getString(R.string.records_pace_body_prev, percentInt(it))) }
        }
        binding.boxSuggest.isVisible = true
        binding.iconSuggest.isVisible = pace.low
        binding.textSuggest.text = if (pace.low) {
            SpannableStringBuilder().apply {
                append(getString(R.string.records_pace_suggest_1, (pace.load * 100).roundToInt()))
                bold(getString(R.string.records_pace_suggest_session, pace.session), 0xFF2F5F55.toInt())
                append(getString(R.string.records_pace_suggest_2))
            }
        } else {
            getString(R.string.records_pace_good)
        }
        val applied = state.appliedLoad != null
        binding.btnApplyPace.isVisible = pace.low && !applied
        binding.rowPaceApplied.isVisible = pace.low && applied
        if (applied) {
            binding.textPaceApplied.text = getString(
                R.string.records_pace_applied,
                ((state.appliedLoad ?: 1f) * 100).roundToInt(),
                state.appliedSession ?: pace.session,
            )
        }
        binding.textPaceCaption.isVisible = true
        binding.textPaceCaption.text = getString(R.string.records_pace_caption, pace.days)
    }

    private fun renderReasons(reasons: List<RecordsAnalysis.ReasonCount>) {
        val box = binding.listReasons
        box.removeAllViews()
        if (reasons.isEmpty()) {
            box.addView(text(getString(R.string.records_reasons_none), 13f, color(R.color.oreum_ink2)))
            return
        }
        val max = reasons.first().count
        reasons.forEach { rc ->
            box.addView(hbarRow(rc.reason.label, color(R.color.oreum_dark_text)) { track ->
                track.addView(View(requireContext()).apply { setBackgroundResource(R.drawable.bg_hbar_miss) },
                    FrameLayout.LayoutParams(0, FrameLayout.LayoutParams.MATCH_PARENT))
                track.post {
                    track.getChildAt(0).layoutParams.width = track.width * rc.count / max
                    track.requestLayout()
                }
                getString(R.string.records_times, rc.count)
            })
        }
    }

    /** 가로 막대 한 줄: 이름(96dp) · 막대 · 값(44dp) */
    private fun hbarRow(label: String, @ColorInt labelColor: Int, fill: (FrameLayout) -> String): View {
        val row = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(4), 0, dp(4))
        }
        row.addView(text(label, 12f, labelColor).apply { maxLines = 1; ellipsize = TextUtils.TruncateAt.END },
            LinearLayout.LayoutParams(dp(96), -2))
        val track = FrameLayout(requireContext()).apply { setBackgroundResource(R.drawable.bg_hbar_track) }
        val value = fill(track)
        row.addView(track, LinearLayout.LayoutParams(0, dp(12), 1f).apply { marginStart = dp(8); marginEnd = dp(8) })
        row.addView(text(value, 12f, color(R.color.oreum_dark_text), bold = true).apply { gravity = Gravity.END },
            LinearLayout.LayoutParams(dp(44), -2))
        return row
    }

    private fun renderRecoveries(list: List<RecordsAnalysis.RecoveryResult>) {
        binding.cardRecovery.isVisible = list.isNotEmpty()
        val box = binding.listRecovery
        box.removeAllViews()
        list.forEachIndexed { i, h ->
            val item = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(0, dp(8), 0, dp(8))
            }
            if (i > 0) box.addView(View(requireContext()).apply { setBackgroundColor(0xFFF0F2F0.toInt()) }, LinearLayout.LayoutParams(-1, dp(1)))
            val reasons = h.reasons.map { it.short }.distinct().joinToString(", ")
            item.addView(text(getString(R.string.records_recovery_applied, DateText.monthDay(LocalDate.ofEpochDay(h.appliedOn)), reasons), 13f, color(R.color.oreum_ink2)))
            val ba = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            val ink = color(R.color.oreum_dark_text)
            ba.addView(text(SpannableStringBuilder(getString(R.string.records_recovery_before)).apply { bold(percent(h.before), ink) }, 13f, ink))
            ba.addView(text("→", 13f, color(R.color.oreum_ink3)), LinearLayout.LayoutParams(-2, -2).apply { marginStart = dp(10); marginEnd = dp(10) })
            val afterColor = when {
                h.after == null -> ink
                h.after >= h.before -> GREEN
                else -> CORAL
            }
            ba.addView(text(SpannableStringBuilder(getString(R.string.records_recovery_after)).apply {
                bold(h.after?.let { percent(it) } ?: getString(R.string.records_recovery_pending), afterColor)
            }, 13f, ink))
            item.addView(ba, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(3) })
            box.addView(item)
        }
    }

    /** 일일 기록 (최근 10일, 최신 순) */
    private fun renderDays(state: RecordsUiState) {
        val box = binding.listDays
        box.removeAllViews()
        state.days.reversed().take(10).forEachIndexed { i, r ->
            if (i > 0) box.addView(View(requireContext()).apply { setBackgroundColor(LINE) }, LinearLayout.LayoutParams(-1, dp(1)))
            val log = state.logs.firstOrNull { it.date == r.date }
            val row = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                minimumHeight = dp(58)
                setPadding(0, dp(8), 0, dp(8))
                setBackgroundResource(rippleRes())
                setOnClickListener { DayLogSheet.newInstance(r.date).show(childFragmentManager, DayLogSheet.TAG) }
            }
            val main = LinearLayout(requireContext()).apply { orientation = LinearLayout.VERTICAL }

            val head = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            head.addView(text(LocalDate.ofEpochDay(r.date).format(DAY_FORMAT), 14.5f, color(R.color.oreum_dark_text), bold = true))
            if (r.hasRecovery) {
                head.addView(smallTag(getString(R.string.records_rec_tag), 0xFFE3F1EC.toInt(), 0xFF3E7F86.toInt()),
                    LinearLayout.LayoutParams(-2, -2).apply { marginStart = dp(4) })
            }
            main.addView(head)

            val reasons = r.reasons.map { it.short }.distinct()
            val cap = getString(R.string.records_day_cap, DateText.minutes(r.planned), DateText.minutes(r.actual)) +
                if (reasons.isEmpty()) "" else " · " + reasons.joinToString(", ")
            main.addView(text(cap, 12f, color(R.color.oreum_ink2)).apply { maxLines = 1; ellipsize = TextUtils.TruncateAt.END },
                LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(2) })

            if (log != null && log.tags.isNotEmpty()) {
                val tags = ChipGroup(requireContext()).apply {
                    chipSpacingHorizontal = dp(3)
                    chipSpacingVertical = dp(3)
                }
                log.tags.forEach { tags.addView(smallTag(it.label, 0xCCFFFFFF.toInt(), color(R.color.oreum_ink2))) }
                main.addView(tags, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(4) })
            }
            if (log != null && log.memo.isNotBlank()) {
                main.addView(text(getString(R.string.rec_memo_quote, log.memo), 12f, color(R.color.oreum_ink2)),
                    LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(2) })
            }
            row.addView(main, LinearLayout.LayoutParams(0, -2, 1f))

            val (bg, fg) = when {
                r.rate >= 0.8f -> 0xFFE3F3EE.toInt() to 0xFF3E8E7A.toInt()
                r.rate >= 0.5f -> 0xFFE4F0F5.toInt() to 0xFF2F6585.toInt()
                else -> 0xFFFBE5DE.toInt() to 0xFFB5492E.toInt()
            }
            row.addView(text(percent(r.rate), 11f, fg, bold = true).apply {
                gravity = Gravity.CENTER
                setPadding(dp(8), 0, dp(8), 0)
                setBackgroundResource(R.drawable.bg_pill)
                backgroundTintList = ColorStateList.valueOf(bg)
            }, LinearLayout.LayoutParams(-2, dp(22)).apply { marginStart = dp(12) })
            box.addView(row)
        }
    }

    private fun smallTag(label: String, @ColorInt bg: Int, @ColorInt fg: Int) =
        text(label, 10.5f, fg, bold = true).apply {
            setPadding(dp(6), dp(1), dp(6), dp(1))
            setBackgroundResource(R.drawable.bg_tag_small)
            backgroundTintList = ColorStateList.valueOf(bg)
        }

    private fun rippleRes(): Int {
        val out = android.util.TypedValue()
        requireContext().theme.resolveAttribute(android.R.attr.selectableItemBackground, out, true)
        return out.resourceId
    }

    /** 학습 결과 기록 저장 뒤 '미완료 n개 · AI 계획 복구 보기' */
    override fun openRecovery() {
        val nav = findNavController()
        if (nav.currentDestination?.id == R.id.recordsFragment) nav.navigate(R.id.action_records_to_recovery)
    }

    private fun text(value: CharSequence, sizeSp: Float, @ColorInt c: Int, bold: Boolean = false) =
        TextView(requireContext()).apply {
            text = value
            textSize = sizeSp
            setTextColor(c)
            if (bold) setTypeface(typeface, Typeface.BOLD)
        }

    private fun SpannableStringBuilder.bold(value: String, @ColorInt c: Int) {
        val start = length
        append(value)
        setSpan(StyleSpan(Typeface.BOLD), start, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        setSpan(ForegroundColorSpan(c), start, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
    }

    private fun percentInt(rate: Float) = (rate * 100).roundToInt()
    private fun percent(rate: Float) = "${percentInt(rate)}%"

    private fun toast(message: String) = Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()

    private fun color(res: Int) = requireContext().getColor(res)

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }

    private companion object {
        val DAY_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("M월 d일 (E)", Locale.KOREAN)
        val MINT = 0xFF69B5A1.toInt()
        val GREEN = 0xFF2F8A6F.toInt()
        val CORAL = 0xFFC0573E.toInt()
        val LINE = 0xFFE1E8E5.toInt()
    }
}
