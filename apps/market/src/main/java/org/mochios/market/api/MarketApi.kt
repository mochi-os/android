// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.market.api

import okhttp3.MultipartBody
import okhttp3.RequestBody
import org.mochios.android.api.ApiResponse
import org.mochios.market.model.Account
import org.mochios.market.model.AccountFees
import org.mochios.market.model.AssetListResponse
import org.mochios.market.model.AuditEvent
import org.mochios.market.model.AuditListResponse
import org.mochios.market.model.Bid
import org.mochios.market.model.BidResponse
import org.mochios.market.model.BidsListResponse
import org.mochios.market.model.Category
import org.mochios.market.model.Dispute
import org.mochios.market.model.InboxReviewListResponse
import org.mochios.market.model.Listing
import org.mochios.market.model.ListingDetailResponse
import org.mochios.market.model.ListingsListResponse
import org.mochios.market.model.ListingsSearchResponse
import org.mochios.market.model.MarketThread
import org.mochios.market.model.Message
import org.mochios.market.model.OkResponse
import org.mochios.market.model.Order
import org.mochios.market.model.OrderCreateResponse
import org.mochios.market.model.OrderDetailResponse
import org.mochios.market.model.OrderDisputeResponse
import org.mochios.market.model.OrderRefundResponse
import org.mochios.market.model.OrdersListResponse
import org.mochios.market.model.Photo
import org.mochios.market.model.PhotosListResponse
import org.mochios.market.model.RelistResponse
import org.mochios.market.model.RemovalCheck
import org.mochios.market.model.Review
import org.mochios.market.model.ReviewsListResponse
import org.mochios.market.model.SavedListResponse
import org.mochios.market.model.SavedToggleResponse
import org.mochios.market.model.SentReviewListResponse
import org.mochios.market.model.StripeOnboardingResponse
import org.mochios.market.model.StripeStatus
import org.mochios.market.model.Subscription
import org.mochios.market.model.SubscriptionCreateResponse
import org.mochios.market.model.SubscriptionListResponse
import org.mochios.market.model.ThreadDetailResponse
import org.mochios.market.model.ThreadsListResponse
import retrofit2.Response
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Retrofit interface for the market app, mirroring the `-/...` actions in
 * `apps/market/app.json`. Bound to a per-app Retrofit with baseUrl
 * `<server>/market/`. Mutations post form-urlencoded because the Starlark proxy
 * reads `a.input(...)`; photo and asset uploads are multipart because it reads
 * `a.file(...)`. Static-asset and photo routes are fetched as plain URLs and
 * are not modelled here.
 */
interface MarketApi {

    // ---- Accounts ----

    /** GET own seller account, or someone else's public summary if `id` is set. */
    @GET("-/accounts/get")
    suspend fun getAccount(
        @Query("id") id: String? = null,
    ): Response<ApiResponse<Account>>

    /** Update the current seller account's profile / billing address. */
    @FormUrlEncoded
    @POST("-/accounts/update")
    suspend fun updateAccount(
        @Field("biography") biography: String? = null,
        @Field("location") location: String? = null,
        @Field("business") business: String? = null,
        @Field("company") company: String? = null,
        @Field("vat") vat: String? = null,
        @Field("address_name") addressName: String? = null,
        @Field("address_line1") addressLine1: String? = null,
        @Field("address_line2") addressLine2: String? = null,
        @Field("address_city") addressCity: String? = null,
        @Field("address_region") addressRegion: String? = null,
        @Field("address_postcode") addressPostcode: String? = null,
        @Field("address_country") addressCountry: String? = null,
    ): Response<ApiResponse<Account>>

    /** Activate the seller account; server returns the updated profile. */
    @FormUrlEncoded
    @POST("-/accounts/activate")
    suspend fun activateAccount(
        @Field("return_url") returnUrl: String? = null,
    ): Response<ApiResponse<Account>>

    /**
     * Public fee disclosure (platform percentage + per-currency Stripe
     * minimums and chargeback fees). Safe to call before onboarding.
     */
    @GET("-/accounts/fees")
    suspend fun getAccountFees(): Response<ApiResponse<AccountFees>>

