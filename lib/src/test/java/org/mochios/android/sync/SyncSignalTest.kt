// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.sync

import android.accounts.AccountManager
import android.content.ContentResolver
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.mochios.android.account.MochiAccount
import org.mochios.android.auth.SessionManager
import org.mochios.android.i18n.FakeKeystore
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowContentResolver

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SyncSignalTest {
    private val context = RuntimeEnvironment.getApplication()

    @Test
    fun readsTheSyncASignalAsksFor() {
        assertEquals("calendars", SyncSignal.kind(mapOf("sync" to "calendars")))
        assertEquals("contacts", SyncSignal.kind(mapOf("sync" to "contacts")))
    }

    @Test
    fun leavesAnOrdinaryNotificationAlone() {
        assertNull(SyncSignal.kind(mapOf("title" to "Reminder test", "body" to "Starts at 12:05")))
        assertNull(SyncSignal.kind(mapOf("sync" to "")))
    }

    /** Push reaches every account on the phone; the signed-in one is only one of them. */
    @Test
    fun syncsTheAccountThePushWasDeliveredFor() {
        FakeKeystore.install()
        shadowOf(AccountManager.get(context)).addAuthenticator(MochiAccount.TYPE)
        val session = SessionManager(context)
        runBlocking {
            session.clearAll()
            session.setBoundAccount("a1")
        }
        MochiAccount.upsert(context, "a1", "Ann", "ann@example.org", "https://one.example", null, "s1")
        MochiAccount.upsert(context, "b2", "Bo", "bo@example.org", "https://two.example", null, "s2")
        val bound = CalendarsSync.account(context, "a1")!!
        val other = CalendarsSync.account(context, "b2")!!
        for (account in listOf(bound, other)) {
            ContentResolver.setIsSyncable(account, CalendarsSync.AUTHORITY, 1)
            ContentResolver.setSyncAutomatically(account, CalendarsSync.AUTHORITY, true)
        }
        SyncSignal.run(context, "calendars", "b2", session)
        val deadline = System.currentTimeMillis() + 5_000
        while (ShadowContentResolver.getStatus(other, CalendarsSync.AUTHORITY).syncRequests == 0) {
            check(System.currentTimeMillis() < deadline) { "the push's account was not synced" }
            Thread.sleep(10)
        }
        assertEquals("THE POINT: not the signed-in one", 0, ShadowContentResolver.getStatus(bound, CalendarsSync.AUTHORITY).syncRequests)
    }
}
