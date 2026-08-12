plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.riedel.aeroglobe"
    // 37 is required by androidx.lifecycle 2.11 and the Compose BOM 2026.06.
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.riedel.aeroglobe"
        // 26 rather than 24 so java.time is available natively. Core library
        // desugaring would also work but adds a build step for no benefit here:
        // API 26 still covers effectively every phone in use.
        minSdk = 26
        targetSdk = 37
        // 0.x while the plan's phases are still being worked through: the minor
        // number is the last completed phase (0.1 scaffold, 0.2 travel map and
        // backup, 0.3 passport and visas), so the About card never claims more than
        // the app does. The patch
        // number carries the half phases — 0.2.5 was phase 1.5, the round of fixes
        // from notes.md. versionCode only ever increases, independently of the name.
        versionCode = 4
        versionName = "0.3.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        // Off by default since AGP 8. On because the settings screen shows the
        // version name, and reading it back through PackageManager instead means a
        // deprecated call and a try/catch for something the build already knows.
        buildConfig = true
    }
}

ksp {
    // Committed schemas let migration tests run against the real previous version
    // instead of a hand-written reconstruction of it. The travel history is the one
    // thing in this app that cannot be re-downloaded, so migrations get tested.
    arg("room.schemaLocation", "$projectDir/schemas")
    arg("room.generateKotlin", "true")
}

dependencies {
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    debugImplementation(libs.compose.ui.tooling)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.kotlinx.coroutines.android)

    implementation(libs.maplibre.android.sdk)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    // Real org.json ahead of android.jar's throwing stubs, so the territory
    // regression test can parse the shipped places.json on the host JVM.
    testImplementation(libs.json)
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.room.testing)
    debugImplementation(libs.compose.ui.test.manifest)
}
