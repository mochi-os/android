// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

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
        versionName = "0.110"
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
        // Both element kinds are user-facing text. Matching only <string> left
        // every <plurals> invisible in both directions, which is how the
        // count-bearing strings went unfilled in most locales for so long.
        val keyPattern = Regex("""<(?:string|plurals) name="([^"]+)"""")
        val sourceKeys = keyPattern.findAll(source.readText()).map { it.groupValues[1] }.toSet()
        // Brand-identity strings stay verbatim in every locale (Latin script,
        // unchanged) — see the i18n glossary rule in CLAUDE.md. Excluding them
        // here keeps the warning signal honest; otherwise every locale flags
        // missing entries for keys that intentionally don't need translation.
        val brandKeys = setOf("app_name")
        val checkKeys = sourceKeys - brandKeys
        // Android resolves a resource language+region -> language -> default, per
        // resource rather than per file, so a key held only by values-de is still
        // the text a de-CH reader sees. Modelling the chain replaces the old
        // hardcoded exempt list, which both missed overlays and hid real gaps.
        // zh-Hant-HK's parent is zh-Hant in CLDR and there is no values-zh here.
        val scriptParents = mapOf(
            "values-zh-rHK" to "values-b+zh+Hant",
            "values-zh-rMO" to "values-b+zh+Hant",
            "values-zh-rCN" to "values-b+zh+Hans",
            "values-zh-rSG" to "values-b+zh+Hans",
        )
        fun parentOf(name: String): String? {
            scriptParents[name]?.let { return it }
            val tag = name.removePrefix("values-")
            return when {
                tag.startsWith("b+") ->
                    "values-" + tag.removePrefix("b+").substringBefore('+').lowercase()
                tag.contains("-r") -> "values-" + tag.substringBefore("-r")
                else -> null
            }
        }
        // English regional variants ship spelling diffs and fall through to
        // values/, which is neutral English by design, so a key absent there
        // already resolves to the right text.
        val englishVariant = Regex("""^values-en(-|$)""")
        val problems = mutableListOf<String>()
        resDir.listFiles { f -> f.isDirectory && f.name.startsWith("values-") }?.forEach { dir ->
            if (englishVariant.containsMatchIn(dir.name)) return@forEach
            val xml = dir.resolve("strings.xml")
            if (!xml.exists()) return@forEach
            val have = keyPattern.findAll(xml.readText()).map { it.groupValues[1] }.toMutableSet()
            val parent = parentOf(dir.name)?.let { resDir.resolve("$it/strings.xml") }
            if (parent != null && parent.exists()) {
                have += keyPattern.findAll(parent.readText()).map { it.groupValues[1] }
            }
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
