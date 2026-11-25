package com.cpd360.ambulancerun

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
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
    val shoe = Color(0xFF263238)
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
    if (o.bag) drawRoundRect(Color(0xFF6D4C41), Offset(px - 0.42f * k, py - 1.1f * k), Size(0.2f * k, 0.32f * k), CornerRadius(0.05f * k, 0.05f * k))
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
    // eyes and cheeks when close enough to see
    if (k > faceK) {
        drawCircle(Color(0xFF212121), 0.03f * k, Offset(px - 0.08f * k, hy2 + 0.02f * k))
        drawCircle(Color(0xFF212121), 0.03f * k, Offset(px + 0.08f * k, hy2 + 0.02f * k))
        drawCircle(Color(0x59FF7878), 0.04f * k, Offset(px - 0.15f * k, hy2 + 0.09f * k))
        drawCircle(Color(0x59FF7878), 0.04f * k, Offset(px + 0.15f * k, hy2 + 0.09f * k))
    }
    if (o.dog) {
        val dx = px + 1.0f * k
        val fur = Color(0xFF6D4C41)
        limb(hx + 0.05f * k, hy + 0.02f * k, dx, py - 0.42f * k, 0.03f * k, Color(0xFFE53935), null)
        drawRoundRect(
            Brush.verticalGradient(listOf(Color(0xFFA1887F), fur), startY = py - 0.55f * k, endY = py),
            Offset(dx - 0.05f * k, py - 0.5f * k), Size(0.55f * k, 0.28f * k), CornerRadius(0.13f * k, 0.13f * k)
        )
        limb(dx + 0.05f * k, py - 0.25f * k, dx + 0.05f * k, py - 0.04f * k, 0.08f * k, fur, null)
        limb(dx + 0.42f * k, py - 0.25f * k, dx + 0.42f * k, py - 0.04f * k, 0.08f * k, fur, null)
        drawCircle(Color(0xFF8D6E63), 0.15f * k, Offset(dx + 0.55f * k, py - 0.55f * k))
        drawOval(Color(0xFF5D4037), Offset(dx + 0.42f * k, py - 0.7f * k), Size(0.1f * k, 0.2f * k))
        drawCircle(Color(0xFF212121), 0.03f * k, Offset(dx + 0.68f * k, py - 0.55f * k))
    }
}
private val glassBlue = Color(0xFF5C9BD6)

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

// Draws the world from the driver seat
private class Renderer(val ds: DrawScope, val cam: DriveCam) {
    val w = ds.size.width
    val h = ds.size.height
    val f = w * 0.85f
    val y0 = h * 0.42f

    private fun tc(x: Float, y: Float, z: Float): FloatArray {
        val dx = x - cam.px
        val dy = y - cam.py
        return floatArrayOf(dx * cam.rx + dy * cam.ry, z - CAM_H, dx * cam.hx + dy * cam.hy)
    }

    private fun pr(p: FloatArray): Offset = Offset(w / 2f + p[0] * f / p[2], y0 - p[1] * f / p[2])

    fun poly(ws: List<FloatArray>, col: Color) {
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
        ds.drawPath(path, col)
    }

    fun ground(x0: Float, y0g: Float, x1: Float, y1: Float, col: Color) {
        poly(
            listOf(
                floatArrayOf(x0, y0g, 0f), floatArrayOf(x1, y0g, 0f),
                floatArrayOf(x1, y1, 0f), floatArrayOf(x0, y1, 0f)
            ), col
        )
    }

    // A flat ring on the ground, like a manhole cover
    fun disc(x: Float, y: Float, rad: Float, col: Color) {
        val pts = ArrayList<FloatArray>()
        for (k in 0 until 10) {
            val a = k / 10f * 2f * PI.toFloat()
            pts.add(floatArrayOf(x + rad * cos(a), y + rad * sin(a), 0.01f))
        }
        poly(pts, col)
    }

