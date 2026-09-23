package com.eltnegcellist.emma

import android.app.Application
import android.content.Context
import com.eltnegcellist.emma.tts.DiagnosticStore

/**
 * v0.5.7 returns to the full Emma app. Older diagnostic builds persisted
 * kokoro_only=true, whose historical default also prevents Gemma from loading.
 * Force normal integration mode before MainActivity/Compose reads that preference.
 */
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

