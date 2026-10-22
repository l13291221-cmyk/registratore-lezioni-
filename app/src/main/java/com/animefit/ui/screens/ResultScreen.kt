package com.animefit.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.animefit.AppViewModel
import com.animefit.analysis.Assessment
import com.animefit.analysis.Change
import com.animefit.analysis.Scoring
import com.animefit.catalog.Area
import com.animefit.catalog.Character
import com.animefit.catalog.Exercises
import com.animefit.catalog.Floors
import com.animefit.catalog.allows
import com.animefit.data.AppState
import com.animefit.data.Measure
import com.animefit.data.PhotoEntry
import com.animefit.data.today
import com.animefit.training.Coach
import com.animefit.ui.components.AlignedCompare
import com.animefit.ui.components.CompareMode
import com.animefit.ui.components.FigureSpec
import com.animefit.ui.components.NeonDialog
import com.animefit.ui.components.NeonScreen
import com.animefit.ui.components.PhotoWithMeasures
import com.animefit.ui.components.PhysiqueFigure
import com.animefit.ui.components.measureColor
import com.animefit.ui.components.rememberImage
import com.animefit.ui.components.shortDate
import com.animefit.ui.theme.BodyText
import com.animefit.ui.theme.ButtonRow
import com.animefit.ui.theme.Neon
import com.animefit.ui.theme.NeonBar
import com.animefit.ui.theme.NeonButton
import com.animefit.ui.theme.NeonCard
import com.animefit.ui.theme.NeonChip
import com.animefit.ui.theme.ProgressRing
import com.animefit.ui.theme.SectionTitle
import com.animefit.ui.theme.glowText
import kotlin.math.abs
import kotlin.math.roundToInt

private enum class ResultTab(val label: String) { DIFFERENZE("Differenze"), PERSONAGGIO("Vs personaggio"), MISURE("Misure") }

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ResultScreen(vm: AppViewModel, photoId: String, onRetake: () -> Unit, onHome: () -> Unit, onBack: () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    val photo = state.photos.find { it.id == photoId }
    val c = Coach.character(state)
    if (photo == null || c == null) {
        NeonScreen("Analisi", Neon.Cyan, onBack) { item { BodyText("Foto non trovata.") } }
        return
    }
    val baseline = state.baseline ?: photo
    val isBaseline = baseline.id == photo.id
    val previous = state.photos.getOrNull(state.photos.indexOfFirst { it.id == photo.id } - 1)
    val a = Scoring.assess(photo.measures, c)
    val aBase = Scoring.assess(baseline.measures, c)
    val aPrev = previous?.let { Scoring.assess(it.measures, c) }
    val week = Coach.photoWeek(state, photo)
    val color = c.neon()

    var tab by rememberSaveable { mutableStateOf(if (isBaseline) ResultTab.PERSONAGGIO else ResultTab.DIFFERENZE) }
    var confirmRetake by rememberSaveable { mutableStateOf(false) }

    if (confirmRetake) {
        NeonDialog(
            "Rifare la foto?", "Questa foto verrà eliminata e potrai scattarne una nuova.", Neon.Magenta,
            confirm = "Rifai", onConfirm = { confirmRetake = false; vm.deletePhoto(photo.id); onRetake() },
            dismiss = "Annulla", onDismiss = { confirmRetake = false },
        )
    }

    NeonScreen(if (isBaseline) "Foto di partenza" else "Settimana $week", Neon.Cyan, onBack) {
        item {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                BodyText(shortDate(photo.epochDay), color = Neon.TextDim, size = 14)
                ProgressRing(a.overall.toFloat(), color, 170.dp, "del fisico di ${c.name}")
                if (!isBaseline) {
                    DeltaLine("Dalla partenza", a.overallPercent - aBase.overallPercent)
                    if (aPrev != null && previous?.id != baseline.id) DeltaLine("Dalla foto precedente", a.overallPercent - aPrev.overallPercent)
                } else {
                    BodyText(
                        "Questo è il tuo punto di partenza. Ogni settimana l'app ti chiederà una nuova foto e la confronterà con questa.",
                        Modifier.padding(8.dp), color = Neon.TextDim, size = 14,
                    )
                }
            }
        }
        if (photo.warnings.isNotEmpty()) item {
            NeonCard(Neon.Yellow) { photo.warnings.forEach { BodyText("⚠ $it", color = Neon.Yellow, size = 14) } }
        }
        item {
            FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                ResultTab.entries.forEach { NeonChip(it.label, Neon.Cyan, tab == it) { tab = it } }
            }
        }
        when (tab) {
            ResultTab.DIFFERENZE -> item { DifferencesTab(vm, baseline, photo, isBaseline, a, aBase) }
            ResultTab.PERSONAGGIO -> item { CharacterTab(vm, state, c, photo, a) }
            ResultTab.MISURE -> item { MeasuresTab(baseline, photo, isBaseline) }
        }
        item {
            Spacer(Modifier.height(12.dp))
            ButtonRow {
                if (photo.id == state.latest?.id && photo.epochDay == today()) {
                    NeonButton("Rifai la foto", Neon.Magenta, Modifier.weight(1f)) { confirmRetake = true }
                }
                NeonButton("Home", Neon.Cyan, Modifier.weight(1f), filled = true, onClick = onHome)
            }
            BodyText(
                "Le misure da foto sono stime con un piccolo margine d'errore: guarda la tendenza nelle settimane, " +
                    "non il singolo numero.",
                Modifier.padding(8.dp), color = Neon.TextDim, size = 12,
            )
        }
    }
}

