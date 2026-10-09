// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.home

import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mochios.home.api.Icon
import org.mochios.home.api.IconsResponse
import org.mochios.home.repository.Tile
import org.mochios.home.repository.grid

/**
 * The grid shows the apps the server lists that this client can open, sorted
 * by their names as the web sorts them, with the theme's tile look.
 */
class GridTest {

    private fun icon(link: String, name: String, highlight: Boolean = false) =
        Icon(id = link, path = link, name = name, file = "images/icon.svg", link = link, highlight = highlight)

    @Test
    fun `apps the client has no module for are left out`() {
        val tiles = grid(IconsResponse(listOf(icon("feeds", "Feeds"), icon("air", "Air"), icon("help", "Help")))).tiles
        assertEquals(listOf(Tile("feeds", "Feeds")), tiles)
    }

    @Test
    fun `tiles sort by label, ignoring case and accents, numbers by value`() {
        val labels = grid(
            IconsResponse(
                listOf(
                    icon("wikis", "wikis"),
                    icon("chat", "Échanges"),
                    icon("feeds", "Feeds"),
                    icon("people", "Annuaire 10"),
                    icon("go", "Annuaire 9"),
                ),
            ),
        ).tiles.map { tile -> tile.label }
        assertEquals(listOf("Annuaire 9", "Annuaire 10", "Échanges", "Feeds", "wikis"), labels)
    }

    @Test
    fun `an app listed twice appears once`() {
        val tiles = grid(IconsResponse(listOf(icon("chess", "Chess"), icon("chess", "Chess")))).tiles
        assertEquals(1, tiles.size)
    }

    @Test
    fun `the server's own label and highlight carry through`() {
        val tile = grid(IconsResponse(listOf(icon("settings", "Einstellungen", highlight = true)))).tiles.single()
        assertEquals(Tile("settings", "Einstellungen", highlight = true), tile)
    }

    @Test
    fun `the root app and nameless entries are left out`() {
        val tiles = grid(IconsResponse(listOf(icon("", "Home"), icon("feeds", "")))).tiles
        assertTrue(tiles.isEmpty())
    }

    @Test
    fun `the theme's mask and background pass through, blank ones as none`() {
        val themed = grid(IconsResponse(emptyList(), mask = "squircle", background = "#3366cc"))
        assertEquals("squircle", themed.mask)
        assertEquals("#3366cc", themed.background)
        val plain = grid(IconsResponse(emptyList(), mask = "", background = ""))
        assertNull(plain.mask)
        assertNull(plain.background)
    }

    @Test
    fun `the server's answer reads as it is sent, unwrapped`() {
        val response = Gson().fromJson(
            """{"icons":[{"development":true,"file":"images/icon.svg","id":"chat","link":"chat",
                "name":"Chat","path":"chat"}],"icon_mask":"rounded","icon_background":"#112233"}""",
            IconsResponse::class.java,
        )
        val built = grid(response)
        assertEquals(listOf(Tile("chat", "Chat")), built.tiles)
        assertEquals("rounded", built.mask)
        assertEquals("#112233", built.background)
    }
}
