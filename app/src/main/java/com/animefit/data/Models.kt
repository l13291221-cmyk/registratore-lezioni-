package com.animefit.data

import kotlinx.serialization.Serializable

@Serializable
enum class Sex(val label: String) { UOMO("Uomo"), DONNA("Donna") }

@Serializable
enum class Level(val label: String, val maxDifficulty: Int) {
    PRINCIPIANTE("Principiante", 1),
    INTERMEDIO("Intermedio", 2),
    AVANZATO("Avanzato", 3),
}

@Serializable
enum class Equipment(val label: String, val detail: String) {
    CASA("Casa", "Solo corpo libero (più uno zaino e una sedia)"),
    CASA_SBARRA("Casa + sbarra", "Corpo libero con sbarra per trazioni"),
    PALESTRA("Palestra", "Bilanciere, manubri, macchine e cavi"),
}

/**
 * Misure ricavate dalla foto. Sono larghezze divise per la lunghezza del
 * busto (spalla-anca), così non dipendono dalla distanza dalla fotocamera.
 * DEFINIZIONE è invece un indice 0..1 del contrasto sugli addominali.
 */
@Serializable
enum class Measure(val label: String) {
    SPALLE("Larghezza spalle"),
    TORACE("Larghezza torace/dorsali"),
    VITA("Larghezza vita"),
    FIANCHI("Larghezza fianchi"),
    BRACCIO("Spessore braccio"),
    AVAMBRACCIO("Spessore avambraccio"),
    COSCIA("Spessore coscia"),
    POLPACCIO("Spessore polpaccio"),
    DEFINIZIONE("Definizione addominali (stima)"),
}

@Serializable
data class Pt(val x: Float, val y: Float)

/** Un segmento di misura disegnato sopra la foto ("qui ho misurato le spalle"). */
@Serializable
data class Seg(val measure: Measure, val a: Pt, val b: Pt)

/** Dati geometrici della foto analizzata, in pixel dell'immagine salvata. */
@Serializable
data class Geometry(
    val imageWidth: Int,
    val imageHeight: Int,
    val maskWidth: Int,
    val maskHeight: Int,
    val torsoPx: Float,
    val shoulderMid: Pt,
    val hipMid: Pt,
    val landmarks: Map<String, Pt>,
    val segments: List<Seg>,
)

@Serializable
data class PhotoEntry(
    val id: String,
    val epochDay: Long,
    val measures: Map<Measure, Double>,
    val geometry: Geometry,
    val warnings: List<String> = emptyList(),
)

@Serializable
data class Profile(
    val name: String = "",
    val sex: Sex = Sex.UOMO,
    val level: Level = Level.PRINCIPIANTE,
    val equipment: Equipment = Equipment.CASA,
    val characterId: String = "",
    val startEpochDay: Long = 0,
    val reminderHour: Int = 18,
    val reminderMinute: Int = 0,
)

/** Avanzamento di un giorno: serie fatte per ogni esercizio (per indice) e giorno chiuso. */
@Serializable
data class DayLog(
    val setsDone: Map<Int, Int> = emptyMap(),
    val completed: Boolean = false,
)

@Serializable
data class AppState(
    val profile: Profile? = null,
    val photos: List<PhotoEntry> = emptyList(),
    val log: Map<Long, DayLog> = emptyMap(),
    /** Giorno in cui l'utente ha premuto "più tardi" sull'avviso della foto. */
    val photoPromptSnoozedDay: Long = -1,
) {
    val onboarded: Boolean get() = profile != null && profile.characterId.isNotEmpty()
    /** Le foto sono tenute in ordine cronologico: la prima è il punto di partenza. */
    val baseline: PhotoEntry? get() = photos.firstOrNull()
    val latest: PhotoEntry? get() = photos.lastOrNull()
}
