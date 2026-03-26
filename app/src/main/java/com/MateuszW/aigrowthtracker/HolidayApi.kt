package com.MateuszW.aigrowthtracker

import retrofit2.http.GET
import retrofit2.http.Query

data class HolidayResponse(
    val response: HolidaysData
)

data class HolidaysData(
    val holidays: List<Holiday>
)

data class Holiday(
    val name: String,
    val date: HolidayDate,
    val type: List<String>
)

data class HolidayDate(
    val iso: String
)

interface HolidayService {
    @GET("holidays")
    suspend fun getHolidays(
        @Query("api_key") key: String,
        @Query("country") country: String = "PL",
        @Query("year") year: Int
    ): HolidayResponse
}