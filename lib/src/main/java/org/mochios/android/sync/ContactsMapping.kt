// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.sync

import android.provider.ContactsContract.CommonDataKinds.Email
import android.provider.ContactsContract.CommonDataKinds.Event
import android.provider.ContactsContract.CommonDataKinds.Nickname
import android.provider.ContactsContract.CommonDataKinds.Note
import android.provider.ContactsContract.CommonDataKinds.Organization
import android.provider.ContactsContract.CommonDataKinds.Phone
import android.provider.ContactsContract.CommonDataKinds.Photo
import android.provider.ContactsContract.CommonDataKinds.StructuredName
import android.provider.ContactsContract.CommonDataKinds.StructuredPostal
import android.provider.ContactsContract.CommonDataKinds.Website
import android.provider.ContactsContract.Data
import java.util.Base64

/**
 * The mapping between a card's managed properties and the `Data` rows of a
 * raw contact, in both directions. Only the managed set has a phone
 * representation; every other property lives on the server alone and
 * survives because an upload sends only what this file produces and the
 * server merges rather than replaces.
 *
 * `PHOTO` is the one exception, and only downwards: a picture the card
 * carries inline becomes the contact's photo, but the server accepts no
 * `PHOTO` from an upload, so a photo set on the phone stays on the phone.
 *
 * Column names are the framework's constants, so the rows this builds are
 * the ones the provider stores, and a row read back from the provider maps
 * with the same keys.
 */
object ContactsMapping {

    /** The vCard properties the phone holds, the same set the server lets a client write. */
    val MANAGED = setOf("FN", "N", "NICKNAME", "EMAIL", "TEL", "ADR", "BDAY", "ORG", "TITLE", "URL", "NOTE")

    /**
     * `data_sync1` of the photo row a download wrote. A later download
     * replaces only a marked photo; one the phone set is left alone.
     */
    const val PHOTO_MARKER = "mochi"

    /** The server refuses a parameter value longer than this. */
    private const val PARAMETER_MAXIMUM = 64

    /** Separates an `ORG`'s organisational units inside the phone's one department field. */
    private const val UNITS = " / "

    /**
     * The kinds a download replaces whole. An event is the sync's only as a
     * birthday and a photo only when marked, so those two are matched apart.
     */
    private val KINDS = setOf(
        StructuredName.CONTENT_ITEM_TYPE,
        Nickname.CONTENT_ITEM_TYPE,
        Email.CONTENT_ITEM_TYPE,
        Phone.CONTENT_ITEM_TYPE,
        StructuredPostal.CONTENT_ITEM_TYPE,
        Organization.CONTENT_ITEM_TYPE,
        Note.CONTENT_ITEM_TYPE,
        Website.CONTENT_ITEM_TYPE,
    )

    /**
     * Whether a `Data` row is one the sync owns. A download replaces exactly
     * these rows and leaves the rest — a photo set on the phone, a group, an
     * anniversary, an instant-messaging handle — as the phone had them.
     */
    fun managed(row: DataRow): Boolean = when (row.mimetype) {
        Event.CONTENT_ITEM_TYPE -> row.values[Event.TYPE] == Event.TYPE_BIRTHDAY.toString()
        Photo.CONTENT_ITEM_TYPE -> row.values[Data.SYNC1] == PHOTO_MARKER
        else -> row.mimetype in KINDS
    }

    /** The kinds a download deletes whole before writing its rows afresh. */
    fun kinds(): Set<String> = KINDS

    /** The kinds an upload reads properties from. */
    fun readable(): Set<String> = KINDS + Event.CONTENT_ITEM_TYPE

    // ---- card -> provider ----

