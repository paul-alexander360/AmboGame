package com.cpd360.ambulancerun

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.tan

// One city block in the drive scene, in world units
private const val BL = 60f
private const val SECONDS_PER_BLOCK = 4f
private const val HOLD = 0.5f
private const val NEAR = 0.4f
private const val CAM_H = 1.5f

private class DriveCam(
    val px: Float, val py: Float, val hx: Float, val hy: Float, val seg: Int
) {
    val rx = -hy
    val ry = hx
}

// Route and timing for one drive
private class DrivePlan(city: CityData, nodes: List<Int>, start: Offset, end: Offset) {
    val pts = ArrayList<FloatArray>()
    val dist = ArrayList<Float>()
    val keys = ArrayList<Float>()
    val durs = ArrayList<Float>()
    val holds = ArrayList<Float>()
    val total: Float
    private val owH = city.owH
    private val owV = city.owV

    init {
        pts.add(floatArrayOf(wx(start.x), wy(start.y)))
        for (k in nodes) pts.add(floatArrayOf((k % city.m) * BL, (k / city.m) * BL))
        pts.add(floatArrayOf(wx(end.x), wy(end.y)))
        dist.add(0f)
        for (i in 1 until pts.size) {
            dist.add(dist[i - 1] + hypot(pts[i][0] - pts[i - 1][0], pts[i][1] - pts[i - 1][1]))
        }
        keys.add(0f)
        for (k in 1 until pts.size - 1) {
            val s = max(keys.last() + 0.1f, dist[k] - 9f)
            val len = s - keys.last()
            keys.add(s)
            durs.add(max(1f, len / BL * SECONDS_PER_BLOCK - HOLD))
            holds.add(HOLD)
        }
        val len = dist.last() - keys.last()
        keys.add(dist.last())
        durs.add(max(1f, len / BL * SECONDS_PER_BLOCK))
        holds.add(0f)
        total = durs.sum() + holds.sum()
    }

    fun distAt(t: Float): Float {
        var tt = t
        for (i in durs.indices) {
            if (tt < durs[i]) {
                val u = tt / durs[i]
                return keys[i] + (keys[i + 1] - keys[i]) * (1f - cos(PI.toFloat() * u)) / 2f
            }
            tt -= durs[i]
            if (tt < holds[i]) return keys[i + 1]
            tt -= holds[i]
        }
        return keys.last()
    }

    private fun segAng(s: Int): Float = atan2(pts[s + 1][1] - pts[s][1], pts[s + 1][0] - pts[s][0])

    private fun laneOff(s: Int): Float {
        val a = pts[s]
        val b = pts[s + 1]
        // One-way traffic uses the middle and right lanes of its flow, so the
        // ambulance takes the lane on the flow's left, even when going against it
        if (abs(a[1] - b[1]) < 0.01f) {
            val j = (a[1] / BL).roundToInt().coerceIn(0, owH.size - 1)
            if (owH[j] == 0) return 1.75f
            val flow = if (owH[j] == 1) 1f else -1f
            return if ((b[0] - a[0]) * flow >= 0f) -3.5f else 3.5f
        }
        val i = (a[0] / BL).roundToInt().coerceIn(0, owV.size - 1)
        if (owV[i] == 0) return 1.75f
        val flow = if (owV[i] == 1) -1f else 1f
        return if ((b[1] - a[1]) * flow >= 0f) -3.5f else 3.5f
    }

    private fun angLerp(a: Float, b: Float, f: Float): Float {
        var d = b - a
        while (d > PI) d -= (2 * PI).toFloat()
        while (d < -PI) d += (2 * PI).toFloat()
        return a + d * f
    }

    fun camAt(d: Float): DriveCam {
        var s = 0
        while (s < pts.size - 2 && d > dist[s + 1]) s++
        val segLen = dist[s + 1] - dist[s]
        val u = if (segLen > 0f) ((d - dist[s]) / segLen).coerceIn(0f, 1f) else 0f
        val cx = pts[s][0] + (pts[s + 1][0] - pts[s][0]) * u
        val cy = pts[s][1] + (pts[s + 1][1] - pts[s][1]) * u
        var a = segAng(s)
        var off = laneOff(s)
        if (s < pts.size - 2 && d > dist[s + 1] - 6f) {
            val f = (d - (dist[s + 1] - 6f)) / 12f
            a = angLerp(a, segAng(s + 1), f)
            off += (laneOff(s + 1) - off) * f
        } else if (s > 0 && d < dist[s] + 6f) {
            val f = (d - (dist[s] - 6f)) / 12f
            a = angLerp(segAng(s - 1), a, f)
            off = laneOff(s - 1) + (off - laneOff(s - 1)) * f
        }
        val hx = cos(a)
        val hy = sin(a)
        return DriveCam(cx + (-hy) * off, cy + hx * off, hx, hy, s)
    }

    companion object {
        fun wx(mx: Float): Float = (mx - 19f) / 80f * BL
        fun wy(my: Float): Float = (my - 20f) / 80f * BL
    }
}

private class Obj(val d: Float, val fn: () -> Unit)

// Lets each car stop or hurry so it never drives through the ambulance or
// another car. delay is how many seconds each car is behind its schedule.
private class Traffic(n: Int) {
    val delay = FloatArray(12 * (n + 2))
    var lastT = 0f
}

// Quadratic curve written as a cubic, from (x0, y0) through control (cx, cy) to (x2, y2)
private fun Path.qTo(x0: Float, y0: Float, cx: Float, cy: Float, x2: Float, y2: Float) {
    cubicTo(
        x0 + 2f / 3f * (cx - x0), y0 + 2f / 3f * (cy - y0),
        x2 + 2f / 3f * (cx - x2), y2 + 2f / 3f * (cy - y2),
        x2, y2
    )
}

private val zoneGround = listOf(Color(0xFFB9E67F), Color(0xFFFFE59A), Color(0xFFDCCFF3))
private val carCols = listOf(
    Color(0xFFFFD54F), Color(0xFF4FC3F7), Color(0xFFAED581), Color(0xFFBA68C8),
    Color(0xFFECEFF1), Color(0xFFFF8A65), Color(0xFF90A4AE)
)
private val pedCols = listOf(
    Color(0xFFE53935), Color(0xFF3949AB), Color(0xFF43A047), Color(0xFFFB8C00), Color(0xFF8E24AA), Color(0xFF00897B)
)
private val hairCols = listOf(Color(0xFF3E2723), Color(0xFF212121), Color(0xFF8D6E63), Color(0xFFFBC02D), Color(0xFF5D4037))
private val skinCols = listOf(Color(0xFFF2C29B), Color(0xFFC68642), Color(0xFF8D5524), Color(0xFFE0AC69), Color(0xFFFFDBAC))
private val pantsCols = listOf(Color(0xFF37474F), Color(0xFF1A237E), Color(0xFF5D4037), Color(0xFF263238))
private val houseWalls = listOf(
    Color(0xFFFFE0B2), Color(0xFFF5C98A), Color(0xFFC8F0D8), Color(0xFF9ED9B5),
    Color(0xFFFFD1DC), Color(0xFFF2AFC0), Color(0xFFFFF3A6), Color(0xFFEED97A)
)
private val roofCols = listOf(Color(0xFFE2583E), Color(0xFF3F8EDC), Color(0xFF2BAF9A), Color(0xFF8E5BD0))
private val awningCols = listOf(Color(0xFFE53935), Color(0xFF43A047), Color(0xFF1E88E5))
private val treeCols = listOf(Color(0xFF43A047), Color(0xFF2E7D32), Color(0xFF66BB6A))
private val shadowCol = Color(0x29283C5A)
private val capCols = listOf(Color(0xFFE53935), Color(0xFF1E88E5), Color(0xFF43A047))
private val carGlass = Color(0xFF3B5568)
// direction the light comes from, for shading the sides of cars
private const val LX = -0.55f
private const val LY = -0.83f

private class PedLook(
    val pid: Int, val shirt: Color, val skin: Color, val hair: Color, val pants: Color,
    val bag: Boolean, val dog: Boolean, val long: Boolean, val cap: Boolean, val capC: Color
)

// Outline of a box with its corners cut off, ol shifts it along its length
private fun rr(cx: Float, cy: Float, ax: Float, ay: Float, ol: Float, hl: Float, hw: Float, c: Float): List<FloatArray> {
    val bx = -ay
    val by = ax
    val q = floatArrayOf(hl, hw - c, hl - c, hw, -hl + c, hw, -hl, hw - c, -hl, -hw + c, -hl + c, -hw, hl - c, -hw, hl, -hw + c)
    return List(8) { i ->
        val l = q[i * 2] + ol
        val wv = q[i * 2 + 1]
        floatArrayOf(cx + ax * l + bx * wv, cy + ay * l + by * wv)
    }
}

private fun shadeCol(col: Color, nx: Float, ny: Float): Color {
    val d = nx * LX + ny * LY
    return if (d > 0f) lerp(col, Color.White, 0.18f * d) else lerp(col, Color.Black, -0.3f * d)
}

// An arm, leg or leash: a round-ended stroke with an optional highlight down one side
private fun DrawScope.limb(x0: Float, y0: Float, x1: Float, y1: Float, wd: Float, col: Color, hi: Color?) {
    drawLine(col, Offset(x0, y0), Offset(x1, y1), wd, StrokeCap.Round)
    if (hi != null) drawLine(hi, Offset(x0 - wd * 0.18f, y0), Offset(x1 - wd * 0.18f, y1), wd * 0.35f, StrokeCap.Round)
}

// A rounded, shaded pedestrian standing at px, py; k is pixels per world unit
private fun DrawScope.person(px: Float, py: Float, k: Float, o: PedLook, t: Float, faceK: Float) {
    val sw = sin(t * 6f + o.pid)
    drawOval(Color(0x40283C5A), Offset(px - 0.42f * k, py - 0.13f * k), Size(0.84f * k, 0.26f * k))
    val sh = py - 1.38f * k
    // back arm
    limb(px + 0.27f * k, sh, px + 0.27f * k + sin(sw * 0.4f) * 0.55f * k, sh + cos(sw * 0.4f) * 0.55f * k, 0.16f * k, lerp(o.shirt, Color.Black, 0.3f), null)
    // legs and shoes
    limb(px - 0.11f * k, py - 0.8f * k, px - 0.11f * k + sw * 0.12f * k, py - 0.12f * k, 0.2f * k, o.pants, lerp(o.pants, Color.White, 0.25f))
    limb(px + 0.11f * k, py - 0.8f * k, px + 0.11f * k - sw * 0.12f * k, py - 0.12f * k, 0.2f * k, lerp(o.pants, Color.Black, 0.15f), null)
    val shoe = g(Color(0xFF263238))
    drawOval(shoe, Offset(px - 0.11f * k + sw * 0.12f * k - 0.1f * k, py - 0.15f * k), Size(0.3f * k, 0.16f * k))
    drawOval(shoe, Offset(px + 0.11f * k - sw * 0.12f * k - 0.1f * k, py - 0.15f * k), Size(0.3f * k, 0.16f * k))
    // shaded body
    drawRoundRect(
        Brush.horizontalGradient(
            0f to lerp(o.shirt, Color.White, 0.35f), 0.45f to o.shirt, 1f to lerp(o.shirt, Color.Black, 0.3f),
            startX = px - 0.32f * k, endX = px + 0.32f * k
        ),
        Offset(px - 0.32f * k, py - 1.52f * k), Size(0.64f * k, 0.8f * k), CornerRadius(0.2f * k, 0.2f * k)
    )
    if (o.bag) drawRoundRect(g(Color(0xFF6D4C41)), Offset(px - 0.42f * k, py - 1.1f * k), Size(0.2f * k, 0.32f * k), CornerRadius(0.05f * k, 0.05f * k))
    // front arm and hand
    val hx = px - 0.27f * k + sin(sw * 0.4f) * 0.55f * k
    val hy = sh + cos(sw * 0.4f) * 0.55f * k
    limb(px - 0.27f * k, sh, hx, hy, 0.17f * k, o.shirt, lerp(o.shirt, Color.White, 0.35f))
    drawCircle(
        Brush.radialGradient(0f to lerp(o.skin, Color.White, 0.3f), 1f to o.skin, center = Offset(hx - 0.03f * k, hy - 0.03f * k), radius = 0.1f * k),
        0.09f * k, Offset(hx, hy)
    )
    // neck, head and hair
    drawRect(lerp(o.skin, Color.Black, 0.15f), Offset(px - 0.07f * k, py - 1.62f * k), Size(0.14f * k, 0.14f * k))
    val hy2 = py - 1.84f * k
    drawCircle(
        Brush.radialGradient(
            0f to lerp(o.skin, Color.White, 0.35f), 0.7f to o.skin, 1f to lerp(o.skin, Color.Black, 0.2f),
            center = Offset(px - 0.08f * k, hy2 - 0.08f * k), radius = 0.26f * k
        ),
        0.24f * k, Offset(px, hy2)
    )
    val hairB = Brush.radialGradient(
        0f to lerp(o.hair, Color.White, 0.3f), 1f to o.hair,
        center = Offset(px - 0.08f * k, hy2 - 0.2f * k), radius = 0.3f * k
    )
    drawArc(hairB, 180f, 180f, true, Offset(px - 0.255f * k, hy2 - 0.28f * k), Size(0.51f * k, 0.44f * k))
    if (o.long) drawRoundRect(hairB, Offset(px + 0.1f * k, hy2 - 0.08f * k), Size(0.17f * k, 0.36f * k), CornerRadius(0.08f * k, 0.08f * k))
    if (o.cap) {
        drawArc(o.capC, 180f, 180f, true, Offset(px - 0.26f * k, hy2 - 0.28f * k), Size(0.52f * k, 0.32f * k))
        drawRect(o.capC, Offset(px - 0.36f * k, hy2 - 0.13f * k), Size(0.3f * k, 0.06f * k))
    }
    // an umbrella in the rain
    if (look.rain && o.pid % 3 != 1) {
        val uc = g(capCols[o.pid % capCols.size])
        drawLine(g(Color(0xFF263238)), Offset(hx, hy), Offset(hx + 0.1f * k, hy2 - 0.55f * k), 0.05f * k)
        drawArc(uc, 180f, 180f, true, Offset(hx + 0.1f * k - 0.7f * k, hy2 - 0.95f * k), Size(1.4f * k, 0.8f * k))
    }
    // eyes and cheeks when close enough to see
    if (k > faceK) {
        drawCircle(g(Color(0xFF212121)), 0.03f * k, Offset(px - 0.08f * k, hy2 + 0.02f * k))
        drawCircle(g(Color(0xFF212121)), 0.03f * k, Offset(px + 0.08f * k, hy2 + 0.02f * k))
        drawCircle(Color(0x59FF7878), 0.04f * k, Offset(px - 0.15f * k, hy2 + 0.09f * k))
        drawCircle(Color(0x59FF7878), 0.04f * k, Offset(px + 0.15f * k, hy2 + 0.09f * k))
    }
    if (o.dog) {
        val dx = px + 1.0f * k
        val fur = g(Color(0xFF6D4C41))
        limb(hx + 0.05f * k, hy + 0.02f * k, dx, py - 0.42f * k, 0.03f * k, g(Color(0xFFE53935)), null)
        drawRoundRect(
            Brush.verticalGradient(listOf(g(Color(0xFFA1887F)), fur), startY = py - 0.55f * k, endY = py),
            Offset(dx - 0.05f * k, py - 0.5f * k), Size(0.55f * k, 0.28f * k), CornerRadius(0.13f * k, 0.13f * k)
        )
        limb(dx + 0.05f * k, py - 0.25f * k, dx + 0.05f * k, py - 0.04f * k, 0.08f * k, fur, null)
        limb(dx + 0.42f * k, py - 0.25f * k, dx + 0.42f * k, py - 0.04f * k, 0.08f * k, fur, null)
        drawCircle(g(Color(0xFF8D6E63)), 0.15f * k, Offset(dx + 0.55f * k, py - 0.55f * k))
        drawOval(g(Color(0xFF5D4037)), Offset(dx + 0.42f * k, py - 0.7f * k), Size(0.1f * k, 0.2f * k))
        drawCircle(g(Color(0xFF212121)), 0.03f * k, Offset(dx + 0.68f * k, py - 0.55f * k))
    }
}
private val glassBlue = Color(0xFF5C9BD6)
private val officeGlassL = Color(0xFFBBDEFB)
private val officeGlassD = Color(0xFF2F7FC1)
private val hospGlass = Color(0xFF81D4FA)
private val shopGlass = Color(0xFF90CAF9)
private val lobbyGlass = Color(0xFFB3E5FC)
private val lobbyLit = Color(0xFFFFE9C0)
private val shopLit = Color(0xFFFFE6B0)
private val darkGlass = Color(0xFF1C2540)
private val reflectCol = Color(0x2E96B4EB)
private val trimCol = Color(0xFFD9D3C5)
private val sillCol = Color(0xFFB9B2A2)
private val sillShadow = Color(0xFF6E685C)
private val headCol = Color(0xFFCBC4B4)
private val blindCol = Color(0x8C8C6432)
private val shelfCol = Color(0xFF7A5A3A)
private val houseTrim = Color(0xFFF2EFE8)
private val snowWhite = Color(0xFFF4F7FC)
private val snowTopCol = Color(0xFFE9EEF7)
private val snowGround = Color(0xFFE2E8F2)
private val litCols = listOf(Color(0xFFFFD98A), Color(0xFFFFE7B0), Color(0xFFFFC870), Color(0xFFFFD98A), Color(0xFFBFE3FF))
private val curtainCols = listOf(Color(0xFFC0392B), Color(0xFF2E86C1), Color(0xFF7D3C98), Color(0xFF1E8449), Color(0xFFB9770E))
private val goodsCols = listOf(Color(0xFFE53935), Color(0xFF1E88E5), Color(0xFF43A047), Color(0xFFFB8C00), Color(0xFF8E24AA))
private val shutterCols = listOf(Color(0xFF1E4D2B), Color(0xFF1F3A60), Color(0xFF5D1F1F), Color(0xFF37474F))
private val flowerCols = listOf(Color(0xFFF06292), Color(0xFFFFD54F), Color(0xFFE57373), Color(0xFFBA68C8))

// Small repeatable hash, 0 to 1, so the same spot always gets the same tree or window
private fun hsh(v: Int): Float {
    var i = (v xor 61) xor (v ushr 16)
    i += i shl 3
    i = i xor (i ushr 4)
    i *= 0x27d4eb2d
    i = i xor (i ushr 15)
    return (i.toLong() and 0xFFFFFFFFL).toFloat() / 4294967296f
}

private fun wrapAng(x: Float): Float {
    var d = x
    while (d > PI) d -= (2 * PI).toFloat()
    while (d < -PI) d += (2 * PI).toFloat()
    return d
}

