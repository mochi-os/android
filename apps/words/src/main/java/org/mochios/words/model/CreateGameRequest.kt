package org.mochios.words.model

/**
 * Request body for `-/create`. Mirrors the web client's `gamesApi.create` —
 * `opponents` is a comma-joined string of entity IDs (the server splits on
 * `,` in `action_create`). `language` is one of `"en_US"` / `"en_UK"`.
 *
 * The repository layer accepts a `List<String>` opponents argument for
 * ergonomics and joins on the way in.
 */
data class CreateGameRequest(
    val opponents: String = "",
    val language: String = "en_US",
)
