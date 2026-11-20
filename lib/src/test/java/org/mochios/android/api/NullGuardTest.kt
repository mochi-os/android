// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.api

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonParseException
import com.google.gson.reflect.TypeToken
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test
import org.mochios.android.model.Attachment
import org.mochios.android.model.FileKind

/** A model every field of which has a default: Gson builds it through the constructor that takes nothing. */
data class Defaulted(
    val id: String = "",
    val body: String = "",
    val tags: List<String> = emptyList(),
    val open: Boolean = false,
    /** Nullable, with a default that is not null: an explicit null stays null. */
    val note: String? = "unread",
)

/** A model with a field that has no default: Gson builds it without running a constructor. */
data class Needed(
    val id: String,
    val name: String = "unnamed",
    val parts: List<Defaulted> = emptyList(),
    val extra: String? = null,
)

/**
 * A model whose constructor takes what its fields hold in another type, so
 * calling it with the fields' values fails in reflection, not in the class.
 */
class Reshaped(note: String = "", tags: String = "") {
    val note: String = note
    val tags: List<String> = tags.split(",")
}

/** A generic wrapper: a type parameter's nullability is the argument's, unknown when it is read. */
data class Wrapped<T>(val data: T)

/**
 * What the app's Gson makes of a null where a model declares none: the
 * class's default, or a refusal where it has none, and nothing different for
 * an answer that holds every field.
 */
class NullGuardTest {

    private val gson = ApiClient.provideGson()

    private inline fun <reified T> read(json: String): T = gson.fromJson(json, object : TypeToken<T>() {}.type)

    @Test
    fun `an answer that holds every field reads as it always did`() {
        assertEquals(
            Defaulted(id = "p1", body = "text", tags = listOf("a"), open = true, note = "seen"),
            read<Defaulted>("""{"id": "p1", "body": "text", "tags": ["a"], "open": 1, "note": "seen"}"""),
        )
        assertEquals(
            Needed(id = "n1", name = "Ann", extra = "x"),
            read<Needed>("""{"id": "n1", "name": "Ann", "parts": [], "extra": "x"}"""),
        )
    }

    @Test
    fun `a null in a non-null field takes the class's default`() {
        val row = read<Defaulted>("""{"id": "p1", "body": null, "tags": null, "note": null}""")
        // THE POINT: what the model declares, not a null its first use throws on.
        assertEquals("", row.body)
        assertEquals(emptyList<String>(), row.tags)
        assertEquals("p1", row.id)
        // A nullable field keeps the null it was sent, whatever its default.
        assertNull(row.note)
    }

    @Test
    fun `a field left out of a class with no empty constructor takes its default`() {
        val needed = read<Needed>("""{"id": "n1", "parts": [{"body": null}]}""")
        assertEquals("unnamed", needed.name)
        assertEquals("", needed.parts.single().body)
        assertNull(needed.extra)
    }

    @Test
    fun `a null where the class has no default refuses the answer`() {
        assertThrows(JsonParseException::class.java) { read<Needed>("""{"name": "Ann"}""") }
        assertThrows(JsonParseException::class.java) { read<Needed>("""{"id": null}""") }
    }

    @Test
    fun `a type parameter's null is left to the reader`() {
        assertNull(read<Wrapped<Defaulted>>("""{"data": null}""").data)
        assertNull(read<ApiResponse<Defaulted>>("""{"data": null}""").data)
    }

    /** Asserts [gson] reads [json] as [T] without refusing it, and as plain Gson does. */
    private fun <T> asPlain(gson: com.google.gson.Gson, json: String, type: Class<T>) {
        val read = runCatching { gson.fromJson(json, type) }
        assertNull("refused: ${read.exceptionOrNull()}", read.exceptionOrNull())
        assertEquals(Gson().fromJson(json, type), read.getOrNull())
    }

    @Test
    fun `a class with no Kotlin metadata is read as plain Gson reads it`() {
        // THE POINT: nothing to judge it by, so nothing is filled or refused.
        val read = runCatching { gson.fromJson("""{"id": "p1", "body": null}""", PlainRow::class.java) }
        assertNull("refused: ${read.exceptionOrNull()}", read.exceptionOrNull())
        assertNull(read.getOrNull()?.body)
        assertEquals("p1", read.getOrNull()?.id)
    }

    @Test
    fun `a class kotlin-reflect cannot read is read as plain Gson reads it`() {
        val blind = GsonBuilder()
            .registerTypeAdapterFactory(NullGuard(properties = { throw KotlinReflectionNotSupportedError() }))
            .create()
        // THE POINT: the same as without the guard, and no refusal.
        asPlain(blind, """{"id": "p1", "body": null, "tags": null}""", Defaulted::class.java)
        asPlain(blind, """{"name": "Ann"}""", Needed::class.java)
    }

    @Test
    fun `a class whose constructor reflection cannot call is read as plain Gson reads it`() {
        val read = runCatching { gson.fromJson("""{"note": null, "tags": ["a"]}""", Reshaped::class.java) }
        // THE POINT: not refused for what reflection could not do.
        assertNull("refused: ${read.exceptionOrNull()}", read.exceptionOrNull())
        assertEquals(listOf("a"), read.getOrNull()?.tags)
        assertNull(read.getOrNull()?.note)
    }

    @Test
    fun `an attachment holding only its id reads with every other field at its default`() {
        val attachment = read<Attachment>("""{"id": "x"}""")
        assertEquals("", attachment.name)
        assertEquals("", attachment.type)
        assertEquals(FileKind.OTHER, attachment.fileKind)
        // With no id, the default id: an attachment is not refused for it.
        val unnamed = runCatching { read<Attachment>("""{"name": "a.png", "type": "image/png"}""") }.getOrNull()
        assertNotNull("an attachment without an id is read", unnamed)
        assertEquals("", unnamed?.id)
        assertEquals(FileKind.IMAGE, unnamed?.fileKind)
    }
}
