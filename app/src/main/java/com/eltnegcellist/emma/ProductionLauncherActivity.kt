package com.eltnegcellist.emma

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity

/** Opens the production Emma experience and normalizes persisted audio mode state. */
class ProductionLauncherActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        getSharedPreferences("emma_speech", Context.MODE_PRIVATE)
            .edit()
            .putBoolean("kokoro_only", false)
            .apply()

        startActivity(Intent(this, ProductionMainActivity::class.java))
        finish()
    }
}
