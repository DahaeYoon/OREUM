package com.example.oreum.ui.home

import android.content.res.ColorStateList
import android.graphics.Typeface
import android.os.Bundle
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.TextUtils
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.ColorInt
import androidx.core.view.doOnLayout
import androidx.core.view.isVisible
import com.example.oreum.R
import com.example.oreum.data.model.Goal
import com.example.oreum.data.model.Task
import com.example.oreum.data.model.TrailOrder
import com.example.oreum.data.repository.StudyRepository
import com.example.oreum.data.repository.StudyRepositoryProvider
import com.example.oreum.databinding.SheetHomeFlagBinding
import com.example.oreum.databinding.SheetHomeListBinding
import com.example.oreum.domain.usecase.HomeMountain
import com.example.oreum.domain.usecase.PlanCalendar
import com.example.oreum.ui.common.GlassSheetFragment
import com.example.oreum.ui.study.priorityText
import com.example.oreum.util.DateText
import com.google.android.material.button.MaterialButton
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.roundToInt

/** 홈 팝업 공용: 제목 · 설명 · 목록 틀 + 작은 도우미 */
abstract class HomeListSheet : GlassSheetFragment() {
    private var _binding: SheetHomeListBinding? = null
    protected val binding get() = checkNotNull(_binding)
    protected val repository: StudyRepository = StudyRepositoryProvider.instance
    protected val home: HomeFragment? get() = parentFragment as? HomeFragment

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = SheetHomeListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.scroll.maxHeightPx = (resources.displayMetrics.heightPixels * 0.5f).toInt()
        binding.btnClose.setOnClickListener { dismiss() }
        build()
    }

    protected abstract fun build()

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }

    protected fun dp(v: Number) = (v.toFloat() * resources.displayMetrics.density).roundToInt()
    protected fun color(res: Int) = requireContext().getColor(res)

    protected fun text(value: CharSequence, sp: Float, @ColorInt c: Int, bold: Boolean = false) = TextView(requireContext()).apply {
        text = value
        textSize = sp
        setTextColor(c)
        if (bold) setTypeface(typeface, Typeface.BOLD)
    }

    protected fun gapParams(top: Int) = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(top) }

    protected fun toast(msg: String) = Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
}

/** 어떤 목표의 산을 볼까요? (전체 · 진행 중 · 이미 오른 산 · 산 오르는 순서) */
class HomeGoalSheet : HomeListSheet() {

    private var showDone = false

    override fun build() {
        binding.textTitle.setText(R.string.home_pick_title)
        render()
    }

    private fun render() {
        val goals = repository.goals.value
        val tasks = repository.tasks.value
        val selected = HomePrefs.goalId
        val isDone = { g: Goal -> g.completedAt != null || HomeMountain.progress(tasks, goals, g.id).top }
        val active = goals.filter { !isDone(it) }
        val done = goals.filter(isDone)
        if (done.any { it.id == selected }) showDone = true

        val list = binding.list
        list.removeAllViews()
        val all = HomeMountain.progress(tasks, goals, null)
        list.addView(option(
            getString(R.string.home_pick_all),
            getString(R.string.home_pick_all_cap, active.size, all.done, all.total),
            "${all.percent}%", selected == null, fin = false,
        ) { pick(null) })
        active.forEach { g ->
            val q = HomeMountain.progress(tasks, goals, g.id)
            list.addView(option(g.title, getString(R.string.home_pick_goal_cap, DateText.dDay(DateText.toDate(g.dueAt)), q.done, q.total),
                "${q.percent}%", selected == g.id, fin = false) { pick(g.id) }, gapParams(6))
        }
        if (done.isNotEmpty()) {
            val toggle = text(getString(if (showDone) R.string.home_pick_done_hide else R.string.home_pick_done_show, done.size), 13f, color(R.color.oreum_ink2), bold = true).apply {
                setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.ic_check_15, 0, 0, 0)
                compoundDrawableTintList = ColorStateList.valueOf(color(R.color.oreum_ink2))
                compoundDrawablePadding = dp(6)
                setPadding(dp(2), dp(10), dp(2), dp(4))
                setOnClickListener { showDone = !showDone; render() }
            }
            list.addView(toggle, LinearLayout.LayoutParams(-2, -2))
            if (showDone) done.forEach { g ->
                val q = HomeMountain.progress(tasks, goals, g.id)
                list.addView(option(g.title, getString(R.string.home_pick_fin_cap, q.done, q.total), null, selected == g.id, fin = true) { pick(g.id) }, gapParams(6))
            }
        }

