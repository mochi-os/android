package org.mochios.market.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.mochios.market.R
import org.mochios.market.lib.formatPrice
import org.mochios.market.model.Currency
import org.mochios.market.model.ShippingOption

/**
 * Per-region shipping options for a listing, one outlined card each.
 *
 * Each card pairs the region (left) with its delivery estimate and price
 * (right); any free-text notes wrap onto a second line. Mirrors the web detail
 * page's card list rather than a dense table so the options stay readable on a
 * phone-width column.
 *
 * `option.currency` is a raw string on the wire (the comptroller doesn't
 * constrain it to the [Currency] enum here), so we tolerate unknown codes by
 * falling back to GBP for formatting.
 */
@Composable
fun ShippingOptionsTable(
    options: List<ShippingOption>,
    modifier: Modifier = Modifier,
) {
    if (options.isEmpty()) return
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        options.forEach { option ->
            ShippingOptionCard(option = option)
        }
    }
}

@Composable
private fun ShippingOptionCard(option: ShippingOption) {
    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = option.region,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (option.days.isNotBlank()) {
                    Text(
                        text = stringResource(R.string.market_shipping_days_value, option.days),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    text = formatPriceSafe(option.price, option.currency),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            if (option.notes.isNotBlank()) {
                Text(
                    text = option.notes,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private fun formatPriceSafe(amount: Long, currency: String): String {
    val curr = runCatching { Currency.valueOf(currency.uppercase()) }
        .getOrDefault(Currency.GBP)
    return formatPrice(amount, curr)
}
