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
        description = "やさしい淡い配色です。",
    ),
    VIVID(
        savedValue = "vivid",
        label = "はっきり色",
        description = "原色寄りの複数色で、顔のコントラストを強くします。",
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
            entries.firstOrNull { it.savedValue == value } ?: COLOR_SHIFT
    }
}

internal enum class EmmaVividPalette(
    val savedValue: String,
    val label: String,
) {
    SUNSHINE("sunshine", "サンシャイン"),
    OCEAN("ocean", "オーシャン"),
    CANDY("candy", "キャンディ"),
    FOREST("forest", "フォレスト");

    companion object {
        fun fromSaved(value: String?): EmmaVividPalette =
            entries.firstOrNull { it.savedValue == value } ?: SUNSHINE
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
    private val soft = EmmaPalette(
        face = Color(0xFFF0E7FF),
        accent = Color(0xFF7653B8),
        dark = Color(0xFF302940),
        blush = Color(0xFFFF8FAA),
        mouth = Color(0xFFAF4269),
        tongue = Color(0xFFFFB0C2),
    )

    private val monoRed = EmmaPalette(
        face = Color.White,
        accent = Color(0xFFE00000),
        dark = Color(0xFF080808),
        blush = Color(0xFFE00000),
        mouth = Color(0xFF080808),
        tongue = Color(0xFFE00000),
    )

    fun palette(mode: EmmaColorMode, vivid: EmmaVividPalette, hue: Float): EmmaPalette = when (mode) {
        EmmaColorMode.SOFT -> soft
        EmmaColorMode.MONO_RED -> monoRed
        EmmaColorMode.VIVID -> vividPalette(vivid)
        EmmaColorMode.COLOR_SHIFT -> shiftingPalette(hue)
    }

    fun preview(mode: EmmaColorMode, vivid: EmmaVividPalette): EmmaPalette = when (mode) {
        EmmaColorMode.COLOR_SHIFT -> shiftingPalette(32f)
        else -> palette(mode, vivid, 0f)
    }

    private fun vividPalette(vivid: EmmaVividPalette): EmmaPalette = when (vivid) {
        EmmaVividPalette.SUNSHINE -> EmmaPalette(
            face = Color(0xFFFFD600),
            accent = Color(0xFF1E5BFF),
            dark = Color(0xFF101010),
            blush = Color(0xFFFF3B30),
            mouth = Color(0xFF8F153B),
            tongue = Color(0xFFFF8CA7),
        )
        EmmaVividPalette.OCEAN -> EmmaPalette(
            face = Color(0xFF2D7FFF),
            accent = Color(0xFFFFD600),
            dark = Color(0xFF0B1733),
            blush = Color(0xFFFF4081),
            mouth = Color(0xFF7A1639),
            tongue = Color(0xFFFF9AB5),
        )
        EmmaVividPalette.CANDY -> EmmaPalette(
            face = Color(0xFFFF4FA3),
            accent = Color(0xFF00C853),
            dark = Color(0xFF1E1020),
            blush = Color(0xFFFFD600),
            mouth = Color(0xFF8A1746),
            tongue = Color(0xFFFFB0C5),
        )
        EmmaVividPalette.FOREST -> EmmaPalette(
            face = Color(0xFF00C853),
            accent = Color(0xFF7C4DFF),
            dark = Color(0xFF102418),
            blush = Color(0xFFFF3B30),
            mouth = Color(0xFF76152F),
            tongue = Color(0xFFFF9DAE),
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