    // A panel on a wall from corner a to corner b: u0..u1 along the wall, z0..z1 up,
    // pushed out from the wall by out
    fun quad(a: FloatArray, b: FloatArray, u0: Float, u1: Float, z0: Float, z1: Float, col: Color, out: Float = 0.03f) {
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

    // A box standing on the ground, ax/ay is its long direction.
    // deco draws details on each wall the driver can see.
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
        if (top != null && z1 < CAM_H) poly(c.map { floatArrayOf(it[0], it[1], z1) }, top)
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
                    ), shadeCol(col, nx, ny)
                )
            }
        }
        if (top != null && z1 < CAM_H) poly(pt.map { floatArrayOf(it[0], it[1], z1) }, top)
    }

    // A round light with a shine, at a point in the world
    fun lamp(x: Float, y: Float, z: Float, col: Color, rad: Float) {
        val p = tc(x, y, z)
        if (p[2] < NEAR) return
        val s = pr(p)
        val k = f / p[2]
        ds.drawCircle(col, rad * k, s)
        ds.drawCircle(Color(0xB3FFFFFF), rad * k * 0.35f, Offset(s.x - rad * k * 0.3f, s.y - rad * k * 0.3f))
    }

    // A tyre with a hubcap, squashed by how much of its side the driver sees
    fun wheel(x: Float, y: Float, bx: Float, by: Float) {
        bill(x, y) { sx, sy, k ->
            val vx = cam.px - x
            val vy = cam.py - y
            val vl = max(0.01f, hypot(vx, vy))
            val fct = max(0.28f, abs((vx * bx + vy * by) / vl))
            val rw = 0.36f * k
            drawOval(Color(0xFF1B1B1B), Offset(sx - rw * fct, sy - 2f * rw), Size(2f * rw * fct, 2f * rw))
            drawOval(Color(0xFFB0BEC5), Offset(sx - rw * 0.5f * fct, sy - 1.5f * rw), Size(rw * fct, rw))
            drawOval(Color(0xFFECEFF1), Offset(sx - rw * 0.28f * fct, sy - 1.28f * rw), Size(rw * 0.36f * fct, rw * 0.36f))
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
        }
        for (wv in wl) if ((wv[1] > 0f) == camLeft) wheel(cx + ax * wv[0] + bx * wv[1] * 0.93f, cy + ay * wv[0] + by * wv[1] * 0.93f, bx, by)
    }

    // Something flat that always faces the driver: gives screen x, y of its base and pixels per world unit
    fun bill(x: Float, y: Float, fn: DrawScope.(Float, Float, Float) -> Unit) {
        val p = tc(x, y, 0f)
        if (p[2] < NEAR) return
        val s = pr(p)
        ds.fn(s.x, s.y, f / p[2])
    }
}

// Which way a wall faces compared with a direction: > 0 means the wall faces that way
private fun faces(a: FloatArray, b: FloatArray, cx: Float, cy: Float, dx: Float, dy: Float): Float =
    ((a[0] + b[0]) / 2f - cx) * dx + ((a[1] + b[1]) / 2f - cy) * dy

