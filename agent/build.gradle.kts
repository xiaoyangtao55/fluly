plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.fluly.launcher.agent"
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

dependencies {
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.lifecycle.viewmodel.ktx)
    // rootfs 的 .tar.xz 解压。
    implementation(libs.xz)
    // 表达式分类器是纯 Kotlin，渲染层与 agent 共用，放 :watchface-core。
    implementation(project(":watchface-core"))

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
}
