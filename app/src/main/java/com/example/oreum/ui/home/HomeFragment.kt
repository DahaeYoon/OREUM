package com.example.oreum.ui.home

import android.animation.ValueAnimator
import android.graphics.Typeface
import android.os.Bundle
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.view.View
import android.view.animation.PathInterpolator
import android.widget.Toast
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.doOnLayout
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.example.oreum.R
import com.example.oreum.data.model.TrailOrder
import com.example.oreum.databinding.FragmentHomeBinding
import com.example.oreum.ui.aiplan.PlanDrafts
import com.example.oreum.ui.common.GlassBackdrop
import com.example.oreum.ui.common.GlassBackgroundDrawable
import com.example.oreum.ui.study.GoalDetailViewModel
import com.example.oreum.util.DateText
import com.google.android.material.bottomnavigation.BottomNavigationView
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.roundToInt

/**
 * 홈 (1차): 산과 이번 구간을 한 화면에
 * - 뒤: 계절 산 (트랙 · 체크포인트 = 로드맵 단계 · 지금 위치 · 정상 깃발). 항목을 끝내면 점이 트랙을 따라 올라가요.
 * - 위: 날짜 · 인사말
 * - 아래 패널: 산 고르기 · 마감 / 다음 할 일 카드 / 항목 모두 보기 / 로드맵 진행률
 */
class HomeFragment : Fragment(R.layout.fragment_home) {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = checkNotNull(_binding)
    private val viewModel: HomeViewModel by viewModels()

    private var backdrop: GlassBackdrop? = null
    private var panelFold = false
    private var lastState: HomeUiState? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentHomeBinding.bind(view)

        // 상태 표시줄만큼 인사말을 내려요
        val headerTop = binding.header.paddingTop
        ViewCompat.setOnApplyWindowInsetsListener(binding.header) { v, insets ->
            val top = insets.getInsets(WindowInsetsCompat.Type.systemBars()).top
            v.setPadding(v.paddingLeft, headerTop + top + dp(38), v.paddingRight, v.paddingBottom)
            insets
        }

        binding.mountain.onFlagClick = ::openFlag
        binding.mountain.onCheckpointClick = ::openCheckpoint

        binding.btnGoal.setOnClickListener { HomeGoalSheet().show(childFragmentManager, HomeGoalSheet.TAG) }
        binding.textGoal.setOnClickListener { binding.btnGoal.performClick() }
        binding.grabClimb.setOnClickListener { setPanelFold(!panelFold) }
        binding.btnRoundAll.setOnClickListener {
            val r = lastState?.round ?: return@setOnClickListener
            HomeRoundSheet.newInstance(r.id).show(childFragmentManager, HomeRoundSheet.TAG)
        }

