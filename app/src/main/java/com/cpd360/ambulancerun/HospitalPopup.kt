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

@Composable
fun HospitalPopup(h: Hospital, onClose: () -> Unit) {
    Column(
        modifier = Modifier
            .background(Color(0xF2FFFFFF), RoundedCornerShape(18.dp))
            .border(2.dp, Color(0xFF0B5BD3), RoundedCornerShape(18.dp))
            .clickable { onClose() }
            .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(h.name, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            for (k in certsFor(h.level)) CertIcon(k)
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
