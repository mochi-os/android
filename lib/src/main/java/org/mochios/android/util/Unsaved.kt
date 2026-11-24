// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.util

/**
 * An object sheet's edits the server may not have yet: each field's value,
 * and the object's parent, held from the edit until its save answers.
 *
 * A read of the object takes [waiting] as it is sent and hands it back with
 * the answer to [values] and [parent]. An edit waiting at either moment
 * stands over the answer, since a read sent before a save landed may have
 * been served before it: waiting still, or saved since, the edit's own value
 * shows; refused since, what the sheet shows, which the refusal has put back.
 * Every other field takes the answer, a field emptied elsewhere included.
 * A copy the sheet opens from, such as the board's, goes through [values] and
 * [parent] with nothing sent, so the edits still waiting go over it.
 *
 * Use it from the main thread, where a view model's coroutines run.
 */
class UnsavedEdits {
    /** One edit, from the moment it is made until its save answers. */
    class Edit internal constructor(internal val value: Any?) {
        internal var outcome = Outcome.Waiting
    }

    internal enum class Outcome { Waiting, Saved, Refused }

    /**
     * The latest edit of each field, by object and field, the parent's under
     * a null field. Kept once answered, for a read sent while it waited.
     */
    private val latest = HashMap<Pair<String, String?>, Edit>()

    /** Holds [value] as the edit of [field] on [objectId] until it is [saved] or [refused]. */
    fun value(objectId: String, field: String, value: Any?): Edit = hold(objectId to field, value)

    /** Holds [parent] as the edit of [objectId]'s parent until it is [saved] or [refused]. */
    fun parent(objectId: String, parent: String): Edit = hold(objectId to null, parent)

    private fun hold(key: Pair<String, String?>, value: Any?): Edit {
        val edit = Edit(value)
        latest[key] = edit
        return edit
    }

    /** The server took [edit]. */
    fun saved(edit: Edit) {
        if (edit.outcome == Outcome.Waiting) edit.outcome = Outcome.Saved
    }

    /** The server refused [edit]. */
    fun refused(edit: Edit) {
        if (edit.outcome == Outcome.Waiting) edit.outcome = Outcome.Refused
    }

    /**
     * The fields of [objectId] with an edit waiting on its save, the parent as
     * null: taken as a read of the object is sent.
     */
    fun waiting(objectId: String): Set<String?> {
        val fields = HashSet<String?>()
        for ((key, edit) in latest) {
            if (key.first == objectId && edit.outcome == Outcome.Waiting) fields += key.second
        }
        return fields
    }

    /**
     * The values to show for [objectId]: [fetched], answered to a read sent
     * while the fields in [sent] waited, with the edits that stand over it.
     * [shown] is what the sheet shows for the object, or null when it shows
     * another.
     */
    fun values(objectId: String, fetched: Map<String, Any?>, shown: Map<String, Any?>?, sent: Set<String?>): Map<String, Any?> {
        val fields = (sent + waiting(objectId)).filterNotNull()
        if (fields.isEmpty()) return fetched
        val values = LinkedHashMap(fetched)
        for (field in fields) {
            val edit = latest[objectId to field] ?: continue
            when {
                edit.outcome != Outcome.Refused -> values[field] = edit.value
                shown == null -> Unit
                field in shown -> values[field] = shown[field]
                else -> values.remove(field)
            }
        }
        return values
    }

    /** The parent to show for [objectId]: [fetched], or the edit that stands over it, as for [values]. */
    fun parent(objectId: String, fetched: String, shown: String?, sent: Set<String?>): String {
        if (null !in sent && null !in waiting(objectId)) return fetched
        val edit = latest[objectId to null] ?: return fetched
        if (edit.outcome == Outcome.Refused) return shown ?: fetched
        return edit.value as? String ?: fetched
    }
}
