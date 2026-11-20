package com.example.oreum.ui.home

import android.content.res.ColorStateList
import android.graphics.Paint
import android.graphics.Typeface
import android.os.Bundle
import android.text.TextUtils
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.ColorInt
import androidx.core.view.doOnLayout
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.oreum.R
import com.example.oreum.data.model.ItemKind
import com.example.oreum.data.model.RoundStatus
import com.example.oreum.data.model.StudyRound
import com.example.oreum.data.repository.StudyRepository
import com.example.oreum.data.repository.StudyRepositoryProvider
import com.example.oreum.databinding.SheetHomeFlagBinding
import com.example.oreum.databinding.SheetHomeListBinding
import com.example.oreum.domain.usecase.HomeMountain
import com.example.oreum.domain.usecase.RoadmapProgress
import com.example.oreum.ui.common.GlassSheetFragment
import com.example.oreum.util.DateText
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.launch
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

    protected fun divider() = View(requireContext()).apply { setBackgroundColor(0x80E1E8E5.toInt()) }

    protected fun toast(msg: String) = Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
}

/** 어떤 목표의 산을 볼까요? (전체 · 진행 중 · 이미 오른 산) */
class HomeGoalSheet : HomeListSheet() {

    private var showDone = false

    override fun build() {
        binding.textTitle.setText(R.string.home_pick_title)
        render()
    }

    private fun render() {
        val goals = repository.goals.value
        val rounds = repository.rounds.value
        val selected = HomePrefs.goalId
        val active = goals.filter { it.completedAt == null }
        val done = goals.filter { it.completedAt != null }
        if (done.any { it.id == selected }) showDone = true

        val list = binding.list
        list.removeAllViews()
        val all = HomeMountain.roundProgress(goals, rounds, null)
        list.addView(option(
            getString(R.string.home_pick_all),
            getString(R.string.home_pick_all_stage_cap, active.size, all.done, all.total),
            "${all.percent}%", selected == null, fin = false,
        ) { pick(null) })
        active.forEach { g ->
            val q = HomeMountain.roundProgress(goals, rounds, g.id)
            val cap = if (q.total > 0) getString(R.string.home_pick_goal_stage_cap, DateText.dDay(DateText.toDate(g.dueAt)), q.done, q.total)
            else getString(R.string.home_pick_goal_noplan_cap, DateText.dDay(DateText.toDate(g.dueAt)))
            list.addView(option(g.title, cap, "${q.percent}%", selected == g.id, fin = false) { pick(g.id) }, gapParams(6))
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
                val q = HomeMountain.roundProgress(goals, rounds, g.id)
                list.addView(option(g.title, getString(R.string.home_pick_fin_stage_cap, q.total), null, selected == g.id, fin = true) { pick(g.id) }, gapParams(6))
            }
        }
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

/** 정상 깃발: 최종 목표 · 마감 · 마친 단계 / 남은 단계 / 남은 학습 */
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
        val q = HomeMountain.roundProgress(repo.goals.value, repo.rounds.value, goalId)
        val today = LocalDate.now()
        val due = DateText.toDate(goal.dueAt)
        val hot = ChronoUnit.DAYS.between(today, due) <= 3
        binding.chipDday.text = if (goal.completedAt != null) getString(R.string.study_dday_done) else DateText.dDay(due, today)
        binding.chipDday.backgroundTintList = ColorStateList.valueOf(if (hot && goal.completedAt == null) 0xFFFBE5DE.toInt() else 0xFFE4F0F5.toInt())
        binding.chipDday.setTextColor(if (hot && goal.completedAt == null) 0xFFB5492E.toInt() else 0xFF2F6585.toInt())
        binding.textTitle.text = goal.title
        binding.statDone.text = "${q.done}/${q.total}"
        binding.statLeft.text = getString(R.string.home_stage_left, q.total - q.done)
        binding.statTime.text = DateText.minutes(q.remainingMinutes)
        binding.track.doOnLayout { binding.fill.layoutParams = binding.fill.layoutParams.apply { width = it.width * q.percent / 100 } }
        val dueText = java.time.Instant.ofEpochMilli(goal.dueAt).atZone(java.time.ZoneId.systemDefault()).format(DUE_FORMAT)
        binding.textCaption.text = getString(R.string.home_flag_caption_stage, dueText)
        binding.btnClose.setOnClickListener { dismiss() }
        // 오른 산 모아보기는 2차 기능
        binding.btnSummits.isVisible = false
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

/** 체크포인트 = 로드맵 단계: 단계 이름 · 범위 · 상태 + 이 단계에서 한 구간들 */
class SlotInfoSheet : HomeListSheet() {

