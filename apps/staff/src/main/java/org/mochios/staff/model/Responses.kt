package org.mochios.staff.model

/**
 * Generic ack payload for endpoints that just return `{ok: true}` (delete /
 * approve / reject / remove / set-config / decide-appeal). The Comptroller
 * actually returns the updated row on most of these, but the api file maps
 * the lightweight ones to [OkResponse] when callers don't need the row.
 */
data class OkResponse(val ok: Boolean = true)
