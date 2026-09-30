// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.people.ui.invitations

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mochios.android.api.ApiResponse
import org.mochios.android.api.unwrap
import org.mochios.people.api.ContactsListResponse
import org.mochios.people.model.FriendInvite
import retrofit2.Response

/**
 * A received invite's name and photo are the sender's own claim, so its row
 * also shows what an impersonator cannot copy: the fingerprint, and the
 * directory's name where it differs from the claimed one.
 */
class ReceivedDetailTest {

    private val listed = { name: String -> "Listed as $name" }

    @Test
    fun `a received invite arrives with its fingerprint and directory name`() {
        val type = object : TypeToken<ApiResponse<ContactsListResponse>>() {}.type
        val body = """{"data": {"contacts": [], "sent": [], "received": [{"identity": "me",
            "id": "p1", "direction": "from", "name": "Ada", "updated": 1,
            "fingerprint": "abcdefghi", "directory": "Ada Lovelace"}]}}"""
        val parsed: ApiResponse<ContactsListResponse> = Gson().fromJson(body, type)
        val invite = Response.success(parsed).unwrap().received.single()
        assertEquals("abcdefghi", invite.fingerprint)
        assertEquals("Ada Lovelace", invite.directory)
    }

    @Test
    fun `the fingerprint shows under the claimed name`() {
        val invite = FriendInvite(name = "Ada", fingerprint = "abcdefghi", directory = "Ada")
        assertEquals("abc-def-ghi", receivedDetail(invite, listed))
    }

    @Test
    fun `a directory name that differs from the claim is shown beside it`() {
        val invite = FriendInvite(name = "Ada", fingerprint = "abcdefghi", directory = "Mallory")
        assertEquals("abc-def-ghi · Listed as Mallory", receivedDetail(invite, listed))
    }

    @Test
    fun `a sender the directory does not list shows the fingerprint alone`() {
        val invite = FriendInvite(name = "Ada", fingerprint = "abcdefghi", directory = "")
        assertEquals("abc-def-ghi", receivedDetail(invite, listed))
    }
}
