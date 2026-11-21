package com.example.oreum.ui.round

import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.TextUtils
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.core.view.doOnLayout
import androidx.core.view.isVisible
import androidx.core.widget.NestedScrollView
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.NavController
import androidx.navigation.fragment.findNavController
import com.example.oreum.R
import com.example.oreum.data.model.Difficulty
import com.example.oreum.data.model.Goal
import com.example.oreum.data.model.ItemKind
import com.example.oreum.data.model.NextProposal
import com.example.oreum.data.repository.AiRequests
import com.example.oreum.data.repository.ApproveResult
import com.example.oreum.data.repository.StudyRepository
import com.example.oreum.data.repository.StudyRepositoryProvider
import com.example.oreum.databinding.FragmentNextRoundBinding
import com.example.oreum.ui.common.AiStateUi
import com.example.oreum.ui.common.GlassBackdrop
import com.example.oreum.ui.common.GlassBackgroundDrawable
import com.example.oreum.ui.home.HomePrefs
import com.example.oreum.util.DateText
import com.example.oreum.util.Season
import com.example.oreum.util.applySystemBarsPadding
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.time.LocalDate
import com.example.oreum.util.showToast

/**
 * AI 다음 구간 추천 (프로토타입 08)
 * - 추천이 없으면 AI(Gemini)에게 요청해요. 만드는 중 · 실패(다시 시도)를 보여 주고, 시뮬레이션으로 대신 만들지 않아요.
 * - 지난 구간 결과로 만든 다음 구간을 보여 주고, '승인하기'를 누르고 확인해야만 다음 구간이 정해져요.
 * - 로드맵을 모두 마쳤으면 '정상 도착'으로 목표를 마쳐요.
 * - 나중에: 추천은 목표 상세 · 홈에 그대로 남아요.
 */
class NextRoundFragment : Fragment(R.layout.fragment_next_round) {

    private var _binding: FragmentNextRoundBinding? = null
    private val binding get() = checkNotNull(_binding)
    private val repository: StudyRepository = StudyRepositoryProvider.instance
    private val goalId: String get() = requireArguments().getString(ARG_GOAL_ID).orEmpty()

    private var backdrop: GlassBackdrop? = null
    private var foldOpen = false
    private var goal: Goal? = null
    private var proposal: NextProposal? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentNextRoundBinding.bind(view)

        binding.bgMountain.setImageResource(Season.of().mountainBackground)
        binding.wrap.applySystemBarsPadding(top = true, bottom = true)

        // 유리 팝업: 뒤의 산을 흐리게 찍어 팝업 자리에 그려요
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
        binding.scroll.setOnScrollChangeListener(NestedScrollView.OnScrollChangeListener { _, _, _, _, _ -> _binding?.card?.invalidate() })

