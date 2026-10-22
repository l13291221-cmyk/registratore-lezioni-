package com.animefit.ui.components

import android.graphics.BlurMaskFilter
import androidx.compose.foundation.layout.Spacer
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import com.animefit.catalog.Area
import com.animefit.data.Measure
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/** Proporzioni del corpo in unità "lunghezza del busto". */
data class FigureSpec(
    val shoulders: Float,
    val chest: Float,
    val waist: Float,
    val hips: Float,
    val arm: Float,
    val forearm: Float,
    val thigh: Float,
    val calf: Float,
    val definition: Float,
) {
    companion object {
        fun from(measures: Map<Measure, Double>, fallback: Map<Measure, Double>): FigureSpec {
            fun v(m: Measure) = (measures[m] ?: fallback[m] ?: 0.5).toFloat()
            return FigureSpec(
                v(Measure.SPALLE), v(Measure.TORACE), v(Measure.VITA), v(Measure.FIANCHI),
                v(Measure.BRACCIO), v(Measure.AVAMBRACCIO), v(Measure.COSCIA), v(Measure.POLPACCIO),
                v(Measure.DEFINIZIONE),
            )
        }
    }
}

class FigureShape(
    val parts: List<Pair<Area?, Path>>,
    val outline: Path,
    val details: Path,
)

/** Riquadro della figura in unità busto: serve per inquadrarla. */
private val BOUNDS = Rect(-1.34f, -0.78f, 1.34f, 2.92f)

/** Trasforma unità busto → pixel. */
private class Mapper(val scale: Float, val ox: Float, val oy: Float) {
    fun p(x: Float, y: Float) = Offset(ox + x * scale, oy + y * scale)
    fun w(v: Float) = v * scale
}

private const val ARM_ANGLE_DEG = 38.0

