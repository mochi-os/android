// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.market.repository

import android.net.Uri
import android.os.SystemClock
import com.google.gson.Gson
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.mochios.android.api.MochiError
import org.mochios.android.auth.SessionManager
import org.mochios.android.api.toMochiError
import org.mochios.android.api.unwrap
import org.mochios.android.api.unwrapEmpty
import org.mochios.android.api.unwrapRaw
import org.mochios.android.files.FileRepository
import org.mochios.android.files.FileStore
import org.mochios.android.files.UploadTracker
import org.mochios.market.api.Changed
import org.mochios.market.api.MarketApi
import org.mochios.market.model.Account
import org.mochios.market.model.AccountFees
import org.mochios.market.model.Asset
import org.mochios.market.model.AssetDownload
import org.mochios.market.model.AssetDownloadEnvelope
import org.mochios.market.model.AuditEvent
import org.mochios.market.model.Bid
import org.mochios.market.model.BidResponse
import org.mochios.market.model.Category
import org.mochios.market.model.Dispute
import org.mochios.market.model.Listing
import org.mochios.market.model.ListingDetailResponse
import org.mochios.market.model.ListingsMineResponse
import org.mochios.market.model.ListingsSearchResponse
import org.mochios.market.model.MarketThread
import org.mochios.market.model.Message
import org.mochios.market.model.Order
import org.mochios.market.model.OrderCreateResponse
import org.mochios.market.model.OrderDetailResponse
import org.mochios.market.model.OrdersListResponse
import org.mochios.market.model.Photo
import org.mochios.market.model.RelistAuction
import org.mochios.market.model.RelistResponse
import org.mochios.market.model.RemovalCheck
import org.mochios.market.model.Review
import org.mochios.market.model.ReviewsListResponse
import org.mochios.market.model.ShippingOption
import org.mochios.market.model.ShippingOptionInput
import org.mochios.market.model.ShippingRegions
import org.mochios.market.model.StripeOauthCompletion
import org.mochios.market.model.StripeOnboardingResponse
import org.mochios.market.model.StripeStatus
import org.mochios.market.model.Subscription
import org.mochios.market.model.SubscriptionCreateResponse
import org.mochios.market.model.SubscriptionsListResponse
import org.mochios.market.model.ThreadDetailResponse
import org.mochios.market.model.ThreadsListResponse
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import retrofit2.HttpException

/**
 * Thin wrapper around [MarketApi]: every call unwraps the response and rethrows
 * failures as [MochiError].
 */
