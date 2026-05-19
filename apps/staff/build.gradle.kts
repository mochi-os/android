plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}

android {
    namespace = "org.mochios.staff"
    compileSdk = 35

    defaultConfig {
        minSdk = 26
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
}

dependencies {
    implementation(project(":lib"))
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    // Custom Tabs for Comptroller dashboard / external moderator-tool redirects.
    implementation(libs.browser)

    // DataStore for staff-local prefs (last-viewed filters, expanded rows, etc.).
    implementation(libs.datastore.preferences)
}
