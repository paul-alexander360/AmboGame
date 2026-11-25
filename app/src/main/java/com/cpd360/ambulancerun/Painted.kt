package com.cpd360.ambulancerun

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.lerp
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

// The Painted cast: busts for Dot, Dr. Ray and the player, and the full-body patient on the exam screen.
// Every shape is shaded the same way: the dark color first, then the base color nudged up and left
// in soft steps (light from the top left), then a soft white highlight.

class Tone(val base: Color, val dark: Color)

private fun tone(a: Long, b: Long) = Tone(Color(a), Color(b))

// Skin, light to dark
val PaintSkin = listOf(
    tone(0xFFF3CDB0, 0xFFC99474),
    tone(0xFFE8B98F, 0xFFB8855E),
    tone(0xFFDBA07E, 0xFFA8694A),
    tone(0xFFB57A55, 0xFF7F4F33),
    tone(0xFF8A5A3C, 0xFF5A3522)
)

// Hair: Blond, Brown, Black, Red, Grey, then White (patients only)
val PaintHair = listOf(
    tone(0xFFC9A061, 0xFF8A6630),
    tone(0xFF5A3A26, 0xFF2E1C12),
    tone(0xFF2A1D16, 0xFF120C08),
    tone(0xFFA0502A, 0xFF6A2E14),
    tone(0xFFB9B6B2, 0xFF7D7A76),
    tone(0xFFE4E2DE, 0xFFA9A6A2)
)
val PaintHairNames = listOf("Blond", "Brown", "Black", "Red", "Grey")

// Hair styles
const val HS_SHORT = 0
const val HS_BUZZ = 1
const val HS_CURLY = 2
const val HS_PONY = 3
const val HS_BUN = 4
const val HS_LONG = 5
const val HS_BOB = 6
const val HS_BALD = 7
const val HS_AFRO = 8
val HairStyleNames = listOf("Short", "Buzz", "Curly", "Ponytail", "Bun", "Long")

// Faces
const val EX_SMILE = 0
const val EX_WORRIED = 1
const val EX_WEAK = 2
const val EX_PAIN = 3
const val EX_CLOSED = 4

// Outfits and hats
const val FIT_NONE = 0
const val FIT_UNIFORM = 1
const val FIT_SCRUBS = 2
const val FIT_COAT = 3
const val HAT_NONE = 0
const val HAT_HEADSET = 1
const val HAT_GLASSES = 2

// Bust backgrounds
const val BG_DISPATCH = 0
const val BG_OFFICE = 1
const val BG_BAY = 2

class Look(
    val hs: Int,
    val skin: Tone,
    val hair: Tone,
    val top: Tone,
    val outfit: Int = FIT_NONE,
    val hat: Int = HAT_NONE,
    val expr: Int = EX_SMILE,
    val eyes: Color = Color(0xFF4A3326),
    val brow: Color? = null,
    val must: Tone? = null,
    val beard: Boolean = false,
    val age: Int = 30,
    val pale: Boolean = false,
    val flush: Boolean = false,
    val sweat: Boolean = false,
    val bandage: Boolean = false,
    val lip: Color = Color(0xFFB65C60)
)

fun dotLook() = Look(
    HS_BUN, PaintSkin[0], PaintHair[1], tone(0xFF1F9E8E, 0xFF0E5A52), FIT_SCRUBS, HAT_HEADSET,
    eyes = Color(0xFF3D6A4A), age = 34
)

fun rayLook() = Look(
    HS_BALD, PaintSkin[4], PaintHair[4], tone(0xFF6C9AD0, 0xFF3A5F8A), FIT_COAT, HAT_GLASSES,
    brow = Color(0xFFD8D5D0), must = PaintHair[4], age = 66
)

fun uniformTone(i: Int): Tone {
    val c = UniformCols[i.coerceIn(0, UniformCols.size - 1)]
    return Tone(c, lerp(c, Color.Black, 0.55f))
}

fun playerLook(p: PlayerData) = Look(
    p.hairStyle.coerceIn(0, 5), PaintSkin[p.skin.coerceIn(0, 4)], PaintHair[p.hairColor.coerceIn(0, 4)],
    uniformTone(p.uniform), FIT_UNIFORM, beard = !p.female, age = 30,
    eyes = if (p.skin >= 3) Color(0xFF2A1C14) else Color(0xFF4A3326)
)

// ---------- drawing helpers ----------

private class PB {
    val p = Path()
    fun m(x: Float, y: Float) = p.moveTo(x, y)
    fun l(x: Float, y: Float) = p.lineTo(x, y)
    fun q(cx: Float, cy: Float, x: Float, y: Float) = p.quadraticTo(cx, cy, x, y)
    fun z() = p.close()
}

private fun pth(b: PB.() -> Unit): Path {
    val pb = PB()
    pb.b()
    return pb.p
}

private fun ell(cx: Float, cy: Float, rx: Float, ry: Float): Path {
    val p = Path()
    p.addOval(Rect(cx - rx, cy - ry, cx + rx, cy + ry))
    return p
}

private fun rr(x: Float, y: Float, w: Float, h: Float, r: Float): Path {
    val p = Path()
    p.addRoundRect(RoundRect(Rect(x, y, x + w, y + h), CornerRadius(r, r)))
    return p
}

// A limb: a rounded tube from a to b, w wide
private fun cap(ax: Float, ay: Float, bx: Float, by: Float, w: Float): Path {
    val r = w / 2f
    val len = hypot(bx - ax, by - ay).coerceAtLeast(0.01f)
    val nx = -(by - ay) / len * r
    val ny = (bx - ax) / len * r
    val an = (atan2(ny, nx) * 180.0 / PI).toFloat()
    val p = Path()
    p.moveTo(ax + nx, ay + ny)
    p.lineTo(bx + nx, by + ny)
    p.arcTo(Rect(bx - r, by - r, bx + r, by + r), an, -180f, false)
    p.lineTo(ax - nx, ay - ny)
    p.arcTo(Rect(ax - r, ay - r, ax + r, ay + r), an + 180f, -180f, false)
    p.close()
    return p
}

