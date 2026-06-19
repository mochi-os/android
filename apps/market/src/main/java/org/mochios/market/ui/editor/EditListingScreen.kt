package org.mochios.market.ui.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.google.gson.Gson
import org.mochios.android.api.userMessage
import org.mochios.android.model.PlaceData
import org.mochios.android.ui.components.PlacePicker
import org.mochios.android.ui.components.Section
import org.mochios.market.R
import org.mochios.market.lib.formatPrice
import org.mochios.market.lib.toMinorUnits
import org.mochios.market.model.Category
import org.mochios.market.model.Condition
import org.mochios.market.model.Currency
import org.mochios.market.model.ListingType
import org.mochios.market.model.PricingModel

/**
 * Listing editor — single screen handling both creation (route
 * `MarketApp.NEW_LISTING`, id = "new") and editing existing rows
 * (`MarketApp.LISTING_EDIT`).
 *
 * Mirrors `apps/market/web/src/features/listings/edit-listing.tsx`. Each
 * section is a [Section] card; the body is a vertically-scrolling column.
 * Auto-save is triggered by [EditListingViewModel] one second after the last
 * edit; the top app bar shows the save status (idle / saving / saved / error)
 * and an explicit Publish button is available at the foot.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditListingScreen(
    navController: NavController,
    viewModel: EditListingViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current

    // Surface ViewModel-emitted events onto the snackbar host and handle the
    // terminal events (deleted / published) by navigating back.
    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is EditListingEvent.Toast -> snackbar.showSnackbar(event.message)
                is EditListingEvent.Error -> snackbar.showSnackbar(event.error.userMessage())
                is EditListingEvent.Deleted -> navController.popBackStack()
                is EditListingEvent.Published -> navController.popBackStack()
            }
        }
    }

    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showAppealDialog by remember { mutableStateOf(false) }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (state.isNew) stringResource(R.string.market_editor_title_new)
                        else stringResource(R.string.market_editor_title_edit),
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.market_editor_back),
                        )
                    }
                },
                actions = {
                    SaveStatusBadge(state.saveStatus)
                    TextButton(
                        onClick = { viewModel.save() },
                        enabled = state.saveStatus != SaveStatus.SAVING &&
                            state.title.isNotBlank() &&
                            !priceBelowStripeMinimum(state),
                    ) {
                        Text(stringResource(R.string.market_editor_save))
                    }
                },
            )
        },
    ) { padding ->
        if (state.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Stripe onboarding banner at top if account is not yet onboarded
            // — gated explicitly on `== false` so the banner stays hidden
            // while the status is still loading.
            if (state.stripeOnboarded == false) {
                StripeOnboardingBanner()
            }

            // ---- Basic info ----
            Section(title = stringResource(R.string.market_editor_section_basic)) {
                OutlinedTextField(
                    value = state.title,
                    onValueChange = viewModel::setTitle,
                    label = { Text(stringResource(R.string.market_editor_title)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.padding(top = 8.dp))
                OutlinedTextField(
                    value = state.description,
                    onValueChange = viewModel::setDescription,
                    label = { Text(stringResource(R.string.market_editor_description)) },
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.padding(top = 8.dp))
                CategoryDropdown(
                    categories = state.categories,
                    selected = state.category,
                    onChange = viewModel::setCategory,
                )
                Spacer(Modifier.padding(top = 8.dp))
                ConditionRadioRow(
                    condition = state.condition,
                    onChange = viewModel::setCondition,
                )
                // Stock — only meaningful for non-auction listings (auctions
                // sell a single unit). Unlimited toggle maps to quantity 0.
                if (state.pricing != PricingModel.AUCTION) {
                    Spacer(Modifier.padding(top = 8.dp))
                    OutlinedTextField(
                        value = if (state.unlimitedStock) "" else state.quantityText,
                        onValueChange = viewModel::setQuantity,
                        label = { Text(stringResource(R.string.market_editor_stock)) },
                        placeholder = {
                            Text(
                                stringResource(
                                    if (state.unlimitedStock) R.string.market_editor_stock_unlimited
                                    else R.string.market_editor_stock_units
                                )
                            )
                        },
                        enabled = !state.unlimitedStock,
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    SwitchRow(
                        label = stringResource(R.string.market_editor_stock_unlimited),
                        checked = state.unlimitedStock,
                        onChange = viewModel::setUnlimitedStock,
                    )
                }
            }

            // ---- Pricing ----
            Section(title = stringResource(R.string.market_editor_section_pricing)) {
                PricingModelSelector(
                    pricing = state.pricing,
                    currency = state.currency,
                    priceText = state.priceText,
                    interval = state.interval,
                    durationDays = state.durationDays,
                    reserveText = state.reserveText,
                    instantText = state.instantText,
                    opensAt = state.opensAt,
                    onPricingChange = viewModel::setPricing,
                    onCurrencyChange = viewModel::setCurrency,
                    onPriceChange = viewModel::setPriceText,
                    onIntervalChange = viewModel::setInterval,
                    onDurationChange = viewModel::setDurationDays,
                    onReserveChange = viewModel::setReserveText,
                    onInstantChange = viewModel::setInstantText,
                    onOpensChange = viewModel::setOpensAt,
                )
            }

            // ---- Photos ----
            Section(title = stringResource(R.string.market_editor_section_photos)) {
                PhotoManager(
                    photos = state.photos,
                    onUpload = { uris ->
                        viewModel.uploadPhoto(uris, context.contentResolver, context.cacheDir)
                    },
                    onDelete = viewModel::deletePhoto,
                    onReorder = viewModel::reorderPhotos,
                    isUploading = state.isUploadingPhoto,
                )
            }

            // ---- Digital assets (only for digital listings) ----
            if (state.type == ListingType.DIGITAL) {
                Section(title = stringResource(R.string.market_editor_section_assets)) {
                    DigitalAssetsManager(
                        assets = state.assets,
                        onUpload = { uris ->
                            viewModel.uploadAsset(uris, context.contentResolver, context.cacheDir)
                        },
                        onDelete = viewModel::deleteAsset,
                        onReorder = viewModel::reorderAssets,
                        onAddExternal = viewModel::addExternalAsset,
                        isUploading = state.isUploadingAsset,
                    )
                }
            }

            // ---- Delivery ----
            Section(title = stringResource(R.string.market_editor_section_delivery)) {
                DeliveryTypeRadioRow(
                    type = state.type,
                    onChange = viewModel::setType,
                )
                Spacer(Modifier.padding(top = 8.dp))
                if (state.type == ListingType.PHYSICAL) {
                    SwitchRow(
                        label = stringResource(R.string.market_editor_pickup),
                        checked = state.pickup,
                        onChange = viewModel::setPickup,
                    )
                    SwitchRow(
                        label = stringResource(R.string.market_editor_shipping),
                        checked = state.shipping,
                        onChange = viewModel::setShipping,
                    )
                } else {
                    SwitchRow(
                        label = stringResource(R.string.market_editor_download),
                        checked = state.download,
                        onChange = viewModel::setDownload,
                    )
                }
            }

            // ---- Shipping zones (conditional) ----
            if (state.type == ListingType.PHYSICAL && state.shipping) {
                Section(title = stringResource(R.string.market_editor_section_shipping_zones)) {
                    ShippingZonesEditor(
                        zones = state.zones,
                        currency = state.currency,
                        onChange = viewModel::saveZones,
                    )
                }
            }

            // ---- Location ----
            Section(title = stringResource(R.string.market_editor_section_location)) {
                val parsed = remember(state.location) {
                    parseLocationToPlace(state.location)
                }
                PlacePicker(
                    place = parsed,
                    onPlaceSelected = { place ->
                        viewModel.setLocation(Gson().toJson(place))
                    },
                )
            }

            // ---- Tags ----
            Section(title = stringResource(R.string.market_editor_section_tags)) {
                OutlinedTextField(
                    value = state.tagsText,
                    onValueChange = viewModel::setTagsText,
                    label = { Text(stringResource(R.string.market_editor_tags_field)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            // ---- Fee preview (only when price > 0) ----
            val priceMinor = remember(state.priceText, state.currency) {
                toMinorUnits(state.priceText, state.currency)
            }
            if (priceMinor > 0L) {
                Section(title = stringResource(R.string.market_editor_section_fee_preview)) {
                    FeePreview(
                        price = priceMinor,
                        currency = state.currency,
                        pricingModel = state.pricing,
                    )
                }
            }

            // ---- Publish / Delete actions ----
            Spacer(Modifier.padding(top = 8.dp))
            Button(
                onClick = { viewModel.publish() },
                enabled = state.publishStatus != SaveStatus.SAVING &&
                    state.title.isNotBlank() &&
                    !priceBelowStripeMinimum(state),
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (state.publishStatus == SaveStatus.SAVING) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.market_editor_publishing))
                } else {
                    Text(stringResource(R.string.market_editor_publish))
                }
            }
            if (!state.isNew) {
                OutlinedButton(
                    onClick = { showDeleteConfirm = true },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.market_editor_delete))
                }
                OutlinedButton(
                    onClick = { showAppealDialog = true },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.market_editor_appeal))
                }
            }
            Spacer(Modifier.padding(top = 24.dp))
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text(stringResource(R.string.market_editor_delete_confirm_title)) },
            text = { Text(stringResource(R.string.market_editor_delete_confirm_message)) },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteConfirm = false
                    viewModel.deleteListing()
                }) {
                    Text(stringResource(R.string.market_editor_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text(stringResource(R.string.market_editor_zone_cancel))
                }
            },
        )
    }

    if (showAppealDialog) {
        var reason by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showAppealDialog = false },
            title = { Text(stringResource(R.string.market_editor_appeal)) },
            text = {
                OutlinedTextField(
                    value = reason,
                    onValueChange = { reason = it },
                    label = { Text(stringResource(R.string.market_editor_appeal_reason)) },
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(
                    enabled = reason.isNotBlank(),
                    onClick = {
                        viewModel.appeal(reason)
                        showAppealDialog = false
                    },
                ) {
                    Text(stringResource(R.string.market_editor_appeal_submit))
                }
            },
            dismissButton = {
                TextButton(onClick = { showAppealDialog = false }) {
                    Text(stringResource(R.string.market_editor_zone_cancel))
                }
            },
        )
    }
}

@Composable
private fun SaveStatusBadge(status: SaveStatus) {
    when (status) {
        SaveStatus.SAVING -> {
            CircularProgressIndicator(
                modifier = Modifier
                    .padding(end = 4.dp)
                    .size(16.dp),
                strokeWidth = 2.dp,
            )
        }
        SaveStatus.SAVED -> {
            Icon(
                Icons.Default.Check,
                contentDescription = stringResource(R.string.market_editor_saved),
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .padding(end = 4.dp)
                    .size(20.dp),
            )
        }
        SaveStatus.ERROR -> {
            Icon(
                Icons.Default.Error,
                contentDescription = stringResource(R.string.market_editor_save_failed),
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier
                    .padding(end = 4.dp)
                    .size(20.dp),
            )
        }
        SaveStatus.IDLE -> Unit
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CategoryDropdown(
    categories: List<Category>,
    selected: String,
    onChange: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val active = categories.firstOrNull { it.id == selected }
    val label = active?.name ?: stringResource(R.string.market_editor_category_none)
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
    ) {
        OutlinedTextField(
            value = label,
            onValueChange = {},
            readOnly = true,
            label = { Text(stringResource(R.string.market_editor_category)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(),
        )
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            categories.forEach { cat ->
                DropdownMenuItem(
                    text = { Text(cat.name) },
                    onClick = {
                        onChange(cat.id)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
private fun ConditionRadioRow(
    condition: Condition?,
    onChange: (Condition?) -> Unit,
) {
    Column {
        Text(
            text = stringResource(R.string.market_editor_condition),
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(bottom = 4.dp),
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            RadioRowItem(
                selected = condition == Condition.NEW,
                onSelect = { onChange(Condition.NEW) },
                label = stringResource(R.string.market_editor_condition_new),
            )
            RadioRowItem(
                selected = condition == Condition.USED,
                onSelect = { onChange(Condition.USED) },
                label = stringResource(R.string.market_editor_condition_used),
            )
            RadioRowItem(
                selected = condition == Condition.REFURBISHED,
                onSelect = { onChange(Condition.REFURBISHED) },
                label = stringResource(R.string.market_editor_condition_refurbished),
            )
        }
    }
}

@Composable
private fun DeliveryTypeRadioRow(
    type: ListingType,
    onChange: (ListingType) -> Unit,
) {
    Column {
        Text(
            text = stringResource(R.string.market_editor_type),
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(bottom = 4.dp),
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            RadioRowItem(
                selected = type == ListingType.PHYSICAL,
                onSelect = { onChange(ListingType.PHYSICAL) },
                label = stringResource(R.string.market_editor_type_physical),
            )
            RadioRowItem(
                selected = type == ListingType.DIGITAL,
                onSelect = { onChange(ListingType.DIGITAL) },
                label = stringResource(R.string.market_editor_type_digital),
            )
        }
    }
}

@Composable
private fun RadioRowItem(
    selected: Boolean,
    onSelect: () -> Unit,
    label: String,
) {
    Row(
        modifier = Modifier
            .selectable(selected = selected, onClick = onSelect)
            .padding(start = 0.dp, top = 4.dp, end = 16.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onSelect)
        Spacer(Modifier.width(4.dp))
        Text(label, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun SwitchRow(
    label: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

/**
 * Stub for the shared `StripeOnboardingBanner` composable from `market/ui/
 * components/` while the parallel agent's version is in flight. Once the
 * shared composable lands, callers should drop this stub and import the
 * shared one (same signature: no parameters required).
 */
