package org.mochios.projects.model

import com.google.gson.annotations.SerializedName

data class MergeRequest(
    val id: String = "",
    @SerializedName("object") val objectId: String = "",
    val type: String = "",
    val repository: String = "",
    val source: String = "",
    val target: String = "",
    val status: String = "",
    val title: String = "",
    val description: String = "",
    val draft: Boolean = false,
    val created: Long = 0,
    val updated: Long = 0
)
