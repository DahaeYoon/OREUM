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
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.doOnLayout
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.example.oreum.R
import com.example.oreum.data.model.RoundStatus
import com.example.oreum.data.model.TrailOrder
import com.example.oreum.databinding.FragmentHomeBinding
import com.example.oreum.data.repository.AiRequests
import com.example.oreum.data.repository.StudyRepositoryProvider
import com.example.oreum.ui.common.GlassBackdrop
import com.example.oreum.ui.common.GlassBackgroundDrawable
import com.example.oreum.ui.round.NextRoundFragment
import com.example.oreum.ui.round.RoundLogSheet
import com.example.oreum.ui.study.GoalDetailViewModel
import com.example.oreum.util.DateText
import com.example.oreum.util.navigateSafe
import com.google.android.material.bottomnavigation.BottomNavigationView
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.roundToInt
import com.example.oreum.util.showToast

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
    /** 완료 버튼 때문에 자동으로 패널을 내렸는지 */
    private var autoFolded = false
    /** 완료 처리 중 (패널이 내려가는 동안 두 번 눌러도 한 번만) */
    private var ticking = false
    /** 완료 때문에 산을 확대해 두었는지 (결과 기록을 닫으면 되돌려요) */
    private var tickFocused = false
    /** 지금 위치가 보이도록 산을 맞춘 진행률 (바뀌면 다시 맞춰요) */
    private var lastShownRate: Float? = null
    private val sheetWatcher = object : FragmentManager.FragmentLifecycleCallbacks() {
        override fun onFragmentDestroyed(fm: FragmentManager, f: Fragment) {
            if (f is RoundLogSheet && _binding != null) restorePanel()
        }
    }
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
        binding.grabClimb.setOnClickListener {
            autoFolded = false
            setPanelFold(!panelFold)
        }
        childFragmentManager.registerFragmentLifecycleCallbacks(sheetWatcher, false)
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
        showSummitIfPending()
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
        // 지금 위치(점)가 아래 패널에 가리거나 화면 밖이면 산을 살짝 옮겨요 (완료 연출 중에는 그대로)
        val rate = state.progress.rate
        if (!ticking && !tickFocused && rate != lastShownRate) {
            lastShownRate = rate
            binding.mountain.post {
                if (_binding != null && !tickFocused) binding.mountain.ensureVisible(rate, visibleBottom(folded = panelFold))
            }
        }

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
        // 학습 결과 기록: 진행 중이고 아직 남은 항목이 있을 때
        binding.btnRoundLog.isVisible = r != null && r.status == RoundStatus.ACTIVE && !r.allDone
        if (r != null) binding.btnRoundLog.setOnClickListener { openLog(r.id, submit = false) }

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
                btn.setOnClickListener { startRound(n.round.id) }
            }
            is NextCard.AllDone -> {
                binding.textNextLabel.text = getString(R.string.home_round_label, n.round.no, n.round.doneCount, n.round.items.size)
                binding.textNextTitle.setText(R.string.home_round_all_done)
                binding.textNextCap.setText(R.string.home_round_all_done_cap)
                btn.text = getString(R.string.home_btn_submit)
                btn.icon = null
                btn.setOnClickListener { openLog(n.round.id, submit = true) }
            }
            is NextCard.Proposal -> {
                val pr = n.round
                binding.textNextLabel.text = if (pr != null) getString(R.string.home_proposal_label_n, pr.no - 1) else getString(R.string.home_proposal_label_final)
                binding.textNextTitle.text = if (pr != null) getString(R.string.home_proposal_title, pr.no) else getString(R.string.home_proposal_final)
                binding.textNextCap.setText(R.string.home_proposal_cap)
                btn.text = getString(R.string.home_btn_check)
                btn.setIconResource(R.drawable.ic_spark)
                btn.setOnClickListener { openNext(n.goalId) }
            }
            is NextCard.NoPlan -> {
                binding.textNextLabel.setText(R.string.home_new_mountain)
                binding.textNextTitle.setText(R.string.home_no_roadmap)
                binding.textNextCap.setText(aiCap(n.ai, R.string.home_no_roadmap_cap))
                btn.text = getString(R.string.home_btn_ai_plan)
                btn.setIconResource(R.drawable.ic_spark)
                btn.setOnClickListener { openReview(n.goalId) }
            }
            is NextCard.NeedProposal -> {
                binding.textNextLabel.text = getString(R.string.home_need_proposal_label, n.lastNo)
                binding.textNextTitle.setText(R.string.home_need_proposal_title)
                binding.textNextCap.setText(aiCap(n.ai, R.string.home_need_proposal_cap))
                btn.text = getString(R.string.home_btn_ai_next)
                btn.setIconResource(R.drawable.ic_spark)
                btn.setOnClickListener { openNext(n.goalId) }
            }
            is NextCard.Draft -> {
                binding.textNextLabel.setText(R.string.home_draft_label)
                binding.textNextTitle.setText(R.string.home_draft_roadmap_title)
                binding.textNextCap.setText(R.string.home_draft_roadmap_cap)
                btn.text = getString(R.string.home_btn_check)
                btn.icon = null
                btn.setOnClickListener { openReview(n.goalId) }
            }
            NextCard.Empty -> {
                binding.textNextLabel.setText(R.string.home_new_mountain)
                binding.textNextTitle.setText(R.string.home_no_goal)
                binding.textNextCap.setText(R.string.home_no_goal_cap)
                btn.text = getString(R.string.home_btn_add_goal)
                btn.setIconResource(R.drawable.ic_add)
                btn.setOnClickListener { navigateSafe(R.id.action_home_to_goalForm) }
            }
            NextCard.Top -> {
                binding.textNextLabel.setText(R.string.home_top_label)
                binding.textNextTitle.setText(R.string.home_top_roadmap_title)
                binding.textNextCap.setText(R.string.home_top_cap)
                btn.text = getString(R.string.home_btn_new_mountain)
                btn.setIconResource(R.drawable.ic_route)
                btn.setOnClickListener { navigateSafe(R.id.action_home_to_goalForm) }
            }
        }
    }

    // ---------- 동작 ----------
    /** 항목 완료 체크 (패널 카드 · 항목 시트에서 함께 사용) */
    fun tick(roundId: String, itemId: String) {
        if (ticking) return
        // 해제는 바로 (산 이동 없음)
        val item = StudyRepositoryProvider.instance.round(roundId)?.items?.firstOrNull { it.id == itemId } ?: return
        if (item.done) {
            val r = viewModel.toggleItem(roundId, itemId) ?: return
            toast(getString(R.string.toast_home_tick_off, r.title.removeSuffix(" (이어서)")))
            return
        }
        // 완료: ① 항목 시트를 닫고 패널을 내려 산을 보여 줘요 → ② 패널이 내려간 뒤 체크해서 점이 트랙을 따라 움직여요
        //       → ③ 움직임이 끝나면 학습 결과 기록(오늘 공부한 시간 · 메모)을 열어요
        (childFragmentManager.findFragmentByTag(HomeRoundSheet.TAG) as? HomeRoundSheet)?.dismiss()
        ticking = true
        val wasFolded = panelFold
        if (!wasFolded) {
            autoFolded = true
            setPanelFold(true)
        }
        // 지금 위치를 확대해서 패널 위 보이는 곳 가운데로 (점이 산 아래쪽에 있어도 보여요)
        tickFocused = true
        binding.mountain.focusOn(viewModel.uiState.value.progress.rate, visibleBottom(folded = true))
        binding.root.postDelayed({
            ticking = false
            if (_binding == null || !isAdded) return@postDelayed
            val r = viewModel.toggleItem(roundId, itemId) ?: return@postDelayed run { restorePanel() }
            toast(getString(R.string.toast_home_done_move, r.title.removeSuffix(" (이어서)"), (r.before * 100).toInt(), (r.after * 100).toInt()))
            binding.root.postDelayed({
                if (_binding == null || !isAdded) return@postDelayed
                openLog(roundId, submit = r.roundAllDone)
            }, if (r.after > r.before) TRACK_MOVE_MS else 600L)
        }, PANEL_MOVE_MS)
    }

    /** 아래 패널에 가리지 않는 산 영역의 아래 끝 (산 뷰 기준 px). folded = 패널을 내렸을 때 */
    private fun visibleBottom(folded: Boolean): Float {
        val panel = binding.pageClimb
        val a = IntArray(2)
        val b = IntArray(2)
        panel.getLocationInWindow(a)
        binding.mountain.getLocationInWindow(b)
        val openTop = a[1] - b[1] - panel.translationY
        return openTop + if (folded) (binding.panel.height - dp(74)).toFloat() else 0f
    }

    /** 완료 때문에 자동으로 내린 패널이면 다시 올려요 (결과 기록을 닫은 뒤) */
    private fun restorePanel() {
        if (autoFolded && panelFold) setPanelFold(false)
        if (tickFocused) {
            // 원래 크기로 돌아가되, 지금 위치가 패널에 가리지 않게
            val rate = viewModel.uiState.value.progress.rate
            lastShownRate = rate
            binding.mountain.ensureVisible(rate, visibleBottom(folded = panelFold), force = true)
        }
        tickFocused = false
        autoFolded = false
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

    /** 추천 화면에서 정상 도착을 승인하고 돌아왔으면: 산을 보여 준 뒤 정상 도착 팝업 */
    private fun showSummitIfPending() {
        val goalId = HomePrefs.summitGoalId ?: return
        HomePrefs.summitGoalId = null
        binding.root.postDelayed({
            if (_binding == null || !isAdded || childFragmentManager.findFragmentByTag(SummitDialog.TAG) != null) return@postDelayed
            SummitDialog.newInstance(goalId).show(childFragmentManager, SummitDialog.TAG)
        }, 700)
    }

    /** 새로운 산 등반하기: 목표 만들기 */
    fun openNewGoal() {
        navigateSafe(R.id.action_home_to_goalForm)
    }

    /** 학습 결과 기록 (submit = true: 못 끝낸 이유까지 바로 보여요) */
    fun openLog(roundId: String, submit: Boolean) {
        if (childFragmentManager.findFragmentByTag(RoundLogSheet.TAG) != null) return
        RoundLogSheet.newInstance(roundId, submit).show(childFragmentManager, RoundLogSheet.TAG)
    }

    /** AI 다음 구간 추천 */
    fun openNext(goalId: String) {
        val nav = findNavController()
        if (nav.currentDestination?.id != R.id.homeFragment) return
        NextRoundFragment.open(nav, goalId)
    }

    fun startRound(roundId: String) {
        val r = viewModel.startRound(roundId) ?: return
        toast(getString(R.string.toast_round_started, r.no))
    }

    fun openDetail(goalId: String) {
        navigateSafe(R.id.action_home_to_goalDetail, Bundle().apply { putString(GoalDetailViewModel.ARG_GOAL_ID, goalId) })
    }

    /** AI 요청 상태에 맞는 안내 문구 (만드는 중 · 실패 · 기본) */
    private fun aiCap(ai: AiRequests.State?, default: Int): Int = when (ai) {
        AiRequests.State.Running -> R.string.home_ai_working_cap
        is AiRequests.State.Failed -> R.string.home_ai_failed_cap
        null -> default
    }

    /** AI 로드맵 확인 화면: 초안이 없으면 그 화면에서 AI에게 요청해요 */
    private fun openReview(goalId: String) {
        navigateSafe(R.id.action_home_to_planReview, Bundle().apply { putString("goalId", goalId) })
    }

    fun selectGoal(goalId: String?) {
        binding.mountain.resetView()
        lastShownRate = null
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

    private fun toast(message: String) = requireActivity().showToast(message)
    private fun color(res: Int) = requireContext().getColor(res)
    private fun dp(value: Int) = (value * resources.displayMetrics.density).roundToInt()

    override fun onDestroyView() {
        childFragmentManager.unregisterFragmentLifecycleCallbacks(sheetWatcher)
        _binding = null
        backdrop = null
        super.onDestroyView()
    }

    private companion object {
        /** 패널이 내려가는 시간 · 점이 트랙을 따라 움직이는 시간 (MountainView.ANIM_SEC 1.5초 + 여유) */
        const val PANEL_MOVE_MS = 500L
        const val TRACK_MOVE_MS = 1_800L
        val DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("M월 d일 EEEE", Locale.KOREAN)
    }
}
