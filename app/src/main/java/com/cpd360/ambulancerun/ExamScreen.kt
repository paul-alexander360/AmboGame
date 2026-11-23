package com.cpd360.ambulancerun

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val Ink = Color(0xFF2B313A)
private val Grey = Color(0xFF9A9CC8)
private val Good = Color(0xFF2E8B2E)
private val LockBg = Color(0xFFE4E5F6)

@Composable
fun ExamScreen(p: Patient, level: Int, onDone: () -> Unit) {
    val done = p.tested.count { it }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFA9ADE3))
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFFFF6A3D), RoundedCornerShape(24.dp))
                .padding(horizontal = 18.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                p.address + ": " + p.title, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            Box(
                modifier = Modifier
                    .background(Color(0xFFFFF3E0), RoundedCornerShape(12.dp))
                    .padding(horizontal = 10.dp, vertical = 3.dp)
            ) {
                Text("Level $level", color = Color(0xFFBF360C), fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFFF3E2A9), RoundedCornerShape(18.dp))
                .padding(8.dp)
        ) {
            Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))) {
                PatientScene(p, Modifier.fillMaxWidth().aspectRatio(314f / 234f))
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(8.dp)
                        .background(Color(0xFFE53935), RoundedCornerShape(12.dp))
                        .padding(horizontal = 14.dp, vertical = 3.dp)
                ) {
                    Text(
                        (if (p.female) "Female, " else "Male, ") + p.age,
                        color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold
                    )
                }
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .background(Color.White, RoundedCornerShape(14.dp))
                        .padding(8.dp)
                ) {
                    Text(p.bubble(), fontSize = 12.sp, modifier = Modifier.widthIn(max = 110.dp), textAlign = TextAlign.Center)
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            val v = level >= testLevels[0]
            ResultTile(
                Modifier.weight(1f), "Pulse", v, testLevels[0], p.tested[0],
                p.hr.toString(), if (p.hrNote != "") "bpm, " + p.hrNote else "bpm"
            )
            ResultTile(
                Modifier.weight(1f), "Oxygen", level >= testLevels[1], testLevels[1], p.tested[1],
                if (p.ox == 0) "None" else p.ox.toString() + "%", if (p.ox == 0) "no reading" else p.oxNote
            )
            ResultTile(
                Modifier.weight(1f), "Blood pressure", v, testLevels[0], p.tested[0],
                p.sys.toString() + "/" + p.dia, "mmHg"
            )
            ResultTile(
                Modifier.weight(1f), "Glucose", level >= testLevels[2], testLevels[2], p.tested[2],
                listOf("Low", "Normal", "High")[p.glu], p.gluNote
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            ResultTile(
                Modifier.weight(1f), "Stroke", level >= testLevels[3], testLevels[3], p.tested[3],
                if (p.stroke) "Positive" else "Negative", ""
            )
            ResultTile(
                Modifier.weight(1f), "ECG", level >= testLevels[4], testLevels[4], p.tested[4],
                if (p.heartAttack) "Heart attack" else "Normal", ""
            )
            Spacer(Modifier.weight(2f))
        }
        Text(
            "Tests", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center
        )
        val testGlyphs = listOf("♥", "SpO2", "💧", "🧠", "ECG")
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            for (k in 0..4) {
                Tool(testNames[k], testGlyphs[k], level < testLevels[k], testLevels[k], p.tested[k], false) { p.test(k) }
            }
        }
        Text(
            "Treatments", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center
        )
        val treatGlyphs = listOf("O2", "G", "✚", "CPR", "⚡")
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            for (k in 0..4) {
                Tool(treatNames[k], treatGlyphs[k], level < treatLevels[k], treatLevels[k], p.treated[k], true) { p.treat(k) }
            }
        }
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "$done " + (if (done == 1) "test" else "tests") + " done",
                color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(6.dp))
            Box(
                modifier = Modifier
                    .background(Brush.verticalGradient(listOf(Color(0xFF9BF076), Color(0xFF4DC43B))), RoundedCornerShape(24.dp))
                    .border(3.dp, Color(0xFFF6A12B), RoundedCornerShape(24.dp))
                    .clickable { onDone() }
                    .padding(horizontal = 40.dp, vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("To hospital!", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun ResultTile(
    m: Modifier, label: String, unlocked: Boolean, unlockLevel: Int, taken: Boolean, value: String, note: String
) {
    val bg = if (unlocked) Color.White else LockBg
    Column(
        modifier = m.background(bg, RoundedCornerShape(14.dp)).padding(vertical = 8.dp, horizontal = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(label, fontSize = 10.sp, color = Color(0xFF5B6085), textAlign = TextAlign.Center)
        when {
            !unlocked -> {
                Text("Locked", fontSize = 18.sp, color = Grey, fontWeight = FontWeight.Bold)
                Text("level $unlockLevel", fontSize = 10.sp, color = Grey, textAlign = TextAlign.Center)
            }
            !taken -> {
                Text("--", fontSize = 18.sp, color = Grey, fontWeight = FontWeight.Bold)
                Text("not taken", fontSize = 10.sp, color = Grey, textAlign = TextAlign.Center)
            }
            else -> {
                Text(
                    value, fontSize = if (value.length > 7) 14.sp else 18.sp, color = Good,
                    fontWeight = FontWeight.Bold, textAlign = TextAlign.Center
                )
                Text(note, fontSize = 10.sp, color = Good, textAlign = TextAlign.Center)
            }
        }
    }
}

@Composable
private fun Tool(
    label: String, glyph: String, locked: Boolean, unlockLevel: Int, done: Boolean, treatment: Boolean, onClick: () -> Unit
) {
    Column(Modifier.width(66.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        val cols = when {
            locked -> listOf(Color(0xFFD3D5F0), Color(0xFFA9ABD6))
            treatment -> listOf(Color(0xFF8FD3FF), Color(0xFF2A9AD6))
            else -> listOf(Color(0xFF9BF076), Color(0xFF4DC43B))
        }
        Box(
            modifier = Modifier
                .size(60.dp)
                .clip(CircleShape)
                .background(Brush.verticalGradient(cols))
                .border(3.dp, if (locked) Grey else Color(0xFFF6A12B), CircleShape)
                .clickable(enabled = !locked) { onClick() },
            contentAlignment = Alignment.Center
        ) {
            if (locked) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("🔒", fontSize = 15.sp)
                    Text("Level $unlockLevel", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFF5B6085))
                }
            } else {
                Text(
                    glyph, fontSize = if (glyph.length > 3) 15.sp else 18.sp, fontWeight = FontWeight.Bold,
                    color = if (treatment) Color(0xFF0B3D6B) else Color(0xFF1B5E20)
                )
            }
            if (done) {
                Box(
                    modifier = Modifier.align(Alignment.TopEnd).size(20.dp).background(Color.White, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text("✓", color = Good, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(label, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
    }
}

// Looks
private val skinLight = listOf(Color(0xFFFFDDBB), Color(0xFFE8B48A), Color(0xFFA86B45))
private val skinDark = listOf(Color(0xFFE39A68), Color(0xFFB87A4B), Color(0xFF6B3E26))
private val hairCols = listOf(Color(0xFFFFD54F), Color(0xFF6B3E26), Color(0xFF2B2B2B), Color(0xFFC1440E))
private val greyHair = Color(0xFFCFD3D8)
private val shirtCols = listOf(
    listOf(Color(0xFF86D9FC), Color(0xFF4FC3F7), Color(0xFF2A9AD6)),
    listOf(Color(0xFFF8BBD0), Color(0xFFEC6FA0), Color(0xFFC2185B)),
    listOf(Color(0xFFC5E1A5), Color(0xFF8BC34A), Color(0xFF558B2F)),
    listOf(Color(0xFFFFE082), Color(0xFFFFB300), Color(0xFFE65100)),
    listOf(Color(0xFFEF9A9A), Color(0xFFE53935), Color(0xFFB71C1C))
)
private val pantsCols = listOf(Color(0xFF3F51B5), Color(0xFF455A64), Color(0xFF6D4C41))

@Composable
private fun PatientScene(p: Patient, modifier: Modifier) {
    Canvas(modifier) {
        scale(size.width / 314f, Offset.Zero) {
            drawRect(Brush.verticalGradient(listOf(Color(0xFFFFF0C9), Color(0xFFFFE3A3)), startY = 0f, endY = 190f), size = Size(314f, 190f))
            drawRect(Brush.verticalGradient(listOf(Color(0xFFD4AE86), Color(0xFFB98F66)), startY = 190f, endY = 234f), topLeft = Offset(0f, 190f), size = Size(314f, 44f))
            colorRR(Color.White, 4f, 36f, 50f, 62f, 6f)
            fillRR(Color(0xFFBDE7FF), 9f, 41f, 40f, 52f, 3f)
            fillRR(Color.White, 28f, 41f, 2f, 52f, 0f)
            fillRR(Color.White, 9f, 65f, 40f, 2f, 0f)
            colorRR(Color.White, 260f, 60f, 44f, 36f, 4f)
            fillRR(Color(0xFFA5D66F), 264f, 64f, 36f, 28f, 2f)
            drawCircle(Color(0xFFFFD76A), 7f, Offset(282f, 78f))
            translate(55f, 2f) { scale(1.2f, Offset.Zero) { drawPatient(p) } }
        }
    }
}

private fun DrawScope.fillRR(c: Color, x: Float, y: Float, w: Float, h: Float, r: Float, a: Float = 1f) =
    drawRoundRect(c, Offset(x, y), Size(w, h), CornerRadius(r, r), alpha = a)

private fun DrawScope.colorRR(c: Color, x: Float, y: Float, w: Float, h: Float, r: Float) {
    drawRoundRect(c, Offset(x, y), Size(w, h), CornerRadius(r, r))
    drawRoundRect(Ink, Offset(x, y), Size(w, h), CornerRadius(r, r), style = Stroke(2.5f))
}

private fun DrawScope.brushRR(b: Brush, x: Float, y: Float, w: Float, h: Float, r: Float) {
    drawRoundRect(b, Offset(x, y), Size(w, h), CornerRadius(r, r))
    drawRoundRect(Ink, Offset(x, y), Size(w, h), CornerRadius(r, r), style = Stroke(2.5f))
}

private fun ptsPath(p: FloatArray): Path {
    val path = Path()
    path.moveTo(p[0], p[1])
    var i = 2
    while (i < p.size) {
        path.lineTo(p[i], p[i + 1])
        i += 2
    }
    return path
}

private fun DrawScope.strokePts(p: FloatArray, c: Color, w: Float, a: Float = 1f) {
    drawPath(ptsPath(p), c, alpha = a, style = Stroke(w, cap = StrokeCap.Round, join = StrokeJoin.Round))
}

private fun DrawScope.tube(p: FloatArray, outW: Float, c: Color, inW: Float) {
    strokePts(p, Ink, outW)
    strokePts(p, c, inW)
}

private fun DrawScope.leg(p: FloatArray, c: Color) {
    tube(p, 17f, c, 12f)
    translate(3f, 1.5f) {
        strokePts(p, Color.Black, 5f, 0.18f)
        strokePts(p, Color.Black, 3f, 0.25f)
    }
    translate(-3f, -1.5f) {
        strokePts(p, Color.White, 5f, 0.2f)
        strokePts(p, Color.White, 3f, 0.3f)
    }
}

private fun DrawScope.shoe(x: Float, y: Float) {
    drawOval(Color(0xFF5D4037), Offset(x - 13f, y - 6f), Size(26f, 12f))
    drawOval(Ink, Offset(x - 13f, y - 6f), Size(26f, 12f), style = Stroke(2.5f))
    drawOval(Color.White, Offset(x - 8f, y - 4f), Size(12f, 4f), alpha = 0.3f)
}

private fun DrawScope.hand(x: Float, y: Float, s: Int) {
    val skin = Brush.radialGradient(listOf(skinLight[s], skinDark[s]), center = Offset(x - 2f, y - 2f), radius = 9f)
    drawCircle(skin, 6f, Offset(x, y))
    drawCircle(Ink, 6f, Offset(x, y), style = Stroke(2.5f))
}

private fun DrawScope.outlined(path: Path, c: Color) {
    path.close()
    drawPath(path, c)
    drawPath(path, Ink, style = Stroke(2.5f, join = StrokeJoin.Round))
}

private fun DrawScope.drawHair(p: Patient) {
    val c = if (p.age > 50) greyHair else hairCols[p.hairColor]
    val h = Path()
    if (p.female) {
        when (p.hairStyle) {
            0 -> {
                h.moveTo(58f, 60f)
                h.cubicTo(50f, 20f, 120f, 20f, 112f, 60f)
                h.cubicTo(116f, 80f, 112f, 92f, 106f, 96f)
                h.cubicTo(108f, 70f, 104f, 44f, 85f, 40f)
                h.cubicTo(66f, 44f, 62f, 70f, 64f, 96f)
                h.cubicTo(58f, 92f, 54f, 80f, 58f, 60f)
                outlined(h, c)
            }
            1 -> {
                h.moveTo(58f, 56f)
                h.cubicTo(55f, 22f, 115f, 22f, 112f, 56f)
                h.lineTo(112f, 72f)
                h.cubicTo(108f, 74f, 106f, 72f, 106f, 66f)
                h.cubicTo(104f, 44f, 66f, 44f, 64f, 66f)
                h.cubicTo(64f, 72f, 62f, 74f, 58f, 72f)
                outlined(h, c)
            }
            else -> {
                drawCircle(c, 9f, Offset(85f, 27f))
                drawCircle(Ink, 9f, Offset(85f, 27f), style = Stroke(2.5f))
                h.moveTo(58f, 52f)
                h.cubicTo(57f, 24f, 113f, 24f, 112f, 52f)
                h.cubicTo(104f, 38f, 66f, 38f, 58f, 52f)
                outlined(h, c)
            }
        }
    } else {
        when (p.hairStyle) {
            0 -> {
                h.moveTo(58f, 52f)
                h.cubicTo(57f, 24f, 113f, 24f, 112f, 52f)
                h.cubicTo(104f, 38f, 66f, 38f, 58f, 52f)
                outlined(h, c)
            }
            1 -> {
                h.moveTo(60f, 48f)
                h.cubicTo(62f, 26f, 108f, 26f, 110f, 48f)
                h.cubicTo(100f, 40f, 70f, 40f, 60f, 48f)
                outlined(h, c)
            }
            else -> {
                for (x in floatArrayOf(56f, 106f)) {
                    drawOval(c, Offset(x, 44f), Size(8f, 16f))
                    drawOval(Ink, Offset(x, 44f), Size(8f, 16f), style = Stroke(2.5f))
                }
            }
        }
    }
}

private fun DrawScope.drawPatient(p: Patient) {
    val sk = p.skin
    val sh = shirtCols[p.shirt]
    val pants = pantsCols[p.pants]
    drawOval(Color.Black, Offset(5f, 161f), Size(160f, 14f), alpha = 0.2f)
    brushRR(Brush.verticalGradient(listOf(Color(0xFFA88DEE), Color(0xFF8466D4)), startY = 66f, endY = 128f), 16f, 66f, 138f, 62f, 16f)
    fillRR(Color.White, 16f, 66f, 138f, 14f, 7f, 0.22f)
    brushRR(Brush.horizontalGradient(listOf(Color(0xFFA184E6), Color(0xFF7556C0)), startX = 8f, endX = 32f), 8f, 98f, 24f, 64f, 10f)
    brushRR(Brush.horizontalGradient(listOf(Color(0xFFA184E6), Color(0xFF7556C0)), startX = 138f, endX = 162f), 138f, 98f, 24f, 64f, 10f)
    fillRR(Color.White, 10f, 102f, 6f, 46f, 3f, 0.25f)
    fillRR(Color.White, 140f, 102f, 6f, 46f, 3f, 0.25f)
    colorRR(Color(0xFF4B3592), 22f, 158f, 12f, 10f, 3f)
    colorRR(Color(0xFF4B3592), 136f, 158f, 12f, 10f, 3f)
    brushRR(Brush.verticalGradient(listOf(Color(0xFF7A5BC8), Color(0xFF553C9E)), startY = 130f, endY = 160f), 16f, 130f, 138f, 30f, 10f)
    drawLine(Color(0x803B2678), Offset(85f, 140f), Offset(85f, 158f), 1.5f)
    brushRR(Brush.verticalGradient(listOf(Color(0xFFB49AF2), Color(0xFF9276E0)), startY = 114f, endY = 138f), 16f, 114f, 138f, 24f, 10f)
    fillRR(Color.White, 22f, 117f, 126f, 5f, 2.5f, 0.32f)
    drawOval(Color.Black, Offset(49f, 132f), Size(34f, 8f), alpha = 0.22f)
    drawOval(Color.Black, Offset(87f, 132f), Size(34f, 8f), alpha = 0.22f)
    shoe(68f, 181f)
    shoe(102f, 181f)
    leg(floatArrayOf(72f, 126f, 52f, 140f, 66f, 172f), pants)
    leg(floatArrayOf(98f, 126f, 118f, 140f, 104f, 172f), pants)
    brushRR(Brush.horizontalGradient(sh, startX = 62f, endX = 108f), 62f, 82f, 46f, 46f, 16f)
    fillRR(Color.White, 67f, 88f, 7f, 30f, 3.5f, 0.3f)
    tube(floatArrayOf(106f, 94f, 122f, 114f, 118f, 138f), 14f, sh[1], 9f)
    tube(floatArrayOf(64f, 94f, 52f, 118f, 80f, 106f), 14f, sh[1], 9f)
    hand(118f, 139f, sk)
    hand(80f, 106f, sk)
    val skin = Brush.radialGradient(listOf(skinLight[sk], skinDark[sk]), center = Offset(76f, 46f), radius = 40f)
    drawCircle(skin, 27f, Offset(85f, 56f))
    drawCircle(Ink, 27f, Offset(85f, 56f), style = Stroke(2.5f))
    drawOval(Color.White, Offset(66f, 35f), Size(18f, 10f), alpha = 0.3f)
    drawHair(p)
    val limp = p.dead || (p.kind == ARREST && p.hr == 0)
    for (ex in floatArrayOf(75f, 95f)) {
        if (limp) {
            drawLine(Ink, Offset(ex - 6f, 58f), Offset(ex + 6f, 58f), 2.5f, StrokeCap.Round)
        } else {
            drawCircle(Color.White, 8f, Offset(ex, 58f))
            drawCircle(Ink, 8f, Offset(ex, 58f), style = Stroke(2.5f))
            drawCircle(Ink, 3f, Offset(ex - 2f, 60f))
        }
    }
    if (!p.helped) {
        drawLine(Ink, Offset(66f, 49f), Offset(79f, 45f), 2.5f, StrokeCap.Round)
        drawLine(Ink, Offset(104f, 49f), Offset(91f, 45f), 2.5f, StrokeCap.Round)
    }
    if (p.helped) {
        val smile = Path()
        smile.moveTo(78f, 73f)
        smile.cubicTo(81f, 78f, 89f, 78f, 92f, 73f)
        drawPath(smile, Ink, style = Stroke(2.5f, cap = StrokeCap.Round))
    } else {
        drawOval(Color(0xFF8A3B2A), Offset(79f, 72f), Size(12f, 8f))
        drawOval(Ink, Offset(79f, 72f), Size(12f, 8f), style = Stroke(2.5f))
        if (!limp) drawOval(Color(0xFF7FD3FF), Offset(111f, 42f), Size(6f, 10f))
    }
}
