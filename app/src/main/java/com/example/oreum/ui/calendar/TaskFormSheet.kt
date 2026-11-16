package com.example.oreum.ui.calendar

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.core.view.isVisible
import com.example.oreum.R
import com.example.oreum.data.model.PlanStatus
import com.example.oreum.data.model.Task
import com.example.oreum.data.repository.StudyRepository
import com.example.oreum.data.repository.StudyRepositoryProvider
import com.example.oreum.databinding.SheetTaskFormBinding
import com.example.oreum.ui.common.GlassSheetFragment
import com.example.oreum.util.DateText
import com.google.android.material.datepicker.MaterialDatePicker
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/** 학습 항목 추가 · 수정 팝업 (프로토타입 itemForm): 이름 · 목표 · 날짜 · 예상 소요시간 (10분 단위) */
class TaskFormSheet : GlassSheetFragment() {

    private var _binding: SheetTaskFormBinding? = null
    private val binding get() = checkNotNull(_binding)
    private val repository: StudyRepository = StudyRepositoryProvider.instance

    private val taskId get() = requireArguments().getString(ARG_TASK_ID)
    private var date: Long = 0L
    private var minutes: Int = 30

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = SheetTaskFormBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val original = taskId?.let { repository.task(it) }
        // 항목을 넣을 수 있는 목표: 진행 중이고 계획이 있는 목표 (원래 목표는 항상 포함)
        val goals = repository.goals.value.filter {
            (it.completedAt == null && it.planStatus != PlanStatus.NONE) || it.id == original?.goalId
        }
        if (goals.isEmpty()) {
            Toast.makeText(requireContext(), R.string.task_need_goal, Toast.LENGTH_SHORT).show()
            dismiss()
            return
        }
        val goalId0 = original?.goalId ?: requireArguments().getString(ARG_GOAL_ID)?.takeIf { id -> goals.any { it.id == id } } ?: goals.first().id

        date = savedInstanceState?.getLong(STATE_DATE) ?: original?.plannedDate ?: requireArguments().getLong(ARG_DATE)
        minutes = savedInstanceState?.getInt(STATE_MINUTES) ?: original?.estimatedMinutes ?: 30
        if (savedInstanceState == null) binding.inputTitle.setText(original?.title.orEmpty())

        binding.textSheetTitle.setText(if (original == null) R.string.item_title_add else R.string.item_title_edit)
        binding.spinnerGoal.adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_dropdown_item,
            goals.map { it.title },
        )
        binding.spinnerGoal.setSelection(goals.indexOfFirst { it.id == goalId0 }.coerceAtLeast(0))
        binding.btnDelete.isVisible = original != null && !original.isDone
        render()

        binding.btnMinus.setOnClickListener { minutes = (minutes - 10).coerceAtLeast(10); render() }
        binding.btnPlus.setOnClickListener { minutes = (minutes + 10).coerceAtMost(600); render() }
        binding.btnDate.setOnClickListener {
            val picker = MaterialDatePicker.Builder.datePicker()
                .setSelection(LocalDate.ofEpochDay(date).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
                .build()
            picker.addOnPositiveButtonClickListener { millis ->
                date = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate().toEpochDay()
                render()
            }
            picker.show(childFragmentManager, "date")
        }
        binding.btnCancel.setOnClickListener { dismiss() }
        binding.btnDelete.setOnClickListener {
            original ?: return@setOnClickListener
            repository.deleteTask(original.id)
            Toast.makeText(requireContext().applicationContext, R.string.task_toast_deleted, Toast.LENGTH_SHORT).show()
            dismiss()
        }
        binding.btnSave.setOnClickListener {
            val title = binding.inputTitle.text?.toString()?.trim().orEmpty()
            binding.textTitleError.isVisible = title.isEmpty()
            if (title.isEmpty()) return@setOnClickListener
            val goal = goals[binding.spinnerGoal.selectedItemPosition.coerceIn(0, goals.lastIndex)]
            if (original == null) {
                repository.addTask(
                    Task(goalId = goal.id, title = title, estimatedMinutes = minutes, plannedDate = date, priority = goal.priority),
                )
                Toast.makeText(requireContext().applicationContext, R.string.task_toast_added, Toast.LENGTH_SHORT).show()
            } else {
                repository.updateTask(
                    original.copy(
                        goalId = goal.id,
                        title = title,
                        estimatedMinutes = minutes,
                        originalMinutes = null,
                        plannedDate = date,
                    ),
                )
                Toast.makeText(requireContext().applicationContext, R.string.task_toast_saved, Toast.LENGTH_SHORT).show()
            }
            dismiss()
        }
    }

    private fun render() {
        binding.btnDate.text = DateText.full(LocalDate.ofEpochDay(date))
        binding.textMinutes.text = DateText.minutes(minutes)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putLong(STATE_DATE, date)
        outState.putInt(STATE_MINUTES, minutes)
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }

    companion object {
        const val TAG = "TaskFormSheet"
        private const val ARG_TASK_ID = "taskId"
        private const val ARG_GOAL_ID = "goalId"
        private const val ARG_DATE = "date"
        private const val STATE_DATE = "state_date"
        private const val STATE_MINUTES = "state_minutes"

        fun add(date: Long, goalId: String?) = TaskFormSheet().apply {
            arguments = Bundle().apply {
                putLong(ARG_DATE, date)
                putString(ARG_GOAL_ID, goalId)
            }
        }

        fun edit(taskId: String) = TaskFormSheet().apply {
            arguments = Bundle().apply { putString(ARG_TASK_ID, taskId) }
        }
    }
}
