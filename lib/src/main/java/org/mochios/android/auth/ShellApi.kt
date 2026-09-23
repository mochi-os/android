// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.auth

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST
import java.util.TimeZone

/** The body of the shell's boot request: the zone this device keeps time in. */
data class ShellRequest(val timezone: String)

/** The boot request as this device sends it. */
fun shellRequest(): ShellRequest = ShellRequest(TimeZone.getDefault().id)

/**
 * The shell's boot request, POST /_/shell, authorised by the session cookie on
 * the shared CookieJar. The web shell sends it on every page load; the client
 * sends it when it refreshes the preferences, and for the same reason: the
 * server keeps the device's zone as the user's while the timezone preference
 * is "auto", so recurrences and reminders follow the device last used.
 */
interface ShellApi {

    @POST("_/shell")
    suspend fun boot(@Body body: ShellRequest): Response<Map<String, Any>>
}
