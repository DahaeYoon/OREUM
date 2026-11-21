package com.example.oreum

import android.util.Log
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory

/**
 * debug 빌드: App Check 디버그 토큰 사용 (에뮬레이터에서도 AI를 쓸 수 있게).
 * 처음 실행하면 Logcat에 'Enter this debug secret into the allow list...' 와 토큰이 찍혀요.
 * 그 토큰을 Firebase 콘솔 → App Check → 앱 → ⋮ → 디버그 토큰 관리에 등록해요. (토큰은 커밋 · 공유하지 않기)
 */
object AppCheckSetup {
    fun install() {
        val appCheck = FirebaseAppCheck.getInstance()
        appCheck.installAppCheckProviderFactory(DebugAppCheckProviderFactory.getInstance())
        // 토큰이 Logcat에 바로 찍히도록 한 번 요청해요
        appCheck.getAppCheckToken(false)
            .addOnSuccessListener { Log.d(TAG, "App Check 준비됨") }
            .addOnFailureListener { Log.w(TAG, "App Check 토큰을 아직 받지 못했어요 (디버그 토큰을 콘솔에 등록했는지 확인)", it) }
    }

    private const val TAG = "OreumAppCheck"
}
