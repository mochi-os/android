package org.mochios.people.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import kotlinx.coroutines.launch
import org.mochios.android.ui.components.EntityAvatar
import org.mochios.android.ui.components.HtmlContent
import org.mochios.android.R as MochiR
import org.mochios.people.R
import org.mochios.people.model.PersonInformation
import org.mochios.people.ui.components.PeopleSidebar
import org.mochios.people.ui.components.PeopleSidebarSection

/**
 * Profile editor for the current user. Mirrors the web `<Profile>` page:
 *
 *  - Hero card at the top: banner background, avatar overlay, display name
 *    and short fingerprint underneath. Lives-updates as drafts change so the
 *    user sees the effect of their accent / avatar / banner choices before
 *    saving.
 *  - Display name: inline text field with a Save button.
 *  - Bio (the server calls it "profile"): multi-line text area with a length
 *    indicator and a Save button. Capped at 100 KB.
 *  - Accent: small swatch + "Change" button → opens [ColourPickerDialog].
 *  - Privacy: switch to toggle public / private (directory listing).
 *  - Three image slots: avatar, banner, favicon. Each picks a system image
 *    via [rememberImagePicker], resizes it, then uploads via the ViewModel.
 *
 * Loading state shows a centred spinner; the first error blocks the entire
 * screen with a retry button. Subsequent in-flight errors are surfaced via
 * a Snackbar to avoid losing the form state mid-edit.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    onSwitchSection: (PeopleSidebarSection) -> Unit,
    @Suppress("unused") onLogout: () -> Unit,
    @Suppress("unused") onOpenNotifications: () -> Unit,
    viewModel: ProfileViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var showPreview by remember { mutableStateOf(false) }
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val drawerScope = rememberCoroutineScope()

    // Surface non-fatal errors as snackbars while the screen is loaded.
    LaunchedEffect(state.error) {
        val err = state.error ?: return@LaunchedEffect
        if (state.info != null) {
            val msg = err.message?.takeUnless { it.isBlank() }
                ?: context.getString(R.string.people_profile_save_failed)
            scope.launch { snackbar.showSnackbar(msg) }
            viewModel.clearError()
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            PeopleSidebar(
                current = PeopleSidebarSection.PROFILE,
                onSelect = { section ->
                    drawerScope.launch { drawerState.close() }
                    if (section != PeopleSidebarSection.PROFILE) onSwitchSection(section)
                },
            )
        },
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(R.string.people_profile_title)) },
                    navigationIcon = {
                        IconButton(onClick = { drawerScope.launch { drawerState.open() } }) {
                            Icon(
                                Icons.Default.Menu,
                                contentDescription = stringResource(R.string.people_open_sidebar),
                            )
                        }
                    },
                    actions = {
                        if (state.info != null) {
                            IconButton(onClick = { showPreview = true }) {
                                Icon(
                                    Icons.Filled.Visibility,
                                    contentDescription = stringResource(R.string.people_profile_preview),
                                )
                            }
                        }
                    },
                )
            },
            snackbarHost = { SnackbarHost(snackbar) },
        ) { padding ->
            when {
                state.isLoading -> LoadingBlock(padding)
                state.info == null && state.error != null -> ErrorBlock(padding) { viewModel.refresh() }
                state.info != null -> Editor(padding, state, viewModel, snackbar, scope, context)
            }
        }
    }

    if (showPreview && state.info != null) {
        ProfilePreviewDialog(
            state = state,
            serverUrl = viewModel.serverUrl,
            onDismiss = { showPreview = false },
        )
    }
}

@Composable
private fun LoadingBlock(padding: PaddingValues) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator()
    }
}

@Composable
private fun ErrorBlock(padding: PaddingValues, onRetry: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.people_profile_save_failed),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.error,
        )
        Spacer(Modifier.height(12.dp))
        Button(onClick = onRetry) {
            Text(stringResource(R.string.people_profile_retry))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Editor(
    padding: PaddingValues,
    state: ProfileUiState,
    viewModel: ProfileViewModel,
    snackbar: SnackbarHostState,
    scope: kotlinx.coroutines.CoroutineScope,
    context: android.content.Context,
) {
    val info = state.info ?: return
    var showColourPicker by remember { mutableStateOf(false) }
    var showSizeWarning by remember { mutableStateOf<String?>(null) }

    val accentPreview = state.accentDraft.trim().takeIf { ProfileViewModel.ACCENT_PATTERN.matches(it) }
    val avatarUrl = imageUrl(viewModel.serverUrl, info.fingerprint, "avatar", info.avatar)
    val bannerUrl = imageUrl(viewModel.serverUrl, info.fingerprint, "banner", info.banner)
    val faviconUrl = imageUrl(viewModel.serverUrl, info.fingerprint, "favicon", info.favicon)

    val avatarPicker = rememberImagePicker(
        slot = ImageSlot.AVATAR,
        onPicked = viewModel::uploadAvatar,
        onError = { scope.launch { snackbar.showSnackbar(context.getString(R.string.people_profile_image_process_failed, "avatar")) } },
        onTooLarge = { showSizeWarning = context.getString(R.string.people_profile_file_too_large) },
    )
    val bannerPicker = rememberImagePicker(
        slot = ImageSlot.BANNER,
        onPicked = viewModel::uploadBanner,
        onError = { scope.launch { snackbar.showSnackbar(context.getString(R.string.people_profile_image_process_failed, "banner")) } },
        onTooLarge = { showSizeWarning = context.getString(R.string.people_profile_file_too_large) },
    )
    val faviconPicker = rememberImagePicker(
        slot = ImageSlot.FAVICON,
        onPicked = viewModel::uploadFavicon,
        onError = { scope.launch { snackbar.showSnackbar(context.getString(R.string.people_profile_image_process_failed, "favicon")) } },
        onTooLarge = { showSizeWarning = context.getString(R.string.people_profile_file_too_large) },
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .verticalScroll(rememberScrollState()),
    ) {
        // ───── Hero / preview card ─────
        PreviewCard(
            name = info.name.ifBlank { state.nameDraft },
            fingerprint = info.fingerprint,
            avatarUrl = avatarUrl,
            bannerUrl = bannerUrl,
            accent = accentPreview,
            uploadingAvatar = state.savingSlot == ImageSlot.AVATAR,
            uploadingBanner = state.savingSlot == ImageSlot.BANNER,
            onChangeAvatar = avatarPicker.launch,
            onChangeBanner = bannerPicker.launch,
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            NameSection(state, info, viewModel)
            HorizontalDivider()
            BioSection(state, info, viewModel)
            HorizontalDivider()
            AccentSection(state, accentPreview, viewModel, onOpenPicker = { showColourPicker = true })
            HorizontalDivider()
            FaviconSection(
                faviconUrl = faviconUrl,
                uploading = state.savingSlot == ImageSlot.FAVICON,
                onUpload = faviconPicker.launch,
            )
            HorizontalDivider()
            PrivacySection(state, viewModel)
        }
    }

    if (showColourPicker) {
        ColourPickerDialog(
            initial = state.accentDraft,
            onDismiss = { showColourPicker = false },
            onConfirm = { hex ->
                viewModel.setAccentDraft(hex)
                viewModel.saveAccent(
                    onSuccess = { scope.launch { snackbar.showSnackbar(context.getString(R.string.people_profile_accent_saved)) } },
                    onError = { scope.launch { snackbar.showSnackbar(context.getString(R.string.people_profile_save_accent_failed)) } },
                )
                showColourPicker = false
            },
        )
    }

    showSizeWarning?.let { msg ->
        AlertDialog(
            onDismissRequest = { showSizeWarning = null },
            title = { Text(stringResource(R.string.people_profile_file_too_large)) },
            text = { Text(msg) },
            confirmButton = {
                TextButton(onClick = { showSizeWarning = null }) {
                    Text(stringResource(R.string.people_common_close))
                }
            },
        )
    }
}

@Composable
private fun PreviewCard(
    name: String,
    fingerprint: String,
    avatarUrl: String?,
    bannerUrl: String?,
    accent: String?,
    uploadingAvatar: Boolean,
    uploadingBanner: Boolean,
    onChangeAvatar: () -> Unit,
    onChangeBanner: () -> Unit,
) {
    val avatarSize = 80.dp
    Box(modifier = Modifier.fillMaxWidth()) {
        // Banner — 3:1 aspect, falls back to a muted placeholder with a
        // camera icon when no banner is set.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(3f)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            if (bannerUrl != null) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(bannerUrl)
                        .crossfade(true)
                        .build(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.AccountCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                    )
                    Text(
                        text = stringResource(R.string.people_profile_no_banner),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            // Top-right "Change banner" button.
            Row(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp),
            ) {
                OutlinedButton(
                    onClick = onChangeBanner,
                    enabled = !uploadingBanner,
                ) {
                    Icon(
                        Icons.Filled.CloudUpload,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = if (uploadingBanner)
                            stringResource(R.string.people_profile_uploading)
                        else stringResource(R.string.people_profile_change_banner),
                    )
                }
            }
        }

        // Avatar pinned over the banner's bottom-left edge.
        Box(
            modifier = Modifier
                .padding(start = 16.dp)
                .offset(y = avatarSize / 2)
                .align(Alignment.BottomStart),
        ) {
            Box(
                modifier = Modifier
                    .size(avatarSize)
                    .clip(CircleShape)
                    .border(
                        4.dp,
                        MaterialTheme.colorScheme.surface,
                        CircleShape,
                    ),
            ) {
                EntityAvatar(
                    name = name,
                    src = avatarUrl,
                    seed = fingerprint,
                    size = avatarSize,
                    accent = accent,
                )
            }
            IconButton(
                onClick = onChangeAvatar,
                enabled = !uploadingAvatar,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .offset(x = 2.dp, y = 2.dp)
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surface)
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape),
            ) {
                Icon(
                    Icons.Filled.CloudUpload,
                    contentDescription = stringResource(R.string.people_profile_upload_avatar),
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }

    // Identity row underneath the hero. Pad enough to clear the half-
    // overhanging avatar.
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 48.dp, start = 16.dp, end = 16.dp),
    ) {
        Text(
            text = name.ifBlank { "—" },
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
        )
        if (fingerprint.isNotBlank()) {
            Text(
                text = fingerprint.take(9),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun NameSection(
    state: ProfileUiState,
    info: PersonInformation,
    viewModel: ProfileViewModel,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarText = stringResource(R.string.people_profile_name_saved)
    val errorText = stringResource(R.string.people_profile_save_name_failed)

    val trimmed = state.nameDraft.trim()
    val dirty = trimmed.isNotEmpty() && trimmed != info.name

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            stringResource(R.string.people_profile_name),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedTextField(
            value = state.nameDraft,
            onValueChange = viewModel::setNameDraft,
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
        ) {
            Button(
                onClick = {
                    viewModel.saveName(
                        onSuccess = { scope.launch { /* snackbar lives at scaffold root */ } },
                        onError = { /* shown via state.error → snackbar */ },
                    )
                },
                enabled = dirty && !state.isSaving,
            ) {
                Text(
                    if (state.isSaving) stringResource(R.string.people_profile_saving)
                    else stringResource(R.string.people_profile_save),
                )
            }
        }
    }
    // Toast-on-success path: trigger after info.name changes.
    LaunchedEffect(info.name) {
        // No-op placeholder — onSuccess fires the scope.launch in saveName().
        // Kept for symmetry with future logic.
        if (info.name == trimmed && dirty.not()) {
            // nothing
        }
    }
}

