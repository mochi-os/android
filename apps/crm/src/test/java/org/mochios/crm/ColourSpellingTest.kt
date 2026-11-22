// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.crm

import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * "Colour" as the web spells it, and "Color" in US English alone, as the
 * web's en-us does: an English reader outside the US is not handed the US
 * catalogue as their language's nearest.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ColourSpellingTest {

    private fun words() = RuntimeEnvironment.getApplication().let { context ->
        listOf(context.getString(R.string.crm_option_color), context.getString(R.string.crm_views_border_field))
    }

    @Test
    @Config(qualifiers = "en")
    fun `the base English spells colour`() {
        assertEquals(listOf("Colour", "Border colour field"), words())
    }

    @Test
    @Config(qualifiers = "en-rCA")
    fun `Canadian English spells colour`() {
        assertEquals(listOf("Colour", "Border colour field"), words())
    }

    @Test
    @Config(qualifiers = "en-rGB")
    fun `British English spells colour`() {
        assertEquals(listOf("Colour", "Border colour field"), words())
    }

    @Test
    @Config(qualifiers = "en-rUS")
    fun `US English spells color`() {
        assertEquals(listOf("Color", "Border color field"), words())
    }
}
