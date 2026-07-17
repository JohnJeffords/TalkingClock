// Build configuration for the single app module.
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)   // Kotlin 2.0's built-in Compose compiler
    alias(libs.plugins.roborazzi)        // JVM screenshot testing
}

android {
    namespace = "io.github.johnjeffords.talkingclock"
    compileSdk = 35

    defaultConfig {
        applicationId = "io.github.johnjeffords.talkingclock"
        minSdk = 26          // Android 8.0 — see docs/DECISIONS.md D-005
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"

        // The instrumented-test runner (used later for on-device UI tests).
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Ship only English resources for now; Android 13 per-app language
        // support comes free once we add more locales (see ARCHITECTURE.md).
        resourceConfigurations += listOf("en")
    }

    buildTypes {
        release {
            // R8 shrinks and optimizes; resource shrinking drops unused
            // drawables/strings. Together they keep the release APK small
            // (the 4 MB budget in docs/ARCHITECTURE.md).
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
    }

    testOptions {
        unitTests {
            // Robolectric + Roborazzi need real Android resources on the
            // classpath to render composables on the JVM.
            isIncludeAndroidResources = true
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.coroutines.android)

    // Compose — the BOM (platform) pins every compose artifact's version.
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    debugImplementation(libs.androidx.compose.ui.tooling)

    // --- Unit + screenshot tests (plain JVM, no emulator) ---
    testImplementation(libs.junit)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.compose.ui.test.junit4)
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.compose)
    testImplementation(libs.roborazzi.junit.rule)
    testImplementation(libs.androidx.compose.ui.tooling)          // ComposeView host for screenshots
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