@Composable
private fun BioSection(
    state: ProfileUiState,
    info: PersonInformation,
    viewModel: ProfileViewModel,
) {
    val length = state.bioDraft.length
    val tooLong = length > ProfileViewModel.BIO_MAX
    val dirty = state.bioDraft != info.profile
    val progress = (length.toFloat() / ProfileViewModel.BIO_MAX).coerceIn(0f, 1f)

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            stringResource(R.string.people_profile_identity),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedTextField(
            value = state.bioDraft,
            onValueChange = viewModel::setBioDraft,
            placeholder = { Text(stringResource(R.string.people_profile_markdown_supported)) },
            minLines = 4,
            maxLines = 10,
            isError = tooLong,
            modifier = Modifier.fillMaxWidth(),
        )
        LinearProgressIndicator(
            progress = { progress },
            color = if (tooLong) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
            modifier = Modifier.fillMaxWidth().height(4.dp),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "$length / ${ProfileViewModel.BIO_MAX}",
                style = MaterialTheme.typography.bodySmall,
                color = if (tooLong) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            Button(
                onClick = { viewModel.saveBio() },
                enabled = dirty && !tooLong && !state.isSaving,
            ) {
                Text(
                    if (state.isSaving) stringResource(R.string.people_profile_saving)
                    else stringResource(R.string.people_profile_save),
                )
            }
        }
    }
}

