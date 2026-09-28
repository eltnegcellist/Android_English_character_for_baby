package com.eltnegcellist.emma.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import com.eltnegcellist.emma.R

/**
 * Face-first live avatar.
 *
 * The white-and-blue robot is deliberately cropped around its head because a
 * large face is one of Mitsukotoba's baby-facing design principles. Listening
 * rings stay outside the crop, while the eyes and mouth remain live overlays.
 *
 * Note: the current legacy resource filenames are swapped:
 * mitsukotoba_parent contains the robot illustration.
 */
@Composable
internal fun CompactEmmaAvatar(
    state: EmmaVisualState,
    mouthLevel: Float,
    modifier: Modifier = Modifier,
) {
    val transition = rememberInfiniteTransition(label = "mitsukotoba-face-avatar")

    val bob by transition.animateFloat(
        initialValue = -4f,
        targetValue = 4f,
        animationSpec = infiniteRepeatable(tween(1900), repeatMode = RepeatMode.Reverse),
        label = "face-bob",
    )
    val pulse by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(950), repeatMode = RepeatMode.Reverse),
        label = "listening-pulse",
    )
    val gaze by transition.animateFloat(
        initialValue = -1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2800), repeatMode = RepeatMode.Reverse),
        label = "gentle-gaze",
    )
    val blink by transition.animateFloat(
        initialValue = 0f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 4300
                0f at 0
                0f at 3500
                1f at 3650
                0f at 3820
                0f at 4300
            },
            repeatMode = RepeatMode.Restart,
        ),
        label = "natural-blink",
    )

    val mouthTarget = when {
        state != EmmaVisualState.SPEAKING -> 0f
        mouthLevel < 0.16f -> 0.18f
        mouthLevel < 0.48f -> 0.58f
        else -> 1f
    }
    val mouth by animateFloatAsState(
        targetValue = mouthTarget,
        animationSpec = tween(75),
        label = "robot-mouth",
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(1.14f),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            if (state == EmmaVisualState.LISTENING || state == EmmaVisualState.ENDPOINT_WAIT) {
                val radius = size.minDimension * (0.43f + pulse * 0.018f)
                drawCircle(
                    color = Color(0xFF4CC9F0).copy(alpha = 0.10f + pulse * 0.08f),
                    radius = radius,
                    center = center,
                    style = Stroke(width = size.minDimension * 0.018f),
                )
                drawCircle(
                    color = Color(0xFF35D3A7).copy(alpha = 0.08f + pulse * 0.05f),
                    radius = radius * 1.06f,
                    center = center,
                    style = Stroke(width = size.minDimension * 0.010f),
                )
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    translationY = bob
                    transformOrigin = TransformOrigin(0.5f, 0.30f)
                    val speakingScale =
                        if (state == EmmaVisualState.SPEAKING) 1f + mouth * 0.010f else 1f
                    scaleX = 1.72f * speakingScale
                    scaleY = 1.72f * speakingScale
                    clip = true
                },
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painter = painterResource(R.drawable.mitsukotoba_parent),
                contentDescription = "AIキャラクター",
                modifier = Modifier.fillMaxSize(),
            )

            Canvas(Modifier.fillMaxSize()) {
                val face = Color(0xFFFBF6EF)
                val dark = Color(0xFF5A4646)
                val tongue = Color(0xFFF58A9B)

                val eyeY = size.height * 0.405f
                val leftEyeX = size.width * 0.421f
                val rightEyeX = size.width * 0.632f
                val coverW = size.width * 0.080f
                val coverH = size.height * 0.100f

                // Cover the source eyes so gaze and blink can be animated.
                listOf(leftEyeX, rightEyeX).forEach { x ->
                    drawOval(
                        color = face,
                        topLeft = Offset(x - coverW / 2f, eyeY - coverH / 2f),
                        size = Size(coverW, coverH),
                    )
                }

                val gazeX = gaze * size.width * 0.0065f
                val gazeY = gaze * size.height * 0.0018f
                val eyeW = size.width * 0.031f
                val openH = size.height * 0.056f
                val eyeH = (openH * (1f - blink * 0.88f)).coerceAtLeast(size.height * 0.007f)

                if (state == EmmaVisualState.UNDERSTOOD) {
                    fun happyEye(x: Float) {
                        val p = Path().apply {
                            moveTo(x - size.width * 0.028f, eyeY)
                            quadraticTo(
                                x,
                                eyeY - size.height * 0.024f,
                                x + size.width * 0.028f,
                                eyeY,
                            )
                        }
                        drawPath(
                            path = p,
                            color = dark,
                            style = Stroke(width = size.width * 0.008f),
                        )
                    }
                    happyEye(leftEyeX)
                    happyEye(rightEyeX)
                } else {
                    listOf(leftEyeX, rightEyeX).forEach { x ->
                        drawOval(
                            color = dark,
                            topLeft = Offset(
                                x + gazeX - eyeW / 2f,
                                eyeY + gazeY - eyeH / 2f,
                            ),
                            size = Size(eyeW, eyeH),
                        )
                    }
                }

                // Cover the source smile and redraw it, even when idle, so the
                // mouth remains visually consistent at this larger face scale.
                val cx = size.width * 0.507f
                val cy = size.height * 0.453f
                drawOval(
                    color = face,
                    topLeft = Offset(
                        cx - size.width * 0.071f,
                        cy - size.height * 0.040f,
                    ),
                    size = Size(
                        size.width * 0.142f,
                        size.height * 0.080f,
                    ),
                )

                if (state != EmmaVisualState.SPEAKING || mouth < 0.28f) {
                    drawArc(
                        color = dark,
                        startAngle = 12f,
                        sweepAngle = 156f,
                        useCenter = false,
                        topLeft = Offset(
                            cx - size.width * 0.043f,
                            cy - size.height * 0.012f,
                        ),
                        size = Size(
                            size.width * 0.086f,
                            size.height * 0.035f,
                        ),
                        style = Stroke(width = size.width * 0.008f),
                    )
                } else {
                    val w = size.width * (0.085f + mouth * 0.030f)
                    val h = size.height * (0.030f + mouth * 0.045f)
                    drawOval(
                        color = dark,
                        topLeft = Offset(cx - w / 2f, cy - h / 2f),
                        size = Size(w, h),
                    )
                    if (mouth > 0.55f) {
                        drawOval(
                            color = tongue,
                            topLeft = Offset(cx - w * 0.30f, cy + h * 0.12f),
                            size = Size(w * 0.60f, h * 0.30f),
                        )
                    }
                }
            }
        }
    }
}