    /** The `Data` rows for a card's managed properties and inline photo; other properties are skipped. */
    fun rows(properties: List<ContactProperty>): List<DataRow> {
        val out = mutableListOf<DataRow>()
        nameRow(properties)?.let(out::add)
        var born = false
        for (property in properties) {
            when (property.name.uppercase()) {
                "NICKNAME" -> if (property.value.isNotBlank()) {
                    out.add(DataRow(Nickname.CONTENT_ITEM_TYPE, mapOf(
                        Nickname.NAME to property.value,
                        Nickname.TYPE to Nickname.TYPE_DEFAULT.toString(),
                    )))
                }
                "EMAIL" -> if (property.value.isNotBlank()) {
                    out.add(DataRow(Email.CONTENT_ITEM_TYPE, mapOf(Email.ADDRESS to property.value) + emailType(property)))
                }
                "TEL" -> {
                    val number = number(property.value)
                    if (number.isNotBlank()) {
                        out.add(DataRow(Phone.CONTENT_ITEM_TYPE, mapOf(Phone.NUMBER to number) + phoneType(property)))
                    }
                }
                "ADR" -> postalRow(property)?.let(out::add)
                // A card has one birthday; a second would be a malformed card
                // and the phone has room for one.
                "BDAY" -> if (property.value.isNotBlank() && !born) {
                    born = true
                    out.add(DataRow(Event.CONTENT_ITEM_TYPE, mapOf(
                        Event.START_DATE to birthday(property.value),
                        Event.TYPE to Event.TYPE_BIRTHDAY.toString(),
                    )))
                }
                "URL" -> if (property.value.isNotBlank()) {
                    out.add(DataRow(Website.CONTENT_ITEM_TYPE, mapOf(Website.URL to property.value) + websiteType(property)))
                }
                "NOTE" -> if (property.value.isNotBlank()) {
                    out.add(DataRow(Note.CONTENT_ITEM_TYPE, mapOf(Note.NOTE to property.value)))
                }
            }
        }
        out.addAll(organizationRows(properties))
        photo(properties)?.let { bytes ->
            out.add(DataRow(Photo.CONTENT_ITEM_TYPE, mapOf(Data.SYNC1 to PHOTO_MARKER), bytes))
        }
        return out
    }

    private fun nameRow(properties: List<ContactProperty>): DataRow? {
        val display = properties.firstOrNull { it.name.equals("FN", true) }?.value.orEmpty()
        val parts = properties.firstOrNull { it.name.equals("N", true) }?.value
            ?.let(::splitComponents).orEmpty()
        val values = mapOf(
            StructuredName.DISPLAY_NAME to display,
            StructuredName.FAMILY_NAME to parts.component(0),
            StructuredName.GIVEN_NAME to parts.component(1),
            StructuredName.MIDDLE_NAME to parts.component(2),
            StructuredName.PREFIX to parts.component(3),
            StructuredName.SUFFIX to parts.component(4),
        )
        if (values.values.all { it.isBlank() }) return null
        return DataRow(StructuredName.CONTENT_ITEM_TYPE, values.filterValues { it.isNotBlank() })
    }

    private fun postalRow(property: ContactProperty): DataRow? {
        val parts = splitComponents(property.value)
        val values = mapOf(
            StructuredPostal.POBOX to parts.component(0),
            StructuredPostal.NEIGHBORHOOD to parts.component(1),
            StructuredPostal.STREET to parts.component(2),
            StructuredPostal.CITY to parts.component(3),
            StructuredPostal.REGION to parts.component(4),
            StructuredPostal.POSTCODE to parts.component(5),
            StructuredPostal.COUNTRY to parts.component(6),
        ).filterValues { it.isNotBlank() }
        if (values.isEmpty()) return null
        return DataRow(StructuredPostal.CONTENT_ITEM_TYPE, values + postalType(property))
    }

