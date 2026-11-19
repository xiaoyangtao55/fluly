plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    alias(libs.plugins.kotlin.compose)
}

android {
    // 包名沿用 com.flue.launcher.ui.* ，模块本身不需要自己的 R。
    namespace = "com.flue.launcher.uikit"
    compileSdk = 36

    defaultConfig {
        minSdk = 27
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
        unitTests.isReturnDefaultValues = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

/**
 * 视觉底座：主题、控件、动效、圆屏几何、覆层（对话框 / 提示）。
 *
 * HOME、编辑器、商店、Studio 共用一套观感，所以这里只放「跟业务无关的 UI」：
 * 不认识 LauncherViewModel，也不认识表盘模型。需要宿主提供的东西一律走注入
 * （例如 [com.flue.launcher.ui.style.FlueOverlayPreferenceSource]）。
 */
dependencies {
    // 图像裁剪覆层要用 :watchface-core 的裁剪几何与受限解码；core 是纯模型/算法，不反向依赖 UI。
    api(project(":watchface-core"))

    implementation(libs.core.ktx)
    implementation(libs.activity.compose)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.kotlinx.coroutines.android)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.foundation)
    // 与 app 保持同一份 material3：BOM 2026.04 的 1.4.0 里
    // MaterialExpressiveTheme 还是 internal。
    implementation(libs.compose.material3.expressive)
    implementation(libs.compose.material.icons)
    implementation(libs.compose.animation)
    implementation(libs.compose.cloudy)
    implementation(libs.miuix.core)
    implementation(libs.miuix.icons)

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
}
