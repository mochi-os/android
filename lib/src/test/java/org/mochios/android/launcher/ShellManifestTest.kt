// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.launcher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * The shell's manifest declares the entry points other apps can reach, and
 * these are the ones it must keep narrow: only MainActivity, the home icon
 * and the share destination take another app's intent, the share
 * destination only a share, the push receiver takes no cleartext delivery,
 * and the distributor takes only Mochi-signed callers.
 */
class ShellManifestTest {

    private val android = "http://schemas.android.com/apk/res/android"

    // Unit tests run in the module's directory.
    private val manifest: Element = DocumentBuilderFactory.newInstance()
        .apply { isNamespaceAware = true }
        .newDocumentBuilder()
        .parse(File("../app/src/main/AndroidManifest.xml"))
        .documentElement

    private fun elements(name: String): List<Element> {
        val nodes = manifest.getElementsByTagName(name)
        return (0 until nodes.length).map { index -> nodes.item(index) as Element }
    }

    private fun Element.attribute(name: String): String = getAttributeNS(android, name)

    private fun Element.actions(): List<String> =
        (0 until getElementsByTagName("action").length).map { index ->
            (getElementsByTagName("action").item(index) as Element).attribute("name")
        }

    @Test
    fun `only MainActivity, the home icon and the share destination are exported`() {
        val exported = elements("activity").associate { activity ->
            activity.attribute("name") to activity.attribute("exported")
        }
        assertEquals(LAUNCHER_ACTIVITIES.size + 2, exported.size)
        for ((name, value) in exported) {
            val expected = if (name in setOf(".MainActivity", ".MochiHomeLauncher", SHARE)) "true" else "false"
            assertEquals(name, expected, value)
        }
    }

    @Test
    fun `the share destination takes a share and nothing that routes`() {
        val share = elements("activity").single { it.attribute("name") == SHARE }
        assertEquals(listOf("android.intent.action.SEND", "android.intent.action.SEND_MULTIPLE"), share.actions())
        assertEquals(0, share.getElementsByTagName("category").let { categories ->
            (0 until categories.length).count { index -> (categories.item(index) as Element).attribute("name") != "android.intent.category.DEFAULT" }
        })
        val data = share.getElementsByTagName("data")
        val types = (0 until data.length).map { index -> data.item(index) as Element }
        assertTrue("no scheme or host: $types", types.all { it.attribute("scheme").isEmpty() && it.attribute("host").isEmpty() })
        assertEquals(setOf("text/plain", "image/*"), types.map { it.attribute("mimeType") }.toSet())
    }

    @Test
    fun `the push receiver takes no delivery but the connector's`() {
        val receiver = elements("receiver").single { it.attribute("name") == "org.mochios.android.push.MochiPushReceiver" }
        assertEquals(
            listOf(
                "org.unifiedpush.android.connector.MESSAGE",
                "org.unifiedpush.android.connector.UNREGISTERED",
                "org.unifiedpush.android.connector.NEW_ENDPOINT",
                "org.unifiedpush.android.connector.REGISTRATION_FAILED",
            ),
            receiver.actions(),
        )
        val local = elements("action").filter { it.attribute("name") == "org.mochios.android.push.MESSAGE" }
        assertEquals("no receiver takes the old cleartext action", 0, local.size)
    }

    @Test
    fun `the distributor is guarded by a signature permission the app holds`() {
        val receiver = elements("receiver")
            .single { it.attribute("name") == "org.mochios.android.push.MochiDistributorReceiver" }
        val permission = receiver.attribute("permission")
        assertTrue("guarded", permission.isNotEmpty())
        val declared = elements("permission").single { it.attribute("name") == permission }
        assertEquals("signature", declared.attribute("protectionLevel"))
        assertTrue(elements("uses-permission").any { it.attribute("name") == permission })
        // The connector finds a distributor by its filter, so it stays.
        assertTrue("org.unifiedpush.android.distributor.REGISTER" in receiver.actions())
    }

    private companion object {
        /** Mochi in the system share sheet. */
        const val SHARE = "org.mochios.android.share.ShareActivity"
    }
}
