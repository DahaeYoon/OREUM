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
import com.example.oreum.data.model.PlanStatus
import com.example.oreum.data.model.RoundStatus
import com.example.oreum.data.repository.StudyRepositoryProvider
import com.example.oreum.databinding.FragmentStudyBinding
import com.example.oreum.databinding.ItemGoalCardBinding
import com.example.oreum.ui.aiplan.PlanDrafts
import com.example.oreum.util.DateText
import com.example.oreum.util.navigateSafe
import com.example.oreum.util.Season
import com.example.oreum.util.applySystemBarsPadding
import com.example.oreum.util.onChildClick
import com.example.oreum.util.selectChild
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** 목표 탭: 진행 중 · 완료, 목표 카드 (이름 · D-day / 상태 하나 / 진행률 / 단계 · 남은 학습). 누르면 목표 상세 */
class StudyFragment : Fragment(R.layout.fragment_study) {

    private var _binding: FragmentStudyBinding? = null
    private val binding get() = checkNotNull(_binding)
    private val viewModel: StudyViewModel by viewModels()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentStudyBinding.bind(view)

        binding.bgMountain.setImageResource(Season.of().mountainBackground)
        binding.appbar.applySystemBarsPadding(top = true, bottom = false)

        binding.btnAdd.setOnClickListener { openForm(goalId = null) }
        binding.btnEmptyAdd.setOnClickListener { openForm(goalId = null) }
        binding.segStatus.onChildClick { viewModel.setShowCompleted(it == 1) }

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

        binding.textNote.isVisible = !state.showCompleted && state.needPlanCount > 0
        binding.textNote.text = getString(R.string.study_note_need_roadmap, state.needPlanCount)

        binding.groupEmpty.isVisible = state.cards.isEmpty()
        binding.btnEmptyAdd.isVisible = !state.showCompleted
        binding.textEmpty.text = getString(if (state.showCompleted) R.string.study_empty_done else R.string.study_empty_first)

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

            // 상태 하나만: 정상 도착 · 다음 구간 추천 · n구간 진행 중 · n구간 시작 전
            val status = when {
                card.completed -> getString(R.string.study_state_top)
                card.hasProposal -> getString(R.string.study_state_proposal)
                card.current?.status == RoundStatus.ACTIVE -> getString(R.string.study_state_active, card.current.no)
                card.current?.status == RoundStatus.READY -> getString(R.string.study_state_ready, card.current.no)
                else -> null
            }
            item.textMeta.isVisible = status != null
            item.textMeta.text = status
            val ai = card.hasProposal
            item.textMeta.setBackgroundResource(if (ai) R.drawable.bg_badge else R.drawable.bg_status_chip)
            item.textMeta.setTextColor(requireContext().getColor(if (ai) R.color.oreum_forest_deep else R.color.oreum_ink2))

            item.progress.progress = card.progress.percent
            item.textPercent.text = getString(R.string.round_percent, card.progress.percent)

            val approved = card.planStatus == PlanStatus.APPROVED && !card.completed
            item.textSub.isVisible = approved
            item.textSub.text = getString(
                R.string.study_sub_roadmap,
                card.progress.doneStages,
                card.progress.totalStages,
                DateText.minutes((card.progress.remainingHours * 60).toInt()),
            )

            // 로드맵 전: AI 로드맵 만들기 · 승인 대기: 로드맵 확인 · 승인
            val needPlan = !card.completed && card.planStatus != PlanStatus.APPROVED
            item.btnDo.isVisible = needPlan
            if (needPlan) {
                val wait = card.planStatus == PlanStatus.DRAFT
                item.textDo.setText(if (wait) R.string.study_do_review else R.string.study_do_make)
                val color = if (wait) 0xFF8A6420.toInt() else 0xFF2F6F64.toInt()
                item.textDo.setTextColor(color)
                item.iconDo.setColorFilter(color)
                item.btnDo.setBackgroundResource(if (wait) R.drawable.bg_goal_do_wait else R.drawable.bg_goal_do)
                item.btnDo.setOnClickListener { openPlanReview(goal.id) }
            }
            item.root.alpha = if (card.completed) 0.85f else 1f
            item.main.setOnClickListener { openDetail(goal.id) }
            binding.goalList.addView(item.root)
        }
    }

    /** "진행 중 4"에서 숫자만 작고 흐리게 */
    private fun countLabel(label: String, count: Int): CharSequence {
        val text = getString(R.string.study_status_count, label, count)
        return SpannableString(text).apply {
            setSpan(RelativeSizeSpan(0.85f), label.length, text.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            setSpan(android.text.style.ForegroundColorSpan(0x99526864.toInt()), label.length, text.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
    }

    /** 목표 상세 화면 */
    private fun openDetail(goalId: String) {
        navigateSafe(
            R.id.action_study_to_goalDetail,
            Bundle().apply { putString(GoalDetailViewModel.ARG_GOAL_ID, goalId) },
        )
    }

    /** 목표 만들기(goalId = null) · 수정 팝업 */
    fun openForm(goalId: String?) {
        navigateSafe(
            R.id.action_study_to_goalForm,
            Bundle().apply { putString(GoalFormViewModel.ARG_GOAL_ID, goalId) },
        )
    }

    /**
     * AI 로드맵 · 1구간 계획 화면으로 이동 (시뮬레이션).
     * 승인 대기 초안이 있으면 그대로 보여 주고, 없으면 새로 만들어요.
     */
    fun openPlanReview(goalId: String) {
        val repository = StudyRepositoryProvider.instance
        if (repository.roadmapDrafts.value[goalId] == null) PlanDrafts.generateFor(goalId, repository)
        navigateSafe(
            R.id.action_study_to_planReview,
            Bundle().apply { putString(GoalFormViewModel.ARG_GOAL_ID, goalId) },
        )
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}
