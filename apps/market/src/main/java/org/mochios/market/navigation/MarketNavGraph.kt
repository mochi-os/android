// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.market.navigation

import android.net.Uri
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import org.mochios.market.ui.account.AccountSettingsScreen
import org.mochios.market.ui.account.PublicProfileScreen
import org.mochios.market.ui.account.SellerSettingsScreen
import org.mochios.market.ui.account.StripeOauthReturn
import org.mochios.market.ui.browse.HomeScreen
import org.mochios.market.ui.buying.MyBidsScreen
import org.mochios.market.ui.buying.MyPurchasesScreen
import org.mochios.market.ui.buying.MySubscriptionsScreen
import org.mochios.market.ui.buying.PurchaseDetailScreen
import org.mochios.market.ui.buying.SavedListingsScreen
import org.mochios.market.ui.checkout.CheckoutScreen
import org.mochios.market.ui.editor.EditListingScreen
import org.mochios.market.ui.listing.ListingDetailScreen
import org.mochios.market.ui.messages.MessageThreadScreen
import org.mochios.market.ui.messages.MessagesInboxScreen
import org.mochios.market.ui.reviews.ReviewsScreen
import org.mochios.market.ui.selling.CreateListingScreen
import org.mochios.market.ui.selling.MyListingsScreen
import org.mochios.market.ui.selling.MySalesScreen
import org.mochios.market.ui.selling.MySubscribersScreen
import org.mochios.market.ui.selling.SaleDetailScreen

/**
 * Market routes. All class-level (no entity prefix) and all assume a signed-in
 * session.
 */
object MarketApp {
    // ---- Class-level routes ----
    const val HOME = "market"
    /** Pattern used when navigating to the browse screen pre-filtered by category. */
    const val HOME_PATTERN = "market?category={category}"
    const val LISTINGS = "market/listings"
    const val SALES = "market/sales"
    const val SUBSCRIBERS = "market/subscribers"
    const val PURCHASES = "market/purchases"
    const val SAVED = "market/saved"
    const val BIDS = "market/bids"
    const val SUBSCRIPTIONS = "market/subscriptions"
    const val MESSAGES = "market/messages"
    const val REVIEWS = "market/reviews"
    const val ACCOUNT = "market/account"
    const val SELLER_SETTINGS = "market/account/seller"
    /**
     * The seller settings route also receives Stripe's OAuth return: the
     * callback hands an app-platform state to `mochi://market/stripe/oauth`,
     * and MainActivity maps it here with Stripe's raw parameters.
     */
    const val SELLER_SETTINGS_PATTERN =
        "market/account/seller?code={code}&state={state}&error={error}&error_description={error_description}"

    // ---- Detail route patterns ----
    const val LISTING_DETAIL = "market/listing/{id}?title={title}"
    const val LISTING_EDIT = "market/listing/{id}/edit"
    const val CREATE_LISTING = "market/listings/create"
    const val NEW_LISTING = "market/listings/new?title={title}"
    const val CHECKOUT = "market/checkout/{listingId}"
    const val PURCHASE_DETAIL = "market/purchases/{orderId}"
    const val SALE_DETAIL = "market/sales/{orderId}"
    const val MESSAGE_THREAD = "market/messages/{listingId}/{threadId}"
    const val PROFILE_PATTERN = "market/account/{accountId}"

    // ---- Detail route builders ----
    fun listingDetail(id: String, title: String? = null): String {
        val query = title?.takeIf { text -> text.isNotBlank() }?.let { text -> "?title=${Uri.encode(text)}" }
        return "market/listing/$id${query.orEmpty()}"
    }
    fun listingEdit(id: String) = "market/listing/$id/edit"
    // Title comes from CreateListingScreen; the editor seeds its state with it
    // and still creates the listing row lazily on first save.
    fun newListing(title: String) = "market/listings/new?title=" + Uri.encode(title)
    fun checkout(listingId: String) = "market/checkout/$listingId"
    fun purchaseDetail(orderId: String) = "market/purchases/$orderId"
    fun saleDetail(orderId: String) = "market/sales/$orderId"
    fun messageThread(listingId: String, threadId: String) =
        "market/messages/$listingId/$threadId"
    fun publicProfile(accountId: String) = "market/account/$accountId"

    /** Seller settings carrying Stripe's OAuth return, for the deep-link handler. */
    fun sellerSettings(code: String?, state: String?, error: String?, errorDescription: String?): String {
        val query = listOf("code" to code, "state" to state, "error" to error, "error_description" to errorDescription)
            .filter { !it.second.isNullOrBlank() }
            .joinToString("&") { (name, value) -> name + "=" + Uri.encode(value) }
        return if (query.isEmpty()) SELLER_SETTINGS else "$SELLER_SETTINGS?$query"
    }

    /** Browse pre-filtered by category id. */
    fun homeWithCategory(categoryId: String) = "market?category=${Uri.encode(categoryId)}"

