// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.crm.api

import com.google.gson.reflect.TypeToken
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.mochios.android.api.ApiResponse
import org.mochios.crm.model.Crm
import org.mochios.crm.model.CrmClass

/**
 * The shapes crm.star actually answers with. `-/probe` puts the CRM flat in
 * `data` with `peer` (share link) or `server` (web address) beside it, and the
 * create actions answer with the new row's columns flat, not nested under a
 * singular key.
 */
class ProbeResponseTest {
    private val gson = org.mochios.android.api.ApiClient.provideGson()

    @Test
    fun `a share-link probe carries the id and the peer to pin`() {
        val json = """{"data":{"id":"crmentity","name":"Sales","description":"","fingerprint":"abc123def","class":"crm","peer":"12D3KooWpeer","remote":true}}"""
        val response: ApiResponse<Crm> = gson.fromJson(json, object : TypeToken<ApiResponse<Crm>>() {}.type)
        val crm = response.data
        assertEquals("crmentity", crm.id)
        assertEquals("abc123def", crm.fingerprint)
        assertEquals("12D3KooWpeer", crm.peer)
        assertNull(crm.server)
    }

    @Test
    fun `a web-address probe carries the server`() {
        val json = """{"data":{"id":"crmentity","name":"Sales","description":"","fingerprint":"abc123def","class":"crm","server":"https://example.org","remote":true}}"""
        val response: ApiResponse<Crm> = gson.fromJson(json, object : TypeToken<ApiResponse<Crm>>() {}.type)
        assertEquals("https://example.org", response.data.server)
        assertEquals("crmentity", response.data.id)
    }

    @Test
    fun `classes create answers with the class flat`() {
        val json = """{"data":{"id":"company","name":"Company","rank":3}}"""
        val response: ApiResponse<CrmClass> = gson.fromJson(json, object : TypeToken<ApiResponse<CrmClass>>() {}.type)
        assertEquals("company", response.data.id)
        assertEquals("Company", response.data.name)
    }
}
