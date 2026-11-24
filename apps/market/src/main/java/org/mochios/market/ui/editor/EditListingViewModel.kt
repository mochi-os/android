// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.market.ui.editor

import android.content.Context
import android.net.Uri
import androidx.annotation.StringRes
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.mochios.android.api.MochiError
import org.mochios.android.api.toMochiError
import org.mochios.android.files.Upload
import org.mochios.android.files.UploadTracker
import org.mochios.market.R
import org.mochios.market.lib.minorToMajorText
import org.mochios.market.lib.toMinorUnits
import org.mochios.market.model.AccountFees
import org.mochios.market.model.Asset
import org.mochios.market.model.Category
import org.mochios.market.model.Condition
import org.mochios.market.model.Currency
import org.mochios.market.model.Interval
import org.mochios.market.model.Listing
import org.mochios.market.model.ListingType
import org.mochios.market.model.Photo
import org.mochios.market.model.PricingModel
import org.mochios.market.model.ShippingOption
import org.mochios.market.model.RelistAuction
import org.mochios.market.model.ShippingOptionInput
import org.mochios.market.model.ShippingRegions
import org.mochios.market.repository.MarketRepository
import java.io.File
import javax.inject.Inject

/**
 * Save-status indicator surfaced in the top app bar of [EditListingScreen].
 */
enum class SaveStatus { IDLE, SAVING, SAVED, ERROR }

/**
 * Auction duration options exposed in the editor (days).
 */
val AUCTION_DURATIONS: List<Int> = listOf(1, 3, 5, 7, 10, 14)

/**
 * Full editable state for the edit-listing screen. Initialised from a loaded
 * [Listing] (existing listing) or a sensible blank draft (new listing).
 */
data class EditUiState(
    val isLoading: Boolean = true,
    val isNew: Boolean = true,
    val listingId: String = "",
    val title: String = "",
    val description: String = "",
    val category: String = "",
    val condition: Condition? = null,
    val type: ListingType = ListingType.PHYSICAL,
    val pricing: PricingModel = PricingModel.FIXED,
    val currency: Currency = Currency.GBP,
    val priceText: String = "",
    val interval: Interval = Interval.MONTHLY,
    val reserveText: String = "",
    val instantText: String = "",
    val durationDays: Int = 7,
    // Optional future auction start time (epoch seconds). null = start on
    // publish. The duration runs from this point, mirroring web.
    val opensAt: Long? = null,
    // Anti-sniping settings a relisted auction carried; null leaves them to
    // the server's defaults.
    val extend: Long? = null,
    val extension: Long? = null,
    // Stock count for fixed-price/subscription listings. "0" (surfaced via
    // unlimitedStock) means unlimited; auctions always sell a single unit.
    val quantityText: String = "1",
    val unlimitedStock: Boolean = false,
    // Whether the seller has set the stock themselves: until then a download
    // defaults to unlimited stock, and a physical item back to its count.
    val stockTouched: Boolean = false,
    val pickup: Boolean = false,
    val shipping: Boolean = false,
    val download: Boolean = false,
    val location: String = "",
    /** The delivery note the listing page shows under "Delivery information". */
    val information: String = "",
    val tagsText: String = "",
    val photos: List<Photo> = emptyList(),
    val assets: List<Asset> = emptyList(),
    val zones: List<ShippingOption> = emptyList(),
    /** Server fee terms; `minimums` gates publishing when known. */
    val fees: AccountFees? = null,
    /** The countries and region groups shipping options may name, once read. */
    val regions: ShippingRegions? = null,
    val categories: List<Category> = emptyList(),
    val saveStatus: SaveStatus = SaveStatus.IDLE,
    val publishStatus: SaveStatus = SaveStatus.IDLE,
    // True while a delete is in flight; the confirmation stays open until it answers.
    val isDeleting: Boolean = false,
    val isDeletingAsset: Boolean = false,
    val stripeOnboarded: Boolean? = null,
    val error: MochiError? = null,
)

/**
 * One-shot user-facing event for the screen to surface via the snackbar host.
 */
