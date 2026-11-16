package com.example.oreum.ui.aiplan

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import com.example.oreum.R
import com.example.oreum.data.model.DraftItem
import com.example.oreum.data.repository.StudyRepository
import com.example.oreum.data.repository.StudyRepositoryProvider
import com.example.oreum.databinding.SheetDraftItemBinding
import com.example.oreum.ui.common.GlassSheetFragment
import com.example.oreum.util.DateText
import com.google.android.material.datepicker.MaterialDatePicker
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.UUID

/** AI 계획 초안의 학습 항목 추가 · 수정 · 삭제 (승인 전이라 초안만 바뀌어요) */
class DraftItemSheet : GlassSheetFragment() {

    private var _binding: SheetDraftItemBinding? = null
    private val binding get() = checkNotNull(_binding)
    private val repository: StudyRepository = StudyRepositoryProvider.instance

    private val goalId get() = requireArguments().getString(ARG_GOAL_ID).orEmpty()
    private val itemId get() = requireArguments().getString(ARG_ITEM_ID)

    private var date: Long = 0L
    private var minutes: Int = 30

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = SheetDraftItemBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val draft = repository.drafts.value[goalId]
        if (draft == null) {
            dismiss()
            return
        }
        val original = itemId?.let { id -> draft.items.firstOrNull { it.id == id } }
        date = savedInstanceState?.getLong(STATE_DATE) ?: original?.date ?: requireArguments().getLong(ARG_DATE)
        minutes = savedInstanceState?.getInt(STATE_MINUTES) ?: original?.minutes ?: 30
        if (savedInstanceState == null) binding.inputTitle.setText(original?.title.orEmpty())

        binding.textSheetTitle.setText(if (original == null) R.string.item_title_add else R.string.item_title_edit)
        binding.btnDelete.isVisible = original != null
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
            val current = repository.drafts.value[goalId] ?: return@setOnClickListener
            repository.updateDraft(current.copy(items = current.items.filterNot { it.id == itemId }))
            dismiss()
        }
        binding.btnSave.setOnClickListener { save() }
    }

    private fun render() {
        binding.btnDate.text = getString(R.string.item_date, DateText.monthDay(LocalDate.ofEpochDay(date)))
        binding.textMinutes.text = DateText.minutes(minutes)
    }

    private fun save() {
        val title = binding.inputTitle.text?.toString()?.trim().orEmpty()
        if (title.isEmpty()) {
            binding.layoutTitle.error = getString(R.string.item_error_title)
            return
        }
        val current = repository.drafts.value[goalId] ?: return dismiss()
        // 날짜를 바꾸면 그 날짜가 속한 주 차로 옮겨요
        val start = current.createdOn
        val week = (((date - start).coerceAtLeast(0)) / 7 + 1).toInt().coerceAtMost(maxOf(1, current.weekThemes.size))
        val items = if (itemId == null) {
            current.items + DraftItem(id = "dp-" + UUID.randomUUID().toString(), title = title, minutes = minutes, date = date, week = week)
        } else {
            current.items.map { if (it.id == itemId) it.copy(title = title, minutes = minutes, date = date, week = week) else it }
        }
        repository.updateDraft(current.copy(items = items.sortedBy { it.date }))
        dismiss()
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
        const val TAG = "DraftItemSheet"
        private const val ARG_GOAL_ID = "goalId"
        private const val ARG_ITEM_ID = "itemId"
        private const val ARG_DATE = "date"
        private const val STATE_DATE = "state_date"
        private const val STATE_MINUTES = "state_minutes"

        fun add(goalId: String, date: Long) = DraftItemSheet().apply {
            arguments = Bundle().apply {
                putString(ARG_GOAL_ID, goalId)
                putLong(ARG_DATE, date)
            }
        }

        fun edit(goalId: String, itemId: String) = DraftItemSheet().apply {
            arguments = Bundle().apply {
                putString(ARG_GOAL_ID, goalId)
                putString(ARG_ITEM_ID, itemId)
            }
        }
    }
}
