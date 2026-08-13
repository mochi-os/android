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

// Checks every module's string catalogues and FAILS the build. One task in one
// place, delegating to tools/check-locales.py, which is the only implementation
// of the rule.
//
// It used to be two Kotlin copies in lib/ and app/ plus a third in YAML in the
// CI workflow. All three drifted: two were blind to <plurals> and to Android's
// fallback chain, and neither Kotlin copy looked at the thirteen app modules at
// all - which is where every catalogue defect actually lived. Duplicating the
// rule per module is what let that happen, so this does not do that.
//
// The script discovers modules itself rather than reading a list from here,
// because a list here would be a fourth thing to keep in step with
// settings.gradle.kts.
tasks.register<Exec>("checkLocaleCompleteness") {
    description = "Fail if any Android string catalogue is incomplete or mis-filled."
    group = "verification"
    val checker = rootDir.resolve("tools/check-locales.py")
    // Always runs: the whole check is under two seconds across every module, and
    // it declares no outputs to be up-to-date against, so inputs would not let
    // Gradle skip it in any case. Do not add a fileTree(rootDir) input to try -
    // Gradle then tracks every module's build directory as an input location and
    // fails validation against the tasks that write there, which only shows up
    // on the release variant.
    outputs.upToDateWhen { false }
    commandLine("python3", checker.absolutePath, "--discover", rootDir.absolutePath, "--strict")
}

// Every module, not just the two that used to carry their own copy.
subprojects {
    plugins.withId("com.android.base") {
        tasks.matching { it.name == "preBuild" }.configureEach {
            dependsOn(rootProject.tasks.named("checkLocaleCompleteness"))
        }
        // One lint policy for every module, in lint.xml beside this file, for
        // the same reason the locale gate lives in one script: per-module copies
        // of a rule drift, and a @Suppress at each call site would restate the
        // same explanation at each call site. Each entry there carries its own
        // rationale, including why one is scoped rather than disabled.
        // AGP 9 dropped CommonExtension's six type parameters, and the raw
        // interface no longer resolves the `lint` block, so each plugin's own
        // extension type is configured instead. Both branches set the same one
        // policy — the split is a typing detail, not two rules.
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
