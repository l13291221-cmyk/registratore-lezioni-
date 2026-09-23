package com.registratorelezioni

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.Calendar
import java.util.Locale

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

    /** Giorni nell'ordine della settimana scolastica, con il nome da mostrare. */
    val GIORNI = listOf(
        Calendar.MONDAY to "Lunedì",
        Calendar.TUESDAY to "Martedì",
        Calendar.WEDNESDAY to "Mercoledì",
        Calendar.THURSDAY to "Giovedì",
        Calendar.FRIDAY to "Venerdì",
        Calendar.SATURDAY to "Sabato",
        Calendar.SUNDAY to "Domenica"
    )

    /** Aumenta a ogni salvataggio: la registrazione in corso ricarica l'orario da sola. */
    @Volatile var versione = 0
        private set

    /** Salva l'orario nel file orario.json (ordinato per giorno e ora). */
    fun salva(context: Context, lezioni: List<Lezione>): Boolean {
        val ordine = GIORNI.map { it.first }
        val arr = JSONArray()
        for (l in lezioni.sortedWith(compareBy({ ordine.indexOf(it.giorno) }, { it.inizioMin }))) {
            arr.put(
                JSONObject()
                    .put("giorno", giornoInStringa(l.giorno))
                    .put("materia", l.materia)
                    .put("inizio", formattaOra(l.inizioMin))
                    .put("fine", formattaOra(l.fineMin))
            )
        }
        return try {
            val f = fileOrario(context)
            f.parentFile?.mkdirs()
            f.writeText(JSONObject().put("lezioni", arr).toString(2))
            versione++
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun giornoInStringa(g: Int): String = when (g) {
        Calendar.MONDAY -> "LUNEDI"
        Calendar.TUESDAY -> "MARTEDI"
        Calendar.WEDNESDAY -> "MERCOLEDI"
        Calendar.THURSDAY -> "GIOVEDI"
        Calendar.FRIDAY -> "VENERDI"
        Calendar.SATURDAY -> "SABATO"
        else -> "DOMENICA"
    }

    fun carica(context: Context): List<Lezione> {
        val f = fileOrario(context)
        if (!f.exists()) copiaDefault(context, f)
        val testo = try { f.readText() } catch (e: Exception) { leggiAsset(context) }
        return parse(testo)
    }

    private fun minutoDelGiorno(now: Calendar): Int =
        now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)

    /** La lezione in corso adesso, o null (intervallo / fuori orario). */
    fun lezioneCorrente(lezioni: List<Lezione>, now: Calendar): Lezione? {
        val g = now.get(Calendar.DAY_OF_WEEK)
        val min = minutoDelGiorno(now)
        return lezioni.firstOrNull { it.giorno == g && min >= it.inizioMin && min < it.fineMin }
    }

    /** La prossima lezione di oggi che deve ancora iniziare, o null. */
    fun prossimaOggi(lezioni: List<Lezione>, now: Calendar): Lezione? {
        val g = now.get(Calendar.DAY_OF_WEEK)
        val min = minutoDelGiorno(now)
        return lezioni.filter { it.giorno == g && it.inizioMin > min }.minByOrNull { it.inizioMin }
    }

    fun formattaOra(minuti: Int): String = String.format(Locale.ROOT, "%02d:%02d", minuti / 60, minuti % 60)

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
