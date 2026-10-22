package com.animefit.ui.screens

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.animefit.AppViewModel
import com.animefit.data.today
import com.animefit.training.Coach
import com.animefit.ui.components.NeonDialog
import com.animefit.ui.components.NeonScreen
import com.animefit.ui.components.PhysiqueFigure
import com.animefit.ui.components.shortDate
import com.animefit.ui.theme.BodyText
import com.animefit.ui.theme.ButtonRow
import com.animefit.ui.theme.Neon
import com.animefit.ui.theme.NeonButton
import com.animefit.ui.theme.NeonCard
import com.animefit.ui.theme.ProgressRing
import com.animefit.ui.theme.glowText

@Composable
fun HomeScreen(
    vm: AppViewModel,
    onWorkout: () -> Unit,
    onCamera: () -> Unit,
    onResult: (String) -> Unit,
    onCharacter: (String) -> Unit,
    onCharacters: () -> Unit,
    onHistory: () -> Unit,
    onSettings: () -> Unit,
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val c = Coach.character(state) ?: return
    val profile = state.profile ?: return
    val day = today()
    val color = c.neon()
    val latest = state.latest
    val assessment = Coach.assessment(state, latest)
    val workout = Coach.workout(state, day)
    val done = state.log[day]?.completed == true
    val photoDue = Coach.photoDue(state, day)
    val streak = Coach.streak(state, day)

    val pulse by rememberInfiniteTransition(label = "pulse").animateFloat(
        initialValue = 6f, targetValue = 22f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "glow",
    )

    if (photoDue && state.photoPromptSnoozedDay != day) {
        NeonDialog(
            title = if (latest == null) "📸 Foto di partenza" else "📸 Foto settimanale",
            text = if (latest == null) "Serve una foto del tuo fisico per capire da dove parti e quanto ti manca per arrivare a ${c.name}."
            else "È passata una settimana! Scatta la foto per vedere le differenze rispetto alla prima e la nuova percentuale.",
            color = Neon.Magenta,
            confirm = "Scatta ora",
            onConfirm = { vm.snoozePhotoPrompt(); onCamera() },
            dismiss = "Più tardi",
            onDismiss = { vm.snoozePhotoPrompt() },
        )
    }

    NeonScreen("Anime Fit", Neon.Cyan) {
        item {
            BodyText(
                if (profile.name.isNotBlank()) "Ciao ${profile.name}! Oggi si diventa più forti." else "Ciao! Oggi si diventa più forti.",
                Modifier.padding(horizontal = 6.dp), color = Neon.TextDim,
            )
        }
        item {
            NeonCard(color, onClick = { onCharacter(c.id) }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    PhysiqueFigure(c.figure(), color, Modifier.width(84.dp).height(130.dp))
                    Column(Modifier.weight(1f).padding(horizontal = 8.dp)) {
                        Text("OBIETTIVO", style = glowText(Neon.TextDim, 12))
                        Text(c.name, style = glowText(color, 20, FontWeight.Black))
                        Text(c.origin, style = glowText(Neon.TextDim, 13, FontWeight.Normal))
                        Spacer(Modifier.height(6.dp))
                        Text("Settimana ${workout?.week ?: 1}", style = glowText(Neon.Text, 14))
                        Text("🔥 $streak giorni di fila", style = glowText(Neon.Orange, 14))
                    }
                    ProgressRing(assessment?.overall?.toFloat(), color, 104.dp, "raggiunto")
                }
            }
        }
        item {
            if (photoDue) {
                NeonCard(Neon.Magenta, onClick = onCamera, glow = pulse.dp) {
                    Text(
                        if (latest == null) "📸 FOTO DI PARTENZA" else "📸 FOTO SETTIMANALE",
                        style = glowText(Neon.Magenta, 19, FontWeight.Black),
                    )
                    BodyText(
                        if (latest == null) "Prima di tutto: una foto per capire il tuo livello di partenza."
                        else "È il momento della foto: vedrai le differenze con la prima e quanto ti manca.",
                    )
                    NeonButton("Scatta la foto", Neon.Magenta, Modifier.fillMaxWidth(), filled = true, onClick = onCamera)
                }
            } else {
                NeonCard(Neon.Magenta, onClick = onCamera) {
                    Text("📸 FOTO SETTIMANALE", style = glowText(Neon.Magenta, 17, FontWeight.Black))
                    val next = Coach.daysToNextPhoto(state, day)
                    BodyText(
                        "Prossima foto tra $next ${if (next == 1) "giorno" else "giorni"}. " +
                            "Ultima: ${latest?.let { shortDate(it.epochDay) } ?: "-"}.",
                        color = Neon.TextDim,
                    )
                }
            }
        }
        if (workout != null) item {
            NeonCard(Neon.Green, onClick = onWorkout) {
                Text("ALLENAMENTO DI OGGI", style = glowText(Neon.Green, 17, FontWeight.Black))
                BodyText(workout.dayType.label)
                BodyText(
                    "${workout.exercises.size} esercizi · circa ${workout.estimatedMinutes} minuti" +
                        if (workout.deload) " · settimana di scarico" else "",
                    color = Neon.TextDim, size = 14,
                )
                if (workout.hasWeakExtras) {
                    BodyText("★ Extra per: ${workout.weakAreas.joinToString { it.label }}", color = Neon.Magenta, size = 14)
                }
                if (workout.signatureTitle != null) {
                    BodyText("★ Oggi c'è: ${workout.signatureTitle}", color = Neon.Yellow, size = 14)
                }
                if (done) {
                    Text("✔ COMPLETATO", Modifier.padding(top = 8.dp), style = glowText(Neon.Green, 18, FontWeight.Black))
                } else {
                    NeonButton("Inizia", Neon.Green, Modifier.fillMaxWidth(), filled = true, onClick = onWorkout)
                }
            }
        }
        if (latest != null && assessment != null) item {
            NeonCard(Neon.Cyan, onClick = { onResult(latest.id) }) {
                Text("ANALISI E CONFRONTO", style = glowText(Neon.Cyan, 17, FontWeight.Black))
                val weak = assessment.weakest
                if (weak.isNotEmpty()) {
                    BodyText("Dove allenarti di più:", color = Neon.TextDim, size = 14)
                    weak.forEach { BodyText("• ${it.area.label}: ${it.percent}%", color = Neon.heat(it.score), size = 15) }
                } else {
                    BodyText("Ci sei quasi: il tuo fisico è vicinissimo a quello di ${c.name}!", color = Neon.Green)
                }
                NeonButton("Vedi analisi completa", Neon.Cyan, Modifier.fillMaxWidth()) { onResult(latest.id) }
            }
        }
        item {
            ButtonRow {
                NeonButton("Storico", Neon.Yellow, Modifier.weight(1f), onClick = onHistory)
                NeonButton("Personaggi", Neon.Purple, Modifier.weight(1f), onClick = onCharacters)
            }
            NeonButton("Impostazioni", Neon.Orange, Modifier.fillMaxWidth(), onClick = onSettings)
        }
    }
}
