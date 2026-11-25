package com.cpd360.ambulancerun

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Dot the dispatcher and Dr. Ray. Dot gives each call and explains the game the first time
// each step comes up. Dr. Ray explains the exam and each new test or treatment.

const val DR_NAME = "Dr. Ray"
private val Hi = Color(0xFFFFD27A)
private val DotSkinL = Color(0xFFF6C9A0)
private val DotSkinD = Color(0xFFD9956A)
private val DrSkinL = Color(0xFFC98E60)
private val DrSkinD = Color(0xFF9C6440)

// Which call card was answered. Lives above the screen switch so it survives page changes.
class GuideState {
    var callFor by mutableIntStateOf(-1)
    var introPage by mutableIntStateOf(0)
}

// ---------- Drawing ----------

private fun svg(d: String): Path = PathParser().parsePathString(d).toPath()

private fun DrawScope.fillOut(d: String, c: Color, w: Float = 2.5f) {
    val p = svg(d)
    drawPath(p, c)
    drawPath(p, ArtInk, style = Stroke(w, join = StrokeJoin.Round))
}

private fun DrawScope.line(d: String, c: Color, w: Float) {
    drawPath(svg(d), c, style = Stroke(w, cap = StrokeCap.Round, join = StrokeJoin.Round))
}

private fun DrawScope.box(c: Color, x: Float, y: Float, w: Float, h: Float, r: Float, out: Float = 2.5f) {
    drawRoundRect(c, Offset(x, y), Size(w, h), CornerRadius(r, r))
    if (out > 0f) drawRoundRect(ArtInk, Offset(x, y), Size(w, h), CornerRadius(r, r), style = Stroke(out))
}

private fun DrawScope.dotC(c: Color, x: Float, y: Float, r: Float, out: Float = 2.5f) {
    drawCircle(c, r, Offset(x, y))
    if (out > 0f) drawCircle(ArtInk, r, Offset(x, y), style = Stroke(out))
}

// Dot at her desk, drawn in a 330 x 250 box
fun DrawScope.dispatcherScene() {
    drawRect(Brush.verticalGradient(listOf(Color(0xFF8FA3D9), Color(0xFF6F82C0)), 0f, 250f), Offset(-300f, -100f), Size(930f, 400f))
    // wall map
    box(Color(0xFF9BD468), 14f, 18f, 112f, 78f, 8f, 0f)
    line("M14 46H126M14 72H126M44 18V96M80 18V96M108 18V96", Color(0xFF8E93C9), 6f)
    dotC(RouteRed, 80f, 46f, 7f, 2f)
    drawRoundRect(ArtInk, Offset(14f, 18f), Size(112f, 78f), CornerRadius(8f, 8f), style = Stroke(2.5f))
    // on-air light
    dotC(RouteRed, 150f, 30f, 11f)
    drawCircle(Color.White, 4f, Offset(147f, 27f), alpha = 0.6f)
    // chair
    box(GlassSolid, 62f, 118f, 116f, 110f, 26f)
    // body
    fillOut("M70 250 C70 186 84 160 120 160 C156 160 170 186 170 250Z", Color(0xFF26A69A))
    fillOut("M104 162 L120 182 L136 162Z", Color.White)
    box(Color.White, 138f, 196f, 18f, 22f, 3f, 2f)
    drawRect(Color(0xFF0A72B8), Offset(141f, 200f), Size(12f, 7f))
    // neck, bun, head
    drawRect(DotSkinD, Offset(111f, 142f), Size(18f, 20f))
    dotC(Color(0xFF5D3A1A), 120f, 74f, 15f)
    drawCircle(Brush.radialGradient(listOf(DotSkinL, DotSkinD), Offset(108f, 104f), 48f), 34f, Offset(120f, 118f))
    drawCircle(ArtInk, 34f, Offset(120f, 118f), style = Stroke(2.5f))
    fillOut("M86 116 C82 82 158 82 154 116 C150 98 136 92 120 94 C104 92 90 98 86 116Z", Color(0xFF5D3A1A))
    // eyes, brows, mouth
    dotC(Color.White, 108f, 118f, 9f)
    drawCircle(ArtInk, 4f, Offset(110f, 119f))
    dotC(Color.White, 133f, 118f, 9f)
    drawCircle(ArtInk, 4f, Offset(135f, 119f))
    line("M99 105 Q107 101 115 104M126 104 Q134 101 142 105", ArtInk, 2.5f)
    fillOut("M112 134 Q121 143 130 134 Q121 138 112 134Z", Color(0xFF8E2A2A), 2f)
    // headset
    line("M84 116 C80 72 160 72 156 116", ArtInk, 7f)
    line("M84 116 C80 72 160 72 156 116", Color(0xFF455A64), 3.5f)
    box(Color(0xFF37474F), 76f, 106f, 14f, 24f, 6f)
    box(Color(0xFF37474F), 150f, 106f, 14f, 24f, 6f)
    line("M84 128 Q88 146 108 142", ArtInk, 3.5f)
    dotC(RouteRed, 109f, 142f, 4.5f, 2f)
    // desk
    drawRect(Color(0xFFB07A4A), Offset(-300f, 212f), Size(930f, 60f))
    drawRect(Color(0xFFC9935E), Offset(-300f, 212f), Size(930f, 7f))
    drawLine(ArtInk, Offset(-300f, 212f), Offset(630f, 212f), 2.5f)
    // radio and hand
    box(Color(0xFF37474F), 176f, 190f, 40f, 26f, 6f)
    dotC(RouteRed, 196f, 200f, 6f, 2f)
    line("M160 200 Q176 206 188 204", ArtInk, 18f)
    line("M160 200 Q176 206 188 204", Color(0xFF26A69A), 13f)
    dotC(DotSkinL, 190f, 203f, 8f)
    // monitor
    box(Color(0xFF263238), 226f, 146f, 90f, 62f, 8f)
    box(Color(0xFF9BD468), 233f, 153f, 76f, 48f, 4f, 0f)
    line("M233 170H309M233 188H309M258 153V201M284 153V201", Color(0xFF8E93C9), 4f)
    line("M246 196 L258 170 L284 170 L284 160", RouteRed, 3f)
    drawRect(Color(0xFF263238), Offset(263f, 208f), Size(16f, 6f))
    // mug
    box(Color(0xFFFFD54F), 20f, 192f, 24f, 24f, 4f)
}

