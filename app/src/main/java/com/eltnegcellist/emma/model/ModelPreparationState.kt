package com.eltnegcellist.emma.model

import android.content.Context
import com.eltnegcellist.emma.asr.MoonshineAsrModel

internal enum class ModelPreparationKind { LITE, FULL, ASR, KITTEN, GEMMA, SEMANTIC }
internal enum class ModelPreparationStatus { IDLE, QUEUED, RUNNING, SUCCEEDED, FAILED }

internal data class ModelPreparationSnapshot(
    val kind: ModelPreparationKind?,
    val asrModel: MoonshineAsrModel,
    val status: ModelPreparationStatus,
    val phase: String,
    val percent: Int?,
    val error: String?,
    val updatedAtMillis: Long,
    val handledAtMillis: Long,
) {
    val active: Boolean
        get() = status == ModelPreparationStatus.QUEUED || status == ModelPreparationStatus.RUNNING
}

internal object ModelPreparationStateStore {
    private const val PREFS = "model_preparation_state"

    fun read(context: Context): ModelPreparationSnapshot {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return ModelPreparationSnapshot(
            kind = p.getString("kind", null)?.let {
                runCatching { ModelPreparationKind.valueOf(it) }.getOrNull()
            },
            asrModel = MoonshineAsrModel.fromSaved(p.getString("asr_model", null)),
            status = p.getString("status", null)?.let {
                runCatching { ModelPreparationStatus.valueOf(it) }.getOrNull()
            } ?: ModelPreparationStatus.IDLE,
            phase = p.getString("phase", "").orEmpty(),
            percent = if (p.contains("percent")) p.getInt("percent", 0) else null,
            error = p.getString("error", null),
            updatedAtMillis = p.getLong("updated_at", 0L),
            handledAtMillis = p.getLong("handled_at", 0L),
        )
    }

    fun queued(context: Context, kind: ModelPreparationKind, asr: MoonshineAsrModel) =
        write(context, kind, asr, ModelPreparationStatus.QUEUED, "準備を開始しています…", null, null)

    fun running(context: Context, kind: ModelPreparationKind, asr: MoonshineAsrModel, phase: String, percent: Int?) =
        write(context, kind, asr, ModelPreparationStatus.RUNNING, phase, percent, null)

    fun succeeded(context: Context, kind: ModelPreparationKind, asr: MoonshineAsrModel) =
        write(context, kind, asr, ModelPreparationStatus.SUCCEEDED, "準備ができました", 100, null)

    fun retrying(context: Context, kind: ModelPreparationKind, asr: MoonshineAsrModel) {
        val current = read(context)
        write(
            context,
            kind,
            asr,
            ModelPreparationStatus.QUEUED,
            "通信が中断されました。自動で再開します…",
            current.percent,
            null,
        )
    }

    fun failed(context: Context, kind: ModelPreparationKind, asr: MoonshineAsrModel, error: String) =
        write(context, kind, asr, ModelPreparationStatus.FAILED, "準備を完了できませんでした", null, error)

    fun markHandled(context: Context, updatedAtMillis: Long) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putLong("handled_at", updatedAtMillis).apply()
    }

    private fun write(
        context: Context,
        kind: ModelPreparationKind,
        asr: MoonshineAsrModel,
        status: ModelPreparationStatus,
        phase: String,
        percent: Int?,
        error: String?,
    ) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().apply {
            putString("kind", kind.name)
            putString("asr_model", asr.savedValue)
            putString("status", status.name)
            putString("phase", phase)
            if (percent == null) remove("percent") else putInt("percent", percent.coerceIn(0, 100))
            if (error == null) remove("error") else putString("error", error)
            putLong("updated_at", System.currentTimeMillis())
        }.apply()
    }
}
