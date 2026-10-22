package com.animefit.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.animefit.AppViewModel
import com.animefit.catalog.Floors
import com.animefit.data.Equipment
import com.animefit.data.Level
import com.animefit.data.Profile
import com.animefit.data.Sex
import com.animefit.ui.components.FigureSpec
import com.animefit.ui.components.NeonScreen
import com.animefit.ui.components.PhysiqueFigure
import com.animefit.ui.theme.BodyText
import com.animefit.ui.theme.Neon
import com.animefit.ui.theme.NeonButton
import com.animefit.ui.theme.NeonCard
import com.animefit.ui.theme.NeonChip
import com.animefit.ui.theme.SectionTitle
import com.animefit.ui.theme.glowText

val levelInfo = mapOf(
    Level.PRINCIPIANTE to "Mi alleno poco o niente",
    Level.INTERMEDIO to "Mi alleno da qualche mese",
    Level.AVANZATO to "Mi alleno con costanza da più di un anno",
)

@Composable
fun OnboardingScreen(vm: AppViewModel, onDone: () -> Unit) {
    var step by rememberSaveable { mutableIntStateOf(0) }
    if (step == 0) {
        Welcome { step = 1 }
    } else {
        NeonScreen("Il tuo profilo", Neon.Cyan, onBack = { step = 0 }) {
            item {
                ProfileForm(vm.state.value.profile ?: Profile(), "AVANTI: SCEGLI IL PERSONAGGIO") {
                    vm.saveProfile(it)
                    onDone()
                }
            }
        }
    }
}

@Composable
private fun Welcome(onStart: () -> Unit) {
    NeonScreen("") {
        item {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("ANIME FIT", style = glowText(Neon.Cyan, 44, FontWeight.Black).copy(letterSpacing = 4.sp))
                Text("Allenati per il fisico del tuo eroe", style = glowText(Neon.Magenta, 17))
                PhysiqueFigure(
                    FigureSpec.from(emptyMap(), Floors.average(Sex.UOMO)).copy(shoulders = 1.12f, waist = 0.55f, arm = 0.26f, definition = 0.9f),
                    Neon.Cyan,
                    Modifier.fillMaxWidth().height(300.dp).padding(vertical = 16.dp),
                )
            }
        }
        item {
            NeonCard(Neon.Magenta) {
                Feature("1", "Scegli il personaggio", "Eroi anime o star famose: ognuno ha il suo fisico e il suo allenamento.", Neon.Magenta)
                Feature("2", "Allenati ogni giorno", "La scheda del giorno cambia con il personaggio, il tuo livello e i tuoi punti deboli.", Neon.Green)
                Feature("3", "Foto ogni settimana", "L'app confronta la foto con quella di partenza e con il personaggio, e ti dà la % di somiglianza.", Neon.Yellow)
            }
        }
        item {
            BodyText(
                "Tutto resta sul telefono: le foto vengono analizzate offline e non vengono inviate a nessuno.",
                Modifier.padding(12.dp), color = Neon.TextDim, size = 13,
            )
            NeonButton("Inizia", Neon.Cyan, Modifier.fillMaxWidth(), filled = true, onClick = onStart)
        }
    }
}

@Composable
private fun Feature(n: String, title: String, text: String, color: Color) {
    Column(Modifier.padding(vertical = 6.dp)) {
        Text("$n · $title", style = glowText(color, 17, FontWeight.Black))
        BodyText(text, color = Neon.TextDim, size = 14)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProfileForm(initial: Profile, buttonText: String, onSave: (Profile) -> Unit) {
    var name by rememberSaveable { mutableStateOf(initial.name) }
    var sex by rememberSaveable { mutableStateOf(initial.sex) }
    var level by rememberSaveable { mutableStateOf(initial.level) }
    var equipment by rememberSaveable { mutableStateOf(initial.equipment) }

    Column {
        SectionTitle("Come ti chiami?")
        OutlinedTextField(
            value = name,
            onValueChange = { name = it.take(24) },
            singleLine = true,
            placeholder = { Text("Nome (facoltativo)", color = Neon.TextDim) },
            modifier = Modifier.fillMaxWidth().padding(4.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Neon.Cyan,
                unfocusedBorderColor = Neon.Cyan.copy(alpha = 0.5f),
                cursorColor = Neon.Cyan,
                focusedTextColor = Neon.Text,
                unfocusedTextColor = Neon.Text,
            ),
        )

        SectionTitle("Corporatura", Neon.Magenta)
        BodyText("Serve per le proporzioni di partenza.", color = Neon.TextDim, size = 13)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Sex.entries.forEach { NeonChip(it.label, Neon.Magenta, sex == it) { sex = it } }
        }

        SectionTitle("Livello", Neon.Green)
        Level.entries.forEach {
            NeonChip("${it.label} — ${levelInfo[it]}", Neon.Green, level == it, Modifier.fillMaxWidth()) { level = it }
        }

        SectionTitle("Dove ti alleni", Neon.Yellow)
        Equipment.entries.forEach {
            NeonChip("${it.label} — ${it.detail}", Neon.Yellow, equipment == it, Modifier.fillMaxWidth()) { equipment = it }
        }

        Spacer(Modifier.height(16.dp))
        BodyText(
            "Prima di iniziare un nuovo allenamento, se hai problemi di salute o dubbi, chiedi a un medico. " +
                "Se senti dolore (non semplice fatica) fermati.",
            color = Neon.TextDim, size = 13,
        )
        Spacer(Modifier.height(10.dp))
        NeonButton(buttonText, Neon.Cyan, Modifier.fillMaxWidth(), filled = true) {
            onSave(initial.copy(name = name.trim(), sex = sex, level = level, equipment = equipment))
        }
    }
}