// Text drawn on the canvas, measured once
private class Labels(
    val stop: TextLayoutResult,
    val mph: TextLayoutResult,
    val rpm: TextLayoutResult,
    val buttons: List<TextLayoutResult>
)

// Draws the world from the driver seat, or from the drone above and behind the ambulance
private class Renderer(val ds: DrawScope, val cam: DriveCam, val drone: Boolean) {
    val w = ds.size.width
    val h = ds.size.height
    val f = w * 0.85f
    val pitch = if (drone) 0.92f else 0f
    val ch = if (drone) 15f else CAM_H
    val y0 = h * (if (drone) 0.66f else 0.42f)
    private val cp = cos(pitch)
    private val sp = sin(pitch)
    // where the sky meets the ground on screen
    val horizon = y0 - f * tan(pitch)
    // near things get extra detail (window trim, curtains, siding)
    var rich = true

    fun tc(x: Float, y: Float, z: Float): FloatArray {
        val dx = x - cam.px
        val dy = y - cam.py
        val fw = dx * cam.hx + dy * cam.hy
        val u = z - ch
        return floatArrayOf(dx * cam.rx + dy * cam.ry, fw * sp + u * cp, fw * cp - u * sp)
    }

    fun pr(p: FloatArray): Offset = Offset(w / 2f + p[0] * f / p[2], y0 - p[1] * f / p[2])

    // Screen point and pixels per world unit at a spot in the world, or nothing if behind the camera
    inline fun at(x: Float, y: Float, z: Float, fn: (Offset, Float) -> Unit) {
        val p = tc(x, y, z)
        if (p[2] < NEAR) return
        fn(pr(p), f / p[2])
    }

    fun poly(ws: List<FloatArray>, col: Color, raw: Boolean = false, blend: BlendMode = BlendMode.SrcOver) {
        val cs = ws.map { tc(it[0], it[1], it[2]) }
        val out = ArrayList<FloatArray>()
        for (i in cs.indices) {
            val a = cs[i]
            val b = cs[(i + 1) % cs.size]
            val ai = a[2] >= NEAR
            val bi = b[2] >= NEAR
            if (ai) out.add(a)
            if (ai != bi) {
                val t = (NEAR - a[2]) / (b[2] - a[2])
                out.add(floatArrayOf(a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t, NEAR))
            }
        }
        if (out.size < 3) return
        val path = Path()
        for (i in out.indices) {
            val q = pr(out[i])
            if (i == 0) path.moveTo(q.x, q.y) else path.lineTo(q.x, q.y)
        }
        path.close()
        ds.drawPath(path, if (raw) col else g(col), blendMode = blend)
    }

    fun ground(x0: Float, y0g: Float, x1: Float, y1: Float, col: Color) {
        poly(
            listOf(
                floatArrayOf(x0, y0g, 0f), floatArrayOf(x1, y0g, 0f),
                floatArrayOf(x1, y1, 0f), floatArrayOf(x0, y1, 0f)
            ), col
        )
    }

    // A flat ring on the ground (or a roof at height z), like a manhole cover
    fun disc(x: Float, y: Float, rad: Float, col: Color, z: Float = 0.01f, segs: Int = 10) {
        val pts = ArrayList<FloatArray>()
        for (k in 0 until segs) {
            val a = k / segs.toFloat() * 2f * PI.toFloat()
            pts.add(floatArrayOf(x + rad * cos(a), y + rad * sin(a), z))
        }
        poly(pts, col)
    }

    // A flat rectangle lying at height z, turned along ax/ay, moved al along and ac across
    fun flat(cx: Float, cy: Float, ax: Float, ay: Float, al: Float, ac: Float, hl: Float, hw: Float, z: Float, col: Color) {
        val bx = -ay
        val by = ax
        val x = cx + ax * al + bx * ac
        val y = cy + ay * al + by * ac
        poly(
            listOf(
                floatArrayOf(x + ax * hl + bx * hw, y + ay * hl + by * hw, z), floatArrayOf(x + ax * hl - bx * hw, y + ay * hl - by * hw, z),
                floatArrayOf(x - ax * hl - bx * hw, y - ay * hl - by * hw, z), floatArrayOf(x - ax * hl + bx * hw, y - ay * hl + by * hw, z)
            ), col
        )
    }

    // A thin line between two points in the world
    fun line(x0: Float, y0l: Float, z0: Float, x1: Float, y1: Float, z1: Float, col: Color, wd: Float) {
        val a = tc(x0, y0l, z0)
        val b = tc(x1, y1, z1)
        if (a[2] < NEAR || b[2] < NEAR) return
        ds.drawLine(g(col), pr(a), pr(b), wd)
    }

    // A glow at a spot in the world; r is in world units
    fun glowAt(x: Float, y: Float, z: Float, r: Float, col: Color, a: Float) {
        at(x, y, z) { s, k -> ds.glow(s.x, s.y, r * k, col, a) }
    }

    // A panel on a wall from corner a to corner b: u0..u1 along the wall, z0..z1 up,
    // pushed out from the wall by out. Windows get trim, sills and curtains when near.
    fun quad(a: FloatArray, b: FloatArray, u0: Float, u1: Float, z0: Float, z1: Float, col: Color, out: Float = 0.03f) {
        if (rich) {
            if ((col == glassBlue || col == officeGlassL || col == officeGlassD || col == hospGlass) && z1 - z0 > 0.6f) {
                window(a, b, u0, u1, z0, z1, out)
                return
            }
            if (col == shopGlass) {
                storefront(a, b, u0, u1, z0, z1, out)
                return
            }
        }
        if (col == lobbyGlass) {
            plain(a, b, u0, u1, z0, z1, lobbyLit, out)
            return
        }
        if (!rich && (col == glassBlue || col == officeGlassL || col == officeGlassD || col == hospGlass)) {
            plain(a, b, u0, u1, z0, z1, if (hsh(winKey(a, b, u0, z0)) < look.lit) litCols[0] else darkGlass, out)
            return
        }
        plain(a, b, u0, u1, z0, z1, col, out)
    }

    fun plain(a: FloatArray, b: FloatArray, u0: Float, u1: Float, z0: Float, z1: Float, col: Color, out: Float = 0.03f) {
        val dx = b[0] - a[0]
        val dy = b[1] - a[1]
        val len = hypot(dx, dy)
        if (len < 0.001f) return
        val ox = -dy / len * out
        val oy = dx / len * out
        val x0 = a[0] + dx * u0 + ox
        val yA = a[1] + dy * u0 + oy
        val x1 = a[0] + dx * u1 + ox
        val yB = a[1] + dy * u1 + oy
        poly(
            listOf(
                floatArrayOf(x0, yA, z0), floatArrayOf(x1, yB, z0),
                floatArrayOf(x1, yB, z1), floatArrayOf(x0, yA, z1)
            ), col
        )
    }

    // Any shape on a wall: pts are (u along the wall, height) pairs
    fun wpoly(a: FloatArray, b: FloatArray, pts: List<FloatArray>, col: Color, out: Float) {
        val dx = b[0] - a[0]
        val dy = b[1] - a[1]
        val len = hypot(dx, dy)
        if (len < 0.001f) return
        val ox = -dy / len * out
        val oy = dx / len * out
        poly(pts.map { floatArrayOf(a[0] + dx * it[0] + ox, a[1] + dy * it[0] + oy, it[1]) }, col)
    }

    private fun winKey(a: FloatArray, b: FloatArray, u0: Float, z0: Float): Int =
        ((a[0] + (b[0] - a[0]) * u0) * 7f + (a[1] + (b[1] - a[1]) * u0) * 13f + z0 * 31f).toInt()

    // A window with trim, a sill and a header; lit ones have curtains or blinds, dark ones a reflection
    private fun window(a: FloatArray, b: FloatArray, u0: Float, u1: Float, z0: Float, z1: Float, out: Float) {
        val len = hypot(b[0] - a[0], b[1] - a[1])
        val du = 0.11f / len
        val dz = 0.11f
        val key = winKey(a, b, u0, z0)
        val lit = hsh(key) < look.lit
        plain(a, b, u0 - du, u1 + du, z0 - dz, z1 + dz, trimCol, out + 0.01f)
        plain(a, b, u0 - du * 1.7f, u1 + du * 1.7f, z0 - dz - 0.2f, z0 - dz - 0.14f, sillShadow, out + 0.05f)
        plain(a, b, u0 - du * 1.7f, u1 + du * 1.7f, z0 - dz - 0.14f, z0 - dz, sillCol, out + 0.07f)
        plain(a, b, u0 - du * 1.4f, u1 + du * 1.4f, z1 + dz, z1 + dz + 0.1f, headCol, out + 0.04f)
        if (look.snow) plain(a, b, u0 - du * 1.7f, u1 + du * 1.7f, z0 - dz, z0 - dz + 0.06f, snowWhite, out + 0.08f)
        if (lit) {
            plain(a, b, u0, u1, z0, z1, litCols[(hsh(key + 5) * litCols.size).toInt().coerceIn(0, litCols.size - 1)], out + 0.015f)
            if (hsh(key + 3) < 0.55f) {
                val cc = curtainCols[(hsh(key + 9) * curtainCols.size).toInt().coerceIn(0, curtainCols.size - 1)]
                val cw = (u1 - u0) * 0.22f
                plain(a, b, u0, u0 + cw, z0, z1, cc, out + 0.02f)
                plain(a, b, u1 - cw, u1, z0, z1, cc, out + 0.02f)
                plain(a, b, u0, u1, z1 - 0.12f, z1, lerp(cc, Color.Black, 0.3f), out + 0.022f)
            } else {
                for (q in 1..6) {
                    val zz = z0 + (z1 - z0) * q / 7f
                    plain(a, b, u0, u1, zz, zz + 0.035f, blindCol, out + 0.02f)
                }
            }
        } else {
            plain(a, b, u0, u1, z0, z1, darkGlass, out + 0.015f)
            val wu = u1 - u0
            wpoly(
                a, b, listOf(
                    floatArrayOf(u0 + wu * 0.1f, z0), floatArrayOf(u0 + wu * 0.38f, z0),
                    floatArrayOf(u0 + wu * 0.78f, z1), floatArrayOf(u0 + wu * 0.5f, z1)
                ), reflectCol, out + 0.02f
            )
        }
        val um = (u0 + u1) / 2f
        val zm = (z0 + z1) / 2f
        plain(a, b, um - du * 0.25f, um + du * 0.25f, z0, z1, trimCol, out + 0.025f)
        plain(a, b, u0, u1, zm - 0.035f, zm + 0.035f, trimCol, out + 0.025f)
    }

    // A lit shop window with shelves of goods
    private fun storefront(a: FloatArray, b: FloatArray, u0: Float, u1: Float, z0: Float, z1: Float, out: Float) {
        val key = (a[0] * 3f + a[1] * 5f + u0 * 97f).toInt()
        plain(a, b, u0, u1, z0, z1, shopLit, out + 0.015f)
        for (q in 1..3) {
            val zz = z0 + (z1 - z0) * q / 4f
            plain(a, b, u0 + 0.01f, u1 - 0.01f, zz, zz + 0.05f, shelfCol, out + 0.02f)
            for (p in 0..5) {
                val uu = u0 + 0.015f + (u1 - u0 - 0.03f) * p / 6f
                plain(a, b, uu, uu + (u1 - u0) * 0.1f, zz + 0.05f, zz + 0.23f + 0.12f * hsh(key + p + q * 7), goodsCols[(p + q + key and 0x7fffffff) % goodsCols.size], out + 0.022f)
            }
        }
        plain(a, b, (u0 + u1) / 2f - 0.004f, (u0 + u1) / 2f + 0.004f, z0, z1, Color(0xFF37474F), out + 0.025f)
        val um = (u0 + u1) / 2f
        glowAt(a[0] + (b[0] - a[0]) * um, a[1] + (b[1] - a[1]) * um, 1.3f, 2.4f, litCols[0], 0.3f)
    }

    // Ledges, a cornice, a darker base and corner trim on a shop, office or hospital wall
    fun facade(a: FloatArray, b: FloatArray, kind: Int, bh: Float, col: Color) {
        val dk = lerp(col, Color.Black, 0.28f)
        val lt = lerp(col, Color.White, 0.3f)
        plain(a, b, 0f, 1f, 0f, 0.5f, dk, 0.03f)
        plain(a, b, 0f, 1f, bh - 0.68f, bh - 0.55f, dk, 0.05f)
        plain(a, b, 0f, 1f, bh - 0.55f, bh, lt, 0.07f)
        if (kind == 1) {
            var z = 3.55f
            while (z < bh - 1f) {
                plain(a, b, 0f, 1f, z - 0.05f, z, dk, 0.03f)
                plain(a, b, 0f, 1f, z, z + 0.16f, lt, 0.045f)
                z += 3.2f
            }
        }
        if (kind == 2) {
            var z = 3.9f
            while (z < bh - 0.8f) {
                plain(a, b, 0f, 1f, z, z + 0.035f, dk, 0.008f)
                z += 0.34f
            }
            plain(a, b, 0f, 1f, 3.65f, 3.85f, lt, 0.06f)
        }
        if (kind == 3) {
            var z = 3f
            while (z < bh - 0.5f) {
                plain(a, b, 0f, 1f, z - 0.12f, z + 0.08f, Color(0xFFC9CDEB), 0.03f)
                z += 3f
            }
        }
        plain(a, b, 0f, 0.022f, 0f, bh, lt, 0.05f)
        plain(a, b, 0.978f, 1f, 0f, bh, lt, 0.05f)
    }

    // Siding, trim boards and a foundation on a house wall; shutters, a flower box and a step out front
    fun houseFacade(a: FloatArray, b: FloatArray, hh: Float, roadSide: Boolean, wallC: Color, key: Int, upper: Boolean) {
        val sid = lerp(wallC, Color.Black, 0.16f)
        var z = 0.4f
        while (z < hh - 0.15f) {
            plain(a, b, 0f, 1f, z, z + 0.03f, sid, 0.005f)
            z += 0.28f
        }
        plain(a, b, 0f, 1f, 0f, 0.32f, Color(0xFF8A867E), 0.03f)
        plain(a, b, 0f, 1f, hh - 0.16f, hh, houseTrim, 0.045f)
        plain(a, b, 0f, 0.03f, 0f, hh, houseTrim, 0.03f)
        plain(a, b, 0.97f, 1f, 0f, hh, houseTrim, 0.03f)
        if (!roadSide) return
        val sh = shutterCols[(key and 0x7fffffff) % shutterCols.size]
        val shD = lerp(sh, Color.Black, 0.4f)
        val wins = ArrayList<FloatArray>()
        wins.add(floatArrayOf(0.4f, 0.58f, 1.1f, 2.1f))
        if (upper) {
            wins.add(floatArrayOf(0.2f, 0.38f, 2.6f, 3.6f))
            wins.add(floatArrayOf(0.6f, 0.78f, 2.6f, 3.6f))
        }
        for (wv in wins) {
            val sw = (wv[1] - wv[0]) * 0.38f
            plain(a, b, wv[0] - sw - 0.012f, wv[0] - 0.012f, wv[2] - 0.05f, wv[3] + 0.05f, sh, 0.03f)
            plain(a, b, wv[1] + 0.012f, wv[1] + sw + 0.012f, wv[2] - 0.05f, wv[3] + 0.05f, sh, 0.03f)
            for (q in 1..4) {
                val zz = wv[2] + (wv[3] - wv[2]) * q / 5f
                plain(a, b, wv[0] - sw - 0.01f, wv[0] - 0.014f, zz, zz + 0.03f, shD, 0.035f)
                plain(a, b, wv[1] + 0.014f, wv[1] + sw + 0.01f, zz, zz + 0.03f, shD, 0.035f)
            }
        }
        // door trim and steps
        plain(a, b, 0.105f, 0.275f, 0f, 2.25f, houseTrim, 0.015f)
        plain(a, b, 0.1f, 0.28f, 0f, 0.16f, Color(0xFF9E9A92), 0.3f)
        plain(a, b, 0.1f, 0.28f, 0.16f, 0.3f, Color(0xFFB5B0A6), 0.18f)
        // a flower box under the front window
        plain(a, b, 0.39f, 0.59f, 0.85f, 1.0f, Color(0xFF6D4C41), 0.12f)
        for (q in 0..6) {
            val uu = 0.4f + q * 0.027f
            plain(a, b, uu, uu + 0.02f, 1.0f, 1.12f, flowerCols[q % flowerCols.size], 0.13f)
        }
    }

    // A box standing on the ground, ax/ay is its long direction.
    // deco draws details on each wall the camera can see.
    fun box(
        cx: Float, cy: Float, ax: Float, ay: Float, hl: Float, hw: Float, z0: Float, z1: Float,
        front: Color, side: Color, top: Color?, deco: ((FloatArray, FloatArray) -> Unit)? = null
    ) {
        val bx = -ay
        val by = ax
        val c = listOf(
            floatArrayOf(cx + ax * hl + bx * hw, cy + ay * hl + by * hw),
            floatArrayOf(cx + ax * hl - bx * hw, cy + ay * hl - by * hw),
            floatArrayOf(cx - ax * hl - bx * hw, cy - ay * hl - by * hw),
            floatArrayOf(cx - ax * hl + bx * hw, cy - ay * hl + by * hw)
        )
        for (i in 0..3) {
            val a = c[i]
            val b = c[(i + 1) % 4]
            val mx = (a[0] + b[0]) / 2f
            val my = (a[1] + b[1]) / 2f
            val nx = mx - cx
            val ny = my - cy
            if (nx * (cam.px - mx) + ny * (cam.py - my) > 0f) {
                poly(
                    listOf(
                        floatArrayOf(a[0], a[1], z0), floatArrayOf(b[0], b[1], z0),
                        floatArrayOf(b[0], b[1], z1), floatArrayOf(a[0], a[1], z1)
                    ),
                    if (i % 2 == 1) side else front
                )
                if (deco != null) deco(a, b)
            }
        }
        if (top != null && z1 < ch) poly(c.map { floatArrayOf(it[0], it[1], z1) }, snowTop(top))
    }

    // A flat shadow on the ground under a box
    fun shadow(cx: Float, cy: Float, ax: Float, ay: Float, hl: Float, hw: Float, col: Color) {
        val bx = -ay
        val by = ax
        val sx = cx + 1.2f
        val sy = cy + 1.2f
        poly(
            listOf(
                floatArrayOf(sx + ax * hl + bx * hw, sy + ay * hl + by * hw, 0.01f),
                floatArrayOf(sx + ax * hl - bx * hw, sy + ay * hl - by * hw, 0.01f),
                floatArrayOf(sx - ax * hl - bx * hw, sy - ay * hl - by * hw, 0.01f),
                floatArrayOf(sx - ax * hl + bx * hw, sy - ay * hl + by * hw, 0.01f)
            ), col
        )
    }

