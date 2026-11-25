package com.cpd360.ambulancerun

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

private val CardInk = Color(0xFF2A2622)
private val CardSoft = Color(0xFF6D6457)
private val Marker = Color(0xFFC4161C)
private val PassGreen = Color(0xFF2E9E2A)

// Run grade card: a paper grade sheet on a wooden clipboard, tilted a little, with
// hand-written grades. A PASS or FAIL stamp lands on it after 5 seconds.
@Composable
// A failed run also offers a Hearts retry next to Next run. With too few Hearts the button opens the Hearts shop.
fun GradeCard(rs: RouteState, onNext: () -> Unit, onKit: () -> Unit, onShop: () -> Unit) {
    var stamp by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(5000)
        stamp = true
    }
    val pass = rs.passed()
    val sc by animateFloatAsState(if (stamp) 1f else 3f, tween(300), label = "stamp")
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0x8C06080E))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { },
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .padding(bottom = 120.dp)
                .rotate(-3f)
                .width(300.dp)
                .shadow(24.dp, RoundedCornerShape(22.dp))
                .background(
                    Brush.linearGradient(listOf(Color(0xFF6B4A2C), Color(0xFF3E2914))),
                    RoundedCornerShape(22.dp)
                )
                .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(22.dp))
                .padding(start = 14.dp, end = 14.dp, top = 34.dp, bottom = 14.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(4.dp, RoundedCornerShape(6.dp))
                    .background(Brush.verticalGradient(listOf(Color(0xFFF6F2E8), Color(0xFFEBE4D4))), RoundedCornerShape(6.dp))
                    .drawBehind {
                        // Ruled lines, like a real form
                        val step = 28.dp.toPx()
                        var y = step
                        while (y < size.height) {
                            drawLine(Color(0x2E5078B4), Offset(0f, y), Offset(size.width, y), 1.dp.toPx())
                            y += step
                        }
                    }
                    .padding(start = 18.dp, end = 18.dp, top = 30.dp, bottom = 14.dp)
            ) {
                Text("Ambulance Run", color = CardInk, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
                Text(
                    "RUN GRADE · CITY " + (rs.idx + 1), color = Color(0xFF7A6E5E), fontSize = 11.sp, fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp
                )
                Spacer(Modifier.height(8.dp))
                GradeRow("Drive to call", rs.nTo, rs.gTo)
                Line()
                GradeRow("Patient exam", rs.nExam, rs.gExam)
                Line()
                GradeRow("Drive to hospital", rs.nHosp, rs.gHosp)
            }
            // Metal clamp across the top
            Box(
                Modifier
                    .align(Alignment.TopCenter)
                    .offset(y = (-28).dp)
                    .size(120.dp, 50.dp)
                    .shadow(6.dp, RoundedCornerShape(12.dp))
                    .background(Brush.verticalGradient(listOf(Color(0xFFE4E7EC), Color(0xFF8D939C))), RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.TopCenter
            ) {
                Box(
                    Modifier
                        .padding(top = 10.dp)
                        .size(32.dp, 14.dp)
                        .background(Color(0xFF5C626B), RoundedCornerShape(7.dp))
                )
            }
        }
        if (stamp) {
            val col = if (pass) PassGreen else Marker
            Box(
                modifier = Modifier
                    .rotate(-18f)
                    .scale(sc)
                    .background(Color(0x33FFFFFF), RoundedCornerShape(14.dp))
                    .border(6.dp, col, RoundedCornerShape(14.dp))
                    .padding(horizontal = 24.dp, vertical = 4.dp)
            ) {
                Text(
                    if (pass) "PASS" else "FAIL", color = col, fontSize = 64.sp,
                    fontWeight = FontWeight.Black, letterSpacing = 4.sp
                )
            }
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .safeDrawingPadding()
                    .padding(start = 12.dp, end = 12.dp, bottom = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                RunProgress(rs, onKit)
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (rs.canRetry()) {
                        val enough = rs.player.hearts >= RETRY_COST
                        Row(
                            modifier = Modifier
                                .background(HeartRed, RoundedCornerShape(25.dp))
                                .clickable { if (enough) rs.retry() else onShop() }
                                .padding(horizontal = 18.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(if (enough) "↻ Retry " else "Get Hearts ", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                            if (enough) HeartAmount(RETRY_COST, Color.White, 18, 17, Color.White)
                        }
                    }
                    GreenButton(if (rs.canMoveUp()) "Go to Level " + (rs.level + 1) else "Next run") { onNext() }
                }
            }
        }
    }
}

