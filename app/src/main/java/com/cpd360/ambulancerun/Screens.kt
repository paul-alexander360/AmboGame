package com.cpd360.ambulancerun

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Dispatch look. Colors are taken from the city map: the road lavender (deepened for panels),
// the route red, the park green and the call-glow yellow.
val Glass = Color(0xEB3B3F6E)
val GlassSolid = Color(0xFF3B3F6E)
val GlassLine = Color(0x26FFFFFF)
val GlassMute = Color(0xFFC3C6EE)
val RouteRed = Color(0xFFE53935)
val ParkGreen = Color(0xFF8FCB59)
val CallYellow = Color(0xFFFFE66B)
val PageBg = Color(0xFFEEEFFA)
val InkText = Color(0xFF2B2F4A)
val MuteText = Color(0xFF7378A0)
val CardLine = Color(0xFFE1E3F3)
val SoftFill = Color(0xFFEDEEFA)

@Composable
fun Coin(sizeDp: Int) {
    Box(
        modifier = Modifier
            .size(sizeDp.dp)
            .background(Color(0xFFFFC94D), CircleShape)
            .border(1.5.dp, Color(0xFFE09B00), CircleShape)
    )
}

@Composable
fun ScreenHeader(title: String, credits: Int, onBack: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(GlassSolid, RoundedCornerShape(18.dp))
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(GlassLine)
                .clickable { onBack() },
            contentAlignment = Alignment.Center
        ) {
            Text("‹", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.width(12.dp))
        Text(title, color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        CreditPill(credits)
    }
}

@Composable
fun CreditPill(credits: Int) {
    Row(
        modifier = Modifier.padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Coin(18)
        Spacer(Modifier.width(6.dp))
        Text("%,d".format(credits), color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun WhiteCard(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White, RoundedCornerShape(16.dp))
            .border(1.dp, CardLine, RoundedCornerShape(16.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        content = content
    )
}

@Composable
fun SectionTitle(t: String) {
    Text(
        t.uppercase(), color = MuteText, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp,
        modifier = Modifier.fillMaxWidth().padding(start = 4.dp, top = 6.dp)
    )
}

@Composable
fun Swatch(c: Color, selected: Boolean, locked: Boolean = false, lockLabel: String = "", onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .border(if (selected) 2.5.dp else 0.dp, if (selected) InkText else Color.Transparent, CircleShape)
                .padding(4.dp)
                .clip(CircleShape)
                .background(if (locked) SoftFill else c)
                .border(1.dp, CardLine, CircleShape)
                .clickable(enabled = !locked) { onClick() },
            contentAlignment = Alignment.Center
        ) {
            if (locked) Text("🔒", fontSize = 13.sp)
            else if (selected) Text("✓", color = if (c.red + c.green + c.blue > 2.2f) InkText else Color.White, fontWeight = FontWeight.Bold)
        }
        if (lockLabel != "") Text(lockLabel, fontSize = 9.sp, color = MuteText, textAlign = TextAlign.Center)
    }
}

@Composable
fun Choice(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) GlassSolid else SoftFill)
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Text(label, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = if (selected) Color.White else InkText)
    }
}

@Composable
fun LabeledRow(label: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, fontSize = 12.sp, color = MuteText, fontWeight = FontWeight.Bold)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) { content() }
    }
}

@Composable
fun UpgradeCard(glyph: String, name: String, desc: String, level: Int, credits: Int, extra: String = "", onBuy: () -> Unit) {
    val maxed = level >= MAX_UP
    val cost = upgradeCost(level)
    val canBuy = !maxed && credits >= cost
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White, RoundedCornerShape(16.dp))
            .border(1.dp, CardLine, RoundedCornerShape(16.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .background(SoftFill, RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text(glyph, fontSize = if (glyph.length > 2) 13.sp else 20.sp, color = GlassSolid, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(name, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = InkText, modifier = Modifier.weight(1f))
                Text("Lv $level/$MAX_UP", fontSize = 11.sp, color = MuteText, fontWeight = FontWeight.Bold)
            }
            Text(desc, fontSize = 11.sp, color = MuteText)
            if (extra != "") Text(extra, fontSize = 11.sp, color = RouteRed, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                for (i in 0 until MAX_UP) {
                    Box(
                        Modifier
                            .weight(1f)
                            .height(5.dp)
                            .background(if (i < level) RouteRed else SoftFill, RoundedCornerShape(3.dp))
                    )
                }
            }
        }
        Spacer(Modifier.width(10.dp))
        if (maxed) {
            Box(
                modifier = Modifier
                    .background(Color(0xFF6FAE3E), RoundedCornerShape(12.dp))
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            ) {
                Text("MAX", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
            }
        } else {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (canBuy) GlassSolid else SoftFill)
                    .clickable(enabled = canBuy) { onBuy() }
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Coin(14)
                Spacer(Modifier.width(5.dp))
                Text("%,d".format(cost), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = if (canBuy) Color.White else MuteText)
            }
        }
    }
}

