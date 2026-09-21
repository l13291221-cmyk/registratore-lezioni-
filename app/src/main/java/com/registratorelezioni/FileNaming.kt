package com.registratorelezioni

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * Crea il file di destinazione della registrazione.
 * Struttura: <cartella app>/<Materia>/<Materia>_AAAA-MM-GG_Giorno_HH-mm.m4a
 * La data in formato anno-mese-giorno si ordina da sola cronologicamente.
 */
object FileNaming {

    private val GIORNI = arrayOf(
        "", "Domenica", "Lunedi", "Martedi", "Mercoledi", "Giovedi", "Venerdi", "Sabato"
    )

    fun creaFile(context: Context, materia: String): File {
        val cal = Calendar.getInstance()
        val data = SimpleDateFormat("yyyy-MM-dd", Locale.ITALY).format(cal.time)
        val ora = SimpleDateFormat("HH-mm", Locale.ITALY).format(cal.time)
        val giorno = GIORNI[cal.get(Calendar.DAY_OF_WEEK)]
        val materiaPulita = pulisci(materia)

        val cartella = File(context.getExternalFilesDir(null), materiaPulita)
        cartella.mkdirs()

        val nome = "${materiaPulita}_${data}_${giorno}_${ora}.m4a"
        return File(cartella, nome)
    }

    private fun pulisci(s: String): String =
        s.trim()
            .replace(Regex("[^A-Za-z0-9àèéìòóù _-]"), "")
            .replace(" ", "-")
            .ifBlank { "Lezione" }
}
