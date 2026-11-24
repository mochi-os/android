// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.market.ui.listing

import org.mochios.android.api.OwnedAddress
import androidx.annotation.StringRes
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
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Inventory
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
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
import org.mochios.android.api.userMessage
import org.mochios.android.auth.SessionManager
import org.mochios.android.i18n.LocalFormat
import org.mochios.android.ui.components.EmptyState
import org.mochios.android.ui.components.EntityAvatar
import org.mochios.android.ui.components.ErrorState
import org.mochios.android.ui.components.LightboxScreen
import org.mochios.android.ui.components.LoadingState
import org.mochios.android.ui.components.MochiButton
import org.mochios.android.ui.components.MochiCard
import org.mochios.android.ui.components.MochiIconButton
import org.mochios.android.ui.components.MochiOutlinedButton
import org.mochios.android.share.ShareSheet
import org.mochios.android.ui.components.StatusBadge
import org.mochios.android.ui.components.StatusBadgeSize
import org.mochios.android.ui.components.StatusTone
import org.mochios.android.ui.components.UnopenedFileDialog
import org.mochios.android.util.AttachmentOpener
import org.mochios.android.util.webUri
import org.mochios.market.R
import org.mochios.market.lib.formatPrice
import org.mochios.market.lib.locationName
import org.mochios.market.lib.parseLocation
import org.mochios.market.lib.ratingStars
import org.mochios.market.model.AccountSummary
import org.mochios.market.model.Asset
import org.mochios.market.model.AuctionStatus
import org.mochios.market.model.AuditEvent
import org.mochios.market.model.Bid
import org.mochios.market.model.Condition
import org.mochios.market.model.Currency
import org.mochios.market.model.Listing
import org.mochios.market.model.ListingDetailResponse
import org.mochios.market.model.ListingStatus
import org.mochios.market.model.ListingType
import org.mochios.market.model.Photo
import org.mochios.market.model.PricingModel
import org.mochios.market.model.RemovalCheck
import org.mochios.market.model.Review
import org.mochios.market.model.photoAddress
import org.mochios.market.model.userAvatarUrl
import org.mochios.market.navigation.MarketApp
import org.mochios.market.navigation.open
import org.mochios.market.ui.components.AuctionBidHistory
import org.mochios.market.ui.components.rememberRemaining
import org.mochios.market.ui.components.AuditHistoryCard
import org.mochios.market.ui.components.DigitalAssetsList
import org.mochios.market.ui.components.ListingStatusBadge
import org.mochios.market.ui.components.MarketStatusBadge
import org.mochios.market.ui.components.PhotoCarousel
import org.mochios.market.ui.components.PriceDisplay
import org.mochios.market.ui.components.RatingStarGold
import org.mochios.market.ui.components.RatingStars
import org.mochios.market.ui.components.SellerReviewsSection
import org.mochios.market.ui.components.ShippingOptionsTable
import org.mochios.market.ui.components.VerifiedGreen
import org.mochios.market.ui.components.ModerationNotice
import org.mochios.market.ui.components.WarningsSection
import org.mochios.market.ui.components.conditionBadgeColor
import org.mochios.market.ui.components.conditionLabel
import org.mochios.market.ui.components.pricingLabel
import org.mochios.market.ui.components.shareable
import org.mochios.market.ui.components.shownStatus
import org.mochios.market.ui.dialog.AppealRemovalDialog
import org.mochios.market.ui.dialog.RemoveListingDialog
import org.mochios.market.ui.dialog.PlaceBidDialog
import org.mochios.market.ui.dialog.ReportListingDialog
import org.mochios.android.R as MochiR

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ListingDetailScreen(
    listingId: String,
    navController: NavController,
    initialTitle: String = "",
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
            navController.open(MarketApp.listingEdit(newId.toString()))
        }
    }
    LaunchedEffect(viewModel) {
        viewModel.closed.collect {
            navController.popBackStack()
        }
    }
    var deleteOpen by remember { mutableStateOf(false) }
    var appealOpen by remember { mutableStateOf(false) }

    val isSaved by viewModel.isSaved().collectAsState(initial = false)
    val isReported by viewModel.isReported().collectAsState(initial = false)

    val snackbarHostState = remember { SnackbarHostState() }
    var unopened by remember { mutableStateOf<AttachmentOpener.UnopenedFile?>(null) }
    val snackbarContext = LocalContext.current
    val resources = LocalResources.current
    val format = LocalFormat.current
    // Surface the view model's one-shot bid / report messages (e.g. instant
    // buy-it-now win, immediate proxy outbid) via the scaffold snackbar.
    LaunchedEffect(viewModel) {
        viewModel.snackbar.collect { message ->
            val amount = message.amount
            val currency = message.currency
            val text = if (amount != null && currency != null) {
                resources.getString(message.messageRes, formatPrice(amount, currency, format))
            } else {
                resources.getString(message.messageRes)
            }
            snackbarHostState.showSnackbar(text)
        }
    }

    // Digital-asset download outcomes.
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is ListingDetailEvent.Toast -> snackbarHostState.showSnackbar(event.message)
                is ListingDetailEvent.OpenUrl -> {
                    // The asset reference comes from the seller via the
                    // Comptroller and launchUrl degrades to ACTION_VIEW, so only
                    // a web scheme may leave the app.
                    webUri(event.url)?.let { target ->
                        val intent = CustomTabsIntent.Builder().build()
                        runCatching { intent.launchUrl(snackbarContext, target) }
                    }
                }
                is ListingDetailEvent.OpenFile -> {
                    val outcome = AttachmentOpener.openCached(
                        snackbarContext,
                        event.fileName,
                        event.mime,
                    )
                    if (outcome == AttachmentOpener.OpenResult.NO_APP) {
                        unopened = AttachmentOpener.unopened(
                            snackbarContext,
                            event.fileName,
                            event.mime,
                        )
                    } else if (outcome != AttachmentOpener.OpenResult.OPENED) {
                        snackbarHostState.showSnackbar(
                            resources.getString(R.string.market_asset_download_failed)
                        )
                    }
                }
            }
        }
    }

    var lightboxOpen by remember { mutableStateOf(false) }
    var lightboxIndex by remember { mutableIntStateOf(0) }
    var reportOpen by remember { mutableStateOf(false) }
    var bidOpen by remember { mutableStateOf(false) }

    val listingTitle = state.listing?.listing?.title.orEmpty()

    unopened?.let { file ->
        UnopenedFileDialog(file = file, onDismiss = { unopened = null })
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = listingTitle.ifBlank { initialTitle }.ifBlank {
                            stringResource(R.string.market_listing_detail_title)
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    MochiIconButton(onClick = { navController.popBackStack() }) {
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
                        offer = response.offer(currentUserId),
                        offset = state.offset,
                        downloadable = response.downloadable(currentUserId),
                        onReload = { viewModel.load(listingId) },
                        isSaved = isSaved,
                        isReported = isReported,
                        onPhotoTap = { i ->
                            lightboxIndex = i
                            lightboxOpen = true
                        },
                        onSellerTap = {
                            navController.open(MarketApp.publicProfile(seller.id, seller.fingerprint))
                        },
                        onToggleSave = { viewModel.toggleSave() },
                        onReport = { reportOpen = true },
                        onMessageSeller = {
                            if (isOwner) {
                                navController.navigate(MarketApp.MESSAGES)
                            } else {
                                navController.open(
                                    MarketApp.messageThread(listing.id.toString(), "new"),
                                )
                            }
                        },
                        onBuyNow = {
                            val auc = response.auction
                            if (auc != null && auc.instant > 0) {
                                viewModel.placeBid(
                                    amount = auc.instant,
                                    ceiling = null,
                                    currency = listing.currency ?: Currency.GBP,
                                    onSuccess = {},
                                    // No dialog to hold the reason, so it
                                    // goes to the snackbar.
                                    onFailure = { failure ->
                                        val text = failure.userMessage().ifEmpty {
                                            resources.getString(R.string.market_bid_dialog_failed)
                                        }
                                        scope.launch { snackbarHostState.showSnackbar(text) }
                                    },
                                    onInstantWin = {
                                        navController.open(
                                            MarketApp.checkout(listing.id.toString()),
                                        )
                                    },
                                )
                            }
                        },
                        onOffer = { offer ->
                            when (offer) {
                                Offer.Bid -> bidOpen = true
                                is Offer.Order -> navController.open(MarketApp.purchaseDetail(offer.id))
                                Offer.Subscribed -> navController.navigate(MarketApp.SUBSCRIPTIONS)
                                is Offer.Buy, Offer.Pay, Offer.Subscribe ->
                                    navController.open(MarketApp.checkout(listing.id))
                                is Offer.Opens, Offer.Ended, Offer.None -> Unit
                            }
                        },
                        onRelist = { viewModel.relistListing() },
                        onEdit = {
                            navController.open(MarketApp.listingEdit(listing.id))
                        },
                        onDelete = { deleteOpen = true },
                        onClear = { viewModel.deleteListing() },
                        onAppeal = { appealOpen = true },
                        onShare = { viewModel.share(format) },
                        onCancelReservation = { viewModel.cancelReservation() },
                        cancellingReservation = state.cancellingReservation,
                        // Through the authenticated client, not a browser: the
                        // action is not public, so a Custom Tab carrying no
                        // token and no cookie could never authenticate to it.
                        onAssetDownload = { asset -> viewModel.downloadAsset(asset.id) },
                        currentUserId = currentUserId,
                    )
                }
            }
        }
    }

    if (lightboxOpen) {
        // Same photo list the carousel renders, reused for the lightbox.
        val urls = remember(state.listing, state.photos) {
            buildPhotoUrls(state.photos, state.listing?.listing)
        }
        if (urls.isNotEmpty()) {
            LightboxScreen(
                images = urls,
                initialIndex = lightboxIndex.coerceIn(0, urls.size - 1),
                onDismiss = { lightboxOpen = false },
            )
        }
    }

    val deleting = state.listing?.listing
    if (deleteOpen && deleting != null) {
        val draft = deleting.status == ListingStatus.DRAFT
        var removal by remember(deleting.id) { mutableStateOf<RemovalCheck?>(null) }
        var checked by remember(deleting.id) { mutableStateOf(draft) }
        LaunchedEffect(deleting.id) {
            if (!draft) {
                removal = viewModel.removalCheck()
                checked = true
            }
        }
        if (checked) {
            RemoveListingDialog(
                listing = deleting,
                check = removal,
                onConfirm = {
                    deleteOpen = false
                    viewModel.deleteListing()
                },
                onDismiss = { deleteOpen = false },
            )
        }
    }
    if (appealOpen) {
        AppealRemovalDialog(
            open = true,
            listingId = listingId,
            notes = state.listing?.listing?.notes.orEmpty(),
            onDismiss = { appealOpen = false },
            onSuccess = { viewModel.load(listingId) },
        )
    }

    ShareSheet(viewModel.shares)

    ReportDialogHost(
        open = reportOpen,
        viewModel = viewModel,
        onClose = { reportOpen = false },
    )

    BidDialogHost(
        open = bidOpen,
        detail = state.listing,
        viewModel = viewModel,
        onClose = { bidOpen = false },
    )
}

