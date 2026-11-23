// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.util

import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mochios.android.i18n.AppContext
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** One table names the services, for the sign-in buttons and every other screen alike. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ProviderNameTest {

    @Before
    fun begin() {
        AppContext.set(RuntimeEnvironment.getApplication())
    }

    @Test
    fun `a listed key is named as its brand writes itself, whatever its case`() {
        assertEquals("GitHub", providerName("github"))
        assertEquals("GitHub", providerName("GitHub"))
        assertEquals("GitHub", providerName("Github"))
        assertEquals("Microsoft", providerName("microsoft"))
        assertEquals("X", providerName("x"))
    }

    @Test
    fun `a key not listed is shown capitalised`() {
        assertEquals("Gitlab", providerName("gitlab"))
    }

    /**
     * Every account, network and push-transport type the server sends has its
     * name: the settings screens used to print "fediverse", "unifiedpush" and
     * "fcm" as they are stored.
     */
    @Test
    fun `every type the server sends is named, not printed`() {
        val names = mapOf(
            "apple" to "Apple", "bluesky" to "Bluesky", "claude" to "Claude", "facebook" to "Facebook",
            "fediverse" to "ActivityPub", "github" to "GitHub", "google" to "Google", "lemmy" to "Lemmy",
            "mastodon" to "Mastodon", "matrix" to "Matrix", "microsoft" to "Microsoft", "ntfy" to "ntfy",
            "openai" to "OpenAI", "pushbullet" to "Pushbullet", "x" to "X", "xmpp" to "XMPP",
            "browser" to "Browser notifications", "caldav" to "Calendar server", "email" to "Email",
            "fcm" to "Android push", "mcp" to "MCP server", "unifiedpush" to "Push notification",
            "url" to "External URL", "web" to "Mochi web",
        )
        for ((type, name) in names) assertEquals(type, name, providerName(type))
    }

    @Test
    @Config(qualifiers = "de")
    fun `a type named in words is named in the reader's language`() {
        AppContext.set(RuntimeEnvironment.getApplication())
        assertEquals("Browser-Benachrichtigungen", providerName("browser"))
        assertEquals("Mochi Web", providerName("web"))
        assertEquals("the brands stay as they are", "ActivityPub", providerName("fediverse"))
    }
}
