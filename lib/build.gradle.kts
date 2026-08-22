// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}

android {
    namespace = "org.mochios.android"
    compileSdk = 37

    defaultConfig {
        minSdk = 26
        consumerProguardFiles("consumer-rules.pro")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        // For BuildConfig.DEBUG, which gates the HTTP logging interceptor in
        // ApiClient: it writes every request URL, and OkHttp keeps application
        // interceptors on WebSocket handshakes, so an unguarded one puts URLs
        // (and anything carried in their query) into release logcat.
        buildConfig = true
    }
}


dependencies {
    api(libs.core.ktx)
    api(libs.lifecycle.runtime)
    api(libs.lifecycle.viewmodel)
    api(libs.activity.compose)
    api(libs.navigation.compose)
    implementation(libs.datastore.preferences)

    api(platform(libs.compose.bom))
    api(libs.compose.ui)
    api(libs.compose.ui.graphics)
    api(libs.compose.material3)
    api(libs.compose.material.icons)
    api(libs.compose.ui.text.google.fonts)
    implementation(libs.compose.ui.tooling.preview)

    implementation(libs.credentials)
    implementation(libs.credentials.play.services)
    implementation(libs.browser)

    api(libs.hilt.android)
    ksp(libs.hilt.compiler)
    api(libs.hilt.navigation.compose)

    api(libs.retrofit)
    api(libs.retrofit.gson)
    api(libs.okhttp)
    implementation(libs.okhttp.logging)
    api(libs.gson)

    api(libs.coroutines.core)
    api(libs.coroutines.android)

    api(libs.coil.compose)
    api(libs.coil.network)
    api(libs.media3.exoplayer)
    api(libs.media3.ui)
    api(libs.media3.datasource.okhttp)

    implementation(libs.markwon.core)
    implementation(libs.markwon.html)
    implementation(libs.markwon.image)
    implementation(libs.markwon.ext.tables)
    implementation(libs.markwon.ext.strikethrough)
    implementation(libs.markwon.ext.tasklist)
    implementation(libs.markwon.linkify)

    implementation(libs.osmdroid)

    api(libs.unifiedpush.connector)
    implementation(libs.firebase.messaging)

    implementation(libs.work.runtime)

    testImplementation(libs.junit)
    testImplementation(libs.okhttp.mockwebserver)
}
