package com.google.mediapipe.examples.production

import android.app.Application
import android.util.Log
import com.google.mediapipe.examples.production.data.local.AppDatabase

class Myapp : Application() {

    private val TAG = "MyApp"

    // Using 'by lazy' for deferred initialization
    private val database: AppDatabase by lazy { AppDatabase.getDatabase(this) }

    override fun onCreate() {
        super.onCreate()

        // Check if the database is null or open
        if (database.isOpen) {
            Log.d(TAG, "Database is open")
        } else {
            Log.e(TAG, "Database is not open")
        }
    }
}