@Composable
private fun Line() {
    Box(
        Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(Color(0x2E000000))
    )
}

@Composable
private fun GradeRow(label: String, note: String, grade: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, color = CardInk, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Text(
                if (note == "") " " else note, color = CardSoft, fontSize = 11.sp,
                maxLines = 2, overflow = TextOverflow.Ellipsis
            )
        }
        Box(Modifier.width(64.dp), contentAlignment = Alignment.Center) {
            Text(
                gradeName(grade),
                color = Marker,
                fontSize = if (grade < 0) 24.sp else 42.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Cursive,
                modifier = Modifier.rotate(-8f)
            )
        }
    }
}

// Panel under the grade card: did this run count, and what is next.
@Composable
fun RunProgress(rs: RouteState, onKit: () -> Unit) {
    val p = rs.player
    val good = p.goodRuns
    val title: String
    val body: String
    if (rs.kitNeeded()) {
        title = "City 5 cleared!"
        body = "To move on to Level " + (rs.level + 1) + " you still need:"
    } else if (rs.canMoveUp()) {
        title = "Level " + rs.level + " cleared!"
        body = "You have everything for Level " + (rs.level + 1) + ". Your next run is there."
    } else if (rs.cityCleared()) {
        title = "Every city cleared!"
        body = "Keep running calls for credits and upgrades."
    } else if (rs.counted) {
        title = "Run counted"
        body = if (good >= RUNS_PER_CITY) "City " + (rs.idx + 1) + " cleared! Next run is in City " + (rs.idx + 2) + "."
            else "$good of $RUNS_PER_CITY runs done in this city."
    } else {
        title = "Run didn't count"
        body = "A run with an F doesn't fill a dot. Still $good of $RUNS_PER_CITY runs done in this city."
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Glass, RoundedCornerShape(18.dp))
            .border(1.dp, CardLine, RoundedCornerShape(18.dp))
            .padding(14.dp)
    ) {
        Text(title, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(2.dp))
        Text(body, color = GlassMute, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        if (rs.heartsEarned > 0) {
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Heart(15)
                Spacer(Modifier.width(5.dp))
                Text("+" + rs.heartsEarned + " Hearts", color = HeartSoft, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }
        }
        if (rs.canRetry()) {
            Spacer(Modifier.height(10.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(GlassLine)
            )
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Retry this run with Hearts?", color = Color.White, fontSize = 15.sp,
                    fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f)
                )
                Text("You have ", color = HeartSoft, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                HeartAmount(p.hearts, HeartSoft, 13, 13)
            }
        }
        if (rs.kitNeeded()) {
            // A tick for each thing the next level needs, a red cross for what's still missing
            val next = rs.level + 1
            val names = itemsFor(next).map { itemName(it) to p.owns(it) } +
                listOf(("Compartment Lv " + p.compFor(next)) to (p.ambUp[COMPARTMENT] >= p.compFor(next)))
            for ((n, have) in names) {
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(20.dp)
                            .background(if (have) ParkGreen else Color.Transparent, CircleShape)
                            .border(2.dp, if (have) ParkGreen else Color(0xFFFF6B6B), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            if (have) "✓" else "✕", color = if (have) Color.White else Color(0xFFFF6B6B),
                            fontSize = 11.sp, fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(n, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(Modifier.height(10.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(GlassLine, RoundedCornerShape(12.dp))
                    .clickable { onKit() }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("See $DR_NAME", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}
