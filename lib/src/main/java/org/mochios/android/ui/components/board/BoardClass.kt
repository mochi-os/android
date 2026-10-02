// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.ui.components.board

/**
 * The class a board keeps its columns in: where it reads them, and where it
 * adds, renames, removes and reorders them. Options are held per class under a
 * field id the classes share, so a board over several classes has to settle on
 * one, and every client on the same one: this is the web's choice.
 *
 * It is the first class the [view] lists, or the first of the [classes] when
 * the view lists none or leads with one that is gone. Both are class ids in
 * class order, as the server sends them.
 */
fun boardClass(classes: List<String>, view: List<String>): String? =
    classes.firstOrNull { id -> id == view.firstOrNull() } ?: classes.firstOrNull()