sealed class EditListingEvent {
    data class Notice(@StringRes val message: Int) : EditListingEvent()
    data class Error(val error: MochiError) : EditListingEvent()
    /** Emit when the listing is deleted so the host can navigate back. */
    data object Deleted : EditListingEvent()
    /** Emit when the listing is published so the host can navigate to detail. */
    data class Published(val id: String) : EditListingEvent()
    /** Emit when photos or files are added before the listing has a title, so
     * there is no listing row to attach them to yet. */
    data object TitleRequired : EditListingEvent()
    /** A picked file over [ASSET_SIZE_MAXIMUM], refused before it was sent. */
    data class TooLarge(val name: String) : EditListingEvent()
}

/**
 * The largest digital asset a listing may hold, in bytes: the Comptroller's
 * ASSET_SIZE_MAXIMUM. The editor refuses a larger file before sending it,
 * since the Comptroller would refuse it only after the whole upload.
 */
internal const val ASSET_SIZE_MAXIMUM = 100L * 1024 * 1024

/** [files] split into those within [ASSET_SIZE_MAXIMUM] and those over it. */
internal fun assetsWithinLimit(files: List<File>): Pair<List<File>, List<File>> =
    files.partition { file -> file.length() <= ASSET_SIZE_MAXIMUM }

@HiltViewModel
class EditListingViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: MarketRepository,
) : ViewModel() {

    private val rawId: String = savedStateHandle.get<String>("id").orEmpty()
    private val isNew: Boolean = rawId.isEmpty() || rawId == "new"
    private val initialId: String = if (isNew) "" else rawId
    // Title collected by CreateListingScreen. Seeding it
    // here means a photo added before any edit already has a title to create
    // the listing row with; the row itself is still created lazily on first save.
    private val initialTitle: String = savedStateHandle.get<String>("title").orEmpty()

    private val _state = MutableStateFlow(
        EditUiState(isNew = isNew, listingId = initialId, title = initialTitle),
    )
    val state: StateFlow<EditUiState> = _state.asStateFlow()

    private val photoTracker = UploadTracker()
    private val assetTracker = UploadTracker()

    /** The photos being uploaded, while they go. */
    val photoUpload: StateFlow<Upload?> = photoTracker.state

    /** The digital assets being uploaded, while they go. */
    val assetUpload: StateFlow<Upload?> = assetTracker.state

    private val _events = MutableSharedFlow<EditListingEvent>(extraBufferCapacity = 8)
    val events: SharedFlow<EditListingEvent> = _events.asSharedFlow()

    private val gson = Gson()
    private var autoSaveJob: Job? = null
    private val saveMutex = Mutex()

    init {
        loadInitial()
    }

    // -------------------------------- Loading

    private fun loadInitial() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            try {
                val categories = repository.listCategories()
                val stripe = runCatching { repository.stripeStatus() }.getOrNull()
                // The server's per-currency minimums gate publishing; the
                // compiled-in table is only the fallback while they are unknown.
                val fees = runCatching { repository.getFees() }.getOrNull()
                // Shipping options name regions by code; without the server's
                // list the editor offers the platform's countries.
                val regions = runCatching { repository.shippingRegions() }.getOrNull()
                if (isNew) {
                    _state.value = _state.value.copy(
                        isLoading = false,
                        isNew = true,
                        listingId = "",
                        categories = categories,
                        stripeOnboarded = stripe?.chargesEnabled,
                        fees = fees,
                        regions = regions,
                    )
                } else {
                    val detail = repository.getListing(initialId)
                    // listing.photo is only the cover; the editor manages the
                    // whole set, so fetch it as the detail view does. Failing
                    // to load photos must not block editing the listing, so
                    // fall back to the cover alone.
                    // The owner route: the public list answers 404 for a draft
                    // or moderation-held listing, which hid every photo but the
                    // cover from the seller who uploaded them.
                    val photos = runCatching {
                        repository.listOwnedPhotos(initialId).sortedBy { it.rank }
                    }.getOrElse { listOfNotNull(detail.listing.photo) }
                    val loaded = fromListing(
                        detail.listing,
                        detail.shipping,
                        detail.assets,
                        categories,
                        stripe?.chargesEnabled,
                        photos,
                    ).copy(fees = fees, regions = regions)
                    // A relisted auction's settings, held since the relist,
                    // fill the form once.
                    val relisted = repository.takeRelisted(initialId)
                    _state.value = if (relisted == null) loaded else relistedInto(loaded, relisted)
                }
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    isLoading = false,
                    error = e.toMochiError(),
                )
                _events.emit(EditListingEvent.Error(e.toMochiError()))
            }
        }
    }

    private fun fromListing(
        listing: Listing,
        zones: List<ShippingOption>,
        assets: List<Asset>,
        categories: List<Category>,
        stripeOnboarded: Boolean?,
        photos: List<Photo>,
    ): EditUiState {
        val type = listing.type ?: ListingType.PHYSICAL
        val pricing = listing.pricing ?: PricingModel.FIXED
        val currency = listing.currency ?: Currency.GBP
        val majorFromMinor: (Long) -> String = { minor ->
            if (minor == 0L) "" else minorToMajorText(minor, currency)
        }
        val tags = runCatching {
            gson.fromJson(listing.tags, Array<String>::class.java)?.toList() ?: emptyList()
        }.getOrDefault(emptyList())
        return EditUiState(
            isLoading = false,
            isNew = false,
            listingId = listing.id,
            title = listing.title,
            description = listing.description,
            category = listing.category,
            condition = listing.condition,
            type = type,
            pricing = pricing,
            currency = currency,
            priceText = majorFromMinor(listing.price),
            interval = listing.interval ?: Interval.MONTHLY,
            reserveText = "",
            instantText = "",
            durationDays = 7,
            quantityText = if (listing.quantity == 0L) "1" else listing.quantity.toString(),
            unlimitedStock = listing.quantity == 0L,
            stockTouched = true,
            pickup = listing.pickup == 1L,
            shipping = listing.shipping == 1L,
            download = type == ListingType.DIGITAL,
            location = listing.location,
            information = listing.information,
            tagsText = tags.joinToString(", "),
            photos = photos,
            assets = assets,
            zones = zones,
            categories = categories,
            saveStatus = SaveStatus.IDLE,
            stripeOnboarded = stripeOnboarded,
        )
    }

    /**
     * [state] with a relisted auction's reserve, buy-now price, length (the
     * offered one nearest the old auction's) and anti-sniping settings.
     */
    private fun relistedInto(state: EditUiState, auction: RelistAuction): EditUiState {
        val text: (Long) -> String = { minor -> if (minor <= 0L) "" else minorToMajorText(minor, state.currency) }
        val days = if (auction.closes > auction.opens) {
            val length = (auction.closes - auction.opens) / 86_400.0
            AUCTION_DURATIONS.minByOrNull { offered -> kotlin.math.abs(offered - length) } ?: state.durationDays
        } else {
            state.durationDays
        }
        return state.copy(
            reserveText = text(auction.reserve),
            instantText = text(auction.instant),
            durationDays = days,
            extend = auction.extend,
            extension = auction.extension,
        )
    }

    // -------------------------------- Setters

    fun setTitle(value: String) = mutate { it.copy(title = value) }
    fun setDescription(value: String) = mutate { it.copy(description = value) }
    fun setCategory(value: String) = mutate { it.copy(category = value) }
    fun setQuantity(value: String) = mutate {
        it.copy(quantityText = value.filter { c -> c.isDigit() }, stockTouched = true)
    }
    fun setOpensAt(value: Long?) = mutate { it.copy(opensAt = value) }
    fun setUnlimitedStock(value: Boolean) = mutate {
        it.copy(
            unlimitedStock = value,
            quantityText = if (value) it.quantityText else it.quantityText.ifBlank { "1" },
            stockTouched = true,
        )
    }
    fun setCondition(value: Condition?) = mutate { it.copy(condition = value) }

    fun setType(value: ListingType) = mutate {
        // Switching to digital implicitly disables shipping/pickup; switching
        // to physical disables direct download. Until the seller sets the
        // stock, a download sells without limit (a stock of one would sell
        // once), and a physical item goes back to its count.
        if (value == ListingType.DIGITAL) {
            it.copy(
                type = value,
                shipping = false,
                pickup = false,
                unlimitedStock = if (it.stockTouched) it.unlimitedStock else true,
            )
        } else {
            it.copy(
                type = value,
                download = false,
                unlimitedStock = if (it.stockTouched) it.unlimitedStock else false,
            )
        }
    }

    /**
     * Only model-specific fields reset, so a typed auction reserve does not
     * follow the seller into another model.
     */
    fun setPricing(value: PricingModel) = mutate {
        if (it.pricing == value) {
            it
        } else {
            it.copy(
                pricing = value,
                interval = Interval.MONTHLY,
                durationDays = 7,
                reserveText = "",
                instantText = "",
            )
        }
    }
    fun setCurrency(value: Currency) = mutate { it.copy(currency = value) }
    fun setPriceText(value: String) = mutate { it.copy(priceText = value) }
    fun setInterval(value: Interval) = mutate { it.copy(interval = value) }
    fun setReserveText(value: String) = mutate { it.copy(reserveText = value) }
    fun setInstantText(value: String) = mutate { it.copy(instantText = value) }
    fun setDurationDays(value: Int) = mutate { it.copy(durationDays = value) }
    fun setPickup(value: Boolean) = mutate { it.copy(pickup = value) }
    fun setShipping(value: Boolean) = mutate { it.copy(shipping = value) }
    fun setDownload(value: Boolean) = mutate { it.copy(download = value) }
    fun setLocation(value: String) = mutate { it.copy(location = value) }
    fun setInformation(value: String) = mutate { it.copy(information = value) }
    fun setTagsText(value: String) = mutate { it.copy(tagsText = value) }

    private inline fun mutate(block: (EditUiState) -> EditUiState) {
        _state.value = block(_state.value)
        scheduleAutoSave()
    }

    // -------------------------------- Save

    private fun scheduleAutoSave() {
        if (_state.value.isLoading) return
        if (_state.value.title.isBlank()) return
        autoSaveJob?.cancel()
        autoSaveJob = viewModelScope.launch {
            delay(1_000)
            save()
        }
    }

    fun save() {
        viewModelScope.launch { saveNow() }
    }

    // The mutex keeps a debounced autosave and an upload's awaited create from
    // both taking the create branch and making duplicate listings; state is
    // re-read inside the lock so the second saver sees the id the first set.
    private suspend fun saveNow() {
        saveMutex.withLock { saveHeld() }
    }

    /** The save itself, run with [saveMutex] held; true when it was saved. */
    private suspend fun saveHeld(): Boolean {
        val current = _state.value
        if (current.isLoading || current.title.isBlank()) return false
        _state.value = _state.value.copy(saveStatus = SaveStatus.SAVING)
        val fields = buildFields(current)
        return try {
            val updated = if (current.isNew || current.listingId.isEmpty()) {
                repository.createListing(fields)
            } else {
                repository.updateListing(fields + ("id" to current.listingId))
            }
            _state.value = _state.value.copy(
                listingId = updated.id,
                isNew = false,
                saveStatus = SaveStatus.SAVED,
            )
            true
        } catch (e: Exception) {
            _state.value = _state.value.copy(saveStatus = SaveStatus.ERROR)
            _events.emit(EditListingEvent.Error(e.toMochiError()))
            false
        }
    }

    private fun buildFields(s: EditUiState): Map<String, String?> {
        val tags = s.tagsText.split(',')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        return mapOf(
            "title" to s.title,
            "description" to s.description,
            "category" to s.category.takeIf { it.isNotEmpty() },
            "condition" to s.condition?.let { conditionWire(it) },
            "type" to typeWire(s.type),
            "pricing" to pricingWire(s.pricing),
            "price" to toMinorUnits(s.priceText, s.currency).toString(),
            "currency" to currencyWire(s.currency),
            "interval" to (if (s.pricing == PricingModel.SUBSCRIPTION) intervalWire(s.interval) else null),
            // Auctions always sell one unit; otherwise 0 (unlimited) when the
            // unlimited toggle is on, else the entered count (min 1). Mirrors
            // web's edit-listing-page quantity handling.
            "quantity" to when {
                s.pricing == PricingModel.AUCTION -> "1"
                s.unlimitedStock -> "0"
                else -> (s.quantityText.toLongOrNull()?.coerceAtLeast(1L) ?: 1L).toString()
            },
            "pickup" to (if (s.pickup) "1" else "0"),
            "shipping" to (if (s.shipping) "1" else "0"),
            "location" to s.location,
            "information" to s.information,
            "tags" to gson.toJson(tags),
        )
    }

    // -------------------------------- Publish

    /**
     * Save the draft as it is on screen, then publish it, in that order and
     * under the save lock: the Comptroller publishes the draft it last
     * stored and takes no edit after, so a change still waiting for its
     * autosave, or a save under way, would otherwise go live without it. A
     * save that fails stops the publish; its error is already shown.
     */
    fun publish() {
        val current = _state.value
        // Block below-minimum prices and auction prices the server refuses at
        // the boundary, so the inline errors are not the only thing stopping
        // a doomed publish.
        if (priceBelowStripeMinimum(current) || auctionProblems(current) != (null to null)) {
            return
        }
        viewModelScope.launch {
            if (_state.value.title.isBlank()) {
                // saveHeld returns without saving on a blank title, so
                // without this publish would be silent.
                _events.emit(EditListingEvent.TitleRequired)
                return@launch
            }
            autoSaveJob?.cancel()
            saveMutex.withLock {
                if (!saveHeld() || _state.value.listingId.isEmpty()) return@withLock
                doPublish(_state.value)
            }
        }
    }

    private suspend fun doPublish(current: EditUiState) {
        _state.value = _state.value.copy(publishStatus = SaveStatus.SAVING)
        val fields = mutableMapOf<String, String?>("id" to current.listingId)
        if (current.pricing == PricingModel.AUCTION) {
            fields["reserve"] = toMinorUnits(current.reserveText, current.currency).toString()
            val instantMinor = toMinorUnits(current.instantText, current.currency)
            if (instantMinor > 0L) fields["instant"] = instantMinor.toString()
            // opens/closes are ABSOLUTE epoch seconds — the server rejects a
            // closes that isn't in the future, so a relative duration must NOT
            // be sent. A future start time goes as opens; the duration runs
            // from opens. Mirrors web's edit-listing-page publish.
            val nowSec = System.currentTimeMillis() / 1000L
            val opens = current.opensAt?.takeIf { it > nowSec } ?: nowSec
            if (opens > nowSec) fields["opens"] = opens.toString()
            fields["closes"] = (opens + current.durationDays * 24L * 60L * 60L).toString()
            current.extend?.let { extend -> fields["extend"] = extend.toString() }
            current.extension?.let { extension -> fields["extension"] = extension.toString() }
        }
        try {
            val published = repository.publishListing(fields)
            _state.value = _state.value.copy(publishStatus = SaveStatus.SAVED)
            _events.emit(EditListingEvent.Published(published.id))
        } catch (e: Exception) {
            _state.value = _state.value.copy(publishStatus = SaveStatus.ERROR)
            _events.emit(EditListingEvent.Error(e.toMochiError()))
        }
    }

    // -------------------------------- Delete

    fun deleteListing() {
        val id = _state.value.listingId
        if (id.isEmpty() || _state.value.isDeleting) return
        _state.value = _state.value.copy(isDeleting = true)
        viewModelScope.launch {
            try {
                repository.deleteListing(id)
                _events.emit(EditListingEvent.Deleted)
            } catch (e: Exception) {
                _events.emit(EditListingEvent.Error(e.toMochiError()))
            } finally {
                _state.value = _state.value.copy(isDeleting = false)
            }
        }
    }

    // -------------------------------- Photos

    fun uploadPhoto(uris: List<Uri>) {
        if (uris.isEmpty()) return
        // Make sure we have a listing row to attach photos to. The first save
        // refuses to create one without a title, so tell the seller instead of
        // silently dropping the photos.
        viewModelScope.launch {
            if (_state.value.listingId.isEmpty()) {
                if (_state.value.title.isBlank()) {
                    _events.emit(EditListingEvent.TitleRequired)
                    return@launch
                }
                saveNow()
                // Create failed; saveNow already surfaced the error.
                if (_state.value.listingId.isEmpty()) return@launch
            }
            val listingId = _state.value.listingId
            photoTracker.track {
                try {
                    val temps = repository.stageFiles(uris, fallbackName = "photo")
                    try {
                        photoTracker.expect(temps)
                        for (temp in temps) {
                            val photo = repository.uploadPhoto(listingId, temp, photoTracker)
                            _state.value = _state.value.copy(photos = _state.value.photos + photo)
                        }
                    } finally {
                        repository.discardStaged(temps)
                    }
                } catch (e: Exception) {
                    _events.emit(EditListingEvent.Error(e.toMochiError()))
                }
            }
        }
    }

    fun deletePhoto(id: String) {
        viewModelScope.launch {
            try {
                repository.deletePhoto(id)
                _state.value = _state.value.copy(
                    photos = _state.value.photos.filterNot { it.id == id },
                )
            } catch (e: Exception) {
                _events.emit(EditListingEvent.Error(e.toMochiError()))
            }
        }
    }

    fun reorderPhotos(newOrder: List<Photo>) {
        val listingId = _state.value.listingId
        if (listingId.isEmpty()) return
        _state.value = _state.value.copy(photos = newOrder)
        viewModelScope.launch {
            try {
                // The server answers the photos in their new order, which
                // replaces the order assumed above.
                val reordered = repository.reorderPhotos(listingId, newOrder.map { it.id })
                _state.value = _state.value.copy(photos = reordered.sortedBy { photo -> photo.rank })
            } catch (e: Exception) {
                _events.emit(EditListingEvent.Error(e.toMochiError()))
            }
        }
    }

    // -------------------------------- Assets

    fun uploadAsset(uris: List<Uri>) {
        if (uris.isEmpty()) return
        // Same first-save handling as uploadPhoto: no listing row without a title.
        viewModelScope.launch {
            if (_state.value.listingId.isEmpty()) {
                if (_state.value.title.isBlank()) {
                    _events.emit(EditListingEvent.TitleRequired)
                    return@launch
                }
                saveNow()
                // Create failed; saveNow already surfaced the error.
                if (_state.value.listingId.isEmpty()) return@launch
            }
            val listingId = _state.value.listingId
            assetTracker.track {
                try {
                    val temps = repository.stageFiles(uris)
                    try {
                        val (accepted, refused) = assetsWithinLimit(temps)
                        for (file in refused) _events.emit(EditListingEvent.TooLarge(file.name))
                        assetTracker.expect(accepted)
                        for (temp in accepted) {
                            val asset = repository.uploadAsset(listingId, temp, assetTracker)
                            _state.value = _state.value.copy(assets = _state.value.assets + asset)
                        }
                    } finally {
                        repository.discardStaged(temps)
                    }
                } catch (e: Exception) {
                    _events.emit(EditListingEvent.Error(e.toMochiError()))
                }
            }
        }
    }

    fun deleteAsset(id: String) {
        if (_state.value.isDeletingAsset) return
        _state.value = _state.value.copy(isDeletingAsset = true)
        viewModelScope.launch {
            try {
                repository.removeAsset(id)
                _state.value = _state.value.copy(
                    assets = _state.value.assets.filterNot { it.id == id },
                )
            } catch (e: Exception) {
                _events.emit(EditListingEvent.Error(e.toMochiError()))
            } finally {
                _state.value = _state.value.copy(isDeletingAsset = false)
            }
        }
    }

    fun reorderAssets(newOrder: List<Asset>) {
        val listingId = _state.value.listingId
        if (listingId.isEmpty()) return
        _state.value = _state.value.copy(assets = newOrder)
        viewModelScope.launch {
            try {
                _state.value = _state.value.copy(assets = repository.reorderAssets(listingId, newOrder.map { it.id }))
            } catch (e: Exception) {
                _events.emit(EditListingEvent.Error(e.toMochiError()))
            }
        }
    }

    fun addExternalAsset(filename: String, mime: String, reference: String) {
        if (filename.isBlank() || reference.isBlank()) return
        viewModelScope.launch {
            if (_state.value.listingId.isEmpty()) {
                // Same reason as publish: save() does not wait for the create.
                if (_state.value.title.isBlank()) {
                    _events.emit(EditListingEvent.TitleRequired)
                    return@launch
                }
                saveNow()
                if (_state.value.listingId.isEmpty()) return@launch
            }
            val listingId = _state.value.listingId
            try {
                val all = repository.externalAsset(listingId, filename, mime, reference)
                _state.value = _state.value.copy(assets = all)
            } catch (e: Exception) {
                _events.emit(EditListingEvent.Error(e.toMochiError()))
            }
        }
    }

    // -------------------------------- Shipping zones

    fun saveZones(zones: List<ShippingOption>) {
        _state.value = _state.value.copy(zones = zones)
        viewModelScope.launch {
            try {
                // Zones live on the server against a listing row, so a zone
                // added before the first autosave creates the row first; a
                // draft with no title cannot be created yet and keeps the
                // zones in state for the save that follows.
                if (_state.value.listingId.isEmpty()) saveNow()
                val listingId = _state.value.listingId
                if (listingId.isEmpty()) return@launch
                repository.setShipping(
                    listingId,
                    zones.map {
                        ShippingOptionInput(
                            region = it.region,
                            price = it.price.toString(),
                            currency = it.currency,
                            days = it.days,
                            notes = it.notes,
                        )
                    },
                )
            } catch (e: Exception) {
                _events.emit(EditListingEvent.Error(e.toMochiError()))
            }
        }
    }

}

