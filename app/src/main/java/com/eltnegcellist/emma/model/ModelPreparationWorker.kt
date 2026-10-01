package com.eltnegcellist.emma.model

import android.content.Context
import android.content.pm.ServiceInfo
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.eltnegcellist.emma.asr.MoonshineAsrModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

internal class ModelPreparationWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val kind = inputData.getString(KEY_KIND)
            ?.let { runCatching { ModelPreparationKind.valueOf(it) }.getOrNull() }
            ?: return Result.failure()
        val asr = MoonshineAsrModel.fromSaved(inputData.getString(KEY_ASR))

        ModelPreparationStateStore.running(applicationContext, kind, asr, "準備を開始しています…", null)
        setForeground(
            ForegroundInfo(
                ModelPreparationNotification.NOTIFICATION_ID,
                ModelPreparationNotification.build(applicationContext, "準備を開始しています…", null),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
            ),
        )

        val result = withContext(Dispatchers.IO) {
            ModelPreparationTask.run(applicationContext, kind, asr) { phase, percent ->
                ModelPreparationStateStore.running(applicationContext, kind, asr, phase, percent)
                ModelPreparationNotification.update(applicationContext, phase, percent)
            }
        }

        return result.fold(
            onSuccess = {
                ModelPreparationStateStore.succeeded(applicationContext, kind, asr)
                Result.success()
            },
            onFailure = { error ->
                val retryable = isRetryableNetworkError(error)
                if (retryable && runAttemptCount < 3) {
                    ModelPreparationStateStore.retrying(applicationContext, kind, asr)
                    Result.retry()
                } else {
                    ModelPreparationStateStore.failed(
                        applicationContext,
                        kind,
                        asr,
                        error.message ?: error.javaClass.simpleName,
                    )
                    Result.failure()
                }
            },
        )
    }

    private fun isRetryableNetworkError(error: Throwable): Boolean {
        var current: Throwable? = error
        while (current != null) {
            if (current is IOException || current is InterruptedException) return true
            current = current.cause
        }
        return false
    }

    companion object {
        const val KEY_KIND = "model_preparation_kind"
        const val KEY_ASR = "model_preparation_asr"
    }
}
