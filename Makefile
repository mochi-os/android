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

.PHONY: all apk clean locales release

all: apk

# Every module's preBuild depends on the root checkLocaleCompleteness task, so
# this fails on an incomplete string catalogue rather than shipping one. `make
# locales` runs the same check alone.
apk:
	./gradlew :app:assembleRelease

# The portable form of the catalogue gate: no JVM, no Android SDK, no CI service.
# Same script the gradle task and the lint workflow call, so there is one set of
# rules and one place to change them.
locales:
	python3 tools/check-locales.py --discover . --strict

clean:
	./gradlew clean

# --------------------------------------------------------------------------
# Release
# --------------------------------------------------------------------------

# Build the signed APK, stage it into the published packages tree with a
# matching versions.json, then publish to yuzu. Target the root@yuzu SSH alias
# with its pinned key - NOT packages.mochi-os.org, whose address has moved
# between hosts and presents a host key that fails strict checking.
release: apk
	mkdir -p $(packages)
	# The version-stamped name must be immutable: the updater resumes a partial
	# download with a Range request, so appending a new APK's tail onto the
	# previous one's partial splices a corrupt file of exactly the expected length.
	# The stable name is a relative symlink to it, naming a file in this same
	# directory - the server opens it through os.Root and refuses a link that
	# leaves the tree.
	rm -f $(packages)/mochi-*.apk
	cp $(apk) $(packages)/mochi-$(version).apk
	ln -sfn mochi-$(version).apk $(packages)/mochi.apk
	@sha=`sha256sum $(apk) | cut -d' ' -f1`; size=`wc -c < $(apk) | tr -d ' '`; \
	  printf '{"tracks": {"production": "%s"}, "releases": {"%s": {"file": "mochi-%s.apk", "size": %s, "sha256": "%s"}}}\n' \
	  '$(version)' '$(version)' '$(version)' "$$size" "$$sha" > $(packages)/versions.json
	# Two passes: rsync creates a symlink up front but transfers its target minutes
	# later, so a single pass leaves the download URL pointing at nothing for the
	# whole upload; the second repoints the link.
	rsync -av --exclude=/mochi.apk $(packages)/ root@yuzu:/srv/packages/android/
	rsync -av $(packages)/ root@yuzu:/srv/packages/android/
