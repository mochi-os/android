// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.ksp) apply false
}

// Fails the build on any incomplete or mis-filled string catalogue. The rule
// lives only in tools/check-locales.py, which discovers the modules itself -
// per-module copies drift.
tasks.register<Exec>("checkLocaleCompleteness") {
    description = "Fail if any Android string catalogue is incomplete or mis-filled."
    group = "verification"
    val checker = rootDir.resolve("tools/check-locales.py")
    // Always runs: the check takes under two seconds and declares no outputs.
    // Do not add a fileTree(rootDir) input - Gradle then treats every module's
    // build directory as an input and fails validation on the release variant.
    outputs.upToDateWhen { false }
    commandLine("python3", checker.absolutePath, "--discover", rootDir.absolutePath, "--strict")
}

// Every module, not just the two that used to carry their own copy.
subprojects {
    plugins.withId("com.android.base") {
        tasks.matching { it.name == "preBuild" }.configureEach {
            dependsOn(rootProject.tasks.named("checkLocaleCompleteness"))
        }
        // One lint policy for every module, in lint.xml beside this file. AGP 9
        // dropped CommonExtension's type parameters, so each plugin's own
        // extension type is configured; both branches set the same policy.
        val lintFile = rootProject.file("lint.xml")
        plugins.withId("com.android.application") {
            extensions.configure<com.android.build.api.dsl.ApplicationExtension>("android") {
                lint { lintConfig = lintFile }
            }
        }
        plugins.withId("com.android.library") {
            extensions.configure<com.android.build.api.dsl.LibraryExtension>("android") {
                lint { lintConfig = lintFile }
            }
        }
    }
}
