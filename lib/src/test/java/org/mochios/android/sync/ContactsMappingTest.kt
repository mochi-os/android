// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.sync

import android.provider.ContactsContract.CommonDataKinds.Email
import android.provider.ContactsContract.CommonDataKinds.Event
import android.provider.ContactsContract.CommonDataKinds.GroupMembership
import android.provider.ContactsContract.CommonDataKinds.Nickname
import android.provider.ContactsContract.CommonDataKinds.Note
import android.provider.ContactsContract.CommonDataKinds.Organization
import android.provider.ContactsContract.CommonDataKinds.Phone
import android.provider.ContactsContract.CommonDataKinds.Photo
import android.provider.ContactsContract.CommonDataKinds.StructuredName
import android.provider.ContactsContract.CommonDataKinds.StructuredPostal
import android.provider.ContactsContract.CommonDataKinds.Website
import android.provider.ContactsContract.Data
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The card-to-rows mapping and back. Only the managed set has a phone
 * representation; everything else stays on the server, so a round trip of
 * the managed properties must be exact and an unknown property must produce
 * no row.
 */
class ContactsMappingTest {

    private val card = listOf(
        ContactProperty("FN", emptyMap(), "Ada Lovelace"),
        ContactProperty("N", emptyMap(), "Lovelace;Ada;Augusta;Lady;OBE"),
        ContactProperty("NICKNAME", emptyMap(), "Ada"),
        ContactProperty("EMAIL", mapOf("TYPE" to listOf("home")), "ada@example.org"),
        ContactProperty("EMAIL", mapOf("TYPE" to listOf("work")), "a.lovelace@example.com"),
        ContactProperty("TEL", mapOf("TYPE" to listOf("mobile")), "+44 7700 900000"),
        ContactProperty("TEL", mapOf("TYPE" to listOf("work", "fax")), "+44 20 7946 0000"),
        ContactProperty(
            "ADR",
            mapOf("TYPE" to listOf("home")),
            "PO 12;Flat 3;12 St James's Square;London;Greater London;SW1Y 4LE;United Kingdom",
        ),
        ContactProperty("BDAY", emptyMap(), "1815-12-10"),
        ContactProperty("ORG", emptyMap(), "Analytical Engine;Programming"),
        ContactProperty("TITLE", emptyMap(), "Mathematician"),
        ContactProperty("URL", mapOf("TYPE" to listOf("work")), "https://example.org/ada"),
        ContactProperty("NOTE", emptyMap(), "First programmer"),
        // Not managed: no row on the phone, kept by the server.
        ContactProperty("PHOTO", mapOf("MEDIATYPE" to listOf("image/jpeg")), "https://example.org/a.jpg"),
        ContactProperty("X-PHONE-WATCH", emptyMap(), "1"),
        ContactProperty("X-MOCHI-PERSON", emptyMap(), "server-owned"),
    )

    private fun List<DataRow>.of(mimetype: String) = filter { it.mimetype == mimetype }

    /** The one property of [name]: a lone email or number also yields the fallback `FN`. */
    private fun List<ContactProperty>.named(name: String) = single { it.name == name }

