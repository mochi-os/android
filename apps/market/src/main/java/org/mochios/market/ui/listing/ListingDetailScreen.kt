package org.mochios.market.ui.listing

import android.content.Intent
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Message
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.mochios.android.auth.SessionManager
import org.mochios.android.i18n.LocalFormat
import org.mochios.android.ui.components.EmptyState
import org.mochios.android.ui.components.EntityAvatar
import org.mochios.android.ui.components.ErrorState
import org.mochios.android.ui.components.LightboxScreen
import org.mochios.android.ui.components.LoadingState
import org.mochios.market.R
import org.mochios.market.lib.formatPrice
import org.mochios.market.lib.locationName
import org.mochios.market.lib.parseLocation
import org.mochios.market.lib.ratingStars
import org.mochios.market.lib.rememberSellerAvatarUrl
import org.mochios.market.model.AccountSummary
import org.mochios.market.model.Asset
import org.mochios.market.model.AuditEvent
import org.mochios.market.model.Bid
import org.mochios.market.model.Condition
import org.mochios.market.model.Currency
import org.mochios.market.model.Listing
import org.mochios.market.model.ListingStatus
import org.mochios.market.model.ListingType
import org.mochios.market.model.Photo
import org.mochios.market.model.PricingModel
import org.mochios.market.model.Review
import org.mochios.market.navigation.MarketApp
import org.mochios.market.repository.MarketRepository
import org.mochios.market.ui.components.AuctionBidHistory
import org.mochios.market.ui.components.AuditTimeline
import org.mochios.market.ui.components.DigitalAssetsList
import org.mochios.market.ui.components.PhotoCarousel
import org.mochios.market.ui.components.PriceDisplay
import org.mochios.market.ui.components.PricingFill
import org.mochios.market.ui.components.conditionBadgeColor
import org.mochios.market.ui.components.conditionLabel
import org.mochios.market.ui.components.pricingLabel
import org.mochios.market.ui.components.RatingStarGold
import org.mochios.market.ui.components.RatingStars
import org.mochios.market.ui.components.VerifiedGreen
import org.mochios.market.ui.components.SellerReviewsSection
import org.mochios.market.ui.components.ShippingOptionsTable
import org.mochios.market.ui.components.knownStatusLabel
import org.mochios.market.ui.components.WarningsSection
import org.mochios.market.ui.dialog.PlaceBidDialog
import org.mochios.market.ui.dialog.ReportListingDialog
import androidx.compose.material.icons.filled.Inventory
import androidx.compose.material.icons.outlined.Flag

