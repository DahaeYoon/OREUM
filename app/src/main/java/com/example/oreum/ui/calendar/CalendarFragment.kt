package com.example.oreum.ui.calendar

import android.os.Bundle
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import android.text.style.StyleSpan
import android.graphics.Paint
import android.graphics.Typeface
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.example.oreum.R
import com.example.oreum.data.model.PlanStatus
import com.example.oreum.databinding.FragmentPlanBinding
import com.example.oreum.databinding.ItemMonthCellBinding
import com.example.oreum.databinding.ItemPlanNoteBinding
import com.example.oreum.databinding.ItemTimelineTaskBinding
import com.example.oreum.databinding.ItemWeekDayBinding
import com.example.oreum.domain.usecase.PlanCalendar
import com.example.oreum.ui.aiplan.PlanDrafts
import com.example.oreum.util.DateText
import com.example.oreum.util.Season
import com.example.oreum.util.applySystemBarsPadding
import com.example.oreum.util.onChildClick
import com.example.oreum.util.selectChild
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

/**
 * STEP 4 계획 (프로토타입 p5 · 월 달력)
 * - 주 | 월 전환, + 학습 항목 추가, 목표 탭(전체 · 목표별)
 * - 안내: AI 계획 승인 대기 · 계획 만들기 · 미완료 복구 · 기록 안 한 날
 * - 선택한 날: 할 일 수 · 시간 / 가능 시간 · 완료율, 타임라인 체크, 학습 결과 기록
 */
class CalendarFragment : Fragment(R.layout.fragment_plan), RecoveryOpener {

    private var _binding: FragmentPlanBinding? = null
    private val binding get() = checkNotNull(_binding)
    private val viewModel: PlanViewModel by viewModels()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentPlanBinding.bind(view)

        binding.bgMountain.setImageResource(Season.of().mountainBackground)
        binding.content.applySystemBarsPadding(top = true, bottom = false)

        binding.toggleView.onChildClick { viewModel.setView(if (it == 0) PlanView.WEEK else PlanView.MONTH) }
        binding.btnAdd.setOnClickListener {
            val s = viewModel.uiState.value.selection
            TaskFormSheet.add(s.selected.toEpochDay(), s.goalId).show(childFragmentManager, TaskFormSheet.TAG)
        }
        binding.btnPrevWeek.setOnClickListener { viewModel.shiftWeek(-1) }
        binding.btnNextWeek.setOnClickListener { viewModel.shiftWeek(1) }
        binding.btnPrevMonth.setOnClickListener { viewModel.shiftMonth(-1) }
        binding.btnNextMonth.setOnClickListener { viewModel.shiftMonth(1) }
        binding.btnLog.setOnClickListener { openLog(viewModel.uiState.value.selection.selected.toEpochDay()) }

        // 월 달력 요일 머리글 (월 ~ 일)
        WEEK_DAYS.forEach { dow ->
            binding.monthWeekdays.addView(
                TextView(requireContext()).apply {
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                    gravity = android.view.Gravity.CENTER
                    text = dow
                    textSize = 11f
                    setTypeface(typeface, Typeface.BOLD)
                    setTextColor(requireContext().getColor(R.color.oreum_ink3))
                    setPadding(0, dp(2), 0, dp(4))
                },
            )
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect(::render)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.applyFocus()
    }