/**
 * The bid dialog and its round trip: an accepted bid closes it, and a refused
 * one keeps it open on the server's reason with the form usable again.
 */
@Composable
internal fun BidDialogHost(
    open: Boolean,
    detail: ListingDetailResponse?,
    viewModel: ListingDetailViewModel,
    onClose: () -> Unit,
) {
    var submitting by remember(open) { mutableStateOf(false) }
    var error by remember(open) { mutableStateOf<String?>(null) }
    val fallback = stringResource(R.string.market_bid_dialog_failed)
    PlaceBidDialog(
        open = open,
        auction = detail?.auction,
        startingPrice = detail?.listing?.price ?: 0L,
        currency = detail?.listing?.currency ?: Currency.GBP,
        submitting = submitting,
        errorMessage = error,
        onSubmit = { amount, ceiling, currency ->
            submitting = true
            error = null
            viewModel.placeBid(
                amount = amount,
                ceiling = ceiling,
                currency = currency,
                onSuccess = {
                    submitting = false
                    onClose()
                },
                onFailure = { failure ->
                    submitting = false
                    error = failure.userMessage().ifEmpty { fallback }
                },
            )
        },
        onDismiss = onClose,
    )
}

/**
 * The report dialog and its round trip: a filed report closes it, and a
 * refused one keeps it open on the server's reason.
 */
