package com.example.oreum.ui.aiplan

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.example.oreum.R
import com.example.oreum.databinding.FragmentPlanReviewBinding
import com.example.oreum.databinding.ItemNoticeBinding
import com.example.oreum.databinding.ItemReviewDayBinding
import com.example.oreum.databinding.ItemReviewTaskBinding
import com.example.oreum.databinding.ItemReviewWeekBinding
import com.example.oreum.ui.study.priorityText
import com.example.oreum.util.DateText
import com.example.oreum.util.Season
import com.example.oreum.util.applySystemBarsPadding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

/** STEP 3 AI 계획 생성 결과: 확인 · 항목 수정 · 재생성 · 나중에 · 승인하고 적용 */
class PlanReviewFragment : Fragment(R.layout.fragment_plan_review) {

    private var _binding: FragmentPlanReviewBinding? = null
    private val binding get() = checkNotNull(_binding)
    private val viewModel: PlanReviewViewModel by viewModels()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentPlanReviewBinding.bind(view)

        binding.bgMountain.setImageResource(Season.of().mountainBackground)
        binding.content.applySystemBarsPadding(top = true, bottom = true)

        binding.btnBack.setOnClickListener { later() }
        binding.btnLater.setOnClickListener { later() }
        requireActivity().onBackPressedDispatcher.addCallback(
            viewLifecycleOwner,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() = later()
            },
        )
        binding.btnRegen.setOnClickListener {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.plan_regen_title)
                .setMessage(R.string.plan_regen_body)
                .setNegativeButton(R.string.study_form_cancel, null)
                .setPositiveButton(R.string.plan_regen_ok) { _, _ ->
                    viewModel.regenerate()
                    toast(getString(R.string.toast_plan_regen))
                }
                .show()
        }
        binding.btnApprove.setOnClickListener { confirmApprove() }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect(::render)
            }
        }
    }

    private fun render(state: PlanReviewUiState) {
        val goal = state.goal
        val draft = state.draft
        binding.textEmpty.isVisible = goal == null || draft == null
        binding.groupDraft.isVisible = goal != null && draft != null
        if (goal == null || draft == null) return

        val start = maxOf(LocalDate.ofEpochDay(goal.startDate), LocalDate.ofEpochDay(draft.createdOn))
        binding.textGoalTitle.text = goal.title
        binding.textGoalMeta.text = getString(
            R.string.plan_review_meta,
            DateText.monthDay(start),
            DateText.monthDay(DateText.toDate(goal.dueAt)),
            draft.weekThemes.size,
            goal.level.label,
            DateText.minutes(goal.weekdayMinutes),
            DateText.minutes(goal.weekendMinutes),
            requireContext().priorityText(goal.priority),
        )
        binding.textNeeded.text = DateText.minutes((draft.neededMinutes / 30) * 30)
        binding.textPlanned.text = DateText.minutes(draft.plannedMinutes)
        binding.textCapacity.text = DateText.minutes(draft.capacityMinutes)

        binding.noticeList.removeAllViews()
        draft.warnings.forEach { addNotice(it, warn = true) }
        if (draft.paceApplied) {
            val user = com.example.oreum.data.repository.AuthRepositoryProvider.instance.currentUser.value
            val pace = user?.paceRatio ?: 1f
            addNotice(getString(R.string.plan_review_pace, (pace * 100).toInt(), user?.paceSessionMinutes ?: 50), warn = false)
        }

        binding.weekList.removeAllViews()
        val byWeek = draft.items.groupBy { it.week }
        draft.weekThemes.forEachIndexed { index, theme ->
            val week = index + 1
            val weekItems = byWeek[week].orEmpty()
            val open = week in state.openWeeks
            val wb = ItemReviewWeekBinding.inflate(layoutInflater, binding.weekList, false)
            wb.textWeek.text = getString(R.string.plan_review_week, week)
            wb.textTheme.text = theme
            wb.textMinutes.text = DateText.minutes(weekItems.sumOf { it.minutes })
            wb.iconExpand.rotation = if (open) 180f else 0f
            wb.header.setOnClickListener { viewModel.toggleWeek(week) }
            wb.dayList.isVisible = open
            if (open) {
                weekItems.groupBy { it.date }.toSortedMap().forEach { (day, items) ->
                    val date = LocalDate.ofEpochDay(day)
                    val db = ItemReviewDayBinding.inflate(layoutInflater, wb.dayList, false)
                    val dateText = getString(
                        R.string.plan_review_day,
                        DateText.monthDay(date),
                        date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.KOREAN),
                    )
                    db.textDate.text = dateText
                    val sum = DateText.minutes(items.sumOf { it.minutes })
                    val over = day in draft.overDays
                    db.textSum.text = if (over) getString(R.string.plan_review_day_over, sum) else sum
                    db.textSum.setTextColor(requireContext().getColor(if (over) R.color.oreum_error else R.color.oreum_sub_text))
                    db.btnAdd.contentDescription = getString(R.string.plan_review_add_item, dateText)
                    db.btnAdd.setOnClickListener { DraftItemSheet.add(viewModel.goalId, day).show(childFragmentManager, DraftItemSheet.TAG) }
                    items.forEach { item ->
                        val tb = ItemReviewTaskBinding.inflate(layoutInflater, db.itemList, false)
                        tb.textTitle.text = item.title
                        tb.textMinutes.text = DateText.minutes(item.minutes)
                        tb.root.setOnClickListener {
                            DraftItemSheet.edit(viewModel.goalId, item.id).show(childFragmentManager, DraftItemSheet.TAG)
                        }
                        db.itemList.addView(tb.root)
                    }
                    wb.dayList.addView(db.root)
                }
            }
            binding.weekList.addView(wb.root)
        }
    }

    private fun addNotice(text: String, warn: Boolean) {
        val nb = ItemNoticeBinding.inflate(layoutInflater, binding.noticeList, false)
        nb.root.text = text
        nb.root.setBackgroundResource(if (warn) R.drawable.bg_warn else R.drawable.bg_ai_tag)
        binding.noticeList.addView(nb.root)
    }

    private fun confirmApprove() {
        val draft = viewModel.uiState.value.draft ?: return
        val body = getString(
            if (viewModel.hasRemainingTasks()) R.string.plan_approve_body_replace else R.string.plan_approve_body,
            draft.items.size,
            DateText.minutes(draft.plannedMinutes),
        )
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.plan_approve_title)
            .setMessage(body)
            .setNegativeButton(R.string.study_form_cancel, null)
            .setPositiveButton(R.string.plan_review_approve) { _, _ ->
                viewModel.approve()
                toast(getString(R.string.toast_plan_approved))
                // 승인한 목표의 계획을 계획 탭에서 바로 보여 줘요
                com.example.oreum.ui.calendar.PlanFocus.goalId = viewModel.goalId
                val activity = requireActivity()
                findNavController().navigateUp()
                activity.findViewById<com.google.android.material.bottomnavigation.BottomNavigationView>(R.id.bottom_nav)
                    ?.selectedItemId = R.id.calendarFragment
            }
            .show()
    }

    /** 나중에: 초안은 '승인 대기'로 남고 화면만 닫아요 */
    private fun later() {
        if (viewModel.uiState.value.draft != null) toast(getString(R.string.toast_plan_later))
        findNavController().navigateUp()
    }

    private fun toast(text: String) {
        Toast.makeText(requireContext().applicationContext, text, Toast.LENGTH_SHORT).show()
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}
