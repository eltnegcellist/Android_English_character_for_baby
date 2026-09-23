package com.eltnegcellist.emma.tts

enum class KokoroCpuMode(
    val savedValue: String,
    val label: String,
    val description: String,
) {
    AUTO(
        savedValue = "AUTO",
        label = "自動（推奨）",
        description = "端末のCPUコア数とRAMに合わせて2・4・6スレッドから安全寄りに選びます。",
    ),
    THREADS_2(
        savedValue = "2",
        label = "軽量（2スレッド）",
        description = "低性能端末や発熱を抑えたい場合向けです。",
    ),
    THREADS_4(
        savedValue = "4",
        label = "標準（最大4スレッド）",
        description = "多くのミドルレンジ端末向けです。CPUコア数が少ない場合は自動で抑えます。",
    ),
    THREADS_6(
        savedValue = "6",
        label = "高速（最大6スレッド）",
        description = "高性能端末向けです。CPUコア数が少ない場合は自動で抑えます。",
    );

    companion object {
        fun fromSaved(value: String?): KokoroCpuMode =
            entries.firstOrNull { it.savedValue == value } ?: AUTO
    }
}

internal object KokoroThreadPolicy {
    fun selectThreads(
        mode: KokoroCpuMode,
        processorCount: Int,
        totalRamMb: Long,
    ): Int {
        val cores = processorCount.coerceAtLeast(1)
        return when (mode) {
            KokoroCpuMode.THREADS_2 -> minOf(2, cores)
            KokoroCpuMode.THREADS_4 -> minOf(4, cores)
            KokoroCpuMode.THREADS_6 -> minOf(6, cores)
            KokoroCpuMode.AUTO -> when {
                cores <= 4 -> minOf(2, cores)
                totalRamMb in 1 until 4096 -> minOf(2, cores)
                cores < 8 -> minOf(4, cores)
                totalRamMb in 1 until 8192 -> minOf(4, cores)
                else -> minOf(6, cores)
            }
        }.coerceAtLeast(1)
    }
}
