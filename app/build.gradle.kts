plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.riedel.beyondborders"
    // 37 is required by androidx.lifecycle 2.11 and the Compose BOM 2026.06.
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.riedel.beyondborders"
        // 30 (Android 11, 2020): only the author and a friend run this app, both
        // on recent phones, so there is no compatibility cost — and D8 refuses to
        // dex a class name with a space in it (which the backtick-sentence test
        // convention produces via lambda helpers) below DEX version 040, which
        // requires minSdk 30.
        minSdk = 30
        targetSdk = 37
        // 0.x while the plan's phases are still being worked through: the minor
        // number is the last completed phase (0.1 scaffold, 0.2 travel map and
        // backup, 0.3 passport and visas — flight tracking, once 0.4, was built
        // and then removed), so the About card never claims more than the app
        // does. The patch number carries the half phases — 0.2.5 was phase 1.5,
        // the round of fixes from notes.md.
        // versionCode only ever increases, independently of the name.
        versionCode = 13
        versionName = "0.7.8"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Sideloaded onto real phones, never distributed through a store that would
        // split by ABI itself — every real phone in use is arm64, so shipping the
        // other three MapLibre native-lib copies (armeabi-v7a, x86, x86_64) only
        // ever paid for emulator testing that this project doesn't do on-device.
        ndk {
            abiFilters += "arm64-v8a"
        }
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
    sourceSets {
        // MigrationTestHelper loads the exported schemas from the *test APK's* assets,
        // and `room.schemaLocation` below only writes them to a directory in the
        // source tree — nothing packages them. Without this line the migration test
        // fails at runtime with "Cannot find the schema file in the assets folder"
        // rather than at compile time.
        getByName("androidTest") {
            assets.srcDir("$projectDir/schemas")
        }
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
    implementation(libs.compose.material.icons.core)
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

// room-testing's bundled schema-bundle serializers are compiled against
// kotlinx-serialization 1.8.1 (see its own transitive request), but the Compose
// BOM's platform constraint strictly pins the whole classpath to 1.7.3 — an
// older ABI missing a method the serializers need, which throws
// AbstractMethodError at runtime rather than failing the build. Force wins
// over a platform's `strictly` constraint, unlike a plain version request.
configurations.matching { it.name.contains("AndroidTest") }.configureEach {
    resolutionStrategy {
        force(
            "org.jetbrains.kotlinx:kotlinx-serialization-core:1.8.1",
            "org.jetbrains.kotlinx:kotlinx-serialization-core-jvm:1.8.1",
        )
    }
}
