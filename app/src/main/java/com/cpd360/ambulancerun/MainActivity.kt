package com.cpd360.ambulancerun

import android.os.Bundle
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.fillMaxHeight
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
private fun NavBtn(kind: Int, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(46.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Glass)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.size(26.dp)) {
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
    val guide = remember { GuideState() }
    when (screen) {
        1 -> {
            Box(Modifier.fillMaxSize()) {
                ExamScreen(rs.patient, rs.level) {
                    rs.examDone()
                    screen = 0
                }
                // Dr. Ray explains the exam the first time, then each new skill
                val dt = examTip(rs.level, player)
                if (dt != "") DrTip(dt, rs.level) { player.markSeen(dt) }
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
        6 -> {
            DrScreen(player, rs.level, rs.kitNeeded(), {
                if (rs.buyKit()) screen = 0
            }) { screen = 0 }
            return
        }
    }
    LaunchedEffect(rs.done, rs.stage) {
        if (rs.done && !rs.driveShown) {
            // Route check: hold the map for 2 seconds so the player can see the red and missed parts
            if (rs.checking) {
                delay(2000)
                rs.checking = false
            }
            if (player.driveScenes && rs.path.size > 0) screen = 5 else rs.driveShown = true
        }
    }
    // The hint shows for a few seconds at the start of each leg, then fades away.
    // It starts once the call card is answered.
    var hint by remember(rs.version, rs.stage, guide.callFor) { mutableStateOf(true) }
    LaunchedEffect(rs.version, rs.stage, guide.callFor) {
        delay(8000)
        hint = false
    }
    BackHandler(enabled = menu) { menu = false }
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
        // Top: one slim bar, then the hint on the left and the find buttons on the right edge.
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .safeDrawingPadding()
                .padding(horizontal = 10.dp, vertical = 8.dp)
        ) {
            TopBar(rs.level, rs.idx, player.goodRuns, rs.kitNeeded(), rs.cityCleared(), par) { menu = true }
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (ACCIDENTS_ON && rs.path.size > 1 && rs.accSeg < 0) RiskPill(rs.routeRisk(), false)
                    if (hint && !rs.done) {
                        HintPill(
                            if (rs.stage == 1) "Drag from the ambulance to the right hospital. Double tap a hospital to see what it treats."
                            else rs.patient.address + ": " + rs.patient.title.lowercase() + ". Drag from the station to the glowing call."
                        ) { hint = false }
                    }
                }
                Spacer(Modifier.width(8.dp))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    NavBtn(0) { rs.focusStation() }
                    NavBtn(1) { rs.focusCall() }
                    NavBtn(2) { rs.focusHospital() }
                }
            }
        }
        // Bottom: only shows once a route is finished.
        if (rs.done && !rs.checking) {
            val user = rs.city.pathTime(rs.path.toList())
            val rt = rating(user, fast)
            val names = listOf("Green", "Yellow", "Orange", "Red")
            val cols = listOf(Color(0xFF6FAE3E), Color(0xFFD9A93A), Color(0xFFF29A2E), RouteRed)
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .safeDrawingPadding()
                    .padding(10.dp)
                    .background(Glass, RoundedCornerShape(20.dp))
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    if (rs.failReason != "") "Run failed: " + rs.failReason else if (rs.runOver) "Run complete" else "Route complete",
                    color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold
                )
                if (rs.stage == 1) {
                    Text("To the call " + fmt(rs.leg1User) + "   Target " + fmt(rs.leg1Fast), color = GlassMute, fontSize = 14.sp)
                }
                if (rs.accSeg < 0) {
                    Text(
                        (if (rs.stage == 1) "To the hospital " else "Your time ") + fmt(user) + "   Target " + fmt(fast),
                        color = GlassMute, fontSize = 14.sp
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .background(cols[rt], RoundedCornerShape(12.dp))
                                .padding(horizontal = 14.dp, vertical = 5.dp)
                        ) {
                            Text(names[rt] + " route", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        }
                        if (ACCIDENTS_ON) RiskPill(rs.routeRisk(), true)
                    }
                }
                if (rs.runOver) {
                    Spacer(Modifier.height(8.dp))
                    Text("XP and credits earned: +" + rs.earned, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(10.dp))
                    GoButton("Next run") { rs.nextRun() }
                } else if (rs.stage == 0) {
                    Spacer(Modifier.height(10.dp))
                    GoButton("Treat patient") { screen = 1 }
                }
            }
        }
        if (menu) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color(0x73141830))
                    .clickable { menu = false }
            )
            Drawer(player, rs, version, { menu = false }) { pick ->
                menu = false
                when (pick) {
                    0 -> screen = 2
                    1 -> screen = 3
                    2 -> screen = 4
                    3 -> rs.reset()
                    4 -> rs.nextCity()
                    5 -> screen = 6
                }
            }
        }
        if (rs.runOver && rs.driveShown && rs.gTo >= 0) {
            GradeCard(rs, { rs.nextRun() }) { screen = 6 }
        }
        // Dot: first-time tips, then the call card at the start of each run
        if (!menu) {
            val tip = mapTip(rs, player, guide)
            if (tip != "") {
                DotTip(tip, rs) { player.markSeen(tip) }
            } else if (rs.stage == 0 && !rs.done && !rs.started && rs.path.isEmpty() && guide.callFor != rs.version) {
                CallCard(rs, player, guide, fast) { guide.callFor = rs.version }
            }
        }
    }
}

