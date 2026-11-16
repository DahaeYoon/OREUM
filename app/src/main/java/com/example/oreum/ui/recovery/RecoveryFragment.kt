package com.example.oreum.ui.recovery

import android.content.res.ColorStateList
import android.graphics.Paint
import android.graphics.Typeface
import android.os.Bundle
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.TextUtils
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.view.Gravity
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.ColorInt
import androidx.core.view.doOnLayout
import androidx.core.view.isVisible
import androidx.core.widget.NestedScrollView
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.example.oreum.R
import com.example.oreum.databinding.FragmentRecoveryBinding
import com.example.oreum.domain.usecase.RecoveryPlanner
import com.example.oreum.ui.calendar.PlanFocus
import com.example.oreum.ui.common.GlassBackdrop
import com.example.oreum.ui.common.GlassBackgroundDrawable
import com.example.oreum.util.DateText
import com.example.oreum.util.Season
import com.example.oreum.util.applySystemBarsPadding
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

/**
 * STEP 5 AI 학습 계획 복구 (프로토타입 r5 · 시뮬레이션)
 * 산 배경 위 유리 팝업: 원인(이유 · 회고 키워드 · 메모) → 이렇게 바꿀게요 → 바뀌는 계획 → 취소 / 복구 승인.
 * '복구 승인'을 누르고 확인해야만 계획이 바뀌어요.
 */
class RecoveryFragment : Fragment(R.layout.fragment_recovery) {

    private var _binding: FragmentRecoveryBinding? = null
    private val binding get() = checkNotNull(_binding)
    private val viewModel: RecoveryViewModel by viewModels()
    private var backdrop: GlassBackdrop? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentRecoveryBinding.bind(view)

        binding.bgMountain.setImageResource(Season.of().mountainBackground)
        binding.wrap.applySystemBarsPadding(top = true, bottom = true)

        // 유리 팝업: 뒤의 산을 흐리게 찍어 팝업 자리에 그려요 (프로토타입 backdrop-filter: blur(22px) saturate(1.15))
        binding.bgMountain.doOnLayout { bg ->
            val b = _binding ?: return@doOnLayout
            backdrop = GlassBackdrop.capture(bg)
            b.card.background = GlassBackgroundDrawable(
                host = b.card,
                backdrop = backdrop,
                radiusPx = dp(26).toFloat(),
                strokePx = dp(1).toFloat(),
                topOnly = false,
                fillColor = 0x99FFFFFF.toInt(),
                dimColor = 0x141E322D,
                strokeColor = 0xD9FFFFFF.toInt(),
            )
        }
        // 스크롤하면 팝업 뒤 흐린 산 위치를 다시 맞춰요
        binding.scroll.setOnScrollChangeListener(NestedScrollView.OnScrollChangeListener { _, _, _, _, _ -> _binding?.card?.invalidate() })

