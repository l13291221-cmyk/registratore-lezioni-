package com.registratorelezioni

import android.app.TimePickerDialog
import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.webkit.MimeTypeMap
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import com.registratorelezioni.databinding.ActivityOrarioBinding
import java.io.File

/**
 * Modifica dell'orario direttamente dall'app: aggiungi, cambia o cancella le
 * lezioni di ogni giorno. Ogni modifica viene salvata subito in orario.json.
 * In alto si può allegare la foto (screenshot) dell'orario come riferimento.
 */
class OrarioActivity : AppCompatActivity() {

    private lateinit var b: ActivityOrarioBinding
    private val lezioni = mutableListOf<Lezione>()

    private val scegliFoto = registerForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri -> if (uri != null) salvaFoto(uri) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityOrarioBinding.inflate(layoutInflater)
        setContentView(b.root)

        b.btnFoto.setOnClickListener {
            scegliFoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }
        b.btnRimuoviFoto.setOnClickListener {
            conferma("Rimuovere la foto dell'orario?") {
                fotoSalvata()?.delete()
                mostraFoto()
            }
        }
        b.foto.setOnClickListener { apriFotoGrande() }
        b.btnCancellaTutto.setOnClickListener {
            conferma("Cancellare TUTTE le lezioni dell'orario?") {
                lezioni.clear()
                salva()
            }
        }

