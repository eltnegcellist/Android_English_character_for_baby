package com.eltnegcellist.emma.model

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.eltnegcellist.emma.asr.MoonshineAsrModel
import java.util.concurrent.TimeUnit

internal object ModelPreparationManager {
    private const val WORK_NAME = "model-preparation"
    private const val ACTIVE_HEARTBEAT_TIMEOUT_MS = 10L * 60L * 1000L

    fun start(context: Context, kind: ModelPreparationKind, asr: MoonshineAsrModel): Boolean {
        val app = context.applicationContext
        val current = ModelPreparationStateStore.read(app)
        if (current.active) {
            val fresh = System.currentTimeMillis() - current.updatedAtMillis < ACTIVE_HEARTBEAT_TIMEOUT_MS
            if (fresh) return current.kind == kind && current.asrModel == asr
        }

        ModelPreparationStateStore.queued(app, kind, asr)
        val request = OneTimeWorkRequestBuilder<ModelPreparationWorker>()
            .setInputData(
                Data.Builder()
                    .putString(ModelPreparationWorker.KEY_KIND, kind.name)
                    .putString(ModelPreparationWorker.KEY_ASR, asr.savedValue)
                    .build(),
            )
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .setRequiresStorageNotLow(true)
                    .build(),
            )
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(app)
            .enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.REPLACE, request)
        return true
    }

    fun snapshot(context: Context): ModelPreparationSnapshot =
        ModelPreparationStateStore.read(context.applicationContext)

    fun markHandled(context: Context, snapshot: ModelPreparationSnapshot) {
        ModelPreparationStateStore.markHandled(context.applicationContext, snapshot.updatedAtMillis)
    }
}