        binding.btnClose.setOnClickListener { findNavController().navigateUp() }
        binding.btnEmptyOk.setOnClickListener { findNavController().navigateUp() }
        binding.btnCancel.setOnClickListener {
            toast(getString(R.string.toast_rec_kept))
            findNavController().navigateUp()
        }
        binding.btnMore.setOnClickListener {
            viewModel.openMore()
            binding.inputExtra.post {
                val input = _binding?.inputExtra ?: return@post
                input.requestFocus()
                requireContext().getSystemService(InputMethodManager::class.java)?.showSoftInput(input, 0)
            }
        }
        binding.btnReanalyze.setOnClickListener { reanalyze() }
        binding.inputExtra.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) { reanalyze(); true } else false
        }
        binding.btnCompare.setOnClickListener { viewModel.toggleCompare() }
        binding.btnAdjustGoal.paintFlags = binding.btnAdjustGoal.paintFlags or Paint.UNDERLINE_TEXT_FLAG
        binding.btnAdjustGoal.setOnClickListener {
            val goalId = viewModel.uiState.value.plan?.goalIds?.firstOrNull() ?: return@setOnClickListener
            findNavController().navigate(R.id.action_recovery_to_goalForm, Bundle().apply { putString("goalId", goalId) })
        }
        binding.btnApprove.setOnClickListener { confirmApprove() }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect(::render)
            }
        }
    }

    private fun render(state: RecoveryUiState) {
        if (!state.loaded) return
        val plan = state.plan
        binding.groupPlan.isVisible = plan != null
        binding.groupEmpty.isVisible = plan == null
        binding.textAiTag.isVisible = plan != null
        if (plan == null) return

        // 계획 P 중 A 수행 · 남은 분량 R
        binding.textLead.text = SpannableStringBuilder().apply {
            append(getString(R.string.rec_lead_planned, DateText.minutes(plan.plannedMinutes)))
            bold(DateText.minutes(plan.actualMinutes))
            append(getString(R.string.rec_lead_done))
            bold(DateText.minutes(plan.remainingMinutes))
        }

        // 원인: 미완료 이유(코랄) · 아쉬운 키워드(흰) · 좋았던 키워드(민트)
        binding.chipsCause.removeAllViews()
        plan.reasons.forEach { addCauseChip(it.short, 0xFFFCEDE6.toInt(), 0xFFA9634C.toInt()) }
        plan.badTags.forEach { addCauseChip(it.label, 0xD9FFFFFF.toInt(), color(R.color.oreum_ink2)) }
        plan.goodTags.forEach { addCauseChip(it.label, 0xFFE3F1EC.toInt(), 0xFF2F8A6F.toInt()) }
        binding.textMemo.isVisible = plan.memos.isNotEmpty()
        binding.textMemo.text = getString(R.string.rec_memo_quote, plan.memos.joinToString(" / "))
        fillList(binding.listWhy, plan.why, "•", color(R.color.oreum_ink3), 16)
        val recent = plan.recentRate
        binding.textRecent.isVisible = recent != null && recent < 0.7f
        if (recent != null) binding.textRecent.text = getString(R.string.rec_recent, (recent * 100).toInt())

        // 이렇게 바꿀게요
        fillList(binding.listFix, plan.fixes, "✓", 0xFF2F8A6F.toInt(), 20)
        binding.btnMore.isVisible = !state.moreOpen
        binding.rowExtra.isVisible = state.moreOpen
        if (!binding.inputExtra.hasFocus() && binding.inputExtra.text.toString() != plan.extraNote) {
            binding.inputExtra.setText(plan.extraNote)
        }

        // 바뀌는 계획
        binding.btnCompare.setText(if (state.compare) R.string.rec_compare_simple else R.string.rec_compare_full)
        binding.listDays.removeAllViews()
        if (state.compare) renderCompare(plan) else renderChanges(plan)

        binding.boxUnplaced.isVisible = plan.unplacedMinutes > 0
        binding.textUnplaced.text = getString(R.string.rec_unplaced, DateText.minutes(plan.unplacedMinutes))
    }

    /** 간단히: 날마다 '+ 새로 추가 · ↪ 옮겨 온 항목 · → 뒤로 미룬 항목' */
    private fun renderChanges(plan: RecoveryPlanner.Plan) {
        var first = true
        plan.days.forEach { day ->
            val changed = day.after.filter { it.isNew || it.moved }
            val gone = day.before.filter { it.gone }
            if (changed.isEmpty() && gone.isEmpty()) return@forEach
            val block = LinearLayout(requireContext()).apply { orientation = LinearLayout.VERTICAL }
            if (!first) {
                block.addView(divider(0x0F1F2D2A), LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(1)).apply {
                    topMargin = dp(10); bottomMargin = dp(8)
                })
            }
            first = false

            // 11/6 (금)                3시간 30분 → 3시간
            val head = LinearLayout(requireContext()).apply { orientation = LinearLayout.HORIZONTAL }
            head.addView(text(dayLabel(day.date), 12f, color(R.color.oreum_ink2), bold = true), LinearLayout.LayoutParams(0, -2, 1f))
            head.addView(text(SpannableStringBuilder().apply {
                append(DateText.minutes(day.beforeMinutes) + " → ")
                bold(DateText.minutes(day.afterMinutes))
            }, 12f, color(R.color.oreum_ink2)))
            block.addView(head, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(4) })

            changed.forEach { line ->
                val c = if (line.isNew) GREEN else BLUE
                block.addView(changeRow(if (line.isNew) "+" else "↪", line.title, DateText.minutes(line.minutes), c, c, strike = false))
            }
            if (gone.isNotEmpty()) {
                val ink3 = color(R.color.oreum_ink3)
                block.addView(changeRow("→", gone.joinToString(", ") { it.title }, getString(R.string.rec_back_later), ink3, ink3, strike = true))
            }
            binding.listDays.addView(block)
        }
        if (binding.listDays.childCount == 0) {
            binding.listDays.addView(text(getString(R.string.rec_no_changes), 11.5f, color(R.color.oreum_ink3)))
        }
    }

    /** 전체 비교: 날마다 기존 | 수정 두 칸 */
    private fun renderCompare(plan: RecoveryPlanner.Plan) {
        plan.days.forEachIndexed { i, day ->
            val block = LinearLayout(requireContext()).apply { orientation = LinearLayout.VERTICAL }
            if (i > 0) block.addView(divider(0x0F000000), LinearLayout.LayoutParams(-1, dp(1)).apply { bottomMargin = dp(8) })
            val head = LinearLayout(requireContext()).apply { orientation = LinearLayout.HORIZONTAL }
            head.addView(text(DateText.monthDay(LocalDate.ofEpochDay(day.date)), 12.5f, color(R.color.oreum_dark_text), bold = true), LinearLayout.LayoutParams(0, -2, 1f))
            head.addView(text("${DateText.minutes(day.beforeMinutes)} → ${DateText.minutes(day.afterMinutes)}", 12.5f, color(R.color.oreum_dark_text)))
            block.addView(head, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(6) })

            val grid = LinearLayout(requireContext()).apply { orientation = LinearLayout.HORIZONTAL }
            val old = compareColumn(getString(R.string.rec_old), R.drawable.bg_rec_cmp_old)
            if (day.before.isEmpty()) old.addView(text(getString(R.string.rec_none), 11.5f, color(R.color.oreum_ink3)))
            day.before.forEach { line ->
                old.addView(cmpLine(line.title, if (line.gone) color(R.color.oreum_ink3) else color(R.color.oreum_dark_text), strike = line.gone, bold = false))
            }
            val new = compareColumn(getString(R.string.rec_new), R.drawable.bg_rec_cmp_new)
            day.after.forEach { line ->
                val c = when {
                    line.isNew -> GREEN
                    line.moved -> BLUE
                    else -> color(R.color.oreum_dark_text)
                }
                new.addView(cmpLine(line.title, c, strike = false, bold = line.isNew))
            }
            grid.addView(old, LinearLayout.LayoutParams(0, -1, 1f))
            grid.addView(new, LinearLayout.LayoutParams(0, -1, 1f).apply { marginStart = dp(6) })
            block.addView(grid, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8) })
            binding.listDays.addView(block)
        }
    }

    private fun compareColumn(label: String, bg: Int) = LinearLayout(requireContext()).apply {
        orientation = LinearLayout.VERTICAL
        setBackgroundResource(bg)
        setPadding(dp(8), dp(6), dp(8), dp(6))
        addView(text(label, 10.5f, color(R.color.oreum_ink3), bold = true))
    }

    private fun cmpLine(title: String, @ColorInt c: Int, strike: Boolean, bold: Boolean) =
        text(title, 11.5f, c, bold = bold).apply {
            if (strike) paintFlags = paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
            setLineSpacing(0f, 1.15f)
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(3) }
        }

    /** 한 줄 변경: 기호 · 제목(한 줄 말줄임) · 시간 */
    private fun changeRow(mark: String, title: String, meta: String, @ColorInt markColor: Int, @ColorInt titleColor: Int, strike: Boolean): View {
        val row = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(2), 0, dp(2))
        }
        row.addView(text(mark, 12f, markColor, black = true).apply { gravity = Gravity.CENTER }, LinearLayout.LayoutParams(dp(14), -2))
        row.addView(text(title, 12f, titleColor).apply {
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            if (strike) paintFlags = paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
        }, LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = dp(6); marginEnd = dp(6) })
        row.addView(text(meta, 11f, color(R.color.oreum_ink3)))
        return row
    }

    /** 글머리 목록 (원인 • · 해결 방법 ✓) */
    private fun fillList(container: LinearLayout, items: List<String>, mark: String, @ColorInt markColor: Int, markWidthDp: Int) {
        container.removeAllViews()
        items.forEach { item ->
            val row = LinearLayout(requireContext()).apply { orientation = LinearLayout.HORIZONTAL }
            row.addView(text(mark, 12.5f, markColor, bold = mark == "✓"), LinearLayout.LayoutParams(dp(markWidthDp), -2))
            row.addView(text(item, 12.5f, color(R.color.oreum_dark_text)).apply { setLineSpacing(0f, 1.25f) }, LinearLayout.LayoutParams(0, -2, 1f))
            container.addView(row)
        }
    }

    private fun addCauseChip(label: String, @ColorInt bg: Int, @ColorInt fg: Int) {
        binding.chipsCause.addView(text(label, 11.5f, fg, bold = true).apply {
            setBackgroundResource(R.drawable.chip_rec_bg)
            backgroundTintList = ColorStateList.valueOf(bg)
            setPadding(dp(9), dp(3), dp(9), dp(3))
        })
    }

    private fun text(value: CharSequence, sizeSp: Float, @ColorInt c: Int, bold: Boolean = false, black: Boolean = false) =
        TextView(requireContext()).apply {
            text = value
            textSize = sizeSp
            setTextColor(c)
            includeFontPadding = true
            if (black) typeface = Typeface.create("sans-serif-black", Typeface.NORMAL)
            else if (bold) setTypeface(typeface, Typeface.BOLD)
        }

    private fun divider(@ColorInt c: Int) = View(requireContext()).apply { setBackgroundColor(c) }

    private fun dayLabel(epochDay: Long): String {
        val d = LocalDate.ofEpochDay(epochDay)
        return "${DateText.monthDay(d)} (${d.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.KOREAN)})"
    }

    private fun SpannableStringBuilder.bold(value: String) {
        val start = length
        append(value)
        setSpan(StyleSpan(Typeface.BOLD), start, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        setSpan(ForegroundColorSpan(color(R.color.oreum_dark_text)), start, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
    }

    private fun reanalyze() {
        val text = binding.inputExtra.text?.toString().orEmpty().trim()
        binding.inputExtra.clearFocus()
        requireContext().getSystemService(InputMethodManager::class.java)?.hideSoftInputFromWindow(binding.inputExtra.windowToken, 0)
        viewModel.reanalyze(text)
        toast(getString(if (text.isNotEmpty()) R.string.toast_rec_reanalyzed else R.string.toast_rec_extra_cleared))
    }

    private fun confirmApprove() {
        val plan = viewModel.uiState.value.plan ?: return
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.rec_confirm_title)
            .setMessage(getString(R.string.rec_confirm_body, plan.placedItems.size, plan.moves.size))
            .setNegativeButton(R.string.rec_cancel, null)
            .setPositiveButton(R.string.rec_confirm_ok) { _, _ ->
                viewModel.approve() ?: return@setPositiveButton
                toast(getString(R.string.toast_rec_applied))
                // 계획 탭에서 내일을 골라 바뀐 계획을 바로 보여 줘요
                PlanFocus.date = LocalDate.now().plusDays(1)
                val activity = requireActivity()
                findNavController().navigateUp()
                activity.findViewById<BottomNavigationView>(R.id.bottom_nav)?.selectedItemId = R.id.calendarFragment
            }
            .show()
    }

    private fun toast(message: String) = Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()

    private fun color(res: Int) = requireContext().getColor(res)

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    override fun onDestroyView() {
        _binding = null
        backdrop = null
        super.onDestroyView()
    }

    private companion object {
        val GREEN = 0xFF2F8A6F.toInt()
        val BLUE = 0xFF4B8DB5.toInt()
    }
}