    /**
     * One row per `ORG`, paired in order with the `TITLE`s: the phone keeps
     * an organisation and a job title together, the card keeps them apart.
     * Titles beyond the organisations get rows of their own. An `ORG`'s
     * organisational units share the one department field.
     */
    private fun organizationRows(properties: List<ContactProperty>): List<DataRow> {
        val organizations = properties.filter { it.name.equals("ORG", true) && it.value.isNotBlank() }
        val titles = properties.filter { it.name.equals("TITLE", true) && it.value.isNotBlank() }.map { it.value }
        return (0 until maxOf(organizations.size, titles.size)).map { index ->
            val values = mutableMapOf(Organization.TYPE to Organization.TYPE_WORK.toString())
            organizations.getOrNull(index)?.let { organization ->
                val parts = splitComponents(organization.value)
                if (parts.component(0).isNotBlank()) values[Organization.COMPANY] = parts.component(0)
                val units = parts.drop(1).filter { it.isNotBlank() }
                if (units.isNotEmpty()) values[Organization.DEPARTMENT] = units.joinToString(UNITS)
            }
            titles.getOrNull(index)?.let { values[Organization.TITLE] = it }
            DataRow(Organization.CONTENT_ITEM_TYPE, values)
        }
    }

    /**
     * The bytes of the card's first `PHOTO` that carries its picture inline,
     * as vCard 3 `ENCODING=b` or a vCard 4 `data:` URI. A photo given by URL
     * is not fetched: following a link a card names would tell its host when
     * this phone synced.
     */
    fun photo(properties: List<ContactProperty>): ByteArray? {
        for (property in properties) {
            if (!property.name.equals("PHOTO", true)) continue
            val value = property.value.trim()
            val encoded = when {
                value.startsWith("data:", true) -> {
                    val comma = value.indexOf(',')
                    if (comma < 0 || !value.substring(0, comma).endsWith(";base64", true)) continue
                    value.substring(comma + 1)
                }
                parameter(property, "ENCODING").any { it.equals("b", true) || it.equals("base64", true) } -> value
                else -> continue
            }
            val bytes = try {
                Base64.getMimeDecoder().decode(encoded)
            } catch (_: IllegalArgumentException) {
                continue
            }
            if (bytes.isNotEmpty()) return bytes
        }
        return null
    }

    // ---- provider -> card ----