@Composable
private fun AccentSection(
    state: ProfileUiState,
    accentPreview: String?,
    viewModel: ProfileViewModel,
    onOpenPicker: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            stringResource(R.string.people_profile_accent),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Live swatch — empty / invalid hex falls back to the surface
            // variant so the slot stays visible.
            val previewColour = accentPreview?.let { parseHexColour(it) }
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(previewColour ?: MaterialTheme.colorScheme.surfaceVariant)
                    .border(
                        1.dp,
                        MaterialTheme.colorScheme.outlineVariant,
                        CircleShape,
                    ),
            )
            Text(
                text = state.accentDraft.ifBlank {
                    stringResource(R.string.people_profile_accent_unset)
                },
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            OutlinedButton(onClick = onOpenPicker) {
                Text(stringResource(R.string.people_profile_change))
            }
        }
        if (state.accentDraft.isNotBlank()) {
            TextButton(onClick = {
                viewModel.setAccentDraft("")
                viewModel.saveAccent()
            }) {
                Text(stringResource(R.string.people_profile_clear))
            }
        }
    }
}

@Composable
private fun FaviconSection(
    faviconUrl: String?,
    uploading: Boolean,
    onUpload: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            stringResource(R.string.people_profile_browser_icon),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Surface(
                modifier = Modifier
                    .size(40.dp)
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(4.dp)),
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(4.dp),
            ) {
                if (faviconUrl != null) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(faviconUrl)
                            .crossfade(true)
                            .build(),
                        contentDescription = stringResource(R.string.people_profile_favicon),
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                        Text(
                            text = "?",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            OutlinedButton(onClick = onUpload, enabled = !uploading) {
                Icon(Icons.Filled.CloudUpload, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(
                    if (uploading) stringResource(R.string.people_profile_uploading)
                    else stringResource(R.string.people_profile_upload),
                )
            }
        }
    }
}

