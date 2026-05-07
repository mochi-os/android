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
