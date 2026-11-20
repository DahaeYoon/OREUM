package com.example.oreum.ui.study

import android.graphics.Paint
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.example.oreum.R
import com.example.oreum.data.model.ItemKind
import com.example.oreum.data.model.PlanStatus
import com.example.oreum.data.model.RoundStatus
import com.example.oreum.data.model.StudyRound
import com.example.oreum.databinding.FragmentGoalDetailBinding
import com.example.oreum.databinding.ItemRoadmapStageBinding
import com.example.oreum.databinding.ItemRoundHistoryBinding
import com.example.oreum.databinding.ItemRoundItemBinding
import com.example.oreum.domain.usecase.RoadmapProgress
import com.example.oreum.ui.aiplan.PlanDrafts
import com.example.oreum.ui.round.NextRoundFragment
import com.example.oreum.ui.round.RoundLogSheet
import com.example.oreum.util.DateText
import com.example.oreum.util.Season
import com.example.oreum.util.applySystemBarsPadding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch
import java.time.LocalDate

/** 목표 상세 (계획 탭 대신): 이번 구간 체크 · 항목 추가 · 시작, 다음 구간 추천, 마감 한 줄, 전체 로드맵, 이전 구간 */
class GoalDetailFragment : Fragment(R.layout.fragment_goal_detail) {

    private var _binding: FragmentGoalDetailBinding? = null
    private val binding get() = checkNotNull(_binding)
    private val viewModel: GoalDetailViewModel by viewModels()
    private var left = false

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentGoalDetailBinding.bind(view)

        binding.bgMountain.setImageResource(Season.of().mountainBackground)
        binding.content.applySystemBarsPadding(top = true, bottom = true)

