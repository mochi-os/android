// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.sync

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.IOException

/**
 * The sync cycle against a fake server and a fake provider: download, upload,
 * conflict and retry, each as the plan states them.
 */
class ContactsSyncCycleTest {

    private fun card(name: String) = listOf(ContactProperty("FN", emptyMap(), name))

    /** The server: contacts by id, a change log, and a script of failures. */
    private class FakeSource : ContactsSource {
        val contacts = linkedMapOf<String, SyncedContact>()
        val deleted = mutableListOf<String>()
        var version = 1L
        var next = 1
        var refuse: String? = null
        /** The next update or delete finds the contact gone mid-flight. */
        var vanish = false
        var offline = false
        var unauthorized = false
        /** A create answers 404, as a refusal of a contact the server never had. */
        var missing = false
        /** The cursor predates deletions the server no longer remembers. */
        var pruned = false
        /** A server from before the reset flag, which never sends it. */
        var legacy = false
        /** Runs inside the next update, as a phone edit landing mid-upload. */
        var during: (() -> Unit)? = null
        /** How many creates yet to commit and then lose their answer. */
        var lose = 0
        /** How many creates yet to fail before they reach the server. */
        var drop = 0
        /** A server that answers contacts without their slug. */
        var anonymous = false
        /** The slug each create carried, in order. */
        val slugs = mutableListOf<String>()
        val calls = mutableListOf<String>()

        fun seed(id: String, name: String, etag: String = "e-$id", slug: String = id): SyncedContact {
            val contact = SyncedContact(id, "book", etag, card(name), slug)
            contacts[id] = contact
            return contact
        }

        private fun gate() {
            if (offline) throw IOException("offline")
            if (unauthorized) throw SyncAuthorization()
        }

        override suspend fun changes(since: Long): ContactsChanges {
            gate()
            calls.add("changes:$since")
            val reset = !legacy && (since == 0L || pruned)
            return ContactsChanges(version, contacts.keys.toList(), if (reset) emptyList() else deleted.toList(), reset)
        }

        override suspend fun fetch(ids: List<String>): List<SyncedContact> {
            gate()
            calls.add("fetch:${ids.joinToString(",")}")
            return ids.mapNotNull { contacts[it]?.answer() }
        }

        private fun SyncedContact.answer() = if (anonymous) copy(slug = null) else this

        override suspend fun create(properties: List<ContactProperty>, slug: String): SyncedContact {
            gate()
            calls.add("create")
            slugs.add(slug)
            if (drop > 0) {
                drop--
                throw IOException("unreachable")
            }
            refuse?.let { throw SyncRefused(it) }
            if (missing) throw SyncMissing()
            // The book already holds a contact of this name: answered as it
            // stands, whatever card this create carries.
            var contact = contacts.values.firstOrNull { it.slug == slug }
            if (contact == null) {
                val id = "new${next++}"
                contact = SyncedContact(id, "book", "e-$id-1", properties, slug)
                contacts[id] = contact
                version++
            }
            if (lose > 0) {
                lose--
                throw IOException("answer lost")
            }
            return contact.answer()
        }

        override suspend fun update(id: String, etag: String, properties: List<ContactProperty>): SyncedContact {
            gate()
            calls.add("update:$id")
            during?.invoke()
            during = null
            val current = contacts[id] ?: throw SyncMissing()
            if (vanish) {
                contacts.remove(id)
                throw SyncConflict()
            }
            if (current.etag != etag) throw SyncConflict()
            val updated = current.copy(etag = etag + "'", properties = properties)
            contacts[id] = updated
            version++
            return updated.answer()
        }

        override suspend fun delete(id: String, etag: String) {
            gate()
            calls.add("delete:$id")
            refuse?.let { throw SyncRefused(it) }
            val current = contacts[id] ?: throw SyncMissing()
            if (current.etag != etag) throw SyncConflict()
            contacts.remove(id)
            deleted.add(id)
            version++
        }

        private fun card(name: String) = listOf(ContactProperty("FN", emptyMap(), name))
    }

