package com.example.oreum.util

import android.os.Bundle
import androidx.annotation.IdRes
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController

/**
 * 지금 화면에서 갈 수 있는 이동일 때만 이동해요.
 * 버튼을 빠르게 두 번 누르거나, 아래에서 올라오는 팝업(목표 만들기)이 열린 상태에서 뒤 화면 버튼이 또 눌리면
 * 이미 다른 화면으로 바뀐 뒤라 앱이 꺼질 수 있어서 막아 둬요.
 */
fun Fragment.navigateSafe(@IdRes action: Int, args: Bundle? = null) {
    val nav = findNavController()
    if (nav.currentDestination?.getAction(action) == null) return
    nav.navigate(action, args)
}
