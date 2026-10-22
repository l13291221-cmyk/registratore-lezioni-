package com.animefit.catalog

import com.animefit.data.Measure
import com.animefit.data.Sex

/**
 * Le zone del fisico valutate confrontando la foto con il personaggio.
 * Ogni zona dice quali muscoli allenare per migliorarla.
 */
enum class Area(
    val label: String,
    val muscles: List<Muscle>,
    val weight: Double,
    val why: String,
) {
    SPALLE("Spalle", listOf(Muscle.SPALLE, Muscle.DORSO), 1.2,
        "Spalle larghe (deltoidi) sono la base del fisico da eroe e della forma a V."),
    TORACE("Petto e dorsali", listOf(Muscle.PETTO, Muscle.DORSO), 1.0,
        "Un torace ampio con dorsali aperti fa sembrare il busto più grande e potente."),
    BRACCIA("Braccia", listOf(Muscle.BICIPITI, Muscle.TRICIPITI), 1.0,
        "Lo spessore del braccio è fatto per 2/3 dal tricipite e per 1/3 dal bicipite."),
    AVAMBRACCI("Avambracci", listOf(Muscle.AVAMBRACCI), 0.5,
        "Avambracci forti danno presa e rendono il braccio proporzionato."),
    VITA("Vita stretta", listOf(Muscle.CARDIO, Muscle.CORE), 1.1,
        "La vita si stringe riducendo il grasso: cardio costante, addominali e alimentazione sana."),
    FORMA_V("Forma a V", listOf(Muscle.DORSO, Muscle.SPALLE), 1.3,
        "Il rapporto spalle/vita è ciò che rende riconoscibile un fisico da anime."),
    ADDOMINALI("Addominali", listOf(Muscle.CORE, Muscle.CARDIO), 1.0,
        "Addominali visibili = muscolo allenato + poco grasso sopra."),
    GAMBE("Cosce", listOf(Muscle.GAMBE), 0.9,
        "Gambe forti reggono tutto il fisico e bruciano tante calorie."),
    POLPACCI("Polpacci", listOf(Muscle.POLPACCI), 0.5,
        "Polpacci pieni completano la linea delle gambe."),
}

/**
 * Valori "di partenza" (punteggio 0%) per ogni misura: un fisico esile e non
 * allenato. Il punteggio di una zona va da qui (0%) al valore del personaggio (100%).
 */
object Floors {
    private val male = mapOf(
        Measure.SPALLE to 0.85, Measure.TORACE to 0.60, Measure.VITA to 0.76,
        Measure.BRACCIO to 0.155, Measure.AVAMBRACCIO to 0.135,
        Measure.COSCIA to 0.29, Measure.POLPACCIO to 0.19, Measure.DEFINIZIONE to 0.0,
    )
    private val female = mapOf(
        Measure.SPALLE to 0.76, Measure.TORACE to 0.55, Measure.VITA to 0.70,
        Measure.BRACCIO to 0.145, Measure.AVAMBRACCIO to 0.125,
        Measure.COSCIA to 0.30, Measure.POLPACCIO to 0.185, Measure.DEFINIZIONE to 0.0,
    )

    fun of(sex: Sex, m: Measure): Double = (if (sex == Sex.UOMO) male else female)[m] ?: 0.0

    fun vTaper(sex: Sex): Double = if (sex == Sex.UOMO) 1.20 else 1.10

    /**
     * Vita di riferimento: oltre questa larghezza le misure di braccia e gambe
     * vengono ridotte, perché il grasso le gonfia senza che siano muscolo.
     */
    fun refWaist(sex: Sex): Double = if (sex == Sex.UOMO) 0.62 else 0.56

    /** Proporzioni medie, usate per disegnare la sagoma guida della fotocamera. */
    fun average(sex: Sex): Map<Measure, Double> = if (sex == Sex.UOMO) mapOf(
        Measure.SPALLE to 0.94, Measure.TORACE to 0.69, Measure.VITA to 0.63,
        Measure.FIANCHI to 0.70, Measure.BRACCIO to 0.20, Measure.AVAMBRACCIO to 0.17,
        Measure.COSCIA to 0.35, Measure.POLPACCIO to 0.23, Measure.DEFINIZIONE to 0.2,
    ) else mapOf(
        Measure.SPALLE to 0.84, Measure.TORACE to 0.62, Measure.VITA to 0.57,
        Measure.FIANCHI to 0.76, Measure.BRACCIO to 0.18, Measure.AVAMBRACCIO to 0.15,
        Measure.COSCIA to 0.36, Measure.POLPACCIO to 0.22, Measure.DEFINIZIONE to 0.15,
    )
}
