package com.example.oreum.ui.home

import android.animation.ValueAnimator
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
import android.view.animation.PathInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.ColorInt
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
import com.example.oreum.databinding.FragmentHomeBinding
import com.example.oreum.databinding.ViewHomePageIndBinding
import com.example.oreum.ui.aiplan.PlanDrafts
import com.example.oreum.ui.calendar.DayLogSheet
import com.example.oreum.ui.calendar.RecoveryOpener
import com.example.oreum.ui.calendar.TaskDetailSheet
import com.example.oreum.ui.common.GlassBackdrop
import com.example.oreum.ui.common.GlassBackgroundDrawable
import com.example.oreum.util.DateText
import com.google.android.material.bottomnavigation.BottomNavigationView
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.roundToInt

/**
 * STEP 7 홈 (프로토타입 홈)
 * - 뒤: 계절 산 (트랙 · 체크포인트 · 지금 위치 · 정상 깃발), 끌어서 둘러보기 · 확대
 * - 위: 날짜 · 인사말
 * - 아래 카드 두 장 (옆으로 넘김): ① 산 — 산 고르기 · 마감 · 다음 할 일(완료) · 진행률 ② 오늘 계획 — 진행률 고리 · 안내 · 체크 목록
 */
class HomeFragment : Fragment(R.layout.fragment_home), RecoveryOpener {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = checkNotNull(_binding)
    private val viewModel: HomeViewModel by viewModels()

    private var backdrop: GlassBackdrop? = null
    private var panelFold = false
    private var todayDown = false
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

        // 카드 전환 (산 · 오늘 계획)
        listOf(binding.indPageClimb, binding.indPageToday).forEach { ind ->
            ind.indItemMountain.setOnClickListener { binding.pager.goTo(0) }
            ind.indItemToday.setOnClickListener { binding.pager.goTo(1) }
        }
        binding.pager.onPageChanged = { page ->
            renderIndicators(page)
            binding.chipRecover.isVisible = page == 0 && (lastState?.pendingRecovery ?: 0) > 0
        }
        binding.pager.onOffset = { refreshGlass() }
        renderIndicators(binding.pager.page)

        binding.btnGoal.setOnClickListener { HomeGoalSheet().show(childFragmentManager, HomeGoalSheet.TAG) }
        binding.textGoal.setOnClickListener { binding.btnGoal.performClick() }
        binding.chipRecover.setOnClickListener { openRecovery() }
        binding.grabClimb.setOnClickListener { setPanelFold(!panelFold) }
        binding.grabToday.setOnClickListener { setTodayDown(!todayDown) }
        binding.btnTodayPlan.setOnClickListener { selectTab(R.id.calendarFragment) }

