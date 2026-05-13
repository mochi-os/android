plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}

// Release signing reads its config from ~/.gradle/gradle.properties so the
// keystore + passwords stay out of the repo. The signing key is the user-
// visible identity of every published Mochi release — losing it means every
// existing install has to be uninstalled before a new build can replace it.
// See .claude/commands/android-release.md for the release flow.
val releaseStorePath: String? = providers.gradleProperty("MOCHI_RELEASE_STORE_FILE").orNull
val releaseStoreFile: File? = releaseStorePath?.let(::File)?.takeIf { it.exists() }

android {
    namespace = "org.mochios.mochi"
    compileSdk = 35

    defaultConfig {
        applicationId = "org.mochios.mochi"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.3"
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
            // Minification is off until ProGuard rules are tuned for Hilt /
            // Retrofit / Compose reflection paths — enabling it without
            // tuned rules would surface runtime crashes that hide in the
            // debug build. APK is ~30 MB either way for now.
            isMinifyEnabled = false
            if (releaseStoreFile != null) {
                signingConfig = signingConfigs.getByName("release")
            }
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
}

tasks.register("checkLocaleCompleteness") {
    val resDir = file("src/main/res")
    inputs.dir(resDir)
    doLast {
        val source = resDir.resolve("values/strings.xml")
        if (!source.exists()) return@doLast
        val keyPattern = Regex("""<string name="([^"]+)"""")
        val sourceKeys = keyPattern.findAll(source.readText()).map { it.groupValues[1] }.toSet()
        val overlays = setOf("values-en-rUS")
        val problems = mutableListOf<String>()
        resDir.listFiles { f -> f.isDirectory && f.name.startsWith("values-") }?.forEach { dir ->
            if (dir.name in overlays) return@forEach
            val xml = dir.resolve("strings.xml")
            if (!xml.exists()) return@forEach
            val have = keyPattern.findAll(xml.readText()).map { it.groupValues[1] }.toSet()
            val missing = sourceKeys - have
            if (missing.isNotEmpty()) {
                problems += "${dir.name}: ${missing.size} missing (${missing.take(3).joinToString()}…)"
            }
        }
        if (problems.isNotEmpty()) {
            logger.warn("Locale catalogs incomplete (run translate-android-from-web.py + fill residue):\n  " + problems.joinToString("\n  "))
        }
    }
}
tasks.named("preBuild") { dependsOn("checkLocaleCompleteness") }

dependencies {
    implementation(project(":lib"))
    implementation(project(":apps:feeds"))
    implementation(project(":apps:chat"))
    implementation(project(":apps:forums"))
    implementation(project(":apps:projects"))

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