    // A solid with outline pb at height z0 and outline pt at z1 (same number of
    // corners): tapering pt gives rounded shoulders and sloped windshields
    fun prism(pb: List<FloatArray>, pt: List<FloatArray>, z0: Float, z1: Float, col: Color, top: Color?) {
        var gx = 0f
        var gy = 0f
        for (p in pb) {
            gx += p[0]
            gy += p[1]
        }
        gx /= pb.size
        gy /= pb.size
        for (i in pb.indices) {
            val a = pb[i]
            val b = pb[(i + 1) % pb.size]
            val at = pt[i]
            val bt = pt[(i + 1) % pt.size]
            var nx = b[1] - a[1]
            var ny = -(b[0] - a[0])
            val l = hypot(nx, ny)
            if (l < 0.0001f) continue
            nx /= l
            ny /= l
            val mx = (a[0] + b[0]) / 2f
            val my = (a[1] + b[1]) / 2f
            if (nx * (mx - gx) + ny * (my - gy) < 0f) {
                nx = -nx
                ny = -ny
            }
            val vx = cam.px - (mx + (at[0] + bt[0]) / 2f) / 2f
            val vy = cam.py - (my + (at[1] + bt[1]) / 2f) / 2f
            if (nx * vx + ny * vy > 0f) {
                poly(
                    listOf(
                        floatArrayOf(a[0], a[1], z0), floatArrayOf(b[0], b[1], z0),
                        floatArrayOf(bt[0], bt[1], z1), floatArrayOf(at[0], at[1], z1)
                    ), if (isLight(col)) col else shadeCol(col, nx, ny)
                )
            }
        }
        if (top != null && z1 < ch) poly(pt.map { floatArrayOf(it[0], it[1], z1) }, snowTop(top))
    }

    // In snow, the tops of things are white (lights stay lights)
    private fun snowTop(c: Color): Color = if (look.snow && !isLight(c)) snowTopCol else c

    // A round light with a shine and a glow, at a point in the world
    fun lamp(x: Float, y: Float, z: Float, col: Color, rad: Float) {
        at(x, y, z) { s, k ->
            ds.drawCircle(col, rad * k, s)
            ds.drawCircle(Color(0xB3FFFFFF), rad * k * 0.35f, Offset(s.x - rad * k * 0.3f, s.y - rad * k * 0.3f))
            val head = col == Color(0xFFFFF59D) || col == Color(0xFFFFF9C4)
            ds.glow(s.x, s.y, rad * k * (if (head) 9f else 6f), col, if (head) 0.5f else 0.45f)
            // a long reflection on a wet road
            if (look.rain) {
                val l = rad * k * 28f
                ds.drawRect(
                    Brush.verticalGradient(listOf(col.copy(alpha = 0.35f), col.copy(alpha = 0f)), startY = s.y + rad * k, endY = s.y + rad * k + l),
                    Offset(s.x - rad * k * 0.9f, s.y + rad * k), Size(rad * k * 1.8f, l), blendMode = BlendMode.Plus
                )
            }
        }
    }

    // A tyre with a hubcap, squashed by how much of its side the camera sees
    fun wheel(x: Float, y: Float, bx: Float, by: Float) {
        bill(x, y) { sx, sy, k ->
            val vx = cam.px - x
            val vy = cam.py - y
            val vl = max(0.01f, hypot(vx, vy))
            val fct = max(0.28f, abs((vx * bx + vy * by) / vl))
            val rw = 0.36f * k
            drawOval(g(Color(0xFF1B1B1B)), Offset(sx - rw * fct, sy - 2f * rw), Size(2f * rw * fct, 2f * rw))
            drawOval(g(Color(0xFFB0BEC5)), Offset(sx - rw * 0.5f * fct, sy - 1.5f * rw), Size(rw * fct, rw))
            drawOval(g(Color(0xFFECEFF1)), Offset(sx - rw * 0.28f * fct, sy - 1.28f * rw), Size(rw * 0.36f * fct, rw * 0.36f))
        }
    }

    // A rounded car: 0 sedan, 1 hatchback, 2 taxi, 3 box truck
    fun car(cx: Float, cy: Float, ax: Float, ay: Float, c: Color, type: Int) {
        val bx = -ay
        val by = ax
        val camFront = (cam.px - cx) * ax + (cam.py - cy) * ay > 0f
        val camLeft = (cam.px - cx) * bx + (cam.py - cy) * by > 0f
        poly(rr(cx + 0.25f, cy + 0.25f, ax, ay, 0f, 2.5f, 1.15f, 0.6f).map { floatArrayOf(it[0], it[1], 0.01f) }, Color(0x471E2346))
        val wl = arrayOf(floatArrayOf(1.35f, 1f), floatArrayOf(-1.35f, 1f), floatArrayOf(1.35f, -1f), floatArrayOf(-1.35f, -1f))
        // far wheels behind the body, near wheels in front of it
        for (wv in wl) if ((wv[1] > 0f) != camLeft) wheel(cx + ax * wv[0] + bx * wv[1] * 0.93f, cy + ay * wv[0] + by * wv[1] * 0.93f, bx, by)
        fun pair(along: Float, across: Float, z: Float, col: Color, rad: Float) {
            lamp(cx + ax * along + bx * across, cy + ay * along + by * across, z, col, rad)
            lamp(cx + ax * along - bx * across, cy + ay * along - by * across, z, col, rad)
        }
        if (type == 3) {
            fun cab() {
                prism(rr(cx, cy, ax, ay, 1.5f, 0.95f, 1.0f, 0.35f), rr(cx, cy, ax, ay, 1.45f, 0.9f, 0.95f, 0.3f), 0.3f, 1.2f, c, null)
                prism(rr(cx, cy, ax, ay, 1.45f, 0.9f, 0.95f, 0.3f), rr(cx, cy, ax, ay, 1.3f, 0.7f, 0.85f, 0.25f), 1.2f, 2.0f, carGlass, null)
                prism(rr(cx, cy, ax, ay, 1.3f, 0.7f, 0.85f, 0.25f), rr(cx, cy, ax, ay, 1.3f, 0.62f, 0.78f, 0.22f), 2.0f, 2.12f, c, lerp(c, Color.White, 0.2f))
                if (camFront) pair(2.43f, 0.65f, 0.75f, Color(0xFFFFF59D), 0.14f)
            }
            fun cargo() {
                prism(rr(cx, cy, ax, ay, -0.8f, 1.65f, 1.1f, 0.2f), rr(cx, cy, ax, ay, -0.8f, 1.65f, 1.1f, 0.2f), 0.4f, 2.8f, Color(0xFFECEFF1), Color(0xFFF5F5F5))
                if (!camFront) pair(-2.47f, 0.85f, 0.6f, Color(0xFFFF1744), 0.12f)
            }
            if (camFront) {
                cargo()
                cab()
            } else {
                cab()
                cargo()
            }
        } else {
            // bumper, lower body, rounded shoulder, glass cabin, roof
            prism(rr(cx, cy, ax, ay, 0f, 2.3f, 0.92f, 0.5f), rr(cx, cy, ax, ay, 0f, 2.3f, 0.92f, 0.5f), 0.28f, 0.45f, Color(0xFF455A64), null)
            prism(rr(cx, cy, ax, ay, 0f, 2.2f, 0.97f, 0.55f), rr(cx, cy, ax, ay, 0f, 2.2f, 0.97f, 0.55f), 0.4f, 0.78f, c, null)
            prism(rr(cx, cy, ax, ay, 0f, 2.2f, 0.97f, 0.55f), rr(cx, cy, ax, ay, -0.05f, 2.0f, 0.85f, 0.5f), 0.78f, 1.0f, c, lerp(c, Color.White, 0.15f))
            val cb = if (type == 1) floatArrayOf(-0.45f, 1.4f, 0.8f, -0.55f, 1.05f, 0.62f) else floatArrayOf(-0.25f, 1.2f, 0.82f, -0.35f, 0.75f, 0.62f)
            prism(rr(cx, cy, ax, ay, cb[0], cb[1], cb[2], 0.35f), rr(cx, cy, ax, ay, cb[3], cb[4], cb[5], 0.3f), 1.0f, 1.62f, carGlass, null)
            prism(rr(cx, cy, ax, ay, cb[3], cb[4], cb[5], 0.3f), rr(cx, cy, ax, ay, cb[3], cb[4] - 0.08f, cb[5] - 0.06f, 0.28f), 1.62f, 1.7f, c, lerp(c, Color.White, 0.25f))
            if (type == 2) prism(rr(cx, cy, ax, ay, cb[3], 0.3f, 0.3f, 0.08f), rr(cx, cy, ax, ay, cb[3], 0.26f, 0.26f, 0.06f), 1.7f, 1.95f, Color.White, Color(0xFFFFEB3B))
            if (camFront) pair(2.21f, 0.6f, 0.66f, Color(0xFFFFF9C4), 0.15f)
            else pair(-2.21f, 0.65f, 0.68f, Color(0xFFFF1744), 0.13f)
            if (rich) {
                // door seams, handles, a chrome line, mirrors and a number plate
                val sd = if (camLeft) 1f else -1f
                for (al in floatArrayOf(0.35f, -0.75f)) {
                    line(cx + ax * al + bx * sd * 0.98f, cy + ay * al + by * sd * 0.98f, 0.45f, cx + ax * al + bx * sd * 0.98f, cy + ay * al + by * sd * 0.98f, 1.0f, Color(0x80000000), 1.5f)
                }
                for (al in floatArrayOf(0.1f, -1.0f)) {
                    line(cx + ax * al + bx * sd * 0.99f, cy + ay * al + by * sd * 0.99f, 0.86f, cx + ax * (al + 0.22f) + bx * sd * 0.99f, cy + ay * (al + 0.22f) + by * sd * 0.99f, 0.86f, Color(0xFFCFD8DC), 2f)
                }
                line(cx + ax * 2f + bx * sd * 0.98f, cy + ay * 2f + by * sd * 0.98f, 0.62f, cx - ax * 2f + bx * sd * 0.98f, cy - ay * 2f + by * sd * 0.98f, 0.62f, Color(0x2EFFFFFF), 1.5f)
                for (s in floatArrayOf(1f, -1f)) {
                    box(cx + ax * 0.55f + bx * s * 1.07f, cy + ay * 0.55f + by * s * 1.07f, ax, ay, 0.1f, 0.09f, 0.95f, 1.13f, c, lerp(c, Color.Black, 0.25f), c)
                }
                val pa = if (camFront) 2.31f else -2.31f
                val px = cx + ax * pa
                val py = cy + ay * pa
                poly(
                    listOf(
                        floatArrayOf(px + bx * 0.32f, py + by * 0.32f, 0.48f), floatArrayOf(px - bx * 0.32f, py - by * 0.32f, 0.48f),
                        floatArrayOf(px - bx * 0.32f, py - by * 0.32f, 0.67f), floatArrayOf(px + bx * 0.32f, py + by * 0.32f, 0.67f)
                    ), Color(0xFFF5F2E6)
                )
            }
        }
        for (wv in wl) if ((wv[1] > 0f) == camLeft) wheel(cx + ax * wv[0] + bx * wv[1] * 0.93f, cy + ay * wv[0] + by * wv[1] * 0.93f, bx, by)
    }

    // Rear chevrons: solid red and fluorescent yellow-green stripes at 45 degrees that meet
    // in the middle as an arrow pointing up. wd is the width of the wall in world units.
    fun chevrons(a: FloatArray, b: FloatArray, z0: Float, z1: Float, wd: Float) {
        val s = 0.26f
        val hw = wd / 2f
        for (k in -12 until 12) {
            val c0 = k * s
            val c1 = c0 + s
            val col = if (k and 1 == 1) Color(0xFFE4FF1A) else Color(0xFFF0262B)
            for (m in 0..1) {
                // left half: x = hw - (z1 - z) - c; the right half is its mirror
                var q = listOf(
                    floatArrayOf(hw - (z1 - z0) - c1, z0), floatArrayOf(hw - (z1 - z0) - c0, z0),
                    floatArrayOf(hw - c0, z1), floatArrayOf(hw - c1, z1)
                )
                if (m == 1) q = q.map { floatArrayOf(wd - it[0], it[1]) }
                val cl = clipRect(q, if (m == 1) hw else 0f, if (m == 1) wd else hw, z0, z1)
                if (cl.size > 2) wpoly(a, b, cl.map { floatArrayOf(it[0] / wd, it[1]) }, col, 0.013f)
            }
        }
    }

    // Our own ambulance seen from the drone: white box with a red band, light bar,
    // roof unit, star of life, lit rear windows and chevrons on the back
    fun ambulance(cx: Float, cy: Float, ax: Float, ay: Float, t: Float) {
        val bx = -ay
        val by = ax
        val flash = floor(t * 4f).toInt() % 2 == 0
        fun px(al: Float, ac: Float) = cx + ax * al + bx * ac
        fun py(al: Float, ac: Float) = cy + ay * al + by * ac
        // headlight beams on the road ahead, and red / blue light splashed around us
        poly(
            listOf(
                floatArrayOf(px(2.7f, 1f), py(2.7f, 1f), 0.02f), floatArrayOf(px(2.7f, -1f), py(2.7f, -1f), 0.02f),
                floatArrayOf(px(12f, -2.6f), py(12f, -2.6f), 0.02f), floatArrayOf(px(12f, 2.6f), py(12f, 2.6f), 0.02f)
            ), Color(0x2EFFF0C8), raw = true, blend = BlendMode.Plus
        )
        poly(
            listOf(
                floatArrayOf(px(12f, -2.6f), py(12f, -2.6f), 0.02f), floatArrayOf(px(12f, 2.6f), py(12f, 2.6f), 0.02f),
                floatArrayOf(px(22f, 4.5f), py(22f, 4.5f), 0.02f), floatArrayOf(px(22f, -4.5f), py(22f, -4.5f), 0.02f)
            ), Color(0x14FFF0C8), raw = true, blend = BlendMode.Plus
        )
        at(cx, cy, 0f) { s, k -> ds.pool(s.x, s.y, 7.5f * k * 1.4f, 7.5f * k * 0.8f, if (flash) Color(0xFFFF1E3C) else Color(0xFF286EFF), 0.34f) }
        poly(rr(cx + 0.3f, cy + 0.3f, ax, ay, 0f, 2.95f, 1.35f, 0.5f).map { floatArrayOf(it[0], it[1], 0.01f) }, Color(0x80050614))
        for (wv in arrayOf(floatArrayOf(1.7f, 1f), floatArrayOf(-1.6f, 1f), floatArrayOf(1.7f, -1f), floatArrayOf(-1.6f, -1f))) {
            wheel(px(wv[0], wv[1] * 1.02f), py(wv[0], wv[1] * 1.02f), bx, by)
        }
        val white = Color(0xFFF4F4F8)
        val red = Color(0xFFE53935)
        // cab
        prism(rr(cx, cy, ax, ay, 1.9f, 0.75f, 1.05f, 0.32f), rr(cx, cy, ax, ay, 1.85f, 0.7f, 1.0f, 0.3f), 0.35f, 1.35f, white, null)
        prism(rr(cx, cy, ax, ay, 1.85f, 0.7f, 1.0f, 0.3f), rr(cx, cy, ax, ay, 1.62f, 0.5f, 0.92f, 0.26f), 1.35f, 2.15f, carGlass, null)
        prism(rr(cx, cy, ax, ay, 1.62f, 0.5f, 0.92f, 0.26f), rr(cx, cy, ax, ay, 1.62f, 0.46f, 0.88f, 0.22f), 2.15f, 2.25f, white, Color(0xFFE8E8F0))
        for (sd in floatArrayOf(1f, -1f)) box(px(1.65f, sd * 1.18f), py(1.65f, sd * 1.18f), ax, ay, 0.08f, 0.13f, 1.35f, 1.75f, Color(0xFF263238), Color(0xFF37474F), Color(0xFF455A64))
        // patient box
        val boxLen = 1.85f
        box(px(-0.75f, 0f), py(-0.75f, 0f), ax, ay, boxLen, 1.18f, 0.35f, 2.95f, white, white, Color(0xFFECEEF4)) { a, b ->
            val len = hypot(b[0] - a[0], b[1] - a[1])
            if (len > 3f) {
                // sides: red band, thin blue line, compartment doors, a small lit window, scene lights
                plain(a, b, 0f, 1f, 1.2f, 1.55f, red, 0.01f)
                plain(a, b, 0f, 1f, 1.6f, 1.66f, Color(0xFF1E88E5), 0.01f)
                for (u in floatArrayOf(0.08f, 0.3f, 0.55f)) {
                    plain(a, b, u, u + 0.18f, 0.5f, 1.12f, Color(0xFFD7DAE3), 0.01f)
                    plain(a, b, u + 0.08f, u + 0.1f, 0.75f, 0.8f, Color(0xFF90A4AE), 0.02f)
                }
                plain(a, b, 0.78f, 0.92f, 1.85f, 2.45f, Color(0xFF2B3A50), 0.01f)
                plain(a, b, 0.79f, 0.91f, 1.9f, 2.4f, Color(0xFFFFE8B0), 0.015f)
                plain(a, b, 0.04f, 0.12f, 2.62f, 2.8f, Color(0xFFFFF9C4), 0.01f)
                plain(a, b, 0.88f, 0.96f, 2.62f, 2.8f, if (flash) Color(0xFFFF1744) else Color(0xFFB71C1C), 0.01f)
            } else if (faces(a, b, px(-0.75f, 0f), py(-0.75f, 0f), -ax, -ay) > 0f) {
                // the back: two doors with windows into the lit patient bay, chevrons, tail lights
                plain(a, b, 0.04f, 0.96f, 0.45f, 2.7f, Color(0xFFECEEF4), 0.005f)
                plain(a, b, 0.495f, 0.505f, 0.45f, 2.7f, Color(0xFF90A4AE), 0.01f)
                for (u in floatArrayOf(0.1f, 0.58f)) plain(a, b, u, u + 0.32f, 1.75f, 2.45f, Color(0xFF2B3A50), 0.01f)
                for (u in floatArrayOf(0.16f, 0.62f)) plain(a, b, u, u + 0.22f, 1.95f, 2.4f, Color(0xFFE8C890), 0.015f)
                chevrons(a, b, 0.45f, 1.5f, len)
                plain(a, b, 0f, 0.06f, 1.4f, 2f, Color(0xFFFF1744), 0.02f)
                plain(a, b, 0.94f, 1f, 1.4f, 2f, Color(0xFFFF1744), 0.02f)
                plain(a, b, 0f, 0.06f, 2.7f, 2.9f, if (flash) Color(0xFFFF1744) else Color(0xFFB71C1C), 0.02f)
                plain(a, b, 0.94f, 1f, 2.7f, 2.9f, if (flash) Color(0xFF0D47A1) else Color(0xFF2979FF), 0.02f)
                glowAt(a[0] * 0.97f + b[0] * 0.03f, a[1] * 0.97f + b[1] * 0.03f, 1.7f, 0.9f, Color(0xFFFF1744), 0.45f)
                glowAt(a[0] * 0.03f + b[0] * 0.97f, a[1] * 0.03f + b[1] * 0.97f, 1.7f, 0.9f, Color(0xFFFF1744), 0.45f)
            }
        }
        // rear step bumper
        box(px(-2.72f, 0f), py(-2.72f, 0f), ax, ay, 0.14f, 1.05f, 0.3f, 0.55f, Color(0xFF37474F), Color(0xFF263238), Color(0xFF546E7A))
        // roof: air conditioner, vents, star of life
        box(px(-1.9f, 0f), py(-1.9f, 0f), ax, ay, 0.5f, 0.62f, 2.95f, 3.3f, Color(0xFFCFD8DC), Color(0xFFB0BEC5), Color(0xFFE0E6EA))
        for (k in -2..2) line(px(-1.9f + k * 0.18f, -0.5f), py(-1.9f + k * 0.18f, -0.5f), 3.31f, px(-1.9f + k * 0.18f, 0.5f), py(-1.9f + k * 0.18f, 0.5f), 3.31f, Color(0x993C465A), 1.5f)
        for (v in floatArrayOf(0.75f, -0.75f)) box(px(-0.3f, v), py(-0.3f, v), ax, ay, 0.18f, 0.18f, 2.95f, 3.08f, Color(0xFFB0BEC5), Color(0xFF90A4AE), Color(0xFF78909C))
        val sx = px(-0.55f, 0f)
        val sy = py(-0.55f, 0f)
        for (i in 0..2) {
            val ang = i * PI.toFloat() / 3f
            val ux = ax * cos(ang) + bx * sin(ang)
            val uy = ay * cos(ang) + by * sin(ang)
            val vx = -uy
            val vy = ux
            poly(
                listOf(
                    floatArrayOf(sx + ux * 0.7f + vx * 0.2f, sy + uy * 0.7f + vy * 0.2f, 2.97f), floatArrayOf(sx + ux * 0.7f - vx * 0.2f, sy + uy * 0.7f - vy * 0.2f, 2.97f),
                    floatArrayOf(sx - ux * 0.7f - vx * 0.2f, sy - uy * 0.7f - vy * 0.2f, 2.97f), floatArrayOf(sx - ux * 0.7f + vx * 0.2f, sy - uy * 0.7f + vy * 0.2f, 2.97f)
                ), Color(0xFF1E88E5)
            )
        }
        // light bar across the front of the box: red, white and blue segments
        box(px(0.95f, 0f), py(0.95f, 0f), ax, ay, 0.2f, 1.15f, 2.95f, 3.05f, Color(0xFF263238), Color(0xFF263238), Color(0xFF37474F))
        val segOn = arrayOf(Color(0xFFFF1744), Color(0xFFFEFEFE), Color(0xFF2979FF), Color(0xFF2979FF), Color(0xFFFEFEFE), Color(0xFFFF1744))
        val segOff = arrayOf(Color(0xFFB71C1C), Color(0xFF9EA7B8), Color(0xFF0D47A1), Color(0xFF0D47A1), Color(0xFF9EA7B8), Color(0xFFB71C1C))
        for (i in 0..5) {
            val on = (i < 3) == flash
            val col = if (on) segOn[i] else segOff[i]
            val ac = -1.0f + i * 0.4f
            box(px(0.95f, ac), py(0.95f, ac), ax, ay, 0.17f, 0.17f, 3.05f, 3.22f, col, col, col)
            if (on) glowAt(px(0.95f, ac), py(0.95f, ac), 3.15f, if (i == 1 || i == 4) 1.4f else 1.9f, if (i == 1 || i == 4) Color(0xFFDDE8FF) else col, 0.45f)
        }
        for (sd in floatArrayOf(0.5f, -0.5f)) {
            val col = if (sd > 0f) (if (flash) Color(0xFFFF1744) else Color(0xFFB71C1C)) else (if (flash) Color(0xFF0D47A1) else Color(0xFF2979FF))
            box(px(1.75f, sd), py(1.75f, sd), ax, ay, 0.1f, 0.18f, 2.25f, 2.36f, col, Color(0xFF37474F), col)
        }
    }