@Composable
internal fun ReportDialogHost(
    open: Boolean,
    viewModel: ListingDetailViewModel,
    onClose: () -> Unit,
) {
    var submitting by remember(open) { mutableStateOf(false) }
    var error by remember(open) { mutableStateOf<String?>(null) }
    val fallback = stringResource(R.string.market_report_dialog_failed)
    ReportListingDialog(
        open = open,
        submitting = submitting,
        errorMessage = error,
        onSubmit = { reason, details ->
            submitting = true
            error = null
            viewModel.reportListing(
                reason = reason,
                details = details,
                onSuccess = {
                    submitting = false
                    onClose()
                },
                onFailure = { failure ->
                    submitting = false
                    error = failure.userMessage().ifEmpty { fallback }
                },
            )
        },
        onDismiss = onClose,
    )
}

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
    offer: Offer,
    offset: Long,
    downloadable: Boolean,
    onReload: () -> Unit,
    isSaved: Boolean,
    isReported: Boolean,
    onPhotoTap: (Int) -> Unit,
    onSellerTap: () -> Unit,
    onToggleSave: () -> Unit,
    onReport: () -> Unit,
    onMessageSeller: () -> Unit,
    onOffer: (Offer) -> Unit,
    onBuyNow: () -> Unit,
    onRelist: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onClear: () -> Unit,
    onAppeal: () -> Unit,
    onShare: () -> Unit,
    onCancelReservation: () -> Unit,
    cancellingReservation: Boolean,
    onAssetDownload: (Asset) -> Unit,
    currentUserId: String?,
) {
    val tags = remember(listing.tags) { parseTags(listing.tags) }
    val parsedLocation = remember(listing.location) { parseLocation(listing.location) }
    val locationDisplay = locationName(parsedLocation)
    val format = LocalFormat.current
    val carousel = remember(detail.listing.id, photos) {
        // /-/photo/{id} paths from the photos endpoint, falling back to the
        // listing's embedded primary photo when that list is empty.
        carouselUrls(photos, detail.listing)
    }
    // A running auction's page reads again as it closes, and a scheduled one's
    // as it opens, so the outcome replaces the bid form.
    when {
        auction?.status == AuctionStatus.ACTIVE && auction.closes > 0L ->
            rememberRemaining(auction.closes, offset, onReload)
        auction?.status == AuctionStatus.SCHEDULED && auction.opens > 0L ->
            rememberRemaining(auction.opens, offset, onReload)
    }
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
        // A held or rejected listing says so to its seller, with staff's reason.
        if (currentUserId != null && currentUserId == seller.id) ModerationNotice(detail.listing)
        // Warnings (above the fold per spec).
        if (detail.warnings.isNotEmpty()) {
            WarningsSection(
                warnings = detail.warnings,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        PhotoCarousel(
            photoUrls = carousel.pages,
            thumbnailUrls = carousel.tiles,
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
            ListingStatusBadge(listing = listing, size = StatusBadgeSize.Regular)
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

        // Tags are inert badges, as on web: the search matches titles and
        // descriptions only, so a tag tap has nothing real to open.
        if (tags.isNotEmpty()) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                tags.forEach { tag ->
                    Surface(
                        shape = MaterialTheme.shapes.small,
                        color = MaterialTheme.colorScheme.surfaceVariant,
                    ) {
                        Text(
                            text = tag,
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        )
                    }
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
        MochiCard(
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
                    // A reservation the caller already holds blocks their own
                    // Buy now until it expires, and nothing else on the screen
                    // says so. Offer the release the web client offers.
                    val reservation = detail.myReservation
                    if (reservation != null) {
                        Text(
                            text = stringResource(R.string.market_listing_checkout_in_progress),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        MochiOutlinedButton(
                            onClick = onCancelReservation,
                            enabled = !cancellingReservation,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp),
                        ) {
                            Text(stringResource(R.string.market_listing_checkout_cancel))
                        }
                    }

                    OfferActions(
                        offer = offer,
                        pricing = listing.pricing,
                        onOffer = onOffer,
                        modifier = Modifier.fillMaxWidth(),
                    )

                    // Auction "Buy it now": when the seller set an instant-buy
                    // price, offer it alongside the bid CTA (mirrors web). A
                    // tap bids at the instant amount, which the server resolves
                    // as an instant win -> checkout.
                    if (offer == Offer.Bid && auction != null && auction.instant > 0) {
                        MochiOutlinedButton(
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
                            Text(
                                stringResource(
                                    R.string.market_listing_detail_buy_now_price,
                                    formatPrice(auction.instant, listing.currency ?: Currency.GBP),
                                ),
                            )
                        }
                    }

                    // Message + save + report.
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        MochiOutlinedButton(
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
                                imageVector = Icons.AutoMirrored.Filled.Message,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.market_listing_detail_message))
                        }
                        if (!isOwner) {
                            MochiIconButton(
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
                    }

                    if (isOwner) {
                        OwnerActions(
                            listing = listing,
                            onEdit = onEdit,
                            onDelete = onDelete,
                            onRelist = onRelist,
                            onClear = onClear,
                            onAppeal = onAppeal,
                            onShare = onShare,
                        )
                    }
                }
            }
        }

        // Seller card — a "Seller" heading over the avatar/name row, with the
        // rating and sales stacked beneath it (left-aligned to the card edge),
        // per the redesign.
        MochiCard(
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
                    EntityAvatar(
                        name = sellerName,
                        src = listing.seller.takeIf { id -> id.isNotBlank() }
                            ?.let { id -> userAvatarUrl(id) },
                        seed = seller.id,
                        size = 40.dp,
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
                                text = stringResource(
                                    R.string.market_review_count,
                                    LocalFormat.current.formatNumber(sellerReviews),
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }

                if (sellerSales > 0) {
                    Text(
                        text = pluralStringResource(
                            R.plurals.market_listing_detail_sales_count,
                            sellerSales,
                            LocalFormat.current.formatNumber(sellerSales),
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
            // Only someone who can take the files is offered them; anyone
            // else sees what they are, as on the web.
            DigitalAssetsList(
                assets = assets,
                onDownload = if (downloadable) onAssetDownload else null,
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
                endsAt = auction.closes.takeIf { auction.status == AuctionStatus.ACTIVE },
                offset = offset,
            )
        }

        AuditHistoryCard(events = audit)

        Spacer(modifier = Modifier.height(24.dp))
    }
}

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
 * The owner's actions on their listing, the same as the ⋯ menu on My
 * listings: a draft is edited or deleted; a rejected one is cleared from the
 * list or appealed; one otherwise removed is cleared from the list; anything
 * else is relisted or removed, and one live for sale shared.
 */
@Composable
private fun OwnerActions(
    listing: Listing,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onRelist: () -> Unit,
    onClear: () -> Unit,
    onAppeal: () -> Unit,
    onShare: () -> Unit,
) {
    val status = listing.shownStatus()
    when {
        status == ListingStatus.DRAFT -> {
            OwnerAction(Icons.Outlined.Edit, R.string.market_listings_action_edit, onEdit)
            OwnerAction(Icons.Outlined.Delete, R.string.market_listings_delete_draft, onDelete)
        }
        status == ListingStatus.REJECTED -> {
            OwnerAction(Icons.Outlined.Delete, R.string.market_listings_clear, onClear)
            OwnerAction(Icons.Outlined.Flag, R.string.market_listings_appeal, onAppeal)
        }
        status == ListingStatus.HELD -> {
            OwnerAction(Icons.Outlined.Flag, R.string.market_listings_appeal, onAppeal)
            OwnerAction(Icons.Outlined.Delete, R.string.market_listings_remove_listing, onDelete)
        }
        status == ListingStatus.REMOVED -> {
            OwnerAction(Icons.Outlined.Delete, R.string.market_listings_clear, onClear)
        }
        else -> {
            if (listing.shareable()) OwnerAction(Icons.Outlined.Share, MochiR.string.share_title, onShare)
            OwnerAction(Icons.Default.Refresh, R.string.market_listings_action_relist, onRelist)
            OwnerAction(Icons.Outlined.Delete, R.string.market_listings_remove_listing, onDelete)
        }
    }
}

@Composable
private fun OwnerAction(icon: ImageVector, @StringRes label: Int, onClick: () -> Unit) {
    MochiOutlinedButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
        )
        Spacer(Modifier.width(4.dp))
        Text(stringResource(label))
    }
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
private fun AppealPendingPill() {
    StatusBadge(
        label = stringResource(R.string.market_listing_detail_appeal_pending),
        tone = StatusTone.Waiting,
        size = StatusBadgeSize.Regular,
    )
}

/**
 * The page's own action for [offer]: Buy now (Complete purchase over the
 * viewer's own checkout), Subscribe, Place bid, Complete purchase for a won
 * auction, View your order, Manage subscription; or why there is none.
 */
@Composable
private fun OfferActions(
    offer: Offer,
    pricing: PricingModel?,
    onOffer: (Offer) -> Unit,
    modifier: Modifier = Modifier,
) {
    when (offer) {
        is Offer.Buy -> OfferButton(
            label = when {
                offer.resume -> R.string.market_purchases_complete_purchase
                pricing == PricingModel.PWYW -> R.string.market_listing_detail_buy
                else -> R.string.market_listing_detail_buy_now
            },
            icon = Icons.Default.ShoppingCart,
            onClick = { onOffer(offer) },
            modifier = modifier,
        )
        Offer.Subscribe -> OfferButton(
            label = R.string.market_listing_detail_subscribe,
            onClick = { onOffer(offer) },
            modifier = modifier,
        )
        Offer.Bid -> OfferButton(
            label = R.string.market_listing_detail_place_bid,
            onClick = { onOffer(offer) },
            modifier = modifier,
        )
        Offer.Pay -> OfferButton(
            label = R.string.market_purchases_complete_purchase,
            icon = Icons.Default.ShoppingCart,
            onClick = { onOffer(offer) },
            modifier = modifier,
        )
        is Offer.Order -> MochiOutlinedButton(
            onClick = { onOffer(offer) },
            modifier = modifier,
            shape = RoundedCornerShape(10.dp),
        ) {
            Text(stringResource(R.string.market_listing_detail_view_order))
        }
        Offer.Subscribed -> MochiOutlinedButton(
            onClick = { onOffer(offer) },
            modifier = modifier,
            shape = RoundedCornerShape(10.dp),
        ) {
            Text(stringResource(R.string.market_listing_detail_manage_subscription))
        }
        is Offer.Opens -> Text(
            text = stringResource(
                R.string.market_listing_detail_opens,
                LocalFormat.current.formatDateTime(offer.opens),
            ),
            style = MaterialTheme.typography.bodyMedium,
            modifier = modifier,
        )
        Offer.Ended -> Text(
            text = stringResource(R.string.market_bid_ended),
            style = MaterialTheme.typography.bodyMedium,
            modifier = modifier,
        )
        Offer.None -> Unit
    }
}

@Composable
private fun OfferButton(
    label: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
) {
    MochiButton(
        onClick = onClick,
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
        Text(stringResource(label))
    }
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

// Server-relative paths: the shared RelativeAssetUrlMapper resolves them
// against the session server inside the Coil pipeline, as ListingCard does.
// With no variant each is the original upload; a variant ("thumbnail") names
// the same photos at that size, in the same order.
internal fun buildPhotoUrls(photos: List<Photo>, listing: Listing?, variant: String? = null): List<OwnedAddress> {
    val urls = photos.mapNotNull { photo ->
        photo.id.takeIf { it.isNotBlank() }?.let { photoAddress(it, variant) }
    }
    if (urls.isNotEmpty()) return urls
    val photoId = listing?.photo?.id?.takeIf { it.isNotBlank() } ?: return emptyList()
    return listOfNotNull(photoAddress(photoId, variant))
}

/** The carousel's addresses: its pages and the tiles of its strip. */
internal data class CarouselUrls(val pages: List<OwnedAddress>, val tiles: List<OwnedAddress>)

// The pager is a square no wider than the screen, so it draws the 1280 px
// preview (a camera photo's original is twenty times its size) and the strip
// draws thumbnails; only the full-screen lightbox shows the originals.
internal fun carouselUrls(photos: List<Photo>, listing: Listing?) = CarouselUrls(
    pages = buildPhotoUrls(photos, listing, "preview"),
    tiles = buildPhotoUrls(photos, listing, "thumbnail"),
)

@EntryPoint
@InstallIn(SingletonComponent::class)
interface ListingDetailEntryPoint {
    fun sessionManager(): SessionManager
}
