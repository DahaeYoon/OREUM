package com.example.oreum.ui.common

import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.DialogFragment
import com.example.oreum.R

/**
 * 아래에서 올라오는 유리 팝업의 공통 부모 (프로토타입 .glass-sheet와 같은 모습).
 * - 뒤 화면을 흐리게 비추는 흰 45% 유리 배경 (GlassBackgroundDrawable)
 * - 위쪽만 둥글게(24dp) · 흰 테두리 · 뒤 화면은 12%만 어둡게
 * - 화면 아래에 붙고, 바깥을 누르거나 뒤로 가기 하면 닫혀요.
 * Material 기본 BottomSheet는 자기 흰 배경을 다시 칠해서 쓰지 않아요.
 */
abstract class GlassSheetFragment : DialogFragment() {

    private var backdrop: GlassBackdrop? = null

    override fun getTheme(): Int = R.style.ThemeOverlay_OREUM_GlassSheet

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        // 팝업이 뜨기 전에 뒤 화면을 찍어 둬요 (팝업 뒤 흐림용)
        backdrop = activity?.window?.decorView?.let { GlassBackdrop.capture(it) }
        return super.onCreateDialog(savedInstanceState).apply { setCanceledOnTouchOutside(true) }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val density = resources.displayMetrics.density
        view.background = GlassBackgroundDrawable(view, backdrop, radiusPx = 24 * density, strokePx = density)
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            setGravity(Gravity.BOTTOM)
            setDimAmount(0.12f)
        }
    }

    override fun onDestroy() {
        backdrop?.bitmap?.recycle()
        backdrop = null
        super.onDestroy()
    }
}
