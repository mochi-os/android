// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.people.ui.contacts

import org.mochios.android.sync.ContactProperty
import org.mochios.android.sync.joinComponents
import org.mochios.android.sync.splitComponents

/**
 * The editor's view of a contact's card, and the mapping between the two.
 *
 * A card is a lossless vCard property list. The editor manages one subset of
 * it — [MANAGED] — and a submission replaces exactly those properties, so
 * everything else a phone or another client stored (photos, X- properties,
 * IMPP, anything) is left alone by never being sent. Inside a managed
 * property the same rule holds component by component: the parts the form has
 * no field for, such as `N`'s prefix or `ADR`'s post-office box, are carried
 * on the form and written back unchanged.
 *
 * Property names and their component order are vCard protocol tokens, so they
 * keep their vCard spelling rather than the project's naming rules.
 */
val MANAGED = listOf(
    "FN", "N", "NICKNAME", "EMAIL", "TEL", "ADR", "BDAY", "ORG", "TITLE", "URL", "NOTE",
)

const val TYPE_HOME = "home"
const val TYPE_WORK = "work"
const val TYPE_MOBILE = "mobile"
const val TYPE_OTHER = "other"

/** The types each kind of value may carry, in the order its picker lists them. */
val EMAIL_TYPES = listOf(TYPE_HOME, TYPE_WORK, TYPE_OTHER)
val PHONE_TYPES = listOf(TYPE_MOBILE, TYPE_HOME, TYPE_WORK, TYPE_OTHER)
val ADDRESS_TYPES = listOf(TYPE_HOME, TYPE_WORK, TYPE_OTHER)

/**
 * One email address, phone number or link. [type] is the editor's choice, one
 * of the kind's types; [params] are every parameter the property came with,
 * `TYPE` included, so its other type values (pref, voice, fax) survive a save.
 * [group] ties it to its siblings, as `item1.EMAIL` to the `item1.X-ABLabel`
 * that names it.
 */
data class TypedEntry(
    val value: String = "",
    val type: String = "",
    val params: Map<String, List<String>> = emptyMap(),
    val group: String? = null,
)

/**
 * One postal address. [pobox] and [extended] have no field in the editor and
 * are kept only so a card that carries them survives a save.
 */
data class AddressEntry(
    val street: String = "",
    val city: String = "",
    val region: String = "",
    val postcode: String = "",
    val country: String = "",
    val type: String = "",
    val pobox: String = "",
    val extended: String = "",
    val params: Map<String, List<String>> = emptyMap(),
    val group: String? = null,
) {
    val empty: Boolean
        get() = listOf(street, city, region, postcode, country, pobox, extended)
            .all { it.isBlank() }
}

/**
 * A single-valued property as the card held it: its parameters and group, its
 * value as the form writes it back when left alone, and the value as the card
 * wrote it.
 */
data class Original(
    val params: Map<String, List<String>> = emptyMap(),
    val group: String? = null,
    val value: String = "",
    val raw: String = value,
)

/** The editable state of a contact. [name] is `FN` and is required. */
data class ContactForm(
    val name: String = "",
    val given: String = "",
    val family: String = "",
    val additional: String = "",
    val prefix: String = "",
    val suffix: String = "",
    val nickname: String = "",
    val emails: List<TypedEntry> = emptyList(),
    val phones: List<TypedEntry> = emptyList(),
    val addresses: List<AddressEntry> = emptyList(),
    val birthday: String = "",
    val organisation: String = "",
    val title: String = "",
    val url: TypedEntry = TypedEntry(),
    val note: String = "",
    val book: String = "",
    /**
     * Every instance past the first of a property the form has one field
     * for, kept for the round trip: a card may hold two URLs or NOTEs, and a
     * save replaces all of them.
     */
    val extras: List<ContactProperty> = emptyList(),
    /**
     * The first instance of each property the form has one field for, by
     * name, so its parameters and group survive a save (LANGUAGE on FN,
     * SORT-AS on N, Apple's X-APPLE-OMIT-YEAR on a birthday without its year).
     */
    val originals: Map<String, Original> = emptyMap(),
    /** Whether the birthday's fields hold something that is not a day of the year. */
    val birthdayInvalid: Boolean = false,
) {
    val valid: Boolean
        get() = name.isNotBlank() && !birthdayInvalid
}

