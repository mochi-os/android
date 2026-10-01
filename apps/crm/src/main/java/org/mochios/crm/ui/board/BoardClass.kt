// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.crm.ui.board

import org.mochios.android.ui.components.board.boardClass
import org.mochios.crm.model.CrmDetails
import org.mochios.crm.model.CrmView
import org.mochios.crm.model.FieldOption

/**
 * The class [view]'s board keeps its columns in, by the rule every client
 * shares. The classes are put in rank order first: the options map beside them
 * arrives keyed in alphabetical order, which is not the CRM's.
 */
fun boardClass(details: CrmDetails, view: CrmView?): String? =
    boardClass(
        classes = details.classes.sortedBy { cls -> cls.rank }.map { cls -> cls.id },
        view = view?.classes.orEmpty(),
    )

/** The options of [field] that [view]'s board shows as its columns or lanes: its board class's. */
fun boardOptions(details: CrmDetails, view: CrmView?, field: String): List<FieldOption> {
    val classId = boardClass(details, view) ?: return emptyList()
    return details.options[classId]?.get(field).orEmpty()
}
