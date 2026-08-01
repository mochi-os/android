// Copyright © 2026 Mochisoft OÜ
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
import org.mochios.android.R

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
 * back the raw session cookie value, and only for [MochiAccount.TOKEN_SESSION]
 * — the value is the full-account session, so a request for some other token
 * type should not silently receive it.
 *
 * No caller check is made beyond the account type's signature protection. The
 * `options` bundle carries KEY_CALLER_UID and KEY_ANDROID_PACKAGE_NAME, so one
 * could be, but a foreign package reaching this is expected to meet the
 * framework's own consent screen first, and there is exactly one applicationId
 * in this project, so nothing exercises the path today.
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
        val result = Bundle()
        result.putString(AccountManager.KEY_ACCOUNT_NAME, account.name)
        result.putString(AccountManager.KEY_ACCOUNT_TYPE, account.type)
        if (authTokenType != MochiAccount.TOKEN_SESSION) {
            result.putInt(AccountManager.KEY_ERROR_CODE, AccountManager.ERROR_CODE_BAD_ARGUMENTS)
            result.putString(
                AccountManager.KEY_ERROR_MESSAGE,
                "Unsupported token type: $authTokenType",
            )
            return result
        }
        val am = AccountManager.get(context)
        val session = am.getPassword(account)
        if (session != null) result.putString(AccountManager.KEY_AUTHTOKEN, session)
        return result
    }

    /** Shown on the framework's consent screen, so it is user-facing text. */
    override fun getAuthTokenLabel(authTokenType: String): String =
        context.getString(R.string.account_token_label)

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
