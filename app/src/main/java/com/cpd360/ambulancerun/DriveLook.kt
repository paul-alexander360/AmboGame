package com.cpd360.ambulancerun

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.scale
import kotlin.math.min

// The night look of the drive scene. Every colour in the city goes through grade(): it is
// pulled toward grey by sat, tinted by mul and lifted by lift, so the day colours become a
// night shift. Lights (lamps, lit windows, signals, the light bar) skip the grade and glow.
internal class DriveLook(
    val mul: FloatArray, val sat: Float, val lift: FloatArray,
    val cabMul: FloatArray, val cabSat: Float, val cabLift: FloatArray,
    val sky: List<Color>, val stars: Boolean, val moon: Boolean, val clouds: Color, val skyline: Color,
    val skyWin: Float,      // share of lit windows in the far skyline
    val lit: Float,         // share of lit windows in the city
    val lamp: Color,        // street lamp colour
    val pool: Float,        // how bright the pools of lamp light are
    val siren: Float,       // red and blue wash from the light bar
    val beam: Float,        // our headlights on the road
    val road: Color,
    val fog: Color?,
    val rain: Boolean,
    val snow: Boolean
)

internal val LookClear = DriveLook(
    floatArrayOf(0.30f, 0.35f, 0.56f), 0.5f, floatArrayOf(6f, 8f, 20f),
    floatArrayOf(0.62f, 0.64f, 0.8f), 0.85f, floatArrayOf(4f, 5f, 12f),
    listOf(Color(0xFF060A1A), Color(0xFF111A3D), Color(0xFF27336A)), true, true, Color(0x4D46528C), Color(0xFF121A38),
    0.45f, 0.5f, Color(0xFFFFC46B), 0.55f, 0.12f, 0.11f, Color(0xFF23273A), null, false, false
)

internal val LookStorm = DriveLook(
    floatArrayOf(0.27f, 0.32f, 0.44f), 0.35f, floatArrayOf(9f, 12f, 20f),
    floatArrayOf(0.5f, 0.54f, 0.66f), 0.7f, floatArrayOf(6f, 8f, 14f),
    listOf(Color(0xFF0A0E17), Color(0xFF18202F), Color(0xFF2B3547)), false, false, Color(0xF22C364A), Color(0xFF151C2A),
    0.35f, 0.42f, Color(0xFFFFCF8A), 0.5f, 0.16f, 0.14f, Color(0xFF1F2430), Color(0xFF465670), true, false
)

internal val LookSnow = DriveLook(
    floatArrayOf(0.44f, 0.49f, 0.68f), 0.5f, floatArrayOf(10f, 12f, 24f),
    floatArrayOf(0.6f, 0.62f, 0.76f), 0.8f, floatArrayOf(8f, 8f, 14f),
    listOf(Color(0xFF141A28), Color(0xFF262E42), Color(0xFF3D475E)), false, false, Color(0x8C78829B), Color(0xFF1C2232),
    0.4f, 0.5f, Color(0xFFFFC98A), 0.7f, 0.14f, 0.14f, Color(0xFF3B4152), Color(0xFF96A0B9), false, true
)

internal fun lookFor(city: CityData): DriveLook = when {
    city.events.contains(EV_SNOW) -> LookSnow
    city.events.contains(EV_STORM) -> LookStorm
    else -> LookClear
}

// The look used while a frame is drawn
internal var look: DriveLook = LookClear

// Colours that are lights: they keep their own colour at night
private val emit = longArrayOf(
    0xFFFF5252, 0xFF69F0AE, 0xFFFFF59D, 0xFFFFF9C4, 0xFFFF1744, 0xFFFFEB3B, 0xFFFF6A3D, 0xFF2979FF,
    0xFFB71C1C, 0xFF0D47A1, 0xFF3FA2FF, 0xFFE4FF1A, 0xFFF0262B, 0xFFE8C890, 0xFFFFE3A6, 0xFFFFE8B0,
    0xFFFFF3C4, 0xFFFEFEFE, 0xFFFFE3A0, 0xFFFFD98A, 0xFFFFE7B0, 0xFFFFC870, 0xFFBFE3FF, 0xFFFFE6B0,
    0xFFFFE9B8, 0xFFFFE9C0, 0xFFFFC46B, 0xFF9FD3FF
)

private fun argb(c: Color): Long {
    val a = (c.alpha * 255f + 0.5f).toInt()
    val r = (c.red * 255f + 0.5f).toInt()
    val g = (c.green * 255f + 0.5f).toInt()
    val b = (c.blue * 255f + 0.5f).toInt()
    return (a.toLong() shl 24) or (r.toLong() shl 16) or (g.toLong() shl 8) or b.toLong()
}

internal fun isLight(c: Color): Boolean {
    val v = argb(c)
    for (e in emit) if (e == v) return true
    return false
}

private fun gradeWith(c: Color, mul: FloatArray, sat: Float, lift: FloatArray): Color {
    val r = c.red * 255f
    val g = c.green * 255f
    val b = c.blue * 255f
    val l = 0.3f * r + 0.59f * g + 0.11f * b
    fun ch(v: Float, i: Int) = ((l + (v - l) * sat) * mul[i] + lift[i]).coerceIn(0f, 255f) / 255f
    return Color(ch(r, 0), ch(g, 1), ch(b, 2), c.alpha)
}

// A city colour at night
internal fun g(c: Color): Color = if (isLight(c)) c else gradeWith(c, look.mul, look.sat, look.lift)

// A colour inside the cab at night (lighter than the street)
internal fun gc(c: Color): Color = if (isLight(c)) c else gradeWith(c, look.cabMul, look.cabSat, look.cabLift)

// A soft light that adds to what is under it
internal fun DrawScope.glow(x: Float, y: Float, r: Float, col: Color, a: Float) {
    if (!(r > 0.5f)) return
    drawCircle(
        Brush.radialGradient(
            0f to col.copy(alpha = min(1f, a)), 0.35f to col.copy(alpha = a * 0.35f), 1f to col.copy(alpha = 0f),
            center = Offset(x, y), radius = r
        ),
        r, Offset(x, y), blendMode = BlendMode.Plus
    )
}

// A light shining down onto the ground: an oval pool
internal fun DrawScope.pool(x: Float, y: Float, rx: Float, ry: Float, col: Color, a: Float) {
    if (!(rx > 0.5f) || !(ry > 0.1f)) return
    scale(1f, ry / rx, Offset(x, y)) {
        drawCircle(
            Brush.radialGradient(0f to col.copy(alpha = a), 1f to col.copy(alpha = 0f), center = Offset(x, y), radius = rx),
            rx, Offset(x, y), blendMode = BlendMode.Plus
        )
    }
}
