package com.cpd360.ambulancerun

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

private val Road = Color(0xFFA9ADE3)
private val XS = floatArrayOf(19f, 99f, 179f, 259f, 339f)
private val YS = floatArrayOf(20f, 100f, 180f, 260f)

@Composable
fun CityMap(
    path: List<Int>,
    done: Boolean,
    started: Boolean,
    stage: Int,
    onBegin: () -> Unit,
    onNode: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val t = rememberInfiniteTransition(label = "pulse")
    val p by t.animateFloat(
        0f, 1f,
        infiniteRepeatable(tween(1600, easing = LinearEasing), RepeatMode.Restart),
        label = "p"
    )
    val cb by rememberUpdatedState(onNode)
    val begin by rememberUpdatedState(onBegin)
    val st by rememberUpdatedState(started)
    val stg by rememberUpdatedState(stage)
    val dn by rememberUpdatedState(done)
    val pth by rememberUpdatedState(path)
    var zm by remember { mutableFloatStateOf(1f) }
    var pan by remember { mutableStateOf(Offset.Zero) }
    Canvas(
        modifier
            .clipToBounds()
            .pointerInput(Unit) {
                awaitEachGesture {
                    val w = size.width.toFloat()
                    val h = size.height.toFloat()
                    val s0 = w / 358f
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val m0 = (down.position - mapOrigin(w, h, s0, zm, pan)) / (s0 * zm)
                    val sp = City.STARTPT[stg]
                    val endX = if (!st || pth.isEmpty()) sp[0] else City.xs[pth.last() % 5]
                    val endY = if (!st || pth.isEmpty()) sp[1] else City.ys[pth.last() / 5]
                    var tracing = !dn && kotlin.math.hypot(m0.x - endX, m0.y - endY) < 34f
                    if (tracing) begin()
                    do {
                        val ev = awaitPointerEvent()
                        val pressed = ev.changes.count { it.pressed }
                        if (pressed >= 2) {
                            tracing = false
                            val zc = ev.calculateZoom()
                            val pz = ev.calculatePan()
                            val c = ev.calculateCentroid()
                            val o = mapOrigin(w, h, s0, zm, pan)
                            val nz = (zm * zc).coerceIn(1f, 5f)
                            val no = c - (c - o) * (nz / zm) + pz
                            zm = nz
                            pan = clampPan(no - baseOrigin(w, h, s0, nz), w, h, s0, nz)
                            ev.changes.forEach { it.consume() }
                        } else if (pressed == 1) {
                            val chg = ev.changes.first { it.pressed }
                            if (tracing) {
                                val mp = (chg.position - mapOrigin(w, h, s0, zm, pan)) / (s0 * zm)
                                var best = -1
                                var bd = 24f
                                for (j in 0..3) for (i in 0..4) {
                                    val d = kotlin.math.hypot(mp.x - City.xs[i], mp.y - City.ys[j])
                                    if (d < bd) {
                                        bd = d
                                        best = j * 5 + i
                                    }
                                }
                                if (best >= 0) cb(best)
                            } else {
                                pan = clampPan(pan + chg.positionChange(), w, h, s0, zm)
                            }
                            chg.consume()
                        }
                    } while (ev.changes.any { it.pressed })
                }
            }
    ) {
        val s0 = size.width / 358f
        val o = mapOrigin(size.width, size.height, s0, zm, pan)
        drawRect(Color(0xFF9BD468))
        translate(o.x, o.y) {
            scale(s0 * zm, Offset.Zero) { drawCity(p, path, done, started, stage) }
        }
    }
}

private fun baseOrigin(w: Float, h: Float, s0: Float, z: Float): Offset =
    Offset((w - 358f * s0 * z) / 2f, (h - 280f * s0 * z) / 2f)

private fun mapOrigin(w: Float, h: Float, s0: Float, z: Float, pan: Offset): Offset =
    baseOrigin(w, h, s0, z) + pan

private fun clampPan(pn: Offset, w: Float, h: Float, s0: Float, z: Float): Offset {
    val mx = maxOf(0f, (358f * s0 * z - w) / 2f)
    val my = maxOf(0f, (280f * s0 * z - h) / 2f)
    return Offset(pn.x.coerceIn(-mx, mx), pn.y.coerceIn(-my, my))
}

private fun DrawScope.box(c: Color, x: Float, y: Float, w: Float, h: Float, r: Float = 0f, a: Float = 1f) =
    drawRoundRect(c, Offset(x, y), Size(w, h), CornerRadius(r, r), alpha = a)

