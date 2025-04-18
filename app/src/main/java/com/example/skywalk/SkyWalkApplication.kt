package com.example.skywalk

import android.app.Application
import com.example.skywalk.core.firebase.FirebaseConfig
import com.google.android.filament.BuildConfig
import timber.log.Timber

class SkyWalkApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        // Initialize Firebase
        FirebaseConfig.initialize(this)

        // Initialize Timber for logging
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }
    }
}