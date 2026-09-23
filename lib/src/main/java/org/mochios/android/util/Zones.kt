// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.util

/**
 * The city an IANA zone is named for, as a label beside a time in that zone:
 * "America/New_York" reads "New York", "UTC" stays "UTC". Agrees with the
 * web's `zoneCity`, character for character.
 */
fun zoneCity(zone: String): String = zone.substringAfterLast('/').replace('_', ' ')
