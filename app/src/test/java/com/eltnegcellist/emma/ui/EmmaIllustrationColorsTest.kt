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

    @Test fun whiteFaceHasVisibleOutlineAndRedAccents() {
        val palette = EmmaColors.palette(EmmaColorMode.MONO_RED, EmmaVividPalette.SUNSHINE, 0f)
        val result = paths(recolorEmmaFace(source(), palette))
        assertEquals(Color.White, (result[0].fill as SolidColor).value)
        assertEquals(palette.dark, (result[0].stroke as SolidColor).value)
        assertTrue(result[0].strokeAlpha >= 0.6f)
        assertTrue(result[0].strokeLineWidth >= 8f)
        assertEquals(palette.accent, (result[1].fill as SolidColor).value)
    }

    @Test fun timeShiftChangesFaceWithoutChangingArtwork() {
        val first = paths(recolorEmmaFace(source(), EmmaColors.palette(EmmaColorMode.COLOR_SHIFT, EmmaVividPalette.SUNSHINE, 0f)))
        val next = paths(recolorEmmaFace(source(), EmmaColors.palette(EmmaColorMode.COLOR_SHIFT, EmmaVividPalette.SUNSHINE, 120f)))
        assertTrue(first[0].fill != next[0].fill)
        assertEquals(first[0].pathData, next[0].pathData)
    }
}
