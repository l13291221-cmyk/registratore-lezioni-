package com.animefit.notify

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.animefit.AnimeFitApp
import com.animefit.MainActivity
import com.animefit.R
import com.animefit.data.today
import com.animefit.training.Coach
import java.time.Duration
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit

object Reminders {
    private const val CHANNEL = "promemoria"
    private const val WORK = "promemoria-giornaliero"
    const val EXTRA_OPEN = "open"
    const val OPEN_CAMERA = "camera"
    const val OPEN_WORKOUT = "workout"

    fun createChannel(context: Context) {
        val channel = NotificationChannel(CHANNEL, "Promemoria", NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = "Allenamento del giorno e foto settimanale"
        }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    /** Un controllo al giorno all'ora scelta: avvisa se manca l'allenamento o la foto. */
    fun schedule(context: Context, hour: Int, minute: Int, replace: Boolean) {
        val now = LocalDateTime.now()
        var next = now.withHour(hour).withMinute(minute).withSecond(0).withNano(0)
        if (!next.isAfter(now)) next = next.plusDays(1)
        val delay = Duration.between(now, next).toMinutes()
        val request = PeriodicWorkRequestBuilder<ReminderWorker>(1, TimeUnit.DAYS)
            .setInitialDelay(delay, TimeUnit.MINUTES)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK,
            if (replace) ExistingPeriodicWorkPolicy.CANCEL_AND_REENQUEUE else ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }

    fun notify(context: Context, id: Int, title: String, text: String, open: String) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) return
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_OPEN, open)
        }
        val pending = PendingIntent.getActivity(
            context, id, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val n = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(0xFF00F5FF.toInt())
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(pending)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(id, n)
    }
}

class ReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val state = (applicationContext as AnimeFitApp).repository.state.value
        if (!state.onboarded) return Result.success()
        val day = today()
        val name = Coach.character(state)?.name ?: "il tuo personaggio"
        if (Coach.photoDue(state, day)) {
            Reminders.notify(
                applicationContext, 2, "📸 Foto settimanale",
                "È passata una settimana: scatta la foto per vedere i tuoi progressi verso $name.",
                Reminders.OPEN_CAMERA,
            )
        }
        val w = Coach.workout(state, day)
        if (w != null && state.log[day]?.completed != true) {
            Reminders.notify(
                applicationContext, 1, "Allenamento di oggi",
                "${w.dayType.label}: ${w.exercises.size} esercizi, circa ${w.estimatedMinutes} minuti.",
                Reminders.OPEN_WORKOUT,
            )
        }
        return Result.success()
    }
}