/**
 * The minimum charge for [state]'s currency in minor units: the server's, once
 * read, else the compiled fallback.
 */
fun minimumCharge(state: EditUiState): Long =
    state.fees?.minimums?.get(state.currency.name.lowercase())
        ?: STRIPE_MINIMUMS[state.currency]
        ?: 0L

/**
 * Whether the price falls under the minimum charge; for an auction the
 * starting bid, which is the listing's price. Zero and empty pass for every
 * model (pay-what-you-want may sit at zero).
 */
fun priceBelowStripeMinimum(state: EditUiState): Boolean {
    val minor = toMinorUnits(state.priceText, state.currency)
    if (minor <= 0L) return false
    return minor < minimumCharge(state)
}

/**
 * Why an auction's reserve and buy-now prices would be refused, as the
 * Comptroller checks them at publish: each must be above the starting bid,
 * and the reserve not above the buy-now price. A pair of string resources,
 * null where that price is fine; both null for any other model.
 */
fun auctionProblems(state: EditUiState): Pair<Int?, Int?> {
    if (state.pricing != PricingModel.AUCTION) return null to null
    val start = toMinorUnits(state.priceText, state.currency)
    val reserve = toMinorUnits(state.reserveText, state.currency)
    val instant = toMinorUnits(state.instantText, state.currency)
    val reserveProblem = when {
        reserve > 0L && start > 0L && reserve <= start -> R.string.market_editor_reserve_below_start
        reserve > 0L && instant > 0L && reserve > instant -> R.string.market_editor_reserve_above_instant
        else -> null
    }
    val instantProblem = if (instant > 0L && start > 0L && instant <= start) {
        R.string.market_editor_instant_below_start
    } else {
        null
    }
    return reserveProblem to instantProblem
}

// -------------------------------- Wire helpers

private fun conditionWire(c: Condition): String = when (c) {
    Condition.NEW -> "new"
    Condition.USED -> "used"
    Condition.REFURBISHED -> "refurbished"
}

private fun typeWire(t: ListingType): String = when (t) {
    ListingType.PHYSICAL -> "physical"
    ListingType.DIGITAL -> "digital"
}

private fun pricingWire(p: PricingModel): String = when (p) {
    PricingModel.FIXED -> "fixed"
    PricingModel.PWYW -> "pwyw"
    PricingModel.SUBSCRIPTION -> "subscription"
    PricingModel.AUCTION -> "auction"
}

private fun currencyWire(c: Currency): String = when (c) {
    Currency.GBP -> "gbp"
    Currency.USD -> "usd"
    Currency.EUR -> "eur"
    Currency.JPY -> "jpy"
}

private fun intervalWire(i: Interval): String = when (i) {
    Interval.MONTHLY -> "monthly"
    Interval.YEARLY -> "yearly"
}

