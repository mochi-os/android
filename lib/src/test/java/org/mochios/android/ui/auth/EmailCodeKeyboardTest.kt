// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.ui.auth

import android.text.InputType
import android.view.inputmethod.EditorInfo
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.platform.InterceptPlatformTextInput
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mochios.android.auth.BeginResult
import org.mochios.android.i18n.AppContext
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * The emailed sign-in code is mixed-case letters and digits: a keyboard
 * correcting it as a word would change what was typed.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class EmailCodeKeyboardTest {

    @get:Rule
    val rule = createComposeRule()

    @Before
    fun begin() {
        AppContext.set(RuntimeEnvironment.getApplication())
    }

    /** The input type the keyboard is asked for when the code's field on [state]'s screen is tapped. */
    @OptIn(ExperimentalComposeUiApi::class)
    private fun input(state: AuthUiState): Int {
        var inputType = 0
        rule.setContent {
            InterceptPlatformTextInput(
                interceptor = { request, next ->
                    val info = EditorInfo()
                    request.createInputConnection(info)
                    inputType = info.inputType
                    next.startInputMethod(request)
                },
            ) {
                AccountMethodsScreen(
                    uiState = state,
                    onRequestCode = {},
                    onUpdateCode = {},
                    onVerifyCode = {},
                    onUpdateTotpCode = {},
                    onVerifyTotp = {},
                    onBeginPasskey = {},
                    onShowRecovery = {},
                    onBack = {},
                    onUpdateMfaEmailCode = {},
                    onUpdateMfaTotpCode = {},
                    onCompleteMfa = {},
                )
            }
        }
        rule.waitForIdle()
        rule.onNode(hasSetTextAction()).performClick()
        rule.waitForIdle()
        return inputType
    }

    @Test
    fun `the sign-in code is not corrected as it is typed`() {
        val state = AuthUiState(email = "ann@example.com", beginResult = BeginResult(listOf("email"), false, false), codeSent = true)
        val type = input(state)
        assertEquals(InputType.TYPE_CLASS_TEXT, type and InputType.TYPE_MASK_CLASS)
        assertEquals(0, type and InputType.TYPE_TEXT_FLAG_AUTO_CORRECT)
    }

    @Test
    fun `the second factor's emailed code is not corrected as it is typed`() {
        val state = AuthUiState(email = "ann@example.com", mfaPartial = "partial", mfaRemaining = listOf("email"))
        val type = input(state)
        assertEquals(InputType.TYPE_CLASS_TEXT, type and InputType.TYPE_MASK_CLASS)
        assertEquals(0, type and InputType.TYPE_TEXT_FLAG_AUTO_CORRECT)
    }
}
