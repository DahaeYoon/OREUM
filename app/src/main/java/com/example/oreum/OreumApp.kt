package com.example.oreum

import android.app.Application
import com.google.firebase.FirebaseApp

/**
 * 앱 시작 시 한 번: Firebase 준비 + App Check 설치.
 * App Check는 "진짜 OREUM 앱에서 온 요청"만 AI(Gemini)를 쓸 수 있게 해요.
 * - 실제 휴대폰(release): Play Integrity
 * - 에뮬레이터 · 개발(debug): 디버그 토큰 (Firebase 콘솔 → App Check → 디버그 토큰 관리에 등록)
 * 어떤 방식을 쓸지는 src/debug · src/release 의 AppCheckSetup이 정해요.
 */
class OreumApp : Application() {
    override fun onCreate() {
        super.onCreate()
        FirebaseApp.initializeApp(this)
        AppCheckSetup.install()
    }
}
