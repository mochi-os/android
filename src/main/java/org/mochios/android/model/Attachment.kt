package org.mochios.android.model

import com.google.gson.annotations.SerializedName

data class Attachment(
    val id: String,
    val name: String = "",
    val size: Long = 0,
    val type: String = "",
    val created: Long = 0,
    val url: String? = null,
    @SerializedName("thumbnail_url") val thumbnailUrl: String? = null
) {
    val isImage: Boolean get() = type.startsWith("image/")
    val isVideo: Boolean get() = type.startsWith("video/")
}

/**
 * Server-issued `url` / `thumbnail_url` fields are relative paths (e.g.
 * `/feeds/<entity>/-/attachments/<id>`). Coil needs an absolute URL — without
 * the host prefix the request silently fails and the image never appears.
 * Use this helper at every call site that hands a URL to Coil.
 */
fun resolveAttachmentUrl(serverUrl: String, path: String): String {
    if (path.startsWith("http://") || path.startsWith("https://")) return path
    val base = serverUrl.trimEnd('/')
    return if (path.startsWith("/")) "$base$path" else "$base/$path"
}
