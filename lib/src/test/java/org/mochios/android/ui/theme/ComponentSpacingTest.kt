// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.ui.theme

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mochios.android.i18n.Density
import org.mochios.android.i18n.UserPreferences
import org.mochios.android.notifications.MochiNotification
import org.mochios.android.ui.components.AccessLabels
import org.mochios.android.ui.components.AccessRulesSection
import org.mochios.android.ui.components.AccessSubjectRule
import org.mochios.android.ui.components.ChoiceFilter
import org.mochios.android.ui.components.ComposeBar
import org.mochios.android.ui.components.DrawerItem
import org.mochios.android.ui.components.ErrorState
import org.mochios.android.ui.components.FilterBar
import org.mochios.android.ui.components.FilterChipRow
import org.mochios.android.ui.components.InlineErrorState
import org.mochios.android.ui.components.MochiDropdownField
import org.mochios.android.ui.components.MochiListDrawer
import org.mochios.android.ui.components.NotificationCard
import org.mochios.android.ui.components.QuoteCard
import org.mochios.android.ui.components.board.CollapsedBoardColumn
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The shared components beyond the primitives set their padding, gaps and
 * control heights by the Density preference, at compact and spacious against
 * comfortable, and their text keeps its size.
 */
@RunWith(RobolectricTestRunner::class)
// A dense screen, so rounding to whole pixels stays well inside the tolerance.
@Config(sdk = [35], qualifiers = "w400dp-h800dp-xxxhdpi")
// Real text measurement, so a line of text is as tall as it is on a phone.
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ComponentSpacingTest {
    @get:Rule
    val compose = createComposeRule()

    /** [content] in a theme at each density in turn, measured by [measure] at each, comfortable first. */
    private fun <T> atDensities(content: @Composable () -> Unit, measure: () -> T): Map<Density, T> {
        var density by mutableStateOf(Density.COMFORTABLE)
        compose.setContent {
            MochiTheme(preferences = UserPreferences(density = density)) { content() }
        }
        return listOf(Density.COMFORTABLE, Density.COMPACT, Density.SPACIOUS).associateWith { next ->
            compose.runOnIdle { density = next }
            compose.waitForIdle()
            measure()
        }
    }

    /** Each of [measured] is what [expected] gives for its density's spacing. */
    private fun assertFollows(measured: Map<Density, Float>, expected: (Spacing) -> Float) {
        measured.forEach { (density, value) ->
            assertEquals("at $density", expected(Spacing.of(density)), value, 0.4f)
        }
    }

    private fun bounds(tag: String): DpRect = compose.onNodeWithTag(tag).getBoundsInRoot()

    /** The text itself, not the clickable parent it merges into. */
    private fun text(value: String): DpRect = compose.onNodeWithText(value, useUnmergedTree = true).getBoundsInRoot()

    private val DpRect.tall: Float get() = (bottom - top).value

    private val DpRect.wide: Float get() = (right - left).value

    @Test
    fun `an error state's gap under its icon follows the preference`() {
        val gap = atDensities({
            Box(Modifier.size(300.dp, 600.dp).testTag("box")) { ErrorState(message = "Failed") }
        }) {
            // The column centres the 64dp icon, the gap and the message, so
            // the message's offset gives the gap away.
            val box = bounds("box")
            val message = text("Failed")
            2 * (message.top - box.top).value - box.tall - 64f + message.tall
        }
        assertFollows(gap) { spacing -> 16f * spacing.gap }
    }

    @Test
    fun `an inline error's padding follows the preference`() {
        val inset = atDensities({
            Box(Modifier.width(300.dp).testTag("box")) { InlineErrorState(message = "Failed") }
        }) {
            // The message is centred on its line with the 16dp icon.
            val message = text("Failed")
            (message.top - bounds("box").top).value - ((16f - message.tall) / 2).coerceAtLeast(0f)
        }
        assertFollows(inset) { spacing -> 8f * spacing.gap }
    }

    private val notification = MochiNotification(id = "one", topic = "chat", title = "Title", content = "Body")

    @Test
    fun `a notification card's padding follows the preference`() {
        val inset = atDensities({
            NotificationCard(notification = notification, onClick = {}, modifier = Modifier.testTag("card"))
        }) { (text("Title").top - bounds("card").top).value }
        assertFollows(inset) { spacing -> 12f * spacing.vertical }
    }

    @Test
    fun `text in a scaled card keeps its size`() {
        val title = atDensities({
            NotificationCard(notification = notification, onClick = {}, modifier = Modifier.testTag("card"))
        }) { text("Title") }
        val comfortable = title.getValue(Density.COMFORTABLE)
        for (density in listOf(Density.COMPACT, Density.SPACIOUS)) {
            assertEquals("as wide at $density", comfortable.wide, title.getValue(density).wide, 0.01f)
            assertEquals("as tall at $density", comfortable.tall, title.getValue(density).tall, 0.01f)
        }
    }

    private val filter = ChoiceFilter(
        label = "Kind",
        chipLabel = "Kind",
        anyLabel = "Any",
        options = listOf("first" to "First", "second" to "Second"),
        current = null,
        onSelect = {},
    )

    @Test
    fun `a filter bar's inset follows the preference`() {
        val inset = atDensities({
            FilterBar(filters = listOf(filter), modifier = Modifier.testTag("bar"))
        }) { (compose.onNodeWithText("Any").getBoundsInRoot().left - bounds("bar").left).value }
        assertFollows(inset) { spacing -> 16f * spacing.gap }
    }

    @Test
    fun `a chip row's gaps follow the preference`() {
        val gap = atDensities({
            FilterChipRow(options = listOf(1 to "One", 2 to "Two"), isSelected = { false }, onSelect = {})
        }) { (compose.onNodeWithText("Two").getBoundsInRoot().left - compose.onNodeWithText("One").getBoundsInRoot().right).value }
        assertFollows(gap) { spacing -> 8f * spacing.gap }
    }

    @Test
    fun `a compose bar's padding follows the preference`() {
        val inset = atDensities({
            ComposeBar(
                value = "",
                onValueChange = {},
                onSend = {},
                sendLabel = "Send",
                windowInsets = WindowInsets(0, 0, 0, 0),
                modifier = Modifier.testTag("bar"),
            )
        }) { (compose.onNode(hasSetTextAction()).getBoundsInRoot().left - bounds("bar").left).value }
        assertFollows(inset) { spacing -> 8f * spacing.gap }
    }

    @Test
    fun `a quote card's padding follows the preference`() {
        val inset = atDensities({
            QuoteCard(
                url = "",
                text = "Quoted",
                image = "",
                name = "",
                address = "",
                avatar = "",
                seed = "",
                modifier = Modifier.testTag("quote"),
            )
        }) { (text("Quoted").left - bounds("quote").left).value }
        assertFollows(inset) { spacing -> 12f * spacing.horizontal }
    }

    private val labels = AccessLabels(
        sectionTitle = "Access",
        empty = "None",
        dialogTitle = "Add",
        tabUsers = "Users",
        tabGroups = "Groups",
        tabOther = "Other",
        searchUsers = "Search",
        selectGroup = "Group",
        noGroups = "No groups",
        selectRule = "Rule",
        subjectAnyone = "Anyone",
        subjectAuthenticated = "Signed in",
        anyoneDesc = "Anyone at all",
        authenticatedDesc = "Anyone signed in",
        selected = { name -> name },
    )

    @Test
    fun `an access rule's row follows the preference`() {
        val inset = atDensities({
            Box(Modifier.testTag("section")) {
                AccessRulesSection(
                    rules = listOf(AccessSubjectRule(subject = "owner", level = "", name = "Ada", isOwner = true)),
                    levels = emptyList(),
                    defaultLevel = "",
                    levelLabel = { level -> level },
                    labels = labels,
                    users = emptyList(),
                    groups = emptyList(),
                    onSearchUsers = {},
                    onLoadGroups = {},
                    onSetAccess = { _, _, _ -> },
                    onRevoke = { _, _ -> },
                )
            }
        }) { (text("Ada").left - bounds("section").left).value }
        // The section's padding, the subject's 24dp icon, then the gap before the name.
        assertFollows(inset) { spacing -> 16f * spacing.horizontal + 24f + 12f * spacing.gap }
    }

    @Test
    fun `a drawer row follows the preference`() {
        val inset = atDensities({
            MochiListDrawer(
                drawerState = rememberDrawerState(DrawerValue.Open),
                items = listOf(DrawerItem(id = "one", title = "One", icon = Icons.Default.Inbox)),
                selectedId = null,
                onItemClick = {},
            ) {}
        }) { (text("One").left - compose.onNode(hasText("One") and hasClickAction()).getBoundsInRoot().left).value }
        // The row's padding, the item's 24dp icon, then the gap before the title.
        assertFollows(inset) { spacing -> 24f * spacing.gap + 24f + 12f * spacing.gap }
    }

    @Test
    fun `a collapsed board column's padding follows the preference`() {
        val offset = atDensities({
            Box(Modifier.size(width = 40.dp, height = 400.dp)) {
                CollapsedBoardColumn(name = "Column", count = "3", onExpand = {}, modifier = Modifier.testTag("column"))
            }
        }) { (text("3").top - bounds("column").top).value }
        // The count follows the top padding, the name, which keeps its length,
        // and the gap after it.
        val name = offset.getValue(Density.COMFORTABLE) - 20f
        assertFollows(offset) { spacing -> 12f * spacing.gap + name + 8f * spacing.gap }
    }

    @Test
    fun `a dropdown field's height follows the preference`() {
        val padding = atDensities({
            MochiDropdownField(
                value = "Value",
                expanded = false,
                onExpandedChange = {},
                label = "Label",
                modifier = Modifier.testTag("field"),
            ) {}
        }) { bounds("field").tall - text("Label").tall - text("Value").tall }
        // As tall as its two lines and the padding above and below them.
        assertFollows(padding) { spacing -> 24f * spacing.gap }
    }
}
