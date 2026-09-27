package com.eltnegcellist.emma.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import com.eltnegcellist.emma.R

/**
 * Live conversation avatar based on the same white-and-blue robot mascot used
 * by the Mitsukotoba start screen.
 *
 * Note: the current legacy resource filenames are swapped: mitsukotoba_parent
 * contains the robot illustration and mitsukotoba_ai contains the parent.
 */
@Composable
internal fun CompactEmmaAvatar(
    state: EmmaVisualState,
    mouthLevel: Float,
    modifier: Modifier = Modifier,
) {
    val transition = rememberInfiniteTransition(label = "mitsukotoba-live-robot")
    val bob by transition.animateFloat(
        initialValue = -5f,
        targetValue = 5f,
        animationSpec = infiniteRepeatable(tween(1800), repeatMode = RepeatMode.Reverse),
        label = "robot-bob",
    )
    val pulse by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(950), repeatMode = RepeatMode.Reverse),
        label = "listening-pulse",
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
        modifier = modifier.fillMaxWidth().aspectRatio(1f),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            if (state == EmmaVisualState.LISTENING || state == EmmaVisualState.ENDPOINT_WAIT) {
                val radius = size.minDimension * (0.41f + pulse * 0.018f)
                drawCircle(
                    color = Color(0xFF4CC9F0).copy(alpha = 0.10f + pulse * 0.08f),
                    radius = radius,
                    center = center,
                    style = Stroke(width = size.minDimension * 0.018f),
                )
                drawCircle(
                    color = Color(0xFF35D3A7).copy(alpha = 0.08f + pulse * 0.05f),
                    radius = radius * 1.07f,
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
                    val speakingScale =
                        if (state == EmmaVisualState.SPEAKING) 1f + mouth * 0.012f else 1f
                    scaleX = speakingScale
                    scaleY = speakingScale
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

                // Cover the mascot's static smile only while speaking, then draw
                // a PCM-linked mouth in the same face region.
                if (state == EmmaVisualState.SPEAKING) {
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

                    if (mouth < 0.28f) {
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

                // Keep the acknowledgement expression from the original live avatar.
                if (state == EmmaVisualState.UNDERSTOOD) {
                    val eyeY = size.height * 0.41f
                    val eyeW = size.width * 0.055f

                    fun happyEye(x: Float) {
                        val p = Path().apply {
                            moveTo(x - eyeW / 2f, eyeY)
                            quadraticTo(
                                x,
                                eyeY - size.height * 0.025f,
                                x + eyeW / 2f,
                                eyeY,
                            )
                        }
                        drawPath(
                            path = p,
                            color = dark,
                            style = Stroke(width = size.width * 0.009f),
                        )
                    }

                    happyEye(size.width * 0.421f)
                    happyEye(size.width * 0.632f)
                }
            }
        }
    }
}
