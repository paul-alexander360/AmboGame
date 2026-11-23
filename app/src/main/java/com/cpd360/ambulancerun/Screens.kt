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

val PageBg = Color(0xFFA9ADE3)
val HeaderOrange = Color(0xFFFF6A3D)
val InkText = Color(0xFF2B313A)
val MuteText = Color(0xFF5B6085)
val GoGreen = listOf(Color(0xFF9BF076), Color(0xFF4DC43B))
val GoBorder = Color(0xFFF6A12B)
val LockGrey = listOf(Color(0xFFD3D5F0), Color(0xFFA9ABD6))

@Composable
fun ScreenHeader(title: String, credits: Int, onBack: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(HeaderOrange, RoundedCornerShape(24.dp))
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(Color(0x33FFFFFF))
                .clickable { onBack() },
            contentAlignment = Alignment.Center
        ) {
            Text("←", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.width(10.dp))
        Text(title, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        CreditPill(credits)
    }
}

@Composable
fun CreditPill(credits: Int) {
    Row(
        modifier = Modifier
            .background(Color(0xFFFFF3E0), RoundedCornerShape(12.dp))
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(16.dp).background(Color(0xFFFFC107), CircleShape).border(1.5.dp, Color(0xFFBF8600), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text("$", color = Color(0xFF7A5200), fontSize = 10.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.width(5.dp))
        Text("%,d".format(credits), color = Color(0xFFBF360C), fontSize = 14.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun WhiteCard(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White, RoundedCornerShape(16.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        content = content
    )
}

@Composable
fun SectionTitle(t: String) {
    Text(
        t, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold,
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp), textAlign = TextAlign.Center
    )
}

@Composable
fun Swatch(c: Color, selected: Boolean, locked: Boolean = false, lockLabel: String = "", onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(if (locked) LockGrey[1] else c)
                .border(if (selected) 4.dp else 2.dp, if (selected) GoBorder else Color(0xFF9A9CC8), CircleShape)
                .clickable(enabled = !locked) { onClick() },
            contentAlignment = Alignment.Center
        ) {
            if (locked) Text("🔒", fontSize = 14.sp)
            else if (selected) Text("✓", color = if (c.red + c.green + c.blue > 2.2f) InkText else Color.White, fontWeight = FontWeight.Bold)
        }
        if (lockLabel != "") Text(lockLabel, fontSize = 9.sp, color = MuteText, textAlign = TextAlign.Center)
    }
}

@Composable
fun Choice(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .background(
                Brush.verticalGradient(if (selected) GoGreen else listOf(Color(0xFFF2F3FB), Color(0xFFE2E4F5))),
                RoundedCornerShape(14.dp)
            )
            .border(2.dp, if (selected) GoBorder else Color(0xFFC9CCEA), RoundedCornerShape(14.dp))
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 7.dp)
    ) {
        Text(label, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = if (selected) Color.White else InkText)
    }
}

@Composable
fun LabeledRow(label: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
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
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Brush.verticalGradient(listOf(Color(0xFF2AA2FF), Color(0xFF0B4FB8))))
                .border(2.dp, Color(0xFF0B5BD3), RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text(glyph, fontSize = if (glyph.length > 2) 13.sp else 22.sp, color = Color.White, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(name, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = InkText)
            Text(desc, fontSize = 11.sp, color = MuteText)
            if (extra != "") Text(extra, fontSize = 11.sp, color = Color(0xFFBF360C), fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                for (i in 0 until MAX_UP) {
                    Box(
                        Modifier
                            .size(width = 18.dp, height = 8.dp)
                            .background(if (i < level) Color(0xFF4DC43B) else Color(0xFFE0E2F2), RoundedCornerShape(4.dp))
                    )
                }
                Spacer(Modifier.width(4.dp))
                Text("Lv $level", fontSize = 11.sp, color = MuteText, fontWeight = FontWeight.Bold)
            }
        }
        Spacer(Modifier.width(8.dp))
        Column(
            modifier = Modifier
                .width(76.dp)
                .background(Brush.verticalGradient(if (canBuy) GoGreen else LockGrey), RoundedCornerShape(14.dp))
                .border(2.dp, if (canBuy) GoBorder else Color(0xFF9A9CC8), RoundedCornerShape(14.dp))
                .clickable(enabled = canBuy) { onBuy() }
                .padding(vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (maxed) {
                Text("MAX", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color.White)
            } else {
                Text("Upgrade", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = if (canBuy) Color.White else MuteText)
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
            .padding(8.dp),
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
                .background(Color(0xFFF3E2A9), RoundedCornerShape(18.dp))
                .padding(8.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Brush.verticalGradient(listOf(Color(0xFFFFF0C9), Color(0xFFFFE3A3))))
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
                        Modifier.background(HeaderOrange, RoundedCornerShape(12.dp)).padding(horizontal = 12.dp, vertical = 3.dp)
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
        SectionTitle("Skills")
        UpgradeCard("🚑", "Driving skill", "Faster drive times on every road.", player.charUp[SKILL_DRIVE], player.credits) {
            player.buyChar(SKILL_DRIVE)
        }
        UpgradeCard("🚶", "Pedestrian awareness", "Fewer pedestrians step out in front of you.", player.charUp[SKILL_PED], player.credits) {
            player.buyChar(SKILL_PED)
        }
        UpgradeCard("🚗", "Crash avoidance", "Fewer car crashes on the way.", player.charUp[SKILL_CRASH], player.credits) {
            player.buyChar(SKILL_CRASH)
        }
    }
}

private val ambParts = listOf(
    Triple("◎", "Tires", "Faster speed. Higher levels lower the chance of an accident."),
    Triple("⚙", "Engine", "Faster speed."),
    Triple("▣", "Brakes", "Lower chance of an accident."),
    Triple("☀", "Lights", "Lower chance of an accident and quicker through intersections."),
    Triple("🔊", "Siren", "Lower chance of an accident and quicker through intersections."),
    Triple("🚚", "Cab", "Faster speed, lower chance of an accident and fewer pedestrians."),
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
                .background(Color(0xFFF3E2A9), RoundedCornerShape(18.dp))
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
        Switch(checked = on, onCheckedChange = onChange)
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
            Text("Player", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = InkText)
            Text("Level " + player.level + "   XP " + player.totalXp + "   Credits " + "%,d".format(player.credits), fontSize = 13.sp, color = MuteText)
        }
        Text("Version $version", color = Color.White, fontSize = 12.sp, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
    }
}
