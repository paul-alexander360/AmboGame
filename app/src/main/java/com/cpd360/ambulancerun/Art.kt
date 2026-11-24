package com.cpd360.ambulancerun

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke

// Shared colors for people and the ambulance
val ArtInk = Color(0xFF2B313A)
val SkinLight = listOf(Color(0xFFFFDDBB), Color(0xFFE8B48A), Color(0xFFA86B45))
val SkinDark = listOf(Color(0xFFE39A68), Color(0xFFB87A4B), Color(0xFF6B3E26))
val HairCols = listOf(Color(0xFFFFD54F), Color(0xFF6B3E26), Color(0xFF2B2B2B), Color(0xFFC1440E))
val HairNames = listOf("Blond", "Brunette", "Black", "Red")
val UniformCols = listOf(Color(0xFF1E3A8A), Color(0xFF263238), Color(0xFFB71C1C), Color(0xFF2E7D32))
val UniformNames = listOf("Navy", "Charcoal", "Red", "Green")
// The first 5 paints unlock with the patient compartment. From FIRST_HEART_PAINT on they are bought with Hearts.
val AmbColors = listOf(
    Color(0xFFEE1010), Color(0xFFF4F4F4), Color(0xFFFFC107), Color(0xFF1E88E5), Color(0xFF43A047),
    Color(0xFFEC407A), Color(0xFF7E57C2), Color(0xFF263238)
)
val AmbColorNames = listOf("Red", "White", "Yellow", "Blue", "Green", "Pink", "Purple", "Midnight")

private const val OUT = 22f

private fun DrawScope.rr(c: Color, x: Float, y: Float, w: Float, h: Float, r: Float, outline: Boolean = true) {
    drawRoundRect(c, Offset(x, y), Size(w, h), CornerRadius(r, r))
    if (outline) drawRoundRect(Color(0xFF111111), Offset(x, y), Size(w, h), CornerRadius(r, r), style = Stroke(OUT))
}

private fun DrawScope.shape(c: Color, p: Path, outline: Boolean = true) {
    drawPath(p, c)
    if (outline) drawPath(p, Color(0xFF111111), style = Stroke(OUT, join = StrokeJoin.Round))
}

private fun DrawScope.ring(c: Color, cx: Float, cy: Float, r: Float) {
    drawCircle(c, r, Offset(cx, cy))
    drawCircle(Color(0xFF111111), r, Offset(cx, cy), style = Stroke(OUT))
}

// Side view of the red Type 1 ambulance, drawn in a 1780 x 880 box.
// Scale it to fit before calling.
fun DrawScope.ambulanceSide(colorIndex: Int, lightsOn: Boolean = false) {
    val body = AmbColors[colorIndex.coerceIn(0, AmbColors.size - 1)]
    val cross = if (colorIndex == 1) Color(0xFFE81010) else Color.White
    val grey = Color(0xFFD0D0D0)
    val cab = Path()
    cab.moveTo(1175f, 232f)
    cab.lineTo(1350f, 236f)
    cab.cubicTo(1363f, 239f, 1375f, 245f, 1385f, 255f)
    cab.lineTo(1520f, 405f)
    cab.cubicTo(1640f, 428f, 1712f, 450f, 1735f, 470f)
    cab.cubicTo(1752f, 490f, 1758f, 540f, 1755f, 620f)
    cab.lineTo(1170f, 725f)
    cab.close()
    shape(body, cab)
    rr(body, 42f, 58f, 1135f, 667f, 30f)
    val win = Path()
    win.moveTo(1215f, 270f)
    win.lineTo(1330f, 272f)
    win.lineTo(1460f, 420f)
    win.lineTo(1460f, 450f)
    win.lineTo(1215f, 450f)
    win.close()
    shape(Color(0xFF7FD3FF), win)
    val shine = Path()
    shine.moveTo(1235f, 425f)
    shine.lineTo(1330f, 290f)
    shine.lineTo(1355f, 292f)
    shine.lineTo(1255f, 440f)
    shine.close()
    drawPath(shine, Color.White, alpha = 0.55f)
    rr(Color(0xFF2B2B2B), 1392f, 378f, 62f, 92f, 14f)
    rr(Color(0xFF2B2B2B), 1205f, 490f, 65f, 34f, 10f)
    rr(Color(0xFFFFC94D), 1680f, 500f, 70f, 90f, 14f)
    rr(grey, 1665f, 625f, 95f, 95f, 14f)
    val step = Path()
    step.moveTo(18f, 648f)
    step.lineTo(75f, 648f)
    step.lineTo(75f, 690f)
    step.lineTo(230f, 690f)
    step.lineTo(230f, 735f)
    step.lineTo(18f, 735f)
    step.close()
    shape(grey, step)
    val red = if (lightsOn) Color(0xFFFF1744) else Color(0xFFE81010)
    val white = if (lightsOn) Color(0xFFFFFFFF) else Color(0xFFF2F2F2)
    rr(red, 80f, 95f, 108f, 78f, 14f)
    rr(white, 210f, 95f, 108f, 78f, 14f)
    rr(white, 900f, 95f, 105f, 78f, 14f)
    rr(red, 1028f, 95f, 108f, 78f, 14f)
    if (lightsOn) {
        for (x in floatArrayOf(134f, 1082f)) drawCircle(Color(0x66FF1744), 120f, Offset(x, 134f))
    }
    val c = Path()
    c.moveTo(525f, 205f)
    c.lineTo(640f, 205f)
    c.lineTo(640f, 308f)
    c.lineTo(750f, 308f)
    c.lineTo(750f, 412f)
    c.lineTo(640f, 412f)
    c.lineTo(640f, 512f)
    c.lineTo(525f, 512f)
    c.lineTo(525f, 412f)
    c.lineTo(415f, 412f)
    c.lineTo(415f, 308f)
    c.lineTo(525f, 308f)
    c.close()
    drawPath(c, cross)
    if (colorIndex == 1) drawPath(c, Color(0xFF111111), style = Stroke(10f, join = StrokeJoin.Round))
    for (wx in floatArrayOf(380f, 1520f)) {
        ring(Color(0xFF3B3838), wx, 735f, 140f)
        ring(grey, wx, 735f, 70f)
        drawCircle(Color.White, 30f, Offset(wx, 735f))
    }
}

