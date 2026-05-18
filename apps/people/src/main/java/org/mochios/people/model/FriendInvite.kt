package org.mochios.people.model

/**
 * A pending friend invitation. The wire shape matches [Friend] — the server
 * uses the same record for received and sent invites; direction is implied
 * by which collection the invite appears in in the friends list response.
 */
typealias FriendInvite = Friend
