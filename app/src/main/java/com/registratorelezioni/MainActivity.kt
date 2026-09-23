package com.registratorelezioni

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.registratorelezioni.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var b: ActivityMainBinding

    private val richiediPermessi = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { aggiornaStato() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityMainBinding.inflate(layoutInflater)
        setContentView(b.root)

        b.btnPermessi.setOnClickListener { chiediPermessi() }
        b.btnBatteria.setOnClickListener { apriEsenzioneBatteria() }
        b.btnAutostart.setOnClickListener { apriAutostart() }

        b.btnGiornata.setOnClickListener {
            if (RecordingService.registrazioneInCorso) RecordingService.ferma(this)
            else if (micConcesso()) RecordingService.avviaGiornata(this)
            else chiediPermessi()
            b.root.postDelayed({ aggiornaStato() }, 700)
        }
        b.btnManuale.setOnClickListener {
            if (RecordingService.registrazioneInCorso) RecordingService.ferma(this)
            else if (micConcesso()) RecordingService.avviaManuale(this)
            else chiediPermessi()
            b.root.postDelayed({ aggiornaStato() }, 700)
        }
        b.btnRicaricaOrario.setOnClickListener { aggiornaStato() }
        b.btnGuarda.setOnClickListener {
            startActivity(Intent(this, RegistrazioniActivity::class.java))
        }
    }

    // Aggiorna lo stato ogni pochi secondi mentre l'app è aperta.
    private val aggiornaPeriodico = object : Runnable {
        override fun run() {
            aggiornaStato()
            b.root.postDelayed(this, 3000)
        }
    }

    override fun onResume() {
        super.onResume()
        aggiornaPeriodico.run()
    }

    override fun onPause() {
        super.onPause()
        b.root.removeCallbacks(aggiornaPeriodico)
    }

    private fun micConcesso(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED

    private fun chiediPermessi() {
        val lista = mutableListOf(Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            lista.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        richiediPermessi.launch(lista.toTypedArray())
    }

    private fun apriEsenzioneBatteria() {
        try {
            startActivity(
                Intent(
                    Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                    Uri.parse("package:$packageName")
                )
            )
        } catch (e: Exception) {
            apriDettagliApp()
        }
    }

    private fun apriAutostart() {
        // Prova ad aprire la schermata "Avvio automatico" di realme / ColorOS / Oppo.
        val tentativi = listOf(
            Intent().setClassName(
                "com.coloros.safecenter",
                "com.coloros.safecenter.permission.startup.StartupAppListActivity"
            ),
            Intent().setClassName(
                "com.coloros.safecenter",
                "com.coloros.safecenter.startupapp.StartupAppListActivity"
            ),
            Intent().setClassName(
                "com.oplus.safecenter",
                "com.oplus.safecenter.permission.startup.StartupAppListActivity"
            ),
            Intent().setClassName(
                "com.oppo.safe",
                "com.oppo.safe.permission.startup.StartupAppListActivity"
            )
        )
        for (i in tentativi) {
            try { startActivity(i); return } catch (_: Exception) { }
        }
        apriDettagliApp()
    }

    private fun apriDettagliApp() {
        try {
            startActivity(
                Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:$packageName")
                )
            )
        } catch (_: Exception) { }
    }

    private fun aggiornaStato() {
        val micOk = micConcesso()
        val batteriaOk = (getSystemService(Context.POWER_SERVICE) as PowerManager)
            .isIgnoringBatteryOptimizations(packageName)

        val lezioni = Timetable.carica(this)
        val attivo = RecordingService.registrazioneInCorso

        val sb = StringBuilder()
        sb.append(RecordingService.stato)
        sb.append("\n\n")
        sb.append("Microfono: ${if (micOk) "OK ✅" else "manca ❌"}\n")
        sb.append("Batteria libera: ${if (batteriaOk) "OK ✅" else "da fare ❌"}\n\n")
        sb.append("Lezioni caricate dall'orario: ${lezioni.size}")
        b.stato.text = sb.toString()

        b.btnGiornata.text =
            if (attivo && RecordingService.modoGiornata) "Ferma giornata"
            else "▶ Avvia giornata (microfono sempre acceso)"
        b.btnGiornata.isEnabled = !attivo || RecordingService.modoGiornata
        b.btnManuale.text =
            if (attivo && !RecordingService.modoGiornata) "Ferma registrazione manuale"
            else "Avvia registrazione manuale"
        b.btnManuale.isEnabled = !attivo || !RecordingService.modoGiornata

        val cartella = getExternalFilesDir(null)?.absolutePath ?: "?"
        b.percorso.text = "Registrazioni e file orario.json in:\n$cartella"
    }
}
