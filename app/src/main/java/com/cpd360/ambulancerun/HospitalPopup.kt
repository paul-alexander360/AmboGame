package com.cpd360.ambulancerun

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.draw.shadow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// kinds: 0 cardiac, 1 respiratory, 2 medical, 3 neuro, 4 trauma
fun certsFor(level: Int): IntArray = when (level) {
    4 -> intArrayOf(0, 1, 2, 3, 4)
    3 -> intArrayOf(1, 2, 3)
    2 -> intArrayOf(1, 2)
    else -> intArrayOf(2)
}

private val certNames = listOf("Heart", "Breathing", "Medical", "Stroke", "Trauma")

// Double tap a hospital: a night photo, its level and what it treats. Tap to close.
@Composable
fun HospitalPopup(h: Hospital, onClose: () -> Unit) {
    val treats = certsFor(h.level)
    Column(
        modifier = Modifier
            .width(320.dp)
            .shadow(20.dp, RoundedCornerShape(28.dp))
            .clip(RoundedCornerShape(28.dp))
            .background(Color(0xEB141820))
            .border(1.dp, Color(0x24FFFFFF), RoundedCornerShape(28.dp))
            .clickable { onClose() }
    ) {
        Canvas(Modifier.fillMaxWidth().height(140.dp)) { hospitalPhoto() }
        Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(8.dp).background(Color(0xFF2FBF5F), CircleShape))
                Spacer(Modifier.width(6.dp))
                Text(
                    "HOSPITAL · LEVEL " + h.level, color = GlassMute, fontSize = 11.sp,
                    fontWeight = FontWeight.Bold, letterSpacing = 1.4.sp
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(h.name, color = InkText, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
            Spacer(Modifier.height(10.dp))
            FlowRowCerts(treats)
            Spacer(Modifier.height(10.dp))
            Text("Tap to close", color = MuteText, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FlowRowCerts(treats: IntArray) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        for (k in certNames.indices) {
            val yes = treats.contains(k)
            Row(
                Modifier
                    .background(if (yes) Color(0x2E2FBF5F) else Color(0x0DFFFFFF), RoundedCornerShape(10.dp))
                    .border(1.dp, if (yes) Color(0x802FBF5F) else Color(0x1FFFFFFF), RoundedCornerShape(10.dp))
                    .padding(horizontal = 9.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    (if (yes) "✓ " else "") + certNames[k],
                    color = if (yes) Color(0xFF7CF29A) else Color(0x73CFD5E2),
                    fontSize = 12.sp, fontWeight = FontWeight.Bold,
                    textDecoration = if (yes) null else TextDecoration.LineThrough
                )
            }
        }
    }
}

// The hospital at night: lit windows, a glowing H sign and the emergency entrance
private fun DrawScope.hospitalPhoto() {
    val sc = maxOf(size.width / 318f, size.height / 150f)
    translate((size.width - 318f * sc) / 2f, (size.height - 150f * sc) / 2f) {
        scale(sc, Offset.Zero) {
            drawRect(Brush.verticalGradient(listOf(Color(0xFF0B1424), Color(0xFF1D2A44)), 0f, 150f), Offset.Zero, Size(318f, 150f))
            for (i in 0..29) drawCircle(Color.White, if (i % 3 != 0) 0.7f else 1.1f, Offset(((i * 53) % 318).toFloat(), ((i * 29) % 50).toFloat()), alpha = 0.7f)
            drawRect(Color(0xFFC9CCD2), Offset(60f, 30f), Size(200f, 110f))
            drawRect(Color.Black, Offset(60f, 30f), Size(200f, 110f), alpha = 0.25f)
            drawRect(Color(0xFFB5B8BF), Offset(40f, 70f), Size(60f, 70f))
            drawRect(Color(0xFFAEB1B8), Offset(220f, 60f), Size(70f, 80f))
            for (i in 0..39) {
                val x = 70f + (i % 10) * 18f
                val y = 40f + (i / 10) * 20f
                drawRect(if ((i * 7) % 5 != 0) Color(0xFFFFD98A) else Color(0xFF2A3446), Offset(x, y), Size(10f, 12f), alpha = 0.9f)
            }
            drawCircle(
                Brush.radialGradient(listOf(Color(0x99FF4040), Color.Transparent), Offset(160f, 25f), 50f),
                50f, Offset(160f, 25f)
            )
            drawRoundRect(Color(0xFFE53935), Offset(130f, 14f), Size(60f, 22f), CornerRadius(4f))
            drawRect(Color.White, Offset(150f, 18f), Size(4f, 14f))
            drawRect(Color.White, Offset(166f, 18f), Size(4f, 14f))
            drawRect(Color.White, Offset(150f, 23.5f), Size(20f, 3f))
            drawRect(Color(0xFF1A1D24), Offset(120f, 112f), Size(80f, 28f))
            drawRect(Color(0xFFE53935), Offset(120f, 108f), Size(80f, 6f))
            for (k in 0..5) drawRect(Color(0xFFFF8A80), Offset(130f + k * 11f, 122f), Size(7f, 5f), alpha = 0.8f)
            drawRect(Color(0xFF2A2D33), Offset(0f, 138f), Size(318f, 12f))
            drawCircle(Brush.radialGradient(listOf(Color(0x593A6BFF), Color.Transparent), Offset(60f, 120f), 45f), 45f, Offset(60f, 120f))
            drawCircle(Brush.radialGradient(listOf(Color(0x4DFF3030), Color.Transparent), Offset(260f, 120f), 45f), 45f, Offset(260f, 120f))
        }
    }
}

@Composable
fun CertIcon(kind: Int) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(Brush.verticalGradient(listOf(Color(0xFF2AA2FF), Color(0xFF0B4FB8))))
            .border(2.dp, Color(0xFF0B5BD3), RoundedCornerShape(10.dp)),
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.size(30.dp)) { drawCert(kind, size.width) }
    }
}