        binding.btnBack.setOnClickListener { findNavController().navigateUp() }
        binding.btnEdit.setOnClickListener {
            findNavController().navigate(
                R.id.action_goalDetail_to_goalForm,
                Bundle().apply { putString(GoalFormViewModel.ARG_GOAL_ID, viewModel.goalId) },
            )
        }
        binding.btnFold.setOnClickListener { viewModel.toggleFold() }
        binding.btnDelete.setOnClickListener { confirmDelete() }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect(::render)
            }
        }
    }

    private fun render(state: GoalDetailUiState) {
        if (!state.loaded) return
        val goal = state.goal ?: run {
            // 지운 목표: 목표 목록으로
            if (!left) {
                left = true
                findNavController().navigateUp()
            }
            return
        }
        val today = LocalDate.now()
        val due = DateText.toDate(goal.dueAt)
        val done = goal.completedAt != null
        binding.textTitle.text = goal.title
        binding.textMeta.text = if (done) {
            getString(R.string.goal_detail_meta_done, DateText.monthDay(DateText.toDate(goal.completedAt!!)))
        } else {
            listOfNotNull(
                goal.subject.takeIf { it.isNotBlank() && it != goal.title },
                getString(R.string.round_review_meta, DateText.monthDay(due), DateText.dDay(due, today)),
            ).joinToString(" · ")
        }

        renderState(state)
        renderRound(state)
        renderDue(state.feasibility)
        renderRoadmap(state)
        renderHistory(state.history)
    }

    /** 로드맵 없음 · 승인 대기 · 다음 구간 추천 도착 */
    private fun renderState(state: GoalDetailUiState) {
        val goal = state.goal ?: return
        val proposal = state.proposal
        when {
            goal.completedAt != null -> binding.cardState.isVisible = false
            state.hasDraft -> {
                binding.cardState.isVisible = true
                binding.textStateTitle.setText(R.string.goal_detail_draft_title)
                binding.textStateBody.setText(R.string.goal_detail_draft_body)
                binding.btnState.isVisible = true
                binding.btnState.setText(R.string.study_do_review)
                binding.btnState.icon = null
                binding.btnState.setOnClickListener { openReview(generate = false) }
            }
            goal.planStatus == PlanStatus.NONE || goal.roadmap.isEmpty() -> {
                binding.cardState.isVisible = true
                binding.textStateTitle.setText(R.string.goal_detail_none_title)
                binding.textStateBody.setText(R.string.goal_detail_none_body)
                binding.btnState.isVisible = true
                binding.btnState.setText(R.string.goal_detail_make)
                binding.btnState.setIconResource(R.drawable.ic_spark)
                binding.btnState.setOnClickListener { openReview(generate = true) }
            }
            proposal != null -> {
                binding.cardState.isVisible = true
                binding.textStateTitle.setText(R.string.goal_detail_proposal_title)
                binding.textStateBody.text = proposal.round?.let { r ->
                    getString(R.string.goal_detail_proposal_body, r.no, r.displayTopic, r.items.size)
                } ?: getString(R.string.goal_detail_proposal_final)
                binding.btnState.isVisible = true
                binding.btnState.setText(R.string.goal_detail_proposal_btn)
                binding.btnState.setIconResource(R.drawable.ic_spark)
                binding.btnState.setOnClickListener { NextRoundFragment.open(findNavController(), viewModel.goalId) }
            }
            else -> binding.cardState.isVisible = false
        }
    }

    /** 이번 구간: 체크 · 항목 추가 · 시작 */
    private fun renderRound(state: GoalDetailUiState) {
        val r = state.current
        binding.cardRound.isVisible = r != null && state.goal?.completedAt == null
        if (r == null) return
        val ready = r.status == RoundStatus.READY
        binding.textRoundNo.text = getString(R.string.round_no, r.no)
        binding.textRoundTopic.text = r.displayTopic
        binding.textRoundStatus.setText(if (ready) R.string.round_status_ready else R.string.round_status_active)
        binding.progressRound.progress = if (r.items.isEmpty()) 0 else r.doneCount * 100 / r.items.size
        binding.textRoundSum.text = getString(R.string.round_sum, r.doneCount, r.items.size, DateText.minutes(r.estimatedMinutes))

        binding.itemList.removeAllViews()
        r.items.forEach { item ->
            val ib = ItemRoundItemBinding.inflate(layoutInflater, binding.itemList, false)
            ib.textTitle.text = item.title.removeSuffix(" (이어서)")
            ib.textMinutes.text = DateText.minutes(item.minutes)
            ib.checkBox.setBackgroundResource(if (item.done) R.drawable.bg_check_on else R.drawable.bg_check_off)
            ib.iconCheck.isVisible = item.done
            ib.textTitle.paintFlags = if (item.done) ib.textTitle.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
            else ib.textTitle.paintFlags and Paint.STRIKE_THRU_TEXT_FLAG.inv()
            ib.textTitle.setTextColor(requireContext().getColor(if (item.done) R.color.oreum_ink3 else R.color.oreum_dark_text))
            when (item.kind) {
                ItemKind.CARRY -> {
                    ib.textKind.isVisible = true
                    ib.textKind.setText(R.string.round_kind_carry)
                    ib.textKind.setBackgroundResource(R.drawable.bg_round_kind)
                    ib.textKind.setTextColor(0xFF3A6EA8.toInt())
                }
                ItemKind.REVIEW -> {
                    ib.textKind.isVisible = true
                    ib.textKind.setText(R.string.round_kind_review)
                    ib.textKind.setBackgroundResource(R.drawable.bg_round_kind_review)
                    ib.textKind.setTextColor(0xFFB4741C.toInt())
                }
                ItemKind.NEW -> ib.textKind.isVisible = false
            }
            ib.btnCheck.contentDescription = getString(R.string.round_item_check, item.title)
            ib.btnCheck.setOnClickListener {
                val wasDone = item.done
                val updated = viewModel.toggleItem(r.id, item.id) ?: return@setOnClickListener
                if (!wasDone && updated.allDone) {
                    // 항목을 모두 마쳤으면 결과 기록을 권해요
                    MaterialAlertDialogBuilder(requireContext())
                        .setTitle(getString(R.string.toast_round_all_done, updated.no))
                        .setMessage(R.string.round_all_done_body)
                        .setNegativeButton(R.string.round_submitted_later, null)
                        .setPositiveButton(R.string.round_all_done_ok) { _, _ -> openLog(updated.id, submit = true) }
                        .show()
                }
            }
            ib.rowMain.setOnClickListener {
                RoundItemSheet.edit(r.id, item.id).show(childFragmentManager, RoundItemSheet.TAG)
            }
            binding.itemList.addView(ib.root)
        }
        binding.btnRoundAdd.setOnClickListener {
            RoundItemSheet.add(r.id).show(childFragmentManager, RoundItemSheet.TAG)
        }
        // 시작 전: [n구간 시작] / 진행 중: [항목 추가] [학습 결과 기록] (다 끝냈으면 [결과 제출])
        binding.btnRoundAdd.isVisible = !ready
        binding.btnRoundMain.isVisible = true
        if (ready) {
            binding.btnRoundMain.text = getString(R.string.round_start, r.no)
            binding.btnRoundMain.setIconResource(R.drawable.ic_route)
            binding.btnRoundMain.setOnClickListener {
                viewModel.startRound(r.id)
                toast(getString(R.string.toast_round_started, r.no))
            }
        } else {
            binding.btnRoundMain.text = getString(if (r.allDone) R.string.home_btn_submit else R.string.home_round_log)
            binding.btnRoundMain.setIconResource(R.drawable.ic_clock)
            binding.btnRoundMain.setOnClickListener { openLog(r.id, submit = r.allDone) }
        }

        binding.iconFold.rotation = if (state.foldOpen) 180f else 0f
        binding.textFold.isVisible = state.foldOpen
        binding.textFold.text = foldText(r)
    }

    private fun openLog(roundId: String, submit: Boolean) {
        if (childFragmentManager.findFragmentByTag(RoundLogSheet.TAG) != null) return
        RoundLogSheet.newInstance(roundId, submit).show(childFragmentManager, RoundLogSheet.TAG)
    }

    private fun foldText(r: StudyRound) = buildString {
        append(getString(R.string.round_review_method))
        r.method.forEach { append("\n· ").append(it) }
        append("\n\n").append(getString(R.string.round_review_criteria))
        r.criteria.forEach { append("\n· ").append(it) }
    }

    /** 마감 위험 한 줄 (빠듯함 · 어려움일 때만) */
    private fun renderDue(f: RoadmapProgress.Feasibility?) {
        val show = f != null && (f.level == RoadmapProgress.FeasLevel.TIGHT || f.level == RoadmapProgress.FeasLevel.RISK)
        binding.rowDue.isVisible = show
        if (!show) return
        binding.textDueTitle.text = getString(R.string.goal_detail_due_title, f!!.level.label)
        binding.textDueBody.text = getString(
            R.string.goal_detail_due_body,
            DateText.minutes((f.remainingHours * 60).toInt()),
            f.daysLeft,
        )
    }

    /** 전체 로드맵: 지난 단계 ✓ · 지금 단계 '지금 여기' · 다음 단계 */
    private fun renderRoadmap(state: GoalDetailUiState) {
        val goal = state.goal ?: return
        binding.groupRoadmap.isVisible = goal.roadmap.isNotEmpty() && goal.planStatus == PlanStatus.APPROVED
        if (!binding.groupRoadmap.isVisible) return
        binding.textRoadmapPercent.text = getString(R.string.round_percent, state.progress.percent)
        binding.stageList.removeAllViews()
        goal.roadmap.forEachIndexed { i, stage ->
            val sb = ItemRoadmapStageBinding.inflate(layoutInflater, binding.stageList, false)
            val fr = state.progress.stageFractions.getOrElse(i) { 0f }
            val stageDone = fr >= 1f
            val current = !stageDone && i == goal.stage && goal.completedAt == null
            sb.textNo.text = if (stageDone) "✓" else (i + 1).toString()
            sb.textNo.setBackgroundResource(if (stageDone || current) R.drawable.bg_stage_num_cur else R.drawable.bg_stage_num)
            sb.textNo.setTextColor(requireContext().getColor(if (stageDone || current) android.R.color.white else R.color.oreum_ink2))
            sb.textTitle.text = stage.title
            sb.textTitle.setTextColor(requireContext().getColor(if (stageDone) R.color.oreum_ink3 else R.color.oreum_dark_text))
            sb.textFirst.isVisible = current
            sb.textFirst.setText(R.string.goal_detail_here)
            sb.textHours.text = if (current) getString(R.string.round_percent, (fr * 100).toInt()) else DateText.minutes((stage.hours * 60).toInt())
            sb.textScope.text = stage.scope
            sb.textScope.isVisible = false
            sb.root.setOnClickListener { sb.textScope.isVisible = !sb.textScope.isVisible }
            binding.stageList.addView(sb.root)
        }
    }

    /** 이전 구간: 날짜 · 완료 개수만 */
    private fun renderHistory(history: List<StudyRound>) {
        binding.groupHistory.isVisible = history.isNotEmpty()
        binding.textHistoryCount.text = getString(R.string.round_count, history.size)
        binding.historyList.removeAllViews()
        history.forEach { r ->
            val hb = ItemRoundHistoryBinding.inflate(layoutInflater, binding.historyList, false)
            hb.textNo.text = getString(R.string.round_badge_two_line, r.no)
            hb.textTopic.text = r.displayTopic
            hb.textSub.text = dateSpan(r)
            hb.textDone.text = getString(R.string.round_done_count, r.doneCount, r.items.size)
            val all = r.allDone
            hb.textDone.setBackgroundResource(if (all) R.drawable.bg_badge else R.drawable.bg_dday_hot)
            hb.textDone.setTextColor(if (all) 0xFF2E7A5F.toInt() else requireContext().getColor(R.color.oreum_coral_ink))
            binding.historyList.addView(hb.root)
        }
    }

    private fun dateSpan(r: StudyRound): String {
        val d = r.dates
        if (d.isEmpty()) return r.doneOn?.let { DateText.monthDay(LocalDate.ofEpochDay(it)) }.orEmpty()
        val first = DateText.monthDay(LocalDate.ofEpochDay(d.first()))
        return if (d.size == 1) first
        else getString(R.string.round_date_span, first, DateText.monthDay(LocalDate.ofEpochDay(d.last())), d.size)
    }

    private fun openReview(generate: Boolean) {
        if (generate) PlanDrafts.generateFor(viewModel.goalId)
        findNavController().navigate(
            R.id.action_goalDetail_to_planReview,
            Bundle().apply { putString(GoalFormViewModel.ARG_GOAL_ID, viewModel.goalId) },
        )
    }

    private fun confirmDelete() {
        val goal = viewModel.uiState.value.goal ?: return
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.study_delete_title)
            .setMessage(getString(R.string.study_delete_body, goal.title))
            .setNegativeButton(R.string.study_delete_cancel, null)
            .setPositiveButton(R.string.study_detail_delete) { _, _ ->
                viewModel.deleteGoal()
                toast(getString(R.string.study_toast_deleted))
            }
            .show()
    }

    private fun toast(text: String) {
        Toast.makeText(requireContext().applicationContext, text, Toast.LENGTH_SHORT).show()
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}
