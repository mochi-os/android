// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.projects.api

import com.google.gson.reflect.TypeToken
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.mochios.android.api.ApiResponse
import org.mochios.android.model.Comment
import org.mochios.projects.model.Project

/**
 * The shapes projects.star actually answers with. `-/probe` puts the project
 * flat in `data` with `peer` (share link) or `server` (web address) beside it;
 * `comments/create` answers with the comment flat, and `attachments/create`
 * with the object's attachment list.
 */
class ProbeResponseTest {
    private val gson = org.mochios.android.api.ApiClient.provideGson()

    @Test
    fun `a share-link probe carries the id and the peer to pin`() {
        val json = """{"data":{"id":"projectentity","name":"Roadmap","description":"","fingerprint":"abc123def","class":"project","peer":"12D3KooWpeer","remote":true}}"""
        val response: ApiResponse<Project> = gson.fromJson(json, object : TypeToken<ApiResponse<Project>>() {}.type)
        assertEquals("projectentity", response.data.id)
        assertEquals("12D3KooWpeer", response.data.peer)
        assertNull(response.data.server)
    }

    @Test
    fun `a comment create answers with the comment flat`() {
        val json = """{"data":{"id":"c1","parent":"","author":"me","name":"Me","content":"hello","created":1700000000,"edited":0,"children":[],"attachments":[]}}"""
        val response: ApiResponse<Comment> = gson.fromJson(json, object : TypeToken<ApiResponse<Comment>>() {}.type)
        assertEquals("c1", response.data.id)
        assertEquals("hello", response.data.content)
    }

    @Test
    fun `an attachment create answers with the list`() {
        val json = """{"data":{"attachments":[{"id":"att1","name":"a.png","size":10},{"id":"att2","name":"b.png","size":20}]}}"""
        val response: ApiResponse<AttachmentListResponse> = gson.fromJson(json, object : TypeToken<ApiResponse<AttachmentListResponse>>() {}.type)
        assertEquals(listOf("att1", "att2"), response.data.attachments.map { it.id })
    }
}
