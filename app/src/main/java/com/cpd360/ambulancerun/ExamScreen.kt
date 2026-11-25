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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val Good = Color(0xFF7CF29A)
private val Locked = Color(0xFF5D6576)

@Composable
fun ExamScreen(p: Patient, level: Int, onDone: () -> Unit) {
    val done = p.tested.count { it }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF1C2029), Color(0xFF12151B))))
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .glassCard(18)
                .padding(horizontal = 16.dp, vertical = 11.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                p.address + " · " + p.title, color = InkText, fontSize = 16.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            Box(
                modifier = Modifier
                    .background(Color(0xE6E53935), RoundedCornerShape(10.dp))
                    .padding(horizontal = 10.dp, vertical = 3.dp)
            ) {
                Text("Level $level", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
        }
        Box(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .border(1.dp, CardLine, RoundedCornerShape(20.dp))
        ) {
            PatientScene(p, Modifier.fillMaxWidth().aspectRatio(316f / 300f))
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(10.dp)
                    .background(Color(0xD9E53935), RoundedCornerShape(14.dp))
                    .border(1.dp, Color(0x4DFFFFFF), RoundedCornerShape(14.dp))
                    .padding(horizontal = 12.dp, vertical = 4.dp)
            ) {
                Text(
                    (if (p.female) "Female, " else "Male, ") + p.age,
                    color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold
                )
            }
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(10.dp)
                    .background(Color(0xBF0F1218), RoundedCornerShape(16.dp))
                    .border(1.dp, Color(0x2EFFFFFF), RoundedCornerShape(16.dp))
                    .padding(horizontal = 10.dp, vertical = 8.dp)
            ) {
                Text(
                    p.bubble(), fontSize = 12.5.sp, color = InkText, fontWeight = FontWeight.Bold,
                    modifier = Modifier.widthIn(max = 140.dp), textAlign = TextAlign.Center
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            val v = level >= testLevels[0]
            ResultTile(
                Modifier.weight(1f), "HR", v, testLevels[0], p.tested[0],
                p.hr.toString(), if (p.hrNote != "") "bpm, " + p.hrNote else "bpm"
            )
            ResultTile(
                Modifier.weight(1f), "SpO₂", level >= testLevels[1], testLevels[1], p.tested[1],
                if (p.ox == 0) "None" else p.ox.toString() + "%", if (p.ox == 0) "no reading" else p.oxNote
            )
            ResultTile(
                Modifier.weight(1f), "BP", v, testLevels[0], p.tested[0],
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
        Label("Tests")
        val testGlyphs = listOf("♥", "SpO₂", "💧", "🧠", "ECG")
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            for (k in 0..4) {
                Tool(testNames[k], testGlyphs[k], level < testLevels[k], testLevels[k], p.tested[k], false) { p.test(k) }
            }
        }
        Label("Treatments")
        val treatGlyphs = listOf("O₂", "G", "✚", "CPR", "⚡")
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            for (k in 0..4) {
                Tool(treatNames[k], treatGlyphs[k], level < treatLevels[k], treatLevels[k], p.treated[k], true) { p.treat(k) }
            }
        }
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "$done " + (if (done == 1) "test" else "tests") + " done",
                color = MuteText, fontSize = 13.sp, fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(8.dp))
            GreenButton("To hospital!", Modifier.fillMaxWidth(0.8f)) { onDone() }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun Label(t: String) {
    Text(
        t.uppercase(), color = MuteText, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp,
        modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center
    )
}

@Composable
private fun ResultTile(
    m: Modifier, label: String, unlocked: Boolean, unlockLevel: Int, taken: Boolean, value: String, note: String
) {
    Column(
        modifier = m
            .glassCard(14)
            .padding(vertical = 8.dp, horizontal = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(label, fontSize = 10.sp, color = MuteText, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        when {
            !unlocked -> {
                Text("Locked", fontSize = 16.sp, color = Locked, fontWeight = FontWeight.Bold)
                Text("level $unlockLevel", fontSize = 10.sp, color = Locked, textAlign = TextAlign.Center)
            }
            !taken -> {
                Text("--", fontSize = 18.sp, color = Locked, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                Text("not taken", fontSize = 10.sp, color = Locked, textAlign = TextAlign.Center)
            }
            else -> {
                Text(
                    value, fontSize = if (value.length > 7) 13.sp else 17.sp, color = Good,
                    fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, fontFamily = FontFamily.Monospace
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
        Box(
            modifier = Modifier
                .size(58.dp)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        listOf(Color(0xFF4A5262), Color(0xFF262B35)),
                        center = Offset(60f, 45f), radius = 130f
                    )
                )
                .border(1.dp, if (done) Good else Color(0x29FFFFFF), CircleShape)
                .clickable(enabled = !locked) { onClick() },
            contentAlignment = Alignment.Center
        ) {
            if (locked) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("🔒", fontSize = 14.sp)
                    Text("Level $unlockLevel", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Locked)
                }
            } else {
                Text(
                    glyph, fontSize = if (glyph.length > 3) 14.sp else 17.sp, fontWeight = FontWeight.Bold,
                    color = if (treatment) Color(0xFF6CC4FF) else Good
                )
            }
            if (done) {
                Box(
                    modifier = Modifier.align(Alignment.TopEnd).size(18.dp).background(Color(0xFF2FBF5F), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text("✓", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(label, color = Color(0xFFC9D0DC), fontSize = 11.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
    }
}

// How the patient looks: face, hands and hair follow what is wrong and how the exam is going
fun patientLook(p: Patient): PatientLook {
    val limp = p.dead || (p.kind == ARREST && p.hr == 0)
    val hs = if (p.female) intArrayOf(HS_LONG, HS_BOB, HS_BUN)[p.hairStyle.coerceIn(0, 2)]
        else if (p.age > 60 && p.hairStyle == 2) HS_BALD
        else intArrayOf(HS_SHORT, HS_BUZZ, HS_CURLY)[p.hairStyle.coerceIn(0, 2)]
    val hair = if (p.age > 72) PaintHair[5] else if (p.age > 50) PaintHair[4] else PaintHair[p.hairColor.coerceIn(0, 3)]
    val expr = when {
        limp -> EX_CLOSED
        p.helped -> EX_SMILE
        p.kind == TRAUMA -> EX_PAIN
        p.kind == DIABETIC || p.kind == NEURO -> EX_WEAK
        else -> EX_WORRIED
    }
    val hand = when {
        limp || p.helped -> HAND_LAP
        p.kind == RESP -> HAND_THROAT
        p.kind == CARDIAC -> HAND_CHEST
        else -> HAND_LAP
    }
    val look = Look(
        hs, PaintSkin[p.skin.coerceIn(0, 4)], hair, PaintSkin[0], expr = expr, age = p.age,
        pale = limp || p.kind == DIABETIC,
        sweat = !limp && !p.helped && (p.kind == CARDIAC || p.kind == RESP || p.kind == DIABETIC),
        bandage = p.kind == TRAUMA,
        eyes = if (p.skin >= 3) Color(0xFF2A1C14) else Color(0xFF4A3326)
    )
    return PatientLook(look, p.shirt, p.pants, hand)
}

@Composable
private fun PatientScene(p: Patient, modifier: Modifier) {
    val pl = patientLook(p)
    Canvas(modifier) {
        val sc = size.width / 316f
        scale(sc, Offset.Zero) {
            translate(0f, -12f) { paintedPatient(pl) }
        }
    }
}
