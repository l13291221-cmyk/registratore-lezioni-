package com.registratorelezioni

import android.content.Context
import org.json.JSONObject
import java.io.File
import java.util.Calendar

/** Una lezione dell'orario settimanale. */
data class Lezione(
    val giorno: Int,     // Calendar.MONDAY ... Calendar.SUNDAY
    val materia: String,
    val inizioMin: Int,  // minuti dalla mezzanotte (es. 08:00 -> 480)
    val fineMin: Int
)

/**
 * Legge l'orario da un file JSON modificabile dall'utente.
 * Il file "orario.json" vive nella cartella dei file dell'app in memoria esterna,
 * così lo puoi aprire/modificare col telefono o via USB.
 * Alla prima apertura viene copiato dall'esempio incluso nell'app (assets).
 */
object Timetable {

    const val NOME_FILE = "orario.json"

    fun fileOrario(context: Context): File =
        File(context.getExternalFilesDir(null), NOME_FILE)

    fun carica(context: Context): List<Lezione> {
        val f = fileOrario(context)
        if (!f.exists()) copiaDefault(context, f)
        val testo = try { f.readText() } catch (e: Exception) { leggiAsset(context) }
        return parse(testo)
    }

    private fun copiaDefault(context: Context, dest: File) {
        try {
            dest.parentFile?.mkdirs()
            dest.writeText(leggiAsset(context))
        } catch (_: Exception) { }
    }

    private fun leggiAsset(context: Context): String = try {
        context.assets.open(NOME_FILE).bufferedReader().use { it.readText() }
    } catch (e: Exception) {
        "{\"lezioni\":[]}"
    }

    fun parse(testo: String): List<Lezione> {
        val lista = mutableListOf<Lezione>()
        try {
            val arr = JSONObject(testo).optJSONArray("lezioni") ?: return lista
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val giorno = giornoDaStringa(o.optString("giorno")) ?: continue
                val materia = o.optString("materia", "Lezione")
                val inizio = minutiDaOra(o.optString("inizio")) ?: continue
                val fine = minutiDaOra(o.optString("fine")) ?: continue
                if (fine <= inizio) continue
                lista.add(Lezione(giorno, materia, inizio, fine))
            }
        } catch (_: Exception) { }
        return lista
    }

    private fun giornoDaStringa(s: String): Int? = when (s.trim().uppercase()) {
        "LUNEDI", "LUNEDÌ", "LUN", "MONDAY" -> Calendar.MONDAY
        "MARTEDI", "MARTEDÌ", "MAR", "TUESDAY" -> Calendar.TUESDAY
        "MERCOLEDI", "MERCOLEDÌ", "MER", "WEDNESDAY" -> Calendar.WEDNESDAY
        "GIOVEDI", "GIOVEDÌ", "GIO", "THURSDAY" -> Calendar.THURSDAY
        "VENERDI", "VENERDÌ", "VEN", "FRIDAY" -> Calendar.FRIDAY
        "SABATO", "SAB", "SATURDAY" -> Calendar.SATURDAY
        "DOMENICA", "DOM", "SUNDAY" -> Calendar.SUNDAY
        else -> null
    }

    private fun minutiDaOra(s: String): Int? {
        val parti = s.trim().split(":")
        if (parti.size != 2) return null
        val h = parti[0].toIntOrNull() ?: return null
        val m = parti[1].toIntOrNull() ?: return null
        if (h !in 0..23 || m !in 0..59) return null
        return h * 60 + m
    }
}
