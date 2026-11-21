// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.people.ui.devices

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import org.mochios.android.auth.SessionManager
import org.mochios.android.ui.components.ConnectedDevices
import org.mochios.android.ui.components.Device
import org.mochios.android.ui.components.DeviceCreated
import org.mochios.android.ui.components.DeviceListing
import org.mochios.android.ui.components.DeviceTokens
import org.mochios.people.api.TokenResponse
import org.mochios.people.api.TokensResponse
import org.mochios.people.repository.PeopleRepository
import javax.inject.Inject

/** Where the user's server serves CardDAV, the address a device's contacts account is given. */
internal const val CARDDAV_PATH = "people/carddav/"

/** The connected-devices screen over People's own device actions. */
@HiltViewModel
class ConnectDeviceViewModel @Inject constructor(
    repository: PeopleRepository,
    sessionManager: SessionManager,
) : ViewModel() {
    val devices = ConnectedDevices(
        viewModelScope,
        sessionManager,
        CARDDAV_PATH,
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