    /**
     * The screens a `/market/...` link opens over the market home, in order,
     * from the path after `market/` and a reader of its query: a listing
     * (`listings/<id>` as the server writes it, or `listing/<id>` as this
     * client's own shortcuts do) with its conversation on top when the link
     * names one (`listings/<id>/messages/<thread>`), the bids, a purchase or
     * the purchases, the subscriptions, or the seller settings a Stripe return
     * carries. Empty for a link that names none of them: the home alone.
     */
    fun linkRoutes(parts: List<String>, query: (String) -> String?): List<String> {
        val id = parts.getOrNull(1)?.takeIf { it.isNotEmpty() }
        return when (parts.getOrNull(0)) {
            "listing", "listings" -> {
                if (id == null) return emptyList()
                val thread = parts.getOrNull(3)?.takeIf { parts.getOrNull(2) == "messages" && it.isNotEmpty() }
                listOfNotNull(listingDetail(id), thread?.let { messageThread(id, it) })
            }
            "bids" -> listOf(BIDS)
            "purchases" -> listOf(if (id != null) purchaseDetail(id) else PURCHASES)
            "subscriptions" -> listOf(SUBSCRIPTIONS)
            "account" -> if (id == "seller") {
                listOf(sellerSettings(query("code"), query("state"), query("error"), query("error_description")))
            } else {
                emptyList()
            }
            else -> emptyList()
        }
    }
}

/**
 * Registers every market route; detail screens read their id from
 * SavedStateHandle in the ViewModel.
 */
fun NavGraphBuilder.marketNavGraph(
    navController: NavController,
    onOpenNotifications: () -> Unit = {},
) {
    // ---- Class-level routes ----
    composable(
        route = MarketApp.HOME_PATTERN,
        arguments = listOf(
            navArgument("category") {
                type = NavType.StringType
                nullable = true
                defaultValue = null
            },
        ),
    ) {
        HomeScreen(
            navController = navController,
            onOpenNotifications = onOpenNotifications,
        )
    }
    composable(MarketApp.LISTINGS) { MyListingsScreen(navController = navController) }
    composable(MarketApp.SALES) { MySalesScreen(navController = navController) }
    composable(MarketApp.SUBSCRIBERS) { MySubscribersScreen(navController = navController) }
    composable(MarketApp.PURCHASES) { MyPurchasesScreen(navController = navController) }
    composable(MarketApp.SAVED) { SavedListingsScreen(navController = navController) }
    composable(MarketApp.BIDS) { MyBidsScreen(navController = navController) }
    composable(MarketApp.SUBSCRIPTIONS) { MySubscriptionsScreen(navController = navController) }
    composable(MarketApp.MESSAGES) { MessagesInboxScreen(navController = navController) }
    composable(MarketApp.REVIEWS) { ReviewsScreen(navController = navController) }
    composable(MarketApp.ACCOUNT) { AccountSettingsScreen(navController = navController) }
    composable(
        route = MarketApp.SELLER_SETTINGS_PATTERN,
        arguments = listOf("code", "state", "error", "error_description").map { name ->
            navArgument(name) {
                type = NavType.StringType
                nullable = true
                defaultValue = null
            }
        },
    ) { entry ->
        SellerSettingsScreen(
            navController = navController,
            stripeReturn = StripeOauthReturn.from(entry.arguments),
        )
    }

    // ---- Detail routes ----
    composable(MarketApp.CREATE_LISTING) { CreateListingScreen(navController = navController) }
    composable(
        route = MarketApp.NEW_LISTING,
        arguments = listOf(navArgument("title") { type = NavType.StringType; defaultValue = "" }),
    ) { EditListingScreen(navController = navController) }
    composable(
        route = MarketApp.LISTING_DETAIL,
        arguments = listOf(
            navArgument("id") { type = NavType.StringType },
            navArgument("title") {
                type = NavType.StringType
                nullable = true
                defaultValue = null
            },
        ),
    ) { entry ->
        val id = entry.arguments?.getString("id").orEmpty()
        ListingDetailScreen(
            listingId = id,
            navController = navController,
            initialTitle = entry.arguments?.getString("title").orEmpty(),
        )
    }
    composable(
        route = MarketApp.LISTING_EDIT,
        arguments = listOf(navArgument("id") { type = NavType.StringType }),
    ) { EditListingScreen(navController = navController) }
    composable(
        route = MarketApp.CHECKOUT,
        arguments = listOf(navArgument("listingId") { type = NavType.StringType }),
    ) { CheckoutScreen(navController = navController) }
    composable(
        route = MarketApp.PURCHASE_DETAIL,
        arguments = listOf(navArgument("orderId") { type = NavType.StringType }),
    ) { PurchaseDetailScreen(navController = navController) }
    composable(
        route = MarketApp.SALE_DETAIL,
        arguments = listOf(navArgument("orderId") { type = NavType.StringType }),
    ) { SaleDetailScreen(navController = navController) }
    composable(
        route = MarketApp.MESSAGE_THREAD,
        arguments = listOf(
            navArgument("listingId") { type = NavType.StringType },
            navArgument("threadId") { type = NavType.StringType },
        ),
    ) { MessageThreadScreen(navController = navController) }
    composable(
        route = MarketApp.PROFILE_PATTERN,
        arguments = listOf(navArgument("accountId") { type = NavType.StringType }),
    ) { PublicProfileScreen(navController = navController) }
}
