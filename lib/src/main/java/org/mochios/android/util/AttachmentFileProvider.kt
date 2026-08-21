// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.util

import androidx.core.content.FileProvider

/**
 * Dedicated [FileProvider] for attachments cached under
 * `cacheDir/attachments/`. Subclassed because two `<provider>` entries sharing
 * one class name collide at manifest merge; its paths come from
 * `android.support.FILE_PROVIDER_PATHS`.
 */
class AttachmentFileProvider : FileProvider()
