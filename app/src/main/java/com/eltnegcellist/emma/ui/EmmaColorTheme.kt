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
        description = "明るくやさしい配色です。グラデーションでは顔と飾りの色がゆっくり変わります。",
    ),
    VIVID(
        savedValue = "vivid",
        label = "はっきり色",
        description = "白い顔に、耳や頭の飾りの鮮やかな色が映える配色です。グラデーションでも白い部分はそのままです。",
    ),
    FILLED(
        savedValue = "filled",
        label = "塗りつぶし",
        description = "濃い飾り色と、顔や体にも薄く色を付けた配色です。グラデーションでは顔と飾りの色がゆっくり変わります。",
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
    LAVENDER("lavender", "ラベンダー"),
    GRADIENT("gradient", "グラデーション");

    companion object {
        fun fromSaved(value: String?): EmmaSoftPalette =
            entries.firstOrNull { it.savedValue == value } ?: PEACH
    }
}

internal enum class EmmaVividPalette(val savedValue: String, val label: String) {
    CORAL("coral", "コーラル（赤）"),
    BLUE("blue", "ブルー"),
    HONEY("honey", "はちみつ"),
    BERRY("berry", "ベリー"),
    GRADIENT("gradient", "グラデーション");

    companion object {
        fun fromSaved(value: String?, savedMode: String? = null): EmmaVividPalette {
            if (savedMode == "mono_red") return CORAL
            if (savedMode == "color_shift") return GRADIENT
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
    fun palette(mode: EmmaColorMode, vivid: EmmaVividPalette, hue: Float,
                soft: EmmaSoftPalette = EmmaSoftPalette.PEACH,
                filled: EmmaVividPalette = EmmaVividPalette.CORAL): EmmaPalette = when (mode) {
        EmmaColorMode.SOFT -> if (soft == EmmaSoftPalette.GRADIENT) shiftingPalette(hue, mode) else softPalette(soft)
        EmmaColorMode.VIVID -> if (vivid == EmmaVividPalette.GRADIENT) shiftingPalette(hue, mode) else vividPalette(vivid)
        EmmaColorMode.FILLED -> if (filled == EmmaVividPalette.GRADIENT) shiftingPalette(hue, mode) else filledPalette(filled)
    }

    fun preview(mode: EmmaColorMode, vivid: EmmaVividPalette,
                soft: EmmaSoftPalette = EmmaSoftPalette.PEACH,
                filled: EmmaVividPalette = EmmaVividPalette.CORAL): EmmaPalette = palette(mode, vivid, 32f, soft, filled)

    private fun softPalette(value: EmmaSoftPalette): EmmaPalette = when (value) {
        EmmaSoftPalette.GRADIENT -> shiftingPalette(0f, EmmaColorMode.SOFT)
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
        EmmaVividPalette.GRADIENT -> shiftingPalette(0f, EmmaColorMode.VIVID)
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

    private fun filledPalette(value: EmmaVividPalette): EmmaPalette = when (value) {
        EmmaVividPalette.GRADIENT -> shiftingPalette(0f, EmmaColorMode.FILLED)
        EmmaVividPalette.CORAL -> EmmaPalette(
            face = Color(0xFFFFD7DA),
            accent = Color(0xFFC52038),
            dark = Color(0xFF3F302C),
            blush = Color(0xFFE99BA5),
            mouth = Color(0xFF3F302C),
            tongue = Color(0xFFF1ABB8),
        )
        EmmaVividPalette.BLUE -> EmmaPalette(
            face = Color(0xFFD4EAFF),
            accent = Color(0xFF085A96),
            dark = Color(0xFF3F302C),
            blush = Color(0xFFE99BA5),
            mouth = Color(0xFF3F302C),
            tongue = Color(0xFFF1ABB8),
        )
        EmmaVividPalette.HONEY -> EmmaPalette(
            face = Color(0xFFFFE5AF),
            accent = Color(0xFFB66E00),
            dark = Color(0xFF3F302C),
            blush = Color(0xFFE99BA5),
            mouth = Color(0xFF3F302C),
            tongue = Color(0xFFF1ABB8),
        )
        EmmaVividPalette.BERRY -> EmmaPalette(
            face = Color(0xFFEED4E6),
            accent = Color(0xFF81245F),
            dark = Color(0xFF3F302C),
            blush = Color(0xFFE99BA5),
            mouth = Color(0xFF3F302C),
            tongue = Color(0xFFF1ABB8),
        )
    }

    private fun shiftingPalette(hue: Float, mode: EmmaColorMode): EmmaPalette {
        val position = (((hue % 360f) + 360f) % 360f) / 90f
        val index = position.toInt()
        val fraction = position - index
        val palettes = if (mode == EmmaColorMode.SOFT) {
            listOf(EmmaSoftPalette.PEACH, EmmaSoftPalette.MINT, EmmaSoftPalette.SKY, EmmaSoftPalette.LAVENDER).map(::softPalette)
        } else {
            val order = listOf(EmmaVividPalette.CORAL, EmmaVividPalette.HONEY, EmmaVividPalette.BLUE, EmmaVividPalette.BERRY)
            if (mode == EmmaColorMode.FILLED) order.map(::filledPalette) else order.map(::vividPalette)
        }
        val from = palettes[index]
        val to = palettes[(index + 1) % palettes.size]
        fun mix(a: Color, b: Color): Color {
            val start = a.toArgb()
            val end = b.toArgb()
            fun channel(shift: Int): Int {
                val x = (start shr shift) and 255
                val y = (end shr shift) and 255
                return (x + (y - x) * fraction).roundToInt()
            }
            return Color(channel(16), channel(8), channel(0))
        }
        return EmmaPalette(mix(from.face,to.face), mix(from.accent,to.accent), mix(from.dark,to.dark),
            mix(from.blush,to.blush), mix(from.mouth,to.mouth), mix(from.tongue,to.tongue))
    }
}

internal fun migrateLegacyEmmaColors(preferences: SharedPreferences) {
    val savedMode = preferences.getString("emma_color_mode", null)
    if (savedMode == "mono_red" || savedMode == "color_shift") {
        val vivid = EmmaVividPalette.fromSaved(preferences.getString("emma_vivid_palette", null), savedMode)
        preferences.edit().putString("emma_vivid_palette", vivid.savedValue)
            .putString("emma_color_mode", EmmaColorMode.VIVID.savedValue).apply()
    }
}
