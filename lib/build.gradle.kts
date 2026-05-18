plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}

android {
    namespace = "org.mochios.android"
    compileSdk = 35

    defaultConfig {
        minSdk = 26
        consumerProguardFiles("consumer-rules.pro")
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

// Fails the build if any key in the source `values/strings.xml` is missing
// from any locale-qualified `values-*/strings.xml`. The en-US overlay is
// exempt — it's allowed to ship only the spelling diffs.
tasks.register("checkLocaleCompleteness") {
    val resDir = file("src/main/res")
    inputs.dir(resDir)
    doLast {
        val source = resDir.resolve("values/strings.xml")
        if (!source.exists()) return@doLast
        val keyPattern = Regex("""<string name="([^"]+)"""")
        val sourceKeys = keyPattern.findAll(source.readText()).map { it.groupValues[1] }.toSet()
        // Brand-identity strings stay verbatim in every locale (Latin script,
        // unchanged) — see the i18n glossary rule in CLAUDE.md. Excluding them
        // here keeps the warning signal honest; otherwise every locale flags
        // missing entries for keys that intentionally don't need translation.
        val brandKeys = setOf("app_brand")
        val checkKeys = sourceKeys - brandKeys
        val overlays = setOf("values-en-rUS")
        val problems = mutableListOf<String>()
        resDir.listFiles { f -> f.isDirectory && f.name.startsWith("values-") }?.forEach { dir ->
            if (dir.name in overlays) return@forEach
            val xml = dir.resolve("strings.xml")
            if (!xml.exists()) return@forEach
            val have = keyPattern.findAll(xml.readText()).map { it.groupValues[1] }.toSet()
            val missing = checkKeys - have
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
}
