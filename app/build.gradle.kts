plugins {
    id("com.android.application")
}

android {
    namespace = "com.fxxkmoondrop.secret"
    // Android 17 SDK, required by the current Material 3 toolchain.
    compileSdk = 37

    defaultConfig {
        applicationId = "com.fxxkmoondrop.secret"
        minSdk = 32
        targetSdk = 37
        versionCode = 401
        versionName = "3.4.1"

        }

    signingConfigs {
        create("release") {
            storeFile = rootProject.file("app2.keystore")
            storePassword = providers.environmentVariable("FXXK_KEYPASS").orElse(providers.gradleProperty("fxxkKeypass")).getOrElse("")
            keyAlias = providers.gradleProperty("fxxkKeyAlias").getOrElse("fxxk")
            keyPassword = providers.environmentVariable("FXXK_KEYPASS").orElse(providers.gradleProperty("fxxkKeypass")).getOrElse("")
        }
    }

    buildTypes {
        release {
            // 3.2.13: false -> true。Standalone Material 3 build keeps the release artifact lean, 
            // 开 R8 压缩换回可接受的体积。
            //
            // Keep rules below are limited to Android components created by the framework.
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("release")
        }
    }

    // 源码复用现有 src/（单一权威源，不复制双份）
    sourceSets {
        getByName("main") {
            // 3.2.13: srcDirs() 在 AGP 9 已废弃，改用 directories 可变集合。
            //
            // ⚠️ 必须**同时**注册 kotlin 与 java：原来的 java.srcDirs("../src")
            // 会同时把该目录纳入 Kotlin 与 Java 两套源；只写 java.directories 时
            // compileDebugKotlin 会报 NO-SOURCE，随后 Java 侧因找不到 Kotlin
            // 生成的类（如 AppLog.hex）报「cannot find symbol」——症状离根因很远。
            // 本项目源码在仓库根 src/（不是 app/src/），这个布局不能改。
            kotlin.directories.add("../src")
            java.directories.add("../src")
            manifest.srcFile("src/main/AndroidManifest.xml")
        }
    }

    compileOptions {
        // Java 21 bytecode target, aligned with the CI JDK.
        // 三处（compileOptions / jvmTarget / CI java-version）必须同步改，
        // 否则会出现「Kotlin 字节码 21 + Java 目标 17」的混编警告或失败。
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
    // Kotlin compiler settings are declared below.
    // 位置必须在顶层 kotlin {}（不能放在 android {} 内），见 kotl.in/u1r8ln

    // 纯 JVM 单元测试（GaiaCommands 帧构造/映射）；无 Android 依赖，缺失的框架桩返回默认值
    lint {
        checkReleaseBuilds = false
        abortOnError = false
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }

    packaging {
        resources {
            excludes += setOf(
                "META-INF/DEPENDENCIES",
                "META-INF/LICENSE*",
                "META-INF/NOTICE*",
                "META-INF/*.kotlin_module",
                "META-INF/versions/**"
            )
        }
    }
}

dependencies {
    // AndroidX（版本与旧链 libs/ 一致，2026-08-23 对齐）
    implementation("androidx.activity:activity:1.9.0")
    implementation("androidx.annotation:annotation:1.7.1")
    implementation("androidx.annotation:annotation-experimental:1.0.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.appcompat:appcompat-resources:1.7.0")
    implementation("androidx.arch.core:core-runtime:2.2.0")
    implementation("androidx.cardview:cardview:1.0.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.coordinatorlayout:coordinatorlayout:1.2.0")
    implementation("androidx.core:core:1.16.0")
    implementation("androidx.cursoradapter:cursoradapter:1.0.0")
    implementation("androidx.customview:customview:1.1.0")
    implementation("androidx.drawerlayout:drawerlayout:1.2.0")
    implementation("androidx.dynamicanimation:dynamicanimation:1.0.0")
    implementation("androidx.emoji2:emoji2:1.4.0")
    implementation("androidx.emoji2:emoji2-views:1.4.0")
    implementation("androidx.emoji2:emoji2-views-helper:1.4.0")
    implementation("androidx.fragment:fragment:1.7.1")
    implementation("androidx.interpolator:interpolator:1.0.0")
    implementation("androidx.lifecycle:lifecycle-livedata:2.7.0")
    implementation("androidx.lifecycle:lifecycle-livedata-core:2.7.0")
    implementation("androidx.lifecycle:lifecycle-process:2.7.0")
    implementation("androidx.lifecycle:lifecycle-runtime:2.7.0")
    implementation("androidx.lifecycle:lifecycle-service:2.7.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel:2.7.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-savedstate:2.7.0")
    implementation("androidx.loader:loader:1.0.0")
    implementation("com.google.android.material:material:1.14.0")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("androidx.savedstate:savedstate:1.2.1")
    implementation("androidx.savedstate:savedstate-ktx:1.2.1")
    implementation("androidx.transition:transition:1.5.1")
    implementation("androidx.vectordrawable:vectordrawable:1.2.0")
    implementation("androidx.vectordrawable:vectordrawable-animated:1.1.0")
    implementation("androidx.versionedparcelable:versionedparcelable:1.1.1")
    implementation("androidx.viewpager:viewpager:1.0.0")
    implementation("androidx.viewpager2:viewpager2:1.1.0")

    // Kotlin（与旧链 libs/ 版本一致）
    implementation("org.jetbrains.kotlin:kotlin-stdlib:2.4.10")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")

    // 单元测试（纯 JVM，验证 GaiaCommands 帧构造）
    // M3 color utilities used by the dynamic palette implementation.
    implementation("com.materialkolor:material-color-utilities:5.0.1")

    testImplementation("junit:junit:4.13.2")
    // 测试专用：android.jar 里的 org.json 是桩（且 unitTests.isReturnDefaultValues=true
    // 会让它静默返回默认值），必须用真实实现才能验证 release 响应的解析。
    // 仅测试期生效，不进 APK、不增加运行时体积。
    testImplementation("org.json:json:20260719")


}

kotlin {
    compilerOptions {
        // 3.2.13: 17 -> 21，与 CI 的 JDK 21 对齐（AGP 8.6 时代是 JDK 17）
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
    }
}