    private fun render(state: PlanUiState) {
        val sel = state.selection
        val today = LocalDate.now()
        val selected = sel.selected
        val goal = state.goals.firstOrNull { it.id == sel.goalId }

        binding.toggleView.selectChild(if (sel.view == PlanView.WEEK) 0 else 1)

        // 목표 탭
        binding.tabsGoal.removeAllViews()
        val tabs = listOf<Pair<String?, String>>(null to getString(R.string.study_filter_all)) + state.goals.map { it.id to it.title }
        tabs.forEach { (id, title) ->
            val tab = layoutInflater.inflate(R.layout.item_goal_tab, binding.tabsGoal, false) as TextView
            tab.text = title
            tab.isSelected = id == sel.goalId
            tab.setOnClickListener { viewModel.setGoal(id) }
            binding.tabsGoal.addView(tab)
        }

        renderNotes(state)

        // 주 줄 또는 월 달력
        binding.weekStrip.isVisible = sel.view == PlanView.WEEK
        binding.monthCard.isVisible = sel.view == PlanView.MONTH
        if (sel.view == PlanView.WEEK) renderWeek(state, today) else renderMonth(state, today)

        // 선택한 날
        val items = PlanCalendar.dayItems(state.tasks, selected.toEpochDay(), sel.goalId)
        val planned = items.sumOf { it.originalMinutes ?: it.estimatedMinutes }
        val got = items.sumOf { PlanCalendar.credited(it) }
        val cap = PlanCalendar.capacity(selected, goal, state.userWeekdayMinutes, state.userWeekendMinutes)
        val over = planned > cap
        val percent = if (planned > 0) got * 100 / planned else 0

        binding.textDayTitle.text = SpannableStringBuilder().apply {
            append(getString(R.string.plan_day_title, selected.monthValue, selected.dayOfMonth))
            append(" ")
            val start = length
            append(getString(R.string.plan_day_weekday, selected.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.KOREAN)))
            setSpan(RelativeSizeSpan(0.72f), start, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            setSpan(ForegroundColorSpan(requireContext().getColor(R.color.oreum_ink2)), start, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        binding.textToday.isVisible = selected == today
        binding.textDayMeta.text = SpannableStringBuilder().apply {
            append(getString(R.string.plan_day_meta, items.size, DateText.minutes(planned)))
            val start = length
            append(getString(R.string.plan_day_cap, DateText.minutes(cap)))
            if (over) setSpan(ForegroundColorSpan(requireContext().getColor(R.color.oreum_coral_ink)), start, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            if (items.isNotEmpty()) append(getString(R.string.plan_day_done, percent))
        }
        binding.progressDay.isVisible = items.isNotEmpty()
        binding.progressDay.progress = percent
        binding.textOver.isVisible = over
        binding.textOver.text = getString(R.string.plan_day_over, DateText.minutes(planned - cap))

        binding.timeline.removeAllViews()
        binding.textDayEmpty.isVisible = items.isEmpty()
        items.forEachIndexed { index, task ->
            val row = ItemTimelineTaskBinding.inflate(layoutInflater, binding.timeline, false)
            val done = task.isDone
            row.line.isVisible = index < items.lastIndex
            row.line.setBackgroundColor(if (done) 0xFFBFE3D7.toInt() else 0x141F2D2A)
            row.btnCheck.setBackgroundResource(if (done) R.drawable.bg_check_on else R.drawable.bg_check_off)
            row.iconCheck.isVisible = done
            row.btnCheck.contentDescription = getString(R.string.task_done) + " " + task.title
            row.textTitle.text = task.title
            row.textTitle.setTextColor(requireContext().getColor(if (done) R.color.oreum_ink3 else R.color.oreum_dark_text))
            row.textTitle.paintFlags = if (done) row.textTitle.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
            else row.textTitle.paintFlags and Paint.STRIKE_THRU_TEXT_FLAG.inv()

            // 아래 줄: (전체일 때) 목표 이름 · 복구 · 미완료 상태
            val sub = SpannableStringBuilder()
            if (sel.goalId == null) state.goals.firstOrNull { it.id == task.goalId }?.let { sub.append(it.title) }
            if (task.fromRecoveryId != null) {
                if (sub.isNotEmpty()) sub.append(" · ")
                val s = sub.length
                sub.append(getString(R.string.plan_rec_tag))
                sub.setSpan(StyleSpan(Typeface.BOLD), s, sub.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                sub.setSpan(ForegroundColorSpan(0xFF3E7F86.toInt()), s, sub.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            val st = PlanCalendar.stateText(task, today.toEpochDay(), state.logs)
            if (st != "예정" && st != "완료") {
                if (sub.isNotEmpty()) sub.append(" ")
                val s = sub.length
                sub.append(st)
                sub.setSpan(StyleSpan(Typeface.BOLD), s, sub.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                sub.setSpan(ForegroundColorSpan(requireContext().getColor(R.color.oreum_coral_ink)), s, sub.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            row.textSub.text = sub
            row.textSub.isVisible = sub.isNotEmpty()
            row.textMinutes.text = DateText.minutes(task.originalMinutes ?: task.estimatedMinutes)
            row.root.alpha = if (task.carriedOver) 0.5f else 1f

            row.btnCheck.setOnClickListener {
                val nowDone = viewModel.toggleTask(task.id) ?: return@setOnClickListener
                Toast.makeText(
                    requireContext(),
                    if (nowDone) getString(R.string.toast_task_done, task.title) else getString(R.string.toast_task_undone),
                    Toast.LENGTH_SHORT,
                ).show()
            }
            row.item.setOnClickListener {
                TaskDetailSheet.newInstance(task.id).show(childFragmentManager, TaskDetailSheet.TAG)
            }
            binding.timeline.addView(row.root)
        }

        binding.btnLog.isVisible = !selected.isAfter(today) && items.isNotEmpty()
    }

    private fun renderNotes(state: PlanUiState) {
        binding.noteList.removeAllViews()
        fun note(color: Int, text: String, onClick: () -> Unit) {
            val nb = ItemPlanNoteBinding.inflate(layoutInflater, binding.noteList, false)
            nb.dot.background.mutate().setTint(color)
            nb.text.text = text
            nb.root.setOnClickListener { onClick() }
            binding.noteList.addView(nb.root)
        }
        state.goals.filter { it.id in state.draftGoalIds }.forEach { g ->
            note(0xFF69B5A1.toInt(), getString(R.string.plan_note_review, g.title)) { openReview(g.id, generate = false) }
        }
        state.goals.filter { it.planStatus == PlanStatus.NONE && it.id !in state.draftGoalIds }.forEach { g ->
            note(0xFF69B5A1.toInt(), getString(R.string.plan_note_gen, g.title)) { openReview(g.id, generate = true) }
        }
        val pending = PlanCalendar.pendingRecovery(state.tasks)
        if (pending.isNotEmpty()) {
            note(0xFFE28C72.toInt(), getString(R.string.plan_note_recover, pending.size)) { openRecovery() }
        }
        val unlogged = PlanCalendar.unloggedDays(state.tasks, state.logs, LocalDate.now().toEpochDay())
        if (unlogged.isNotEmpty()) {
            val days = unlogged.joinToString(", ") { DateText.monthDay(LocalDate.ofEpochDay(it)) }
            note(0xFFE2B65E.toInt(), getString(R.string.plan_note_unlogged, days)) { openLog(unlogged.first()) }
        }
        binding.noteList.isVisible = binding.noteList.childCount > 0
    }

    private fun renderWeek(state: PlanUiState, today: LocalDate) {
        val sel = state.selection
        val monday = sel.selected.minusDays((sel.selected.dayOfWeek.value - 1).toLong())
        binding.weekDays.removeAllViews()
        (0 until 7).forEach { i ->
            val date = monday.plusDays(i.toLong())
            val cell = ItemWeekDayBinding.inflate(layoutInflater, binding.weekDays, false)
            cell.textDow.text = date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.KOREAN)
            cell.textNum.text = date.dayOfMonth.toString()
            styleDayNumber(cell.textNum, selected = date == sel.selected, today = date == today, past = false)
            val has = PlanCalendar.dayItems(state.tasks, date.toEpochDay(), sel.goalId).isNotEmpty()
            cell.dot.visibility = if (has) View.VISIBLE else View.INVISIBLE
            cell.root.setOnClickListener { viewModel.select(date) }
            binding.weekDays.addView(cell.root)
        }
    }

    private fun renderMonth(state: PlanUiState, today: LocalDate) {
        val sel = state.selection
        val first = sel.selected.withDayOfMonth(1)
        val days = first.lengthOfMonth()
        val lead = first.dayOfWeek.value - 1
        binding.textMonth.text = getString(R.string.plan_month_title, first.year, first.monthValue)
        binding.monthGrid.removeAllViews()

        var row: LinearLayout? = null
        var total = 0
        var doneCount = 0
        for (index in 0 until lead + days) {
            if (index % 7 == 0) {
                row = LinearLayout(requireContext()).apply { orientation = LinearLayout.HORIZONTAL }
                binding.monthGrid.addView(row)
            }
            val cell = ItemMonthCellBinding.inflate(layoutInflater, row, false)
            if (index < lead) {
                cell.root.visibility = View.INVISIBLE
                row!!.addView(cell.root)
                continue
            }
            val date = first.plusDays((index - lead).toLong())
            val items = PlanCalendar.dayItems(state.tasks, date.toEpochDay(), sel.goalId)
            total += items.size
            doneCount += items.count { it.isDone }
            val allDone = items.isNotEmpty() && items.all { it.isDone }
            cell.textNum.text = date.dayOfMonth.toString()
            styleDayNumber(cell.textNum, selected = date == sel.selected, today = date == today, past = date.isBefore(today))
            cell.dot.visibility = if (items.isNotEmpty() && !allDone) View.VISIBLE else if (allDone) View.GONE else View.INVISIBLE
            cell.ok.isVisible = allDone
            cell.root.setOnClickListener { viewModel.select(date) }
            row!!.addView(cell.root)
        }
        // 마지막 줄 빈칸 채우기 (칸 너비를 맞추려고)
        val rest = (7 - (lead + days) % 7) % 7
        repeat(rest) {
            val cell = ItemMonthCellBinding.inflate(layoutInflater, row, false)
            cell.root.visibility = View.INVISIBLE
            row!!.addView(cell.root)
        }
        binding.textMonthSum.text = getString(R.string.plan_month_sum, total, doneCount)
    }

    /** 날짜 숫자: 선택 = 진한 원 + 흰 글자 · 오늘 = 민트 테두리 + 초록 글자 · 지난날(월 달력) = 흐린 글자 */
    private fun styleDayNumber(view: TextView, selected: Boolean, today: Boolean, past: Boolean) {
        val ctx = requireContext()
        when {
            selected -> {
                view.setBackgroundResource(R.drawable.bg_day_selected)
                view.setTextColor(ctx.getColor(R.color.white))
            }
            today -> {
                view.setBackgroundResource(R.drawable.bg_day_today)
                view.setTextColor(ctx.getColor(R.color.oreum_today_green))
            }
            else -> {
                view.background = null
                view.setTextColor(ctx.getColor(if (past) R.color.oreum_ink3 else R.color.oreum_dark_text))
            }
        }
    }

    /** AI 계획 생성 결과로 (generate = true면 먼저 초안을 만들어요 · 시뮬레이션) */
    private fun openReview(goalId: String, generate: Boolean) {
        if (generate) PlanDrafts.generateFor(goalId)
        findNavController().navigate(
            R.id.action_calendar_to_planReview,
            Bundle().apply { putString("goalId", goalId) },
        )
    }

    fun openLog(date: Long) {
        DayLogSheet.newInstance(date).show(childFragmentManager, DayLogSheet.TAG)
    }

    /** AI 학습 계획 복구 (시뮬레이션 · 승인하기 전에는 계획이 바뀌지 않아요) */
    override fun openRecovery() {
        val nav = findNavController()
        if (nav.currentDestination?.id == R.id.calendarFragment) nav.navigate(R.id.action_calendar_to_recovery)
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }

    private companion object {
        val WEEK_DAYS = listOf("월", "화", "수", "목", "금", "토", "일")
    }
}