        // 산 오르는 순서
        val after = binding.listAfter
        after.removeAllViews()
        val order = com.example.oreum.data.repository.AuthRepositoryProvider.instance.currentUser.value?.trailOrder ?: TrailOrder.PLAN_DATE
        val link = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundResource(R.drawable.bg_home_order_link)
            minimumHeight = dp(52)
            setPadding(dp(14), dp(10), dp(14), dp(10))
            setOnClickListener {
                val parent = parentFragment
                dismiss()
                TrailOrderSheet().show(parent?.childFragmentManager ?: return@setOnClickListener, TrailOrderSheet.TAG)
            }
        }
        val main = LinearLayout(requireContext()).apply { orientation = LinearLayout.VERTICAL }
        main.addView(text(getString(R.string.home_order_title), 14.5f, color(R.color.oreum_dark_text), bold = true))
        val sub = LinearLayout(requireContext()).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        sub.addView(TrailSwatchView(requireContext()).apply { this.order = order }, LinearLayout.LayoutParams(dp(38), dp(20)))
        sub.addView(text(orderLabel(order), 12f, color(R.color.oreum_ink2)), LinearLayout.LayoutParams(-2, -2).apply { marginStart = dp(6) })
        main.addView(sub, gapParams(3))
        link.addView(main, LinearLayout.LayoutParams(0, -2, 1f))
        link.addView(text("›", 18f, color(R.color.oreum_ink3), bold = true))
        after.addView(link, gapParams(14))
    }

    private fun pick(goalId: String?) {
        dismiss()
        if (goalId != HomePrefs.goalId) home?.selectGoal(goalId)
    }

    private fun option(title: String, cap: String, right: String?, selected: Boolean, fin: Boolean, onClick: () -> Unit): View {
        val v = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundResource(if (fin && !selected) R.drawable.bg_home_goal_opt_fin else R.drawable.bg_home_goal_opt)
            isSelected = selected
            minimumHeight = dp(56)
            setPadding(dp(12), dp(10), dp(12), dp(10))
            setOnClickListener { onClick() }
        }
        val texts = LinearLayout(requireContext()).apply { orientation = LinearLayout.VERTICAL }
        texts.addView(text(title, 16f, color(if (fin) R.color.oreum_ink2 else R.color.oreum_dark_text), bold = true).apply {
            maxLines = 1; ellipsize = TextUtils.TruncateAt.END
        })
        texts.addView(text(cap, 12f, color(R.color.oreum_ink2)), gapParams(2))
        v.addView(texts, LinearLayout.LayoutParams(0, -2, 1f))
        if (right != null) {
            v.addView(text(right, 16f, 0xFF3E8E7A.toInt(), bold = true), LinearLayout.LayoutParams(-2, -2).apply { marginStart = dp(10) })
        } else {
            v.addView(text(getString(R.string.home_pick_fin_chip), 11f, 0xFF3E8E7A.toInt(), bold = true).apply {
                setBackgroundResource(R.drawable.bg_pill)
                backgroundTintList = ColorStateList.valueOf(0xFFE3F3EE.toInt())
                setPadding(dp(8), dp(3), dp(8), dp(3))
            }, LinearLayout.LayoutParams(-2, -2).apply { marginStart = dp(10) })
        }
        return v
    }

    companion object {
        const val TAG = "HomeGoalSheet"
    }
}

internal fun androidx.fragment.app.Fragment.orderLabel(order: TrailOrder): String = getString(
    when (order) {
        TrailOrder.PLAN_DATE -> R.string.home_order_plan
        TrailOrder.DUE -> R.string.home_order_due
        TrailOrder.PRIORITY -> R.string.home_order_priority
        TrailOrder.BY_GOAL -> R.string.home_order_goal
    },
)

