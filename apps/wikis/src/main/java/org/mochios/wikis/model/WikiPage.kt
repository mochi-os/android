// Copyright © 2026 Mochi OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.wikis.model

import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.google.gson.annotations.SerializedName
import java.lang.reflect.Type

data class WikiPage(
    val id: String = "",
    val slug: String = "",
    val title: String = "",
    val content: String = "",
    val author: String = "",
    val created: Long = 0,
    val updated: Long = 0,
    val version: Int = 0,
    val tags: List<String> = emptyList(),
)

data class PageResponse(
    val page: WikiPage = WikiPage(),
    @SerializedName("missing_links")
    val missingLinks: List<String>? = null,
    @SerializedName("comment_count")
    val commentCount: Int? = null,
)

data class PageNotFoundResponse(
    val error: String = "",
    val page: String = "",
)

sealed class PageFetchResponse {
    data class Page(
        val page: WikiPage,
        val missingLinks: List<String>?,
        val commentCount: Int?,
    ) : PageFetchResponse()

    data class NotFound(val slug: String) : PageFetchResponse()
}

class PageFetchResponseDeserializer : JsonDeserializer<PageFetchResponse> {
    override fun deserialize(
        json: JsonElement,
        typeOfT: Type,
        context: JsonDeserializationContext,
    ): PageFetchResponse {
        val obj = json.asJsonObject
        val error = obj.get("error")?.takeIf { !it.isJsonNull }?.asString
        return if (error != null) {
            val slug = obj.get("page")?.takeIf { !it.isJsonNull }?.asString ?: ""
            PageFetchResponse.NotFound(slug)
        } else {
            val page = context.deserialize<WikiPage>(obj.get("page"), WikiPage::class.java)
            val missingLinks = obj.get("missing_links")
                ?.takeIf { !it.isJsonNull }
                ?.asJsonArray
                ?.map { it.asString }
            val commentCount = obj.get("comment_count")
                ?.takeIf { !it.isJsonNull }
                ?.asInt
            PageFetchResponse.Page(page, missingLinks, commentCount)
        }
    }
}

data class PageEditResponse(
    val id: String = "",
    val slug: String = "",
    val version: Int = 0,
    val created: Boolean = false,
)

data class NewPageResponse(
    val id: String = "",
    val slug: String = "",
)

data class PageRevertResponse(
    val slug: String = "",
    val version: Int = 0,
    @SerializedName("reverted_from")
    val revertedFrom: Int = 0,
)

data class PageDeleteResponse(
    val ok: Boolean = false,
    val slug: String = "",
)
