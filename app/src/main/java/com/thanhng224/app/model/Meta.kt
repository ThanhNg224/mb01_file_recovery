package com.thanhng224.app.model


import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Meta(
    @SerialName("barcode")
    val barcode: String,
    @SerialName("createdAt")
    val createdAt: String,
    @SerialName("qrCode")
    val qrCode: String,
    @SerialName("updatedAt")
    val updatedAt: String
)