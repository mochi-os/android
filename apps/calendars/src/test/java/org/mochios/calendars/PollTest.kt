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
import org.mochios.calendars.model.PollResponse
import retrofit2.Response

/**
 * "Check for changes" and "Sync now" read `-/calendars/poll`, which answers
 * whether the poll changed anything as a bool, and the calendar after it,
 * whose kind decides what the snackbar says.
 */
class PollTest {

    private fun poll(body: String): PollResponse {
        val type = object : TypeToken<ApiResponse<PollResponse>>() {}.type
        val parsed: ApiResponse<PollResponse> = Gson().fromJson(body, type)
        return Response.success(parsed).unwrap()
    }

    @Test
    fun `a poll that changed something parses`() {
        val response = poll("""{"data": {"changed": true, "calendar": {"id": "c", "kind": "subscription"}}}""")
        assertEquals(true, response.changed)
        assertEquals(false, response.calendar.linked)
    }

    @Test
    fun `a sync of a linked calendar that changed nothing parses and says it is linked`() {
        val response = poll("""{"data": {"changed": false, "calendar": {"id": "c", "kind": "linked"}}}""")
        assertEquals(false, response.changed)
        assertEquals(true, response.calendar.linked)
    }
}