    /**
     * Start Stripe onboarding; the response contains an OAuth authorize
     * URL the browser (or a Custom Tab on Android) should navigate to.
     */
    @FormUrlEncoded
    @POST("-/accounts/stripe/onboarding")
    suspend fun startStripeOnboarding(
        @Field("return_url") returnUrl: String,
    ): Response<ApiResponse<StripeOnboardingResponse>>

    /** Current Stripe Connect charges/payouts capability flags. */
    @GET("-/accounts/stripe/status")
    suspend fun getStripeStatus(): Response<ApiResponse<StripeStatus>>

    /**
     * Stripe's OAuth landing page (HTML). A Custom Tab handles the round trip;
     * Android never calls this directly.
     */
    @GET("-/stripe/oauth/callback")
    suspend fun stripeOauthCallback(
        @Query("code") code: String? = null,
        @Query("state") state: String? = null,
        @Query("error") error: String? = null,
        @Query("error_description") errorDescription: String? = null,
    ): Response<okhttp3.ResponseBody>

    // ---- Categories ----

    /** All marketplace categories (public). */
    @GET("-/categories/list")
    suspend fun listCategories(): Response<ApiResponse<List<Category>>>

    // ---- Person assets (avatar/banner/favicon/style/information) ----

    /**
     * Person-entity asset stream (avatar, banner, favicon, style, information).
     * Normally loaded as an image URL, so the body is returned raw.
     */
    @GET("-/user/{user}/asset/{asset}")
    suspend fun getUserAsset(
        @Path(value = "user", encoded = true) user: String,
        @Path(value = "asset", encoded = true) asset: String,
    ): Response<okhttp3.ResponseBody>

    // ---- Listings ----

    /**
     * Create a draft listing. `tags` is a JSON-encoded array of strings.
     */
    @FormUrlEncoded
    @POST("-/listings/create")
    suspend fun createListing(
        @Field("title") title: String,
        @Field("description") description: String? = null,
        @Field("category") category: String? = null,
        @Field("condition") condition: String? = null,
        @Field("type") type: String? = null,
        @Field("pricing") pricing: String? = null,
        @Field("price") price: String? = null,
        @Field("currency") currency: String? = null,
        @Field("interval") interval: String? = null,
        @Field("pickup") pickup: String? = null,
        @Field("shipping") shipping: String? = null,
        @Field("location") location: String? = null,
        @Field("information") information: String? = null,
        @Field("quantity") quantity: String? = null,
        @Field("tags") tagsJson: String? = null,
    ): Response<ApiResponse<Listing>>

    /** Update an existing draft listing (`tags` is JSON-encoded). */
    @FormUrlEncoded
    @POST("-/listings/update")
    suspend fun updateListing(
        @Field("id") id: String,
        @Field("title") title: String? = null,
        @Field("description") description: String? = null,
        @Field("category") category: String? = null,
        @Field("condition") condition: String? = null,
        @Field("type") type: String? = null,
        @Field("pricing") pricing: String? = null,
        @Field("price") price: String? = null,
        @Field("currency") currency: String? = null,
        @Field("interval") interval: String? = null,
        @Field("pickup") pickup: String? = null,
        @Field("shipping") shipping: String? = null,
        @Field("location") location: String? = null,
        @Field("information") information: String? = null,
        @Field("quantity") quantity: String? = null,
        @Field("tags") tagsJson: String? = null,
    ): Response<ApiResponse<Listing>>

    /**
     * Release the caller's in-progress checkout on a listing, so it offers
     * Buy now again. Comptroller clears the reservation row.
     */
    @FormUrlEncoded
    @POST("-/reservations/cancel")
    suspend fun cancelReservation(
        @Field("listing") listing: String,
    ): Response<ApiResponse<OkResponse>>

    /** Delete a listing by id. */
    @FormUrlEncoded
    @POST("-/listings/delete")
    suspend fun deleteListing(
        @Field("id") id: String,
    ): Response<ApiResponse<OkResponse>>

    @FormUrlEncoded
    @POST("-/listings/removal/check")
    suspend fun removalCheckListing(
        @Field("id") id: String,
    ): Response<ApiResponse<RemovalCheck>>

