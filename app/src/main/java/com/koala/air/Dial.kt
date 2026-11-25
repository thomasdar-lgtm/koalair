package com.koala.air

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

private const val START = 135f
private const val SWEEP = 270f

/** Température (16–32 °C) correspondant à un point touché sur l'anneau, ou null au centre. */
private fun tempAt(pos: Offset, w: Float, h: Float): Int? {
    val dx = pos.x - w / 2; val dy = pos.y - h / 2
    // Ignore le centre (le texte) : seul l'anneau règle la température
    if (dx * dx + dy * dy < (minOf(w, h) * 0.3f).let { it * it }) return null
    val a = Math.toDegrees(atan2((pos.y - h / 2).toDouble(), (pos.x - w / 2).toDouble())).toFloat()
    var rel = (a - START + 720f) % 360f
    if (rel > SWEEP) rel = if (rel > SWEEP + (360f - SWEEP) / 2) 0f else SWEEP
    return (Ac.MIN_TEMP + rel / SWEEP * (Ac.MAX_TEMP - Ac.MIN_TEMP)).roundToInt()
}

/** Cadran circulaire : glisser ou toucher l'anneau pour régler la consigne. */
@Composable
fun TempDial(temp: Int?, on: Boolean, accent: Color, modifier: Modifier = Modifier, onChange: (Int) -> Unit) {
    val current by rememberUpdatedState(temp)
    val change by rememberUpdatedState(onChange)
    val frac by animateFloatAsState(
        ((temp ?: Ac.MIN_TEMP) - Ac.MIN_TEMP).toFloat() / (Ac.MAX_TEMP - Ac.MIN_TEMP), tween(350), label = "dial",
    )
    Box(
        modifier
            .pointerInput(Unit) {
                detectTapGestures { p -> tempAt(p, size.width.toFloat(), size.height.toFloat())?.let { if (it != current) change(it) } }
            }
            .pointerInput(Unit) {
                detectDragGestures { ch, _ ->
                    tempAt(ch.position, size.width.toFloat(), size.height.toFloat())?.let { if (it != current) change(it) }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = 18.dp.toPx()
            val r = size.minDimension / 2 - stroke
            val tl = Offset(center.x - r, center.y - r)
            val sz = Size(r * 2, r * 2)
            // Graduations
            for (t in Ac.MIN_TEMP..Ac.MAX_TEMP) {
                val a = Math.toRadians((START + (t - Ac.MIN_TEMP) * SWEEP / (Ac.MAX_TEMP - Ac.MIN_TEMP)).toDouble())
                val r1 = r + stroke * 0.9f
                val r2 = r1 + (if (t % 2 == 0) 8.dp.toPx() else 4.dp.toPx())
                drawLine(
                    Muted.copy(alpha = if (t % 2 == 0) 0.6f else 0.3f),
                    Offset(center.x + r1 * cos(a).toFloat(), center.y + r1 * sin(a).toFloat()),
                    Offset(center.x + r2 * cos(a).toFloat(), center.y + r2 * sin(a).toFloat()),
                    strokeWidth = 1.5.dp.toPx(), cap = StrokeCap.Round,
                )
            }
            // Piste
            drawArc(CardHi, START, SWEEP, false, tl, sz, style = Stroke(stroke, cap = StrokeCap.Round))
            // Valeur
            val c1 = if (on) accent.copy(alpha = 0.35f) else Muted.copy(alpha = 0.2f)
            val c2 = if (on) accent else Muted.copy(alpha = 0.5f)
            drawArc(
                Brush.sweepGradient(listOf(c1, c2, c1), center), START, SWEEP * frac.coerceAtLeast(0.005f), false, tl, sz,
                style = Stroke(stroke, cap = StrokeCap.Round),
            )
            // Curseur
            val a = Math.toRadians((START + SWEEP * frac).toDouble())
            val k = Offset(center.x + r * cos(a).toFloat(), center.y + r * sin(a).toFloat())
            if (on) drawCircle(accent.copy(alpha = 0.25f), stroke * 1.1f, k)
            drawCircle(Color.White, stroke * 0.62f, k)
            drawCircle(c2, stroke * 0.36f, k)
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("CONSIGNE", color = Muted, fontSize = 11.sp, fontWeight = FontWeight.Black, letterSpacing = 1.5.sp)
            Row(verticalAlignment = Alignment.Top) {
                Text(temp?.toString() ?: "--", color = if (on) Color.White else Muted, fontSize = 76.sp, fontWeight = FontWeight.Black, lineHeight = 78.sp)
                Text("°C", color = if (on) accent else Muted, fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 12.dp))
            }
            Text(if (on) "Glisse sur l'anneau" else "Clim éteinte", color = Muted, fontSize = 12.sp)
        }
    }
}
