// Copyright © 2026 Mochi OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.util

import androidx.core.content.FileProvider

/**
 * Dedicated [FileProvider] for chat / feed attachments cached under
 * `cacheDir/attachments/`.
 *
 * Subclassed rather than declared as the bare `androidx.core.content.FileProvider`
 * so its authority and paths stay independent of the app module's update-installer
 * provider — two `<provider>` entries sharing the same class name collide during
 * manifest merge. The path config is bound via the `android.support.FILE_PROVIDER_PATHS`
 * meta-data in the manifest, which the static `getUriForFile` lookup reads.
 */
class AttachmentFileProvider : FileProvider()
