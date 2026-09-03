// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.words.ui.detail.board

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.mochios.android.ui.components.MochiButton
import org.mochios.android.ui.components.MochiOutlinedButton
import org.mochios.words.engine.DraftStatus
import org.mochios.words.R
import org.mochios.words.engine.MoveDraft
import org.mochios.words.engine.MoveError
import org.mochios.words.ui.detail.ValidState

/**
 * What the pending tiles add up to, shown between the board and the rack: the
 * words they spell with their scores, or why the move will not play.
 *
 * @param pendingPlacements how many tiles are on the board but not yet played.
 * @param exchangeMode whether the rack is picking tiles to exchange.
 * @param moveDraft the engine's reading of the placements.
 * @param draftWords each word the placements form, with its score.
 * @param wordValidationState per-word dictionary result, keyed by upper-case word.
 * @param validationUnavailable whether the dictionary could not be reached.
 */
@Composable
fun MoveFeedback(
    pendingPlacements: Int,
    exchangeMode: Boolean,
    moveDraft: MoveDraft?,
    draftWords: List<Pair<String, Int>>,
    wordValidationState: Map<String, ValidState>,
    validationUnavailable: Boolean = false,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 36.dp)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (!exchangeMode && pendingPlacements > 0) {
            DraftFeedback(
                moveDraft = moveDraft,
                draftWords = draftWords,
                wordValidationState = wordValidationState,
                validationUnavailable = validationUnavailable,
            )
        }
    }
}

/**
 * The centred pair of buttons under the rack: recall and submit while a move is
 * being composed, cancel and exchange while picking tiles to swap. Empty
 * otherwise, keeping its height so the board does not resize mid-move.
 *
 * @param pendingPlacements how many tiles are on the board but not yet played.
 * @param exchangeMode whether the rack is picking tiles to exchange.
 * @param exchangeSelected how many rack tiles are queued for exchange.
 * @param draftScore what the pending move would score.
 * @param onRecall takes every pending tile back to the rack.
 * @param onSubmit plays the pending move.
 * @param onExchangeConfirm swaps the selected tiles.
 * @param onExchangeCancel leaves exchange mode.
 * @param canSubmit whether the move is playable and checked.
 * @param canRecallMove whether there is anything to take back.
 * @param isSubmitting whether a move is in flight.
 * @param isExchanging whether an exchange is in flight.
 */
@Composable
fun MoveActions(
    pendingPlacements: Int,
    exchangeMode: Boolean,
    exchangeSelected: Int,
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
            .heightIn(min = 56.dp)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
    ) {
        if (exchangeMode) {
            ExchangeButtons(
                exchangeSelected = exchangeSelected,
                isExchanging = isExchanging,
                onExchangeCancel = onExchangeCancel,
                onExchangeConfirm = onExchangeConfirm,
            )
        } else if (pendingPlacements > 0) {
            MoveButtons(
                draftScore = draftScore,
                canRecallMove = canRecallMove,
                canSubmit = canSubmit,
                isSubmitting = isSubmitting,
                onRecall = onRecall,
                onSubmit = onSubmit,
            )
        }
    }
}

/**
 * A line of guidance above the rack: why the move will not play, or what the
 * checker could not reach.
 */
@Composable
private fun ComposerMessage(text: String, isError: Boolean = false) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = if (isError) {
            MaterialTheme.colorScheme.error
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
    )
}