    /**
     * The managed properties a raw contact's rows express. Rows of other kinds
     * contribute nothing. `FN` falls back through the name parts, nickname,
     * organisation, email and phone so a contact the phone saved with only a
     * number still has the name the server requires.
     */
    fun properties(rows: List<DataRow>): List<ContactProperty> {
        val out = mutableListOf<ContactProperty>()
        val name = rows.firstOrNull { it.mimetype == StructuredName.CONTENT_ITEM_TYPE }
        val parts = listOf(
            name?.get(StructuredName.FAMILY_NAME),
            name?.get(StructuredName.GIVEN_NAME),
            name?.get(StructuredName.MIDDLE_NAME),
            name?.get(StructuredName.PREFIX),
            name?.get(StructuredName.SUFFIX),
        ).map { it.orEmpty() }
        val nicknames = rows.filter { it.mimetype == Nickname.CONTENT_ITEM_TYPE }
            .map { it.get(Nickname.NAME) }.filter { it.isNotBlank() }
        val organizations = rows.filter { it.mimetype == Organization.CONTENT_ITEM_TYPE }
        val emails = rows.filter { it.mimetype == Email.CONTENT_ITEM_TYPE }.filter { it.get(Email.ADDRESS).isNotBlank() }
        val phones = rows.filter { it.mimetype == Phone.CONTENT_ITEM_TYPE }.filter { it.get(Phone.NUMBER).isNotBlank() }

        val display = name?.get(StructuredName.DISPLAY_NAME).orEmpty().ifBlank {
            listOf(parts[3], parts[1], parts[2], parts[0], parts[4]).filter { it.isNotBlank() }.joinToString(" ")
        }.ifBlank { nicknames.firstOrNull().orEmpty() }
            .ifBlank { organizations.firstOrNull()?.get(Organization.COMPANY).orEmpty() }
            .ifBlank { emails.firstOrNull()?.get(Email.ADDRESS).orEmpty() }
            .ifBlank { phones.firstOrNull()?.get(Phone.NUMBER).orEmpty() }
        if (display.isNotBlank()) out.add(ContactProperty("FN", emptyMap(), display))
        if (parts.any { it.isNotBlank() }) out.add(ContactProperty("N", emptyMap(), joinComponents(parts)))
        for (nickname in nicknames) out.add(ContactProperty("NICKNAME", emptyMap(), nickname))
        for (row in emails) {
            out.add(ContactProperty("EMAIL", typeParameter(emailLabels(row)), row.get(Email.ADDRESS)))
        }
        for (row in phones) {
            out.add(ContactProperty("TEL", typeParameter(phoneLabels(row)), row.get(Phone.NUMBER)))
        }
        for (row in rows.filter { it.mimetype == StructuredPostal.CONTENT_ITEM_TYPE }) {
            val components = listOf(
                row.get(StructuredPostal.POBOX),
                row.get(StructuredPostal.NEIGHBORHOOD),
                row.get(StructuredPostal.STREET),
                row.get(StructuredPostal.CITY),
                row.get(StructuredPostal.REGION),
                row.get(StructuredPostal.POSTCODE),
                row.get(StructuredPostal.COUNTRY),
            )
            val value = if (components.all { it.isBlank() }) {
                // The provider splits a formatted address into components on
                // write, so this is the rare row that has only the whole.
                val formatted = row.get(StructuredPostal.FORMATTED_ADDRESS)
                if (formatted.isBlank()) continue
                joinComponents(listOf("", "", formatted, "", "", "", ""))
            } else {
                joinComponents(components)
            }
            out.add(ContactProperty("ADR", typeParameter(postalLabels(row)), value))
        }
        val birthday = rows.firstOrNull {
            it.mimetype == Event.CONTENT_ITEM_TYPE && it.get(Event.TYPE) == Event.TYPE_BIRTHDAY.toString()
        }?.get(Event.START_DATE).orEmpty()
        if (birthday.isNotBlank()) out.add(ContactProperty("BDAY", emptyMap(), birthday))
        for (row in organizations) {
            val company = row.get(Organization.COMPANY)
            val units = row.get(Organization.DEPARTMENT).split(UNITS).filter { it.isNotBlank() }
            if (company.isNotBlank() || units.isNotEmpty()) {
                out.add(ContactProperty("ORG", emptyMap(), joinComponents(listOf(company) + units)))
            }
        }
        for (row in organizations) {
            val title = row.get(Organization.TITLE)
            if (title.isNotBlank()) out.add(ContactProperty("TITLE", emptyMap(), title))
        }
        for (row in rows.filter { it.mimetype == Website.CONTENT_ITEM_TYPE }) {
            val url = row.get(Website.URL)
            if (url.isNotBlank()) out.add(ContactProperty("URL", typeParameter(websiteLabels(row)), url))
        }
        for (row in rows.filter { it.mimetype == Note.CONTENT_ITEM_TYPE }) {
            val note = row.get(Note.NOTE)
            if (note.isNotBlank()) out.add(ContactProperty("NOTE", emptyMap(), note))
        }
        return out
    }

    // ---- TYPE parameter <-> provider type ----

    private const val HOME = "home"
    private const val WORK = "work"
    private const val MOBILE = "mobile"
    private const val OTHER = "other"
    private const val FAX = "fax"
    private const val PAGER = "pager"

    /**
     * `TYPE` tokens that say how a value may be used rather than which kind
     * it is: preference, the address's delivery class, a number's features.
     */
    private val NOISE = setOf(
        "pref", "internet", "x400", "voice", "text", "video", "msg", "bbs", "modem",
        "dom", "intl", "postal", "parcel",
    )