    override fun build() {
        // id = "st:{goalId}:{단계 index}"
        val parts = requireArguments().getString(ARG_SLOT).orEmpty().split(":")
        val goal = parts.getOrNull(1)?.let { repository.goal(it) }
        val index = parts.getOrNull(2)?.toIntOrNull()
        val stage = index?.let { goal?.roadmap?.getOrNull(it) }
        if (goal == null || index == null || stage == null) { dismiss(); return }

        val rounds = repository.rounds.value
        val fr = RoadmapProgress.of(goal, rounds).stageFractions.getOrElse(index) { 0f }
        val state = when {
            fr >= 1f -> getString(R.string.home_slot_stage_done)
            index == goal.stage && goal.completedAt == null -> getString(R.string.home_slot_stage_now, (fr * 100).toInt())
            else -> getString(R.string.home_slot_stage_next)
        }
        binding.rowHead.isVisible = true
        binding.rowHead.addView(text(state, 12f, 0xFF3E8E7A.toInt(), bold = true).apply {
            setBackgroundResource(R.drawable.bg_pill)
            backgroundTintList = ColorStateList.valueOf(0xFFE3F3EE.toInt())
            setPadding(dp(10), dp(4), dp(10), dp(4))
        })
        binding.rowHead.addView(View(requireContext()), LinearLayout.LayoutParams(0, 1, 1f))
        binding.rowHead.addView(text(DateText.minutes((stage.hours * 60).toInt()), 12f, color(R.color.oreum_ink2)))
        binding.textTitle.text = getString(R.string.home_slot_stage_title, index + 1, stage.title)
        binding.textCaption.isVisible = true
        binding.textCaption.text = stage.scope

        val mine = rounds.filter { it.goalId == goal.id && it.stage == index }.sortedBy { it.no }
        if (mine.isEmpty()) {
            binding.list.addView(text(getString(R.string.home_slot_stage_empty), 13f, color(R.color.oreum_ink2)).apply { setPadding(0, dp(6), 0, dp(6)) })
            return
        }
        val card = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundResource(R.drawable.bg_home_card14)
            setPadding(dp(12), dp(2), dp(12), dp(2))
        }
        mine.forEachIndexed { i, r ->
            if (i > 0) card.addView(divider(), LinearLayout.LayoutParams(-1, dp(1)))
            val row = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, dp(10), 0, dp(10))
            }
            row.addView(text(getString(R.string.round_no, r.no), 12.5f, 0xFF2E7A5F.toInt(), bold = true), LinearLayout.LayoutParams(dp(52), -2))
            row.addView(text(r.displayTopic, 14f, color(R.color.oreum_dark_text), bold = true).apply { maxLines = 1; ellipsize = TextUtils.TruncateAt.END },
                LinearLayout.LayoutParams(0, -2, 1f))
            val st = when (r.status) {
                RoundStatus.DONE -> getString(R.string.round_done_count, r.doneCount, r.items.size)
                RoundStatus.ACTIVE -> getString(R.string.round_status_active)
                RoundStatus.READY -> getString(R.string.round_status_ready)
            }
            row.addView(text(st, 11.5f, color(R.color.oreum_ink2), bold = true).apply {
                setBackgroundResource(R.drawable.bg_state_plain)
                setPadding(dp(9), dp(3), dp(9), dp(3))
            }, LinearLayout.LayoutParams(-2, -2).apply { marginStart = dp(8) })
            card.addView(row)
        }
        binding.list.addView(card)
    }

    companion object {
        const val TAG = "SlotInfoSheet"
        private const val ARG_SLOT = "slotId"

        fun newInstance(slotId: String) = SlotInfoSheet().apply {
            arguments = Bundle().apply { putString(ARG_SLOT, slotId) }
        }
    }
}

/** 이번 구간 항목 모두 보기: 홈을 떠나지 않고 체크 (체크하면 뒤의 산에서 점이 올라가요) */
class HomeRoundSheet : HomeListSheet() {

    private val roundId get() = requireArguments().getString(ARG_ROUND).orEmpty()
    private var act: MaterialButton? = null

