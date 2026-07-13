// Copyright © 2026 Mochi OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.market.ui.editor

import android.content.ContentResolver
import android.net.Uri
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
import org.mochios.market.lib.toMinorUnits
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
import org.mochios.market.model.ShippingOptionInput
import org.mochios.market.repository.MarketRepository
import java.io.File
import java.io.FileOutputStream
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
    // Stock count for fixed-price/subscription listings. "0" (surfaced via
    // unlimitedStock) means unlimited; auctions always sell a single unit.
    val quantityText: String = "1",
    val unlimitedStock: Boolean = false,
    val pickup: Boolean = false,
    val shipping: Boolean = false,
    val download: Boolean = false,
    val location: String = "",
    val tagsText: String = "",
    val photos: List<Photo> = emptyList(),
    val assets: List<Asset> = emptyList(),
    val zones: List<ShippingOption> = emptyList(),
    val categories: List<Category> = emptyList(),
    val saveStatus: SaveStatus = SaveStatus.IDLE,
    val publishStatus: SaveStatus = SaveStatus.IDLE,
    val isUploadingPhoto: Boolean = false,
    val isUploadingAsset: Boolean = false,
    val stripeOnboarded: Boolean? = null,
    val error: MochiError? = null,
)

/**
 * One-shot user-facing event for the screen to surface via the snackbar host.
 */
sealed class EditListingEvent {
    data class Toast(val message: String) : EditListingEvent()
    data class Error(val error: MochiError) : EditListingEvent()
    /** Emit when the listing is deleted so the host can navigate back. */
    data object Deleted : EditListingEvent()
    /** Emit when the listing is published so the host can navigate to detail. */
    data class Published(val id: String) : EditListingEvent()
    /** Emit when photos or files are added before the listing has a title, so
     * there is no listing row to attach them to yet. */
    data object TitleRequired : EditListingEvent()
}

/**
 * Host view model for the listing editor. Holds the entire editable shape in
 * a single [EditUiState] and exposes per-field setters. The screen drives
 * auto-save via a 1-second debounce after the last edit, and an explicit
 * Publish button.
 *
 * Route parameter: `id` from `MarketApp.LISTING_EDIT` (numeric) or the literal
 * "new" / blank from `MarketApp.NEW_LISTING` to start a fresh draft.
 */
