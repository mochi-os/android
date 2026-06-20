// Copyright © 2026 Mochi OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.account

import android.accounts.AbstractAccountAuthenticator
import android.accounts.Account
import android.accounts.AccountAuthenticatorResponse
import android.accounts.AccountManager
import android.content.Context
import android.os.Bundle

/**
 * Authenticator backing [MochiAccount].
 *
 * Deliberately minimal: AccountManager needs *some* registered authenticator
 * for our account type, but per-app JWTs are minted locally inside each app
 * (via `/_/token` using the session this authenticator hands out). Doing the
 * mint here would mean cross-process IPC into the device-elected owner's
 * process for something every app can already do itself — added latency, more
 * failure modes, no benefit. Apps fetch the session via [AccountManager.getPassword]
 * (signature-protected) and run their own `/_/token` calls with it.
 *
 * "Add account" routes to the host app's normal login UI; "get token" hands
 * back the raw session cookie value regardless of [authTokenType].
 */
class MochiAuthenticator(private val context: Context) :
    AbstractAccountAuthenticator(context) {

    override fun addAccount(
        response: AccountAuthenticatorResponse,
        accountType: String,
        authTokenType: String?,
        requiredFeatures: Array<out String>?,
        options: Bundle?
    ): Bundle {
        val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
        return Bundle().apply { putParcelable(AccountManager.KEY_INTENT, intent) }
    }

    override fun getAuthToken(
        response: AccountAuthenticatorResponse,
        account: Account,
        authTokenType: String,
        options: Bundle?
    ): Bundle {
        val am = AccountManager.get(context)
        val session = am.getPassword(account)
        val result = Bundle()
        result.putString(AccountManager.KEY_ACCOUNT_NAME, account.name)
        result.putString(AccountManager.KEY_ACCOUNT_TYPE, account.type)
        if (session != null) result.putString(AccountManager.KEY_AUTHTOKEN, session)
        return result
    }

    override fun getAuthTokenLabel(authTokenType: String): String = "Mochi session"

    override fun editProperties(
        response: AccountAuthenticatorResponse,
        accountType: String
    ): Bundle? = null

    override fun confirmCredentials(
        response: AccountAuthenticatorResponse,
        account: Account,
        options: Bundle?
    ): Bundle? = null

    override fun updateCredentials(
        response: AccountAuthenticatorResponse,
        account: Account,
        authTokenType: String?,
        options: Bundle?
    ): Bundle? = null

    override fun hasFeatures(
        response: AccountAuthenticatorResponse,
        account: Account,
        features: Array<out String>
    ): Bundle = Bundle().apply { putBoolean(AccountManager.KEY_BOOLEAN_RESULT, false) }
}
