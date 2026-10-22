package com.animefit

import com.animefit.analysis.Scoring
import com.animefit.catalog.Area
import com.animefit.catalog.Characters
import com.animefit.catalog.DayType
import com.animefit.catalog.Floors
import com.animefit.catalog.allows
import com.animefit.data.AppState
import com.animefit.data.DayLog
import com.animefit.data.Equipment
import com.animefit.data.Geometry
import com.animefit.data.Level
import com.animefit.data.Measure
import com.animefit.data.PhotoEntry
import com.animefit.data.Profile
import com.animefit.data.Pt
import com.animefit.data.Sex
import com.animefit.training.Coach
import com.animefit.training.Tag
import com.animefit.training.WorkoutGenerator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class LogicTest {
    private val monday = LocalDate.of(2026, 10, 19).toEpochDay()

    private fun profile(level: Level = Level.INTERMEDIO, equipment: Equipment = Equipment.CASA, id: String = "goku") =
        Profile("Test", Sex.UOMO, level, equipment, id, monday)

    private fun photo(day: Long, measures: Map<Measure, Double>) = PhotoEntry(
        "p$day", day, measures,
        Geometry(100, 100, 25, 25, 50f, Pt(50f, 30f), Pt(50f, 80f), emptyMap(), emptyList()),
    )

    @Test
    fun targetPhysiqueScoresHundredPercent() {
        for (c in Characters.all) {
            val a = Scoring.assess(c.targets, c)
            assertEquals("${c.name}", 1.0, a.overall, 0.001)
            assertTrue(a.weakest.isEmpty())
        }
    }

    @Test
    fun averageBodyIsInTheMiddle() {
        val avg = Floors.average(Sex.UOMO)
        val saitama = Scoring.assess(avg, Characters.get("saitama")!!)
        val goku = Scoring.assess(avg, Characters.get("goku")!!)
        assertTrue("saitama ${saitama.overall}", saitama.overall in 0.3..0.75)
        assertTrue("goku ${goku.overall}", goku.overall in 0.2..0.6)
        assertTrue("Goku è più lontano di Saitama", goku.overall < saitama.overall)
    }

    @Test
    fun fatDoesNotCountAsMuscle() {
        val c = Characters.get("goku")!!
        val lean = Floors.average(Sex.UOMO)
        // Stesse braccia ma vita molto più larga: il punteggio delle braccia deve scendere.
        val heavy = lean + (Measure.VITA to 0.78)
        val armLean = Scoring.assess(lean, c).areas.first { it.area == Area.BRACCIA }.score
        val armHeavy = Scoring.assess(heavy, c).areas.first { it.area == Area.BRACCIA }.score
        assertTrue(armHeavy < armLean)
    }

    @Test
    fun weakestAreasPointToTheGap() {
        val c = Characters.get("goku")!!
        val m = c.targets + (Measure.SPALLE to 0.90) + (Measure.BRACCIO to 0.18)
        val weak = Scoring.assess(m, c).weakest.map { it.area }
        assertTrue(Area.SPALLE in weak)
        assertTrue(Area.BRACCIA in weak)
    }

    @Test
    fun workoutIsDeterministic() {
        val c = Characters.get("goku")!!
        val a = WorkoutGenerator.generate(profile(), c, monday + 2, emptyList())
        val b = WorkoutGenerator.generate(profile(), c, monday + 2, emptyList())
        assertEquals(a, b)
    }

    @Test
    fun everyCharacterHasAFullWeekForEverySetup() {
        for (c in Characters.all) for (level in Level.entries) for (eq in Equipment.entries) {
            for (d in 0 until 28) {
                val p = profile(level, eq, c.id)
                val w = WorkoutGenerator.generate(p, c, monday + d, listOf(Area.SPALLE, Area.VITA))
                assertTrue("${c.id} $level $eq giorno $d vuoto", w.exercises.isNotEmpty())
                for (e in w.exercises) {
                    assertTrue("${e.exercise.id} non adatto a $eq", eq.allows(e.exercise.kit))
                    if (e.tag != Tag.FIRMA) {
                        assertTrue("${e.exercise.id} troppo difficile per $level", e.exercise.difficulty <= level.maxDifficulty)
                    }
                    assertTrue(e.sets >= 1 && e.amount > 0)
                }
                if (w.dayType != DayType.RIPOSO) {
                    val needed = w.dayType.slots.sumOf { it.second }
                    val regular = w.exercises.count { it.tag != Tag.FIRMA }
                    if (c.signature.replacesWorkout && (d % 7) in c.signature.days) continue
                    assertTrue("${c.id} $level $eq giorno $d: $regular < $needed", regular >= needed)
                }
            }
        }
    }

    @Test
    fun weakPointsAddTaggedExercises() {
        val c = Characters.get("goku")!!
        val w = WorkoutGenerator.generate(profile(), c, monday, listOf(Area.SPALLE, Area.VITA))
        assertTrue(w.exercises.any { it.tag == Tag.PUNTO_DEBOLE })
        val without = WorkoutGenerator.generate(profile(), c, monday, emptyList())
        assertTrue(w.exercises.size > without.exercises.size)
    }

    @Test
    fun saitamaDoesHisRoutine() {
        val c = Characters.get("saitama")!!
        val beginner = WorkoutGenerator.generate(profile(Level.PRINCIPIANTE, id = "saitama"), c, monday, emptyList())
        assertTrue(beginner.exercises.all { it.tag == Tag.FIRMA })
        assertEquals(30, beginner.exercises.first { it.exercise.id == "piegamenti" }.amount)
        // Dopo 10 settimane si arriva a 100 (il massimo).
        val later = WorkoutGenerator.generate(profile(Level.PRINCIPIANTE, id = "saitama"), c, monday + 70, emptyList())
        assertEquals(100, later.exercises.first { it.exercise.id == "piegamenti" }.amount)
        assertEquals(10, later.exercises.first { it.exercise.id == "corsa_km" }.amount)
        val sunday = WorkoutGenerator.generate(profile(id = "saitama"), c, monday + 6, emptyList())
        assertTrue(sunday.isRest)
    }

    @Test
    fun deloadEveryFourthWeek() {
        val c = Characters.get("goku")!!
        val normal = WorkoutGenerator.generate(profile(), c, monday, emptyList())
        val deload = WorkoutGenerator.generate(profile(), c, monday + 21, emptyList())
        assertFalse(normal.deload)
        assertTrue(deload.deload)
        assertTrue(deload.exercises.sumOf { it.sets } < normal.exercises.sumOf { it.sets })
    }

    @Test
    fun photoIsDueEverySevenDays() {
        val base = AppState(profile = profile())
        assertTrue(Coach.photoDue(base, monday))
        val withPhoto = base.copy(photos = listOf(photo(monday, Floors.average(Sex.UOMO))))
        assertFalse(Coach.photoDue(withPhoto, monday + 6))
        assertEquals(1, Coach.daysToNextPhoto(withPhoto, monday + 6))
        assertTrue(Coach.photoDue(withPhoto, monday + 7))
    }

    @Test
    fun streakCountsConsecutiveDays() {
        val done = DayLog(completed = true)
        val s = AppState(profile = profile(), log = mapOf(monday to done, monday + 1 to done, monday + 2 to done))
        assertEquals(3, Coach.streak(s, monday + 2))
        assertEquals(3, Coach.streak(s, monday + 3)) // oggi non ancora fatto: la serie resta viva
        assertEquals(0, Coach.streak(s, monday + 5))
    }

    @Test
    fun newPhotoDoesNotChangeTodaysWorkout() {
        val p = profile()
        val first = photo(monday, Floors.average(Sex.UOMO))
        val s1 = AppState(profile = p, photos = listOf(first))
        val today = monday + 7
        val before = Coach.workout(s1, today)
        val newPhoto = photo(today, Characters.get("goku")!!.targets)
        val after = Coach.workout(s1.copy(photos = listOf(first, newPhoto)), today)
        assertNotNull(before)
        assertEquals(before, after)
    }
}
