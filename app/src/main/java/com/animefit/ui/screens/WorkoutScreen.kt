package com.animefit.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.animefit.AppViewModel
import com.animefit.catalog.Character
import com.animefit.data.today
import com.animefit.training.Coach
import com.animefit.training.PlannedExercise
import com.animefit.training.Tag
import com.animefit.ui.components.NeonDialog
import com.animefit.ui.components.NeonScreen
import com.animefit.ui.components.beep
import com.animefit.ui.components.dateText
import com.animefit.ui.theme.BodyText
import com.animefit.ui.theme.Neon
import com.animefit.ui.theme.NeonButton
import com.animefit.ui.theme.NeonCard
import com.animefit.ui.theme.SectionTitle
import com.animefit.ui.theme.glowText
import com.animefit.ui.theme.neonBorder
import kotlinx.coroutines.delay

@Composable
fun WorkoutScreen(vm: AppViewModel, onBack: () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    val day = today()
    val c = Coach.character(state) ?: return
    val workout = Coach.workout(state, day) ?: return
    val log = state.log[day]
    val completed = log?.completed == true
    var restLeft by remember { mutableIntStateOf(0) }
    var celebrate by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(restLeft > 0) {
        while (restLeft > 0) {
            delay(1000)
            restLeft--
            if (restLeft == 0) beep()
        }
    }

    val totalSets = workout.exercises.sumOf { it.sets }
    val doneSets = workout.exercises.indices.sumOf { i -> minOf(log?.setsDone?.get(i) ?: 0, workout.exercises[i].sets) }
    val allDone = doneSets >= totalSets

    if (celebrate) {
        NeonDialog(
            title = "🔥 ALLENAMENTO FATTO!",
            text = "Un passo in più verso ${c.name}. Serie attuale: ${Coach.streak(state, day)} giorni di fila. " +
                "Bevi acqua, mangia bene e dormi: i muscoli crescono quando recuperi.",
            color = Neon.Green,
            confirm = "Torna alla home",
            onConfirm = { celebrate = false; onBack() },
            onDismiss = { celebrate = false },
        )
    }

    Box(Modifier.fillMaxSize()) {
        NeonScreen("Allenamento", Neon.Green, onBack) {
            item {
                NeonCard(Neon.Green) {
                    Text(workout.dayType.label.uppercase(), style = glowText(Neon.Green, 18, FontWeight.Black))
                    BodyText(dateText(day).replaceFirstChar { it.uppercase() }, color = Neon.TextDim, size = 14)
                    BodyText("Settimana ${workout.week} · circa ${workout.estimatedMinutes} minuti · $doneSets/$totalSets serie", size = 14)
                    if (workout.deload) {
                        BodyText(
                            "Settimana di scarico: una serie in meno per far recuperare il corpo. Fa parte del piano!",
                            color = Neon.Yellow, size = 13,
                        )
                    }
                    if (workout.isRest) {
                        BodyText(
                            "Oggi recupero attivo: niente fatica, solo movimento leggero. I muscoli crescono mentre riposano.",
                            color = Neon.Cyan, size = 13,
                        )
                    } else {
                        BodyText("Riscaldati 5 minuti prima di iniziare (corsa sul posto, cerchi con le braccia).", color = Neon.TextDim, size = 13)
                    }
                }
            }
            if (workout.weakAreas.isNotEmpty() && !workout.isRest) item {
                NeonCard(Neon.Magenta) {
                    BodyText(
                        "★ Dalla tua ultima foto i punti da migliorare sono: ${workout.weakAreas.joinToString { it.label }}. " +
                            if (workout.hasWeakExtras) "Gli esercizi segnati ★ sono extra per queste zone."
                            else "Oggi non si allenano: avranno esercizi extra nei giorni dedicati.",
                        color = Neon.Magenta, size = 14,
                    )
                }
            }
            val regular = workout.exercises.withIndex().filter { it.value.tag != Tag.FIRMA }
            val special = workout.exercises.withIndex().filter { it.value.tag == Tag.FIRMA }
            regular.forEach { (i, p) ->
                item(key = "ex$i") {
                    ExerciseCard(p, log?.setsDone?.get(i) ?: 0, c) { n ->
                        val before = log?.setsDone?.get(i) ?: 0
                        vm.setSetsDone(day, i, n)
                        if (n > before && n < p.sets && p.restSeconds > 0) restLeft = p.restSeconds
                    }
                }
            }
            if (special.isNotEmpty()) {
                item {
                    SectionTitle("★ ${workout.signatureTitle}", Neon.Yellow)
                    workout.signatureNote?.let { BodyText(it, Modifier.padding(horizontal = 6.dp), color = Neon.TextDim, size = 13) }
                }
                special.forEach { (i, p) ->
                    item(key = "ex$i") {
                        ExerciseCard(p, log?.setsDone?.get(i) ?: 0, c) { n ->
                            val before = log?.setsDone?.get(i) ?: 0
                            vm.setSetsDone(day, i, n)
                            if (n > before && n < p.sets && p.restSeconds > 0) restLeft = p.restSeconds
                        }
                    }
                }
            }
            item {
                Spacer(Modifier.height(12.dp))
                if (completed) {
                    Text("✔ ALLENAMENTO COMPLETATO", Modifier.fillMaxWidth().padding(8.dp), style = glowText(Neon.Green, 20, FontWeight.Black))
                    NeonButton("Segna come non fatto", Neon.TextDim, Modifier.fillMaxWidth()) { vm.setDayCompleted(day, false) }
                } else {
                    NeonButton(
                        if (allDone) "Allenamento completato!" else "Segna allenamento come fatto",
                        Neon.Green, Modifier.fillMaxWidth(), filled = allDone,
                    ) {
                        vm.setDayCompleted(day, true)
                        celebrate = true
                    }
                }
                Spacer(Modifier.height(if (restLeft > 0) 90.dp else 0.dp))
            }
        }
        if (restLeft > 0) {
            Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(12.dp)) {
                NeonCard(Neon.Cyan, glow = 18.dp) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("RECUPERO", style = glowText(Neon.TextDim, 13))
                            Text("%d:%02d".format(restLeft / 60, restLeft % 60), style = glowText(Neon.Cyan, 30, FontWeight.Black))
                        }
                        NeonButton("+15s", Neon.Cyan) { restLeft += 15 }
                        NeonButton("Salta", Neon.Magenta) { restLeft = 0 }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ExerciseCard(p: PlannedExercise, done: Int, c: Character, onSets: (Int) -> Unit) {
    val color = when (p.tag) {
        Tag.NORMALE -> Neon.Cyan
        Tag.PUNTO_DEBOLE -> Neon.Magenta
        Tag.FIRMA -> Neon.Yellow
    }
    val finished = done >= p.sets
    var showTip by rememberSaveable(p.exercise.id) { mutableStateOf(false) }
    NeonCard(if (finished) Neon.Green else color, glow = if (finished) 4.dp else 10.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    (if (p.tag == Tag.PUNTO_DEBOLE) "★ " else "") + p.exercise.name,
                    style = glowText(if (finished) Neon.Green else Neon.Text, 17, FontWeight.Bold),
                )
                Text(
                    p.exercise.muscle.label + if (p.restSeconds > 0 && p.sets > 1) " · recupero ${p.restSeconds}\"" else "",
                    style = glowText(Neon.TextDim, 13, FontWeight.Normal),
                )
            }
            Text(p.doseText, style = glowText(if (finished) Neon.Green else color, 18, FontWeight.Black))
        }
        Text(
            if (showTip) "▲ Nascondi" else "▼ Come si fa",
            Modifier.clickable { showTip = !showTip }.padding(vertical = 6.dp),
            style = glowText(color, 13, FontWeight.SemiBold),
        )
        if (showTip) BodyText(p.exercise.tip, color = Neon.TextDim, size = 14)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 6.dp)) {
            for (i in 0 until p.sets) {
                val on = i < done
                Box(
                    Modifier
                        .size(42.dp)
                        .neonBorder(if (on) Neon.Green else color, radius = 21.dp, glow = if (on) 8.dp else 3.dp, alpha = if (on) 1f else 0.6f)
                        .clip(CircleShape)
                        .background(if (on) Neon.Green.copy(alpha = 0.3f) else Color.Transparent)
                        .clickable { onSets(if (done > i) i else i + 1) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(if (on) "✔" else "${i + 1}", style = glowText(if (on) Neon.Green else color, 15, FontWeight.Black))
                }
            }
        }
        if (p.tag == Tag.FIRMA && p.exercise.id == "corsa_km") {
            BodyText("Per ${c.name} conta la costanza, non la velocità.", color = Neon.TextDim, size = 12)
        }
    }
}
