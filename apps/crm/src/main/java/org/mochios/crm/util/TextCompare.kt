// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.crm.util

import org.mochios.android.util.NaturalCompare

/**
 * How two field values order when they are text: the shared natural compare
 * (case- and accent-insensitive, numeric-aware), which is what web's
 * `naturalCompare` sorts the same view by.
 */
fun textCompare(a: String, b: String): Int = NaturalCompare.compare(a, b)