/**
 * Detail view for a single market listing. Mirrors
 * `apps/market/web/src/features/listing/listing-page.tsx`.
 *
 * The screen owns its lightbox / dialog / loading state and delegates every
 * network mutation to [ListingDetailViewModel]. Navigation requests bubble
 * back through the supplied [NavController] so the route taxonomy stays
 * centralised in [MarketApp].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ListingDetailScreen(
    listingId: String,
    navController: NavController,
    viewModel: ListingDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val sessionManager = remember(context) {
        EntryPointAccessors.fromApplication(
            context.applicationContext,
            ListingDetailEntryPoint::class.java,
        ).sessionManager()
    }
    val currentUserId by produceState<String?>(initialValue = null, sessionManager) {
        value = sessionManager.boundIdentity.first()
    }

    LaunchedEffect(listingId) {
        viewModel.load(listingId)
    }

    // Relisting hops to the editor with the new draft id.
    LaunchedEffect(viewModel) {
        viewModel.navigateToEdit.collect { newId ->
            navController.navigate(MarketApp.listingEdit(newId.toString()))
        }
    }

    val isSaved by viewModel.isSaved().collectAsState(initial = false)
    val isReported by viewModel.isReported().collectAsState(initial = false)

    val snackbarHostState = remember { SnackbarHostState() }
    val snackbarContext = LocalContext.current
    // Surface the view model's one-shot bid / report messages (e.g. instant
    // buy-it-now win, immediate proxy outbid) via the scaffold snackbar.
    LaunchedEffect(viewModel) {
        viewModel.snackbar.collect { message ->
            val text = snackbarContext.getString(
                message.messageRes,
                *message.args.toTypedArray(),
            )
            snackbarHostState.showSnackbar(text)
        }
    }

    var lightboxOpen by remember { mutableStateOf(false) }
    var lightboxIndex by remember { mutableIntStateOf(0) }
    var reportOpen by remember { mutableStateOf(false) }
    var bidOpen by remember { mutableStateOf(false) }
    var auditExpanded by remember { mutableStateOf(false) }
    var submittingReport by remember { mutableStateOf(false) }
    var submittingBid by remember { mutableStateOf(false) }
    var bidErrorMessage by remember { mutableStateOf<String?>(null) }

    val listingTitle = state.listing?.listing?.title.orEmpty()
    val currentListingId = state.listing?.listing?.id

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = listingTitle.ifBlank {
                            stringResource(R.string.market_listing_detail_title)
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.market_back),
                        )
                    }
                },
            )
        },
    ) { padding ->
        Box(modifier = Modifier
            .fillMaxSize()
            .padding(padding)) {
            when {
                state.isLoading && state.listing == null -> {
                    LoadingState()
                }

                state.error != null && state.listing == null -> {
                    ErrorState(
                        error = state.error!!,
                        onRetry = { viewModel.load(listingId) },
                    )
                }

                state.listing == null -> {
                    EmptyState(
                        icon = Icons.Default.Inventory,
                        title = stringResource(R.string.market_listing_detail_not_found),
                    )
                }

                else -> {
                    val response = state.listing!!
                    val listing = response.listing
                    val seller = response.seller
                    val auction = response.auction
                    val isOwner = currentUserId != null && currentUserId == listing.seller

                    ListingDetailContent(
                        listing = listing,
                        detail = response,
                        photos = state.photos,
                        seller = seller,
                        sellerName = seller.name.ifBlank {
                            stringResource(R.string.market_listing_detail_seller_heading)
                        },
                        sellerRating = ratingStars(seller.rating),
                        sellerReviews = seller.reviews.toInt(),
                        sellerSales = seller.sales.toInt(),
                        // Match ListingCard: show the verified tick once the
                        // seller is onboarded (or explicitly verified).
                        sellerVerified = (seller.onboarded ?: 0) > 0 ||
                            (seller.verified ?: 0) >= 2,
                        auction = auction,
                        bids = response.bids,
                        assets = response.assets,
                        audit = state.audit,
                        reviews = state.sellerReviews,
                        isOwner = isOwner,
                        isSaved = isSaved,
                        isReported = isReported,
                        auditExpanded = auditExpanded,
                        onToggleAudit = { auditExpanded = !auditExpanded },
                        onPhotoTap = { i ->
                            lightboxIndex = i
                            lightboxOpen = true
                        },
                        onSellerTap = {
                            navController.navigate(MarketApp.publicProfile(seller.id))
                        },
                        onTagTap = { tag ->
                            navController.navigate(MarketApp.homeWithTag(tag))
                        },
                        onToggleSave = { viewModel.toggleSave() },
                        onReport = { reportOpen = true },
                        onMessageSeller = {
                            navController.navigate(
                                MarketApp.messageThread(listing.id.toString(), "new"),
                            )
                        },
                        onBuyNow = {
                            val auc = response.auction
                            if (auc != null && auc.instant > 0) {
                                viewModel.placeBid(
                                    amount = auc.instant,
                                    ceiling = null,
                                    currency = listing.currency ?: Currency.GBP,
                                    onInstantWin = {
                                        navController.navigate(
                                            MarketApp.checkout(listing.id.toString()),
                                        )
                                    },
                                )
                            }
                        },
                        onPrimaryCta = {
                            when (listing.pricing) {
                                PricingModel.AUCTION -> {
                                    bidErrorMessage = null
                                    bidOpen = true
                                }

                                PricingModel.SUBSCRIPTION -> {
                                    navController.navigate(
                                        "${MarketApp.checkout(listing.id.toString())}?subscription=true",
                                    )
                                }

                                else -> {
                                    navController.navigate(
                                        MarketApp.checkout(listing.id.toString()),
                                    )
                                }
                            }
                        },
                        onEdit = {
                            navController.navigate(MarketApp.listingEdit(listing.id.toString()))
                        },
                        onRelist = { viewModel.relistListing() },
                        onAssetDownload = { asset ->
                            scope.launch {
                                try {
                                    // Hit the server-side download endpoint; for
                                    // externally-hosted assets the response is the
                                    // metadata JSON whose `reference` URL we hand
                                    // off to Custom Tabs.
                                    val baseUrl = sessionManager.getServerUrlBlocking()
                                        .trimEnd('/')
                                    val url = "$baseUrl/market/-/assets/download/${asset.id}"
                                    val intent = CustomTabsIntent.Builder().build()
                                    intent.launchUrl(context, Uri.parse(url))
                                } catch (_: Exception) {
                                    // No browser / malformed URL — silently noop;
                                    // matches the wikis pattern for link taps.
                                }
                            }
                        },
                        currentUserId = currentUserId,
                    )
                }
            }
        }
    }

    if (lightboxOpen) {
        // Same photo list the carousel renders, reused for the lightbox.
        val urls = remember(state.listing, state.photos) {
            buildPhotoUrls(
                state.photos,
                state.listing?.listing,
                sessionManager.getServerUrlBlocking().trimEnd('/'),
            )
        }
        if (urls.isNotEmpty()) {
            LightboxScreen(
                images = urls,
                initialIndex = lightboxIndex.coerceIn(0, urls.size - 1),
                onDismiss = { lightboxOpen = false },
            )
        }
    }

    ReportListingDialog(
        open = reportOpen,
        submitting = submittingReport,
        onSubmit = { reason, details ->
            submittingReport = true
            viewModel.reportListing(reason, details) {
                submittingReport = false
                reportOpen = false
            }
        },
        onDismiss = { reportOpen = false },
    )

    PlaceBidDialog(
        open = bidOpen,
        auction = state.listing?.auction,
        currency = state.listing?.listing?.currency ?: Currency.GBP,
        submitting = submittingBid,
        errorMessage = bidErrorMessage,
        onSubmit = { amount, ceiling, currency ->
            submittingBid = true
            viewModel.placeBid(amount, ceiling, currency) {
                submittingBid = false
                bidOpen = false
            }
        },
        onDismiss = { bidOpen = false },
    )
}

/**
 * Stateless body used by [ListingDetailScreen]. Pulled out so the loading /
 * error / not-found branches stay readable in the parent, and so the layout
 * can be previewed in isolation as the component library lands more pieces.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ListingDetailContent(
    listing: Listing,
    detail: org.mochios.market.model.ListingDetailResponse,
    photos: List<Photo>,
    seller: AccountSummary,
    sellerName: String,
    sellerRating: Float,
    sellerReviews: Int,
    sellerSales: Int,
    sellerVerified: Boolean,
    auction: org.mochios.market.model.Auction?,
    bids: List<Bid>,
    assets: List<Asset>,
    audit: List<AuditEvent>,
    reviews: List<Review>,
    isOwner: Boolean,
    isSaved: Boolean,
    isReported: Boolean,
    auditExpanded: Boolean,
    onToggleAudit: () -> Unit,
    onPhotoTap: (Int) -> Unit,
    onSellerTap: () -> Unit,
    onTagTap: (String) -> Unit,
    onToggleSave: () -> Unit,
    onReport: () -> Unit,
    onMessageSeller: () -> Unit,
    onPrimaryCta: () -> Unit,
    onBuyNow: () -> Unit,
    onEdit: () -> Unit,
    onRelist: () -> Unit,
    onAssetDownload: (Asset) -> Unit,
    currentUserId: String?,
) {
    val tags = remember(listing.tags) { parseTags(listing.tags) }
    val parsedLocation = remember(listing.location) { parseLocation(listing.location) }
    val locationDisplay = locationName(parsedLocation)
    val format = LocalFormat.current
    val context = LocalContext.current
    val photoUrls = remember(detail.listing.id, photos) {
        // Full /-/photo/{id} URL list from the photos endpoint, falling back
        // to the listing's embedded primary photo when that list is empty.
        buildPhotoUrls(photos, detail.listing, baseUrlForContext(context))
    }
    val sellerStatus = seller.status.orEmpty().lowercase()
    val sellerSuspended = sellerStatus == "suspended" || sellerStatus == "banned"
    val showAppealPending = detail.appealPending &&
            currentUserId != null &&
            currentUserId == seller.id

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Warnings (above the fold per spec).
        if (detail.warnings.isNotEmpty()) {
            WarningsSection(
                warnings = detail.warnings,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        // Suspended/banned seller banner.
        if (sellerSuspended) {
            SellerStatusBanner(status = sellerStatus)
        }

        PhotoCarousel(
            photoUrls = photoUrls,
            onPhotoTap = onPhotoTap,
            modifier = Modifier.fillMaxWidth(),
        )

        // Condition + status + delivery-method badges row. Condition reuses the
        // colour-coded ListingCard styling; the status badge always shows
        // (including "active"); delivery chips mirror the listing's fulfilment.
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            listing.condition?.let { ConditionChip(condition = it) }
            listing.status?.let { StatusChip(status = it.name.lowercase()) }
            DeliveryMethodChips(listing = listing)
            if (showAppealPending) {
                AppealPendingPill()
            }
        }

        // Description — rendered as plain body text (no heading) per the
        // redesigned detail layout.
        if (listing.description.isNotBlank()) {
            Text(
                text = listing.description,
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        // Tags — chips only, no heading.
        if (tags.isNotEmpty()) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                tags.forEach { tag ->
                    AssistChip(
                        onClick = { onTagTap(tag) },
                        label = { Text(tag) },
                    )
                }
            }
        }

        // Delivery information — the listing's free-text fulfilment note (e.g.
        // "Download link will be provided after purchase.").
        if (listing.information.isNotBlank()) {
            Text(
                text = stringResource(R.string.market_listing_detail_information_heading),
                style = MaterialTheme.typography.titleSmall,
            )
            Text(
                text = listing.information,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        // Shipping.
        if (listing.shipping > 0L) {
            Text(
                text = stringResource(R.string.market_listing_detail_shipping_heading),
                style = MaterialTheme.typography.titleSmall,
            )
            ShippingOptionsTable(
                options = detail.shipping,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        // Seller reviews.
        if (reviews.isNotEmpty()) {
            Text(
                text = stringResource(R.string.market_listing_detail_seller_reviews_heading),
                style = MaterialTheme.typography.titleSmall,
            )
            SellerReviewsSection(
                reviews = reviews,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        // Primary info + actions card: title, price, location, listed date,
        // the buy CTA + secondary actions, and the seller row, grouped into one
        // card per the redesign.
        OutlinedCard(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = listing.title,
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.fillMaxWidth(),
                )

                PriceDisplay(
                    listing = listing,
                    auction = auction,
                    compact = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                // Location (pin + place) and listed date.
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    if (locationDisplay.isNotBlank()) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Default.LocationOn,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp),
                            )
                            Text(
                                text = locationDisplay,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    Text(
                        text = stringResource(
                            R.string.market_listing_detail_listed,
                            format.formatDateTime(listing.created),
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    PrimaryCta(
                        pricing = listing.pricing,
                        onClick = onPrimaryCta,
                        enabled = !sellerSuspended,
                        modifier = Modifier.fillMaxWidth(),
                    )

                    // Auction "Buy it now": when the seller set an instant-buy
                    // price, offer it alongside the bid CTA (mirrors web). A
                    // tap bids at the instant amount, which the server resolves
                    // as an instant win -> checkout.
                    if (listing.pricing == PricingModel.AUCTION && auction != null && auction.instant > 0 && !sellerSuspended) {
                        OutlinedButton(
                            onClick = onBuyNow,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Default.ShoppingCart,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(Modifier.width(8.dp))
                            // "Buy it now" is translated; the price is appended
                            // as a formatted, language-neutral amount (web shows
                            // the same "Buy it now — <price>").
                            Text(
                                stringResource(R.string.market_listing_detail_buy_now_label) +
                                    " — " + formatPrice(auction.instant, listing.currency ?: Currency.GBP)
                            )
                        }
                    }

                    if (sellerSuspended) {
                        Text(
                            text = stringResource(R.string.market_listing_detail_seller_suspended_cta_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }

                    // Message + save + report.
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        OutlinedButton(
                            onClick = onMessageSeller,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.onSurface,
                            ),
                            border = BorderStroke(
                                width = 1.dp,
                                color = MaterialTheme.colorScheme.outlineVariant,
                            ),
                        ) {
                            Icon(
                                imageVector = Icons.Default.Message,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.market_listing_detail_message))
                        }
                        IconButton(
                            onClick = onToggleSave,
                        ) {
                            Icon(
                                imageVector = if (isSaved) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
                                contentDescription = stringResource(
                                    if (isSaved) R.string.market_listing_detail_unsave
                                    else R.string.market_listing_detail_save,
                                ),
                            )
                        }
                        OutlinedIconButton(
                            onClick = onReport,
                            enabled = !isReported,
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(
                                width = 1.dp,
                                color = MaterialTheme.colorScheme.outlineVariant,
                            ),
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Flag,
                                contentDescription = stringResource(
                                    if (isReported) R.string.market_listing_detail_already_reported
                                    else R.string.market_listing_detail_report,
                                ),
                            )
                        }
                    }

                    // Owner controls.
                    if (isOwner) {
                        OutlinedButton(
                            onClick = onEdit,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(stringResource(R.string.market_listing_detail_edit))
                        }
                    }
                    val showRelist = listing.status == ListingStatus.SOLD ||
                            listing.status == ListingStatus.EXPIRED
                    if (showRelist) {
                        OutlinedButton(
                            onClick = onRelist,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(stringResource(R.string.market_listing_detail_relist))
                        }
                    }
                }
            }
        }

        // Seller card — a "Seller" heading over the avatar/name row, with the
        // rating and sales stacked beneath it (left-aligned to the card edge),
        // per the redesign.
        OutlinedCard(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSellerTap() }
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = stringResource(R.string.market_listing_detail_seller_heading),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                // Avatar + name + verified tick.
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    // Flat white avatar with black initials and a hairline
                    // outline, matching the seller avatar on ListingCard.
                    EntityAvatar(
                        name = sellerName,
                        src = rememberSellerAvatarUrl(listing),
                        seed = seller.id,
                        size = 40.dp,
                        containerColor = Color.White,
                        contentColor = Color.Black,
                        modifier = Modifier.border(
                            width = 1.dp,
                            color = MaterialTheme.colorScheme.outlineVariant,
                            shape = CircleShape,
                        ),
                    )
                    Text(
                        text = sellerName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium,
                    )
                    if (sellerVerified) {
                        Icon(
                            imageVector = Icons.Filled.Verified,
                            contentDescription = stringResource(
                                R.string.market_listing_detail_verified,
                            ),
                            tint = VerifiedGreen,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }

                // Rating stars + (count), gold to match the listing card.
                if (sellerReviews > 0 || sellerRating > 0f) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        RatingStars(
                            rating = sellerRating,
                            showCount = false,
                            tint = RatingStarGold,
                        )
                        if (sellerReviews > 0) {
                            Text(
                                text = "($sellerReviews)",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }

                if (sellerSales > 0) {
                    Text(
                        text = stringResource(
                            R.string.market_listing_detail_sales_count,
                            sellerSales,
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        // Digital assets.
        if (listing.type == ListingType.DIGITAL && assets.isNotEmpty()) {
            Text(
                text = stringResource(R.string.market_listing_detail_assets_heading),
                style = MaterialTheme.typography.titleSmall,
            )
            DigitalAssetsList(
                assets = assets,
                onDownload = onAssetDownload,
            )
        }

        // Auction history.
        if (auction != null && bids.isNotEmpty()) {
            Text(
                text = stringResource(R.string.market_listing_detail_bids_heading),
                style = MaterialTheme.typography.titleSmall,
            )
            AuctionBidHistory(
                bids = bids,
                fallbackCurrency = listing.currency ?: Currency.GBP,
                endsAt = auction.closes,
            )
        }

        // Audit timeline (collapsible). Buyers without read access on the
        // listing get an empty list back from the server — hide the toggle
        // entirely in that case so we don't render a dead-end button.
        if (audit.isNotEmpty()) {
            TextButton(onClick = onToggleAudit) {
                Text(
                    text = stringResource(
                        if (auditExpanded) R.string.market_listing_detail_audit_hide
                        else R.string.market_listing_detail_audit_show,
                    ),
                )
            }
            if (auditExpanded) {
                AuditTimeline(
                    events = audit,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

/**
 * Delivery-method chips for the badges row, mirroring the listing's fulfilment
 * setup: a digital listing shows "Download"; physical listings show "Shipping"
 * and/or "Pickup" depending on which the seller enabled. Static (non-clickable)
 * outlined pills with a leading icon, matching the web detail page.
 */