@Composable
private fun DeltaLine(label: String, delta: Int) {
    val c = when {
        delta > 0 -> Neon.Green
        delta < 0 -> Neon.Red
        else -> Neon.TextDim
    }
    val sign = if (delta > 0) "+" else ""
    Text("$label: $sign$delta punti", style = glowText(c, 16))
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DifferencesTab(
    vm: AppViewModel, baseline: PhotoEntry, photo: PhotoEntry, isBaseline: Boolean,
    a: Assessment, aBase: Assessment,
) {
    var mode by rememberSaveable { mutableStateOf(CompareMode.AFFIANCATE) }
    if (isBaseline) {
        Column {
            PhotoWithMeasures(vm.photoFile(photo.id), photo.geometry, Modifier.fillMaxWidth().height(460.dp))
            MeasureLegend(photo)
        }
        return
    }
    Column {
        FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            CompareMode.entries.forEach { NeonChip(it.label, Neon.Magenta, mode == it) { mode = it } }
        }
        NeonCard(Neon.Magenta, contentPadding = androidx.compose.foundation.layout.PaddingValues(6.dp)) {
            when (mode) {
                CompareMode.AFFIANCATE -> Row(Modifier.fillMaxWidth().height(400.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text("PARTENZA · ${shortDate(baseline.epochDay)}", style = glowText(Neon.Cyan, 12))
                        PhotoWithMeasures(vm.photoFile(baseline.id), baseline.geometry, Modifier.fillMaxSize())
                    }
                    Spacer(Modifier.width(6.dp))
                    Column(Modifier.weight(1f)) {
                        Text("ORA · ${shortDate(photo.epochDay)}", style = glowText(Neon.Magenta, 12))
                        PhotoWithMeasures(vm.photoFile(photo.id), photo.geometry, Modifier.fillMaxSize())
                    }
                }
                else -> AlignedCompare(
                    baseline.geometry, vm.photoFile(baseline.id), vm.maskFile(baseline.id),
                    photo.geometry, vm.photoFile(photo.id), vm.maskFile(photo.id),
                    mode, Modifier.fillMaxWidth().height(460.dp),
                )
            }
        }
        BodyText(
            when (mode) {
                CompareMode.AFFIANCATE -> "Le linee colorate mostrano dove sono state prese le misure."
                CompareMode.SCORRI -> "Trascina la linea per passare dalla foto di partenza a quella di oggi (allineate su spalle e busto)."
                CompareMode.SAGOME -> "Ciano = sagoma di partenza, magenta = sagoma di oggi, bianco = uguale. " +
                    "Il magenta all'esterno indica dove sei cresciuto/a, il ciano dove ti sei ridotto/a."
            },
            Modifier.padding(6.dp), color = Neon.TextDim, size = 13,
        )
        SectionTitle("Cosa è cambiato", Neon.Magenta)
        val changes = Scoring.changes(baseline.measures, photo.measures).filter { it.measure != Measure.FIANCHI }
        val significant = changes.filter { it.significant }
        NeonCard(Neon.Magenta) {
            if (significant.isEmpty()) {
                BodyText("Nessuna differenza misurabile per ora: è normale nelle prime settimane. Continua così!", color = Neon.TextDim)
            } else {
                significant.sortedByDescending { abs(it.percent) }.forEach { ChangeRow(it) }
            }
            Spacer(Modifier.height(8.dp))
            val areaDeltas = a.areas.mapNotNull { now ->
                aBase.areas.find { it.area == now.area }?.let { now.area to (now.percent - it.percent) }
            }
            val best = areaDeltas.filter { it.second > 0 }.maxByOrNull { it.second }
            val worst = areaDeltas.filter { it.second < 0 }.minByOrNull { it.second }
            best?.let { BodyText("Miglioramento più grande: ${it.first.label} (+${it.second} punti)", color = Neon.Green, size = 14) }
            worst?.let { BodyText("Da tenere d'occhio: ${it.first.label} (${it.second} punti)", color = Neon.Orange, size = 14) }
        }
    }
}

