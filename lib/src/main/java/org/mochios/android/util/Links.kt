// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.util

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.text.util.Linkify
import io.noties.markwon.MarkwonPlugin
import io.noties.markwon.linkify.LinkifyPlugin
import org.mochios.android.api.OwnedAddress
import org.mochios.android.i18n.Flights
import org.mochios.android.i18n.Maps
import org.mochios.android.model.Attachment
import java.net.URLEncoder

/**
 * Whether a peer-supplied URL is a web link - the only kind that may leave the
 * app. Launching one verbatim would hand `tel:`, `file:`, `intent:` or
 * `javascript:` to ACTION_VIEW with this app as the sender. The scheme is
 * everything before the colon.
 */
fun isWebUrl(url: String): Boolean {
    val colon = url.indexOf(':')
    if (colon < 0) return false
    val scheme = url.substring(0, colon)
    return scheme.equals("http", ignoreCase = true) ||
        scheme.equals("https", ignoreCase = true)
}

/**
 * Resolve a peer-supplied URL to a [Uri] when it is a web link, null otherwise.
 * Every site that launches or downloads a peer's URL goes through here.
 */
fun webUri(url: String): Uri? = if (isWebUrl(url)) Uri.parse(url) else null

/**
 * Whether a peer-supplied URL is an e-mail address, `mailto:`. Opening one
 * starts a draft in the user's mail app, which sends nothing until they do.
 */
fun isMailUrl(url: String): Boolean {
    val colon = url.indexOf(':')
    return colon >= 0 && url.substring(0, colon).equals("mailto", ignoreCase = true)
}

/**
 * Resolve a link in a peer's rendered text - a post, a comment, a wiki page -
 * to a [Uri] when it is a web or an e-mail address, null otherwise. The
 * renderers link bare text for exactly these two kinds ([LINKED_ADDRESSES]),
 * so nothing is drawn as a link that a tap would not open.
 */
fun linkUri(url: String): Uri? = if (isWebUrl(url) || isMailUrl(url)) Uri.parse(url) else null

/**
 * The kinds of address a renderer links in bare text, as a [Linkify] mask:
 * web and e-mail addresses, the schemes [linkUri] opens. Never phone numbers,
 * which nothing opens.
 */
const val LINKED_ADDRESSES = Linkify.WEB_URLS or Linkify.EMAIL_ADDRESSES

/**
 * The Markwon plugin that links bare addresses in rendered Markdown, for the
 * kinds [LINKED_ADDRESSES] names and no others. Every renderer takes it from
 * here, beside the mask: a module reading the constant from this library's
 * compiled classes sees only a number, not a combination of Linkify's flags.
 */
fun linkifyPlugin(): MarkwonPlugin = LinkifyPlugin.create(LINKED_ADDRESSES)

/**
 * Whether an account's address on a profile is one the profile links: a web
 * page, or the `xmpp:` address a chat client opens.
 */
fun isProfileUrl(url: String): Boolean =
    url.startsWith("https://", ignoreCase = true) || url.startsWith("xmpp:", ignoreCase = true)

/**
 * Resolve an account's address on a profile to a [Uri] when [isProfileUrl],
 * null otherwise.
 */
fun profileUri(url: String): Uri? = if (isProfileUrl(url)) Uri.parse(url) else null

/**
 * [id], a peer's, escaped as one segment of an address on the user's server,
 * or null when no segment can carry it. An image request carries the session
 * and the token of the app its path names, so a slash or a question mark left
 * in an id would point it at another action, of any app. A slash cannot be
 * escaped away: the server decodes the path before it routes, so an escaped
 * one divides it again. Escaping leaves a dot alone, and the URL resolves "."
 * and ".." away whether escaped or not; an empty id would name the parent.
 * Everything else but RFC 3986's unreserved characters is escaped, as UTF-8.
 */
fun segment(id: String): String? {
    if (id.isEmpty() || id == "." || id == ".." || '/' in id) return null
    return buildString {
        for (byte in id.toByteArray(Charsets.UTF_8)) {
            val code = byte.toInt() and 0xff
            val character = code.toChar()
            if (character in 'a'..'z' || character in 'A'..'Z' || character in '0'..'9' || character in "-._~") {
                append(character)
            } else {
                append('%').append(HEX[code shr 4]).append(HEX[code and 15])
            }
        }
    }
}

private const val HEX = "0123456789ABCDEF"

/**
 * The parts of an in-app link's [path], each decoded once. A link arrives
 * escaped - a notification's, a shortcut's - and every module's route builder
 * escapes what it puts in a route, so a part handed on still escaped would be
 * escaped twice and name another item.
 */
fun linkParts(path: String): List<String> = path.split('/').map { part -> Uri.decode(part) }

/**
 * [part] when it is a plain identifier, RFC 3986's unreserved characters
 * alone as [segment] leaves them, else null: for a route builder that puts
 * what it is given into a route as it stands, where a decoded `/` or `?`
 * would name another destination or none.
 */
fun plain(part: String): String? = part.takeIf { segment(it) == it }

/**
 * What a `mochi:` URI is, for a log line: the system intent's name
 * (`notification`, `oauth-return`), the authority of a hierarchical one, or
 * `entity`. Never its query or path, which carry codes, nonces and ids.
 */
fun uriName(uri: Uri): String = when {
    !uri.isHierarchical -> uri.encodedSchemeSpecificPart.orEmpty().substringBefore('?')
    !uri.authority.isNullOrEmpty() -> uri.authority.orEmpty()
    else -> "entity"
}

/**
 * The launch [intent] handed on to [component]: its action, data, type,
 * categories and flags, and none of its extras. The activity that forwards is
 * exported, so any app's extras arrive with the launch; the forwarder adds
 * back the one the receiver routes by.
 */
