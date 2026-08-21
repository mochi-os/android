// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.ui.components

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.MenuItemColors
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.PopupProperties
import org.mochios.android.ui.theme.LocalEntityRadius

/** Narrowest a menu may be, so short labels still read as a panel rather than a chip. */
private val MenuMinWidth = 200.dp

/** Menus round harder than list entities do, but still follow the user's radius setting. */
private val MenuMinRadius = 16.dp

/** Roomier than [MenuDefaults.DropdownMenuItemContentPadding], which leaves rows cramped. */
private val ItemPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp)


/**
 * A [DropdownMenu] with the app's rounded, tinted, shadowless container.
 * Container tone, 0dp elevation, floored radius and 16dp row padding depart
 * from `MenuTokens` deliberately - the tone replaces the shadow, so never
 * restore one without the other.
 */
@Composable
fun MochiDropdownMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    offset: DpOffset = DpOffset(0.dp, 0.dp),
    scrollState: ScrollState = rememberScrollState(),
    properties: PopupProperties = PopupProperties(focusable = true),
    content: @Composable ColumnScope.() -> Unit
) {
    val radius = LocalEntityRadius.current.coerceAtLeast(MenuMinRadius)
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        modifier = modifier.widthIn(min = MenuMinWidth),
        offset = offset,
        scrollState = scrollState,
        properties = properties,
        shape = RoundedCornerShape(radius),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        shadowElevation = 0.dp,
        content = content
    )
}

/**
 * A row inside [MochiDropdownMenu]. Mark a picker's chosen row with [selected]
 * and a delete with [destructive] rather than colouring by hand; [selected]
 * also draws the trailing check, so colour and mark cannot disagree.
 */
@Composable
fun MochiDropdownMenuItem(
    text: @Composable () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
    enabled: Boolean = true,
    selected: Boolean = false,
    destructive: Boolean = false,
    colors: MenuItemColors? = null
) {
    val accent = when {
        selected -> MaterialTheme.colorScheme.primary
        destructive -> MaterialTheme.colorScheme.error
        else -> null
    }
    val resolvedColors = colors ?: if (accent == null) {
        MenuDefaults.itemColors()
    } else {
        MenuDefaults.itemColors(
            textColor = accent,
            leadingIconColor = accent,
            trailingIconColor = accent
        )
    }
    DropdownMenuItem(
        text = text,
        onClick = onClick,
        modifier = modifier,
        leadingIcon = leadingIcon,
        trailingIcon = trailingIcon ?: if (selected) SelectedCheck else null,
        enabled = enabled,
        colors = resolvedColors,
        contentPadding = ItemPadding
    )
}

/** The mark on a [MochiDropdownMenuItem] the user has chosen. */
private val SelectedCheck: @Composable () -> Unit = {
    Icon(Icons.Outlined.Check, contentDescription = null)
}

/**
 * A hairline between groups of related rows. Menus long enough to need scanning read
 * better in groups; a menu of five or fewer rows rarely needs one.
 */
@Composable
fun MochiDropdownMenuDivider(modifier: Modifier = Modifier) {
    HorizontalDivider(modifier = modifier.padding(ItemPadding))
}

