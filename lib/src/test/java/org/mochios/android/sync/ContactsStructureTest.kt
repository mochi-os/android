// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

// CommonDataKinds.Im is deprecated since API 31 as a kind some contacts apps
// do not show; it is still the phone's only kind for a messaging address, and
// the Contacts apps that show it are the ones a user edits these contacts in.
@file:Suppress("DEPRECATION")

package org.mochios.android.sync

import android.provider.ContactsContract.CommonDataKinds.Email
import android.provider.ContactsContract.CommonDataKinds.Event
import android.provider.ContactsContract.CommonDataKinds.Im
import android.provider.ContactsContract.CommonDataKinds.Nickname
import android.provider.ContactsContract.CommonDataKinds.Note
import android.provider.ContactsContract.CommonDataKinds.Organization
import android.provider.ContactsContract.CommonDataKinds.Phone
import android.provider.ContactsContract.CommonDataKinds.StructuredName
import android.provider.ContactsContract.CommonDataKinds.StructuredPostal
import android.provider.ContactsContract.CommonDataKinds.Website
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element

/**
 * The app's contacts structure file, which Contacts apps read to decide
 * whether a Mochi contact can be edited and which fields to offer. It must
 * declare an edit schema, or the account is read-only, and the schema must
 * match the sync: every kind the sync carries is editable, nothing the sync
 * drops is offered, and every type the editor offers survives an upload.
 */
class ContactsStructureTest {

