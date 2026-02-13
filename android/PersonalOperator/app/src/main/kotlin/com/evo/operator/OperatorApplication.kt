package com.evo.operator

import android.app.Application
import android.util.Log

/**
 * Application class for Personal Operator.
 * Initializes global dependencies on app startup.
 */
class OperatorApplication : Application() {

    companion object {
        private const val TAG = "OperatorApp"
        lateinit var instance: OperatorApplication
            private set
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        Log.i(TAG, "Personal Operator application started")
    }
}
