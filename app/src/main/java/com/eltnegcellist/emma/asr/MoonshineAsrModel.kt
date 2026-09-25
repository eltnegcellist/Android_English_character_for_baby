package com.eltnegcellist.emma.asr

import ai.moonshine.voice.JNI

enum class MoonshineAsrModel(
    val savedValue: String,
    val label: String,
    val shortLabel: String,
    val modelName: String,
    val arch: Int,
    val approxDownloadMb: Int?,
) {
    TINY(
        savedValue = "tiny",
        label = "軽量・標準（Tiny）",
        shortLabel = "Tiny",
        modelName = "moonshine-tiny-streaming-ja",
        arch = JNI.MOONSHINE_MODEL_ARCH_TINY_STREAMING,
        approxDownloadMb = 32,
    ),
    SMALL(
        savedValue = "small",
        label = "高精度（Small）",
        shortLabel = "Small",
        modelName = "moonshine-small-streaming-ja",
        arch = JNI.MOONSHINE_MODEL_ARCH_SMALL_STREAMING,
        approxDownloadMb = null,
    );

    companion object {
        fun fromSaved(value: String?): MoonshineAsrModel =
            entries.firstOrNull { it.savedValue == value } ?: TINY
    }
}
