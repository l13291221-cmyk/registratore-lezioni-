package com.registratorelezioni

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.MediaRecorder
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import java.io.File

/**
 * Servizio in primo piano che registra l'audio.
 * Gira anche a schermo spento grazie al foreground service + wakelock.
 */
class RecordingService : Service() {

    private var recorder: MediaRecorder? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var fileCorrente: File? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_AVVIA -> avviaRegistrazione(intent.getStringExtra(EXTRA_MATERIA) ?: "Lezione")
            ACTION_FERMA -> { fermaRegistrazione(); stopSelf() }
            else -> stopSelf()
        }
        return START_STICKY
    }

    private fun avviaRegistrazione(materia: String) {
        if (registrazioneInCorso) return
        creaCanale()
        avviaForeground(materia)

        val file = FileNaming.creaFile(this, materia)
        fileCorrente = file
        try {
            val r = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) MediaRecorder(this)
            else @Suppress("DEPRECATION") MediaRecorder()
            r.setAudioSource(MediaRecorder.AudioSource.MIC)
            r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            r.setAudioEncodingBitRate(64000)
            r.setAudioSamplingRate(44100)
            r.setOutputFile(file.absolutePath)
            r.prepare()
            r.start()
            recorder = r
            registrazioneInCorso = true
            materiaInCorso = materia
            acquisisciWakeLock()
        } catch (e: Exception) {
            fermaRegistrazione()
            stopSelf()
        }
    }

    private fun fermaRegistrazione() {
        if (registrazioneInCorso) {
            try {
                recorder?.stop()
            } catch (e: Exception) {
                // registrazione troppo corta o interrotta: elimina il file incompleto
                fileCorrente?.delete()
            } finally {
                try { recorder?.release() } catch (_: Exception) { }
            }
        }
        recorder = null
        registrazioneInCorso = false
        materiaInCorso = null
        rilasciaWakeLock()
        fermaForeground()
    }

    private fun avviaForeground(materia: String) {
        val n: Notification = NotificationCompat.Builder(this, CANALE)
            .setContentTitle("Registrazione lezione")
            .setContentText(materia)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        } else {
            startForeground(NOTIF_ID, n)
        }
    }

    private fun fermaForeground() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION") stopForeground(true)
        }
    }

    private fun creaCanale() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (nm.getNotificationChannel(CANALE) == null) {
                val ch = NotificationChannel(CANALE, "Registrazione", NotificationManager.IMPORTANCE_LOW)
                ch.setSound(null, null)
                nm.createNotificationChannel(ch)
            }
        }
    }

    private fun acquisisciWakeLock() {
        try {
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            val wl = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "reglez:registrazione")
            wl.setReferenceCounted(false)
            wl.acquire(6 * 60 * 60 * 1000L) // limite di sicurezza: 6 ore
            wakeLock = wl
        } catch (_: Exception) { }
    }

    private fun rilasciaWakeLock() {
        try { if (wakeLock?.isHeld == true) wakeLock?.release() } catch (_: Exception) { }
        wakeLock = null
    }

    override fun onDestroy() {
        fermaRegistrazione()
        super.onDestroy()
    }

    companion object {
        const val ACTION_AVVIA = "com.registratorelezioni.AVVIA"
        const val ACTION_FERMA = "com.registratorelezioni.FERMA"
        const val EXTRA_MATERIA = "materia"
        private const val CANALE = "registrazione"
        private const val NOTIF_ID = 1

        @Volatile var registrazioneInCorso = false
        @Volatile var materiaInCorso: String? = null

        fun avvia(context: Context, materia: String) {
            val i = Intent(context, RecordingService::class.java)
                .setAction(ACTION_AVVIA)
                .putExtra(EXTRA_MATERIA, materia)
            ContextCompat.startForegroundService(context, i)
        }

        fun ferma(context: Context) {
            val i = Intent(context, RecordingService::class.java).setAction(ACTION_FERMA)
            ContextCompat.startForegroundService(context, i)
        }
    }
}
