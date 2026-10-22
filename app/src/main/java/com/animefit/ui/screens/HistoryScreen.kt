package com.animefit.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.animefit.AppViewModel
import com.animefit.analysis.Scoring
import com.animefit.training.Coach
import com.animefit.ui.components.NeonDialog
import com.animefit.ui.components.NeonScreen
import com.animefit.ui.components.PhotoWithMeasures
import com.animefit.ui.components.shortDate
import com.animefit.ui.theme.BodyText
import com.animefit.ui.theme.Neon
import com.animefit.ui.theme.NeonButton
import com.animefit.ui.theme.NeonCard
import com.animefit.ui.theme.glowText
import kotlin.math.roundToInt

@Composable
fun HistoryScreen(vm: AppViewModel, onOpen: (String) -> Unit, onCamera: () -> Unit, onBack: () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    val c = Coach.character(state)
    var toDelete by rememberSaveable { mutableStateOf<String?>(null) }

    toDelete?.let { id ->
        NeonDialog(
            "Eliminare la foto?",
            if (id == state.baseline?.id) "È la foto di partenza: la successiva diventerà il nuovo punto di partenza."
            else "La foto e le sue misure verranno cancellate.",
            Neon.Red,
            confirm = "Elimina", onConfirm = { vm.deletePhoto(id); toDelete = null },
            dismiss = "Annulla", onDismiss = { toDelete = null },
        )
    }

    NeonScreen("Storico foto", Neon.Yellow, onBack) {
        if (state.photos.isEmpty() || c == null) {
            item {
                BodyText("Nessuna foto ancora.", Modifier.padding(8.dp))
                NeonButton("Scatta la prima foto", Neon.Magenta, Modifier.fillMaxWidth(), filled = true, onClick = onCamera)
            }
            return@NeonScreen
        }
        val scores = state.photos.map { Scoring.assess(it.measures, c).overall }
        if (scores.size >= 2) item {
            NeonCard(Neon.Yellow) {
                Text("RAGGIUNGIMENTO DI ${c.name.uppercase()} (%)", style = glowText(Neon.Text, 14, FontWeight.Black))
                ProgressChart(scores, Neon.Yellow, Modifier.fillMaxWidth().height(170.dp).padding(top = 8.dp))
                BodyText("Una foto a settimana: la linea dovrebbe salire piano piano.", color = Neon.TextDim, size = 12)
            }
        }
        state.photos.asReversed().forEach { p ->
            item(key = p.id) {
                val score = Scoring.assess(p.measures, c)
                val week = Coach.photoWeek(state, p)
                NeonCard(Neon.Yellow, onClick = { onOpen(p.id) }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        PhotoWithMeasures(vm.photoFile(p.id), p.geometry, Modifier.size(84.dp, 112.dp), showLines = false)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                if (p.id == state.baseline?.id) "PARTENZA" else "SETTIMANA $week",
                                style = glowText(Neon.Yellow, 16, FontWeight.Black),
                            )
                            BodyText(shortDate(p.epochDay), color = Neon.TextDim, size = 13)
                            Text("${score.overallPercent}%", style = glowText(Neon.heat(score.overall), 22, FontWeight.Black))
                        }
                        NeonButton("Elimina", Neon.Red) { toDelete = p.id }
                    }
                }
            }
        }
    }
}

/** Andamento della percentuale nel tempo: una sola serie, griglia discreta, etichette su primo e ultimo punto. */
@Composable
private fun ProgressChart(values: List<Double>, color: Color, modifier: Modifier = Modifier) {
    val gridColor = Neon.TextDim.copy(alpha = 0.25f)
    val labelColor = Neon.TextDim.toArgb()
    val valueColor = Neon.Text.toArgb()
    Canvas(modifier) {
        val left = 34.dp.toPx()
        val right = size.width - 16.dp.toPx()
        val top = 18.dp.toPx()
        val bottom = size.height - 8.dp.toPx()
        val textPaint = android.graphics.Paint().apply {
            isAntiAlias = true
            textSize = 11.dp.toPx()
            this.color = labelColor
        }
        for (v in listOf(0, 50, 100)) {
            val y = bottom - (bottom - top) * v / 100f
            drawLine(gridColor, Offset(left, y), Offset(right, y), 1.dp.toPx())
            drawContext.canvas.nativeCanvas.drawText("$v", 0f, y + 4.dp.toPx(), textPaint)
        }
        fun pt(i: Int): Offset {
            val x = if (values.size == 1) (left + right) / 2 else left + (right - left) * i / (values.size - 1)
            val y = bottom - (bottom - top) * values[i].toFloat().coerceIn(0f, 1f)
            return Offset(x, y)
        }
        val path = Path().apply {
            values.indices.forEach { i -> pt(i).let { if (i == 0) moveTo(it.x, it.y) else lineTo(it.x, it.y) } }
        }
        drawPath(path, color.copy(alpha = 0.3f), style = Stroke(6.dp.toPx(), cap = StrokeCap.Round))
        drawPath(path, color, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round))
        values.indices.forEach { i ->
            drawCircle(Neon.Black, 6.dp.toPx(), pt(i))
            drawCircle(color, 4.dp.toPx(), pt(i))
        }
        val valuePaint = android.graphics.Paint(textPaint).apply {
            this.color = valueColor
            textAlign = android.graphics.Paint.Align.CENTER
            isFakeBoldText = true
        }
        for (i in listOf(0, values.lastIndex)) {
            val p = pt(i)
            drawContext.canvas.nativeCanvas.drawText("${(values[i] * 100).roundToInt()}%", p.x, p.y - 10.dp.toPx(), valuePaint)
        }
    }
}