private fun DrawScope.poly(c: Color, a: Float, vararg p: Float) {
    val path = Path()
    path.moveTo(p[0], p[1])
    var i = 2
    while (i < p.size) {
        path.lineTo(p[i], p[i + 1])
        i += 2
    }
    path.close()
    drawPath(path, c, alpha = a)
}

private fun DrawScope.house(cx: Float, cy: Float, s: Float) = translate(cx, cy) {
    scale(s, Offset.Zero) {
        drawOval(Color.Black, Offset(-17f, 14f), Size(34f, 8f), alpha = 0.15f)
        box(Color(0xFFFFE0B2), -14f, -2f, 28f, 19f, 4f)
        box(Color(0xFFE6C690), 7f, -2f, 7f, 19f, 3f)
        poly(Color(0xFFE53935), 1f, -17f, -3f, 0f, -18f, 17f, -3f)
        poly(Color.White, 0.22f, -17f, -3f, 0f, -18f, 0f, -3f)
        box(Color(0xFF8D5A3C), -4f, 6f, 7f, 11f, 2f)
    }
}

private fun DrawScope.tower(cx: Float, cy: Float, s: Float) = translate(cx, cy) {
    scale(s, Offset.Zero) {
        drawOval(Color.Black, Offset(-17f, 15f), Size(34f, 8f), alpha = 0.15f)
        box(Color(0xFF4FC3F7), -14f, -22f, 28f, 40f, 6f)
        box(Color(0xFF1E9BDB), 5f, -22f, 9f, 40f, 5f)
        box(Color.White, -14f, -22f, 9f, 40f, 5f, 0.28f)
        for (wy in floatArrayOf(-14f, -5f, 4f)) for (wx in floatArrayOf(-8f, 2f)) {
            box(Color(0xFFE1F5FE), wx, wy, 6f, 5f, 1.5f)
        }
    }
}

private fun DrawScope.factory(cx: Float, cy: Float, s: Float) = translate(cx, cy) {
    scale(s, Offset.Zero) {
        drawOval(Color.Black, Offset(-20f, 14f), Size(40f, 8f), alpha = 0.15f)
        box(Color(0xFF8D6E63), 6f, -20f, 7f, 17f, 3f)
        drawCircle(Color.White, 5f, Offset(10f, -25f))
        box(Color(0xFFFFB74D), -18f, -6f, 36f, 23f, 5f)
        box(Color(0xFFF29A2E), 8f, -6f, 10f, 23f, 5f)
        box(Color.White, -18f, -6f, 10f, 23f, 5f, 0.25f)
        box(Color(0xFF8D5A3C), -6f, 3f, 10f, 14f, 2f)
    }
}

private fun DrawScope.hospital(cx: Float, cy: Float) = translate(cx, cy) {
    drawOval(Color.Black, Offset(-12f, 9f), Size(24f, 6f), alpha = 0.15f)
    box(Color(0xFFB8BDE6), -11.5f, -10.5f, 23f, 21f, 2f)
    box(Color.White, -11f, -10f, 22f, 20f, 2f)
    box(Color(0xFFE8EAFB), -11f, -10f, 22f, 4f)
    box(Color(0xFFE53935), -1.8f, -5f, 3.6f, 11f)
    box(Color(0xFFE53935), -5.5f, -1.5f, 11f, 3.6f)
}

