// MoonEar Android application build plugins.
plugins {
    id("com.android.application") version "9.4.1" apply false
    // Pin the AGP 9 built-in Kotlin compiler to the app's Kotlin stdlib version.
    // The Android module must not apply the legacy kotlin-android plugin separately.
    id("org.jetbrains.kotlin.android") version "2.4.10" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.10" apply false
}