    @Test
    fun `a card becomes one row per managed value and nothing for the rest`() {
        val rows = ContactsMapping.rows(card)

        val name = rows.of(StructuredName.CONTENT_ITEM_TYPE).single()
        assertEquals("Ada Lovelace", name.values[StructuredName.DISPLAY_NAME])
        assertEquals("Ada", name.values[StructuredName.GIVEN_NAME])
        assertEquals("Lovelace", name.values[StructuredName.FAMILY_NAME])
        assertEquals("Augusta", name.values[StructuredName.MIDDLE_NAME])
        assertEquals("Lady", name.values[StructuredName.PREFIX])
        assertEquals("OBE", name.values[StructuredName.SUFFIX])

        assertEquals("Ada", rows.of(Nickname.CONTENT_ITEM_TYPE).single().values[Nickname.NAME])

        val emails = rows.of(Email.CONTENT_ITEM_TYPE)
        assertEquals(2, emails.size)
        assertEquals("ada@example.org", emails[0].values[Email.ADDRESS])
        assertEquals(Email.TYPE_HOME.toString(), emails[0].values[Email.TYPE])
        assertEquals(Email.TYPE_WORK.toString(), emails[1].values[Email.TYPE])

        val phones = rows.of(Phone.CONTENT_ITEM_TYPE)
        assertEquals(Phone.TYPE_MOBILE.toString(), phones[0].values[Phone.TYPE])
        assertEquals(Phone.TYPE_FAX_WORK.toString(), phones[1].values[Phone.TYPE])

        val postal = rows.of(StructuredPostal.CONTENT_ITEM_TYPE).single()
        assertEquals("PO 12", postal.values[StructuredPostal.POBOX])
        assertEquals("Flat 3", postal.values[StructuredPostal.NEIGHBORHOOD])
        assertEquals("12 St James's Square", postal.values[StructuredPostal.STREET])
        assertEquals("London", postal.values[StructuredPostal.CITY])
        assertEquals("Greater London", postal.values[StructuredPostal.REGION])
        assertEquals("SW1Y 4LE", postal.values[StructuredPostal.POSTCODE])
        assertEquals("United Kingdom", postal.values[StructuredPostal.COUNTRY])
        assertEquals(StructuredPostal.TYPE_HOME.toString(), postal.values[StructuredPostal.TYPE])

        val birthday = rows.of(Event.CONTENT_ITEM_TYPE).single()
        assertEquals("1815-12-10", birthday.values[Event.START_DATE])
        assertEquals(Event.TYPE_BIRTHDAY.toString(), birthday.values[Event.TYPE])

        val organization = rows.of(Organization.CONTENT_ITEM_TYPE).single()
        assertEquals("Analytical Engine", organization.values[Organization.COMPANY])
        assertEquals("Programming", organization.values[Organization.DEPARTMENT])
        assertEquals("Mathematician", organization.values[Organization.TITLE])

        val website = rows.of(Website.CONTENT_ITEM_TYPE).single()
        assertEquals("https://example.org/ada", website.values[Website.URL])
        assertEquals(Website.TYPE_WORK.toString(), website.values[Website.TYPE])

        assertEquals("First programmer", rows.of(Note.CONTENT_ITEM_TYPE).single().values[Note.NOTE])

        // The unmanaged properties produced nothing.
        assertEquals(11, rows.size)
        assertTrue(rows.none { it.mimetype == Photo.CONTENT_ITEM_TYPE })
    }

    @Test
    fun `the managed properties survive a round trip exactly`() {
        val managed = card.filter { it.name in ContactsMapping.MANAGED }
        assertEquals(managed, ContactsMapping.properties(ContactsMapping.rows(card)))
    }

    @Test
    fun `rows of other kinds contribute no property`() {
        val rows = ContactsMapping.rows(card) + listOf(
            DataRow(Photo.CONTENT_ITEM_TYPE, mapOf(Photo.PHOTO to "bytes")),
            DataRow(GroupMembership.CONTENT_ITEM_TYPE, mapOf(GroupMembership.GROUP_ROW_ID to "4")),
            DataRow(Event.CONTENT_ITEM_TYPE, mapOf(
                Event.START_DATE to "2001-01-01",
                Event.TYPE to Event.TYPE_ANNIVERSARY.toString(),
            )),
        )
        val properties = ContactsMapping.properties(rows)
        assertEquals(card.filter { it.name in ContactsMapping.MANAGED }, properties)
        // One birthday, not the anniversary.
        assertEquals(listOf("1815-12-10"), properties.filter { it.name == "BDAY" }.map { it.value })
    }

    @Test
    fun `a download replaces only the rows it owns`() {
        assertTrue(ContactsMapping.managed(DataRow(Email.CONTENT_ITEM_TYPE, emptyMap())))
        assertTrue(ContactsMapping.managed(DataRow(Event.CONTENT_ITEM_TYPE, mapOf(Event.TYPE to Event.TYPE_BIRTHDAY.toString()))))
        assertFalse(ContactsMapping.managed(DataRow(Event.CONTENT_ITEM_TYPE, mapOf(Event.TYPE to Event.TYPE_ANNIVERSARY.toString()))))
        assertFalse(ContactsMapping.managed(DataRow(Photo.CONTENT_ITEM_TYPE, emptyMap())))
        assertTrue(ContactsMapping.managed(DataRow(Photo.CONTENT_ITEM_TYPE, mapOf(Data.SYNC1 to ContactsMapping.PHOTO_MARKER))))
        assertFalse(ContactsMapping.managed(DataRow(GroupMembership.CONTENT_ITEM_TYPE, emptyMap())))
    }