        lezioni.addAll(Timetable.carica(this))
        mostraFoto()
        popola()
    }

    // ---------- foto dell'orario ----------

    private fun fotoSalvata(): File? =
        getExternalFilesDir(null)?.listFiles()?.firstOrNull { it.isFile && it.name.startsWith(NOME_FOTO + ".") }

    private fun salvaFoto(uri: Uri) {
        try {
            val tipo = contentResolver.getType(uri)
            val est = MimeTypeMap.getSingleton().getExtensionFromMimeType(tipo ?: "") ?: "jpg"
            val dest = File(getExternalFilesDir(null), "$NOME_FOTO.$est")
            val tmp = File(getExternalFilesDir(null), "$NOME_FOTO.tmp")
            contentResolver.openInputStream(uri).use { input ->
                requireNotNull(input)
                tmp.outputStream().use { input.copyTo(it) }
            }
            fotoSalvata()?.delete()
            tmp.renameTo(dest)
            mostraFoto()
        } catch (e: Exception) {
            Toast.makeText(this, "Impossibile salvare la foto: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun mostraFoto() {
        val f = fotoSalvata()
        val bmp = f?.let { caricaRidotta(it) }
        if (bmp != null) {
            b.foto.setImageBitmap(bmp)
            b.foto.visibility = View.VISIBLE
            b.fotoInfo.text = "Tocca la foto per ingrandirla."
            b.btnFoto.text = "Cambia foto"
            b.btnRimuoviFoto.visibility = View.VISIBLE
        } else {
            b.foto.setImageDrawable(null)
            b.foto.visibility = View.GONE
            b.fotoInfo.text = "Nessuna foto. Allega lo screenshot dell'orario per averlo sempre " +
                "qui mentre inserisci le lezioni."
            b.btnFoto.text = "Allega foto orario"
            b.btnRimuoviFoto.visibility = View.GONE
        }
    }

    /** Decodifica l'immagine rimpicciolita (larghezza max ~1400 px) per non esaurire la memoria. */
    private fun caricaRidotta(f: File) = try {
        val opz = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(f.absolutePath, opz)
        var scala = 1
        while (opz.outWidth / (scala * 2) >= 1400) scala *= 2
        BitmapFactory.decodeFile(f.absolutePath, BitmapFactory.Options().apply { inSampleSize = scala })
    } catch (_: Throwable) {
        null
    }

    private fun apriFotoGrande() {
        val f = fotoSalvata() ?: return
        try {
            val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", f)
            startActivity(
                Intent(Intent.ACTION_VIEW)
                    .setDataAndType(uri, "image/*")
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            )
        } catch (_: Exception) {
            Toast.makeText(this, "Nessuna app per aprire la foto", Toast.LENGTH_SHORT).show()
        }
    }

    // ---------- lezioni ----------

    private fun salva() {
        if (!Timetable.salva(this, lezioni)) {
            Toast.makeText(this, "⚠️ Errore nel salvare l'orario", Toast.LENGTH_LONG).show()
        }
        popola()
    }

    private fun popola() {
        b.contenitore.removeAllViews()
        for ((giorno, nome) in Timetable.GIORNI) {
            val delGiorno = lezioni.filter { it.giorno == giorno }.sortedBy { it.inizioMin }
            // la domenica compare solo se ha già delle lezioni
            if (giorno == java.util.Calendar.SUNDAY && delGiorno.isEmpty()) continue

            b.contenitore.addView(intestazione(nome))
            if (delGiorno.isEmpty()) {
                val vuoto = TextView(this)
                vuoto.text = "Nessuna lezione"
                vuoto.setTextColor(0xFF888888.toInt())
                vuoto.setPadding(0, dp(2), 0, dp(2))
                b.contenitore.addView(vuoto)
            }
            for (l in delGiorno) b.contenitore.addView(riga(l))

            val aggiungi = Button(this)
            aggiungi.text = "+ Aggiungi lezione di $nome"
            aggiungi.setOnClickListener { modifica(null, giorno) }
            b.contenitore.addView(aggiungi)
        }
    }

    private fun intestazione(nome: String): TextView {
        val tv = TextView(this)
        tv.text = nome
        tv.textSize = 18f
        tv.setTypeface(tv.typeface, Typeface.BOLD)
        tv.setPadding(0, dp(16), 0, dp(4))
        return tv
    }

    private fun riga(l: Lezione): LinearLayout {
        val riga = LinearLayout(this)
        riga.orientation = LinearLayout.HORIZONTAL
        riga.gravity = Gravity.CENTER_VERTICAL

        val testo = TextView(this)
        testo.text = "${Timetable.formattaOra(l.inizioMin)}–${Timetable.formattaOra(l.fineMin)}   ${l.materia}"
        testo.textSize = 15f
        testo.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        testo.setPadding(0, dp(8), dp(8), dp(8))
        testo.setOnClickListener { modifica(l, l.giorno) }

        val modifica = Button(this)
        modifica.text = "Modifica"
        modifica.setOnClickListener { modifica(l, l.giorno) }

        val elimina = Button(this)
        elimina.text = "✕"
        elimina.setOnClickListener {
            conferma("Cancellare ${l.materia} (${Timetable.formattaOra(l.inizioMin)})?") {
                lezioni.remove(l)
                salva()
            }
        }

        riga.addView(testo)
        riga.addView(modifica)
        riga.addView(elimina)
        return riga
    }

    /** Finestra per aggiungere (originale = null) o modificare una lezione. */
    private fun modifica(originale: Lezione?, giorno: Int) {
        val nomeGiorno = Timetable.GIORNI.first { it.first == giorno }.second
        val stessoGiorno = lezioni.filter { it.giorno == giorno && it != originale }
        var inizio = originale?.inizioMin ?: (stessoGiorno.maxOfOrNull { it.fineMin } ?: 8 * 60)
        var fine = originale?.fineMin ?: minOf(inizio + 60, 23 * 60 + 59)

        val box = LinearLayout(this)
        box.orientation = LinearLayout.VERTICAL
        box.setPadding(dp(20), dp(8), dp(20), 0)

        val materia = AutoCompleteTextView(this)
        materia.hint = "Materia (es. Matematica)"
        materia.setText(originale?.materia ?: "")
        materia.threshold = 1
        materia.setAdapter(
            ArrayAdapter(
                this, android.R.layout.simple_dropdown_item_1line,
                lezioni.map { it.materia }.distinct().sorted()
            )
        )
        box.addView(materia)

        val btnInizio = Button(this)
        val btnFine = Button(this)
        fun aggiornaTesti() {
            btnInizio.text = "Inizio: ${Timetable.formattaOra(inizio)}"
            btnFine.text = "Fine: ${Timetable.formattaOra(fine)}"
        }
        aggiornaTesti()
        btnInizio.setOnClickListener {
            TimePickerDialog(this, { _, h, m ->
                val durata = fine - inizio
                inizio = h * 60 + m
                if (fine <= inizio) fine = minOf(inizio + maxOf(durata, 60), 23 * 60 + 59)
                aggiornaTesti()
            }, inizio / 60, inizio % 60, true).show()
        }
        btnFine.setOnClickListener {
            TimePickerDialog(this, { _, h, m ->
                fine = h * 60 + m
                aggiornaTesti()
            }, fine / 60, fine % 60, true).show()
        }
        box.addView(btnInizio)
        box.addView(btnFine)

        val dlg = AlertDialog.Builder(this)
            .setTitle(if (originale == null) "Nuova lezione · $nomeGiorno" else "Modifica · $nomeGiorno")
            .setView(box)
            .setPositiveButton("Salva", null)
            .setNegativeButton("Annulla", null)
            .create()
        dlg.setOnShowListener {
            // gestiamo noi il click, così in caso di errore la finestra resta aperta
            dlg.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val nome = materia.text.toString().trim()
                val errore = when {
                    nome.isEmpty() -> "Scrivi il nome della materia"
                    fine <= inizio -> "L'ora di fine deve essere dopo l'inizio"
                    else -> stessoGiorno.firstOrNull { it.inizioMin < fine && inizio < it.fineMin }
                        ?.let { "Si sovrappone a ${it.materia} " +
                            "(${Timetable.formattaOra(it.inizioMin)}–${Timetable.formattaOra(it.fineMin)})" }
                }
                if (errore != null) {
                    Toast.makeText(this, errore, Toast.LENGTH_LONG).show()
                    return@setOnClickListener
                }
                val nuova = Lezione(giorno, nome, inizio, fine)
                val i = if (originale != null) lezioni.indexOf(originale) else -1
                if (i >= 0) lezioni[i] = nuova else lezioni.add(nuova)
                salva()
                dlg.dismiss()
            }
        }
        dlg.show()
    }

    private fun conferma(domanda: String, azione: () -> Unit) {
        AlertDialog.Builder(this)
            .setMessage(domanda)
            .setPositiveButton("Sì") { _, _ -> azione() }
            .setNegativeButton("No", null)
            .show()
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    companion object {
        private const val NOME_FOTO = "foto_orario"
    }
}
