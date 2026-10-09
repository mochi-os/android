// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.home

import android.content.Context
import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.mochios.home.api.Icon
import org.mochios.home.api.IconsResponse
import org.mochios.home.repository.HomeCache
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** The last answer is kept per account, so one account never sees another's apps. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class HomeCacheTest {

    private val context: Context = RuntimeEnvironment.getApplication()

    private val answer = IconsResponse(
        icons = listOf(Icon(id = "chat", path = "chat", name = "Chat", file = "images/icon.svg", link = "chat")),
        mask = "circle",
        background = "#3366cc",
    )

    @Test
    fun `an account reads back what was kept for it`() {
        val cache = HomeCache(context, Gson())
        cache.write("alice", answer)
        assertEquals(answer, cache.read("alice"))
    }

    @Test
    fun `another account reads nothing`() {
        val cache = HomeCache(context, Gson())
        cache.write("alice", answer)
        assertNull(cache.read("bob"))
    }

    @Test
    fun `a kept answer that no longer reads is treated as none`() {
        context.getSharedPreferences("mochi_home", Context.MODE_PRIVATE)
            .edit().putString("alice", "{not json").commit()
        assertNull(HomeCache(context, Gson()).read("alice"))
    }
}
