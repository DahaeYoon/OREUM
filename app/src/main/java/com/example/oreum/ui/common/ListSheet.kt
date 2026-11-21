package com.example.oreum.ui.common

import android.graphics.Typeface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.ColorInt
import com.example.oreum.data.repository.StudyRepository
import com.example.oreum.data.repository.StudyRepositoryProvider
import com.example.oreum.databinding.SheetHomeListBinding
import kotlin.math.roundToInt

/** 유리 팝업 공용 틀 (제목 · 설명 · 목록 · 닫기): 기록 · 구간 상세에서 함께 써요 */
abstract class ListSheet : GlassSheetFragment() {
    private var _binding: SheetHomeListBinding? = null
    protected val binding get() = checkNotNull(_binding)
    protected val repository: StudyRepository = StudyRepositoryProvider.instance

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = SheetHomeListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.scroll.maxHeightPx = (resources.displayMetrics.heightPixels * 0.55f).toInt()
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

    protected fun wrap() = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
    protected fun fill(top: Int = 0) = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(top) }
    protected fun divider() = View(requireContext()).apply { setBackgroundColor(0x80E1E8E5.toInt()) }
}
