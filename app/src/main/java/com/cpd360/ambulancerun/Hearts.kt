package com.cpd360.ambulancerun

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate

// ---- Hearts: the premium currency ----

// What Hearts buy
const val RETRY_COST = 5
const val BOOST_COST = 10
const val KIT_HELP_COST = 20
const val KIT_HELP_CREDITS = 1_000
const val PAINT_COST = 40
const val BOOST_SPEEDUP = 0.15

// Free Hearts
const val HEARTS_STRAIGHT_A = 2
const val HEARTS_PER_LEVEL = 10
const val AD_HEARTS = 5
const val ADS_PER_DAY = 3

// Paints from this index on are bought with Hearts
const val FIRST_HEART_PAINT = 5

// Hearts packs: amount, name, price shown
val heartPacks = listOf(
    Triple(50, "Handful", "$0.99"),
    Triple(300, "Best value", "$4.99"),
    Triple(700, "Big bag", "$9.99")
)

// Google Play purchases and real ads come in a later update.
// Until then the pack buttons and Watch give Hearts right away, with no charge and no ad.
const val HEARTS_TEST_STORE = true

val HeartRed = Color(0xFFE53935)
val HeartSoft = Color(0xFFFF8A80)
private val ShopBg = Color(0xFF13161D)
private val ShopTile = Color(0xFF232833)
private val ShopGo = Color(0xFF2FBF5F)
private val ShopBest = Color(0xFFFFD54A)

// Ads watched today. Resets when the date changes.
fun adsLeft(p: PlayerData): Int {
    val today = LocalDate.now().toEpochDay()
    if (p.adDay != today) return ADS_PER_DAY
    return (ADS_PER_DAY - p.adsToday).coerceAtLeast(0)
}

fun watchAd(p: PlayerData): Boolean {
    val today = LocalDate.now().toEpochDay()
    if (p.adDay != today) {
        p.adDay = today
        p.adsToday = 0
    }
    if (p.adsToday >= ADS_PER_DAY) return false
    p.adsToday++
    p.addHearts(AD_HEARTS)
    return true
}

// A red heart, drawn so it looks the same on every phone
@Composable
fun Heart(sizeDp: Int, color: Color = HeartRed) {
    Canvas(Modifier.size(sizeDp.dp)) {
        val w = size.width
        val h = size.height
        val p = Path()
        p.moveTo(w * 0.5f, h * 0.92f)
        p.cubicTo(w * 0.1f, h * 0.62f, -w * 0.02f, h * 0.36f, w * 0.12f, h * 0.18f)
        p.cubicTo(w * 0.26f, h * 0.0f, w * 0.46f, h * 0.06f, w * 0.5f, h * 0.26f)
        p.cubicTo(w * 0.54f, h * 0.06f, w * 0.74f, h * 0.0f, w * 0.88f, h * 0.18f)
        p.cubicTo(w * 1.02f, h * 0.36f, w * 0.9f, h * 0.62f, w * 0.5f, h * 0.92f)
        p.close()
        drawPath(p, color)
        drawCircle(Color(0x66FFFFFF), w * 0.08f, Offset(w * 0.28f, h * 0.3f))
    }
}

// Heart and a number, for buttons and pills
@Composable
fun HeartAmount(n: Int, textColor: Color, fontSize: Int = 14, iconDp: Int = 14, iconColor: Color = HeartRed) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Heart(iconDp, iconColor)
        Spacer(Modifier.width(4.dp))
        Text("%,d".format(n), color = textColor, fontSize = fontSize.sp, fontWeight = FontWeight.Bold)
    }
}