// Dr. Ray in his office, drawn in a 330 x 250 box
fun DrawScope.doctorScene() {
    drawRect(Brush.verticalGradient(listOf(Color(0xFFCFE8F3), Color(0xFFA9D3E6)), 0f, 250f), Offset(-300f, -100f), Size(930f, 400f))
    // eye chart
    box(Color.White, 20f, 20f, 62f, 86f, 6f)
    drawRect(ArtInk, Offset(42f, 32f), Size(18f, 16f))
    drawRect(ArtInk, Offset(36f, 58f), Size(12f, 9f))
    drawRect(ArtInk, Offset(54f, 58f), Size(12f, 9f))
    for (k in 0..2) drawRect(ArtInk, Offset(34f + 12f * k, 80f), Size(7f, 6f))
    // heart poster
    box(Color.White, 236f, 20f, 74f, 60f, 6f)
    fillOut("M273 66 C250 50 252 34 263 34 C268 34 272 38 273 42 C274 38 278 34 283 34 C294 34 296 50 273 66Z", RouteRed, 2f)
    // white coat
    fillOut("M96 250 C96 186 112 160 160 160 C208 160 224 186 224 250Z", Color(0xFFFAFAFA))
    fillOut("M144 162 L160 196 L176 162Z", Color(0xFF5C6BC0))
    fillOut("M154 176 L160 200 L166 176Z", RouteRed, 2f)
    line("M140 162 L156 214 M180 162 L164 214", ArtInk, 2.5f)
    box(Color.White, 182f, 204f, 24f, 18f, 3f, 2f)
    drawRect(Color(0xFF0A72B8), Offset(188f, 196f), Size(4f, 12f))
    // stethoscope
    line("M140 166 C126 196 132 214 146 218 C160 222 168 208 170 196", ArtInk, 6f)
    line("M140 166 C126 196 132 214 146 218 C160 222 168 208 170 196", Color(0xFF546E7A), 3f)
    dotC(Color(0xFFB0BEC5), 170f, 194f, 7f)
    // neck and head
    drawRect(DrSkinD, Offset(151f, 140f), Size(18f, 22f))
    drawCircle(Brush.radialGradient(listOf(DrSkinL, DrSkinD), Offset(148f, 100f), 50f), 35f, Offset(160f, 114f))
    drawCircle(ArtInk, 35f, Offset(160f, 114f), style = Stroke(2.5f))
    fillOut("M126 116 C122 92 132 82 140 84 C136 96 134 108 134 122Z", Color(0xFFCFD8DC))
    fillOut("M194 116 C198 92 188 82 180 84 C184 96 186 108 186 122Z", Color(0xFFCFD8DC))
    drawOval(Color.White, Offset(141f, 87f), Size(18f, 10f), alpha = 0.3f)
    // eyes and glasses
    dotC(Color.White, 147f, 114f, 8f)
    drawCircle(ArtInk, 3.6f, Offset(149f, 115f))
    dotC(Color.White, 173f, 114f, 8f)
    drawCircle(ArtInk, 3.6f, Offset(175f, 115f))
    drawRoundRect(ArtInk, Offset(135f, 103f), Size(24f, 22f), CornerRadius(7f, 7f), style = Stroke(3f))
    drawRoundRect(ArtInk, Offset(161f, 103f), Size(24f, 22f), CornerRadius(7f, 7f), style = Stroke(3f))
    line("M139 98 Q147 94 155 97M165 97 Q173 94 181 98", Color(0xFFCFD8DC), 4f)
    // mustache and smile
    fillOut("M146 134 Q160 126 174 134 Q166 138 160 134 Q154 138 146 134Z", Color(0xFFCFD8DC), 2f)
    line("M152 140 Q160 146 168 140", ArtInk, 2.5f)
    // raised hand
    line("M216 216 Q240 196 244 170", ArtInk, 20f)
    line("M216 216 Q240 196 244 170", Color(0xFFFAFAFA), 15f)
    dotC(DrSkinL, 245f, 164f, 9f)
    box(DrSkinL, 242f, 146f, 6f, 16f, 3f, 2f)
    // desk
    drawRect(Color(0xFF90A4AE), Offset(-300f, 226f), Size(930f, 60f))
    drawLine(ArtInk, Offset(-300f, 226f), Offset(630f, 226f), 2.5f)
}

