package com.registratorelezioni

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import java.util.Calendar

/**
 * Servizio in primo piano che tiene il microfono SEMPRE acceso.
 *
 * Perché: da Android 14 un'app a schermo spento non può riaccendere il
 * microfono. Quindi lo apriamo UNA volta (la mattina, con l'app aperta) e non
 * lo chiudiamo più fino a fine giornata. L'audio viene poi diviso in file
 * separati, uno per lezione, seguendo l'orario: al cambio d'ora si chiude il
 * file e se ne apre un altro, ma il microfono resta acceso.
 *
 * Modalità:
 *  - GIORNATA: un file per lezione dell'orario; negli intervalli/fuori orario
 *    l'audio viene scartato; dopo l'ultima lezione del giorno si ferma da solo.
 *  - MANUALE: un unico file "Manuale" finché non premi Ferma.
 */
class RecordingService : Service() {

    private var wakeLock: PowerManager.WakeLock? = null
    private var thread: Thread? = null
    @Volatile private var attivo = false
    private val main = Handler(Looper.getMainLooper())

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_AVVIA_GIORNATA -> avvia(giornata = true)
            ACTION_AVVIA_MANUALE -> avvia(giornata = false)
            ACTION_FERMA -> {
                if (thread != null) attivo = false  // il thread chiude il file e poi ferma il servizio
                else stopSelf()
            }
            // riavvio automatico dopo che il sistema ha chiuso il servizio: senza app
            // aperta Android non ridà il microfono, quindi non ha senso ripartire.
            else -> stopSelf()
        }
        return START_NOT_STICKY
    }

    private fun avvia(giornata: Boolean) {
        if (thread != null) {
            // già in esecuzione: rispetta comunque il contratto di startForegroundService
            avviaForeground(stato)
            return
        }
        creaCanale()
        stato = "Avvio…"
        if (!avviaForeground("Avvio microfono…")) {
            stato = "⚠️ Android non ha concesso il microfono. Apri l'app e riprova."
            stopSelf()
            return
        }
        acquisisciWakeLock()
        attivo = true
        modoGiornata = giornata
        registrazioneInCorso = true
        thread = Thread({ cicloCattura(giornata) }, "cattura-audio").also { it.start() }
    }

    @SuppressLint("MissingPermission") // controllato in MainActivity prima di avviare
    private fun cicloCattura(giornata: Boolean) {
        var lezioni = if (giornata) Timetable.carica(this) else emptyList()
        var versioneOrario = Timetable.versione
        val minBuf = AudioRecord.getMinBufferSize(RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val letto = maxOf(minBuf, RATE / 5 * 2) // ~200 ms per lettura
        var rec: AudioRecord? = null
        var enc: EncoderAac? = null
        var messaggioFine = "Registrazione fermata."
        try {
            rec = AudioRecord(
                MediaRecorder.AudioSource.MIC, RATE,
                AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, letto * 4
            )
            if (rec.state != AudioRecord.STATE_INITIALIZED) {
                messaggioFine = "⚠️ Impossibile aprire il microfono."
                return
            }
            rec.startRecording()
            val buf = ByteArray(letto)
            // "segmento" = la lezione che stiamo scrivendo ora (null = nessun file aperto)
            var segmento: Lezione? = null
            var primo = true

            while (attivo) {
                val n = rec.read(buf, 0, buf.size)
                if (n < 0) { messaggioFine = "⚠️ Microfono interrotto (errore $n)."; break }

                if (giornata && versioneOrario != Timetable.versione) {
                    // orario modificato dall'app mentre la giornata è in corso
                    versioneOrario = Timetable.versione
                    lezioni = Timetable.carica(this)
                    if (segmento == null) primo = true // aggiorna "in attesa di…"
                }

                val nuovo: Lezione? = if (giornata) {
                    Timetable.lezioneCorrente(lezioni, Calendar.getInstance())
                } else MANUALE

                // stessa lezione (anche se hai appena cambiato l'ora di fine): continua lo stesso file
                if (!primo && nuovo != null && nuovo != segmento && stessaLezione(nuovo, segmento)) {
                    segmento = nuovo
                    stato = testoRegistrazione(nuovo, giornata)
                    aggiornaNotifica()
                }

                if (primo || nuovo != segmento) {
                    primo = false
                    enc?.chiudi()
                    enc = null
                    segmento = nuovo
                    if (nuovo != null) enc = apriFile(nuovo.materia)
                    materiaInCorso = nuovo?.materia
                    if (nuovo == null) {
                        val prossima = Timetable.prossimaOggi(lezioni, Calendar.getInstance())
                        if (prossima == null) {
                            messaggioFine = "✅ Giornata finita: nessun'altra lezione oggi."
                            break
                        }
                        stato = "🎙️ Microfono acceso · in attesa di ${prossima.materia} " +
                            "(${Timetable.formattaOra(prossima.inizioMin)})"
                    } else {
                        stato = testoRegistrazione(nuovo, giornata)
                    }
                    aggiornaNotifica()
                }
                if (n > 0) {
                    try {
                        enc?.scrivi(buf, n)
                    } catch (_: Exception) {
                        // problema col file: lo chiudiamo, il microfono resta acceso
                        try { enc?.chiudi() } catch (_: Exception) { }
                        enc = null
                    }
                }
            }
        } catch (e: Exception) {
            messaggioFine = "⚠️ Errore: ${e.message}"
        } finally {
            try { enc?.chiudi() } catch (_: Exception) { }
            try { rec?.stop() } catch (_: Exception) { }
            try { rec?.release() } catch (_: Exception) { }
            stato = messaggioFine
            materiaInCorso = null
            main.post { termina() }
        }
    }

    private fun testoRegistrazione(l: Lezione, giornata: Boolean): String =
        "🔴 STA REGISTRANDO: ${l.materia}" +
            if (giornata) " (fino alle ${Timetable.formattaOra(l.fineMin)})" else ""

    private fun stessaLezione(a: Lezione?, b: Lezione?): Boolean =
        a != null && b != null &&
            a.giorno == b.giorno && a.materia == b.materia && a.inizioMin == b.inizioMin

    private fun apriFile(materia: String): EncoderAac? = try {
        EncoderAac(FileNaming.creaFile(this, materia), RATE, BITRATE)
    } catch (_: Exception) {
        null
    }

    /** Chiamato sul thread principale quando il thread di cattura è finito. */
    private fun termina() {
        thread = null
        attivo = false
        registrazioneInCorso = false
        modoGiornata = false
        rilasciaWakeLock()
        fermaForeground()
        stopSelf()
    }

    private fun costruisciNotifica(testo: String): Notification {
        val apri = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val ferma = PendingIntent.getService(
            this, 1, Intent(this, RecordingService::class.java).setAction(ACTION_FERMA),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CANALE)
            .setContentTitle("Registratore lezioni")
            .setContentText(testo)
            .setStyle(NotificationCompat.BigTextStyle().bigText(testo))
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(apri)
            .addAction(0, "Ferma", ferma)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun avviaForeground(testo: String): Boolean = try {
        val n = costruisciNotifica(testo)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        } else {
            startForeground(NOTIF_ID, n)
        }
        true
    } catch (_: Exception) {
        false
    }

    private fun aggiornaNotifica() {
        try {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.notify(NOTIF_ID, costruisciNotifica(stato))
        } catch (_: Exception) { }
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
            wl.acquire(16 * 60 * 60 * 1000L) // limite di sicurezza: 16 ore
            wakeLock = wl
        } catch (_: Exception) { }
    }

    private fun rilasciaWakeLock() {
        try { if (wakeLock?.isHeld == true) wakeLock?.release() } catch (_: Exception) { }
        wakeLock = null
    }

    override fun onDestroy() {
        // chiusura forzata: ferma la cattura e aspetta che il file venga salvato
        attivo = false
        try { thread?.join(4000) } catch (_: Exception) { }
        main.removeCallbacksAndMessages(null)
        thread = null
        registrazioneInCorso = false
        modoGiornata = false
        materiaInCorso = null
        rilasciaWakeLock()
        super.onDestroy()
    }

    companion object {
        const val ACTION_AVVIA_GIORNATA = "com.registratorelezioni.AVVIA_GIORNATA"
        const val ACTION_AVVIA_MANUALE = "com.registratorelezioni.AVVIA_MANUALE"
        const val ACTION_FERMA = "com.registratorelezioni.FERMA"
        private const val CANALE = "registrazione"
        private const val NOTIF_ID = 1
        private const val RATE = 44100
        private const val BITRATE = 64000
        private val MANUALE = Lezione(0, "Manuale", 0, 0)

        /** true = microfono acceso (in registrazione o in attesa della prossima lezione). */
        @Volatile var registrazioneInCorso = false
        @Volatile var modoGiornata = false
        /** Materia del file che si sta scrivendo ora (null = nessun file aperto). */
        @Volatile var materiaInCorso: String? = null
        /** Ultimo stato leggibile, mostrato in app e in notifica. */
        @Volatile var stato: String = "⚪ Fermo"

        fun avviaGiornata(context: Context) {
            val i = Intent(context, RecordingService::class.java).setAction(ACTION_AVVIA_GIORNATA)
            ContextCompat.startForegroundService(context, i)
        }

        fun avviaManuale(context: Context) {
            val i = Intent(context, RecordingService::class.java).setAction(ACTION_AVVIA_MANUALE)
            ContextCompat.startForegroundService(context, i)
        }

        fun ferma(context: Context) {
            val i = Intent(context, RecordingService::class.java).setAction(ACTION_FERMA)
            try { context.startService(i) } catch (_: Exception) { }
        }
    }
}
