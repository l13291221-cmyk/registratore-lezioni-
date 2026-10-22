package com.animefit.training

import com.animefit.catalog.Area
import com.animefit.catalog.Character
import com.animefit.catalog.DayType
import com.animefit.catalog.Exercise
import com.animefit.catalog.Exercises
import com.animefit.catalog.Kit
import com.animefit.catalog.Muscle
import com.animefit.catalog.Unit
import com.animefit.catalog.allows
import com.animefit.data.Equipment
import com.animefit.data.Level
import com.animefit.data.Profile
import java.time.LocalDate
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.random.Random

enum class Tag { NORMALE, PUNTO_DEBOLE, FIRMA }

data class PlannedExercise(
    val exercise: Exercise,
    val sets: Int,
    /** Ripetizioni, secondi, minuti o km a seconda di exercise.unit. */
    val amount: Int,
    val restSeconds: Int,
    val tag: Tag,
) {
    val doseText: String
        get() = when (exercise.unit) {
            Unit.MINUTI -> "$amount minuti"
            Unit.KM -> "$amount km"
            Unit.SECONDI -> if (sets > 1) "$sets × $amount sec" else "$amount sec"
            Unit.REPS -> if (sets > 1) "$sets × $amount" else "$amount ripetizioni"
        }
}

data class Workout(
    val epochDay: Long,
    val dayType: DayType,
    /** Settimana di allenamento, da 1. */
    val week: Int,
    val deload: Boolean,
    val exercises: List<PlannedExercise>,
    val signatureTitle: String?,
    val signatureNote: String?,
    val weakAreas: List<Area>,
) {
    val isRest: Boolean get() = dayType == DayType.RIPOSO

    /** true se oggi ci sono esercizi extra per i punti deboli. */
    val hasWeakExtras: Boolean get() = exercises.any { it.tag == Tag.PUNTO_DEBOLE }

    val estimatedMinutes: Int
        get() = max(10, exercises.sumOf { p ->
            when (p.exercise.unit) {
                Unit.REPS -> p.sets * (p.amount * 3 + p.restSeconds)
                Unit.SECONDI -> p.sets * (p.amount + p.restSeconds)
                Unit.MINUTI -> p.amount * 60
                Unit.KM -> p.amount * 6 * 60
            }
        } / 60)
}

/**
 * Crea l'allenamento del giorno. È deterministico: lo stesso giorno dà sempre
 * lo stesso allenamento, così riaprendo l'app non cambia.
 */
object WorkoutGenerator {

    private val ONLY_SPECIAL = setOf("corsa_km", "calci_frontali")
    private val SMALL_MUSCLES = setOf(Muscle.CORE, Muscle.CARDIO, Muscle.AVAMBRACCI, Muscle.POLPACCI)

    fun dayOfWeek(epochDay: Long): Int = LocalDate.ofEpochDay(epochDay).dayOfWeek.value - 1

    fun weekIndex(profile: Profile, epochDay: Long): Int =
        ((epochDay - profile.startEpochDay) / 7).toInt().coerceAtLeast(0)

