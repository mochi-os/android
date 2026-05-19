package org.mochios.staff.model

import com.google.gson.annotations.SerializedName

/**
 * Category row as exposed by `categories/list`.
 *
 * Mirrors `Category` in `apps/staff/web/src/types/categories.ts`. `digital`,
 * `physical`, `active` are 0/1 ints in the public market response — the
 * staff types file types them as booleans, which Gson tolerates either way;
 * here they are booleans to match the TS source of truth.
 *
 * `parent` is the integer id of the parent category. It is nullable so a
 * root-level category (web omits the field or sends `0`) can be modelled
 * cleanly without conflating "explicitly under category 0" with "no parent".
 * `children` is the count of direct sub-categories, populated server-side
 * for the staff tree view.
 */
data class Category(
    val id: Long = 0,
    val parent: Long? = null,
    val name: String = "",
    val slug: String = "",
    val icon: String = "",
    val digital: Boolean = false,
    val physical: Boolean = false,
    val position: Long = 0,
    val active: Boolean = true,
    val children: Long = 0,
)
