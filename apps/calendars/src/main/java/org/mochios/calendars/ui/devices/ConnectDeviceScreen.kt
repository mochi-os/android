// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.calendars.ui.devices

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import org.mochios.android.ui.components.ConnectedDevicesScreen
import org.mochios.calendars.R

/** The connected devices, with the address a device's calendar account takes. */
@Composable
fun ConnectDeviceScreen(
    onBack: () -> Unit,
    viewModel: ConnectDeviceViewModel = hiltViewModel(),
) {
    ConnectedDevicesScreen(
        devices = viewModel.devices,
        addressLabel = stringResource(R.string.calendars_device_address),
        onBack = onBack,
    )
}