// A soft, blurred-looking oval
private fun DrawScope.soft(c: Color, a: Float, cx: Float, cy: Float, rx: Float, ry: Float, blur: Float) {
    if (rx <= 0f || ry <= 0f) return
    val r = rx + blur * 1.6f
    val inner = (rx - blur * 0.6f).coerceAtLeast(0f) / r
    scale(1f, ry / rx, Offset(cx, cy)) {
        drawCircle(
            Brush.radialGradient(
                0f to c.copy(alpha = a), inner to c.copy(alpha = a), 1f to c.copy(alpha = 0f),
                center = Offset(cx, cy), radius = r
            ),
            r, Offset(cx, cy)
        )
    }
}

// One painted shape. hl = highlight x, y, rx, ry.
private fun DrawScope.part(path: Path, t: Tone, o: Float = 5f, hl: FloatArray? = null, hlA: Float = 0.18f) {
    drawPath(path, t.dark)
    clipPath(path) {
        for (i in 1..6) {
            val f = i / 6f
            translate(-o * f, -o * f) { drawPath(path, lerp(t.dark, t.base, f)) }
        }
        if (hl != null) soft(Color.White, hlA * 0.6f, hl[0], hl[1], hl[2], hl[3], 7f)
    }
}

private fun DrawScope.line(path: Path, c: Color, w: Float, a: Float = 1f) =
    drawPath(path, c, alpha = a, style = Stroke(w, cap = StrokeCap.Round, join = StrokeJoin.Round))

private fun dk(c: Color, f: Float) = Color(
    (c.red * f).coerceIn(0f, 1f), (c.green * f).coerceIn(0f, 1f), (c.blue * f).coerceIn(0f, 1f), c.alpha
)

private fun hl(vararg v: Float) = v

// ---------- head ----------

// Hair that sits behind the head and body. Drawn first.
private fun DrawScope.hairBehind(o: Look, cx: Float, hy: Float, rx: Float, ry: Float) {
    val h = o.hair
    when (o.hs) {
        HS_LONG -> part(pth {
            m(cx - rx - 8f, hy - 10f); q(cx - rx - 14f, hy + 70f, cx - rx + 4f, hy + 96f); l(cx + rx - 4f, hy + 96f)
            q(cx + rx + 14f, hy + 70f, cx + rx + 8f, hy - 10f); z()
        }, h, 7f)
        HS_BOB -> part(pth {
            m(cx - rx - 8f, hy - 16f); q(cx - rx - 12f, hy + 44f, cx - rx + 6f, hy + 50f); l(cx + rx - 6f, hy + 50f)
            q(cx + rx + 12f, hy + 44f, cx + rx + 8f, hy - 16f); z()
        }, h, 6f)
        HS_AFRO -> part(ell(cx, hy - 12f, rx + 22f, ry + 12f), h, 8f)
        HS_PONY -> part(pth {
            m(cx + rx - 6f, hy - 30f); q(cx + rx + 30f, hy - 20f, cx + rx + 20f, hy + 40f)
            q(cx + rx + 10f, hy + 70f, cx + rx + 2f, hy + 40f); q(cx + rx + 10f, hy, cx + rx - 10f, hy - 20f); z()
        }, h, 5f)
    }
}

