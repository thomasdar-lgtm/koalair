package com.koala.air

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.sin

enum class G { Snow, Sun, Drop, Fan, Auto, Power, Leaf, Mute, Bolt, Moon, Swing, Bulb, Thermo, Plus, Minus, Back, Refresh }

/** Icônes dessinées (grille 24×24), pour ne dépendre d'aucune bibliothèque d'icônes. */
@Composable
fun Glyph(g: G, color: Color, size: Dp = 24.dp, modifier: Modifier = Modifier, spin: Boolean = false) {
    val angle = if (spin) {
        val t = rememberInfiniteTransition(label = "spin")
        val a by t.animateFloat(0f, 360f, infiniteRepeatable(tween(1600, easing = LinearEasing), RepeatMode.Restart), label = "a")
        a
    } else 0f
    Canvas(modifier.size(size)) {
        val k = this.size.minDimension / 24f
        val st = Stroke(width = 2f * k, cap = StrokeCap.Round, join = StrokeJoin.Round)
        fun p(x: Float, y: Float) = Offset(x * k, y * k)
        fun line(x1: Float, y1: Float, x2: Float, y2: Float) =
            drawLine(color, p(x1, y1), p(x2, y2), strokeWidth = st.width, cap = StrokeCap.Round)
        fun path(block: Path.() -> Unit) = drawPath(Path().apply(block), color, style = st)
        fun Path.m(x: Float, y: Float) = moveTo(x * k, y * k)
        fun Path.l(x: Float, y: Float) = lineTo(x * k, y * k)
        fun Path.q(x1: Float, y1: Float, x: Float, y: Float) = quadraticTo(x1 * k, y1 * k, x * k, y * k)

        when (g) {
            G.Snow -> {
                for (i in 0 until 3) rotate(i * 60f) {
                    line(12f, 2.5f, 12f, 21.5f)
                    line(9.5f, 4.5f, 12f, 7f); line(14.5f, 4.5f, 12f, 7f)
                    line(9.5f, 19.5f, 12f, 17f); line(14.5f, 19.5f, 12f, 17f)
                }
            }
            G.Sun -> {
                drawCircle(color, 4.5f * k, p(12f, 12f), style = st)
                for (i in 0 until 8) {
                    val a = Math.toRadians(i * 45.0)
                    line(12f + 7.5f * cos(a).toFloat(), 12f + 7.5f * sin(a).toFloat(), 12f + 10f * cos(a).toFloat(), 12f + 10f * sin(a).toFloat())
                }
            }
            G.Drop -> path {
                m(12f, 3f); q(19f, 11f, 18f, 15f); q(17f, 21f, 12f, 21f); q(7f, 21f, 6f, 15f); q(5f, 11f, 12f, 3f); close()
            }
            G.Fan -> rotate(angle) {
                drawCircle(color, 1.8f * k, p(12f, 12f))
                for (i in 0 until 3) rotate(i * 120f) {
                    path { m(12f, 10f); q(10f, 3f, 14.5f, 2.5f); q(18f, 4f, 13.5f, 10.5f) }
                }
            }
            G.Auto -> {
                path { m(6f, 19f); l(12f, 5f); l(18f, 19f) }
                line(8.6f, 13.5f, 15.4f, 13.5f)
            }
            G.Power -> {
                drawArc(color, -60f, 300f, false, p(4.5f, 4.5f), Size(15f * k, 15f * k), style = st)
                line(12f, 2.5f, 12f, 11f)
            }
            G.Leaf -> {
                path { m(5f, 19f); q(4f, 6f, 19f, 4f); q(20f, 18f, 5f, 19f); close() }
                line(5f, 19f, 13f, 11f)
            }
            G.Mute -> {
                path { m(3f, 9f); l(7f, 9f); l(12f, 4.5f); l(12f, 19.5f); l(7f, 15f); l(3f, 15f); close() }
                line(15.5f, 9.5f, 20.5f, 14.5f); line(20.5f, 9.5f, 15.5f, 14.5f)
            }
            G.Bolt -> path { m(13.5f, 2.5f); l(5f, 13.5f); l(11.5f, 13.5f); l(10.5f, 21.5f); l(19f, 10.5f); l(12.5f, 10.5f); close() }
            G.Moon -> path { m(19.5f, 14.5f); q(17f, 20f, 11f, 20f); q(4f, 19f, 4f, 12f); q(4.5f, 6f, 10f, 4f); q(7.5f, 11f, 12f, 14.5f); q(15.5f, 17f, 19.5f, 14.5f); close() }
            G.Swing -> {
                path { m(4f, 8f); q(12f, 2f, 20f, 8f) }
                path { m(4f, 16f); q(12f, 22f, 20f, 16f) }
                line(12f, 7f, 12f, 17f)
                path { m(9.5f, 9.5f); l(12f, 7f); l(14.5f, 9.5f) }
                path { m(9.5f, 14.5f); l(12f, 17f); l(14.5f, 14.5f) }
            }
            G.Bulb -> {
                path { m(9f, 16f); q(9f, 14f, 7.5f, 12.5f); q(5f, 9.5f, 7f, 6f); q(9f, 3f, 12f, 3f); q(15f, 3f, 17f, 6f); q(19f, 9.5f, 16.5f, 12.5f); q(15f, 14f, 15f, 16f); close() }
                line(9.5f, 19f, 14.5f, 19f); line(10.5f, 21.5f, 13.5f, 21.5f)
            }
            G.Thermo -> {
                path { m(10f, 14f); l(10f, 5f); q(10f, 3f, 12f, 3f); q(14f, 3f, 14f, 5f); l(14f, 14f) }
                drawCircle(color, 3.8f * k, p(12f, 17.5f), style = st)
                drawCircle(color, 1.6f * k, p(12f, 17.5f))
            }
            G.Plus -> { line(12f, 5f, 12f, 19f); line(5f, 12f, 19f, 12f) }
            G.Minus -> line(5f, 12f, 19f, 12f)
            G.Back -> path { m(15f, 4.5f); l(7.5f, 12f); l(15f, 19.5f) }
            G.Refresh -> {
                drawArc(color, -30f, 290f, false, p(4.5f, 4.5f), Size(15f * k, 15f * k), style = st)
                path { m(15.5f, 3.5f); l(19.5f, 5.5f); l(17.5f, 9.5f) }
            }
        }
    }
}