    private val schema: Element by lazy {
        val file = File("../app/src/main/res/xml/structure.xml")
        val root = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file).documentElement
        assertEquals("ContactsAccountType", root.tagName)
        val schemas = root.getElementsByTagName("EditSchema")
        assertEquals("one EditSchema, or the account is read-only", 1, schemas.length)
        schemas.item(0) as Element
    }

    private fun kinds(): Map<String, Element> {
        val nodes = schema.getElementsByTagName("DataKind")
        return (0 until nodes.length).map { nodes.item(it) as Element }.associateBy { it.getAttribute("kind") }
    }

    private fun types(kind: String): List<Element> {
        val nodes = kinds().getValue(kind).getElementsByTagName("Type")
        return (0 until nodes.length).map { nodes.item(it) as Element }
    }

    private val names = mapOf(
        StructuredName.CONTENT_ITEM_TYPE to "name",
        Nickname.CONTENT_ITEM_TYPE to "nickname",
        Email.CONTENT_ITEM_TYPE to "email",
        Phone.CONTENT_ITEM_TYPE to "phone",
        StructuredPostal.CONTENT_ITEM_TYPE to "postal",
        Organization.CONTENT_ITEM_TYPE to "organization",
        Note.CONTENT_ITEM_TYPE to "note",
        Website.CONTENT_ITEM_TYPE to "website",
        Event.CONTENT_ITEM_TYPE to "event",
        Im.CONTENT_ITEM_TYPE to "im",
    )

    @Test
    fun `the schema offers exactly the kinds the sync carries, and photo`() {
        val carried = ContactsMapping.readable().map { names.getValue(it) }.toSet()
        // The parser refuses a schema without photo; a photo set on the phone stays on the phone.
        assertEquals(carried + "photo", kinds().keys)
    }

    @Test
    fun `the name kind claims every part, or the parser rejects the schema`() {
        val name = kinds().getValue("name")
        for (part in listOf(
            "supportsDisplayName", "supportsPrefix", "supportsMiddleName", "supportsSuffix",
            "supportsPhoneticFamilyName", "supportsPhoneticMiddleName", "supportsPhoneticGivenName",
        )) {
            assertEquals(part, "true", name.getAttribute(part))
        }
        assertEquals("1", name.getAttribute("maxOccurs"))
    }

    @Test
    fun `the only event is one birthday, with the year optional`() {
        val event = types("event").single()
        assertEquals("birthday", event.getAttribute("type"))
        assertEquals("1", event.getAttribute("maxOccurs"))
        assertEquals("true", event.getAttribute("yearOptional"))
    }

    @Test
    fun `every phone type the editor offers uploads as its own vCard type`() {
        val constants = mapOf(
            "mobile" to Phone.TYPE_MOBILE, "home" to Phone.TYPE_HOME, "work" to Phone.TYPE_WORK,
            "work_mobile" to Phone.TYPE_WORK_MOBILE, "main" to Phone.TYPE_MAIN,
            "fax_work" to Phone.TYPE_FAX_WORK, "fax_home" to Phone.TYPE_FAX_HOME,
            "other_fax" to Phone.TYPE_OTHER_FAX, "pager" to Phone.TYPE_PAGER,
            "work_pager" to Phone.TYPE_WORK_PAGER, "car" to Phone.TYPE_CAR,
            "company_main" to Phone.TYPE_COMPANY_MAIN, "isdn" to Phone.TYPE_ISDN,
            "callback" to Phone.TYPE_CALLBACK, "radio" to Phone.TYPE_RADIO, "telex" to Phone.TYPE_TELEX,
            "tty_tdd" to Phone.TYPE_TTY_TDD, "assistant" to Phone.TYPE_ASSISTANT, "mms" to Phone.TYPE_MMS,
            "other" to Phone.TYPE_OTHER, "custom" to Phone.TYPE_CUSTOM,
        )
        val labels = types("phone").map { type ->
            val constant = constants[type.getAttribute("type")]
            assertNotNull("phone type ${type.getAttribute("type")}", constant)
            uploaded(DataRow(Phone.CONTENT_ITEM_TYPE, mapOf(
                Phone.NUMBER to "1",
                Phone.TYPE to constant.toString(),
                Phone.LABEL to "Boat",
            )))
        }
        assertDistinct(labels)
    }

    @Test
    fun `every email and address type the editor offers uploads as its own vCard type`() {
        val email = mapOf(
            "home" to Email.TYPE_HOME, "work" to Email.TYPE_WORK, "mobile" to Email.TYPE_MOBILE,
            "other" to Email.TYPE_OTHER, "custom" to Email.TYPE_CUSTOM,
        )
        assertDistinct(types("email").map { type ->
            uploaded(DataRow(Email.CONTENT_ITEM_TYPE, mapOf(
                Email.ADDRESS to "a@b.c",
                Email.TYPE to email.getValue(type.getAttribute("type")).toString(),
                Email.LABEL to "Club",
            )))
        })
        val postal = mapOf(
            "home" to StructuredPostal.TYPE_HOME, "work" to StructuredPostal.TYPE_WORK,
            "other" to StructuredPostal.TYPE_OTHER, "custom" to StructuredPostal.TYPE_CUSTOM,
        )
        assertDistinct(types("postal").map { type ->
            uploaded(DataRow(StructuredPostal.CONTENT_ITEM_TYPE, mapOf(
                StructuredPostal.STREET to "1 High Street",
                StructuredPostal.TYPE to postal.getValue(type.getAttribute("type")).toString(),
                StructuredPostal.LABEL to "Holiday",
            )))
        })
    }

    @Test
    fun `every messaging protocol the editor offers uploads as its own scheme`() {
        val protocols = mapOf(
            "aim" to Im.PROTOCOL_AIM, "msn" to Im.PROTOCOL_MSN, "yahoo" to Im.PROTOCOL_YAHOO,
            "skype" to Im.PROTOCOL_SKYPE, "qq" to Im.PROTOCOL_QQ, "google_talk" to Im.PROTOCOL_GOOGLE_TALK,
            "icq" to Im.PROTOCOL_ICQ, "jabber" to Im.PROTOCOL_JABBER, "custom" to Im.PROTOCOL_CUSTOM,
        )
        val schemes = types("im").map { type ->
            val protocol = protocols[type.getAttribute("type")]
            assertNotNull("im protocol ${type.getAttribute("type")}", protocol)
            val value = ContactsMapping.properties(listOf(DataRow(Im.CONTENT_ITEM_TYPE, mapOf(
                Im.DATA to "ada",
                Im.PROTOCOL to protocol.toString(),
                Im.CUSTOM_PROTOCOL to "Matrix",
            )))).single { it.name == "IMPP" }.value
            value.substringBefore(':')
        }
        assertEquals("two protocols upload as the same scheme: $schemes", schemes.size, schemes.toSet().size)
    }

    private val properties = mapOf(
        Phone.CONTENT_ITEM_TYPE to "TEL",
        Email.CONTENT_ITEM_TYPE to "EMAIL",
        StructuredPostal.CONTENT_ITEM_TYPE to "ADR",
    )

    /** The TYPE an upload writes for one row, which must not be empty. */
    private fun uploaded(row: DataRow): List<String> {
        val name = properties.getValue(row.mimetype)
        val types = ContactsMapping.properties(listOf(row)).single { it.name == name }.params["TYPE"].orEmpty()
        assertTrue("${row.mimetype} ${row.values} uploads with no TYPE", types.isNotEmpty())
        return types
    }

    private fun assertDistinct(labels: List<List<String>>) =
        assertEquals("two editor types upload as the same vCard type: $labels", labels.size, labels.toSet().size)
}