@Singleton
class MarketRepository @Inject constructor(
    private val api: MarketApi,
    fileStore: FileStore,
    session: SessionManager? = null,
) : FileRepository(fileStore) {

    private val text = "text/plain".toMediaTypeOrNull()
    private val gson = Gson()

    /** Where shared reads run, apart from the screens that ask for them. */
    private val readScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // ---- Accounts ----

    /** The user's own account, shared by the screens that open together. */
    private val ownAccount = SharedRead(ACCOUNT_LIFETIME, readScope) { readAccount(null) }

    /**
     * The account [id], or the user's own when null. The user's own is shared
     * with other screens asking at the same time and held briefly; [fresh]
     * reads it again, for a check the user asked for.
     */
    suspend fun getAccount(id: String? = null, fresh: Boolean = false): Account {
        if (id != null) {
            return readAccount(id)
        }
        return ownAccount.get(fresh)
    }

    private suspend fun readAccount(id: String?): Account {
        return try {
            api.getAccount(id).unwrap()
        } catch (e: Exception) {
            throw e.toMochiError()
        }
    }

    suspend fun updateAccount(fields: Map<String, String?>): Account {
        return try {
            api.updateAccount(
                biography = fields["biography"],
                location = fields["location"],
                business = fields["business"],
                company = fields["company"],
                vat = fields["vat"],
                addressName = fields["address_name"],
                addressLine1 = fields["address_line1"],
                addressLine2 = fields["address_line2"],
                addressCity = fields["address_city"],
                addressRegion = fields["address_region"],
                addressPostcode = fields["address_postcode"],
                addressCountry = fields["address_country"],
            ).unwrap().also { account -> ownAccount.put(account) }
        } catch (e: Exception) {
            throw e.toMochiError()
        }
    }

    suspend fun activateAccount(returnUrl: String? = null): Account {
        return try {
            api.activateAccount(returnUrl).unwrap().also { account -> ownAccount.put(account) }
        } catch (e: Exception) {
            throw e.toMochiError()
        }
    }

    suspend fun getFees(): AccountFees {
        return try {
            api.getAccountFees().unwrap()
        } catch (e: Exception) {
            throw e.toMochiError()
        }
    }

    /**
     * `client_platform=android` makes the Comptroller mint an app-platform
     * state, so Stripe's callback is handed to `mochi://market/stripe/oauth`
     * for [completeStripeOauth] instead of a web session the browser lacks.
     */
    suspend fun stripeOnboarding(returnUrl: String): StripeOnboardingResponse {
        return try {
            api.startStripeOnboarding(returnUrl, clientPlatform = ANDROID_PLATFORM).unwrap()
                .also { ownAccount.forget() }
        } catch (e: Exception) {
            throw e.toMochiError()
        }
    }

    suspend fun completeStripeOauth(
        code: String?,
        state: String?,
        error: String?,
        errorDescription: String?,
    ): StripeOauthCompletion {
        return try {
            api.completeStripeOauth(code, state, error, errorDescription).unwrap()
                .also { ownAccount.forget() }
        } catch (e: Exception) {
            throw e.toMochiError()
        }
    }

    suspend fun stripeStatus(): StripeStatus {
        return try {
            api.getStripeStatus().unwrap()
        } catch (e: Exception) {
            throw e.toMochiError()
        }
    }

    // ---- Categories ----

    /** The marketplace's categories, which change rarely, read once an hour at most. */
    private val categories = SharedRead(CATEGORIES_LIFETIME, readScope) {
        try {
            api.listCategories().unwrap()
        } catch (e: Exception) {
            throw e.toMochiError()
        }
    }

    suspend fun listCategories(): List<Category> = categories.get()

    // ---- Listings ----

    suspend fun createListing(fields: Map<String, String?>): Listing {
        return try {
            api.createListing(
                title = fields["title"].orEmpty(),
                description = fields["description"],
                category = fields["category"],
                condition = fields["condition"],
                type = fields["type"],
                pricing = fields["pricing"],
                price = fields["price"],
                currency = fields["currency"],
                interval = fields["interval"],
                pickup = fields["pickup"],
                shipping = fields["shipping"],
                location = fields["location"],
                information = fields["information"],
                quantity = fields["quantity"],
                tagsJson = fields["tags"],
            ).unwrap()
        } catch (e: Exception) {
            throw e.toMochiError()
        }
    }

    suspend fun updateListing(fields: Map<String, String?>): Listing {
        val id = fields["id"]
            ?: throw IllegalArgumentException("updateListing requires id")
        return try {
            api.updateListing(
                id = id,
                title = fields["title"],
                description = fields["description"],
                category = fields["category"],
                condition = fields["condition"],
                type = fields["type"],
                pricing = fields["pricing"],
                price = fields["price"],
                currency = fields["currency"],
                interval = fields["interval"],
                pickup = fields["pickup"],
                shipping = fields["shipping"],
                location = fields["location"],
                information = fields["information"],
                quantity = fields["quantity"],
                tagsJson = fields["tags"],
            ).unwrap().also { knownListings.remove(id) }
        } catch (e: Exception) {
            throw e.toMochiError()
        }
    }

    /**
     * Release the caller's in-progress checkout on a listing. Its answer
     * carries no data, so it is not read (unwrapEmpty), as for the deletes.
     */
    suspend fun cancelReservation(listing: String) {
        try {
            api.cancelReservation(listing).unwrapEmpty()
        } catch (e: Exception) {
            throw e.toMochiError()
        }
    }

    suspend fun deleteListing(id: String) {
        try {
            api.deleteListing(id.toString()).unwrapEmpty()
            knownListings.remove(id)
        } catch (e: Exception) {
            throw e.toMochiError()
        }
    }

    suspend fun listingRemovalCheck(id: String): RemovalCheck {
        return try {
            api.removalCheckListing(id.toString()).unwrap()
        } catch (e: Exception) {
            throw e.toMochiError()
        }
    }

    suspend fun publishListing(fields: Map<String, String?>): Changed<Listing> {
        val id = fields["id"]
            ?: throw IllegalArgumentException("publishListing requires id")
        return try {
            api.publishListing(
                id = id,
                reserve = fields["reserve"],
                instant = fields["instant"],
                opens = fields["opens"],
                closes = fields["closes"],
                extend = fields["extend"],
                extension = fields["extension"],
            ).unwrapRaw().also { knownListings.remove(id) }
        } catch (e: Exception) {
            throw e.toMochiError()
        }
    }

    /**
     * Relist [id] as a new draft. An auction's settings come back with it and
     * are held for the editor that opens the draft, which takes them once
     * ([takeRelisted]).
     */
    suspend fun relistListing(id: String): RelistResponse {
        return try {
            api.relistListing(id.toString()).unwrap().also { response ->
                knownListings.remove(id)
                val auction = response.auction
                if (auction != null && response.listing.id.isNotEmpty()) {
                    relisted[response.listing.id] = auction
                }
            }
        } catch (e: Exception) {
            throw e.toMochiError()
        }
    }

    /** Auction settings of relisted drafts, by the draft's id, until the editor takes them. */
    private val relisted = ConcurrentHashMap<String, RelistAuction>()

    /** The auction settings draft [id] was relisted with, once; null after or otherwise. */
    fun takeRelisted(id: String): RelistAuction? = relisted.remove(id)

    suspend fun searchListings(params: Map<String, String?>): ListingsSearchResponse {
        return try {
            api.searchListings(
                query = params["query"],
                category = params["category"],
                type = params["type"],
                condition = params["condition"],
                pricing = params["pricing"],
                min = params["min"],
                max = params["max"],
                currency = params["currency"],
                delivery = params["delivery"],
                location = params["location"],
                sort = params["sort"],
                page = params["page"]?.toIntOrNull(),
                limit = params["limit"]?.toIntOrNull(),
                after = params["after"],
            ).unwrap()
        } catch (e: Exception) {
            throw e.toMochiError()
        }
    }

    /** A listing read or shown lately, and when, for [getListingsByIds]. */
    private class KnownListing(val listing: Listing, val read: Long)

    /** Listings by id, as last read or shown, for strips that list them again. */
    private val knownListings = ConcurrentHashMap<String, KnownListing>()

    /** The listing pages' reads under way, which [getListingsByIds] may join. */
    private val listingReads = ConcurrentHashMap<String, Deferred<ListingDetailResponse>>()

    /**
     * Read listing [id] for its page, always from the server. Its answer is
     * kept for [getListingsByIds], and a strip asking for it meanwhile waits on
     * this read rather than making its own.
     */
    suspend fun getListing(id: String): ListingDetailResponse {
        val read = readScope.async {
            try {
                api.getListing(id).unwrap()
            } catch (e: Exception) {
                throw e.toMochiError()
            }
        }
        listingReads[id] = read
        try {
            val response = read.await()
            remember(response.listing)
            return response
        } finally {
            listingReads.remove(id, read)
        }
    }

    /** Keep [listing], as a card just showed it, for [getListingsByIds]. */
    fun remember(listing: Listing) {
        knownListings[listing.id] = KnownListing(listing, SystemClock.elapsedRealtime())
    }

    /**
     * Resolve listings by id, in the order asked: from those read or shown in
     * the last [KNOWN_LISTING_LIFETIME] milliseconds, then a listing page's read
     * under way, and the rest in one `listings/viewer/batch` call. An id the
     * batch leaves out is gone (removed, held or unknown) and is left out
     * here; a batch call that fails throws, so a caller never takes its ids
     * for gone.
     */
    suspend fun getListingsByIds(ids: List<String>): List<Listing> {
        val now = SystemClock.elapsedRealtime()
        val found = HashMap<String, Listing>()
        for (id in ids) {
            knownListings[id]?.takeIf { held -> now - held.read < KNOWN_LISTING_LIFETIME }
                ?.let { held -> found[id] = held.listing }
        }
        for (id in ids) {
            if (id in found) continue
            val reading = listingReads[id] ?: continue
            try {
                found[id] = reading.await().listing
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // Left to the batch below.
            }
        }
        val rest = ids.filter { id -> id !in found }.distinct()
        for (chunk in rest.chunked(BATCH_LIMIT)) {
            val answered = try {
                api.batchListings(gson.toJson(chunk)).unwrap().listings
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                throw e.toMochiError()
            }
            for (listing in answered) {
                remember(listing)
                found[listing.id] = listing
            }
        }
        return ids.mapNotNull { id -> found[id] }
    }

    suspend fun mineListings(
        status: String? = null,
        query: String? = null,
        page: Int? = null,
        limit: Int? = null,
        after: String? = null,
    ): ListingsMineResponse {
        return try {
            api.listMyListings(status, query, page, limit, after).unwrap()
        } catch (e: Exception) {
            throw e.toMochiError()
        }
    }

    suspend fun appealListing(id: String, reason: String): Listing {
        return try {
            api.appealListing(id.toString(), reason).unwrap()
        } catch (e: Exception) {
            throw e.toMochiError()
        }
    }

    // ---- Shipping ----

    suspend fun setShipping(
        listing: String,
        options: List<ShippingOptionInput>,
    ): List<ShippingOption> {
        return try {
            api.setShipping(listing, gson.toJson(options)).unwrap()
        } catch (e: Exception) {
            throw e.toMochiError()
        }
    }

    /** The countries and region groups, read once for the session. */
    private val regions = SharedRead(REGIONS_LIFETIME, readScope) {
        try {
            api.shippingRegions().unwrap()
        } catch (e: Exception) {
            throw e.toMochiError()
        }
    }

    suspend fun shippingRegions(): ShippingRegions = regions.get()

    // ---- Photos ----

    suspend fun uploadPhoto(listing: String, file: File, tracker: UploadTracker? = null): Changed<Photo> {
        return try {
            val listingPart = listing.toString().toRequestBody(text)
            val filePart = multipart("file", file, tracker)
            api.uploadPhoto(listingPart, filePart).unwrapRaw()
        } catch (e: Exception) {
            throw e.toMochiError()
        }
    }

    suspend fun listPhotos(listing: String): List<Photo> {
        return try {
            api.listPhotos(listing.toString()).unwrap()
        } catch (e: Exception) {
            throw e.toMochiError()
        }
    }

    suspend fun listOwnedPhotos(listing: String): List<Photo> {
        return try {
            api.listOwnedPhotos(listing.toString()).unwrap()
        } catch (e: Exception) {
            throw e.toMochiError()
        }
    }

    /** Delete a photo of [listing]; answers what the listing's copies went without. */
    suspend fun deletePhoto(id: String, listing: String): List<String> {
        return try {
            api.deletePhoto(id, listing).unwrapRaw().unsent.orEmpty()
        } catch (e: Exception) {
            throw e.toMochiError()
        }
    }

    /**
     * Reorder [listing]'s photos; answers them in the server's new order, and
     * what its copies went without.
     */
    suspend fun reorderPhotos(listing: String, ids: List<String>): Changed<List<Photo>> {
        return try {
            api.reorderPhotos(listing.toString(), gson.toJson(ids)).unwrapRaw()
        } catch (e: Exception) {
            throw e.toMochiError()
        }
    }

    // ---- Assets ----

    suspend fun uploadAsset(listing: String, file: File, tracker: UploadTracker? = null): Asset {
        return try {
            val listingPart = listing.toString().toRequestBody(text)
            val filePart = multipart("file", file, tracker)
            api.uploadAsset(listingPart, filePart).unwrap()
        } catch (e: Exception) {
            throw e.toMochiError()
        }
    }

    suspend fun externalAsset(
        listing: String,
        filename: String,
        mime: String,
        reference: String,
    ): List<Asset> {
        return try {
            api.createExternalAsset(listing.toString(), filename, mime, reference).unwrap()
        } catch (e: Exception) {
            throw e.toMochiError()
        }
    }

    /** Remove an asset; its answer carries no data (unwrapEmpty). */
    suspend fun removeAsset(id: String) {
        try {
            api.removeAsset(id.toString()).unwrapEmpty()
        } catch (e: Exception) {
            throw e.toMochiError()
        }
    }

    /** Reorder [listing]'s assets; answers them in the server's new order. */
    suspend fun reorderAssets(listing: String, ids: List<String>): List<Asset> {
        return try {
            api.reorderAssets(listing.toString(), gson.toJson(ids)).unwrap()
        } catch (e: Exception) {
            throw e.toMochiError()
        }
    }

    /**
     * Fetch a purchased [asset]: an external one answers a JSON envelope naming
     * its URL, a hosted one its bytes with the seller's own type, which may be
     * JSON too, so the asset's `hosting` decides how the answer is read, as the
     * web does. A hosted file is named from the asset row, which holds its full
     * name where the header's plain filename drops every non-ASCII letter. It
     * cannot go through a Custom Tab - the action is not public and the tab
     * carries neither bearer token nor session cookie.
     */
    suspend fun downloadAsset(asset: Asset): AssetDownload {
        val response = try {
            api.downloadAsset(asset.id)
        } catch (e: Exception) {
            throw e.toMochiError()
        }
        if (!response.isSuccessful) {
            throw HttpException(response).toMochiError()
        }
        val body = response.body() ?: throw MochiError.Unknown()
        if (asset.hosting == "external") {
            val envelope = gson.fromJson(body.string(), AssetDownloadEnvelope::class.java)
            val reference = envelope?.data?.asset?.reference.orEmpty()
            if (reference.isBlank()) throw MochiError.Unknown()
            return AssetDownload.External(reference)
        }
        val contentType = response.headers()["Content-Type"].orEmpty()
        val name = asset.filename.ifBlank { "asset-${asset.id}" }
        return AssetDownload.Bytes(name, contentType.ifBlank { asset.mime }, body)
    }

    // ---- Bids ----

    suspend fun placeBid(auction: String, amount: Long, ceiling: Long? = null): BidResponse {
        return try {
            api.placeBid(auction.toString(), amount.toString(), ceiling?.toString()).unwrap()
        } catch (e: Exception) {
            throw e.toMochiError()
        }
    }

    data class MyBidsResult(val bids: List<Bid>, val total: Long, val next: String? = null)

    suspend fun myBids(
        status: String? = null,
        page: Int? = null,
        limit: Int? = null,
        after: String? = null,
    ): MyBidsResult {
        return try {
            val r = api.listMyBids(status, page, limit, after).unwrap()
            MyBidsResult(bids = r.bids, total = r.total, next = r.next)
        } catch (e: Exception) {
            throw e.toMochiError()
        }
    }

    // ---- Orders ----

    suspend fun createOrder(fields: Map<String, String?>): OrderCreateResponse {
        val listing = fields["listing"]
            ?: throw IllegalArgumentException("createOrder requires listing")
        return try {
            api.createOrder(
                listing = listing,
                delivery = fields["delivery"],
                option = fields["option"],
                amount = fields["amount"],
                addressName = fields["address_name"],
                addressLine1 = fields["address_line1"],
                addressLine2 = fields["address_line2"],
                addressCity = fields["address_city"],
                addressRegion = fields["address_region"],
                addressPostcode = fields["address_postcode"],
                addressCountry = fields["address_country"],
                successUrl = fields["success_url"],
                cancelUrl = fields["cancel_url"],
                clientPlatform = fields["client_platform"],
            ).unwrap()
        } catch (e: Exception) {
            throw e.toMochiError()
        }
    }

    suspend fun createAuctionOrder(fields: Map<String, String?>): OrderCreateResponse {
        val listing = fields["listing"]
            ?: throw IllegalArgumentException("createAuctionOrder requires listing")
        return try {
            api.createAuctionOrder(
                listing = listing,
                delivery = fields["delivery"],
                option = fields["option"],
                addressName = fields["address_name"],
                addressLine1 = fields["address_line1"],
                addressLine2 = fields["address_line2"],
                addressCity = fields["address_city"],
                addressRegion = fields["address_region"],
                addressPostcode = fields["address_postcode"],
                addressCountry = fields["address_country"],
                successUrl = fields["success_url"],
                cancelUrl = fields["cancel_url"],
                clientPlatform = fields["client_platform"],
            ).unwrap()
        } catch (e: Exception) {
            throw e.toMochiError()
        }
    }

    suspend fun listPurchases(
        status: String? = null,
        page: Int? = null,
        limit: Int? = null,
        after: String? = null,
    ): OrdersListResponse {
        return try {
            api.listPurchases(status, page, limit, after).unwrap()
        } catch (e: Exception) {
            throw e.toMochiError()
        }
    }

    suspend fun listSales(
        status: String? = null,
        page: Int? = null,
        limit: Int? = null,
        after: String? = null,
    ): OrdersListResponse {
        return try {
            api.listSales(status, page, limit, after).unwrap()
        } catch (e: Exception) {
            throw e.toMochiError()
        }
    }

    suspend fun getOrder(id: String): OrderDetailResponse {
        return try {
            api.getOrder(id.toString()).unwrap()
        } catch (e: Exception) {
            throw e.toMochiError()
        }
    }

    suspend fun shipOrder(
        id: String,
        carrier: String? = null,
        tracking: String? = null,
        url: String? = null,
    ): Order {
        return try {
            api.shipOrder(id.toString(), carrier, tracking, url).unwrap()
        } catch (e: Exception) {
            throw e.toMochiError()
        }
    }

    suspend fun confirmOrder(id: String): Order {
        return try {
            api.confirmOrder(id.toString()).unwrap()
        } catch (e: Exception) {
            throw e.toMochiError()
        }
    }

    suspend fun disputeOrder(
        id: String,
        reason: String? = null,
        description: String? = null,
    ): Order {
        return try {
            api.disputeOrder(id.toString(), reason, description).unwrap().order
        } catch (e: Exception) {
            throw e.toMochiError()
        }
    }

    data class RefundResult(val order: Order, val dispute: Dispute?)

    /** Refund [amount] minor units of order [id]; null refunds the whole remainder. */
    suspend fun refundOrder(
        id: String,
        amount: Long? = null,
    ): RefundResult {
        return try {
            val r = api.refundOrder(id.toString(), amount?.toString()).unwrap()
            RefundResult(order = r.order, dispute = r.dispute)
        } catch (e: Exception) {
            throw e.toMochiError()
        }
    }

    // ---- Subscriptions ----

    suspend fun createSubscription(
        listing: String,
        successUrl: String? = null,
        cancelUrl: String? = null,
        clientPlatform: String? = null,
    ): SubscriptionCreateResponse {
        return try {
            api.createSubscription(listing.toString(), successUrl, cancelUrl, clientPlatform).unwrap()
        } catch (e: Exception) {
            throw e.toMochiError()
        }
    }

    suspend fun mySubscriptions(
        status: String? = null,
        page: Int? = null,
        limit: Int? = null,
        after: String? = null,
    ): SubscriptionsListResponse {
        return try {
            api.listMySubscriptions(status, page, limit, after).unwrap()
        } catch (e: Exception) {
            throw e.toMochiError()
        }
    }

    suspend fun listSubscribers(
        listing: String? = null,
        status: String? = null,
        page: Int? = null,
        limit: Int? = null,
        after: String? = null,
    ): SubscriptionsListResponse {
        return try {
            api.listSubscribers(listing?.toString(), status, page, limit, after).unwrap()
        } catch (e: Exception) {
            throw e.toMochiError()
        }
    }

    suspend fun cancelSubscription(id: String): Subscription {
        return try {
            api.cancelSubscription(id.toString()).unwrap()
        } catch (e: Exception) {
            throw e.toMochiError()
        }
    }

    suspend fun pauseSubscription(id: String): Subscription {
        return try {
            api.pauseSubscription(id.toString()).unwrap()
        } catch (e: Exception) {
            throw e.toMochiError()
        }
    }

    suspend fun resumeSubscription(id: String): Subscription {
        return try {
            api.resumeSubscription(id.toString()).unwrap()
        } catch (e: Exception) {
            throw e.toMochiError()
        }
    }

    suspend fun reactivateSubscription(id: String): Subscription {
        return try {
            api.reactivateSubscription(id.toString()).unwrap()
        } catch (e: Exception) {
            throw e.toMochiError()
        }
    }

    // ---- Threads ----

    suspend fun createThread(listing: String, buyer: String? = null): MarketThread {
        return try {
            api.createThread(listing.toString(), buyer).unwrap()
        } catch (e: Exception) {
            throw e.toMochiError()
        }
    }

    suspend fun myThreads(
        role: String? = null,
        page: Int? = null,
        limit: Int? = null,
        after: String? = null,
    ): ThreadsListResponse {
        return try {
            api.listMyThreads(role, page, limit, after).unwrap()
        } catch (e: Exception) {
            throw e.toMochiError()
        }
    }

    suspend fun getThread(id: String): ThreadDetailResponse {
        return try {
            api.getThread(id.toString()).unwrap()
        } catch (e: Exception) {
            throw e.toMochiError()
        }
    }

    // ---- Messages ----

    suspend fun sendMessage(thread: String, body: String): Message {
        return try {
            api.sendMessage(thread.toString(), body).unwrap()
        } catch (e: Exception) {
            throw e.toMochiError()
        }
    }

    suspend fun markMessagesRead(thread: String) {
        try {
            api.markMessagesRead(thread).unwrapEmpty()
        } catch (e: Exception) {
            throw e.toMochiError()
        }
    }

    // ---- Reviews ----

    suspend fun createReview(order: String, rating: Int, text: String? = null): Review {
        return try {
            api.createReview(order.toString(), rating, text).unwrap()
        } catch (e: Exception) {
            throw e.toMochiError()
        }
    }

    suspend fun respondToReview(id: String, response: String): Review {
        return try {
            api.respondReview(id.toString(), response).unwrap()
        } catch (e: Exception) {
            throw e.toMochiError()
        }
    }

    suspend fun accountReviews(
        id: String,
        role: String? = null,
        page: Int? = null,
        limit: Int? = null,
        after: String? = null,
    ): ReviewsListResponse {
        return try {
            api.listAccountReviews(id, role, page, limit, after).unwrap()
        } catch (e: Exception) {
            throw e.toMochiError()
        }
    }

    data class InboxReviewsResult(val reviews: List<Review>, val total: Long, val next: String? = null)

    suspend fun inboxReviews(page: Int? = null, limit: Int? = null, after: String? = null): InboxReviewsResult {
        return try {
            val r = api.listInboxReviews(page, limit, after).unwrap()
            InboxReviewsResult(reviews = r.reviews, total = r.total, next = r.next)
        } catch (e: Exception) {
            throw e.toMochiError()
        }
    }

    data class SentReviewsResult(val reviews: List<Review>, val total: Long, val next: String? = null)

    suspend fun sentReviews(page: Int? = null, limit: Int? = null, after: String? = null): SentReviewsResult {
        return try {
            val r = api.listSentReviews(page, limit, after).unwrap()
            SentReviewsResult(reviews = r.reviews, total = r.total, next = r.next)
        } catch (e: Exception) {
            throw e.toMochiError()
        }
    }

    // ---- Reports ----

    suspend fun createReport(
        target: String,
        type: String,
        reason: String,
        details: String? = null,
        thread: String? = null,
    ) {
        try {
            api.createReport(target, type, reason, details, thread).unwrap()
        } catch (e: Exception) {
            throw e.toMochiError()
        }
    }

    // ---- Disputes ----

    suspend fun respondToDispute(id: String, body: String): Dispute {
        return try {
            api.respondDispute(id.toString(), body).unwrap()
        } catch (e: Exception) {
            throw e.toMochiError()
        }
    }

    // ---- Audit ----

    data class AuditObjectResult(val audit: List<AuditEvent>, val total: Long, val next: String? = null)

    suspend fun auditObject(
        kind: String,
        objectId: String,
        page: Int? = null,
        limit: Int? = null,
        after: String? = null,
    ): AuditObjectResult {
        return try {
            val r = api.getObjectAudit(kind, objectId, page, limit, after).unwrap()
            AuditObjectResult(audit = r.audit, total = r.total, next = r.next)
        } catch (e: Exception) {
            throw e.toMochiError()
        }
    }

    // Last, so every field it clears is in place.
    init {
        // What is held is one account's: its own market account, and listing
        // cards that carry the seller's own fields on their own listings. When
        // another account signs in, none of it is handed to the next.
        session?.let { manager ->
            readScope.launch {
                manager.boundIdentity.distinctUntilChanged().drop(1).collect {
                    ownAccount.forget()
                    knownListings.clear()
                }
            }
        }
    }

    private fun multipart(field: String, file: File, tracker: UploadTracker? = null): MultipartBody.Part =
        fileStore.filePart(field, file, tracker = tracker)

    companion object {
        private const val ANDROID_PLATFORM = "android"

        /** How long the user's own account is handed out before it is read again. */
        private const val ACCOUNT_LIFETIME = 30_000L

        /** How long the categories are handed out before they are read again. */
        private const val CATEGORIES_LIFETIME = 3_600_000L

        /** The shipping regions change only with a server release: held for the session. */
        private const val REGIONS_LIFETIME = Long.MAX_VALUE

        /** How long a listing read or shown stands in for another read of it. */
        private const val KNOWN_LISTING_LIFETIME = 300_000L

        /** The most ids one `listings/viewer/batch` call takes. */
        private const val BATCH_LIMIT = 100
    }
}
