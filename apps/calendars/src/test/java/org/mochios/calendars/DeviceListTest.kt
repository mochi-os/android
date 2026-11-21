// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.calendars

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mochios.android.api.ApiResponse
import org.mochios.android.api.unwrap
import org.mochios.android.ui.components.Device
import org.mochios.android.ui.components.DeviceCreated
import org.mochios.android.ui.components.DeviceListing
import org.mochios.calendars.model.TokenResponse
import org.mochios.calendars.model.TokensResponse
import org.mochios.calendars.ui.devices.CALDAV_PATH
import org.mochios.calendars.ui.devices.created
import org.mochios.calendars.ui.devices.listing
import retrofit2.Response

/**
 * The connected-devices screen shows the username before any device exists,
 * so it has to arrive with the device list, under the key the server uses.
 */
class DeviceListTest {

    private fun listing(body: String): TokensResponse {
        val type = object : TypeToken<ApiResponse<TokensResponse>>() {}.type
        val parsed: ApiResponse<TokensResponse> = Gson().fromJson(body, type)
        return Response.success(parsed).unwrap()
    }

    @Test
    fun `the device list carries the account's username`() {
        val listed = listing(
            """{"data": {"tokens": [{"hash": "h1", "name": "Phone", "created": 1, "used": 0}], "username": "someone@example.test"}}"""
        )
        assertEquals("someone@example.test", listed.username)
        assertEquals(listOf("Phone"), listed.tokens.map { it.name })
    }

    @Test
    fun `a server that sends no username leaves it empty`() {
        assertEquals("", listing("""{"data": {"tokens": []}}""").username)
    }

    @Test
    fun `the shared screen is given every device whole, the username and the CalDAV address`() {
        val listed = listing(
            """{"data": {"tokens": [{"hash": "h1", "name": "Phone", "created": 1, "used": 2}], "username": "someone@example.test"}}"""
        ).listing()
        assertEquals(DeviceListing(listOf(Device("h1", "Phone", 1, 2)), "someone@example.test"), listed)
        assertEquals(DeviceCreated("secret", "someone@example.test"), TokenResponse("secret", "someone@example.test").created())
        assertEquals("calendars/caldav/", CALDAV_PATH)
    }
}
