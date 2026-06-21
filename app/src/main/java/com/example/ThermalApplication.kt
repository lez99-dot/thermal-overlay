package com.example

import android.app.Application
import android.util.Log
import com.example.thermal.CrashReporter

class ThermalApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        Log.i("ThermalApplication", "Initializing Thermal Overlay App with Crash Log support")
        CrashReporter.initialize(this)
    }
}