    /**
     * Publish a draft. The remaining fields apply to auctions; `reserve` is the
     * reserve price and `instant` the buy-now price.
     */
    @FormUrlEncoded
    @POST("-/listings/publish")
    suspend fun publishListing(
        @Field("id") id: String,
        @Field("reserve") reserve: String? = null,
        @Field("instant") instant: String? = null,
        @Field("opens") opens: String? = null,
        @Field("closes") closes: String? = null,
        @Field("extend") extend: String? = null,
        @Field("extension") extension: String? = null,
    ): Response<ApiResponse<Listing>>

    /** Duplicate a listing as a new draft and return both ids. */
    @FormUrlEncoded
    @POST("-/listings/relist")
    suspend fun relistListing(
        @Field("id") id: String,
    ): Response<ApiResponse<RelistResponse>>

    /**
     * Search active listings via the viewer route, which personalises results;
     * the public `-/listings/search` is for anonymous web browsing.
     */
    @GET("-/listings/viewer/search")
    suspend fun searchListings(
        @Query("query") query: String? = null,
        @Query("category") category: String? = null,
        @Query("type") type: String? = null,
        @Query("condition") condition: String? = null,
        @Query("pricing") pricing: String? = null,
        @Query("min") min: String? = null,
        @Query("max") max: String? = null,
        @Query("delivery") delivery: String? = null,
        @Query("location") location: String? = null,
        @Query("sort") sort: String? = null,
        @Query("page") page: Int? = null,
        @Query("limit") limit: Int? = null,
    ): Response<ApiResponse<ListingsSearchResponse>>

    /**
     * Single listing via the viewer route, for the same reason as
     * [searchListings].
     */
    @GET("-/listings/viewer/get")
    suspend fun getListing(
        @Query("id") id: String,
    ): Response<ApiResponse<ListingDetailResponse>>

    /** Current identity's own listings (draft / active / closed). */
    @GET("-/listings/mine")
    suspend fun listMyListings(
        @Query("status") status: String? = null,
        @Query("query") query: String? = null,
        @Query("page") page: Int? = null,
        @Query("limit") limit: Int? = null,
    ): Response<ApiResponse<ListingsListResponse>>

    /** Appeal a listing that has been held or rejected by moderation. */
    @FormUrlEncoded
    @POST("-/listings/appeal")
    suspend fun appealListing(
        @Field("id") id: String,
        @Field("reason") reason: String,
    ): Response<ApiResponse<Listing>>

    // ---- Saved (wishlist) ----

    /** List the current identity's saved (wishlisted) listings. */
    @GET("-/saved/list")
    suspend fun listSaved(): Response<ApiResponse<SavedListResponse>>

    /**
     * Save a listing. `data` is the JSON-encoded [Listing] snapshot the
     * server persists so the saved list can be rendered without re-fetching.
     */
    @FormUrlEncoded
    @POST("-/saved/add")
    suspend fun addSaved(
        @Field("listing") listing: String, // contract-ok: read via _saved_listing_id(a) helper
        @Field("data") data: String,
    ): Response<ApiResponse<SavedToggleResponse>>

    /** Remove a listing from the saved set. */
    @FormUrlEncoded
    @POST("-/saved/remove")
    suspend fun removeSaved(
        @Field("listing") listing: String, // contract-ok: read via _saved_listing_id(a) helper
    ): Response<ApiResponse<SavedToggleResponse>>


    /** Remove all of the current identity's saved listings. */
    @POST("-/saved/clear")
    suspend fun clearSaved(): Response<ApiResponse<SavedToggleResponse>>

    // ---- Shipping ----

    /**
     * Set a listing's shipping options. `options` is a JSON-encoded array.
     */
    @FormUrlEncoded
    @POST("-/shipping/set")
    suspend fun setShipping(
        @Field("listing") listing: String,
        @Field("options") optionsJson: String,
    ): Response<ApiResponse<OkResponse>>

    // ---- Photos ----

    /** Upload one photo for a listing. */
    @Multipart
    @POST("-/photos/upload")
    suspend fun uploadPhoto(
        @Part("listing") listing: RequestBody,
        @Part file: MultipartBody.Part,
    ): Response<ApiResponse<Photo>>

    /** List all photos for a listing (public). */
    @GET("-/photos/list")
    suspend fun listPhotos(
        @Query("listing") listing: String,
    ): Response<ApiResponse<PhotosListResponse>>

    /** Delete a photo by id. */
    @FormUrlEncoded
    @POST("-/photos/delete")
    suspend fun deletePhoto(
        @Field("id") id: String,
    ): Response<ApiResponse<OkResponse>>