    /**
     * The provider: raw contacts with their managed properties and flags.
     * [Row.version] moves on every change to its data, as the provider's
     * `VERSION` does.
     */
    private class FakeStore : ContactsStore {
        data class Row(
            var remote: String?,
            var etag: String?,
            var book: String?,
            var dirty: Boolean,
            var deleted: Boolean,
            var properties: List<ContactProperty>,
            /** A row the sync does not own, which a replace must keep. */
            var photo: Boolean = false,
            var version: Long = 1,
            var slug: String? = null,
        )

        val rows = linkedMapOf<Long, Row>()
        private var cursor = 0L
        private var next = 1L

        fun add(row: Row): Long {
            val id = next++
            rows[id] = row
            return id
        }

        fun local(name: String, dirty: Boolean = true): Long =
            add(Row(null, null, null, dirty, false, listOf(ContactProperty("FN", emptyMap(), name))))

        fun synced(contact: SyncedContact, dirty: Boolean = false, deleted: Boolean = false): Long =
            add(Row(contact.id, contact.etag, contact.book, dirty, deleted, contact.properties))

        fun byRemote(id: String): Row? = rows.values.firstOrNull { it.remote == id }

        /** An edit made in the phone's Contacts app. */
        fun edit(local: Long, name: String) {
            val row = rows.getValue(local)
            row.properties = listOf(ContactProperty("FN", emptyMap(), name))
            row.dirty = true
            row.version++
        }

        override fun version(): Long = cursor
        override fun version(value: Long) { cursor = value }
        override fun contacts(): List<StoredContact> =
            rows.map { (id, row) -> StoredContact(id, row.remote, row.etag, row.book, row.dirty, row.deleted, row.version, row.slug) }
        override fun properties(local: Long): List<ContactProperty> = rows.getValue(local).properties
        override fun insert(contact: SyncedContact) {
            add(Row(contact.id, contact.etag, contact.book, false, false, contact.properties))
        }
        override fun replace(local: Long, contact: SyncedContact) {
            val row = rows.getValue(local)
            row.remote = contact.id
            row.etag = contact.etag
            row.book = contact.book
            row.dirty = false
            row.properties = contact.properties
            row.version++
        }
        // A bind or a slug writes only the raw contact's own columns, which
        // leaves VERSION where it was; only a change to its data rows moves it.
        override fun bind(local: Long, contact: SyncedContact, version: Long?) {
            val row = rows.getValue(local)
            if (row.version == version) row.dirty = false
            row.remote = contact.id
            row.etag = contact.etag
            row.book = contact.book
        }
        override fun recreate(local: Long, contact: SyncedContact) {
            rows.remove(local)
            insert(contact)
        }
        override fun remove(local: Long) { rows.remove(local) }
        override fun slug(local: Long, value: String) { rows.getValue(local).slug = value }
    }

    private fun run(source: FakeSource, store: FakeStore): SyncOutcome =
        runBlocking { ContactsSyncCycle(source, store).run() }

    @Test
    fun `a first sync downloads everything and stores the cursor`() {
        val source = FakeSource()
        source.seed("a", "Ada")
        source.seed("b", "Bob")
        val store = FakeStore()

        val outcome = run(source, store)

        assertEquals(2, outcome.downloaded)
        assertEquals(setOf("a", "b"), store.rows.values.map { it.remote }.toSet())
        assertEquals("Ada", store.byRemote("a")!!.properties.single().value)
        assertEquals("e-a", store.byRemote("a")!!.etag)
        assertEquals(source.version, store.version())
        assertEquals("changes:0", source.calls.first())
    }

    @Test
    fun `new on the server arrives, changed is replaced, unchanged is left alone`() {
        val source = FakeSource()
        val a = source.seed("a", "Ada")
        val b = source.seed("b", "Bob")
        val store = FakeStore()
        val ada = store.synced(a)
        store.synced(b)
        store.rows.getValue(ada).photo = true
        store.version(5)
        // The server moved Ada on and added Cy.
        source.contacts["a"] = a.copy(etag = "e-a-2", properties = card("Ada L"))
        source.seed("c", "Cy")
        source.version = 9

        val outcome = run(source, store)

        assertEquals(2, outcome.downloaded)
        assertEquals("Ada L", store.rows.getValue(ada).properties.single().value)
        assertEquals("e-a-2", store.rows.getValue(ada).etag)
        assertTrue(store.rows.getValue(ada).photo)
        assertEquals("Cy", store.byRemote("c")!!.properties.single().value)
        assertEquals(9, store.version())
        assertEquals(listOf("changes:5", "fetch:a,b,c"), source.calls)
    }

