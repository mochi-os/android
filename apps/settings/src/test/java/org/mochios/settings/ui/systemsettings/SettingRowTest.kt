// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.settings.ui.systemsettings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mochios.settings.api.SystemSetting
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** A setting's row draws the value asked for while it is pending, and the stored one otherwise. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SettingRowTest {

    @get:Rule
    val rule = createComposeRule()

    private val signup = SystemSetting(name = "signup_enabled", value = "true", default = "true", pattern = "^(true|false)$")
    private val firebase = SystemSetting(name = "fcm.firebase_config", value = "", default = "", pattern = "text")
    private val switch = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Switch)

    @Test
    fun `a switch shows the value asked for, and the stored one once it is dropped`() {
        var pending by mutableStateOf<String?>("false")
        rule.setContent { SettingRow(setting = signup, pending = pending, isSaving = false, themes = emptyList(), onSave = {}) }
        rule.onNode(switch).assertIsOff()
        pending = null
        rule.waitForIdle()
        rule.onNode(switch).assertIsOn()
    }

    @Test
    fun `a method state shows the value asked for, and the stored one once it is dropped`() {
        val passkey = SystemSetting(name = "auth_passkey", value = "allowed", default = "allowed", pattern = "^(disabled|allowed|required)$")
        var pending by mutableStateOf<String?>("required")
        val picked = mutableListOf<String>()
        rule.setContent { SettingRow(setting = passkey, pending = pending, isSaving = false, themes = emptyList(), onSave = { picked += it }) }
        // The filled button is the current value and takes no tap.
        rule.onNodeWithText("Allowed").performClick()
        assertEquals(listOf("allowed"), picked)
        pending = null
        rule.waitForIdle()
        rule.onNodeWithText("Required").performClick()
        assertEquals(listOf("allowed", "required"), picked)
    }

    @Test
    fun `an unset file setting offers a file, a set one its replacement and clearing`() {
        var setting by mutableStateOf(firebase)
        var chosen = 0
        val saves = mutableListOf<String>()
        rule.setContent {
            SettingRow(setting = setting, pending = null, isSaving = false, themes = emptyList(), onSave = { saves += it }, onChooseFile = { chosen++ })
        }
        rule.onNodeWithText("Choose file").performClick()
        assertEquals(1, chosen)
        setting = firebase.copy(value = """{"project_id": "p1"}""")
        rule.waitForIdle()
        rule.onNodeWithText("Configured").assertExists()
        rule.onNodeWithText("Replace").performClick()
        assertEquals(2, chosen)
        rule.onNodeWithText("Clear").performClick()
        assertEquals(listOf(""), saves)
    }
}
