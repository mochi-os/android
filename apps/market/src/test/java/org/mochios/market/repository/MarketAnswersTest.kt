// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.market.repository

import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mochios.market.FlowServer
import org.mochios.market.model.Asset
import org.mochios.market.model.AssetDownload
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * The repository reads each answer as the server gives it: a write that
 * answers no data succeeds; a download is read by the asset's hosting and
 * named from its row; an account change is held as answered; and the
 * shipping regions are read once.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MarketAnswersTest {

    private val context = RuntimeEnvironment.getApplication()
    private lateinit var server: FlowServer
    private lateinit var repository: MarketRepository

    @Before
    fun begin() {
        server = FlowServer()
        repository = server.repository(context)
    }

    @After
    fun end() = server.stop()

    private fun answerAll(body: String) {
        for (path in listOf("-/listings/delete", "-/assets/remove", "-/reservations/cancel")) {
            server.on(path) { MockResponse().setBody(body) }
        }
    }

    private fun writes() = runBlocking {
        repository.deleteListing("l1")
        repository.removeAsset("a1")
        repository.cancelReservation("l1")
    }

    @Test
    fun `a delete, a removal and a cancel answered with no data succeed`() {
        answerAll("""{"data": null}""")
        writes()
        assertEquals(3, server.asked.size)
    }

    @Test
    fun `the same writes answered ok succeed`() {
        answerAll("""{"data": {"ok": true}}""")
        writes()
        assertEquals(3, server.asked.size)
    }

    @Test
    fun `a hosted asset that is itself JSON downloads as its bytes, named from its row`() {
        server.on("-/assets/download") {
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setHeader("Content-Disposition", "attachment; filename=\".json\"; filename*=UTF-8''%E5%86%99%E7%9C%9F.json")
                .setBody("""{"frames": 30}""")
        }
        val asset = Asset(id = "a1", hosting = "mochi", filename = "写真.json", mime = "application/json")
        val outcome = runBlocking { repository.downloadAsset(asset) }
        assertTrue("the file itself: $outcome", outcome is AssetDownload.Bytes)
        outcome as AssetDownload.Bytes
        assertEquals("写真.json", outcome.fileName)
        assertEquals("""{"frames": 30}""", outcome.body.string())
    }

    @Test
    fun `an external asset opens its link`() {
        server.data("-/assets/download", """{"hosting": "external", "asset": {"reference": "https://example.com/pack.zip"}}""")
        val outcome = runBlocking { repository.downloadAsset(Asset(id = "a1", hosting = "external")) }
        assertEquals(AssetDownload.External("https://example.com/pack.zip"), outcome)
    }

    @Test
    fun `an account change is held as answered, its computed fields with it`() {
        server.data(
            "-/accounts/update",
            """{"id": "a1", "name": "Ana", "verified": 1, "stripe_testmode": true, "location": "York"}""",
        )
        server.data("-/accounts/get", """{"id": "a1", "name": "Ana", "verified": 1, "stripe_testmode": true}""")
        runBlocking { repository.updateAccount(mapOf("location" to "York")) }
        val held = runBlocking { repository.getAccount() }
        assertTrue(held.stripeTestmode)
        assertEquals("Ana", held.name)
        assertEquals(0, server.of("-/accounts/get").size)
    }

    @Test
    fun `the shipping regions are read once`() {
        server.data("-/shipping/regions", """{"countries": ["GB"], "groups": {"world": []}}""")
        val first = runBlocking { repository.shippingRegions() }
        val second = runBlocking { repository.shippingRegions() }
        assertEquals(listOf("GB"), first.countries)
        assertEquals(first, second)
        assertEquals(1, server.of("-/shipping/regions").size)
    }
}
