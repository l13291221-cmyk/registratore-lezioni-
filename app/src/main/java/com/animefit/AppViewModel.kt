package com.animefit

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.animefit.analysis.AnalysisResult
import com.animefit.analysis.BodyAnalyzer
import com.animefit.data.AppState
import com.animefit.data.DayLog
import com.animefit.data.PhotoEntry
import com.animefit.data.Profile
import com.animefit.data.today
import com.animefit.notify.Reminders
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

sealed class AnalysisUi {
    data object Idle : AnalysisUi()
    data object Running : AnalysisUi()
    data class Failed(val message: String) : AnalysisUi()
    data class Done(val photoId: String) : AnalysisUi()
}

class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = (app as AnimeFitApp).repository
    val state: StateFlow<AppState> = repo.state

    private val _analysis = MutableStateFlow<AnalysisUi>(AnalysisUi.Idle)
    val analysis: StateFlow<AnalysisUi> = _analysis.asStateFlow()

    /** Cambia ogni volta che un'immagine di riferimento viene aggiunta o tolta. */
    private val _refVersion = MutableStateFlow(0)
    val refVersion: StateFlow<Int> = _refVersion.asStateFlow()

    fun photoFile(id: String): File = repo.photoFile(id)
    fun maskFile(id: String): File = repo.maskFile(id)
    fun referenceFile(characterId: String): File = repo.referenceFile(characterId)

    fun saveProfile(profile: Profile) {
        val old = state.value.profile
        repo.update { it.copy(profile = profile) }
        if (old == null || old.reminderHour != profile.reminderHour || old.reminderMinute != profile.reminderMinute) {
            Reminders.schedule(getApplication(), profile.reminderHour, profile.reminderMinute, replace = true)
        }
    }

    fun chooseCharacter(id: String) {
        repo.update { s ->
            val p = s.profile ?: Profile()
            val start = if (p.startEpochDay == 0L) today() else p.startEpochDay
            s.copy(profile = p.copy(characterId = id, startEpochDay = start))
        }
    }

    fun setSetsDone(day: Long, index: Int, done: Int) {
        repo.update { s ->
            val log = s.log[day] ?: DayLog()
            s.copy(log = s.log + (day to log.copy(setsDone = log.setsDone + (index to done))))
        }
    }

    fun setDayCompleted(day: Long, completed: Boolean) {
        repo.update { s ->
            val log = s.log[day] ?: DayLog()
            s.copy(log = s.log + (day to log.copy(completed = completed)))
        }
    }

    fun snoozePhotoPrompt() {
        repo.update { it.copy(photoPromptSnoozedDay = today()) }
    }

    fun analyze(source: Bitmap) {
        if (_analysis.value == AnalysisUi.Running) return
        _analysis.value = AnalysisUi.Running
        viewModelScope.launch {
            val bitmap = withContext(Dispatchers.Default) { ImageUtils.scaleDown(source, 1280) }
            when (val r = BodyAnalyzer.analyze(bitmap)) {
                is AnalysisResult.Failure -> _analysis.value = AnalysisUi.Failed(r.message)
                is AnalysisResult.Success -> {
                    val id = "${System.currentTimeMillis()}-${UUID.randomUUID().toString().take(6)}"
                    withContext(Dispatchers.IO) {
                        ImageUtils.saveJpeg(bitmap, repo.photoFile(id))
                        ImageUtils.savePng(r.mask, repo.maskFile(id))
                    }
                    val entry = PhotoEntry(id, today(), r.measures, r.geometry, r.warnings)
                    // sortedBy è stabile: foto dello stesso giorno restano in ordine di scatto.
                    repo.update { s -> s.copy(photos = (s.photos + entry).sortedBy { it.epochDay }) }
                    _analysis.value = AnalysisUi.Done(id)
                }
            }
        }
    }

    fun analyzeUri(uri: Uri) {
        viewModelScope.launch {
            val bmp = withContext(Dispatchers.IO) { ImageUtils.load(getApplication(), uri, 1600) }
            if (bmp == null) _analysis.value = AnalysisUi.Failed("Non riesco ad aprire questa immagine.")
            else analyze(bmp)
        }
    }

    fun resetAnalysis() {
        _analysis.value = AnalysisUi.Idle
    }

    fun deletePhoto(id: String) {
        repo.deletePhotoFiles(id)
        repo.update { s -> s.copy(photos = s.photos.filterNot { it.id == id }) }
    }

    fun setReference(characterId: String, uri: Uri) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                ImageUtils.load(getApplication(), uri, 1280)?.let {
                    ImageUtils.saveJpeg(it, repo.referenceFile(characterId))
                }
            }
            _refVersion.value++
        }
    }

    fun removeReference(characterId: String) {
        repo.referenceFile(characterId).delete()
        _refVersion.value++
    }

    fun wipeAll() {
        repo.wipeAll()
        _refVersion.value++
    }
}
