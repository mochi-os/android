// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.model

// What the access level a project or CRM answers (owner, design, write,
// comment or view) lets its reader do, as the web's entity-access.ts reads it
// and as the owner checks a forwarded request: each level allows what the
// levels below it do. Managing the container's settings and access is the
// owner's alone, read from the owner being local rather than from a level.

/** Whether [access] may change the design: classes, fields, options and views. */
fun canDesign(access: String?): Boolean = access == "owner" || access == "design"

/** Whether [access] may change objects: their values, moves, links, attachments and requests. */
fun canWrite(access: String?): Boolean = canDesign(access) || access == "write"

/** Whether [access] may create objects, which is writing. */
fun canCreate(access: String?): Boolean = canWrite(access)

/** Whether [access] may comment. */
fun canComment(access: String?): Boolean = canWrite(access) || access == "comment"
