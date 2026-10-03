package com.eltnegcellist.emma.ui

import android.content.SharedPreferences
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import kotlin.math.roundToInt

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
    COLOR_SHIFT(
        savedValue = "color_shift",
        label = "カラーチェンジ",
        description = "白い顔と体はそのまま、耳や飾りが赤・はちみつ・ブルー・ベリーへ滑らかに変わります。",
    );

    companion object {
        fun fromSaved(value: String?): EmmaColorMode =
            entries.firstOrNull { it.savedValue == value } ?: VIVID
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
    CORAL("coral", "コーラル（赤）"),
    BLUE("blue", "ブルー"),
    HONEY("honey", "はちみつ"),
    BERRY("berry", "ベリー");

    companion object {
        fun fromSaved(value: String?, savedMode: String? = null): EmmaVividPalette {
            if (savedMode == "mono_red") return CORAL
            return when (value) {
                "sunshine" -> HONEY
                "ocean" -> BLUE
                "candy" -> CORAL
                "forest" -> BERRY
                else -> entries.firstOrNull { it.savedValue == value } ?: CORAL
            }
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
    fun palette(mode: EmmaColorMode, vivid: EmmaVividPalette, hue: Float, soft: EmmaSoftPalette = EmmaSoftPalette.PEACH): EmmaPalette = when (mode) {
        EmmaColorMode.SOFT -> softPalette(soft)
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
        val order = listOf(EmmaVividPalette.CORAL, EmmaVividPalette.HONEY, EmmaVividPalette.BLUE, EmmaVividPalette.BERRY)
        val position = (((hue % 360f) + 360f) % 360f) / 90f
        val index = position.toInt()
        val fraction = position - index
        val from = vividPalette(order[index]).accent.toArgb()
        val to = vividPalette(order[(index + 1) % order.size]).accent.toArgb()
        // Match Web's rounded sRGB channel interpolation.
        fun channel(shift: Int): Int {
            val a = (from shr shift) and 255
            val b = (to shr shift) and 255
            return (a + (b - a) * fraction).roundToInt()
        }
        val accent = Color(channel(16), channel(8), channel(0))
        return vividPalette(EmmaVividPalette.CORAL).copy(accent = accent)
    }
}

internal fun migrateLegacyEmmaColors(preferences: SharedPreferences) {
    val savedMode = preferences.getString("emma_color_mode", null)
    if (savedMode == "mono_red") {
        val vivid = EmmaVividPalette.fromSaved(preferences.getString("emma_vivid_palette", null), savedMode)
        preferences.edit().putString("emma_vivid_palette", vivid.savedValue)
            .putString("emma_color_mode", EmmaColorMode.VIVID.savedValue).apply()
    }
}
