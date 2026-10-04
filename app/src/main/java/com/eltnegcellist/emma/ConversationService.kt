package com.eltnegcellist.emma

import android.app.*
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.*

/** Created only from a visible, explicitly started conversation. Never restarts itself. */
class ConversationService : Service() {
    private lateinit var runtime: ConversationController
    private lateinit var audio: AudioManager
    private var focus: AudioFocusRequest? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var ending = false
    override fun onBind(intent: Intent?) = null
    override fun onCreate() {
        super.onCreate()
        runtime = ConversationRuntime.get(this)
        audio = getSystemService(AudioManager::class.java)
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel("conversation", "画面オフ会話", NotificationManager.IMPORTANCE_LOW))
    }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "STOP") { finishConversation(); return START_NOT_STICKY }
        if (!runtime.startRequested) { ending=true; stopSelf(); return START_NOT_STICKY }
        ending=false
        try {
            val notification = buildNotification()
            if (Build.VERSION.SDK_INT >= 29) startForeground(21, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE or ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
            else startForeground(21, notification)
            runtime.conversationServiceActiveState.value = true
            runtime.onNotificationChanged = { updateNotification() }
            runtime.attachConversationService()
            val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
                .setOnAudioFocusChangeListener({ change -> if (change < 0) finishConversation() }, Handler(Looper.getMainLooper()))
                .build()
            focus = request
            check(audio.requestAudioFocus(request) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) { "音声を使えません" }
            wakeLock = getSystemService(PowerManager::class.java).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "emma:conversation").apply { acquire() }
            runtime.onStopped = { finishConversation() }
            if (!runtime.recording) runtime.beginRecording()
        } catch (error: Exception) {
            finishConversation()
            runtime.status = ProductionEmmaStatus.ERROR
            runtime.statusMessage = "画面オフ会話を開始できません: ${error.message}"
        }
        return START_NOT_STICKY
    }
    private fun buildNotification(): Notification {
        val stop = PendingIntent.getService(this, 1, Intent(this, ConversationService::class.java).setAction("STOP"), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val open = PendingIntent.getActivity(this, 0, Intent(this, ProductionMainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val controls = getSharedPreferences("emma_speech", 0).getBoolean("conversation_notification_controls", getSharedPreferences("emma_speech", 0).getBoolean("conversation_notification_permission_requested", false))
        val builder = Notification.Builder(this, "conversation")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now).setContentTitle("みつことばの会話中")
            .setContentText(if (controls) "通知から会話を止められます。" else "アプリの「会話を止める」で終了できます。")
            .setContentIntent(open).setOngoing(true).setOnlyAlertOnce(true)
        if (Build.VERSION.SDK_INT >= 31) builder.setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE)
        if (controls) builder.addAction(Notification.Action.Builder(null, "会話を止める", stop).build())
        return builder.build()
    }
    private fun updateNotification() {
        if (!ending && runtime.conversationServiceActiveState.value) {
            getSystemService(NotificationManager::class.java).notify(21, buildNotification())
        }
    }
    private fun finishConversation() {
        if (ending) return
        ending = true
        runtime.onStopped = null
        runtime.onNotificationChanged = null
        runtime.conversationServiceActiveState.value = false
        runtime.stopSession()
        focus?.let { audio.abandonAudioFocusRequest(it) }; focus = null
        wakeLock?.let { if (it.isHeld) it.release() }; wakeLock = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }
    override fun onDestroy() { finishConversation(); super.onDestroy() }
}
