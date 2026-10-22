package com.animefit.ui.screens

import android.app.TimePickerDialog
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.animefit.AppViewModel
import com.animefit.data.Profile
import com.animefit.training.Coach
import com.animefit.ui.components.NeonDialog
import com.animefit.ui.components.NeonScreen
import com.animefit.ui.theme.BodyText
import com.animefit.ui.theme.Neon
import com.animefit.ui.theme.NeonButton
import com.animefit.ui.theme.NeonCard
import com.animefit.ui.theme.SectionTitle

@Composable
fun SettingsScreen(vm: AppViewModel, onCharacters: () -> Unit, onWiped: () -> Unit, onBack: () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    val profile = state.profile ?: Profile()
    val context = LocalContext.current
    var confirmWipe by rememberSaveable { mutableStateOf(false) }
    var saved by rememberSaveable { mutableStateOf(false) }

    if (confirmWipe) {
        NeonDialog(
            "Eliminare tutto?",
            "Profilo, foto, misure e storico allenamenti verranno cancellati per sempre.",
            Neon.Red,
            confirm = "Elimina tutto", onConfirm = { confirmWipe = false; vm.wipeAll(); onWiped() },
            dismiss = "Annulla", onDismiss = { confirmWipe = false },
        )
    }

    NeonScreen("Impostazioni", Neon.Orange, onBack) {
        item {
            ProfileForm(profile, if (saved) "Salvato ✔" else "Salva profilo") {
                vm.saveProfile(it)
                saved = true
            }
        }
        item {
            SectionTitle("Promemoria", Neon.Cyan)
            NeonCard(Neon.Cyan) {
                BodyText(
                    "Ogni giorno alle %02d:%02d l'app ti avvisa se manca l'allenamento o se è il giorno della foto."
                        .format(profile.reminderHour, profile.reminderMinute),
                    size = 14,
                )
                NeonButton("Cambia orario", Neon.Cyan, Modifier.fillMaxWidth()) {
                    TimePickerDialog(context, android.R.style.Theme_Material_Dialog_Alert, { _, h, m ->
                        vm.saveProfile(profile.copy(reminderHour = h, reminderMinute = m))
                    }, profile.reminderHour, profile.reminderMinute, true).show()
                }
            }
        }
        item {
            SectionTitle("Personaggio", Neon.Purple)
            NeonCard(Neon.Purple) {
                BodyText("Attuale: ${Coach.character(state)?.name ?: "-"}. Cambiandolo, foto e misure restano: cambia solo l'obiettivo.", size = 14)
                NeonButton("Cambia personaggio", Neon.Purple, Modifier.fillMaxWidth(), onClick = onCharacters)
            }
        }
        item {
            SectionTitle("Privacy", Neon.Green)
            NeonCard(Neon.Green) {
                BodyText(
                    "Le foto restano solo nella memoria privata dell'app e vengono analizzate sul telefono, senza " +
                        "internet. L'app non è collegata ai personaggi o alle persone citate: i nomi servono solo come obiettivo.",
                    size = 14,
                )
            }
        }
        item {
            NeonButton("Elimina tutti i dati", Neon.Red, Modifier.fillMaxWidth().padding(top = 16.dp)) { confirmWipe = true }
        }
    }
}
