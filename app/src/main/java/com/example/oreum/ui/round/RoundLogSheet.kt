package com.example.oreum.ui.round

import android.graphics.Typeface
import android.os.Bundle
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.view.children
import androidx.core.view.isVisible
import androidx.navigation.fragment.findNavController
import com.example.oreum.R
import com.example.oreum.data.model.Difficulty
import com.example.oreum.data.model.MissReason
import com.example.oreum.data.model.RoundResult
import com.example.oreum.data.model.RoundStatus
import com.example.oreum.data.model.StudyRound
import com.example.oreum.data.repository.AiRequests
import com.example.oreum.data.repository.RoundLogEntry
import com.example.oreum.data.repository.StudyRepository
import com.example.oreum.data.repository.StudyRepositoryProvider
import com.example.oreum.databinding.ItemLogRowBinding
import com.example.oreum.databinding.SheetRoundLogBinding
import com.example.oreum.ui.common.GlassSheetFragment
import com.example.oreum.util.DateText
import com.example.oreum.util.selectChild
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.time.LocalDate
import com.example.oreum.util.showToast
import com.example.oreum.util.AppToast

/**
 * 구간 학습 결과 기록 (프로토타입 roundLogSheet · 1차)
 * - 항목별: 완료 체크 · 오늘 공부한 시간(10분 단위) · 못 끝냈으면 이유
 * - 체감 난이도 · 한 줄 메모 (회고 태그는 2차)
 * - 오늘 기록 저장: 구간은 그대로 이어가요 (날짜가 바뀌어도 다음 구간으로 넘어가지 않아요)
 * - 구간 결과 제출: 이유 · 난이도가 모두 있어야 해요. 제출하면 AI가 다음 구간을 추천해요 (승인 전에는 확정되지 않음)
 */
class RoundLogSheet : GlassSheetFragment() {

    private var _binding: SheetRoundLogBinding? = null
    private val binding get() = checkNotNull(_binding)
    private val repository: StudyRepository = StudyRepositoryProvider.instance
    private val roundId: String get() = requireArguments().getString(ARG_ROUND_ID).orEmpty()

    private data class RowState(var done: Boolean, var minutes: Int, var reason: MissReason?)

    private lateinit var round: StudyRound
    private val states = linkedMapOf<String, RowState>()
    /** 오늘 말고 이전에 공부한 시간 (항목별) */
    private val before = mutableMapOf<String, Int>()
    private var difficulty: Difficulty? = null
    /** 못 끝낸 이유 칸 보이기 (제출하려고 할 때부터) */
    private var showReasons = false

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = SheetRoundLogBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val r = repository.round(roundId)
        if (r == null || r.status == RoundStatus.DONE || r.items.isEmpty()) {
            dismiss()
            return
        }
        round = r
        showReasons = requireArguments().getBoolean(ARG_SUBMIT)
        val today = today()
        val sessions = repository.sessions.value
        r.items.forEach { item ->
            val todayMin = sessions.filter { it.itemId == item.id && it.date == today }.sumOf { it.minutes }
            before[item.id] = (item.actualMinutes - todayMin).coerceAtLeast(0)
            states[item.id] = RowState(item.done, todayMin, item.reason)
        }
        difficulty = r.result?.difficulty
        binding.inputMemo.setText(r.result?.memo.orEmpty())

        val goal = repository.goals.value.firstOrNull { it.id == r.goalId }
        binding.textTitle.text = getString(R.string.round_log_title, r.no)
        binding.textCaption.text = getString(
            R.string.round_log_caption,
            goal?.title.orEmpty(),
            DateText.monthDay(LocalDate.ofEpochDay(today)),
        )

        // 체감 난이도: 쉬움 · 적당 · 어려움
        binding.segDiff.children.forEachIndexed { i, v ->
            val d = Difficulty.entries[i]
            (v as TextView).text = d.label
            v.setOnClickListener {
                difficulty = d
                draw()
            }
        }

