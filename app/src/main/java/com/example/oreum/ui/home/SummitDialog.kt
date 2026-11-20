package com.example.oreum.ui.home

import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.DialogFragment
import com.example.oreum.R
import com.example.oreum.data.model.RoundStatus
import com.example.oreum.data.repository.StudyRepositoryProvider
import com.example.oreum.databinding.DialogSummitBinding
import com.example.oreum.ui.common.GlassBackdrop
import com.example.oreum.ui.common.GlassBackgroundDrawable
import com.example.oreum.util.DateText
import java.time.LocalDate

/**
 * 정상 도착 (프로토타입 summit-pop · 1차)
 * 마친 구간 · 학습 시간 · 등반 기간을 보여 주고, 새 산(목표 만들기) 또는 정상 둘러보기(닫기).
 * 등반 분석 · 오른 산 모아보기는 2차예요.
 */
class SummitDialog : DialogFragment() {

    private var _binding: DialogSummitBinding? = null
    private val binding get() = checkNotNull(_binding)
    private var backdrop: GlassBackdrop? = null

    override fun getTheme(): Int = R.style.ThemeOverlay_OREUM_GlassSheet

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        backdrop = activity?.window?.decorView?.let { GlassBackdrop.capture(it) }
        return super.onCreateDialog(savedInstanceState).apply { setCanceledOnTouchOutside(true) }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = DialogSummitBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val density = resources.displayMetrics.density
        view.background = GlassBackgroundDrawable(
            host = view,
            backdrop = backdrop,
            radiusPx = 26 * density,
            strokePx = density,
            topOnly = false,
            fillColor = 0xB3FFFFFF.toInt(),
            strokeColor = 0xE6FFFFFF.toInt(),
        )
        binding.boxStats.background = GradientDrawable().apply {
            cornerRadius = 16 * density
            setColor(0xA6FFFFFF.toInt())
            setStroke(density.toInt().coerceAtLeast(1), 0xE6FFFFFF.toInt())
        }

        val repository = StudyRepositoryProvider.instance
        val goalId = requireArguments().getString(ARG_GOAL).orEmpty()
        val goal = repository.goals.value.firstOrNull { it.id == goalId }
        val rounds = repository.rounds.value.filter { it.goalId == goalId && it.status == RoundStatus.DONE }
        val sessions = repository.sessions.value.filter { it.goalId == goalId && it.minutes > 0 }
        val today = LocalDate.now().toEpochDay()
        val start = sessions.minOfOrNull { it.date } ?: today
        val end = goal?.completedAt?.let { DateText.toDate(it).toEpochDay() } ?: today

        binding.textGoal.text = goal?.title.orEmpty()
        binding.statRounds.text = getString(R.string.round_count, rounds.size)
        binding.statTime.text = DateText.minutes(sessions.sumOf { it.minutes })
        binding.statDays.text = getString(R.string.records_days_value, (end - start + 1).coerceAtLeast(1).toInt())

        binding.btnNew.setOnClickListener {
            val home = parentFragment as? HomeFragment
            dismiss()
            home?.openNewGoal()
        }
        binding.btnLook.setOnClickListener { dismiss() }
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            val margin = (28 * resources.displayMetrics.density).toInt()
            setLayout(resources.displayMetrics.widthPixels - margin * 2, ViewGroup.LayoutParams.WRAP_CONTENT)
            setGravity(Gravity.CENTER)
            setDimAmount(0.2f)
        }
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }

    override fun onDestroy() {
        backdrop = null
        super.onDestroy()
    }

    companion object {
        const val TAG = "SummitDialog"
        private const val ARG_GOAL = "goalId"

        fun newInstance(goalId: String) = SummitDialog().apply {
            arguments = Bundle().apply { putString(ARG_GOAL, goalId) }
        }
    }
}