        // 유리 카드: 뒤의 산을 흐리게 비춰요
        binding.root.doOnLayout {
            captureBackdrop()
            binding.scrollToday.maxHeightPx = (binding.root.height * 0.3f).toInt()
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

        // 산
        binding.mountain.setState(
            MountainView.State(
                progress = state.progress.rate,
                slots = state.slots,
                top = state.goal != null && state.progress.top,
                order = state.order,
            ),
            animateFrom = viewModel.takeAnimateFrom(),
        )

        // 산 고르기 · 마감
        binding.textGoal.text = state.goal?.title ?: getString(R.string.home_all_goals)
        val due = state.dueGoal
        binding.chipDday.isVisible = due != null
        if (due != null) {
            val dueDate = DateText.toDate(due.dueAt)
            binding.chipDday.text = DateText.dDay(dueDate, today)
            val hot = ChronoUnit.DAYS.between(today, dueDate) <= 3
            binding.chipDday.backgroundTintList = android.content.res.ColorStateList.valueOf(if (hot) 0xFFFBE5DE.toInt() else 0xFFE3F3EE.toInt())
            binding.chipDday.setTextColor(if (hot) 0xFFB5492E.toInt() else 0xFF3E8E7A.toInt())
        }

        renderNext(state)

        // 진행률
        val pct = state.progress.percent
        binding.trackProgress.doOnLayout { track ->
            binding.fillProgress.layoutParams = binding.fillProgress.layoutParams.apply { width = track.width * pct / 100 }
        }
        binding.textProgress.text = SpannableStringBuilder().apply {
            val s = length
            append("${state.progress.done}/${state.progress.total}")
            setSpan(StyleSpan(Typeface.BOLD), s, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            setSpan(ForegroundColorSpan(color(R.color.oreum_dark_text)), s, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            append(" · $pct%")
        }

        // 미완료 · AI 복구
        binding.chipRecover.text = getString(R.string.home_rec_chip, state.pendingRecovery)
        binding.chipRecover.isVisible = state.pendingRecovery > 0 && binding.pager.page == 0

        renderToday(state)
        binding.mountain.post { captureBackdrop() }
    }

    private fun renderNext(state: HomeUiState) {
        val card = binding.cardNext
        card.setBackgroundResource(if (state.next is NextCard.Top) R.drawable.bg_home_next_card_top else R.drawable.bg_home_next_card)
        val btn = binding.btnNext
        when (val n = state.next) {
            is NextCard.Next -> {
                val d = LocalDate.ofEpochDay(n.task.plannedDate ?: LocalDate.now().toEpochDay())
                binding.textNextLabel.text = if (n.isToday) getString(R.string.home_next_today)
                else getString(R.string.home_next_day, DateText.monthDay(d), d.format(DOW_FORMAT))
                binding.textNextTitle.text = n.task.title
                binding.textNextCap.text = getString(R.string.home_next_cap, n.goalTitle, DateText.minutes(n.task.estimatedMinutes))
                btn.text = getString(R.string.home_btn_done)
                btn.setIconResource(R.drawable.ic_check)
                btn.setOnClickListener { completeNext(n.task.id) }
            }
            is NextCard.NoPlan -> {
                binding.textNextLabel.setText(R.string.home_new_mountain)
                binding.textNextTitle.setText(R.string.home_no_plan)
                binding.textNextCap.setText(R.string.home_no_plan_cap)
                btn.text = getString(R.string.home_btn_ai_plan)
                btn.setIconResource(R.drawable.ic_spark)
                btn.setOnClickListener { openReview(n.goalId, generate = true) }
            }
            is NextCard.Draft -> {
                binding.textNextLabel.setText(R.string.home_draft_label)
                binding.textNextTitle.setText(R.string.home_draft_title)
                binding.textNextCap.setText(R.string.home_draft_cap)
                btn.text = getString(R.string.home_btn_check)
                btn.icon = null
                btn.setOnClickListener { openReview(n.goalId, generate = false) }
            }
            NextCard.Empty -> {
                binding.textNextLabel.setText(R.string.home_new_mountain)
                binding.textNextTitle.setText(R.string.home_no_plan)
                binding.textNextCap.setText(R.string.home_empty_cap)
                btn.text = getString(R.string.home_btn_add)
                btn.setIconResource(R.drawable.ic_add)
                btn.setOnClickListener { selectTab(R.id.studyFragment) }
            }
            NextCard.Top -> {
                binding.textNextLabel.setText(R.string.home_top_label)
                binding.textNextTitle.setText(R.string.home_top_title)
                binding.textNextCap.setText(R.string.home_top_cap)
                btn.text = getString(R.string.home_btn_new_mountain)
                btn.setIconResource(R.drawable.ic_route)
                btn.setOnClickListener { findNavController().navigate(R.id.action_home_to_goalForm) }
            }
        }
    }

    // ---------- 오늘 계획 ----------
    private fun renderToday(state: HomeUiState) {
        binding.ringToday.percent = state.todayPercent
        val doneCount = state.today.count { it.task.isDone }
        binding.textTodayCap.text = getString(
            R.string.home_today_cap,
            DateText.minutes(state.todayPlanned),
            DateText.minutes(state.todayCredited),
            doneCount,
            state.today.size,
        )

        // 안내: AI 복구 · 기록하지 않은 날
        val banners = binding.listBanners
        banners.removeAllViews()
        if (state.pendingRecovery > 0) {
            banners.addView(banner(R.drawable.bg_home_ban_rec, R.drawable.ic_route, 0xFFC0573E.toInt(),
                getString(R.string.home_ban_rec_title), getString(R.string.home_ban_rec_body, state.pendingRecovery)) { openRecovery() })
        }
        if (state.unloggedDays.isNotEmpty()) {
            val days = state.unloggedDays.joinToString(", ") { DateText.monthDay(LocalDate.ofEpochDay(it)) }
            banners.addView(banner(R.drawable.bg_home_ban_warn, R.drawable.ic_clock, 0xFFA27A24.toInt(),
                getString(R.string.home_ban_log_title), getString(R.string.home_ban_log_body, days)) { openLog(state.unloggedDays.first()) })
        }

        // 체크 목록
        val list = binding.listToday
        list.removeAllViews()
        if (state.today.isEmpty()) {
            list.addView(text(
                getString(R.string.home_today_empty) + if (state.anyGoalWithoutPlan) " " + getString(R.string.home_today_empty_hint) else "",
                12f, color(R.color.oreum_ink2),
            ).apply { setPadding(0, dp(12), 0, dp(12)) })
        }
        state.today.forEachIndexed { i, item ->
            if (i > 0) list.addView(View(requireContext()).apply { setBackgroundColor(0x80E1E8E5.toInt()) }, LinearLayout.LayoutParams(-1, dp(1)))
            list.addView(taskRow(item))
        }

        val today = LocalDate.now().toEpochDay()
        if (state.today.isNotEmpty()) {
            binding.btnTodayPrimary.setText(R.string.home_btn_log)
            binding.btnTodayPrimary.setIconResource(R.drawable.ic_clock)
            binding.btnTodayPrimary.setOnClickListener { openLog(today) }
        } else {
            binding.btnTodayPrimary.setText(R.string.home_btn_make_plan)
            binding.btnTodayPrimary.setIconResource(R.drawable.ic_nav_study)
            binding.btnTodayPrimary.setOnClickListener { selectTab(R.id.studyFragment) }
        }
    }

    private fun taskRow(item: TodayItem): View {
        val t = item.task
        val row = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(8), 0, dp(8))
        }
        // 체크 (30dp 누르는 칸 · 20dp 동그라미)
        val check = FrameLayout(requireContext()).apply {
            contentDescription = getString(R.string.home_check_desc, t.title)
            isClickable = true
            setOnClickListener { toggleToday(t.id) }
        }
        val box = ImageView(requireContext()).apply {
            setBackgroundResource(if (t.isDone) R.drawable.bg_home_check_on else R.drawable.bg_home_check_off)
            if (t.isDone) {
                setImageResource(R.drawable.ic_check)
                imageTintList = android.content.res.ColorStateList.valueOf(0xFFFFFFFF.toInt())
                setPadding(dp(3), dp(3), dp(3), dp(3))
            }
        }
        check.addView(box, FrameLayout.LayoutParams(dp(20), dp(20), Gravity.CENTER))
        row.addView(check, LinearLayout.LayoutParams(dp(30), dp(30)).apply { marginStart = dp(-6) })

        val meta = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            isClickable = true
            setBackgroundResource(rippleRes())
            setOnClickListener { TaskDetailSheet.newInstance(t.id).show(childFragmentManager, TaskDetailSheet.TAG) }
        }
        meta.addView(text(t.title, 13f, color(if (t.isDone) R.color.oreum_ink3 else R.color.oreum_dark_text)).apply {
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            if (t.isDone) paintFlags = paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
        })
        val cap = SpannableStringBuilder("${item.goalTitle} · ${DateText.minutes(t.estimatedMinutes)}")
        if (t.fromRecoveryId != null) {
            cap.append(" · ")
            val s = cap.length
            cap.append(getString(R.string.home_rec_tag))
            cap.setSpan(StyleSpan(Typeface.BOLD), s, cap.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            cap.setSpan(ForegroundColorSpan(0xFF3E7F86.toInt()), s, cap.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        val actual = t.actualMinutes ?: 0
        if (!t.isDone && actual > 0) cap.append(" · ").append(getString(R.string.home_done_minutes, DateText.minutes(actual)))
        meta.addView(text(cap, 11f, color(R.color.oreum_ink2)).apply { maxLines = 1; ellipsize = TextUtils.TruncateAt.END },
            LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(2) })
        row.addView(meta, LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = dp(4) })
        return row
    }

