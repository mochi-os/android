package org.mochios.android.model

data class PlaceData(
    val name: String = "",
    val lat: Double = 0.0,
    val lon: Double = 0.0,
    val country: String = "",
    val state: String = "",
    val city: String = "",
    val category: String = ""
)
