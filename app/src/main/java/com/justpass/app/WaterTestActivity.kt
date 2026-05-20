package com.justpass.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.justpass.app.ui.components.water.drawWater
import com.justpass.app.ui.components.water.rememberWaterState

/**
 * Standalone water sandbox. Launch with:
 *   adb shell am start -n com.justpass.app/.WaterTestActivity
 *
 * Full-screen water tank + a single fill-level slider. Used to iterate on
 * the WaterPhysics + drawWater pipeline in isolation, without the rest of
 * the dashboard fighting for redraws/recomposition. When the playground
 * looks good, port the values back to DashboardScreen.
 */
class WaterTestActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { WaterTestScreen() }
    }
}

@Composable
private fun WaterTestScreen() {
    var fillPct by remember { mutableFloatStateOf(75f) }
    val scroll = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0B1726))
            .systemBarsPadding()
            .verticalScroll(scroll)
            .padding(16.dp)
    ) {
        Text(
            "Water Playground",
            color = Color.White,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(4.dp))
        Text(
            "fill: ${"%.1f".format(fillPct)}%",
            color = Color(0xFFB3E5FC),
            fontSize = 14.sp,
            fontFamily = FontFamily.Monospace
        )
        Spacer(Modifier.height(12.dp))

        // Big tank — water lives in here, full width, fills most of the
        // screen so any glitching is highly visible to the camera. Pass
        // scroll offset so fast scrolls/flings produce a visible slosh.
        val water = rememberWaterState(
            fillFraction = fillPct / 100f,
            scrollOffsetPx = scroll.value.toFloat(),
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(440.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(Color(0xFF132435))
                .drawBehind { drawWater(water) }
        )

        Spacer(Modifier.height(20.dp))
        Text(
            "fill",
            color = Color(0xFF8FB3D9),
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace
        )
        Slider(
            value = fillPct,
            onValueChange = { fillPct = it },
            valueRange = 0f..100f,
            steps = 99
        )

        Spacer(Modifier.height(16.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "shake the phone — slosh kicks via accelerometer jerk",
                color = Color(0xFF8FB3D9),
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace
            )
        }

        // Spacer to give the screen something to scroll. Fling/scroll the
        // page → water sloshes via scrollOffsetPx delta in WaterFill.
        Spacer(Modifier.height(900.dp))
        Text(
            "scroll up/down — water sloshes from scroll delta",
            color = Color(0xFF8FB3D9),
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace
        )
        Spacer(Modifier.height(800.dp))
        Text(
            "(end of scroll)",
            color = Color(0xFF8FB3D9),
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace
        )
    }
}
