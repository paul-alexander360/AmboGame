package com.cpd360.ambulancerun

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.random.Random

// Credits for each important finding picked in the handoff report
const val HANDOFF_BONUS = 20

// Seconds to treat a patient who gets worse in the back
const val BACK_SECONDS = 12

// Your partner, who rides in the back while you drive
const val PARTNER = "Sam"

// One line of the chat in the back. partner = true for Sam, false for the patient.
class ChatLine(val partner: Boolean, val text: String)

private fun pt(s: String) = ChatLine(false, s)
private fun sam(s: String) = ChatLine(true, s)

// Small talk on the way to the hospital. Most of it is simple or funny.
private val chats = listOf(
    listOf(
        pt("I feel much better, thank you."), sam("Glad to hear it. Hang in there."),
        pt("You are so nice. How long have you done this?"), sam("Twelve years. Still not used to the night shift!")
    ),
    listOf(
        pt("This is my first ambulance ride!"), sam("How is it so far?"),
        pt("Bumpier than I thought."), sam("Our driver is doing their best!")
    ),
    listOf(
        pt("Did I leave the stove on?"), sam("Your neighbor said she'd check on it."),
        pt("Oh good. She's nosy, but she's nice.")
    ),
    listOf(
        pt("Do you two get to pick the music up front?"), sam("Only when the siren's off."),
        pt("Ha! Fair enough.")
    ),
    listOf(
        pt("Can you call my son when we get there?"), sam("Sure. The nurse can help with that too."),
        pt("Thank you. You're very kind.")
    ),
    listOf(
        pt("Are you a doctor?"), sam("I'm a paramedic. The doctors are at the hospital."),
        pt("Well, you're good at this.")
    ),
    listOf(
        pt("Is the driver always this fast?"), sam("Only on the good days."),
        pt("I'll hold on, then.")
    ),
    listOf(
        pt("I'm so sorry about all this trouble."), sam("No trouble at all. That's what we're here for."),
        pt("You're very sweet.")
    )
)

fun rideChat(p: Patient, rnd: Random): List<ChatLine> {
    if (p.kind == ARREST) {
        return listOf(
            pt("Ugh... what happened?"), sam("Your heart stopped. We got it going again."),
            pt("Thank you... I think.")
        )
    }
    return chats[rnd.nextInt(chats.size)]
}

// Which way a patient can get worse on the way, given the treatments you own. -1 = they stay stable.
// 0 oxygen falls, 1 blood sugar drops, 2 bleeding starts again, 4 heart races
fun pickWorse(p: Patient, player: PlayerData, rnd: Random): Int {
    if (p.kind == WELL || p.kind == ARREST || p.level < 3) return -1
    val l = ArrayList<Int>()
    if (player.hasTreat(0) && (p.kind == RESP || p.kind == CARDIAC || p.kind == NEURO)) l.add(0)
    if (player.hasTreat(1) && p.kind == DIABETIC) l.add(1)
    if (player.hasTreat(2) && p.kind == TRAUMA && p.injury > 0) l.add(2)
    if (player.hasTreat(4) && p.kind == CARDIAC) l.add(4)
    if (l.isEmpty() || rnd.nextInt(100) >= 40) return -1
    return l[rnd.nextInt(l.size)]
}

fun makeWorse(p: Patient, k: Int, rnd: Random) {
    fun rr(a: Int, b: Int) = a + rnd.nextInt(b - a + 1)
    when (k) {
        0 -> {
            p.ox = rr(84, 88)
            p.hr = (p.hr + rr(8, 15)).coerceAtMost(150)
        }
        1 -> {
            p.glu = 0
            p.hr = (p.hr + rr(5, 12)).coerceAtMost(140)
        }
        2 -> {
            p.sys = rr(82, 90)
            p.dia = rr(48, 55)
            p.hr = rr(120, 135)
        }
        else -> p.hr = rr(160, 180)
    }
}

