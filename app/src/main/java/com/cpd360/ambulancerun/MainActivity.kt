package com.cpd360.ambulancerun

import android.os.Bundle
import kotlinx.coroutines.delay
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
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
import androidx.compose.runtime.LaunchedEffect
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
        val player = PlayerData.load(this)
        setContent { HomeScreen(version, player) }
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
private fun NavBtn(kind: Int, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(48.dp)
            .background(Color(0xE6FFFFFF), CircleShape)
            .border(2.dp, Color(0xFF7C80C8), CircleShape)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.size(28.dp)) {
            val s = size.width
            val c = Offset(s * 0.5f, s * 0.5f)
            val r = CornerRadius(s * 0.12f, s * 0.12f)
            when (kind) {
                0 -> {
                    drawRoundRect(Color(0xFFE53935), Offset(s * 0.1f, s * 0.12f), Size(s * 0.8f, s * 0.78f), r)
                    val sc = Offset(s * 0.5f, s * 0.46f)
                    drawCircle(Color.White, s * 0.34f, sc)
                    val rr = s * 0.27f
                    val bw = rr * 0.51f
                    for (a in floatArrayOf(0f, 60f, 120f)) {
                        rotate(a, sc) {
                            drawRect(Color(0xFF0A72B8), Offset(sc.x - bw / 2f, sc.y - rr), Size(bw, 2f * rr))
                        }
                    }
                    drawRoundRect(Color.White, Offset(sc.x - rr * 0.07f, sc.y - rr * 0.85f), Size(rr * 0.14f, rr * 1.7f), CornerRadius(rr * 0.07f, rr * 0.07f))
                    val sn = Path()
                    sn.moveTo(sc.x - rr * 0.2f, sc.y - rr * 0.6f)
                    sn.cubicTo(sc.x + rr * 0.4f, sc.y - rr * 0.5f, sc.x + rr * 0.4f, sc.y - rr * 0.1f, sc.x, sc.y - rr * 0.05f)
                    sn.cubicTo(sc.x - rr * 0.4f, sc.y, sc.x - rr * 0.4f, sc.y + rr * 0.4f, sc.x + rr * 0.2f, sc.y + rr * 0.5f)
                    drawPath(sn, Color.White, style = Stroke(rr * 0.12f, cap = StrokeCap.Round))
                }
                1 -> {
                    drawCircle(Color(0x553FA2FF), s * 0.48f, c)
                    drawCircle(Color(0xFF3FA2FF), s * 0.34f, c)
                    drawRoundRect(Color.White, Offset(s * 0.36f, s * 0.46f), Size(s * 0.28f, s * 0.22f), CornerRadius(s * 0.03f, s * 0.03f))
                    val roof = Path()
                    roof.moveTo(s * 0.32f, s * 0.47f)
                    roof.lineTo(s * 0.5f, s * 0.3f)
                    roof.lineTo(s * 0.68f, s * 0.47f)
                    roof.close()
                    drawPath(roof, Color.White)
                }
                else -> {
                    drawRoundRect(Color.White, Offset(s * 0.1f, s * 0.12f), Size(s * 0.8f, s * 0.78f), r)
                    drawRoundRect(Color(0xFFB8BDE6), Offset(s * 0.1f, s * 0.12f), Size(s * 0.8f, s * 0.78f), r, style = Stroke(s * 0.04f))
                    drawRoundRect(Color(0xFFE53935), Offset(s * 0.42f, s * 0.26f), Size(s * 0.16f, s * 0.5f), CornerRadius(s * 0.02f, s * 0.02f))
                    drawRoundRect(Color(0xFFE53935), Offset(s * 0.25f, s * 0.43f), Size(s * 0.5f, s * 0.16f), CornerRadius(s * 0.02f, s * 0.02f))
                }
            }
        }
    }
}

@Composable
fun HomeScreen(version: String, player: PlayerData) {
    val rs = remember { RouteState(player) }
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
        val pop = rs.popup
        if (pop != null) {
            LaunchedEffect(pop) {
                delay(4000)
                rs.popup = null
            }
            Box(Modifier.align(Alignment.Center)) {
                HospitalPopup(pop) { rs.popup = null }
            }
        }
        Column(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            NavBtn(0) { rs.focusStation() }
            NavBtn(1) { rs.focusCall() }
            NavBtn(2) { rs.focusHospital() }
        }
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
                "City " + rs.level + "." + (rs.idx + 1) + ", run " + rs.run + " of 5",
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
                        if (rs.stage == 0) "Drag from the red station to the glowing building. Pinch to zoom, drag with two fingers to move the map."
                        else "Drag from the ambulance to the right hospital. Double tap a hospital to see what it treats.",
                        fontSize = 15.sp
                    )
                }
            } else {
                val user = rs.city.pathTime(rs.path.toList())
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
                        if (rs.failReason != "") "Run failed: " + rs.failReason else if (rs.runOver) "Run complete" else "Route complete",
                        fontSize = 18.sp, fontWeight = FontWeight.Bold
                    )
                    if (rs.stage == 1) {
                        Text("To the call: " + fmt(rs.leg1User) + "   Fastest " + fmt(rs.leg1Fast), fontSize = 14.sp)
                    }
                    if (rs.accSeg < 0) {
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
                    }
                    if (rs.runOver) {
                        Spacer(Modifier.height(8.dp))
                        Text("Credits earned: +" + rs.earned, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        Text("Level " + player.level + "   Credits " + player.credits, fontSize = 13.sp)
                        Spacer(Modifier.height(10.dp))
                        Box(
                            modifier = Modifier
                                .background(Color(0xFF4DC43B), RoundedCornerShape(16.dp))
                                .border(2.dp, Color(0xFFF6A12B), RoundedCornerShape(16.dp))
                                .clickable { rs.nextRun() }
                                .padding(horizontal = 22.dp, vertical = 10.dp)
                        ) {
                            Text("Next run", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        }
                    } else if (rs.stage == 0) {
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
            Text(
                "Level " + player.level + "  Drive " + "%.3f".format(player.drive) + "  Avoid " + "%.2f".format(player.avoidance) +
                    "  Dodge " + Math.round(player.dodge * 100).toInt() + "%",
                color = Color.White, fontSize = 12.sp
            )
            Text("Version $version", color = Color.White, fontSize = 12.sp)
        }
    }
}
