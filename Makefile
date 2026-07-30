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
	# Two copies: the stable name for humans clicking a download link, and a
	# version-stamped name for the in-app updater. The updater resumes a
	# partial download with a Range request, sometimes across days, so its
	# target must be immutable — appending the tail of a newly-published APK
	# onto the partial of the previous one splices two files into a corrupt
	# APK of exactly the expected length. Pruned to the current version.
	rm -f $(packages)/mochi-*.apk
	cp $(apk) $(packages)/mochi.apk
	cp $(apk) $(packages)/mochi-$(version).apk
	@sha=`sha256sum $(apk) | cut -d' ' -f1`; size=`wc -c < $(apk) | tr -d ' '`; \
	  printf '{"tracks": {"production": "%s"}, "releases": {"%s": {"file": "mochi-%s.apk", "size": %s, "sha256": "%s"}}}\n' \
	  '$(version)' '$(version)' '$(version)' "$$size" "$$sha" > $(packages)/versions.json
	rsync -av $(packages)/ root@yuzu:/srv/packages/android/
	rsync -av $(packages)/ root@wasabi:/srv/packages/android/
