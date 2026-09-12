// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.push

import android.content.Context
import android.content.SharedPreferences
import java.security.MessageDigest

/**
 * One push registration this client made, or had refused: the server, the
 * transport, a fingerprint of the credential it sent (an FCM token and
 * installation id, or a UnifiedPush endpoint and keys) and when.
 */
data class Registration(
    val server: String,
    val transport: String,
    val credential: String,
    val at: Long,
)

/** What a registration post answered: the HTTP status and the account id on success. */
data class Answer(val code: Int, val account: String?) {
    val accepted: Boolean get() = code in 200..299
    val refused: Boolean get() = code in 400..499
}

/**
 * Whether a registration needs posting. PushTransport.configure runs on every
 * resume, so without a memo an unchanged phone re-registers on each one and a
 * refused registration is retried on every resume forever. A success or a
 * refusal for this exact server, transport and credential is trusted for
 * [WINDOW]; anything else, including a credential the phone has never sent,
 * is posted.
 */
object RegistrationMemo {
    const val WINDOW: Long = 24L * 60 * 60 * 1000

    enum class Verdict { REGISTER, FRESH, REFUSED }

    fun judge(
        now: Long,
        server: String,
        transport: String,
        credential: String,
        last: Registration?,
        refusal: Registration?,
        window: Long = WINDOW,
    ): Verdict {
        // A refusal after a success for the same credential wins: the server
        // has changed its mind, and re-posting is what it just declined.
        if (refusal.covers(now, server, transport, credential, window)) return Verdict.REFUSED
        if (last.covers(now, server, transport, credential, window)) return Verdict.FRESH
        return Verdict.REGISTER
    }

    private fun Registration?.covers(
        now: Long,
        server: String,
        transport: String,
        credential: String,
        window: Long,
    ): Boolean {
        if (this == null) return false
        if (this.server != server || this.transport != transport || this.credential != credential) return false
        val age = now - at
        // A clock that went backwards makes the record unjudgeable; register.
        return age >= 0 && age < window
    }

    /**
     * An order-sensitive digest of the parts a registration sends, so the memo
     * never stores the token or keys themselves. Each part is terminated, so
     * ("ab", "c") and ("a", "bc") differ.
     */
    fun fingerprint(vararg parts: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        for (part in parts) {
            digest.update(part.toByteArray(Charsets.UTF_8))
            digest.update(0)
        }
        return digest.digest().joinToString("") { "%02x".format(it.toInt() and 0xff) }
    }
}

/**
 * The memo's persistence: the last success and the last refusal. Cleared on
 * sign-out, and superseded rather than cleared when the server or credential
 * changes, since the judge compares all three.
 */
class RegistrationStore(private val prefs: SharedPreferences) {
    constructor(context: Context) : this(
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    )

    fun last(): Registration? = read(REGISTERED)

    fun refusal(): Registration? = read(REFUSED)

    /** A success stands in for any earlier refusal of the same credential. */
    fun success(registration: Registration) {
        prefs.edit().also { write(it, REGISTERED, registration) }.also { forget(it, REFUSED) }.apply()
    }

    fun refuse(registration: Registration) {
        prefs.edit().also { write(it, REFUSED, registration) }.apply()
    }

    fun clear() {
        prefs.edit().also { forget(it, REGISTERED) }.also { forget(it, REFUSED) }.apply()
    }

    private fun read(prefix: String): Registration? {
        val server = prefs.getString("${prefix}_server", null) ?: return null
        val transport = prefs.getString("${prefix}_transport", null) ?: return null
        val credential = prefs.getString("${prefix}_credential", null) ?: return null
        return Registration(server, transport, credential, prefs.getLong("${prefix}_at", 0L))
    }

    private fun write(editor: SharedPreferences.Editor, prefix: String, registration: Registration) {
        editor
            .putString("${prefix}_server", registration.server)
            .putString("${prefix}_transport", registration.transport)
            .putString("${prefix}_credential", registration.credential)
            .putLong("${prefix}_at", registration.at)
    }

    private fun forget(editor: SharedPreferences.Editor, prefix: String) {
        editor
            .remove("${prefix}_server")
            .remove("${prefix}_transport")
            .remove("${prefix}_credential")
            .remove("${prefix}_at")
    }

    private companion object {
        const val PREFS = "mochi_push_registrations"
        const val REGISTERED = "registered"
        const val REFUSED = "refused"
    }
}
