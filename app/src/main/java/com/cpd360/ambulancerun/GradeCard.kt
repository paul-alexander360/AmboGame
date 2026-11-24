package com.cpd360.ambulancerun

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

private val CardTan = Color(0xFFD9B98A)
private val CardEdge = Color(0xFFA8875A)
private val CardInk = Color(0xFF4A3218)
private val CardSoft = Color(0xFF7A5A32)
private val Marker = Color(0xFFC4161C)
private val PassGreen = Color(0xFF2E9E2A)

// Run grade card: a tan card tilted 30 degrees counter-clockwise with
// hand-written grades. A PASS or FAIL stamp lands on it after 5 seconds.
@Composable
fun GradeCard(rs: RouteState, onNext: () -> Unit, onKit: () -> Unit) {
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
            .background(Color(0x8C14141F))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { },
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .rotate(-30f)
                .width(240.dp)
                .shadow(14.dp, RoundedCornerShape(10.dp))
                .background(CardTan, RoundedCornerShape(10.dp))
                .border(3.dp, CardEdge, RoundedCornerShape(10.dp))
                .padding(horizontal = 16.dp, vertical = 14.dp)
        ) {
            Text(
                "Ambulance Run", color = Color(0xFF5A3C1A), fontSize = 26.sp, fontWeight = FontWeight.Black,
                textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()
            )
            Text(
                "RUN GRADE", color = CardSoft, fontSize = 14.sp, fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            Line()
            GradeRow("Drive to call", rs.nTo, rs.gTo)
            Line()
            GradeRow("Patient exam", rs.nExam, rs.gExam)
            Line()
            GradeRow("Drive to hospital", rs.nHosp, rs.gHosp)
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
                Box(
                    modifier = Modifier
                        .background(Color(0xFF4DC43B), RoundedCornerShape(16.dp))
                        .border(2.dp, Color(0xFFF6A12B), RoundedCornerShape(16.dp))
                        .clickable { onNext() }
                        .padding(horizontal = 26.dp, vertical = 12.dp)
                ) {
                    Text("Next run", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
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
            .height(2.dp)
            .background(Color(0x40785528))
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
            Text(label, color = CardInk, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold)
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
        body = "See $DR_NAME to learn the Level " + (rs.level + 1) + " skills and move on to Level " + (rs.level + 1) + "."
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
            .padding(14.dp)
    ) {
        Text(title, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(2.dp))
        Text(body, color = GlassMute, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        if (rs.kitNeeded()) {
            val cost = rs.nextKitCost()
            val have = p.credits
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Coin(13)
                Spacer(Modifier.width(5.dp))
                Text(
                    "%,d".format(minOf(have, cost)) + " of " + "%,d".format(cost),
                    color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f)
                )
                val left = cost - have
                Text(
                    if (left <= 0) "ready to buy" else "about " + ((left + 99) / 100) + " more runs",
                    color = GlassMute, fontSize = 12.sp, fontWeight = FontWeight.Bold
                )
            }
            Spacer(Modifier.height(6.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .background(GlassLine, RoundedCornerShape(4.dp))
            ) {
                Box(
                    Modifier
                        .fillMaxWidth((have.toFloat() / cost).coerceIn(0f, 1f))
                        .height(8.dp)
                        .background(CallYellow, RoundedCornerShape(4.dp))
                )
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
