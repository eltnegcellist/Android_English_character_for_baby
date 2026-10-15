package com.eltnegcellist.emma

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import com.eltnegcellist.emma.tts.DiagnosticStore
import java.io.File

// Cleanup is restricted to obsolete Gemma artifacts, never other models or history.
internal val LEGACY_GEMMA_FILE_NAMES = listOf(
    "gemma-4-E2B-it.litertlm",
    "gemma-4-E2B-it.litertlm.part",
    "gemma-4-E2B-it.litertlm.download.part",
    "gemma-4-E2B-it.litertlm.download.meta",
)

class EmmaApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        getSharedPreferences("emma_speech", Context.MODE_PRIVATE).edit()
            .putString("conversation_engine_mode", "LITE")
            .putString("audience_mode", "BABY")
            .remove("full_hidden_from_public_v1")
            .putBoolean("kokoro_only", false)
            .apply()
        val modelDir = File(filesDir, "models")
        LEGACY_GEMMA_FILE_NAMES.forEach { fileName ->
            val file = File(modelDir, fileName)
            if (file.exists() && !file.delete()) {
                DiagnosticStore.mark(this, "legacy_gemma_cleanup_failed", fileName)
            }
        }
        val preparation = getSharedPreferences("model_preparation_state", Context.MODE_PRIVATE)
        if (preparation.getString("kind", null) in listOf("FULL", "GEMMA")) {
            preparation.edit().clear().apply()
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(
                "conversation",
                "画面オフ会話",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "みつことばの画面オフ会話を表示します。"
                setShowBadge(false)
            },
        )
        DiagnosticStore.mark(
            this,
            "integration_mode",
            "liteOnly=true",
        )
    }
}