    @Test
    fun `deleted on the server is removed from the phone`() {
        val source = FakeSource()
        val a = source.seed("a", "Ada")
        val store = FakeStore()
        store.synced(a)
        store.synced(SyncedContact("gone", "book", "e", card("Gone")))
        store.version(3)
        source.deleted.add("gone")

        val outcome = run(source, store)

        assertEquals(1, outcome.removed)
        assertNull(store.byRemote("gone"))
        assertEquals(1, store.rows.size)
    }

    @Test
    fun `a full listing prunes rows the server no longer lists`() {
        val source = FakeSource()
        val a = source.seed("a", "Ada")
        val store = FakeStore()
        store.synced(a)
        store.synced(SyncedContact("stale", "book", "e", card("Stale")))
        // A phone edit on a stale row still uploads rather than vanishing.
        store.synced(SyncedContact("edited", "book", "e", card("Edited")), dirty = true)

        run(source, store)

        assertNull(store.byRemote("stale"))
        // The edited one was missing on the server: server wins, it goes too.
        assertNull(store.byRemote("edited"))
        assertTrue(source.calls.contains("update:edited"))
    }

    @Test
    fun `a reset removes a clean row the listing leaves out`() {
        val source = FakeSource()
        val a = source.seed("a", "Ada")
        val store = FakeStore()
        store.synced(a)
        store.synced(SyncedContact("gone", "book", "e", card("Gone long ago")))
        store.version(5)
        source.pruned = true

        val outcome = run(source, store)

        assertNull(store.byRemote("gone"))
        assertEquals("Ada", store.byRemote("a")!!.properties.single().value)
        assertEquals(1, outcome.removed)
        assertEquals(source.version, store.version())
    }

    @Test
    fun `a reset keeps rows with phone changes for the upload`() {
        val source = FakeSource()
        val a = source.seed("a", "Ada")
        val store = FakeStore()
        store.synced(a)
        val made = store.local("Made on the phone")
        val edited = store.synced(SyncedContact("edited", "book", "e", card("Edited")), dirty = true)
        val dropped = store.synced(SyncedContact("dropped", "book", "e", card("Dropped")), deleted = true)
        store.version(5)
        source.pruned = true

        run(source, store)

        // The new contact went up rather than being taken for stale.
        assertEquals("new1", store.rows.getValue(made).remote)
        assertEquals("Made on the phone", source.contacts.getValue("new1").properties.single().value)
        // The edit and the delete were offered to the server, which no longer
        // has either contact: the server wins and both rows go.
        assertTrue(source.calls.contains("update:edited"))
        assertTrue(source.calls.contains("delete:dropped"))
        assertFalse(store.rows.containsKey(edited))
        assertFalse(store.rows.containsKey(dropped))
    }

    @Test
    fun `without a reset a row the changes do not mention stays`() {
        val source = FakeSource()
        val a = source.seed("a", "Ada")
        val store = FakeStore()
        store.synced(a)
        store.synced(SyncedContact("unmentioned", "book", "e", card("Unmentioned")))
        store.version(5)

        run(source, store)

        assertEquals("Unmentioned", store.byRemote("unmentioned")!!.properties.single().value)
    }

    @Test
    fun `a first sync against a server without the reset flag still prunes`() {
        val source = FakeSource()
        val a = source.seed("a", "Ada")
        val store = FakeStore()
        store.synced(a)
        store.synced(SyncedContact("stale", "book", "e", card("Stale")))
        source.legacy = true

        run(source, store)

        assertNull(store.byRemote("stale"))
        assertEquals("Ada", store.byRemote("a")!!.properties.single().value)
    }

    @Test
    fun `a contact created on the phone is uploaded and bound`() {
        val source = FakeSource()
        val store = FakeStore()
        val local = store.local("New Person")

        val outcome = run(source, store)

        assertEquals(1, outcome.uploaded)
        val row = store.rows.getValue(local)
        assertEquals("new1", row.remote)
        assertEquals("e-new1-1", row.etag)
        assertEquals("book", row.book)
        assertFalse(row.dirty)
        assertEquals("New Person", source.contacts.getValue("new1").properties.single().value)
        // The upload's own change is not fetched back as a download.
        assertEquals(0, outcome.downloaded)
    }