fun buildFigure(s: FigureSpec, scale: Float, origin: Offset): FigureShape {
    val m = Mapper(scale, origin.x, origin.y)
    val parts = mutableListOf<Pair<Area?, Path>>()

    val dr = 0.55f * s.arm
    val jx = s.shoulders / 2f - dr * 0.95f
    val nw = 0.12f + 0.10f * (s.shoulders - 0.9f).coerceAtLeast(0f)
    val c2 = s.chest / 2f
    val w2 = s.waist / 2f
    val h2 = s.hips / 2f

    // Busto.
    val torso = Path().apply {
        moveTo(m.p(-nw, -0.10f))
        quadTo(m.p(-(nw + 0.10f), -0.11f), m.p(-jx, -0.05f))
        cubicTo(m.p(-(jx + 0.02f), 0.08f), m.p(-c2, 0.15f), m.p(-c2, 0.26f))
        cubicTo(m.p(-c2, 0.42f), m.p(-w2, 0.55f), m.p(-w2, 0.70f))
        cubicTo(m.p(-w2, 0.82f), m.p(-h2, 0.88f), m.p(-h2, 0.98f))
        quadTo(m.p(-h2, 1.10f), m.p(0f, 1.14f))
        quadTo(m.p(h2, 1.10f), m.p(h2, 0.98f))
        cubicTo(m.p(h2, 0.88f), m.p(w2, 0.82f), m.p(w2, 0.70f))
        cubicTo(m.p(w2, 0.55f), m.p(c2, 0.42f), m.p(c2, 0.26f))
        cubicTo(m.p(c2, 0.15f), m.p(jx + 0.02f, 0.08f), m.p(jx, -0.05f))
        quadTo(m.p(nw + 0.10f, -0.11f), m.p(nw, -0.10f))
        close()
    }
    val upperRect = Path().apply { addRect(Rect(m.p(-3f, -1f), m.p(3f, 0.42f))) }
    val lowerRect = Path().apply { addRect(Rect(m.p(-3f, 0.42f), m.p(3f, 1.3f))) }
    parts += Area.TORACE to Path.combine(PathOperation.Intersect, torso, upperRect)
    parts += Area.ADDOMINALI to Path.combine(PathOperation.Intersect, torso, lowerRect)

    // Testa e collo.
    val head = Path().apply { addOval(Rect(m.p(-0.17f, -0.74f), m.p(0.17f, -0.30f))) }
    val neck = Path().apply {
        addRect(Rect(m.p(-nw * 0.85f, -0.36f), m.p(nw * 0.85f, -0.06f)))
    }
    parts += null to head
    parts += null to neck

    val angle = Math.toRadians(ARM_ANGLE_DEG)
    for (side in listOf(-1f, 1f)) {
        val dx = (sin(angle) * side).toFloat()
        val dy = cos(angle).toFloat()
        // Deltoide.
        parts += Area.SPALLE to Path().apply {
            addOval(Rect(m.p(side * jx - dr, 0.04f - dr * 1.15f), m.p(side * jx + dr, 0.04f + dr * 1.15f)))
        }
        val j = Offset(side * jx, 0.05f)
        val e = Offset(j.x + dx * 0.62f, j.y + dy * 0.62f)
        val wr = Offset(e.x + dx * 0.56f, e.y + dy * 0.56f)
        parts += Area.BRACCIA to limb(m, j, e, s.arm, s.arm, 0.35f, s.arm * 0.7f)
        parts += Area.AVAMBRACCI to limb(
            m, e, wr, maxOf(s.forearm * 0.85f, s.arm * 0.65f), s.forearm, 0.25f, s.forearm * 0.5f,
        )
        val hand = Offset(wr.x + dx * 0.09f, wr.y + dy * 0.09f)
        parts += null to Path().apply { addOval(Rect(m.p(hand.x - 0.06f, hand.y - 0.08f), m.p(hand.x + 0.06f, hand.y + 0.08f))) }

        // Gamba.
        val hj = Offset(side * h2 * 0.5f, 0.95f)
        val knee = Offset(hj.x + side * 0.06f, 1.93f)
        val ankle = Offset(hj.x + side * 0.10f, 2.81f)
        parts += Area.GAMBE to limb(m, hj, knee, s.thigh * 1.05f, s.thigh, 0.25f, s.thigh * 0.55f)
        parts += Area.POLPACCI to limb(m, knee, ankle, s.thigh * 0.55f, s.calf, 0.30f, s.calf * 0.45f)
        val foot = Offset(ankle.x + side * 0.04f, ankle.y + 0.06f)
        parts += null to Path().apply { addOval(Rect(m.p(foot.x - 0.09f, foot.y - 0.05f), m.p(foot.x + 0.09f, foot.y + 0.05f))) }
    }

    var outline = Path().apply { addPath(torso) }
    for ((_, p) in parts) outline = Path.combine(PathOperation.Union, outline, p)

    // Linee dei muscoli: più visibili quanto più il fisico è definito.
    val details = Path().apply {
        for (side in listOf(-1f, 1f)) {
            moveTo(m.p(side * c2 * 0.88f, 0.27f))
            quadTo(m.p(side * c2 * 0.55f, 0.46f), m.p(side * 0.02f, 0.40f))
            moveTo(m.p(side * w2 * 0.80f, 0.78f))
            quadTo(m.p(side * w2 * 0.45f, 0.95f), m.p(side * 0.06f, 1.03f))
        }
        moveTo(m.p(0f, 0.14f)); lineTo(m.p(0f, 0.92f))
        for (y in listOf(0.52f, 0.63f, 0.74f)) {
            val hw = w2 * 0.42f
            moveTo(m.p(-hw, y))
            quadTo(m.p(0f, y + 0.03f), m.p(hw, y))
        }
    }
    return FigureShape(parts, outline, details)
}