    // Something flat that always faces the camera: gives screen x, y of its base and pixels per world unit
    fun bill(x: Float, y: Float, fn: DrawScope.(Float, Float, Float) -> Unit) {
        val p = tc(x, y, 0f)
        if (p[2] < NEAR) return
        val s = pr(p)
        ds.fn(s.x, s.y, f / p[2])
    }
}

// Cuts a shape to a rectangle (x0..x1 across, z0..z1 up)
private fun clipRect(pts: List<FloatArray>, x0: Float, x1: Float, z0: Float, z1: Float): List<FloatArray> {
    var p = pts
    for (e in 0..3) {
        val o = ArrayList<FloatArray>()
        fun inside(q: FloatArray) = when (e) {
            0 -> q[0] >= x0
            1 -> q[0] <= x1
            2 -> q[1] >= z0
            else -> q[1] <= z1
        }
        fun cut(a: FloatArray, b: FloatArray): FloatArray = when (e) {
            0, 1 -> {
                val xe = if (e == 0) x0 else x1
                val t = (xe - a[0]) / (b[0] - a[0])
                floatArrayOf(xe, a[1] + (b[1] - a[1]) * t)
            }
            else -> {
                val ze = if (e == 2) z0 else z1
                val t = (ze - a[1]) / (b[1] - a[1])
                floatArrayOf(a[0] + (b[0] - a[0]) * t, ze)
            }
        }
        for (i in p.indices) {
            val a = p[i]
            val b = p[(i + 1) % p.size]
            val ai = inside(a)
            val bi = inside(b)
            if (ai) o.add(a)
            if (ai != bi) o.add(cut(a, b))
        }
        p = o
        if (p.isEmpty()) break
    }
    return p
}

// Which way a wall faces compared with a direction: > 0 means the wall faces that way
private fun faces(a: FloatArray, b: FloatArray, cx: Float, cy: Float, dx: Float, dy: Float): Float =
    ((a[0] + b[0]) / 2f - cx) * dx + ((a[1] + b[1]) / 2f - cy) * dy