    /**
     * Reorder a listing's photos. `ids` is the JSON-encoded array of
     * photo ids in the desired order.
     */
    @FormUrlEncoded
    @POST("-/photos/reorder")
    suspend fun reorderPhotos(
        @Field("listing") listing: String,
        @Field("ids") idsJson: String,
    ): Response<ApiResponse<OkResponse>>

    // ---- Assets (digital downloads) ----

    /** Upload one digital-asset file for a listing. */
    @Multipart
    @POST("-/assets/upload")
    suspend fun uploadAsset(
        @Part("listing") listing: RequestBody,
        @Part file: MultipartBody.Part,
    ): Response<ApiResponse<org.mochios.market.model.Asset>>

    /** Attach an external-URL asset (no file upload). */
    @FormUrlEncoded
    @POST("-/assets/external")
    suspend fun createExternalAsset(
        @Field("listing") listing: String,
        @Field("filename") filename: String,
        @Field("mime") mime: String,
        @Field("reference") reference: String,
    ): Response<ApiResponse<AssetListResponse>>

    /** Remove a single asset from a listing. */
    @FormUrlEncoded
    @POST("-/assets/remove")
    suspend fun removeAsset(
        @Field("id") id: String,
    ): Response<ApiResponse<OkResponse>>

    /** Reorder assets (`ids` is the JSON-encoded array). */
    @FormUrlEncoded
    @POST("-/assets/reorder")
    suspend fun reorderAssets(
        @Field("listing") listing: String,
        @Field("ids") idsJson: String,
    ): Response<ApiResponse<OkResponse>>

    /**
     * Download a purchased asset: either a JSON envelope naming an external URL
     * or the file bytes; see [MarketRepository.downloadAsset].
     */
    @GET("-/assets/download")
    suspend fun downloadAsset(
        @Query("id") id: String,
    ): Response<okhttp3.ResponseBody>

    // ---- Bids ----

    /** Place a bid on an auction listing. */
    @FormUrlEncoded
    @POST("-/bids/place")
    suspend fun placeBid(
        @Field("auction") auction: String,
        @Field("amount") amount: String,
        @Field("ceiling") ceiling: String? = null,
    ): Response<ApiResponse<BidResponse>>

    /** Current identity's own bids across auctions. */
    @GET("-/bids/mine")
    suspend fun listMyBids(
        @Query("status") status: String? = null,
        @Query("page") page: Int? = null,
        @Query("limit") limit: Int? = null,
    ): Response<ApiResponse<BidsListResponse>>

    // ---- Orders ----

    /**
     * Create an order from a fixed-price listing; the response carries
     * the Stripe Checkout URL the browser should navigate to.
     */
    @FormUrlEncoded
    @POST("-/orders/create")
    suspend fun createOrder(
        @Field("listing") listing: String,
        @Field("delivery") delivery: String? = null,
        @Field("option") option: String? = null,
        @Field("amount") amount: String? = null,
        @Field("address_name") addressName: String? = null,
        @Field("address_line1") addressLine1: String? = null,
        @Field("address_line2") addressLine2: String? = null,
        @Field("address_city") addressCity: String? = null,
        @Field("address_region") addressRegion: String? = null,
        @Field("address_postcode") addressPostcode: String? = null,
        @Field("address_country") addressCountry: String? = null,
        @Field("success_url") successUrl: String? = null,
        @Field("cancel_url") cancelUrl: String? = null,
        @Field("client_platform") clientPlatform: String? = null,
    ): Response<ApiResponse<OrderCreateResponse>>

    /** Create an order from a won auction. */
    @FormUrlEncoded
    @POST("-/orders/auction")
    suspend fun createAuctionOrder(
        @Field("listing") listing: String,
        @Field("delivery") delivery: String? = null,
        @Field("option") option: String? = null,
        @Field("address_name") addressName: String? = null,
        @Field("address_line1") addressLine1: String? = null,
        @Field("address_line2") addressLine2: String? = null,
        @Field("address_city") addressCity: String? = null,
        @Field("address_region") addressRegion: String? = null,
        @Field("address_postcode") addressPostcode: String? = null,
        @Field("address_country") addressCountry: String? = null,
        @Field("success_url") successUrl: String? = null,
        @Field("cancel_url") cancelUrl: String? = null,
        @Field("client_platform") clientPlatform: String? = null,
    ): Response<ApiResponse<OrderCreateResponse>>

