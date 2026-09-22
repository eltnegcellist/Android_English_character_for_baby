package com.eltnegcellist.emma

import android.app.Application
import android.content.Context
import com.eltnegcellist.emma.tts.DiagnosticStore

/** Application entry point for Emma's local audio and model services. */
class EmmaApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        getSharedPreferences("emma_speech", Context.MODE_PRIVATE)
            .edit()
            .putBoolean("kokoro_only", false)
            .commit()
        DiagnosticStore.mark(
            this,
            "integration_mode",
            "kokoroOnly=false gemmaAutoLoad=true kokoroCallbackMode=none",
        )
    }
}