@Composable
private fun PrivacySection(
    state: ProfileUiState,
    viewModel: ProfileViewModel,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            stringResource(R.string.people_profile_directory_listing),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.people_profile_privacy_public_label),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = stringResource(R.string.people_profile_privacy_public_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(
                checked = state.privacyDraft == "public",
                onCheckedChange = { checked ->
                    viewModel.savePrivacy(if (checked) "public" else "private")
                },
                enabled = !state.isSaving,
            )
        }
        if (state.privacyDraft == "private") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(selected = true, onClick = null)
                Spacer(Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.people_profile_privacy_private_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * Fullscreen preview of the user's draft profile. Mirrors the web "Profile
 * preview" dialog: banner + avatar overlay, name (using accent tint), short
 * fingerprint, bio rendered as markdown. Reads the *draft* form values so the
 * user can see unsaved edits as they'll appear to others.
 */
@Composable
private fun ProfilePreviewDialog(
    state: ProfileUiState,
    serverUrl: String,
    onDismiss: () -> Unit,
) {
    val info = state.info ?: return
    val accent = state.accentDraft.trim().takeIf { ProfileViewModel.ACCENT_PATTERN.matches(it) }
    val accentColour = accent?.let { parseHexColour(it) }
    val name = state.nameDraft.trim().ifBlank { info.name }
    val bio = state.bioDraft
    val avatarUrl = imageUrl(serverUrl, info.fingerprint, "avatar", info.avatar)
    val bannerUrl = imageUrl(serverUrl, info.fingerprint, "banner", info.banner)

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = false,
        ),
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState()),
                ) {
                    // ---- Banner + avatar overlay (matches PersonViewScreen) ----
                    Box(modifier = Modifier.fillMaxWidth()) {
                        if (bannerUrl != null) {
                            AsyncImage(
                                model = ImageRequest.Builder(LocalContext.current)
                                    .data(bannerUrl)
                                    .crossfade(true)
                                    .build(),
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(3f)
                                    .clip(RoundedCornerShape(bottomStart = 12.dp, bottomEnd = 12.dp)),
                            )
                        } else {
                            Surface(
                                color = accentColour?.copy(alpha = 0.12f)
                                    ?: MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(96.dp)
                                    .clip(RoundedCornerShape(bottomStart = 12.dp, bottomEnd = 12.dp)),
                            ) {}
                        }
                        EntityAvatar(
                            name = name,
                            src = avatarUrl,
                            seed = info.fingerprint,
                            size = 96.dp,
                            accent = accent,
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .offset(x = 16.dp, y = 48.dp),
                        )
                    }

                    Spacer(Modifier.height(56.dp))

                    // ---- Name + fingerprint ----
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                    ) {
                        Text(
                            text = name.ifBlank { "—" },
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = accentColour ?: MaterialTheme.colorScheme.onSurface,
                        )
                        if (info.fingerprint.isNotBlank()) {
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = info.fingerprint,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontFamily = FontFamily.Monospace,
                                ),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    Spacer(Modifier.height(16.dp))

                    // ---- Bio (markdown) ----
                    if (bio.isNotBlank()) {
                        HorizontalDivider()
                        Spacer(Modifier.height(16.dp))
                        Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                            HtmlContent(html = bio)
                        }
                        Spacer(Modifier.height(24.dp))
                    }
                }

                // ---- Close button (top-right X) ----
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = stringResource(R.string.people_common_close),
                    )
                }
            }
        }
    }
}

// ---- Helpers ----

private fun imageUrl(serverUrl: String, fingerprint: String, slot: String, version: String?): String? {
    if (serverUrl.isBlank() || fingerprint.isBlank()) return null
    if (version.isNullOrBlank()) return null
    return "$serverUrl/${fingerprint.replace("-", "")}/-/$slot?v=$version"
}

private fun parseHexColour(hex: String): Color? {
    val s = hex.trim().removePrefix("#")
    return try {
        when (s.length) {
            6 -> Color(
                red = s.substring(0, 2).toInt(16) / 255f,
                green = s.substring(2, 4).toInt(16) / 255f,
                blue = s.substring(4, 6).toInt(16) / 255f,
            )
            3 -> Color(
                red = (s[0].digitToInt(16) * 17) / 255f,
                green = (s[1].digitToInt(16) * 17) / 255f,
                blue = (s[2].digitToInt(16) * 17) / 255f,
            )
            else -> null
        }
    } catch (_: Exception) {
        null
    }
}
