// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.settings.ui.account

import java.security.SecureRandom

/**
 * Passphrases for the data export. The bundle carries the account's private
 * keys and is attacked offline, so entropy is what matters: ten words from
 * this list (the web client's list, so a phrase reads the same on either)
 * is about 80 bits. The server refuses anything shorter than [MINIMUM] code
 * points.
 */
object Passphrase {
    const val MINIMUM = 12
    const val WORDS = 10

    val LIST: List<String> = listOf(
        "able", "acid", "aged", "also", "area", "army", "away", "baby",
        "back", "ball", "band", "bank", "base", "bath", "bear", "beat",
        "been", "bell", "best", "bird", "bite", "blue", "bold", "bolt",
        "bond", "bone", "book", "boot", "born", "both", "bowl", "burn",
        "calm", "came", "camp", "card", "care", "cart", "case", "cash",
        "cast", "cave", "chef", "chip", "city", "clan", "clay", "clip",
        "coal", "coat", "code", "coil", "cold", "cone", "cook", "cool",
        "cord", "core", "corn", "cost", "cozy", "crop", "crow", "curl",
        "damp", "dare", "dark", "dart", "dash", "data", "dawn", "dear",
        "deck", "deep", "deer", "deny", "desk", "dial", "dirt", "disk",
        "dock", "dome", "done", "door", "down", "draw", "drum", "dusk",
        "dust", "earn", "ease", "east", "edge", "else", "emit", "even",
        "ever", "exam", "face", "fact", "fade", "fair", "fall", "farm",
        "fast", "fear", "felt", "file", "fill", "find", "fine", "fire",
        "firm", "fish", "fist", "flag", "flat", "flew", "flip", "flow",
        "foam", "fold", "folk", "fond", "font", "food", "fool", "fork",
        "form", "fort", "fowl", "free", "from", "fuel", "full", "fund",
        "gain", "game", "gate", "gave", "gaze", "gear", "glow", "glue",
        "gnat", "goal", "goat", "gold", "golf", "good", "grab", "gray",
        "grew", "grid", "grip", "grow", "gulf", "gust", "half", "hall",
        "halt", "hand", "hard", "harm", "harp", "hash", "hate", "haze",
        "head", "heal", "heap", "heat", "heel", "held", "helm", "help",
        "hero", "hide", "high", "hill", "hint", "hole", "holy", "home",
        "hood", "hook", "hope", "horn", "hose", "host", "hour", "huge",
        "hull", "hunt", "hurt", "icon", "idea", "inch", "into", "iris",
        "iron", "isle", "item", "jade", "jail", "jazz", "join", "joke",
        "jump", "just", "keen", "keep", "kelp", "kind", "king", "knot",
        "know", "lace", "lack", "lake", "lamp", "land", "lane", "last",
        "late", "lava", "lawn", "lead", "leaf", "lean", "leap", "left",
        "lend", "lens", "lift", "lime", "link", "lion", "list", "live",
        "load", "lock", "loft", "logo", "lone", "long", "look", "loop",
    )

    fun generate(random: SecureRandom = SecureRandom()): String =
        List(WORDS) { LIST[random.nextInt(LIST.size)] }.joinToString(" ")

    /** Whether [passphrase] meets the server's floor, counted in code points
     *  as the server counts it. */
    fun long(passphrase: String): Boolean =
        passphrase.codePointCount(0, passphrase.length) >= MINIMUM
}
