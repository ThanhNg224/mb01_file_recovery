package com.thanhng224.app.model


import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Review(
    @SerialName("comment")
    val comment: String,
    @SerialName("date")
    val date: String,
    @SerialName("rating")
    val rating: Int,
    @SerialName("reviewerEmail")
    val reviewerEmail: String,
    @SerialName("reviewerName")
    val reviewerName: String
)