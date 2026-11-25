package com.cpd360.ambulancerun

import androidx.compose.animation.core.FloatExponentialDecaySpec
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateDecay
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.TransformOrigin
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
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

// Night city: wet asphalt, concrete sidewalks, rooftops seen from above
private val Road = Color(0xFF34373C)
private val Asphalt = Color(0xFF3A3D42)
private val Walk = Color(0xFF8C8A84)
private val WalkEdge = Color(0xFFB5B1A6)
private val Lane = Color(0xFFE8E2C8)
private val Lamp = Color(0xFFFFD27A)
private val houseRoofs = listOf(Color(0xFF7A4B3A), Color(0xFF5D5650), Color(0xFF8A6A4A), Color(0xFF545B63))
private val towerRoofs = listOf(Color(0xFF5F6670), Color(0xFF6D6A63), Color(0xFF575E68))
private val factoryRoofs = listOf(Color(0xFF7B7F86), Color(0xFF8E8A7E), Color(0xFF6C7078))
private val carCols = listOf(Color(0xFFC0392B), Color(0xFFECF0F1), Color(0xFF2C3E50), Color(0xFF7F8C8D), Color(0xFF2E86DE), Color(0xFF151515))

// A small repeatable number from a position, so every city keeps its look between frames
private fun hsh(a: Int, b: Int): Int {
    var h = a * 73856093 xor b * 19349663
    h = h xor (h ushr 13)
    return (h * 1274126177) and 0x7FFFFFFF
}

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
    var lastTapT by remember { mutableLongStateOf(0L) }
    var lastTapP by remember { mutableStateOf(Offset.Zero) }
    // Where the finger is while drawing a route, so a lighter lead line can follow it
    var lead by remember { mutableStateOf<Pair<Offset, Offset>?>(null) }
    val scope = rememberCoroutineScope()
    val fling = remember { arrayOfNulls<Job>(1) }
    // The city only needs painting again when the run changes or zoom crosses a detail step
    val tier by remember(city) {
        derivedStateOf {
            val sc = if (sz.width > 0) sz.width / city.w * zm else 1f
            if (sc >= 2f) 2 else if (sc >= 1.5f) 1 else 0
        }
    }
    LaunchedEffect(rs.version, rs.stage, sz) {
        fling[0]?.cancel()
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
        fling[0]?.cancel()
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
            .background(Asphalt)
            .onSizeChanged { sz = it }
            .pointerInput(Unit) {
                awaitEachGesture {
                    val cty = rs.city
                    val w = size.width.toFloat()
                    val h = size.height.toFloat()
                    val s0 = w / cty.w
                    val down = awaitFirstDown(requireUnconsumed = false)
                    // A new touch catches the map if it is still gliding
                    fling[0]?.cancel()
                    val m0 = (down.position - mapOrigin(cty, w, h, s0, zm, pan)) / (s0 * zm)
                    val last = if (rs.path.isEmpty()) rs.startPt() else Offset(
                        cty.nodeX(rs.path.last() % cty.m), cty.nodeY(rs.path.last() / cty.m)
                    )
                    var tracing = !rs.done && (m0 - last).getDistance() < 34f
                    if (tracing) rs.begin()
                    var moved = false
                    var lastT = down.uptimeMillis
                    val vt = VelocityTracker()
                    var flingOk = false
                    do {
                        val ev = awaitPointerEvent()
                        lastT = ev.changes[0].uptimeMillis
                        val pressed = ev.changes.count { it.pressed }
                        if (pressed >= 2) {
                            tracing = false
                            lead = null
                            moved = true
                            flingOk = false
                            vt.resetTracking()
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
                            if ((chg.position - down.position).getDistance() > 12f) moved = true
                            if (tracing) {
                                val mp = (chg.position - mapOrigin(cty, w, h, s0, zm, pan)) / (s0 * zm)
                                lead = traceTo(rs, mp)
                            } else {
                                pan = clampPan(pan + chg.positionChange(), cty, w, h, s0, zm)
                                vt.addPosition(chg.uptimeMillis, chg.position)
                                flingOk = true
                            }
                            chg.consume()
                        }
                    } while (ev.changes.any { it.pressed })
                    lead = null
                    // A flick keeps the map gliding, slowing to a stop
                    if (flingOk && moved) {
                        val v = vt.calculateVelocity()
                        val speed = hypot(v.x, v.y)
                        if (speed > 300f) {
                            val dir = Offset(v.x / speed, v.y / speed)
                            fling[0] = scope.launch {
                                var prev = 0f
                                animateDecay(0f, speed, FloatExponentialDecaySpec(1.6f)) { value, _ ->
                                    val np = clampPan(pan + dir * (value - prev), cty, w, h, s0, zm)
                                    prev = value
                                    if (np == pan) throw CancellationException()
                                    pan = np
                                }
                            }
                        }
                    }
                    if (!moved && lastT - down.uptimeMillis < 300L) {
                        if (lastT - lastTapT < 350L && (down.position - lastTapP).getDistance() < 60f) {
                            val tp = (down.position - mapOrigin(cty, w, h, s0, zm, pan)) / (s0 * zm)
                            for (hh in cty.hospitals) {
                                if (hypot(tp.x - cty.slotX(hh.slot), tp.y - cty.slotY(hh.slot)) < 16f) {
                                    rs.showHospital(hh)
                                    break
                                }
                            }
                            lastTapT = 0L
                        } else {
                            lastTapT = lastT
                            lastTapP = down.position
                        }
                    }
                }
            }
    ) {
        // The city is painted once into its own layer, then only slid and scaled while you pan and zoom.
        // It is painted again only for a new run or when zoom crosses a detail step.
        Canvas(
            Modifier.fillMaxSize().graphicsLayer {
                val s0 = size.width / city.w
                val o = mapOrigin(city, size.width, size.height, s0, zm, pan)
                transformOrigin = TransformOrigin(0f, 0f)
                translationX = o.x
                translationY = o.y
                scaleX = s0 * zm
                scaleY = s0 * zm
            }
        ) {
            rs.version
            drawStatic(city, when (tier) { 2 -> 2f; 1 -> 1.5f; else -> 1f })
        }
        // The route is built once each time it changes, then only drawn each frame for the pulse
        Box(Modifier.fillMaxSize().drawWithCache {
            val rd = buildRoute(rs)
            onDrawBehind {
                val s0 = size.width / city.w
                val o = mapOrigin(city, size.width, size.height, s0, zm, pan)
                translate(o.x, o.y) {
                    scale(s0 * zm, Offset.Zero) {
                        drawDynamic(rs, p, rd)
                        val ld = lead
                        if (ld != null) leadLine(ld.first, ld.second)
                    }
                }
            }
        })
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

// Ground inside a block: lawns for homes, paved lots downtown and in the factory district
private fun zoneGround(z: Int, d: Int): Color {
    val base = when (z) { 0 -> Color(0xFF3E5F2E); 1 -> Color(0xFF5A5852); else -> Color(0xFF55585E) }
    return lerp(base, Color(0xFF1E1F22), 0.08f * d)
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

// Rooftops seen from above. Each casts a soft shadow down and to the right.
private fun DrawScope.roofShadow(x: Float, y: Float, w: Float, h: Float, off: Float) {
    for (g in 0..2) {
        val e = g * 1.2f
        box(Color.Black, x + off - e, y + off - e, w + 2f * e, h + 2f * e, 2f + e, 0.16f)
    }
}

private fun DrawScope.house(cx: Float, cy: Float, s: Float, v: Int) {
    val w = 30f * s
    val x = cx - w / 2f
    val y = cy - w / 2f
    roofShadow(x, y, w, w, 3f)
    val roof = houseRoofs[v % houseRoofs.size]
    box(roof, x, y, w, w, 1.5f)
    // Pitched roof: the lit half and the ridge line
    box(Color.White, x, y, w, w / 2f, 1.5f, 0.09f)
    box(Color.Black, x, y + w / 2f, w, w / 2f, 1.5f, 0.12f)
    drawLine(Color.Black, Offset(x, cy), Offset(x + w, cy), 0.8f, alpha = 0.4f)
    if (v % 3 == 0) box(Color(0xFF2A2018), x + w * 0.66f, y + w * 0.12f, w * 0.14f, w * 0.14f, 0.5f)
}

private fun DrawScope.tower(cx: Float, cy: Float, s: Float, v: Int) {
    val w = 34f * s
    val x = cx - w / 2f
    val y = cy - w / 2f
    roofShadow(x, y, w, w, 6f)
    box(towerRoofs[v % towerRoofs.size], x, y, w, w, 1.5f)
    drawRoundRect(Color(0xFFC9C6BD), Offset(x + 1f, y + 1f), Size(w - 2f, w - 2f), CornerRadius(1f), alpha = 0.5f, style = Stroke(0.9f))
    // Air units on the roof
    for (k in 0..1) {
        val ax = x + 3f + ((v * 7 + k * 11) % 10) / 10f * (w - 11f)
        val ay = y + 3f + ((v * 5 + k * 13) % 10) / 10f * (w - 10f)
        box(Color.Black, ax + 1.2f, ay + 1.2f, 5f, 4.4f, 0.5f, 0.5f)
        box(Color(0xFFB9BCC2), ax, ay, 5f, 4.4f, 0.5f)
        drawCircle(Color(0xFF6D7076), 1.4f, Offset(ax + 2.5f, ay + 2.2f))
    }
    // A red beacon on the tallest ones
    if (v % 2 == 0) {
        drawCircle(Color(0xFFFF3B3B), 3.5f, Offset(x + w - 3f, y + 3f), alpha = 0.25f)
        drawCircle(Color(0xFFFF5A52), 1.2f, Offset(x + w - 3f, y + 3f))
    }
}

private fun DrawScope.factory(cx: Float, cy: Float, s: Float, v: Int) {
    val w = 36f * s
    val h = 28f * s
    val x = cx - w / 2f
    val y = cy - h / 2f
    roofShadow(x, y, w, h, 4f)
    box(factoryRoofs[v % factoryRoofs.size], x, y, w, h, 1f)
    // Saw-tooth roof stripes
    for (k in 1..3) drawLine(Color.Black, Offset(x + w * k / 4f, y), Offset(x + w * k / 4f, y + h), 0.8f, alpha = 0.3f)
    box(Color.White, x, y, w, 2f, 1f, 0.12f)
    // Chimney with a little smoke
    drawCircle(Color.Black, 3.4f, Offset(x + w - 3f, y + 4.5f), alpha = 0.45f)
    drawCircle(Color(0xFF8D6E63), 2.6f, Offset(x + w - 4f, y + 3.5f))
    drawCircle(Color(0xFF2B2B2B), 1.4f, Offset(x + w - 4f, y + 3.5f))
    drawCircle(Color(0xFFCFD2D8), 4f, Offset(x + w + 1f, y - 1f), alpha = 0.18f)
}

// Hospital roof with a helipad and a glowing red light
private fun DrawScope.hospital(cx: Float, cy: Float) = translate(cx, cy) {
    roofShadow(-11f, -10f, 22f, 20f, 4f)
    box(Color(0xFFD9DBE0), -11f, -10f, 22f, 20f, 1.5f)
    box(Color.Black, -11f, 3f, 22f, 7f, 1.5f, 0.08f)
    val y = Color(0xFFF2C230)
    drawCircle(y, 7f, Offset(0f, 0f), style = Stroke(1.1f))
    box(y, -3.6f, -4f, 1.6f, 8f)
    box(y, 2f, -4f, 1.6f, 8f)
    box(y, -3.6f, -0.8f, 7.2f, 1.6f)
    drawCircle(Color(0xFFFF3030), 4f, Offset(8f, -7f), alpha = 0.3f)
    box(Color(0xFFE53935), 6.5f, -8.5f, 3f, 3f, 0.6f)
}

// A tree seen from above
private fun DrawScope.tree(x: Float, y: Float, r: Float) {
    drawCircle(Color.Black, r, Offset(x + 2f, y + 2.5f), alpha = 0.45f)
    drawCircle(
        Brush.radialGradient(
            listOf(Color(0xFF4C7A3A), Color(0xFF2C5222), Color(0xFF17331A)),
            center = Offset(x - r * 0.3f, y - r * 0.4f), radius = r * 1.5f
        ),
        r, Offset(x, y)
    )
}

// A parked car along the curb
private fun DrawScope.car(x: Float, y: Float, vert: Boolean, c: Color) {
    val w = if (vert) 4.5f else 10f
    val h = if (vert) 10f else 4.5f
    box(c, x, y, w, h, 1.5f)
    box(Color(0xFF1B2A3A), x + (if (vert) 0.8f else 3f), y + (if (vert) 3f else 0.8f), if (vert) 3f else 3.5f, if (vert) 3.5f else 3f, 0.4f, 0.75f)
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


private fun DrawScope.starOfLife(cx: Float, cy: Float, r: Float) {
    val blue = Color(0xFF0A72B8)
    val bw = r * 0.51f
    for (a in floatArrayOf(0f, 60f, 120f)) {
        rotate(a, Offset(cx, cy)) {
            drawRect(blue, Offset(cx - bw / 2f, cy - r), Size(bw, 2f * r))
        }
    }
    drawRoundRect(
        Color.White, Offset(cx - r * 0.07f, cy - r * 0.85f), Size(r * 0.14f, r * 1.7f),
        CornerRadius(r * 0.07f, r * 0.07f)
    )
    val sn = Path()
    sn.moveTo(cx - r * 0.2f, cy - r * 0.6f)
    sn.cubicTo(cx + r * 0.4f, cy - r * 0.5f, cx + r * 0.4f, cy - r * 0.1f, cx, cy - r * 0.05f)
    sn.cubicTo(cx - r * 0.4f, cy, cx - r * 0.4f, cy + r * 0.4f, cx + r * 0.2f, cy + r * 0.5f)
    drawPath(sn, Color.White, style = Stroke(r * 0.12f, cap = StrokeCap.Round, join = StrokeJoin.Round))
}

private fun DrawScope.station(cx: Float, cy: Float) = translate(cx, cy) {
    scale(0.55f, Offset.Zero) {
        drawOval(Color.Black, Offset(-18f, 14f), Size(36f, 8f), alpha = 0.15f)
        box(Color(0xFFB71C1C), -17f, -15f, 34f, 34f, 6f)
        box(Color(0xFFE53935), -17f, -17f, 34f, 34f, 6f)
        box(Color.White, -17f, -17f, 34f, 7f, 3f, 0.3f)
        box(Color(0xFFB0BEC5), -9f, 8f, 18f, 9f, 2f)
        drawCircle(Color.White, 11.5f, Offset(0f, -2f))
        starOfLife(0f, -2f, 9.5f)
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
    box(Asphalt, 0f, 0f, c.w, c.h)
    for (j in 0..n) box(Road, 0f, c.nodeY(j) - 8f, c.w, 16f)
    for (i in 0..n) box(Road, c.nodeX(i) - 8f, 12f, 16f, c.h - 24f)
    val dash = PathEffect.dashPathEffect(floatArrayOf(8f, 6f))
    val yellow = Lane
    val pot = Color(0xFF1E2024)
    for (j in 0..n) for (b in 0 until n) {
        val a = 27f + 80f * b
        val y = c.nodeY(j)
        drawLine(if (c.owH[j] != 0) Color.White else yellow, Offset(a, y), Offset(a + 64f, y), 1.4f, alpha = 0.7f, pathEffect = dash)
        if (c.qH[j * n + b] == 2) {
            drawOval(pot, Offset(a + 16f, y - 6f), Size(9f, 4f), alpha = 0.55f)
            drawOval(pot, Offset(a + 38f, y + 3f), Size(9f, 4f), alpha = 0.55f)
        }
    }
    for (i in 0..n) for (b in 0 until n) {
        val a = 28f + 80f * b
        val x = c.nodeX(i)
        drawLine(if (c.owV[i] != 0) Color.White else yellow, Offset(x, a), Offset(x, a + 64f), 1.4f, alpha = 0.7f, pathEffect = dash)
        if (c.qV[i * n + b] == 2) {
            drawOval(pot, Offset(x - 6f, a + 16f), Size(4f, 9f), alpha = 0.55f)
            drawOval(pot, Offset(x + 3f, a + 38f), Size(4f, 9f), alpha = 0.55f)
        }
    }
    val cellX = floatArrayOf(18f, 46f, 18f, 46f)
    val cellY = floatArrayOf(18f, 18f, 46f, 46f)
    val dw = Color(0xFF6F6D68)
    val ap = Color(0xFF9C9A93)
    val door = Color(0xFF2A2018)
    for (r in 0 until n) for (cc in 0 until n) {
        val bx = 27f + 80f * cc
        val by = 28f + 80f * r
        val z = c.zone[r * n + cc]
        val d = c.dens[r * n + cc]
        box(Walk, bx + 2f, by + 2f, 60f, 60f, 3f)
        drawRoundRect(WalkEdge, Offset(bx + 2f, by + 2f), Size(60f, 60f), CornerRadius(3f), style = Stroke(0.8f))
        box(zoneGround(z, d), bx + 5f, by + 5f, 54f, 54f, 1.5f)
        if (detail && z == 0) tree(bx + 32f, by + 32f, 4.5f)
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
                val v = hsh(key, d)
                when (z) {
                    0 -> house(cx, cy, 0.62f, v)
                    1 -> tower(cx, cy, 0.62f, v)
                    else -> factory(cx, cy, 0.62f, v)
                }
            } else {
                val v = hsh(key, d)
                box(when (z) { 0 -> houseRoofs[v % 4]; 1 -> towerRoofs[v % 3]; else -> factoryRoofs[v % 3] }, cx - 9f, cy - 9f, 18f, 18f, 1.5f)
            }
        }
    }
    if (detail) {
        // Cars parked along some curbs
        for (r in 0 until n) for (cc in 0 until n) {
            val v = hsh(r * 31 + 7, cc * 17 + 3)
            if (v % 3 == 0) continue
            val bx = 27f + 80f * cc
            val by = 28f + 80f * r
            val col = carCols[v % carCols.size]
            when (v % 4) {
                0 -> car(bx + 10f + (v % 30), by - 6f, false, col)
                1 -> car(bx + 66f, by + 10f + (v % 30), true, col)
                2 -> car(bx + 14f + (v % 26), by + 66f, false, col)
                else -> car(bx - 6.5f, by + 12f + (v % 28), true, col)
            }
        }
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
        // Street lamps light every few corners
        if ((i * 3 + j) % 4 == 0) {
            drawCircle(
                Brush.radialGradient(listOf(Lamp.copy(alpha = 0.22f), Lamp.copy(alpha = 0f)), Offset(x, y), 16f),
                16f, Offset(x, y)
            )
        }
    }
    drawEvents(c)
    // Wet night sheen over the whole city
    box(Color(0xFF3B6EA5), 0f, 0f, c.w, c.h, 0f, 0.12f)
}

// Road events on the map: rush hour roads in red, the school zone in yellow, the train tracks,
// cones on the closed block, and rain for a storm
private fun DrawScope.drawEvents(c: CityData) {
    val n = c.n
    val m = c.m
    if (c.rushRow >= 0) {
        box(Color(0xFFE53935), 0f, c.nodeY(c.rushRow) - 8f, c.w, 16f, 0f, 0.35f)
        box(Color(0xFFE53935), c.nodeX(c.rushCol) - 8f, 12f, 16f, c.h - 24f, 0f, 0.35f)
    }
    if (c.schoolBlock >= 0) {
        val bc = c.schoolBlock % n
        val br = c.schoolBlock / n
        val x0 = c.nodeX(bc)
        val y0 = c.nodeY(br)
        val yellow = Color(0xFFFFD23A)
        box(yellow, x0 - 8f, y0 - 8f, 96f, 16f, 0f, 0.35f)
        box(yellow, x0 - 8f, y0 + 72f, 96f, 16f, 0f, 0.35f)
        box(yellow, x0 - 8f, y0 - 8f, 16f, 96f, 0f, 0.35f)
        box(yellow, x0 + 72f, y0 - 8f, 16f, 96f, 0f, 0.35f)
        // School sign in the middle of the block
        drawRoundRect(yellow, Offset(x0 + 28f, y0 + 30f), Size(24f, 20f), CornerRadius(3f))
        drawRoundRect(Color(0xFF3A2E00), Offset(x0 + 28f, y0 + 30f), Size(24f, 20f), CornerRadius(3f), style = Stroke(1.5f))
        drawCircle(Color(0xFF3A2E00), 2.5f, Offset(x0 + 36f, y0 + 36f))
        drawCircle(Color(0xFF3A2E00), 2.5f, Offset(x0 + 44f, y0 + 36f))
        box(Color(0xFF3A2E00), x0 + 34f, y0 + 40f, 4f, 7f)
        box(Color(0xFF3A2E00), x0 + 42f, y0 + 40f, 4f, 7f)
    }
    if (c.trainRow >= 0) {
        val y = c.nodeY(c.trainRow) + 40f
        val tie = Color(0xFF5A4636)
        var x = 2f
        while (x < c.w) {
            box(tie, x, y - 6f, 3f, 12f)
            x += 7f
        }
        drawLine(Color(0xFFA9A39A), Offset(0f, y - 3.5f), Offset(c.w, y - 3.5f), 1.6f)
        drawLine(Color(0xFFA9A39A), Offset(0f, y + 3.5f), Offset(c.w, y + 3.5f), 1.6f)
        // Red and white gates where each street crosses
        for (i in 0..n) {
            val gx = c.nodeX(i)
            for (k in 0..3) box(if (k % 2 == 0) Color(0xFFE53935) else Color.White, gx - 8f + k * 4f, y - 11f, 4f, 2.5f)
            for (k in 0..3) box(if (k % 2 == 0) Color(0xFFE53935) else Color.White, gx - 8f + k * 4f, y + 8.5f, 4f, 2.5f)
        }
    }
    if (c.workA >= 0) {
        val ax = c.nodeX(c.workA % m)
        val ay = c.nodeY(c.workA / m)
        val bx = c.nodeX(c.workB % m)
        val by = c.nodeY(c.workB / m)
        val orange = Color(0xFFFF8A1C)
        drawLine(orange, Offset(ax, ay), Offset(bx, by), 14f, alpha = 0.45f)
        // Cones and a barrier in the middle of the block
        val mx = (ax + bx) / 2f
        val my = (ay + by) / 2f
        val horiz = ay == by
        for (k in -2..2) {
            val cx = if (horiz) mx + k * 12f else mx
            val cy = if (horiz) my else my + k * 12f
            poly(orange, 1f, cx - 3.5f, cy + 3f, cx + 3.5f, cy + 3f, cx, cy - 5f)
            box(Color.White, cx - 2f, cy - 1f, 4f, 1.4f)
        }
        if (horiz) for (k in 0..4) box(if (k % 2 == 0) orange else Color.White, mx - 10f + k * 4f, my - 9f, 4f, 3f)
        else for (k in 0..4) box(if (k % 2 == 0) orange else Color.White, mx + 6f, my - 10f + k * 4f, 3f, 4f)
    }
    if (c.events.contains(EV_STORM)) {
        box(Color(0xFF1A2A48), 0f, 0f, c.w, c.h, 0f, 0.25f)
        val rain = Color(0xFF9FC4FF)
        var k = 0
        while (k < (c.w * c.h / 900f).toInt()) {
            val x = ((k * 53) % c.w.toInt()).toFloat()
            val y = ((k * 97) % c.h.toInt()).toFloat()
            drawLine(rain, Offset(x, y), Offset(x - 3f, y + 10f), 1f, alpha = 0.35f)
            k++
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

private val OutC = Color(0xFF111111)

private fun DrawScope.polyO(c: Color, vararg p: Float) {
    val path = Path()
    path.moveTo(p[0], p[1])
    var i = 2
    while (i < p.size) {
        path.lineTo(p[i], p[i + 1])
        i += 2
    }
    path.close()
    drawPath(path, c)
    drawPath(path, OutC, style = Stroke(0.5f, join = StrokeJoin.Round))
}

private fun DrawScope.boxO(c: Color, x: Float, y: Float, w: Float, h: Float, r: Float) {
    drawRoundRect(c, Offset(x, y), Size(w, h), CornerRadius(r, r))
    drawRoundRect(OutC, Offset(x, y), Size(w, h), CornerRadius(r, r), style = Stroke(0.5f))
}

private fun DrawScope.circO(c: Color, r: Float, x: Float, y: Float) {
    drawCircle(c, r, Offset(x, y))
    drawCircle(OutC, r, Offset(x, y), style = Stroke(0.5f))
}

private fun DrawScope.ambSide(x: Float, y: Float, flip: Boolean) = translate(x, y) {
    scale(if (flip) -0.55f else 0.55f, 0.55f, Offset.Zero) {
        translate(-26f, -13f) {
            val red = Color(0xFFE81010)
            val top = Color(0xFFF25555)
            val dark = Color(0xFFB00C0C)
            val grey = Color(0xFFD0D0D0)
            val tyre = Color(0xFF3B3838)
            drawOval(Color.Black, Offset(3f, 23.8f), Size(46f, 4.2f), alpha = 0.18f)
            polyO(grey, 0.4f, 19f, 2.4f, 19f, 2.4f, 20.2f, 6.4f, 20.2f, 6.4f, 21.8f, 0.4f, 21.8f)
            polyO(top, 1.2f, 1.6f, 34.7f, 1.6f, 37.7f, -2.2f, 4.2f, -2.2f)
            boxO(red, 1.2f, 1.6f, 33.5f, 19.8f, 1.6f)
            polyO(dark, 34.7f, 1.6f, 37.7f, -2.2f, 37.7f, 3.1f, 34.7f, 6.7f)
            polyO(top, 34.7f, 6.7f, 39.2f, 6.8f, 42.2f, 3.1f, 37.7f, 3.1f)
            polyO(red, 34.7f, 6.7f, 39.2f, 6.8f, 40.4f, 7.5f, 44.7f, 12.1f, 48.5f, 13f, 50.9f, 14f, 51.6f, 15.4f, 51.4f, 21.3f, 34.7f, 21.3f)
            polyO(Color(0xFF7FD3FF), 35.7f, 8.1f, 39.3f, 8.1f, 43.1f, 12.2f, 43.1f, 13.2f, 35.7f, 13.2f)
            poly(Color.White, 0.5f, 36f, 12.9f, 37f, 12.9f, 39.9f, 8.6f, 39f, 8.6f)
            boxO(Color(0xFF3A3A3A), 40.9f, 11f, 1.9f, 3f, 0.6f)
            boxO(Color(0xFF3A3A3A), 35.4f, 14.4f, 2f, 1.1f, 0.4f)
            boxO(Color.White, 49.4f, 14.6f, 2.1f, 2.8f, 0.5f)
            box(Color(0xFFFFC94D), 49.5f, 14.7f, 1f, 2.6f, 0.3f)
            boxO(grey, 49.1f, 18.2f, 2.8f, 3.1f, 0.6f)
            for (cx in floatArrayOf(11.1f, 44.7f)) {
                drawArc(tyre, 180f, 180f, true, Offset(cx - 5f, 16.6f), Size(10f, 10f))
                drawArc(OutC, 180f, 180f, false, Offset(cx - 5f, 16.6f), Size(10f, 10f), style = Stroke(0.5f))
                circO(Color(0xFF413C3C), 4.1f, cx, 21.6f)
                circO(grey, 2.1f, cx, 21.6f)
                circO(Color(0xFFBDBDBD), 0.9f, cx, 21.6f)
            }
            box(Color.White, 15.4f, 6f, 3.4f, 9.1f, 0.3f)
            box(Color.White, 12.2f, 9f, 9.9f, 3.1f, 0.3f)
            boxO(Color(0xFFFF2A2A), 2.3f, 2.7f, 3.3f, 2.5f, 0.6f)
            boxO(Color(0xFFF5F5F5), 6.1f, 2.7f, 3.3f, 2.5f, 0.6f)
            boxO(Color(0xFFF5F5F5), 26.4f, 2.7f, 3.3f, 2.5f, 0.6f)
            boxO(Color(0xFFFF2A2A), 30.2f, 2.7f, 3.4f, 2.5f, 0.6f)
            boxO(Color(0xFFFF1744), 18.5f, 0.1f, 3f, 1.3f, 0.4f)
            boxO(Color(0xFFFF1744), 20.4f, -1.9f, 3f, 1.3f, 0.4f)
        }
    }
}

private fun DrawScope.ambFront(x: Float, y: Float) = translate(x, y) {
    scale(0.55f, Offset.Zero) {
        val red = Color(0xFFE81010)
        val grey = Color(0xFFD0D0D0)
        drawOval(Color.Black, Offset(-13f, 12f), Size(26f, 4.5f), alpha = 0.18f)
        boxO(Color(0xFF413C3C), -14.5f, 6f, 3f, 9f, 1f)
        boxO(Color(0xFF413C3C), 11.5f, 6f, 3f, 9f, 1f)
        circO(grey, 0.9f, -13f, 10.5f)
        circO(grey, 0.9f, 13f, 10.5f)
        polyO(Color(0xFFF25555), -10f, -18f, 10f, -18f, 12f, -13f, -12f, -13f)
        polyO(red, -12f, -13f, 12f, -13f, 12f, -1f, -12f, -1f)
        polyO(Color(0xFF7FD3FF), -9f, 0f, 9f, 0f, 10f, 5f, -10f, 5f)
        poly(Color.White, 0.5f, -6f, 4.6f, -4.6f, 4.6f, -1.8f, 0.4f, -3.2f, 0.4f)
        polyO(red, -11f, 5f, 11f, 5f, 11f, 12f, -11f, 12f)
        boxO(Color(0xFF37474F), -5f, 7.2f, 10f, 3f, 1f)
        circO(Color(0xFFFFC94D), 1.9f, -8f, 8.6f)
        circO(Color(0xFFFFC94D), 1.9f, 8f, 8.6f)
        boxO(grey, -12f, 11.5f, 24f, 2.8f, 1f)
        boxO(Color(0xFFFF1744), -6.5f, -17f, 4.5f, 2.4f, 0.8f)
        boxO(Color(0xFFFF1744), 2f, -17f, 4.5f, 2.4f, 0.8f)
    }
}

// One route line: soft glow, drop shadow, main stroke and a thin highlight
private fun DrawScope.routeLine(path: Path, main: Color, shade: Color, hi: Color) {
    // Turns get a gentle curve instead of a sharp corner
    val bend = PathEffect.cornerPathEffect(14f)
    val rst = { w: Float -> Stroke(w, cap = StrokeCap.Round, join = StrokeJoin.Round, pathEffect = bend) }
    drawPath(path, main, alpha = 0.10f, style = rst(9f))
    translate(0f, 2.2f) { drawPath(path, shade, alpha = 0.55f, style = rst(6f)) }
    drawPath(path, main, style = rst(6f))
    translate(0f, -1.3f) { drawPath(path, hi, style = rst(2f)) }
}

// The lighter line that runs down the road from the last corner to the finger
private fun DrawScope.leadLine(a: Offset, b: Offset) {
    drawLine(Color(0xFF7FB0FF), a, b, 9f, StrokeCap.Round, alpha = 0.10f)
    drawLine(Color(0xFF7FB0FF), a, b, 5f, StrokeCap.Round, alpha = 0.75f)
}

// Grows or shrinks the route as the finger moves along the roads.
// The next corner locks in once the finger is past halfway down the block, and a fast swipe
// fills in every corner it crossed. Going back to within 32 of the corner before takes the last one off.
// Returns the lead line to draw (from, to), or null when there is none.
private fun traceTo(rs: RouteState, mp: Offset): Pair<Offset, Offset>? {
    val c = rs.city
    val pt = { u: Int -> Offset(c.nodeX(u % c.m), c.nodeY(u / c.m)) }
    for (guard in 0 until 64) {
        if (rs.done) return null
        if (rs.path.isEmpty()) {
            val sp = rs.startPt()
            val d = mp - sp
            var best = -1
            var bt = 0f
            var bd = 28f
            for (s in rs.starts()) {
                val e = pt(s) - sp
                val len = e.getDistance()
                if (len < 1f) continue
                val ux = e.x / len
                val uy = e.y / len
                val along = d.x * ux + d.y * uy
                val perp = abs(d.x * uy - d.y * ux)
                if (along > 0f && perp < bd) {
                    bd = perp
                    best = s
                    bt = along / len
                }
            }
            if (best < 0) return null
            if (bt < 0.5f) return sp to (sp + (pt(best) - sp) * bt)
            rs.move(best)
            if (rs.path.isEmpty()) return null
            continue
        }
        val ln = rs.path.last()
        val lp = pt(ln)
        val d = mp - lp
        val horiz = abs(d.x) >= abs(d.y)
        val along = if (horiz) d.x else d.y
        val perp = abs(if (horiz) d.y else d.x)
        if (perp > 28f || abs(along) < 2f) return null
        val step = if (along > 0f) 1 else -1
        val ni = ln % c.m + (if (horiz) step else 0)
        val nj = ln / c.m + (if (horiz) 0 else step)
        if (ni < 0 || ni > c.n || nj < 0 || nj > c.n) return null
        val nn = nj * c.m + ni
        val back = rs.path.size > 1 && nn == rs.path[rs.path.size - 2]
        if (back) {
            if (abs(along) < 48f) return null
            rs.move(nn)
            continue
        }
        if (!c.open(ln, nn) || rs.path.contains(nn)) return null
        if (abs(along) < 40f) return lp to (lp + (pt(nn) - lp) * (abs(along) / 80f))
        val before = rs.path.size
        rs.move(nn)
        if (rs.path.size == before) return null
    }
    return null
}

// The route lines for the map, built only when the route changes
private class RouteDraw(
    val route: Path?,
    val busy: List<Pair<Offset, Offset>>,
    val missed: Path?,
    val red: Path?,
    val crash: Offset?
)

private fun buildRoute(rs: RouteState): RouteDraw {
    val c = rs.city
    if (!rs.started) return RouteDraw(null, emptyList(), null, null, null)
    val route = Path()
    val sp = rs.startPt()
    route.moveTo(sp.x, sp.y)
    var crash: Offset? = null
    val acc = rs.accSeg
    val lim = if (acc >= 0) acc else rs.path.size
    for (q in 0 until lim) {
        val n = rs.path[q]
        route.lineTo(c.nodeX(n % c.m), c.nodeY(n / c.m))
    }
    if (acc >= 0) {
        val a = rs.path[acc - 1]
        val b = rs.path[acc]
        val mx = (c.nodeX(a % c.m) + c.nodeX(b % c.m)) / 2f
        val my = (c.nodeY(a / c.m) + c.nodeY(b / c.m)) / 2f
        route.lineTo(mx, my)
        crash = Offset(mx, my)
    } else if (rs.done) {
        val ep = rs.endPt()
        route.lineTo(ep.x, ep.y)
    }
    // Road pieces beside busy blocks get an orange glow
    val busy = ArrayList<Pair<Offset, Offset>>()
    for (q in 1 until lim) {
        val a = rs.path[q - 1]
        val b = rs.path[q]
        if (c.segMult(a, b) >= 2.0) {
            busy.add(Offset(c.nodeX(a % c.m), c.nodeY(a / c.m)) to Offset(c.nodeX(b % c.m), c.nodeY(b / c.m)))
        }
    }
    // Route check after the drive to the call: red where the route left the fastest one,
    // and the missed part of the fastest route as the same line, 80% lighter.
    val fast = rs.fastPath.toList()
    val check = rs.stage == 0 && rs.done && fast.isNotEmpty() && rs.path.isNotEmpty()
    if (!check) return RouteDraw(route, busy, null, null, crash)
    val mm = c.m * c.m
    val ek = { a: Int, b: Int -> minOf(a, b) * mm + maxOf(a, b) }
    val pt = { u: Int -> Offset(c.nodeX(u % c.m), c.nodeY(u / c.m)) }
    val fastEdges = HashSet<Int>()
    for (q in 1 until fast.size) fastEdges.add(ek(fast[q - 1], fast[q]))
    val userEdges = HashSet<Int>()
    for (q in 1 until rs.path.size) userEdges.add(ek(rs.path[q - 1], rs.path[q]))
    val missed = Path()
    val rp = Path()
    val ep = rs.endPt()
    if (fast[0] != rs.path[0]) {
        missed.moveTo(sp.x, sp.y)
        missed.lineTo(pt(fast[0]).x, pt(fast[0]).y)
        rp.moveTo(sp.x, sp.y)
        rp.lineTo(pt(rs.path[0]).x, pt(rs.path[0]).y)
    }
    for (q in 1 until fast.size) {
        if (!userEdges.contains(ek(fast[q - 1], fast[q]))) {
            missed.moveTo(pt(fast[q - 1]).x, pt(fast[q - 1]).y)
            missed.lineTo(pt(fast[q]).x, pt(fast[q]).y)
        }
    }
    if (fast.last() != rs.path.last()) {
        missed.moveTo(pt(fast.last()).x, pt(fast.last()).y)
        missed.lineTo(ep.x, ep.y)
        if (acc < 0) {
            rp.moveTo(pt(rs.path.last()).x, pt(rs.path.last()).y)
            rp.lineTo(ep.x, ep.y)
        }
    }
    for (q in 1 until lim) {
        if (!fastEdges.contains(ek(rs.path[q - 1], rs.path[q]))) {
            rp.moveTo(pt(rs.path[q - 1]).x, pt(rs.path[q - 1]).y)
            rp.lineTo(pt(rs.path[q]).x, pt(rs.path[q]).y)
        }
    }
    val cr0 = crash
    if (acc >= 1 && cr0 != null && !fastEdges.contains(ek(rs.path[acc - 1], rs.path[acc]))) {
        rp.moveTo(pt(rs.path[acc - 1]).x, pt(rs.path[acc - 1]).y)
        rp.lineTo(cr0.x, cr0.y)
    }
    return RouteDraw(route, busy, missed, rp, crash)
}

private fun DrawScope.drawDynamic(rs: RouteState, p: Float, rd: RouteDraw) {
    val c = rs.city
    val k = if (p < 0.5f) p * 2f else (1f - p) * 2f
    if (rs.stage == 0) glow(c.slotX(c.call), c.slotY(c.call), p, k)
    val route = rd.route
    if (route != null) {
        for (seg in rd.busy) drawLine(Color(0xFFFF9F1C), seg.first, seg.second, 14f, StrokeCap.Round, alpha = 0.6f)
        val blue = Color(0xFF2E7BFF)
        val blueShade = Color(0xFF0E2A78)
        val blueHi = Color(0xFFBFE0FF)
        val missed = rd.missed
        if (missed != null) {
            routeLine(missed, lerp(blue, Color.White, 0.8f), lerp(blueShade, Color.White, 0.8f), lerp(blueHi, Color.White, 0.8f))
        }
        routeLine(route, blue, blueShade, blueHi)
        val rr = rd.red
        if (rr != null) routeLine(rr, Color(0xFFE53935), Color(0xFF7A1010), Color(0xFFFFC4C4))
        val cr = rd.crash
        if (cr != null) {
            drawCircle(Color(0x66E53935), 12f, cr)
            drawCircle(Color.White, 8f, cr)
            drawCircle(Color(0xFFE53935), 6.5f, cr)
            box(Color.White, cr.x - 1f, cr.y - 4f, 2f, 5f, 0.8f)
            drawCircle(Color.White, 1.1f, Offset(cr.x, cr.y + 3f))
        }
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