private fun DrawScope.drawWorld(city: CityData, plan: DrivePlan, t: Float, destKey: Int, crashAt: FloatArray?, tr: Traffic, lab: Labels) {
    val d = plan.distAt(t)
    val cam = plan.camAt(d)
    val r = Renderer(this, cam)
    val w = size.width
    val h = size.height
    val n = city.n
    val sc = w / 390f
    // sky, clouds and a far-off skyline that turn with the ambulance
    drawRect(Brush.verticalGradient(listOf(Color(0xFF6EC6FF), Color(0xFFD6F2FF)), endY = r.y0), size = Size(w, r.y0 + 1f))
    val head = atan2(cam.hy, cam.hx)
    for (i in 0 until 8) {
        val da = wrapAng(i / 8f * 2f * PI.toFloat() - head)
        if (abs(da) > 1f) continue
        val cx = w / 2f + tan(da) * r.f
        val cy = r.y0 * (0.3f + 0.25f * hsh(i))
        val s = (40f + 30f * hsh(i + 9)) * sc
        val puffs = arrayOf(floatArrayOf(0f, 0f, 1f), floatArrayOf(-0.8f, 0.25f, 0.7f), floatArrayOf(0.85f, 0.2f, 0.75f), floatArrayOf(0.35f, -0.35f, 0.7f))
        for (q in puffs) {
            drawOval(
                Color(0xEBFFFFFF),
                Offset(cx + q[0] * s - s * q[2], cy + q[1] * s - s * q[2] * 0.62f),
                Size(2f * s * q[2], 2f * s * q[2] * 0.62f)
            )
        }
    }
    for (i in 0 until 60) {
        val da = wrapAng(i / 60f * 2f * PI.toFloat() - head)
        if (abs(da) > 1.1f) continue
        val sx = w / 2f + tan(da) * r.f
        val bw = (18f + 20f * hsh(i * 3)) * sc
        val bh = (12f + 40f * hsh(i * 5)) * sc
        drawRect(Color(0xFFA9C7E0), Offset(sx - bw / 2f, r.y0 - bh), Size(bw, bh + 1f))
    }
    drawRect(Color(0xFF9BD468), Offset(0f, r.y0), Size(w, h - r.y0))
    r.ground(-9f, -9f, n * BL + 9f, n * BL + 9f, Color(0xFF7D82A8))
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
        val gc = lerp(zoneGround[city.zone[row * n + col]], Color(0xFF2B2B2B), 0.08f * city.dens[row * n + col])
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
                                if (faces(a, b, hx, hy, rfx, rfy) > 0.5f) {
                                    r.quad(a, b, 0.12f, 0.26f, 0f, 2.1f, Color(0xFF8D5524), 0.02f)
                                    r.quad(a, b, 0.16f, 0.22f, 0.9f, 1.1f, Color(0xFFFFD54F))
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
                                if (sg == camSide) roofC else roofD
                            )
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
                    }
                    // white picket fence along the sidewalk, with a gap for the gate
                    if (dd < 90f) {
                        val fx = cx + rfx * 6.8f
                        val fy = cy + rfy * 6.8f
                        var q = -14f
                        while (q <= 14f) {
                            if (abs(q) >= 1.2f) {
                                r.bill(fx + ax * q, fy + ay * q) { px, py, k ->
                                    drawRect(Color.White, Offset(px - 0.12f * k, py - 0.9f * k), Size(0.24f * k, 0.9f * k))
                                    drawRect(Color.White, Offset(px - 0.55f * k, py - 0.65f * k), Size(1.1f * k, 0.1f * k))
                                }
                            }
                            q += 1.1f
                        }
                    }
                } else {
                    r.box(cx, cy, ax, ay, 15f, 7f, 0f, bh, front, side, null) { a, b ->
                        if (detail) {
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
                        r.box(cx + ax * 6f, cy + ay * 6f, ax, ay, 1.6f, 1.2f, bh, bh + 1.4f, Color(0xFFB0BEC5), Color(0xFF90A4AE), null)
                        r.box(cx - ax * 5f, cy - ay * 5f, ax, ay, 1.1f, 1.1f, bh, bh + 1f, Color(0xFFCFD8DC), Color(0xFF90A4AE), null)
                        if (kind == 1) r.box(cx - ax * 9f, cy - ay * 9f, ax, ay, 0.15f, 0.15f, bh, bh + 5f, Color(0xFF9E9E9E), Color(0xFF757575), null)
                    }
                }
                if (isHosp || isDest) {
                    // sign on the roof facing the road
                    val sx = cx - exs[e] * 7.5f
                    val sy = cy - eys[e] * 7.5f
                    r.bill(sx, sy) { px, py, k ->
                        if (isHosp) {
                            drawRect(Color.White, Offset(px - 1.4f * k, py - (topH + 2.8f) * k), Size(2.8f * k, 2.8f * k))
                            drawRect(Color(0xFFE53935), Offset(px - 0.35f * k, py - (topH + 2.5f) * k), Size(0.7f * k, 2.2f * k))
                            drawRect(Color(0xFFE53935), Offset(px - 1.1f * k, py - (topH + 1.75f) * k), Size(2.2f * k, 0.7f * k))
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
                            drawRect(Color(0xFF546E7A), Offset(sx - 0.07f * k, sy - 4.2f * k), Size(0.14f * k, 4.2f * k))
                            drawRect(Color(0xFF546E7A), Offset(sx - 0.07f * k, sy - 4.2f * k), Size(0.6f * k, 0.1f * k))
                            drawOval(Color(0xFFFFF59D), Offset(sx + 0.33f * k, sy - 4.17f * k), Size(0.44f * k, 0.24f * k))
                        }
                    })
                    z == 1 && hv < 0.5f -> objs.add(Obj(dd) {
                        r.bill(px0, py0) { sx, sy, k ->
                            drawRect(Color(0xFF6D4C41), Offset(sx - 0.7f * k, sy - 0.5f * k), Size(1.4f * k, 0.12f * k))
                            drawRect(Color(0xFF6D4C41), Offset(sx - 0.7f * k, sy - 0.9f * k), Size(1.4f * k, 0.1f * k))
                            drawRect(Color(0xFF37474F), Offset(sx - 0.6f * k, sy - 0.5f * k), Size(0.08f * k, 0.5f * k))
                            drawRect(Color(0xFF37474F), Offset(sx + 0.52f * k, sy - 0.5f * k), Size(0.08f * k, 0.5f * k))
                        }
                    })
                    z == 2 && hv < 0.6f -> objs.add(Obj(dd) {
                        r.bill(px0, py0) { sx, sy, k ->
                            drawRect(Color(0xFFE53935), Offset(sx - 0.16f * k, sy - 0.7f * k), Size(0.32f * k, 0.7f * k))
                            drawCircle(Color(0xFFE53935), 0.16f * k, Offset(sx, sy - 0.7f * k))
                            drawRect(Color(0xFFE53935), Offset(sx - 0.26f * k, sy - 0.5f * k), Size(0.52f * k, 0.12f * k))
                        }
                    })
                    else -> {
                        val pine = z == 0 && hv > 0.7f
                        val tall = if (z == 0) 1f else 0.8f
                        val tc = treeCols[(hv * 3f).toInt().coerceIn(0, 2)]
                        objs.add(Obj(dd) {
                            r.bill(px0, py0) { sx, sy, k ->
                                drawOval(Color(0x2E283C5A), Offset(sx - 0.8f * k, sy - 0.3f * k), Size(2.2f * k, 0.6f * k))
                                if (pine) {
                                    drawRect(Color(0xFF795548), Offset(sx - 0.12f * k, sy - 0.8f * k), Size(0.24f * k, 0.8f * k))
                                    val tiers = listOf(Color(0xFF2E7D32), Color(0xFF388E3C), Color(0xFF43A047))
                                    for (i in 0..2) {
                                        val hw = (1.2f - i * 0.3f) * k
                                        val base = sy - (0.7f + i * 1.0f) * k
                                        val tri = Path()
                                        tri.moveTo(sx - hw, base)
                                        tri.lineTo(sx + hw, base)
                                        tri.lineTo(sx, base - 1.7f * k)
                                        tri.close()
                                        drawPath(tri, tiers[i])
                                    }
                                } else {
                                    drawRect(Color(0xFF795548), Offset(sx - 0.12f * k, sy - 1.8f * tall * k), Size(0.24f * k, 1.8f * tall * k))
                                    val dark = lerp(tc, Color.Black, 0.2f)
                                    drawCircle(dark, 0.9f * k, Offset(sx - 0.55f * k, sy - 2.5f * tall * k))
                                    drawCircle(dark, 0.85f * k, Offset(sx + 0.6f * k, sy - 2.6f * tall * k))
                                    drawCircle(tc, 1.1f * k, Offset(sx, sy - 3.1f * tall * k))
                                    drawCircle(Color(0x2EFFFFFF), 0.45f * k, Offset(sx - 0.35f * k, sy - 3.45f * tall * k))
                                }
                            }
                        })
                    }
                }
            }
            sAlong += 11f
        }
    }
    // traffic: cars travel the length of each street, one-way streets go one way.
    // Cars yield to the ambulance: they stop at the stop line of any intersection
    // it is about to cross, hurry out of that intersection if already in it,
    // stop if the ambulance is in their path, and never drive into the car ahead.
    val dt = (t - tr.lastT).coerceIn(0f, 0.1f)
    tr.lastT = t
    val bodyX = floatArrayOf(cam.px + cam.hx * 1.5f, cam.px - cam.hx * 1f, cam.px - cam.hx * 3.5f)
    val bodyY = floatArrayOf(cam.py + cam.hy * 1.5f, cam.py - cam.hy * 1f, cam.py - cam.hy * 3.5f)
    fun touchesAmbo(x: Float, y: Float, rad: Float): Boolean {
        for (q in 0..2) if (hypot(x - bodyX[q], y - bodyY[q]) < rad) return true
        return false
    }
    // route intersections the ambulance is close to and has not yet cleared
    val active = ArrayList<FloatArray>()
    for (q in 1 until plan.pts.size - 1) {
        val ix = plan.pts[q][0]
        val iy = plan.pts[q][1]
        val dx = ix - cam.px
        val dy = iy - cam.py
        if (hypot(dx, dy) < 70f && dx * cam.hx + dy * cam.hy > -14f) active.add(floatArrayOf(ix, iy))
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
                    if (dd > 200f || dd < 3f) continue
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
        for (sd in floatArrayOf(-8.5f, 8.5f)) for (k in 0..4) {
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
            val look = PedLook(
                pid, pedCols[pid % pedCols.size], skinCols[pid % skinCols.size], hairCols[pid % hairCols.size],
                pantsCols[pid % pantsCols.size], pid % 6 == 2, pid % 9 == 4, pid % 4 == 1, pid % 5 == 3, capCols[pid % 3]
            )
            val kid = pid % 7 == 3
            objs.add(Obj(dd) {
                r.bill(x, y) { px, py, k2 -> person(px, py, if (kid) k2 * 0.72f else k2, look, t, faceK) }
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
                    drawRect(Color(0xFF78909C), Offset(sx - 0.06f * k, sy - poleH * k), Size(0.12f * k, poleH * k))
                    if (lt) {
                        drawRoundRect(Color(0xFF2B313A), Offset(sx - 0.28f * k, sy - 5.1f * k), Size(0.56f * k, 1.6f * k), CornerRadius(0.1f * k, 0.1f * k))
                        val red = floor(t / 3f).toInt() % 2 == 1
                        drawCircle(if (red) Color(0xFFFF5252) else Color(0xFF5A2020), 0.16f * k, Offset(sx, sy - 4.8f * k))
                        drawCircle(Color(0xFF5A4A20), 0.16f * k, Offset(sx, sy - 4.3f * k))
                        drawCircle(if (red) Color(0xFF1D4A30) else Color(0xFF69F0AE), 0.16f * k, Offset(sx, sy - 3.8f * k))
                        // street name sign
                        drawRect(Color(0xFF2E7D32), Offset(sx - 0.7f * k, sy - 3.3f * k), Size(1.4f * k, 0.3f * k))
                        drawRect(Color.White, Offset(sx - 0.55f * k, sy - 3.2f * k), Size(1.1f * k, 0.08f * k))
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
                            drawPath(oct, if (ring == 0) Color.White else Color(0xFFE53935))
                        }
                        if (dd < 60f) {
                            val ls = 0.56f * k / lab.stop.size.width
                            translate(sx - lab.stop.size.width * ls / 2f, sy - 2.6f * k - lab.stop.size.height * ls / 2f) {
                                scale(ls, Offset.Zero) { drawText(lab.stop) }
                            }
                        } else {
                            drawRect(Color.White, Offset(sx - 0.24f * k, sy - 2.64f * k), Size(0.48f * k, 0.08f * k))
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
    objs.sortByDescending { it.d }
    for (o in objs) o.fn()
}

private fun DrawScope.drawCab(t: Float, plan: DrivePlan, d: Float, lab: Labels) {
    val w = size.width
    val h = size.height
    val frame = Color(0xFF1D2027)
    val flash = floor(t * 4f).toInt() % 2 == 0
    val red = Color(0xFFD01010)
    // light bar glow on the windshield, red then blue
    drawRect(if (flash) Color(0x2EFF1744) else Color(0x241E78FF), size = Size(w, h * 0.2f))
    drawRect(frame, size = Size(w, h * 0.055f))
    for (i in 0..7) {
        val on = (i + (if (flash) 0 else 1)) % 2 == 0
        drawRect(if (on) Color(0xD9FF1744) else Color(0xD92979FF), Offset(i * w / 8f, h * 0.055f), Size(w / 8f + 1f, h * 0.01f))
    }
    // sun visors
    drawRoundRect(Color(0xFF3A414E), Offset(w * 0.05f, h * 0.062f), Size(w * 0.36f, h * 0.03f), CornerRadius(8f, 8f))
    drawRoundRect(Color(0xFF3A414E), Offset(w * 0.59f, h * 0.062f), Size(w * 0.36f, h * 0.03f), CornerRadius(8f, 8f))
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
            Brush.verticalGradient(listOf(Color(0xFFB3E5FC), Color(0xFF8A8FB0)), startY = h * 0.505f, endY = h * 0.58f),
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
    drawPath(stripe, Color.White)
    // wipers at rest
    drawLine(Color(0xFF111111), Offset(w * 0.18f, h * 0.735f), Offset(w * 0.5f, h * 0.715f), 4f, StrokeCap.Round)
    drawLine(Color(0xFF111111), Offset(w * 0.58f, h * 0.735f), Offset(w * 0.88f, h * 0.716f), 4f, StrokeCap.Round)
    // dashboard
    val dash = Path()
    dash.moveTo(0f, h * 0.75f)
    dash.qTo(0f, h * 0.75f, w / 2f, h * 0.72f, w, h * 0.75f)
    dash.lineTo(w, h)
    dash.lineTo(0f, h)
    dash.close()
    drawPath(dash, Color(0xFF2B313A))
    val lip = Path()
    lip.moveTo(0f, h * 0.75f)
    lip.qTo(0f, h * 0.75f, w / 2f, h * 0.72f, w, h * 0.75f)
    lip.lineTo(w, h * 0.758f)
    lip.qTo(w, h * 0.758f, w / 2f, h * 0.728f, 0f, h * 0.758f)
    lip.close()
    drawPath(lip, Color(0xFF232830))
    // speed and engine gauges
    drawRoundRect(Color(0xFF1F242B), Offset(w * 0.2f, h * 0.765f), Size(w * 0.36f, h * 0.075f), CornerRadius(18f, 18f))
    val gy = h * 0.803f
    val gr = w * 0.055f
    for (g in 0..1) {
        val gx = if (g == 0) w * 0.29f else w * 0.47f
        val v = if (g == 0) 0.55f + 0.25f * sin(t) else 0.45f + 0.2f * sin(t * 1.3f)
        drawCircle(Color(0xFF3A414E), gr, Offset(gx, gy))
        for (q in 0..8) {
            val a = (PI * 0.75 + q / 8.0 * PI * 1.5).toFloat()
            drawLine(Color(0xFFECEFF1), Offset(gx + cos(a) * gr * 0.82f, gy + sin(a) * gr * 0.82f), Offset(gx + cos(a) * gr * 0.95f, gy + sin(a) * gr * 0.95f), 1.5f)
        }
        val a = (PI * 0.75 + v * PI * 1.5).toFloat()
        drawLine(Color(0xFFFF6A3D), Offset(gx, gy), Offset(gx + cos(a) * gr * 0.73f, gy + sin(a) * gr * 0.73f), 3f, StrokeCap.Round)
        drawCircle(Color.White, 3f, Offset(gx, gy))
        val lt = if (g == 0) lab.mph else lab.rpm
        drawText(lt, topLeft = Offset(gx - lt.size.width / 2f, gy + gr * 0.58f - lt.size.height / 2f))
    }
    // siren control panel: lights and siren on, flashing with the light bar
    drawRoundRect(Color(0xFF1F242B), Offset(w * 0.62f, h * 0.77f), Size(w * 0.33f, h * 0.1f), CornerRadius(12f, 12f))
    for (i in 0..3) {
        val bx = w * 0.64f + (i % 2) * w * 0.15f
        val by = h * 0.778f + (i / 2) * h * 0.045f
        val col = when (i) {
            0 -> if (flash) Color(0xFFFF1744) else Color(0xFFB71C1C)
            1 -> if (flash) Color(0xFF2979FF) else Color(0xFF0D47A1)
            else -> Color(0xFF3A414E)
        }
        drawRoundRect(col, Offset(bx, by), Size(w * 0.135f, h * 0.037f), CornerRadius(6f, 6f))
        val lt = lab.buttons[i]
        drawText(lt, topLeft = Offset(bx + w * 0.0675f - lt.size.width / 2f, by + h * 0.0185f - lt.size.height / 2f))
    }
    // GPS screen showing the route and where the ambulance is on it
    drawRoundRect(Color(0xFF1F242B), Offset(w * 0.62f, h * 0.88f), Size(w * 0.33f, h * 0.09f), CornerRadius(10f, 10f))
    val sl = w * 0.635f
    val st = h * 0.888f
    val sw = w * 0.3f
    val sh = h * 0.074f
    drawRect(Color(0xFF263238), Offset(sl, st), Size(sw, sh))
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
        drawLine(Color(0xFF7D82A8), gp(plan.pts[i][0], plan.pts[i][1]), gp(plan.pts[i + 1][0], plan.pts[i + 1][1]), 3f, StrokeCap.Round)
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
        drawLine(Color(0xFF3FA2FF), gp(plan.pts[i][0], plan.pts[i][1]), end, 3f, StrokeCap.Round)
        if (i == cam.seg) drawCircle(Color(0xFFFF6A3D), 4f, end)
    }
    val last = plan.pts.last()
    drawCircle(Color(0xFF3FA2FF), 4f, gp(last[0], last[1]))
    // steering wheel with spokes and a red badge
    val c = Offset(w * 0.38f, h * 1.03f)
    val rad = w * 0.36f
    drawArc(Color(0xFF444444), 215f, 110f, false, Offset(c.x - rad - w * 0.022f, c.y - rad - w * 0.022f), Size((rad + w * 0.022f) * 2f, (rad + w * 0.022f) * 2f), style = Stroke(3f))
    drawArc(Color(0xFF111111), 202f, 136f, false, Offset(c.x - rad, c.y - rad), Size(rad * 2f, rad * 2f), style = Stroke(w * 0.056f, cap = StrokeCap.Round))
    drawLine(Color(0xFF1A1A1A), Offset(c.x - rad * 0.9f, c.y - rad * 0.32f), Offset(c.x - rad * 0.25f, c.y - rad * 0.18f), w * 0.035f, StrokeCap.Round)
    drawLine(Color(0xFF1A1A1A), Offset(c.x + rad * 0.9f, c.y - rad * 0.32f), Offset(c.x + rad * 0.25f, c.y - rad * 0.18f), w * 0.035f, StrokeCap.Round)
    drawOval(Color(0xFF1A1A1A), Offset(c.x - rad * 0.3f, c.y - rad * 0.32f), Size(rad * 0.6f, rad * 0.4f))
    drawCircle(red, rad * 0.06f, Offset(c.x, c.y - rad * 0.17f))
}

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
    onDone: () -> Unit
) {
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
    Box(Modifier.fillMaxSize().background(Color(0xFF1D2027))) {
        Canvas(Modifier.fillMaxSize()) {
            drawWorld(city, plan, t, destKey, crashAt, traffic, labels)
            drawCab(t, plan, plan.distAt(t), labels)
        }
        Column(
            modifier = Modifier
                .align(Alignment.TopStart)
                .safeDrawingPadding()
                .padding(start = 8.dp, top = 56.dp)
                .background(Color(0xF0FF6A3D), RoundedCornerShape(24.dp))
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Text(
                if (toHospital) "Driving to the hospital" else "Driving to the call",
                color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold
            )
            Text("Block " + blockNo + " of " + blocks, color = Color.White, fontSize = 13.sp)
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
        if (!backOpen) Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .safeDrawingPadding()
                .padding(end = 8.dp, top = 60.dp)
                .background(Color(0xF2FFFFFF), RoundedCornerShape(22.dp))
                .clickable { finish() }
                .padding(horizontal = 18.dp, vertical = 10.dp)
        ) {
            Text("Skip ▸", color = DarkInk, fontSize = 15.sp, fontWeight = FontWeight.Bold)
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
