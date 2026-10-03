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
        val accents = EmmaSoftPalette.entries.map { soft ->
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

    @Test fun gradientMatchesVividStopsAndKeepsOtherPaintsFixed() {
        val coral = EmmaColors.palette(EmmaColorMode.VIVID, EmmaVividPalette.CORAL, 0f)
        for ((hue, vivid) in listOf(0f to EmmaVividPalette.CORAL, 90f to EmmaVividPalette.HONEY,
            180f to EmmaVividPalette.BLUE, 270f to EmmaVividPalette.BERRY, 360f to EmmaVividPalette.CORAL,
            -90f to EmmaVividPalette.BERRY)) {
            assertEquals(EmmaColors.palette(EmmaColorMode.VIVID, vivid, 0f).accent,
                EmmaColors.palette(EmmaColorMode.COLOR_SHIFT, vivid, hue).accent)
        }
        assertEquals(Color(0xFFDB652D), EmmaColors.palette(EmmaColorMode.COLOR_SHIFT, EmmaVividPalette.BLUE, 45f).accent)
        for (step in 0 until 720) {
            val palette = EmmaColors.palette(EmmaColorMode.COLOR_SHIFT, EmmaVividPalette.BLUE, step / 2f)
            assertEquals(coral, palette.copy(accent = coral.accent))
        }
        assertEquals(coral, EmmaColors.palette(EmmaColorMode.COLOR_SHIFT, EmmaVividPalette.BLUE, 359.999f))
    }

    @Test fun timeShiftChangesAccentWithoutChangingWhiteFaceOrArtwork() {
        val first = paths(recolorEmmaFace(source(), EmmaColors.palette(EmmaColorMode.COLOR_SHIFT, EmmaVividPalette.HONEY, 0f)))
        val next = paths(recolorEmmaFace(source(), EmmaColors.palette(EmmaColorMode.COLOR_SHIFT, EmmaVividPalette.HONEY, 120f)))
        assertEquals(first[0].fill, next[0].fill)
        assertTrue(first[1].fill != next[1].fill)
        assertEquals(first[0].pathData, next[0].pathData)
    }
}
