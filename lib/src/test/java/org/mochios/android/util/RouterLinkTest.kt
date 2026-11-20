// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.util

import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * What the shell's link router does with a link and a launch before a module
 * sees them: each path part decoded once, a launch handed on without the
 * extras another app put on it, and a URI named in a log without its query.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class RouterLinkTest {

    // The modules' builders escape what they put in a route, so a part left
    // escaped here was escaped twice and named another item.
    @Test
    fun `each part of a link is decoded once`() {
        assertEquals(listOf("forums", "a/b c", "50%"), linkParts("forums/a%2Fb%20c/50%25"))
        assertEquals("a plus is a plus in a path", listOf("wikis", "C++"), linkParts("wikis/C++"))
        assertEquals(listOf("chat", "new"), linkParts("chat/new"))
    }

    @Test
    fun `only a plain part reaches a builder that escapes nothing`() {
        assertEquals("g1Ab-9_.~", plain("g1Ab-9_.~"))
        assertNull(plain("a/b"))
        assertNull(plain("x?y=z"))
        assertNull(plain(""))
    }

    @Test
    fun `a log names a URI without its query`() {
        assertEquals("oauth-return", uriName(Uri.parse("mochi:oauth-return?code=secret&nonce=n")))
        assertEquals("notification", uriName(Uri.parse("mochi:notification?link=%2Ffeeds%2Fx&nonce=n")))
        assertEquals("market", uriName(Uri.parse("mochi://market/stripe/oauth?code=c&state=s")))
        assertEquals("entity", uriName(Uri.parse("mochi:/1abc/page")))
    }

    // The forwarding activity is exported: an extra another app sets must not
    // reach the app's own class as though the app had set it.
    @Test
    fun `a forwarded launch keeps its data and flags and drops every extra`() {
        val received = Intent(Intent.ACTION_VIEW, Uri.parse("mochi:notification?link=%2Ffeeds&nonce=n"))
            .addCategory(Intent.CATEGORY_BROWSABLE)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            .putExtra("org.mochios.android.extra.REAUTH_ACCOUNT", "someone")
            .putExtra("app", "staff")
        val target = ComponentName("org.mochios.mochi", "org.mochios.mochi.MochiFeedsLauncher")
        val forwarded = forwardedIntent(received, target)
        assertEquals(Intent.ACTION_VIEW, forwarded.action)
        assertEquals(received.data, forwarded.data)
        assertTrue(forwarded.hasCategory(Intent.CATEGORY_BROWSABLE))
        assertEquals(Intent.FLAG_ACTIVITY_CLEAR_TOP, forwarded.flags and Intent.FLAG_ACTIVITY_CLEAR_TOP)
        assertEquals(target, forwarded.component)
        assertNull("THE POINT: no extra crosses", forwarded.extras)
    }
}
