package com.eltnegcellist.emma

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity

/**
 * Production entry point. It migrates installs out of the temporary TTS-only
 * diagnostic mode used during development, then opens the production Emma UI.
 */
class ProductionLauncherActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        getSharedPreferences("emma_speech", Context.MODE_PRIVATE)
            .edit()
            .putBoolean("kokoro_only", false)
            .putBoolean("supertonic_only", false)
            .apply()

        startActivity(Intent(this, ProductionMainActivity::class.java))
        finish()
    }
}