/** Arto affusolato con estremità arrotondate. */
private fun limb(m: Mapper, a: Offset, b: Offset, w0: Float, wm: Float, tm: Float, w1: Float): Path {
    val len = (b - a).getDistance()
    val d = (b - a) / len
    val n = Offset(-d.y, d.x)
    fun width(t: Float): Float {
        return if (t < tm) {
            val k = (sin((t / tm) * PI / 2)).toFloat()
            w0 + (wm - w0) * k
        } else {
            val k = (1 - cos(((t - tm) / (1 - tm)) * PI / 2)).toFloat()
            wm + (w1 - wm) * k
        }
    }
    val steps = 18
    val path = Path()
    for (i in 0..steps) {
        val t = i / steps.toFloat()
        val c = a + (b - a) * t
        val p = c + n * (width(t) / 2)
        if (i == 0) path.moveTo(m.p(p.x, p.y)) else path.lineTo(m.p(p.x, p.y))
    }
    for (i in 1 until 10) {
        val phi = PI * i / 10
        val p = b + n * (cos(phi).toFloat() * w1 / 2) + d * (sin(phi).toFloat() * w1 / 2)
        path.lineTo(m.p(p.x, p.y))
    }
    for (i in steps downTo 0) {
        val t = i / steps.toFloat()
        val c = a + (b - a) * t
        val p = c - n * (width(t) / 2)
        path.lineTo(m.p(p.x, p.y))
    }
    for (i in 1 until 10) {
        val phi = PI * i / 10
        val p = a - n * (cos(phi).toFloat() * w0 / 2) - d * (sin(phi).toFloat() * w0 / 2)
        path.lineTo(m.p(p.x, p.y))
    }
    path.close()
    return path
}

private fun Path.moveTo(o: Offset) = moveTo(o.x, o.y)
private fun Path.lineTo(o: Offset) = lineTo(o.x, o.y)
private fun Path.quadTo(c: Offset, e: Offset) = quadraticTo(c.x, c.y, e.x, e.y)
private fun Path.cubicTo(c1: Offset, c2: Offset, e: Offset) = cubicTo(c1.x, c1.y, c2.x, c2.y, e.x, e.y)

/**
 * Calcola scala e origine per far stare la figura nel riquadro [w]×[h],
 * occupando la frazione [fill] dell'altezza.
 */
fun figureFit(w: Float, h: Float, fill: Float = 0.96f): Pair<Float, Offset> {
    val scale = min(w / BOUNDS.width, h / BOUNDS.height) * fill
    val ox = w / 2f - BOUNDS.center.x * scale
    val oy = h / 2f - BOUNDS.center.y * scale
    return scale to Offset(ox, oy)
}

/**
 * Figura neon del fisico. Se [areaColors] è indicato, ogni zona è colorata
 * (mappa di calore); altrimenti la figura è riempita leggermente col colore del contorno.
 */
@Composable
fun PhysiqueFigure(
    spec: FigureSpec,
    color: Color,
    modifier: Modifier = Modifier,
    areaColors: Map<Area, Color>? = null,
    dashed: Boolean = false,
    fill: Float = 0.96f,
) {
    Spacer(
        modifier.drawWithCache {
            val (scale, origin) = figureFit(size.width, size.height, fill)
            val fig = buildFigure(spec, scale, origin)
            val strokePx = (scale * 0.018f).coerceIn(2f, 6f)
            val glowPaint = Paint().asFrameworkPaint().apply {
                isAntiAlias = true
                style = android.graphics.Paint.Style.STROKE
                strokeWidth = strokePx * 2.5f
                this.color = color.copy(alpha = 0.75f).toArgb()
                maskFilter = BlurMaskFilter(strokePx * 4f, BlurMaskFilter.Blur.NORMAL)
            }
            val dash = if (dashed) PathEffect.dashPathEffect(floatArrayOf(strokePx * 5, strokePx * 4)) else null
            val detailAlpha = (0.15f + 0.7f * spec.definition).coerceIn(0f, 0.9f)
            onDrawBehind {
                if (areaColors != null) {
                    for ((area, p) in fig.parts) {
                        val c = area?.let { areaColors[it] } ?: color
                        drawPath(p, c.copy(alpha = if (area == null) 0.12f else 0.45f))
                    }
                } else if (!dashed) {
                    drawPath(fig.outline, color.copy(alpha = 0.12f))
                }
                if (!dashed) {
                    drawIntoCanvas { it.nativeCanvas.drawPath(fig.outline.asAndroidPath(), glowPaint) }
                }
                drawPath(fig.outline, color, style = Stroke(strokePx, cap = StrokeCap.Round, pathEffect = dash))
                if (!dashed) {
                    drawPath(
                        fig.details, color.copy(alpha = detailAlpha),
                        style = Stroke(strokePx * 0.6f, cap = StrokeCap.Round),
                    )
                }
            }
        }
    )
}