@Composable
private fun ChangeRow(ch: Change) {
    val good = ch.improved
    val col = if (good) Neon.Green else Neon.Red
    val arrow = if (ch.after > ch.before) "▲" else "▼"
    val value = if (ch.measure == Measure.DEFINIZIONE) {
        "${(ch.before * 100).roundToInt()} → ${(ch.after * 100).roundToInt()}"
    } else {
        "%+.1f%%".format(ch.percent)
    }
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
        BodyText(ch.measure.label, Modifier.weight(1f), size = 14)
        Text("$arrow $value", style = glowText(col, 15))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MeasureLegend(photo: PhotoEntry) {
    FlowRow(Modifier.padding(6.dp)) {
        photo.geometry.segments.map { it.measure }.distinct().forEach { m ->
            Text("● ${m.label}  ", style = glowText(measureColor(m), 12))
        }
    }
}

@Composable
private fun CharacterTab(vm: AppViewModel, state: AppState, c: Character, photo: PhotoEntry, a: Assessment) {
    val color = c.neon()
    val refVersion by vm.refVersion.collectAsStateWithLifecycle()
    val refFile = vm.referenceFile(c.id)
    val refImage = rememberImage(refFile.takeIf { it.exists() }, key = refVersion)
    val heat = a.areas.associate { it.area to Neon.heat(it.score) }
    val userSpec = FigureSpec.from(photo.measures, Floors.average(c.sex))

    Column {
        NeonCard(color) {
            Row(Modifier.fillMaxWidth().height(330.dp)) {
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("TU", style = glowText(Neon.Text, 15, FontWeight.Black))
                    PhysiqueFigure(userSpec, Neon.Text, Modifier.fillMaxSize(), areaColors = heat)
                }
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(c.name.uppercase(), style = glowText(color, 15, FontWeight.Black), maxLines = 1)
                    PhysiqueFigure(c.figure(), color, Modifier.fillMaxSize())
                }
            }
            BodyText(
                "Le zone della tua figura sono colorate: rosso = lontano dal personaggio, giallo = a metà, verde = raggiunto.",
                color = Neon.TextDim, size = 12,
            )
        }
        if (refImage != null) {
            NeonCard(color) {
                Text("LA TUA FOTO E L'IMMAGINE DI ${c.name.uppercase()}", style = glowText(color, 13))
                Row(Modifier.fillMaxWidth().height(320.dp)) {
                    PhotoWithMeasures(vm.photoFile(photo.id), photo.geometry, Modifier.weight(1f).fillMaxSize(), showLines = false)
                    Spacer(Modifier.width(6.dp))
                    Image(refImage, "Immagine del personaggio", Modifier.weight(1f).fillMaxSize(), contentScale = ContentScale.Fit)
                }
            }
        }

        SectionTitle("Zona per zona", color)
        NeonCard(color) {
            a.areas.sortedBy { it.score }.forEach { s ->
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    BodyText(s.area.label + if (s.area in c.focus) " ★" else "", size = 15)
                    Text("${s.percent}%", style = glowText(Neon.heat(s.score), 15, FontWeight.Black))
                }
                NeonBar(s.score.toFloat(), Neon.heat(s.score))
            }
            BodyText("★ = zona chiave per ${c.name}, pesa di più nella percentuale finale.", Modifier.padding(top = 8.dp), color = Neon.TextDim, size = 12)
        }

        val weak = a.weakest
        if (weak.isNotEmpty()) {
            SectionTitle("Dove allenarti di più", Neon.Magenta)
            weak.forEachIndexed { i, s ->
                NeonCard(Neon.Magenta) {
                    Text("${i + 1}. ${s.area.label.uppercase()} · ${s.percent}%", style = glowText(Neon.heat(s.score), 17, FontWeight.Black))
                    BodyText(s.area.why, size = 14)
                    BodyText("Rispetto a ${c.name}: ${s.gapText()}.", color = Neon.TextDim, size = 14)
                    val suggestions = suggestedExercises(state, s.area)
                    if (suggestions.isNotEmpty()) {
                        BodyText("Esercizi consigliati: ${suggestions.joinToString()}.", color = Neon.Cyan, size = 14)
                    }
                }
            }
            BodyText(
                "La scheda giornaliera aggiunge già esercizi extra (★) per i primi due punti deboli.",
                Modifier.padding(6.dp), color = Neon.TextDim, size = 13,
            )
        }
        val strong = a.strongest
        if (strong.isNotEmpty()) {
            SectionTitle("Punti forti", Neon.Green)
            NeonCard(Neon.Green) {
                strong.forEach { BodyText("✔ ${it.area.label}: ${it.percent}%", color = Neon.Green) }
            }
        }
    }
}

