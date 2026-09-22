package com.registratorelezioni

import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import com.registratorelezioni.databinding.ActivityRegistrazioniBinding
import java.io.File
import java.util.Locale

/**
 * Elenca le registrazioni salvate, raggruppate per materia (una cartella per
 * materia sotto getExternalFilesDir). Ogni riga ha un tasto "Condividi" che
 * manda il file ad altre app tramite FileProvider.
 */
class RegistrazioniActivity : AppCompatActivity() {

    private lateinit var b: ActivityRegistrazioniBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityRegistrazioniBinding.inflate(layoutInflater)
        setContentView(b.root)
    }

    override fun onResume() {
        super.onResume()
        popola()
    }

    /** Ricostruisce la lista leggendo cartelle e file dal disco. */
    private fun popola() {
        b.contenitore.removeAllViews()

        val base = getExternalFilesDir(null)
        val materie = (base?.listFiles() ?: emptyArray())
            .filter { it.isDirectory }
            .sortedBy { it.name.lowercase(Locale.ITALY) }

        var totale = 0
        for (cartella in materie) {
            val registrazioni = (cartella.listFiles() ?: emptyArray())
                .filter { it.isFile && it.name.endsWith(".m4a", ignoreCase = true) }
                .sortedByDescending { it.name } // il nome inizia con la data: più recenti in alto
            if (registrazioni.isEmpty()) continue

            b.contenitore.addView(intestazioneMateria(cartella.name))
            for (f in registrazioni) {
                b.contenitore.addView(riga(f, cartella.name))
                totale++
            }
        }

        b.vuoto.visibility = if (totale == 0) View.VISIBLE else View.GONE
    }

    private fun intestazioneMateria(nome: String): TextView {
        val tv = TextView(this)
        tv.text = nome
        tv.textSize = 18f
        tv.setTypeface(tv.typeface, Typeface.BOLD)
        tv.setPadding(0, dp(18), 0, dp(6))
        return tv
    }

    private fun riga(f: File, materia: String): LinearLayout {
        val riga = LinearLayout(this)
        riga.orientation = LinearLayout.HORIZONTAL
        riga.gravity = Gravity.CENTER_VERTICAL
        riga.setPadding(0, dp(4), 0, dp(4))

        val testo = TextView(this)
        testo.text = etichetta(f, materia)
        testo.textSize = 14f
        val lp = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        lp.marginEnd = dp(8)
        testo.layoutParams = lp

        val btn = Button(this)
        btn.text = "Condividi"
        btn.setOnClickListener { condividi(f) }

        riga.addView(testo)
        riga.addView(btn)
        return riga
    }

    /** Etichetta leggibile ricavata dal nome file (con fallback al nome grezzo). */
    private fun etichetta(f: File, materia: String): String {
        return try {
            val senzaMateria = f.nameWithoutExtension.removePrefix("${materia}_")
            val parti = senzaMateria.split("_")
            val leggibile = if (parti.size >= 3)
                "${parti[1]} ${parti[0]} · ${parti[2].replace("-", ":")}"
            else senzaMateria.replace("_", " ")
            val mb = f.length() / 1024.0 / 1024.0
            String.format(Locale.ITALY, "%s · %.1f MB", leggibile, mb)
        } catch (e: Exception) {
            f.name
        }
    }

    private fun condividi(f: File) {
        try {
            val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", f)
            val invio = Intent(Intent.ACTION_SEND).apply {
                type = "audio/mp4"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(invio, "Condividi registrazione"))
        } catch (e: Exception) {
            Toast.makeText(this, "Impossibile condividere: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
}