@Composable
private fun TopBar(level: Int, idx: Int, good: Int, kitNeeded: Boolean, allDone: Boolean, par: Double, onMenu: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Glass, RoundedCornerShape(18.dp))
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(GlassLine)
                .clickable { onMenu() },
            contentAlignment = Alignment.Center
        ) {
            Canvas(Modifier.size(20.dp)) {
                val w = size.width
                drawLine(Color.White, Offset(w * 0.08f, w * 0.2f), Offset(w * 0.92f, w * 0.2f), w * 0.13f, StrokeCap.Round)
                drawLine(Color.White, Offset(w * 0.08f, w * 0.5f), Offset(w * 0.92f, w * 0.5f), w * 0.13f, StrokeCap.Round)
                drawLine(Color.White, Offset(w * 0.08f, w * 0.8f), Offset(w * 0.6f, w * 0.8f), w * 0.13f, StrokeCap.Round)
            }
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text("Level $level", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(8.dp))
                Text("City " + (idx + 1) + " of 5", color = GlassMute, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(6.dp))
            if (kitNeeded || allDone) {
                Box(
                    Modifier
                        .background(CallYellow, RoundedCornerShape(6.dp))
                        .padding(horizontal = 7.dp, vertical = 1.dp)
                ) {
                    Text(
                        if (kitNeeded) "SEE DR. RAY" else "ALL CLEARED", color = InkText,
                        fontSize = 10.sp, fontWeight = FontWeight.Bold
                    )
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    for (i in 1..RUNS_PER_CITY) {
                        val c = if (i <= good) ParkGreen else if (i == good + 1) CallYellow else GlassLine
                        Box(
                            Modifier
                                .weight(1f)
                                .height(4.dp)
                                .background(c, RoundedCornerShape(2.dp))
                        )
                    }
                }
            }
        }
        Spacer(Modifier.width(10.dp))
        Row(
            modifier = Modifier
                .background(Color(0x33FFE66B), RoundedCornerShape(10.dp))
                .padding(horizontal = 9.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Canvas(Modifier.size(14.dp)) {
                val w = size.width
                val c = Offset(w / 2f, w / 2f)
                drawCircle(CallYellow, w * 0.42f, c, style = Stroke(w * 0.14f))
                drawLine(CallYellow, c, Offset(c.x, w * 0.22f), w * 0.14f, StrokeCap.Round)
                drawLine(CallYellow, c, Offset(w * 0.72f, w * 0.62f), w * 0.14f, StrokeCap.Round)
            }
            Spacer(Modifier.width(5.dp))
            Text(fmt(par), color = CallYellow, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        }
    }
}

