// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.util

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * An attachment's MIME type is peer-supplied and unvalidated, and
 * AttachmentOpener hands it to ACTION_VIEW with REQUEST_INSTALL_PACKAGES held.
 */
class MimeSafetyTest {

    /** The attack: this type resolves to the system package installer. */
    @Test
    fun `an android package is never viewable`() {
        assertEquals(OPAQUE_MIME, coerceMimeType("application/vnd.android.package-archive"))
    }

    /** Normalised before matching, so case and parameters cannot smuggle it past. */
    @Test
    fun `the package type is caught however it is spelled`() {
        assertEquals(OPAQUE_MIME, coerceMimeType("APPLICATION/VND.ANDROID.PACKAGE-ARCHIVE"))
        assertEquals(OPAQUE_MIME, coerceMimeType("application/vnd.android.package-archive; x=1"))
        assertEquals(OPAQUE_MIME, coerceMimeType("  application/vnd.android.package-archive  "))
    }

    /** A wildcard would let the chooser offer every handler, installer included. */
    @Test
    fun `wildcards are refused`() {
        assertEquals(OPAQUE_MIME, coerceMimeType("*/*"))
        assertEquals(OPAQUE_MIME, coerceMimeType("application/*"))
    }

    @Test
    fun `an absent type is opaque rather than a wildcard`() {
        assertEquals(OPAQUE_MIME, coerceMimeType(""))
        assertEquals(OPAQUE_MIME, coerceMimeType("   "))
    }

    /** Anything not recognised is opaque — an allowlist, not a blocklist. */
    @Test
    fun `unknown executable-ish types are opaque`() {
        assertEquals(OPAQUE_MIME, coerceMimeType("application/x-executable"))
        assertEquals(OPAQUE_MIME, coerceMimeType("application/java-archive"))
        assertEquals(OPAQUE_MIME, coerceMimeType("application/x-sh"))
        assertEquals(OPAQUE_MIME, coerceMimeType("application/vnd.android.package-archive.v2"))
    }

    /** The point is to keep genuine attachments working. */
    @Test
    fun `ordinary media and documents pass through unchanged`() {
        for (type in listOf(
            "image/png", "image/jpeg", "image/svg+xml",
            "video/mp4", "audio/mpeg",
            "text/plain", "text/csv",
            "application/pdf", "application/zip",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/vnd.oasis.opendocument.text",
        )) {
            assertEquals(type, coerceMimeType(type))
        }
    }

    @Test
    fun `parameters are dropped from an accepted type`() {
        assertEquals("text/plain", coerceMimeType("text/plain; charset=utf-8"))
        assertEquals("image/png", coerceMimeType("IMAGE/PNG"))
    }
}