        binding.btnSave.setOnClickListener { save() }
        binding.btnSubmit.setOnClickListener { submit() }
        draw()
    }

    private fun draw() {
        val items = round.items
        val todayTotal = states.values.sumOf { it.minutes }
        val all = items.sumOf { (before[it.id] ?: 0) + states.getValue(it.id).minutes }
        val est = items.sumOf { it.minutes }
        val doneN = items.count { states.getValue(it.id).done }

        // 오늘 X · 이 구간 누적 Y / 예상 Z      완료 k/n
        binding.textSum.text = SpannableStringBuilder().apply {
            append(getString(R.string.round_log_today))
            append(" ")
            val s = length
            append(DateText.minutes(todayTotal))
            setSpan(StyleSpan(Typeface.BOLD), s, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            setSpan(ForegroundColorSpan(requireContext().getColor(R.color.oreum_dark_text)), s, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            append(getString(R.string.round_log_total, DateText.minutes(all), DateText.minutes(est)))
        }
        binding.textCount.text = getString(R.string.round_done_count, doneN, items.size)
        binding.progress.progress = (all * 100 / maxOf(1, est)).coerceAtMost(100)

        // 제출하려고 할 때 70% 미만이면 경고 한 줄 (연속 미완료 판단은 2차)
        val miss = showReasons && doneN * 10 < items.size * 7
        binding.boxMiss.isVisible = miss
        if (miss) binding.textMiss.text = getString(R.string.round_log_miss_title, items.size - doneN)

        binding.rowList.removeAllViews()
        items.forEach { item ->
            val st = states.getValue(item.id)
            val row = ItemLogRowBinding.inflate(layoutInflater, binding.rowList, false)
            row.root.setBackgroundResource(if (st.done) R.drawable.bg_log_row_ok else R.drawable.bg_log_row)
            row.box.setBackgroundResource(if (st.done) R.drawable.bg_log_check_on else R.drawable.bg_check_off)
            row.iconCheck.isVisible = st.done
            row.btnCheck.contentDescription = getString(R.string.round_item_check, item.title)
            row.textTitle.text = item.title.removeSuffix(" (이어서)")
            val prev = before[item.id] ?: 0
            row.textPlanned.text = buildString {
                append(getString(R.string.round_review_est, DateText.minutes(item.minutes)))
                if (prev > 0) append(getString(R.string.round_log_prev, DateText.minutes(prev)))
            }
            row.textMinutes.text = DateText.minutes(st.minutes)

            row.btnCheck.setOnClickListener {
                st.done = !st.done
                if (st.done && st.minutes == 0 && prev == 0) st.minutes = item.minutes
                if (st.done) st.reason = null
                draw()
            }
            row.btnMinus.setOnClickListener { st.minutes = (st.minutes - 10).coerceAtLeast(0); draw() }
            row.btnPlus.setOnClickListener { st.minutes = (st.minutes + 10).coerceAtMost(600); draw() }

            // 못 끝낸 이유 (제출하려고 할 때부터 보여요)
            row.groupReason.isVisible = showReasons && !st.done
            if (showReasons && !st.done) {
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

        binding.segDiff.selectChild(difficulty?.let { Difficulty.entries.indexOf(it) } ?: -1)
    }

    private fun entries(): List<RoundLogEntry> = round.items.map { item ->
        val s = states.getValue(item.id)
        RoundLogEntry(itemId = item.id, done = s.done, todayMinutes = s.minutes, reason = if (s.done) null else s.reason)
    }

    private fun memo() = binding.inputMemo.text?.toString()?.trim().orEmpty()

    /** 오늘 기록 저장: 구간은 그대로 이어가요 */
    private fun save() {
        repository.saveRoundLog(round.id, entries(), RoundResult(difficulty, memo()), today())
        requireActivity().showToast(getString(R.string.toast_round_log_saved, round.no))
        dismiss()
    }

    /** 구간 결과 제출 → AI 다음 구간 추천 (승인 전에는 확정되지 않아요) */
    private fun submit() {
        val missing = round.items.filter { val s = states.getValue(it.id); !s.done && s.reason == null }
        if (missing.isNotEmpty() || difficulty == null) {
            showReasons = true
            draw()
            val msg = if (difficulty == null) getString(R.string.round_log_need_diff) else getString(R.string.log_need_reason, missing.size)
            AppToast.showInDialog(this, msg)
            scrollTo(if (missing.isNotEmpty()) binding.rowList.getChildAt(round.items.indexOf(missing.first())) else binding.rowDiffLabel)
            return
        }
        repository.submitRound(round.id, entries(), RoundResult(difficulty, memo()), today()) ?: return
        val context = requireActivity()
        val nav = runCatching { findNavController() }.getOrNull()
        val goalId = round.goalId
        val no = round.no
        // 바로 AI에게 다음 구간 추천을 요청해요 (화면을 닫아도 이어져요). 로드맵을 다 마쳤으면 AI 없이 '정상 도착' 안내만 저장돼요
        AiRequests.requestNext(goalId)
        val final = repository.proposals.value[goalId]?.final == true
        dismiss()

        MaterialAlertDialogBuilder(context)
            .setTitle(context.getString(R.string.round_submitted_title, no))
            .setMessage(if (final) R.string.round_submitted_final else R.string.round_submitted_body)
            .setNegativeButton(R.string.round_submitted_later, null)
            .setPositiveButton(R.string.round_submitted_ok) { _, _ -> if (nav != null) NextRoundFragment.open(nav, goalId) }
            .show()
    }

    private fun scrollTo(target: View?) {
        target ?: return
        binding.scroll.post {
            val b = _binding ?: return@post
            var y = 0
            var v: View? = target
            while (v != null && v !== b.scroll) {
                y += v.top
                v = v.parent as? View
            }
            b.scroll.smoothScrollTo(0, (y - b.scroll.height / 3).coerceAtLeast(0))
        }
    }

    private fun today() = LocalDate.now().toEpochDay()

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }

    companion object {
        const val TAG = "RoundLogSheet"
        private const val ARG_ROUND_ID = "roundId"
        private const val ARG_SUBMIT = "submit"

        /** submit = true: 항목을 모두 끝냈을 때처럼 '못 끝낸 이유'까지 바로 보여 줘요 */
        fun newInstance(roundId: String, submit: Boolean = false) = RoundLogSheet().apply {
            arguments = Bundle().apply {
                putString(ARG_ROUND_ID, roundId)
                putBoolean(ARG_SUBMIT, submit)
            }
        }
    }
}
