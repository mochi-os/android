package org.mochios.people.ui.profile

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.OutlinedTextField
import androidx.compose.ui.text.input.ImeAction
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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
 *  - Accent: inline [AccentColorPicker] (presets, saturation/value field, hue
 *    slider, hex input) with Clear / Save.
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
            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
            Spacer(Modifier.width(ButtonDefaults.IconSpacing))
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
    var showSizeWarning by remember { mutableStateOf<String?>(null) }
    var showEditName by remember { mutableStateOf(false) }

    val accentPreview = state.accentDraft.trim().takeIf { ProfileViewModel.ACCENT_PATTERN.matches(it) }
    val avatarUrl = avatarPath(info.id, info.avatar)
    val bannerUrl = imageUrl(info.id, "banner", info.banner)
    val faviconUrl = imageUrl(info.id, "favicon", info.favicon)

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
            onEditName = { showEditName = true },
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            BioSection(state, info, viewModel)
            AccentSection(state, viewModel)
            FaviconSection(
                faviconUrl = faviconUrl,
                uploading = state.savingSlot == ImageSlot.FAVICON,
                onUpload = faviconPicker.launch,
            )
            PrivacySection(state, viewModel)
        }
    }

    if (showEditName) {
        EditNameDialog(
            initialName = state.nameDraft,
            isSaving = state.savingField == ProfileField.NAME,
            onDismiss = { showEditName = false },
            onSave = { newName ->
                viewModel.setNameDraft(newName)
                viewModel.saveName(
                    onSuccess = {
                        showEditName = false
                        scope.launch {
                            snackbar.showSnackbar(context.getString(R.string.people_profile_name_saved))
                        }
                    },
                    // Failures surface through state.error → scaffold snackbar.
                    onError = {},
                )
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
    onEditName: () -> Unit,
) {
    val avatarSize = 96.dp
    Box(modifier = Modifier.fillMaxWidth()) {
        // Banner — 3:1 aspect, falls back to a muted placeholder with a
        // camera icon when no banner is set. Tapping the banner picks a new
        // image (disabled while an upload is in flight).
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(3f)
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .clickable(enabled = !uploadingBanner, onClick = onChangeBanner),
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
            if (uploadingBanner) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(Color.Black.copy(alpha = 0.35f)),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(color = Color.White)
                }
            } else {
                // Text hint — signals the banner is tappable to change.
                Text(
                    text = stringResource(R.string.people_profile_change_banner),
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(Color.Black.copy(alpha = 0.45f))
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                )
            }
        }

        // Avatar pinned over the banner's bottom-left edge. Tapping it picks a
        // new avatar (disabled while an upload is in flight).
        Box(
            modifier = Modifier
                .padding(start = 16.dp)
                .offset(y = avatarSize / 2)
                .align(Alignment.BottomStart),
        ) {
            // A surface separator ring always sits between the avatar and the
            // banner. When the user sets a parseable accent, its ring is drawn
            // in that separator band (overlapping the surface padding only), so
            // the image keeps the same size whether or not an accent is set.
            val ringColour = accent?.let { parseHexColour(it) }
            val separator = 5.dp
            val accentWidth = 2.5.dp
            val imageSize = avatarSize - separator * 2
            Box(
                modifier = Modifier
                    .size(avatarSize)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surface)
                    .clickable(enabled = !uploadingAvatar, onClick = onChangeAvatar),
                contentAlignment = Alignment.Center,
            ) {
                // Accent ring — overlaps the separator band, hugging the image.
                if (ringColour != null) {
                    Box(
                        modifier = Modifier
                            .size(imageSize + accentWidth * 2)
                            .border(accentWidth, ringColour, CircleShape),
                    )
                }
                Box(
                    modifier = Modifier
                        .size(imageSize)
                        .clip(CircleShape),
                ) {
                    EntityAvatar(
                        name = name,
                        src = avatarUrl,
                        seed = fingerprint,
                        size = imageSize,
                        borderColor = Color.Transparent,
                    )
                    if (uploadingAvatar) {
                        Box(
                            modifier = Modifier
                                .matchParentSize()
                                .background(Color.Black.copy(alpha = 0.35f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                strokeWidth = 2.dp,
                                color = Color.White,
                            )
                        }
                    }
                }
            }
            if (!uploadingAvatar) {
                // Camera hint badge — signals the avatar is tappable to change.
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surface)
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Filled.PhotoCamera,
                        contentDescription = stringResource(R.string.people_profile_upload_avatar),
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        }

        // Name + edit pencil, aligned to the avatar's bottom and sitting to
        // its right. The pencil is a compact clickable icon (not an
        // IconButton) so the row collapses to the text height — that lets the
        // avatarSize/2 offset land the name's bottom exactly on the avatar's.
        Row(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .offset(y = avatarSize / 2)
                .fillMaxWidth()
                .padding(start = 16.dp + avatarSize + 12.dp, end = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = name.ifBlank { "—" },
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            Icon(
                imageVector = Icons.Outlined.Edit,
                contentDescription = stringResource(R.string.people_profile_edit_name),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable(onClick = onEditName)
                    .padding(6.dp)
                    .size(20.dp),
            )
        }
    }

    // Clear the half-overhanging avatar / name row before the form begins.
    Spacer(Modifier.height(avatarSize / 2 + 8.dp))
}

