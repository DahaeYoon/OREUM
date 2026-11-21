package com.example.oreum.util

import android.app.Activity
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.annotation.StringRes
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.fragment.app.DialogFragment
import com.example.oreum.R

/**
 * 앱 안내 알림 (프로토타입 .toast): 진한 초록 카드 · 흰 글자 · 아래에서 살짝 올라와 2.4초 뒤 사라져요.
 * 안드로이드 기본 Toast(회색 알약) 대신 써요.
 * - 하단 탭이 보이면 탭 바로 위에, 아니면 화면 아래쪽에 떠요.
 * - 새 알림이 오면 앞의 알림은 바로 바뀌어요.
 */
object AppToast {

    private const val TAG = "oreum_app_toast"
    private const val SHOW_MS = 2400L

    /** 화면(Activity) 위에 띄워요. 팝업을 닫고 다른 화면으로 넘어가도 계속 보여요. */
    fun show(activity: Activity, text: CharSequence) {
        val root = activity.findViewById<ViewGroup>(android.R.id.content) ?: return
        // 알림 직후 화면이 바뀌는 경우(로그인 → 홈)가 많아서, 바뀐 화면 기준으로 위치를 정해요
        root.post {
            val d = root.resources.displayMetrics.density
            val nav = activity.findViewById<View>(R.id.bottom_nav)
            val navBar = ViewCompat.getRootWindowInsets(root)?.getInsets(WindowInsetsCompat.Type.navigationBars())?.bottom ?: 0
            val bottom = if (nav != null && nav.isVisible) {
                (nav.height.takeIf { it > 0 } ?: (80 * d).toInt()) + (12 * d).toInt()
            } else {
                navBar + (24 * d).toInt()
            }
            attach(root, text, bottom)
        }
    }

    /** 열려 있는 팝업 안에 띄워요 (팝업을 닫지 않고 안내만 할 때 · 예: 입력 확인). */
    fun showInDialog(fragment: DialogFragment, text: CharSequence) {
        val root = fragment.dialog?.takeIf { it.isShowing }?.window?.decorView as? ViewGroup
        if (root == null) {
            fragment.activity?.let { show(it, text) }
            return
        }
        // 팝업 아래쪽 버튼을 가리지 않게 조금 위에
        attach(root, text, (84 * root.resources.displayMetrics.density).toInt())
    }

    private fun attach(root: ViewGroup, text: CharSequence, bottomPx: Int) {
        val d = root.resources.displayMetrics.density
        root.findViewWithTag<View>(TAG)?.let { old ->
            old.animate().cancel()
            root.removeView(old)
        }
        val view = TextView(root.context).apply {
            tag = TAG
            this.text = text
            textSize = 13.5f
            setTextColor(0xFFFFFFFF.toInt())
            setLineSpacing(0f, 1.2f)
            setPadding((16 * d).toInt(), (12 * d).toInt(), (16 * d).toInt(), (12 * d).toInt())
            background = GradientDrawable().apply {
                cornerRadius = 14 * d
                setColor(0xFF263D39.toInt())
            }
            elevation = 8 * d
            accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
            alpha = 0f
            translationY = 16 * d
        }
        val lp = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM).apply {
            leftMargin = (16 * d).toInt()
            rightMargin = (16 * d).toInt()
            bottomMargin = bottomPx
        }
        root.addView(view, lp)
        view.animate().alpha(1f).translationY(0f).setDuration(200).start()
        view.postDelayed({
            if (view.parent == null) return@postDelayed
            view.animate().alpha(0f).setDuration(150).withEndAction { (view.parent as? ViewGroup)?.removeView(view) }.start()
        }, SHOW_MS)
    }
}

/** 화면 위 앱 알림 */
fun Activity.showToast(text: CharSequence) = AppToast.show(this, text)

fun Activity.showToast(@StringRes res: Int) = AppToast.show(this, getString(res))
