// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.util

import android.content.Context
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.mochios.android.R

/**
 * One zone's shape on the map: its rings, each a run of points given as x
 * then y in the map's frame. A ring inside another is a hole in it.
 */
class Area(val zone: String, val rings: List<FloatArray>) {
    /** Whether the point lies inside, by the even-odd rule, so a hole is outside. */
    fun contains(x: Float, y: Float): Boolean {
        var inside = false
        for (ring in rings) {
            val count = ring.size / 2
            var j = count - 1
            for (i in 0 until count) {
                val xi = ring[2 * i]
                val yi = ring[2 * i + 1]
                val xj = ring[2 * j]
                val yj = ring[2 * j + 1]
                if ((yi > y) != (yj > y) && x < (xj - xi) * (y - yi) / (yj - yi) + xi) inside = !inside
                j = i
            }
        }
        return inside
    }
}

/**
 * The time zone map the web's picker draws, built by
 * claude/scripts/timezone-map.py from OpenStreetMap-derived boundaries and
 * Natural Earth's coastlines, in a [width] by [height] equirectangular frame
 * cropped at 60 degrees south. Three layers, drawn in this order: [oceans],
 * the Etc/GMT zones as bands beneath everything; [zones], each zone whole,
 * drawn invisible so a tap in a zone's waters still finds it; and [land],
 * each zone clipped to its coastlines, which is what shows.
 */
class Atlas(
    val width: Float,
    val height: Float,
    val attribution: String,
    val oceans: List<Area>,
    val zones: List<Area>,
    val land: List<Area>,
) {
    /**
     * The zone a point in the map's frame falls in: the one drawn on top, so
     * land before a zone's waters before the sea bands, and within a layer
     * the one drawn last, as the web's map picks where two zones cover the
     * same ground.
     */
    fun at(x: Float, y: Float): String? {
        for (layer in listOf(land, zones, oceans)) {
            for (index in layer.indices.reversed()) {
                if (layer[index].contains(x, y)) return layer[index].zone
            }
        }
        return null
    }

    companion object {
        @Volatile
        private var loaded: Atlas? = null

        /** The map the app carries, read once. */
        fun load(context: Context): Atlas = loaded ?: synchronized(this) {
            loaded ?: context.resources.openRawResource(R.raw.timezones).bufferedReader().use { parse(it.readText()) }
                .also { loaded = it }
        }

        /** The map from the text the build script writes. */
        fun parse(text: String): Atlas {
            val json = JsonParser.parseString(text).asJsonObject
            fun layer(name: String): List<Area> {
                val paths: JsonObject = json.getAsJsonObject(name) ?: return emptyList()
                return paths.entrySet().map { (zone, path) -> Area(zone, rings(path.asString)) }
            }
            return Atlas(
                width = json.get("width")?.asFloat ?: 960f,
                height = json.get("height")?.asFloat ?: 400f,
                attribution = json.get("attribution")?.asString.orEmpty(),
                oceans = layer("oceans"),
                zones = layer("zones"),
                land = layer("land"),
            )
        }

        /**
         * The rings of an SVG path written as the build script writes one:
         * each ring a move, then lines, then a close, which a ring's own
         * points already are.
         */
        fun rings(path: String): List<FloatArray> {
            val out = mutableListOf<FloatArray>()
            var ring = mutableListOf<Float>()
            fun close() {
                if (ring.size >= 6) out += ring.toFloatArray()
                ring = mutableListOf()
            }
            for (token in TOKEN.findAll(path)) {
                when (val value = token.value) {
                    "M" -> close()
                    "L", "Z" -> Unit
                    else -> ring += value.toFloat()
                }
            }
            close()
            return out
        }

        private val TOKEN = Regex("""[MLZ]|-?\d+(?:\.\d+)?""")
    }
}

/**
 * How the map sits in its box: [scale] pixels to a unit of the map's frame,
 * and the map's top left corner at [x], [y] in pixels. The map always covers
 * the box, from its whole width at [floor] up to eight times that.
 */
data class Viewport(val scale: Float, val x: Float, val y: Float, val floor: Float) {
    /** The point of the map's frame under the pixel [px], [py]. */
    fun map(px: Float, py: Float): Pair<Float, Float> = (px - x) / scale to (py - y) / scale

    /**
     * Zoomed by [factor] about the pixel [px], [py] and moved by [dx], [dy],
     * held so the map of [width] by [height] units still covers a box of
     * [box] by [tall] pixels.
     */
    fun moved(factor: Float, px: Float, py: Float, dx: Float, dy: Float, width: Float, height: Float, box: Float, tall: Float): Viewport {
        val next = (scale * factor).coerceIn(floor, floor * 8)
        val grown = next / scale
        val nx = px - (px - x) * grown + dx
        val ny = py - (py - y) * grown + dy
        return Viewport(
            scale = next,
            x = nx.coerceIn(minOf(0f, box - width * next), 0f),
            y = ny.coerceIn(minOf(0f, tall - height * next), 0f),
            floor = floor,
        )
    }

    companion object {
        /** The whole map across a box [box] pixels wide. */
        fun whole(box: Float, width: Float): Viewport {
            val scale = if (width > 0) box / width else 1f
            return Viewport(scale, 0f, 0f, scale)
        }
    }
}
