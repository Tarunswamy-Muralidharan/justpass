package com.justpass.app.data.model

import com.google.gson.annotations.SerializedName

data class CircularListResponse(
    @SerializedName("records") val records: List<Circular> = emptyList(),
    @SerializedName("totalRecords") val totalRecords: Int = 0
)

data class Circular(
    @SerializedName("_id") val id: String,
    @SerializedName("status") val status: String? = null,
    @SerializedName("date") val date: String? = null,
    @SerializedName("tagId") val tagId: String? = null,
    @SerializedName("title") val title: String? = null,
    @SerializedName("ref") val ref: String? = null,
    @SerializedName("tag") val tag: String? = null
)

data class CircularDetail(
    @SerializedName("_id") val id: String,
    @SerializedName("status") val status: String? = null,
    @SerializedName("date") val date: String? = null,
    @SerializedName("title") val title: String? = null,
    @SerializedName("ref") val ref: String? = null,
    @SerializedName("tag") val tag: String? = null,
    @SerializedName("content") val content: String? = null,
    @SerializedName("attachments") val attachments: List<CircularAttachment> = emptyList()
)

data class CircularAttachment(
    @SerializedName("url") val url: String? = null,
    @SerializedName("originalname") val originalName: String? = null,
    @SerializedName("contentType") val contentType: String? = null
)

data class SignedUrlResponse(
    @SerializedName("signedUrl") val signedUrl: String? = null
)
