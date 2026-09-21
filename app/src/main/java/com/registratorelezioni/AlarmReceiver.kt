package com.registratorelezioni

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Riceve le sveglie di avvio/stop e agisce, poi riprogramma la prossima. */
class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Scheduler.ACTION_START -> {
                // riprogramma() rileva la lezione in corso, avvia e programma lo stop
                Scheduler.riprogramma(context)
            }
            Scheduler.ACTION_STOP -> {
                RecordingService.ferma(context)
                Scheduler.riprogramma(context)
            }
        }
    }
}
