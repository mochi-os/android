// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.staff.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.mochios.android.api.MochiError
import org.mochios.android.api.toMochiError
import org.mochios.android.i18n.LocalFormat
import org.mochios.android.i18n.formatTimestamp
import org.mochios.android.ui.components.InlineErrorState
import org.mochios.android.ui.components.LoadState
import org.mochios.android.ui.components.MochiTextButton
import org.mochios.android.util.Paging
import org.mochios.android.util.append
import org.mochios.staff.R
import org.mochios.staff.model.AuditEntry
import org.mochios.staff.repository.StaffRepository
import javax.inject.Inject

/**
 * Per-object audit history, mirroring
 * `apps/staff/web/src/components/shared/audit-timeline.tsx`. `objectId` is the
 * row's id as the Comptroller keys it (an account's is its entity id). Owns
 * its own ViewModel, so it can be embedded in any dialog; the history arrives
 * a page at a time, the next on Load more.
 */
@Composable
fun StaffAuditTimeline(
    kind: String,
    objectId: String,
    modifier: Modifier = Modifier,
) {
    // Key on (kind, objectId) so each call site gets its own ViewModel
    // instance — otherwise nested dialogs hosting two different timelines
    // would share state via the parent NavBackStackEntry-scoped Hilt VM.
    key(kind, objectId) {
        val viewModel: AuditTimelineHostViewModel = hiltViewModel(key = "audit:$kind:$objectId")
        LaunchedEffect(kind, objectId) {
            viewModel.load(kind, objectId)
        }
        val state by viewModel.state.collectAsState()
        AuditTimelineBody(state = state, onMore = viewModel::loadMore, modifier = modifier)
    }
}

@Composable
internal fun AuditTimelineBody(
    state: AuditTimelineUiState,
    onMore: () -> Unit,
    modifier: Modifier = Modifier,
) {
    when {
        state.isLoading -> Box(
            modifier = modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
        }

        state.error != null -> InlineErrorState(
            error = state.error,
            modifier = modifier,
        )

        state.entries.isEmpty() -> Text(
            text = stringResource(R.string.staff_audit_empty),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = modifier.padding(vertical = 8.dp),
        )

        else -> Column(modifier = modifier) {
            Text(
                text = stringResource(R.string.staff_audit_title),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(bottom = 8.dp),
            )
            val more = state.paging.more
            state.entries.forEachIndexed { index, entry ->
                AuditRow(entry = entry, isLast = index == state.entries.lastIndex && !more)
            }
            if (more) {
                when (val load = state.load) {
                    LoadState.Loading -> Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    }
                    is LoadState.Failed -> InlineErrorState(error = load.error, onRetry = onMore)
                    LoadState.Idle -> MochiTextButton(onClick = onMore) {
                        Text(stringResource(R.string.staff_audit_load_more))
                    }
                }
            }
        }
    }
}

@Composable
private fun AuditRow(entry: AuditEntry, isLast: Boolean) {
    val format = LocalFormat.current
    val label = auditActionLabel(entry.action)
    val detail = auditDetail(entry)
    val actor = auditActor(entry)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = if (isLast) 0.dp else 10.dp),
    ) {
        // Dot + connector line gutter.
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.width(20.dp),
        ) {
            Box(
                modifier = Modifier
                    .padding(top = 4.dp)
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
            )
            if (!isLast) {
                Spacer(modifier = Modifier.height(2.dp))
                Box(
                    modifier = Modifier
                        .width(2.dp)
                        .height(28.dp)
                        .background(MaterialTheme.colorScheme.outlineVariant),
                )
            }
        }
        Spacer(modifier = Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                )
                if (detail.isNotBlank()) {
                    Text(
                        text = ": $detail",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Text(
                text = "${format.formatTimestamp(entry.timestamp)} · $actor",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// ---- Internal state + ViewModel for the embedded timeline ----

internal data class AuditTimelineUiState(
    val isLoading: Boolean = true,
    val entries: List<AuditEntry> = emptyList(),
    val error: MochiError? = null,
    /** How far the history has been read. */
    val paging: Paging = Paging(),
    /** The next page, for the timeline's foot. */
    val load: LoadState = LoadState.Idle,
)

private const val PAGE_SIZE = 50

@HiltViewModel
internal class AuditTimelineHostViewModel @Inject constructor(
    private val repository: StaffRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(AuditTimelineUiState())
    val state: StateFlow<AuditTimelineUiState> = _state.asStateFlow()

    private var lastKey: Pair<String, String>? = null
    private var job: Job? = null

    fun load(kind: String, objectId: String) {
        val key = kind to objectId
        if (lastKey == key) return
        lastKey = key
        job?.cancel()
        _state.value = AuditTimelineUiState(isLoading = true)
        job = viewModelScope.launch {
            try {
                val r = repository.getObjectAudit(kind, objectId, limit = PAGE_SIZE)
                _state.value = AuditTimelineUiState(
                    isLoading = false,
                    entries = r.audit,
                    // The history never paged by number: a server that sends
                    // no cursor has no next page to offer.
                    paging = Paging().advance(r.audit.size, r.total, r.next ?: ""),
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.value = AuditTimelineUiState(
                    isLoading = false,
                    error = e.toMochiError(),
                )
            }
        }
    }

    /** Reads the next page of the history, from Load more or its retry. */
    fun loadMore() {
        val (kind, objectId) = lastKey ?: return
        val s = _state.value
        if (s.isLoading || s.load == LoadState.Loading || !s.paging.more) return
        job = viewModelScope.launch {
            _state.value = s.copy(load = LoadState.Loading)
            try {
                val r = repository.getObjectAudit(kind, objectId, limit = PAGE_SIZE, after = s.paging.cursor)
                _state.value = _state.value.copy(
                    load = LoadState.Idle,
                    entries = _state.value.entries.append(r.audit) { entry -> entry.id },
                    paging = s.paging.advance(r.audit.size, r.total, r.next ?: ""),
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.value = _state.value.copy(load = LoadState.Failed(e.toMochiError()))
            }
        }
    }
}
