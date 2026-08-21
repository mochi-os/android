// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.account

import android.app.Service
import android.content.Intent
import android.os.IBinder

/**
 * Exposes [MochiAuthenticator] to AccountManager. Every Mochi app declares this
 * service identically; Android elects one as the device-wide owner, and apps
 * signed with the same key read the shared session via
 * [AccountManager.getPassword].
 */
class AuthenticatorService : Service() {
    override fun onBind(intent: Intent): IBinder = MochiAuthenticator(this).iBinder
}
