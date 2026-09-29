package com.eltnegcellist.emma.model

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build

internal object ModelPreparationNotification {
    const val CHANNEL_ID = "model_preparation"
    const val NOTIFICATION_ID = 4102

    fun build(context: Context, phase: String, percent: Int?): Notification {
        ensureChannel(context)
        return Notification.Builder(context, CHANNEL_ID)
            .setContentTitle("みつことばを準備しています")
            .setContentText(phase)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setProgress(100, percent ?: 0, percent == null)
            .build()
    }

    fun update(context: Context, phase: String, percent: Int?) {
        context.getSystemService(NotificationManager::class.java)
            .notify(NOTIFICATION_ID, build(context, phase, percent))
    }

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "モデルの準備",
                NotificationManager.IMPORTANCE_LOW,
            ).apply { description = "音声認識・音声・Full会話モデルのダウンロード進捗" },
        )
    }
}
