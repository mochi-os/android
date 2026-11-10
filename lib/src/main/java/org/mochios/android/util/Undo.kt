// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.util

/** What showed before a tap, wrapped so that null can be a value. */
data class Previous<T>(val value: T)

/** One tap on a thing, from the tap until the server answers. */
interface UndoStep<T> {
    /** What showed before this tap. Only the first call counts. */
    fun remember(value: T)

    /** The server took the change. */
    fun accepted()

    /** The server refused it: what to put back, or null to change nothing. */
    fun refused(): Previous<T>?
}

/**
 * The tap order for a change shown on the tap, one thing at a time, as the
 * web client's `undoChain` keeps it. For two quick taps on one thing:
 * - refused with a later tap still waiting: nothing changes on screen, and
 *   the later tap takes over what to put back if it is refused too
 * - refused as the last tap waiting: the value from before goes back
 * - accepted: every earlier tap on the thing has nothing left to undo, as
 *   the server now holds this tap's value
 *
 * Make one per kind of change and call [start] with the thing's id on each
 * tap. It is not thread-safe: use it from the main thread, where a view
 * model's coroutines run.
 */
class UndoChain<T> {
    private class Waiting<T> {
        var before: Previous<T>? = null
    }

    /** The taps still waiting on the server, per thing, oldest first. */
    private val waiting = HashMap<String, MutableList<Waiting<T>>>()

    fun start(id: String): UndoStep<T> {
        val entry = Waiting<T>()
        val list = waiting.getOrPut(id) { mutableListOf() }
        list.add(entry)
        val tidy = {
            if (list.isEmpty() && waiting[id] === list) waiting.remove(id)
        }

        return object : UndoStep<T> {
            override fun remember(value: T) {
                if (entry.before == null) entry.before = Previous(value)
            }

            override fun accepted() {
                val index = list.indexOf(entry)
                if (index < 0) return
                list.subList(0, index + 1).clear()
                tidy()
            }

            override fun refused(): Previous<T>? {
                val index = list.indexOf(entry)
                if (index < 0) return null
                list.removeAt(index)
                val later = list.getOrNull(index)
                tidy()
                if (later != null) {
                    // A tap that never remembered a value hands nothing on, so
                    // the later tap keeps its own.
                    later.before = entry.before ?: later.before
                    return null
                }
                return entry.before
            }
        }
    }
}
