// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.chat.ui.encryption

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mochios.chat.model.EncryptionOverview
import org.mochios.chat.model.EncryptionPerson
import org.mochios.chat.model.EncryptionStatus
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The Encryption section at a phone's width, with text measured as the device
 * measures it: a key beside its badge, and a name beside its Compare button,
 * are wider together than the screen, and neither may squeeze the other.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w360dp-h800dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class EncryptionLayoutTest {

    @get:Rule
    val rule = createComposeRule()

    @Test
    fun `on a phone the device's key sits under its label, its badge one line, and no name is cut short`() {
        rule.setContent {
            Column(Modifier.width(328.dp)) {
                EncryptionContent(
                    state = EncryptionUiState(
                        overview = EncryptionOverview(
                            status = EncryptionStatus(
                                network = "matrix", address = "@me:m.example", verified = true, signing = true, recovery = true,
                                fingerprint = "q2Lx7mZp4VbN8cRt1Hs6Kd9Wf3Gj5Ya0Ue2Io7Pl4Mk",
                            ),
                            people = listOf(EncryptionPerson("@alistair:m.example", "Alistair Cunningham")),
                        ),
                    ),
                    onRetry = {},
                    onToggle = {},
                    onTrust = { _, _, _ -> },
                    onVerify = {},
                    onSetup = {},
                    onRecover = {},
                )
            }
        }
        val label = rule.onNodeWithText("This device").getUnclippedBoundsInRoot()
        val key = rule.onNodeWithText("q2Lx7mZp", substring = true).getUnclippedBoundsInRoot()
        val badge = rule.onNodeWithText("Verified").getUnclippedBoundsInRoot()
        assertTrue("the badge is ${badge.height} tall", badge.height < 40.dp)
        assertTrue("the key starts ${key.top - label.bottom} under its label", key.top - label.bottom < 12.dp)
        for (name in listOf("Your other devices", "Alistair Cunningham")) {
            val layouts = mutableListOf<TextLayoutResult>()
            rule.onNodeWithText(name).fetchSemanticsNode().config[SemanticsActions.GetTextLayoutResult].action?.invoke(layouts)
            assertFalse("$name is cut short", layouts.first().hasVisualOverflow)
        }
    }
}