private fun DrawScope.drawWorld(city: CityData, plan: DrivePlan, t: Float, destKey: Int, crashAt: FloatArray?, tr: Traffic, lab: Labels, drone: Boolean) {
    val d = plan.distAt(t)
    // amb is where the ambulance is; cam is where we look from (the drone sits above and behind it)
    val amb = plan.camAt(d)
    val cam = if (drone) DriveCam(amb.px - amb.hx * DRONE_BACK, amb.py - amb.hy * DRONE_BACK, amb.hx, amb.hy, amb.seg) else amb
    val r = Renderer(this, cam, drone)
    val w = size.width
    val h = size.height
    val n = city.n
    val sc = w / 390f
    drawSky(r, cam, t)
    r.poly(
        listOf(floatArrayOf(-9f, -9f, 0f), floatArrayOf(n * BL + 9f, -9f, 0f), floatArrayOf(n * BL + 9f, n * BL + 9f, 0f), floatArrayOf(-9f, n * BL + 9f, 0f)),
        look.road, raw = true
    )
    fun near(x: Float, y: Float, lim: Float) = hypot(x - cam.px, y - cam.py) < lim
    val seam = Color(0xFFC3C6D6)
    for (row in 0 until n) for (col in 0 until n) {
        val x0 = col * BL
        val yy = row * BL
        if (!near(x0 + BL / 2f, yy + BL / 2f, 260f)) continue
        r.ground(x0 + 7f, yy + 7f, x0 + BL - 7f, yy + BL - 7f, Color(0xFFA9ACBE))
        r.ground(x0 + 7.35f, yy + 7.35f, x0 + BL - 7.35f, yy + BL - 7.35f, Color(0xFFD9DBE8))
        if (near(x0 + BL / 2f, yy + BL / 2f, 90f)) {
            var s = 10f
            while (s < BL - 10f) {
                r.ground(x0 + s, yy + 7.35f, x0 + s + 0.12f, yy + 10f, seam)
                r.ground(x0 + s, yy + BL - 10f, x0 + s + 0.12f, yy + BL - 7.35f, seam)
                r.ground(x0 + 7.35f, yy + s, x0 + 10f, yy + s + 0.12f, seam)
                r.ground(x0 + BL - 10f, yy + s, x0 + BL - 7.35f, yy + s + 0.12f, seam)
                s += 3f
            }
        }
        val gc = if (look.snow) snowGround else lerp(zoneGround[city.zone[row * n + col]], Color(0xFF2B2B2B), 0.08f * city.dens[row * n + col])
        r.ground(x0 + 10f, yy + 10f, x0 + BL - 10f, yy + BL - 10f, gc)
    }
    val yellow = Color(0xFFFFE66B)
    val total = n * BL
    var s = 0f
    while (s < total) {
        val m = s % BL
        if (m >= 8f && m <= BL - 10f) {
            for (j in 0..n) {
                val y = j * BL
                if (!near(s, y, 140f)) continue
                if (city.owH[j] == 0) r.ground(s, y - 0.2f, s + 4f, y + 0.2f, yellow)
                else {
                    r.ground(s, y - 1.95f, s + 3f, y - 1.6f, Color.White)
                    r.ground(s, y + 1.6f, s + 3f, y + 1.95f, Color.White)
                }
            }
            for (i in 0..n) {
                val x = i * BL
                if (!near(x, s, 140f)) continue
                if (city.owV[i] == 0) r.ground(x - 0.2f, s, x + 0.2f, s + 4f, yellow)
                else {
                    r.ground(x - 1.95f, s, x - 1.6f, s + 3f, Color.White)
                    r.ground(x + 1.6f, s, x + 1.95f, s + 3f, Color.White)
                }
            }
        }
        s += 8f
    }
    // edge lines and manhole covers
    val edge = Color(0xFFE8E8EE)
    val manhole = Color(0xFF5F6385)
    for (j in 0..n) for (c in 0 until n) {
        val y = j * BL
        val x = c * BL
        if (near(x + BL / 2f, y, 160f)) {
            r.ground(x + 10f, y - 6.6f, x + BL - 10f, y - 6.4f, edge)
            r.ground(x + 10f, y + 6.4f, x + BL - 10f, y + 6.6f, edge)
            r.disc(x + 20f + 20f * hsh(j * 13 + c), y + (if (hsh(c * 7 + j) > 0.5f) 3.5f else -3.5f), 0.6f, manhole)
        }
        val vx = j * BL
        val vy = c * BL
        if (near(vx, vy + BL / 2f, 160f)) {
            r.ground(vx - 6.6f, vy + 10f, vx - 6.4f, vy + BL - 10f, edge)
            r.ground(vx + 6.4f, vy + 10f, vx + 6.6f, vy + BL - 10f, edge)
            r.disc(vx + (if (hsh(c * 3 + j + 500) > 0.5f) 3.5f else -3.5f), vy + 20f + 20f * hsh(j * 11 + c + 500), 0.6f, manhole)
        }
    }
    // crosswalks and stop bars
    val zebra = Color(0xFFF4F4F8)
    for (j in 0..n) for (i in 0..n) {
        val x = i * BL
        val y = j * BL
        if (!near(x, y, 120f)) continue
        var q = -6f
        while (q <= 6f) {
            r.ground(x + q - 0.5f, y - 10f, x + q + 0.5f, y - 7.5f, zebra)
            r.ground(x + q - 0.5f, y + 7.5f, x + q + 0.5f, y + 10f, zebra)
            r.ground(x - 10f, y + q - 0.5f, x - 7.5f, y + q + 0.5f, zebra)
            r.ground(x + 7.5f, y + q - 0.5f, x + 10f, y + q + 0.5f, zebra)
            q += 2f
        }
        r.ground(x - 11f, y + 0.3f, x - 10.5f, y + 7f, Color.White)
        r.ground(x + 10.5f, y - 7f, x + 11f, y - 0.3f, Color.White)
        r.ground(x - 7f, y - 11f, x - 0.3f, y - 10.5f, Color.White)
        r.ground(x + 0.3f, y + 10.5f, x + 7f, y + 11f, Color.White)
    }
    roadDetail(r, city, cam, t)

    val objs = ArrayList<Obj>()
    val exs = intArrayOf(-1, 0, 0, 1)
    val eys = intArrayOf(0, -1, 1, 0)
    val stationKey = city.key(city.station)
    for (row in 0 until n) for (col in 0 until n) {
        val z = city.zone[row * n + col]
        val dn = city.dens[row * n + col]
        val bx = col * BL + BL / 2f
        val by = row * BL + BL / 2f
        for (e in 0..3) {
            val cx = bx + exs[e] * (BL / 2f - 17f)
            val cy = by + eys[e] * (BL / 2f - 17f)
            val dd = hypot(cx - cam.px, cy - cam.py)
            if (dd > 220f) continue
            val key = (row * n + col) * 4 + e
            val hl = city.hospKey[key]
            // 0 house, 1 office, 2 shop, 3 hospital, 4 station
            var kind: Int
            var bh: Float
            var front: Color
            var side: Color
            when {
                hl != null -> { kind = 3; bh = 12f + 2f * hl; front = Color(0xFFF4F4F8); side = Color(0xFFB8BDE6) }
                key == stationKey -> { kind = 4; bh = 7f; front = Color(0xFFE53935); side = Color(0xFFB71C1C) }
                z == 0 -> { kind = 0; bh = 5f + 2f * dn; front = Color(0xFFFFE0B2); side = Color(0xFFE6C690) }
                z == 1 -> { kind = 1; bh = 12f + 9f * dn; front = Color(0xFF4FC3F7); side = Color(0xFF1E9BDB) }
                else -> { kind = 2; bh = 8f + 2.5f * dn; front = Color(0xFFFFB74D); side = Color(0xFFF29A2E) }
            }
            val ax = if (eys[e] != 0) 1f else 0f
            val ay = if (eys[e] != 0) 0f else 1f
            // the road is on this side of the building
            val rfx = exs[e].toFloat()
            val rfy = eys[e].toFloat()
            val detail = dd < 120f
            val isDest = key == destKey
            val isHosp = kind == 3
            val hh = 3.2f + 1.2f * dn
            val topH = if (kind == 0) hh + 2.6f else bh
            // shadows go down first, under everything
            objs.add(Obj(dd + 400f) {
                if (kind == 0) r.shadow(cx, cy, ax, ay, 14.5f, 6.5f, shadowCol)
                else r.shadow(cx, cy, ax, ay, 16f, 8f, shadowCol)
            })
            objs.add(Obj(dd) {
                r.rich = dd < 80f
                if (kind == 0) {
                    // two homes per lot: pitched roof, chimney, door, garage and windows
                    for (sd in intArrayOf(-1, 1)) {
                        val hx = cx + ax * sd * 7.5f
                        val hy = cy + ay * sd * 7.5f
                        val pi = (key * 2 + (if (sd > 0) 1 else 0)) % 4
                        val wallC = houseWalls[pi * 2]
                        val sideC = houseWalls[pi * 2 + 1]
                        r.box(hx, hy, ax, ay, 6.2f, 5.5f, 0f, hh, wallC, sideC, null) { a, b ->
                            if (detail) {
                                val roadSide = faces(a, b, hx, hy, rfx, rfy) > 0.5f
                                if (r.rich) r.houseFacade(a, b, hh, roadSide, if (hypot(b[0] - a[0], b[1] - a[1]) > 11.5f) sideC else wallC, key * 2 + sd, dn > 0)
                                if (roadSide) {
                                    r.quad(a, b, 0.12f, 0.26f, 0f, 2.1f, Color(0xFF8D5524), 0.02f)
                                    r.quad(a, b, 0.16f, 0.22f, 0.9f, 1.1f, Color(0xFFFFD54F))
                                    // porch light by the door
                                    if (hsh(key * 3 + sd) > 0.25f) r.glowAt(a[0] + (b[0] - a[0]) * 0.19f, a[1] + (b[1] - a[1]) * 0.19f, 2.4f, 2.2f, look.lamp, 0.5f)
                                    r.quad(a, b, 0.4f, 0.58f, 1.1f, 2.1f, Color.White, 0.02f)
                                    r.quad(a, b, 0.42f, 0.56f, 1.2f, 2.0f, glassBlue)
                                    r.quad(a, b, 0.66f, 0.92f, 0f, 1.9f, Color(0xFFECEFF1), 0.02f)
                                    for (k in 0..3) r.quad(a, b, 0.66f, 0.92f, 0.4f + k * 0.4f, 0.45f + k * 0.4f, Color(0xFFB0BEC5))
                                    if (dn > 0) {
                                        for (u in floatArrayOf(0.2f, 0.6f)) {
                                            r.quad(a, b, u, u + 0.18f, 2.6f, 3.6f, Color.White, 0.02f)
                                            r.quad(a, b, u + 0.02f, u + 0.16f, 2.7f, 3.5f, glassBlue)
                                        }
                                    }
                                } else {
                                    r.quad(a, b, 0.35f, 0.65f, 1.1f, 2.1f, glassBlue, 0.02f)
                                }
                            }
                        }
                        // roof: ridge along the long side, the slope facing away drawn first
                        val roofC = roofCols[(key + (if (sd > 0) 1 else 0)) % 4]
                        val roofD = lerp(roofC, Color.Black, 0.25f)
                        val rh = hh + 2.6f
                        val pbx = -ay
                        val pby = ax
                        val camSide = if ((cam.px - hx) * pbx + (cam.py - hy) * pby > 0f) 1f else -1f
                        for (sg in floatArrayOf(-camSide, camSide)) {
                            r.poly(
                                listOf(
                                    floatArrayOf(hx + ax * 6.6f + pbx * sg * 6.1f, hy + ay * 6.6f + pby * sg * 6.1f, hh - 0.2f),
                                    floatArrayOf(hx - ax * 6.6f + pbx * sg * 6.1f, hy - ay * 6.6f + pby * sg * 6.1f, hh - 0.2f),
                                    floatArrayOf(hx - ax * 6.6f, hy - ay * 6.6f, rh),
                                    floatArrayOf(hx + ax * 6.6f, hy + ay * 6.6f, rh)
                                ),
                                if (look.snow) (if (sg == camSide) snowTopCol else Color(0xFFC5CEDE)) else if (sg == camSide) roofC else roofD
                            )
                            // rows of shingles, seen from the drone
                            if (drone && detail && !look.snow) {
                                val sh = lerp(roofC, Color.Black, 0.35f)
                                for (k in 1..5) {
                                    val p = k / 6f
                                    val zz = hh - 0.2f + (rh - hh + 0.2f) * p + 0.02f
                                    r.line(
                                        hx + ax * 6.6f + pbx * sg * 6.1f * (1f - p), hy + ay * 6.6f + pby * sg * 6.1f * (1f - p), zz,
                                        hx - ax * 6.6f + pbx * sg * 6.1f * (1f - p), hy - ay * 6.6f + pby * sg * 6.1f * (1f - p), zz, sh, 1.2f * sc
                                    )
                                }
                            }
                        }
                        for (end in floatArrayOf(1f, -1f)) {
                            val ex = hx + ax * end * 6.2f
                            val ey = hy + ay * end * 6.2f
                            if ((ex - hx) * (cam.px - ex) + (ey - hy) * (cam.py - ey) > 0f) {
                                r.poly(
                                    listOf(
                                        floatArrayOf(ex + pbx * 5.5f, ey + pby * 5.5f, hh),
                                        floatArrayOf(ex - pbx * 5.5f, ey - pby * 5.5f, hh),
                                        floatArrayOf(ex, ey, rh - 0.3f)
                                    ), sideC
                                )
                            }
                        }
                        if (detail) r.box(hx + ax * 3f, hy + ay * 3f, ax, ay, 0.5f, 0.5f, hh, rh + 0.6f, Color(0xFF8D6E63), Color(0xFF6D4C41), null)
                        // bushes along the front of the house, some in flower
                        if (r.rich) for (kq in intArrayOf(-1, 1)) {
                            val flowers = hsh(key + kq * 7 + sd) > 0.5f
                            r.bill(hx + rfx * 6.2f + ax * kq * 4.6f, hy + rfy * 6.2f + ay * kq * 4.6f) { sx, sy, k -> bush(sx, sy, k, flowers) }
                        }
                    }
                    // white picket fence along the sidewalk, with a gap for the gate
                    if (dd < 90f) {
                        val fx = cx + rfx * 6.8f
                        val fy = cy + rfy * 6.8f
                        var q = -14f
                        while (q <= 14f) {
                            if (abs(q) >= 1.2f) {
                                r.bill(fx + ax * q, fy + ay * q) { px, py, k ->
                                    val fc = g(Color.White)
                                    drawRect(fc, Offset(px - 0.12f * k, py - 0.9f * k), Size(0.24f * k, 0.9f * k))
                                    drawRect(fc, Offset(px - 0.55f * k, py - 0.65f * k), Size(1.1f * k, 0.1f * k))
                                    if (look.snow) drawRect(g(snowWhite), Offset(px - 0.55f * k, py - 0.7f * k), Size(1.1f * k, 0.05f * k))
                                }
                            }
                            q += 1.1f
                        }
                    }
                } else {
                    r.box(cx, cy, ax, ay, 15f, 7f, 0f, bh, front, side, if (drone) lerp(side, Color.Black, 0.3f) else null) { a, b ->
                        if (detail) {
                            if (r.rich) r.facade(a, b, kind, bh, if (hypot(b[0] - a[0], b[1] - a[1]) > 20f) side else front)
                            val roadSide = faces(a, b, cx, cy, rfx, rfy) > 0.5f
                            val len = hypot(b[0] - a[0], b[1] - a[1])
                            when (kind) {
                                1 -> {
                                    // office: white floor bands with glass panes, lobby at street level
                                    val floors = floor(bh / 3.2f).toInt()
                                    val cols = max(2, floor(len / 3.4f).toInt())
                                    for (k in 0 until floors) {
                                        val z0 = 0.4f + k * 3.2f
                                        if (k == 0 && roadSide) continue
                                        r.quad(a, b, 0.02f, 0.98f, z0 + 0.5f, z0 + 2.5f, Color.White, 0.02f)
                                        for (q in 0 until cols) {
                                            r.quad(
                                                a, b, (q + 0.15f) / cols, (q + 0.85f) / cols, z0 + 0.62f, z0 + 2.38f,
                                                if ((q + k) % 3 == 0) Color(0xFFBBDEFB) else Color(0xFF2F7FC1)
                                            )
                                        }
                                    }
                                    if (roadSide) {
                                        r.quad(a, b, 0.05f, 0.95f, 0.2f, 2.9f, Color(0xFFB3E5FC), 0.02f)
                                        for (q in 1..5) r.quad(a, b, q / 6f - 0.005f, q / 6f + 0.005f, 0.2f, 2.9f, Color.White)
                                        r.quad(a, b, 0.42f, 0.58f, 0f, 2.6f, Color(0xFF263238), 0.04f)
                                        r.quad(a, b, 0.03f, 0.97f, 3.0f, 3.4f, Color.White)
                                    }
                                }
                                2 -> {
                                    if (roadSide) {
                                        // three shop fronts with striped awnings and sign boards
                                        for (q in 0..2) {
                                            val u0 = 0.05f + q * 0.32f
                                            val u1 = u0 + 0.26f
                                            val st = (u1 - u0) / 6f
                                            r.quad(a, b, u0, u1, 0.3f, 2.6f, Color(0xFF37474F), 0.02f)
                                            r.quad(a, b, u0 + 0.02f, u1 - 0.02f, 0.4f, 2.5f, Color(0xFF90CAF9))
                                            r.quad(a, b, u0, u1, 2.8f, 3.6f, awningCols[(q + key) % 3], 0.6f)
                                            for (k in 0..2) r.quad(a, b, u0 + (2 * k + 1) * st, u0 + (2 * k + 2) * st, 2.8f, 3.6f, Color.White, 0.62f)
                                            r.quad(a, b, u0 + 0.03f, u1 - 0.03f, 4.0f, 4.9f, Color(0xFFFFF8E1), 0.02f)
                                        }
                                        var k = 0
                                        while (5.5f + k * 3f + 1.6f < bh - 0.6f) {
                                            for (q in 0..4) r.quad(a, b, 0.06f + q * 0.19f, 0.17f + q * 0.19f, 5.5f + k * 3f, 5.5f + k * 3f + 1.6f, glassBlue, 0.02f)
                                            k++
                                        }
                                    } else {
                                        for (q in 0..3) r.quad(a, b, 0.1f + q * 0.22f, 0.24f + q * 0.22f, bh * 0.5f, bh * 0.5f + 1.4f, glassBlue, 0.02f)
                                        r.quad(a, b, 0.35f, 0.65f, 0f, 3.4f, Color(0xFFB0BEC5), 0.02f)
                                        for (k in 0..5) r.quad(a, b, 0.35f, 0.65f, 0.3f + k * 0.5f, 0.36f + k * 0.5f, Color(0xFF78909C))
                                    }
                                }
                                3 -> {
                                    val floors = floor(bh / 3f).toInt()
                                    for (k in 1 until floors) for (q in 0..6) {
                                        r.quad(a, b, 0.05f + q * 0.135f, 0.14f + q * 0.135f, k * 3f + 0.5f, k * 3f + 2f, Color(0xFF81D4FA), 0.02f)
                                    }
                                    if (roadSide) {
                                        r.quad(a, b, 0.35f, 0.65f, 0f, 2.6f, Color(0xFF4FC3F7), 0.02f)
                                        r.quad(a, b, 0.3f, 0.7f, 2.6f, 3.0f, Color(0xFFE53935), 0.4f)
                                    }
                                }
                                else -> {
                                    if (roadSide) {
                                        for (u in floatArrayOf(0.1f, 0.55f)) {
                                            r.quad(a, b, u, u + 0.35f, 0f, 4.2f, Color(0xFFECEFF1), 0.02f)
                                            for (k in 0..5) r.quad(a, b, u, u + 0.35f, 0.5f + k * 0.65f, 0.6f + k * 0.65f, Color(0xFFB0BEC5))
                                        }
                                        r.quad(a, b, 0f, 1f, 5f, 6f, Color.White)
                                    }
                                }
                            }
                        }
                    }
                    if (detail) {
                        // roof edge, air conditioners and an antenna on offices
                        val rim = lerp(side, Color.Black, 0.15f)
                        r.box(cx, cy, ax, ay, 15.2f, 7.2f, bh, bh + 0.5f, rim, rim, null)
                        r.box(cx + ax * 6f, cy + ay * 6f, ax, ay, 1.6f, 1.2f, bh, bh + 1.4f, Color(0xFFB0BEC5), Color(0xFF90A4AE), if (drone) Color(0xFFCFD8DC) else null)
                        r.box(cx - ax * 5f, cy - ay * 5f, ax, ay, 1.1f, 1.1f, bh, bh + 1f, Color(0xFFCFD8DC), Color(0xFF90A4AE), if (drone) Color(0xFFECEFF1) else null)
                        if (kind == 1) r.box(cx - ax * 9f, cy - ay * 9f, ax, ay, 0.15f, 0.15f, bh, bh + 5f, Color(0xFF9E9E9E), Color(0xFF757575), null)
                        if (drone) roofDetail(r, cx, cy, ax, ay, bh, kind, key)
                    }
                }
                if (isHosp || isDest) {
                    // sign on the roof facing the road
                    val sx = cx - exs[e] * 7.5f
                    val sy = cy - eys[e] * 7.5f
                    r.bill(sx, sy) { px, py, k ->
                        if (isHosp) {
                            // lit sign: white box, red cross, red glow
                            drawRect(Color(0xFFFFF3C4), Offset(px - 1.4f * k, py - (topH + 2.8f) * k), Size(2.8f * k, 2.8f * k))
                            drawRect(Color(0xFFF0262B), Offset(px - 0.35f * k, py - (topH + 2.5f) * k), Size(0.7f * k, 2.2f * k))
                            drawRect(Color(0xFFF0262B), Offset(px - 1.1f * k, py - (topH + 1.75f) * k), Size(2.2f * k, 0.7f * k))
                            glow(px, py - (topH + 1.4f) * k, 4f * k, Color(0xFFFF3B3B), 0.5f)
                        }
                        if (isDest) {
                            val bob = 0.6f * sin(t * 4f)
                            val top = py - (topH + 6.5f + bob) * k
                            val arrow = Path()
                            arrow.moveTo(px - 1.6f * k, top)
                            arrow.lineTo(px + 1.6f * k, top)
                            arrow.lineTo(px, top + 2.2f * k)
                            arrow.close()
                            drawPath(arrow, Color(0xFF3FA2FF))
                            drawCircle(Color(0x553FA2FF), 2.6f * k, Offset(px, top + 0.8f * k))
                        }
                    }
                }
            })
        }
    }
    // trees, street lamps, benches and fire hydrants along the sidewalks
    for (row in 0 until n) for (col in 0 until n) {
        val z = city.zone[row * n + col]
        val x0 = col * BL
        val yy = row * BL
        if (!near(x0 + BL / 2f, yy + BL / 2f, 180f)) continue
        var idx = 0
        var sAlong = 16f
        while (sAlong <= BL - 16f) {
            val spots = arrayOf(
                floatArrayOf(x0 + sAlong, yy + 9.1f), floatArrayOf(x0 + sAlong, yy + BL - 9.1f),
                floatArrayOf(x0 + 9.1f, yy + sAlong), floatArrayOf(x0 + BL - 9.1f, yy + sAlong)
            )
            for (p in spots) {
                val myIdx = idx
                idx++
                val dd = hypot(p[0] - cam.px, p[1] - cam.py)
                if (dd > 140f || dd < 1.5f) continue
                val hv = hsh(row * 977 + col * 131 + myIdx)
                val px0 = p[0]
                val py0 = p[1]
                when {
                    (myIdx shr 2) % 2 == 1 -> objs.add(Obj(dd) {
                        r.bill(px0, py0) { sx, sy, k ->
                            val pole = g(Color(0xFF546E7A))
                            drawRect(pole, Offset(sx - 0.07f * k, sy - 4.2f * k), Size(0.14f * k, 4.2f * k))
                            drawRect(pole, Offset(sx - 0.07f * k, sy - 4.2f * k), Size(0.6f * k, 0.1f * k))
                            drawOval(Color(0xFFFFF59D), Offset(sx + 0.33f * k, sy - 4.17f * k), Size(0.44f * k, 0.24f * k))
                            glow(sx + 0.55f * k, sy - 4f * k, 2.6f * k, look.lamp, 0.55f)
                            pool(sx + 0.55f * k, sy, 4.5f * k, 4.5f * k * (if (drone) 0.7f else 0.22f), look.lamp, look.pool * 0.45f)
                        }
                    })
                    z == 1 && hv < 0.5f -> objs.add(Obj(dd) {
                        r.bill(px0, py0) { sx, sy, k ->
                            val wood = g(Color(0xFF6D4C41))
                            val leg = g(Color(0xFF37474F))
                            drawRect(wood, Offset(sx - 0.7f * k, sy - 0.5f * k), Size(1.4f * k, 0.12f * k))
                            drawRect(wood, Offset(sx - 0.7f * k, sy - 0.9f * k), Size(1.4f * k, 0.1f * k))
                            drawRect(leg, Offset(sx - 0.6f * k, sy - 0.5f * k), Size(0.08f * k, 0.5f * k))
                            drawRect(leg, Offset(sx + 0.52f * k, sy - 0.5f * k), Size(0.08f * k, 0.5f * k))
                        }
                    })
                    z == 2 && hv < 0.6f -> objs.add(Obj(dd) {
                        r.bill(px0, py0) { sx, sy, k ->
                            val hyd = g(Color(0xFFE53935))
                            drawRect(hyd, Offset(sx - 0.16f * k, sy - 0.7f * k), Size(0.32f * k, 0.7f * k))
                            drawCircle(hyd, 0.16f * k, Offset(sx, sy - 0.7f * k))
                            drawRect(hyd, Offset(sx - 0.26f * k, sy - 0.5f * k), Size(0.52f * k, 0.12f * k))
                        }
                    })
                    else -> {
                        val pine = z == 0 && hv > 0.7f
                        val tall = if (z == 0) 1f else 0.8f
                        objs.add(Obj(dd) {
                            r.bill(px0, py0) { sx, sy, k -> tree(sx, sy, k, hv, pine, tall) }
                        })
                    }
                }
            }
            sAlong += 11f
        }
    }
    streetThings(r, city, cam, objs)
    // traffic: cars travel the length of each street, one-way streets go one way.
    // Cars yield to the ambulance: they stop at the stop line of any intersection
    // it is about to cross, hurry out of that intersection if already in it,
    // stop if the ambulance is in their path, and never drive into the car ahead.
    val dt = (t - tr.lastT).coerceIn(0f, 0.1f)
    tr.lastT = t
    val bodyX = floatArrayOf(amb.px + amb.hx * 1.5f, amb.px - amb.hx * 1f, amb.px - amb.hx * 3.5f)
    val bodyY = floatArrayOf(amb.py + amb.hy * 1.5f, amb.py - amb.hy * 1f, amb.py - amb.hy * 3.5f)
    fun touchesAmbo(x: Float, y: Float, rad: Float): Boolean {
        for (q in 0..2) if (hypot(x - bodyX[q], y - bodyY[q]) < rad) return true
        return false
    }
    // route intersections the ambulance is close to and has not yet cleared
    val active = ArrayList<FloatArray>()
    for (q in 1 until plan.pts.size - 1) {
        val ix = plan.pts[q][0]
        val iy = plan.pts[q][1]
        val dx = ix - amb.px
        val dy = iy - amb.py
        if (hypot(dx, dy) < 70f && dx * amb.hx + dy * amb.hy > -14f) active.add(floatArrayOf(ix, iy))
    }
    fun isActive(x: Float, y: Float): Boolean {
        for (a in active) if (abs(a[0] - x) < 1f && abs(a[1] - y) < 1f) return true
        return false
    }
    var cid = 0
    fun streetCars(horiz: Boolean, idx: Int, ow: Int) {
        val dirs = if (ow == 0) intArrayOf(1, -1) else intArrayOf(if (ow == 1) (if (horiz) 1 else -1) else (if (horiz) -1 else 1))
        for (dr in dirs) {
            val lanes = if (ow == 0) floatArrayOf(5.25f) else floatArrayOf(0f, 3.5f)
            val ax = if (horiz) dr.toFloat() else 0f
            val ay = if (horiz) 0f else dr.toFloat()
            val rx = -ay
            val ry = ax
            val across = idx * BL
            for (ln in lanes) {
                val ids = IntArray(3)
                val sps = FloatArray(3)
                val us = FloatArray(3)
                fun place(k: Int): Float {
                    val id = ids[k]
                    var u = ((id * 97 % 100) / 100f * total + dr * sps[k] * (t - tr.delay[id % tr.delay.size])) % total
                    if (u < 0f) u += total
                    return u
                }
                for (k in 0..2) {
                    cid++
                    ids[k] = cid
                    sps[k] = 5f + (cid * 37 % 5)
                    us[k] = place(k)
                }
                for (k in 0..2) {
                    val u = us[k]
                    val cx = (if (horiz) u else across) + rx * ln
                    val cy = (if (horiz) across else u) + ry * ln
                    val fu = u + dr * 2.2f
                    val ru = u - dr * 2.2f
                    val nextA = if (dr > 0) kotlin.math.ceil(fu / BL) * BL else floor(fu / BL) * BL
                    val prevA = nextA - dr * BL
                    val gap = abs(nextA - fu)
                    val nx = if (horiz) nextA else across
                    val ny = if (horiz) across else nextA
                    val px = if (horiz) prevA else across
                    val py = if (horiz) across else prevA
                    var halt = false
                    var hurry = false
                    if (gap >= 10.5f && gap <= 12.5f && isActive(nx, ny)) halt = true
                    else if ((gap < 10.5f && isActive(nx, ny)) || (dr * (ru - prevA) < 10.5f && isActive(px, py))) hurry = true
                    // the ambulance is in the car's path
                    if (!touchesAmbo(cx, cy, 2.6f)) {
                        var sAhead = 0f
                        while (sAhead <= 6f) {
                            if (touchesAmbo(cx + ax * (2.2f + sAhead), cy + ay * (2.2f + sAhead), 2.6f)) halt = true
                            sAhead += 1.5f
                        }
                    }
                    // the car ahead in this lane
                    for (o in 0..2) {
                        if (o == k) continue
                        val ahead = dr * (us[o] - u)
                        if (ahead > 0f && ahead < 6.6f) halt = true
                    }
                    val slot = ids[k] % tr.delay.size
                    if (halt) tr.delay[slot] += dt
                    else if (hurry) tr.delay[slot] -= dt
                }
                for (k in 0..2) {
                    val id = ids[k]
                    val u = place(k)
                    val cx = (if (horiz) u else across) + rx * ln
                    val cy = (if (horiz) across else u) + ry * ln
                    val dd = hypot(cx - cam.px, cy - cam.py)
                    if (dd > 200f) continue
                    // in the cab we sit inside the ambulance, from the drone we see it, so keep cars off it
                    if (if (drone) touchesAmbo(cx, cy, 2.4f) else dd < 3f) continue
                    // 0 sedan, 1 hatchback, 2 taxi, 3 box truck
                    val type = if (id % 5 == 4) 3 else if (id % 5 == 1) 1 else if (id % 7 == 3) 2 else 0
                    val c = if (type == 2) Color(0xFFFFC107) else carCols[id % carCols.size]
                    objs.add(Obj(dd) { r.car(cx, cy, ax, ay, c, type) })
                }
            }
        }
    }
    for (j in 0..n) streetCars(true, j, city.owH[j])
    for (i in 0..n) streetCars(false, i, city.owV[i])
    // pedestrians on the sidewalks
    val faceK = w * 0.036f
    var pid = 0
    fun streetPeds(horiz: Boolean, idx: Int) {
        for (sd in floatArrayOf(-8.5f, 8.5f)) for (k in 0..7) {
            pid++
            val dr = if (pid % 2 == 1) 1f else -1f
            var u = ((pid * 61 % 100) / 100f * total + dr * 1.3f * t) % total
            if (u < 0f) u += total
            val um = u % BL
            if (um < 11f || um > BL - 11f) continue
            val x = if (horiz) u else idx * BL + sd
            val y = if (horiz) idx * BL + sd else u
            val dd = hypot(x - cam.px, y - cam.py)
            if (dd > 120f) continue
            val pl = PedLook(
                pid, g(pedCols[pid % pedCols.size]), g(skinCols[pid % skinCols.size]), g(hairCols[pid % hairCols.size]),
                g(pantsCols[pid % pantsCols.size]), pid % 6 == 2, pid % 9 == 4, pid % 4 == 1, pid % 5 == 3 || look.snow, g(capCols[pid % 3])
            )
            val kid = pid % 7 == 3
            val phone = pid % 4 == 3 && !kid
            objs.add(Obj(dd) {
                r.bill(x, y) { px, py, k2 ->
                    val kk = if (kid) k2 * 0.72f else k2
                    person(px, py, kk, pl, t, faceK)
                    // a phone screen lights the face
                    if (phone) {
                        drawRect(Color(0xFF9FD3FF), Offset(px - 0.3f * kk, py - 1.5f * kk), Size(0.1f * kk, 0.16f * kk))
                        glow(px - 0.25f * kk, py - 1.5f * kk, 0.7f * kk, Color(0xFF9FD3FF), 0.35f)
                    }
                }
            })
        }
    }
    for (j in 0..n) streetPeds(true, j)
    for (i in 0..n) streetPeds(false, i)
    // stop signs and traffic lights, the same as on the map
    val corners = listOf(floatArrayOf(-9f, -9f), floatArrayOf(9f, -9f), floatArrayOf(-9f, 9f), floatArrayOf(9f, 9f))
    for (j in 0..n) for (i in 0..n) {
        val lt = city.light[j * city.m + i]
        for (o in corners) {
            val px0 = i * BL + o[0]
            val py0 = j * BL + o[1]
            val dd = hypot(px0 - cam.px, py0 - cam.py)
            if (dd > 150f) continue
            objs.add(Obj(dd) {
                r.bill(px0, py0) { sx, sy, k ->
                    val poleH = if (lt) 3.6f else 2.4f
                    drawRect(g(Color(0xFF78909C)), Offset(sx - 0.06f * k, sy - poleH * k), Size(0.12f * k, poleH * k))
                    if (lt) {
                        drawRoundRect(g(Color(0xFF2B313A)), Offset(sx - 0.28f * k, sy - 5.1f * k), Size(0.56f * k, 1.6f * k), CornerRadius(0.1f * k, 0.1f * k))
                        if (look.snow) drawRect(snowWhite, Offset(sx - 0.28f * k, sy - 5.16f * k), Size(0.56f * k, 0.1f * k))
                        val red = floor(t / 3f).toInt() % 2 == 1
                        drawCircle(if (red) Color(0xFFFF5252) else Color(0xFF3A1616), 0.16f * k, Offset(sx, sy - 4.8f * k))
                        drawCircle(Color(0xFF3A3016), 0.16f * k, Offset(sx, sy - 4.3f * k))
                        drawCircle(if (red) Color(0xFF12301F) else Color(0xFF69F0AE), 0.16f * k, Offset(sx, sy - 3.8f * k))
                        if (red) glow(sx, sy - 4.8f * k, 1.1f * k, Color(0xFFFF5252), 0.5f)
                        else glow(sx, sy - 3.8f * k, 1.1f * k, Color(0xFF69F0AE), 0.5f)
                        // street name sign
                        drawRect(g(Color(0xFF2E7D32)), Offset(sx - 0.7f * k, sy - 3.3f * k), Size(1.4f * k, 0.3f * k))
                        drawRect(g(Color.White), Offset(sx - 0.55f * k, sy - 3.2f * k), Size(1.1f * k, 0.08f * k))
                    } else {
                        for (ring in 0..1) {
                            val rad = if (ring == 0) 0.4f else 0.36f
                            val oct = Path()
                            for (q in 0..7) {
                                val a = (22.5 + 45.0 * q) * PI / 180.0
                                val xq = sx + (cos(a) * rad * k).toFloat()
                                val yq = sy - 2.6f * k + (sin(a) * rad * k).toFloat()
                                if (q == 0) oct.moveTo(xq, yq) else oct.lineTo(xq, yq)
                            }
                            oct.close()
                            drawPath(oct, g(if (ring == 0) Color.White else Color(0xFFE53935)))
                        }
                        if (dd < 60f) {
                            val ls = 0.56f * k / lab.stop.size.width
                            translate(sx - lab.stop.size.width * ls / 2f, sy - 2.6f * k - lab.stop.size.height * ls / 2f) {
                                scale(ls, Offset.Zero) { drawText(lab.stop) }
                            }
                        } else {
                            drawRect(g(Color.White), Offset(sx - 0.24f * k, sy - 2.64f * k), Size(0.48f * k, 0.08f * k))
                        }
                    }
                }
            })
        }
    }
    if (crashAt != null) {
        val dd = hypot(crashAt[0] - cam.px, crashAt[1] - cam.py)
        objs.add(Obj(dd) {
            r.box(crashAt[0], crashAt[1], 0.7f, 0.7f, 2.2f, 0.95f, 0.3f, 1.1f, Color(0xFF616161), Color(0xFF424242), Color(0xFF757575))
        })
    }
    // from the drone we see our own ambulance
    if (drone) objs.add(Obj(DRONE_BACK) { r.ambulance(amb.px, amb.py, amb.hx, amb.hy, t) })
    objs.sortByDescending { it.d }
    for (o in objs) o.fn()
    weather(r, cam, t, drone)
}

