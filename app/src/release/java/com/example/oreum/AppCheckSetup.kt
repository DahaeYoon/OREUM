package com.example.oreum

import android.content.Context
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory

/** release 빌드: Play Integrity로 "진짜 OREUM 앱"인지 확인해요. */
object AppCheckSetup {
    fun install() {
        FirebaseAppCheck.getInstance().installAppCheckProviderFactory(PlayIntegrityAppCheckProviderFactory.getInstance())
    }

    /** release 빌드에는 디버그 토큰이 없어요 */
    @Suppress("UNUSED_PARAMETER")
    fun debugToken(context: Context): String? = null
}