/** 산 오르는 순서: 순서마다 트랙 색 · 모양이 달라요 */
class TrailOrderSheet : HomeListSheet() {
    override fun build() {
        binding.textTitle.setText(R.string.home_order_title)
        binding.textCaption.isVisible = true
        binding.textCaption.setText(R.string.home_order_caption)
        val current = com.example.oreum.data.repository.AuthRepositoryProvider.instance.currentUser.value?.trailOrder ?: TrailOrder.PLAN_DATE
        val list = binding.list
        TrailOrder.entries.forEachIndexed { i, o ->
            val (style, desc) = when (o) {
                TrailOrder.PLAN_DATE -> R.string.home_order_plan_style to R.string.home_order_plan_desc
                TrailOrder.DUE -> R.string.home_order_due_style to R.string.home_order_due_desc
                TrailOrder.PRIORITY -> R.string.home_order_priority_style to R.string.home_order_priority_desc
                TrailOrder.BY_GOAL -> R.string.home_order_goal_style to R.string.home_order_goal_desc
            }
            val row = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setBackgroundResource(R.drawable.bg_home_goal_opt)
                isSelected = o == current
                setPadding(dp(12), dp(10), dp(12), dp(10))
                setOnClickListener {
                    dismiss()
                    if (o != current) {
                        home?.setTrailOrder(o)
                        val label = orderLabel(o)
                        toast(getString(R.string.toast_home_order, label, if (hasBatchim(label)) "으로" else "로"))
                    }
                }
            }
            row.addView(TrailSwatchView(requireContext()).apply { order = o }, LinearLayout.LayoutParams(dp(38), dp(20)))
            val texts = LinearLayout(requireContext()).apply { orientation = LinearLayout.VERTICAL }
            texts.addView(text(SpannableStringBuilder(orderLabel(o)).apply {
                append("  ")
                val s = length
                append(getString(style))
                setSpan(RelativeSizeSpan(0.78f), s, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                setSpan(ForegroundColorSpan(color(R.color.oreum_ink3)), s, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }, 14.5f, color(R.color.oreum_dark_text), bold = true))
            texts.addView(text(getString(desc), 12f, color(R.color.oreum_ink2)).apply { setLineSpacing(0f, 1.15f) }, gapParams(2))
            row.addView(texts, LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = dp(10); marginEnd = dp(6) })
            if (o == current) row.addView(ImageView(requireContext()).apply {
                setImageResource(R.drawable.ic_check)
                imageTintList = ColorStateList.valueOf(0xFF3E8E7A.toInt())
            }, LinearLayout.LayoutParams(dp(18), dp(18)))
            list.addView(row, if (i == 0) LinearLayout.LayoutParams(-1, -2) else gapParams(6))
        }
    }

    /** 받침이 있으면 '으로' (ㄹ 받침은 '로') */
    private fun hasBatchim(word: String): Boolean {
        val c = word.lastOrNull()?.code?.minus(0xAC00) ?: return false
        return c >= 0 && c % 28 != 0 && c % 28 != 8
    }

    companion object {
        const val TAG = "TrailOrderSheet"
    }
}