private fun DrawScope.chev(x: Float, y: Float, deg: Float) = translate(x, y) {
    rotate(deg, Offset.Zero) {
        val p = Path()
        p.moveTo(-2.2f, -2.6f)
        p.lineTo(2.2f, 0f)
        p.lineTo(-2.2f, 2.6f)
        drawPath(p, Color(0xFFE6E8FB), alpha = 0.5f, style = Stroke(1.2f, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

private fun DrawScope.stopSign(x: Float, y: Float) {
    val path = Path()
    for (k in 0..7) {
        val a = (22.5 + 45.0 * k) * PI / 180.0
        val px = x + (3.5 * cos(a)).toFloat()
        val py = y + (3.5 * sin(a)).toFloat()
        if (k == 0) path.moveTo(px, py) else path.lineTo(px, py)
    }
    path.close()
    drawPath(path, Color(0xFFE53935))
}

private fun DrawScope.light(x: Float, y: Float) {
    box(Color.White, x - 4.2f, y - 7.7f, 8.4f, 15.4f, 2.5f)
    box(Color(0xFF2B313A), x - 3.5f, y - 7f, 7f, 14f, 2f)
    drawCircle(Color(0xFFFF5252), 2f, Offset(x, y - 3.5f))
    drawCircle(Color(0xFF69F0AE), 2f, Offset(x, y + 3.5f))
}

private fun DrawScope.drawCity(p: Float, path: List<Int>, done: Boolean, started: Boolean, stage: Int) {
    val k = if (p < 0.5f) p * 2f else (1f - p) * 2f
    box(Color(0xFF9BD468), 0f, 0f, 358f, 280f)
    for (y in YS) box(Road, 0f, y - 8f, 358f, 16f)
    for (x in XS) box(Road, x - 8f, 12f, 16f, 256f)
    val dash = PathEffect.dashPathEffect(floatArrayOf(8f, 6f))
    val yellow = Color(0xFFFFE66B)
    for (j in 0..3) for (c in 0..3) {
        val a = 27f + 80f * c
        drawLine(if (j == 1) Color.White else yellow, Offset(a, YS[j]), Offset(a + 64f, YS[j]), 2f, pathEffect = dash)
    }
    for (i in 0..4) for (r in 0..2) {
        val b = 28f + 80f * r
        drawLine(if (i == 2) Color.White else yellow, Offset(XS[i], b), Offset(XS[i], b + 64f), 2f, pathEffect = dash)
    }
    val zones = arrayOf("rrcc", "rccc", "iirr")
    val cellX = floatArrayOf(18f, 46f, 18f, 46f)
    val cellY = floatArrayOf(18f, 18f, 46f, 46f)
    val dirs = "WNSE"
    val dw = Color(0xFFE2D39B)
    val ap = Color(0xFFBFC2EC)
    val door = Color(0xFF8D5A3C)
    for (r in 0..2) for (c in 0..3) {
        val bx = 27f + 80f * c
        val by = 28f + 80f * r
        val z = zones[r][c]
        val top = when (z) { 'r' -> Color(0xFFB9E67F); 'c' -> Color(0xFFFFE59A); else -> Color(0xFFDCCFF3) }
        val bot = when (z) { 'r' -> Color(0xFF8FCB59); 'c' -> Color(0xFFFFCB5A); else -> Color(0xFFB9A7DF) }
        val edge = when (z) { 'r' -> Color(0xFF6FAE3E); 'c' -> Color(0xFFD9A93A); else -> Color(0xFF9684BB) }
        box(edge, bx + 2f, by + 7f, 60f, 60f)
        box(Color(0xFFF3E2A9), bx + 2f, by + 2f, 60f, 60f)
        drawRect(Brush.verticalGradient(listOf(top, bot), startY = by + 5f, endY = by + 59f), Offset(bx + 5f, by + 5f), Size(54f, 54f))
        box(Color.White, bx + 9f, by + 6.5f, 46f, 3f, 0f, 0.28f)
        for (n in 0..3) {
            val cx = bx + cellX[n]
            val cy = by + cellY[n]
            when (dirs[n]) {
                'W' -> { box(dw, bx - 1f, cy - 3f, (cx - 9f) - (bx - 1f), 6f); box(ap, bx - 3f, cy - 5f, 3f, 10f); drawCircle(door, 1.8f, Offset(cx - 9f, cy)) }
                'N' -> { box(dw, cx - 3f, by - 1f, 6f, (cy - 9f) - (by - 1f)); box(ap, cx - 5f, by - 3f, 10f, 3f); drawCircle(door, 1.8f, Offset(cx, cy - 9f)) }
                'S' -> { box(dw, cx - 3f, cy + 9f, 6f, (by + 65f) - (cy + 9f)); box(ap, cx - 5f, by + 64f, 10f, 3f); drawCircle(door, 1.8f, Offset(cx, cy + 9f)) }
                else -> { box(dw, cx + 9f, cy - 3f, (bx + 65f) - (cx + 9f), 6f); box(ap, bx + 64f, cy - 5f, 3f, 10f); drawCircle(door, 1.8f, Offset(cx + 9f, cy)) }
            }
            val call = if (stage == 0) (c == 2 && r == 0 && n == 2) else (c == 3 && r == 1 && n == 2)
            val hosp = c == 3 && r == 1 && n == 2
            if (call) {
                val blue = Color(0xFF3FA2FF)
                for (g in 0..3) {
                    val d = g * 3.5f
                    box(blue, cx - 13f - d, cy - 13f - d, 26f + 2f * d, 26f + 2f * d, 10f + 3f * g, (0.35f - 0.07f * g) * (0.4f + 0.6f * k))
                }
                drawCircle(Color(0xFF6CB8FF), 12f + 12f * p, Offset(cx, cy), alpha = 0.8f * (1f - p), style = Stroke(2f))
            }
            if (hosp) hospital(cx, cy)
            else when (z) {
                'r' -> house(cx, cy, 0.58f)
                'c' -> tower(cx, cy, 0.62f)
                else -> factory(cx, cy + 3f, 0.5f)
            }
            if (call) box(Color(0xFF3FA2FF), cx - 11f, cy - 12f, 22f, 24f, 6f, 0.05f + 0.35f * k)
        }
    }
    for (j in 0..3) for (m in floatArrayOf(59f, 139f, 219f, 299f)) for (o in floatArrayOf(-16f, 0f, 16f)) {
        val x = m + o
        val y = YS[j]
        if (j == 1) { chev(x, y - 4f, 0f); chev(x, y + 4f, 0f) } else { chev(x, y - 4f, 180f); chev(x, y + 4f, 0f) }
    }
    for (i in 0..4) for (m in floatArrayOf(60f, 140f, 220f)) for (o in floatArrayOf(-16f, 0f, 16f)) {
        val y = m + o
        val x = XS[i]
        if (i == 2) { chev(x - 4f, y, 270f); chev(x + 4f, y, 270f) } else { chev(x - 4f, y, 90f); chev(x + 4f, y, 270f) }
    }
    for (j in 0..3) for (i in 0..4) {
        val x = XS[i]
        val y = YS[j]
        for (n in 0..3) {
            val o = -7.7f + 4.4f * n
            if (j > 0) box(Color.White, x + o, y - 13.5f, 2.2f, 5f, 0f, 0.85f)
            if (j < 3) box(Color.White, x + o, y + 8.5f, 2.2f, 5f, 0f, 0.85f)
            if (i > 0) box(Color.White, x - 13.5f, y + o, 5f, 2.2f, 0f, 0.85f)
            if (i < 4) box(Color.White, x + 8.5f, y + o, 5f, 2.2f, 0f, 0.85f)
        }
    }
    if (started) {
        val route = Path()
        route.moveTo(City.STARTPT[stage][0], City.STARTPT[stage][1])
        for (n in path) route.lineTo(City.xs[n % 5], City.ys[n / 5])
        if (done) route.lineTo(City.ENDPT[stage][0], City.ENDPT[stage][1])
        val rs = { w: Float -> Stroke(w, cap = StrokeCap.Round, join = StrokeJoin.Round) }
        drawPath(route, Color(0xFF2E7BFF), alpha = 0.10f, style = rs(9f))
        translate(0f, 2.2f) { drawPath(route, Color(0xFF0E2A78), alpha = 0.55f, style = rs(6f)) }
        drawPath(route, Color(0xFF2E7BFF), style = rs(6f))
        translate(0f, -1.3f) { drawPath(route, Color(0xFFBFE0FF), style = rs(2f)) }
    }
    for (j in 0..3) for (i in 0..4) {
        val isL = (i == 1 && j == 1) || (i == 2 && j == 1) || (i == 1 && j == 2)
        for (o in floatArrayOf(-11f, 11f)) for (q in floatArrayOf(-11f, 11f)) {
            if (isL) light(XS[i] + o, YS[j] + q) else stopSign(XS[i] + o, YS[j] + q)
        }
    }
    val ax = if (stage == 0) 74f else 205f
    val ay = if (stage == 0) 264f else 104f
    translate(ax, ay) {
        scale(0.7f, Offset.Zero) {
            drawOval(Color.Black, Offset(-17f, 5f), Size(34f, 8f), alpha = 0.2f)
            box(Color(0xFFB0BEC5), -16f, -8f, 32f, 18f, 7f)
            box(Color.White, -16f, -11f, 32f, 18f, 7f)
            box(Color(0xFFE53935), -3f, -7f, 6f, 11f, 1f)
            box(Color(0xFFE53935), -6f, -4f, 12f, 5f, 1f)
            drawCircle(Color(0xFF2979FF), 3.5f, Offset(10f, -11f))
            drawCircle(Color(0xFFFF5252), 3.5f, Offset(-10f, -11f))
            drawCircle(Color(0xFF37474F), 3f, Offset(-10f, 8f))
            drawCircle(Color(0xFF37474F), 3f, Offset(10f, 8f))
        }
    }
    if (stage == 0) {
        drawCircle(Color.White, 13f, Offset(50f, 231f))
        poly(Color.White, 1f, 40f, 239f, 60f, 239f, 50f, 252f)
        drawCircle(Color(0xFF2979FF), 11f, Offset(50f, 231f))
        poly(Color(0xFF2979FF), 1f, 42f, 238f, 58f, 238f, 50f, 249f)
        drawCircle(Color.White, 4f, Offset(50f, 231f))
    }
}