    /** Orders the current identity has bought. */
    @GET("-/orders/purchases")
    suspend fun listPurchases(
        @Query("status") status: String? = null,
        @Query("page") page: Int? = null,
        @Query("limit") limit: Int? = null,
    ): Response<ApiResponse<OrdersListResponse>>

    /** Orders the current identity has sold. */
    @GET("-/orders/sales")
    suspend fun listSales(
        @Query("status") status: String? = null,
        @Query("page") page: Int? = null,
        @Query("limit") limit: Int? = null,
    ): Response<ApiResponse<OrdersListResponse>>

    /** Detailed view of a single order (with listing, assets, dispute, review). */
    @GET("-/orders/get")
    suspend fun getOrder(
        @Query("id") id: String,
    ): Response<ApiResponse<OrderDetailResponse>>

    /** Seller marks the order as shipped (carrier / tracking / URL optional). */
    @FormUrlEncoded
    @POST("-/orders/ship")
    suspend fun shipOrder(
        @Field("id") id: String,
        @Field("carrier") carrier: String? = null,
        @Field("tracking") tracking: String? = null,
        @Field("url") url: String? = null,
    ): Response<ApiResponse<Order>>

    /** Buyer confirms delivery. */
    @FormUrlEncoded
    @POST("-/orders/confirm")
    suspend fun confirmOrder(
        @Field("id") id: String,
    ): Response<ApiResponse<Order>>

    /** Buyer opens a dispute requesting a refund. */
    @FormUrlEncoded
    @POST("-/orders/dispute")
    suspend fun disputeOrder(
        @Field("id") id: String,
        @Field("reason") reason: String? = null,
        @Field("description") description: String? = null,
    ): Response<ApiResponse<OrderDisputeResponse>>

    /** Seller issues a refund (full or partial); may also resolve the dispute. */
    @FormUrlEncoded
    @POST("-/orders/refund")
    suspend fun refundOrder(
        @Field("id") id: String,
        @Field("amount") amount: String? = null,
        @Field("reason") reason: String? = null,
    ): Response<ApiResponse<OrderRefundResponse>>

    // ---- Subscriptions ----

    /**
     * Create a subscription; response carries the Stripe Checkout URL
     * the browser should navigate to.
     */
    @FormUrlEncoded
    @POST("-/subscriptions/create")
    suspend fun createSubscription(
        @Field("listing") listing: String,
        @Field("success_url") successUrl: String? = null,
        @Field("cancel_url") cancelUrl: String? = null,
        @Field("client_platform") clientPlatform: String? = null,
    ): Response<ApiResponse<SubscriptionCreateResponse>>

    /** Subscriptions the current identity has bought. */
    @GET("-/subscriptions/mine")
    suspend fun listMySubscriptions(
        @Query("status") status: String? = null,
        @Query("page") page: Int? = null,
        @Query("limit") limit: Int? = null,
    ): Response<ApiResponse<SubscriptionListResponse>>

    /** Subscribers on a listing the current identity is selling. */
    @GET("-/subscriptions/subscribers")
    suspend fun listSubscribers(
        @Query("listing") listing: String? = null,
        @Query("status") status: String? = null,
        @Query("page") page: Int? = null,
        @Query("limit") limit: Int? = null,
    ): Response<ApiResponse<SubscriptionListResponse>>

    /** Cancel an active subscription (at end of current period). */
    @FormUrlEncoded
    @POST("-/subscriptions/cancel")
    suspend fun cancelSubscription(
        @Field("id") id: String,
    ): Response<ApiResponse<Subscription>>

    /** Pause an active subscription. */
    @FormUrlEncoded
    @POST("-/subscriptions/pause")
    suspend fun pauseSubscription(
        @Field("id") id: String,
    ): Response<ApiResponse<Subscription>>

    /** Resume a paused subscription. */
    @FormUrlEncoded
    @POST("-/subscriptions/resume")
    suspend fun resumeSubscription(
        @Field("id") id: String,
    ): Response<ApiResponse<Subscription>>