// how far behind the ambulance the drone flies
private const val DRONE_BACK = 10.5f

// Night sky, stars, moon, clouds and a far skyline that turn with the ambulance, then the ground below
private fun DrawScope.drawSky(r: Renderer, cam: DriveCam, t: Float) {
    val w = size.width
    val h = size.height
    val hy = r.horizon.coerceIn(0f, h)
    if (hy > 1f) drawRect(Brush.verticalGradient(look.sky, startY = 0f, endY = hy), Offset.Zero, Size(w, hy + 1f))
    val head = atan2(cam.hy, cam.hx)
    if (look.stars) for (i in 0 until 140) {
        val sx = hsh(i * 3 + 1) * w
        val sy = hsh(i * 5 + 2) * hy * 0.8f
        val a = 0.25f + 0.6f * hsh(i * 7) * (0.8f + 0.2f * sin(t * 2f + i))
        drawCircle(Color.White.copy(alpha = a.coerceIn(0f, 1f)), if (hsh(i * 11) > 0.92f) 1.4f else 0.8f, Offset(sx, sy))
    }
    if (look.moon) {
        val mx = w * 0.76f
        val my = hy * 0.36f
        glow(mx, my, 80f * w / 390f, Color(0xFF9FB8FF), 0.3f)
        val mr = 15f * w / 390f
        drawCircle(Color(0xFFEEF1FF), mr, Offset(mx, my))
        val crater = Color(0x73AAB4D7)
        drawCircle(crater, mr * 0.23f, Offset(mx - mr * 0.27f, my - mr * 0.2f))
        drawCircle(crater, mr * 0.17f, Offset(mx + mr * 0.33f, my + mr * 0.27f))
        drawCircle(crater, mr * 0.12f, Offset(mx + mr * 0.13f, my - mr * 0.47f))
    }
    val ks = w / 390f
    for (i in 0 until 8) {
        val da = wrapAng(i / 8f * 2f * PI.toFloat() - head + t * 0.004f)
        if (abs(da) > 1f) continue
        val sx = w / 2f + tan(da) * r.f
        val sy = hy * (0.25f + 0.3f * hsh(i))
        val s = ((if (look.rain) 70f else 40f) + 30f * hsh(i + 9)) * ks
        for (q in arrayOf(floatArrayOf(0f, 0f, 1f), floatArrayOf(-0.8f, 0.25f, 0.7f), floatArrayOf(0.85f, 0.2f, 0.75f), floatArrayOf(0.35f, -0.35f, 0.7f))) {
            drawOval(look.clouds, Offset(sx + q[0] * s - s * q[2], sy + q[1] * s - s * q[2] * 0.5f), Size(2f * s * q[2], s * q[2]))
        }
    }
    // warm city glow low in the sky
    val gh = 50f * ks
    drawRect(Brush.verticalGradient(listOf(Color(0x00FFAA5A), Color(0x24FFAA5A)), startY = hy - gh, endY = hy), Offset(0f, hy - gh), Size(w, gh))
    // far skyline with a few lit windows and red lights on the tall ones
    val winA = Color(0xB3FFCD78)
    for (i in 0 until 60) {
        val da = wrapAng(i / 60f * 2f * PI.toFloat() - head)
        if (abs(da) > 1.1f) continue
        val sx = w / 2f + tan(da) * r.f
        val bw = (18f + 20f * hsh(i * 3)) * ks
        val bh = (12f + 40f * hsh(i * 5)) * ks
        drawRect(look.skyline, Offset(sx - bw / 2f, hy - bh), Size(bw, bh + 1f))
        var yy = hy - bh + 4f * ks
        while (yy < hy - 3f * ks) {
            var xx = sx - bw / 2f + 3f * ks
            while (xx < sx + bw / 2f - 3f * ks) {
                if (hsh(((xx - sx) * 13f + (hy - yy) * 7f).toInt() + i * 977) < look.skyWin * 0.4f) drawRect(winA, Offset(xx, yy), Size(1.6f * ks, 2f * ks))
                xx += 4f * ks
            }
            yy += 5f * ks
        }
        if (bh > 42f * ks && sin(t * 3f + i) > -0.3f) {
            drawCircle(Color(0xFFFF3030), 1.5f * ks, Offset(sx, hy - bh - 1.5f * ks))
            glow(sx, hy - bh - 1.5f * ks, 6f * ks, Color(0xFFFF3030), 0.5f)
        }
    }
    drawRect(g(if (look.snow) snowGround else Color(0xFF9BD468)), Offset(0f, hy), Size(w, h - hy))
}

// Grain, cracks, drains and puddles on the road, snow banks and tyre tracks in snow,
// and cones around the block that is closed for road work
private fun roadDetail(r: Renderer, city: CityData, cam: DriveCam, t: Float) {
    val n = city.n
    val m = city.m
    fun near(x: Float, y: Float, lim: Float) = hypot(x - cam.px, y - cam.py) < lim
    val grainL = Color(0x0DFFFFFF)
    val grainD = Color(0x33000000)
    val crack = Color(0x59000000)
    val drain = Color(0xFF11141C)
    val bar = Color(0xFF3A3F52)
    val puddle = Color(0x2E7896C8)
    val bank = Color(0xFFE6ECF6)
    val bankTop = Color(0xFFF4F7FC)
    val track = Color(0x590A0C14)
    for (horiz in booleanArrayOf(true, false)) for (j in 0..n) for (c in 0 until n) {
        // a block of road from node (c, j) to (c + 1, j), turned for the other direction
        fun X(along: Float, across: Float) = if (horiz) c * BL + along else j * BL + across
        fun Y(along: Float, across: Float) = if (horiz) j * BL + across else c * BL + along
        fun rect(a0: Float, c0: Float, a1: Float, c1: Float, col: Color, raw: Boolean) {
            r.poly(
                listOf(
                    floatArrayOf(X(a0, c0), Y(a0, c0), 0f), floatArrayOf(X(a1, c0), Y(a1, c0), 0f),
                    floatArrayOf(X(a1, c1), Y(a1, c1), 0f), floatArrayOf(X(a0, c1), Y(a0, c1), 0f)
                ), col, raw
            )
        }
        if (!near(X(BL / 2f, 0f), Y(BL / 2f, 0f), 100f)) continue
        val key = (if (horiz) 1 else 7) * 7919 + j * 131 + c * 977
        for (i in 0 until 60) {
            val a = hsh(key + i * 3) * BL
            val b = -6.4f + hsh(key * 3 + i * 7) * 12.8f
            rect(a, b, a + 0.22f, b + 0.22f, if (hsh(i * 5 + j) > 0.5f) grainL else grainD, true)
        }
        if (look.snow) {
            for (sd in floatArrayOf(-1f, 1f)) {
                rect(9f, sd * 6.05f - 0.35f, BL - 9f, sd * 6.05f + 0.35f, bank, false)
                rect(9f, sd * 6.6f - 0.15f, BL - 9f, sd * 6.6f + 0.15f, bankTop, false)
            }
            for (ln in floatArrayOf(-5.25f, -1.75f, 1.75f, 5.25f)) for (o in floatArrayOf(-0.75f, 0.75f))
                rect(10.5f, ln + o - 0.22f, BL - 10.5f, ln + o + 0.22f, track, true)
        } else {
            for (i in 0..2) {
                var a = 10f + hsh(key + i) * 40f
                var b = -5f + hsh(key + i * 9) * 10f
                for (q in 0..4) {
                    val na = a + 0.8f + hsh(q + i * 7 + key) * 0.8f
                    val nb = b + (hsh(q * 3 + i + key) - 0.5f) * 0.9f
                    r.line(X(a, b), Y(a, b), 0.01f, X(na, nb), Y(na, nb), 0.01f, crack, 0.8f)
                    a = na
                    b = nb
                }
            }
        }
        // storm drains by the curb
        for (sd in floatArrayOf(-1f, 1f)) {
            rect(12f, sd * 6.3f - 0.25f, 13.2f, sd * 6.3f + 0.25f, drain, false)
            for (q in 0..4) rect(12.1f + q * 0.22f, sd * 6.3f - 0.2f, 12.2f + q * 0.22f, sd * 6.3f + 0.2f, bar, false)
        }
        if (look.rain) {
            val pa = 20f + hsh(key + 11) * 20f
            val pb = if (hsh(key + 5) > 0.5f) 4f else -4f
            val pts = ArrayList<FloatArray>()
            for (k in 0 until 14) {
                val an = k / 14f * 2f * PI.toFloat()
                val rr2 = 1f + 0.35f * sin(k * 2.3f)
                val aa = pa + 1.8f * rr2 * cos(an)
                val bb = pb + 0.9f * rr2 * sin(an)
                pts.add(floatArrayOf(X(aa, bb), Y(aa, bb), 0.01f))
            }
            r.poly(pts, puddle, raw = true)
        }
    }
    // cones and a flashing light where the road is closed
    if (city.workA >= 0 && city.events.contains(EV_WORK)) {
        val ax0 = (city.workA % m) * BL
        val ay0 = (city.workA / m) * BL
        val bx0 = (city.workB % m) * BL
        val by0 = (city.workB / m) * BL
        if (near((ax0 + bx0) / 2f, (ay0 + by0) / 2f, 120f)) {
            val dx = (bx0 - ax0) / BL
            val dy = (by0 - ay0) / BL
            val on = floor(t * 3f).toInt() % 2 == 0
            for (end in floatArrayOf(10f, BL - 10f)) {
                for (q in -3..3) {
                    val x = ax0 + dx * end - dy * q * 1.6f
                    val y = ay0 + dy * end + dx * q * 1.6f
                    r.bill(x, y) { sx, sy, k ->
                        val cone = Path()
                        cone.moveTo(sx - 0.28f * k, sy)
                        cone.lineTo(sx, sy - 0.9f * k)
                        cone.lineTo(sx + 0.28f * k, sy)
                        cone.close()
                        drawPath(cone, g(Color(0xFFFF6D00)))
                        drawRect(g(Color.White), Offset(sx - 0.14f * k, sy - 0.5f * k), Size(0.28f * k, 0.1f * k))
                    }
                }
                r.glowAt(ax0 + dx * end, ay0 + dy * end, 1f, 1.5f, Color(0xFFFFB300), if (on) 0.6f else 0.15f)
            }
        }
    }
}

