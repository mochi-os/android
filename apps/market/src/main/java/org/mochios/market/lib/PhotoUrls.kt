package org.mochios.market.lib

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import org.mochios.android.auth.SessionManager
import org.mochios.market.model.Listing

/**
 * Builds `/market/-/photo/{id}` URLs for listing photos.
 *
 * Centralises the base-URL lookup that the detail screens each previously
 * duplicated as a private `baseUrlForContext`, so list / grid surfaces (e.g.
 * [org.mochios.market.ui.components.ListingCard]) can resolve a thumbnail from
 * the `Listing.photo` they already hold instead of relying on the caller to
 * thread a URL through.
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface PhotoUrlEntryPoint {
    fun sessionManager(): SessionManager
}

/** Resolved server origin without a trailing slash, e.g. `https://host`. */
fun marketBaseUrl(context: Context): String =
    EntryPointAccessors.fromApplication(
        context.applicationContext,
        PhotoUrlEntryPoint::class.java,
    ).sessionManager().getServerUrlBlocking().trimEnd('/')

/**
 * Absolute URL for a listing's primary photo, or null when it has none.
 *
 * Uses the server's `/thumbnail` variant (a small resized stream) rather than
 * the full-resolution `/market/-/photo/{id}` original, so grid cells don't pay
 * to download a full-size photo each. Mirrors [SaleDetailScreen]'s thumbnail.
 */
fun listingThumbnailUrl(listing: Listing, baseUrl: String): String? {
    val id = listing.photo?.id?.takeIf { it.isNotBlank() } ?: return null
    return "$baseUrl/market/-/photo/$id/thumbnail"
}

/**
 * Resolves the base URL once (remembered) and returns the primary-photo URL
 * for [listing], or null when the listing carries no photo.
 */
@Composable
fun rememberListingThumbnailUrl(listing: Listing): String? {
    val context = LocalContext.current
    val baseUrl = remember(context) { marketBaseUrl(context) }
    return remember(baseUrl, listing.photo?.id) {
        listingThumbnailUrl(listing, baseUrl)
    }
}