// Ears, face, hair and hat. The head is rx wide (40 on a bust) and rx * ryk tall.
private fun DrawScope.paintedHead(o: Look, cx: Float, hy: Float, rx: Float, ryk: Float, ho: Float) {
    val ry = rx * ryk
    val k = rx / 27f
    val skin = when {
        o.flush -> Tone(o.skin.base, Color(0xFFB5524A))
        o.pale -> Tone(lerp(o.skin.base, Color(0xFFE6D2C6), 0.55f), lerp(o.skin.dark, Color(0xFFA99088), 0.55f))
        else -> o.skin
    }
    val hair = o.hair
    part(ell(cx - rx - 1f, hy + 4f * k, 5f * k, 8f * k), o.skin, 3f)
    part(ell(cx + rx + 1f, hy + 4f * k, 5f * k, 8f * k), o.skin, 3f)
    part(pth {
        m(cx - rx, hy - ry * 0.1f); q(cx - rx, hy - ry, cx, hy - ry); q(cx + rx, hy - ry, cx + rx, hy - ry * 0.1f)
        q(cx + rx * 0.98f, hy + ry * 0.55f, cx + rx * 0.55f, hy + ry * 0.85f); q(cx, hy + ry * 1.08f, cx - rx * 0.55f, hy + ry * 0.85f)
        q(cx - rx * 0.98f, hy + ry * 0.55f, cx - rx, hy - ry * 0.1f); z()
    }, skin, ho, hl(cx - rx * 0.35f, hy - ry * 0.45f, rx * 0.32f, ry * 0.2f), 0.2f)
    if (o.flush) soft(Color(0xFFE0505A), 0.3f, cx, hy + 6f, rx * 0.8f, ry * 0.5f, 6f)
    // Age lines
    if (o.age > 45) {
        val a = ((o.age - 40) / 70f).coerceAtMost(0.55f)
        val w = 1.2f * k
        line(pth { m(cx - 12f * k, hy - ry * 0.55f); q(cx, hy - ry * 0.55f - 3f * k, cx + 12f * k, hy - ry * 0.55f) }, o.skin.dark, w, a)
        line(pth { m(cx - 9f * k, hy - ry * 0.45f); q(cx, hy - ry * 0.45f - 2f * k, cx + 9f * k, hy - ry * 0.45f) }, o.skin.dark, w, a)
        line(pth { m(cx - 8f * k, hy + 10f * k); q(cx - 11f * k, hy + 17f * k, cx - 9f * k, hy + 22f * k) }, o.skin.dark, w, a)
        line(pth { m(cx + 8f * k, hy + 10f * k); q(cx + 11f * k, hy + 17f * k, cx + 9f * k, hy + 22f * k) }, o.skin.dark, w, a)
        if (o.age > 65) {
            line(pth { m(cx - 17f * k, hy + 5f * k); q(cx - 13f * k, hy + 7f * k, cx - 9f * k, hy + 5f * k) }, o.skin.dark, w, a)
            line(pth { m(cx + 9f * k, hy + 5f * k); q(cx + 13f * k, hy + 7f * k, cx + 17f * k, hy + 5f * k) }, o.skin.dark, w, a)
        }
    }
    // Eyes and brows
    val ey = hy + 2f * k
    val ex = 11f * k
    val lid = dk(skin.dark, 0.45f)
    val bc = o.brow ?: hair.dark
    for (s in floatArrayOf(-1f, 1f)) {
        val x = cx + s * ex
        if (o.expr == EX_PAIN || o.expr == EX_CLOSED) {
            line(pth { m(x - 6f * k, ey); q(x, ey + 3f * k, x + 6f * k, ey) }, lid, 1.8f * k)
            if (o.expr == EX_PAIN) {
                line(pth { m(x + s * 7f * k, ey - 7f * k); q(x + s * 1f * k, ey - 8f * k, x - s * 5f * k, ey - 12f * k) }, bc, 2.6f * k)
            } else {
                line(pth { m(x - s * 5f * k, ey - 9.5f * k); q(x + s * 1f * k, ey - 11.5f * k, x + s * 7.5f * k, ey - 9f * k) }, bc, 2.6f * k)
            }
        } else {
            drawOval(
                Color(0xFFFBF7F2), Offset(x - 6.3f * k, ey - (if (o.expr == EX_SMILE) 3.9f else 4.6f) * k),
                Size(12.6f * k, (if (o.expr == EX_SMILE) 7.8f else 9.2f) * k)
            )
            val ix = x - s * 0.3f * k
            drawCircle(o.eyes, 3.5f * k, Offset(ix, ey + 0.4f * k))
            drawCircle(Color(0xFF120C08), 1.8f * k, Offset(ix, ey + 0.4f * k))
            drawCircle(Color.White, 1.1f * k, Offset(ix - 1.2f * k, ey - 0.9f * k))
            when (o.expr) {
                EX_WEAK -> {
                    drawPath(pth {
                        m(x - 7f * k, ey - 5.5f * k); l(x + 7f * k, ey - 5.5f * k); l(x + 7f * k, ey + 0.3f * k)
                        q(x, ey - 1.6f * k, x - 7f * k, ey + 0.3f * k); z()
                    }, skin.base)
                    line(pth { m(x - 6.5f * k, ey + 0.3f * k); q(x, ey - 1.6f * k, x + 6.5f * k, ey + 0.3f * k) }, lid, 1.6f * k)
                }
                EX_WORRIED -> line(pth { m(x - s * 6.6f * k, ey + 0.5f * k); q(x, ey - 6.6f * k, x + s * 6.6f * k, ey - 1.5f * k) }, lid, 1.8f * k)
                else -> line(pth { m(x - 6.6f * k, ey + 0.5f * k); q(x, ey - 6f * k, x + 6.6f * k, ey + 0.5f * k) }, lid, 1.8f * k)
            }
            line(pth { m(x - 6f * k, ey + 1.8f * k); q(x, ey + 5.4f * k, x + 6f * k, ey + 1.8f * k) }, skin.dark, 1f * k, 0.6f)
            if (o.expr == EX_WORRIED || o.expr == EX_WEAK) {
                line(pth { m(x + s * 7.5f * k, ey - 8f * k); q(x + s * 1f * k, ey - 10.5f * k, x - s * 5f * k, ey - 12.5f * k) }, bc, 2.6f * k)
            } else {
                line(pth { m(x - s * 5f * k, ey - 9.5f * k); q(x + s * 1f * k, ey - 12.5f * k, x + s * 7.5f * k, ey - 9f * k) }, bc, 2.6f * k)
            }
        }
        soft(Color(0xFFE0565C), if (o.flush) 0.35f else 0.12f, x + s * 3f * k, ey + 10f * k, 6f * k, 4f * k, 3f)
    }
    // Nose
    line(pth {
        m(cx + 1f * k, ey + 2f * k); q(cx + 3.5f * k, ey + 9f * k, cx + 2.5f * k, ey + 11f * k)
        q(cx, ey + 12.5f * k, cx - 2.5f * k, ey + 11f * k)
    }, skin.dark, 1.4f * k)
    drawOval(Color.White, Offset(cx - 2.6f * k, ey + 5.5f * k), Size(3.2f * k, 5f * k), alpha = 0.22f)
    // Mustache and stubble
    val mt = o.must
    if (mt != null) part(pth {
        m(cx - 10f * k, ey + 17f * k); q(cx - 6f * k, ey + 12f * k, cx, ey + 14f * k); q(cx + 6f * k, ey + 12f * k, cx + 10f * k, ey + 17f * k)
        q(cx, ey + 15.5f * k, cx - 10f * k, ey + 17f * k); z()
    }, mt, 2f)
    if (o.beard) line(pth {
        m(cx - rx * 0.92f, hy + 4f); q(cx - rx * 0.8f, hy + ry * 0.9f, cx, hy + ry * 1.05f); q(cx + rx * 0.8f, hy + ry * 0.9f, cx + rx * 0.92f, hy + 4f)
    }, hair.base, 3f * k, 0.3f)
    // Mouth
    val my = ey + 18f * k
    when (o.expr) {
        EX_SMILE -> {
            drawPath(pth {
                m(cx - 8f * k, my - 1.5f * k); q(cx, my + 7f * k, cx + 8f * k, my - 1.5f * k); q(cx, my + 1f * k, cx - 8f * k, my - 1.5f * k); z()
            }, Color(0xFF5A2224))
            drawPath(pth {
                m(cx - 6f * k, my - 0.5f * k); q(cx, my + 2.2f * k, cx + 6f * k, my - 0.5f * k); l(cx + 5f * k, my + 0.6f * k)
                q(cx, my + 2.4f * k, cx - 5f * k, my + 0.6f * k); z()
            }, Color(0xFFF4EFE8))
            line(pth { m(cx - 8.5f * k, my - 1.5f * k); q(cx, my + 8f * k, cx + 8.5f * k, my - 1.5f * k) }, o.lip, 1.6f * k)
        }
        EX_PAIN -> {
            drawRoundRect(Color(0xFF5A2224), Offset(cx - 7f * k, my - 2f * k), Size(14f * k, 5f * k), CornerRadius(2.5f * k))
            drawRoundRect(Color(0xFFF4EFE8), Offset(cx - 6f * k, my - 1.4f * k), Size(12f * k, 3.8f * k), CornerRadius(1.5f * k))
            drawLine(Color(0xFFC9C0B8), Offset(cx - 6f * k, my + 0.5f * k), Offset(cx + 6f * k, my + 0.5f * k), 0.6f * k)
        }
        else -> {
            val small = o.expr == EX_WEAK || o.expr == EX_CLOSED
            val mrx = (if (small) 3.5f else 5.5f) * k
            val mry = (if (small) 2f else 3.4f) * k
            drawOval(Color(0xFF4A1A1C), Offset(cx - mrx, my - mry), Size(mrx * 2f, mry * 2f))
            line(pth { m(cx - 6.5f * k, my - 0.3f * k); q(cx, my - 4f * k, cx + 6.5f * k, my - 0.3f * k) }, o.lip, 1.8f * k)
            line(pth { m(cx - 5.5f * k, my + 1f * k); q(cx, my + 5.5f * k, cx + 5.5f * k, my + 1f * k) }, o.lip, 2.2f * k)
        }
    }
    // Hair in front
    val capP = { dip: Float ->
        pth {
            m(cx - rx - 2f, hy + 2f * k); q(cx - rx - 5f, hy - ry - 6f * k, cx, hy - ry - 4f * k)
            q(cx + rx + 5f, hy - ry - 6f * k, cx + rx + 2f, hy + 2f * k); q(cx + rx, hy - ry * 0.45f, cx + rx * 0.55f, hy - ry * dip)
            q(cx + rx * 0.1f, hy - ry * 0.35f, cx - rx * 0.25f, hy - ry * 0.62f); q(cx - rx * 0.75f, hy - ry * 0.5f, cx - rx - 2f, hy + 2f * k); z()
        }
    }
    val capHl = hl(cx - rx * 0.3f, hy - ry * 0.85f, rx * 0.35f, 4f * k)
    when (o.hs) {
        HS_SHORT, HS_PONY, HS_BUN -> part(capP(0.55f), hair, 5f, capHl, 0.25f)
        HS_LONG, HS_BOB -> part(pth {
            m(cx - rx - 4f, hy + 18f * k); q(cx - rx - 6f, hy - ry - 8f * k, cx, hy - ry - 5f * k)
            q(cx + rx + 6f, hy - ry - 8f * k, cx + rx + 4f, hy + 18f * k); q(cx + rx - 2f, hy - ry * 0.4f, cx + rx * 0.3f, hy - ry * 0.62f)
            q(cx - rx * 0.1f, hy - ry * 0.6f, cx - rx * 0.15f, hy - ry * 0.75f); q(cx - rx * 0.5f, hy - ry * 0.45f, cx - rx * 0.85f, hy - ry * 0.2f)
            q(cx - rx * 0.95f, hy, cx - rx - 4f, hy + 18f * k); z()
        }, hair, 5f, hl(cx - rx * 0.3f, hy - ry * 0.85f, rx * 0.4f, 4f * k), 0.25f)
        HS_AFRO -> part(pth {
            m(cx - rx - 6f, hy); q(cx - rx - 10f, hy - ry - 14f * k, cx, hy - ry - 12f * k)
            q(cx + rx + 10f, hy - ry - 14f * k, cx + rx + 6f, hy); q(cx + rx * 0.7f, hy - ry * 0.55f, cx, hy - ry * 0.6f)
            q(cx - rx * 0.7f, hy - ry * 0.55f, cx - rx - 6f, hy); z()
        }, hair, 5f, hl(cx - rx * 0.3f, hy - ry * 0.95f, rx * 0.4f, 4f * k), 0.15f)
        HS_BUZZ -> drawPath(pth {
            m(cx - rx + 1f, hy - ry * 0.2f); q(cx - rx, hy - ry - 2f, cx, hy - ry - 1f); q(cx + rx, hy - ry - 2f, cx + rx - 1f, hy - ry * 0.2f)
            q(cx + rx * 0.7f, hy - ry * 0.62f, cx, hy - ry * 0.68f); q(cx - rx * 0.7f, hy - ry * 0.62f, cx - rx + 1f, hy - ry * 0.2f); z()
        }, hair.base, alpha = 0.85f)
        HS_BALD -> for (s in floatArrayOf(-1f, 1f)) part(pth {
            m(cx + s * (rx + 3f), hy + 6f * k); q(cx + s * (rx + 6f), hy - ry * 0.55f, cx + s * rx * 0.6f, hy - ry * 0.78f)
            q(cx + s * rx * 0.8f, hy - ry * 0.4f, cx + s * (rx - 4f), hy + 6f * k); z()
        }, hair, 3f)
        HS_CURLY -> {
            part(capP(0.7f), hair, 4f)
            for (i in 0..8) {
                val a = PI * (1.05 + i * 0.1)
                part(ell(cx + (cos(a) * rx * 0.95).toFloat(), hy - 6f * k + (sin(a) * ry * 0.95).toFloat(), 9f * k, 8f * k), hair, 3f)
            }
        }
    }
    if (o.hs == HS_BUN) part(ell(cx, hy - ry - 10f * k, 14f * k, 12f * k), hair, 4f, hl(cx - 4f * k, hy - ry - 14f * k, 5f * k, 3f * k), 0.25f)
    // Hats and glasses
    when (o.hat) {
        HAT_HEADSET -> {
            val band = Color(0xFF2A2D33)
            val cup = tone(0xFF3A3E46, 0xFF16181C)
            line(pth { m(cx - rx - 4f, hy + 2f); q(cx - rx - 6f, hy - ry - 14f, cx, hy - ry - 12f); q(cx + rx + 6f, hy - ry - 14f, cx + rx + 4f, hy + 2f) }, band, 3.6f * k)
            part(rr(cx - rx - 10f, hy - 8f, 14f, 26f, 6f), cup, 2f)
            part(rr(cx + rx - 4f, hy - 8f, 14f, 26f, 6f), cup, 2f)
            line(pth { m(cx - rx - 4f, hy + 14f); q(cx - rx + 4f, hy + ry * 0.9f, cx - 12f, hy + ry * 0.78f) }, band, 3f)
            part(ell(cx - 10f, hy + ry * 0.78f, 5f, 4f), cup, 1.5f)
        }
        HAT_GLASSES -> {
            val col = Color(0xFF2A2422)
            for (gx in floatArrayOf(cx - 19f * k, cx + 4f * k)) {
                drawRoundRect(Color(0xFFBCD6FF), Offset(gx, hy - 3f * k), Size(15f * k, 10f * k), CornerRadius(4f * k), alpha = 0.12f)
                drawRoundRect(col, Offset(gx, hy - 3f * k), Size(15f * k, 10f * k), CornerRadius(4f * k), style = Stroke(1.6f * k))
            }
            line(pth { m(cx - 4f * k, hy + 1f * k); q(cx, hy - 1.5f * k, cx + 4f * k, hy + 1f * k) }, col, 1.4f * k)
            drawLine(col, Offset(cx - 19f * k, hy), Offset(cx - rx, hy - 1f * k), 1.4f * k)
            drawLine(col, Offset(cx + 19f * k, hy), Offset(cx + rx, hy - 1f * k), 1.4f * k)
            drawLine(Color.White, Offset(cx - 16f * k, hy - 1f * k), Offset(cx - 12f * k, hy + 4f * k), 1f * k, alpha = 0.5f)
        }
    }
    if (o.sweat) {
        for (d in listOf(Offset(rx - 2f, -6f * k), Offset(-rx + 4f, 4f * k), Offset(rx * 0.4f, -ry * 0.55f))) {
            val x = cx + d.x
            val y = hy + d.y
            drawPath(pth { m(x, y); q(x + 3f * k, y + 6f * k, x, y + 9f * k); q(x - 3f * k, y + 6f * k, x, y); z() }, Color(0xFFA8DCFF), alpha = 0.9f)
        }
    }
    if (o.bandage) {
        val bx = cx + rx * 0.3f
        val by = hy + ry * 0.15f
        run {
            rotate(-18f, Offset(bx + rx * 0.27f, by + ry * 0.1f)) {
                part(rr(bx, by, rx * 0.55f, ry * 0.24f, 4f), tone(0xFFF2ECE2, 0xFFC4BCAE), 2f)
                drawLine(Color(0xFFD8CFBF), Offset(bx + rx * 0.08f, by + ry * 0.08f), Offset(bx + rx * 0.46f, by + ry * 0.08f), 1f)
                drawLine(Color(0xFFD8CFBF), Offset(bx + rx * 0.08f, by + ry * 0.16f), Offset(bx + rx * 0.46f, by + ry * 0.16f), 1f)
            }
        }
    }
}

