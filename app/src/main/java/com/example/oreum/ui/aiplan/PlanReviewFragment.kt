package com.example.oreum.ui.aiplan

import android.os.Bundle
import android.view.View
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
import com.example.oreum.databinding.ItemReviewTaskBinding
import com.example.oreum.databinding.ItemRoadmapStageBinding
import com.example.oreum.domain.usecase.RoundEngine
import com.example.oreum.ui.common.AiStateUi
import com.example.oreum.ui.home.HomePrefs
import com.example.oreum.util.DateText
import com.example.oreum.util.Season
import com.example.oreum.util.applySystemBarsPadding
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch
import java.time.LocalDate
import com.example.oreum.util.showToast

/** AI 로드맵 · 1구간 계획: 만드는 중 · 실패(다시 시도) · 확인 · 항목 수정 · 나중에 · 승인하고 시작 → 홈 */
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
        binding.btnAddItem.setOnClickListener {
            DraftItemSheet.add(viewModel.goalId).show(childFragmentManager, DraftItemSheet.TAG)
        }
        binding.btnFold.setOnClickListener { viewModel.toggleFold() }
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
        val ai = state.ai.takeIf { goal != null && draft == null }
        AiStateUi.render(
            binding.aiState, ai,
            loadingTitle = R.string.ai_loading_roadmap,
            loadingBody = R.string.ai_loading_roadmap_body,
            onRetry = viewModel::retry,
            onLater = ::later,
        )
        binding.textEmpty.isVisible = ai == null && (goal == null || draft == null)
        binding.groupDraft.isVisible = goal != null && draft != null
        if (goal == null || draft == null) return

        binding.textAiTag.setText(if (draft.byAi) R.string.ai_tag_real else R.string.ai_tag_simulation)

        val today = LocalDate.now()
        binding.textGoalTitle.text = goal.title
        binding.textGoalMeta.text = getString(
            R.string.round_review_meta,
            DateText.monthDay(DateText.toDate(goal.dueAt)),
            DateText.dDay(DateText.toDate(goal.dueAt), today),
        )
        binding.textStages.text = getString(R.string.round_review_stages, draft.roadmap.size)
        binding.textTotal.text = DateText.minutes((draft.roadmap.sumOf { it.hours } * 60).toInt())
        binding.textDays.text = getString(R.string.round_review_days, RoundEngine.daysLeft(goal, today.toEpochDay()))

        // 전체 로드맵: 한 줄에 번호 · 단계 · 예상 시간, 누르면 범위
        binding.stageList.removeAllViews()
        draft.roadmap.forEachIndexed { i, stage ->
            val sb = ItemRoadmapStageBinding.inflate(layoutInflater, binding.stageList, false)
            val first = i == 0
            sb.textNo.text = (i + 1).toString()
            sb.textNo.setBackgroundResource(if (first) R.drawable.bg_stage_num_cur else R.drawable.bg_stage_num)
            sb.textNo.setTextColor(requireContext().getColor(if (first) android.R.color.white else R.color.oreum_ink2))
            sb.textTitle.text = stage.title
            sb.textFirst.isVisible = first
            sb.textHours.text = DateText.minutes((stage.hours * 60).toInt())
            sb.textScope.text = stage.scope
            sb.textScope.isVisible = i in state.openStages
            sb.root.setOnClickListener { viewModel.toggleStage(i) }
            binding.stageList.addView(sb.root)
        }

        // 1구간 할 일
        val round = draft.round
        binding.textRoundTopic.text = round.displayTopic
        binding.textRoundMinutes.text = getString(R.string.round_review_est, DateText.minutes(round.estimatedMinutes))
        binding.itemList.removeAllViews()
        round.items.forEach { item ->
            val tb = ItemReviewTaskBinding.inflate(layoutInflater, binding.itemList, false)
            tb.textTitle.text = item.title
            tb.textTitle.textSize = 14f
            tb.textMinutes.text = DateText.minutes(item.minutes)
            tb.root.minimumHeight = (44 * resources.displayMetrics.density).toInt()
            tb.root.setOnClickListener {
                DraftItemSheet.edit(viewModel.goalId, item.id).show(childFragmentManager, DraftItemSheet.TAG)
            }
            binding.itemList.addView(tb.root)
        }
        binding.btnApprove.isEnabled = round.items.isNotEmpty()

        binding.iconFold.rotation = if (state.foldOpen) 180f else 0f
        binding.textFold.isVisible = state.foldOpen
        binding.textFold.text = buildString {
            append(getString(R.string.round_review_method))
            round.method.forEach { append("\n· ").append(it) }
            append("\n\n").append(getString(R.string.round_review_criteria))
            round.criteria.forEach { append("\n· ").append(it) }
        }
    }

    private fun confirmApprove() {
        val draft = viewModel.uiState.value.draft ?: return
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.round_approve_title)
            .setMessage(
                getString(
                    R.string.round_approve_body,
                    draft.roadmap.size,
                    draft.round.items.size,
                ),
            )
            .setNegativeButton(R.string.study_form_cancel, null)
            .setPositiveButton(R.string.round_review_approve) { _, _ ->
                viewModel.approve() ?: return@setPositiveButton
                toast(getString(R.string.toast_round_approved))
                // 승인한 목표의 산이 보이는 홈으로 이동
                HomePrefs.goalId = viewModel.goalId
                HomePrefs.picked = true
                val activity = requireActivity()
                findNavController().navigateUp()
                activity.findViewById<BottomNavigationView>(R.id.bottom_nav)?.selectedItemId = R.id.homeFragment
            }
            .show()
    }

    /** 나중에: 초안은 '승인 대기'로 남고 화면만 닫아요 */
    private fun later() {
        if (viewModel.uiState.value.draft != null) toast(getString(R.string.toast_plan_later))
        findNavController().navigateUp()
    }

    private fun toast(text: String) {
        requireActivity().showToast(text)
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}
