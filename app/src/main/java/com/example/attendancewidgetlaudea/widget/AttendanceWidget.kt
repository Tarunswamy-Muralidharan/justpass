package com.example.attendancewidgetlaudea.widget

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.attendancewidgetlaudea.MainActivity
import com.example.attendancewidgetlaudea.data.local.SecurePreferences

class AttendanceWidget : GlanceAppWidget() {

    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val prefs = SecurePreferences.getInstance(context)
        val attendanceData = prefs.getAttendanceData()
        val isLoggedIn = prefs.isLoggedIn()

        provideContent {
            AttendanceWidgetContent(
                presentCount = attendanceData.presentCount,
                absentCount = attendanceData.absentCount,
                percentage = attendanceData.attendanceWithExemption,
                isLoggedIn = isLoggedIn
            )
        }
    }
}

// Colors
private val DarkBackground = Color(0xFF1A1A1A)
private val DotInactive = Color(0xFF3D3D3D)
private val DotActive = Color(0xFFFF3B30)  // Red like in the reference
private val TextWhite = Color(0xFFFFFFFF)
private val TextGray = Color(0xFF8E8E8E)

@Composable
private fun AttendanceWidgetContent(
    presentCount: Int,
    absentCount: Int,
    percentage: Double,
    isLoggedIn: Boolean
) {
    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(ColorProvider(DarkBackground))
            .cornerRadius(24.dp)
            .clickable(actionStartActivity<MainActivity>())
            .padding(16.dp)
    ) {
        if (!isLoggedIn) {
            NotLoggedInContent()
        } else {
            LoggedInContent(
                presentCount = presentCount,
                absentCount = absentCount,
                percentage = percentage
            )
        }
    }
}

@Composable
private fun NotLoggedInContent() {
    Column(
        modifier = GlanceModifier.fillMaxSize(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "TAP TO LOGIN",
            style = TextStyle(
                fontWeight = FontWeight.Medium,
                fontSize = 14.sp,
                color = ColorProvider(TextGray)
            )
        )
    }
}

@Composable
private fun LoggedInContent(
    presentCount: Int,
    absentCount: Int,
    percentage: Double
) {
    Column(
        modifier = GlanceModifier.fillMaxSize(),
        verticalAlignment = Alignment.Top,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Dot Matrix Progress Indicator
        DotMatrixProgress(percentage = percentage)

        Spacer(modifier = GlanceModifier.height(16.dp))

        // Main percentage display - large number
        Row(
            verticalAlignment = Alignment.Bottom,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "${percentage.toInt()}",
                style = TextStyle(
                    fontWeight = FontWeight.Bold,
                    fontSize = 48.sp,
                    color = ColorProvider(TextWhite)
                )
            )
            Text(
                text = "%",
                style = TextStyle(
                    fontWeight = FontWeight.Normal,
                    fontSize = 24.sp,
                    color = ColorProvider(TextGray)
                ),
                modifier = GlanceModifier.padding(bottom = 6.dp)
            )
        }

        Spacer(modifier = GlanceModifier.height(4.dp))

        // Label
        Text(
            text = "ATTENDANCE",
            style = TextStyle(
                fontWeight = FontWeight.Medium,
                fontSize = 12.sp,
                color = ColorProvider(TextGray)
            )
        )

        Spacer(modifier = GlanceModifier.defaultWeight())

        // Present/Absent at bottom
        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "P: $presentCount",
                style = TextStyle(
                    fontSize = 11.sp,
                    color = ColorProvider(Color(0xFF4CD964))
                )
            )
            Spacer(modifier = GlanceModifier.width(16.dp))
            Text(
                text = "A: $absentCount",
                style = TextStyle(
                    fontSize = 11.sp,
                    color = ColorProvider(Color(0xFFFF3B30))
                )
            )
        }

        // Refresh button
        Spacer(modifier = GlanceModifier.height(8.dp))
        Box(
            modifier = GlanceModifier
                .cornerRadius(8.dp)
                .background(ColorProvider(Color(0xFF2D2D2D)))
                .clickable(actionRunCallback<RefreshWidgetAction>())
                .padding(horizontal = 12.dp, vertical = 4.dp)
        ) {
            Text(
                text = "↻ Refresh",
                style = TextStyle(
                    fontSize = 10.sp,
                    color = ColorProvider(TextGray)
                )
            )
        }
    }
}

@Composable
private fun DotMatrixProgress(percentage: Double) {
    // Create a 10x3 dot matrix (30 dots total)
    // Fill dots based on percentage
    val totalDots = 30
    val filledDots = ((percentage / 100.0) * totalDots).toInt().coerceIn(0, totalDots)

    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Row 1
        DotRow(startIndex = 0, filledDots = filledDots)
        Spacer(modifier = GlanceModifier.height(4.dp))
        // Row 2
        DotRow(startIndex = 10, filledDots = filledDots)
        Spacer(modifier = GlanceModifier.height(4.dp))
        // Row 3
        DotRow(startIndex = 20, filledDots = filledDots)
    }
}

@Composable
private fun DotRow(startIndex: Int, filledDots: Int) {
    Row(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        for (i in 0 until 10) {
            val dotIndex = startIndex + i
            val isActive = dotIndex < filledDots
            Dot(isActive = isActive)
            if (i < 9) {
                Spacer(modifier = GlanceModifier.width(4.dp))
            }
        }
    }
}

@Composable
private fun Dot(isActive: Boolean) {
    Box(
        modifier = GlanceModifier
            .size(8.dp)
            .cornerRadius(4.dp)
            .background(ColorProvider(if (isActive) DotActive else DotInactive)),
        contentAlignment = Alignment.Center
    ) {}
}
