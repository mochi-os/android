// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.util

import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/**
 * How long typing must pause before a search-as-you-type request fires.
 *
 * One value for every search in the app — user, member and place pickers, entity
 * discovery, mentions, chat, staff and wiki search — so a search feels the same
 * wherever it appears, and so tuning it is one edit rather than a hunt. Sites
 * previously ran anywhere from 200ms to 500ms.
 *
 * A [Duration] rather than a bare millisecond count: both `delay` and
 * `Flow.debounce` take one, and their `Long` overloads are the legacy form.
 *
 * Debouncing a search is not only about call volume: without it, a slow early
 * response can land after a later one and overwrite newer results. Pair this
 * with cancelling the in-flight job (or `collectLatest`) rather than relying on
 * the delay alone.
 */
val SEARCH_DEBOUNCE: Duration = 300.milliseconds
