// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.settings.ui.systemsettings

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mochios.settings.api.SystemSetting

/**
 * The trap this pins: core blanks a secret's value before sending it, so a
 * configured OAuth client secret arrives with `value == ""` — and its default
 * is `""` too. Every value-based test therefore reports "unset" no matter how
 * the server is configured, which is what hid configured secrets from the
 * administrator and removed the affordance to clear them.
 */
class StoredValueTest {

    /** What core actually sends for a configured secret (settings.go). */
    private val configuredSecret = SystemSetting(
        name = "oauth_google_secret",
        value = "",
        default = "",
        pattern = "line",
        secret = true,
        set = true,
    )

    private val unsetSecret = configuredSecret.copy(set = false)

    @Test
    fun `a configured secret is recognised despite arriving blank`() {
        assertTrue(hasStoredValue(configuredSecret))
    }

    @Test
    fun `an unset secret is not`() {
        assertFalse(hasStoredValue(unsetSecret))
    }

    /**
     * The specific mistake: the two are indistinguishable by value, so anything
     * keyed on value or on `value == default` cannot tell them apart.
     */
    @Test
    fun `the two are indistinguishable by value alone`() {
        assertTrue(configuredSecret.value == unsetSecret.value)
        assertTrue(configuredSecret.value == configuredSecret.default)
        assertTrue(hasStoredValue(configuredSecret) != hasStoredValue(unsetSecret))
    }

    @Test
    fun `an ordinary setting is still judged by its value`() {
        val plain = SystemSetting(name = "hostname", value = "mochi.test", pattern = "line")
        assertTrue(hasStoredValue(plain))
        assertFalse(hasStoredValue(plain.copy(value = "")))
    }

    /**
     * `set` is computed for every setting, not just secrets, but for an
     * ordinary one the value is authoritative — a server that sent them out of
     * step should not blank a value the user can see.
     */
    @Test
    fun `an ordinary setting ignores set when it contradicts the value`() {
        val plain = SystemSetting(name = "hostname", value = "mochi.test", set = false)
        assertTrue(hasStoredValue(plain))
    }
}