// Bus stops, newspaper boxes, trash cans and wheelie bins along the sidewalks
private fun DrawScope.streetThings(r: Renderer, city: CityData, cam: DriveCam, objs: ArrayList<Obj>) {
    val n = city.n
    for (row in 0 until n) for (col in 0 until n) {
        val z = city.zone[row * n + col]
        val x0 = col * BL
        val yy = row * BL
        val sides = arrayOf(
            floatArrayOf(x0 + BL / 2f, yy + 8.2f, 1f, 0f), floatArrayOf(x0 + BL / 2f, yy + BL - 8.2f, 1f, 0f),
            floatArrayOf(x0 + 8.2f, yy + BL / 2f, 0f, 1f), floatArrayOf(x0 + BL - 8.2f, yy + BL / 2f, 0f, 1f)
        )
        for (si in sides.indices) {
            val sd = sides[si]
            val dd = hypot(sd[0] - cam.px, sd[1] - cam.py)
            if (dd > 90f) continue
            val hv = hsh(row * 71 + col * 13 + si)
            val ax = sd[2]
            val ay = sd[3]
            if (z == 1 && hv < 0.6f) {
                // bus stop shelter with a lit ad and people waiting
                val bx = sd[0] + ax * 5f
                val by = sd[1] + ay * 5f
                val p1 = PedLook(21 + si, g(Color(0xFF00897B)), g(Color(0xFFC68642)), g(Color(0xFF212121)), g(Color(0xFF263238)), true, false, false, look.snow, g(Color(0xFFE53935)))
                val p2 = PedLook(30 + si, g(Color(0xFF8E24AA)), g(Color(0xFFFFDBAC)), g(Color(0xFFFBC02D)), g(Color(0xFF1A237E)), false, false, true, look.snow, g(Color(0xFF1E88E5)))
                objs.add(Obj(dd) {
                    r.box(bx, by, ax, ay, 2.2f, 0.5f, 2.45f, 2.6f, Color(0xFF37474F), Color(0xFF263238), if (look.snow) snowTopCol else Color(0xFF37474F))
                    for (e in floatArrayOf(-2.1f, 2.1f)) r.box(bx + ax * e, by + ay * e, ax, ay, 0.06f, 0.06f, 0f, 2.45f, Color(0xFF546E7A), Color(0xFF455A64), null)
                    r.box(bx + ax * 2.2f, by + ay * 2.2f, ax, ay, 0.08f, 0.5f, 0.3f, 2.3f, Color(0xFFFFF3C4), Color(0xFFFFF3C4), null)
                    r.glowAt(bx + ax * 2.2f, by + ay * 2.2f, 1.3f, 2.2f, Color(0xFFFFE7A0), 0.45f)
                    r.bill(bx - ax * 0.8f, by - ay * 0.8f) { px, py, k -> person(px, py, k, p1, 0f, size.width * 0.036f) }
                    r.bill(bx + ax * 0.7f, by + ay * 0.7f) { px, py, k ->
                        person(px, py, k, p2, 0f, size.width * 0.036f)
                        drawRect(Color(0xFF9FD3FF), Offset(px - 0.3f * k, py - 1.5f * k), Size(0.1f * k, 0.16f * k))
                        glow(px - 0.25f * k, py - 1.5f * k, 0.7f * k, Color(0xFF9FD3FF), 0.35f)
                    }
                })
            } else if (z == 2) {
                // newspaper boxes and a trash can
                val bx = sd[0] - ax * 12f
                val by = sd[1] - ay * 12f
                objs.add(Obj(dd) {
                    val top = if (look.snow) snowTopCol else null
                    r.box(bx, by, ax, ay, 0.3f, 0.3f, 0f, 1.1f, Color(0xFF1E88E5), Color(0xFF1565C0), top ?: Color(0xFF42A5F5))
                    r.box(bx + ax * 0.8f, by + ay * 0.8f, ax, ay, 0.3f, 0.3f, 0f, 1.1f, Color(0xFFE53935), Color(0xFFC62828), top ?: Color(0xFFEF5350))
                    r.box(bx + ax * 2.2f, by + ay * 2.2f, ax, ay, 0.35f, 0.35f, 0f, 1f, Color(0xFF455A64), Color(0xFF37474F), top ?: Color(0xFF263238))
                })
            } else if (z == 0 && hv < 0.7f) {
                // wheelie bins at the curb
                val bx = sd[0] + ax * (hv * 20f - 10f)
                val by = sd[1] + ay * (hv * 20f - 10f)
                objs.add(Obj(dd) {
                    val top = if (look.snow) snowTopCol else null
                    r.box(bx, by, ax, ay, 0.3f, 0.3f, 0f, 1f, Color(0xFF2E7D32), Color(0xFF1B5E20), top ?: Color(0xFF388E3C))
                    r.box(bx + ax * 0.8f, by + ay * 0.8f, ax, ay, 0.3f, 0.3f, 0f, 1f, Color(0xFF546E7A), Color(0xFF455A64), top ?: Color(0xFF607D8B))
                })
            }
        }
    }
}

// A round bush, with flowers on some
private fun DrawScope.bush(sx: Float, sy: Float, k: Float, flowers: Boolean) {
    drawCircle(g(Color(0xFF2E7D32)), 0.6f * k, Offset(sx, sy - 0.45f * k))
    drawCircle(g(Color(0xFF43A047)), 0.35f * k, Offset(sx - 0.15f * k, sy - 0.6f * k))
    if (look.snow) drawArc(g(snowWhite), 180f, 180f, true, Offset(sx - 0.55f * k, sy - 1.05f * k), Size(1.1f * k, 0.5f * k))
    else if (flowers) for (q in 0..3) {
        drawCircle(g(flowerCols[q % flowerCols.size]), 0.09f * k, Offset(sx + (q - 1.5f) * 0.25f * k, sy - (0.5f + 0.2f * (q % 2)) * k))
    }
}

// Things on the flat roofs, seen from the drone
private fun roofDetail(r: Renderer, cx: Float, cy: Float, ax: Float, ay: Float, bh: Float, kind: Int, key: Int) {
    val z = bh + 0.03f
    fun rn(i: Int) = hsh(key * 31 + i)
    val bx = -ay
    val by = ax
    if (kind == 3) {
        // helipad with green lights around it
        r.disc(cx, cy, 4.2f, Color(0xFF3A3F55), z, 20)
        r.disc(cx, cy, 3.8f, Color(0xFFFFD54F), z + 0.01f, 20)
        r.disc(cx, cy, 3.4f, Color(0xFF3A3F55), z + 0.02f, 20)
        r.flat(cx, cy, ax, ay, 0f, -1.3f, 1.8f, 0.35f, z + 0.03f, Color(0xFFFFD54F))
        r.flat(cx, cy, ax, ay, 0f, 1.3f, 1.8f, 0.35f, z + 0.03f, Color(0xFFFFD54F))
        r.flat(cx, cy, ax, ay, 0f, 0f, 0.3f, 1.3f, z + 0.03f, Color(0xFFFFD54F))
        for (k in 0 until 8) {
            val a = k / 8f * 2f * PI.toFloat()
            r.glowAt(cx + 4.4f * cos(a), cy + 4.4f * sin(a), z, 0.6f, Color(0xFF69F0AE), 0.7f)
        }
        return
    }
    if (kind == 1) {
        // water tank on legs, a door out to the roof, skylights
        val tx = cx - ax * 9f + bx * 3.5f
        val ty = cy - ay * 9f + by * 3.5f
        for (l in arrayOf(floatArrayOf(-0.8f, -0.8f), floatArrayOf(0.8f, -0.8f), floatArrayOf(-0.8f, 0.8f), floatArrayOf(0.8f, 0.8f)))
            r.box(tx + l[0], ty + l[1], 1f, 0f, 0.08f, 0.08f, bh, bh + 1.4f, Color(0xFF5D4037), Color(0xFF4E342E), null)
        val ring = rr(tx, ty, 1f, 0f, 0f, 1.3f, 1.3f, 0.55f)
        r.prism(ring, ring, bh + 1.4f, bh + 3.6f, Color(0xFF8D6E63), Color(0xFF6D4C41))
        r.prism(ring, rr(tx, ty, 1f, 0f, 0f, 0.3f, 0.3f, 0.1f), bh + 3.6f, bh + 4.3f, Color(0xFF5D4037), if (look.snow) snowTopCol else null)
        r.box(cx + ax * 10f, cy + ay * 10f, ax, ay, 1.2f, 1f, bh, bh + 2.2f, Color(0xFF90A4AE), Color(0xFF78909C), if (look.snow) snowTopCol else Color(0xFFB0BEC5))
        for (i in 0..2) {
            val al = -3f + i * 3.2f
            val lit = rn(i + 40) > 0.4f
            r.flat(cx, cy, ax, ay, al, -3.5f, 1.1f, 0.8f, z + 0.02f, Color(0xFFC9D3E0))
            r.flat(cx, cy, ax, ay, al, -3.5f, 0.95f, 0.65f, z + 0.03f, if (lit) Color(0xFFFFE3A0) else Color(0xFF2B3550))
            if (lit) r.glowAt(cx + ax * al - bx * 3.5f, cy + ay * al - by * 3.5f, z + 0.1f, 1.8f, Color(0xFFFFD98A), 0.25f)
        }
    }
    if (kind == 2) {
        // solar panels and vents
        for (i in 0..3) for (j in 0..1) {
            val al = -11f + i * 3f
            val ac = -4f + j * 2.2f
            r.flat(cx, cy, ax, ay, al, ac, 1.3f, 0.95f, z + 0.02f, Color(0xFFC0C8D8))
            r.flat(cx, cy, ax, ay, al, ac, 1.2f, 0.85f, z + 0.03f, if (look.snow) snowTopCol else Color(0xFF1A2A5A))
        }
        for (i in 0..2) {
            val px = cx + ax * (6f + i * 2.5f) + bx * 4f
            val py = cy + ay * (6f + i * 2.5f) + by * 4f
            r.box(px, py, 1f, 0f, 0.3f, 0.3f, bh, bh + 0.8f, Color(0xFFB0BEC5), Color(0xFF90A4AE), if (look.snow) snowTopCol else Color(0xFFCFD8DC))
        }
    }
}

private val greensA = listOf(Color(0xFF1D4728), Color(0xFF2D6834), Color(0xFF4C8E3E), Color(0xFF8DBE4E))
private val greensB = listOf(Color(0xFF22432C), Color(0xFF386A39), Color(0xFF5F9C47), Color(0xFFADCF5E))
private val autumnA = listOf(Color(0xFF6E2A10), Color(0xFFAE4419), Color(0xFFDF7A2A), Color(0xFFF6B648))
private val autumnB = listOf(Color(0xFF5E2E12), Color(0xFF9C5A1C), Color(0xFFD49A2A), Color(0xFFF2CF5A))
// leaf clusters: x, y, radius, layer (back to front)
private val leafBalls = arrayOf(
    floatArrayOf(-0.95f, 0.25f, 0.72f, 0f), floatArrayOf(0.95f, 0.3f, 0.68f, 0f), floatArrayOf(0f, 0.5f, 0.78f, 0f),
    floatArrayOf(-0.6f, -0.3f, 0.75f, 1f), floatArrayOf(0.62f, -0.25f, 0.72f, 1f), floatArrayOf(0f, 0f, 0.85f, 1f),
    floatArrayOf(-0.28f, -0.75f, 0.66f, 2f), floatArrayOf(0.36f, -0.68f, 0.6f, 2f),
    floatArrayOf(-0.55f, -0.15f, 0.36f, 3f), floatArrayOf(-0.1f, -0.98f, 0.34f, 3f), floatArrayOf(0.5f, -0.55f, 0.26f, 3f)
)

// A tree with layered leaves in several greens (a few in autumn colours), or a pine; snow on top in snow
private fun DrawScope.tree(sx: Float, sy: Float, k: Float, hv: Float, pine: Boolean, tall: Float) {
    drawOval(Color(0x520A0C1E), Offset(sx - k, sy - 0.35f * k), Size(2.7f * k, 0.7f * k))
    val greens = if (hv < 0.55f) greensA else greensB
    val raw = if (pine) greens else if (hv < 0.13f) autumnA else if (hv < 0.2f) autumnB else greens
    val pal = raw.map { g(it) }
    val snowC = g(Color(0xFFEEF3FA))
    if (pine) {
        drawRect(g(Color(0xFF4E342E)), Offset(sx - 0.12f * k, sy - 0.8f * k), Size(0.24f * k, 0.8f * k))
        var top = sy
        for (i in 0..3) {
            val hw = (1.35f - i * 0.28f) * k
            val yb = sy - (0.6f + i * 0.82f) * k
            val yt = yb - 1.5f * k
            top = yt
            val p = Path()
            p.moveTo(sx - hw, yb)
            for (q in 1..5) p.lineTo(sx - hw + q * hw * 2f / 6f, yb + (if (q % 2 == 1) 0.18f else 0f) * k)
            p.lineTo(sx + hw, yb)
            p.lineTo(sx, yt)
            p.close()
            drawPath(p, Brush.horizontalGradient(listOf(pal[2], pal[1], pal[0]), startX = sx - hw, endX = sx + hw))
            drawLine(pal[3], Offset(sx - 0.05f * k, yt + 0.1f * k), Offset(sx - hw * 0.7f, yb - 0.05f * k), 0.05f * k)
            if (look.snow) {
                val sn = Path()
                sn.moveTo(sx - hw * 0.85f, yb - 0.06f * k)
                sn.qTo(sx - hw * 0.85f, yb - 0.06f * k, sx, yb + 0.1f * k, sx + hw * 0.85f, yb - 0.06f * k)
                sn.lineTo(sx + hw * 0.55f, yb - 0.32f * k)
                sn.qTo(sx + hw * 0.55f, yb - 0.32f * k, sx, yb - 0.14f * k, sx - hw * 0.55f, yb - 0.32f * k)
                sn.close()
                drawPath(sn, snowC)
            }
        }
        if (look.snow) {
            val cap = Path()
            cap.moveTo(sx - 0.3f * k, top + 0.4f * k)
            cap.lineTo(sx, top)
            cap.lineTo(sx + 0.3f * k, top + 0.4f * k)
            cap.close()
            drawPath(cap, snowC)
        }
        return
    }
    val th = 1.9f * tall * k
    drawRect(
        Brush.horizontalGradient(listOf(g(Color(0xFF7B5A48)), g(Color(0xFF3E2A22))), startX = sx - 0.14f * k, endX = sx + 0.14f * k),
        Offset(sx - 0.14f * k, sy - th), Size(0.28f * k, th)
    )
    val branch = g(Color(0xFF4E342E))
    drawLine(branch, Offset(sx, sy - th * 0.75f), Offset(sx - 0.6f * k, sy - th * 1.12f), 0.11f * k, StrokeCap.Round)
    drawLine(branch, Offset(sx, sy - th * 0.85f), Offset(sx + 0.55f * k, sy - th * 1.18f), 0.11f * k, StrokeCap.Round)
    val cy0 = sy - th - 0.95f * k
    for (b in leafBalls) {
        val x = sx + b[0] * k
        val y = cy0 + b[1] * k
        val rad = b[2] * k
        val l = b[3].toInt()
        drawCircle(
            Brush.radialGradient(0f to pal[min(3, l + 1)], 1f to pal[l], center = Offset(x - rad * 0.35f, y - rad * 0.4f), radius = rad),
            rad, Offset(x, y)
        )
    }
    // speckles of light and dark leaves
    val hk = (hv * 1000f).toInt()
    for (q in 0 until 30) {
        val a = hsh(q * 7 + hk) * 6.28f
        val r2 = hsh(q * 3 + hk / 2) * 1.35f
        drawCircle(if (q % 3 != 0) pal[3] else pal[0], 0.075f * k, Offset(sx + cos(a) * r2 * k, cy0 + sin(a) * r2 * 0.8f * k - 0.1f * k))
    }
    if (look.snow) for (b in leafBalls) {
        if (b[3] < 1f || b[3] > 2f) continue
        val rad = b[2] * k
        drawArc(snowC, 180f, 180f, true, Offset(sx + b[0] * k - rad * 0.8f, cy0 + b[1] * k - rad * 0.86f), Size(rad * 1.6f, rad * 0.72f))
    }
}

// Light over the whole picture: our headlights and light bar in the cab, fog, rain, snow
private fun DrawScope.weather(r: Renderer, cam: DriveCam, t: Float, drone: Boolean) {
    val w = size.width
    val h = size.height
    val flash = floor(t * 4f).toInt() % 2 == 0
    if (!drone) {
        // our headlights on the road ahead
        val top = r.y0 + 14f
        val bot = h * 0.7f
        val beam = Path()
        beam.moveTo(w * 0.02f, bot)
        beam.lineTo(w * 0.98f, bot)
        beam.lineTo(w * 0.6f, top)
        beam.lineTo(w * 0.4f, top)
        beam.close()
        drawPath(
            beam,
            Brush.verticalGradient(listOf(Color(0xFFFFECBE).copy(alpha = look.beam), Color(0x00FFECBE)), startY = bot, endY = top),
            blendMode = BlendMode.Plus
        )
        // red and blue from the light bar washing over the street
        val sx = if (flash) w * 0.15f else w * 0.85f
        val sc = if (flash) Color(0xFFFF1E46) else Color(0xFF286EFF)
        drawRect(
            Brush.radialGradient(listOf(sc.copy(alpha = look.siren), Color.Transparent), center = Offset(sx, h * 0.1f), radius = w),
            Offset.Zero, size, blendMode = BlendMode.Plus
        )
    }
    val fog = look.fog
    if (fog != null) {
        val hy = r.horizon
        drawRect(
            Brush.verticalGradient(0f to fog.copy(alpha = 0f), 0.35f to fog.copy(alpha = 0.55f), 1f to fog.copy(alpha = 0f), startY = hy - 60f, endY = hy + 140f),
            Offset(0f, hy - 60f), Size(w, 200f)
        )
    }
    if (look.rain) {
        val streak = Color(0x47BED2F0)
        for (i in 0 until 320) {
            val ln = (10f + 18f * hsh(i * 3)) * w / 390f
            val x = (hsh(i * 17 + 5) * w * 1.1f - t * 60f * (w / 390f)).mod(w * 1.1f)
            val y = (hsh(i * 29 + 3) * h + t * 900f * (h / 844f)).mod(h)
            drawLine(streak, Offset(x, y), Offset(x - ln * 0.22f, y + ln), 1f)
        }
        // drops on the windshield, cleared where the wipers have just been
        if (!drone) for (i in 0 until 120) {
            val x = hsh(i * 41 + 9) * w
            val y = h * 0.07f + hsh(i * 53 + 1) * h * 0.6f
            val rd = 1.2f + 2.6f * hsh(i * 7 + 2)
            val dx = x - w * 0.5f
            val dy = y - h * 0.735f
            if (hypot(dx, dy) < h * 0.24f && dy < 0f && abs(dx) < w * 0.3f) continue
            drawOval(Color(0x29C8DCFF), Offset(x - rd, y - rd * 1.2f), Size(rd * 2f, rd * 2.4f))
            drawCircle(Color(0x73FFFFFF), rd * 0.3f, Offset(x - rd * 0.3f, y - rd * 0.4f))
        }
    }
    if (look.snow) {
        for (i in 0 until 280) {
            val rd = (0.6f + 2.6f * hsh(i * 3) * hsh(i * 3)) * w / 390f
            // bigger flakes are nearer and fall faster
            val x = (hsh(i * 17 + 5) * w + sin(t * 1.3f + i) * 8f * rd).mod(w)
            val y = (hsh(i * 29 + 3) * h + t * (30f + 25f * rd)).mod(h)
            drawCircle(Color(0xFFF0F5FF).copy(alpha = 0.35f + 0.5f * hsh(i * 7)), rd, Offset(x, y))
        }
    }
}

