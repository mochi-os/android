// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.settings.ui.accounts

import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mochios.android.i18n.AppContext
import org.mochios.settings.R
import org.mochios.settings.api.ConnectedAccount
import org.mochios.settings.api.Provider
import org.mochios.settings.api.ProviderField
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.mochios.android.R as MochiR

/**
 * The Add account form as the server describes it, and how accounts are named.
 * Plain @Config: a MochiAlertDialog with text fields never idles under display
 * qualifiers.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AddAccountTest {

    @get:Rule
    val rule = createComposeRule()

    private val context = RuntimeEnvironment.getApplication()

    // The labels and placeholders arrive resolved in the user's language, as
    // core's api_account_providers sends them.
    private val openai = Provider(
        type = "openai",
        capabilities = listOf("ai"),
        flow = "form",
        fields = listOf(
            ProviderField(name = "api_key", label = "Clé d'API", type = "password", required = true, placeholder = "sk-"),
            ProviderField(name = "model", label = "Modèle", type = "text", placeholder = "par défaut"),
            ProviderField(name = "label", label = "Nom", type = "text"),
        ),
    )

    @Before
    fun begin() {
        AppContext.set(context)
    }

    /** Every field used to be labelled with its wire name: api_key, model, label. */
    @Test
    fun `each field is labelled as the server labels it, with its example value`() {
        rule.setContent { AddAccountDialog(providers = listOf(openai), onDismiss = {}, onSave = { _, _ -> }) }
        rule.onNodeWithText("Nom").assertExists()
        // A labelled field shows its example once it has the focus.
        rule.onNodeWithText("Clé d'API").performClick()
        rule.waitForIdle()
        rule.onNodeWithText("sk-").assertExists()
        rule.onNodeWithText("api_key").assertDoesNotExist()
        rule.onNodeWithText("OpenAI").assertExists()
    }

    /** As the web form starts it: an optional text field with an example holds it. */
    @Test
    fun `an AI account's model starts at its default and is sent with the key`() {
        var saved: Pair<String, Map<String, String>>? = null
        rule.setContent { AddAccountDialog(providers = listOf(openai), onDismiss = {}, onSave = { type, fields -> saved = type to fields }) }
        rule.onNode(hasSetTextAction() and hasText("par défaut")).assertExists()
        rule.onNode(hasSetTextAction() and hasText("Clé d'API")).performTextInput("sk-test")
        rule.onNodeWithText(context.getString(MochiR.string.common_save)).performClick()
        rule.waitForIdle()
        assertEquals("openai" to mapOf("api_key" to "sk-test", "model" to "par défaut"), saved)
    }

    @Test
    fun `only optional text fields with an example are filled`() {
        assertEquals(mapOf("model" to "par défaut"), prefilled(openai))
        assertEquals(emptyMap<String, String>(), prefilled(null))
    }

    /** A network's account used to read "fediverse", its stored type. */
    @Test
    fun `an account on another network is named by its network, and its actions are named`() {
        rule.setContent {
            AccountRow(
                account = ConnectedAccount(id = "f", type = "fediverse", hosted = true),
                providers = emptyList(),
                onVerify = {},
                onSettings = {},
                onTest = {},
                onRemove = {},
                onToggleNotify = {},
                onSetAiDefault = {},
            )
        }
        rule.onAllNodes(hasText("ActivityPub")).fetchSemanticsNodes().let { assertEquals(2, it.size) }
        rule.onNodeWithText("fediverse").assertDoesNotExist()
        rule.onNodeWithContentDescription(context.getString(R.string.networks_account_actions)).assertExists()
    }
}
