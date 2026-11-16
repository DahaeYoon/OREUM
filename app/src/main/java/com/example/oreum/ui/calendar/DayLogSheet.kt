package com.example.oreum.ui.calendar

import android.os.Bundle
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.graphics.Typeface
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.core.view.isVisible
import com.example.oreum.R
import com.example.oreum.data.model.DayLogEntry
import com.example.oreum.data.model.MissReason
import com.example.oreum.data.model.RetroTag
import com.example.oreum.data.model.Task
import com.example.oreum.data.repository.StudyRepository
import com.example.oreum.data.repository.StudyRepositoryProvider
import com.example.oreum.databinding.ItemLogRowBinding
import com.example.oreum.databinding.SheetDayLogBinding
import com.example.oreum.domain.usecase.PlanCalendar
import com.example.oreum.ui.common.GlassSheetFragment
import com.example.oreum.util.DateText
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

/**
 * 학습 결과 기록 팝업 (프로토타입 logSheet)
 * - 항목별: 완료 체크 · 실제 시간(10분 단위) · 못 끝냈으면 이유(필수)
 * - 회고 키워드(여러 개 · 선택) · 한 줄 메모(선택)
 * - 저장 후 AI 복구가 필요한 미완료가 있으면 복구를 권해요 (승인 전에는 계획이 바뀌지 않아요)
 */
class DayLogSheet : GlassSheetFragment() {

    private var _binding: SheetDayLogBinding? = null
    private val binding get() = checkNotNull(_binding)
    private val repository: StudyRepository = StudyRepositoryProvider.instance
    private val date: Long get() = requireArguments().getLong(ARG_DATE)

    private data class RowState(var done: Boolean, var minutes: Int, var reason: MissReason?)

    private lateinit var items: List<Task>
    private val states = linkedMapOf<String, RowState>()
    private val tags = linkedSetOf<RetroTag>()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = SheetDayLogBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        items = PlanCalendar.dayItems(repository.tasks.value, date, null).filterNot { it.carriedOver }
        if (items.isEmpty()) {
            Toast.makeText(requireContext(), R.string.log_no_items, Toast.LENGTH_SHORT).show()
            dismiss()
            return
        }
        items.forEach { t ->
            states[t.id] = RowState(
                done = t.isDone,
                minutes = if (t.isDone) t.actualMinutes ?: t.estimatedMinutes else t.actualMinutes ?: 0,
                reason = t.missReason,
            )
        }
        val log = repository.logs.value.firstOrNull { it.date == date }
        log?.let {
            tags += it.tags
            binding.inputMemo.setText(it.memo)
        }

        val day = LocalDate.ofEpochDay(date)
        binding.textTitle.text = getString(
            R.string.log_title,
            DateText.monthDay(day),
            day.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.KOREAN),
        )

        // 회고 키워드 (여러 개 선택)
        RetroTag.entries.forEach { tag ->
            val chip = layoutInflater.inflate(R.layout.item_tag_chip, binding.groupTags, false) as TextView
            chip.text = tag.label
            chip.isSelected = tag in tags
            chip.setOnClickListener {
                if (!tags.remove(tag)) tags += tag
                chip.isSelected = tag in tags
            }
            binding.groupTags.addView(chip)
        }

        binding.btnCancel.setOnClickListener { dismiss() }
        binding.btnSave.setOnClickListener { save() }
        draw()
    }

    private fun draw() {
        val planned = items.sumOf { it.estimatedMinutes }
        val actual = states.values.sumOf { it.minutes }
        val percent = (actual.toFloat() / maxOf(1, planned) * 100).toInt().coerceAtMost(100)
        binding.textSum.text = SpannableStringBuilder().apply {
            append("실제 ")
            val s = length
            append(DateText.minutes(actual))
            setSpan(StyleSpan(Typeface.BOLD), s, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            setSpan(ForegroundColorSpan(requireContext().getColor(R.color.oreum_dark_text)), s, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            append(" / 계획 ")
            append(DateText.minutes(planned))
        }
        binding.textPercent.text = "$percent%"
        binding.progress.progress = percent

        binding.rowList.removeAllViews()
        items.forEach { task ->
            val st = states.getValue(task.id)
            val row = ItemLogRowBinding.inflate(layoutInflater, binding.rowList, false)
            row.root.setBackgroundResource(if (st.done) R.drawable.bg_log_row_ok else R.drawable.bg_log_row)
            row.box.setBackgroundResource(if (st.done) R.drawable.bg_log_check_on else R.drawable.bg_check_off)
            row.iconCheck.isVisible = st.done
            row.btnCheck.contentDescription = getString(R.string.task_done) + " " + task.title
            row.textTitle.text = task.title
            row.textPlanned.text = getString(R.string.log_planned, DateText.minutes(task.estimatedMinutes))
            row.textMinutes.text = DateText.minutes(st.minutes)

            row.btnCheck.setOnClickListener {
                st.done = !st.done
                if (st.done && st.minutes == 0) st.minutes = task.estimatedMinutes
                if (st.done) st.reason = null
                draw()
            }
            row.btnMinus.setOnClickListener { st.minutes = (st.minutes - 10).coerceAtLeast(0); draw() }
            row.btnPlus.setOnClickListener { st.minutes = (st.minutes + 10).coerceAtMost(600); draw() }

            // 못 끝냈으면 이유 고르기
            row.groupReason.isVisible = !st.done
            if (!st.done) {
                MissReason.entries.forEach { reason ->
                    val chip = layoutInflater.inflate(R.layout.item_reason_chip, row.groupReasons, false) as TextView
                    chip.text = reason.short
                    chip.isSelected = st.reason == reason
                    chip.setOnClickListener { st.reason = reason; draw() }
                    row.groupReasons.addView(chip)
                }
            }
            binding.rowList.addView(row.root)
        }
    }

    private fun save() {
        val missing = items.count { val s = states.getValue(it.id); !s.done && s.reason == null }
        if (missing > 0) {
            Toast.makeText(requireContext(), getString(R.string.log_need_reason, missing), Toast.LENGTH_SHORT).show()
            return
        }
        repository.saveDayLog(
            date = date,
            entries = items.map { t ->
                val s = states.getValue(t.id)
                DayLogEntry(taskId = t.id, done = s.done, actualMinutes = s.minutes, reason = if (s.done) null else s.reason)
            },
            tags = tags.toList(),
            memo = binding.inputMemo.text?.toString()?.trim().orEmpty(),
        )
        val parent = parentFragment as? RecoveryOpener
        val context = requireActivity()
        dismiss()

        val pending = PlanCalendar.pendingRecovery(repository.tasks.value)
        if (pending.isNotEmpty()) {
            MaterialAlertDialogBuilder(context)
                .setTitle(context.getString(R.string.log_pending_title, pending.size))
                .setMessage(R.string.log_pending_body)
                .setNegativeButton(R.string.log_pending_later, null)
                .setPositiveButton(R.string.log_pending_ok) { _, _ -> parent?.openRecovery() }
                .show()
        } else {
            Toast.makeText(context, R.string.log_saved, Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }

    companion object {
        const val TAG = "DayLogSheet"
        private const val ARG_DATE = "date"

        fun newInstance(date: Long) = DayLogSheet().apply {
            arguments = Bundle().apply { putLong(ARG_DATE, date) }
        }
    }
}
