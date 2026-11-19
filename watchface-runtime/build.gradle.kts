plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    alias(libs.plugins.kotlin.compose)
}

android {
    // 与源码包名一致，模块内的 R 才能直接解析（运行时有 4 条字符串资源）。
    namespace = "com.flue.launcher.watchface.standard"
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
 * 表盘运行时：WatchMaker / JB 的解析、Lua 沙箱、渲染、归档安装。
 *
 * 这里只依赖 `:watchface-core`、LuaJ 与 Compose 的最小集合，不认识 launcher 的
 * Activity / ViewModel / 设置页 —— HOME 与 Studio 都能直接引用它渲染表盘。
 */
dependencies {
    api(project(":watchface-core"))

    implementation(libs.core.ktx)
    implementation(libs.luaj.jse)
    implementation(libs.kotlinx.coroutines.android)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.runtime)
    implementation(libs.compose.ui)
    implementation(libs.compose.foundation)

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
}
