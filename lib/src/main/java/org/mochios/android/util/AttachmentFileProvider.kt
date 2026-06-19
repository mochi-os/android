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
