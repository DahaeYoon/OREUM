package com.example.oreum.ui.study

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.example.oreum.R
import com.example.oreum.data.model.GoalLevel
import com.example.oreum.data.model.GoalPeriod
import com.example.oreum.databinding.FragmentGoalFormBinding
import com.example.oreum.ui.auth.setTextIfChanged
import com.example.oreum.ui.common.GlassSheetFragment
import com.example.oreum.util.DateText
import com.example.oreum.util.onChildClick
import com.example.oreum.util.selectChild
import com.google.android.material.datepicker.MaterialDatePicker
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/** STEP 2 목표 만들기 · 수정 팝업 (프로토타입 '새 학습 목표' 시트): 6개 항목 입력 → 저장 */
class GoalFormFragment : GlassSheetFragment() {

    private var _binding: FragmentGoalFormBinding? = null
    private val binding get() = checkNotNull(_binding)
    private val viewModel: GoalFormViewModel by viewModels()

    private val periods = listOf(GoalPeriod.DAILY, GoalPeriod.WEEKLY, GoalPeriod.MONTHLY)
    private val levels = listOf(GoalLevel.BEGINNER, GoalLevel.INTERMEDIATE, GoalLevel.ADVANCED)
    private val priorities = listOf(1, 2, 3)

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentGoalFormBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.periodDaily.text = getString(R.string.study_card_meta_period, GoalPeriod.DAILY.label)
        binding.periodWeekly.text = getString(R.string.study_card_meta_period, GoalPeriod.WEEKLY.label)
        binding.periodMonthly.text = getString(R.string.study_card_meta_period, GoalPeriod.MONTHLY.label)
        binding.levelBeginner.text = GoalLevel.BEGINNER.label
        binding.levelIntermediate.text = GoalLevel.INTERMEDIATE.label
        binding.levelAdvanced.text = GoalLevel.ADVANCED.label
        binding.priorityHigh.setText(R.string.study_priority_high)
        binding.priorityMid.setText(R.string.study_priority_mid)
        binding.priorityLow.setText(R.string.study_priority_low)

        binding.btnCancel.setOnClickListener { dismiss() }
        binding.btnSave.setOnClickListener { viewModel.save() }
        binding.inputTitle.doAfterTextChanged { viewModel.onTitleChanged(it?.toString().orEmpty()) }
        binding.inputDesc.doAfterTextChanged { viewModel.onDescriptionChanged(it?.toString().orEmpty()) }

        binding.segPeriod.onChildClick { viewModel.setPeriod(periods[it]) }
        binding.segLevel.onChildClick { viewModel.setLevel(levels[it]) }
        binding.segPriority.onChildClick { viewModel.setPriority(priorities[it]) }

        binding.btnWeekdayMinus.setOnClickListener { viewModel.changeWeekday(-STEP_MINUTES) }
        binding.btnWeekdayPlus.setOnClickListener { viewModel.changeWeekday(STEP_MINUTES) }
        binding.btnWeekendMinus.setOnClickListener { viewModel.changeWeekend(-STEP_MINUTES) }
        binding.btnWeekendPlus.setOnClickListener { viewModel.changeWeekend(STEP_MINUTES) }

        binding.btnStart.setOnClickListener { pickDate(viewModel.uiState.value.startDate) { viewModel.setStartDate(it) } }
        binding.btnDue.setOnClickListener { pickDate(viewModel.uiState.value.dueDate) { viewModel.setDueDate(it) } }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect(::render)
            }
        }
    }

    private fun render(state: GoalFormUiState) {
        binding.textScreenTitle.setText(if (state.isEdit) R.string.study_form_title_edit else R.string.study_form_title_new)
        binding.btnSave.setText(if (state.isEdit) R.string.study_form_save_edit else R.string.study_form_save_new)
        binding.inputTitle.setTextIfChanged(state.title)
        binding.inputDesc.setTextIfChanged(state.description)
        binding.textTitleError.isVisible = state.titleError != null

        binding.segPeriod.selectChild(periods.indexOf(state.period))
        binding.segLevel.selectChild(levels.indexOf(state.level))
        binding.segPriority.selectChild(priorities.indexOf(state.priority))

        binding.btnStart.text = DateText.full(state.startDate)
        binding.btnDue.text = DateText.full(state.dueDate)
        binding.textWeekday.text = DateText.minutes(state.weekdayMinutes)
        binding.textWeekend.text = DateText.minutes(state.weekendMinutes)

        binding.textFormError.isVisible = state.formError != null
        state.formError?.let { binding.textFormError.setText(it) }

        state.savedMessage?.let { message ->
            val reviewGoalId = state.reviewGoalId
            viewModel.onClosed()
            Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
            if (reviewGoalId != null) {
                // 새 목표 → 팝업을 닫고 AI 계획 생성 결과로 (승인 전에는 적용 안 함)
                findNavController().navigate(
                    R.id.action_goalForm_to_planReview,
                    Bundle().apply { putString(GoalFormViewModel.ARG_GOAL_ID, reviewGoalId) },
                )
            } else {
                dismiss()
            }
        }
    }

    /** MaterialDatePicker는 UTC 자정 기준 millis를 써요. */
    private fun pickDate(current: LocalDate, onPicked: (LocalDate) -> Unit) {
        val picker = MaterialDatePicker.Builder.datePicker()
            .setSelection(current.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
            .build()
        picker.addOnPositiveButtonClickListener { millis ->
            onPicked(Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate())
        }
        picker.show(childFragmentManager, "date")
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }

    private companion object {
        const val STEP_MINUTES = 30
    }
}