/** 정상 깃발: 최종 목표 · 마감 · 완료/남은 계획 */
class FlagInfoSheet : GlassSheetFragment() {
    private var _binding: SheetHomeFlagBinding? = null
    private val binding get() = checkNotNull(_binding)

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = SheetHomeFlagBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val repo = StudyRepositoryProvider.instance
        val goalId = requireArguments().getString(ARG_GOAL).orEmpty()
        val goal = repo.goal(goalId) ?: run { dismiss(); return }
        val q = HomeMountain.progress(repo.tasks.value, repo.goals.value, goalId)
        val today = LocalDate.now()
        val due = DateText.toDate(goal.dueAt)
        val hot = ChronoUnit.DAYS.between(today, due) <= 3
        binding.chipDday.text = DateText.dDay(due, today)
        binding.chipDday.backgroundTintList = ColorStateList.valueOf(if (hot) 0xFFFBE5DE.toInt() else 0xFFE4F0F5.toInt())
        binding.chipDday.setTextColor(if (hot) 0xFFB5492E.toInt() else 0xFF2F6585.toInt())
        binding.textTitle.text = goal.title
        binding.statDone.text = "${q.done}/${q.total}"
        binding.statLeft.text = getString(R.string.home_count, q.total - q.done)
        binding.statTime.text = DateText.minutes(q.remainingMinutes)
        binding.track.doOnLayout { binding.fill.layoutParams = binding.fill.layoutParams.apply { width = it.width * q.percent / 100 } }
        val dueText = java.time.Instant.ofEpochMilli(goal.dueAt).atZone(java.time.ZoneId.systemDefault()).format(DUE_FORMAT)
        binding.textCaption.text = getString(R.string.home_flag_caption, dueText)
        binding.btnClose.setOnClickListener { dismiss() }
        binding.btnSummits.setOnClickListener {
            dismiss()
            Toast.makeText(requireContext(), R.string.home_summits_next_step, Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }

    companion object {
        const val TAG = "FlagInfoSheet"
        private const val ARG_GOAL = "goalId"
        private val DUE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("M월 d일 (E) HH:mm", Locale.KOREAN)

        fun newInstance(goalId: String) = FlagInfoSheet().apply { arguments = Bundle().apply { putString(ARG_GOAL, goalId) } }
    }
}

/** 완료한 체크포인트: 할 일 하나(완료 정보 · 완료 취소) 또는 주 차 묶음(항목 목록 · 완료 취소) */
class SlotInfoSheet : HomeListSheet() {

    override fun build() {
        val slotId = requireArguments().getString(ARG_SLOT).orEmpty()
        val items = requireArguments().getStringArrayList(ARG_ITEMS).orEmpty()
        if (items.isNotEmpty()) buildGroup(slotId, items) else buildSingle(slotId)
    }

