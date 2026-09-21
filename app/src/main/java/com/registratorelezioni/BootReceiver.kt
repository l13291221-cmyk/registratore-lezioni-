package com.registratorelezioni

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Dopo il riavvio del telefono, riprogramma le sveglie dell'orario. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val a = intent.action
        if (a == Intent.ACTION_BOOT_COMPLETED || a == Intent.ACTION_LOCKED_BOOT_COMPLETED) {
            Scheduler.riprogramma(context)
        }
    }
}
