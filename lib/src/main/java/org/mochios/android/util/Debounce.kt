// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.util

import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/**
 * How long typing must pause before a search-as-you-type request fires - one
 * value for every search in the app. The delay is not enough on its own: cancel
 * the in-flight job (or use `collectLatest`), or a slow early response
 * overwrites newer.
 */
val SEARCH_DEBOUNCE: Duration = 300.milliseconds
