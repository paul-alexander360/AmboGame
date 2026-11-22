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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
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
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

private val Road = Color(0xFFA9ADE3)

@Composable
fun CityMap(rs: RouteState, modifier: Modifier = Modifier) {
    val city = rs.city
    val t = rememberInfiniteTransition(label = "pulse")
    val p by t.animateFloat(
        0f, 1f,
        infiniteRepeatable(tween(1600, easing = LinearEasing), RepeatMode.Restart),
        label = "p"
    )
    var zm by remember { mutableFloatStateOf(1f) }
    var pan by remember { mutableStateOf(Offset.Zero) }
    var sz by remember { mutableStateOf(IntSize.Zero) }
    LaunchedEffect(rs.version, rs.stage, sz) {
        if (sz.width > 0) {
            val w = sz.width.toFloat()
            val h = sz.height.toFloat()
            val s0 = w / city.w
            val z = (city.n / 4f).coerceIn(1f, 6f)
            val fp = rs.startPt()
            val base = baseOrigin(city, w, h, s0, z)
            val target = Offset(w / 2f - fp.x * s0 * z, h / 2f - fp.y * s0 * z)
            zm = z
            pan = clampPan(target - base, city, w, h, s0, z)
        }
    }
    LaunchedEffect(rs.focusTick) {
        if (rs.focusTick > 0 && sz.width > 0) {
            val w = sz.width.toFloat()
            val h = sz.height.toFloat()
            val s0 = w / city.w
            val z = (city.w / 240f).coerceIn(1f, 8f)
            val fp = rs.focusPt
            val base = baseOrigin(city, w, h, s0, z)
            val target = Offset(w / 2f - fp.x * s0 * z, h / 2f - fp.y * s0 * z)
            zm = z
            pan = clampPan(target - base, city, w, h, s0, z)
        }
    }
    Box(
        modifier
            .clipToBounds()
            .onSizeChanged { sz = it }
            .pointerInput(Unit) {
                awaitEachGesture {
                    val cty = rs.city
                    val w = size.width.toFloat()
                    val h = size.height.toFloat()
                    val s0 = w / cty.w
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val m0 = (down.position - mapOrigin(cty, w, h, s0, zm, pan)) / (s0 * zm)
                    val last = if (rs.path.isEmpty()) rs.startPt() else Offset(
                        cty.nodeX(rs.path.last() % cty.m), cty.nodeY(rs.path.last() / cty.m)
                    )
                    var tracing = !rs.done && (m0 - last).getDistance() < 34f
                    if (tracing) rs.begin()
                    do {
                        val ev = awaitPointerEvent()
                        val pressed = ev.changes.count { it.pressed }
                        if (pressed >= 2) {
                            tracing = false
                            val pr = ev.changes.filter { it.pressed }
                            var sx = 0f
                            var sy = 0f
                            var qx = 0f
                            var qy = 0f
                            for (q in pr) {
                                sx += q.position.x
                                sy += q.position.y
                                qx += q.previousPosition.x
                                qy += q.previousPosition.y
                            }
                            val kk = pr.size.toFloat()
                            val cc = Offset(sx / kk, sy / kk)
                            val pc = Offset(qx / kk, qy / kk)
                            var spread = 0f
                            var spreadPrev = 0f
                            for (q in pr) {
                                spread += hypot(q.position.x - cc.x, q.position.y - cc.y)
                                spreadPrev += hypot(q.previousPosition.x - pc.x, q.previousPosition.y - pc.y)
                            }
                            val zc = if (spreadPrev > 1f) spread / spreadPrev else 1f
                            val o = mapOrigin(cty, w, h, s0, zm, pan)
                            val nz = (zm * zc).coerceIn(1f, 8f)
                            val no = cc - (pc - o) * (nz / zm)
                            zm = nz
                            pan = clampPan(no - baseOrigin(cty, w, h, s0, nz), cty, w, h, s0, nz)
                            ev.changes.forEach { it.consume() }
                        } else if (pressed == 1) {
                            val chg = ev.changes.first { it.pressed }
                            if (tracing) {
                                val mp = (chg.position - mapOrigin(cty, w, h, s0, zm, pan)) / (s0 * zm)
                                var best = -1
                                var bd = 24f
                                for (j in 0..cty.n) for (i in 0..cty.n) {
                                    val d = hypot(mp.x - cty.nodeX(i), mp.y - cty.nodeY(j))
                                    if (d < bd) {
                                        bd = d
                                        best = j * cty.m + i
                                    }
                                }
                                if (best >= 0) rs.move(best)
                            } else {
                                pan = clampPan(pan + chg.positionChange(), cty, w, h, s0, zm)
                            }
                            chg.consume()
                        }
                    } while (ev.changes.any { it.pressed })
                }
            }
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val s0 = size.width / city.w
            val o = mapOrigin(city, size.width, size.height, s0, zm, pan)
            drawRect(Color(0xFF9BD468))
            translate(o.x, o.y) {
                scale(s0 * zm, Offset.Zero) { drawStatic(city, s0 * zm) }
            }
        }
        Canvas(Modifier.fillMaxSize()) {
            val s0 = size.width / city.w
            val o = mapOrigin(city, size.width, size.height, s0, zm, pan)
            translate(o.x, o.y) {
                scale(s0 * zm, Offset.Zero) { drawDynamic(rs, p) }
            }
        }
    }
}