fun forwardedIntent(intent: Intent, component: ComponentName): Intent =
    Intent(intent.action ?: Intent.ACTION_MAIN).apply {
        setDataAndType(intent.data, intent.type)
        intent.categories?.forEach { category -> addCategory(category) }
        flags = intent.flags
        setComponent(component)
    }

/**
 * The address of attachment [id] of [entity] in [app] on the user's server,
 * `/<app>/<entity>/-/attachments/<id>`, with [variant] ("thumbnail",
 * "preview") after it, or null when [entity] or [id] cannot be one segment
 * ([segment]). Both can reach the phone from another server, in a remote
 * view.
 */
fun attachmentAddress(app: String, entity: String, id: String, variant: String? = null): OwnedAddress? {
    val owner = segment(entity) ?: return null
    val attachment = segment(id) ?: return null
    return OwnedAddress.of("/$app/$owner/-/attachments/$attachment" + if (variant == null) "" else "/$variant")
}

/**
 * An attachment's image, media or file as a Coil model: [url], the address
 * the server gave for it, when it is one of the app's own (an [OwnedAddress],
 * fetched with a credential) or another host's web address, such as a feed
 * source's enclosure (fetched without); else [built], the address the app
 * builds from its own routes.
 */
fun attachmentModel(url: String?, built: OwnedAddress?): Any? =
    OwnedAddress.of(url) ?: url?.takeIf { address -> isWebUrl(address) } ?: built

/**
 * [attachments] of [entity] in [app] that have an address: the one the
 * user's server gave, or one [attachmentAddress] builds. The rest have no
 * image, and are left out rather than shown broken.
 */
fun addressed(attachments: List<Attachment>, app: String, entity: String): List<Attachment> =
    attachments.filter { it.url != null || attachmentAddress(app, entity, it.id) != null }

/**
 * What a `mochi:/<entity>` path segment may contain: letters and digits in any
 * script, since a wiki page's name may be written in its own language, and
 * `.`, `_` and `-`. Nothing that separates a path or a query, escapes one, or
 * reorders or hides text gets through. See [entityDeepLink].
 */
private val ROUTE_SEGMENT = Regex("[\\p{L}\\p{N}._-]{1,128}")

/**
 * Build the in-app route for a `mochi:/<entity>[/<sub>...]` intent, or null
 * when it cannot be trusted.
 *
 * The activity that receives these is exported, so [app] and every segment ride
 * in an intent any installed app can send. [app] must name one of [apps] - the
 * features this build hosts - and every segment must be a plain identifier, so
 * a sender cannot smuggle its own path or query into the route.
 */
fun entityDeepLink(app: String?, segments: List<String>, apps: List<String>): String? {
    if (app == null || app !in apps) return null
    val entity = segments.firstOrNull() ?: return null
    // `.` and `..` match the character class but are path traversal, not names.
    if (segments.any { !it.matches(ROUTE_SEGMENT) || it == "." || it == ".." }) return null
    return buildString {
        append('/').append(app).append('/').append(entity)
        for (s in segments.drop(1)) append('/').append(s)
    }
}

/**
 * Hand [link] to the system share sheet, whose targets include Copy, so a
 * screen needs no copy button of its own. [title] heads the sheet: Android 10
 * and later ignore the chooser's title and read "Sharing text" without it.
 */
fun shareLink(context: Context, link: String, title: String) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, link)
        putExtra(Intent.EXTRA_TITLE, title)
    }
    val chooser = Intent.createChooser(intent, title)
    chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    context.startActivity(chooser)
}

// A flight number on its own: an airline's two-character code (two letters, or
// a letter and a digit) and one to four digits with an optional letter, with or
// without a space between. matchEntire anchors both patterns.
private val BARE = Regex("([A-Z]{2}|[A-Z][0-9]|[0-9][A-Z]) ?([0-9]{1,4}[A-Z]?)")
// The airline's name and then its flight number, "Aer Lingus EI59": only the
// two-letter code form after a name, so "Gate B12" is not a flight.
private val NAMED = Regex(".*\\s([A-Z]{2}) ?([0-9]{1,4}[A-Z]?)")

/**
 * The flight number a location holds, or null. A location is a flight when it
 * is a flight number alone, or an airline's name followed by one, and nothing
 * else, so a street address, "EI59 gate 12" or "Alaska 1342" is not. Answers
 * the number normalised, upper case and unspaced. The web's flightNumber()
 * reads the same shape.
 */
fun flightNumber(location: String): String? {
    val text = location.trim().uppercase()
    val match = BARE.matchEntire(text) ?: NAMED.matchEntire(text) ?: return null
    return match.groupValues[1] + match.groupValues[2]
}

/**
 * A search for a free-text place on the user's map service, as the web's
 * mapLink() builds it: the encoder's `+` for a space becomes `%20`.
 */
fun mapLink(location: String, service: Maps): String {
    val query = URLEncoder.encode(location.trim(), "UTF-8").replace("+", "%20")
    return when (service) {
        Maps.GOOGLE -> "https://www.google.com/maps/search/?api=1&query=$query"
        Maps.OPENSTREETMAP -> "https://www.openstreetmap.org/search?query=$query"
    }
}

/** A flight's page on the user's tracker, by its number as [flightNumber] answers it. */
fun flightLink(flight: String, service: Flights): String = when (service) {
    Flights.FLIGHTAWARE -> "https://www.flightaware.com/live/flight/" + URLEncoder.encode(flight, "UTF-8")
    Flights.FLIGHTRADAR24 -> "https://www.flightradar24.com/data/flights/" + URLEncoder.encode(flight.lowercase(), "UTF-8")
}
