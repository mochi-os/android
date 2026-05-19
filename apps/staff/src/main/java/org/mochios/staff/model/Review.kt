package org.mochios.staff.model

import com.google.gson.annotations.SerializedName

/**
 * One row from the staff reviews queue (`reviews/list`).
 *
 * Mirrors `Review` in `apps/staff/web/src/types/reviews.ts`. Reviews are
 * tied to an order; the buyer and seller fields are denormalised for the
 * list view. `rating` is 1-5; `text` is the buyer-submitted body and
 * `response` is the seller's reply. `visible` is a 0/1 int; `status` is one
 * of the lowercase values in [ReviewStatus].
 *
 * `role` indicates which party authored the review (`"buyer"` reviewing a
 * seller, or `"seller"` reviewing a buyer). Several flat fields are nullable
 * because the server only populates them on certain list shapes (e.g.
 * `subject_name` is set when the consumer needs the rated party's display
 * name rendered inline).
 */
data class Review(
    val id: Long = 0,
    val order: Long = 0,
    val reviewer: String = "",
    @SerializedName("reviewer_name") val reviewerName: String? = null,
    val subject: String = "",
    @SerializedName("subject_name") val subjectName: String? = null,
    val buyer: String? = null,
    @SerializedName("buyer_name") val buyerName: String? = null,
    val seller: String? = null,
    @SerializedName("seller_name") val sellerName: String? = null,
    val listing: Long? = null,
    @SerializedName("listing_title") val listingTitle: String? = null,
    val role: String = "",
    val rating: Int = 0,
    val text: String = "",
    val response: String = "",
    val visible: Int = 0,
    val status: String = "",
    val created: Long = 0,
)

/**
 * Result of `reviews/list`. Mirrors `ReviewsResponse` in
 * `apps/staff/web/src/types/reviews.ts`. Named `ReviewsListResponse` here
 * to match the existing api file imports.
 */
data class ReviewsListResponse(
    val reviews: List<Review> = emptyList(),
    val total: Long = 0,
)

/**
 * Review moderation status. Sourced from `event_staff_reviews_action` in
 * `apps/comptroller/starlark/reviews.star`: `hide` writes `hidden`, `remove`
 * writes `removed`, `restore` writes `published`. Unknown statuses must be
 * tolerated.
 */
enum class ReviewStatus {
    @SerializedName("published") PUBLISHED,
    @SerializedName("hidden") HIDDEN,
    @SerializedName("removed") REMOVED,
}

/**
 * Action a moderator can take on a review. The Comptroller validates against
 * this exact set (see `event_staff_reviews_action`):
 *
 * - [HIDE] hides a published review (status -> hidden).
 * - [REMOVE] removes a review (status -> removed).
 * - [RESTORE] restores a hidden/removed review (status -> published).
 */
enum class ReviewAction {
    @SerializedName("hide") HIDE,
    @SerializedName("remove") REMOVE,
    @SerializedName("restore") RESTORE,
}
