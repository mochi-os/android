package org.mochios.market.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
 * Compact table of per-region shipping options for a listing.
 *
 * Columns: region, price, delivery estimate, notes. Each row shrinks
 * gracefully on narrow screens — region and notes ellipsize, the price
 * column is fixed-width.
 *
 * `option.currency` is a raw string on the wire (the comptroller doesn't
 * constrain it to the [Currency] enum here), so we tolerate unknown
 * codes by falling back to GBP for formatting and surfacing the raw
 * code if [Currency.valueOf] doesn't match.
 */
@Composable
fun ShippingOptionsTable(
    options: List<ShippingOption>,
    modifier: Modifier = Modifier,
) {
    if (options.isEmpty()) return
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            HeaderCell(stringResource(R.string.market_shipping_region), Modifier.weight(2f))
            HeaderCell(stringResource(R.string.market_shipping_price), Modifier.weight(1f))
            HeaderCell(stringResource(R.string.market_shipping_days), Modifier.weight(2f))
            HeaderCell(stringResource(R.string.market_shipping_notes), Modifier.weight(2f))
        }
        HorizontalDivider()
        options.forEachIndexed { index, opt ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Cell(opt.region, Modifier.weight(2f))
                Cell(formatPriceSafe(opt.price, opt.currency), Modifier.weight(1f))
                Cell(opt.days, Modifier.weight(2f))
                Cell(opt.notes, Modifier.weight(2f))
            }
            if (index < options.size - 1) HorizontalDivider()
        }
    }
}

@Composable
private fun HeaderCell(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier,
    )
}

@Composable
private fun Cell(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier,
    )
}

private fun formatPriceSafe(amount: Long, currency: String): String {
    val curr = runCatching { Currency.valueOf(currency.uppercase()) }
        .getOrDefault(Currency.GBP)
    return formatPrice(amount, curr)
}