// ---------- bust ----------

private const val BW = 265f
private const val BH = 250f

private fun DrawScope.glowAt(x: Float, y: Float, r: Float, c: Long, a: Float) = soft(Color(c), a, x, y, r, r, r / 2.2f)

private fun DrawScope.props(bg: Int) {
    when (bg) {
        BG_DISPATCH -> {
            val panel = Color(0xFF0E1A2C)
            val edge = Color(0xFF3A4A66)
            drawRoundRect(panel, Offset(160f, 40f), Size(96f, 62f), CornerRadius(8f))
            drawRoundRect(edge, Offset(160f, 40f), Size(96f, 62f), CornerRadius(8f), style = Stroke(3f))
            line(pth { m(168f, 90f); l(184f, 72f); l(196f, 80f); l(212f, 58f); l(228f, 66f); l(248f, 50f) }, Color(0xFF46E07A), 2.5f)
            drawCircle(Color(0xFFFF4A4A), 4f, Offset(212f, 58f))
            drawRoundRect(panel, Offset(8f, 50f), Size(70f, 48f), CornerRadius(8f))
            drawRoundRect(edge, Offset(8f, 50f), Size(70f, 48f), CornerRadius(8f), style = Stroke(3f))
            drawRoundRect(Color(0xFF2F6FD0), Offset(16f, 58f), Size(54f, 6f), CornerRadius(3f))
            drawRoundRect(Color(0xFF2F6FD0), Offset(16f, 70f), Size(40f, 6f), CornerRadius(3f), alpha = 0.6f)
            drawRoundRect(Color(0xFFE5483F), Offset(16f, 82f), Size(48f, 6f), CornerRadius(3f), alpha = 0.8f)
            glowAt(200f, 70f, 50f, 0xFF46E07A, 0.25f)
            glowAt(40f, 74f, 40f, 0xFF2F6FD0, 0.3f)
        }
        BG_OFFICE -> {
            glowAt(230f, 60f, 60f, 0xFFFFCF7A, 0.25f)
            drawRoundRect(Color(0xFFC9B07A), Offset(18f, 34f), Size(62f, 46f), CornerRadius(4f))
            drawRoundRect(Color(0xFF6B5434), Offset(18f, 34f), Size(62f, 46f), CornerRadius(4f), style = Stroke(3f))
            drawRect(Color(0xFFF2EAD6), Offset(26f, 42f), Size(46f, 30f))
            for ((y, w) in listOf(50f to 34f, 57f to 28f, 64f to 20f)) drawLine(Color(0xFF8A7A5A), Offset(32f, y), Offset(32f + w, y), 2f)
            drawRoundRect(Color(0xFFDFE6EE), Offset(190f, 30f), Size(56f, 74f), CornerRadius(6f))
            drawRoundRect(Color(0xFF8A96A8), Offset(190f, 30f), Size(56f, 74f), CornerRadius(6f), style = Stroke(2f))
            for ((y, w) in listOf(50f to 36f, 62f to 28f, 74f to 32f, 86f to 20f)) drawLine(Color(0xFF9AA6B8), Offset(200f, y), Offset(200f + w, y), 3f)
        }
        else -> {
            drawRect(Color(0xFF1A2030), Offset.Zero, Size(BW, 120f))
            drawRect(Color(0xFFFFD23A), Offset(0f, 118f), Size(BW, 6f), alpha = 0.5f)
            drawRoundRect(Color(0xFFE8E8EC), Offset(170f, 36f), Size(90f, 70f), CornerRadius(10f))
            drawRect(Color(0xFFD33A36), Offset(170f, 70f), Size(90f, 10f))
            drawRoundRect(Color(0xFF3A6BFF), Offset(200f, 28f), Size(30f, 10f), CornerRadius(4f))
            glowAt(30f, 80f, 50f, 0xFFFF3030, 0.35f)
            glowAt(240f, 60f, 50f, 0xFF3A6BFF, 0.35f)
        }
    }
}