    /**
     * The phone kinds with a card word of their own, both ways. The fax,
     * pager and work-mobile kinds carry two words and are matched apart.
     */
    private val PHONE_KINDS = mapOf(
        Phone.TYPE_HOME to HOME,
        Phone.TYPE_WORK to WORK,
        Phone.TYPE_MOBILE to MOBILE,
        Phone.TYPE_OTHER to OTHER,
        Phone.TYPE_PAGER to PAGER,
        Phone.TYPE_OTHER_FAX to FAX,
        Phone.TYPE_CAR to "car",
        Phone.TYPE_ISDN to "isdn",
        Phone.TYPE_MAIN to "main",
        Phone.TYPE_CALLBACK to "callback",
        Phone.TYPE_COMPANY_MAIN to "company",
        Phone.TYPE_RADIO to "radio",
        Phone.TYPE_TELEX to "telex",
        Phone.TYPE_TTY_TDD to "textphone",
        Phone.TYPE_ASSISTANT to "assistant",
        Phone.TYPE_MMS to "mms",
    )

    /** A parameter's values, whatever the case of its name. */
    private fun parameter(property: ContactProperty, name: String): List<String> =
        property.params.entries.firstOrNull { it.key.equals(name, true) }?.value.orEmpty()

    /** The `TYPE` tokens of a property as written, without the noise. */
    private fun tokens(property: ContactProperty): List<String> =
        parameter(property, "TYPE").map { it.trim() }.filter { it.isNotBlank() && it.lowercase() !in NOISE }

    private fun typeParameter(labels: List<String>): Map<String, List<String>> =
        if (labels.isEmpty()) emptyMap() else mapOf("TYPE" to labels)

    private fun emailType(property: ContactProperty): Map<String, String?> {
        val tokens = tokens(property)
        val known = tokens.firstNotNullOfOrNull { token ->
            when (token.lowercase()) {
                HOME -> Email.TYPE_HOME
                WORK -> Email.TYPE_WORK
                MOBILE, "cell" -> Email.TYPE_MOBILE
                OTHER -> Email.TYPE_OTHER
                else -> null
            }
        }
        return typed(known, tokens, Email.TYPE_OTHER)
    }

    private fun emailLabels(row: DataRow): List<String> = when (row.get(Email.TYPE).toIntOrNull()) {
        Email.TYPE_HOME -> listOf(HOME)
        Email.TYPE_WORK -> listOf(WORK)
        Email.TYPE_MOBILE -> listOf(MOBILE)
        Email.TYPE_OTHER -> listOf(OTHER)
        Email.TYPE_CUSTOM -> custom(row)
        else -> emptyList()
    }

    private fun phoneType(property: ContactProperty): Map<String, String?> {
        val tokens = tokens(property)
        val words = tokens.map { it.lowercase() }.toSet()
        val mobile = MOBILE in words || "cell" in words
        val known = when {
            FAX in words && WORK in words -> Phone.TYPE_FAX_WORK
            FAX in words && HOME in words -> Phone.TYPE_FAX_HOME
            PAGER in words && WORK in words -> Phone.TYPE_WORK_PAGER
            mobile && WORK in words -> Phone.TYPE_WORK_MOBILE
            mobile -> Phone.TYPE_MOBILE
            else -> PHONE_KINDS.entries.firstOrNull { it.value in words }?.key
        }
        return typed(known, tokens, Phone.TYPE_OTHER)
    }

    private fun phoneLabels(row: DataRow): List<String> {
        val type = row.get(Phone.TYPE).toIntOrNull()
        return when (type) {
            Phone.TYPE_FAX_HOME -> listOf(HOME, FAX)
            Phone.TYPE_FAX_WORK -> listOf(WORK, FAX)
            Phone.TYPE_WORK_PAGER -> listOf(WORK, PAGER)
            Phone.TYPE_WORK_MOBILE -> listOf(WORK, MOBILE)
            Phone.TYPE_CUSTOM -> custom(row)
            else -> listOfNotNull(type?.let { PHONE_KINDS[it] })
        }
    }

    private fun postalType(property: ContactProperty): Map<String, String?> {
        val tokens = tokens(property)
        val known = tokens.firstNotNullOfOrNull { token ->
            when (token.lowercase()) {
                HOME -> StructuredPostal.TYPE_HOME
                WORK -> StructuredPostal.TYPE_WORK
                OTHER -> StructuredPostal.TYPE_OTHER
                else -> null
            }
        }
        return typed(known, tokens, StructuredPostal.TYPE_OTHER)
    }