    fun generate(profile: Profile, character: Character, epochDay: Long, weakAreas: List<Area>): Workout {
        val dow = dayOfWeek(epochDay)
        val week = weekIndex(profile, epochDay)
        val deload = week % 4 == 3
        val style = character.style
        val dayType = style.week[dow]
        val random = Random(epochDay * 1_000_003L + character.id.hashCode())
        val weak = weakAreas.take(2)

        val planned = mutableListOf<PlannedExercise>()
        val used = mutableSetOf<String>()

        if (dayType == DayType.RIPOSO) {
            for (id in listOf("stretching", if (dow % 2 == 0) "mobilita" else "saluto_sole", "camminata_veloce")) {
                val ex = Exercises.get(id)
                planned += PlannedExercise(ex, 1, minutes(ex, profile.level, week), 0, Tag.NORMALE)
            }
            return Workout(epochDay, dayType, week + 1, deload, planned, null, null, weak)
        }

        val sig = character.signature.takeIf { dow in it.days }

        // Quanti esercizi per muscolo, più quelli extra per i punti deboli.
        val slots = linkedMapOf<Muscle, Int>()
        val extra = mutableMapOf<Muscle, Int>()
        if (sig?.replacesWorkout != true) dayType.slots.forEach { (m, n) -> slots[m] = n }
        for (area in weak) {
            val present = area.muscles.firstOrNull { it in slots }
            val muscle = present ?: area.muscles.firstOrNull { it in SMALL_MUSCLES } ?: continue
            slots[muscle] = (slots[muscle] ?: 0) + 1
            extra[muscle] = (extra[muscle] ?: 0) + 1
        }

        for ((muscle, count) in slots) {
            val weakCount = extra[muscle] ?: 0
            repeat(count) { i ->
                val ex = pick(muscle, profile, used, random) ?: return@repeat
                used += ex.id
                val tag = if (i >= count - weakCount) Tag.PUNTO_DEBOLE else Tag.NORMALE
                planned += dose(ex, character, profile.level, week, deload, isMain = i == 0, tag = tag)
            }
        }

        if (sig != null) {
            for (item in sig.items) {
                val ex = Exercises.get(item.exerciseId)
                val base = item.amount[profile.level.ordinal]
                val amount = minOf(item.cap, base + week * item.weeklyInc)
                val sets = if (deload) max(1, item.sets - 1) else item.sets
                val rest = if (ex.unit == Unit.KM) 0 else 60
                planned += PlannedExercise(ex, sets, amount, rest, Tag.FIRMA)
            }
        }

        return Workout(
            epochDay, dayType, week + 1, deload, planned,
            sig?.title, sig?.note, weak,
        )
    }

    private fun pick(muscle: Muscle, profile: Profile, used: Set<String>, random: Random): Exercise? {
        val candidates = Exercises.all.filter {
            it.muscle == muscle && it.id !in ONLY_SPECIAL && it.id !in used &&
                profile.equipment.allows(it.kit) && it.difficulty <= profile.level.maxDifficulty
        }
        if (candidates.isEmpty()) return null
        val weights = candidates.map { ex ->
            var w = 1.0
            if (ex.difficulty == profile.level.maxDifficulty) w *= 2.0
            if (profile.equipment == Equipment.PALESTRA && ex.kit == Kit.PALESTRA) w *= 3.0
            if (profile.equipment == Equipment.CASA_SBARRA && ex.kit == Kit.SBARRA) w *= 2.0
            w
        }
        var r = random.nextDouble() * weights.sum()
        for ((i, ex) in candidates.withIndex()) {
            r -= weights[i]
            if (r <= 0) return ex
        }
        return candidates.last()
    }

    private fun minutes(ex: Exercise, level: Level, week: Int): Int {
        val base = if (ex.muscle == Muscle.MOBILITA) listOf(8, 10, 12) else listOf(12, 18, 25)
        val grow = if (ex.muscle == Muscle.MOBILITA) 0 else minOf(week, 8)
        return (base[level.ordinal] * ex.scale + grow).roundToInt()
    }

    fun dose(
        ex: Exercise, character: Character, level: Level, week: Int,
        deload: Boolean, isMain: Boolean, tag: Tag,
    ): PlannedExercise {
        val style = character.style
        val progress = minOf(week, 8)
        if (ex.unit == Unit.MINUTI || ex.unit == Unit.KM) {
            return PlannedExercise(ex, 1, minutes(ex, level, week), 0, tag)
        }
        var sets = style.sets
        if (level == Level.PRINCIPIANTE) sets -= 1
        if (level == Level.AVANZATO && isMain) sets += 1
        if (deload) sets -= 1
        sets = sets.coerceAtLeast(2)

        val amount = when (ex.unit) {
            Unit.SECONDI -> {
                val base = listOf(25, 40, 60)[level.ordinal] * ex.scale
                (((base + progress * 5) / 5).roundToInt() * 5).coerceAtLeast(10)
            }
            else -> if (ex.kit == Kit.PALESTRA) {
                (style.gymReps * ex.scale).roundToInt().coerceAtLeast(4)
            } else {
                val base = style.bodyweightReps * listOf(0.7, 1.0, 1.3)[level.ordinal] * ex.scale
                val step = max(1.0, base * 0.08)
                (base + progress * step).roundToInt().coerceAtLeast(3)
            }
        }
        val rest = when (ex.muscle) {
            Muscle.CARDIO -> 30
            Muscle.CORE, Muscle.AVAMBRACCI, Muscle.POLPACCI -> minOf(style.restSeconds, 45)
            else -> style.restSeconds
        }
        return PlannedExercise(ex, sets, amount, rest, tag)
    }
}