        // 카드 배경 (프로토타입 .nx2-hero · .nx2-last · .nx2-it · .nx2-more)
        binding.hero.background = GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            intArrayOf(0xF2E1F4EC.toInt(), 0xBFFFFFFF.toInt()),
        ).apply {
            cornerRadius = dpf(18)
            setStroke(dp(1), 0xE6FFFFFF.toInt())
        }
        binding.rowLast.background = rounded(0xA8FFFFFF.toInt(), 14)
        binding.listItems.background = rounded(0xB3FFFFFF.toInt(), 16, 0xF2FFFFFF.toInt())
        binding.fold.background = rounded(0x80FFFFFF.toInt(), 14)

        binding.btnClose.setOnClickListener { later(showToast = false) }
        binding.btnLater.setOnClickListener { later(showToast = true) }
        requireActivity().onBackPressedDispatcher.addCallback(
            viewLifecycleOwner,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() = later(showToast = false)
            },
        )
        binding.btnFold.setOnClickListener {
            foldOpen = !foldOpen
            renderFold()
        }
        binding.btnApprove.setOnClickListener { approve() }

        // 추천이 없으면 AI에게 요청 (이미 만드는 중이면 기다리고, 실패 상태면 '다시 시도'를 눌러야 다시 보내요)
        if (AiRequests.states.value[AiRequests.nextKey(goalId)] == null) AiRequests.requestNext(goalId)

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                combine(repository.goals, repository.proposals, AiRequests.states) { goals, proposals, ai ->
                    Triple(goals.firstOrNull { it.id == goalId }, proposals[goalId], ai[AiRequests.nextKey(goalId)])
                }.collect { (g, p, ai) -> render(g, p, ai) }
            }
        }
    }

    private fun render(g: Goal?, p: NextProposal?, ai: AiRequests.State?) {
        goal = g
        proposal = p
        val round = p?.round

        // AI가 만드는 중 · 실패
        val aiShown = ai.takeIf { g != null && p == null }
        AiStateUi.render(
            binding.aiState, aiShown,
            loadingTitle = R.string.ai_loading_next,
            loadingBody = R.string.ai_loading_next_body,
            onRetry = { AiRequests.requestNext(goalId) },
            onLater = { later(showToast = false) },
        )
        binding.textTitle.isVisible = aiShown == null
        binding.rowButtons.isVisible = aiShown == null
        binding.textAiTag.isVisible = p != null && (p.byAi || !p.final)
        binding.textAiTag.setText(if (p?.byAi == true) R.string.rec_ai_tag_real else R.string.rec_ai_tag)
        if (aiShown != null) {
            binding.textEyebrow.isVisible = false
            binding.groupPlan.isVisible = false
            binding.textLead.isVisible = false
            return
        }
        binding.textEyebrow.text = g?.title.orEmpty()
        binding.textEyebrow.isVisible = g != null && round != null && p?.final != true

        // 추천이 없어요
        if (g == null || p == null) {
            binding.groupPlan.isVisible = false
            binding.textLead.isVisible = true
            binding.textTitle.setText(R.string.next_empty_title)
            binding.textLead.setText(R.string.next_empty_body)
            binding.btnLater.isVisible = false
            binding.btnApprove.text = getString(R.string.home_btn_check)
            binding.btnApprove.icon = null
            return
        }

        // 로드맵을 모두 마쳤어요 → 정상 도착
        if (p.final || round == null) {
            binding.groupPlan.isVisible = false
            binding.textLead.isVisible = true
            binding.textTitle.setText(R.string.next_final_title)
            binding.textLead.text = getString(R.string.next_final_body, g.title, g.roadmap.size)
            binding.btnLater.isVisible = true
            binding.btnApprove.text = getString(R.string.next_reach_top)
            binding.btnApprove.setIconResource(R.drawable.ic_flag)
            return
        }

        binding.groupPlan.isVisible = true
        binding.textLead.isVisible = false
        binding.btnLater.isVisible = true
        binding.btnApprove.text = getString(R.string.next_approve)
        binding.btnApprove.setIconResource(R.drawable.ic_check)
        binding.textTitle.text = getString(R.string.next_title, round.no)

        // 단계 · 주제
        val stageTitle = g.roadmap.getOrNull(round.stage)?.title.orEmpty()
        val topic = round.displayTopic
        binding.textStage.text = if (stageTitle.isNotBlank() && stageTitle != topic) {
            getString(R.string.next_stage_named, round.stage + 1, stageTitle, g.roadmap.size)
        } else {
            getString(R.string.next_stage, round.stage + 1, g.roadmap.size)
        }
        binding.textTopic.text = topic
        binding.textKpiItems.text = getString(R.string.round_count, round.items.size)
        binding.textKpiMinutes.text = DateText.minutes(round.estimatedMinutes)

        // 지난 n구간: 완료 수 + 칩 2개 (난이도 · 못 한 이유)
        binding.textLast.text = SpannableStringBuilder().apply {
            append(getString(R.string.next_last, p.lastNo))
            append("  ")
            val s = length
            append(getString(R.string.next_last_done, p.lastDoneCount, p.lastTotal))
            setSpan(StyleSpan(Typeface.BOLD), s, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            setSpan(ForegroundColorSpan(color(R.color.oreum_dark_text)), s, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        binding.chipsLast.removeAllViews()
        val chips = buildList {
            p.lastDifficulty?.let { d -> add(d.label to (if (d == Difficulty.HARD) CHIP_BAD else if (d == Difficulty.EASY) CHIP_GOOD else CHIP_PLAIN)) }
            p.lastReasons.forEach { add(it.short to CHIP_BAD) }
        }.take(2)
        chips.forEach { (text, style) -> binding.chipsLast.addView(lastChip(text, style)) }

        // 이렇게 바꿨어요
        binding.groupFixes.removeAllViews()
        p.fixes.forEach { binding.groupFixes.addView(fixChip(it)) }

        // n구간 할 일
        binding.textItemsTitle.text = getString(R.string.next_items, round.no)
        binding.textItemsMinutes.text = DateText.minutes(round.estimatedMinutes)
        binding.listItems.removeAllViews()
        round.items.forEachIndexed { i, item ->
            if (i > 0) {
                binding.listItems.addView(View(requireContext()).apply {
                    setBackgroundColor(0x14285041)
                    layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(1))
                })
            }
            binding.listItems.addView(itemRow(item.title.removeSuffix(" (이어서)"), item.minutes, item.kind))
        }

        // 완료 기준 · 추천 이유 · 메모
        binding.textCriteria.text = round.criteria.joinToString("\n") { "•  $it" }
        binding.textWhy.text = p.why.joinToString("\n") { "•  $it" }
        binding.textMemo.isVisible = p.lastMemo.isNotBlank()
        binding.textMemo.text = getString(R.string.next_memo_quote, p.lastMemo)
        renderFold()
    }

    private fun renderFold() {
        binding.foldBody.isVisible = foldOpen
        binding.iconFold.rotation = if (foldOpen) 0f else -90f
    }

    // ---------- 동작 ----------
    private fun approve() {
        val g = goal
        val p = proposal
        if (g == null || p == null) {
            findNavController().navigateUp()
            return
        }
        val round = p.round
        if (p.final || round == null) {
            // 정상 도착: 확인 없이 바로 (프로토타입과 같음)
            if (repository.approveProposal(g.id, today()) == ApproveResult.ReachedTop) {
                HomePrefs.summitGoalId = g.id
                goHome(g.id, null)
            }
            return
        }
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.next_approve_title, round.no))
            .setMessage(getString(R.string.next_approve_body, round.items.size, DateText.minutes(round.estimatedMinutes)))
            .setNegativeButton(R.string.study_form_cancel, null)
            .setPositiveButton(R.string.next_approve) { _, _ ->
                val res = repository.approveProposal(g.id, today()) as? ApproveResult.NextReady ?: return@setPositiveButton
                goHome(g.id, getString(R.string.toast_next_approved, res.round.no))
            }
            .show()
    }

    /** 승인한 목표의 산이 보이는 홈으로 */
    private fun goHome(goalId: String, message: String?) {
        HomePrefs.goalId = goalId
        HomePrefs.picked = true
        if (message != null) toast(message)
        val activity = requireActivity()
        findNavController().navigateUp()
        activity.findViewById<BottomNavigationView>(R.id.bottom_nav)?.selectedItemId = R.id.homeFragment
    }

    /** 나중에: 추천은 그대로 남아요 */
    private fun later(showToast: Boolean) {
        if (showToast && proposal != null) toast(getString(R.string.toast_next_later))
        findNavController().navigateUp()
    }

    // ---------- 작은 뷰 ----------
    /** 지난 구간 칩 (프로토타입 .nx2-chips em) */
    private fun lastChip(text: String, style: Int): TextView = TextView(requireContext()).apply {
        this.text = text
        textSize = 11.5f
        setTypeface(typeface, Typeface.BOLD)
        setPadding(dp(8), dp(3), dp(8), dp(3))
        val (bg, fg) = when (style) {
            CHIP_BAD -> 0xFFFCE9E4.toInt() to 0xFFC0533A.toInt()
            CHIP_GOOD -> 0xFFE1F4EC.toInt() to 0xFF2E7A5F.toInt()
            else -> 0xD9FFFFFF.toInt() to color(R.color.oreum_ink2)
        }
        background = rounded(bg, 999)
        setTextColor(fg)
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            .apply { marginStart = dp(4) }
    }

    /** 이렇게 바꿨어요 칩: 아이콘 + 짧은 문장 (프로토타입 .nx2-fx) */
    private fun fixChip(text: String): View {
        val (icon, bg, fg) = when {
            text.endsWith("이어서") -> Triple("↪", 0xFFE1F4EC.toInt(), 0xFF2E7A5F.toInt())
            text.endsWith("먼저") -> Triple("↺", 0xFFE1F4EC.toInt(), 0xFF2E7A5F.toInt())
            text.endsWith("줄임") -> Triple("↓", 0xFFFFF1DC.toInt(), 0xFFB4741C.toInt())
            text.endsWith("늘림") -> Triple("↑", 0xFFE1F4EC.toInt(), 0xFF2E7A5F.toInt())
            text.endsWith("나눠서") -> Triple("÷", 0xFFFFF1DC.toInt(), 0xFFB4741C.toInt())
            text.endsWith("시작") -> Triple("▲", 0xFFE3EEFB.toInt(), 0xFF3A6EA8.toInt())
            else -> Triple("✓", 0xFFE1F4EC.toInt(), 0xFF2E7A5F.toInt())
        }
        return LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(10), dp(7), dp(10), dp(7))
            background = rounded(0xCCFFFFFF.toInt(), 12, 0xF2FFFFFF.toInt())
            addView(TextView(context).apply {
                this.text = icon
                gravity = Gravity.CENTER
                textSize = if (icon == "▲") 9f else 12f
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(fg)
                background = rounded(bg, 7)
                layoutParams = LinearLayout.LayoutParams(dp(20), dp(20))
            })
            addView(TextView(context).apply {
                this.text = text
                textSize = 13f
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(color(R.color.oreum_dark_text))
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                    .apply { marginStart = dp(6) }
            })
        }
    }

    /** n구간 할 일 한 줄: (이어서 · 복습) 이름 · 분 (프로토타입 .nx2-it li) */
    private fun itemRow(title: String, minutes: Int, kind: ItemKind): View = LinearLayout(requireContext()).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(0, dp(10), 0, dp(10))
        val tag = when (kind) {
            ItemKind.CARRY -> Triple(R.string.round_kind_carry, 0xFFE3EEFB.toInt(), 0xFF3A6EA8.toInt())
            ItemKind.REVIEW -> Triple(R.string.round_kind_review, 0xFFFFF1DC.toInt(), 0xFFB4741C.toInt())
            ItemKind.NEW -> null
        }
        if (tag != null) {
            addView(TextView(context).apply {
                setText(tag.first)
                textSize = 10.5f
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(tag.third)
                setPadding(dp(6), dp(2), dp(6), dp(2))
                background = rounded(tag.second, 6)
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                    .apply { marginEnd = dp(8) }
            })
        }
        addView(TextView(context).apply {
            text = title
            textSize = 13.5f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(color(R.color.oreum_dark_text))
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })
        addView(TextView(context).apply {
            text = getString(R.string.next_item_minutes, minutes)
            textSize = 12.5f
            setTextColor(color(R.color.oreum_ink3))
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                .apply { marginStart = dp(8) }
        })
    }

    private fun rounded(fill: Int, radiusDp: Int, stroke: Int? = null) = GradientDrawable().apply {
        setColor(fill)
        cornerRadius = dpf(radiusDp)
        if (stroke != null) setStroke(dp(1), stroke)
    }

    private fun today() = LocalDate.now().toEpochDay()
    private fun toast(text: String) = requireActivity().showToast(text)
    private fun color(res: Int) = requireContext().getColor(res)
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
    private fun dpf(value: Int) = value * resources.displayMetrics.density

    override fun onDestroyView() {
        _binding = null
        backdrop = null
        super.onDestroyView()
    }

    companion object {
        const val ARG_GOAL_ID = "goalId"
        private const val CHIP_PLAIN = 0
        private const val CHIP_BAD = 1
        private const val CHIP_GOOD = 2

        /** 어느 화면에서든 추천 화면 열기 */
        fun open(nav: NavController, goalId: String) {
            nav.navigate(R.id.action_global_nextRound, Bundle().apply { putString(ARG_GOAL_ID, goalId) })
        }
    }
}
