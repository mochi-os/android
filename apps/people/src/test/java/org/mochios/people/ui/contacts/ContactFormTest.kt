// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.people.ui.contacts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mochios.people.model.ContactProperty

/**
 * The card-to-form mapping and back. A submission replaces every managed
 * property and must leave everything else alone, which on the client means
 * never sending it: the server keeps what it is not given.
 */
class ContactFormTest {

    private val card = listOf(
        ContactProperty("FN", emptyMap(), "Ada Lovelace"),
        ContactProperty("N", emptyMap(), "Lovelace;Ada;Augusta;Lady;OBE"),
        ContactProperty("NICKNAME", emptyMap(), "Ada"),
        ContactProperty("EMAIL", mapOf("TYPE" to listOf("home")), "ada@example.org"),
        ContactProperty("EMAIL", mapOf("TYPE" to listOf("work")), "a.lovelace@example.com"),
        ContactProperty("TEL", mapOf("TYPE" to listOf("mobile")), "+44 7700 900000"),
        ContactProperty(
            "ADR",
            mapOf("TYPE" to listOf("home")),
            "PO 12;Flat 3;12 St James's Square;London;Greater London;SW1Y 4LE;United Kingdom",
        ),
        ContactProperty("BDAY", emptyMap(), "1815-12-10"),
        ContactProperty("ORG", emptyMap(), "Analytical Engine"),
        ContactProperty("TITLE", emptyMap(), "Mathematician"),
        ContactProperty("URL", emptyMap(), "https://example.org/ada"),
        ContactProperty("NOTE", emptyMap(), "First programmer"),
        // Not managed by the editor: the server keeps these, so they must
        // never come back in a submission.
        ContactProperty("PHOTO", mapOf("MEDIATYPE" to listOf("image/jpeg")), "https://example.org/a.jpg"),
        ContactProperty("X-PHONE-WATCH", emptyMap(), "1"),
        ContactProperty("UID", emptyMap(), "server-owned"),
    )

    @Test
    fun `a card reads into the form, component by component`() {
        val form = contactForm(card, book = "book1")

        assertEquals("Ada Lovelace", form.name)
        assertEquals("Ada", form.given)
        assertEquals("Lovelace", form.family)
        assertEquals("Augusta", form.additional)
        assertEquals("Lady", form.prefix)
        assertEquals("OBE", form.suffix)
        assertEquals("Ada", form.nickname)
        assertEquals("book1", form.book)

        assertEquals(2, form.emails.size)
        assertEquals("ada@example.org", form.emails[0].value)
        assertEquals(TYPE_HOME, form.emails[0].type)
        assertEquals(TYPE_WORK, form.emails[1].type)

        assertEquals(1, form.phones.size)
        assertEquals(TYPE_MOBILE, form.phones[0].type)

        assertEquals(1, form.addresses.size)
        val address = form.addresses[0]
        assertEquals("PO 12", address.pobox)
        assertEquals("Flat 3", address.extended)
        assertEquals("12 St James's Square", address.street)
        assertEquals("London", address.city)
        assertEquals("Greater London", address.region)
        assertEquals("SW1Y 4LE", address.postcode)
        assertEquals("United Kingdom", address.country)
        assertEquals(TYPE_HOME, address.type)

        assertEquals("1815-12-10", form.birthday)
        assertEquals("Analytical Engine", form.organisation)
        assertEquals("Mathematician", form.title)
        assertEquals("https://example.org/ada", form.url.value)
        assertEquals("First programmer", form.note)
    }

    @Test
    fun `a submission carries the managed set and nothing else`() {
        val properties = contactForm(card).properties()
        val names = properties.map { it.name }

        assertTrue(names.all { it in MANAGED })
        assertTrue("PHOTO" !in names)
        assertTrue("X-PHONE-WATCH" !in names)
        assertTrue("UID" !in names)
    }

    @Test
    fun `components the editor has no field for survive a round trip`() {
        // The form shows given and family, not the additional name, the prefix
        // or the suffix; nor an address's post-office box. Rebuilding the card
        // from the form has to put them back or an edit quietly drops them.
        val rebuilt = contactForm(contactForm(card).properties())

        assertEquals("Augusta", rebuilt.additional)
        assertEquals("Lady", rebuilt.prefix)
        assertEquals("OBE", rebuilt.suffix)
        assertEquals("PO 12", rebuilt.addresses[0].pobox)
        assertEquals("Flat 3", rebuilt.addresses[0].extended)
    }

    @Test
    fun `parameters other than the type survive a round trip`() {
        val typed = listOf(
            ContactProperty("FN", emptyMap(), "Grace Hopper"),
            ContactProperty(
                "EMAIL",
                mapOf("TYPE" to listOf("work"), "PREF" to listOf("1")),
                "grace@example.mil",
            ),
        )
        val email = contactForm(typed).properties().first { it.name == "EMAIL" }

        assertEquals(listOf("work"), email.params["TYPE"])
        assertEquals(listOf("1"), email.params["PREF"])
    }

    @Test
    fun `the form is what the card becomes, both ways`() {
        val form = contactForm(card, book = "book1")
        assertEquals(form, contactForm(form.properties(), book = "book1"))
    }

    @Test
    fun `an edit replaces the managed property it touches`() {
        val edited = contactForm(card).copy(
            emails = listOf(TypedEntry(value = "ada@new.example", type = TYPE_WORK)),
        )
        val emails = edited.properties().filter { it.name == "EMAIL" }

        assertEquals(1, emails.size)
        assertEquals("ada@new.example", emails[0].value)
    }

    @Test
    fun `an empty repeated row is dropped rather than submitted`() {
        val form = ContactForm(
            name = "Ada",
            emails = listOf(TypedEntry(type = TYPE_HOME)),
            phones = listOf(TypedEntry(type = TYPE_MOBILE)),
            addresses = listOf(AddressEntry(type = TYPE_HOME)),
        )
        assertEquals(listOf("FN"), form.properties().map { it.name })
    }

    @Test
    fun `a name is required before the form may be saved`() {
        assertTrue(ContactForm(name = "Ada").valid)
        assertTrue(!ContactForm(name = "  ").valid)
    }

    @Test
    fun `a separator inside a component survives the round trip`() {
        val value = joinComponents(listOf("a;b", "c\\d", "e"))
        assertEquals("a\\;b;c\\\\d;e", value)
        assertEquals(listOf("a;b", "c\\d", "e"), splitComponents(value))
    }
}