    private fun banner(bg: Int, icon: Int, @ColorInt tint: Int, title: String, body: String, onClick: () -> Unit): View {
        val v = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundResource(bg)
            setPadding(dp(10), dp(8), dp(10), dp(8))
            isClickable = true
            setOnClickListener { onClick() }
        }
        v.addView(ImageView(requireContext()).apply {
            setImageResource(icon)
            imageTintList = android.content.res.ColorStateList.valueOf(tint)
        }, LinearLayout.LayoutParams(dp(17), dp(17)))
        val texts = LinearLayout(requireContext()).apply { orientation = LinearLayout.VERTICAL }
        texts.addView(text(title, 12.5f, 0xFF1F2D2A.toInt(), bold = true))
        texts.addView(text(body, 11f, color(R.color.oreum_ink2)))
        v.addView(texts, LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = dp(10); marginEnd = dp(10) })
        v.addView(text("›", 16f, tint, bold = true))
        return v.also {
            it.layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) }
        }
    }

    private fun renderIndicators(page: Int) {
        listOf(binding.indPageClimb, binding.indPageToday).forEach { ind: ViewHomePageIndBinding ->
            ind.indItemMountain.isSelected = page == 0
            ind.indItemToday.isSelected = page == 1
        }
    }

    // ---------- 동작 ----------
    private fun completeNext(taskId: String) {
        val r = viewModel.completeNext(taskId) ?: return
        if (!r.reachedTop) toast(getString(R.string.toast_home_done_move, r.title, (r.before * 100).toInt(), (r.after * 100).toInt()))
        else toast(getString(R.string.toast_home_top))
    }

    private fun toggleToday(taskId: String) {
        val r = viewModel.toggleToday(taskId) ?: return
        val todayPct = viewModel.uiState.value.todayPercent
        toast(if (r.nowDone) getString(R.string.toast_home_tick_done, r.title, todayPct) else getString(R.string.toast_home_tick_undo, todayPct))
        if (r.reachedTop) {
            binding.pager.goTo(0)
            toast(getString(R.string.toast_home_top))
        }
    }

    fun undoTask(taskId: String) {
        val r = viewModel.undo(taskId) ?: return
        toast(getString(R.string.toast_home_undo, r.title, (r.after * 100).toInt()))
    }

    private fun openFlag() {
        val s = lastState ?: return
        val goal = s.goal
        if (goal == null) {
            toast(getString(R.string.home_summits_next_step))
            return
        }
        FlagInfoSheet.newInstance(goal.id).show(childFragmentManager, FlagInfoSheet.TAG)
    }

    private fun openCheckpoint(index: Int) {
        val slot = lastState?.slots?.getOrNull(index) ?: return
        SlotInfoSheet.newInstance(slot.id, slot.itemIds, slot.title).show(childFragmentManager, SlotInfoSheet.TAG)
    }

    fun openLog(date: Long) {
        DayLogSheet.newInstance(date).show(childFragmentManager, DayLogSheet.TAG)
    }

    override fun openRecovery() {
        val nav = findNavController()
        if (nav.currentDestination?.id == R.id.homeFragment) nav.navigate(R.id.action_home_to_recovery)
    }

    private fun openReview(goalId: String, generate: Boolean) {
        if (generate) PlanDrafts.generateFor(goalId)
        findNavController().navigate(R.id.action_home_to_planReview, Bundle().apply { putString("goalId", goalId) })
    }

    fun selectGoal(goalId: String?) {
        binding.mountain.resetView()
        viewModel.selectGoal(goalId)
    }

    fun setTrailOrder(order: com.example.oreum.data.model.TrailOrder) = viewModel.setOrder(order)

    fun currentState(): HomeUiState? = lastState

    private fun selectTab(id: Int) {
        requireActivity().findViewById<BottomNavigationView>(R.id.bottom_nav)?.selectedItemId = id
    }

    /** 산 카드 접기 · 펼치기 (산을 더 넓게 보기) */
    private fun setPanelFold(fold: Boolean) {
        panelFold = fold
        val panel = binding.pageClimb
        val target = if (fold) (binding.panel.height - dp(74)).toFloat() else 0f
        animateY(panel, target)
        binding.iconGrabClimb.animate().rotation(if (fold) 180f else 0f).setDuration(320).start()
        listOf(binding.cardNext, binding.trackProgress.parent as View).forEach { it.animate().alpha(if (fold) 0f else 1f).setDuration(250).start() }
        binding.cardNext.isEnabled = !fold
        (binding.chipRecover.layoutParams as FrameLayout.LayoutParams).bottomMargin = dp(if (fold) 130 else 238)
        binding.chipRecover.requestLayout()
    }

    /** 오늘 계획 카드 내리기 · 올리기 (산 보기) */
    private fun setTodayDown(down: Boolean) {
        todayDown = down
        val target = if (down) (binding.sheetToday.height - dp(58)).toFloat() else 0f
        animateY(binding.pageToday, target)
        binding.iconGrabToday.animate().rotation(if (down) 180f else 0f).setDuration(320).start()
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
        listOf(b.panel, b.sheetToday).forEach { host ->
            host.background = GlassBackgroundDrawable(
                host = host,
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
    }

    private fun refreshGlass() {
        _binding?.let { it.panel.invalidate(); it.sheetToday.invalidate() }
    }

    // ---------- 도우미 ----------
    private fun text(value: CharSequence, sizeSp: Float, @ColorInt c: Int, bold: Boolean = false) =
        TextView(requireContext()).apply {
            text = value
            textSize = sizeSp
            setTextColor(c)
            if (bold) setTypeface(typeface, Typeface.BOLD)
        }

    private fun rippleRes(): Int {
        val out = android.util.TypedValue()
        requireContext().theme.resolveAttribute(android.R.attr.selectableItemBackground, out, true)
        return out.resourceId
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
        val DOW_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("E", Locale.KOREAN)
    }
}