// What Sam says happened, with the changed number marked for highlight()
fun worseText(p: Patient, k: Int): String {
    val they = if (p.female) "Her" else "His"
    return when (k) {
        0 -> "$they **oxygen** fell to **" + p.ox + "%**."
        1 -> "$they **blood sugar** dropped. " + (if (p.female) "She's" else "He's") + " shaky and sweaty."
        2 -> "The **bleeding** started again. BP fell to **" + p.sys + "/" + p.dia + "**."
        else -> "$they **heart** is racing at **" + p.hr + "**."
    }
}

// ---------- chat bubbles over the drive ----------

@Composable
fun ChatBubbles(lines: List<ChatLine>, p: Patient, warn: Boolean, modifier: Modifier = Modifier) {
    // The newest three lines; older ones fade
    val shown = lines.takeLast(if (warn) 2 else 3)
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        for ((i, c) in shown.withIndex()) {
            Bubble(c, p, false, Modifier.alpha(if (i < shown.size - 1 || warn) 0.6f else 1f))
        }
        if (warn) Bubble(pt("I don't feel good..."), p, true, Modifier)
    }
}

@Composable
private fun Bubble(c: ChatLine, p: Patient, warn: Boolean, modifier: Modifier) {
    val pl = remember(p) { patientLook(p) }
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = if (c.partner) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Bottom
    ) {
        if (!c.partner) {
            Face(pl.look, false)
            Spacer(Modifier.width(8.dp))
        }
        Column(
            modifier = Modifier
                .widthIn(max = 250.dp)
                .background(
                    when {
                        warn -> Color(0xF5FFECC8)
                        c.partner -> Color(0xEB3FA2FF)
                        else -> Color(0xF0F5F7FA)
                    },
                    RoundedCornerShape(16.dp)
                )
                .border(if (warn) 2.dp else 0.dp, if (warn) Color(0xFFFF9A3A) else Color.Transparent, RoundedCornerShape(16.dp))
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Text(
                if (c.partner) PARTNER.uppercase() + " · YOUR PARTNER" else "PATIENT",
                color = if (c.partner) Color(0xFFD6EBFF) else Color(0xFF6B7386),
                fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp
            )
            Text(
                c.text, color = if (c.partner) Color.White else Color(0xFF1C2230),
                fontSize = 14.sp, fontWeight = FontWeight.Bold
            )
        }
        if (c.partner) {
            Spacer(Modifier.width(8.dp))
            Face(partnerLook(), true)
        }
    }
}

// Sam: short blond hair and the navy uniform
fun partnerLook() = Look(
    HS_SHORT, PaintSkin[0], PaintHair[0], uniformTone(0), FIT_UNIFORM, age = 40,
    eyes = Color(0xFF3D5A80)
)

@Composable
private fun Face(o: Look, partner: Boolean) {
    Canvas(
        Modifier
            .size(36.dp)
            .clip(CircleShape)
            .border(2.dp, Color(0x4DFFFFFF), CircleShape)
    ) { bustFramed(o, if (partner) BG_BAY else BG_OFFICE, FRAME_AVATAR) }
}

// ---------- the patient gets worse: Sam calls you back, then you treat in the back ----------

