plugins {
    alias(libs.plugins.android.application)
    // Firebase: app/google-services.json을 읽어 앱과 Firebase 프로젝트를 연결해요
    alias(libs.plugins.google.services)
}

android {
    namespace = "com.example.oreum"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.example.oreum"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    // STEP 0: XML 화면을 Kotlin에서 안전하게 쓰기 위한 ViewBinding
    buildFeatures {
        viewBinding = true
    }
}

dependencies {
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.core.ktx)
    implementation(libs.material)

    // STEP 0: Fragment · ViewModel · StateFlow 수집 · Navigation
    implementation(libs.androidx.fragment.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.navigation.fragment.ktx)
    implementation(libs.androidx.navigation.ui.ktx)

    // 1번: Firebase Authentication (BoM이 Firebase 라이브러리 버전을 맞춰 줘요)
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    // 2번: Cloud Firestore (계정별 목표 · 구간 · 학습 기록 저장)
    implementation(libs.firebase.firestore)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
}
