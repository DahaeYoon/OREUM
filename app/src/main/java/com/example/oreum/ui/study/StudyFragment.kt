package com.example.oreum.ui.study

import android.os.Bundle
import android.text.SpannableString
import android.text.Spanned
import android.text.style.RelativeSizeSpan
import android.view.View
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.example.oreum.R
import com.example.oreum.data.model.GoalPeriod
import com.example.oreum.data.model.PlanStatus
import com.example.oreum.data.repository.StudyRepositoryProvider
import com.example.oreum.databinding.FragmentStudyBinding
import com.example.oreum.databinding.ItemGoalCardBinding
import com.example.oreum.ui.aiplan.PlanDrafts
import com.example.oreum.util.DateText
import com.example.oreum.util.Season
import com.example.oreum.util.applySystemBarsPadding
import com.example.oreum.util.onChildClick
import com.example.oreum.util.selectChild
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** STEP 2 목표 (프로토타입 g4): 진행 중 · 완료, 기간 탭(전체 · 일일 · 주간 · 월간), 목표 카드 */
class StudyFragment : Fragment(R.layout.fragment_study) {

    private var _binding: FragmentStudyBinding? = null
    private val binding get() = checkNotNull(_binding)
    private val viewModel: StudyViewModel by viewModels()

    /** 기간 탭 순서: 전체 · 일일 · 주간 · 월간 */
    private val periods = listOf(null, GoalPeriod.DAILY, GoalPeriod.WEEKLY, GoalPeriod.MONTHLY)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentStudyBinding.bind(view)

        binding.bgMountain.setImageResource(Season.of().mountainBackground)
        binding.appbar.applySystemBarsPadding(top = true, bottom = false)
        binding.periodDaily.text = GoalPeriod.DAILY.label
        binding.periodWeekly.text = GoalPeriod.WEEKLY.label
        binding.periodMonthly.text = GoalPeriod.MONTHLY.label

        binding.btnAdd.setOnClickListener { openForm(goalId = null) }
        binding.btnEmptyAdd.setOnClickListener { openForm(goalId = null) }
        binding.segStatus.onChildClick { viewModel.setShowCompleted(it == 1) }
        binding.tabsPeriod.onChildClick { viewModel.setPeriod(periods[it]) }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect(::render)
            }
        }
    }

    private fun render(state: StudyUiState) {
        binding.statusActive.text = countLabel(getString(R.string.study_status_active), state.activeCount)
        binding.statusDone.text = countLabel(getString(R.string.study_status_done), state.completedCount)
        binding.segStatus.selectChild(if (state.showCompleted) 1 else 0)
        binding.tabsPeriod.selectChild(periods.indexOf(state.period))

        binding.textNote.isVisible = !state.showCompleted && state.needPlanCount > 0
        binding.textNote.text = getString(R.string.study_note_need, state.needPlanCount)

        binding.groupEmpty.isVisible = state.cards.isEmpty()
        binding.btnEmptyAdd.isVisible = !state.showCompleted
        binding.textEmpty.text = when {
            state.showCompleted -> getString(R.string.study_empty_done)
            state.period != null -> getString(R.string.study_empty_period, state.period.label)
            else -> getString(R.string.study_empty_first)
        }

        val today = LocalDate.now()
        binding.goalList.removeAllViews()
        state.cards.forEach { card ->
            val goal = card.goal
            val item = ItemGoalCardBinding.inflate(layoutInflater, binding.goalList, false)
            val due = DateText.toDate(goal.dueAt)
            item.textTitle.text = goal.title

            val hot = !card.completed && ChronoUnit.DAYS.between(today, due) <= 3
            item.textDday.text = if (card.completed) getString(R.string.study_dday_done) else DateText.dDay(due, today)
            item.textDday.setBackgroundResource(if (hot) R.drawable.bg_dday_hot else R.drawable.bg_dday)
            item.textDday.setTextColor(requireContext().getColor(if (hot) R.color.oreum_coral_ink else R.color.oreum_ink2))

            item.textMeta.text = requireContext().goalMetaLine(
                priority = goal.priority,
                parts = listOf(goal.period.label, getString(R.string.study_card_due, DateText.monthDay(due))),
                today = if (card.todayCount > 0 && !card.completed) getString(R.string.study_today_count, card.todayCount) else null,
            )
            item.progress.progress = card.progress.percent
            item.textPercent.text = "${card.progress.percent}%"

            val showRemaining = card.planStatus == PlanStatus.APPROVED && !card.completed
            item.textSub.isVisible = showRemaining
            item.textSub.text = getString(
                R.string.study_remaining,
                DateText.minutes((card.progress.plannedMinutes - card.progress.doneMinutes).coerceAtLeast(0)),
            )

            // 계획 전: AI 계획 만들기 · 승인 대기: 계획 확인 · 승인
            val needPlan = !card.completed && card.planStatus != PlanStatus.APPROVED
            item.btnDo.isVisible = needPlan
            if (needPlan) {
                val wait = card.planStatus == PlanStatus.DRAFT
                item.textDo.setText(if (wait) R.string.study_do_review else R.string.study_do_make)
                val color = if (wait) 0xFF8A6420.toInt() else 0xFF2F6F64.toInt()
                item.textDo.setTextColor(color)
                item.iconDo.setColorFilter(color)
                item.btnDo.setBackgroundResource(if (wait) R.drawable.bg_goal_do_wait else R.drawable.bg_goal_do)
                item.btnDo.setOnClickListener { openPlanReview(goal.id, regenerate = false) }
            }
            item.root.alpha = if (card.completed) 0.85f else 1f
            item.main.setOnClickListener {
                GoalDetailSheet.newInstance(goal.id).show(childFragmentManager, GoalDetailSheet.TAG)
            }
            binding.goalList.addView(item.root)
        }
    }

    /** "진행 중 4"에서 숫자만 작고 흐리게 (프로토타입 .num 11px · 60%) */
    private fun countLabel(label: String, count: Int): CharSequence {
        val text = getString(R.string.study_status_count, label, count)
        return SpannableString(text).apply {
            setSpan(RelativeSizeSpan(0.85f), label.length, text.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            setSpan(android.text.style.ForegroundColorSpan(0x99526864.toInt()), label.length, text.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
    }

    /** 목표 만들기(goalId = null) · 수정 팝업 */
    fun openForm(goalId: String?) {
        findNavController().navigate(
            R.id.action_study_to_goalForm,
            Bundle().apply { putString(GoalFormViewModel.ARG_GOAL_ID, goalId) },
        )
    }

    /**
     * AI 계획 생성 결과 화면으로 이동 (시뮬레이션).
     * 승인 대기 초안이 있으면 그대로 보여 주고, 없거나 다시 생성이면 새로 만들어요.
     */
    fun openPlanReview(goalId: String, regenerate: Boolean) {
        val repository = StudyRepositoryProvider.instance
        if (regenerate || repository.drafts.value[goalId] == null) PlanDrafts.generateFor(goalId, repository)
        findNavController().navigate(
            R.id.action_study_to_planReview,
            Bundle().apply { putString(GoalFormViewModel.ARG_GOAL_ID, goalId) },
        )
    }

    /** 계획 탭으로 이동 (승인된 계획 보기 · 그 목표 탭이 열려요) */
    fun openPlanTab(goalId: String) {
        com.example.oreum.ui.calendar.PlanFocus.goalId = goalId
        requireActivity().findViewById<com.google.android.material.bottomnavigation.BottomNavigationView>(R.id.bottom_nav)
            ?.selectedItemId = R.id.calendarFragment
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}