// Draws a scene to fill the canvas, cropping the top 30 units. shift moves the person left.
private fun DrawScope.fitScene(dr: Boolean, shift: Float) {
    val sc = maxOf(size.width / 330f, size.height / 220f)
    val tx = (size.width - 330f * sc) / 2f
    val ty = (size.height - 220f * sc) / 2f - 30f * sc
    translate(tx, ty) {
        scale(sc, Offset.Zero) {
            translate(shift, 0f) { if (dr) doctorScene() else dispatcherScene() }
        }
    }
}

// Small painted portrait of Dr. Ray for the menu
fun DrawScope.doctorIcon() {
    val c = Path()
    c.addOval(Rect(Offset.Zero, size))
    clipPath(c) { bustFramed(rayLook(), BG_OFFICE, FRAME_AVATAR) }
}

// Dot or Dr. Ray, painted, with what they say as a headline across the bottom of the picture
@Composable
fun GuideScene(dr: Boolean, bubble: String, modifier: Modifier) {
    val look = remember(dr) { if (dr) rayLook() else dotLook() }
    // Clip so the painted person stays inside the picture and never covers the text below
    Box(modifier.clipToBounds()) {
        Canvas(Modifier.fillMaxSize()) { bustFramed(look, if (dr) BG_OFFICE else BG_DISPATCH, FRAME_SHEET) }
        if (bubble != "") {
            Box(
                Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xCC0B0E14))))
                    .padding(start = 16.dp, end = 16.dp, top = 22.dp, bottom = 10.dp)
            ) {
                Text(bubble, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold, lineHeight = 19.sp)
            }
        }
    }
}

// Text with **words** in yellow
fun highlight(s: String): AnnotatedString = buildAnnotatedString {
    val parts = s.split("**")
    for (i in parts.indices) {
        if (i % 2 == 1) {
            pushStyle(SpanStyle(color = Hi))
            append(parts[i])
            pop()
        } else append(parts[i])
    }
}