@Composable
private fun Page(title: String, player: PlayerData, onBack: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    BackHandler { onBack() }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(PageBg)
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        ScreenHeader(title, player.credits, onBack)
        content()
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
fun CharacterScreen(player: PlayerData, onBack: () -> Unit) {
    Page("Character", player, onBack) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.White, RoundedCornerShape(18.dp))
                .border(1.dp, CardLine, RoundedCornerShape(18.dp))
                .padding(8.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Brush.verticalGradient(listOf(Color(0xFFF3F4FC), Color(0xFFDCDFF5))))
                    .padding(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Canvas(Modifier.width(120.dp).aspectRatio(170f / 330f)) {
                    scale(size.width / 170f, Offset.Zero) {
                        paramedic(player.female, player.skin, player.hairStyle, player.hairColor, player.uniform)
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(player.name, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = InkText)
                    Text("Paramedic, " + player.org, fontSize = 12.sp, color = MuteText)
                    Box(
                        Modifier.background(RouteRed, RoundedCornerShape(12.dp)).padding(horizontal = 12.dp, vertical = 3.dp)
                    ) {
                        Text("Level " + player.level, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                    Text("Driver rating " + "%.2f".format(player.drive), fontSize = 12.sp, color = MuteText)
                }
            }
        }
        SectionTitle("Look")
        WhiteCard {
            LabeledRow("Name") {
                BasicTextField(
                    value = player.name,
                    onValueChange = { player.name = it.take(16); player.save() },
                    singleLine = true,
                    textStyle = TextStyle(fontSize = 16.sp, color = InkText, fontWeight = FontWeight.Bold),
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFFF2F3FB), RoundedCornerShape(10.dp))
                        .border(2.dp, Color(0xFFC9CCEA), RoundedCornerShape(10.dp))
                        .padding(horizontal = 12.dp, vertical = 10.dp)
                )
            }
            LabeledRow("Gender") {
                Choice("Male", !player.female) { player.female = false; player.save() }
                Choice("Female", player.female) { player.female = true; player.save() }
            }
            LabeledRow("Skin") {
                for (i in 0..2) Swatch(SkinLight[i], player.skin == i) { player.skin = i; player.save() }
            }
            LabeledRow("Hair style") {
                for (i in 0..2) Choice("Style " + (i + 1), player.hairStyle == i) { player.hairStyle = i; player.save() }
            }
            LabeledRow("Hair color") {
                for (i in 0..3) Swatch(HairCols[i], player.hairColor == i) { player.hairColor = i; player.save() }
            }
            LabeledRow("Uniform") {
                for (i in UniformCols.indices) Swatch(UniformCols[i], player.uniform == i) { player.uniform = i; player.save() }
            }
        }
        SectionTitle("Speed")
        SpeedCard(player)
        SectionTitle("Skills")
        UpgradeCard("🚑", "Driving skill", "Faster drive times on every road. About 1.3% faster per level.", player.charUp[SKILL_DRIVE], player.credits) {
            player.buyChar(SKILL_DRIVE)
        }
        UpgradeCard("🚶", "Pedestrian awareness", "Crash risk down 8% per level.", player.charUp[SKILL_PED], player.credits) {
            player.buyChar(SKILL_PED)
        }
        UpgradeCard("🚗", "Crash avoidance", "Crash risk down 8% per level.", player.charUp[SKILL_CRASH], player.credits) {
            player.buyChar(SKILL_CRASH)
        }
    }
}

private val ambParts = listOf(
    Triple("◎", "Tires", "Faster speed. About 1% faster per level."),
    Triple("⚙", "Engine", "Faster speed. About 1% faster per level."),
    Triple("▣", "Brakes", "Crash risk down 4% per level."),
    Triple("☀", "Lights", "Crash risk down 4% per level and quicker through intersections."),
    Triple("🔊", "Siren", "Crash risk down 4% per level and quicker through intersections."),
    Triple("🚚", "Cab", "Faster speed. About 1% faster per level."),
    Triple("✚", "Patient compartment", "Needed to move up levels. Unlocks more tests, treatments and paint colors.")
)