/**
 * "Edit name" dialog — holds the name field locally so Cancel discards the
 * edit and only Save commits it. Mirrors the old inline name section, moved
 * behind the pencil affordance on the hero card.
 */
@Composable
private fun EditNameDialog(
    initialName: String,
    isSaving: Boolean,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(initialName) }
    val trimmed = name.trim()
    val valid = trimmed.isNotEmpty()

    AlertDialog(
        onDismissRequest = { if (!isSaving) onDismiss() },
        title = { Text(stringResource(R.string.people_profile_edit_name)) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { value -> name = value },
                singleLine = true,
                label = { Text(stringResource(R.string.people_profile_name)) },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(
                    onDone = { if (valid && !isSaving) onSave(trimmed) },
                ),
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(onClick = { onSave(trimmed) }, enabled = valid && !isSaving) {
                if (isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(ButtonDefaults.IconSize),
                        strokeWidth = 2.dp,
                    )
                } else {
                    Text(stringResource(R.string.people_profile_save))
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isSaving) {
                Text(stringResource(R.string.people_common_cancel))
            }
        },
    )
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
            stringResource(R.string.people_profile_title),
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
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
            ),
            modifier = Modifier.fillMaxWidth(),
        )
        // Bottom row: inline progress track (left, flexible) + character count
        // + Save button, matching the web profile editor layout.
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            LinearProgressIndicator(
                progress = { progress },
                color = if (tooLong) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.primary,
                drawStopIndicator = {},
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "%,d / %,d".format(length, ProfileViewModel.BIO_MAX),
                style = MaterialTheme.typography.bodySmall,
                color = if (tooLong) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            val savingBio = state.savingField == ProfileField.BIO
            Button(
                onClick = { viewModel.saveBio() },
                enabled = dirty && !tooLong && !savingBio,
            ) {
                if (savingBio) {
                    CircularProgressIndicator(modifier = Modifier.size(ButtonDefaults.IconSize), strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Filled.Save, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                }
                Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                Text(
                    if (savingBio) stringResource(R.string.people_profile_saving)
                    else stringResource(R.string.people_profile_save),
                )
            }
        }
    }
}

@Composable
private fun AccentSection(
    state: ProfileUiState,
    viewModel: ProfileViewModel,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            stringResource(R.string.people_profile_accent),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        AccentColorPicker(
            hex = state.accentDraft,
            isSaving = state.savingField == ProfileField.ACCENT,
            onHexChange = { hex -> viewModel.setAccentDraft(hex) },
            onClear = { viewModel.clearAccent() },
            onSave = { viewModel.saveAccent() },
        )
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
            OutlinedButton(
                onClick = onUpload,
                enabled = !uploading,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            ) {
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
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.people_profile_directory_listing),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            Switch(
                checked = state.privacyDraft == "public",
                onCheckedChange = { checked ->
                    viewModel.savePrivacy(if (checked) "public" else "private")
                },
                enabled = state.savingField != ProfileField.PRIVACY,
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
    onDismiss: () -> Unit,
) {
    val info = state.info ?: return
    val accent = state.accentDraft.trim().takeIf { ProfileViewModel.ACCENT_PATTERN.matches(it) }
    val accentColour = accent?.let { parseHexColour(it) }
    val name = state.nameDraft.trim().ifBlank { info.name }
    val bio = state.bioDraft
    val avatarUrl = avatarPath(info.id, info.avatar)
    val bannerUrl = imageUrl(info.id, "banner", info.banner)

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

                    // ---- Name ----
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
                    }

                    Spacer(Modifier.height(16.dp))

                    // ---- Bio (markdown) ----
                    if (bio.isNotBlank()) {
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

private fun imageUrl(id: String, slot: String, version: String?): String? {
    if (id.isBlank() || version.isNullOrBlank()) return null
    return "/people/$id/-/$slot?v=$version"
}

/**
 * Server-relative avatar path for [EntityAvatar], which resolves the host
 * itself. Banner/favicon still use [imageUrl] since they feed `AsyncImage`,
 * which needs an absolute URL.
 */
private fun avatarPath(id: String, version: String?): String? {
    if (id.isBlank() || version.isNullOrBlank()) return null
    return "/people/$id/-/avatar?v=$version"
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
