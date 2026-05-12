package org.mochios.projects.model

import com.google.gson.annotations.SerializedName

data class ProjectObject(
    val id: String = "",
    val project: String = "",
    @SerializedName("class") val objectClass: String = "",
    val number: Int = 0,
    val parent: String = "",
    val rank: Int = 0,
    val created: Long = 0,
    val updated: Long = 0,
    val readable: String = "",
    val values: Map<String, Any?> = emptyMap()
) {
    fun stringValue(fieldId: String): String = values[fieldId]?.toString() ?: ""

    fun listValue(fieldId: String): List<String> =
        (values[fieldId] as? List<*>)?.mapNotNull { it?.toString() } ?: emptyList()
}