    @Test
    fun `TYPE tokens map to the provider's kinds and back`() {
        fun email(vararg types: String) =
            ContactsMapping.rows(listOf(ContactProperty("EMAIL", mapOf("TYPE" to types.toList()), "x@y.z"))).single()
        assertEquals(Email.TYPE_HOME.toString(), email("home").values[Email.TYPE])
        assertEquals(Email.TYPE_WORK.toString(), email("WORK").values[Email.TYPE])
        assertEquals(Email.TYPE_MOBILE.toString(), email("cell").values[Email.TYPE])
        assertEquals(Email.TYPE_OTHER.toString(), email("other").values[Email.TYPE])
        // vCard 3 noise is ignored.
        assertEquals(Email.TYPE_HOME.toString(), email("INTERNET", "pref", "home").values[Email.TYPE])
        // No type at all is "other" on the phone.
        assertEquals(Email.TYPE_OTHER.toString(), email().values[Email.TYPE])
        // An unknown type becomes a custom label that comes back as given.
        val custom = email("Assistant")
        assertEquals(Email.TYPE_CUSTOM.toString(), custom.values[Email.TYPE])
        assertEquals("Assistant", custom.values[Email.LABEL])
        assertEquals(
            listOf("Assistant"),
            ContactsMapping.properties(listOf(custom)).named("EMAIL").params["TYPE"],
        )

        fun phone(vararg types: String) =
            ContactsMapping.rows(listOf(ContactProperty("TEL", mapOf("TYPE" to types.toList()), "1"))).single()
        assertEquals(Phone.TYPE_MOBILE.toString(), phone("cell", "voice").values[Phone.TYPE])
        assertEquals(Phone.TYPE_FAX_HOME.toString(), phone("home", "fax").values[Phone.TYPE])
        assertEquals(Phone.TYPE_PAGER.toString(), phone("pager").values[Phone.TYPE])
        assertEquals(Phone.TYPE_WORK_MOBILE.toString(), phone("work", "mobile").values[Phone.TYPE])
        assertEquals(Phone.TYPE_OTHER.toString(), phone().values[Phone.TYPE])
        // Every phone kind has a card word and comes back as itself.
        for (kind in listOf(
            Phone.TYPE_HOME, Phone.TYPE_MOBILE, Phone.TYPE_WORK, Phone.TYPE_FAX_WORK, Phone.TYPE_FAX_HOME,
            Phone.TYPE_PAGER, Phone.TYPE_OTHER, Phone.TYPE_CALLBACK, Phone.TYPE_CAR, Phone.TYPE_COMPANY_MAIN,
            Phone.TYPE_ISDN, Phone.TYPE_MAIN, Phone.TYPE_OTHER_FAX, Phone.TYPE_RADIO, Phone.TYPE_TELEX,
            Phone.TYPE_TTY_TDD, Phone.TYPE_WORK_MOBILE, Phone.TYPE_WORK_PAGER, Phone.TYPE_ASSISTANT, Phone.TYPE_MMS,
        )) {
            val row = DataRow(Phone.CONTENT_ITEM_TYPE, mapOf(Phone.NUMBER to "1", Phone.TYPE to kind.toString()))
            val back = ContactsMapping.rows(ContactsMapping.properties(listOf(row))).of(Phone.CONTENT_ITEM_TYPE).single()
            assertEquals("kind $kind", kind.toString(), back.values[Phone.TYPE])
        }
        assertEquals(listOf("car"), ContactsMapping.properties(listOf(phone("car"))).named("TEL").params["TYPE"])
        // A custom phone label is kept as the phone wrote it.
        val labelled = DataRow(Phone.CONTENT_ITEM_TYPE, mapOf(
            Phone.NUMBER to "1",
            Phone.TYPE to Phone.TYPE_CUSTOM.toString(),
            Phone.LABEL to "Boat",
        ))
        assertEquals(listOf("Boat"), ContactsMapping.properties(listOf(labelled)).named("TEL").params["TYPE"])

        fun website(vararg types: String) =
            ContactsMapping.rows(listOf(ContactProperty("URL", mapOf("TYPE" to types.toList()), "https://x"))).single()
        assertEquals(Website.TYPE_HOMEPAGE.toString(), website().values[Website.TYPE])
        assertEquals(Website.TYPE_BLOG.toString(), website("blog").values[Website.TYPE])
        assertNull(ContactsMapping.properties(listOf(website())).single().params["TYPE"])
    }