// Hearts shop: buy packs, watch an ad, and spend Hearts
@Composable
fun HeartsShopScreen(player: PlayerData, onBack: () -> Unit, onAmbulance: () -> Unit) {
    BackHandler { onBack() }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ShopBg)
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
                    .background(ShopTile)
                    .clickable { onBack() },
                contentAlignment = Alignment.Center
            ) {
                Text("‹", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.width(10.dp))
            Text("Hearts shop", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Box(
                Modifier
                    .background(Color(0x40E53935), RoundedCornerShape(10.dp))
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                HeartAmount(player.hearts, HeartSoft, 15, 16)
            }
        }

        ShopCard("GET HEARTS") {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                for ((amount, name, price) in heartPacks) {
                    val best = name == "Best value"
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(ShopTile)
                            .border(if (best) 2.dp else 0.dp, if (best) ShopBest else Color.Transparent, RoundedCornerShape(12.dp))
                            .padding(horizontal = 6.dp, vertical = 10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        HeartAmount(amount, HeartSoft, 17, 17)
                        Text(name, color = GlassMute, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(6.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(ShopGo)
                                .clickable { if (HEARTS_TEST_STORE) player.addHearts(amount) }
                                .padding(vertical = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(price, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
            if (HEARTS_TEST_STORE) ShopNote("Test store: packs are free for now. Google Play purchases come later.")
        }

        val left = adsLeft(player)
        ShopCard("") {
            ShopRow("▶", "Watch an ad", "+$AD_HEARTS Hearts · $left of $ADS_PER_DAY left today") {
                ShopBtn("Watch", ShopGo, left > 0) { watchAd(player) }
            }
        }

        ShopCard("SPEND HEARTS") {
            ShopRow("↻", "Retry a run", "Offered on the report card when a run fails") {
                CostTag(RETRY_COST, false) { }
            }
            val boostOk = !player.boost && player.hearts >= BOOST_COST
            ShopRow(
                "⚡", "Siren boost",
                if (player.boost) "Ready! Your next run drives 15% faster" else "Your next run drives 15% faster"
            ) {
                if (player.boost) ShopBtn("On", ShopGo, false) { }
                else CostTag(BOOST_COST, boostOk) {
                    if (player.spendHearts(BOOST_COST)) {
                        player.boost = true
                        player.save()
                    }
                }
            }
            ShopRow("✚", "Kit helper", "Adds " + "%,d".format(KIT_HELP_CREDITS) + " credits toward a kit") {
                CostTag(KIT_HELP_COST, player.hearts >= KIT_HELP_COST) {
                    if (player.spendHearts(KIT_HELP_COST)) {
                        player.credits += KIT_HELP_CREDITS
                        player.save()
                    }
                }
            }
            ShopRow("🎨", "Special paints", "Hearts-only colors in Ambulance") {
                ShopBtn("Open", ShopTile, true) { onAmbulance() }
            }
        }
        ShopNote(
            "Earn free Hearts: +$HEARTS_STRAIGHT_A for a run with straight A's, +$HEARTS_PER_LEVEL each character level."
        )
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun ShopCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(GlassSolid, RoundedCornerShape(16.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (title != "") Text(title, color = GlassMute, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
        content()
    }
}

@Composable
private fun ShopNote(text: String) {
    Text(text, color = Color(0xFFAAB0D8), fontSize = 12.sp, fontWeight = FontWeight.Bold)
}

@Composable
private fun ShopRow(glyph: String, title: String, sub: String, action: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .background(ShopTile, RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text(glyph, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Text(sub, color = GlassMute, fontSize = 11.sp)
        }
        Spacer(Modifier.width(8.dp))
        action()
    }
}

@Composable
private fun ShopBtn(label: String, fill: Color, enabled: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (enabled) fill else ShopTile)
            .clickable(enabled = enabled) { onClick() }
            .padding(horizontal = 12.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = if (enabled || fill == ShopGo) Color.White else GlassMute, fontSize = 14.sp, fontWeight = FontWeight.Bold)
    }
}

// Red Hearts price tag. Grey when you can't buy it.
@Composable
private fun CostTag(cost: Int, enabled: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(9.dp))
            .background(if (enabled) HeartRed else Color(0x99E53935))
            .clickable(enabled = enabled) { onClick() }
            .padding(horizontal = 9.dp, vertical = 6.dp)
    ) {
        HeartAmount(cost, Color.White, 13, 13, Color.White)
    }
}
