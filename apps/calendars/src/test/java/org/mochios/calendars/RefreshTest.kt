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
import org.mochios.calendars.model.RefreshResponse
import retrofit2.Response

/**
 * The calendar screen asks the server to sync the stale linked calendars as it
 * comes into view, and reloads only when the answer says something changed.
 */
class RefreshTest {

    private fun refresh(body: String): RefreshResponse {
        val type = object : TypeToken<ApiResponse<RefreshResponse>>() {}.type
        val parsed: ApiResponse<RefreshResponse> = Gson().fromJson(body, type)
        return Response.success(parsed).unwrap()
    }

    @Test
    fun `a sync that changed something says so`() {
        assertEquals(true, refresh("""{"data": {"changed": true}}""").changed)
    }

    @Test
    fun `a sync that changed nothing leaves the screen alone`() {
        assertEquals(false, refresh("""{"data": {"changed": false}}""").changed)
    }
}
