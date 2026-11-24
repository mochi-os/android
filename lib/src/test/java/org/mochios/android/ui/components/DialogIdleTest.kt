// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalCursorBlinkEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.TimeUnit

/**
 * Why a dialog holding a text field never goes idle under Robolectric at a
 * phone's width ("Compose did not get idle after 60 s", then out of memory),
 * and settles at the default size.
 *
 * A floating dialog's window wraps its content, so each traversal measures
 * it first at the platform's preferred dialog width, 320 dp (a 272 dp
 * content area inside the dialog's padding), and then again at the width the
 * window is given - in Robolectric always the whole display. A text field
 * measured at a new width writes state as it lays out (its text layout, and
 * the coordinates its context menu keeps), which asks for another traversal,
 * which measures it at the two widths again. It is a measure loop: the
 * content composes once, the cursor's blink and focus play no part, and
 * Material's own AlertDialog and TextField do the same. A display 320 dp
 * wide, Robolectric's default, measures once, which is why the dialog tests
 * use it; so does a dialog that sizes its own window.
 *
 * It is Robolectric's alone. On an Android 16 emulator 411 dp wide, and again
 * at 360 dp, the access rule dialog (a MochiAlertDialog holding a
 * MochiTextField, at the default size) rendered no frame in 5 s left alone
 * with the field unfocused, and 10 with it focused: the cursor's blink. A
 * dialog holding only text rendered none either. So dialogs keep the platform's
 * width, and these tests the default display.
 *
 * Every test bounds the wait for idle to a few seconds, so a dialog that
 * does not settle fails fast rather than spinning until the heap is gone.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class DialogIdleTest {

    @get:Rule
    val rule = createComposeRule()

    private class Counts {
        var compositions = 0
        val widths = mutableSetOf<Int>()
    }

    // Espresso's idling policy bounds Robolectric's wait for idle. Espresso is
    // on the test runtime through the Compose test rule but not on its
    // compile classpath, so the policy is set by name.
    private fun idleTimeout(seconds: Long) {
        Class.forName("androidx.test.espresso.IdlingPolicies")
            .getMethod("setMasterPolicyTimeout", Long::class.javaPrimitiveType, TimeUnit::class.java)
            .invoke(null, seconds, TimeUnit.SECONDS)
    }

    @Before
    fun bound() = idleTimeout(3)

    @After
    fun unbound() = idleTimeout(60)

    @Composable
    private fun Body(counts: Counts, material: Boolean, field: Boolean) {
        SideEffect { counts.compositions++ }
        Box(
            Modifier.layout { measurable, constraints ->
                counts.widths += constraints.maxWidth
                val placeable = measurable.measure(constraints)
                layout(placeable.width, placeable.height) { placeable.place(0, 0) }
            },
        ) {
            if (field) {
                var value by remember { mutableStateOf("") }
                // Neither focus nor the cursor's blink: the loop needs neither.
                val unfocused = Modifier.focusProperties { canFocus = false }
                if (material) {
                    TextField(value = value, onValueChange = { value = it }, modifier = unfocused)
                } else {
                    MochiTextField(value = value, onValueChange = { value = it }, modifier = unfocused)
                }
            } else {
                // As wide as the field, and writing nothing as it lays out.
                Box(Modifier.width(290.dp).height(56.dp))
            }
        }
    }

    /** Whether the dialog went idle, with the clock stopped. */
    private fun show(
        counts: Counts,
        material: Boolean = false,
        field: Boolean = true,
        platformWidth: Boolean = true,
    ): Boolean {
        rule.mainClock.autoAdvance = false
        val properties = DialogProperties(usePlatformDefaultWidth = platformWidth)
        return runCatching {
            rule.setContent {
                CompositionLocalProvider(LocalCursorBlinkEnabled provides false) {
                    if (material) {
                        AlertDialog(
                            onDismissRequest = {},
                            title = { Text("Name") },
                            confirmButton = { TextButton(onClick = {}) { Text("Save") } },
                            properties = properties,
                            text = { Body(counts, material = true, field = field) },
                        )
                    } else {
                        MochiAlertDialog(
                            onDismissRequest = {},
                            title = "Name",
                            confirmText = "Save",
                            properties = properties,
                            content = { Body(counts, material = false, field = field) },
                        )
                    }
                }
            }
        }.isSuccess
    }

    @Test
    @Config(qualifiers = "w411dp-h900dp")
    fun `a field in a dialog at a phone's width is measured at two widths and never settles`() {
        val counts = Counts()
        assertFalse(show(counts))
        assertEquals(1, counts.compositions)
        assertEquals(setOf(272, 363), counts.widths)
    }

    @Test
    @Config(qualifiers = "w411dp-h900dp")
    fun `material's own dialog and field do the same`() {
        val counts = Counts()
        assertFalse(show(counts, material = true))
        assertEquals(1, counts.compositions)
    }

    @Test
    @Config(qualifiers = "w411dp-h900dp")
    fun `content as wide that writes nothing as it lays out settles`() {
        val counts = Counts()
        assertTrue(show(counts, field = false))
        assertEquals(setOf(272, 363), counts.widths)
    }

    @Test
    fun `the default display, 320 dp wide, measures once and settles`() {
        val counts = Counts()
        assertTrue(show(counts))
        assertEquals(setOf(272), counts.widths)
    }

    @Test
    @Config(qualifiers = "w411dp-h900dp")
    fun `a dialog that sizes its own window measures once and settles`() {
        val counts = Counts()
        assertTrue(show(counts, platformWidth = false))
        assertEquals(setOf(363), counts.widths)
    }
}
