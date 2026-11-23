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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
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
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontWeight
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
        if (abs(a[1] - b[1]) < 0.01f) {
            val j = (a[1] / BL).roundToInt().coerceIn(0, owH.size - 1)
            return if (owH[j] == 0) 1.75f else -3.5f
        }
        val i = (a[0] / BL).roundToInt().coerceIn(0, owV.size - 1)
        return if (owV[i] == 0) 1.75f else -3.5f
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

    // A box standing on the ground, ax/ay is its long direction
    fun box(cx: Float, cy: Float, ax: Float, ay: Float, hl: Float, hw: Float, z0: Float, z1: Float, front: Color, side: Color, top: Color?) {
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
            }
        }
        if (top != null && z1 < CAM_H) poly(c.map { floatArrayOf(it[0], it[1], z1) }, top)
    }

    // Something flat that always faces the driver: gives screen x, y of its base and pixels per world unit
    fun bill(x: Float, y: Float, fn: DrawScope.(Float, Float, Float) -> Unit) {
        val p = tc(x, y, 0f)
        if (p[2] < NEAR) return
        val s = pr(p)
        ds.fn(s.x, s.y, f / p[2])
    }
}

private fun DrawScope.drawWorld(city: CityData, plan: DrivePlan, t: Float, destKey: Int, crashAt: FloatArray?) {
    val d = plan.distAt(t)
    val cam = plan.camAt(d)
    val r = Renderer(this, cam)
    val w = size.width
    val h = size.height
    val n = city.n
    drawRect(Brush.verticalGradient(listOf(Color(0xFF7FD0FF), Color(0xFFCDEFFF)), endY = r.y0), size = Size(w, r.y0 + 1f))
    drawRect(Color(0xFF9BD468), Offset(0f, r.y0), Size(w, h - r.y0))
    r.ground(-9f, -9f, n * BL + 9f, n * BL + 9f, Color(0xFF7D82A8))
    fun near(x: Float, y: Float, lim: Float) = hypot(x - cam.px, y - cam.py) < lim
    for (row in 0 until n) for (col in 0 until n) {
        val x0 = col * BL
        val yy = row * BL
        if (!near(x0 + BL / 2f, yy + BL / 2f, 260f)) continue
        r.ground(x0 + 7f, yy + 7f, x0 + BL - 7f, yy + BL - 7f, Color(0xFFD9DBE8))
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
            var bh: Float
            var front: Color
            var side: Color
            when {
                hl != null -> { bh = 12f + 2f * hl; front = Color(0xFFF4F4F8); side = Color(0xFFB8BDE6) }
                key == stationKey -> { bh = 7f; front = Color(0xFFE53935); side = Color(0xFFB71C1C) }
                z == 0 -> { bh = 5f + 2f * dn; front = Color(0xFFFFE0B2); side = Color(0xFFE6C690) }
                z == 1 -> { bh = 12f + 9f * dn; front = Color(0xFF4FC3F7); side = Color(0xFF1E9BDB) }
                else -> { bh = 8f + 2.5f * dn; front = Color(0xFFFFB74D); side = Color(0xFFF29A2E) }
            }
            val ax = if (eys[e] != 0) 1f else 0f
            val ay = if (eys[e] != 0) 0f else 1f
            val isDest = key == destKey
            val isHosp = hl != null
            val topH = bh
            objs.add(Obj(dd) {
                r.box(cx, cy, ax, ay, 15f, 7f, 0f, topH, front, side, null)
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
    // traffic: cars travel the length of each street, one-way streets go one way
    var cid = 0
    fun streetCars(horiz: Boolean, idx: Int, ow: Int) {
        val dirs = if (ow == 0) intArrayOf(1, -1) else intArrayOf(if (ow == 1) (if (horiz) 1 else -1) else (if (horiz) -1 else 1))
        for (dr in dirs) {
            val lanes = if (ow == 0) floatArrayOf(5.25f) else floatArrayOf(0f, 3.5f)
            for (ln in lanes) for (k in 0..2) {
                cid++
                val sp = 5f + (cid * 37 % 5)
                var u = ((cid * 97 % 100) / 100f * total + dr * sp * t) % total
                if (u < 0f) u += total
                val ax = if (horiz) dr.toFloat() else 0f
                val ay = if (horiz) 0f else dr.toFloat()
                val rx = -ay
                val ry = ax
                val cx = (if (horiz) u else idx * BL) + rx * ln
                val cy = (if (horiz) idx * BL else u) + ry * ln
                val dd = hypot(cx - cam.px, cy - cam.py)
                if (dd > 200f || dd < 3f) continue
                val c = carCols[cid % carCols.size]
                val sideC = lerp(c, Color.Black, 0.25f)
                objs.add(Obj(dd) {
                    r.box(cx, cy, ax, ay, 2.2f, 0.95f, 0.3f, 1.1f, c, sideC, c)
                    r.box(cx - ax * 0.3f, cy - ay * 0.3f, ax, ay, 1.2f, 0.85f, 1.1f, 1.75f, Color(0xFF37474F), Color(0xFF263238), Color(0xFFECEFF1))
                })
            }
        }
    }
    for (j in 0..n) streetCars(true, j, city.owH[j])
    for (i in 0..n) streetCars(false, i, city.owV[i])
    // pedestrians on the sidewalks
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
            val c = pedCols[pid % pedCols.size]
            val sw = sin(t * 6f + pid)
            objs.add(Obj(dd) {
                r.bill(x, y) { px, py, k2 ->
                    val legC = Color(0xFF37474F)
                    drawRect(legC, Offset(px - 0.22f * k2 + sw * 0.05f * k2, py - 0.85f * k2), Size(0.18f * k2, 0.85f * k2))
                    drawRect(legC, Offset(px + 0.04f * k2 - sw * 0.05f * k2, py - 0.85f * k2), Size(0.18f * k2, 0.85f * k2))
                    drawRoundRect(c, Offset(px - 0.3f * k2, py - 1.5f * k2), Size(0.6f * k2, 0.7f * k2), CornerRadius(0.15f * k2, 0.15f * k2))
                    drawCircle(Color(0xFFF2C29B), 0.2f * k2, Offset(px, py - 1.68f * k2))
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
                    drawRect(Color(0xFF78909C), Offset(sx - 0.06f * k, sy - poleH * k), Size(0.12f * k, poleH * k))
                    if (lt) {
                        drawRoundRect(Color(0xFF2B313A), Offset(sx - 0.25f * k, sy - 4.6f * k), Size(0.5f * k, 1.3f * k), CornerRadius(0.1f * k, 0.1f * k))
                        val red = floor(t / 3f).toInt() % 2 == 1
                        drawCircle(if (red) Color(0xFFFF5252) else Color(0xFF5A2020), 0.15f * k, Offset(sx, sy - 4.3f * k))
                        drawCircle(if (red) Color(0xFF1D4A30) else Color(0xFF69F0AE), 0.15f * k, Offset(sx, sy - 3.6f * k))
                    } else {
                        val oct = Path()
                        for (q in 0..7) {
                            val a = (22.5 + 45.0 * q) * PI / 180.0
                            val xq = sx + (cos(a) * 0.38 * k).toFloat()
                            val yq = sy - 2.6f * k + (sin(a) * 0.38 * k).toFloat()
                            if (q == 0) oct.moveTo(xq, yq) else oct.lineTo(xq, yq)
                        }
                        oct.close()
                        drawPath(oct, Color(0xFFE53935))
                        drawRect(Color.White, Offset(sx - 0.24f * k, sy - 2.64f * k), Size(0.48f * k, 0.08f * k))
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

private fun DrawScope.drawCab(t: Float, d: Float) {
    val w = size.width
    val h = size.height
    val frame = Color(0xFF1D2027)
    val flash = floor(t * 4f).toInt() % 2 == 0
    // light bar glow on the windshield
    drawRect(if (flash) Color(0x2EFF1744) else Color(0x1FFFFFFF), size = Size(w, h * 0.2f))
    drawRect(frame, size = Size(w, h * 0.055f))
    drawRect(if (flash) Color(0x8CFF1744) else Color(0x80FFFFFF), Offset(0f, h * 0.055f), Size(w, h * 0.01f))
    val pl = Path()
    pl.moveTo(0f, 0f); pl.lineTo(w * 0.09f, 0f); pl.lineTo(w * 0.02f, h * 0.62f); pl.lineTo(0f, h * 0.62f); pl.close()
    drawPath(pl, frame)
    val pr = Path()
    pr.moveTo(w, 0f); pr.lineTo(w * 0.91f, 0f); pr.lineTo(w * 0.98f, h * 0.62f); pr.lineTo(w, h * 0.62f); pr.close()
    drawPath(pr, frame)
    // red hood
    val hood = Path()
    hood.moveTo(0f, h * 0.70f)
    hood.qTo(0f, h * 0.70f, w / 2f, h * 0.655f, w, h * 0.70f)
    hood.lineTo(w, h)
    hood.lineTo(0f, h)
    hood.close()
    drawPath(hood, Color(0xFFD01010))
    val shine = Path()
    shine.moveTo(w * 0.1f, h * 0.69f)
    shine.qTo(w * 0.1f, h * 0.69f, w / 2f, h * 0.66f, w * 0.9f, h * 0.69f)
    shine.lineTo(w * 0.9f, h * 0.695f)
    shine.qTo(w * 0.9f, h * 0.695f, w / 2f, h * 0.668f, w * 0.1f, h * 0.695f)
    shine.close()
    drawPath(shine, Color(0x40FFFFFF))
    // dashboard
    val dash = Path()
    dash.moveTo(0f, h * 0.75f)
    dash.qTo(0f, h * 0.75f, w / 2f, h * 0.72f, w, h * 0.75f)
    dash.lineTo(w, h)
    dash.lineTo(0f, h)
    dash.close()
    drawPath(dash, Color(0xFF2B313A))
    val gy = h * 0.8f
    for (gx in floatArrayOf(w * 0.3f, w * 0.45f)) {
        drawCircle(Color(0xFF3A414E), w * 0.046f, Offset(gx, gy))
        val a = -2.4f + d % 3f
        drawLine(Color(0xFFFF6A3D), Offset(gx, gy), Offset(gx + w * 0.03f * cos(a), gy + w * 0.03f * sin(a)), 3f, StrokeCap.Round)
    }
    // steering wheel
    val c = Offset(w * 0.38f, h * 1.03f)
    val rad = w * 0.36f
    drawArc(Color(0xFF111111), 202f, 136f, false, Offset(c.x - rad, c.y - rad), Size(rad * 2f, rad * 2f), style = Stroke(w * 0.056f, cap = StrokeCap.Round))
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
    onDone: () -> Unit
) {
    val plan = remember(city, nodes, start, end) { DrivePlan(city, nodes, start, end) }
    val crashAt = remember(plan, crash) {
        if (crash) floatArrayOf(DrivePlan.wx(end.x) + 4f, DrivePlan.wy(end.y) + 4f) else null
    }
    var t by remember { mutableFloatStateOf(0f) }
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
    LaunchedEffect(plan) {
        var first = -1L
        while (true) {
            withFrameNanos { now ->
                if (first < 0L) first = now
                t = (now - first) / 1_000_000_000f
            }
            if (t > plan.total + 1f) {
                finish()
                break
            }
        }
    }
    Box(Modifier.fillMaxSize().background(Color(0xFF1D2027))) {
        Canvas(Modifier.fillMaxSize()) {
            drawWorld(city, plan, t, destKey, crashAt)
            drawCab(t, plan.distAt(t))
        }
        val cam = plan.camAt(plan.distAt(t))
        val blocks = plan.pts.size - 1
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
            Text("Block " + min(cam.seg + 1, blocks) + " of " + blocks, color = Color.White, fontSize = 13.sp)
        }
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .safeDrawingPadding()
                .padding(end = 8.dp, top = 60.dp)
                .background(Color(0xF2FFFFFF), RoundedCornerShape(22.dp))
                .clickable { finish() }
                .padding(horizontal = 18.dp, vertical = 10.dp)
        ) {
            Text("Skip ▸", color = InkText, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        }
        if (t > plan.total) {
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
    }
}
