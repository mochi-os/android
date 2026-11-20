// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.people.navigation

import android.net.Uri
import android.os.Bundle
import androidx.navigation.NavHostController
import androidx.navigation.compose.ComposeNavigator
import androidx.navigation.createGraph
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.mochios.people.model.Contact
import org.mochios.people.model.ContactMessage
import org.mochios.people.model.ProfileHistory
import org.mochios.people.ui.contacts.messageLink
import org.mochios.people.ui.person.forumLink
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.net.URLDecoder

/**
 * The route builders against the module's real graph, and the links the
 * module hands the app's router, each with an id holding every character
 * that splits or ends a route.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class RoutesTest {

    private val navigation = NavHostController(RuntimeEnvironment.getApplication()).apply {
        navigatorProvider.addNavigator(ComposeNavigator())
        graph = createGraph(startDestination = PeopleApp.ROUTER) { peopleNavGraph(this@apply, onOpenNotifications = {}) }
    }

    /** Opens [route] and answers the destination it matched and the arguments it filled. */
    private fun open(route: String): Pair<String?, Bundle> {
        navigation.navigate(route)
        val entry = navigation.currentBackStackEntry!!
        return entry.destination.route to (entry.arguments ?: Bundle())
    }

    @Test
    fun `every builder hands its screen an awkward id unchanged`() {
        mapOf(
            PeopleApp.groupDetail(AWKWARD) to PeopleApp.GROUP_DETAIL,
            PeopleApp.groupAddMember(AWKWARD) to PeopleApp.GROUP_ADD_MEMBER,
            PeopleApp.personView(AWKWARD) to PeopleApp.PERSON_VIEW,
            PeopleApp.contactEdit(AWKWARD) to PeopleApp.CONTACT_EDIT,
            PeopleApp.book(AWKWARD) to PeopleApp.BOOK,
        ).forEach { (built, pattern) ->
            val (route, arguments) = open(built)
            assertEquals(built, pattern, route)
            assertEquals(built, AWKWARD, arguments.getString("id"))
        }
        open(PeopleApp.contacts(AWKWARD)).let { (route, arguments) ->
            assertEquals(PeopleApp.CONTACTS, route)
            assertEquals(AWKWARD, arguments.getString("action"))
        }
        open(PeopleApp.contactsAdd(AWKWARD, AWKWARD, AWKWARD)).let { (route, arguments) ->
            assertEquals(PeopleApp.CONTACTS_ADD, route)
            assertEquals(AWKWARD, arguments.getString("contact"))
            assertEquals(AWKWARD, arguments.getString("name"))
            assertEquals(AWKWARD, arguments.getString("book"))
        }
    }

    /** The query values of [link] as the app's router reads them: split at the first `?`, each value URL-decoded. */
    private fun query(link: String): Map<String, String> =
        link.substringAfter('?', "").split('&').filter { it.isNotEmpty() }.associate { pair ->
            pair.substringBefore('=') to URLDecoder.decode(pair.substringAfter('='), Charsets.UTF_8.name())
        }

    /** The path parts of [link] as the app's router reads them: split at `/`, each part decoded once. */
    private fun parts(link: String): List<String> = link.substringBefore('?').split('/').map { Uri.decode(it) }

    @Test
    fun `Message on a relayed person's page cannot add an account or address to the chat link`() {
        val link = chatLink("x&account=A&address=B")
        assertEquals(mapOf("friend" to "x&account=A&address=B"), query(link))
        assertEquals(mapOf("friend" to AWKWARD), query(chatLink(AWKWARD)))
    }

    @Test
    fun `a contact's Message link carries each value whole`() {
        val friend = Contact(id = "c1", person = AWKWARD, friend = true, name = "Ada")
        assertEquals(mapOf("friend" to AWKWARD), query(messageLink(friend)!!))
        val other = Contact(id = "c2", name = AWKWARD, message = ContactMessage(address = AWKWARD, account = AWKWARD))
        assertEquals(mapOf("account" to AWKWARD, "address" to AWKWARD, "name" to AWKWARD), query(messageLink(other)!!))
    }

    @Test
    fun `a forum post on a profile opens under its own forum and post, and adds no query`() {
        val link = forumLink(ProfileHistory(kind = "post", post = AWKWARD, forum = "f1", fingerprint = AWKWARD))
        assertEquals(listOf("forums", AWKWARD, AWKWARD), parts(link))
        val server = forumLink(ProfileHistory(kind = "post", post = "p?server=evil.example", forum = "f1"))
        assertEquals(emptyMap<String, String>(), query(server))
        assertEquals(listOf("forums", "f1", "p?server=evil.example"), parts(server))
    }

    private companion object {
        /** Every character that splits or ends a route, and a space. */
        const val AWKWARD = "a/b?c#d&e=f%g h+i"
    }
}
