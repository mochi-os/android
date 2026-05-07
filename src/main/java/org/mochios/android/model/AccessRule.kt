package org.mochios.android.model

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.google.gson.annotations.SerializedName
import org.mochios.android.R

data class AccessRule(
    val id: Int = 0,
    val subject: String = "",
    val resource: String = "",
    val operation: String = "",
    val grant: Int = 0,
    val granter: String = "",
    val created: Long = 0,
    val name: String? = null,
    @SerializedName("isOwner") val isOwner: Boolean = false
)

enum class AccessLevel(val value: String) {
    MANAGE("manage"),
    COMMENT("comment"),
    REACT("react"),
    VIEW("view"),
    NONE("none");

    companion object {
        fun fromValue(value: String): AccessLevel? {
            return entries.find { it.value == value }
        }
    }
}

@Composable
fun AccessLevel.label(): String = stringResource(
    when (this) {
        AccessLevel.MANAGE -> R.string.access_level_manage
        AccessLevel.COMMENT -> R.string.access_level_comment
        AccessLevel.REACT -> R.string.access_level_react
        AccessLevel.VIEW -> R.string.access_level_view
        AccessLevel.NONE -> R.string.access_level_none
    }
)
