// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.words.ui.detail.board

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.mochios.words.engine.DraftStatus
import org.mochios.words.R
import org.mochios.words.engine.MoveDraft
import org.mochios.words.ui.detail.ValidState

/**
 * Action bar above the rack. Three height-states:
 *  1. Exchange mode active → `Cancel` + spacer + `Exchange (N)` button.
 *  2. Pending placements + not in exchange mode → `Recall` + word-chips +
 *     total score + `Submit` button.
 *  3. Otherwise (rest state) → 32dp blank row keeping the layout stable.
 *
 * Word chips render with a status icon (CheckCircle for VALID, Cancel for
 * INVALID, spinner for CHECKING) + uppercase word + `+score` muted. Chips
 * scroll horizontally so a wide play doesn't push the Submit button off
 * the right edge.
 */
@Composable
fun MoveComposer(
    pendingPlacements: Int,
    exchangeMode: Boolean,
    exchangeSelected: Int,
    moveDraft: MoveDraft?,
    draftWords: List<Pair<String, Int>>,
    wordValidationState: Map<String, ValidState>,
    draftScore: Int,
    onRecall: () -> Unit,
    onSubmit: () -> Unit,
    onExchangeConfirm: () -> Unit,
    onExchangeCancel: () -> Unit,
    canSubmit: Boolean,
    canRecallMove: Boolean,
    isSubmitting: Boolean,
    isExchanging: Boolean,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(36.dp)
            .padding(horizontal = 4.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        when {
            exchangeMode -> ExchangeRow(
                exchangeSelected = exchangeSelected,
                isExchanging = isExchanging,
                onExchangeCancel = onExchangeCancel,
                onExchangeConfirm = onExchangeConfirm,
            )
            pendingPlacements > 0 -> ComposerRow(
                moveDraft = moveDraft,
                draftWords = draftWords,
                wordValidationState = wordValidationState,
                draftScore = draftScore,
                canRecallMove = canRecallMove,
                canSubmit = canSubmit,
                isSubmitting = isSubmitting,
                onRecall = onRecall,
                onSubmit = onSubmit,
            )
            else -> {
                // Empty rest state — Spacer keeps the row's height stable.
                Spacer(modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun RowScope.ExchangeRow(
    exchangeSelected: Int,
    isExchanging: Boolean,
    onExchangeCancel: () -> Unit,
    onExchangeConfirm: () -> Unit,
) {
    OutlinedButton(
        onClick = onExchangeCancel,
        enabled = !isExchanging,
    ) {
        Text(stringResource(R.string.words_detail_cancel))
    }
    Spacer(modifier = Modifier.weight(1f, fill = true))
    Button(
        onClick = onExchangeConfirm,
        enabled = exchangeSelected > 0 && !isExchanging,
    ) {
        if (isExchanging) {
            CircularProgressIndicator(
                modifier = Modifier.size(14.dp),
                strokeWidth = 1.5.dp,
                color = MaterialTheme.colorScheme.onPrimary,
            )
            Spacer(modifier = Modifier.size(6.dp))
        }
        val label = if (exchangeSelected > 0) {
            stringResource(R.string.words_detail_exchange_count, exchangeSelected)
        } else {
            stringResource(R.string.words_detail_exchange)
        }
        Text(label)
    }
}

@Composable
private fun RowScope.ComposerRow(
    moveDraft: MoveDraft?,
    draftWords: List<Pair<String, Int>>,
    wordValidationState: Map<String, ValidState>,
    draftScore: Int,
    canRecallMove: Boolean,
    canSubmit: Boolean,
    isSubmitting: Boolean,
    onRecall: () -> Unit,
    onSubmit: () -> Unit,
) {
    OutlinedButton(
        onClick = onRecall,
        enabled = canRecallMove,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 4.dp),
    ) {
        Text(stringResource(R.string.words_detail_recall))
    }

    val invalidLocal = moveDraft != null &&
        moveDraft.status == DraftStatus.INVALID_LOCAL
    val invalidMsg = moveDraft?.errorMessage

    Row(
        modifier = Modifier
            .weight(1f, fill = true)
            .horizontalScroll(rememberScrollState()),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        when {
            invalidLocal && !invalidMsg.isNullOrBlank() -> {
                Text(
                    text = invalidMsg,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            draftWords.isNotEmpty() -> {
                for ((word, score) in draftWords) {
                    WordChip(
                        word = word,
                        score = score,
                        state = wordValidationState[word.uppercase()] ?: ValidState.UNKNOWN,
                    )
                }
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = draftScore.toString(),
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.Bold,
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }

    Button(
        onClick = onSubmit,
        enabled = canSubmit,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 4.dp),
    ) {
        if (isSubmitting) {
            CircularProgressIndicator(
                modifier = Modifier.size(14.dp),
                strokeWidth = 1.5.dp,
                color = MaterialTheme.colorScheme.onPrimary,
            )
            Spacer(modifier = Modifier.size(6.dp))
        }
        Text(stringResource(R.string.words_detail_submit))
    }
}

@Composable
private fun WordChip(word: String, score: Int, state: ValidState) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        when (state) {
            ValidState.VALID -> Icon(
                imageVector = Icons.Filled.CheckCircle,
                contentDescription = null,
                tint = Color(0xFF10B981),
                modifier = Modifier.size(12.dp),
            )
            ValidState.INVALID -> Icon(
                imageVector = Icons.Filled.Cancel,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(12.dp),
            )
            ValidState.CHECKING -> CircularProgressIndicator(
                modifier = Modifier.size(10.dp),
                strokeWidth = 1.dp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            ValidState.UNKNOWN -> Spacer(modifier = Modifier.size(12.dp))
        }
        Text(
            text = word.uppercase(),
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.SemiBold,
                fontSize = 11.sp,
            ),
            color = if (state == ValidState.INVALID) MaterialTheme.colorScheme.error
            else MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = "+$score",
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
