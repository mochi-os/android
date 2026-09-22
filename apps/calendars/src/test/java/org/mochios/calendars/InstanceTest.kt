// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.
package org.mochios.calendars

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mochios.calendars.model.Instance

/**
 * A tap opens the editor for what can be edited and the summary sheet for
 * what cannot: a subscription's occurrence and a birthday, which is derived
 * from a contact and has no event of its own.
 */
class InstanceTest {
    @Test
    fun anOwnEventIsEditable() {
        assertTrue(Instance(event = "01a0c3f2", readonly = false).editable)
    }

    @Test
    fun aSubscriptionOccurrenceIsNot() {
        assertFalse(Instance(event = "01a0c3f2", readonly = true).editable)
    }

    @Test
    fun aBirthdayIsNotEvenWhenNotMarkedReadOnly() {
        assertFalse(Instance(event = Instance.BIRTHDAY + "contact", readonly = false).editable)
    }
}
