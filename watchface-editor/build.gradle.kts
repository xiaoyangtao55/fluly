plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    alias(libs.plugins.kotlin.compose)
}

android {
    // Studio 创作编辑器；商店 UI 在独立模块，发布仅复用 client。
    namespace = "com.fluly.launcher.watchface.editor"
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
        buildConfig = true
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
 * Studio 的表盘创作编辑器。
 *
 * 发布仅依赖 :watchface-store-client 的 API 与凭据，不包含商店浏览或安装 UI。
 * HOME 的商店单独使用 :watchface-store，不会因此带入 Agent 或 FFmpeg。
 *
 * 这里**不**包含 HOME 的内置表盘配置页（`InternalWatchFaceConfigActivity` /
 * `CanvasAdjustActivity`）—— 那是桌面本体的一部分，留在 app。
 *
 * 对启动器的依赖一律走 [com.fluly.launcher.watchface.editor.WatchEditorHost]：
 * 主题偏好、表盘列表、安装 / 选择 / 删除都在宿主那边，模块本身不认识
 * LauncherViewModel。
 */
dependencies {
    implementation(project(":ui-kit"))
    implementation(project(":watchface-core"))
    implementation(project(":watchface-runtime"))
    implementation(project(":agent"))
    implementation(project(":watchface-store-client"))

    implementation(libs.core.ktx)
    implementation(libs.activity.compose)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.lifecycle.viewmodel.ktx)
    implementation(libs.kotlinx.coroutines.android)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3.expressive)
    implementation(libs.compose.material.icons)
    implementation(libs.compose.animation)
    implementation(libs.compose.cloudy)
    implementation(libs.miuix.core)
    implementation(libs.miuix.icons)
    // 编辑器要转码 / 裁切导入的视频；LuaJ 只用于在编辑器里试跑全局脚本。
    implementation(libs.ffmpeg.kit.video)
    implementation(libs.smart.exception.java)
    implementation(libs.luaj.jse)

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
}
