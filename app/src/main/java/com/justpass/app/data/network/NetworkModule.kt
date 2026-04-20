package com.justpass.app.data.network

import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object NetworkModule {

    private const val AUTH_BASE_URL = "https://accounts.psgitech.ac.in/"
    private const val SIS_BASE_URL = "https://laudea.psgitech.ac.in/"

    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
    }

    private val okHttpClient = OkHttpClient.Builder()
        .addInterceptor(loggingInterceptor)
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val authRetrofit: Retrofit = Retrofit.Builder()
        .baseUrl(AUTH_BASE_URL)
        .client(okHttpClient)
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    private val sisRetrofit: Retrofit = Retrofit.Builder()
        .baseUrl(SIS_BASE_URL)
        .client(okHttpClient)
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    val authApi: AuthApi = authRetrofit.create(AuthApi::class.java)
    val attendanceApi: AttendanceApi = sisRetrofit.create(AttendanceApi::class.java)
}