    override fun build() {
        binding.textCaption.isVisible = true
        binding.btnClose.isVisible = false
        binding.rowButtons.isVisible = true
        val detail = MaterialButton(requireContext(), null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
            text = getString(R.string.home_btn_goal_detail)
            setTextColor(color(R.color.oreum_dark_text))
            setOnClickListener {
                val r = repository.round(roundId)
                dismiss()
                if (r != null) home?.openDetail(r.goalId)
            }
        }
        // 오른쪽 버튼: 시작 전이면 'n구간 시작', 진행 중이면 '학습 결과 기록' (다 끝냈으면 '결과 제출')
        act = MaterialButton(requireContext()).apply {
            backgroundTintList = ColorStateList.valueOf(color(R.color.oreum_deep_button))
            setTextColor(0xFFFFFFFF.toInt())
            iconTint = ColorStateList.valueOf(0xFFFFFFFF.toInt())
            iconGravity = MaterialButton.ICON_GRAVITY_TEXT_START
            iconSize = dp(16)
            iconPadding = dp(4)
        }
        binding.rowButtons.addView(detail, LinearLayout.LayoutParams(0, dp(46), 1f))
        binding.rowButtons.addView(act, LinearLayout.LayoutParams(0, dp(46), 1.4f).apply { marginStart = dp(8) })

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                repository.rounds.collect { list ->
                    val r = list.firstOrNull { it.id == roundId }
                    if (r == null) dismissAllowingStateLoss() else render(r)
                }
            }
        }
    }

    private fun render(r: StudyRound) {
        binding.textTitle.text = getString(R.string.home_round_sheet_title, r.no, r.displayTopic)
        act?.apply {
            isVisible = r.status != RoundStatus.DONE
            if (r.status == RoundStatus.READY) {
                text = getString(R.string.round_start, r.no)
                setIconResource(R.drawable.ic_route)
                setOnClickListener { home?.startRound(r.id) }
            } else {
                text = getString(if (r.allDone) R.string.home_btn_submit else R.string.home_round_log)
                setIconResource(R.drawable.ic_clock)
                setOnClickListener {
                    val h = home
                    dismiss()
                    h?.openLog(r.id, submit = r.allDone)
                }
            }
        }
        binding.textCaption.text = getString(R.string.round_sum, r.doneCount, r.items.size, DateText.minutes(r.estimatedMinutes))
        val list = binding.list
        list.removeAllViews()
        val card = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundResource(R.drawable.bg_home_card14)
            setPadding(dp(12), dp(2), dp(12), dp(2))
        }
        r.items.forEachIndexed { i, item ->
            if (i > 0) card.addView(divider(), LinearLayout.LayoutParams(-1, dp(1)))
            val row = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, dp(6), 0, dp(6))
            }
            val check = FrameLayout(requireContext()).apply {
                contentDescription = getString(R.string.round_item_check, item.title)
                isClickable = true
                isEnabled = r.status != RoundStatus.DONE
                setOnClickListener { home?.tick(r.id, item.id) }
            }
            val box = ImageView(requireContext()).apply {
                setBackgroundResource(if (item.done) R.drawable.bg_home_check_on else R.drawable.bg_home_check_off)
                if (item.done) {
                    setImageResource(R.drawable.ic_check)
                    imageTintList = ColorStateList.valueOf(0xFFFFFFFF.toInt())
                    setPadding(dp(3), dp(3), dp(3), dp(3))
                }
            }
            check.addView(box, FrameLayout.LayoutParams(dp(22), dp(22), Gravity.CENTER))
            row.addView(check, LinearLayout.LayoutParams(dp(36), dp(36)).apply { marginStart = dp(-6) })
            if (item.kind != ItemKind.NEW) {
                row.addView(text(getString(if (item.kind == ItemKind.CARRY) R.string.round_kind_carry else R.string.round_kind_review), 10.5f,
                    if (item.kind == ItemKind.CARRY) 0xFF3A6EA8.toInt() else 0xFFB4741C.toInt(), bold = true).apply {
                    setBackgroundResource(if (item.kind == ItemKind.CARRY) R.drawable.bg_round_kind else R.drawable.bg_round_kind_review)
                    setPadding(dp(6), dp(1), dp(6), dp(1))
                }, LinearLayout.LayoutParams(-2, -2).apply { marginStart = dp(4); marginEnd = dp(6) })
            }
            row.addView(text(item.title.removeSuffix(" (이어서)"), 14f, color(if (item.done) R.color.oreum_ink3 else R.color.oreum_dark_text), bold = true).apply {
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
                if (item.done) paintFlags = paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
            }, LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = dp(4) })
            row.addView(text(DateText.minutes(item.minutes), 12.5f, color(R.color.oreum_ink3)), LinearLayout.LayoutParams(-2, -2).apply { marginStart = dp(8) })
            card.addView(row)
        }
        list.addView(card)
    }

    companion object {
        const val TAG = "HomeRoundSheet"
        private const val ARG_ROUND = "roundId"

        fun newInstance(roundId: String) = HomeRoundSheet().apply {
            arguments = Bundle().apply { putString(ARG_ROUND, roundId) }
        }
    }
}
