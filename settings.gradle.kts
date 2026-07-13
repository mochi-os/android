// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // chesslib (com.github.bhlangonijr:chesslib) is published via JitPack.
        maven { url = uri("https://jitpack.io") }
    }
}
rootProject.name = "Mochi"
include(":lib")
include(":app")
include(":apps:feeds")
include(":apps:chat")
include(":apps:forums")
include(":apps:projects")
include(":apps:crm")
include(":apps:people")
include(":apps:settings")
include(":apps:wikis")
include(":apps:chess")
include(":apps:go")
include(":apps:words")
include(":apps:market")
include(":apps:staff")