// Standing paramedic in the same cartoon style as the patients, drawn in a 170 x 330 box.
fun DrawScope.paramedic(female: Boolean, skin: Int, hairStyle: Int, hairColor: Int, uniform: Int) {
    val ink = ArtInk
    val sk = skin.coerceIn(0, 2)
    val shirt = UniformCols[uniform.coerceIn(0, UniformCols.size - 1)]
    val pants = Color(0xFF263238)
    fun tube(pts: FloatArray, w: Float, c: Color) {
        val p = Path()
        p.moveTo(pts[0], pts[1])
        var i = 2
        while (i < pts.size) {
            p.lineTo(pts[i], pts[i + 1])
            i += 2
        }
        drawPath(p, ink, style = Stroke(w + 5f, cap = StrokeCap.Round, join = StrokeJoin.Round))
        drawPath(p, c, style = Stroke(w, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
    drawOval(Color.Black, Offset(30f, 312f), Size(110f, 14f), alpha = 0.2f)
    // legs and boots
    tube(floatArrayOf(70f, 200f, 66f, 300f), 22f, pants)
    tube(floatArrayOf(100f, 200f, 104f, 300f), 22f, pants)
    for (bx in floatArrayOf(60f, 110f)) {
        drawOval(Color(0xFF212121), Offset(bx - 20f, 296f), Size(40f, 18f))
        drawOval(ink, Offset(bx - 20f, 296f), Size(40f, 18f), style = Stroke(2.5f))
    }
    // arms
    tube(floatArrayOf(48f, 110f, 34f, 160f, 40f, 205f), 16f, shirt)
    tube(floatArrayOf(122f, 110f, 136f, 160f, 130f, 205f), 16f, shirt)
    for (hx in floatArrayOf(40f, 130f)) {
        drawCircle(Brush.radialGradient(listOf(SkinLight[sk], SkinDark[sk]), Offset(hx - 2f, 205f), 12f), 9f, Offset(hx, 207f))
        drawCircle(ink, 9f, Offset(hx, 207f), style = Stroke(2.5f))
    }
    // body
    val bodyBrush = Brush.horizontalGradient(listOf(shirt.copy(alpha = 1f), shirt), startX = 40f, endX = 130f)
    drawRoundRect(bodyBrush, Offset(40f, 96f), Size(90f, 114f), CornerRadius(22f, 22f))
    drawRoundRect(Color.White, Offset(46f, 102f), Size(12f, 90f), CornerRadius(6f, 6f), alpha = 0.18f)
    drawRoundRect(ink, Offset(40f, 96f), Size(90f, 114f), CornerRadius(22f, 22f), style = Stroke(2.5f))
    drawRect(Color(0xFF212121), Offset(41f, 192f), Size(88f, 10f))
    drawRect(Color(0xFFFFC107), Offset(80f, 192f), Size(10f, 10f))
    // reflective stripe
    drawRect(Color(0xFFCFD8DC), Offset(41f, 160f), Size(88f, 6f), alpha = 0.9f)
    // Star of Life patch
    val pc = Offset(108f, 124f)
    drawCircle(Color.White, 10f, pc)
    val blue = Color(0xFF0A72B8)
    for (a in floatArrayOf(0f, 60f, 120f)) {
        val rad = Math.toRadians(a.toDouble())
        val dx = (8f * Math.sin(rad)).toFloat()
        val dy = (8f * Math.cos(rad)).toFloat()
        drawLine(blue, Offset(pc.x - dx, pc.y - dy), Offset(pc.x + dx, pc.y + dy), 4f)
    }
    // neck and head
    drawRect(SkinDark[sk], Offset(76f, 82f), Size(18f, 18f))
    val head = Offset(85f, 56f)
    drawCircle(Brush.radialGradient(listOf(SkinLight[sk], SkinDark[sk]), Offset(76f, 46f), 40f), 30f, head)
    drawCircle(ink, 30f, head, style = Stroke(2.5f))
    drawOval(Color.White, Offset(64f, 33f), Size(18f, 10f), alpha = 0.3f)
    val hc = HairCols[hairColor.coerceIn(0, 3)]
    val h = Path()
    fun fill(p: Path) {
        p.close()
        drawPath(p, hc)
        drawPath(p, ink, style = Stroke(2.5f, join = StrokeJoin.Round))
    }
    if (female) {
        when (hairStyle) {
            0 -> {
                h.moveTo(56f, 60f)
                h.cubicTo(46f, 16f, 124f, 16f, 114f, 60f)
                h.cubicTo(120f, 82f, 116f, 96f, 108f, 100f)
                h.cubicTo(110f, 72f, 106f, 42f, 85f, 38f)
                h.cubicTo(64f, 42f, 60f, 72f, 62f, 100f)
                h.cubicTo(54f, 96f, 50f, 82f, 56f, 60f)
                fill(h)
            }
            1 -> {
                h.moveTo(55f, 56f)
                h.cubicTo(52f, 18f, 118f, 18f, 115f, 56f)
                h.lineTo(115f, 74f)
                h.cubicTo(110f, 76f, 108f, 74f, 108f, 66f)
                h.cubicTo(106f, 42f, 64f, 42f, 62f, 66f)
                h.cubicTo(62f, 74f, 60f, 76f, 55f, 74f)
                fill(h)
            }
            else -> {
                drawCircle(hc, 10f, Offset(85f, 22f))
                drawCircle(ink, 10f, Offset(85f, 22f), style = Stroke(2.5f))
                h.moveTo(55f, 52f)
                h.cubicTo(54f, 20f, 116f, 20f, 115f, 52f)
                h.cubicTo(106f, 36f, 64f, 36f, 55f, 52f)
                fill(h)
            }
        }
    } else {
        when (hairStyle) {
            0 -> {
                h.moveTo(55f, 52f)
                h.cubicTo(54f, 20f, 116f, 20f, 115f, 52f)
                h.cubicTo(106f, 36f, 64f, 36f, 55f, 52f)
                fill(h)
            }
            1 -> {
                h.moveTo(58f, 46f)
                h.cubicTo(60f, 22f, 110f, 22f, 112f, 46f)
                h.cubicTo(101f, 38f, 69f, 38f, 58f, 46f)
                fill(h)
            }
            else -> {
                for (x in floatArrayOf(52f, 110f)) {
                    drawOval(hc, Offset(x, 42f), Size(9f, 18f))
                    drawOval(ink, Offset(x, 42f), Size(9f, 18f), style = Stroke(2.5f))
                }
            }
        }
    }
    for (ex in floatArrayOf(74f, 96f)) {
        drawCircle(Color.White, 7.5f, Offset(ex, 58f))
        drawCircle(ink, 7.5f, Offset(ex, 58f), style = Stroke(2.5f))
        drawCircle(ink, 3f, Offset(ex + 1f, 59f))
    }
    val smile = Path()
    smile.moveTo(76f, 72f)
    smile.cubicTo(80f, 79f, 90f, 79f, 94f, 72f)
    drawPath(smile, ink, style = Stroke(2.5f, cap = StrokeCap.Round))
}
