package com.animefit

import android.app.Application
import com.animefit.data.Repository
import com.animefit.notify.Reminders

class AnimeFitApp : Application() {
    lateinit var repository: Repository
        private set

    override fun onCreate() {
        super.onCreate()
        repository = Repository(this)
        Reminders.createChannel(this)
        repository.state.value.profile?.let {
            Reminders.schedule(this, it.reminderHour, it.reminderMinute, replace = false)
        }
    }
}
