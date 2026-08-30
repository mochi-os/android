// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties

/** Narrowest a fly-out may be, so a two-word mode still reads as a panel. */
private val SubmenuMinWidth = 180.dp

/** Widest, matching `DropdownMenuItemDefaultMaxWidth`, so a long label wraps instead of spanning the screen. */
private val SubmenuMaxWidth = 280.dp

/** Breathing room above and below a fly-out's rows, matching a menu's own. */
private val SubmenuVerticalPadding = 8.dp

/** Gap kept between a fly-out and the window edge when it has to be nudged inward. */
private val WindowMargin = 8.dp

/**
 * A [MochiDropdownMenuItem] that opens its own panel beside itself, for the
 * handful of menus with one row's worth of modes hanging off them.
 *
 * Material3 1.4 has no nested menu, so this is the fly-out built from the
 * pieces it does expose: a plain [Popup], positioned by [SubmenuPositionProvider]
 * against the row's own bounds. The alternative - expanding the modes inline -
 * pushes every row below them down, which is what this replaces.
 *
 * The panel is deliberately **not** focusable. A focusable child takes focus
 * from the menu hosting it, and that menu then dismisses itself out from under
 * the fly-out. Dismissal is therefore the host's to own: collapse [expanded]
 * when the menu closes, so the fly-out is down again the next time it opens.
 */
@Composable
fun MochiDropdownSubmenu(
    text: @Composable () -> Unit,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    // The Box is what the panel anchors to. Without it the Popup would measure
    // against the whole menu and open level with its top, not with this row.
    Box {
        // Open reads as an accent rather than a check: the row is a way in, not
        // a choice already made.
        val accent = MaterialTheme.colorScheme.primary
        MochiDropdownMenuItem(
            text = text,
            onClick = { onExpandedChange(!expanded) },
            modifier = modifier,
            leadingIcon = leadingIcon,
            trailingIcon = {
                Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null)
            },
            colors = if (expanded) {
                MenuDefaults.itemColors(
                    textColor = accent,
                    leadingIconColor = accent,
                    trailingIconColor = accent
                )
            } else {
                null
            }
        )

        if (expanded) {
            val density = LocalDensity.current
            Popup(
                popupPositionProvider = remember(density) { SubmenuPositionProvider(density) },
                onDismissRequest = { onExpandedChange(false) },
                properties = PopupProperties(focusable = false)
            ) {
                Surface(
                    modifier = Modifier.widthIn(min = SubmenuMinWidth, max = SubmenuMaxWidth),
                    shape = mochiPopupShape(),
                    color = mochiPopupContainerColor(),
                    shadowElevation = MochiPopupElevation
                ) {
                    // A menu row fills the width it is given, and a Popup gives
                    // it the whole window - so the panel has to size itself to
                    // its widest row first, the way DropdownMenuContent does.
                    Column(
                        modifier = Modifier
                            .width(IntrinsicSize.Max)
                            .padding(vertical = SubmenuVerticalPadding),
                        content = content
                    )
                }
            }
        }
    }
}

/**
 * Places a fly-out at its row's end edge, level with the row's top.
 *
 * Material3 1.4 keeps its own provider internal, but the interface it
 * implements is public and hands over [anchorBounds] - which is all the end
 * edge is. Where the panel would leave the window it flips to the row's start
 * edge instead, so a menu opened near the end of the screen folds inward
 * rather than off it. Written against the layout direction rather than left
 * and right, so RTL flips with it.
 */
private class SubmenuPositionProvider(private val density: Density) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize
    ): IntOffset {
        val margin = with(density) { WindowMargin.roundToPx() }
        val ltr = layoutDirection == LayoutDirection.Ltr

        val beside = if (ltr) anchorBounds.right else anchorBounds.left - popupContentSize.width
        val folded = if (ltr) anchorBounds.left - popupContentSize.width else anchorBounds.right
        val fits = beside >= 0 && beside + popupContentSize.width <= windowSize.width
        val x = (if (fits) beside else folded)
            .coerceIn(0, (windowSize.width - popupContentSize.width).coerceAtLeast(0))

        // Level with the row, then lifted just enough to keep a long panel on screen.
        val y = anchorBounds.top
            .coerceAtMost(windowSize.height - popupContentSize.height - margin)
            .coerceAtLeast(margin)

        return IntOffset(x, y)
    }
}
