package com.animefit.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import com.animefit.catalog.Category
import com.animefit.catalog.Character
import com.animefit.catalog.Characters
import com.animefit.catalog.Exercises
import com.animefit.catalog.Floors
import com.animefit.data.Level
import com.animefit.data.Sex
import com.animefit.training.PlannedExercise
import com.animefit.training.Tag
import com.animefit.ui.components.FigureSpec
import com.animefit.ui.components.NeonScreen
import com.animefit.ui.components.NeonTopBar
import com.animefit.ui.components.PhysiqueFigure
import com.animefit.ui.components.WEEKDAYS
import com.animefit.ui.components.rememberImage
import com.animefit.ui.components.stars
import com.animefit.ui.theme.BodyText
import com.animefit.ui.theme.ButtonRow
import com.animefit.ui.theme.Neon
import com.animefit.ui.theme.NeonButton
import com.animefit.ui.theme.NeonCard
import com.animefit.ui.theme.NeonChip
import com.animefit.ui.theme.SectionTitle
import com.animefit.ui.theme.glowText

fun Character.neon(): Color = Color(color)

fun Character.figure(): FigureSpec = FigureSpec.from(targets, Floors.average(sex))

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CharactersScreen(vm: AppViewModel, onOpen: (String) -> Unit, onBack: (() -> Unit)?) {
    val state by vm.state.collectAsStateWithLifecycle()
    var category by rememberSaveable { mutableStateOf<Category?>(null) }
    var sex by rememberSaveable { mutableStateOf<Sex?>(null) }
    val list = Characters.all.filter { (category == null || it.category == category) && (sex == null || it.sex == sex) }
    val current = state.profile?.characterId

    Column(Modifier.fillMaxSize().background(Neon.Black)) {
        NeonTopBar("Scegli il tuo fisico", Neon.Magenta, onBack)
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            contentPadding = PaddingValues(start = 10.dp, end = 10.dp, bottom = 32.dp),
        ) {
            item(span = { GridItemSpan(2) }) {
                Column {
                    FlowRow {
                        NeonChip("Tutti", Neon.Cyan, category == null) { category = null }
                        Category.entries.forEach { NeonChip(it.label, Neon.Cyan, category == it) { category = it } }
                    }
                    FlowRow {
                        NeonChip("Uomo e donna", Neon.Magenta, sex == null) { sex = null }
                        Sex.entries.forEach { NeonChip(it.label, Neon.Magenta, sex == it) { sex = it } }
                    }
                }
            }
            items(list, key = { it.id }) { c ->
                NeonCard(c.neon(), onClick = { onOpen(c.id) }, contentPadding = PaddingValues(10.dp)) {
                    PhysiqueFigure(c.figure(), c.neon(), Modifier.fillMaxWidth().height(150.dp))
                    Text(c.name, style = glowText(c.neon(), 16, FontWeight.Black), maxLines = 1)
                    Text(c.origin, style = glowText(Neon.TextDim, 12, FontWeight.Normal), maxLines = 1)
                    Text(stars(c.difficulty), style = glowText(Neon.Yellow, 13, FontWeight.Normal))
                    Text(
                        if (c.id == current) "✔ ATTUALE" else c.style.label,
                        style = glowText(if (c.id == current) Neon.Green else Neon.Text, 12, FontWeight.SemiBold),
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CharacterDetailScreen(vm: AppViewModel, id: String, onChosen: () -> Unit, onBack: () -> Unit) {
    val c = Characters.get(id) ?: return
    val state by vm.state.collectAsStateWithLifecycle()
    val refVersion by vm.refVersion.collectAsStateWithLifecycle()
    val color = c.neon()
    val level = state.profile?.level ?: Level.PRINCIPIANTE
    val isCurrent = state.profile?.characterId == c.id
    val refFile = vm.referenceFile(c.id)
    val refImage = rememberImage(refFile.takeIf { it.exists() }, key = refVersion)
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) vm.setReference(c.id, uri)
    }

    NeonScreen(c.name, color, onBack) {
        item {
            Row(Modifier.fillMaxWidth().height(360.dp)) {
                PhysiqueFigure(c.figure(), color, Modifier.weight(1f).fillMaxSize())
                if (refImage != null) {
                    Image(
                        refImage, "Immagine di riferimento",
                        Modifier.weight(1f).fillMaxSize().padding(6.dp),
                        contentScale = ContentScale.Fit,
                    )
                }
            }
            Text(
                "${c.origin} · ${c.category.label} · ${stars(c.difficulty)}",
                Modifier.fillMaxWidth(), style = glowText(Neon.TextDim, 14, FontWeight.Normal).copy(textAlign = TextAlign.Center),
            )
        }
        item {
            NeonCard(color) {
                BodyText(c.description)
                if (c.fantasy) {
                    Spacer(Modifier.height(8.dp))
                    BodyText(
                        "⚠ Il fisico originale è esagerato: il 100% corrisponde a una versione umana e raggiungibile.",
                        color = Neon.Yellow, size = 13,
                    )
                }
            }
        }
        item {
            SectionTitle("Zone chiave", color)
            FlowRow { c.focus.forEach { NeonChip(it.label, color, true) {} } }
            SectionTitle("Settimana tipo · ${c.style.label}", color)
            NeonCard(color) {
                c.style.week.forEachIndexed { i, d ->
                    val special = i in c.signature.days
                    Row(Modifier.padding(vertical = 3.dp)) {
                        Text(WEEKDAYS[i], Modifier.width(48.dp), style = glowText(color, 15))
                        BodyText(d.label + if (special) "  + ★ ${c.signature.title}" else "", size = 14)
                    }
                }
            }
        }
        item {
            SectionTitle("★ ${c.signature.title}", Neon.Yellow)
            NeonCard(Neon.Yellow) {
                BodyText(c.signature.note, color = Neon.TextDim, size = 13)
                Spacer(Modifier.height(6.dp))
                c.signature.items.forEach { item ->
                    val ex = Exercises.get(item.exerciseId)
                    val p = PlannedExercise(ex, item.sets, item.amount[level.ordinal], 0, Tag.FIRMA)
                    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        BodyText(ex.name, Modifier.weight(1f))
                        Text(p.doseText, style = glowText(Neon.Yellow, 15))
                    }
                }
                BodyText("Valori per il livello ${level.label.lowercase()}: crescono ogni settimana.", color = Neon.TextDim, size = 12)
            }
        }
        item {
            SectionTitle("Immagine del personaggio", Neon.Cyan)
            BodyText(
                "Per rispetto del copyright l'app non contiene immagini dei personaggi. Puoi aggiungerne una tua " +
                    "dalla galleria: la vedrai accanto alla tua foto nei confronti.",
                color = Neon.TextDim, size = 13,
            )
            ButtonRow {
                NeonButton(if (refImage == null) "Aggiungi immagine" else "Cambia", Neon.Cyan, Modifier.weight(1f)) {
                    picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                }
                if (refImage != null) NeonButton("Rimuovi", Neon.Red, Modifier.weight(1f)) { vm.removeReference(c.id) }
            }
        }
        item {
            Spacer(Modifier.height(16.dp))
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                if (isCurrent) {
                    NeonButton("Personaggio attuale ✔", Neon.Green, Modifier.fillMaxWidth(), enabled = false) {}
                } else {
                    NeonButton("Scegli ${c.name}", color, Modifier.fillMaxWidth(), filled = true) {
                        vm.chooseCharacter(c.id)
                        onChosen()
                    }
                }
            }
        }
    }
}