    /** A contact made on the phone whose create reached the server but whose answer did not. */
    private fun lost(source: FakeSource, store: FakeStore, name: String): Long {
        val local = store.local(name)
        source.lose = 1
        try {
            run(source, store)
            fail("expected the lost answer to propagate")
        } catch (_: IOException) {
        }
        assertEquals(setOf("new1"), source.contacts.keys)
        assertNull(store.rows.getValue(local).remote)
        assertTrue(store.rows.getValue(local).slug!!.startsWith("android-"))
        return local
    }

    @Test
    fun `a contact a lost create made downloads onto the phone's own row`() {
        val source = FakeSource()
        val store = FakeStore()
        val local = lost(source, store, "New Person")
        val slug = store.rows.getValue(local).slug

        val outcome = run(source, store)

        // Bound rather than added as a copy, and not created again.
        assertEquals(setOf(local), store.rows.keys)
        val row = store.rows.getValue(local)
        assertEquals("new1", row.remote)
        assertEquals("e-new1-1", row.etag)
        assertEquals("book", row.book)
        assertFalse(row.dirty)
        assertEquals(slug, row.slug)
        assertEquals(setOf("new1"), source.contacts.keys)
        assertEquals(listOf(slug), source.slugs)
        // The server holds the card the phone has: nothing more to send.
        assertFalse(source.calls.any { it.startsWith("update") })
        assertEquals(0, outcome.downloaded)
        assertEquals(1, outcome.uploaded)
    }

    @Test
    fun `a contact a lost create made is deleted on the server when the phone deleted it meanwhile`() {
        val source = FakeSource()
        val store = FakeStore()
        val local = lost(source, store, "Regretted")
        store.rows.getValue(local).deleted = true

        val outcome = run(source, store)

        assertTrue(store.rows.isEmpty())
        assertTrue(source.contacts.isEmpty())
        assertEquals(listOf("new1"), source.deleted)
        assertTrue(source.calls.contains("delete:new1"))
        assertEquals(1, source.slugs.size)
        assertEquals(1, outcome.removed)
    }

    @Test
    fun `an edit made on the phone after a lost create goes up as an update`() {
        val source = FakeSource()
        val store = FakeStore()
        val local = lost(source, store, "First draft")
        store.edit(local, "Second draft")

        run(source, store)

        assertEquals(setOf("new1"), source.contacts.keys)
        assertEquals("Second draft", source.contacts.getValue("new1").properties.single().value)
        assertEquals(1, source.slugs.size)
        assertTrue(source.calls.contains("update:new1"))
        assertEquals(setOf(local), store.rows.keys)
        val row = store.rows.getValue(local)
        assertEquals("new1", row.remote)
        assertEquals(source.contacts.getValue("new1").etag, row.etag)
        assertFalse(row.dirty)
    }

    @Test
    fun `a create that never reached the server is sent again under the same name`() {
        val source = FakeSource()
        val store = FakeStore()
        val local = store.local("New Person")
        source.drop = 1
        try {
            run(source, store)
            fail("expected the failure to propagate")
        } catch (_: IOException) {
        }
        assertTrue(source.contacts.isEmpty())
        val slug = store.rows.getValue(local).slug!!

        run(source, store)

        assertEquals(listOf(slug, slug), source.slugs)
        assertEquals(setOf("new1"), source.contacts.keys)
        assertEquals("new1", store.rows.getValue(local).remote)
        assertFalse(store.rows.getValue(local).dirty)
        assertEquals(1, store.rows.size)
    }

    @Test
    fun `each contact made on the phone gets a name of its own`() {
        val source = FakeSource()
        val store = FakeStore()
        val first = store.local("First")
        val second = store.local("Second")

        run(source, store)

        val one = store.rows.getValue(first)
        val two = store.rows.getValue(second)
        assertTrue(one.slug!!.startsWith("android-"))
        assertTrue(two.slug!!.startsWith("android-"))
        assertTrue(one.slug != two.slug)
        assertEquals(listOf(one.slug, two.slug), source.slugs)
        assertEquals(2, source.contacts.size)
        assertTrue(one.remote != two.remote)
        assertFalse(one.dirty)
        assertFalse(two.dirty)
    }

