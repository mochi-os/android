// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.people.ui.contacts

import org.mochios.people.model.ContactProperty

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

/** The types a typed value may carry, in the order the picker lists them. */
val CONTACT_TYPES = listOf(TYPE_HOME, TYPE_WORK, TYPE_MOBILE, TYPE_OTHER)

/**
 * One email address, phone number or link, with its `TYPE` and whatever other
 * parameters came with it. An empty [type] means the property had no `TYPE`,
 * and none is written back.
 */
data class TypedEntry(
    val value: String = "",
    val type: String = "",
    val params: Map<String, List<String>> = emptyMap(),
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
) {
    val empty: Boolean
        get() = listOf(street, city, region, postcode, country, pobox, extended)
            .all { it.isBlank() }
}

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
) {
    val valid: Boolean
        get() = name.isNotBlank()
}

/** The form a card reads as, with [book] the address book it sits in. */
fun contactForm(card: List<ContactProperty>?, book: String = ""): ContactForm {
    var form = ContactForm(book = book)
    val emails = mutableListOf<TypedEntry>()
    val phones = mutableListOf<TypedEntry>()
    val addresses = mutableListOf<AddressEntry>()
    for (property in card.orEmpty()) {
        when (property.name.uppercase()) {
            "FN" -> if (form.name.isBlank()) form = form.copy(name = property.value)
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
            "NICKNAME" -> if (form.nickname.isBlank()) form = form.copy(nickname = property.value)
            "EMAIL" -> emails.add(typedEntry(property))
            "TEL" -> phones.add(typedEntry(property))
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
                        type = property.type,
                        params = property.otherParams,
                    )
                )
            }
            "BDAY" -> if (form.birthday.isBlank()) form = form.copy(birthday = property.value)
            "ORG" -> if (form.organisation.isBlank()) form = form.copy(organisation = property.value)
            "TITLE" -> if (form.title.isBlank()) form = form.copy(title = property.value)
            "URL" -> if (form.url.value.isBlank()) form = form.copy(url = typedEntry(property))
            "NOTE" -> if (form.note.isBlank()) form = form.copy(note = property.value)
        }
    }
    return form.copy(emails = emails, phones = phones, addresses = addresses)
}

/**
 * The managed properties to submit. Nothing outside [MANAGED] is ever
 * emitted: the server keeps the rest of the card, and sending a property it
 * does not manage is refused outright.
 */
fun ContactForm.properties(): List<ContactProperty> {
    val out = mutableListOf<ContactProperty>()
    if (name.isNotBlank()) out.add(ContactProperty("FN", emptyMap(), name.trim()))
    val structured = listOf(family, given, additional, prefix, suffix)
    if (structured.any { it.isNotBlank() }) {
        out.add(ContactProperty("N", emptyMap(), joinComponents(structured)))
    }
    if (nickname.isNotBlank()) out.add(ContactProperty("NICKNAME", emptyMap(), nickname.trim()))
    for (email in emails) {
        if (email.value.isBlank()) continue
        out.add(ContactProperty("EMAIL", email.parameters(), email.value.trim()))
    }
    for (phone in phones) {
        if (phone.value.isBlank()) continue
        out.add(ContactProperty("TEL", phone.parameters(), phone.value.trim()))
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
                parameters(address.type, address.params),
                joinComponents(components),
            )
        )
    }
    if (birthday.isNotBlank()) out.add(ContactProperty("BDAY", emptyMap(), birthday.trim()))
    if (organisation.isNotBlank()) out.add(ContactProperty("ORG", emptyMap(), organisation.trim()))
    if (title.isNotBlank()) out.add(ContactProperty("TITLE", emptyMap(), title.trim()))
    if (url.value.isNotBlank()) {
        out.add(ContactProperty("URL", url.parameters(), url.value.trim()))
    }
    if (note.isNotBlank()) out.add(ContactProperty("NOTE", emptyMap(), note.trim()))
    return out
}

private fun typedEntry(property: ContactProperty) =
    TypedEntry(value = property.value, type = property.type, params = property.otherParams)

/** The property's `TYPE`, lowercased; empty when it carries none. */
private val ContactProperty.type: String
    get() = params.entries
        .firstOrNull { it.key.equals("TYPE", ignoreCase = true) }
        ?.value?.firstOrNull()?.lowercase()
        .orEmpty()

/** Every parameter except `TYPE`, which the editor owns. */
private val ContactProperty.otherParams: Map<String, List<String>>
    get() = params.filterKeys { !it.equals("TYPE", ignoreCase = true) }

private fun TypedEntry.parameters() = parameters(type, params)

private fun parameters(type: String, others: Map<String, List<String>>) =
    if (type.isBlank()) others else others + ("TYPE" to listOf(type))

private fun List<String>.component(index: Int) = getOrNull(index).orEmpty()

/**
 * Split a structured vCard value on its unescaped `;` separators, undoing the
 * escapes each component carries.
 */
internal fun splitComponents(value: String): List<String> {
    val out = mutableListOf<String>()
    val current = StringBuilder()
    var escaped = false
    for (character in value) {
        when {
            escaped -> {
                current.append(if (character == 'n' || character == 'N') '\n' else character)
                escaped = false
            }
            character == '\\' -> escaped = true
            character == ';' -> {
                out.add(current.toString())
                current.setLength(0)
            }
            else -> current.append(character)
        }
    }
    if (escaped) current.append('\\')
    out.add(current.toString())
    return out
}

/** Join components back into one structured value, escaping as vCard wants. */
internal fun joinComponents(components: List<String>): String =
    components.joinToString(";") { component ->
        buildString {
            for (character in component) {
                when (character) {
                    '\\' -> append("\\\\")
                    ';' -> append("\\;")
                    '\n' -> append("\\n")
                    else -> append(character)
                }
            }
        }
    }
