// Copyright © 2026 Mochi OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.market.ui.buying

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.mochios.market.R
import org.mochios.market.model.Review

/**
 * Inline "leave a review" form for the purchase-detail screen.
 *
 * Star selector is tap-to-select (1..5). The submit button calls
 * [onSubmit] with the chosen rating and the (possibly empty) review
 * body; the parent VM is responsible for calling
 * [org.mochios.market.repository.MarketRepository.createReview] and
 * updating local state.
 *
 * Pass an existing [submitted] review to render the "your review"
 * confirmation state (stars only, body, optional seller response). This
 * keeps the parent layout simple — the same composable handles both the
 * before- and after-submit states.
 */
@Composable
fun WriteReviewForm(
    submitting: Boolean,
    submitted: Review? = null,
    onSubmit: (rating: Int, body: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (submitted != null) {
        SubmittedReviewCard(submitted, modifier)
        return
    }

    var rating by remember { mutableIntStateOf(5) }
    var body by remember { mutableStateOf("") }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.outlinedCardColors(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                stringResource(R.string.market_purchase_review_section),
                style = MaterialTheme.typography.titleSmall,
            )
            Text(
                stringResource(R.string.market_purchase_review_rating),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            StarSelector(rating = rating, onRating = { rating = it })
            OutlinedTextField(
                value = body,
                onValueChange = { body = it },
                label = { Text(stringResource(R.string.market_purchase_review_body)) },
                minLines = 3,
                modifier = Modifier.fillMaxWidth(),
            )
            Button(
                onClick = { onSubmit(rating, body) },
                enabled = !submitting && rating in 1..5,
            ) {
                Icon(
                    Icons.Default.Send,
                    contentDescription = null,
                    modifier = Modifier.size(ButtonDefaults.IconSize),
                )
                Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                Text(stringResource(R.string.market_purchase_review_submit))
            }
        }
    }
}

@Composable
private fun StarSelector(rating: Int, onRating: (Int) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        for (i in 1..5) {
            val isFilled = i <= rating
            Icon(
                imageVector = if (isFilled) Icons.Filled.Star else Icons.Outlined.StarOutline,
                contentDescription = null,
                tint = if (isFilled) MaterialTheme.colorScheme.tertiary
                else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .size(32.dp)
                    .clickable { onRating(i) },
            )
        }
    }
}

@Composable
private fun SubmittedReviewCard(review: Review, modifier: Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.outlinedCardColors(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.market_purchase_review_your_review),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f),
                )
                Row {
                    for (i in 1..5) {
                        val isFilled = i <= review.rating
                        Icon(
                            imageVector = if (isFilled) Icons.Filled.Star else Icons.Outlined.StarOutline,
                            contentDescription = null,
                            tint = if (isFilled) MaterialTheme.colorScheme.tertiary
                            else Color.Unspecified,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
            }
            if (review.text.isNotBlank()) {
                Text(review.text, style = MaterialTheme.typography.bodyMedium)
            }
            if (review.visible == 0) {
                Text(
                    stringResource(R.string.market_purchase_review_hidden),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
