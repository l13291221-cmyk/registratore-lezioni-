package com.animefit.training

import com.animefit.analysis.Assessment
import com.animefit.analysis.Scoring
import com.animefit.catalog.Area
import com.animefit.catalog.Character
import com.animefit.catalog.Characters
import com.animefit.data.AppState
import com.animefit.data.PhotoEntry

/** Regole dell'app che uniscono profilo, foto e scheda (senza dipendenze Android). */
object Coach {
    const val PHOTO_EVERY_DAYS = 7

    fun character(state: AppState): Character? = state.profile?.let { Characters.get(it.characterId) }

    fun assessment(state: AppState, photo: PhotoEntry?): Assessment? {
        val c = character(state) ?: return null
        return photo?.let { Scoring.assess(it.measures, c) }
    }

    /**
     * Punti deboli usati per la scheda del giorno: dall'ultima foto scattata
     * prima di quel giorno, così una foto nuova non cambia un allenamento già iniziato.
     * Fa eccezione la foto di partenza, che vale già dal primo giorno.
     */
    fun weakAreasFor(state: AppState, epochDay: Long): List<Area> {
        val photo = state.photos.lastOrNull { it.epochDay < epochDay }
            ?: state.baseline?.takeIf { it.epochDay <= epochDay }
        return assessment(state, photo)?.weakest?.map { it.area } ?: emptyList()
    }

    fun workout(state: AppState, epochDay: Long): Workout? {
        val profile = state.profile ?: return null
        val c = character(state) ?: return null
        return WorkoutGenerator.generate(profile, c, epochDay, weakAreasFor(state, epochDay))
    }

    fun photoDue(state: AppState, today: Long): Boolean {
        val last = state.latest ?: return true
        return today - last.epochDay >= PHOTO_EVERY_DAYS
    }

    fun daysToNextPhoto(state: AppState, today: Long): Int {
        val last = state.latest ?: return 0
        return (PHOTO_EVERY_DAYS - (today - last.epochDay)).toInt().coerceAtLeast(0)
    }

    /** Giorni consecutivi di allenamento completato (oggi conta solo se già fatto). */
    fun streak(state: AppState, today: Long): Int {
        var day = if (state.log[today]?.completed == true) today else today - 1
        var n = 0
        while (state.log[day]?.completed == true) {
            n++
            day--
        }
        return n
    }

    /** Numero della settimana di una foto rispetto alla foto di partenza (0 = partenza). */
    fun photoWeek(state: AppState, photo: PhotoEntry): Int {
        val base = state.baseline ?: return 0
        return ((photo.epochDay - base.epochDay) / 7).toInt()
    }
}
