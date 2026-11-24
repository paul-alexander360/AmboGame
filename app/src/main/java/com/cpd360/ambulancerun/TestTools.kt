package com.cpd360.ambulancerun

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Highest character level the test page can set
const val TEST_MAX_CHAR_LEVEL = 50

private val TestBg = Color(0xFF2E3260)
private val TestField = Color(0xFF262A52)
private val TestBtnFill = Color(0xFF474B82)
private val TestLabel = Color(0xFF8E92C4)
private val TestGo = Color(0xFF6FAE3E)

// Testing page: set credits, set the character level, and jump to any level, city and run count.
@Composable
fun TestToolsScreen(player: PlayerData, rs: RouteState, onBack: () -> Unit) {
    BackHandler { onBack() }
    var creditText by remember { mutableStateOf(player.credits.toString()) }
    var heartText by remember { mutableStateOf(player.hearts.toString()) }
    var jLevel by remember { mutableIntStateOf(rs.level) }
    var jCity by remember { mutableIntStateOf(rs.idx + 1) }
    var jRuns by remember { mutableIntStateOf(player.goodRuns) }

    fun setCredits(v: Int) {
        player.credits = v.coerceAtLeast(0)
        player.save()
        creditText = player.credits.toString()
    }

    fun setHearts(v: Int) {
        player.hearts = v.coerceAtLeast(0)
        player.save()
        heartText = player.hearts.toString()
    }

    fun setCharLevel(l: Int) {
        player.totalXp = (l.coerceIn(1, TEST_MAX_CHAR_LEVEL) - 1) * 500
        player.save()
        rs.applyPlayer()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(TestBg)
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(TestBtnFill)
                    .clickable { onBack() },
                contentAlignment = Alignment.Center
            ) {
                Text("‹", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.width(10.dp))
            Text("Test tools", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }

        TestCard("CREDITS") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                BasicTextField(
                    value = creditText,
                    onValueChange = { t -> creditText = t.filter { it.isDigit() }.take(9) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    textStyle = TextStyle(color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold),
                    cursorBrush = SolidColor(Color.White),
                    modifier = Modifier
                        .weight(1f)
                        .background(TestField, RoundedCornerShape(10.dp))
                        .border(1.5.dp, TestLabel, RoundedCornerShape(10.dp))
                        .padding(horizontal = 10.dp, vertical = 9.dp)
                )
                Spacer(Modifier.width(8.dp))
                TestPill("Set", TestGo) { setCredits(creditText.toIntOrNull() ?: 0) }
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                for (v in listOf(0, 1_000, 10_000, 100_000)) {
                    TestPill("%,d".format(v), TestBtnFill, Modifier.weight(1f)) { setCredits(v) }
                }
            }
        }

        TestCard("HEARTS") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                BasicTextField(
                    value = heartText,
                    onValueChange = { t -> heartText = t.filter { it.isDigit() }.take(7) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    textStyle = TextStyle(color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold),
                    cursorBrush = SolidColor(Color.White),
                    modifier = Modifier
                        .weight(1f)
                        .background(TestField, RoundedCornerShape(10.dp))
                        .border(1.5.dp, TestLabel, RoundedCornerShape(10.dp))
                        .padding(horizontal = 10.dp, vertical = 9.dp)
                )
                Spacer(Modifier.width(8.dp))
                TestPill("Set", TestGo) { setHearts(heartText.toIntOrNull() ?: 0) }
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                for (v in listOf(0, 10, 100, 1_000)) {
                    TestPill("%,d".format(v), TestBtnFill, Modifier.weight(1f)) { setHearts(v) }
                }
            }
            Spacer(Modifier.height(8.dp))
            TestPill("Reset ads watched today", TestBtnFill, Modifier.fillMaxWidth()) {
                player.adsToday = 0
                player.save()
            }
        }

        TestCard("CHARACTER LEVEL") {
            Stepper("Level", player.level, 1, TEST_MAX_CHAR_LEVEL) { setCharLevel(it) }
            TestNote("Sets XP to the start of that level. Changes right away.")
        }

        TestCard("JUMP TO") {
            Stepper("Level", jLevel, 1, MAX_LEVEL) { jLevel = it }
            Stepper("City", jCity, 1, 5) { jCity = it }
            Stepper("Runs done", jRuns, 0, RUNS_PER_CITY) { jRuns = it }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                for (i in 1..RUNS_PER_CITY) {
                    Box(
                        Modifier
                            .padding(start = 5.dp)
                            .size(16.dp)
                            .background(if (i <= jRuns) TestGo else TestBtnFill, CircleShape)
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            TestPill("Go to Level $jLevel, City $jCity", TestGo, Modifier.fillMaxWidth(), 17) {
                rs.jumpTo(jLevel, jCity - 1, jRuns)
                onBack()
            }
            TestNote("Level 1–$MAX_LEVEL, City 1–5, Runs 0–$RUNS_PER_CITY. Starts a new call there.")
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun TestCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(GlassSolid, RoundedCornerShape(16.dp))
            .padding(12.dp)
    ) {
        Text(title, color = GlassMute, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
        Spacer(Modifier.height(8.dp))
        content()
    }
}

@Composable
private fun TestNote(text: String) {
    Spacer(Modifier.height(4.dp))
    Text(text, color = Color(0xFFAAB0D8), fontSize = 12.sp, fontWeight = FontWeight.Bold)
}

@Composable
private fun TestPill(label: String, fill: Color, modifier: Modifier = Modifier, fontSize: Int = 14, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(fill)
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 9.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = Color.White, fontSize = fontSize.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun Stepper(label: String, value: Int, min: Int, max: Int, onChange: (Int) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        StepBtn("−", value > min) { onChange(value - 1) }
        Box(Modifier.width(56.dp), contentAlignment = Alignment.Center) {
            Text(value.toString(), color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }
        StepBtn("+", value < max) { onChange(value + 1) }
    }
}

@Composable
private fun StepBtn(label: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(TestBtnFill)
            .clickable(enabled = enabled) { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = if (enabled) Color.White else TestLabel, fontSize = 20.sp, fontWeight = FontWeight.Bold)
    }
}