@Composable
fun BackCare(rs: RouteState, inDrive: Boolean, firstStep: Int = 0, onDone: () -> Unit) {
    val p = rs.patient
    val k = rs.worse
    LaunchedEffect(Unit) { rs.startWorse() }
    var step by remember { mutableIntStateOf(firstStep) }
    var left by remember { mutableIntStateOf(BACK_SECONDS) }
    var picked by remember { mutableIntStateOf(-1) }
    LaunchedEffect(step) {
        while (step < 2 && left > 0) {
            delay(1000)
            left--
        }
        if (step < 2 && left <= 0) {
            rs.finishWorse(false)
            step = 2
        }
    }
    val block = Modifier
        .fillMaxSize()
        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { }
    if (step == 0) {
        Box(block.background(Color(0x8C06080E)), contentAlignment = Alignment.BottomCenter) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .safeDrawingPadding()
                    .padding(10.dp)
                    .clip(RoundedCornerShape(28.dp))
                    .background(Color(0xF2141820))
                    .border(1.dp, CardLine, RoundedCornerShape(28.dp))
            ) {
                AmbulancePic(p, true, Modifier.fillMaxWidth().aspectRatio(316f / 230f))
                Column(Modifier.padding(16.dp)) {
                    Who(Color(0xFFE53935), PARTNER.uppercase() + " · IN THE BACK")
                    Spacer(Modifier.height(4.dp))
                    Text(
                        highlight("\"Get back here, " + (if (p.female) "she's" else "he's") + " getting worse!\" " + worseText(p, k)),
                        color = InkText, fontSize = 16.sp, fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(12.dp))
                    TimerBar(left)
                    Spacer(Modifier.height(12.dp))
                    RedButton("Treat in the back") { step = 1 }
                }
            }
        }
        return
    }
    Column(
        modifier = block
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
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("In the back · " + p.title, color = InkText, fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Box(
                Modifier
                    .background(if (step == 2 && rs.worseOk) Color(0xD92FBF5F) else Color(0xE6E53935), RoundedCornerShape(10.dp))
                    .padding(horizontal = 10.dp, vertical = 3.dp)
            ) {
                Text(if (step == 2) (if (rs.worseOk) "Stable" else "Worse") else "0:%02d".format(left), color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
        }
        Box(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .border(1.dp, CardLine, RoundedCornerShape(20.dp))
        ) {
            AmbulancePic(p, !(step == 2 && rs.worseOk), Modifier.fillMaxWidth().aspectRatio(316f / 230f))
        }
        val was = rs.worseWas
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Vital(Modifier.weight(1f), "Pulse", p.hr.toString(), was[0].toString(), p.hr != was[0])
            Vital(Modifier.weight(1f), "Oxygen", if (p.ox == 0) "None" else p.ox.toString() + "%", was[1].toString() + "%", p.ox != was[1])
            Vital(Modifier.weight(1f), "BP", p.sys.toString() + "/" + p.dia, was[2].toString() + "/" + was[3], p.sys != was[2])
            val gl = listOf("Low", "Normal", "High")
            Vital(Modifier.weight(1f), "Glucose", gl[p.glu.coerceIn(0, 2)], gl[was[4].coerceIn(0, 2)], p.glu != was[4])
        }
        if (step == 1) {
            Text(
                "PICK ONE TREATMENT", color = MuteText, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp,
                modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center
            )
            val glyphs = listOf("O₂", "G", "✚", "CPR", "⚡")
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                for (t in 0..4) {
                    // Only the treatments you've bought can be picked
                    val own = rs.player.hasTreat(t)
                    Column(Modifier.width(58.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            modifier = Modifier
                                .size(52.dp)
                                .clip(CircleShape)
                                .background(
                                    if (own) Brush.radialGradient(listOf(Color(0xFF5AA8FF), Color(0xFF1D5FC0)), center = Offset(55f, 45f), radius = 130f)
                                    else Brush.radialGradient(listOf(Color(0xFF4A4F5C), Color(0xFF2A2E38)), center = Offset(55f, 45f), radius = 130f)
                                )
                                .border(1.dp, Color(0x29FFFFFF), CircleShape)
                                .clickable(enabled = own) {
                                    picked = t
                                    rs.finishWorse(t == k)
                                    step = 2
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(if (own) glyphs[t] else "🔒", color = Color.White, fontSize = if (glyphs[t].length > 2) 13.sp else 17.sp, fontWeight = FontWeight.Bold)
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(treatNames[t].substringBefore(" /"), color = Color(0xFFC9D0DC), fontSize = 11.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                    }
                }
            }
            TimerBar(left)
            Text(
                "The right treatment brings the numbers back. A wrong one, or none, lowers the Patient grade.",
                color = MuteText, fontSize = 12.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)
            )
        } else {
            val ok = rs.worseOk
            Column(
                Modifier
                    .fillMaxWidth()
                    .glassCard(18)
                    .padding(16.dp)
            ) {
                Who(if (ok) ParkGreen else Color(0xFFE53935), PARTNER.uppercase() + " · IN THE BACK")
                Spacer(Modifier.height(4.dp))
                Text(
                    when {
                        ok -> "\"That did it. " + (if (p.female) "She's" else "He's") + " looking better.\""
                        picked < 0 -> "\"Too late, we're nearly there. The ER will take over.\""
                        else -> "\"" + treatNames[picked].substringBefore(" /") + " didn't help. It needed " + treatNames[k].substringBefore(" /").lowercase() + ".\""
                    },
                    color = InkText, fontSize = 16.sp, fontWeight = FontWeight.Bold
                )
            }
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                GreenButton(if (inDrive) "Back to driving" else "Continue", Modifier.fillMaxWidth(0.8f)) {
                    rs.worseAck = true
                    onDone()
                }
            }
        }
    }
}

