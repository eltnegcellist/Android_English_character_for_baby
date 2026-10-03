package com.eltnegcellist.emma.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathNode
import androidx.compose.ui.graphics.vector.VectorGroup
import androidx.compose.ui.graphics.vector.VectorPath
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EmmaIllustrationColorsTest {
    private val geometry = listOf(PathNode.MoveTo(12f, 16f), PathNode.LineTo(28f, 32f))
    private fun source(): ImageVector = ImageVector.Builder(
        defaultWidth = 380.dp, defaultHeight = 380.dp,
        viewportWidth = 1024f, viewportHeight = 1024f,
    ).apply {
        for (color in listOf(0xFFFBFCFF, 0xFF70C8FF, 0xFF513934, 0xFFFFFFFF)) {
            addPath(pathData = geometry, fill = SolidColor(Color(color)))
        }
    }.build()

    private fun paths(image: ImageVector): List<VectorPath> =
        (image.root[0] as VectorGroup).map { it as VectorPath }

    @Test fun everyModeColorsTheArtworkAndPreservesExpressionGeometry() {
        for (mode in EmmaColorMode.entries) {
            for (vivid in EmmaVividPalette.entries) {
                val palette = EmmaColors.palette(mode, vivid, 72f)
                val result = paths(recolorEmmaFace(source(), palette))
                assertEquals(palette.face, (result[0].fill as SolidColor).value)
                assertEquals(palette.accent, (result[1].fill as SolidColor).value)
                assertEquals(palette.dark, (result[2].fill as SolidColor).value)
                assertEquals(Color.White, (result[3].fill as SolidColor).value)
                result.forEach { assertEquals(geometry, it.pathData) }
            }
        }
    }

    @Test fun allVividFacesAndBodiesStayWhite() {
        for (vivid in EmmaVividPalette.entries) {
            val palette = EmmaColors.palette(EmmaColorMode.VIVID, vivid, 0f)
            val result = paths(recolorEmmaFace(source(), palette))
            assertEquals(Color.White, palette.face)
            assertEquals(Color.White, (result[0].fill as SolidColor).value)
            assertEquals(palette.accent, (result[1].fill as SolidColor).value)
            assertEquals(geometry, result[0].pathData)
        }
    }

    @Test fun softSelectionsRoundTripAndProduceFourDifferentAccents() {
        val accents = EmmaSoftPalette.entries.filter { it != EmmaSoftPalette.GRADIENT }.map { soft ->
            assertEquals(soft, EmmaSoftPalette.fromSaved(soft.savedValue))
            val palette = EmmaColors.palette(EmmaColorMode.SOFT, EmmaVividPalette.CORAL, 0f, soft)
            val result = paths(recolorEmmaFace(source(), palette))
            assertEquals(palette.face, (result[0].fill as SolidColor).value)
            assertEquals(palette.accent, (result[1].fill as SolidColor).value)
            palette.accent
        }
        assertEquals(4, accents.toSet().size)
        assertEquals(EmmaSoftPalette.PEACH, EmmaSoftPalette.fromSaved(null))
        assertEquals(EmmaSoftPalette.PEACH, EmmaSoftPalette.fromSaved("unknown"))
    }

    @Test fun legacyVividSettingsMigrateWithoutLosingTheirSelection() {
        assertEquals(EmmaVividPalette.HONEY, EmmaVividPalette.fromSaved("sunshine"))
        assertEquals(EmmaVividPalette.BLUE, EmmaVividPalette.fromSaved("ocean"))
        assertEquals(EmmaVividPalette.CORAL, EmmaVividPalette.fromSaved("candy"))
        assertEquals(EmmaVividPalette.BERRY, EmmaVividPalette.fromSaved("forest"))
        assertEquals(EmmaVividPalette.CORAL, EmmaVividPalette.fromSaved(null))
        EmmaVividPalette.entries.forEach { assertEquals(it, EmmaVividPalette.fromSaved(it.savedValue)) }
    }

    @Test fun defaultAndRemovedModeUseVividCoral() {
        assertEquals(3, EmmaColorMode.entries.size)
        assertEquals(EmmaColorMode.VIVID, EmmaColorMode.fromSaved(null))
        assertEquals(EmmaColorMode.VIVID, EmmaColorMode.fromSaved("unknown"))
        assertEquals(EmmaColorMode.VIVID, EmmaColorMode.fromSaved("mono_red"))
        assertEquals(EmmaVividPalette.CORAL, EmmaVividPalette.fromSaved("blue", "mono_red"))
        val palette = EmmaColors.palette(EmmaColorMode.VIVID, EmmaVividPalette.CORAL, 0f)
        val result = paths(recolorEmmaFace(source(), palette))
        assertEquals(Color.White, (result[0].fill as SolidColor).value)
        assertEquals(palette.dark, (result[0].stroke as SolidColor).value)
        assertTrue(result[0].strokeAlpha >= 0.6f)
    }

    @Test fun oldGradientMigratesToVividGradient() {
        assertEquals(EmmaColorMode.VIVID, EmmaColorMode.fromSaved("color_shift"))
        assertEquals(EmmaVividPalette.GRADIENT, EmmaVividPalette.fromSaved("blue", "color_shift"))
        assertEquals(EmmaSoftPalette.GRADIENT, EmmaSoftPalette.fromSaved("gradient"))
        assertEquals(EmmaVividPalette.GRADIENT, EmmaVividPalette.fromSaved("gradient"))
    }

    @Test fun gradientsFollowTheirOwnModeAndLoopSeamlessly() {
        assertEquals(60_000, EmmaColors.GRADIENT_CYCLE_MILLIS)
        EmmaColorMode.entries.forEach { assertTrue(it.gradientDescription.contains("60秒で一周")) }
        val softOrder = listOf(EmmaSoftPalette.PEACH, EmmaSoftPalette.MINT, EmmaSoftPalette.SKY, EmmaSoftPalette.LAVENDER)
        val vividOrder = listOf(EmmaVividPalette.CORAL, EmmaVividPalette.HONEY, EmmaVividPalette.BLUE, EmmaVividPalette.BERRY)
        for (mode in EmmaColorMode.entries) {
            fun gradient(hue: Float) = EmmaColors.palette(mode, EmmaVividPalette.GRADIENT, hue,
                EmmaSoftPalette.GRADIENT, EmmaVividPalette.GRADIENT)
            for (index in 0..3) {
                val fixed = EmmaColors.palette(mode, vividOrder[index], 0f, softOrder[index], vividOrder[index])
                assertEquals(fixed, gradient(index * 90f))
            }
            assertEquals(gradient(0f), gradient(360f))
            assertEquals(gradient(0f), gradient(359.999f))
            assertEquals(gradient(270f), gradient(-90f))
            val first = paths(recolorEmmaFace(source(), gradient(0f)))
            val next = paths(recolorEmmaFace(source(), gradient(90f)))
            assertTrue(first[1].fill != next[1].fill)
            assertEquals(first[0].pathData, next[0].pathData)
            for (step in 0 until 720) {
                val palette = gradient(step / 2f)
                if (mode == EmmaColorMode.VIVID) assertEquals(Color.White, palette.face)
                else assertTrue(Color.White != palette.face)
            }
        }
    }

    @Test fun filledTintsWhiteShellsAndKeepsEyeHighlightsWhite() {
        for (vivid in EmmaVividPalette.entries.filter { it != EmmaVividPalette.GRADIENT }) {
            val filled = EmmaColors.palette(EmmaColorMode.FILLED, vivid, 0f, filled = vivid)
            val normal = EmmaColors.palette(EmmaColorMode.VIVID, vivid, 0f)
            fun light(color: Color) = color.red * 0.2126f + color.green * 0.7152f + color.blue * 0.0722f
            assertTrue(light(filled.accent) < light(normal.accent))
            assertTrue(filled.face != Color.White)
            val result = paths(recolorEmmaFace(source(), filled))
            assertEquals(filled.face, (result[0].fill as SolidColor).value)
            assertEquals(Color.White, (result[3].fill as SolidColor).value)
            assertEquals(geometry, result[0].pathData)
        }
    }
}
