// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The version compare decides whether the client downloads a ~40 MB APK and
 * offers it to the system installer, and it is the only gate on doing so. A
 * lexicographic slip here means either never updating past "0.9" or offering an
 * older build as if it were newer.
 */
class UpdateVersionTest {

    @Test
    fun `minor components compare numerically, not as text`() {
        assertTrue(UpdateChecker.compareVersions("0.10", "0.9") > 0)
        assertTrue(UpdateChecker.compareVersions("0.120", "0.99") > 0)
        assertTrue(UpdateChecker.compareVersions("0.9", "0.10") < 0)
    }

    @Test
    fun `a major bump outranks any minor`() {
        assertTrue(UpdateChecker.compareVersions("1.0", "0.99") > 0)
        assertTrue(UpdateChecker.compareVersions("1.0", "0.9999") > 0)
        assertTrue(UpdateChecker.compareVersions("0.9999", "1.0") < 0)
    }

    @Test
    fun `equal versions compare equal, including a trailing zero component`() {
        assertEquals(0, UpdateChecker.compareVersions("0.120", "0.120"))
        // The running APK reports "1.4"; a manifest saying "1.4.0" is the same
        // build and must not be re-offered as an update.
        assertEquals(0, UpdateChecker.compareVersions("1.4.0", "1.4"))
    }

    @Test
    fun `unparseable components are dropped rather than treated as newer`() {
        // Guards the "nothing to do" branch: garbage in the manifest must not
        // read as a version ahead of what is installed.
        assertEquals(0, UpdateChecker.compareVersions("0.120-beta", "0.120"))
    }
}

/**
 * The About dialog renders this percentage directly, including for a resumed
 * download whose byte count starts partway through.
 */
class DownloadProgressTest {

    @Test
    fun `percent is a whole-number share of the total`() {
        assertEquals(0, DownloadState.Running("0.121", 0, 200).percent)
        assertEquals(50, DownloadState.Running("0.121", 100, 200).percent)
        assertEquals(100, DownloadState.Running("0.121", 200, 200).percent)
    }

    @Test
    fun `an unknown or overshooting total never renders out of range`() {
        // A zero total would divide by zero; an overshoot would push the
        // progress bar past its track.
        assertEquals(0, DownloadState.Running("0.121", 10, 0).percent)
        assertEquals(100, DownloadState.Running("0.121", 300, 200).percent)
    }
}
