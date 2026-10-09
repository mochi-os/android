// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.people.repository

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.MultipartBody
import org.mochios.android.api.unwrap
import org.mochios.android.files.FileRepository
import org.mochios.android.files.FileStore
import org.mochios.android.sync.ContactProperty
import org.mochios.people.api.ContactRequest
import org.mochios.people.api.ContactUpdateRequest
import org.mochios.people.api.MergeSource
import org.mochios.people.api.ContactsListResponse
import org.mochios.people.api.PeopleApi
import org.mochios.people.api.PreferenceResponse
import org.mochios.people.api.WelcomeResponse
import org.mochios.people.model.Book
import org.mochios.people.model.Contact
import org.mochios.people.model.ContactMerge
import org.mochios.people.api.TokenResponse
import org.mochios.people.api.TokensResponse
import org.mochios.people.model.Group
import org.mochios.people.model.GroupMember
import org.mochios.people.model.GroupMemberType
import org.mochios.people.model.LocalUser
import org.mochios.people.model.PersonInformation
import org.mochios.people.model.PersonStyle
import org.mochios.people.model.User
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PeopleRepository @Inject constructor(
    private val api: PeopleApi,
    fileStore: FileStore,
) : FileRepository(fileStore) {

    /**
     * Fires when a group is edited or deleted, so the list screen (a separate
     * ViewModel) can reload.
     */
    private val _groupsChanged = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val groupsChanged: SharedFlow<Unit> = _groupsChanged.asSharedFlow()

    /**
     * Fires after any contact or address-book mutation, so a list screen whose
     * ViewModel did not make the change still reloads.
     */
    private val _contactsChanged = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val contactsChanged: SharedFlow<Unit> = _contactsChanged.asSharedFlow()

    /** Signals a change and marks the books stale, since their contact counts may have moved. */
    private fun notifyChanged() {
        booksStale = true
        _contactsChanged.tryEmit(Unit)
    }

    // ---- Contacts + invites ----

    /** [book] limits the list to one address book; null lists them all. */
    suspend fun listContacts(book: String? = null): ContactsListResponse =
        api.listContacts(book).unwrap()

    suspend fun getContact(contact: String): Contact =
        api.getContact(contact).unwrap().contact

    /**
     * The merge of [contact] and [source] as the server would save it: the
     * contact that survives, its card holding both cards' details, and the
     * one it absorbs. The survivor is whichever is linked to a Mochi person.
     */
    suspend fun previewMerge(contact: String, source: String): ContactMerge {
        val response = api.getContact(contact, source).unwrap()
        return ContactMerge(response.contact, response.source ?: Contact())
    }

    suspend fun createContact(
        properties: List<ContactProperty>,
        person: String? = null,
        book: String? = null,
        source: String? = null,
    ): Contact {
        val contact = api.createContact(
            ContactRequest(properties, person, book, source = source),
        ).unwrap().contact
        notifyChanged()
        return contact
    }

    /**
     * [properties] replaces every managed property of the card, so it carries
     * the editor's whole set. A stale [etag] is refused with 412.
     */
    suspend fun updateContact(
        contact: String,
        etag: String? = null,
        properties: List<ContactProperty>? = null,
        book: String? = null,
        source: MergeSource? = null,
    ): Contact {
        val updated = api.updateContact(
            ContactUpdateRequest(contact, etag, properties, book, source),
        ).unwrap().contact
        notifyChanged()
        return updated
    }

    /** Deleting a friend's contact also ends the friendship. */
    suspend fun deleteContact(contact: String) {
        api.deleteContact(contact).unwrap()
        notifyChanged()
    }

    suspend fun searchDirectory(query: String): List<User> =
        api.searchDirectory(query).unwrap().results

    // ---- Address books ----

    private val _books = MutableStateFlow<List<Book>>(emptyList())

    /** The address books as last fetched, so every screen's drawer lists them at once. */
    val books: StateFlow<List<Book>> = _books.asStateFlow()

    private val booksLock = Mutex()

    @Volatile
    private var booksStale = true

    /** Fetches the address books from the server, whatever is already held. */
    suspend fun listBooks(): List<Book> = booksLock.withLock { fetchBooks() }

    /**
     * The address books, fetched only when none are held yet or a change has
     * made them stale. Callers arriving together share the one request.
     */
    suspend fun loadBooks(): List<Book> = booksLock.withLock {
        if (booksStale) fetchBooks() else _books.value
    }

    /** Marks the books stale, so the next [loadBooks] fetches them again. */
    fun markBooksStale() {
        booksStale = true
    }

    private suspend fun fetchBooks(): List<Book> {
        val fetched = api.listBooks().unwrap().books
        _books.value = fetched
        booksStale = false
        return fetched
    }

    suspend fun createBook(name: String): Book {
        val book = api.createBook(name).unwrap().book
        notifyChanged()
        return book
    }

    suspend fun renameBook(book: String, name: String) {
        api.renameBook(book, name).unwrap()
        notifyChanged()
    }

    /** Deletes the book and every contact in it. The default book is refused. */
    suspend fun deleteBook(book: String) {
        api.deleteBook(book).unwrap()
        notifyChanged()
    }

    // ---- Device tokens ----

    /** A new device's password. The server shows it this once. */
    suspend fun createToken(name: String): TokenResponse =
        api.createToken(name).unwrap()

    suspend fun listTokens(): TokensResponse =
        api.listTokens().unwrap()

    suspend fun deleteToken(hash: String) {
        api.deleteToken(hash).unwrap()
    }

    // ---- Friendship ----

    suspend fun inviteFriend(
        person: String,
        name: String,
        contact: String? = null,
        book: String? = null,
    ) {
        api.inviteFriend(person, name, contact, book).unwrap()
        notifyChanged()
    }

    suspend fun acceptInvite(person: String) {
        api.acceptInvite(person).unwrap()
        notifyChanged()
    }

    suspend fun ignoreInvite(person: String) {
        api.ignoreInvite(person).unwrap()
        notifyChanged()
    }

    /** Ends the friendship and keeps the contact. */
    suspend fun removeFriend(person: String) {
        api.removeFriend(person).unwrap()
        notifyChanged()
    }

    // ---- Welcome state ----

    suspend fun getWelcome(): WelcomeResponse =
        api.getWelcome().unwrap()

    suspend fun markWelcomeSeen() {
        api.markWelcomeSeen().unwrap()
    }

    // ---- Preferences ----

    suspend fun getPreferences(): PreferenceResponse =
        api.getPreferences().unwrap()

    suspend fun setInvitePolicy(invitePolicy: String) {
        api.setPreferences(invitePolicy).unwrap()
    }

    // ---- Local users (group-membership picker) ----

    suspend fun searchLocalUsers(query: String): List<LocalUser> =
        api.searchLocalUsers(query).unwrap().results

    // ---- Groups ----

    suspend fun listGroups(): List<Group> =
        api.listGroups().unwrap().groups

    data class GroupDetail(val group: Group, val members: List<GroupMember>)

    suspend fun getGroup(id: String): GroupDetail {
        val response = api.getGroup(id).unwrap()
        return GroupDetail(group = response.group, members = response.members)
    }

    suspend fun createGroup(
        name: String,
        description: String? = null,
        id: String? = null,
    ): String =
        api.createGroup(id, name, description).unwrap().id

    suspend fun updateGroup(
        id: String,
        name: String? = null,
        description: String? = null,
    ) {
        api.updateGroup(id, name, description).unwrap()
        _groupsChanged.tryEmit(Unit)
    }

    suspend fun deleteGroup(id: String) {
        api.deleteGroup(id).unwrap()
        _groupsChanged.tryEmit(Unit)
    }

    suspend fun addGroupMember(group: String, member: String, type: GroupMemberType) {
        api.addGroupMember(group, member, wireType(type)).unwrap()
    }

    suspend fun removeGroupMember(group: String, member: String) {
        api.removeGroupMember(group, member).unwrap()
    }

    // ---- Person profile ----

    suspend fun getPersonInformation(person: String): PersonInformation =
        api.getPersonInformation(person).unwrap()

    suspend fun getPersonStyle(person: String): PersonStyle =
        api.getPersonStyle(person).unwrap()

    suspend fun setName(person: String, name: String) {
        api.setName(person, name).unwrap()
    }

    suspend fun setProfile(person: String, profile: String) {
        api.setProfile(person, profile).unwrap()
    }

    suspend fun setAccent(person: String, accent: String) {
        api.setAccent(person, accent).unwrap()
    }

    suspend fun setPrivacy(person: String, privacy: String) {
        api.setPrivacy(person, privacy).unwrap()
    }

    suspend fun setAvatar(person: String, file: File) {
        api.setAvatar(person, multipart(file)).unwrap()
    }

    suspend fun setBanner(person: String, file: File) {
        api.setBanner(person, multipart(file)).unwrap()
    }

    suspend fun setFavicon(person: String, file: File) {
        api.setFavicon(person, multipart(file)).unwrap()
    }

    // The picker always re-encodes to JPEG, so the part must declare an image
    // Content-Type — the server rejects "application/octet-stream" with
    // "<slot> must be an image". Field name "file" matches the web upload.
    private fun multipart(file: File): MultipartBody.Part =
        fileStore.filePart("file", file, "image/jpeg")

    private fun wireType(type: GroupMemberType): String = when (type) {
        GroupMemberType.USER -> "user"
        GroupMemberType.GROUP -> "group"
    }
}