    private fun postalLabels(row: DataRow): List<String> = when (row.get(StructuredPostal.TYPE).toIntOrNull()) {
        StructuredPostal.TYPE_HOME -> listOf(HOME)
        StructuredPostal.TYPE_WORK -> listOf(WORK)
        StructuredPostal.TYPE_OTHER -> listOf(OTHER)
        StructuredPostal.TYPE_CUSTOM -> custom(row)
        else -> emptyList()
    }

    private val WEBSITE_KINDS = mapOf(
        Website.TYPE_HOME to HOME,
        Website.TYPE_WORK to WORK,
        Website.TYPE_OTHER to OTHER,
        Website.TYPE_BLOG to "blog",
        Website.TYPE_PROFILE to "profile",
        Website.TYPE_FTP to "ftp",
    )

    private fun websiteType(property: ContactProperty): Map<String, String?> {
        val tokens = tokens(property)
        // A website with no type is the person's home page, which is what the
        // phone shows for the default kind.
        if (tokens.isEmpty()) return mapOf(Website.TYPE to Website.TYPE_HOMEPAGE.toString())
        val words = tokens.map { it.lowercase() }
        val known = WEBSITE_KINDS.entries.firstOrNull { it.value in words }?.key
        return typed(known, tokens, Website.TYPE_OTHER)
    }

    private fun websiteLabels(row: DataRow): List<String> {
        val type = row.get(Website.TYPE).toIntOrNull()
        return if (type == Website.TYPE_CUSTOM) custom(row) else listOfNotNull(type?.let { WEBSITE_KINDS[it] })
    }

    /**
     * The provider's `TYPE` and `LABEL` for a known kind, or a custom kind
     * carrying the first token as its label; no token at all is the kind's
     * own [other], whose number differs between kinds. Every kind shares the
     * same two column names and custom number, so one helper serves all.
     */
    private fun typed(known: Int?, tokens: List<String>, other: Int): Map<String, String?> = when {
        known != null -> mapOf(Email.TYPE to known.toString())
        tokens.isNotEmpty() -> mapOf(Email.TYPE to Email.TYPE_CUSTOM.toString(), Email.LABEL to tokens.first())
        else -> mapOf(Email.TYPE to other.toString())
    }

    /** A custom kind's label as a `TYPE` value the server accepts: one line, bounded. */
    private fun custom(row: DataRow): List<String> {
        val label = row.get(Email.LABEL).replace(Regex("\\s+"), " ").trim().take(PARAMETER_MAXIMUM).trim()
        return if (label.isBlank()) emptyList() else listOf(label)
    }

    /** A vCard 4 `tel:` URI's number; any other value as it is. */
    private fun number(value: String): String {
        val trimmed = value.trim()
        return if (trimmed.startsWith("tel:", true)) trimmed.substring(4) else trimmed
    }

    /**
     * A vCard date in the provider's form: the basic `YYYYMMDD` and `--MMDD`
     * become `YYYY-MM-DD` and `--MM-DD`, which the Contacts app reads, and a
     * date-time keeps its date. Anything else — text, a partial date —
     * passes through.
     */
    fun birthday(value: String): String {
        val trimmed = value.trim()
        val date = trimmed.substringBefore('T')
        return when {
            Regex("\\d{8}").matches(date) ->
                "${date.substring(0, 4)}-${date.substring(4, 6)}-${date.substring(6, 8)}"
            Regex("--\\d{4}").matches(date) -> "--${date.substring(2, 4)}-${date.substring(4, 6)}"
            Regex("\\d{4}-\\d{2}-\\d{2}|--\\d{2}-\\d{2}").matches(date) -> date
            else -> trimmed
        }
    }

    private fun DataRow.get(column: String): String = values[column].orEmpty()

    private fun List<String>.component(index: Int): String = getOrNull(index).orEmpty()
}