    private fun buildGroup(@Suppress("UNUSED_PARAMETER") slotId: String, ids: List<String>) {
        val tasks = ids.mapNotNull { repository.task(it) }
        binding.textTitle.text = requireArguments().getString(ARG_TITLE).orEmpty()
        binding.textCaption.isVisible = true
        val allDone = tasks.all { it.isDone }
        binding.textCaption.text = getString(R.string.home_slot_week_cap, tasks.size,
            if (allDone) getString(R.string.home_slot_all_done) else getString(R.string.home_slot_left, DateText.minutes(tasks.sumOf { remaining(it) })))
        val card = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundResource(R.drawable.bg_home_card14)
            setPadding(dp(12), dp(2), dp(12), dp(2))
        }
        val today = LocalDate.now().toEpochDay()
        val logs = repository.logs.value
        tasks.forEachIndexed { i, t ->
            if (i > 0) card.addView(View(requireContext()).apply { setBackgroundColor(0x80E1E8E5.toInt()) }, LinearLayout.LayoutParams(-1, dp(1)))
            val row = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, dp(9), 0, dp(9))
            }
            val main = LinearLayout(requireContext()).apply { orientation = LinearLayout.VERTICAL }
            main.addView(text(t.title, 14f, color(R.color.oreum_dark_text), bold = true).apply { maxLines = 1; ellipsize = TextUtils.TruncateAt.END })
            val day = t.plannedDate?.let { DateText.monthDay(LocalDate.ofEpochDay(it)) }.orEmpty()
            main.addView(text("$day · ${DateText.minutes(t.originalMinutes ?: t.estimatedMinutes)}", 12f, color(R.color.oreum_ink2)), gapParams(2))
            row.addView(main, LinearLayout.LayoutParams(0, -2, 1f))
            if (t.isDone && !t.carriedOver) {
                row.addView(undoButton(t), LinearLayout.LayoutParams(-2, dp(28)))
            } else {
                val st = PlanCalendar.stateText(t, today, logs)
                row.addView(text(st, 11.5f, color(if (t.isDone) R.color.oreum_today_green else R.color.oreum_ink2), bold = true).apply {
                    setBackgroundResource(if (t.isDone) R.drawable.bg_state_ok else R.drawable.bg_state_plain)
                    setPadding(dp(9), dp(3), dp(9), dp(3))
                })
            }
            card.addView(row)
        }
        binding.list.addView(card)
        binding.listAfter.addView(text(getString(R.string.home_slot_note), 12f, color(R.color.oreum_ink2)), gapParams(10))
    }

    private fun buildSingle(taskId: String) {
        val t = repository.task(taskId) ?: run { dismiss(); return }
        val goal = t.goalId?.let { repository.goal(it) }
        binding.rowHead.isVisible = true
        binding.rowHead.addView(text(getString(R.string.home_slot_done_chip), 12f, 0xFF3E8E7A.toInt(), bold = true).apply {
            setBackgroundResource(R.drawable.bg_pill)
            backgroundTintList = ColorStateList.valueOf(0xFFE3F3EE.toInt())
            setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.ic_check_13, 0, 0, 0)
            compoundDrawableTintList = ColorStateList.valueOf(0xFF3E8E7A.toInt())
            compoundDrawablePadding = dp(4)
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(10), dp(4), dp(10), dp(4))
        })
        binding.rowHead.addView(View(requireContext()), LinearLayout.LayoutParams(0, 1, 1f))
        binding.rowHead.addView(text(getString(R.string.home_slot_priority, requireContext().priorityText(t.priority)), 12f, color(R.color.oreum_ink2)))
        binding.textTitle.text = t.title
        binding.textTitle.textSize = 20f
        val info = listOf(
            getString(R.string.home_slot_goal) to (goal?.title ?: "-"),
            getString(R.string.home_slot_est) to DateText.minutes(t.originalMinutes ?: t.estimatedMinutes),
            getString(R.string.home_slot_done_day) to (t.completedAt?.let { DateText.toDate(it) } ?: t.plannedDate?.let { LocalDate.ofEpochDay(it) })
                ?.format(DateTimeFormatter.ofPattern("yyyy년 M월 d일 (E)", Locale.KOREAN)).orEmpty().ifEmpty { "-" },
        )
        info.forEachIndexed { i, (k, v) ->
            val row = LinearLayout(requireContext()).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, dp(7), 0, dp(7)) }
            row.addView(text(k, 13f, color(R.color.oreum_ink2)), LinearLayout.LayoutParams(dp(96), -2))
            row.addView(text(v, 13f, color(R.color.oreum_dark_text), bold = true), LinearLayout.LayoutParams(0, -2, 1f))
            binding.list.addView(row, if (i == 0) LinearLayout.LayoutParams(-1, -2) else gapParams(0))
        }
        binding.btnClose.isVisible = false
        binding.rowButtons.isVisible = true
        val close = MaterialButton(requireContext(), null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
            text = getString(R.string.home_close)
            setTextColor(color(R.color.oreum_dark_text))
            setOnClickListener { dismiss() }
        }
        val undo = MaterialButton(requireContext()).apply {
            text = getString(R.string.home_undo)
            backgroundTintList = ColorStateList.valueOf(0xFFE9785A.toInt())
            setTextColor(0xFFFFFFFF.toInt())
            isEnabled = t.isDone && !t.carriedOver
            setOnClickListener { dismiss(); home?.undoTask(t.id) }
        }
        binding.rowButtons.addView(close, LinearLayout.LayoutParams(0, dp(44), 1f))
        binding.rowButtons.addView(undo, LinearLayout.LayoutParams(0, dp(44), 1f).apply { marginStart = dp(8) })
    }

    private fun undoButton(t: Task) = text(getString(R.string.home_undo), 12f, 0xFFC0573E.toInt(), bold = true).apply {
        setBackgroundResource(R.drawable.bg_home_undo)
        gravity = Gravity.CENTER
        setPadding(dp(10), 0, dp(10), 0)
        setOnClickListener { dismiss(); home?.undoTask(t.id) }
    }

    private fun remaining(t: Task) = if (t.isDone) 0 else (t.estimatedMinutes - minOf(t.actualMinutes ?: 0, t.estimatedMinutes)).coerceAtLeast(0)

    companion object {
        const val TAG = "SlotInfoSheet"
        private const val ARG_SLOT = "slotId"
        private const val ARG_ITEMS = "items"
        private const val ARG_TITLE = "title"

        fun newInstance(slotId: String, items: List<String>, title: String) = SlotInfoSheet().apply {
            arguments = Bundle().apply {
                putString(ARG_TITLE, title)
                putString(ARG_SLOT, slotId)
                putStringArrayList(ARG_ITEMS, ArrayList(items))
            }
        }
    }
}