private fun bgCols(bg: Int): List<Color> = when (bg) {
    BG_DISPATCH -> listOf(Color(0xFF22324A), Color(0xFF121A28))
    BG_OFFICE -> listOf(Color(0xFF3A3240), Color(0xFF1E1A22))
    else -> listOf(Color(0xFF262C3C), Color(0xFF12151E))
}

private fun DrawScope.outfit(o: Look, c: Float, sy: Float, sw: Float) {
    when (o.outfit) {
        FIT_UNIFORM -> {
            part(pth {
                m(c - 16f, sy - 14f); l(c, sy + 18f); l(c + 16f, sy - 14f); l(c + 8f, sy - 18f); l(c, sy - 2f); l(c - 8f, sy - 18f); z()
            }, Tone(lerp(o.top.base, Color.Black, 0.18f), lerp(o.top.dark, Color.Black, 0.2f)), 2f)
            part(ell(c - sw + 40f, sy + 16f, 13f, 15f), tone(0xFFD33A36, 0xFF8E1E1C), 2f)
            val px = c - sw + 40f
            line(pth { m(px, sy + 7f); l(px, sy + 25f); m(px - 8f, sy + 11f); l(px + 8f, sy + 20f); m(px + 8f, sy + 11f); l(px - 8f, sy + 20f) }, Color.White, 3f)
            part(rr(c + 18f, sy + 20f, 30f, 9f, 4f), tone(0xFFD9D2BD, 0xFFA19A86), 1.5f)
            part(rr(c + 52f, sy + 14f, 12f, 26f, 4f), tone(0xFF2C2C30, 0xFF101012), 1.5f)
            drawRoundRect(Color(0xFF222222), Offset(c + 56f, sy + 4f), Size(3f, 12f), CornerRadius(1.5f))
        }
        FIT_SCRUBS -> line(pth { m(c - 22f, sy - 16f); l(c, sy + 16f); l(c + 22f, sy - 16f) }, o.top.dark, 5f)
        FIT_COAT -> {
            val white = tone(0xFFF1F1EE, 0xFFB8BAB8)
            part(pth {
                m(c - sw - 10f, 260f); q(c - sw - 6f, sy + 4f, c - sw + 36f, sy - 12f); l(c - 14f, sy - 16f); l(c - 6f, sy + 50f); l(c - 18f, 260f); z()
            }, white, 7f, hl(c - 70f, sy, 20f, 8f), 0.3f)
            part(pth {
                m(c + sw + 10f, 260f); q(c + sw + 6f, sy + 4f, c + sw - 36f, sy - 12f); l(c + 14f, sy - 16f); l(c + 6f, sy + 50f); l(c + 18f, 260f); z()
            }, white, 7f)
            part(pth { m(c - 5f, sy - 12f); l(c + 5f, sy - 12f); l(c + 7f, sy + 40f); l(c, sy + 50f); l(c - 7f, sy + 40f); z() }, tone(0xFFB0393A, 0xFF6E1F20), 2f)
            line(pth { m(c - 40f, sy - 14f); q(c - 50f, sy + 30f, c - 26f, sy + 44f); m(c + 40f, sy - 14f); q(c + 46f, sy + 20f, c + 34f, sy + 36f) }, Color(0xFF2A2D33), 5f)
            part(ell(c + 34f, sy + 42f, 9f, 9f), tone(0xFFC9CCD2, 0xFF7D8088), 2f, hl(c + 31f, sy + 39f, 3f, 2f), 0.6f)
            part(rr(c + 44f, sy + 20f, 26f, 12f, 3f), tone(0xFFD9E4F2, 0xFF9AA8BA), 1.5f)
        }
    }
}

