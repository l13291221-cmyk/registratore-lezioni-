package com.animefit.ui.theme

import android.graphics.BlurMaskFilter
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

object Neon {
    val Black = Color(0xFF000000)
    val Panel = Color(0xFF07070C)
    val Cyan = Color(0xFF00F5FF)
    val Magenta = Color(0xFFFF2BD6)
    val Green = Color(0xFF39FF14)
    val Yellow = Color(0xFFFFE600)
    val Orange = Color(0xFFFF9100)
    val Purple = Color(0xFFB026FF)
    val Red = Color(0xFFFF1744)
    val Blue = Color(0xFF2979FF)
    val Text = Color(0xFFF4F4FA)
    val TextDim = Color(0xFFA0A0B8)

    /** Rosso (lontano) → giallo → verde (raggiunto). */
    fun heat(score: Double): Color {
        val s = score.toFloat().coerceIn(0f, 1f)
        return if (s < 0.5f) lerp(Red, Yellow, s / 0.5f) else lerp(Yellow, Green, (s - 0.5f) / 0.5f)
    }
}

@Composable
fun AnimeFitTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Neon.Cyan,
            secondary = Neon.Magenta,
            tertiary = Neon.Green,
            background = Neon.Black,
            surface = Neon.Black,
            surfaceVariant = Neon.Panel,
            onPrimary = Neon.Black,
            onBackground = Neon.Text,
            onSurface = Neon.Text,
            error = Neon.Red,
        ),
        content = content,
    )
}

fun glowText(color: Color, size: Int = 20, weight: FontWeight = FontWeight.Bold) = TextStyle(
    color = color,
    fontSize = size.sp,
    fontWeight = weight,
    shadow = Shadow(color = color.copy(alpha = 0.9f), offset = Offset.Zero, blurRadius = 18f),
)

/** Disegna un alone luminoso (blur) attorno a un rettangolo arrotondato. */
fun DrawScope.neonRoundRect(color: Color, radius: Float, stroke: Float, glow: Float, alpha: Float = 1f) {
    drawIntoCanvas { canvas ->
        val paint = Paint().asFrameworkPaint().apply {
            isAntiAlias = true
            style = android.graphics.Paint.Style.STROKE
            strokeWidth = stroke * 2.5f
            this.color = color.copy(alpha = 0.8f * alpha).toArgb()
            maskFilter = BlurMaskFilter(glow, BlurMaskFilter.Blur.NORMAL)
        }
        canvas.nativeCanvas.drawRoundRect(0f, 0f, size.width, size.height, radius, radius, paint)
    }
    drawRoundRect(
        color = color.copy(alpha = alpha),
        cornerRadius = CornerRadius(radius),
        style = Stroke(stroke),
        topLeft = Offset(stroke / 2, stroke / 2),
        size = Size(size.width - stroke, size.height - stroke),
    )
}

fun Modifier.neonBorder(
    color: Color,
    radius: Dp = 18.dp,
    stroke: Dp = 2.dp,
    glow: Dp = 10.dp,
    alpha: Float = 1f,
): Modifier = this.drawBehind {
    neonRoundRect(color, radius.toPx(), stroke.toPx(), glow.toPx(), alpha)
}

@Composable
fun NeonButton(
    text: String,
    color: Color,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    filled: Boolean = false,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val c = if (enabled) color else Neon.TextDim.copy(alpha = 0.5f)
    val shape = RoundedCornerShape(16.dp)
    Box(
        modifier = modifier
            .padding(4.dp)
            .neonBorder(c, radius = 16.dp, glow = if (pressed) 18.dp else 10.dp, alpha = if (enabled) 1f else 0.5f)
            .clip(shape)
            .background(
                when {
                    filled && enabled -> c.copy(alpha = if (pressed) 0.45f else 0.25f)
                    pressed -> c.copy(alpha = 0.18f)
                    else -> Color.Transparent
                }
            )
            .clickable(interactionSource = interaction, indication = null, enabled = enabled, onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text.uppercase(),
            style = glowText(if (filled && enabled) Neon.Text else c, 15, FontWeight.Black)
                .copy(letterSpacing = 1.5.sp, textAlign = TextAlign.Center),
        )
    }
}

