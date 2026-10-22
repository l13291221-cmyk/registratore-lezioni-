package com.animefit.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json
import java.io.File
import java.time.LocalDate

fun today(): Long = LocalDate.now().toEpochDay()

/**
 * Tutti i dati restano nella memoria interna dell'app: profilo, misure,
 * registro allenamenti (state.json) e foto (cartella photos/).
 */
class Repository(context: Context) {
    private val dir = context.filesDir
    private val stateFile = File(dir, "state.json")
    private val photosDir = File(dir, "photos").apply { mkdirs() }
    private val refDir = File(dir, "riferimenti").apply { mkdirs() }

    private val _state = MutableStateFlow(load())
    val state: StateFlow<AppState> = _state.asStateFlow()

    @Synchronized
    fun update(change: (AppState) -> AppState) {
        val next = change(_state.value)
        _state.value = next
        save(next)
    }

    fun photoFile(id: String) = File(photosDir, "$id.jpg")
    fun maskFile(id: String) = File(photosDir, "${id}_mask.png")
    fun referenceFile(characterId: String) = File(refDir, "$characterId.jpg")

    fun deletePhotoFiles(id: String) {
        photoFile(id).delete()
        maskFile(id).delete()
    }

    fun wipeAll() {
        photosDir.listFiles()?.forEach { it.delete() }
        refDir.listFiles()?.forEach { it.delete() }
        update { AppState() }
    }

    private fun load(): AppState = try {
        if (stateFile.exists()) json.decodeFromString(AppState.serializer(), stateFile.readText()) else AppState()
    } catch (e: Exception) {
        AppState()
    }

    private fun save(s: AppState) {
        val tmp = File(dir, "state.json.tmp")
        tmp.writeText(json.encodeToString(AppState.serializer(), s))
        tmp.renameTo(stateFile)
    }

    companion object {
        private val json = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }
    }
}
