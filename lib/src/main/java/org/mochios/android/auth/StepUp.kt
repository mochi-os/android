// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.auth

/**
 * Step-up re-authentication, mirroring lib/web's StepUpDialog + StepUpClient.
 *
 * A sensitive account-security change (adding a passkey, enabling an
 * authenticator, regenerating recovery codes, changing login methods) must
 * be re-verified with the same factor(s) the user logs in with. The shared
 * [org.mochios.android.ui.components.StepUpDialog] drives the UI; the app
 * supplies a [StepUpClient] wired to its own re-authentication actions (a lib
 * component must not reference an app's action paths).
 *
 * Each verify returns a [StepUpResult]: a single-use proof token once every
 * required factor is satisfied, otherwise the factors still outstanding.
 */
data class StepUpResult(
    val token: String? = null,
    val remaining: List<String>? = null,
)

interface StepUpClient {
    /** The code/credential factors to show: a subset of email/totp/passkey.
     *  When something is required it is the required code factors; otherwise
     *  every usable factor (any one satisfies the step-up). OAuth is offered
     *  separately via [oauthProviders]. */
    suspend fun methods(): List<String>

    /** Email the user a step-up code. */
    suspend fun send()

    suspend fun verifyEmail(code: String): StepUpResult

    suspend fun verifyTotp(code: String): StepUpResult

    /** Run the full passkey assertion ceremony (begin + platform UI + finish)
     *  and return its result. */
    suspend fun passkey(): StepUpResult

    /** Linked OAuth providers (e.g. ["google"]) that can satisfy this step-up,
     *  or empty when OAuth can't complete it. */
    suspend fun oauthProviders(): List<String>

    /** Begin an OAuth step-up for [provider]: returns the URL to open in a
     *  browser. The client holds the verifier for [oauthPoll]. */
    suspend fun oauthBegin(provider: String): String

    /** Poll for the proof the OAuth browser flow produced. Returns a result
     *  carrying token/remaining once the provider callback has stored it, or
     *  an empty result (both null) while it is still pending. */
    suspend fun oauthPoll(): StepUpResult
}