@Composable
private fun DeliveryMethodChips(listing: Listing) {
    if (listing.type == ListingType.DIGITAL) {
        DeliveryMethodChip(
            icon = Icons.Filled.Download,
            label = stringResource(R.string.market_filter_delivery_download),
        )
        return
    }
    if (listing.shipping > 0L) {
        DeliveryMethodChip(
            icon = Icons.Filled.LocalShipping,
            label = stringResource(R.string.market_filter_delivery_shipping),
        )
    }
    if (listing.pickup > 0L) {
        DeliveryMethodChip(
            icon = Icons.Filled.Storefront,
            label = stringResource(R.string.market_filter_delivery_pickup),
        )
    }
}

/**
 * Status chip — a soft pill at the shared [DetailBadgeChip] size with a
 * semantic light fill + dark label per [statusChipColors] and a neutral 1dp
 * border (the same `outlineVariant` the delivery chip uses). The "active"
 * green fill (#E2FBE8) and text (#2B6536) are sampled from the design.
 */
@Composable
private fun StatusChip(status: String) {
    val key = status.trim().lowercase()
    val (fill, text) = statusChipColors(key)
    DetailBadgeChip(
        label = knownStatusLabel(key) ?: key,
        containerColor = fill,
        contentColor = text,
        borderColor = MaterialTheme.colorScheme.outlineVariant,
    )
}

