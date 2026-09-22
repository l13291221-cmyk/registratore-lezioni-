package com.registratorelezioni

import android.Manifest
import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.View
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
        b.btnEsatti.setOnClickListener { apriSvegliePrecise() }
        b.btnBatteria.setOnClickListener { apriEsenzioneBatteria() }
        b.btnAutostart.setOnClickListener { apriAutostart() }

        b.btnAttivaAuto.setOnClickListener {
            Scheduler.riprogramma(this)
            aggiornaStato()
        }
        b.btnManuale.setOnClickListener {
            if (RecordingService.registrazioneInCorso) RecordingService.ferma(this)
            else RecordingService.avvia(this, "Manuale")
            b.root.postDelayed({ aggiornaStato() }, 700)
        }
        b.btnRicaricaOrario.setOnClickListener { aggiornaStato() }
        b.btnGuarda.setOnClickListener {
            startActivity(Intent(this, RegistrazioniActivity::class.java))
        }
    }

    override fun onResume() {
        super.onResume()
        aggiornaStato()
    }

    private fun chiediPermessi() {
        val lista = mutableListOf(Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            lista.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        richiediPermessi.launch(lista.toTypedArray())
    }

    private fun apriSvegliePrecise() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try {
                startActivity(
                    Intent(
                        Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                        Uri.parse("package:$packageName")
                    )
                )
                return
            } catch (_: Exception) { }
        }
        apriDettagliApp()
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
        val micOk = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        val esattiOk = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
            (getSystemService(Context.ALARM_SERVICE) as AlarmManager).canScheduleExactAlarms() else true
        val batteriaOk = (getSystemService(Context.POWER_SERVICE) as PowerManager)
            .isIgnoringBatteryOptimizations(packageName)

        val lezioni = Timetable.carica(this)

        val sb = StringBuilder()
        sb.append(if (RecordingService.registrazioneInCorso) "🔴 STA REGISTRANDO" else "⚪ In attesa")
        RecordingService.materiaInCorso?.let { sb.append("  ($it)") }
        sb.append("\n\n")
        sb.append("Microfono: ${if (micOk) "OK ✅" else "manca ❌"}\n")
        sb.append("Sveglie precise: ${if (esattiOk) "OK ✅" else "manca ❌"}\n")
        sb.append("Batteria libera: ${if (batteriaOk) "OK ✅" else "da fare ❌"}\n\n")
        sb.append("Lezioni caricate dall'orario: ${lezioni.size}")
        b.stato.text = sb.toString()

        b.btnEsatti.visibility = if (esattiOk) View.GONE else View.VISIBLE
        b.btnManuale.text =
            if (RecordingService.registrazioneInCorso) "Ferma registrazione"
            else "Avvia registrazione manuale"

        val cartella = getExternalFilesDir(null)?.absolutePath ?: "?"
        b.percorso.text = "Registrazioni e file orario.json in:\n$cartella"
    }
}
