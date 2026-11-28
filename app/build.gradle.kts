import java.io.FileInputStream
import java.util.Properties
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

val keystoreProperties = Properties()
val keystorePropertiesFile = rootProject.file("keystore.properties")
if (keystorePropertiesFile.exists()) {
    FileInputStream(keystorePropertiesFile).use { input ->
        keystoreProperties.load(input)
    }
}

fun signingValue(key: String): String? {
    val envValue = System.getenv(key)
    val gradleValue = providers.gradleProperty(key).orNull
    val fileValue = keystoreProperties.getProperty(key)
    return sequenceOf(envValue, gradleValue, fileValue)
        .firstOrNull { !it.isNullOrBlank() }
        ?.trim()
}

val signingStoreFile = signingValue("FLULY_SIGNING_STORE_FILE")
val signingStorePassword = signingValue("FLULY_SIGNING_STORE_PASSWORD")
val signingKeyAlias = signingValue("FLULY_SIGNING_KEY_ALIAS")
val signingKeyPassword = signingValue("FLULY_SIGNING_KEY_PASSWORD")
val hasReleaseSigning = listOf(
    signingStoreFile,
    signingStorePassword,
    signingKeyAlias,
    signingKeyPassword
).all { !it.isNullOrBlank() }
val disableReleaseMinify = providers.gradleProperty("fluly.disableReleaseMinify")
    .map { it.toBoolean() }
    .orElse(false)

android {
    namespace = "com.fluly.launcher"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.fluly.launcher"
        minSdk = 27
        targetSdk = 36
        versionCode = 31
        versionName = "2.4.0"
        ndk { abiFilters += listOf("armeabi-v7a", "arm64-v8a") }

        // 商店默认地址现在由 :watchface-editor 的 buildConfigField 提供
        // （-PflulyWatchStoreBaseUrl=https://host/api 仍然有效），app 不再自己存一份。
    }

    signingConfigs {
        create("release") {
            if (hasReleaseSigning) {
                storeFile = rootProject.file(signingStoreFile!!)
                storePassword = signingStorePassword
                keyAlias = signingKeyAlias
                keyPassword = signingKeyPassword
            }
        }
    }

    buildTypes {
        release {
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("release")
            }
            val minifyRelease = !disableReleaseMinify.get()
            isMinifyEnabled = minifyRelease
            isShrinkResources = minifyRelease
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
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
            // libproot_exec.so is launched by ProcessBuilder, so Android must
            // materialise it as a real executable in nativeLibraryDir.
            useLegacyPackaging = true
        }
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
        unitTests.isReturnDefaultValues = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(project(":ui-kit"))
    implementation(project(":watchface-core"))
    implementation(project(":watchface-runtime"))
    implementation(project(":watchface-store"))
    implementation(libs.core.ktx)
    implementation(libs.activity.compose)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.datastore.preferences)
    implementation(libs.mediarouter)
    implementation(libs.recyclerview)
    implementation("com.github.Dimezis:BlurView:version-3.2.0")

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.foundation)
    // material3 单独提到 1.5 beta：BOM 2026.04 捆绑的 1.4.0 里
    // MaterialExpressiveTheme / MotionScheme.expressive 仍是 internal，
    // 真 M3 Expressive 主题需要 1.5 的公开 API（:ui-kit 里同一份）。
    implementation(libs.compose.material3.expressive)
    implementation(libs.compose.material.icons)
    implementation(libs.compose.animation)
    implementation(libs.compose.cloudy)
    // miuix-icons 0.8.8 publishes its MiuixIcons receiver through a runtime
    // dependency. Add the matching core module explicitly so Kotlin can
    // resolve the official extension properties at compile time.
    implementation(libs.miuix.core)
    implementation(libs.miuix.icons)
    // 视频转码（ffmpeg-kit）只服务编辑器，已经随编辑器搬进 :watchface-editor。
    // WatchMaker face scripts run inside a Fluly-owned whitelist sandbox.  LuaJ is
    // used only as the pure-Java VM/compiler; its file, package and Java bridge
    // libraries are never exposed to a face.
    implementation(libs.luaj.jse)
    implementation(libs.xz)

    debugImplementation(libs.compose.ui.tooling)
    implementation(libs.compose.ui.tooling.preview)

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
}
