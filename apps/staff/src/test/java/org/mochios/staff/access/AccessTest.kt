// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.staff.access

import org.junit.Assert.assertEquals
import org.junit.Test
import org.mochios.android.api.MochiError
import org.mochios.staff.model.Me

/**
 * What the start-up access check does to the Staff launcher, the only thing
 * that switches it off: shown for a staff role, hidden for none or a refusal,
 * and left as it is when the check could not tell.
 */
class AccessTest {

    @Test
    fun `a staff role shows the launcher and no role hides it`() {
        assertEquals(true, access(Me(id = "1abc", role = "admin")))
        assertEquals(true, access(Me(id = "1abc", role = "moderator")))
        assertEquals(false, access(Me(id = "1abc", role = "")))
        assertEquals(false, access(Me()))
    }

    @Test
    fun `a refusal hides the launcher`() {
        assertEquals(false, access(MochiError.ForbiddenError()))
    }

    @Test
    fun `a token not taken, a network failure or a server error leaves it as it is`() {
        assertEquals(null, access(MochiError.AuthError()))
        assertEquals(null, access(MochiError.NetworkError()))
        assertEquals(null, access(MochiError.ServerError(502)))
        assertEquals(null, access(IllegalStateException("unexpected")))
    }
}