private fun DrawScope.drawCert(kind: Int, s: Float) {
    val w = Color.White
    val bl = Color(0xFF0B4FB8)
    when (kind) {
        0 -> {
            val h = Path()
            h.moveTo(s * 0.5f, s * 0.86f)
            h.cubicTo(s * 0.08f, s * 0.56f, s * 0.04f, s * 0.24f, s * 0.28f, s * 0.2f)
            h.cubicTo(s * 0.4f, s * 0.18f, s * 0.48f, s * 0.26f, s * 0.5f, s * 0.32f)
            h.cubicTo(s * 0.52f, s * 0.26f, s * 0.6f, s * 0.18f, s * 0.72f, s * 0.2f)
            h.cubicTo(s * 0.96f, s * 0.24f, s * 0.92f, s * 0.56f, s * 0.5f, s * 0.86f)
            h.close()
            drawPath(h, w)
            val e = Path()
            e.moveTo(s * 0.1f, s * 0.5f)
            e.lineTo(s * 0.36f, s * 0.5f)
            e.lineTo(s * 0.44f, s * 0.3f)
            e.lineTo(s * 0.56f, s * 0.7f)
            e.lineTo(s * 0.64f, s * 0.5f)
            e.lineTo(s * 0.9f, s * 0.5f)
            drawPath(e, bl, style = Stroke(s * 0.05f, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
        1 -> {
            val l = Path()
            l.moveTo(s * 0.42f, s * 0.3f)
            l.cubicTo(s * 0.3f, s * 0.3f, s * 0.12f, s * 0.5f, s * 0.15f, s * 0.78f)
            l.cubicTo(s * 0.17f, s * 0.88f, s * 0.28f, s * 0.86f, s * 0.4f, s * 0.78f)
            l.cubicTo(s * 0.44f, s * 0.74f, s * 0.44f, s * 0.5f, s * 0.42f, s * 0.3f)
            l.close()
            drawPath(l, w)
            val r = Path()
            r.moveTo(s * 0.58f, s * 0.3f)
            r.cubicTo(s * 0.7f, s * 0.3f, s * 0.88f, s * 0.5f, s * 0.85f, s * 0.78f)
            r.cubicTo(s * 0.83f, s * 0.88f, s * 0.72f, s * 0.86f, s * 0.6f, s * 0.78f)
            r.cubicTo(s * 0.56f, s * 0.74f, s * 0.56f, s * 0.5f, s * 0.58f, s * 0.3f)
            r.close()
            drawPath(r, w)
            drawRoundRect(w, Offset(s * 0.47f, s * 0.1f), Size(s * 0.06f, s * 0.3f), CornerRadius(s * 0.02f, s * 0.02f))
        }
        2 -> {
            val st = Stroke(s * 0.06f, cap = StrokeCap.Round, join = StrokeJoin.Round)
            val t = Path()
            t.moveTo(s * 0.3f, s * 0.16f)
            t.cubicTo(s * 0.2f, s * 0.36f, s * 0.3f, s * 0.6f, s * 0.5f, s * 0.6f)
            t.cubicTo(s * 0.7f, s * 0.6f, s * 0.8f, s * 0.36f, s * 0.7f, s * 0.16f)
            drawPath(t, w, style = st)
            val u = Path()
            u.moveTo(s * 0.5f, s * 0.6f)
            u.lineTo(s * 0.5f, s * 0.72f)
            u.cubicTo(s * 0.5f, s * 0.92f, s * 0.78f, s * 0.92f, s * 0.82f, s * 0.7f)
            drawPath(u, w, style = st)
            drawCircle(w, s * 0.04f, Offset(s * 0.3f, s * 0.13f))
            drawCircle(w, s * 0.04f, Offset(s * 0.7f, s * 0.13f))
            drawCircle(w, s * 0.09f, Offset(s * 0.82f, s * 0.62f), style = Stroke(s * 0.04f))
            drawCircle(w, s * 0.03f, Offset(s * 0.82f, s * 0.62f))
        }
        3 -> {
            drawCircle(w, s * 0.16f, Offset(s * 0.33f, s * 0.42f))
            drawCircle(w, s * 0.17f, Offset(s * 0.5f, s * 0.3f))
            drawCircle(w, s * 0.16f, Offset(s * 0.67f, s * 0.42f))
            drawCircle(w, s * 0.17f, Offset(s * 0.56f, s * 0.54f))
            drawCircle(w, s * 0.15f, Offset(s * 0.4f, s * 0.54f))
            drawRoundRect(w, Offset(s * 0.55f, s * 0.6f), Size(s * 0.1f, s * 0.26f), CornerRadius(s * 0.04f, s * 0.04f))
            val f = Path()
            f.moveTo(s * 0.35f, s * 0.5f)
            f.cubicTo(s * 0.42f, s * 0.4f, s * 0.5f, s * 0.46f, s * 0.5f, s * 0.56f)
            drawPath(f, bl, style = Stroke(s * 0.025f, cap = StrokeCap.Round))
        }
        else -> {
            rotate(-40f, Offset(s * 0.5f, s * 0.5f)) {
                drawRoundRect(w, Offset(s * 0.12f, s * 0.45f), Size(s * 0.34f, s * 0.1f), CornerRadius(s * 0.02f, s * 0.02f))
                drawCircle(w, s * 0.07f, Offset(s * 0.14f, s * 0.43f))
                drawCircle(w, s * 0.07f, Offset(s * 0.14f, s * 0.57f))
                drawRoundRect(w, Offset(s * 0.56f, s * 0.45f), Size(s * 0.32f, s * 0.1f), CornerRadius(s * 0.02f, s * 0.02f))
                drawCircle(w, s * 0.07f, Offset(s * 0.86f, s * 0.43f))
                drawCircle(w, s * 0.07f, Offset(s * 0.86f, s * 0.57f))
            }
            val bs = Stroke(s * 0.035f, cap = StrokeCap.Round)
            val b = Path()
            b.moveTo(s * 0.3f, s * 0.2f)
            b.lineTo(s * 0.36f, s * 0.3f)
            b.moveTo(s * 0.5f, s * 0.14f)
            b.lineTo(s * 0.5f, s * 0.26f)
            b.moveTo(s * 0.7f, s * 0.76f)
            b.lineTo(s * 0.78f, s * 0.86f)
            drawPath(b, w, style = bs)
        }
    }
}
