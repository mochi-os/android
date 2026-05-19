package org.mochios.staff.model

import com.google.gson.annotations.SerializedName

/**
 * Caller's own staff record.
 *
 * Mirrors the response shape of `event_staff_me` in
 * `apps/comptroller/starlark/staff.star`: `{id, role}` for a staff member,
 * `{id: <identity>, role: ""}` for an authenticated non-staff caller, and
 * `{id: "", role: ""}` when there is no identity at all. The layout uses
 * `role` to decide which admin-only items to surface, so a blank role must
 * be treated as "not staff".
 */
data class Me(
    val id: String = "",
    val role: String = "",
)

/**
 * Canonical staff roles, sourced from `VALID_STAFF_ROLES` in
 * `apps/comptroller/starlark/comptroller.star`.
 *
 * The wire value is a free-form lowercase string — the Comptroller validates
 * it against the same list. Unknown roles must be tolerated (treated as
 * non-staff) rather than crashing the deserialiser; the enum is non-exhaustive
 * and callers usually compare against [Role.ADMIN] for admin-gated UI.
 */
enum class Role {
    @SerializedName("admin") ADMIN,
    @SerializedName("moderator") MODERATOR,
    @SerializedName("support") SUPPORT,
}