        // 유리 카드: 뒤의 산을 흐리게 비춰요
        binding.root.doOnLayout {
            captureBackdrop()
            binding.btnGoal.doOnLayout { binding.textGoal.maxWidth = binding.btnGoal.width - binding.btnGoal.paddingLeft - binding.btnGoal.paddingRight }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect(::render)
            }
        }
    }

    private fun render(state: HomeUiState) {
        if (!state.loaded) return
        lastState = state
        val today = LocalDate.now()

        binding.textDate.text = today.format(DATE_FORMAT)
        binding.textGreeting.text = getString(R.string.home_greeting, state.nickname.ifBlank { getString(R.string.home_default_name) })

        // 산: 체크포인트 = 로드맵 단계
        binding.mountain.setState(
            MountainView.State(
                progress = state.progress.rate,
                slots = state.slots,
                top = state.goal != null && state.progress.top,
                order = TrailOrder.PLAN_DATE,
            ),
            animateFrom = viewModel.takeAnimateFrom(),
        )

        // 산 고르기 · 마감
        binding.textGoal.text = state.goal?.title ?: getString(R.string.home_all_goals)
        val due = state.focusGoal?.takeIf { it.completedAt == null }
        binding.chipDday.isVisible = due != null
        if (due != null) {
            val dueDate = DateText.toDate(due.dueAt)
            binding.chipDday.text = DateText.dDay(dueDate, today)
            val hot = ChronoUnit.DAYS.between(today, dueDate) <= 3
            binding.chipDday.backgroundTintList = android.content.res.ColorStateList.valueOf(if (hot) 0xFFFBE5DE.toInt() else 0xFFE3F3EE.toInt())
            binding.chipDday.setTextColor(if (hot) 0xFFB5492E.toInt() else 0xFF3E8E7A.toInt())
        }

        renderNext(state)

        // 항목 모두 보기 (지금 구간이 있을 때)
        val r = state.round
        binding.rowRoundActs.isVisible = r != null
        if (r != null) binding.btnRoundAll.text = getString(R.string.home_round_all, r.items.size)

        // 로드맵 진행률: 단계 k/n · %
        val pct = state.progress.percent
        binding.trackProgress.doOnLayout { track ->
            binding.fillProgress.layoutParams = binding.fillProgress.layoutParams.apply { width = track.width * pct / 100 }
        }
        binding.textProgress.text = SpannableStringBuilder().apply {
            val s = length
            append(getString(R.string.home_stage_count, state.progress.done, state.progress.total))
            setSpan(StyleSpan(Typeface.BOLD), s, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            setSpan(ForegroundColorSpan(color(R.color.oreum_dark_text)), s, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            append(" · $pct%")
        }

        binding.mountain.post { captureBackdrop() }
    }

    private fun renderNext(state: HomeUiState) {
        val card = binding.cardNext
        card.setBackgroundResource(if (state.next is NextCard.Top) R.drawable.bg_home_next_card_top else R.drawable.bg_home_next_card)
        val btn = binding.btnNext
        when (val n = state.next) {
            is NextCard.Next -> {
                binding.textNextLabel.text = getString(R.string.home_round_label, n.round.no, n.round.doneCount, n.round.items.size)
                binding.textNextTitle.text = n.item.title.removeSuffix(" (이어서)")
                binding.textNextCap.text = getString(R.string.home_next_cap, n.goalTitle, DateText.minutes(n.item.minutes))
                btn.text = getString(R.string.home_btn_done)
                btn.setIconResource(R.drawable.ic_check)
                btn.setOnClickListener { tick(n.round.id, n.item.id) }
            }
            is NextCard.Ready -> {
                binding.textNextLabel.text = getString(R.string.home_round_ready_label, n.round.no)
                binding.textNextTitle.text = n.round.displayTopic
                binding.textNextCap.text = getString(R.string.home_round_ready_cap, n.goalTitle, n.round.items.size, DateText.minutes(n.round.estimatedMinutes))
                btn.text = getString(R.string.home_btn_start)
                btn.setIconResource(R.drawable.ic_route)
                btn.setOnClickListener {
                    viewModel.startRound(n.round.id)
                    toast(getString(R.string.toast_round_started, n.round.no))
                }
            }
            is NextCard.AllDone -> {
                binding.textNextLabel.text = getString(R.string.home_round_label, n.round.no, n.round.doneCount, n.round.items.size)
                binding.textNextTitle.setText(R.string.home_round_all_done)
                binding.textNextCap.setText(R.string.home_round_all_done_cap)
                // 결과 기록 화면은 5단계에서 연결해요
                btn.text = getString(R.string.home_btn_detail)
                btn.icon = null
                btn.setOnClickListener { openDetail(n.round.goalId) }
            }
            is NextCard.Proposal -> {
                binding.textNextLabel.setText(R.string.home_proposal_label)
                binding.textNextTitle.text = n.round?.let { getString(R.string.home_proposal_title, it.no, it.displayTopic) }
                    ?: getString(R.string.home_proposal_final)
                binding.textNextCap.setText(R.string.home_proposal_cap)
                btn.text = getString(R.string.home_btn_check)
                btn.icon = null
                btn.setOnClickListener { openDetail(n.goalId) }
            }
            is NextCard.NoPlan -> {
                binding.textNextLabel.setText(R.string.home_new_mountain)
                binding.textNextTitle.setText(R.string.home_no_roadmap)
                binding.textNextCap.setText(R.string.home_no_roadmap_cap)
                btn.text = getString(R.string.home_btn_ai_plan)
                btn.setIconResource(R.drawable.ic_spark)
                btn.setOnClickListener { openReview(n.goalId, generate = true) }
            }
            is NextCard.Draft -> {
                binding.textNextLabel.setText(R.string.home_draft_label)
                binding.textNextTitle.setText(R.string.home_draft_roadmap_title)
                binding.textNextCap.setText(R.string.home_draft_roadmap_cap)
                btn.text = getString(R.string.home_btn_check)
                btn.icon = null
                btn.setOnClickListener { openReview(n.goalId, generate = false) }
            }
            NextCard.Empty -> {
                binding.textNextLabel.setText(R.string.home_new_mountain)
                binding.textNextTitle.setText(R.string.home_no_goal)
                binding.textNextCap.setText(R.string.home_no_goal_cap)
                btn.text = getString(R.string.home_btn_add_goal)
                btn.setIconResource(R.drawable.ic_add)
                btn.setOnClickListener { findNavController().navigate(R.id.action_home_to_goalForm) }
            }
            NextCard.Top -> {
                binding.textNextLabel.setText(R.string.home_top_label)
                binding.textNextTitle.setText(R.string.home_top_roadmap_title)
                binding.textNextCap.setText(R.string.home_top_cap)
                btn.text = getString(R.string.home_btn_new_mountain)
                btn.setIconResource(R.drawable.ic_route)
                btn.setOnClickListener { findNavController().navigate(R.id.action_home_to_goalForm) }
            }
        }
    }

    // ---------- 동작 ----------
    /** 항목 완료 체크 (패널 카드 · 항목 시트에서 함께 사용) */
    fun tick(roundId: String, itemId: String) {
        val r = viewModel.toggleItem(roundId, itemId) ?: return
        when {
            r.nowDone && r.roundAllDone -> toast(getString(R.string.toast_round_all_done, r.roundNo))
            r.nowDone -> toast(getString(R.string.toast_home_done_move, r.title.removeSuffix(" (이어서)"), (r.before * 100).toInt(), (r.after * 100).toInt()))
            else -> toast(getString(R.string.toast_home_tick_off, r.title.removeSuffix(" (이어서)")))
        }
    }

    private fun openFlag() {
        val goal = lastState?.goal
        if (goal == null) {
            toast(getString(R.string.home_flag_pick_goal))
            return
        }
        FlagInfoSheet.newInstance(goal.id).show(childFragmentManager, FlagInfoSheet.TAG)
    }

    private fun openCheckpoint(index: Int) {
        val slot = lastState?.slots?.getOrNull(index) ?: return
        SlotInfoSheet.newInstance(slot.id).show(childFragmentManager, SlotInfoSheet.TAG)
    }

    fun openDetail(goalId: String) {
        val nav = findNavController()
        if (nav.currentDestination?.id != R.id.homeFragment) return
        nav.navigate(R.id.action_home_to_goalDetail, Bundle().apply { putString(GoalDetailViewModel.ARG_GOAL_ID, goalId) })
    }

    private fun openReview(goalId: String, generate: Boolean) {
        if (generate) PlanDrafts.generateFor(goalId)
        findNavController().navigate(R.id.action_home_to_planReview, Bundle().apply { putString("goalId", goalId) })
    }

    fun selectGoal(goalId: String?) {
        binding.mountain.resetView()
        viewModel.selectGoal(goalId)
    }

    fun currentState(): HomeUiState? = lastState

    @Suppress("unused")
    private fun selectTab(id: Int) {
        requireActivity().findViewById<BottomNavigationView>(R.id.bottom_nav)?.selectedItemId = id
    }

    /** 패널 접기 · 펼치기 (산을 더 넓게 보기) */
    private fun setPanelFold(fold: Boolean) {
        panelFold = fold
        val panel = binding.pageClimb
        val target = if (fold) (binding.panel.height - dp(74)).toFloat() else 0f
        animateY(panel, target)
        binding.iconGrabClimb.animate().rotation(if (fold) 180f else 0f).setDuration(320).start()
        listOf(binding.cardNext, binding.rowRoundActs, binding.trackProgress.parent as View).forEach {
            it.animate().alpha(if (fold) 0f else 1f).setDuration(250).start()
        }
        binding.cardNext.isEnabled = !fold
    }

    private fun animateY(v: View, target: Float) {
        ValueAnimator.ofFloat(v.translationY, target).apply {
            duration = 320
            interpolator = PathInterpolator(0.2f, 0.8f, 0.2f, 1f)
            addUpdateListener { v.translationY = it.animatedValue as Float; refreshGlass() }
            start()
        }
    }

    // ---------- 유리 카드 ----------
    private fun captureBackdrop() {
        val b = _binding ?: return
        if (b.mountain.width == 0) return
        backdrop = GlassBackdrop.capture(b.mountain)
        val r = 24f * resources.displayMetrics.density
        b.panel.background = GlassBackgroundDrawable(
            host = b.panel,
            backdrop = backdrop,
            radiusPx = r,
            strokePx = resources.displayMetrics.density,
            topOnly = true,
            strokeBottom = false,
            fillColor = 0x94FFFCF5.toInt(),
            dimColor = 0,
            strokeColor = 0xD9FFFFFF.toInt(),
        )
    }

    private fun refreshGlass() {
        _binding?.panel?.invalidate()
    }

    private fun toast(message: String) = Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
    private fun color(res: Int) = requireContext().getColor(res)
    private fun dp(value: Int) = (value * resources.displayMetrics.density).roundToInt()

    override fun onDestroyView() {
        _binding = null
        backdrop = null
        super.onDestroyView()
    }

    private companion object {
        val DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("M월 d일 EEEE", Locale.KOREAN)
    }
}