    /** Reactivate a subscription that is scheduled for cancellation. */
    @FormUrlEncoded
    @POST("-/subscriptions/reactivate")
    suspend fun reactivateSubscription(
        @Field("id") id: String,
    ): Response<ApiResponse<Subscription>>

    // ---- Threads (buyer-seller conversations) ----

    /** Open a thread on a listing (buyer may be inferred from session). */
    @FormUrlEncoded
    @POST("-/threads/create")
    suspend fun createThread(
        @Field("listing") listing: String,
        @Field("buyer") buyer: String? = null,
    ): Response<ApiResponse<MarketThread>>

    /** Threads the current identity is in (buyer or seller). */
    @GET("-/threads/mine")
    suspend fun listMyThreads(
        @Query("role") role: String? = null,
        @Query("page") page: Int? = null,
        @Query("limit") limit: Int? = null,
    ): Response<ApiResponse<ThreadsListResponse>>

    /** Single thread with its messages and the related listing. */
    @GET("-/threads/get")
    suspend fun getThread(
        @Query("id") id: String,
    ): Response<ApiResponse<ThreadDetailResponse>>

    // ---- Messages ----

    /** Send a message into an existing thread. */
    @FormUrlEncoded
    @POST("-/messages/send")
    suspend fun sendMessage(
        @Field("thread") thread: String,
        @Field("body") body: String,
    ): Response<ApiResponse<Message>>

    /** Mark all messages in a thread as read by the current identity. */
    @FormUrlEncoded
    @POST("-/messages/read")
    suspend fun markMessagesRead(
        @Field("thread") thread: String,
    ): Response<ApiResponse<OkResponse>>

    // ---- Reviews ----

    /** Buyer (or seller, depending on the side) creates a review on an order. */
    @FormUrlEncoded
    @POST("-/reviews/create")
    suspend fun createReview(
        @Field("order") order: String,
        @Field("rating") rating: Int,
        @Field("text") text: String? = null,
    ): Response<ApiResponse<Review>>

    /** Counterparty responds to a review. */
    @FormUrlEncoded
    @POST("-/reviews/respond")
    suspend fun respondReview(
        @Field("id") id: String,
        @Field("response") response: String,
    ): Response<ApiResponse<Review>>

    /** Reviews of an account (public). */
    @GET("-/reviews/account")
    suspend fun listAccountReviews(
        @Query("id") id: String,
        @Query("role") role: String? = null,
        @Query("page") page: Int? = null,
        @Query("limit") limit: Int? = null,
    ): Response<ApiResponse<ReviewsListResponse>>

    /** Reviews where the current identity is the subject. */
    @GET("-/reviews/inbox")
    suspend fun listInboxReviews(
        @Query("page") page: Int? = null,
        @Query("limit") limit: Int? = null,
    ): Response<ApiResponse<InboxReviewListResponse>>

    /** Reviews the current identity has written. */
    @GET("-/reviews/sent")
    suspend fun listSentReviews(
        @Query("page") page: Int? = null,
        @Query("limit") limit: Int? = null,
    ): Response<ApiResponse<SentReviewListResponse>>

    // ---- Reports ----

    /** File a report against a listing, account, or message. */
    @FormUrlEncoded
    @POST("-/reports/create")
    suspend fun createReport(
        @Field("target") target: String,
        @Field("type") type: String,
        @Field("reason") reason: String,
        @Field("details") details: String? = null,
    ): Response<ApiResponse<OkResponse>>

    // ---- Disputes ----

    /** Detailed view of a single dispute (server enforces ownership/staff). */
    @GET("-/disputes/get")
    suspend fun getDispute(
        @Query("id") id: String,
    ): Response<ApiResponse<Dispute>>

    /** Add a response message to an open dispute. */
    @FormUrlEncoded
    @POST("-/disputes/respond")
    suspend fun respondDispute(
        @Field("id") id: String,
        @Field("body") body: String,
    ): Response<ApiResponse<Dispute>>

    // ---- Audit ----

    /**
     * Per-object audit timeline; the server enforces ownership / staff.
     */
    @GET("-/audit/object")
    suspend fun getObjectAudit(
        @Query("kind") kind: String,
        @Query("object") `object`: String,
        @Query("page") page: Int? = null,
        @Query("limit") limit: Int? = null,
    ): Response<ApiResponse<AuditListResponse>>
}