private fun baseOrigin(c: CityData, w: Float, h: Float, s0: Float, z: Float): Offset =
    Offset((w - c.w * s0 * z) / 2f, (h - c.h * s0 * z) / 2f)

private fun mapOrigin(c: CityData, w: Float, h: Float, s0: Float, z: Float, pan: Offset): Offset =
    baseOrigin(c, w, h, s0, z) + pan

private fun clampPan(pn: Offset, c: CityData, w: Float, h: Float, s0: Float, z: Float): Offset {
    val mx = maxOf(0f, (c.w * s0 * z - w) / 2f)
    val my = maxOf(0f, (c.h * s0 * z - h) / 2f)
    return Offset(pn.x.coerceIn(-mx, mx), pn.y.coerceIn(-my, my))
}

private fun zoneCol(z: Int, which: Int, d: Int): Color {
    val base = when (which) {
        0 -> when (z) { 0 -> Color(0xFFB9E67F); 1 -> Color(0xFFFFE59A); else -> Color(0xFFDCCFF3) }
        1 -> when (z) { 0 -> Color(0xFF8FCB59); 1 -> Color(0xFFFFCB5A); else -> Color(0xFFB9A7DF) }
        else -> when (z) { 0 -> Color(0xFF6FAE3E); 1 -> Color(0xFFD9A93A); else -> Color(0xFF9684BB) }
    }
    return lerp(base, Color(0xFF2B2B2B), 0.1f * d)
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


private fun DrawScope.station(cx: Float, cy: Float) = translate(cx, cy) {
    scale(0.55f, Offset.Zero) {
        drawOval(Color.Black, Offset(-18f, 14f), Size(36f, 8f), alpha = 0.15f)
        box(Color(0xFFB71C1C), -17f, -15f, 34f, 34f, 6f)
        box(Color(0xFFE53935), -17f, -17f, 34f, 34f, 6f)
        box(Color.White, -17f, -17f, 34f, 7f, 3f, 0.3f)
        box(Color(0xFFB0BEC5), -9f, 8f, 18f, 9f, 2f)
        drawCircle(Color.White, 10f, Offset(0f, -2f))
        for (a in floatArrayOf(0f, 60f, 120f)) {
            rotate(a, Offset(0f, -2f)) {
                drawLine(Color(0xFF1E6FE0), Offset(-8f, -2f), Offset(8f, -2f), 3f, StrokeCap.Round)
            }
        }
    }
}

private fun DrawScope.hospitalL(cx: Float, cy: Float, lv: Int) {
    hospital(cx, cy)
    for (q in 0 until lv) {
        drawCircle(Color(0xFF2979FF), 1.7f, Offset(cx - (lv - 1) * 2.5f + q * 5f, cy + 14f))
    }
}

private fun DrawScope.drawStatic(c: CityData, sc: Float) {
    val n = c.n
    val detail = sc >= 2f
    box(Color(0xFF9BD468), 0f, 0f, c.w, c.h)
    for (j in 0..n) box(Road, 0f, c.nodeY(j) - 8f, c.w, 16f)
    for (i in 0..n) box(Road, c.nodeX(i) - 8f, 12f, 16f, c.h - 24f)
    val dash = PathEffect.dashPathEffect(floatArrayOf(8f, 6f))
    val yellow = Color(0xFFFFE66B)
    val pot = Color(0xFF5B5F9A)
    for (j in 0..n) for (b in 0 until n) {
        val a = 27f + 80f * b
        val y = c.nodeY(j)
        drawLine(if (c.owH[j] != 0) Color.White else yellow, Offset(a, y), Offset(a + 64f, y), 2f, pathEffect = dash)
        if (c.qH[j * n + b] == 2) {
            drawOval(pot, Offset(a + 16f, y - 6f), Size(9f, 4f), alpha = 0.55f)
            drawOval(pot, Offset(a + 38f, y + 3f), Size(9f, 4f), alpha = 0.55f)
        }
    }
    for (i in 0..n) for (b in 0 until n) {
        val a = 28f + 80f * b
        val x = c.nodeX(i)
        drawLine(if (c.owV[i] != 0) Color.White else yellow, Offset(x, a), Offset(x, a + 64f), 2f, pathEffect = dash)
        if (c.qV[i * n + b] == 2) {
            drawOval(pot, Offset(x - 6f, a + 16f), Size(4f, 9f), alpha = 0.55f)
            drawOval(pot, Offset(x + 3f, a + 38f), Size(4f, 9f), alpha = 0.55f)
        }
    }
    val cellX = floatArrayOf(18f, 46f, 18f, 46f)
    val cellY = floatArrayOf(18f, 18f, 46f, 46f)
    val dw = Color(0xFFE2D39B)
    val ap = Color(0xFFBFC2EC)
    val door = Color(0xFF8D5A3C)
    for (r in 0 until n) for (cc in 0 until n) {
        val bx = 27f + 80f * cc
        val by = 28f + 80f * r
        val z = c.zone[r * n + cc]
        val d = c.dens[r * n + cc]
        box(zoneCol(z, 2, d), bx + 2f, by + 7f, 60f, 60f)
        box(Color(0xFFF3E2A9), bx + 2f, by + 2f, 60f, 60f)
        drawRect(
            Brush.verticalGradient(listOf(zoneCol(z, 0, d), zoneCol(z, 1, d)), startY = by + 5f, endY = by + 59f),
            Offset(bx + 5f, by + 5f), Size(54f, 54f)
        )
        if (detail) box(Color.White, bx + 9f, by + 6.5f, 46f, 3f, 0f, 0.28f)
        for (k in 0..3) {
            val cx = bx + cellX[k]
            val cy = by + cellY[k]
            if (detail) {
                when (k) {
                    0 -> { box(dw, bx - 1f, cy - 3f, (cx - 9f) - (bx - 1f), 6f); box(ap, bx - 3f, cy - 5f, 3f, 10f); drawCircle(door, 1.8f, Offset(cx - 9f, cy)) }
                    1 -> { box(dw, cx - 3f, by - 1f, 6f, (cy - 9f) - (by - 1f)); box(ap, cx - 5f, by - 3f, 10f, 3f); drawCircle(door, 1.8f, Offset(cx, cy - 9f)) }
                    2 -> { box(dw, cx - 3f, cy + 9f, 6f, (by + 65f) - (cy + 9f)); box(ap, cx - 5f, by + 64f, 10f, 3f); drawCircle(door, 1.8f, Offset(cx, cy + 9f)) }
                    else -> { box(dw, cx + 9f, cy - 3f, (bx + 65f) - (cx + 9f), 6f); box(ap, bx + 64f, cy - 5f, 3f, 10f); drawCircle(door, 1.8f, Offset(cx + 9f, cy)) }
                }
            }
            val key = (r * n + cc) * 4 + k
            val hl = c.hospKey[key]
            if (key == c.key(c.station)) {
                station(cx, cy)
            } else if (hl != null) {
                hospitalL(cx, cy, hl)
            } else if (detail) {
                when (z) {
                    0 -> house(cx, cy, 0.58f)
                    1 -> tower(cx, cy, 0.62f)
                    else -> factory(cx, cy + 3f, 0.5f)
                }
            } else {
                box(zoneCol(z, 2, d), cx - 8f, cy - 8f, 16f, 16f, 3f)
            }
        }
    }
    if (detail) {
        for (j in 0..n) for (b in 0 until n) for (o in floatArrayOf(-16f, 0f, 16f)) {
            val x = 27f + 80f * b + 32f + o
            val y = c.nodeY(j)
            when (c.owH[j]) {
                0 -> { chev(x, y - 4f, 180f); chev(x, y + 4f, 0f) }
                1 -> { chev(x, y - 4f, 0f); chev(x, y + 4f, 0f) }
                else -> { chev(x, y - 4f, 180f); chev(x, y + 4f, 180f) }
            }
        }
        for (i in 0..n) for (b in 0 until n) for (o in floatArrayOf(-16f, 0f, 16f)) {
            val y = 28f + 80f * b + 32f + o
            val x = c.nodeX(i)
            when (c.owV[i]) {
                0 -> { chev(x - 4f, y, 90f); chev(x + 4f, y, 270f) }
                1 -> { chev(x - 4f, y, 270f); chev(x + 4f, y, 270f) }
                else -> { chev(x - 4f, y, 90f); chev(x + 4f, y, 90f) }
            }
        }
    }
    for (j in 0..n) for (i in 0..n) {
        val x = c.nodeX(i)
        val y = c.nodeY(j)
        if (sc >= 1.5f) {
            for (q in 0..3) {
                val o = -7.7f + 4.4f * q
                if (j > 0) box(Color.White, x + o, y - 13.5f, 2.2f, 5f, 0f, 0.85f)
                if (j < n) box(Color.White, x + o, y + 8.5f, 2.2f, 5f, 0f, 0.85f)
                if (i > 0) box(Color.White, x - 13.5f, y + o, 5f, 2.2f, 0f, 0.85f)
                if (i < n) box(Color.White, x + 8.5f, y + o, 5f, 2.2f, 0f, 0.85f)
            }
        }
        val isL = c.light[j * c.m + i]
        for (ox in floatArrayOf(-11f, 11f)) for (oy in floatArrayOf(-11f, 11f)) {
            val px = x + ox
            val py = y + oy
            if (px < 4f || px > c.w - 4f || py < 4f || py > c.h - 4f) continue
            if (isL) light(px, py) else stopSign(px, py)
        }
    }
}

private fun DrawScope.glow(cx: Float, cy: Float, p: Float, k: Float) {
    val blue = Color(0xFF3FA2FF)
    for (g in 0..3) {
        val d = g * 3.5f
        box(blue, cx - 13f - d, cy - 13f - d, 26f + 2f * d, 26f + 2f * d, 10f + 3f * g, (0.30f - 0.06f * g) * (0.4f + 0.6f * k))
    }
    drawCircle(Color(0xFF6CB8FF), 12f + 12f * p, Offset(cx, cy), alpha = 0.8f * (1f - p), style = Stroke(2f))
    box(blue, cx - 11f, cy - 12f, 22f, 24f, 6f, 0.05f + 0.3f * k)
}

private fun DrawScope.ambSide(x: Float, y: Float, flip: Boolean) = translate(x, y) {
    scale(if (flip) -0.6f else 0.6f, 0.6f, Offset.Zero) {
        val red = Color(0xFFE53935)
        val top = Color(0xFFEF5350)
        val dark = Color(0xFFB71C1C)
        drawOval(Color.Black, Offset(-24f, 8f), Size(52f, 9f), alpha = 0.2f)
        poly(red, 1f, -22f, -8f, 6f, -8f, 6f, 6f, -22f, 6f)
        poly(top, 1f, -22f, -8f, 6f, -8f, 11f, -14f, -17f, -14f)
        poly(dark, 1f, 6f, -8f, 11f, -14f, 11f, 0f, 6f, 6f)
        poly(Color.White, 0.8f, -22f, 3f, 6f, 3f, 6f, 5f, -22f, 5f)
        poly(red, 1f, 6f, -3f, 16f, -3f, 22f, 1f, 22f, 6f, 6f, 6f)
        poly(top, 1f, 6f, -3f, 16f, -3f, 19f, -6f, 9f, -6f)
        poly(Color(0xFFBDE7FF), 1f, 8f, -2f, 15f, -2f, 19f, 1f, 8f, 1f)
        poly(dark, 1f, 22f, 1f, 24f, -1f, 24f, 6f, 22f, 6f)
        drawCircle(Color(0xFF263238), 5f, Offset(-12f, 7f))
        drawCircle(Color(0xFFB0BEC5), 2.2f, Offset(-12f, 7f))
        drawCircle(Color(0xFF263238), 5f, Offset(14f, 7f))
        drawCircle(Color(0xFFB0BEC5), 2.2f, Offset(14f, 7f))
        box(Color.White, -12f, -2.5f, 8f, 3f, 0.5f)
        box(Color.White, -9.5f, -5.5f, 3f, 9f, 0.5f)
        box(Color.White, -11.5f, -11f, 6f, 3f, 1f)
        box(Color(0xFFFF1744), -10.8f, -10.5f, 4.6f, 2f, 0.8f)
        box(Color.White, -8.5f, -14f, 6f, 3f, 1f)
        box(Color(0xFFFF1744), -7.8f, -13.5f, 4.6f, 2f, 0.8f)
        drawCircle(Color(0xFFFFEB3B), 1.5f, Offset(23f, 2f))
    }
}

private fun DrawScope.ambFront(x: Float, y: Float) = translate(x, y) {
    scale(0.6f, Offset.Zero) {
        val red = Color(0xFFE53935)
        drawOval(Color.Black, Offset(-14f, 9f), Size(28f, 7f), alpha = 0.2f)
        box(Color(0xFF263238), -15f, 5f, 4f, 9f, 1.5f)
        box(Color(0xFF263238), 11f, 5f, 4f, 9f, 1.5f)
        drawCircle(Color(0xFFB0BEC5), 1.3f, Offset(-15.2f, 9.5f))
        drawCircle(Color(0xFFB0BEC5), 1.3f, Offset(15.2f, 9.5f))
        poly(Color(0xFFEF5350), 1f, -10f, -17f, 10f, -17f, 12f, -12f, -12f, -12f)
        poly(red, 1f, -12f, -12f, 12f, -12f, 12f, -1f, -12f, -1f)
        poly(Color.White, 0.8f, -12f, -6f, 12f, -6f, 12f, -4f, -12f, -4f)
        poly(Color(0xFFBDE7FF), 1f, -9f, 0f, 9f, 0f, 10f, 5f, -10f, 5f)
        poly(red, 1f, -11f, 5f, 11f, 5f, 11f, 11f, -11f, 11f)
        box(Color(0xFF37474F), -5f, 7f, 10f, 3f, 1f)
        drawCircle(Color(0xFFFFEB3B), 1.8f, Offset(-8f, 8.5f))
        drawCircle(Color(0xFFFFEB3B), 1.8f, Offset(8f, 8.5f))
        box(Color(0xFFB0BEC5), -12f, 11f, 24f, 2.5f, 1f)
        box(Color.White, -7f, -16.5f, 5f, 3f, 1f)
        box(Color(0xFFFF1744), -6.5f, -16f, 4f, 2f, 0.8f)
        box(Color.White, 2f, -16.5f, 5f, 3f, 1f)
        box(Color(0xFFFF1744), 2.5f, -16f, 4f, 2f, 0.8f)
    }
}

private fun DrawScope.drawDynamic(rs: RouteState, p: Float) {
    val c = rs.city
    val k = if (p < 0.5f) p * 2f else (1f - p) * 2f
    if (rs.stage == 0) glow(c.slotX(c.call), c.slotY(c.call), p, k)
    if (rs.started) {
        val route = Path()
        val sp = rs.startPt()
        route.moveTo(sp.x, sp.y)
        for (n in rs.path) route.lineTo(c.nodeX(n % c.m), c.nodeY(n / c.m))
        if (rs.done) {
            val ep = rs.endPt()
            route.lineTo(ep.x, ep.y)
        }
        val rst = { w: Float -> Stroke(w, cap = StrokeCap.Round, join = StrokeJoin.Round) }
        drawPath(route, Color(0xFF2E7BFF), alpha = 0.10f, style = rst(9f))
        translate(0f, 2.2f) { drawPath(route, Color(0xFF0E2A78), alpha = 0.55f, style = rst(6f)) }
        drawPath(route, Color(0xFF2E7BFF), style = rst(6f))
        translate(0f, -1.3f) { drawPath(route, Color(0xFFBFE0FF), style = rst(2f)) }
    }
    val s = rs.startSlot()
    val ax = c.accX(s)
    val ay = c.accY(s)
    if (s.corner == 1 || s.corner == 2) {
        val west = c.owH[if (s.corner == 1) s.br else s.br + 1] == 2
        ambSide(ax + 16f, ay + 4f, west)
    } else {
        ambFront(ax + 4f, ay - 16f)
    }
}
