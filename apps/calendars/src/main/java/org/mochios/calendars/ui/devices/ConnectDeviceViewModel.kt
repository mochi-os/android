// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.calendars.ui.devices

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import org.mochios.android.auth.SessionManager
import org.mochios.android.ui.components.ConnectedDevices
import org.mochios.android.ui.components.Device
import org.mochios.android.ui.components.DeviceCreated
import org.mochios.android.ui.components.DeviceListing
import org.mochios.android.ui.components.DeviceTokens
import org.mochios.calendars.model.TokenResponse
import org.mochios.calendars.model.TokensResponse
import org.mochios.calendars.repository.CalendarsRepository
import javax.inject.Inject

/** Where the user's server serves CalDAV, the address a device's calendar account is given. */
internal const val CALDAV_PATH = "calendars/caldav/"

/**
 * The connected-devices screen over Calendars' own device actions. The
 * server lists device credentials alone: a calendar's ICS link token is not a
 * device, and is replaced or revoked from that calendar's menu.
 */
@HiltViewModel
class ConnectDeviceViewModel @Inject constructor(
    repository: CalendarsRepository,
    sessionManager: SessionManager,
) : ViewModel() {
    val devices = ConnectedDevices(
        viewModelScope,
        sessionManager,
        CALDAV_PATH,
        object : DeviceTokens {
            override suspend fun list(): DeviceListing = repository.listTokens().listing()
            override suspend fun create(name: String): DeviceCreated = repository.createToken(name).created()
            override suspend fun delete(hash: String) = repository.deleteToken(hash)
        },
    )
}

/** The device list as the shared screen takes it. */
internal fun TokensResponse.listing(): DeviceListing =
    DeviceListing(tokens.map { Device(it.hash, it.name, it.created, it.used) }, username)

internal fun TokenResponse.created(): DeviceCreated = DeviceCreated(token, username)