// Crash risk of the route. Green under 2.5%, yellow under 5%, red above.
@Composable
private fun RiskPill(risk: Double, withWord: Boolean) {
    val pct = risk * 100.0
    val bg = if (pct < 2.5) Color(0xFFCFEFB0) else if (pct < 5.0) Color(0xFFFFE08A) else Color(0xFFFFB3A8)
    val dot = if (pct < 2.5) Color(0xFF4E9A22) else if (pct < 5.0) Color(0xFFD99A00) else RouteRed
    val word = if (pct < 2.5) " · Quiet streets" else if (pct < 5.0) "" else " · Busy streets"
    Row(
        modifier = Modifier
            .background(bg, RoundedCornerShape(12.dp))
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(9.dp)
                .background(dot, CircleShape)
        )
        Spacer(Modifier.width(6.dp))
        Text(
            "Crash risk " + "%.1f".format(pct) + "%" + (if (withWord) word else ""),
            color = InkText, fontSize = 13.sp, fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun HintPill(text: String, onClose: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xF2FFFFFF), RoundedCornerShape(14.dp))
            .clickable { onClose() }
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(18.dp)
                .background(Color(0xFF5B5F9A), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text("i", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.width(8.dp))
        Text(text, color = InkText, fontSize = 13.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun GoButton(label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .background(Color(0xFF6FAE3E), RoundedCornerShape(14.dp))
            .clickable { onClick() }
            .padding(horizontal = 24.dp, vertical = 11.dp)
    ) {
        Text(label, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun Drawer(player: PlayerData, rs: RouteState, version: String, onClose: () -> Unit, onPick: (Int) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxHeight()
            .width(296.dp)
            .background(GlassSolid, RoundedCornerShape(topEnd = 24.dp, bottomEnd = 24.dp))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { }
            .safeDrawingPadding()
            .padding(horizontal = 14.dp, vertical = 16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Canvas(
                Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(GlassLine)
            ) {
                val sc = size.width * 1.3f / 170f
                translate(size.width / 2f - 85f * sc, size.height * 0.1f) {
                    scale(sc, Offset.Zero) {
                        paramedic(player.female, player.skin, player.hairStyle, player.hairColor, player.uniform)
                    }
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(player.name, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Text("Paramedic · Level " + player.level, color = GlassMute, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .background(GlassLine, RoundedCornerShape(3.dp))
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth((player.totalXp % 500) / 500f)
                            .height(6.dp)
                            .background(RouteRed, RoundedCornerShape(3.dp))
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .clickable { onClose() },
                contentAlignment = Alignment.Center
            ) {
                Text("✕", color = GlassMute, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
        }
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatTile("CREDITS", Modifier.weight(1f)) {
                Coin(16)
                Spacer(Modifier.width(5.dp))
                Text("%,d".format(player.credits), color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
            }
            StatTile("DRIVER RATING", Modifier.weight(1f)) {
                Text("%.2f".format(player.drive), color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
            }
        }
        Spacer(Modifier.height(12.dp))
        DrawerItem("Ambulance", "Paint and upgrades", {
            val sc = size.width / 1780f
            translate(0f, size.height / 2f - 440f * sc) { scale(sc, Offset.Zero) { ambulanceSide(player.ambColor) } }
        }) { onPick(0) }
        DrawerItem("Character", "Look and skills", {
            val sc = size.height / 330f
            translate(size.width / 2f - 85f * sc, 0f) {
                scale(sc, Offset.Zero) { paramedic(player.female, player.skin, player.hairStyle, player.hairColor, player.uniform) }
            }
        }) { onPick(1) }
        DrawerItem(DR_NAME, "Learn tests and treatments", { doctorIcon() }) { onPick(5) }
        DrawerItem("Settings", "Sound and drive scenes", {
            val c = Offset(size.width / 2f, size.height / 2f)
            val r = size.width * 0.28f
            for (k in 0..7) {
                rotate(45f * k, c) {
                    drawRect(Color.White, Offset(c.x - r * 0.22f, c.y - r * 1.3f), Size(r * 0.44f, r * 0.5f))
                }
            }
            drawCircle(Color.White, r, c)
            drawCircle(GlassSolid, r * 0.42f, c)
        }) { onPick(2) }
        Box(
            Modifier
                .padding(vertical = 12.dp)
                .fillMaxWidth()
                .height(1.dp)
                .background(GlassLine)
        )
        Text("TESTING", color = Color(0xFF8E92C4), fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TestBtn("Test again", rs.done, Modifier.weight(1f)) { onPick(3) }
            TestBtn("Next city", true, Modifier.weight(1f)) { onPick(4) }
        }
        Spacer(Modifier.weight(1f))
        Text(
            "Version $version  ·  Drive " + "%.3f".format(player.drive) + "  ·  Crash risk ×" + "%.2f".format(player.riskMult),
            color = Color(0xFF8E92C4), fontSize = 11.sp
        )
    }
}

@Composable
private fun StatTile(label: String, modifier: Modifier, content: @Composable () -> Unit) {
    Column(
        modifier = modifier
            .background(GlassLine, RoundedCornerShape(12.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp)
    ) {
        Text(label, color = GlassMute, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        Row(verticalAlignment = Alignment.CenterVertically) { content() }
    }
}

@Composable
private fun DrawerItem(
    label: String,
    sub: String,
    draw: androidx.compose.ui.graphics.drawscope.DrawScope.() -> Unit,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable { onClick() }
            .padding(horizontal = 8.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(GlassLine),
            contentAlignment = Alignment.Center
        ) {
            Canvas(Modifier.size(32.dp)) { draw() }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(label, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Text(sub, color = GlassMute, fontSize = 12.sp)
        }
        Text("›", color = Color(0xFF8E92C4), fontSize = 22.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun TestBtn(label: String, enabled: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .border(1.5.dp, if (enabled) Color(0xFF8E92C4) else GlassLine, RoundedCornerShape(10.dp))
            .clickable(enabled = enabled) { onClick() }
            .padding(vertical = 9.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = if (enabled) GlassMute else Color(0xFF6E72A4), fontSize = 13.sp, fontWeight = FontWeight.Bold)
    }
}
