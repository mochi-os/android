package org.mochios.crm.model

import com.google.gson.annotations.SerializedName

data class Link(
    val source: String = "",
    val target: String = "",
    val linktype: String = "",
    val created: Long = 0,
    val number: Int = 0,
    @SerializedName("class") val objectClass: String = "",
    val title: String = ""
)