@Composable
private fun AmbulancePic(p: Patient, sick: Boolean, modifier: Modifier) {
    val pl = patientLook(p)
    Canvas(modifier) {
        val sc = size.width / 316f
        scale(sc, Offset.Zero) { ambulanceBack(pl, if (sick) EX_PAIN else EX_SMILE, sick) }
    }
}

@Composable
private fun Who(c: Color, t: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).background(c, CircleShape))
        Spacer(Modifier.width(6.dp))
        Text(t, color = GlassMute, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.4.sp)
    }
}

@Composable
private fun TimerBar(left: Int) {
    Column {
        Box(
            Modifier
                .fillMaxWidth()
                .height(10.dp)
                .clip(RoundedCornerShape(5.dp))
                .background(Color(0x1AFFFFFF))
        ) {
            Box(
                Modifier
                    .fillMaxWidth(left / BACK_SECONDS.toFloat())
                    .height(10.dp)
                    .background(Brush.horizontalGradient(listOf(Color(0xFFFF7A5A), RouteRed)))
            )
        }
        Spacer(Modifier.height(4.dp))
        Row(Modifier.fillMaxWidth()) {
            Text("Treat within", color = Color(0xFFFF9A96), fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Text("0:%02d".format(left), color = Color(0xFFFF9A96), fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun RedButton(label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(25.dp))
            .background(Brush.verticalGradient(listOf(Color(0xFFEF4A45), Color(0xFFB3241F))))
            .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(25.dp))
            .clickable { onClick() }
            .padding(vertical = 13.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun Vital(m: Modifier, label: String, value: String, was: String, changed: Boolean) {
    Column(
        modifier = m
            .glassCard(14)
            .border(if (changed) 1.dp else 0.dp, if (changed) Color(0xB3E53935) else Color.Transparent, RoundedCornerShape(14.dp))
            .padding(vertical = 8.dp, horizontal = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(label, fontSize = 10.sp, color = MuteText, fontWeight = FontWeight.Bold)
        Text(
            value, fontSize = if (value.length > 5) 14.sp else 17.sp, fontWeight = FontWeight.Bold,
            color = if (changed) Color(0xFFFF6B6B) else Color(0xFF7CF29A)
        )
        Text(if (changed) "was $was" else "same", fontSize = 10.sp, color = Color(0xFF7D8597))
    }
}

// ---------- handoff report ----------

class Finding(val text: String, val key: Boolean)

// Three findings that matter for this call and three that don't, shuffled
fun makeFindings(rs: RouteState, rnd: Random): List<Finding> {
    val p = rs.patient
    val given = (0..4).filter { p.treated[it] && !(p.kind == ARREST && it == 3) }.map { treatNames[it].substringBefore(" /").lowercase() }
    val they = if (p.female) "her" else "his"
    val first = if (p.kind == ARREST) "Found not breathing, CPR brought them back"
        else p.title + ": \"" + p.complaint + "\""
    var second = when (p.kind) {
        CARDIAC -> if (p.tested[4]) (if (p.heartAttack) "ECG shows a heart attack" else "ECG normal") else "Chest pain with pulse " + p.startHr
        RESP -> "Oxygen was " + p.startOx + "% at the scene"
        DIABETIC -> "Blood sugar was " + (if (p.startGlu == 0) "low" else "high")
        TRAUMA -> listOf("Bumped head, no injury found", "Bleeding from a cut", "Broken bone")[p.injury.coerceIn(0, 2)]
        NEURO -> if (p.stroke) "Stroke test positive" else "Dizzy, stroke test negative"
        ARREST -> "Heart started again after CPR"
        else -> "Nothing serious found"
    }
    if (given.isNotEmpty()) second += ", gave " + given.joinToString(" and ")
    val third = if (rs.worseStarted) {
        "Got worse on the way, " + (if (rs.worseOk) treatNames[rs.worse].substringBefore(" /").lowercase() + " helped" else "still not right")
    } else "Pulse " + p.hr + ", BP " + p.sys + "/" + p.dia
    val street = p.address.substringAfter(" ")
    val noise = listOf(
        "Lives on $street", "Has a cat named Biscuit", "Hurt $they wrist last year",
        "Wants $they phone charger", "$they favorite team won last night".replaceFirstChar { it.uppercase() },
        "Parked $they car on the street"
    ).shuffled(rnd).take(3)
    return (listOf(Finding(first, true), Finding(second, true), Finding(third, true)) + noise.map { Finding(it, false) }).shuffled(rnd)
}

@Composable
fun Handoff(rs: RouteState) {
    val items = remember(rs.version) { makeFindings(rs, Random(rs.patient.address.hashCode().toLong() + rs.version)) }
    val chosen = remember(items) { mutableStateListOf<Int>() }
    val nurse = remember { nurseLook() }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { }
            .background(Brush.verticalGradient(listOf(Color(0xFF1C2029), Color(0xFF12151B))))
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .glassCard(18)
                .padding(horizontal = 16.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                (rs.reached?.name ?: "Hospital") + " · Handoff", color = InkText, fontSize = 16.sp,
                fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f)
            )
            Box(
                Modifier
                    .background(Color(0xD92FBF5F), RoundedCornerShape(10.dp))
                    .padding(horizontal = 10.dp, vertical = 3.dp)
            ) {
                Text("+$HANDOFF_BONUS each", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
        }
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(170.dp)
                .clip(RoundedCornerShape(20.dp))
                .border(1.dp, CardLine, RoundedCornerShape(20.dp))
        ) { bustFramed(nurse, BG_ER, FRAME_SHEET) }
        Column(Modifier.padding(horizontal = 6.dp)) {
            Who(Color(0xFF3FA2FF), "NURSE KIM · ER")
            Spacer(Modifier.height(4.dp))
            Text(
                highlight("\"What have you got for me?\" Pick the **3 most important** things to tell her."),
                color = InkText, fontSize = 16.sp, fontWeight = FontWeight.Bold
            )
        }
        for ((i, f) in items.withIndex()) {
            val on = chosen.contains(i)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (on) Color(0x242FBF5F) else Color(0x0DFFFFFF))
                    .border(1.dp, if (on) Color(0x8C2FBF5F) else Color(0x1AFFFFFF), RoundedCornerShape(14.dp))
                    .clickable {
                        if (on) chosen.remove(i) else if (chosen.size < 3) chosen.add(i)
                    }
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier
                        .size(22.dp)
                        .background(if (on) ParkGreen else Color.Transparent, RoundedCornerShape(7.dp))
                        .border(2.dp, if (on) ParkGreen else Color(0x4DFFFFFF), RoundedCornerShape(7.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    if (on) Text("✓", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.width(10.dp))
                Text(f.text, color = InkText, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }
        }
        Spacer(Modifier.height(4.dp))
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            GreenButton("Hand off (" + chosen.size + " of 3)", Modifier.fillMaxWidth(0.85f), enabled = chosen.size == 3) {
                rs.finishHandoff(chosen.count { items[it].key })
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}