    @Test
    fun `an address with only the formatted whole becomes the street`() {
        val row = DataRow(StructuredPostal.CONTENT_ITEM_TYPE, mapOf(
            StructuredPostal.FORMATTED_ADDRESS to "1 High Street\nTown",
            StructuredPostal.TYPE to StructuredPostal.TYPE_WORK.toString(),
        ))
        val property = ContactsMapping.properties(listOf(row)).single()
        assertEquals("ADR", property.name)
        assertEquals(";;1 High Street\\nTown;;;;", property.value)
        assertEquals(listOf("work"), property.params["TYPE"])
    }

    @Test
    fun `birthdays in the basic vCard forms become the provider's extended ones`() {
        assertEquals("1815-12-10", ContactsMapping.birthday("18151210"))
        assertEquals("--12-10", ContactsMapping.birthday("--1210"))
        assertEquals("1815-12-10", ContactsMapping.birthday("1815-12-10"))
        assertEquals("--12-10", ContactsMapping.birthday("--12-10"))
        assertEquals("1996-10-22", ContactsMapping.birthday("19961022T140000Z"))
        assertEquals("1996-10-22", ContactsMapping.birthday("1996-10-22T14:00:00"))
        // Text a card may carry passes through for the phone to show as it is.
        assertEquals("circa 1800", ContactsMapping.birthday("circa 1800"))
        // One birthday row even when a malformed card carries two.
        val rows = ContactsMapping.rows(listOf(
            ContactProperty("BDAY", emptyMap(), "1815-12-10"),
            ContactProperty("BDAY", emptyMap(), "1816-01-01"),
        ))
        assertEquals(listOf("1815-12-10"), rows.filter { it.mimetype == Event.CONTENT_ITEM_TYPE }.map { it.values[Event.START_DATE] })
    }

    @Test
    fun `a nameless phone contact still gets the name the server requires`() {
        val phone = listOf(DataRow(Phone.CONTENT_ITEM_TYPE, mapOf(Phone.NUMBER to "+1 555 0100", Phone.TYPE to "2")))
        assertEquals("+1 555 0100", ContactsMapping.properties(phone).first { it.name == "FN" }.value)

        val parts = listOf(DataRow(StructuredName.CONTENT_ITEM_TYPE, mapOf(
            StructuredName.GIVEN_NAME to "Grace",
            StructuredName.FAMILY_NAME to "Hopper",
        )))
        val properties = ContactsMapping.properties(parts)
        assertEquals("Grace Hopper", properties.first { it.name == "FN" }.value)
        assertEquals("Hopper;Grace;;;", properties.first { it.name == "N" }.value)

        val company = listOf(DataRow(Organization.CONTENT_ITEM_TYPE, mapOf(Organization.COMPANY to "Acme")))
        assertEquals("Acme", ContactsMapping.properties(company).first { it.name == "FN" }.value)

        assertTrue(ContactsMapping.properties(emptyList()).isEmpty())
    }

    @Test
    fun `a title without an organisation gets a row of its own`() {
        val rows = ContactsMapping.rows(listOf(ContactProperty("TITLE", emptyMap(), "Captain")))
        val organization = rows.single()
        assertEquals("Captain", organization.values[Organization.TITLE])
        assertNull(organization.values[Organization.COMPANY])
        val back = ContactsMapping.properties(rows)
        assertEquals(listOf(ContactProperty("TITLE", emptyMap(), "Captain")), back.filter { it.name == "TITLE" })
        assertTrue(back.none { it.name == "ORG" })
    }

    @Test
    fun `every nickname survives, not just the first`() {
        val card = listOf(
            ContactProperty("FN", emptyMap(), "Ada"),
            ContactProperty("NICKNAME", emptyMap(), "Countess"),
            ContactProperty("NICKNAME", emptyMap(), "Enchantress of Numbers"),
        )
        assertEquals(2, ContactsMapping.rows(card).count { it.mimetype == Nickname.CONTENT_ITEM_TYPE })
        assertEquals(card, ContactsMapping.properties(ContactsMapping.rows(card)))
    }

