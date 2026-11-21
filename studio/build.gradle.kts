plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.flue.studio"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.flue.studio"
        minSdk = 27
        //noinspection ExpiredTargetSdkVersion
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"

        // Agent 的 rootfs 只有 arm64 版本，Studio 也只出 arm64 包。
        ndk {
            abiFilters += "arm64-v8a"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        jniLibs {
            // proot 由 ProcessBuilder 启动，必须是 nativeLibraryDir 里真正的可执行文件。
            useLegacyPackaging = true
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(project(":agent"))
    implementation(project(":ui-kit"))
    implementation(project(":watchface-core"))
    implementation(project(":watchface-runtime"))
    // 编辑器与商店整块搬进 Studio：HOME 从此不再打包 ffmpeg / miuix / WebView 商店。
    implementation(project(":watchface-editor"))
    implementation(libs.core.ktx)
    implementation(libs.activity.compose)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.lifecycle.viewmodel.compose)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.compose.animation)
    implementation(libs.compose.material3.expressive)
    implementation(libs.compose.material.icons)
    implementation(libs.compose.cloudy)
    implementation(libs.miuix.core)
    implementation(libs.miuix.icons)
    // 编辑器要转码导入的视频、在编辑器里试跑全局脚本，这些都得在运行时可见。
    implementation(libs.ffmpeg.kit.video)
    implementation(libs.smart.exception.java)
    implementation(libs.luaj.jse)
    testImplementation(libs.junit)
}