@HiltViewModel
class EditListingViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: MarketRepository,
) : ViewModel() {

    private val rawId: String = savedStateHandle.get<String>("id").orEmpty()
    private val isNew: Boolean = rawId.isEmpty() || rawId == "new"
    private val initialId: String = if (isNew) "" else rawId
    // Title collected by the new-listing dialog on MyListingsScreen. Seeding it
    // here means a photo added before any edit already has a title to create
    // the listing row with; the row itself is still created lazily on first save.
    private val initialTitle: String = savedStateHandle.get<String>("title").orEmpty()

    private val _state = MutableStateFlow(
        EditUiState(isNew = isNew, listingId = initialId, title = initialTitle),
    )
    val state: StateFlow<EditUiState> = _state.asStateFlow()

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
                if (isNew) {
                    _state.value = _state.value.copy(
                        isLoading = false,
                        isNew = true,
                        listingId = "",
                        categories = categories,
                        stripeOnboarded = stripe?.chargesEnabled,
                    )
                } else {
                    val detail = repository.getListing(initialId)
                    _state.value = fromListing(
                        detail.listing,
                        detail.shipping,
                        detail.assets,
                        categories,
                        stripe?.chargesEnabled,
                    )
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
    ): EditUiState {
        val type = listing.type ?: ListingType.PHYSICAL
        val pricing = listing.pricing ?: PricingModel.FIXED
        val currency = listing.currency ?: Currency.GBP
        val majorFromMinor: (Long) -> String = { minor ->
            if (minor == 0L) "" else minorToMajor(minor, currency)
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
            pickup = listing.pickup == 1L,
            shipping = listing.shipping == 1L,
            download = type == ListingType.DIGITAL,
            location = listing.location,
            tagsText = tags.joinToString(", "),
            photos = listing.photo?.let { listOf(it) } ?: emptyList(),
            assets = assets,
            zones = zones,
            categories = categories,
            saveStatus = SaveStatus.IDLE,
            stripeOnboarded = stripeOnboarded,
        )
    }

    // -------------------------------- Setters

    fun setTitle(value: String) = mutate { it.copy(title = value) }
    fun setDescription(value: String) = mutate { it.copy(description = value) }
    fun setCategory(value: String) = mutate { it.copy(category = value) }
    fun setQuantity(value: String) = mutate { it.copy(quantityText = value.filter { c -> c.isDigit() }) }
    fun setOpensAt(value: Long?) = mutate { it.copy(opensAt = value) }
    fun setUnlimitedStock(value: Boolean) = mutate {
        it.copy(unlimitedStock = value, quantityText = if (value) it.quantityText else it.quantityText.ifBlank { "1" })
    }
    fun setCondition(value: Condition?) = mutate { it.copy(condition = value) }

    fun setType(value: ListingType) = mutate {
        // Switching to digital implicitly disables shipping/pickup; switching
        // to physical disables direct download.
        if (value == ListingType.DIGITAL) {
            it.copy(type = value, shipping = false, pickup = false)
        } else {
            it.copy(type = value, download = false)
        }
    }

    /**
     * Switching the pricing model preserves every user-entered field that isn't
     * specific to the previous model — title, description, category, condition,
     * tags, location, currency, photos, assets, zones and the headline price
     * all stay put. Only the model-specific fields reset so a previously-typed
     * auction reserve doesn't silently follow the user into a fixed-price
     * listing. Web's `pricing-model-selector.tsx` does the same scrub when the
     * selected radio changes.
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

    // Persist the current state, creating the listing row on first save. The
    // mutex serialises concurrent saves (debounced autosave vs. an awaited
    // create from uploadPhoto/uploadAsset) so two in-flight first saves can't
    // both take the create branch and produce duplicate listings; the state is
    // re-read inside the lock so the second saver sees the id the first set.
    private suspend fun saveNow() {
        saveMutex.withLock {
            val current = _state.value
            if (current.isLoading || current.title.isBlank()) return
            _state.value = _state.value.copy(saveStatus = SaveStatus.SAVING)
            val fields = buildFields(current)
            try {
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
            } catch (e: Exception) {
                _state.value = _state.value.copy(saveStatus = SaveStatus.ERROR)
                _events.emit(EditListingEvent.Error(e.toMochiError()))
            }
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
            "tags" to gson.toJson(tags),
        )
    }

    // -------------------------------- Publish

    fun publish() {
        val current = _state.value
        // Block below-minimum prices at the boundary so the inline error on the
        // price field isn't the only thing stopping a doomed Stripe charge from
        // being created server-side.
        if (priceBelowStripeMinimum(current)) {
            return
        }
        if (current.listingId.isEmpty()) {
            // Make sure the draft has been saved before publishing.
            viewModelScope.launch {
                save()
                if (_state.value.listingId.isNotEmpty()) {
                    doPublish(_state.value)
                }
            }
            return
        }
        viewModelScope.launch { doPublish(current) }
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
        if (id.isEmpty()) return
        viewModelScope.launch {
            try {
                repository.deleteListing(id)
                _events.emit(EditListingEvent.Deleted)
            } catch (e: Exception) {
                _events.emit(EditListingEvent.Error(e.toMochiError()))
            }
        }
    }

    // -------------------------------- Appeal

    fun appeal(reason: String) {
        val id = _state.value.listingId
        if (id.isEmpty() || reason.isBlank()) return
        viewModelScope.launch {
            try {
                repository.appealListing(id, reason.trim())
                _events.emit(EditListingEvent.Toast("Appeal submitted"))
            } catch (e: Exception) {
                _events.emit(EditListingEvent.Error(e.toMochiError()))
            }
        }
    }

    // -------------------------------- Photos

    fun uploadPhoto(
        uris: List<Uri>,
        contentResolver: ContentResolver,
        cacheDir: File,
    ) {
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
            _state.value = _state.value.copy(isUploadingPhoto = true)
            val temps = mutableListOf<File>()
            try {
                for (uri in uris) {
                    val name = displayName(contentResolver, uri) ?: uri.lastPathSegment ?: "photo"
                    val temp = File(cacheDir, "market_photo_${System.nanoTime()}_$name")
                    contentResolver.openInputStream(uri)?.use { input ->
                        FileOutputStream(temp).use { out -> input.copyTo(out) }
                    } ?: throw IllegalStateException("Cannot open $uri")
                    temps.add(temp)
                    val photo = repository.uploadPhoto(listingId, temp)
                    _state.value = _state.value.copy(photos = _state.value.photos + photo)
                }
            } catch (e: Exception) {
                _events.emit(EditListingEvent.Error(e.toMochiError()))
            } finally {
                temps.forEach { runCatching { it.delete() } }
                _state.value = _state.value.copy(isUploadingPhoto = false)
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
                repository.reorderPhotos(listingId, newOrder.map { it.id })
            } catch (e: Exception) {
                _events.emit(EditListingEvent.Error(e.toMochiError()))
            }
        }
    }

    // -------------------------------- Assets

    fun uploadAsset(
        uris: List<Uri>,
        contentResolver: ContentResolver,
        cacheDir: File,
    ) {
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
            _state.value = _state.value.copy(isUploadingAsset = true)
            val temps = mutableListOf<File>()
            try {
                for (uri in uris) {
                    val name = displayName(contentResolver, uri) ?: uri.lastPathSegment ?: "file"
                    val temp = File(cacheDir, "market_asset_${System.nanoTime()}_$name")
                    contentResolver.openInputStream(uri)?.use { input ->
                        FileOutputStream(temp).use { out -> input.copyTo(out) }
                    } ?: throw IllegalStateException("Cannot open $uri")
                    temps.add(temp)
                    val asset = repository.uploadAsset(listingId, temp)
                    _state.value = _state.value.copy(assets = _state.value.assets + asset)
                }
            } catch (e: Exception) {
                _events.emit(EditListingEvent.Error(e.toMochiError()))
            } finally {
                temps.forEach { runCatching { it.delete() } }
                _state.value = _state.value.copy(isUploadingAsset = false)
            }
        }
    }

    fun deleteAsset(id: String) {
        viewModelScope.launch {
            try {
                repository.removeAsset(id)
                _state.value = _state.value.copy(
                    assets = _state.value.assets.filterNot { it.id == id },
                )
            } catch (e: Exception) {
                _events.emit(EditListingEvent.Error(e.toMochiError()))
            }
        }
    }

    fun reorderAssets(newOrder: List<Asset>) {
        val listingId = _state.value.listingId
        if (listingId.isEmpty()) return
        _state.value = _state.value.copy(assets = newOrder)
        viewModelScope.launch {
            try {
                repository.reorderAssets(listingId, newOrder.map { it.id })
            } catch (e: Exception) {
                _events.emit(EditListingEvent.Error(e.toMochiError()))
            }
        }
    }

    fun addExternalAsset(filename: String, mime: String, reference: String) {
        if (filename.isBlank() || reference.isBlank()) return
        viewModelScope.launch {
            if (_state.value.listingId.isEmpty()) {
                save()
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
        val listingId = _state.value.listingId
        _state.value = _state.value.copy(zones = zones)
        if (listingId.isEmpty()) return
        viewModelScope.launch {
            try {
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

    // -------------------------------- Helpers

    private fun displayName(resolver: ContentResolver, uri: Uri): String? {
        return runCatching {
            resolver.query(
                uri,
                arrayOf(android.provider.OpenableColumns.DISPLAY_NAME),
                null,
                null,
                null,
            )?.use {
                if (it.moveToFirst()) it.getString(0) else null
            }
        }.getOrNull()
    }
}

/**
 * True when the price the seller has typed is a positive amount below Stripe's
 * minimum charge for the chosen currency. Auction listings use the reserve
 * field instead of the headline price (the editor stashes auction values in
 * [EditUiState.reserveText]). PWYW listings can sit at zero so an empty / zero
 * price is allowed for every model.
 */
fun priceBelowStripeMinimum(state: EditUiState): Boolean {
    val text = if (state.pricing == PricingModel.AUCTION) state.reserveText else state.priceText
    val minor = toMinorUnits(text, state.currency)
    if (minor <= 0L) return false
    val minimum = STRIPE_MINIMUMS[state.currency] ?: return false
    return minor < minimum
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

/** Convert a minor-unit amount back to a major-unit string for prefilled fields. */
private fun minorToMajor(amount: Long, currency: Currency): String {
    val decimals = when (currency) {
        Currency.JPY -> 0
        Currency.GBP, Currency.USD, Currency.EUR -> 2
    }
    if (decimals == 0) return amount.toString()
    val factor = 10.0.let { pow ->
        var r = 1.0
        repeat(decimals) { r *= pow }
        r
    }
    val major = amount / factor
    return if (decimals == 0) major.toLong().toString()
    else String.format("%.${decimals}f", major)
}
