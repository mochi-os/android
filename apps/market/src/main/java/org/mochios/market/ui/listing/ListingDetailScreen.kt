package org.mochios.market.ui.listing

import android.content.Intent
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Message
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
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
import org.mochios.android.ui.components.EmptyState
import org.mochios.android.ui.components.EntityAvatar
import org.mochios.android.ui.components.ErrorState
import org.mochios.android.ui.components.LightboxScreen
import org.mochios.android.ui.components.LoadingState
import org.mochios.android.ui.components.LocationMapView
import org.mochios.market.R
import org.mochios.market.lib.locationName
import org.mochios.market.lib.parseLocation
import org.mochios.market.lib.ratingStars
import org.mochios.market.lib.toPlaceData
import org.mochios.market.model.AccountSummary
import org.mochios.market.model.Asset
import org.mochios.market.model.AuditEvent
import org.mochios.market.model.Bid
import org.mochios.market.model.Category
import org.mochios.market.model.Currency
import org.mochios.market.model.Listing
import org.mochios.market.model.ListingStatus
import org.mochios.market.model.ListingType
import org.mochios.market.model.PricingModel
import org.mochios.market.navigation.MarketApp
import org.mochios.market.repository.MarketRepository
import org.mochios.market.ui.components.AuctionBidHistory
import org.mochios.market.ui.components.AuditTimeline
import org.mochios.market.ui.components.ConditionBadge
import org.mochios.market.ui.components.DigitalAssetsList
import org.mochios.market.ui.components.PhotoCarousel
import org.mochios.market.ui.components.PriceDisplay
import org.mochios.market.ui.components.RatingStars
import org.mochios.market.ui.components.ShippingOptionsTable
import org.mochios.market.ui.components.StatusBadge
import org.mochios.market.ui.components.WarningsSection
import org.mochios.market.ui.dialog.PlaceBidDialog
import org.mochios.market.ui.dialog.ReportListingDialog
import androidx.compose.material.icons.filled.Inventory

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
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
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
                    val categoryName = state.categories
                        .firstOrNull { it.id == listing.category }?.name

                    ListingDetailContent(
                        listing = listing,
                        detail = response,
                        seller = seller,
                        sellerName = seller.name.ifBlank {
                            stringResource(R.string.market_listing_detail_seller_heading)
                        },
                        sellerRating = ratingStars(seller.rating),
                        sellerReviews = seller.reviews.toInt(),
                        sellerSales = seller.sales.toInt(),
                        sellerVerified = (seller.verified ?: 0) >= 2,
                        categoryId = listing.category,
                        categoryName = categoryName,
                        auction = auction,
                        bids = response.bids,
                        assets = response.assets,
                        audit = state.audit,
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
                        onCategoryTap = { id ->
                            navController.navigate(MarketApp.homeWithCategory(id.toString()))
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
        // The parallel photo agent landed PhotoCarousel taking `photoUrls`;
        // we synthesise the same list here for the lightbox.
        val urls = remember(state.listing) {
            buildPhotoUrls(
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
        onSubmit = { amount, currency ->
            submittingBid = true
            viewModel.placeBid(amount, currency) {
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
    seller: AccountSummary,
    sellerName: String,
    sellerRating: Float,
    sellerReviews: Int,
    sellerSales: Int,
    sellerVerified: Boolean,
    categoryId: Long,
    categoryName: String?,
    auction: org.mochios.market.model.Auction?,
    bids: List<Bid>,
    assets: List<Asset>,
    audit: List<AuditEvent>,
    isOwner: Boolean,
    isSaved: Boolean,
    isReported: Boolean,
    auditExpanded: Boolean,
    onToggleAudit: () -> Unit,
    onPhotoTap: (Int) -> Unit,
    onSellerTap: () -> Unit,
    onCategoryTap: (Long) -> Unit,
    onTagTap: (String) -> Unit,
    onToggleSave: () -> Unit,
    onReport: () -> Unit,
    onMessageSeller: () -> Unit,
    onPrimaryCta: () -> Unit,
    onEdit: () -> Unit,
    onRelist: () -> Unit,
    onAssetDownload: (Asset) -> Unit,
    currentUserId: String?,
) {
    val tags = remember(listing.tags) { parseTags(listing.tags) }
    val parsedLocation = remember(listing.location) { parseLocation(listing.location) }
    val locationDisplay = locationName(parsedLocation)
    val mapPlace = remember(parsedLocation) { parsedLocation?.toPlaceData() }
    val context = LocalContext.current
    val photoUrls = remember(detail.listing.id) {
        // Best-effort. The full /-/photo/{id} URL list isn't on the listing
        // detail payload — the screen normally calls photosApi.list() —
        // but a stub from the embedded `photo` thumbnail keeps the carousel
        // alive until the photos endpoint is wired in.
        listingPrimaryPhotoUrl(detail.listing, baseUrlForContext(context))?.let { listOf(it) }
            ?: emptyList()
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

        // Title row with condition + status badges.
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = listing.title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            listing.condition?.let { ConditionBadge(condition = it) }
        }

        // Status + category + appeal-pending badges row.
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            listing.status?.let {
                if (it != ListingStatus.ACTIVE) {
                    StatusBadge(status = it.name.lowercase())
                }
            }
            if (showAppealPending) {
                AppealPendingPill()
            }
            if (categoryName != null && categoryId > 0L) {
                AssistChip(
                    onClick = { onCategoryTap(categoryId) },
                    label = { Text(categoryName) },
                )
            }
        }

        PriceDisplay(
            listing = listing,
            auction = auction,
            modifier = Modifier.fillMaxWidth(),
        )

        // Seller row.
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onSellerTap() }
                .padding(vertical = 4.dp),
        ) {
            EntityAvatar(name = sellerName, seed = seller.id, size = 32.dp)
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = sellerName,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                    )
                    if (sellerVerified) {
                        Icon(
                            imageVector = Icons.Default.Verified,
                            contentDescription = stringResource(
                                R.string.market_listing_detail_verified,
                            ),
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
                if (sellerReviews > 0 || sellerRating > 0f) {
                    RatingStars(
                        rating = sellerRating,
                        count = sellerReviews,
                        showCount = true,
                    )
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

        HorizontalDivider()

        // Action row.
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onToggleSave) {
                Icon(
                    imageVector = if (isSaved) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                    contentDescription = stringResource(
                        if (isSaved) R.string.market_listing_detail_unsave
                        else R.string.market_listing_detail_save,
                    ),
                )
            }
            IconButton(
                onClick = onReport,
                enabled = !isReported,
            ) {
                Icon(
                    imageVector = Icons.Default.Flag,
                    contentDescription = stringResource(
                        if (isReported) R.string.market_listing_detail_already_reported
                        else R.string.market_listing_detail_report,
                    ),
                )
            }
            IconButton(onClick = onMessageSeller) {
                Icon(
                    imageVector = Icons.Default.Message,
                    contentDescription = stringResource(
                        R.string.market_listing_detail_message_seller,
                    ),
                )
            }
            Spacer(modifier = Modifier.weight(1f))
            if (isOwner) {
                OutlinedButton(onClick = onEdit) {
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
                OutlinedButton(onClick = onRelist) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(R.string.market_listing_detail_relist))
                }
            }
            PrimaryCta(
                pricing = listing.pricing,
                onClick = onPrimaryCta,
                enabled = !sellerSuspended,
            )
        }

        if (sellerSuspended) {
            Text(
                text = stringResource(R.string.market_listing_detail_seller_suspended_cta_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        // Description.
        if (listing.description.isNotBlank()) {
            Text(
                text = stringResource(R.string.market_listing_detail_description_heading),
                style = MaterialTheme.typography.titleSmall,
            )
            Text(
                text = listing.description,
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        // Tags.
        if (tags.isNotEmpty()) {
            Text(
                text = stringResource(R.string.market_listing_detail_tags_heading),
                style = MaterialTheme.typography.titleSmall,
            )
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

        // Location.
        if (locationDisplay.isNotBlank()) {
            Text(
                text = stringResource(R.string.market_listing_detail_location_heading),
                style = MaterialTheme.typography.titleSmall,
            )
            Text(
                text = locationDisplay,
                style = MaterialTheme.typography.bodyMedium,
            )
            if (mapPlace != null) {
                LocationMapView(
                    checkin = mapPlace,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
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
) {
    val labelRes = when (pricing) {
        PricingModel.AUCTION -> R.string.market_listing_detail_place_bid
        PricingModel.SUBSCRIPTION -> R.string.market_listing_detail_subscribe
        PricingModel.PWYW -> R.string.market_listing_detail_buy
        else -> R.string.market_listing_detail_buy_now
    }
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(10.dp),
    ) {
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

private fun buildPhotoUrls(
    listing: Listing?,
    baseUrl: String,
): List<String> {
    val photoId = listing?.photo?.id ?: return emptyList()
    if (photoId.isBlank()) return emptyList()
    return listOf("$baseUrl/market/-/photo/$photoId")
}

private fun listingPrimaryPhotoUrl(listing: Listing?, baseUrl: String): String? {
    val id = listing?.photo?.id ?: return null
    if (id.isBlank()) return null
    return "$baseUrl/market/-/photo/$id"
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
