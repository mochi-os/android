// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
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
    // Re-run when any catalogue or the checker itself changes; skip otherwise.
    inputs.files(fileTree(rootDir) { include("**/src/main/res/values*/strings.xml") })
    inputs.file(checker)
    outputs.upToDateWhen { false }
    commandLine("python3", checker.absolutePath, "--discover", rootDir.absolutePath, "--strict")
}

// Every module, not just the two that used to carry their own copy.
subprojects {
    plugins.withId("com.android.base") {
        tasks.matching { it.name == "preBuild" }.configureEach {
            dependsOn(rootProject.tasks.named("checkLocaleCompleteness"))
        }
    }
}