/** The properties the form shows one instance of. */
private val SINGLE = setOf("FN", "N", "NICKNAME", "BDAY", "ORG", "TITLE", "URL", "NOTE")

/** The form a card reads as, with [book] the address book it sits in. */
fun contactForm(card: List<ContactProperty>?, book: String = ""): ContactForm {
    var form = ContactForm(book = book)
    val emails = mutableListOf<TypedEntry>()
    val phones = mutableListOf<TypedEntry>()
    val addresses = mutableListOf<AddressEntry>()
    val extras = mutableListOf<ContactProperty>()
    val seen = mutableSetOf<String>()
    for (property in card.orEmpty()) {
        val name = property.name.uppercase()
        if (name in SINGLE && !seen.add(name)) {
            extras.add(property)
            continue
        }
        when (name) {
            "FN" -> form = form.copy(name = property.value)
            "N" -> {
                val parts = splitComponents(property.value)
                form = form.copy(
                    family = parts.component(0),
                    given = parts.component(1),
                    additional = parts.component(2),
                    prefix = parts.component(3),
                    suffix = parts.component(4),
                )
            }
            "NICKNAME" -> form = form.copy(nickname = property.value)
            "EMAIL" -> emails.add(typedEntry(property, EMAIL_TYPES))
            "TEL" -> phones.add(typedEntry(property, PHONE_TYPES))
            "ADR" -> {
                val parts = splitComponents(property.value)
                addresses.add(
                    AddressEntry(
                        pobox = parts.component(0),
                        extended = parts.component(1),
                        street = parts.component(2),
                        city = parts.component(3),
                        region = parts.component(4),
                        postcode = parts.component(5),
                        country = parts.component(6),
                        type = typeOf(property.params, ADDRESS_TYPES),
                        params = property.params,
                        group = property.group,
                    )
                )
            }
            "BDAY" -> form = form.copy(birthday = birthday(property))
            "ORG" -> form = form.copy(organisation = property.value)
            "TITLE" -> form = form.copy(title = property.value)
            "URL" -> form = form.copy(url = TypedEntry(value = property.value))
            "NOTE" -> form = form.copy(note = property.value)
        }
    }
    form = form.copy(emails = emails, phones = phones, addresses = addresses, extras = extras)
    val written = form.singles()
    val originals = mutableMapOf<String, Original>()
    for (property in card.orEmpty()) {
        val name = property.name.uppercase()
        if (name !in SINGLE || name in originals) continue
        originals[name] = Original(property.params, property.group, written[name].orEmpty(), property.value)
    }
    return form.copy(originals = originals)
}

/**
 * A birthday as the form holds it. Apple writes one without its year as a date
 * in a placeholder year that X-APPLE-OMIT-YEAR names, which reads as --MMDD;
 * any other value is kept as written.
 */
private fun birthday(property: ContactProperty): String {
    val omitted = property.params.entries
        .firstOrNull { it.key.equals("X-APPLE-OMIT-YEAR", ignoreCase = true) }?.value?.firstOrNull()
    val match = Regex("""^(\d{4})-?(\d{2})-?(\d{2})$""").matchEntire(property.value)
    if (match != null && omitted == match.groupValues[1]) {
        return "--${match.groupValues[2]}${match.groupValues[3]}"
    }
    return property.value
}

/** The value each single-valued field writes, by property name. */
private fun ContactForm.singles(): Map<String, String> {
    val structured = listOf(family, given, additional, prefix, suffix)
    return mapOf(
        "FN" to name.trim(),
        "N" to if (structured.any { it.isNotBlank() }) joinComponents(structured) else "",
        "NICKNAME" to nickname.trim(),
        "BDAY" to birthday.trim(),
        "ORG" to organisation.trim(),
        "TITLE" to title.trim(),
        "URL" to url.value.trim(),
        "NOTE" to note.trim(),
    )
}

/**
 * The managed properties to submit. Nothing outside [MANAGED] is ever
 * emitted: the server keeps the rest of the card, and sending a property it
 * does not manage is refused outright.
 */
