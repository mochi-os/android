// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.feeds.ui.component

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sign

/*
 * A blurhash is the few-character placeholder Mastodon and its kin attach to
 * a picture: a handful of DCT coefficients from which a blurred version is
 * drawn before the picture loads, and in place of it while it is marked
 * sensitive. This decodes one to pixels (the reference algorithm from
 * blurha.sh), as the web client does.
 */

private const val ALPHABET =
    "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz#$%*+,-.:;=?@[]^_{|}~"

private fun decode83(text: String): Int {
    var value = 0
    for (character in text) {
        val digit = ALPHABET.indexOf(character)
        if (digit < 0) return 0
        value = value * 83 + digit
    }
    return value
}

private fun linear(value: Int): Double {
    val v = value / 255.0
    return if (v <= 0.04045) v / 12.92 else ((v + 0.055) / 1.055).pow(2.4)
}

private fun srgb(value: Double): Int {
    val v = value.coerceIn(0.0, 1.0)
    return ((if (v <= 0.0031308) v * 12.92 else 1.055 * v.pow(1 / 2.4) - 0.055) * 255).roundToInt()
}

private fun signed(value: Double, exponent: Double): Double = sign(value) * abs(value).pow(exponent)

/** Whether a string is a blurhash this decoder can read. */
internal fun isBlurhash(hash: String?): Boolean {
    if (hash == null || hash.length < 6) return false
    val size = decode83(hash.substring(0, 1))
    val components = (size / 9 + 1) * (size % 9 + 1)
    return hash.length == 4 + 2 * components
}

/**
 * The pixels of a blurhash at the size given, as ARGB colours row by row, or
 * null for a string that is not a blurhash.
 */
internal fun decodeBlurhash(hash: String, width: Int, height: Int): IntArray? {
    if (!isBlurhash(hash)) return null
    val size = decode83(hash.substring(0, 1))
    val columns = size % 9 + 1
    val rows = size / 9 + 1
    val maximum = (decode83(hash.substring(1, 2)) + 1) / 166.0
    val colours = Array(columns * rows) { i ->
        if (i == 0) {
            val value = decode83(hash.substring(2, 6))
            doubleArrayOf(linear(value shr 16), linear((value shr 8) and 255), linear(value and 255))
        } else {
            val value = decode83(hash.substring(4 + i * 2, 6 + i * 2))
            doubleArrayOf(
                signed((value / (19 * 19) - 9) / 9.0, 2.0) * maximum,
                signed(((value / 19) % 19 - 9) / 9.0, 2.0) * maximum,
                signed((value % 19 - 9) / 9.0, 2.0) * maximum,
            )
        }
    }
    val pixels = IntArray(width * height)
    for (y in 0 until height) {
        for (x in 0 until width) {
            var r = 0.0
            var g = 0.0
            var b = 0.0
            for (j in 0 until rows) {
                for (i in 0 until columns) {
                    val basis = cos(PI * x * i / width) * cos(PI * y * j / height)
                    val colour = colours[i + j * columns]
                    r += colour[0] * basis
                    g += colour[1] * basis
                    b += colour[2] * basis
                }
            }
            pixels[x + y * width] = (0xFF shl 24) or (srgb(r) shl 16) or (srgb(g) shl 8) or srgb(b)
        }
    }
    return pixels
}
