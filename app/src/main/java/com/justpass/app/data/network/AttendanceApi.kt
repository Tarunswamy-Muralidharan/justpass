package com.justpass.app.data.network

import com.justpass.app.data.model.AttendanceResponse
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Path

interface AttendanceApi {

    @GET("sis/attendance/{rollNumber}")
    suspend fun getAttendance(
        @Path("rollNumber") rollNumber: String,
        @Header("Authorization") authorization: String,
        @Header("Origin") origin: String = "https://laudea.psgitech.ac.in",
        @Header("Referer") referer: String = "https://laudea.psgitech.ac.in/sis/"
    ): Response<AttendanceResponse>
}
