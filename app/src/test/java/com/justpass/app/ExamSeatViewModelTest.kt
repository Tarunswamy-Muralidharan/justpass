package com.justpass.app

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.justpass.app.data.local.SecurePreferences
import com.justpass.app.ui.viewmodel.ExamSeatViewModel
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import org.junit.After
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.lang.reflect.Method

@RunWith(AndroidJUnit4::class)
@Config(sdk = [28])
class ExamSeatViewModelTest {

    @Before
    fun setup() {
        mockkObject(SecurePreferences)
        every { SecurePreferences.getInstance(any()) } returns mockk(relaxed = true) {
            every { rollNumber } returns "22CS101"
        }
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

    private fun invokeParseExamInfoFromFilename(vm: ExamSeatViewModel, fileName: String): Pair<String, String> {
        val method: Method = ExamSeatViewModel::class.java.getDeclaredMethod("parseExamInfoFromFilename", String::class.java)
        method.isAccessible = true
        return method.invoke(vm, fileName) as Pair<String, String>
    }

    private fun invokeNormalizeRollNumber(vm: ExamSeatViewModel, raw: String): String {
        val method: Method = ExamSeatViewModel::class.java.getDeclaredMethod("normalizeRollNumber", String::class.java)
        method.isAccessible = true
        return method.invoke(vm, raw) as String
    }

    @Test
    fun `parseExamInfoFromFilename extracts session and date`() {
        val vm = ExamSeatViewModel(createMockApp())
        val result = invokeParseExamInfoFromFilename(vm, "3 Yr 03-02 FN-II.xlsx")
        Assert.assertEquals("Session: FN-II", result.first)
        // dateTime contains the parsed date + session time range (FN-II = 10:45 AM - 12:30 PM)
        Assert.assertTrue(result.second.contains("10:45 AM"))
        // Ambiguous 03-02 treated as MM-DD => 2 Mar
        Assert.assertTrue(result.second.contains("Mar"))
    }

    @Test
    fun `parseExamInfoFromFilename handles ambiguous date as MM-DD`() {
        val vm = ExamSeatViewModel(createMockApp())
        val result = invokeParseExamInfoFromFilename(vm, "2 Yr 04-01 AN-I.xls")
        Assert.assertTrue(result.second.contains("1 Apr") || result.second.contains("Apr"))
    }

    @Test
    fun `parseExamInfoFromFilename handles DD-MM when first part exceeds 12`() {
        val vm = ExamSeatViewModel(createMockApp())
        val result = invokeParseExamInfoFromFilename(vm, "2 Yr 15-03 FN.xls")
        Assert.assertTrue(result.second.contains("15 Mar"))
    }

    @Test
    fun `parseExamInfoFromFilename handles no date gracefully`() {
        val vm = ExamSeatViewModel(createMockApp())
        val result = invokeParseExamInfoFromFilename(vm, "exam_schedule.xlsx")
        Assert.assertEquals("", result.first)
        Assert.assertEquals("", result.second)
    }

    @Test
    fun `normalizeRollNumber strips spaces dots dashes and unicode`() {
        val vm = ExamSeatViewModel(createMockApp())
        Assert.assertEquals("22CS101", invokeNormalizeRollNumber(vm, "22CS101"))
        Assert.assertEquals("22CS101", invokeNormalizeRollNumber(vm, "22 CS 101"))
        Assert.assertEquals("22CS101", invokeNormalizeRollNumber(vm, "22.CS.101"))
        Assert.assertEquals("22CS101", invokeNormalizeRollNumber(vm, "22-CS-101"))
        Assert.assertEquals("22CS101", invokeNormalizeRollNumber(vm, "22\u00A0CS\u200B101"))
    }
}
