package org.mochios.staff.model

/**
 * One entry from the marketplace configuration (`config/get`).
 *
 * Mirrors `ConfigEntry` in `apps/staff/web/src/types/config.ts`. Values are
 * stored as opaque strings — the Comptroller coerces to the right type at
 * use site (e.g. `threshold_low` parses to int, `fee_percent` parses to a
 * decimal). Secret values (Stripe keys, etc.) are returned as empty strings
 * when read so the UI can show a "set" indicator without leaking the value.
 */
data class ConfigEntry(
    val key: String = "",
    val value: String = "",
)
