// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.launcher

import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import androidx.core.net.toUri
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowShortcutManager
import org.robolectric.util.ReflectionHelpers

/**
 * A feed, a forum or a chat on the phone's home screen: a pinned shortcut
 * that opens the item in its app, kept in step with the items the user has.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ShortcutsTest {

    private val context = RuntimeEnvironment.getApplication()
    private val manager = context.getSystemService(ShortcutManager::class.java)

    @Before
    fun setUp() {
        ShadowShortcutManager.reset()
        shadowOf(manager).setIsRequestPinShortcutSupported(true)
    }

    private fun pinned(): List<ShortcutInfo> = manager.pinnedShortcuts

    /** The ids the launcher shows greyed out. */
    private fun disabled(): Set<String> =
        ReflectionHelpers.getStaticField<Map<String, ShortcutInfo>>(
            ShadowShortcutManager::class.java,
            "disabledPinnedShortcuts",
        ).keys

    @Test
    fun `an item opens as its mochi address in its own app`() {
        assertTrue(Shortcuts.pinItem(context, "feeds", "abc123", "Garden"))
        val shortcut = pinned().single()
        val intent = shortcut.intent!!
        assertEquals(Intent.ACTION_VIEW, intent.action)
        assertEquals("mochi:/abc123".toUri(), intent.data)
        assertEquals("feeds", intent.getStringExtra("app"))
        assertEquals(context.packageName, intent.`package`)
        assertTrue(intent.flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0)
        assertEquals("Garden", shortcut.shortLabel.toString())
    }

    @Test
    fun `nothing is asked of a launcher that cannot pin`() {
        shadowOf(manager).setIsRequestPinShortcutSupported(false)
        assertFalse(Shortcuts.pinItem(context, "feeds", "abc123", "Garden"))
        assertTrue(pinned().isEmpty())
    }

    @Test
    fun `an item gone from the list is turned off, and one still there is not`() {
        Shortcuts.pinItem(context, "forums", "kept", "Kept")
        Shortcuts.pinItem(context, "forums", "left", "Left")
        Shortcuts.reconcile(context, "forums", mapOf("kept" to "Kept"))
        assertEquals(setOf("forums_left"), disabled())
    }

    @Test
    fun `an item back in the list is turned on again, and can be pinned again`() {
        Shortcuts.pinItem(context, "forums", "back", "Back")
        Shortcuts.reconcile(context, "forums", emptyMap())
        assertEquals(setOf("forums_back"), disabled())
        Shortcuts.reconcile(context, "forums", mapOf("back" to "Back"))
        assertTrue(disabled().isEmpty())
        Shortcuts.reconcile(context, "forums", emptyMap())
        // A request for a shortcut that is off would be refused.
        assertTrue(Shortcuts.pinItem(context, "forums", "back", "Back"))
        assertTrue(disabled().isEmpty())
    }

    @Test
    fun `a renamed item's shortcut takes its new name`() {
        Shortcuts.pinItem(context, "wikis", "notes", "Notes")
        Shortcuts.reconcile(context, "wikis", mapOf("notes" to "House notes"))
        assertEquals("House notes", pinned().single().shortLabel.toString())
    }

    @Test
    fun `another app's shortcuts, and the app's own icon, are left alone`() {
        Shortcuts.pinItem(context, "chat", "x1", "Chat")
        Shortcuts.pinItem(context, "feeds", "x1", "Feed")
        // The home grid's pin of the whole app opens it as its launcher icon does.
        val app = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).putExtra("app", "feeds")
        manager.requestPinShortcut(ShortcutInfo.Builder(context, "feeds").setShortLabel("Feeds").setIntent(app).build(), null)
        Shortcuts.reconcile(context, "feeds", emptyMap())
        assertEquals(setOf("feeds_x1"), disabled())
    }

    @Test
    fun `an item further into the app is matched by its whole path`() {
        Shortcuts.pinItem(context, "calendars", "calendar/c1", "Work")
        Shortcuts.pinItem(context, "calendars", "calendar/c2", "Home")
        Shortcuts.reconcile(context, "calendars", mapOf("calendar/c1" to "Work"))
        assertEquals(setOf("calendars_calendar/c2"), disabled())
        assertEquals("mochi:/calendar/c1".toUri(), pinned().first { it.id == "calendars_calendar/c1" }.intent!!.data)
    }

    @Test
    fun `a shortcut pinned before items had a shared id is matched by where it goes`() {
        // Projects pinned as project_<id>; the address and the app say which item.
        val intent = Intent(Intent.ACTION_VIEW, "mochi:/p1".toUri())
            .setPackage(context.packageName)
            .putExtra("app", "projects")
        manager.requestPinShortcut(
            ShortcutInfo.Builder(context, "project_p1").setShortLabel("Plans").setIntent(intent).build(),
            null,
        )
        Shortcuts.reconcile(context, "projects", emptyMap())
        assertEquals(setOf("project_p1"), disabled())
    }
}
