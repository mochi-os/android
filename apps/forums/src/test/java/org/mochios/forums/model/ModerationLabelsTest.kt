// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.forums.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The moderation screen used to render the server's raw enums. These pin that
 * every value the server actually sends has a label of its own, and that an
 * unknown one degrades to a phrase rather than to `resolve_report`.
 */
class ModerationLabelsTest {

    // log_moderation in forums.star records exactly these.
    private val actions = listOf(
        "remove", "restore", "approve", "lock", "unlock",
        "pin", "unpin", "restrict", "unrestrict", "resolve_report",
    )

    @Test
    fun `every recorded action has a label`() {
        for (action in actions) {
            assertNotNull("no label for $action", moderationAction(action))
        }
    }

    @Test
    fun `each action has its own label`() {
        val labels = actions.map { moderationAction(it) }
        assertEquals(actions.size, labels.toSet().size)
    }

    @Test
    fun `every target and reason the server sends has a label`() {
        for (type in listOf("post", "comment", "user")) {
            assertNotNull("no label for $type", moderationTarget(type))
        }
        val reasons = listOf(
            "spam", "harassment", "hate", "violence",
            "misinformation", "offtopic", "other",
        )
        for (reason in reasons) {
            assertNotNull("no label for $reason", moderationReason(reason))
        }
        assertEquals(reasons.size, reasons.map { moderationReason(it) }.toSet().size)
    }

    @Test
    fun `an unknown value has no label and is humanised instead`() {
        assertNull(moderationAction("quarantine"))
        assertNull(moderationTarget("attachment"))
        assertNull(moderationReason("nonsense"))
        assertEquals("resolve report", humanise("resolve_report"))
        assertEquals("quarantine", humanise("quarantine"))
    }
}
