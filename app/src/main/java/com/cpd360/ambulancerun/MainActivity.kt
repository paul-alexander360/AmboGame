package com.cpd360.ambulancerun

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val version = packageManager.getPackageInfo(packageName, 0).versionName ?: "?"
        setContent { HomeScreen(version) }
    }
}

@Composable
private fun Chip(label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .background(Color(0xE6FFFFFF), RoundedCornerShape(16.dp))
            .border(2.dp, Color(0xFF7C80C8), RoundedCornerShape(16.dp))
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 7.dp)
    ) {
        Text(label, fontSize = 13.sp)
    }
}

@Composable
fun HomeScreen(version: String) {
    val rs = remember { RouteState() }
    val fast = remember(rs.version, rs.stage) { rs.fastest() }
    val par = fast * 1.6
    var screen by remember { mutableIntStateOf(0) }
    if (screen == 1) {
        ExamScreen {
            rs.startHospital()
            screen = 0
        }
        return
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF9BD468))
    ) {
        CityMap(rs, Modifier.fillMaxSize())
        Row(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .safeDrawingPadding()
                .padding(8.dp)
                .background(Color(0xEBFF6A3D), RoundedCornerShape(24.dp))
                .padding(horizontal = 18.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "City " + rs.level + "." + (rs.idx + 1) + (if (rs.stage == 0) ": call" else ": hospital"),
                color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold
            )
            Box(
                modifier = Modifier
                    .background(Color(0xFFFFF3E0), RoundedCornerShape(12.dp))
                    .padding(horizontal = 10.dp, vertical = 3.dp)
            ) {
                Text("Par " + fmt(par), color = Color(0xFFBF360C), fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
        }
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .safeDrawingPadding()
                .padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (!rs.done) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xF2FFFFFF), RoundedCornerShape(20.dp))
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        if (rs.stage == 0) "Drag from the red station to the glowing building. Pinch to zoom, drag elsewhere to move the map."
                        else "Drag from the ambulance to a glowing hospital. Pinch to zoom, drag elsewhere to move the map.",
                        fontSize = 15.sp
                    )
                }
            } else {
                val user = rs.city.pathTime(rs.path.toList())
                val wrong = rs.wrongHospital()
                val failed = user > par || wrong
                val rt = rating(user, fast)
                val names = listOf("Green", "Yellow", "Orange", "Red")
                val cols = listOf(Color(0xFF4DC43B), Color(0xFFFFD54F), Color(0xFFFF9800), Color(0xFFE53935))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xF2FFFFFF), RoundedCornerShape(20.dp))
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        if (wrong) "Wrong hospital! Call failed" else if (user > par) "Too slow! Call failed" else "Route complete",
                        fontSize = 18.sp, fontWeight = FontWeight.Bold
                    )
                    if (rs.stage == 1) {
                        Text("To the call: " + fmt(rs.leg1User) + "   Fastest " + fmt(rs.leg1Fast), fontSize = 14.sp)
                    }
                    Text(
                        (if (rs.stage == 1) "To the hospital: " else "Your time ") + fmt(user) + "   Fastest " + fmt(fast),
                        fontSize = 14.sp
                    )
                    Spacer(Modifier.height(8.dp))
                    Box(
                        modifier = Modifier
                            .background(cols[rt], RoundedCornerShape(16.dp))
                            .padding(horizontal = 16.dp, vertical = 6.dp)
                    ) {
                        Text(names[rt] + " route", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    }
                    if (!failed && rs.stage == 0) {
                        Spacer(Modifier.height(10.dp))
                        Box(
                            modifier = Modifier
                                .background(Color(0xFF4DC43B), RoundedCornerShape(16.dp))
                                .border(2.dp, Color(0xFFF6A12B), RoundedCornerShape(16.dp))
                                .clickable { screen = 1 }
                                .padding(horizontal = 22.dp, vertical = 10.dp)
                        ) {
                            Text("Treat patient", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (rs.done) Chip("Test again") { rs.reset() }
                Chip("Next city (test)") { rs.nextCity() }
            }
            Spacer(Modifier.height(6.dp))
            Text("Version $version", color = Color.White, fontSize = 12.sp)
        }
    }
}