@Composable
fun NeonCard(
    color: Color,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    glow: Dp = 10.dp,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val shape = RoundedCornerShape(20.dp)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(5.dp)
            .neonBorder(color, radius = 20.dp, glow = if (pressed) glow + 8.dp else glow)
            .clip(shape)
            .background(if (pressed) color.copy(alpha = 0.10f) else Neon.Panel)
            .then(
                if (onClick != null) Modifier.clickable(interactionSource = interaction, indication = null, onClick = onClick)
                else Modifier
            )
            .padding(contentPadding),
        content = content,
    )
}

@Composable
fun NeonChip(text: String, color: Color, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(50)
    Box(
        modifier = modifier
            .padding(3.dp)
            .neonBorder(color, radius = 50.dp, stroke = if (selected) 2.dp else 1.dp, glow = if (selected) 10.dp else 3.dp,
                alpha = if (selected) 1f else 0.55f)
            .clip(shape)
            .background(if (selected) color.copy(alpha = 0.22f) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 9.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = TextStyle(color = if (selected) Neon.Text else color, fontSize = 14.sp, fontWeight = FontWeight.SemiBold))
    }
}

/** Anello di avanzamento con alone neon e percentuale al centro. */
@Composable
fun ProgressRing(fraction: Float?, color: Color, size: Dp, label: String, modifier: Modifier = Modifier) {
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        androidx.compose.foundation.Canvas(Modifier.size(size)) {
            val stroke = this.size.minDimension * 0.08f
            val inset = stroke * 1.5f
            val arcSize = Size(this.size.width - inset * 2, this.size.height - inset * 2)
            val topLeft = Offset(inset, inset)
            drawArc(color.copy(alpha = 0.15f), 0f, 360f, false, topLeft, arcSize, style = Stroke(stroke))
            val sweep = 360f * (fraction ?: 0f).coerceIn(0f, 1f)
            drawIntoCanvas { canvas ->
                val paint = Paint().asFrameworkPaint().apply {
                    isAntiAlias = true
                    style = android.graphics.Paint.Style.STROKE
                    strokeWidth = stroke * 1.6f
                    strokeCap = android.graphics.Paint.Cap.ROUND
                    this.color = color.copy(alpha = 0.7f).toArgb()
                    maskFilter = BlurMaskFilter(stroke * 1.2f, BlurMaskFilter.Blur.NORMAL)
                }
                canvas.nativeCanvas.drawArc(
                    topLeft.x, topLeft.y, topLeft.x + arcSize.width, topLeft.y + arcSize.height,
                    -90f, sweep, false, paint,
                )
            }
            drawArc(color, -90f, sweep, false, topLeft, arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                if (fraction == null) "—" else "${(fraction * 100).roundToInt()}%",
                style = glowText(color, (size.value / 4.2f).toInt(), FontWeight.Black),
            )
            Text(label, style = TextStyle(color = Neon.TextDim, fontSize = (size.value / 13f).coerceAtLeast(10f).sp))
        }
    }
}

/** Barra orizzontale neon con valore 0..1. */
@Composable
fun NeonBar(fraction: Float, color: Color, modifier: Modifier = Modifier) {
    androidx.compose.foundation.Canvas(modifier.fillMaxWidth().height(12.dp)) {
        val r = size.height / 2
        drawRoundRect(color.copy(alpha = 0.15f), cornerRadius = CornerRadius(r))
        val w = size.width * fraction.coerceIn(0f, 1f)
        if (w > 0) {
            drawIntoCanvas { canvas ->
                val paint = Paint().asFrameworkPaint().apply {
                    isAntiAlias = true
                    this.color = color.copy(alpha = 0.7f).toArgb()
                    maskFilter = BlurMaskFilter(r * 1.4f, BlurMaskFilter.Blur.NORMAL)
                }
                canvas.nativeCanvas.drawRoundRect(0f, 0f, w, size.height, r, r, paint)
            }
            drawRoundRect(color, size = Size(w, size.height), cornerRadius = CornerRadius(r))
        }
    }
}

@Composable
fun SectionTitle(text: String, color: Color = Neon.Cyan, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(),
        modifier = modifier.padding(top = 18.dp, bottom = 6.dp, start = 4.dp),
        style = glowText(color, 15, FontWeight.Black).copy(letterSpacing = 2.sp),
    )
}

@Composable
fun BodyText(text: String, modifier: Modifier = Modifier, color: Color = Neon.Text, size: Int = 15) {
    Text(text, modifier = modifier, style = TextStyle(color = color, fontSize = size.sp, lineHeight = (size + 6).sp))
}

/** Riga di pulsanti a larghezza piena divisi in parti uguali. */
@Composable
fun ButtonRow(content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp), content = content)
}
