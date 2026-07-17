// Gradle settings: declares where plugins/dependencies are fetched from and
// which modules make up the build. This project is a single module (`:app`).

pluginManagement {
    repositories {
        google {
            // Restrict the Google repo to Android/Google artifacts so Gradle
            // doesn't waste time searching it for everything else.
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

dependencyResolutionManagement {
    // Fail the build if any module declares its own repositories — all
    // dependencies must come from the two trusted repos below. This is part
    // of the F-Droid "no mystery artifacts" hygiene (docs/TESTING_AND_CI.md).
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "TalkingClock"
include(":app")