@Composable
fun AmbulanceScreen(player: PlayerData, onBack: () -> Unit) {
    val t = rememberInfiniteTransition(label = "lights")
    val flash by t.animateFloat(0f, 1f, infiniteRepeatable(tween(700, easing = LinearEasing), RepeatMode.Restart), label = "f")
    Page("Ambulance", player, onBack) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.White, RoundedCornerShape(18.dp))
                .border(1.dp, CardLine, RoundedCornerShape(18.dp))
                .padding(8.dp)
        ) {
            Canvas(
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(12.dp))
            ) {
                val w = size.width
                val h = size.height
                drawRect(Brush.verticalGradient(listOf(Color(0xFF7FD0FF), Color(0xFF2A8FE0)), endY = h * 0.72f), size = Size(w, h * 0.72f))
                drawRect(Color(0xFF9BD468), Offset(0f, h * 0.72f), Size(w, h * 0.28f))
                drawRect(Color(0xFF7FB851), Offset(0f, h * 0.72f), Size(w, h * 0.025f))
                drawCircle(Color.White, h * 0.08f, Offset(w * 0.15f, h * 0.2f), alpha = 0.8f)
                drawCircle(Color.White, h * 0.1f, Offset(w * 0.22f, h * 0.18f), alpha = 0.8f)
                val s = w * 0.86f / 1780f
                translate(w * 0.07f, h * 0.74f - 875f * s) {
                    scale(s, Offset.Zero) { ambulanceSide(player.ambColor, flash < 0.5f) }
                }
            }
        }
        SectionTitle("Paint")
        WhiteCard {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                for (i in AmbColors.indices) {
                    val locked = player.ambUp[COMPARTMENT] < i
                    Swatch(
                        AmbColors[i], player.ambColor == i, locked,
                        if (locked) "Comp. Lv $i" else AmbColorNames[i]
                    ) { player.ambColor = i; player.save() }
                }
            }
        }
        SectionTitle("Speed")
        SpeedCard(player)
        SectionTitle("Upgrades")
        for (k in ambParts.indices) {
            val (g, n, d) = ambParts[k]
            val extra = if (k == COMPARTMENT) "Next player level needs Lv " + minOf(MAX_UP, player.level) else ""
            UpgradeCard(g, n, d, player.ambUp[k], player.credits, extra) { player.buyAmb(k) }
        }
    }
}

@Composable
private fun SettingRow(title: String, desc: String, on: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = InkText)
            Text(desc, fontSize = 11.sp, color = MuteText)
        }
        Switch(
            checked = on, onCheckedChange = onChange,
            colors = SwitchDefaults.colors(checkedTrackColor = Color(0xFF6FAE3E), checkedThumbColor = Color.White)
        )
    }
}

@Composable
fun SettingsScreen(player: PlayerData, version: String, onBack: () -> Unit) {
    Page("Settings", player, onBack) {
        WhiteCard {
            SettingRow("Siren sound", "Play the siren during drive scenes.", player.sirenSound) {
                player.sirenSound = it
                player.save()
            }
            SettingRow("Drive scenes", "Show the view from the driver seat after you trace a route.", player.driveScenes) {
                player.driveScenes = it
                player.save()
            }
        }
        WhiteCard {
            Text("Tips", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = InkText)
            Text("Dot and $DR_NAME explain each step the first time it comes up.", fontSize = 11.sp, color = MuteText)
            Spacer(Modifier.height(8.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (player.tipsSeen.isEmpty()) SoftFill else GlassSolid)
                    .clickable(enabled = player.tipsSeen.isNotEmpty()) { player.replayTips() }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "Replay tips",
                    fontSize = 14.sp, fontWeight = FontWeight.Bold,
                    color = if (player.tipsSeen.isEmpty()) MuteText else Color.White
                )
            }
        }
        WhiteCard {
            Text("Player", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = InkText)
            Text("Level " + player.level + "   XP " + player.totalXp + "   Credits " + "%,d".format(player.credits), fontSize = 13.sp, color = MuteText)
        }
        Text("Version $version", color = MuteText, fontSize = 12.sp, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
    }
}

