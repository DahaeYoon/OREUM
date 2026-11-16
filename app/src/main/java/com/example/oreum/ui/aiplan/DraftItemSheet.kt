package com.example.oreum.ui.aiplan

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import com.example.oreum.R
import com.example.oreum.data.model.RoundItem
import com.example.oreum.data.repository.StudyRepository
import com.example.oreum.data.repository.StudyRepositoryProvider
import com.example.oreum.databinding.SheetDraftItemBinding
import com.example.oreum.domain.usecase.RoundEngine
import com.example.oreum.ui.common.GlassSheetFragment
import com.example.oreum.util.DateText

/** AI 1구간 초안의 학습 항목 추가 · 수정 · 삭제 (승인 전이라 초안만 바뀌어요. 구간은 날짜가 없어서 이름 · 시간만) */
class DraftItemSheet : GlassSheetFragment() {

    private var _binding: SheetDraftItemBinding? = null
    private val binding get() = checkNotNull(_binding)
    private val repository: StudyRepository = StudyRepositoryProvider.instance

    private val goalId get() = requireArguments().getString(ARG_GOAL_ID).orEmpty()
    private val itemId get() = requireArguments().getString(ARG_ITEM_ID)

    private var minutes: Int = 30

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = SheetDraftItemBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val draft = repository.roadmapDrafts.value[goalId]
        if (draft == null) {
            dismiss()
            return
        }
        val original = itemId?.let { id -> draft.round.items.firstOrNull { it.id == id } }
        minutes = savedInstanceState?.getInt(STATE_MINUTES) ?: original?.minutes ?: 30
        if (savedInstanceState == null) binding.inputTitle.setText(original?.title.orEmpty())

        binding.textSheetTitle.setText(if (original == null) R.string.item_title_add else R.string.item_title_edit)
        binding.btnDelete.isVisible = original != null
        // 구간 항목에는 날짜가 없어요
        binding.btnDate.isVisible = false
        render()

        binding.btnMinus.setOnClickListener { minutes = (minutes - 10).coerceAtLeast(10); render() }
        binding.btnPlus.setOnClickListener { minutes = (minutes + 10).coerceAtMost(600); render() }
        binding.btnCancel.setOnClickListener { dismiss() }
        binding.btnDelete.setOnClickListener {
            val current = repository.roadmapDrafts.value[goalId] ?: return@setOnClickListener
            repository.updateRoadmapDraft(current.copy(round = current.round.copy(items = current.round.items.filterNot { it.id == itemId })))
            dismiss()
        }
        binding.btnSave.setOnClickListener { save() }
    }

    private fun render() {
        binding.textMinutes.text = DateText.minutes(minutes)
    }

    private fun save() {
        val title = binding.inputTitle.text?.toString()?.trim().orEmpty()
        if (title.isEmpty()) {
            binding.layoutTitle.error = getString(R.string.item_error_title)
            return
        }
        val current = repository.roadmapDrafts.value[goalId] ?: return dismiss()
        val items = if (itemId == null) {
            current.round.items + RoundItem(id = RoundEngine.newId("ri"), title = title, minutes = minutes)
        } else {
            current.round.items.map { if (it.id == itemId) it.copy(title = title, minutes = minutes) else it }
        }
        repository.updateRoadmapDraft(current.copy(round = current.round.copy(items = items)))
        dismiss()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
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
        private const val STATE_MINUTES = "state_minutes"

        fun add(goalId: String) = DraftItemSheet().apply {
            arguments = Bundle().apply { putString(ARG_GOAL_ID, goalId) }
        }

        fun edit(goalId: String, itemId: String) = DraftItemSheet().apply {
            arguments = Bundle().apply {
                putString(ARG_GOAL_ID, goalId)
                putString(ARG_ITEM_ID, itemId)
            }
        }
    }
}
