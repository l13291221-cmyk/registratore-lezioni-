package com.animefit.analysis

import com.animefit.catalog.Area
import com.animefit.catalog.Character
import com.animefit.catalog.Floors
import com.animefit.data.Measure
import kotlin.math.roundToInt

data class AreaScore(
    val area: Area,
    /** Valore misurato (già corretto per la massa grassa dove serve). */
    val value: Double,
    val target: Double,
    /** 0..1: quanto sei vicino al personaggio in questa zona. */
    val score: Double,
    val weight: Double,
) {
    val percent: Int get() = (score * 100).roundToInt()

    /** Frase breve su quanto manca, es. "manca circa il 12% di larghezza". */
    fun gapText(): String {
        if (score >= 0.99) return "obiettivo raggiunto"
        return when (area) {
            Area.VITA -> "vita da stringere di circa ${pct(value / target - 1)}%"
            Area.ADDOMINALI -> "servono più definizione e meno grasso sull'addome"
            Area.FORMA_V -> "rapporto spalle/vita da aumentare di circa ${pct(target / value - 1)}%"
            else -> "manca circa il ${pct(target / value - 1)}% di spessore/larghezza"
        }
    }

    private fun pct(x: Double) = (x * 100).roundToInt().coerceAtLeast(1)
}

data class Assessment(
    val overall: Double,
    val areas: List<AreaScore>,
) {
    val overallPercent: Int get() = (overall * 100).roundToInt()

    /** Zone su cui allenarsi di più: più lontane dal target, pesate per importanza. */
    val weakest: List<AreaScore>
        get() = areas.filter { it.score < 0.95 }
            .sortedByDescending { it.weight * (1 - it.score) }
            .take(3)

    val strongest: List<AreaScore>
        get() = areas.filter { it.score >= 0.8 }.sortedByDescending { it.score }.take(3)
}

data class Change(val measure: Measure, val before: Double, val after: Double) {
    val percent: Double get() = if (before == 0.0) 0.0 else (after - before) / before * 100.0

    /** Variazioni piccole rientrano nel margine d'errore della foto. */
    val significant: Boolean
        get() = if (measure == Measure.DEFINIZIONE) kotlin.math.abs(after - before) >= 0.05
        else kotlin.math.abs(percent) >= 1.5

    /** true se la variazione va nella direzione giusta (vita più stretta, il resto più grande). */
    val improved: Boolean
        get() = when (measure) {
            Measure.VITA, Measure.FIANCHI -> after < before
            else -> after > before
        }
}

object Scoring {

    private fun lean(m: Map<Measure, Double>, c: Character): Double {
        val waist = m[Measure.VITA] ?: return 1.0
        return minOf(1.0, Floors.refWaist(c.sex) / waist)
    }

    private fun higher(value: Double, floor: Double, target: Double) =
        ((value - floor) / (target - floor)).coerceIn(0.0, 1.0)

    private fun lower(value: Double, floor: Double, target: Double) =
        ((floor - value) / (floor - target)).coerceIn(0.0, 1.0)

    fun assess(m: Map<Measure, Double>, c: Character): Assessment {
        val k = lean(m, c)
        val sex = c.sex
        val list = mutableListOf<AreaScore>()

        fun add(area: Area, value: Double?, target: Double, score: (Double) -> Double) {
            if (value == null || value <= 0) return
            val w = area.weight * (if (area in c.focus) 1.6 else 1.0)
            list += AreaScore(area, value, target, score(value), w)
        }

        fun sized(area: Area, measure: Measure) {
            val v = m[measure]?.times(k)
            add(area, v, c.target(measure)) { higher(it, Floors.of(sex, measure), c.target(measure)) }
        }

        sized(Area.SPALLE, Measure.SPALLE)
        sized(Area.TORACE, Measure.TORACE)
        sized(Area.BRACCIA, Measure.BRACCIO)
        sized(Area.AVAMBRACCI, Measure.AVAMBRACCIO)

        val waist = m[Measure.VITA]
        val waistScore = waist?.let { lower(it, Floors.of(sex, Measure.VITA), c.target(Measure.VITA)) }
        add(Area.VITA, waist, c.target(Measure.VITA)) { waistScore ?: 0.0 }

        val shoulders = m[Measure.SPALLE]
        if (shoulders != null && waist != null) {
            val vTarget = c.target(Measure.SPALLE) / c.target(Measure.VITA)
            add(Area.FORMA_V, shoulders / waist, vTarget) { higher(it, Floors.vTaper(sex), vTarget) }
        }

        // Addominali: metà definizione (dalla foto), metà vita stretta.
        val def = m[Measure.DEFINIZIONE]
        val defTarget = c.target(Measure.DEFINIZIONE)
        if (waistScore != null) {
            val defScore = def?.let { (it / defTarget).coerceIn(0.0, 1.0) }
            val combined = if (defScore != null) 0.5 * defScore + 0.5 * waistScore else waistScore
            add(Area.ADDOMINALI, def ?: 0.01, defTarget) { combined }
        }

        sized(Area.GAMBE, Measure.COSCIA)
        sized(Area.POLPACCI, Measure.POLPACCIO)

        val totalW = list.sumOf { it.weight }
        val overall = if (totalW > 0) list.sumOf { it.score * it.weight } / totalW else 0.0
        return Assessment(overall, list)
    }

    fun changes(before: Map<Measure, Double>, after: Map<Measure, Double>): List<Change> =
        Measure.entries.mapNotNull { m ->
            val b = before[m]
            val a = after[m]
            if (b != null && a != null) Change(m, b, a) else null
        }
}