fun ContactForm.properties(): List<ContactProperty> {
    val out = mutableListOf<ContactProperty>()
    val written = singles()
    // A single field keeps the parameters and group its property came with. A
    // changed value drops the ones that described the old one: its sort key,
    // its value type, which a date entered in the fields no longer is, and
    // Apple's placeholder year. A birthday left alone is written as the card
    // held it, so Apple's form keeps the year its parameter names.
    fun single(name: String) {
        val value = written[name].orEmpty()
        if (value.isBlank()) return
        val original = originals[name]
        if (original == null) {
            out.add(ContactProperty(name, emptyMap(), value))
            return
        }
        val params = if (value == original.value) {
            original.params
        } else {
            original.params.filterKeys {
                !it.equals("SORT-AS", ignoreCase = true) &&
                    !it.equals("VALUE", ignoreCase = true) &&
                    !it.equals("X-APPLE-OMIT-YEAR", ignoreCase = true)
            }
        }
        val kept = name == "BDAY" && value == original.value
        out.add(ContactProperty(name, params, if (kept) original.raw else value, original.group))
    }
    single("FN")
    single("N")
    single("NICKNAME")
    for (email in emails) {
        if (email.value.isBlank()) continue
        out.add(
            ContactProperty(
                "EMAIL",
                withType(email.params, email.type, EMAIL_TYPES),
                email.value.trim(),
                email.group,
            )
        )
    }
    for (phone in phones) {
        if (phone.value.isBlank()) continue
        out.add(
            ContactProperty(
                "TEL",
                withType(phone.params, phone.type, PHONE_TYPES),
                phone.value.trim(),
                phone.group,
            )
        )
    }
    for (address in addresses) {
        if (address.empty) continue
        val components = listOf(
            address.pobox,
            address.extended,
            address.street,
            address.city,
            address.region,
            address.postcode,
            address.country,
        )
        out.add(
            ContactProperty(
                "ADR",
                withType(address.params, address.type, ADDRESS_TYPES),
                joinComponents(components),
                address.group,
            )
        )
    }
    single("BDAY")
    single("ORG")
    single("TITLE")
    single("URL")
    single("NOTE")
    out.addAll(extras)
    return out
}

private fun typedEntry(property: ContactProperty, allowed: List<String>) = TypedEntry(
    value = property.value,
    type = typeOf(property.params, allowed),
    params = property.params,
    group = property.group,
)

/** The `TYPE` values of [params], under whatever case the key arrived in. */
private fun types(params: Map<String, List<String>>): List<String> =
    params.entries.firstOrNull { it.key.equals("TYPE", ignoreCase = true) }?.value.orEmpty()

/**
 * The editor's type for a property: the first of its `TYPE` values the kind
 * offers, CELL read as mobile; Other when it has none of them or no type.
 */
internal fun typeOf(params: Map<String, List<String>>, allowed: List<String>): String {
    for (value in types(params)) {
        val folded = value.lowercase()
        val mapped = if (folded == "cell") TYPE_MOBILE else folded
        if (mapped in allowed) return mapped
    }
    return TYPE_OTHER
}

/**
 * The vCard `TYPE` value a choice writes. vCard has no "other": an Other value
 * is written with no type of its own, and mobile is vCard's CELL.
 */
private fun token(type: String): String? = when (type) {
    TYPE_MOBILE -> "cell"
    TYPE_OTHER, "" -> null
    else -> type
}

/**
 * The parameters with the chosen type in place of the one the property had.
 * The editor owns the tokens of the kind's choices, and the "mobile" and
 * "other" it wrote before, which a save corrects; the owned value is replaced
 * where it stood, and kept as written when it already says the chosen type.
 * Every other `TYPE` value stays.
 */
internal fun withType(
    params: Map<String, List<String>>,
    type: String,
    allowed: List<String>,
): Map<String, List<String>> {
    val owned = setOf("mobile", "other") + allowed.mapNotNull(::token)
    val chosen = token(type)
    val values = mutableListOf<String>()
    var placed = false
    for (value in types(params)) {
        val folded = value.lowercase()
        if (folded !in owned) {
            values.add(value)
            continue
        }
        if (placed || chosen == null) continue
        values.add(if (folded == chosen) value else chosen)
        placed = true
    }
    if (chosen != null && !placed) values.add(chosen)
    val key = params.keys.firstOrNull { it.equals("TYPE", ignoreCase = true) } ?: "TYPE"
    val rest = params.filterKeys { !it.equals("TYPE", ignoreCase = true) }
    return if (values.isEmpty()) rest else rest + (key to values)
}

private fun List<String>.component(index: Int) = getOrNull(index).orEmpty()
