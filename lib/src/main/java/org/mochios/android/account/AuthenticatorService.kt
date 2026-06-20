// Copyright © 2026 Mochi OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.account

import android.app.Service
import android.content.Intent
import android.os.IBinder

/**
 * Bound service exposing [MochiAuthenticator] to AccountManager. Each Mochi
 * app declares this service in its manifest with the same intent filter and
 * the same xml metadata; Android picks one as the device-wide authenticator
 * owner. All apps (signed with the same key) can read the shared session via
 * [AccountManager.getPassword].
 */
class AuthenticatorService : Service() {
    override fun onBind(intent: Intent): IBinder = MochiAuthenticator(this).iBinder
}