    @Test
    fun `a contact answered without its slug binds no row`() {
        val source = FakeSource()
        source.seed("c", "Unnamed", slug = "android-x")
        source.anonymous = true
        val store = FakeStore()
        val local = store.add(FakeStore.Row(null, null, null, true, true, card("Unnamed"), slug = "android-x"))
        store.version(1)

        run(source, store)

        // Nothing says the two are one contact: it arrives as its own row,
        // and the phone's deleted row goes without reaching the server.
        assertFalse(store.rows.containsKey(local))
        assertEquals("Unnamed", store.byRemote("c")!!.properties.single().value)
        assertTrue(source.contacts.containsKey("c"))
        assertFalse(source.calls.any { it.startsWith("delete") })
    }

    @Test
    fun `a dirty row is updated with its etag and rebound`() {
        val source = FakeSource()
        val a = source.seed("a", "Ada")
        val store = FakeStore()
        val local = store.synced(a, dirty = true)
        store.rows.getValue(local).properties = card("Ada Byron")
        store.version(1)

        val outcome = run(source, store)

        assertEquals(1, outcome.uploaded)
        assertEquals("Ada Byron", source.contacts.getValue("a").properties.single().value)
        assertEquals("e-a'", store.rows.getValue(local).etag)
        assertFalse(store.rows.getValue(local).dirty)
        // Dirty rows are not downloaded over before the upload resolves them.
        assertFalse(source.calls.any { it.startsWith("fetch") })
    }

    @Test
    fun `a deleted row is deleted on the server and removed`() {
        val source = FakeSource()
        val a = source.seed("a", "Ada")
        val store = FakeStore()
        store.synced(a, deleted = true)
        store.version(1)

        val outcome = run(source, store)

        assertEquals(1, outcome.removed)
        assertTrue(store.rows.isEmpty())
        assertFalse(source.contacts.containsKey("a"))
    }

    @Test
    fun `created and deleted on the phone between syncs never reaches the server`() {
        val source = FakeSource()
        val store = FakeStore()
        store.add(FakeStore.Row(null, null, null, true, true, card("Ghost")))

        run(source, store)

        assertTrue(store.rows.isEmpty())
        assertTrue(source.contacts.isEmpty())
        assertFalse(source.calls.contains("create"))
    }

    @Test
    fun `on a conflict the server wins and the phone's edit is dropped`() {
        val source = FakeSource()
        val a = source.seed("a", "Ada")
        val store = FakeStore()
        val local = store.synced(a, dirty = true)
        store.rows.getValue(local).properties = card("Phone edit")
        store.version(1)
        // The server moved on after the phone's copy was taken.
        source.contacts["a"] = a.copy(etag = "e-a-2", properties = card("Web edit"))

        val outcome = run(source, store)

        assertEquals(0, outcome.uploaded)
        assertEquals("Web edit", source.contacts.getValue("a").properties.single().value)
        val row = store.rows.getValue(local)
        assertEquals("Web edit", row.properties.single().value)
        assertEquals("e-a-2", row.etag)
        assertFalse(row.dirty)
    }

    @Test
    fun `a delete against a changed row is not applied and the row comes back`() {
        val source = FakeSource()
        val a = source.seed("a", "Ada")
        val store = FakeStore()
        val local = store.synced(a, deleted = true)
        store.version(1)
        source.contacts["a"] = a.copy(etag = "e-a-2", properties = card("Ada, updated"))

        run(source, store)

        assertTrue(source.contacts.containsKey("a"))
        // Written afresh rather than undeleted in place.
        assertFalse(store.rows.containsKey(local))
        val row = store.byRemote("a")!!
        assertFalse(row.deleted)
        assertFalse(row.dirty)
        assertEquals("Ada, updated", row.properties.single().value)
        assertEquals("e-a-2", row.etag)
        assertEquals(1, store.rows.size)
    }

