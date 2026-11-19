plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.flue.launcher.watchface.core"
    compileSdk = 36

    defaultConfig {
        minSdk = 27
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
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
 * 表盘「共享底座」：画布尺寸、形状几何、裁剪数学。
 *
 * 这里只放纯计算与纯模型，不碰渲染、不碰 Compose、不碰资源，
 * 好让渲染层(:watchface-runtime)与编辑器(:app watchface/editor)
 * 各自依赖它，而不是互相依赖。
 */
dependencies {
    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
}