private fun suggestedExercises(state: AppState, area: Area): List<String> {
    val profile = state.profile ?: return emptyList()
    return area.muscles.flatMap { m ->
        Exercises.all.filter {
            it.muscle == m && profile.equipment.allows(it.kit) &&
                it.difficulty <= profile.level.maxDifficulty && it.id !in setOf("corsa_km")
        }.sortedByDescending { it.difficulty }.take(2)
    }.map { it.name }.distinct().take(4)
}

@Composable
private fun MeasuresTab(baseline: PhotoEntry, photo: PhotoEntry, isBaseline: Boolean) {
    NeonCard(Neon.Cyan) {
        Row(Modifier.fillMaxWidth()) {
            Text("MISURA", Modifier.weight(1.6f), style = glowText(Neon.TextDim, 12))
            if (!isBaseline) Text("PARTENZA", Modifier.weight(1f), style = glowText(Neon.TextDim, 12).copy(textAlign = TextAlign.End))
            Text("ORA", Modifier.weight(1f), style = glowText(Neon.TextDim, 12).copy(textAlign = TextAlign.End))
            if (!isBaseline) Text("Δ", Modifier.weight(0.9f), style = glowText(Neon.TextDim, 12).copy(textAlign = TextAlign.End))
        }
        Measure.entries.forEach { m ->
            val now = photo.measures[m] ?: return@forEach
            val before = baseline.measures[m]
            Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(m.label, Modifier.weight(1.6f), style = glowText(measureColor(m), 13))
                if (!isBaseline) {
                    Text(before?.let { fmt(m, it) } ?: "-", Modifier.weight(1f), style = glowText(Neon.Text, 14, FontWeight.Normal).copy(textAlign = TextAlign.End))
                }
                Text(fmt(m, now), Modifier.weight(1f), style = glowText(Neon.Text, 14, FontWeight.Normal).copy(textAlign = TextAlign.End))
                if (!isBaseline) {
                    val ch = before?.let { Change(m, it, now) }
                    val col: Color = when {
                        ch == null || !ch.significant -> Neon.TextDim
                        ch.improved -> Neon.Green
                        else -> Neon.Red
                    }
                    Text(ch?.let { "%+.1f%%".format(it.percent) } ?: "-", Modifier.weight(0.9f), style = glowText(col, 13).copy(textAlign = TextAlign.End))
                }
            }
        }
        BodyText(
            "Le larghezze sono in percentuale della lunghezza del busto (spalle-anche), così non dipendono dalla " +
                "distanza dalla fotocamera. La definizione è un indice da 0 a 100.",
            Modifier.padding(top = 8.dp), color = Neon.TextDim, size = 12,
        )
    }
}

private fun fmt(m: Measure, v: Double): String = (v * 100).roundToInt().toString()
