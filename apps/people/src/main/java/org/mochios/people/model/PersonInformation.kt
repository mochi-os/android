// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.people.model

/**
 * Public profile of a person, local or remote. [avatar], [banner] and [favicon]
 * are attachment ids, not URLs.
 */
data class PersonInformation(
    val id: String = "",
    val fingerprint: String = "",
    val name: String = "",
    val privacy: String = "",
    val profile: String = "",
    val style: PersonStyle = PersonStyle(),
    val avatar: String = "",
    val banner: String = "",
    val favicon: String = "",
    /** The person's accounts on other networks, as the profile links them. */
    val accounts: List<ProfileAccount> = emptyList(),
    /** The person's newest posts and comments in public forums. */
    val history: List<ProfileHistory> = emptyList(),
)

/** An account on another network the person shows on their profile, at [url]. */
data class ProfileAccount(
    val type: String = "",
    val identifier: String = "",
    val name: String = "",
    val url: String = "",
)

/**
 * A post or comment in a public forum. [title] is the post's, for a comment
 * the post it is under; [body] is the start of a comment's text.
 */
data class ProfileHistory(
    val kind: String = "",
    val id: String = "",
    val post: String = "",
    val forum: String = "",
    @com.google.gson.annotations.SerializedName("forum_name") val forumName: String = "",
    val fingerprint: String = "",
    val title: String = "",
    val body: String = "",
    val created: Long = 0,
)
