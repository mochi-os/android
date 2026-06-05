package org.mochios.market.navigation

import android.net.Uri
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import org.mochios.market.ui.account.AccountSettingsScreen
import org.mochios.market.ui.account.PublicProfileScreen
import org.mochios.market.ui.browse.HomeScreen
import org.mochios.market.ui.buying.MyBidsScreen
import org.mochios.market.ui.buying.MyPurchasesScreen
import org.mochios.market.ui.buying.MySubscriptionsScreen
import org.mochios.market.ui.buying.PurchaseDetailScreen
import org.mochios.market.ui.buying.SavedListingsScreen
import org.mochios.market.ui.buying.SubscriptionDetailScreen
import org.mochios.market.ui.checkout.CheckoutScreen
import org.mochios.market.ui.editor.EditListingScreen
import org.mochios.market.ui.listing.ListingDetailScreen
import org.mochios.market.ui.messages.MessageThreadScreen
import org.mochios.market.ui.messages.MessagesInboxScreen
import org.mochios.market.ui.reviews.ReviewsScreen
import org.mochios.market.ui.selling.MyListingsScreen
import org.mochios.market.ui.selling.MySalesScreen
import org.mochios.market.ui.selling.MySubscribersScreen
import org.mochios.market.ui.selling.SaleDetailScreen

/**
 * Route constants and helpers for the market Android module.
 *
 * Market is a fully-featured C2C marketplace with auctions, subscriptions,
 * PWYW pricing, digital downloads, physical shipping, Stripe Connect payouts,
 * real-time messaging, disputes, reviews, reports/appeals, and moderation.
 *
 * Routes here are class-level (no entity prefix) because market is a global
 * surface rather than an entity-scoped feature. Detail routes carry an `id`
 * (listing / order / thread / account) within the market namespace.
 *
 * Every route assumes a signed-in session.
 */
object MarketApp {
    // ---- Class-level routes ----
    const val HOME = "market"
    /** Pattern used when navigating to the browse screen pre-filtered by tag or category. */
    const val HOME_PATTERN = "market?tag={tag}&category={category}"
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

    // ---- Detail route patterns ----
    const val LISTING_DETAIL = "market/listing/{id}"
    const val LISTING_EDIT = "market/listing/{id}/edit"
    const val NEW_LISTING = "market/listings/new"
    const val CHECKOUT = "market/checkout/{listingId}"
    const val PURCHASE_DETAIL = "market/purchases/{orderId}"
    const val SALE_DETAIL = "market/sales/{orderId}"
    const val MESSAGE_THREAD = "market/messages/{listingId}/{threadId}"
    const val PROFILE_PATTERN = "market/account/{accountId}"
    const val SUBSCRIPTION_DETAIL = "market/subscriptions/{id}"

    // ---- Detail route builders ----
    fun listingDetail(id: String) = "market/listing/$id"
    fun listingEdit(id: String) = "market/listing/$id/edit"
    fun newListing() = "market/listings/new"
    fun checkout(listingId: String) = "market/checkout/$listingId"
    fun purchaseDetail(orderId: String) = "market/purchases/$orderId"
    fun saleDetail(orderId: String) = "market/sales/$orderId"
    fun messageThread(listingId: String, threadId: String) =
        "market/messages/$listingId/$threadId"
    fun publicProfile(accountId: String) = "market/account/$accountId"
    fun subscriptionDetail(id: String) = "market/subscriptions/$id"

    /** Browse pre-filtered by tag. */
    fun homeWithTag(tag: String) = "market?tag=${Uri.encode(tag)}"

    /** Browse pre-filtered by category id. */
    fun homeWithCategory(categoryId: String) = "market?category=${Uri.encode(categoryId)}"
}

/**
 * Registers every market route on the given [NavGraphBuilder]. Detail screens
 * pull their path argument via SavedStateHandle in their ViewModel, so the
 * composable bodies only forward the NavController.
 */
fun NavGraphBuilder.marketNavGraph(navController: NavController) {
    // ---- Class-level routes ----
    composable(
        route = MarketApp.HOME_PATTERN,
        arguments = listOf(
            navArgument("tag") {
                type = NavType.StringType
                nullable = true
                defaultValue = null
            },
            navArgument("category") {
                type = NavType.StringType
                nullable = true
                defaultValue = null
            },
        ),
    ) { HomeScreen(navController = navController) }
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

    // ---- Detail routes ----
    composable(MarketApp.NEW_LISTING) { EditListingScreen(navController = navController) }
    composable(
        route = MarketApp.LISTING_DETAIL,
        arguments = listOf(navArgument("id") { type = NavType.StringType }),
    ) { entry ->
        val id = entry.arguments?.getString("id").orEmpty()
        ListingDetailScreen(listingId = id, navController = navController)
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
    composable(
        route = MarketApp.SUBSCRIPTION_DETAIL,
        arguments = listOf(navArgument("id") { type = NavType.StringType }),
    ) { SubscriptionDetailScreen(navController = navController) }
}
