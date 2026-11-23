package com.cpd360.ambulancerun

import android.os.Bundle
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.mutableStateOf
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
    var menu by remember { mutableStateOf(false) }
    when (screen) {
        1 -> {
            ExamScreen(rs.patient, rs.level) {
                rs.examDone()
                screen = 0
            }
            return
        }
        2 -> {
            AmbulanceScreen(player) {
                rs.applyPlayer()
                screen = 0
            }
            return
        }
        3 -> {
            CharacterScreen(player) {
                rs.applyPlayer()
                screen = 0
            }
            return
        }
        4 -> {
            SettingsScreen(player, version) { screen = 0 }
            return
        }
        5 -> {
            DriveScene(
                city = rs.city,
                nodes = remember(rs.version, rs.stage) { rs.driveNodes() },
                start = rs.startPt(),
                end = rs.driveEnd(),
                toHospital = rs.stage == 1,
                destKey = rs.destKey(),
                crash = rs.accSeg >= 0,
                sirenOn = player.sirenSound
            ) {
                rs.driveShown = true
                screen = 0
            }
            return
        }
    }
    LaunchedEffect(rs.done, rs.stage) {
        if (rs.done && !rs.driveShown && player.driveScenes && rs.path.size > 0) screen = 5
        else if (rs.done) rs.driveShown = true
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
        Row(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .safeDrawingPadding()
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            MenuButton { menu = true }
            Spacer(Modifier.width(8.dp))
            Row(
                modifier = Modifier
                    .weight(1f)
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
        if (menu) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color(0x66000000))
                    .clickable { menu = false }
            )
            Box(
                Modifier
                    .align(Alignment.TopStart)
                    .safeDrawingPadding()
                    .padding(start = 8.dp, top = 64.dp)
            ) {
                MenuPanel { pick ->
                    menu = false
                    when (pick) {
                        0 -> screen = 2
                        1 -> screen = 3
                        2 -> screen = 4
                        3 -> rs.focusStation()
                        4 -> rs.focusCall()
                        5 -> rs.focusHospital()
                    }
                }
            }
        }
    }
}

@Composable
private fun MenuButton(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(48.dp)
            .background(Color(0xEBFF6A3D), RoundedCornerShape(16.dp))
            .border(2.dp, Color(0xFFFFFFFF), RoundedCornerShape(16.dp))
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.size(24.dp)) {
            val w = size.width
            for (k in 0..2) {
                val y = w * (0.2f + 0.3f * k)
                drawLine(Color.White, Offset(w * 0.1f, y), Offset(w * 0.9f, y), w * 0.13f, StrokeCap.Round)
            }
        }
    }
}

@Composable
private fun MenuItem(label: String, icon: @Composable () -> Unit, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .width(230.dp)
            .clip(RoundedCornerShape(14.dp))
            .clickable { onClick() }
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) { icon() }
        Spacer(Modifier.width(12.dp))
        Text(label, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color(0xFF2B313A))
    }
}

@Composable
private fun MenuIcon(draw: androidx.compose.ui.graphics.drawscope.DrawScope.() -> Unit) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Brush.verticalGradient(listOf(Color(0xFF9BF076), Color(0xFF4DC43B))))
            .border(2.dp, Color(0xFFF6A12B), RoundedCornerShape(12.dp)),
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.size(34.dp)) { draw() }
    }
}

@Composable
private fun MenuPanel(onPick: (Int) -> Unit) {
    Column(
        modifier = Modifier
            .background(Color.White, RoundedCornerShape(20.dp))
            .border(3.dp, Color(0xFFFF6A3D), RoundedCornerShape(20.dp))
            .padding(8.dp)
    ) {
        MenuItem("Ambulance", {
            MenuIcon {
                val sc = size.width / 1780f
                translate(0f, size.height / 2f - 440f * sc) { scale(sc, Offset.Zero) { ambulanceSide(0) } }
            }
        }) { onPick(0) }
        MenuItem("Character", {
            MenuIcon {
                val sc = size.height / 330f
                translate(size.width / 2f - 85f * sc, 0f) { scale(sc, Offset.Zero) { paramedic(false, 0, 0, 1, 0) } }
            }
        }) { onPick(1) }
        MenuItem("Settings", {
            MenuIcon {
                val c = Offset(size.width / 2f, size.height / 2f)
                val r = size.width * 0.3f
                for (k in 0..7) {
                    rotate(45f * k, c) {
                        drawRect(Color.White, Offset(c.x - r * 0.22f, c.y - r * 1.3f), Size(r * 0.44f, r * 0.5f))
                    }
                }
                drawCircle(Color.White, r, c)
                drawCircle(Color(0xFF4DC43B), r * 0.42f, c)
            }
        }) { onPick(2) }
        Box(
            Modifier
                .padding(vertical = 6.dp)
                .width(230.dp)
                .height(2.dp)
                .background(Color(0xFFE0E2F2))
        )
        MenuItem("Find station", { NavBtn(0) { onPick(3) } }) { onPick(3) }
        MenuItem("Find call", { NavBtn(1) { onPick(4) } }) { onPick(4) }
        MenuItem("Find hospital", { NavBtn(2) { onPick(5) } }) { onPick(5) }
    }
}
