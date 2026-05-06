package com.justpass.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.justpass.app.data.model.CalendarEvent
import com.justpass.app.data.model.CalendarEventType
import com.google.gson.Gson
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.URL
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter

data class CalendarUiState(
    val isLoading: Boolean = false,
    val events: List<CalendarEvent> = emptyList(),
    val selectedMonth: YearMonth = YearMonth.now(),
    val errorMessage: String? = null
)

class CalendarViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(CalendarUiState())
    val uiState: StateFlow<CalendarUiState> = _uiState.asStateFlow()

    private val gson = Gson()

    companion object {
        private const val CALENDAR_ID = "c_f65646ec47f509e6a093824790c28766188222d525707dfb817f80ac21e9e24c%40group.calendar.google.com"
        private const val API_KEY = "AIzaSyBNlYH01_9Hc5S1J9vuFmu2nUqBZJNAXxs"
    }

    init {
        fetchEvents()
    }

    fun fetchEvents() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            try {
                val events = withContext(Dispatchers.IO) { fetchCalendarEvents() }
                _uiState.value = _uiState.value.copy(isLoading = false, events = events)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = "Failed to load calendar"
                )
            }
        }
    }

    fun selectMonth(month: YearMonth) {
        _uiState.value = _uiState.value.copy(selectedMonth = month)
    }

    fun previousMonth() {
        val m = _uiState.value.selectedMonth.minusMonths(1)
        _uiState.value = _uiState.value.copy(selectedMonth = m)
        com.justpass.app.data.analytics.Analytics.logCalendarMonthViewed(m.monthValue, m.year)
    }

    fun nextMonth() {
        val m = _uiState.value.selectedMonth.plusMonths(1)
        _uiState.value = _uiState.value.copy(selectedMonth = m)
        com.justpass.app.data.analytics.Analytics.logCalendarMonthViewed(m.monthValue, m.year)
    }

    private fun fetchCalendarEvents(): List<CalendarEvent> {
        val now = LocalDate.now()
        val timeMin = now.minusMonths(6).withDayOfMonth(1).format(DateTimeFormatter.ISO_LOCAL_DATE) + "T00:00:00Z"
        val timeMax = now.plusMonths(6).withDayOfMonth(1).format(DateTimeFormatter.ISO_LOCAL_DATE) + "T00:00:00Z"

        val url = "https://www.googleapis.com/calendar/v3/calendars/$CALENDAR_ID/events" +
                "?key=$API_KEY&timeMin=$timeMin&timeMax=$timeMax" +
                "&singleEvents=true&orderBy=startTime&maxResults=200"

        val response = URL(url).readText()
        val json = gson.fromJson(response, JsonObject::class.java)
        val items = json.getAsJsonArray("items") ?: return emptyList()

        return items.mapNotNull { item ->
            val obj = item.asJsonObject
            val summary = obj.get("summary")?.asString ?: return@mapNotNull null
            val start = obj.getAsJsonObject("start")
            val end = obj.getAsJsonObject("end")
            val startDate = start?.get("date")?.asString
                ?: start?.get("dateTime")?.asString?.substring(0, 10)
                ?: return@mapNotNull null
            val endDate = end?.get("date")?.asString
                ?: end?.get("dateTime")?.asString?.substring(0, 10)

            CalendarEvent(
                id = obj.get("id")?.asString ?: "",
                summary = summary,
                startDate = startDate,
                endDate = endDate,
                eventType = CalendarEventType.fromSummary(summary)
            )
        }
    }
}