/** Semantic (light fill, dark text) pair for a status chip: green / red / amber / grey. */
private fun statusChipColors(key: String): Pair<Color, Color> = when (key) {
    "active", "paid", "shipped", "delivered", "completed" ->
        Color(0xFFE2FBE8) to Color(0xFF2B6536)

    "disputed", "cancelled", "past_due" ->
        Color(0xFFFBE2E2) to Color(0xFF7A2B2B)

    "pending", "paused", "refunded" ->
        Color(0xFFFBF3E2) to Color(0xFF7A5A2B)

    else ->
        Color(0xFFEDEDED) to Color(0xFF555555)
}

/** Condition chip — ListingCard colours at the shared [DetailBadgeChip] size. */
@Composable
private fun ConditionChip(condition: Condition) {
    DetailBadgeChip(
        label = conditionLabel(condition),
        containerColor = conditionBadgeColor(condition),
        contentColor = Color.White,
    )
}

/** Pricing chip — ListingCard's dark fill at the shared [DetailBadgeChip] size. */
@Composable
private fun PricingChip(label: String) {
    DetailBadgeChip(
        label = label,
        containerColor = PricingFill,
        contentColor = Color.White,
    )
}

@Composable
private fun DeliveryMethodChip(icon: ImageVector, label: String) {
    DetailBadgeChip(
        label = label,
        leadingIcon = icon,
        containerColor = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onSurface,
        borderColor = MaterialTheme.colorScheme.outlineVariant,
        iconTint = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/**
 * Shared chip used for every badge in the detail row (condition, pricing,
 * delivery method) so they resolve to one uniform size/shape — 8dp corners,
 * h10/v5 padding, [labelMedium] text, optional 16dp leading icon. Only the
 * fill, content colour, and optional border vary per badge.
 */
@Composable
private fun DetailBadgeChip(
    label: String,
    containerColor: Color,
    contentColor: Color,
    leadingIcon: ImageVector? = null,
    borderColor: Color? = null,
    iconTint: Color = contentColor,
) {
    val shape = RoundedCornerShape(8.dp)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier
            .clip(shape)
            .background(containerColor)
            .then(
                if (borderColor != null) {
                    Modifier.border(width = 1.dp, color = borderColor, shape = shape)
                } else {
                    Modifier
                },
            )
            .padding(horizontal = 10.dp, vertical = 5.dp),
    ) {
        if (leadingIcon != null) {
            Icon(
                imageVector = leadingIcon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(16.dp),
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = contentColor,
        )
    }
}

@Composable
private fun SellerStatusBanner(status: String) {
    val message = stringResource(
        if (status == "banned") R.string.market_listing_detail_seller_banned_banner
        else R.string.market_listing_detail_seller_suspended_banner,
    )
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
        ),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(12.dp),
        ) {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = null,
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun AppealPendingPill() {
    Text(
        text = stringResource(R.string.market_listing_detail_appeal_pending),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onTertiaryContainer,
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(MaterialTheme.colorScheme.tertiaryContainer)
            .padding(horizontal = 10.dp, vertical = 4.dp),
    )
}

@Composable
private fun PrimaryCta(
    pricing: PricingModel?,
    onClick: () -> Unit,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val labelRes = when (pricing) {
        PricingModel.AUCTION -> R.string.market_listing_detail_place_bid
        PricingModel.SUBSCRIPTION -> R.string.market_listing_detail_subscribe
        PricingModel.PWYW -> R.string.market_listing_detail_buy
        else -> R.string.market_listing_detail_buy_now
    }
    // A cart fits the buy-style CTAs; auction / subscription read better label-only.
    val icon = when (pricing) {
        PricingModel.AUCTION, PricingModel.SUBSCRIPTION -> null
        else -> Icons.Default.ShoppingCart
    }
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(10.dp),
        modifier = modifier,
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(8.dp))
        }
        Text(stringResource(labelRes))
    }
}

