// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.account

import android.accounts.Account
import android.accounts.AccountAuthenticatorResponse
import android.accounts.AccountManager
import android.content.Context
import android.os.Bundle
import android.os.Parcel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mochios.android.i18n.FakeKeystore
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * Each account's session is the whole credential: every token core mints
 * names it. It is kept sealed in the app's own storage, by identity, for each
 * account on the device - push opens a channel per account with it - and the
 * framework's account database, where it was the password in the clear, holds
 * none.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AccountSessionTest {
    private val context = RuntimeEnvironment.getApplication()
    private val accounts = AccountManager.get(context)

    @Before
    fun begin() {
        FakeKeystore.install()
        shadowOf(accounts).addAuthenticator(MochiAccount.TYPE)
    }

    private fun stored(identity: String): String? =
        context.getSharedPreferences("mochi_sessions", Context.MODE_PRIVATE).getString(identity, null)

    @Test
    fun `an account's session is kept sealed, and not as its password`() {
        MochiAccount.upsert(context, "a1", "Ada", "ada@example.org", "https://one.example", null, "session-one")
        val account = accounts.getAccountsByType(MochiAccount.TYPE).single()
        assertNull("THE POINT: no password in the clear", accounts.getPassword(account))
        assertFalse("sealed, not in the clear", stored("a1")!!.contains("session-one"))
        assertEquals("session-one", MochiAccount.byIdentity(context, "a1")!!.session)
    }

    @Test
    fun `each account keeps its own session`() {
        MochiAccount.upsert(context, "a1", "Ada", "ada@example.org", "https://one.example", null, "session-one")
        MochiAccount.upsert(context, "b2", "Bo", "bo@example.org", "https://two.example", null, "session-two")
        val sessions = MochiAccount.all(context).associate { snapshot -> snapshot.identity to snapshot.session }
        assertEquals(mapOf("a1" to "session-one", "b2" to "session-two"), sessions)
        MochiAccount.upsert(context, "a1", "Ada", "ada@example.org", "https://one.example", null, "session-renewed")
        assertEquals("session-renewed", MochiAccount.byIdentity(context, "a1")!!.session)
    }

    @Test
    fun `a session kept as a password moves into the sealed store`() {
        val data = Bundle().apply {
            putString("identity", "a1")
            putString("server", "https://one.example")
        }
        val account = Account("ada@example.org", MochiAccount.TYPE)
        accounts.addAccountExplicitly(account, "legacy-session", data)
        assertEquals("legacy-session", MochiAccount.byIdentity(context, "a1")!!.session)
        assertNull("the clear copy is gone", accounts.getPassword(account))
        assertTrue(stored("a1") != null)
        assertEquals("and still reads", "legacy-session", MochiAccount.byIdentity(context, "a1")!!.session)
    }

    @Test
    fun `the authenticator hands no session out`() {
        MochiAccount.upsert(context, "a1", "Ada", "ada@example.org", "https://one.example", null, "session-one")
        val account = accounts.getAccountsByType(MochiAccount.TYPE).single()
        val parcel = Parcel.obtain().apply {
            writeStrongBinder(null)
            setDataPosition(0)
        }
        val response = AccountAuthenticatorResponse.CREATOR.createFromParcel(parcel)
        val answer = MochiAuthenticator(context).getAuthToken(response, account, "session", Bundle())
        assertNull(answer.getString(AccountManager.KEY_AUTHTOKEN))
        assertEquals(AccountManager.ERROR_CODE_UNSUPPORTED_OPERATION, answer.getInt(AccountManager.KEY_ERROR_CODE))
    }

    @Test
    fun `removing an account removes its session`() {
        MochiAccount.upsert(context, "a1", "Ada", "ada@example.org", "https://one.example", null, "session-one")
        MochiAccount.remove(context, "a1")
        assertNull(stored("a1"))
        assertNull(MochiAccount.byIdentity(context, "a1"))
    }
}