// The card both guides use: scene, speech bubble, who is talking, message and one button.
@Composable
fun GuideCard(
    dr: Boolean,
    bubble: String,
    body: String,
    button: String,
    extra: @Composable ColumnScope.() -> Unit = {},
    onButton: () -> Unit
) {
    BackHandler { onButton() }
    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0x73141830))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { }
    ) {
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .safeDrawingPadding()
                .padding(10.dp)
                .clip(RoundedCornerShape(28.dp))
                .background(Color(0xEB141820))
                .border(1.dp, Color(0x24FFFFFF), RoundedCornerShape(28.dp))
        ) {
            GuideScene(dr, bubble, Modifier.fillMaxWidth().height(190.dp))
            Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(9.dp)
                        .background(if (dr) Color(0xFF3FA2FF) else RouteRed, CircleShape)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    if (dr) "DR. RAY · MEDICAL" else "DISPATCH · DOT",
                    color = GlassMute, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(highlight(body), color = InkText, fontSize = 16.sp, fontWeight = FontWeight.Bold, lineHeight = 22.sp)
            extra()
            Spacer(Modifier.height(14.dp))
            GreenButton(button, Modifier.fillMaxWidth()) { onButton() }
            }
        }
    }
}

@Composable
private fun Chip(t: String) {
    Box(
        Modifier
            .background(Color(0x12FFFFFF), RoundedCornerShape(10.dp))
            .border(1.dp, Color(0x1FFFFFFF), RoundedCornerShape(10.dp))
            .padding(horizontal = 9.dp, vertical = 5.dp)
    ) {
        Text(t, color = Color(0xFFCFD5E2), fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun RedChip(t: String) {
    Box(
        Modifier
            .background(Color(0x38E53935), RoundedCornerShape(10.dp))
            .border(1.dp, Color(0x8CE53935), RoundedCornerShape(10.dp))
            .padding(horizontal = 9.dp, vertical = 5.dp)
    ) {
        Text(t, color = Color(0xFFFF9A96), fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

private val loopSteps = listOf(
    "I send you a call", "Drag a route to the patient", "Check them and help them",
    "Pick the right hospital", "Get graded, earn credits", "Five good runs clear a city"
)

// ---------- Dot on the map ----------

// Dot's tip for the map right now, or "" for none. The call card is handled separately.
fun mapTip(rs: RouteState, p: PlayerData, guide: GuideState): String {
    if (rs.stage == 0 && !rs.done && !rs.started && rs.path.isEmpty() && guide.callFor != rs.version) {
        if (rs.kitNeeded() && !p.seen("dr")) return "dr"
        if ((rs.idx > 0 || rs.level > 1) && !p.seen("city") && p.seen("intro")) return "city"
    }
    if (rs.stage == 1 && !rs.done && rs.path.isEmpty() && !p.seen("hosp")) return "hosp"
    if (ACCIDENTS_ON && rs.stage == 0 && rs.done && !rs.checking && rs.accSeg < 0 && rs.driveShown && !p.seen("risk")) return "risk"
    if (rs.runOver && rs.driveShown && rs.gTo >= 0 && !p.seen("grade")) return "grade"
    return ""
}

@Composable
fun DotTip(key: String, rs: RouteState, onDone: () -> Unit) {
    when (key) {
        "dr" -> GuideCard(
            false, "City 5 cleared! Nice job.",
            "To reach Level " + (rs.level + 1) + " you need new skills. Open the menu and see **$DR_NAME**. " +
                "Lessons cost credits, so keep running calls here until you have enough.",
            "Got it", onButton = onDone
        )
        "city" -> GuideCard(
            false, "Welcome to a new city!",
            "Each city has its own streets and hospitals. Get **5 runs with no F** here to open the next city. " +
                "After City 5, $DR_NAME teaches you the next level.",
            "Got it", onButton = onDone
        )
        "hosp" -> GuideCard(
            false, "Nice work! Now the hospital.",
            "Drag from the **ambulance** to a hospital that treats this patient. **Double tap** a hospital to see " +
                "what it treats. The wrong one means a bad grade.",
            "Got it", onButton = onDone
        )
        "risk" -> GuideCard(
            false, "Watch the traffic.",
            "Busy streets raise your **crash risk**, and a crash can end the run. Quieter roads, better brakes " +
                "and driving skills keep it low.",
            "Got it", onButton = onDone
        )
        "grade" -> GuideCard(
            false, "Here's how you did!",
            "You get a grade for each part: **drive to the call**, **patient exam** and **drive to the hospital**. " +
                "A run with no F counts toward the 5 you need to clear the city.",
            "Got it", onButton = onDone
        )
    }
}

// The call card at the start of every run. On your very first run it starts with the game loop.
@Composable
fun CallCard(rs: RouteState, p: PlayerData, guide: GuideState, target: Double, onRespond: () -> Unit) {
    val first = !p.seen("intro")
    if (first && guide.introPage == 0) {
        GuideCard(
            false, "Hi " + p.name + "! I'm Dot, your dispatcher.", "Here's how a run works:", "Next",
            extra = {
                Spacer(Modifier.height(8.dp))
                for (r in 0..2) {
                    Row(Modifier.fillMaxWidth().padding(bottom = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        for (c in 0..1) {
                            val i = r * 2 + c
                            Row(
                                Modifier
                                    .weight(1f)
                                    .background(Color(0x12FFFFFF), RoundedCornerShape(10.dp))
                                    .border(1.dp, Color(0x1FFFFFFF), RoundedCornerShape(10.dp))
                                    .padding(horizontal = 8.dp, vertical = 6.dp)
                            ) {
                                Text("" + (i + 1) + " ", color = Hi, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                Text(loopSteps[i], color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
                Text("1 of 2", color = GlassMute, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.End))
            }
        ) { guide.introPage = 1 }
        return
    }
    val pt = rs.patient
    val who = (if (pt.female) "Woman, " else "Man, ") + pt.age
    var body = (if (first) "First call: " else "Respond to ") + "**" + pt.address + "**. " + who + ", **" +
        pt.title.lowercase() + "**."
    if (first) body += " Drag a route from your station to the glowing house. Pinch to zoom, two fingers to move."
    GuideCard(
        false,
        if (first) "Your first call! Let's go." else "Medic " + rs.level + ", you've got a call!",
        body, "Respond",
        extra = {
            if (!first) {
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    RedChip(pt.title)
                    Chip("Run " + minOf(p.goodRuns + 1, RUNS_PER_CITY) + " of " + RUNS_PER_CITY)
                    Chip("Target " + fmt(target))
                }
            } else {
                Text("2 of 2", color = GlassMute, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.End))
            }
        }
    ) {
        if (first) p.markSeen("intro")
        guide.introPage = 0
        onRespond()
    }
}

// ---------- Dr. Ray on the exam screen ----------

fun examTip(level: Int, p: PlayerData): String {
    if (!p.seen("exam")) return "exam"
    if (level >= 2 && !p.seen("lv$level")) return "lv$level"
    return ""
}

private fun skillTip(level: Int): Pair<String, String> = when (level) {
    2 -> "New skill: Vitals!" to "Tap **Vitals** on every patient who is awake. It checks blood pressure and pulse. Numbers in red mean trouble."
    3 -> "New skill: Oxygen!" to "Trouble breathing? Run **Pulse ox**. Under 93% is low and under 90% is dangerous, so give **Oxygen** before you leave."
    4 -> "New skill: Glucose!" to "Weak or shaky? Run the **Glucose** test. Low sugar, give **Glucose**. High sugar needs the hospital, not glucose."
    5 -> "New skill: Splints!" to "Bad cut or broken bone? Use **Splint / Stop bleeding** before you go. A sore bump on the head doesn't need it."
    6 -> "New skill: CPR!" to "Not breathing and no pulse? Start **CPR** right away. It doesn't always work, but without it they have no chance."
    7 -> "New skill: Stroke test!" to "Dizzy or feeling strange? Run the **Stroke** test and **Glucose**. A stroke needs a bigger hospital."
    else -> "New skill: ECG and Shock!" to "Chest pain? Run the **ECG**. If the pulse is under 80 or over 120, a **Shock** brings it back to normal."
}

@Composable
fun DrTip(key: String, level: Int, onDone: () -> Unit) {
    if (key == "exam") {
        GuideCard(
            true, "I'm $DR_NAME. Let's look after your patient.",
            if (level <= 1) "Your patient tells you what's wrong. As you move up, I'll teach you **tests** that show what's wrong " +
                "and **treatments** that help. When you're ready, tap **To hospital!**"
            else "**Tests** show what's wrong. **Treatments** help. Read the numbers: red means trouble. " +
                "When you're done, tap **To hospital!**",
            "Start exam", onButton = onDone
        )
    } else {
        val t = skillTip(level)
        GuideCard(true, t.first, t.second, "Got it", onButton = onDone)
    }
}
