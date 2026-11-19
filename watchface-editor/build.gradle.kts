plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    alias(libs.plugins.kotlin.compose)
}

android {
    // 与源码包名一致：模块内有 500+ 条表盘编辑器 / 商店文案。
    namespace = "com.flue.launcher.watchface.editor"
    compileSdk = 36

    defaultConfig {
        minSdk = 27
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    defaultConfig {
        // 商店默认地址随模块走：app 里的同名 buildConfigField 只服务 app 自己的代码。
        // 与 app 保持一致，可用 -PflueWatchStoreBaseUrl=https://host/api 覆盖。
        val watchStoreBaseUrl = providers.gradleProperty("flueWatchStoreBaseUrl")
            .getOrElse("https://jbwatchface.ai-life.xyz/api")
        buildConfigField("String", "WATCH_STORE_BASE_URL", "\"$watchStoreBaseUrl\"")
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
 * 表盘编辑器与表盘商店。
 *
 * 两件事放一个模块，是因为编辑器里内嵌了「发布到市场」的整条上传链路
 * （`WatchEditorPublishSheet` 直接用 store 的 API 与凭据存储），拆成两个模块
 * 只会让这条链路跨模块打洞。
 *
 * 这里**不**包含 HOME 的内置表盘配置页（`InternalWatchFaceConfigActivity` /
 * `CanvasAdjustActivity`）—— 那是桌面本体的一部分，留在 app。
 *
 * 对启动器的依赖一律走 [com.flue.launcher.watchface.editor.WatchEditorHost]：
 * 主题偏好、表盘列表、安装 / 选择 / 删除都在宿主那边，模块本身不认识
 * LauncherViewModel。
 */
dependencies {
    implementation(project(":ui-kit"))
    implementation(project(":watchface-core"))
    implementation(project(":watchface-runtime"))
    implementation(project(":agent"))

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
