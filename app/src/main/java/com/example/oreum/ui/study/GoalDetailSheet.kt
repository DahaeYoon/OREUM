package com.example.oreum.ui.study

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.oreum.R
import com.example.oreum.data.model.PlanStatus
import com.example.oreum.data.repository.StudyRepository
import com.example.oreum.data.repository.StudyRepositoryProvider
import com.example.oreum.databinding.ItemInfoRowBinding
import com.example.oreum.databinding.SheetGoalDetailBinding
import com.example.oreum.domain.usecase.GoalProgress
import com.example.oreum.ui.common.GlassSheetFragment
import com.example.oreum.util.DateText
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * 목표 상세 팝업 (프로토타입 goalSheet3)
 * - 정보: 진행률 · 현재 수준 · 학습 가능 시간 · 우선순위 · 계획 상태
 * - 버튼: 삭제 · 수정 · (계획 전) AI 계획 생성 / (승인 대기) 생성 결과 보기 / (승인됨) 계획 보기
 * - 승인됨: AI 계획 다시 생성 · 완료로 표시
 */
class GoalDetailSheet : GlassSheetFragment() {

    private var _binding: SheetGoalDetailBinding? = null
    private val binding get() = checkNotNull(_binding)
    private val repository: StudyRepository = StudyRepositoryProvider.instance
    private val goalId: String get() = requireArguments().getString(ARG_GOAL_ID).orEmpty()
    private val parentStudy get() = parentFragment as? StudyFragment

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = SheetGoalDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.btnEdit.setOnClickListener {
            dismiss()
            parentStudy?.openForm(goalId)
        }
        binding.btnDelete.setOnClickListener { confirmDelete() }
        binding.btnPlanView.setOnClickListener {
            dismiss()
            parentStudy?.openPlanTab(goalId)
        }
        binding.btnRegen.setOnClickListener {
            dismiss()
            parentStudy?.openPlanReview(goalId, regenerate = true)
        }
        binding.btnComplete.setOnClickListener {
            repository.setGoalCompleted(goalId, true)
            Toast.makeText(requireContext().applicationContext, R.string.study_toast_completed, Toast.LENGTH_SHORT).show()
            dismiss()
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                combine(repository.goals, repository.tasks, repository.drafts) { goals, tasks, drafts ->
                    Triple(goals.firstOrNull { it.id == goalId }, tasks, drafts.containsKey(goalId))
                }.collect { (goal, tasks, hasDraft) ->
                    if (goal == null) {
                        dismissAllowingStateLoss()
                        return@collect
                    }
                    val progress = GoalProgress.of(goal.id, tasks)
                    val completed = GoalProgress.isCompleted(goal, tasks)
                    val status = if (hasDraft) PlanStatus.DRAFT else goal.planStatus
                    val due = DateText.toDate(goal.dueAt)

                    binding.imgMountain.setImageLevel(progress.percent * 100)
                    binding.textTitle.text = goal.title
                    binding.textMeta.text = getString(
                        R.string.study_detail_meta,
                        goal.period.label,
                        DateText.monthDay(LocalDate.ofEpochDay(goal.startDate)),
                        DateText.monthDay(due),
                        DateText.dDay(due),
                    )
                    binding.textDesc.text = goal.description
                    binding.textDesc.isVisible = goal.description.isNotBlank()

                    binding.infoList.removeAllViews()
                    addInfo(
                        R.string.study_detail_progress,
                        getString(
                            R.string.study_detail_progress_value,
                            progress.percent,
                            DateText.minutes(progress.doneMinutes),
                            DateText.minutes(progress.plannedMinutes),
                        ),
                    )
                    addInfo(R.string.study_detail_level_label, goal.level.label)
                    addInfo(
                        R.string.study_detail_time_label,
                        getString(
                            R.string.study_detail_time_value,
                            DateText.minutes(goal.weekdayMinutes),
                            DateText.minutes(goal.weekendMinutes),
                        ),
                    )
                    addInfo(R.string.study_detail_priority_label, requireContext().priorityText(goal.priority))
                    addInfo(
                        R.string.study_detail_plan_label,
                        when (status) {
                            PlanStatus.NONE -> getString(R.string.study_detail_plan_none)
                            PlanStatus.DRAFT -> getString(R.string.study_detail_plan_draft)
                            PlanStatus.APPROVED -> getString(R.string.study_detail_plan_approved, progress.totalCount)
                        },
                        last = true,
                    )

                    // 세 번째 버튼: 계획 전 → AI 계획 생성 · 승인 대기 → 생성 결과 보기 · 승인됨 → 계획 보기
                    binding.btnPlanPrimary.isVisible = status != PlanStatus.APPROVED
                    binding.btnPlanView.isVisible = status == PlanStatus.APPROVED
                    if (status == PlanStatus.DRAFT) {
                        binding.btnPlanPrimary.setText(R.string.study_detail_review)
                        binding.btnPlanPrimary.icon = null
                    } else {
                        binding.btnPlanPrimary.setText(R.string.study_detail_gen)
                        binding.btnPlanPrimary.setIconResource(R.drawable.ic_spark)
                    }
                    binding.btnPlanPrimary.setOnClickListener {
                        dismiss()
                        parentStudy?.openPlanReview(goalId, regenerate = false)
                    }

                    // 승인됨: AI 계획 다시 생성 · (진행 중이면) 완료로 표시
                    binding.rowMore.isVisible = status == PlanStatus.APPROVED
                    binding.btnComplete.isVisible = !completed
                }
            }
        }
    }

    private fun addInfo(labelRes: Int, value: String, last: Boolean = false) {
        val row = ItemInfoRowBinding.inflate(layoutInflater, binding.infoList, false)
        row.textLabel.setText(labelRes)
        row.textValue.text = value
        binding.infoList.addView(row.root)
        if (!last) {
            binding.infoList.addView(
                View(requireContext()).apply {
                    setBackgroundColor(requireContext().getColor(R.color.oreum_divider))
                    layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1)
                },
            )
        }
    }

    private fun confirmDelete() {
        val goal = repository.goal(goalId) ?: return
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.study_delete_title)
            .setMessage(getString(R.string.study_delete_body, goal.title))
            .setNegativeButton(R.string.study_delete_cancel, null)
            .setPositiveButton(R.string.study_detail_delete) { _, _ ->
                repository.deleteGoal(goal.id)
                Toast.makeText(requireContext().applicationContext, R.string.study_toast_deleted, Toast.LENGTH_SHORT).show()
            }
            .show()
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }

    companion object {
        const val TAG = "GoalDetailSheet"
        private const val ARG_GOAL_ID = "goalId"

        fun newInstance(goalId: String) = GoalDetailSheet().apply {
            arguments = Bundle().apply { putString(ARG_GOAL_ID, goalId) }
        }
    }
}