private fun DrawScope.drawCab(t: Float, plan: DrivePlan, d: Float, lab: Labels) {
    val w = size.width
    val h = size.height
    val frame = gc(Color(0xFF1D2027))
    val flash = floor(t * 4f).toInt() % 2 == 0
    val red = gc(Color(0xFFD01010))
    drawRect(frame, size = Size(w, h * 0.055f))
    for (i in 0..7) {
        val on = (i + (if (flash) 0 else 1)) % 2 == 0
        drawRect(if (on) gc(Color(0xD9FF1744)) else gc(Color(0xD92979FF)), Offset(i * w / 8f, h * 0.055f), Size(w / 8f + 1f, h * 0.01f))
    }
    // sun visors
    drawRoundRect(gc(Color(0xFF3A414E)), Offset(w * 0.05f, h * 0.062f), Size(w * 0.36f, h * 0.03f), CornerRadius(8f, 8f))
    drawRoundRect(gc(Color(0xFF3A414E)), Offset(w * 0.59f, h * 0.062f), Size(w * 0.36f, h * 0.03f), CornerRadius(8f, 8f))
    val pl = Path()
    pl.moveTo(0f, 0f); pl.lineTo(w * 0.09f, 0f); pl.lineTo(w * 0.02f, h * 0.62f); pl.lineTo(0f, h * 0.62f); pl.close()
    drawPath(pl, frame)
    val pr = Path()
    pr.moveTo(w, 0f); pr.lineTo(w * 0.91f, 0f); pr.lineTo(w * 0.98f, h * 0.62f); pr.lineTo(w, h * 0.62f); pr.close()
    drawPath(pr, frame)
    // side mirrors
    for (mx in floatArrayOf(w * 0.06f, w * 0.94f)) {
        drawRoundRect(red, Offset(mx - w * 0.07f, h * 0.5f), Size(w * 0.14f, h * 0.085f), CornerRadius(10f, 10f))
        drawRoundRect(
            Brush.verticalGradient(listOf(gc(Color(0xFFB3E5FC)), gc(Color(0xFF8A8FB0))), startY = h * 0.505f, endY = h * 0.58f),
            Offset(mx - w * 0.06f, h * 0.507f), Size(w * 0.12f, h * 0.07f), cornerRadius = CornerRadius(8f, 8f)
        )
        drawRect(Color(0x59FFFFFF), Offset(mx - w * 0.05f, h * 0.512f), Size(w * 0.03f, h * 0.06f))
    }
    // red hood with a shine and a white stripe
    val hood = Path()
    hood.moveTo(0f, h * 0.70f)
    hood.qTo(0f, h * 0.70f, w / 2f, h * 0.655f, w, h * 0.70f)
    hood.lineTo(w, h)
    hood.lineTo(0f, h)
    hood.close()
    drawPath(hood, red)
    val shine = Path()
    shine.moveTo(w * 0.1f, h * 0.69f)
    shine.qTo(w * 0.1f, h * 0.69f, w / 2f, h * 0.66f, w * 0.9f, h * 0.69f)
    shine.lineTo(w * 0.9f, h * 0.695f)
    shine.qTo(w * 0.9f, h * 0.695f, w / 2f, h * 0.668f, w * 0.1f, h * 0.695f)
    shine.close()
    drawPath(shine, Color(0x40FFFFFF))
    drawOval(Color(0x1FFFFFFF), Offset(w * 0.08f, h * 0.74f), Size(w * 0.44f, h * 0.04f))
    val stripe = Path()
    stripe.moveTo(0f, h * 0.716f)
    stripe.qTo(0f, h * 0.716f, w / 2f, h * 0.674f, w, h * 0.716f)
    stripe.lineTo(w, h * 0.724f)
    stripe.qTo(w, h * 0.724f, w / 2f, h * 0.682f, 0f, h * 0.724f)
    stripe.close()
    drawPath(stripe, gc(Color.White))
    // wipers: at rest, or sweeping in the rain
    val wip = gc(Color(0xFF111111))
    if (look.rain) {
        val sw0 = (0.5f - 0.5f * cos(t * 3.2f)) * 2.2f
        for (pv in floatArrayOf(0.2f, 0.6f)) {
            val p0 = Offset(w * pv, h * 0.735f)
            val ln = w * 0.32f
            val an = 0.06f + sw0
            drawLine(wip, p0, Offset(p0.x + cos(an) * ln, p0.y - sin(an) * ln), 4f, StrokeCap.Round)
        }
    } else {
        drawLine(wip, Offset(w * 0.18f, h * 0.735f), Offset(w * 0.5f, h * 0.715f), 4f, StrokeCap.Round)
        drawLine(wip, Offset(w * 0.58f, h * 0.735f), Offset(w * 0.88f, h * 0.716f), 4f, StrokeCap.Round)
    }
    // dashboard
    val dash = Path()
    dash.moveTo(0f, h * 0.75f)
    dash.qTo(0f, h * 0.75f, w / 2f, h * 0.72f, w, h * 0.75f)
    dash.lineTo(w, h)
    dash.lineTo(0f, h)
    dash.close()
    drawPath(dash, gc(Color(0xFF2B313A)))
    val lip = Path()
    lip.moveTo(0f, h * 0.75f)
    lip.qTo(0f, h * 0.75f, w / 2f, h * 0.72f, w, h * 0.75f)
    lip.lineTo(w, h * 0.758f)
    lip.qTo(w, h * 0.758f, w / 2f, h * 0.728f, 0f, h * 0.758f)
    lip.close()
    drawPath(lip, gc(Color(0xFF232830)))
    // speed and engine gauges
    drawRoundRect(gc(Color(0xFF1F242B)), Offset(w * 0.2f, h * 0.765f), Size(w * 0.36f, h * 0.075f), CornerRadius(18f, 18f))
    val gy = h * 0.803f
    val gr = w * 0.055f
    for (gi in 0..1) {
        val gx = if (gi == 0) w * 0.29f else w * 0.47f
        val v = if (gi == 0) 0.55f + 0.25f * sin(t) else 0.45f + 0.2f * sin(t * 1.3f)
        drawCircle(gc(Color(0xFF3A414E)), gr, Offset(gx, gy))
        for (q in 0..8) {
            val a = (PI * 0.75 + q / 8.0 * PI * 1.5).toFloat()
            drawLine(gc(Color(0xFFECEFF1)), Offset(gx + cos(a) * gr * 0.82f, gy + sin(a) * gr * 0.82f), Offset(gx + cos(a) * gr * 0.95f, gy + sin(a) * gr * 0.95f), 1.5f)
        }
        val a = (PI * 0.75 + v * PI * 1.5).toFloat()
        drawLine(gc(Color(0xFFFF6A3D)), Offset(gx, gy), Offset(gx + cos(a) * gr * 0.73f, gy + sin(a) * gr * 0.73f), 3f, StrokeCap.Round)
        drawCircle(gc(Color.White), 3f, Offset(gx, gy))
        val lt = if (gi == 0) lab.mph else lab.rpm
        drawText(lt, topLeft = Offset(gx - lt.size.width / 2f, gy + gr * 0.58f - lt.size.height / 2f))
    }
    // siren control panel: lights and siren on, flashing with the light bar
    drawRoundRect(gc(Color(0xFF1F242B)), Offset(w * 0.62f, h * 0.77f), Size(w * 0.33f, h * 0.1f), CornerRadius(12f, 12f))
    for (i in 0..3) {
        val bx = w * 0.64f + (i % 2) * w * 0.15f
        val by = h * 0.778f + (i / 2) * h * 0.045f
        val col = when (i) {
            0 -> if (flash) gc(Color(0xFFFF1744)) else gc(Color(0xFFB71C1C))
            1 -> if (flash) gc(Color(0xFF2979FF)) else gc(Color(0xFF0D47A1))
            else -> gc(Color(0xFF3A414E))
        }
        drawRoundRect(col, Offset(bx, by), Size(w * 0.135f, h * 0.037f), CornerRadius(6f, 6f))
        val lt = lab.buttons[i]
        drawText(lt, topLeft = Offset(bx + w * 0.0675f - lt.size.width / 2f, by + h * 0.0185f - lt.size.height / 2f))
    }
    // GPS screen showing the route and where the ambulance is on it
    drawRoundRect(gc(Color(0xFF1F242B)), Offset(w * 0.62f, h * 0.88f), Size(w * 0.33f, h * 0.09f), CornerRadius(10f, 10f))
    val sl = w * 0.635f
    val st = h * 0.888f
    val sw = w * 0.3f
    val sh = h * 0.074f
    drawRect(gc(Color(0xFF263238)), Offset(sl, st), Size(sw, sh))
    var minX = Float.MAX_VALUE
    var minY = Float.MAX_VALUE
    var maxX = -Float.MAX_VALUE
    var maxY = -Float.MAX_VALUE
    for (p in plan.pts) {
        minX = min(minX, p[0]); maxX = max(maxX, p[0])
        minY = min(minY, p[1]); maxY = max(maxY, p[1])
    }
    val gs = min((sw - 16f) / max(1f, maxX - minX), (sh - 12f) / max(1f, maxY - minY))
    val ox = sl + sw / 2f - (minX + maxX) / 2f * gs
    val oy = st + sh / 2f - (minY + maxY) / 2f * gs
    fun gp(x: Float, y: Float) = Offset(ox + x * gs, oy + y * gs)
    for (i in 0 until plan.pts.size - 1) {
        drawLine(gc(Color(0xFF7D82A8)), gp(plan.pts[i][0], plan.pts[i][1]), gp(plan.pts[i + 1][0], plan.pts[i + 1][1]), 3f, StrokeCap.Round)
    }
    val cam = plan.camAt(d)
    for (i in 0..cam.seg) {
        val b = if (i < cam.seg) plan.pts[i + 1] else null
        val end = if (b != null) gp(b[0], b[1]) else {
            val a0 = plan.pts[i]
            val a1 = plan.pts[i + 1]
            val segLen = plan.dist[i + 1] - plan.dist[i]
            val u = if (segLen > 0f) ((d - plan.dist[i]) / segLen).coerceIn(0f, 1f) else 0f
            gp(a0[0] + (a1[0] - a0[0]) * u, a0[1] + (a1[1] - a0[1]) * u)
        }
        drawLine(gc(Color(0xFF3FA2FF)), gp(plan.pts[i][0], plan.pts[i][1]), end, 3f, StrokeCap.Round)
        if (i == cam.seg) drawCircle(gc(Color(0xFFFF6A3D)), 4f, end)
    }
    val last = plan.pts.last()
    drawCircle(gc(Color(0xFF3FA2FF)), 4f, gp(last[0], last[1]))
    // steering wheel with spokes and a red badge
    val c = Offset(w * 0.38f, h * 1.03f)
    val rad = w * 0.36f
    drawArc(gc(Color(0xFF444444)), 215f, 110f, false, Offset(c.x - rad - w * 0.022f, c.y - rad - w * 0.022f), Size((rad + w * 0.022f) * 2f, (rad + w * 0.022f) * 2f), style = Stroke(3f))
    drawArc(gc(Color(0xFF111111)), 202f, 136f, false, Offset(c.x - rad, c.y - rad), Size(rad * 2f, rad * 2f), style = Stroke(w * 0.056f, cap = StrokeCap.Round))
    drawLine(gc(Color(0xFF1A1A1A)), Offset(c.x - rad * 0.9f, c.y - rad * 0.32f), Offset(c.x - rad * 0.25f, c.y - rad * 0.18f), w * 0.035f, StrokeCap.Round)
    drawLine(gc(Color(0xFF1A1A1A)), Offset(c.x + rad * 0.9f, c.y - rad * 0.32f), Offset(c.x + rad * 0.25f, c.y - rad * 0.18f), w * 0.035f, StrokeCap.Round)
    drawOval(gc(Color(0xFF1A1A1A)), Offset(c.x - rad * 0.3f, c.y - rad * 0.32f), Size(rad * 0.6f, rad * 0.4f))
    drawCircle(red, rad * 0.06f, Offset(c.x, c.y - rad * 0.17f))
    // the light bar and the screens light up the cab
    glow(if (flash) w * 0.2f else w * 0.8f, 0f, w * 0.5f, if (flash) Color(0xFFFF1744) else Color(0xFF2979FF), 0.22f)
    glow(w * 0.785f, h * 0.925f, w * 0.2f, Color(0xFF3FA2FF), 0.12f)
    glow(w * 0.38f, h * 0.8f, w * 0.2f, Color(0xFFFF6A3D), 0.06f)
}

private val HudGlass = Color(0xD90E1428)
private val HudEdge = Color(0x33A0B4FF)

@Composable
fun DriveScene(
    city: CityData,
    nodes: List<Int>,
    start: Offset,
    end: Offset,
    toHospital: Boolean,
    destKey: Int,
    crash: Boolean,
    sirenOn: Boolean,
    chat: List<ChatLine> = emptyList(),
    patient: Patient? = null,
    worse: Boolean = false,
    back: (@Composable (onClose: () -> Unit) -> Unit)? = null,
    drone: Boolean = true,
    onView: (Boolean) -> Unit = {},
    onDone: () -> Unit
) {
    look = lookFor(city)
    val plan = remember(city, nodes, start, end) { DrivePlan(city, nodes, start, end) }
    val crashAt = remember(plan, crash) {
        if (crash) floatArrayOf(DrivePlan.wx(end.x) + 4f, DrivePlan.wy(end.y) + 4f) else null
    }
    var t by remember { mutableFloatStateOf(0f) }
    val traffic = remember(plan) { Traffic(city.n) }
    val tm = rememberTextMeasurer()
    val labels = remember(tm) {
        val small = TextStyle(color = Color(0xFF90A4AE), fontSize = 8.sp, fontWeight = FontWeight.Bold)
        val btn = TextStyle(color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        Labels(
            tm.measure("STOP", TextStyle(color = Color.White, fontSize = 40.sp, fontWeight = FontWeight.Bold)),
            tm.measure("MPH", small),
            tm.measure("RPM", small),
            listOf("LIGHTS", "SIREN", "HORN", "PA").map { tm.measure(it, btn) }
        )
    }
    val done by rememberUpdatedState(onDone)
    val finished = remember { booleanArrayOf(false) }
    fun finish() {
        if (!finished[0]) {
            finished[0] = true
            done()
        }
    }
    BackHandler { finish() }
    DisposableEffect(sirenOn) {
        val siren = Siren()
        if (sirenOn) siren.start()
        onDispose { siren.stop() }
    }
    // On the way to the hospital: chat lines pop up from the back. When the patient gets worse,
    // "I don't feel good..." shows halfway, then the drive pauses while you treat in the back.
    val hasBack = worse && back != null && patient != null
    val chatEnd = if (hasBack) 0.4f else 0.85f
    val warnAt = plan.total * 0.5f
    val pauseAt = minOf(warnAt + 1.8f, plan.total + 0.5f)
    var paused by remember { mutableStateOf(false) }
    var backOpen by remember { mutableStateOf(false) }
    var backDone by remember { mutableStateOf(false) }
    LaunchedEffect(plan) {
        var first = -1L
        var last = 0L
        while (true) {
            withFrameNanos { now ->
                if (first < 0L) first = now
                if (paused) first += now - last
                last = now
                t = (now - first) / 1_000_000_000f
            }
            if (hasBack && !backDone && !backOpen && t >= pauseAt) {
                paused = true
                backOpen = true
            }
            if (t > plan.total + 1f) {
                finish()
                break
            }
        }
    }
    // These only change when the block number changes or the drive ends, so the screen
    // around the drawing isn't rebuilt every frame. Only the drawing reads t each frame.
    val blocks = plan.pts.size - 1
    val blockNo by remember(plan) { derivedStateOf { min(plan.camAt(plan.distAt(t)).seg + 1, blocks) } }
    val arrived by remember(plan) { derivedStateOf { t > plan.total } }
    val chatShown by remember(plan) {
        derivedStateOf {
            val f = (t / plan.total - 0.05f) / (chatEnd - 0.05f)
            if (chat.isEmpty() || f < 0f) 0 else (1 + (f * chat.size).toInt()).coerceAtMost(chat.size)
        }
    }
    val warn by remember(plan) { derivedStateOf { hasBack && !backDone && t >= warnAt } }
    val flashOn by remember(plan) { derivedStateOf { floor(t * 4f).toInt() % 2 == 0 } }
    Box(Modifier.fillMaxSize().background(Color(0xFF1D2027))) {
        Canvas(Modifier.fillMaxSize()) {
            look = lookFor(city)
            drawWorld(city, plan, t, destKey, crashAt, traffic, labels, drone)
            if (!drone) drawCab(t, plan, plan.distAt(t), labels)
        }
        // dark glass banner: where we are going, how far along, and the weather
        Column(
            modifier = Modifier
                .align(Alignment.TopStart)
                .safeDrawingPadding()
                .padding(start = 8.dp, top = 56.dp, end = 120.dp)
                .background(HudGlass, RoundedCornerShape(18.dp))
                .border(1.dp, HudEdge, RoundedCornerShape(18.dp))
                .padding(horizontal = 14.dp, vertical = 8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(9.dp)
                        .background(if (flashOn) Color(0xFFFF1744) else Color(0xFF2979FF), CircleShape)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    if (toHospital) "Driving to the hospital" else "Driving to the call",
                    color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold
                )
            }
            val where = if (!toHospital && patient != null) patient.address + " · " else ""
            Text(where + "Block " + blockNo + " of " + blocks, color = Color(0xFFB8C2E0), fontSize = 13.sp)
            if (look.rain || look.snow) {
                Spacer(Modifier.height(5.dp))
                Text(
                    if (look.snow) "❄ Snow · roads icy" else "☂ Storm · roads slick",
                    color = Color(0xFFDCE6FF), fontSize = 12.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .background(Color(0x334F7BD9), RoundedCornerShape(10.dp))
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                )
            }
        }
        if (patient != null && (chatShown > 0 || warn) && !arrived) {
            ChatBubbles(
                chat.take(chatShown), patient, warn,
                Modifier
                    .align(Alignment.TopCenter)
                    .safeDrawingPadding()
                    .padding(start = 10.dp, end = 10.dp, top = 118.dp)
            )
        }
        if (!backOpen) Column(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .safeDrawingPadding()
                .padding(end = 8.dp, top = 60.dp),
            horizontalAlignment = Alignment.End
        ) {
            Box(
                modifier = Modifier
                    .background(HudGlass, RoundedCornerShape(22.dp))
                    .border(1.dp, HudEdge, RoundedCornerShape(22.dp))
                    .clickable { finish() }
                    .padding(horizontal = 18.dp, vertical = 10.dp)
            ) {
                Text("Skip ▸", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(8.dp))
            // Cab or drone view; the choice is saved
            Row(
                Modifier
                    .background(HudGlass, RoundedCornerShape(16.dp))
                    .border(1.dp, HudEdge, RoundedCornerShape(16.dp))
                    .padding(3.dp)
            ) {
                for (v in 0..1) {
                    val on = (v == 1) == drone
                    Text(
                        if (v == 0) "Cab" else "Drone",
                        color = if (on) Color.White else Color(0xFF9AA6C8), fontSize = 13.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .background(if (on) Color(0xFF3B5BDB) else Color.Transparent, RoundedCornerShape(13.dp))
                            .clickable { onView(v == 1) }
                            .padding(horizontal = 12.dp, vertical = 5.dp)
                    )
                }
            }
        }
        if (arrived) {
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .background(if (crash) Color(0xF2E53935) else Color(0xF24DC43B), RoundedCornerShape(24.dp))
                    .padding(horizontal = 28.dp, vertical = 14.dp)
            ) {
                Text(
                    if (crash) "Accident!" else "Arrived",
                    color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold
                )
            }
        }
        if (backOpen && back != null) {
            back {
                backOpen = false
                backDone = true
                paused = false
            }
        }
    }
}
