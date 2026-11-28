pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        exclusiveContent {
            forRepository { maven("https://jitpack.io") }
            filter { includeModule("com.github.Dimezis", "BlurView") }
        }
    }
}

rootProject.name = "Fluly"
include(":app")
include(":ui-kit")
include(":watchface-editor")
include(":watchface-store-client")
include(":watchface-store")
include(":agent")
include(":watchface-core")
include(":watchface-runtime")
include(":studio")
