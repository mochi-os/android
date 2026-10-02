// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.crm.util

import org.mochios.crm.model.CrmField

/**
 * The fields an object's own form shows: every field of its class, in the
 * class's order, as the web's panel shows them. The view the object was opened
 * from has no say: the fields a view lists are the ones on its cards and rows.
 */
fun formFields(fields: List<CrmField>): List<CrmField> = fields.sortedBy { field -> field.rank }
