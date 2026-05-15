package org.mochios.android.model

import com.google.gson.annotations.SerializedName

/**
 * User account record as returned by `<app>/-/accounts/list[?capability=...]`.
 *
 * `default` is a SQL reserved word — server serialises it under the
 * unchanged field name so we mark the binding explicitly.
 */
data class Account(
    val id: Int = 0,
    val type: String = "",
    val label: String = "",
    val identifier: String = "",
    val created: Long = 0,
    val verified: Int = 0,
    val enabled: Int = 0,
    @SerializedName("default") val isDefault: Int = 0,
) {
    /** Display label: explicit `label`, falling back to the identifier (e.g. email). */
    val displayLabel: String get() = label.ifBlank { identifier }
}
