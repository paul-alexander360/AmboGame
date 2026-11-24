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
fun GradeCard(rs: RouteState, onNext: () -> Unit) {
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
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .safeDrawingPadding()
                    .padding(bottom = 32.dp)
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