    @Test
    fun `organisations pair with titles in order and extra titles keep their own rows`() {
        val card = listOf(
            ContactProperty("FN", emptyMap(), "Ada"),
            ContactProperty("ORG", emptyMap(), "Analytical Engine;Programming;Loops"),
            ContactProperty("ORG", emptyMap(), "Royal Society"),
            ContactProperty("TITLE", emptyMap(), "Mathematician"),
            ContactProperty("TITLE", emptyMap(), "Fellow"),
            ContactProperty("TITLE", emptyMap(), "Writer"),
        )
        val rows = ContactsMapping.rows(card).filter { it.mimetype == Organization.CONTENT_ITEM_TYPE }
        assertEquals(3, rows.size)
        assertEquals("Analytical Engine", rows[0].values[Organization.COMPANY])
        assertEquals("Programming / Loops", rows[0].values[Organization.DEPARTMENT])
        assertEquals("Mathematician", rows[0].values[Organization.TITLE])
        assertEquals("Royal Society", rows[1].values[Organization.COMPANY])
        assertEquals("Fellow", rows[1].values[Organization.TITLE])
        assertNull(rows[2].values[Organization.COMPANY])
        assertEquals("Writer", rows[2].values[Organization.TITLE])
        assertEquals(card, ContactsMapping.properties(ContactsMapping.rows(card)))
    }

    @Test
    fun `a vCard 4 tel URI becomes the number`() {
        val row = ContactsMapping.rows(listOf(
            ContactProperty("TEL", mapOf("VALUE" to listOf("uri"), "TYPE" to listOf("cell")), "tel:+44-20-7946-0000"),
        )).single()
        assertEquals("+44-20-7946-0000", row.values[Phone.NUMBER])
        assertEquals(Phone.TYPE_MOBILE.toString(), row.values[Phone.TYPE])
    }

    @Test
    fun `address delivery classes are not taken for a kind`() {
        val row = ContactsMapping.rows(listOf(
            ContactProperty("ADR", mapOf("TYPE" to listOf("postal", "parcel", "work")), ";;1 High Street;Town;;;"),
        )).single()
        assertEquals(StructuredPostal.TYPE_WORK.toString(), row.values[StructuredPostal.TYPE])
    }

    @Test
    fun `a custom label is sent as a TYPE value the server accepts`() {
        val row = DataRow(Email.CONTENT_ITEM_TYPE, mapOf(
            Email.ADDRESS to "x@y.z",
            Email.TYPE to Email.TYPE_CUSTOM.toString(),
            Email.LABEL to "  Line one\nline two " + "x".repeat(80),
        ))
        val label = ContactsMapping.properties(listOf(row)).named("EMAIL").params.getValue("TYPE").single()
        assertTrue(label.length <= 64)
        assertFalse(label.contains('\n'))
        assertTrue(label.startsWith("Line one line two"))
    }

    @Test
    fun `an inline photo becomes a marked photo row and a linked one is not fetched`() {
        val bytes = byteArrayOf(1, 2, 3, 4, 5)
        val encoded = java.util.Base64.getEncoder().encodeToString(bytes)
        fun photo(vararg properties: ContactProperty) =
            ContactsMapping.rows(listOf(ContactProperty("FN", emptyMap(), "Ada")) + properties)
                .filter { it.mimetype == Photo.CONTENT_ITEM_TYPE }

        val three = photo(ContactProperty("PHOTO", mapOf("ENCODING" to listOf("b"), "TYPE" to listOf("JPEG")), encoded)).single()
        assertArrayEquals(bytes, three.blob)
        assertEquals(ContactsMapping.PHOTO_MARKER, three.values[Data.SYNC1])
        assertTrue(ContactsMapping.managed(three))

        val four = photo(ContactProperty("PHOTO", emptyMap(), "data:image/jpeg;base64,$encoded")).single()
        assertArrayEquals(bytes, four.blob)

        assertTrue(photo(ContactProperty("PHOTO", mapOf("VALUE" to listOf("uri")), "https://example.org/a.jpg")).isEmpty())
        assertTrue(photo(ContactProperty("PHOTO", mapOf("ENCODING" to listOf("b")), "not base64 !")).isEmpty())
        // The phone never sends a photo back: the server refuses one.
        assertTrue(ContactsMapping.properties(listOf(three)).none { it.name == "PHOTO" })
    }

    @Test
    fun `escaped separators inside a component survive`() {
        val property = ContactProperty("ADR", emptyMap(), joinComponents(listOf("", "", "1;2", "Town", "", "", "")))
        val row = ContactsMapping.rows(listOf(property)).single()
        assertEquals("1;2", row.values[StructuredPostal.STREET])
        assertEquals(property.value, ContactsMapping.properties(listOf(row)).single().value)
    }
}
