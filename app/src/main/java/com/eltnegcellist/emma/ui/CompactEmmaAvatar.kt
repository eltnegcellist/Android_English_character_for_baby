package com.eltnegcellist.emma.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import com.eltnegcellist.emma.R
import kotlinx.coroutines.delay
import kotlin.random.Random

private enum class BlinkFrame { OPEN, HALF, CLOSED }

/**
 * Emma's live face uses six pre-drawn, pixel-aligned expression frames.
 * No eye or mouth is painted over a source image, so facial geometry cannot drift.
 */
@Composable
internal fun CompactEmmaAvatar(
    state: EmmaVisualState,
    mouthLevel: Float,
    modifier: Modifier = Modifier,
) {
    val transition = rememberInfiniteTransition(label = "emma-face")
    val bob by transition.animateFloat(
        initialValue = -2f,
        targetValue = 2f,
        animationSpec = infiniteRepeatable(tween(2100), repeatMode = RepeatMode.Reverse),
        label = "gentle-bob",
    )
    val pulse by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(950), repeatMode = RepeatMode.Reverse),
        label = "listening-pulse",
    )

    var blinkFrame by remember { mutableStateOf(BlinkFrame.OPEN) }

    LaunchedEffect(state) {
        blinkFrame = BlinkFrame.OPEN
        if (state == EmmaVisualState.SPEAKING) return@LaunchedEffect
        while (true) {
            delay(Random.nextLong(2800L, 5000L))
            blinkFrame = BlinkFrame.HALF
            delay(55L)
            blinkFrame = BlinkFrame.CLOSED
            delay(75L)
            blinkFrame = BlinkFrame.HALF
            delay(55L)
            blinkFrame = BlinkFrame.OPEN
        }
    }

    val faceRes = when {
        state == EmmaVisualState.SPEAKING && mouthLevel >= 0.58f -> R.drawable.emma_face_talk_large
        state == EmmaVisualState.SPEAKING && mouthLevel >= 0.25f -> R.drawable.emma_face_talk_medium
        state == EmmaVisualState.SPEAKING -> R.drawable.emma_face_talk_small
        state == EmmaVisualState.UNDERSTOOD -> R.drawable.emma_face_idle_closed
        blinkFrame == BlinkFrame.CLOSED -> R.drawable.emma_face_idle_closed
        blinkFrame == BlinkFrame.HALF -> R.drawable.emma_face_idle_half
        else -> R.drawable.emma_face_idle_open
    }

    Box(
        modifier = modifier.fillMaxWidth().aspectRatio(1.05f),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            if (state == EmmaVisualState.LISTENING || state == EmmaVisualState.ENDPOINT_WAIT) {
                val radius = size.minDimension * (0.43f + pulse * 0.018f)
                drawCircle(
                    color = Color(0xFF4CC9F0).copy(alpha = 0.10f + pulse * 0.08f),
                    radius = radius,
                    center = center,
                    style = Stroke(width = size.minDimension * 0.016f),
                )
                drawCircle(
                    color = Color(0xFF35D3A7).copy(alpha = 0.08f + pulse * 0.05f),
                    radius = radius * 1.055f,
                    center = center,
                    style = Stroke(width = size.minDimension * 0.009f),
                )
            }
        }

        Image(
            painter = painterResource(faceRes),
            contentDescription = "AIキャラクター",
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { translationY = bob },
        )
    }
}
