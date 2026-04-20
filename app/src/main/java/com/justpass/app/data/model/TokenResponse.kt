package com.justpass.app.data.model

import com.google.gson.annotations.SerializedName

data class TokenResponse(
    @SerializedName("access_token")
    val accessToken: String,

    @SerializedName("refresh_token")
    val refreshToken: String,

    @SerializedName("expires_in")
    val expiresIn: Long,

    @SerializedName("refresh_expires_in")
    val refreshExpiresIn: Long? = null,

    @SerializedName("token_type")
    val tokenType: String? = null,

    @SerializedName("scope")
    val scope: String? = null
)
