# Makefile for the Mochi Android client
# Copyright © 2026 Mochisoft OÜ
# SPDX-License-Identifier: AGPL-3.0-only
# This file is part of Mochi, licensed under the GNU AGPL v3 with the
# Mochi Application Interface Exception - see license.txt and license-exception.md.

# versionName is the single source of truth — declared in app/build.gradle.kts
# and read by Gradle at build time. Read it here too so versions.json always
# matches the APK. Bump versionName there before `make release`.
version = $(shell grep -oP 'versionName\s*=\s*"\K[^"]+' app/build.gradle.kts)

# Signed release APK that `assembleRelease` produces.
apk = app/build/outputs/apk/release/app-release.apk

# Published packages tree in the umbrella repo (../../ = ~/mochi). Clients poll
# this app's subdirectory for mochi.apk + versions.json.
packages = ../../packages/android

.PHONY: all apk clean release

all: apk

# Build the signed release APK. Gradle handles incremental compilation, so this
# is cheap when nothing changed; the signing config lives in app/build.gradle.kts.
apk:
	./gradlew :app:assembleRelease

clean:
	./gradlew clean

# --------------------------------------------------------------------------
# Release
# --------------------------------------------------------------------------

# Build the signed APK, stage it into the published packages tree with a
# matching versions.json, then publish to both hosts. Target the stable SSH
# aliases (root@yuzu, root@wasabi), each with a pinned key in known_hosts —
# NOT packages.mochi-os.org, whose A/AAAA float between hosts and present a
# host key that fails strict checking. yuzu is the primary, wasabi the backup;
# both serve the same static tree, so neither depends on the other.
release: apk
	mkdir -p $(packages)
	cp $(apk) $(packages)/mochi.apk
	echo '{"tracks": {"production": "$(version)"}}' > $(packages)/versions.json
	rsync -av $(packages)/ root@yuzu:/srv/packages/android/
	rsync -av $(packages)/ root@wasabi:/srv/packages/android/
