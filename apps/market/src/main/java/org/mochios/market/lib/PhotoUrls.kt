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
 * Current market app token, or null when unauthenticated. Coil image requests
 * bypass the Retrofit `Authorization` interceptor, so gated asset URLs (e.g. a
 * user's avatar) must carry the token as a `?token=` query parameter instead.
 */
fun marketToken(context: Context): String? =
    EntryPointAccessors.fromApplication(
        context.applicationContext,
        PhotoUrlEntryPoint::class.java,
    ).sessionManager().getTokenBlocking("market")

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

/**
 * Absolute URL for a market user's avatar asset, or null when [accountId] is
 * blank. The asset endpoint is token-gated, so [token] is appended as a query
 * parameter (Coil requests don't carry the Retrofit `Authorization` header). A
 * user with no uploaded avatar yields a 404, which the caller's avatar
 * component falls back to initials for.
 */
fun userAvatarUrl(accountId: String, baseUrl: String, token: String?): String? {
    val id = accountId.takeIf { it.isNotBlank() } ?: return null
    val url = "$baseUrl/market/-/user/$id/asset/avatar"
    return if (token.isNullOrBlank()) url else "$url?token=$token"
}

/**
 * Resolves the base URL and token once (remembered) and returns the avatar URL
 * for [listing]'s seller, or null when the listing carries no seller id.
 */
@Composable
fun rememberSellerAvatarUrl(listing: Listing): String? {
    val context = LocalContext.current
    val baseUrl = remember(context) { marketBaseUrl(context) }
    val token = remember(context) { marketToken(context) }
    return remember(baseUrl, token, listing.seller) {
        userAvatarUrl(listing.seller, baseUrl, token)
    }
}
