// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Every app's access list marks the resource owner's rule with `owner`
 * (feeds, forums, projects, repositories, crm and wikis all write the same
 * key). The flag decides which row loses its level picker, so a mismatched
 * wire key silently offers to demote the owner.
 */
class AccessRuleTest {
    private val gson = org.mochios.android.api.ApiClient.provideGson()

    @Test
    fun `the owner flag is read from the shared owner key`() {
        val rule = gson.fromJson("""{"subject":"abc","operation":"*","grant":1,"owner":true}""", AccessRule::class.java)
        assertTrue(rule.isOwner)
    }

    @Test
    fun `a rule without the flag is not the owner`() {
        val rule = gson.fromJson("""{"subject":"abc","operation":"view","grant":1}""", AccessRule::class.java)
        assertFalse(rule.isOwner)
    }
}
