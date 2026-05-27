pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

// Auto-provisions the JDK toolchain(s) declared by each module
// (`jvmToolchain(17)` / `java.toolchain.languageVersion.set(17)`).
// Without this plugin, Gradle fails with
//     "No locally installed toolchains match and toolchain download
//      repositories have not been configured."
// on machines that don't have a standalone JDK 17 installed
// (Android Studio's bundled JBR is sufficient to *run* Gradle but
// doesn't satisfy task-level toolchain requirements). See
// `DEBUG_LOG.md` → DL-001 for the full RCA and prevention rule.
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.9.0"
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "Handy"
include(":core")
include(":android-runtime")
include(":app")
include(":macrobenchmark")
