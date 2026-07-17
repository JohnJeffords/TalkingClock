// Root build file. Plugins are declared here with `apply false` so their
// versions are fixed project-wide; each module opts in by re-declaring the
// plugin (without a version) in its own build.gradle.kts.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.roborazzi) apply false
}