/** The words the pending tiles spell, and anything wrong with them. */
@Composable
private fun ColumnScope.DraftFeedback(
    moveDraft: MoveDraft?,
    draftWords: List<Pair<String, Int>>,
    wordValidationState: Map<String, ValidState>,
    validationUnavailable: Boolean,
) {
    val invalidLocal = moveDraft != null && moveDraft.status == DraftStatus.INVALID_LOCAL
    // The engine returns a reason, not prose — it has no string resources, and
    // returning English here put untranslated text in front of every locale.
    val invalidMsg = moveDraft?.error?.let { error -> stringResource(moveErrorLabel(error)) }

    if (invalidLocal && !invalidMsg.isNullOrBlank()) {
        ComposerMessage(text = invalidMsg, isError = true)
        return
    }

    if (draftWords.isNotEmpty()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        ) {
            for ((word, score) in draftWords) {
                WordChip(
                    word = word,
                    score = score,
                    state = wordValidationState[word.uppercase()] ?: ValidState.UNKNOWN,
                )
            }
        }
    }

    // Web shows both of these; Android tracked the state and rendered neither,
    // so a player had no way to tell an unchecked word from a checked one or to
    // know validation was offline.
    val notice = when {
        validationUnavailable -> stringResource(R.string.words_validation_offline)
        draftWords.any { (word, _) ->
            wordValidationState[word.uppercase()] == ValidState.INVALID
        } -> stringResource(R.string.words_validation_unknown_words)
        else -> null
    }
    if (notice != null) {
        ComposerMessage(text = notice)
    }
}

@Composable
private fun RowScope.ExchangeButtons(
    exchangeSelected: Int,
    isExchanging: Boolean,
    onExchangeCancel: () -> Unit,
    onExchangeConfirm: () -> Unit,
) {
    MochiOutlinedButton(
        onClick = onExchangeCancel,
        enabled = !isExchanging,
    ) {
        Text(stringResource(R.string.words_detail_cancel))
    }
    MochiButton(
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
private fun RowScope.MoveButtons(
    draftScore: Int,
    canRecallMove: Boolean,
    canSubmit: Boolean,
    isSubmitting: Boolean,
    onRecall: () -> Unit,
    onSubmit: () -> Unit,
) {
    MochiOutlinedButton(
        onClick = onRecall,
        enabled = canRecallMove,
    ) {
        Text(stringResource(R.string.words_detail_recall))
    }
    MochiButton(
        onClick = onSubmit,
        enabled = canSubmit,
    ) {
        if (isSubmitting) {
            CircularProgressIndicator(
                modifier = Modifier.size(14.dp),
                strokeWidth = 1.5.dp,
                color = MaterialTheme.colorScheme.onPrimary,
            )
            Spacer(modifier = Modifier.size(6.dp))
        }
        // The score rides on the button so the reward sits on the action, not
        // adrift at the end of a scrolling row where it was cut off.
        val label = if (draftScore > 0) {
            "${stringResource(R.string.words_detail_submit)} +$draftScore"
        } else {
            stringResource(R.string.words_detail_submit)
        }
        Text(label)
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
                modifier = Modifier.size(16.dp),
            )
            ValidState.INVALID -> Icon(
                imageVector = Icons.Filled.Cancel,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(16.dp),
            )
            ValidState.CHECKING -> CircularProgressIndicator(
                modifier = Modifier.size(10.dp),
                strokeWidth = 1.dp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            ValidState.UNKNOWN -> Spacer(modifier = Modifier.size(16.dp))
        }
        Text(
            text = word.uppercase(),
            style = MaterialTheme.typography.labelLarge.copy(
                fontWeight = FontWeight.SemiBold,
            ),
            color = if (state == ValidState.INVALID) MaterialTheme.colorScheme.error
            else MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = "+$score",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** String resource for a [MoveError]; see WordsEngine's MoveError. */
@androidx.annotation.StringRes
private fun moveErrorLabel(error: MoveError): Int = when (error) {
    MoveError.NO_TILES_PLACED -> R.string.words_move_error_no_tiles
    MoveError.OUT_OF_BOUNDS -> R.string.words_move_error_out_of_bounds
    MoveError.SQUARE_OCCUPIED -> R.string.words_move_error_square_occupied
    MoveError.NOT_IN_LINE -> R.string.words_move_error_not_in_line
    MoveError.NOT_CONTIGUOUS -> R.string.words_move_error_not_contiguous
    MoveError.FIRST_MOVE_MUST_COVER_CENTRE -> R.string.words_move_error_first_move_centre
    MoveError.FIRST_MOVE_NEEDS_TWO_TILES -> R.string.words_move_error_first_move_two_tiles
    MoveError.NOT_CONNECTED -> R.string.words_move_error_not_connected
    MoveError.NO_VALID_WORDS -> R.string.words_move_error_no_words
}
