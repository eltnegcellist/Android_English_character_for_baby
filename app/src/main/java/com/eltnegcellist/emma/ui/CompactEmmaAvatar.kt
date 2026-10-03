package com.eltnegcellist.emma.ui

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.animation.core.LinearEasing
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.vectorResource
import com.eltnegcellist.emma.R
import kotlinx.coroutines.delay
import kotlin.random.Random

private enum class BlinkFrame { OPEN, HALF, CLOSED }

/**
 * Emma's live face uses pre-drawn, pixel-aligned expression frames, including
 * dedicated half-blink and closed-eye frames for each speaking mouth size.
 * No eye or mouth is painted over a source image, so facial geometry cannot drift.
 */
@Composable
internal fun CompactEmmaAvatar(
    state: EmmaVisualState,
    mouthLevel: Float,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val preferences = remember(context) { context.getSharedPreferences("emma_speech", Context.MODE_PRIVATE) }
    var colorMode by remember(preferences) {
        mutableStateOf(EmmaColorMode.fromSaved(preferences.getString("emma_color_mode", null)))
    }
    var softPalette by remember {
        mutableStateOf(EmmaSoftPalette.fromSaved(preferences.getString("emma_soft_palette", null)))
    }
    var vividPalette by remember(preferences) {
        mutableStateOf(EmmaVividPalette.fromSaved(preferences.getString("emma_vivid_palette", null)))
    }
    DisposableEffect(preferences) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            when (key) {
                "emma_soft_palette", null -> softPalette = EmmaSoftPalette.fromSaved(preferences.getString("emma_soft_palette", null))
            }
            when (key) {
                "emma_color_mode", null -> colorMode = EmmaColorMode.fromSaved(preferences.getString("emma_color_mode", null))
            }
            when (key) {
                "emma_vivid_palette", null -> vividPalette = EmmaVividPalette.fromSaved(preferences.getString("emma_vivid_palette", null))
            }
        }
        preferences.registerOnSharedPreferenceChangeListener(listener)
        onDispose { preferences.unregisterOnSharedPreferenceChangeListener(listener) }
    }

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

    val hue by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(120_000, easing = LinearEasing)),
        label = "face-color-shift",
    )
    // One-degree steps avoid rebuilding the vector on every animation tick.
    val palette = EmmaColors.palette(colorMode, vividPalette, hue.toInt().toFloat(), softPalette)

    var blinkFrame by remember { mutableStateOf(BlinkFrame.OPEN) }

    LaunchedEffect(Unit) {
        blinkFrame = BlinkFrame.OPEN
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
        state == EmmaVisualState.SPEAKING && mouthLevel >= 0.58f && blinkFrame == BlinkFrame.CLOSED ->
            R.drawable.emma_face_talk_large_closed
        state == EmmaVisualState.SPEAKING && mouthLevel >= 0.58f && blinkFrame == BlinkFrame.HALF ->
            R.drawable.emma_face_talk_large_half
        state == EmmaVisualState.SPEAKING && mouthLevel >= 0.58f ->
            R.drawable.emma_face_talk_large

        state == EmmaVisualState.SPEAKING && mouthLevel >= 0.25f && blinkFrame == BlinkFrame.CLOSED ->
            R.drawable.emma_face_talk_medium_closed
        state == EmmaVisualState.SPEAKING && mouthLevel >= 0.25f && blinkFrame == BlinkFrame.HALF ->
            R.drawable.emma_face_talk_medium_half
        state == EmmaVisualState.SPEAKING && mouthLevel >= 0.25f ->
            R.drawable.emma_face_talk_medium

        state == EmmaVisualState.SPEAKING && blinkFrame == BlinkFrame.CLOSED ->
            R.drawable.emma_face_talk_small_closed
        state == EmmaVisualState.SPEAKING && blinkFrame == BlinkFrame.HALF ->
            R.drawable.emma_face_talk_small_half
        state == EmmaVisualState.SPEAKING ->
            R.drawable.emma_face_talk_small

        state == EmmaVisualState.UNDERSTOOD -> R.drawable.emma_face_idle_closed
        blinkFrame == BlinkFrame.CLOSED -> R.drawable.emma_face_idle_closed
        blinkFrame == BlinkFrame.HALF -> R.drawable.emma_face_idle_half
        else -> R.drawable.emma_face_idle_open
    }

    val source = ImageVector.vectorResource(faceRes)
    val coloredFace = remember(source, palette) { recolorEmmaFace(source, palette) }

    Box(
        modifier = modifier.fillMaxWidth().aspectRatio(1.05f),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            if (state == EmmaVisualState.LISTENING || state == EmmaVisualState.ENDPOINT_WAIT) {
                val radius = size.minDimension * (0.43f + pulse * 0.018f)
                drawCircle(
                    color = palette.accent.copy(alpha = 0.10f + pulse * 0.08f),
                    radius = radius,
                    center = center,
                    style = Stroke(width = size.minDimension * 0.016f),
                )
                drawCircle(
                    color = palette.blush.copy(alpha = 0.08f + pulse * 0.05f),
                    radius = radius * 1.055f,
                    center = center,
                    style = Stroke(width = size.minDimension * 0.009f),
                )
            }
        }

        Image(
            painter = rememberVectorPainter(coloredFace),
            contentDescription = "AIキャラクター",
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { translationY = bob },
        )
    }
}
