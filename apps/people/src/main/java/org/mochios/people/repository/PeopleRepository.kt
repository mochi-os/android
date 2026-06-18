package org.mochios.people.repository

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import org.mochios.android.api.unwrap
import org.mochios.people.api.FriendsListResponse
import org.mochios.people.api.PeopleApi
import org.mochios.people.api.PreferenceResponse
import org.mochios.people.api.WelcomeResponse
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

/**
 * Thin convenience wrapper around [PeopleApi]. Mirrors the structure of
 * `CrmsRepository`: each method calls `.unwrap()` on the wrapped
 * `ApiResponse<T>` to surface either the inner payload or a typed
 * `MochiError` for the ViewModels to render. No caching layer — the people
 * data set is small enough that we lean on TanStack-style stale-while-
 * revalidate in the ViewModel rather than pre-warming here.
 */
@Singleton
class PeopleRepository @Inject constructor(
    private val api: PeopleApi,
) {

    /**
     * Emits whenever a group is edited or deleted. The group list and the
     * group detail live on separate back-stack entries with independent
     * ViewModels, so the list can't observe the detail directly. This shared
     * signal lets the list reload the moment a mutation succeeds, instead of
     * relying on navigation-resume callbacks that don't fire across the app's
     * multi-graph navigation.
     */
    private val _groupsChanged = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val groupsChanged: SharedFlow<Unit> = _groupsChanged.asSharedFlow()

    // ---- Friends + invites ----

    suspend fun listFriends(): FriendsListResponse =
        api.listFriends().unwrap()

    suspend fun searchFriends(query: String): List<User> =
        api.searchFriends(query).unwrap().results

    suspend fun createFriend(id: String, name: String) {
        api.createFriend(id, name).unwrap()
    }

    suspend fun acceptInvite(id: String) {
        api.acceptInvite(id).unwrap()
    }

    suspend fun ignoreInvite(id: String) {
        api.ignoreInvite(id).unwrap()
    }

    /** Used for both "remove confirmed friend" and "cancel outgoing invite". */
    suspend fun deleteFriend(id: String) {
        api.deleteFriend(id).unwrap()
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
    private fun multipart(file: File): MultipartBody.Part {
        val body = file.asRequestBody("image/jpeg".toMediaTypeOrNull())
        return MultipartBody.Part.createFormData("file", file.name, body)
    }

    private fun wireType(type: GroupMemberType): String = when (type) {
        GroupMemberType.USER -> "user"
        GroupMemberType.GROUP -> "group"
    }
}
