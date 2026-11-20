// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.model

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.util.UUID

/**
 * One item of a checklist field. A checklist's value is a single JSON string
 * that every client of a project or CRM shares, and the web finds an item by
 * its [id], so an item keeps its id through every edit. Keys another client
 * wrote travel through untouched in [extra].
 */
data class ChecklistItem(
    val id: String,
    val text: String,
    val done: Boolean,
    val extra: JsonObject = JsonObject(),
)

/**
 * Reads and writes a checklist field's value in the shape the web writes:
 * an array of `{"id", "text", "done"}`. Reading repairs what older clients
 * left: an item with no id, or one repeating an earlier id, is given one that
 * stays the same on every read (`item-<index>`, as the web's reader gives
 * it), and an item written as `{"text", "checked"}` reads `checked` as done.
 * The next edit then writes the repaired list.
 */
object Checklist {
    private val RESERVED = setOf("id", "text", "done", "checked")

    /** The items of [value]; anything that is not an array of objects reads as none. */
    fun parse(value: String?): List<ChecklistItem> {
        if (value.isNullOrBlank()) return emptyList()
        val root: JsonElement = try {
            JsonParser.parseString(value)
        } catch (_: Exception) {
            return emptyList()
        }
        if (!root.isJsonArray) return emptyList()
        val seen = HashSet<String>()
        val items = ArrayList<ChecklistItem>()
        root.asJsonArray.forEachIndexed { index, element ->
            if (element == null || !element.isJsonObject) return@forEachIndexed
            val item = element.asJsonObject
            var id = text(item, "id")
            if (id.isEmpty()) id = "item-$index"
            while (!seen.add(id)) id = "$id-$index"
            val done = flag(item, "done") ?: flag(item, "checked") ?: false
            val extra = JsonObject()
            for ((key, kept) in item.entrySet()) {
                if (key !in RESERVED) extra.add(key, kept)
            }
            items += ChecklistItem(id = id, text = text(item, "text"), done = done, extra = extra)
        }
        return items
    }

    /** [items] as the value to store. */
    fun write(items: List<ChecklistItem>): String {
        val array = JsonArray()
        for (item in items) {
            val written = JsonObject()
            for ((key, kept) in item.extra.entrySet()) written.add(key, kept)
            written.addProperty("id", item.id)
            written.addProperty("text", item.text)
            written.addProperty("done", item.done)
            array.add(written)
        }
        return array.toString()
    }

    /** A new, unticked item with an id of its own. */
    fun item(text: String = ""): ChecklistItem =
        ChecklistItem(id = UUID.randomUUID().toString().replace("-", "").take(12), text = text, done = false)

    private fun text(item: JsonObject, key: String): String {
        val element = item.get(key) ?: return ""
        return if (element.isJsonPrimitive) element.asString else ""
    }

    private fun flag(item: JsonObject, key: String): Boolean? {
        val element = item.get(key) ?: return null
        if (!element.isJsonPrimitive) return null
        val primitive = element.asJsonPrimitive
        return if (primitive.isBoolean) primitive.asBoolean else null
    }
}