// A bust in a 265 x 250 box: background, props, body, head and a vignette.
fun DrawScope.bust(o: Look, bg: Int) {
    val bgc = bgCols(bg)
    drawRect(Brush.verticalGradient(bgc, startY = 0f, endY = BH), Offset.Zero, Size(BW, BH))
    props(bg)
    val cx = 132f
    val hy = 98f
    val rx = 40f
    val ry = rx * 1.2f
    hairBehind(o, cx, hy, rx, ry)
    val sw = 78f
    val sy = 180f
    val body = pth {
        m(cx - sw + 2f, BH + 4f); l(cx - sw - 2f, sy + 44f); q(cx - sw - 2f, sy + 6f, cx - sw + 26f, sy + 2f)
        q(cx - 44f, sy - 4f, cx - 22f, sy - 18f); q(cx, sy - 10f, cx + 22f, sy - 18f); q(cx + 44f, sy - 4f, cx + sw - 26f, sy + 2f)
        q(cx + sw + 2f, sy + 6f, cx + sw + 2f, sy + 44f); l(cx + sw - 2f, BH + 4f); z()
    }
    part(rr(cx - rx * 0.42f, hy + ry * 0.55f, rx * 0.84f, ry * 0.9f, rx * 0.3f), o.skin, 5f)
    soft(o.skin.dark, 0.7f, cx, hy + ry * 0.98f, rx * 0.4f, 6f, 3f)
    part(body, o.top, 8f, hl(cx - 50f, sy, 30f, 10f), 0.12f)
    outfit(o, cx, sy, sw)
    paintedHead(o, cx, hy, rx, 1.2f, 7f)
    drawRect(
        Brush.radialGradient(
            0.55f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.5f),
            center = Offset(BW * 0.5f, BH * 0.42f), radius = BW * 0.75f
        ),
        Offset.Zero, Size(BW, BH)
    )
}

// Framings for the bust in a canvas of any size
const val FRAME_SHEET = 0
const val FRAME_AVATAR = 1
const val FRAME_HERO = 2

