package com.eltnegcellist.emma.ui

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.VectorGroup
import androidx.compose.ui.graphics.vector.VectorPath

/** Reuse the completed expression artwork; only its paints change, never facial geometry. */
internal fun recolorEmmaFace(source: ImageVector, palette: EmmaPalette): ImageVector {
    fun recolor(brush: Brush?): Brush? {
        if (brush !is SolidColor) return brush
        val color = when (brush.value) {
            Color(0xFFFBFCFF) -> palette.face
            Color(0xFFFFF8EE) -> lerp(palette.face, Color.White, 0.28f)
            Color(0xFF513934) -> palette.dark
            Color(0xFFF6B4C1), Color(0xFFF07EAC) -> palette.blush
            Color(0xFFF58EA4) -> palette.tongue
            Color(0xFFBDEAFF), Color(0xFFBFEAFF), Color(0xFFD9F0FF), Color(0xFFDDF4FF) ->
                lerp(palette.accent, Color.White, 0.65f)
            Color(0xFF52ADF2), Color(0xFF5CB7F5), Color(0xFF62BDF5), Color(0xFF64BDF7),
            Color(0xFF70C8FF), Color(0xFF74C8F8), Color(0xFF75C8FA) -> palette.accent
            // Keep the eye glints and the translucent artwork highlight white.
            else -> brush.value
        }
        return SolidColor(color)
    }

    val builder = ImageVector.Builder(
        name = source.name,
        defaultWidth = source.defaultWidth,
        defaultHeight = source.defaultHeight,
        viewportWidth = source.viewportWidth,
        viewportHeight = source.viewportHeight,
        autoMirror = source.autoMirror,
    )
    fun append(group: VectorGroup) {
        builder.addGroup(
            name = group.name, rotate = group.rotation,
            pivotX = group.pivotX, pivotY = group.pivotY,
            scaleX = group.scaleX, scaleY = group.scaleY,
            translationX = group.translationX, translationY = group.translationY,
            clipPathData = group.clipPathData,
        )
        for (node in group) {
            when (node) {
                is VectorGroup -> append(node)
                is VectorPath -> {
                    val originalColor = (node.fill as? SolidColor)?.value
                    val shell = originalColor == Color(0xFFFBFCFF)
                    val facePanel = originalColor == Color(0xFFFFF8EE)
                    val outline = shell || facePanel
                    builder.addPath(
                        pathData = node.pathData, pathFillType = node.pathFillType,
                        name = node.name, fill = recolor(node.fill), fillAlpha = node.fillAlpha,
                        stroke = if (outline) SolidColor(palette.dark) else recolor(node.stroke),
                        strokeAlpha = if (shell) 0.65f else if (facePanel) 0.30f else node.strokeAlpha,
                        strokeLineWidth = if (shell) 8f else if (facePanel) 4f else node.strokeLineWidth,
                        strokeLineCap = node.strokeLineCap, strokeLineJoin = node.strokeLineJoin,
                        strokeLineMiter = node.strokeLineMiter,
                        trimPathStart = node.trimPathStart, trimPathEnd = node.trimPathEnd,
                        trimPathOffset = node.trimPathOffset,
                    )
                }
            }
        }
        builder.clearGroup()
    }
    append(source.root)
    return builder.build()
}
