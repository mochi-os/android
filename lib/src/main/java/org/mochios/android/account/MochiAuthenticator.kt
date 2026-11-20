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
 * Authenticator backing [MochiAccount]: the account type needs one for the
 * sync adapters and the phone's account settings. It hands out nothing: an
 * account's session is kept sealed in the app's own storage, and per-app JWTs
 * are minted from it through `/_/token`.
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
    ): Bundle = Bundle().apply {
        putString(AccountManager.KEY_ACCOUNT_NAME, account.name)
        putString(AccountManager.KEY_ACCOUNT_TYPE, account.type)
        putInt(AccountManager.KEY_ERROR_CODE, AccountManager.ERROR_CODE_UNSUPPORTED_OPERATION)
        putString(AccountManager.KEY_ERROR_MESSAGE, context.getString(R.string.account_error_token_type))
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