// One lesson on Dr. Ray's page. state: 0 learned, 1 next (buyable when canBuy), 2 later
@Composable
private fun KitCard(lv: Int, state: Int, canBuy: Boolean, note: String, onBuy: () -> Unit) {
    val i = lv - 1
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (state == 2) Color(0x99FFFFFF) else Color.White, RoundedCornerShape(16.dp))
            .border(if (state == 1) 2.dp else 1.dp, if (state == 1) RouteRed else CardLine, RoundedCornerShape(16.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .background(SoftFill, RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text(kitGlyphs[i], fontSize = 20.sp)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                "Level $lv · " + kitNames[i], fontSize = 15.sp, fontWeight = FontWeight.Bold,
                color = if (state == 2) MuteText else InkText
            )
            Text(kitDescs[i], fontSize = 11.sp, color = MuteText)
            if (note != "") Text(note, fontSize = 11.sp, color = RouteRed, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.width(10.dp))
        when (state) {
            0 -> Box(
                modifier = Modifier
                    .background(Color(0xFF6FAE3E), RoundedCornerShape(12.dp))
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Text("LEARNED", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
            }
            else -> Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (canBuy) GlassSolid else SoftFill)
                    .clickable(enabled = canBuy) { onBuy() }
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (state == 1) {
                    Coin(14)
                    Spacer(Modifier.width(5.dp))
                }
                Text(
                    "%,d".format(kitCost(lv)), fontSize = 14.sp, fontWeight = FontWeight.Bold,
                    color = if (canBuy) Color.White else MuteText
                )
            }
        }
    }
}

// Dr. Ray's page: the lessons that teach each level's tests and treatments.
// level is the level you are on; ready is true once City 5 of that level is cleared.
@Composable
fun DrScreen(player: PlayerData, level: Int, ready: Boolean, onBuy: () -> Unit, onBack: () -> Unit) {
    Page(DR_NAME, player, onBack) {
        val bubble = if (level >= MAX_LEVEL) "You've learned everything I know. Great work!"
            else if (!ready) "Clear City 5 and I'll teach you the next skills."
            else if (player.credits < kitCost(level + 1)) "Ready when you are. Earn a few more credits first."
            else "You're ready for Level " + (level + 1) + "!"
        GuideScene(true, bubble, Modifier.fillMaxWidth().height(150.dp))
        SectionTitle("You've learned")
        for (lv in 1..level) KitCard(lv, 0, false, "") { }
        if (level < MAX_LEVEL) {
            val next = level + 1
            val cost = kitCost(next)
            val note = if (!ready) "Clear City 5 of Level $level to unlock."
                else if (player.credits < cost) "%,d".format(cost - player.credits) + " more credits needed."
                else "Opens Level $next cities."
            SectionTitle("Next lesson")
            KitCard(next, 1, ready && player.credits >= cost, note) { onBuy() }
            if (next < MAX_LEVEL) {
                SectionTitle("Later")
                for (lv in next + 1..MAX_LEVEL) KitCard(lv, 2, false, "") { }
            }
        }
    }
}

// How your speed compares with the standard medic of your current level
@Composable
fun SpeedCard(player: PlayerData) {
    val lvl = player.cityLevel
    val d = player.speedVsTarget(lvl)
    val pct = Math.round(kotlin.math.abs(d) * 100.0).toInt()
    val label = if (pct == 0) "On target" else if (d > 0) "$pct% slower than target" else "$pct% faster than target"
    val col = if (pct == 0 || d < 0) Color(0xFF3F8F1F) else if (d < 0.1) Color(0xFFD9822B) else RouteRed
    // The bar fills to the target line at 60% when you match it
    val fill = (0.6f / (1f + d.toFloat())).coerceIn(0.05f, 1f)
    WhiteCard {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Your speed", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = InkText, modifier = Modifier.weight(1f))
            Text(label, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = col)
        }
        Box(
            Modifier
                .fillMaxWidth()
                .height(18.dp)
        ) {
            Box(
                Modifier
                    .align(Alignment.CenterStart)
                    .fillMaxWidth()
                    .height(10.dp)
                    .background(SoftFill, RoundedCornerShape(5.dp))
            )
            Box(
                Modifier
                    .align(Alignment.CenterStart)
                    .fillMaxWidth(fill)
                    .height(10.dp)
                    .background(col, RoundedCornerShape(5.dp))
            )
            Row(Modifier.fillMaxWidth()) {
                Spacer(Modifier.weight(0.6f))
                Box(
                    Modifier
                        .width(3.dp)
                        .height(18.dp)
                        .background(InkText, RoundedCornerShape(2.dp))
                )
                Spacer(Modifier.weight(0.4f))
            }
        }
        Text(
            "The line is the Level $lvl target. Driving skill, Tires, Engine and Cab move you toward it.",
            fontSize = 11.sp, color = MuteText
        )
    }
}
