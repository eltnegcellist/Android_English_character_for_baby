package com.eltnegcellist.emma.ui

import android.content.Context
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import kotlin.math.max

@Composable
internal fun CompactEmmaAvatar(
    state: EmmaVisualState,
    mouthLevel: Float,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val preferences = remember { context.getSharedPreferences("emma_speech", Context.MODE_PRIVATE) }
    val colorMode = remember {
        EmmaColorMode.fromSaved(preferences.getString("emma_color_mode", null))
    }
    val vividPalette = remember {
        EmmaVividPalette.fromSaved(preferences.getString("emma_vivid_palette", null))
    }

    val transition = rememberInfiniteTransition(label = "compact-emma")
    val bob by transition.animateFloat(
        initialValue = -0.010f,
        targetValue = 0.010f,
        animationSpec = infiniteRepeatable(tween(1900), repeatMode = RepeatMode.Reverse),
        label = "bob",
    )
    val blink by transition.animateFloat(
        initialValue = 1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            keyframes {
                durationMillis = 4700
                1f at 0
                1f at 3980
                0.08f at 4100
                1f at 4230
                1f at 4700
            },
            repeatMode = RepeatMode.Restart,
        ),
        label = "blink",
    )
    val pulse by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1000), repeatMode = RepeatMode.Reverse),
        label = "pulse",
    )
    val colorHue by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 30_000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "time-color-shift",
    )
    val mouthTarget = when {
        state != EmmaVisualState.SPEAKING -> 0f
        mouthLevel < 0.16f -> 0f
        mouthLevel < 0.48f -> 0.55f
        else -> 1f
    }
    val mouth by animateFloatAsState(mouthTarget, tween(80), label = "mouth")

    Box(
        modifier = modifier.fillMaxWidth().aspectRatio(1f),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxWidth().aspectRatio(1f)) {
            val palette = EmmaColors.palette(colorMode, vividPalette, colorHue)
            val cx = size.width / 2f
            // Keep only a very small safety margin so Emma is the visual focus of the home screen.
            // The listening ring still fits inside the square at its largest pulse.
            val radius = size.minDimension * 0.475f
            val cy = size.height / 2f + radius * bob
            val face = if (state == EmmaVisualState.ERROR && colorMode == EmmaColorMode.SOFT) {
                Color(0xFFFFE8EC)
            } else {
                palette.face
            }

            if (state == EmmaVisualState.LISTENING || state == EmmaVisualState.ENDPOINT_WAIT) {
                drawCircle(
                    palette.accent.copy(alpha = 0.08f + pulse * 0.08f),
                    radius * (1.02f + pulse * 0.018f),
                    Offset(cx, cy),
                    style = Stroke(width = radius * 0.032f),
                )
            }

            drawCircle(face, radius, Offset(cx, cy))
            drawCircle(
                palette.dark.copy(alpha = if (colorMode == EmmaColorMode.SOFT) 0.10f else 0.82f),
                radius,
                Offset(cx, cy),
                style = Stroke(width = radius * 0.02f),
            )
            drawArc(
                color = palette.accent.copy(alpha = if (colorMode == EmmaColorMode.SOFT) 0.42f else 0.9f),
                startAngle = 210f,
                sweepAngle = 120f,
                useCenter = false,
                topLeft = Offset(cx - radius * 0.82f, cy - radius * 1.01f),
                size = Size(radius * 1.64f, radius * 0.78f),
                style = Stroke(width = radius * 0.07f),
            )

            val eyeY = cy - radius * 0.17f
            val eyeX = radius * 0.34f
            val eyeW = radius * 0.47f
            val eyeH = radius * 0.54f
            val eyeScale = when (state) {
                EmmaVisualState.LISTENING -> 1.05f
                EmmaVisualState.ENDPOINT_WAIT -> 1.02f
                EmmaVisualState.ERROR -> 0.88f
                else -> blink
            }

            if (state == EmmaVisualState.UNDERSTOOD) {
                fun happyEye(x: Float) {
                    val p = Path().apply {
                        moveTo(x - eyeW * 0.46f, eyeY + radius * 0.03f)
                        quadraticTo(x, eyeY - radius * 0.15f, x + eyeW * 0.46f, eyeY + radius * 0.03f)
                    }
                    drawPath(p, palette.dark, style = Stroke(width = radius * 0.048f))
                }
                happyEye(cx - eyeX)
                happyEye(cx + eyeX)
            } else {
                fun eye(x: Float) {
                    val h = max(3f, eyeH * eyeScale)
                    drawOval(
                        Color.White.copy(alpha = 0.96f),
                        Offset(x - eyeW / 2f, eyeY - h / 2f),
                        Size(eyeW, h),
                    )
                    drawOval(
                        palette.dark.copy(alpha = 0.94f),
                        Offset(x - eyeW / 2f, eyeY - h / 2f),
                        Size(eyeW, h),
                        style = Stroke(width = eyeW * 0.055f),
                    )
                    if (eyeScale > 0.18f) {
                        val pupil = eyeW * 0.22f
                        drawCircle(palette.dark, pupil, Offset(x, eyeY))
                        drawCircle(Color.White, pupil * 0.32f, Offset(x - pupil * 0.30f, eyeY - pupil * 0.33f))
                    }
                }
                eye(cx - eyeX)
                eye(cx + eyeX)
            }

            val cheekY = cy + radius * 0.18f
            val cheekAlpha = if (colorMode == EmmaColorMode.SOFT) 0.28f else 0.58f
            drawCircle(palette.blush.copy(alpha = cheekAlpha), radius * 0.14f, Offset(cx - radius * 0.58f, cheekY))
            drawCircle(palette.blush.copy(alpha = cheekAlpha), radius * 0.14f, Offset(cx + radius * 0.58f, cheekY))

            when {
                state == EmmaVisualState.SPEAKING && mouth > 0.05f -> {
                    val w = radius * (0.58f + mouth * 0.20f)
                    val h = radius * (0.15f + mouth * 0.40f)
                    drawOval(palette.mouth, Offset(cx - w / 2f, cy + radius * 0.28f), Size(w, h))
                    if (mouth > 0.48f) {
                        drawOval(
                            palette.tongue,
                            Offset(cx - w * 0.30f, cy + radius * 0.28f + h * 0.56f),
                            Size(w * 0.60f, h * 0.20f),
                        )
                    }
                }
                state == EmmaVisualState.ERROR -> {
                    val p = Path().apply {
                        moveTo(cx - radius * 0.28f, cy + radius * 0.44f)
                        quadraticTo(cx, cy + radius * 0.25f, cx + radius * 0.28f, cy + radius * 0.44f)
                    }
                    drawPath(p, palette.dark, style = Stroke(width = radius * 0.044f))
                }
                else -> {
                    drawArc(
                        palette.dark,
                        startAngle = 12f,
                        sweepAngle = 156f,
                        useCenter = false,
                        topLeft = Offset(cx - radius * 0.45f, cy + radius * 0.22f),
                        size = Size(radius * 0.90f, radius * 0.28f),
                        style = Stroke(width = radius * 0.044f),
                    )
                }
            }
        }
    }
}