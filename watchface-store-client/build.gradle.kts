plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.flue.launcher.watchface.store.client"
    compileSdk = 36

    defaultConfig {
        minSdk = 27
        // Studio publishing and the store UI share this independently configured client.
        val watchStoreBaseUrl = providers.gradleProperty("flueWatchStoreBaseUrl")
            .getOrElse("https://jbwatchface.ai-life.xyz/api")
        buildConfigField("String", "WATCH_STORE_BASE_URL", "\"$watchStoreBaseUrl\"")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
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

/** Store transport, credentials, publishing validation, installation catalog and caches; no UI. */
dependencies {
    // Public repository signatures expose core descriptors and runtime scan results.
    api(project(":watchface-core"))
    api(project(":watchface-runtime"))
    implementation(libs.kotlinx.coroutines.android)

    // Preserve model stability annotations without depending on Compose UI or a UI module.
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.runtime)

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
}
