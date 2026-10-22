package com.animefit.ui.components

import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Handler
import android.os.Looper
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.animefit.ImageUtils
import com.animefit.data.Measure
import com.animefit.ui.theme.BodyText
import com.animefit.ui.theme.ButtonRow
import com.animefit.ui.theme.Neon
import com.animefit.ui.theme.NeonButton
import com.animefit.ui.theme.NeonCard
import com.animefit.ui.theme.glowText
import com.animefit.ui.theme.neonBorder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Schermata con barra in alto (titolo + indietro) e contenuto scorrevole. */
@Composable
fun NeonScreen(
    title: String,
    color: Color = Neon.Cyan,
    onBack: (() -> Unit)? = null,
    content: LazyListScope.() -> Unit,
) {
    Column(Modifier.fillMaxSize().background(Neon.Black)) {
        NeonTopBar(title, color, onBack)
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 32.dp),
            content = content,
        )
    }
}

@Composable
fun NeonTopBar(title: String, color: Color, onBack: (() -> Unit)?) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            Box(
                Modifier
                    .size(44.dp)
                    .neonBorder(color, radius = 22.dp, glow = 8.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onBack),
                contentAlignment = Alignment.Center,
            ) { Text("←", style = glowText(color, 20, FontWeight.Black)) }
            Spacer(Modifier.width(14.dp))
        }
        Text(title.uppercase(), style = glowText(color, 21, FontWeight.Black), maxLines = 1)
    }
}

@Composable
fun NeonDialog(
    title: String,
    text: String,
    color: Color,
    confirm: String,
    onConfirm: () -> Unit,
    dismiss: String? = null,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        NeonCard(color, glow = 16.dp) {
            Text(title, style = glowText(color, 20, FontWeight.Black))
            Spacer(Modifier.size(10.dp))
            BodyText(text)
            Spacer(Modifier.size(14.dp))
            ButtonRow {
                if (dismiss != null) NeonButton(dismiss, Neon.TextDim, Modifier.weight(1f), onClick = onDismiss)
                NeonButton(confirm, color, Modifier.weight(1f), filled = true, onClick = onConfirm)
            }
        }
    }
}

/** Carica un'immagine da file in background. [key] forza il ricaricamento. */
@Composable
fun rememberImage(file: File?, maxSide: Int = 1280, key: Any? = null): ImageBitmap? {
    val image by produceState<ImageBitmap?>(null, file?.path, key) {
        value = if (file == null) null else withContext(Dispatchers.IO) {
            ImageUtils.decodeFile(file, maxSide)?.asImageBitmap()
        }
    }
    return image
}

private val dateFmt = DateTimeFormatter.ofPattern("EEEE d MMMM yyyy", Locale.ITALIAN)
private val shortFmt = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ITALIAN)

fun dateText(epochDay: Long): String = LocalDate.ofEpochDay(epochDay).format(dateFmt)
fun shortDate(epochDay: Long): String = LocalDate.ofEpochDay(epochDay).format(shortFmt)

val WEEKDAYS = listOf("Lun", "Mar", "Mer", "Gio", "Ven", "Sab", "Dom")

fun stars(n: Int): String = "★".repeat(n) + "☆".repeat((5 - n).coerceAtLeast(0))

fun measureColor(m: Measure): Color = when (m) {
    Measure.SPALLE -> Neon.Cyan
    Measure.TORACE -> Neon.Blue
    Measure.VITA -> Neon.Yellow
    Measure.FIANCHI -> Neon.Orange
    Measure.BRACCIO -> Neon.Magenta
    Measure.AVAMBRACCIO -> Neon.Purple
    Measure.COSCIA -> Neon.Green
    Measure.POLPACCIO -> Color(0xFF76FF03)
    Measure.DEFINIZIONE -> Neon.Red
}

@Composable
fun InfoLine(label: String, value: String, valueColor: Color = Neon.Text) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        BodyText(label, color = Neon.TextDim)
        BodyText(value, color = valueColor)
    }
}

/** Breve segnale acustico (fine recupero, timer della foto). */
fun beep(durationMs: Int = 300) {
    try {
        val tg = ToneGenerator(AudioManager.STREAM_MUSIC, 80)
        tg.startTone(ToneGenerator.TONE_PROP_BEEP2, durationMs)
        Handler(Looper.getMainLooper()).postDelayed({ tg.release() }, durationMs + 200L)
    } catch (_: Exception) {
        // Audio non disponibile: nessun suono.
    }
}