private fun shareListing(
    context: android.content.Context,
    listingId: Long,
    listingTitle: String,
) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, listingTitle)
        putExtra(Intent.EXTRA_TEXT, "mochi://market/listing/$listingId")
    }
    val chooser = Intent.createChooser(
        intent,
        context.getString(R.string.market_listings_share_chooser),
    )
    chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    context.startActivity(chooser)
}

private fun parseTags(json: String): List<String> {
    if (json.isBlank()) return emptyList()
    return try {
        val type = object : TypeToken<List<String>>() {}.type
        Gson().fromJson<List<String>>(json, type) ?: emptyList()
    } catch (_: Exception) {
        emptyList()
    }
}

/**
 * Absolute `/-/photo/{id}` URLs for the carousel, in display order.
 *
 * Prefers the full [photos] set fetched from `-/photos/list`; when that came
 * back empty (a transient failure, say) it falls back to the [listing]'s
 * embedded primary photo so the carousel still shows something.
 */
private fun buildPhotoUrls(
    photos: List<Photo>,
    listing: Listing?,
    baseUrl: String,
): List<String> {
    val urls = photos.mapNotNull { photo ->
        photo.id.takeIf { it.isNotBlank() }?.let { "$baseUrl/market/-/photo/$it" }
    }
    if (urls.isNotEmpty()) return urls
    val photoId = listing?.photo?.id?.takeIf { it.isNotBlank() } ?: return emptyList()
    return listOf("$baseUrl/market/-/photo/$photoId")
}

private fun baseUrlForContext(context: android.content.Context): String {
    return EntryPointAccessors.fromApplication(
        context.applicationContext,
        ListingDetailEntryPoint::class.java,
    ).sessionManager().getServerUrlBlocking().trimEnd('/')
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface ListingDetailEntryPoint {
    fun sessionManager(): SessionManager
    fun marketRepository(): MarketRepository
}
