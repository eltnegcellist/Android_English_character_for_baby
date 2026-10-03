package com.eltnegcellist.emma.ui

import androidx.compose.ui.graphics.Color

internal enum class EmmaColorMode(
    val savedValue: String,
    val label: String,
    val description: String,
) {
    SOFT(
        savedValue = "soft",
        label = "やさしい色",
        description = "明るくやさしい4種類の配色から選べます。",
    ),
    VIVID(
        savedValue = "vivid",
        label = "はっきり色",
        description = "白い顔に、耳や頭の飾りの鮮やかな色が映える配色です。",
    ),
    MONO_RED(
        savedValue = "mono_red",
        label = "白黒＋赤",
        description = "白い顔、黒い目と輪郭、赤いアクセントの固定配色です。",
    ),
    COLOR_SHIFT(
        savedValue = "color_shift",
        label = "カラーチェンジ",
        description = "会話状態とは無関係に、時間経過で配色がゆっくり変わります。",
    );

    companion object {
        fun fromSaved(value: String?): EmmaColorMode =
            entries.firstOrNull { it.savedValue == value } ?: MONO_RED
    }
}

internal enum class EmmaSoftPalette(val savedValue: String, val label: String) {
    PEACH("peach", "ピーチ"),
    MINT("mint", "ミント"),
    SKY("sky", "そら"),
    LAVENDER("lavender", "ラベンダー");

    companion object {
        fun fromSaved(value: String?): EmmaSoftPalette =
            entries.firstOrNull { it.savedValue == value } ?: PEACH
    }
}

internal enum class EmmaVividPalette(val savedValue: String, val label: String) {
    CORAL("coral", "コーラル"),
    BLUE("blue", "ブルー"),
    HONEY("honey", "はちみつ"),
    BERRY("berry", "ベリー");

    companion object {
        fun fromSaved(value: String?): EmmaVividPalette = when (value) {
            "sunshine" -> HONEY
            "ocean" -> BLUE
            "candy" -> CORAL
            "forest" -> BERRY
            else -> entries.firstOrNull { it.savedValue == value } ?: CORAL
        }
    }
}

internal data class EmmaPalette(
    val face: Color,
    val accent: Color,
    val dark: Color,
    val blush: Color,
    val mouth: Color,
    val tongue: Color,
)

internal object EmmaColors {
    private val monoRed = EmmaPalette(
        face = Color.White,
        accent = Color(0xFFE00000),
        dark = Color(0xFF080808),
        blush = Color(0xFFE00000),
        mouth = Color(0xFF080808),
        tongue = Color(0xFFE00000),
    )

    fun palette(mode: EmmaColorMode, vivid: EmmaVividPalette, hue: Float, soft: EmmaSoftPalette = EmmaSoftPalette.PEACH): EmmaPalette = when (mode) {
        EmmaColorMode.SOFT -> softPalette(soft)
        EmmaColorMode.MONO_RED -> monoRed
        EmmaColorMode.VIVID -> vividPalette(vivid)
        EmmaColorMode.COLOR_SHIFT -> shiftingPalette(hue)
    }

    fun preview(mode: EmmaColorMode, vivid: EmmaVividPalette, soft: EmmaSoftPalette = EmmaSoftPalette.PEACH): EmmaPalette = when (mode) {
        EmmaColorMode.COLOR_SHIFT -> shiftingPalette(32f)
        else -> palette(mode, vivid, 0f, soft)
    }

    private fun softPalette(value: EmmaSoftPalette): EmmaPalette = when (value) {
        EmmaSoftPalette.PEACH -> EmmaPalette(
            face = Color(0xFFFFEDE6),
            accent = Color(0xFFF2A69C),
            dark = Color(0xFF51443F),
            blush = Color(0xFFF0A9AE),
            mouth = Color(0xFF51443F),
            tongue = Color(0xFFEBA0AA),
        )
        EmmaSoftPalette.MINT -> EmmaPalette(
            face = Color(0xFFE5F5EE),
            accent = Color(0xFF7BC6AE),
            dark = Color(0xFF51443F),
            blush = Color(0xFFF0A9AE),
            mouth = Color(0xFF51443F),
            tongue = Color(0xFFEBA0AA),
        )
        EmmaSoftPalette.SKY -> EmmaPalette(
            face = Color(0xFFE8F2FE),
            accent = Color(0xFF83B9E6),
            dark = Color(0xFF51443F),
            blush = Color(0xFFF0A9AE),
            mouth = Color(0xFF51443F),
            tongue = Color(0xFFEBA0AA),
        )
        EmmaSoftPalette.LAVENDER -> EmmaPalette(
            face = Color(0xFFF2E9FF),
            accent = Color(0xFFB49AE3),
            dark = Color(0xFF51443F),
            blush = Color(0xFFF0A9AE),
            mouth = Color(0xFF51443F),
            tongue = Color(0xFFEBA0AA),
        )
    }

    private fun vividPalette(value: EmmaVividPalette): EmmaPalette = when (value) {
        EmmaVividPalette.CORAL -> EmmaPalette(
            face = Color(0xFFFFFFFF),
            accent = Color(0xFFDF3E50),
            dark = Color(0xFF3F302C),
            blush = Color(0xFFE99BA5),
            mouth = Color(0xFF3F302C),
            tongue = Color(0xFFF1ABB8),
        )
        EmmaVividPalette.BLUE -> EmmaPalette(
            face = Color(0xFFFFFFFF),
            accent = Color(0xFF1474B2),
            dark = Color(0xFF3F302C),
            blush = Color(0xFFE99BA5),
            mouth = Color(0xFF3F302C),
            tongue = Color(0xFFF1ABB8),
        )
        EmmaVividPalette.HONEY -> EmmaPalette(
            face = Color(0xFFFFFFFF),
            accent = Color(0xFFD68C0A),
            dark = Color(0xFF3F302C),
            blush = Color(0xFFE99BA5),
            mouth = Color(0xFF3F302C),
            tongue = Color(0xFFF1ABB8),
        )
        EmmaVividPalette.BERRY -> EmmaPalette(
            face = Color(0xFFFFFFFF),
            accent = Color(0xFFA13D7C),
            dark = Color(0xFF3F302C),
            blush = Color(0xFFE99BA5),
            mouth = Color(0xFF3F302C),
            tongue = Color(0xFFF1ABB8),
        )
    }

    private fun shiftingPalette(hue: Float): EmmaPalette {
        val baseHue = ((hue % 360f) + 360f) % 360f
        return EmmaPalette(
            face = Color.hsl(baseHue, 0.88f, 0.67f),
            accent = Color.hsl((baseHue + 155f) % 360f, 0.92f, 0.46f),
            dark = Color(0xFF121019),
            blush = Color.hsl((baseHue + 292f) % 360f, 0.95f, 0.58f),
            mouth = Color(0xFF82173A),
            tongue = Color(0xFFFF9FB7),
        )
    }
}

