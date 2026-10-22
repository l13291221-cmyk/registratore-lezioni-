package com.animefit.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.animefit.data.Geometry
import com.animefit.ui.theme.Neon
import com.animefit.ui.theme.glowText
import java.io.File
import kotlin.math.min

/** Foto con le linee delle misure prese (spalle, vita, braccia...). */
@Composable
fun PhotoWithMeasures(file: File, geometry: Geometry, modifier: Modifier = Modifier, showLines: Boolean = true) {
    val img = rememberImage(file)
    Box(modifier.clipToBounds(), contentAlignment = Alignment.Center) {
        if (img == null) {
            Text("Caricamento…", style = glowText(Neon.TextDim, 14))
        } else {
            Canvas(Modifier.fillMaxSize()) {
                val s = min(size.width / geometry.imageWidth, size.height / geometry.imageHeight)
                val ox = (size.width - geometry.imageWidth * s) / 2f
                val oy = (size.height - geometry.imageHeight * s) / 2f
                withTransform({
                    translate(ox, oy)
                    scale(s, s, pivot = Offset.Zero)
                }) {
                    drawImage(img, dstSize = IntSize(geometry.imageWidth, geometry.imageHeight))
                }
                if (showLines) {
                    val w = 3.dp.toPx()
                    for (seg in geometry.segments) {
                        val c = measureColor(seg.measure)
                        val a = Offset(ox + seg.a.x * s, oy + seg.a.y * s)
                        val b = Offset(ox + seg.b.x * s, oy + seg.b.y * s)
                        drawLine(c.copy(alpha = 0.35f), a, b, strokeWidth = w * 3, cap = StrokeCap.Round)
                        drawLine(c, a, b, strokeWidth = w, cap = StrokeCap.Round)
                        drawCircle(c, w * 1.4f, a)
                        drawCircle(c, w * 1.4f, b)
                    }
                }
            }
        }
    }
}

enum class CompareMode(val label: String) { AFFIANCATE("Affiancate"), SCORRI("Scorri"), SAGOME("Sagome") }

private data class Placement(val scale: Float, val tx: Float, val ty: Float)

/** Allinea le foto sovrapponendo spalle e busto, così si confrontano a parità di distanza. */
private fun DrawScope.place(g: Geometry): Placement {
    val s = size.height * 0.22f / g.torsoPx
    return Placement(s, size.width / 2f - g.shoulderMid.x * s, size.height * 0.27f - g.shoulderMid.y * s)
}

private fun DrawScope.drawPlaced(img: ImageBitmap, g: Geometry, p: Placement, alpha: Float = 1f, tint: Color? = null) {
    withTransform({
        translate(p.tx, p.ty)
        scale(p.scale, p.scale, pivot = Offset.Zero)
    }) {
        drawImage(
            img,
            dstSize = IntSize(g.imageWidth, g.imageHeight),
            alpha = alpha,
            colorFilter = tint?.let { ColorFilter.tint(it, BlendMode.SrcIn) },
            blendMode = if (tint != null) BlendMode.Plus else BlendMode.SrcOver,
        )
    }
}

/**
 * Confronto tra due foto allineate: "Scorri" mostra prima/dopo con una
 * tendina trascinabile; "Sagome" sovrappone le sagome (ciano = prima,
 * magenta = ora, bianco = uguale).
 */
@Composable
fun AlignedCompare(
    before: Geometry, beforeFile: File, beforeMask: File,
    after: Geometry, afterFile: File, afterMask: File,
    mode: CompareMode,
    modifier: Modifier = Modifier,
) {
    val imgBefore = rememberImage(beforeFile)
    val imgAfter = rememberImage(afterFile)
    val maskBefore = rememberImage(beforeMask)
    val maskAfter = rememberImage(afterMask)
    var slider by remember { mutableFloatStateOf(0.5f) }

    Box(modifier.clipToBounds()) {
        Canvas(
            Modifier
                .fillMaxSize()
                .pointerInput(mode) {
                    detectHorizontalDragGestures { change, _ ->
                        slider = (change.position.x / size.width).coerceIn(0f, 1f)
                    }
                }
                .pointerInput(mode) {
                    detectTapGestures { slider = (it.x / size.width).coerceIn(0f, 1f) }
                }
        ) {
            drawRect(Neon.Black)
            val pb = place(before)
            val pa = place(after)
            when (mode) {
                CompareMode.SCORRI -> {
                    if (imgAfter != null) drawPlaced(imgAfter, after, pa)
                    val x = size.width * slider
                    clipRect(right = x) {
                        drawRect(Neon.Black)
                        if (imgBefore != null) drawPlaced(imgBefore, before, pb)
                    }
                    drawLine(Neon.Cyan.copy(alpha = 0.4f), Offset(x, 0f), Offset(x, size.height), 10.dp.toPx())
                    drawLine(Neon.Cyan, Offset(x, 0f), Offset(x, size.height), 3.dp.toPx())
                    drawCircle(Neon.Cyan, 12.dp.toPx(), Offset(x, size.height / 2))
                    drawCircle(Neon.Black, 8.dp.toPx(), Offset(x, size.height / 2))
                }
                CompareMode.SAGOME -> {
                    if (maskBefore != null) drawPlaced(maskBefore, before, pb, 0.9f, Neon.Cyan)
                    if (maskAfter != null) drawPlaced(maskAfter, after, pa, 0.9f, Neon.Magenta)
                }
                CompareMode.AFFIANCATE -> Unit
            }
        }
        if (mode == CompareMode.SCORRI) {
            Text("PRIMA", Modifier.align(Alignment.TopStart).padding(10.dp), style = glowText(Neon.Cyan, 14))
            Text("ORA", Modifier.align(Alignment.TopEnd).padding(10.dp), style = glowText(Neon.Magenta, 14))
        }
    }
}
