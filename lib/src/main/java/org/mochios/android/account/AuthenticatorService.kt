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