fun DrawScope.bustFramed(o: Look, bg: Int, frame: Int) {
    val w = size.width
    val h = size.height
    val bgc = bgCols(bg)
    drawRect(Brush.verticalGradient(bgc))
    when (frame) {
        FRAME_SHEET -> {
            // Head and shoulders across a wide, short strip
            val sc = maxOf(w * 1.08f / BW, h / 160f)
            translate(w / 2f - 132f * sc, -10f * sc) { scale(sc, Offset.Zero) { bust(o, bg) } }
        }
        FRAME_AVATAR -> {
            val sc = w / 127f
            translate(w / 2f - 132f * sc, -35f * sc) { scale(sc, Offset.Zero) { bust(o, bg) } }
        }
        else -> {
            val sc = h / 228f
            translate(w / 2f - 132f * sc, -6f * sc) { scale(sc, Offset.Zero) { bust(o, bg) } }
        }
    }
}

// ---------- exam patient ----------

const val HAND_LAP = 0
const val HAND_THROAT = 1
const val HAND_CHEST = 2

private val PatientShirts = listOf(
    tone(0xFF6B88A6, 0xFF3E5670),
    tone(0xFFA35A7A, 0xFF5E2F45),
    tone(0xFF4D7A5A, 0xFF2A4532),
    tone(0xFFB08A3A, 0xFF6E5420),
    tone(0xFF9A3A3A, 0xFF5A1E1E)
)
private val PatientPants = listOf(
    tone(0xFF343B4C, 0xFF1F2430),
    tone(0xFF4A4F57, 0xFF2A2E34),
    tone(0xFF5A4434, 0xFF36281C)
)

class PatientLook(val look: Look, val shirt: Int, val pants: Int, val hand: Int)

private fun DrawScope.room() {
    drawRect(Brush.verticalGradient(listOf(Color(0xFF433D44), Color(0xFF26222A)), 0f, 320f), Offset.Zero, Size(316f, 320f))
    drawRect(Brush.verticalGradient(listOf(Color(0xFF5E402C), Color(0xFF3A271A)), 252f, 320f), Offset(0f, 252f), Size(316f, 68f))
    for (i in 0..8) drawLine(Color.Black, Offset(i * 40f - 10f, 252f), Offset(i * 40f - 40f, 320f), 1.2f, alpha = 0.18f)
    drawRect(Color(0xFFD9D2C6), Offset(0f, 248f), Size(316f, 6f), alpha = 0.55f)
    // Window at night, with the ambulance lights outside
    part(rr(14f, 26f, 66f, 90f, 4f), tone(0xFFD8D0C4, 0xFFA99F92), 3f, hl(30f, 32f, 14f, 4f), 0.18f)
    drawRoundRect(Brush.verticalGradient(listOf(Color(0xFF0D1C36), Color(0xFF1B2F55)), 33f, 109f), Offset(21f, 33f), Size(52f, 76f), CornerRadius(2f))
    drawRect(Color(0xFFCFC7BA), Offset(45.5f, 33f), Size(3f, 76f))
    drawRect(Color(0xFFCFC7BA), Offset(21f, 69f), Size(52f, 3f))
    for (i in 0..7) drawCircle(Color.White, 0.8f, Offset(24f + ((i * 37) % 48), 38f + ((i * 23) % 30)), alpha = 0.7f)
    soft(Color(0xFFFF3030), 0.4f, 28f, 96f, 18f, 18f, 12f)
    soft(Color(0xFF3A6BFF), 0.4f, 66f, 92f, 18f, 18f, 12f)
    // Picture
    part(rr(236f, 40f, 58f, 42f, 3f), tone(0xFF8A6C48, 0xFF5E4630), 3f, hl(250f, 44f, 10f, 3f))
    drawRoundRect(Color(0xFF6F8F68), Offset(242f, 46f), Size(46f, 30f), CornerRadius(1f))
    drawCircle(Color(0xFFE6D49A), 5f, Offset(276f, 55f))
    drawPath(pth { m(242f, 76f); l(258f, 60f); l(270f, 70f); l(278f, 64f); l(288f, 76f); z() }, Color(0xFF4F6E4A))
    // Lamp
    soft(Color(0xFFFFCF7A), 0.5f, 292f, 150f, 40f, 40f, 30f)
    part(rr(276f, 200f, 40f, 52f, 4f), tone(0xFF6A4C36, 0xFF46301F), 3f, hl(286f, 204f, 8f, 3f))
    part(cap(296f, 198f, 296f, 168f, 5f), tone(0xFFC9B998, 0xFF8D7F66), 1.5f)
    part(pth { m(280f, 168f); l(312f, 168f); l(304f, 140f); l(288f, 140f); z() }, tone(0xFFFBE9C6, 0xFFE0BF86), 3f, hl(292f, 146f, 5f, 3f))
    // Couch
    val couch = tone(0xFF4E5868, 0xFF2C323E)
    soft(Color.Black, 0.35f, 158f, 262f, 132f, 9f, 5f)
    part(rr(40f, 124f, 236f, 96f, 10f), couch, 8f, hl(90f, 132f, 26f, 5f))
    part(rr(56f, 198f, 204f, 46f, 7f), couch, 5f, hl(100f, 203f, 24f, 3f))
    part(rr(22f, 166f, 46f, 90f, 10f), couch, 5f, hl(38f, 172f, 7f, 3f))
    part(rr(248f, 166f, 46f, 90f, 10f), couch, 5f, hl(262f, 172f, 7f, 3f))
    part(rr(44f, 236f, 228f, 22f, 5f), Tone(dk(couch.base, 0.9f), dk(couch.dark, 0.8f)), 4f)
    drawRoundRect(Color(0xFF2A2018), Offset(58f, 256f), Size(8f, 8f), CornerRadius(2f))
    drawRoundRect(Color(0xFF2A2018), Offset(250f, 256f), Size(8f, 8f), CornerRadius(2f))
}

