package com.example.oreum.ui.calendar

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
import com.example.oreum.databinding.SheetTaskDetailBinding
import com.example.oreum.domain.usecase.PlanCalendar
import com.example.oreum.ui.common.GlassSheetFragment
import com.example.oreum.util.DateText
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** 학습 항목 상세 팝업 (프로토타입 itemSheet): 상태 · 목표 · 날짜 · 예상/실제 시간 · 미완료 이유 + 수정 · 기록 · 완료 */
class TaskDetailSheet : GlassSheetFragment() {

    private var _binding: SheetTaskDetailBinding? = null
    private val binding get() = checkNotNull(_binding)
    private val repository: StudyRepository = StudyRepositoryProvider.instance
    private val taskId: String get() = requireArguments().getString(ARG_TASK_ID).orEmpty()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = SheetTaskDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                combine(repository.tasks, repository.goals, repository.logs) { tasks, goals, logs -> Triple(tasks, goals, logs) }
                    .collect { (tasks, goals, logs) ->
                        val task = tasks.firstOrNull { it.id == taskId }
                        if (task == null) {
                            dismissAllowingStateLoss()
                            return@collect
                        }
                        val today = LocalDate.now().toEpochDay()
                        val date = task.plannedDate ?: today
                        val goal = goals.firstOrNull { it.id == task.goalId }

                        val state = PlanCalendar.stateText(task, today, logs)
                        binding.textState.text = state
                        val (bg, color) = when (state) {
                            "완료" -> R.drawable.bg_state_ok to R.color.oreum_today_green
                            "예정" -> R.drawable.bg_state_plain to R.color.oreum_ink2
                            else -> R.drawable.bg_state_no to R.color.oreum_coral_ink
                        }
                        binding.textState.setBackgroundResource(bg)
                        binding.textState.setTextColor(requireContext().getColor(color))
                        binding.textRec.isVisible = task.fromRecoveryId != null
                        binding.textTitle.text = task.title

                        binding.infoList.removeAllViews()
                        val goalText = goal?.title ?: "-"
                        addInfo(
                            R.string.task_info_goal,
                            if (goal != null && goal.planStatus == PlanStatus.APPROVED) getString(R.string.task_info_goal_week, goalText, task.week) else goalText,
                        )
                        addInfo(R.string.task_info_date, LocalDate.ofEpochDay(date).format(DATE_FORMAT))
                        addInfo(R.string.task_info_planned, DateText.minutes(task.originalMinutes ?: task.estimatedMinutes))
                        val actual = task.actualMinutes
                        addInfo(
                            R.string.task_info_actual,
                            if (task.isDone || (actual ?: 0) > 0) DateText.minutes(actual ?: task.estimatedMinutes) else getString(R.string.task_info_actual_none),
                            last = task.missReason == null,
                        )
                        task.missReason?.let { addInfo(R.string.task_info_reason, it.label, last = true) }

                        // 버튼: 수정 · 기록(오늘까지) · 완료 / 완료 취소 (복구로 넘긴 항목은 수정 · 완료 없음)
                        binding.btnEdit.isVisible = !task.carriedOver
                        binding.btnLog.isVisible = date <= today
                        binding.btnDone.isVisible = !task.carriedOver && !task.isDone
                        binding.btnUndone.isVisible = !task.carriedOver && task.isDone
                    }
            }
        }

        binding.btnEdit.setOnClickListener {
            dismiss()
            TaskFormSheet.edit(taskId).show(parentFragmentManager, TaskFormSheet.TAG)
        }
        binding.btnLog.setOnClickListener {
            val date = repository.task(taskId)?.plannedDate ?: return@setOnClickListener
            dismiss()
            DayLogSheet.newInstance(date).show(parentFragmentManager, DayLogSheet.TAG)
        }
        binding.btnDone.setOnClickListener { toggle(true) }
        binding.btnUndone.setOnClickListener { toggle(false) }
    }

    private fun toggle(done: Boolean) {
        val task = repository.task(taskId) ?: return
        repository.setTaskDone(taskId, done)
        Toast.makeText(
            requireContext().applicationContext,
            if (done) getString(R.string.toast_task_done, task.title) else getString(R.string.toast_task_undone),
            Toast.LENGTH_SHORT,
        ).show()
        dismiss()
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

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }

    companion object {
        const val TAG = "TaskDetailSheet"
        private const val ARG_TASK_ID = "taskId"
        private val DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy년 M월 d일 (E)", Locale.KOREAN)

        fun newInstance(taskId: String) = TaskDetailSheet().apply {
            arguments = Bundle().apply { putString(ARG_TASK_ID, taskId) }
        }
    }
}
