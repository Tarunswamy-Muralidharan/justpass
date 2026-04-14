package com.example.attendancewidgetlaudea.data.network

import com.example.attendancewidgetlaudea.data.model.TokenResponse
import retrofit2.Response
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.POST

interface AuthApi {

    @FormUrlEncoded
    @POST("realms/psgitech/protocol/openid-connect/token")
    suspend fun login(
        @Field("grant_type") grantType: String = "password",
        @Field("client_id") clientId: String = "ies_sis",
        @Field("username") username: String,
        @Field("password") password: String
    ): Response<TokenResponse>

    @FormUrlEncoded
    @POST("realms/psgitech/protocol/openid-connect/token")
    suspend fun refreshToken(
        @Field("grant_type") grantType: String = "refresh_token",
        @Field("client_id") clientId: String = "ies_sis",
        @Field("refresh_token") refreshToken: String
    ): Response<TokenResponse>
}
