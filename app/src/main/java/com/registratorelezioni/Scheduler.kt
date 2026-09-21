package com.registratorelezioni

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import java.util.Calendar

/**
 * Programma l'avvio e lo stop della registrazione in base all'orario.
 * Strategia: teniamo al massimo una sveglia in sospeso.
 *  - Se ORA siamo dentro una lezione -> avvia la registrazione e programma lo STOP a fine ora.
 *  - Altrimenti -> programma lo START della prossima lezione.
 * Dopo ogni sveglia (o al riavvio, o all'apertura dell'app) si richiama riprogramma().
 */
object Scheduler {

    const val ACTION_START = "com.registratorelezioni.alarm.START"
    const val ACTION_STOP = "com.registratorelezioni.alarm.STOP"
    private const val RC_START = 1001
    private const val RC_STOP = 1002

    fun riprogramma(context: Context) {
        val lezioni = Timetable.carica(context)
        val now = Calendar.getInstance()

        val corrente = lezioneCorrente(lezioni, now)
        if (corrente != null) {
            RecordingService.avvia(context, corrente.materia)
            val stop = prossimaOccorrenza(now, corrente.giorno, corrente.fineMin)
            if (stop > 0) impostaAllarme(context, ACTION_STOP, RC_STOP, stop)
        } else {
            val prossimo = prossimoAvvio(lezioni, now)
            if (prossimo != null) impostaAllarme(context, ACTION_START, RC_START, prossimo)
        }
    }

    private fun lezioneCorrente(lezioni: List<Lezione>, now: Calendar): Lezione? {
        val g = now.get(Calendar.DAY_OF_WEEK)
        val min = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)
        return lezioni.firstOrNull { it.giorno == g && min >= it.inizioMin && min < it.fineMin }
    }

    private fun prossimoAvvio(lezioni: List<Lezione>, now: Calendar): Long? {
        var best = Long.MAX_VALUE
        for (l in lezioni) {
            val t = prossimaOccorrenza(now, l.giorno, l.inizioMin)
            if (t in 1 until best) best = t
        }
        return if (best == Long.MAX_VALUE) null else best
    }

    /** Prossimo istante (in millis) del dato giorno-della-settimana e minuto-del-giorno, nel futuro. */
    private fun prossimaOccorrenza(base: Calendar, giorno: Int, minuti: Int): Long {
        for (add in 0..7) {
            val c = base.clone() as Calendar
            c.set(Calendar.SECOND, 0)
            c.set(Calendar.MILLISECOND, 0)
            c.set(Calendar.HOUR_OF_DAY, 0)
            c.set(Calendar.MINUTE, 0)
            c.add(Calendar.DAY_OF_MONTH, add)
            c.add(Calendar.MINUTE, minuti)
            if (c.get(Calendar.DAY_OF_WEEK) == giorno && c.timeInMillis > base.timeInMillis) {
                return c.timeInMillis
            }
        }
        return -1
    }

    private fun impostaAllarme(context: Context, azione: String, requestCode: Int, quando: Long) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pi = PendingIntent.getBroadcast(
            context, requestCode,
            Intent(context, AlarmReceiver::class.java).setAction(azione),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val puoEsatte =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) am.canScheduleExactAlarms() else true
        try {
            if (puoEsatte) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, quando, pi)
            else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, quando, pi)
        } catch (_: SecurityException) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, quando, pi)
        }
    }
}