    @Test
    fun `an edit made on the phone during the upload stays dirty for the next sync`() {
        val source = FakeSource()
        val a = source.seed("a", "Ada")
        val store = FakeStore()
        val local = store.synced(a, dirty = true)
        store.rows.getValue(local).properties = card("First edit")
        store.version(1)
        source.during = { store.edit(local, "Second edit") }

        run(source, store)

        val row = store.rows.getValue(local)
        assertEquals("First edit", source.contacts.getValue("a").properties.single().value)
        // The server's new etag is stored, so the next upload is not a conflict.
        assertEquals("e-a'", row.etag)
        assertTrue(row.dirty)

        run(source, store)

        assertEquals("Second edit", source.contacts.getValue("a").properties.single().value)
        assertFalse(store.rows.getValue(local).dirty)
    }

    @Test
    fun `a contact made on the phone survives a create the server answers with 404`() {
        val source = FakeSource()
        val store = FakeStore()
        val local = store.local("New Person")
        source.missing = true

        val outcome = run(source, store)

        assertTrue(store.rows.getValue(local).dirty)
        assertNull(store.rows.getValue(local).remote)
        assertEquals(1, outcome.failures.size)
    }

    @Test
    fun `a delete the server refuses is reported and the row kept`() {
        val source = FakeSource()
        val a = source.seed("a", "Ada")
        val store = FakeStore()
        val local = store.synced(a, deleted = true)
        store.version(1)
        source.refuse = "Not allowed"

        val outcome = run(source, store)

        assertEquals(listOf("Not allowed"), outcome.failures)
        assertTrue(store.rows.getValue(local).deleted)
        assertTrue(source.contacts.containsKey("a"))
    }

    @Test
    fun `a conflict on a row the server has since deleted removes it`() {
        val source = FakeSource()
        val a = source.seed("a", "Ada")
        val store = FakeStore()
        val local = store.synced(a, dirty = true)
        store.version(1)
        // Deleted on the server between the change listing and the upload.
        source.vanish = true

        val outcome = run(source, store)

        assertFalse(store.rows.containsKey(local))
        assertEquals(1, outcome.removed)
        assertEquals(listOf("changes:1", "update:a", "fetch:a"), source.calls)
    }

    @Test
    fun `a server delete wins over a phone edit not yet uploaded`() {
        val source = FakeSource()
        val a = source.seed("a", "Ada")
        val store = FakeStore()
        val local = store.synced(a, dirty = true)
        store.version(1)
        source.contacts.remove("a")
        source.deleted.add("a")

        val outcome = run(source, store)

        assertFalse(store.rows.containsKey(local))
        assertEquals(1, outcome.removed)
        assertFalse(source.calls.any { it.startsWith("update") })
    }

    @Test
    fun `a row the server refuses is reported and the rest still sync`() {
        val source = FakeSource()
        val a = source.seed("a", "Ada")
        val store = FakeStore()
        val refused = store.local("Refused")
        val edited = store.synced(a, dirty = true)
        store.version(1)
        source.refuse = "Contact name is required"

        val outcome = run(source, store)

        assertEquals(listOf("Contact name is required"), outcome.failures)
        assertTrue(store.rows.getValue(refused).dirty)
        assertFalse(store.rows.getValue(edited).dirty)
        assertEquals(1, outcome.uploaded)
    }

    @Test
    fun `a transport failure propagates with the dirty flags still set`() {
        val source = FakeSource()
        val store = FakeStore()
        val local = store.local("Pending")
        source.offline = true

        try {
            run(source, store)
            fail("expected the failure to propagate")
        } catch (_: IOException) {
        }
        assertTrue(store.rows.getValue(local).dirty)
        assertEquals(0, store.version())
    }

    @Test
    fun `an authorization failure propagates as itself`() {
        val source = FakeSource()
        val store = FakeStore()
        source.unauthorized = true

        try {
            run(source, store)
            fail("expected the failure to propagate")
        } catch (_: SyncAuthorization) {
        }
    }

    @Test
    fun `changed ids are fetched in batches`() {
        val source = FakeSource()
        repeat(ContactsSource.BATCH + 1) { source.seed("c$it", "Contact $it") }
        val store = FakeStore()

        val outcome = run(source, store)

        assertEquals(ContactsSource.BATCH + 1, outcome.downloaded)
        val fetches = source.calls.filter { it.startsWith("fetch:") }
        assertEquals(2, fetches.size)
        assertEquals(ContactsSource.BATCH, fetches[0].removePrefix("fetch:").split(",").size)
        assertEquals(1, fetches[1].removePrefix("fetch:").split(",").size)
    }
}
