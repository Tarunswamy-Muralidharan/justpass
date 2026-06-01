package com.justpass.app

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.justpass.app.data.local.SecurePreferences
import com.justpass.app.data.repository.AttendanceRepository
import com.justpass.app.ui.viewmodel.DashboardUiState
import com.justpass.app.ui.viewmodel.DashboardViewModel
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import org.junit.After
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
@Config(sdk = [28])
class DashboardViewModelTest {

    @Before
    fun setup() {
        mockkObject(SecurePreferences)
        mockkObject(AttendanceRepository)
        every { SecurePreferences.getInstance(any()) } returns mockk(relaxed = true)
        every { AttendanceRepository.getInstance(any()) } returns mockk(relaxed = true)

        mockkStatic(FirebaseRemoteConfig::class)
        every { FirebaseRemoteConfig.getInstance() } returns mockk(relaxed = true)
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    private fun createMockApp(): Application {
        val app = mockk<Application>(relaxed = true)
        val prefs = mockk<SharedPreferences>(relaxed = true)
        val editor = mockk<SharedPreferences.Editor>(relaxed = true)
        every { app.applicationContext } returns app
        every { app.getSharedPreferences("laudea_prefs", Context.MODE_PRIVATE) } returns prefs
        every { prefs.edit() } returns editor
        return app
    }

    private fun createVmWithSessions(sessions: List<Int>): DashboardViewModel {
        val vm = DashboardViewModel(createMockApp())
        val stateField: Field = DashboardViewModel::class.java.getDeclaredField("_uiState")
        stateField.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val stateFlow = stateField.get(vm) as kotlinx.coroutines.flow.MutableStateFlow<DashboardUiState>
        val current = stateFlow.value
        stateFlow.value = current.copy(sessionsPerDay = sessions)
        return vm
    }

    @Test
    fun `calculateLeaveHours returns 0 for zero days`() {
        val vm = createVmWithSessions(List(6) { 6 })
        val method: Method = DashboardViewModel::class.java.getDeclaredMethod(
            "calculateLeaveHours", LocalDate::class.java, Int::class.java, Map::class.java
        )
        method.isAccessible = true
        val result = method.invoke(vm, LocalDate.of(2026, 6, 1), 0, emptyMap<LocalDate, String>()) as Int
        Assert.assertEquals(0, result)
    }

    @Test
    fun `calculateLeaveHours skips Sundays and holidays`() {
        val vm = createVmWithSessions(List(6) { 6 })
        val method: Method = DashboardViewModel::class.java.getDeclaredMethod(
            "calculateLeaveHours", LocalDate::class.java, Int::class.java, Map::class.java
        )
        method.isAccessible = true
        val start = LocalDate.of(2026, 6, 1) // Monday
        val holidays = mapOf(LocalDate.of(2026, 6, 2) to "Holiday")
        val result = method.invoke(vm, start, 3, holidays) as Int
        // Mon(6) + Wed(6) + Thu(6) = 18 (skipped Tue holiday)
        Assert.assertEquals(18, result)
    }

    @Test
    fun `getHoursForDate returns 0 on Sunday`() {
        val vm = createVmWithSessions(List(6) { 6 })
        val method: Method = DashboardViewModel::class.java.getDeclaredMethod("getHoursForDate", LocalDate::class.java)
        method.isAccessible = true
        val sunday = LocalDate.of(2026, 6, 7)
        val result = method.invoke(vm, sunday) as Int
        Assert.assertEquals(0, result)
    }

    @Test
    fun `getHoursForDate returns 0 on holiday`() {
        val vm = createVmWithSessions(List(6) { 6 })
        val stateField: Field = DashboardViewModel::class.java.getDeclaredField("_uiState")
        stateField.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val stateFlow = stateField.get(vm) as kotlinx.coroutines.flow.MutableStateFlow<DashboardUiState>
        val current = stateFlow.value
        val monday = LocalDate.of(2026, 6, 1)
        stateFlow.value = current.copy(holidays = mapOf(monday to "Holiday"))

        val method: Method = DashboardViewModel::class.java.getDeclaredMethod("getHoursForDate", LocalDate::class.java)
        method.isAccessible = true
        val result = method.invoke(vm, monday) as Int
        Assert.assertEquals(0, result)
    }

    @Test
    fun `getHoursForDate returns sessions for working day`() {
        val vm = createVmWithSessions(listOf(5, 6, 7, 8, 6, 4))
        val method: Method = DashboardViewModel::class.java.getDeclaredMethod("getHoursForDate", LocalDate::class.java)
        method.isAccessible = true
        val monday = LocalDate.of(2026, 6, 1)
        val result = method.invoke(vm, monday) as Int
        Assert.assertEquals(5, result)
    }

    @Test
    fun `calculateHoursForDates sums correctly skipping Sundays and holidays`() {
        val vm = createVmWithSessions(List(6) { 6 })
        val method: Method = DashboardViewModel::class.java.getDeclaredMethod("calculateHoursForDates", Set::class.java)
        method.isAccessible = true
        val dates = setOf(
            LocalDate.of(2026, 6, 1), // Monday
            LocalDate.of(2026, 6, 2), // Tuesday
            LocalDate.of(2026, 6, 7)  // Sunday - skipped
        )
        val result = method.invoke(vm, dates) as Int
        Assert.assertEquals(12, result)
    }

    @Test
    fun `getWorkingDaysInLeaveRange returns correct dates`() {
        val vm = createVmWithSessions(List(6) { 6 })
        val method: Method = DashboardViewModel::class.java.getDeclaredMethod(
            "getWorkingDaysInLeaveRange", LocalDate::class.java, Int::class.java, Map::class.java
        )
        method.isAccessible = true
        val start = LocalDate.of(2026, 6, 1)
        val result = method.invoke(vm, start, 2, emptyMap<LocalDate, String>()) as List<LocalDate>
        Assert.assertEquals(2, result.size)
        Assert.assertEquals(LocalDate.of(2026, 6, 1), result[0])
        Assert.assertEquals(LocalDate.of(2026, 6, 2), result[1])
    }

    @Test
    fun `getWorkingDaysInLeaveRange skips Sunday but includes Saturday`() {
        val vm = createVmWithSessions(List(6) { 6 })
        val method: Method = DashboardViewModel::class.java.getDeclaredMethod(
            "getWorkingDaysInLeaveRange", LocalDate::class.java, Int::class.java, Map::class.java
        )
        method.isAccessible = true
        val start = LocalDate.of(2026, 6, 5) // Friday
        val result = method.invoke(vm, start, 3, emptyMap<LocalDate, String>()) as List<LocalDate>
        // The logic only skips Sunday, not Saturday
        // Fri(5) -> Sat(6) -> Mon(8) = 3 working days (Sun skipped)
        Assert.assertEquals(3, result.size)
        Assert.assertTrue(result.contains(LocalDate.of(2026, 6, 6))) // Saturday included
        Assert.assertFalse(result.contains(LocalDate.of(2026, 6, 7))) // Sunday skipped
    }
}