@Composable
private fun StripeOnboardingBanner() {
    Surface(
        color = MaterialTheme.colorScheme.tertiaryContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.market_editor_stripe_banner_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onTertiaryContainer,
            )
            Text(
                text = stringResource(R.string.market_editor_stripe_banner_description),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onTertiaryContainer,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

/**
 * Stub for the shared `FeePreview` composable. Renders a single-line
 * formatted price so the editor compiles before the shared composable
 * arrives. The real version (from the parallel agent) is signature-
 * compatible — no parameter rename required when the stub is removed.
 */
@Composable
private fun FeePreview(
    price: Long,
    currency: Currency,
    pricingModel: PricingModel,
) {
    Text(
        text = formatPrice(price, currency),
        style = MaterialTheme.typography.bodyMedium,
    )
    Text(
        text = pricingModel.name.lowercase(),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/**
 * Decode the listing.location JSON blob into the [PlaceData] shape expected
 * by [PlacePicker]. Best-effort — returns null when the input is blank so
 * the picker shows its empty state.
 */
private fun parseLocationToPlace(json: String): PlaceData? {
    if (json.isBlank()) return null
    return runCatching {
        Gson().fromJson(json, PlaceData::class.java)
    }.getOrNull() ?: PlaceData(name = json.trim())
}
