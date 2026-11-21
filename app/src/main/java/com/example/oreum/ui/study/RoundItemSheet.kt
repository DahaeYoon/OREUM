package com.example.oreum.ui.study

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

/** 승인한 구간의 학습 항목 추가 · 수정 · 삭제 (이름 · 예상 시간) */
class RoundItemSheet : GlassSheetFragment() {

    private var _binding: SheetDraftItemBinding? = null
    private val binding get() = checkNotNull(_binding)
    private val repository: StudyRepository = StudyRepositoryProvider.instance

    private val roundId get() = requireArguments().getString(ARG_ROUND_ID).orEmpty()
    private val itemId get() = requireArguments().getString(ARG_ITEM_ID)

    private var minutes: Int = 30

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = SheetDraftItemBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val round = repository.round(roundId)
        if (round == null) {
            dismiss()
            return
        }
        val original = itemId?.let { id -> round.items.firstOrNull { it.id == id } }
        minutes = savedInstanceState?.getInt(STATE_MINUTES) ?: original?.minutes ?: 30
        if (savedInstanceState == null) binding.inputTitle.setText(original?.title.orEmpty())

        binding.textSheetTitle.setText(if (original == null) R.string.item_title_add else R.string.item_title_edit)
        binding.btnDelete.isVisible = original != null && round.items.size > 1
        binding.btnDate.isVisible = false
        render()

        binding.btnMinus.setOnClickListener { minutes = (minutes - 10).coerceAtLeast(10); render() }
        binding.btnPlus.setOnClickListener { minutes = (minutes + 10).coerceAtMost(600); render() }
        binding.btnCancel.setOnClickListener { dismiss() }
        binding.btnDelete.setOnClickListener {
            val current = repository.round(roundId) ?: return@setOnClickListener
            repository.updateRound(current.copy(items = current.items.filterNot { it.id == itemId }))
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
        val current = repository.round(roundId) ?: return dismiss()
        val items = if (itemId == null) {
            current.items + RoundItem(id = RoundEngine.newId("ri"), title = title, minutes = minutes)
        } else {
            current.items.map { if (it.id == itemId) it.copy(title = title, minutes = minutes) else it }
        }
        repository.updateRound(current.copy(items = items))
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
        const val TAG = "RoundItemSheet"
        private const val ARG_ROUND_ID = "roundId"
        private const val ARG_ITEM_ID = "itemId"
        private const val STATE_MINUTES = "state_minutes"

        fun add(roundId: String) = RoundItemSheet().apply {
            arguments = Bundle().apply { putString(ARG_ROUND_ID, roundId) }
        }

        fun edit(roundId: String, itemId: String) = RoundItemSheet().apply {
            arguments = Bundle().apply {
                putString(ARG_ROUND_ID, roundId)
                putString(ARG_ITEM_ID, itemId)
            }
        }
    }
}