// The patient sitting on a couch at home, in a 316 x 320 box
fun DrawScope.paintedPatient(pl: PatientLook) {
    val o = pl.look
    val shirt = PatientShirts[pl.shirt.coerceIn(0, PatientShirts.size - 1)]
    val pants = PatientPants[pl.pants.coerceIn(0, PatientPants.size - 1)]
    val skin = o.skin
    room()
    val rx = 26f
    val ry = rx * 1.16f
    val cx = 158f
    val cy = 122f - ry
    val n = cy + ry + 8f
    val hs = 26f / 40f
    soft(Color.Black, 0.3f, 160f, 226f, 56f, 10f, 6f)
    // Long hair hangs behind the shoulders
    scale(hs, Offset(cx, cy)) { hairBehind(o, cx, cy, 40f, 40f * 1.16f) }
    // Legs
    part(cap(cx - 14f, 250f, cx - 15f, 300f, 18f), pants, 4f)
    part(cap(cx + 14f, 250f, cx + 15f, 300f, 18f), pants, 4f)
    val shoe = tone(0xFF3A2C26, 0xFF1C1410)
    part(ell(cx - 17f, 306f, 11f, 6f), shoe, 2.5f, hl(cx - 20f, 302f, 4f, 2f))
    part(ell(cx + 17f, 306f, 11f, 6f), shoe, 2.5f, hl(cx + 14f, 302f, 4f, 2f))
    part(rr(cx - 31f, 204f, 62f, 32f, 13f), pants, 5f, hl(cx - 14f, 209f, 12f, 4f))
    part(cap(cx - 15f, 224f, cx - 14f, 248f, 26f), pants, 5f, hl(cx - 19f, 232f, 5f, 5f))
    part(cap(cx + 15f, 224f, cx + 14f, 248f, 26f), pants, 5f, hl(cx + 11f, 232f, 5f, 5f))
    // Neck and body
    part(rr(cx - 8f, cy + ry - 14f, 16f, 26f, 7f), skin, 4f)
    part(pth {
        m(cx - 12f, n); q(cx - 23.9f, n + 4f, cx - 34.2f, n + 8f); q(cx - 41f, n + 12f, cx - 40.2f, n + 24f)
        l(cx - 35.9f, n + 60f); q(cx - 34.2f, n + 80f, cx - 32.5f, 222f); l(cx + 32.5f, 222f)
        q(cx + 34.2f, n + 80f, cx + 35.9f, n + 60f); l(cx + 40.2f, n + 24f); q(cx + 41f, n + 12f, cx + 34.2f, n + 8f)
        q(cx + 23.9f, n + 4f, cx + 12f, n); q(cx, n + 5f, cx - 12f, n); z()
    }, shirt, 6f, hl(cx - 18.8f, n + 14f, 12f, 7f))
    line(pth { m(cx - 7.7f, n + 1f); q(cx, n + 9f, cx + 7.7f, n + 1f) }, dk(shirt.dark, 0.7f), 2.5f, 0.8f)
    // The arm on the viewer's right rests in the lap
    part(cap(cx + 35.9f, n + 14f, cx + 40.2f, n + 40f, 19f), shirt, 4f, hl(cx + 35.1f, n + 16f, 4f, 5f))
    part(cap(cx + 40.2f, n + 40f, cx + 42.8f, n + 64f, 13f), skin, 3f)
    part(cap(cx + 42.8f, n + 64f, cx + 20f, 236f, 12f), skin, 3f, hl(cx + 39.3f, n + 74f, 2f, 5f))
    part(ell(cx + 17f, 240f, 9f, 7f), skin, 2.5f, hl(cx + 14f, 237f, 3f, 2f))
    // Head, drawn at bust size and scaled down
    scale(hs, Offset(cx, cy)) { paintedHead(o, cx, cy, 40f, 1.16f, 7f) }
    // The other arm: in the lap, at the throat, or on the chest
    part(cap(cx - 35.9f, n + 14f, cx - 40.2f, n + 40f, 19f), shirt, 4f, hl(cx - 39.3f, n + 16f, 4f, 5f))
    when (pl.hand) {
        HAND_THROAT -> {
            part(cap(cx - 40.2f, n + 40f, cx - 41f, n + 62f, 13f), skin, 3f)
            part(cap(cx - 41f, n + 62f, cx - 9.4f, cy + ry + 7f, 12f), skin, 3f, hl(cx - 34.2f, n + 52f, 2f, 5f))
            part(ell(cx - 7f, cy + ry + 5f, 8f, 7f), skin, 2.5f, hl(cx - 10f, cy + ry + 2f, 3f, 2f))
            for (f in 0..2) part(cap(cx - 4f, cy + ry + 1f + f * 3.2f, cx + 3f, cy + ry - 1f + f * 3.6f, 3.3f), skin, 1f)
        }
        HAND_CHEST -> {
            part(cap(cx - 40.2f, n + 40f, cx - 41f, n + 62f, 13f), skin, 3f)
            part(cap(cx - 41f, n + 62f, cx - 12f, n + 26f, 12f), skin, 3f, hl(cx - 34.2f, n + 52f, 2f, 5f))
            part(ell(cx - 9f, n + 24f, 9f, 7.5f), skin, 2.5f, hl(cx - 12f, n + 21f, 3f, 2f))
            for (f in 0..2) part(cap(cx - 6f, n + 20f + f * 3.4f, cx + 3f, n + 18f + f * 3.8f, 3.3f), skin, 1f)
        }
        else -> {
            part(cap(cx - 40.2f, n + 40f, cx - 42.8f, n + 64f, 13f), skin, 3f)
            part(cap(cx - 42.8f, n + 64f, cx - 20f, 236f, 12f), skin, 3f, hl(cx - 39.3f, n + 74f, 2f, 5f))
            part(ell(cx - 17f, 240f, 9f, 7f), skin, 2.5f, hl(cx - 20f, 237f, 3f, 2f))
        }
    }
    // Room light: a dark edge, a cool night tint and the warm lamp
    drawRect(
        Brush.radialGradient(
            0.5f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.6f),
            center = Offset(158f, 144f), radius = 240f
        ),
        Offset.Zero, Size(316f, 320f)
    )
    drawRect(Color(0xFF2A3A70), Offset.Zero, Size(316f, 320f), alpha = 0.12f, blendMode = BlendMode.Multiply)
    drawCircle(
        Brush.radialGradient(
            0f to Color(0xFFFFCF7A).copy(alpha = 0.28f), 1f to Color.Transparent,
            center = Offset(300f, 150f), radius = 140f
        ),
        140f, Offset(300f, 150f), blendMode = BlendMode.Screen
    )
}
