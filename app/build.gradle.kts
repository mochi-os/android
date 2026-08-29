// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

// The one place the version is written. versionCode and versionName below both
// read it, because when they were two literals the 0.124 bump moved only the
// name and shipped a release carrying 0.123's code.
val mochiVersion = "0.125"

// "major.minor" -> a monotonically rising integer, major * 10000 + minor.
// Minor is bounded at 9999 so a major bump always outranks everything before it.
fun versionNameToCode(name: String): Int {
    val parts = name.split(".")
    val major = parts.getOrNull(0)?.toIntOrNull() ?: 0
    val minor = parts.getOrNull(1)?.toIntOrNull() ?: 0
    require(minor in 0..9999) { "version minor out of range: $name" }
    return major * 10000 + minor
}

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}

// Release signing reads its config from ~/.gradle/gradle.properties so the
// keystore and passwords stay out of the repo. Losing the signing key means
// every existing install must be uninstalled before a new build can replace it.
val releaseStorePath: String? = providers.gradleProperty("MOCHI_RELEASE_STORE_FILE").orNull
val releaseStoreFile: File? = releaseStorePath?.let(::File)?.takeIf { it.exists() }

android {
    namespace = "org.mochios.mochi"
    compileSdk = 37

    defaultConfig {
        applicationId = "org.mochios.mochi"
        minSdk = 26
        targetSdk = 35
        // Android's downgrade protection keys on versionCode alone, so it is
        // derived from versionName and must keep rising: "0.113" -> 113, "1.4"
        // -> 10004 across a major bump.
        versionCode = versionNameToCode(mochiVersion)
        versionName = mochiVersion
    }

    signingConfigs {
        if (releaseStoreFile != null) {
            create("release") {
                storeFile = releaseStoreFile
                storePassword = providers.gradleProperty("MOCHI_RELEASE_STORE_PASSWORD").orNull
                keyAlias = providers.gradleProperty("MOCHI_RELEASE_KEY_ALIAS").orNull
                keyPassword = providers.gradleProperty("MOCHI_RELEASE_KEY_PASSWORD").orNull
            }
        }
    }

    buildTypes {
        debug {
            isPseudoLocalesEnabled = true
        }
        release {
            // R8 shrinks the release APK from 43.5 MB to 26.7 MB and, because
            // dexing then has ~40% less code to chew through, builds it faster
            // than the unminified path did. proguard-rules.pro must stay wired
            // here: without it R8 runs on the library consumer rules alone and
            // renames the app modules' Gson model fields, so every field that
            // maps by name rather than @SerializedName silently decodes as its
            // default - a release-only data loss that no debug build shows.
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            if (releaseStoreFile != null) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(project(":lib"))
    implementation(project(":apps:feeds"))
    implementation(project(":apps:chat"))
    implementation(project(":apps:forums"))
    implementation(project(":apps:projects"))
    implementation(project(":apps:crm"))
    implementation(project(":apps:people"))
    implementation(project(":apps:settings"))
    implementation(project(":apps:wikis"))
    implementation(project(":apps:chess"))
    implementation(project(":apps:go"))
    implementation(project(":apps:words"))
    implementation(project(":apps:market"))
    implementation(project(":apps:staff"))

    implementation(libs.core.ktx)
    implementation(libs.navigation.compose)
    implementation(libs.lifecycle.runtime)
    implementation(libs.activity.compose)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons)
    debugImplementation(libs.compose.ui.tooling)
    implementation(libs.compose.ui.tooling.preview)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    implementation(libs.coroutines.core)
    implementation(libs.coroutines.android)
}
