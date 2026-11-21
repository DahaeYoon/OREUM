// Top-level build file where you can add configuration options common to all sub-projects/modules.

// Firebase 최신 라이브러리는 Kotlin 2.4로 만들어져서, AGP에 들어 있는 Kotlin(2.2)보다 높은 버전이 필요해요.
// AGP 9 안내대로 Kotlin Gradle 플러그인 버전만 올려요.
buildscript {
    repositories {
        google()
        mavenCentral()
    }
    dependencies {
        classpath(libs.kotlin.gradle.plugin)
    }
}

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.google.services) apply false
}