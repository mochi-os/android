package org.mochios.market.repository

import com.google.gson.Gson
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.mochios.android.api.toMochiError
import org.mochios.android.api.unwrap
import org.mochios.market.api.MarketApi
import org.mochios.market.model.Account
import org.mochios.market.model.AccountFees
import org.mochios.market.model.Asset
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
import org.mochios.market.model.RelistResponse
import org.mochios.market.model.RemovalCheck
import org.mochios.market.model.Review
import org.mochios.market.model.ReviewsListResponse
import org.mochios.market.model.ShippingOptionInput
import org.mochios.market.model.StripeOnboardingResponse
import org.mochios.market.model.StripeStatus
import org.mochios.market.model.Subscription
import org.mochios.market.model.SubscriptionCreateResponse
import org.mochios.market.model.SubscriptionsListResponse
import org.mochios.market.model.ThreadDetailResponse
import org.mochios.market.model.ThreadsListResponse
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Thin wrapper around [MarketApi]. Mirrors the structure of
 * `WikisRepository`: every method calls `.unwrap()` on the Retrofit
 * response and re-throws any exception as a typed
 * [org.mochios.android.api.MochiError] via [toMochiError] so ViewModels
 * can render localised messages.
 *
 * The market app is a stateless proxy to the Comptroller — every action
 * is a class-context route (`-/<group>/<verb>`) with no entity scope.
 * Listing / order / bid IDs are opaque comptroller uids (String); the wire
 * format is `application/x-www-form-urlencoded`, and the `.toString()` calls
 * below are identity no-ops retained to make the wire serialisation explicit.
 */
