// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.ui.components

import android.os.Looper
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mochios.android.auth.SessionManager
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** An app's device actions, held in memory. */
private class Tokens(var devices: List<Device> = emptyList()) : DeviceTokens {
    val deleted = mutableListOf<String>()

    override suspend fun list() = DeviceListing(devices, "someone@example.test")

    override suspend fun create(name: String): DeviceCreated {
        devices = devices + Device(hash = "h-$name", name = name, created = 1_700_000_000)
        return DeviceCreated("mochi-secret", "someone@example.test")
    }

    override suspend fun delete(hash: String) {
        deleted += hash
        devices = devices.filterNot { it.hash == hash }
    }
}

/**
 * The connected-devices screen both Calendars and People show. It gives a
 * device the app's address and the username, before a device is added and
 * again beside its new password, and no bare server beside them: the address
 * serves where a client asks for a server, and a second field read as the
 * address sent people astray. Each device reads when it was made and when it
 * was last used, or that it never has been.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w400dp-h800dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ConnectedDevicesTest {

    @get:Rule
    val rule = createComposeRule()

    private val context = RuntimeEnvironment.getApplication()
    private val session = SessionManager(context)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    /** The origin the session points at, which the address is built on. */
    private val origin: String = runBlocking { session.serverUrl.first().trimEnd('/') }

    @After
    fun end() = scope.cancel()

    private fun show(tokens: Tokens): ConnectedDevices {
        val devices = ConnectedDevices(scope, session, "calendars/caldav/", tokens)
        rule.setContent { ConnectedDevicesScreen(devices = devices, addressLabel = "Address", onBack = {}) }
        until { !devices.state.value.isLoading && devices.state.value.address.isNotEmpty() }
        return devices
    }

    /**
     * Waits for [done], running the main looper the session's answer resumes
     * on; a condition that reads the state alone never runs it.
     */
    private fun until(done: () -> Boolean) {
        rule.waitUntil(5_000) {
            shadowOf(Looper.getMainLooper()).idle()
            done()
        }
        rule.waitForIdle()
    }

    private fun count(text: String) = rule.onAllNodesWithText(text).fetchSemanticsNodes().size

    /** The texts holding [part], matching its case. */
    private fun within(part: String) = rule.onAllNodesWithText(part, substring = true).fetchSemanticsNodes().size

    /** The address and the username each once, and neither a "Server" label nor the bare origin. */
    private fun details() {
        assertEquals(1, count("$origin/calendars/caldav/"))
        assertEquals(1, count("someone@example.test"))
        assertEquals(0, count("Server"))
        assertEquals(0, count(origin))
    }

    @Test
    fun `before a device is added the screen shows the address and the username and no bare server`() {
        show(Tokens())
        details()
    }

    @Test
    fun `a new device's password comes with the address and the username and no bare server`() {
        val devices = show(Tokens())
        // Typed through the holder: text entry in a dialog window never
        // settles under Robolectric, and the dialog is not what is tested.
        rule.runOnIdle {
            devices.setName("iPad")
            devices.create()
        }
        until { devices.state.value.token != null && !devices.state.value.isLoading }
        assertEquals(1, count("mochi-secret"))
        details()
    }

    @Test
    fun `no devices reads as such, without a full stop`() {
        show(Tokens())
        assertEquals(1, count("No devices connected"))
    }

    @Test
    fun `a device never used says so in its own words, and a used one says when`() {
        val made = 1_700_000_000L
        val used = 1_700_100_000L
        show(
            Tokens(
                listOf(
                    Device(hash = "a", name = "Laptop", created = made),
                    Device(hash = "b", name = "Phone", created = made, used = used),
                ),
            ),
        )
        assertEquals(2, within("Created "))
        assertEquals(1, within(" · Never used"))
        assertEquals(1, within(" · Last used "))
        assertEquals(0, within("never"))
    }

    @Test
    fun `a device is deleted by its own delete button and confirmation`() {
        val tokens = Tokens(listOf(Device(hash = "a", name = "Laptop", created = 1_700_000_000)))
        val devices = show(tokens)
        rule.onNodeWithContentDescription("Delete device").performClick()
        rule.onNodeWithText("Delete device?").assertExists()
        rule.onNodeWithText("Delete").performClick()
        until { devices.state.value.devices.isEmpty() }
        assertEquals(listOf("a"), tokens.deleted)
        assertTrue(count("Laptop") == 0)
        until { count("Device deleted") == 1 }
    }
}
