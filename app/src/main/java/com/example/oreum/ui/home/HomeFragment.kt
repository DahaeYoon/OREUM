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
import android.widget.LinearLayout
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
import com.example.oreum.databinding.ItemHomeNextCardBinding
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
 * - 뒤: 계절 산 (전체 로드맵 단계 또는 개별 목표의 이번 구간 항목 · 지금 위치 · 정상 깃발).
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
    /** 산 연출 중 (그동안 완료를 다시 눌러도 무시) */
    private var ticking = false
    /** 구간 결과 제출 안내 창이 떠 있어요 (RoundLogSheet가 알려 줘요. 창이 닫힌 뒤에 산 연출) */
    var waitSubmitDialog = false

    /** 제출 안내 창이 닫혔어요: 홈에 그대로 있으면 산 연출, '추천 보기'로 넘어갔으면 홈에 돌아왔을 때 (onResume) */
    fun onSubmitDialogClosed() {
        waitSubmitDialog = false
        val b = _binding ?: return
        b.root.post {
            if (_binding == null || !isResumed) return@post
            if (findNavController().currentDestination?.id != R.id.homeFragment) return@post
            replayTick()
        }
    }

    override fun onResume() {
        super.onResume()
        // 다른 화면(다음 구간 추천 등)에 다녀왔으면, 미뤄 둔 산 연출을 이제 보여 줘요
        if (pendingTick != null && !waitSubmitDialog && childFragmentManager.findFragmentByTag(RoundLogSheet.TAG) == null) {
            binding.root.post { if (_binding != null && isResumed) replayTick() }
        }
    }

    /** 완료했지만 학습 결과 기록을 마친 뒤에 보여 줄 산 연출 */
    private var pendingTick: HomeViewModel.TickResult? = null
    /** 산을 맞춘 진행률 (바뀌면 다시 맞춰요) */
    private var lastShownRate: Float? = null
    private val sheetWatcher = object : FragmentManager.FragmentLifecycleCallbacks() {
        override fun onFragmentDestroyed(fm: FragmentManager, f: Fragment) {
            if (f is RoundLogSheet && _binding != null && !waitSubmitDialog) replayTick()
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
            v.setPadding(v.paddingLeft, headerTop + top + dp(6), v.paddingRight, v.paddingBottom)
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
            val state = lastState ?: return@setOnClickListener
            val sheet = if (state.goal == null) {
                HomeRoundSheet.combined()
            } else {
                val round = state.round ?: return@setOnClickListener
                HomeRoundSheet.newInstance(round.id)
            }
            sheet.show(childFragmentManager, HomeRoundSheet.TAG)
        }

        // 유리 카드: 뒤의 산을 흐리게 비춰요
        binding.nextCardsScroll.maxHeightPx = dp(168)
        binding.root.doOnLayout {
            binding.nextCardsScroll.maxHeightPx = minOf(dp(168), it.height * 2 / 5)
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
        val previous = lastState
        lastState = state
        showSummitIfPending()
        val today = LocalDate.now()

        binding.textDate.text = today.format(DATE_FORMAT)
        binding.textGreeting.text = getString(R.string.home_greeting, state.nickname.ifBlank { getString(R.string.home_default_name) })

        binding.mountain.setState(
            MountainView.State(
                progress = state.climb.rate,
                slots = state.slots,
                top = state.goal != null && state.progress.top,
                order = TrailOrder.PLAN_DATE,
                combinedGoals = state.goal == null,
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

        val combined = state.goal == null
        val cardsChanged = previous?.nextCards != state.nextCards
        if (cardsChanged) renderNextCards(state.nextCards)
        if (previous?.goal?.id != state.goal?.id) binding.nextCardsScroll.scrollTo(0, 0)

        // 전체 보기에서는 각 목표의 이번 구간 항목을 한 곳에 모아요.
        val r = state.round
        val itemCount = if (combined) state.roundGroups.sumOf { it.round.items.size } else r?.items?.size ?: 0
        binding.btnRoundAll.isVisible = if (combined) itemCount > 0 else r != null
        binding.btnRoundAll.text = getString(if (combined) R.string.home_round_all_combined else R.string.home_round_all, itemCount)
        // 학습 결과 기록: 진행 중이고 아직 남은 항목이 있을 때
        val recordable = state.roundGroups.filter { it.round.status == RoundStatus.ACTIVE }
        binding.btnRoundLog.isVisible = if (combined) recordable.isNotEmpty()
            else r != null && r.status == RoundStatus.ACTIVE && !r.allDone
        binding.btnRoundLog.setOnClickListener {
            if (combined) {
                if (childFragmentManager.findFragmentByTag(HomeRecordGoalSheet.TAG) == null) {
                    HomeRecordGoalSheet().show(childFragmentManager, HomeRecordGoalSheet.TAG)
                }
            } else if (r != null) {
                openLog(r.id, submit = false)
            }
        }
        binding.rowRoundActs.isVisible = binding.btnRoundAll.isVisible || binding.btnRoundLog.isVisible

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

        // 카드 수나 진행률이 바뀌면 패널 위에 산의 현재 위치가 보이도록 맞춰요.
        val rate = state.climb.rate
        if (rate != lastShownRate || cardsChanged) {
            val first = lastShownRate == null
            lastShownRate = rate
            binding.panel.doOnLayout { if (_binding != null) frameMountain(animate = !first) }
        }
        binding.mountain.post { captureBackdrop() }
    }

    private fun renderNextCards(cards: List<GoalNextCard>) {
        val list = binding.nextCards
        list.removeAllViews()
        cards.forEachIndexed { index, card ->
            val item = ItemHomeNextCardBinding.inflate(layoutInflater, list, false)
            val params = item.root.layoutParams as LinearLayout.LayoutParams
            if (index > 0) params.topMargin = dp(8)
            renderNext(item, card)
            list.addView(item.root)
        }
    }

    private fun renderNext(binding: ItemHomeNextCardBinding, card: GoalNextCard) {
        binding.root.setBackgroundResource(if (card.next is NextCard.Top) R.drawable.bg_home_next_card_top else R.drawable.bg_home_next_card)
        val btn = binding.btnNext
        when (val n = card.next) {
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
                binding.textNextTitle.text = n.item?.title?.removeSuffix(" (이어서)") ?: n.round.displayTopic
                binding.textNextCap.text = if (n.item != null) {
                    getString(R.string.home_next_cap, n.goalTitle, DateText.minutes(n.item.minutes))
                } else {
                    getString(R.string.home_round_ready_cap, n.goalTitle, n.round.items.size, DateText.minutes(n.round.estimatedMinutes))
                }
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
        if (card.goalTitle != null && card.next !is NextCard.Next && card.next !is NextCard.Ready) {
            binding.textNextLabel.text = getString(R.string.home_next_cap, card.goalTitle, binding.textNextLabel.text)
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
        // 완료: ① 체크는 바로 저장하지만 산의 길은 아직 채우지 않고 → ② 학습 결과 기록(오늘 공부한 시간 · 메모)을 먼저 열어요
        //       → ③ 기록 창을 닫으면 패널을 내리고 노란 길이 채워지며 점이 다음 곳으로 올라가요 (replayTick)
        (childFragmentManager.findFragmentByTag(HomeRoundSheet.TAG) as? HomeRoundSheet)?.dismiss()
        binding.mountain.hold(viewModel.uiState.value.climb.rate)
        val r = viewModel.toggleItem(roundId, itemId)
        viewModel.takeAnimateFrom() // 산 연출은 기록을 마친 뒤에
        if (r == null) {
            binding.mountain.release()
            return
        }
        pendingTick = r
        openLog(roundId, submit = r.roundAllDone)
    }

    /** 학습 결과 기록 창을 닫은 뒤: 패널을 내리고 → 노란 길이 채워지며 점이 올라가고 → 패널을 다시 올려요 */
    private fun replayTick() {
        val r = pendingTick ?: return
        pendingTick = null
        if (r.displayedGoalId != viewModel.uiState.value.goal?.id) {
            binding.mountain.release(animate = false)
            return
        }
        ticking = true
        // 홈 화면이 새로 만들어졌을 수도 있어서, 연출 시작점(완료 전 진행률)을 다시 잡아 둬요
        binding.mountain.hold(r.before)
        val wasFolded = panelFold
        if (!wasFolded) {
            autoFolded = true
            setPanelFold(true)
        }
        binding.root.postDelayed({
            if (_binding == null || !isAdded) {
                ticking = false
                return@postDelayed
            }
            binding.mountain.release()
            val moved = r.after > r.before
            if (moved) toast(getString(R.string.toast_home_done_move, r.title.removeSuffix(" (이어서)"), r.doneCount, r.total))
            binding.root.postDelayed({
                ticking = false
                if (_binding != null && isAdded) restorePanel()
            }, if (moved) TRACK_MOVE_MS else 300L)
        }, if (wasFolded) 0L else PANEL_MOVE_MS)
    }

    /** 완료 때문에 자동으로 내린 패널이면 다시 올려요 */
    private fun restorePanel() {
        if (autoFolded && panelFold) setPanelFold(false)
        autoFolded = false
    }

    /** 보이는 곳(인사말 아래 ~ 패널 위)에 정상부터 지금 위치까지 들어오게 산을 맞춰요 */
    private fun frameMountain(animate: Boolean = true) {
        val greet = binding.textGreeting
        val a = IntArray(2)
        val b = IntArray(2)
        greet.getLocationInWindow(a)
        binding.mountain.getLocationInWindow(b)
        val top = (a[1] - b[1] + greet.height + dp(8)).toFloat()
        binding.mountain.frame(viewModel.uiState.value.climb.rate, top, visibleBottom(folded = panelFold), animate)
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
        // 산 위 점 = 이번 구간 항목 → 항목 모두 보기
        if (slot.id.startsWith("it:")) {
            val roundId = slot.id.split(":").getOrNull(1) ?: return
            HomeRoundSheet.newInstance(roundId).show(childFragmentManager, HomeRoundSheet.TAG)
            return
        }
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
        listOf(binding.nextCardsScroll, binding.rowRoundActs, binding.trackProgress.parent as View).forEach {
            it.animate().alpha(if (fold) 0f else 1f).setDuration(250).start()
        }
        binding.nextCardsScroll.importantForAccessibility =
            if (fold) View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS else View.IMPORTANT_FOR_ACCESSIBILITY_AUTO
        // 보이는 곳이 넓어지거나 좁아졌으니 산을 다시 맞춰요 (내리면 빈 곳 없이 크게, 올리면 다시 패널 위로)
        frameMountain()
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
        lastState = null
        _binding = null
        backdrop = null
        super.onDestroyView()
    }

    private companion object {
        /** 패널이 내려가는 시간 · 점이 트랙을 따라 움직이는 시간 (점 이동 1.5초 + 오른 만큼(+n%) 표시) */
        const val PANEL_MOVE_MS = 500L
        const val TRACK_MOVE_MS = 2_300L
        val DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("M월 d일 EEEE", Locale.KOREAN)
    }
}