@Singleton
class MarketRepository @Inject constructor(
    private val api: MarketApi,
) {

    private val text = "text/plain".toMediaTypeOrNull()
    private val gson = Gson()

    // ---- Accounts ----

    suspend fun getAccount(id: String? = null): Account {
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
            ).unwrap()
        } catch (e: Exception) {
            throw e.toMochiError()
        }
    }

    suspend fun activateAccount(returnUrl: String? = null): Account {
        return try {
            api.activateAccount(returnUrl).unwrap()
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

    suspend fun stripeOnboarding(returnUrl: String): StripeOnboardingResponse {
        return try {
            api.startStripeOnboarding(returnUrl).unwrap()
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

    suspend fun listCategories(): List<Category> {
        return try {
            api.listCategories().unwrap()
        } catch (e: Exception) {
            throw e.toMochiError()
        }
    }

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
            ).unwrap()
        } catch (e: Exception) {
            throw e.toMochiError()
        }
    }

    suspend fun deleteListing(id: String) {
        try {
            api.deleteListing(id.toString()).unwrap()
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

    suspend fun publishListing(fields: Map<String, String?>): Listing {
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
            ).unwrap()
        } catch (e: Exception) {
            throw e.toMochiError()
        }
    }

    suspend fun relistListing(id: String): RelistResponse {
        return try {
            api.relistListing(id.toString()).unwrap()
        } catch (e: Exception) {
            throw e.toMochiError()
        }
    }

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
                delivery = params["delivery"],
                location = params["location"],
                sort = params["sort"],
                page = params["page"]?.toIntOrNull(),
                limit = params["limit"]?.toIntOrNull(),
            ).unwrap()
        } catch (e: Exception) {
            throw e.toMochiError()
        }
    }

    suspend fun getListing(id: String): ListingDetailResponse {
        return try {
            api.getListing(id.toString()).unwrap()
        } catch (e: Exception) {
            throw e.toMochiError()
        }
    }

    /**
     * Resolve a batch of listings by ID. The Comptroller's `listings/search`
     * does not accept an `ids=` parameter, so we fan the per-ID `listings/get`
     * calls out in parallel via `coroutineScope { async }` rather than walking
     * them sequentially. Individual failures (e.g. listing removed server-side)
     * are dropped silently so the caller still gets the rows that did resolve.
     */
    suspend fun getListingsByIds(ids: List<String>): List<Listing> = coroutineScope {
        ids.map { id ->
            async {
                try {
                    api.getListing(id.toString()).unwrap().listing
                } catch (_: Exception) {
                    null
                }
            }
        }.awaitAll().filterNotNull()
    }

    suspend fun mineListings(
        status: String? = null,
        query: String? = null,
        page: Int? = null,
        limit: Int? = null,
    ): ListingsMineResponse {
        return try {
            api.listMyListings(status, query, page, limit).unwrap()
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

    suspend fun setShipping(listing: String, options: List<ShippingOptionInput>) {
        try {
            api.setShipping(listing.toString(), gson.toJson(options)).unwrap()
        } catch (e: Exception) {
            throw e.toMochiError()
        }
    }

    // ---- Photos ----

    suspend fun uploadPhoto(listing: String, file: File): Photo {
        return try {
            val listingPart = listing.toString().toRequestBody(text)
            val filePart = multipart("file", file)
            api.uploadPhoto(listingPart, filePart).unwrap()
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

    suspend fun deletePhoto(id: String) {
        try {
            api.deletePhoto(id).unwrap()
        } catch (e: Exception) {
            throw e.toMochiError()
        }
    }

    suspend fun reorderPhotos(listing: String, ids: List<String>) {
        try {
            api.reorderPhotos(listing.toString(), gson.toJson(ids)).unwrap()
        } catch (e: Exception) {
            throw e.toMochiError()
        }
    }

    // ---- Assets ----

    suspend fun uploadAsset(listing: String, file: File): Asset {
        return try {
            val listingPart = listing.toString().toRequestBody(text)
            val filePart = multipart("file", file)
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

    suspend fun removeAsset(id: String) {
        try {
            api.removeAsset(id.toString()).unwrap()
        } catch (e: Exception) {
            throw e.toMochiError()
        }
    }

    suspend fun reorderAssets(listing: String, ids: List<String>) {
        try {
            api.reorderAssets(listing.toString(), gson.toJson(ids)).unwrap()
        } catch (e: Exception) {
            throw e.toMochiError()
        }
    }

    /**
     * Triggers the download stream from the Comptroller. The actual file
     * bytes are streamed; this repo call returns the ResponseBody for the
     * caller to write to disk, or surface the external reference URL.
     */
    suspend fun downloadAsset(id: String): okhttp3.ResponseBody {
        return try {
            api.downloadAsset(id.toString()).also { response ->
                if (!response.isSuccessful) {
                    throw IllegalStateException("Asset download failed (${response.code()})")
                }
            }.body() ?: throw IllegalStateException("Empty asset download body")
        } catch (e: Exception) {
            throw e.toMochiError()
        }
    }

    // ---- Bids ----

    suspend fun placeBid(auction: String, amount: Long, ceiling: Long? = null): BidResponse {
        return try {
            api.placeBid(auction.toString(), amount.toString(), ceiling?.toString()).unwrap()
        } catch (e: Exception) {
            throw e.toMochiError()
        }
    }

    data class MyBidsResult(val bids: List<Bid>, val total: Long)

    suspend fun myBids(
        status: String? = null,
        page: Int? = null,
        limit: Int? = null,
    ): MyBidsResult {
        return try {
            val r = api.listMyBids(status, page, limit).unwrap()
            MyBidsResult(bids = r.bids, total = r.total)
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
    ): OrdersListResponse {
        return try {
            api.listPurchases(status, page, limit).unwrap()
        } catch (e: Exception) {
            throw e.toMochiError()
        }
    }

    suspend fun listSales(
        status: String? = null,
        page: Int? = null,
        limit: Int? = null,
    ): OrdersListResponse {
        return try {
            api.listSales(status, page, limit).unwrap()
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

    suspend fun refundOrder(
        id: String,
        amount: Long? = null,
        reason: String? = null,
    ): RefundResult {
        return try {
            val r = api.refundOrder(id.toString(), amount?.toString(), reason).unwrap()
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
    ): SubscriptionsListResponse {
        return try {
            api.listMySubscriptions(status, page, limit).unwrap()
        } catch (e: Exception) {
            throw e.toMochiError()
        }
    }

    suspend fun listSubscribers(
        listing: String? = null,
        status: String? = null,
        page: Int? = null,
        limit: Int? = null,
    ): SubscriptionsListResponse {
        return try {
            api.listSubscribers(listing?.toString(), status, page, limit).unwrap()
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
    ): ThreadsListResponse {
        return try {
            api.listMyThreads(role, page, limit).unwrap()
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
            api.markMessagesRead(thread.toString()).unwrap()
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
    ): ReviewsListResponse {
        return try {
            api.listAccountReviews(id, role, page, limit).unwrap()
        } catch (e: Exception) {
            throw e.toMochiError()
        }
    }

    data class InboxReviewsResult(val reviews: List<Review>, val total: Long)

    suspend fun inboxReviews(page: Int? = null, limit: Int? = null): InboxReviewsResult {
        return try {
            val r = api.listInboxReviews(page, limit).unwrap()
            InboxReviewsResult(reviews = r.reviews, total = r.total)
        } catch (e: Exception) {
            throw e.toMochiError()
        }
    }

    data class SentReviewsResult(val reviews: List<Review>, val total: Long)

    suspend fun sentReviews(page: Int? = null, limit: Int? = null): SentReviewsResult {
        return try {
            val r = api.listSentReviews(page, limit).unwrap()
            SentReviewsResult(reviews = r.reviews, total = r.total)
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
    ) {
        try {
            api.createReport(target, type, reason, details).unwrap()
        } catch (e: Exception) {
            throw e.toMochiError()
        }
    }

    // ---- Disputes ----

    suspend fun getDispute(id: String): Dispute {
        return try {
            api.getDispute(id.toString()).unwrap()
        } catch (e: Exception) {
            throw e.toMochiError()
        }
    }

    suspend fun respondToDispute(id: String, body: String): Dispute {
        return try {
            api.respondDispute(id.toString(), body).unwrap()
        } catch (e: Exception) {
            throw e.toMochiError()
        }
    }

    // ---- Audit ----

    data class AuditObjectResult(val audit: List<AuditEvent>, val total: Long)

    suspend fun auditObject(
        kind: String,
        objectId: String,
        page: Int? = null,
        limit: Int? = null,
    ): AuditObjectResult {
        return try {
            val r = api.getObjectAudit(kind, objectId, page, limit).unwrap()
            AuditObjectResult(audit = r.audit, total = r.total)
        } catch (e: Exception) {
            throw e.toMochiError()
        }
    }

    private fun multipart(field: String, file: File): MultipartBody.Part {
        val body: RequestBody = file.asRequestBody("application/octet-stream".toMediaTypeOrNull())
        return MultipartBody.Part.createFormData(field, file.name, body)
    }
}
