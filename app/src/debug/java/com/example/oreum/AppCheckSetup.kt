package com.example.oreum

import android.content.Context
import android.util.Log
import com.google.firebase.FirebaseApp
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

    /**
     * 이 기기의 디버그 토큰 (테스트하는 사람이 마이 → 오름 소개에서 복사해 앱 주인에게 보내요).
     * 기기마다 달라요. 콘솔에 등록하면 그 기기에서 AI를 쓸 수 있어요. 아직 만들어지지 않았으면 null.
     */
    fun debugToken(context: Context): String? = runCatching {
        val key = FirebaseApp.getInstance().persistenceKey
        context.getSharedPreferences("com.google.firebase.appcheck.debug.store.$key", Context.MODE_PRIVATE)
            .getString("com.google.firebase.appcheck.debug.DEBUG_SECRET", null)
    }.getOrNull()

    private const val TAG = "OreumAppCheck"
}
