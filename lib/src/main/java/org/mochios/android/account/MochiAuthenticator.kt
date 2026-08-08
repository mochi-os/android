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
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Process
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
 * The caller must be signed with our certificate. The framework has enforced
 * that for account visibility and token grants since API 23 (and minSdk is
 * above that), so this is defence-in-depth rather than the only gate — but
 * the value handed out is the full-account session, and a one-line signature
 * check is the standard posture for an authenticator holding one.
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
        // The framework puts the requesting app's uid in the options bundle.
        // Refuse anything not signed with our certificate — and refuse when
        // the uid is absent, since a request the framework didn't stamp has
        // no business receiving a session.
        val caller = options?.getInt(AccountManager.KEY_CALLER_UID, -1) ?: -1
        val match = caller >= 0 &&
            context.packageManager.checkSignatures(caller, Process.myUid()) ==
            PackageManager.SIGNATURE_MATCH
        if (!match) {
            result.putInt(AccountManager.KEY_ERROR_CODE, AccountManager.ERROR_CODE_BAD_REQUEST)
            result.putString(AccountManager.KEY_ERROR_MESSAGE, "Caller not permitted")
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
