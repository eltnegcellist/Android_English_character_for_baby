package com.eltnegcellist.emma.ui

import android.content.Context
import android.os.SystemClock
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.eltnegcellist.emma.ai.AudienceMode
import kotlinx.coroutines.delay
import kotlin.math.max

internal enum class EmmaVisualState {
    IDLE,
    LISTENING,
    ENDPOINT_WAIT,
    UNDERSTOOD,
    THINKING,
    SPEAKING,
    ERROR,
}

@Composable
internal fun EmmaAvatar(
    state: EmmaVisualState,
    mouthLevel: Float,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val preferences = remember { context.getSharedPreferences("emma_speech", Context.MODE_PRIVATE) }
    var audienceMode by remember {
        mutableStateOf(AudienceMode.fromSaved(preferences.getString("audience_mode", null)))
    }
    var colorMode by remember {
        mutableStateOf(EmmaColorMode.fromSaved(preferences.getString("emma_color_mode", null)))
    }
    var vividPalette by remember {
        mutableStateOf(EmmaVividPalette.fromSaved(preferences.getString("emma_vivid_palette", null)))
    }
    var colorMenuExpanded by remember { mutableStateOf(false) }
    var vividMenuExpanded by remember { mutableStateOf(false) }
    val modeEnabled = state != EmmaVisualState.UNDERSTOOD &&
        state != EmmaVisualState.THINKING &&
        state != EmmaVisualState.SPEAKING

    // Keep a clear acknowledgement smile on screen long enough for a baby to register it,
    // while the real processing state is free to move on to THINKING immediately.
    var displayedState by remember { mutableStateOf(state) }
    var smileHoldUntil by remember { mutableLongStateOf(0L) }
    LaunchedEffect(state) {
        val now = SystemClock.uptimeMillis()
        if (state == EmmaVisualState.UNDERSTOOD) {
            smileHoldUntil = now + 1_050L
            displayedState = EmmaVisualState.UNDERSTOOD
        } else {
            val remaining = smileHoldUntil - now
            if (displayedState == EmmaVisualState.UNDERSTOOD && remaining > 0L) {
                delay(remaining)
            }
            displayedState = state
        }
    }

    val transition = rememberInfiniteTransition(label = "emma-avatar")
    val bob by transition.animateFloat(
        initialValue = -0.012f,
        targetValue = 0.012f,
        animationSpec = infiniteRepeatable(tween(1800), repeatMode = RepeatMode.Reverse),
        label = "bob",
    )
    val blink by transition.animateFloat(
        initialValue = 1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 4600
                1f at 0
                1f at 3820
                0.08f at 3950
                1f at 4080
                1f at 4600
            },
            repeatMode = RepeatMode.Restart,
        ),
        label = "blink",
    )
    val thinkingDot by transition.animateFloat(
        initialValue = 0.2f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(720), repeatMode = RepeatMode.Reverse),
        label = "thinking",
    )
    val pulse by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1000), repeatMode = RepeatMode.Reverse),
        label = "listening-pulse",
    )
    val colorHue by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 120_000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "time-color-shift",
    )
    val nodAmount by animateFloatAsState(
        targetValue = if (displayedState == EmmaVisualState.UNDERSTOOD) 0.055f else 0f,
        animationSpec = tween(170),
        label = "understood-nod",
    )
    val targetMouth = when {
        state != EmmaVisualState.SPEAKING -> 0f
        mouthLevel < 0.16f -> 0f
        mouthLevel < 0.48f -> 0.55f
        else -> 1f
    }
    val smoothMouth by animateFloatAsState(
        targetValue = targetMouth,
        animationSpec = tween(80),
        label = "mouth-smoothing",
    )

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text("Emmaは誰に話す？", style = MaterialTheme.typography.titleSmall)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
        ) {
            if (audienceMode == AudienceMode.BABY) {
                Button(onClick = {}, enabled = modeEnabled) { Text("赤ちゃんへ") }
            } else {
                OutlinedButton(
                    onClick = {
                        audienceMode = AudienceMode.BABY
                        preferences.edit().putString("audience_mode", AudienceMode.BABY.name).apply()
                    },
                    enabled = modeEnabled,
                ) { Text("赤ちゃんへ") }
            }

            if (audienceMode == AudienceMode.PARENT) {
                Button(onClick = {}, enabled = modeEnabled) { Text("親へ") }
            } else {
                OutlinedButton(
                    onClick = {
                        audienceMode = AudienceMode.PARENT
                        preferences.edit().putString("audience_mode", AudienceMode.PARENT.name).apply()
                    },
                    enabled = modeEnabled,
                ) { Text("親へ") }
            }
        }
        Text(audienceMode.description, style = MaterialTheme.typography.bodySmall)

        Box(
            modifier = Modifier.fillMaxWidth().aspectRatio(1f),
            contentAlignment = Alignment.Center,
        ) {
            Canvas(Modifier.fillMaxSize()) {
                val cx = size.width / 2f
                val baseCy = size.height / 2f
                val radius = size.minDimension * 0.445f
                val cy = baseCy + radius * (bob + nodAmount)
                val renderState = displayedState
                val palette = EmmaColors.palette(colorMode, vividPalette, colorHue)

                val face = if (colorMode == EmmaColorMode.SOFT && renderState == EmmaVisualState.ERROR) {
                    Color(0xFFFFE8EC)
                } else {
                    palette.face
                }
                val accent = palette.accent
                val dark = palette.dark
                val blush = palette.blush
                val mouth = palette.mouth
                val tongue = palette.tongue

                if (renderState == EmmaVisualState.LISTENING || renderState == EmmaVisualState.ENDPOINT_WAIT) {
                    drawCircle(
                        accent.copy(alpha = 0.10f + pulse * 0.06f),
                        radius * (1.03f + pulse * 0.025f),
                        Offset(cx, cy),
                        style = Stroke(width = radius * 0.035f),
                    )
                    drawCircle(
                        accent.copy(alpha = 0.05f + pulse * 0.04f),
                        radius * (1.09f + pulse * 0.025f),
                        Offset(cx, cy),
                        style = Stroke(width = radius * 0.022f),
                    )
                }

                drawCircle(face, radius, Offset(cx, cy))
                drawCircle(
                    color = if (colorMode == EmmaColorMode.SOFT) dark.copy(alpha = 0.10f) else dark.copy(alpha = 0.88f),
                    radius = radius,
                    center = Offset(cx, cy),
                    style = Stroke(width = radius * if (colorMode == EmmaColorMode.SOFT) 0.014f else 0.026f),
                )

                // A contrasting top accent makes each palette visibly multi-colour rather than a flat face fill.
                drawArc(
                    color = accent.copy(alpha = if (colorMode == EmmaColorMode.SOFT) 0.46f else 0.92f),
                    startAngle = 205f,
                    sweepAngle = 130f,
                    useCenter = false,
                    topLeft = Offset(cx - radius * 0.84f, cy - radius * 1.02f),
                    size = Size(radius * 1.68f, radius * 0.82f),
                    style = Stroke(width = radius * if (colorMode == EmmaColorMode.SOFT) 0.07f else 0.085f),
                )

                val eyeY = cy - radius * 0.18f
                val eyeX = radius * 0.36f
                val eyeWidth = radius * 0.50f
                val eyeHeight = radius * 0.58f
                val eyeScale = when (renderState) {
                    EmmaVisualState.LISTENING -> 1.06f
                    EmmaVisualState.ENDPOINT_WAIT -> 1.03f
                    EmmaVisualState.ERROR -> 0.90f
                    else -> blink
                }

                if (renderState == EmmaVisualState.UNDERSTOOD) {
                    drawHappyEye(cx - eyeX, eyeY, eyeWidth, radius, dark)
                    drawHappyEye(cx + eyeX, eyeY, eyeWidth, radius, dark)
                } else {
                    val pupilShift = when (renderState) {
                        EmmaVisualState.THINKING -> Offset(radius * 0.045f, -radius * 0.040f)
                        EmmaVisualState.ENDPOINT_WAIT -> Offset(0f, radius * 0.020f)
                        else -> Offset.Zero
                    }
                    drawOpenEye(cx - eyeX, eyeY, eyeWidth, eyeHeight, eyeScale, dark, pupilShift)
                    drawOpenEye(cx + eyeX, eyeY, eyeWidth, eyeHeight, eyeScale, dark, pupilShift)
                }

                val browY = eyeY - eyeHeight * 0.63f
                val browHalf = eyeWidth * 0.34f
                val browStroke = radius * 0.027f
                when (renderState) {
                    EmmaVisualState.LISTENING -> {
                        drawLine(dark, Offset(cx - eyeX - browHalf, browY + radius * 0.015f), Offset(cx - eyeX + browHalf, browY - radius * 0.015f), strokeWidth = browStroke)
                        drawLine(dark, Offset(cx + eyeX - browHalf, browY - radius * 0.015f), Offset(cx + eyeX + browHalf, browY + radius * 0.015f), strokeWidth = browStroke)
                    }
                    EmmaVisualState.THINKING -> {
                        drawLine(dark, Offset(cx - eyeX - browHalf, browY), Offset(cx - eyeX + browHalf, browY + radius * 0.035f), strokeWidth = browStroke)
                        drawLine(dark, Offset(cx + eyeX - browHalf, browY + radius * 0.035f), Offset(cx + eyeX + browHalf, browY), strokeWidth = browStroke)
                    }
                    EmmaVisualState.ERROR -> {
                        drawLine(dark, Offset(cx - eyeX - browHalf, browY + radius * 0.045f), Offset(cx - eyeX + browHalf, browY), strokeWidth = browStroke)
                        drawLine(dark, Offset(cx + eyeX - browHalf, browY), Offset(cx + eyeX + browHalf, browY + radius * 0.045f), strokeWidth = browStroke)
                    }
                    else -> Unit
                }

                // Cheeks keep their own contrasting colour. Vivid modes never fade them into the face colour.
                val stateCheekAlpha = when {
                    renderState == EmmaVisualState.UNDERSTOOD -> 0.62f
                    state == EmmaVisualState.SPEAKING -> 0.40f
                    renderState == EmmaVisualState.THINKING -> 0.30f
                    renderState == EmmaVisualState.LISTENING -> 0.28f
                    renderState == EmmaVisualState.ERROR -> 0.16f
                    else -> 0.22f
                }
                val cheekAlpha = if (colorMode == EmmaColorMode.SOFT) stateCheekAlpha else max(stateCheekAlpha, 0.58f)
                val cheekRadius = radius * 0.145f
                val cheekY = cy + radius * 0.18f
                drawCircle(blush.copy(alpha = cheekAlpha), cheekRadius, Offset(cx - radius * 0.60f, cheekY))
                drawCircle(blush.copy(alpha = cheekAlpha), cheekRadius, Offset(cx + radius * 0.60f, cheekY))

                // Mouth movement follows real speech even if the acknowledgement smile is still being held.
                if (state == EmmaVisualState.SPEAKING) {
                    if (smoothMouth < 0.05f) {
                        drawSmile(cx, cy, radius, dark, widthScale = 0.98f, depthScale = 0.30f)
                    } else {
                        val mouthWidth = radius * (0.60f + smoothMouth * 0.20f)
                        val mouthHeight = radius * (0.15f + smoothMouth * 0.40f)
                        drawOval(
                            color = mouth,
                            topLeft = Offset(cx - mouthWidth / 2f, cy + radius * 0.28f - mouthHeight * 0.18f),
                            size = Size(mouthWidth, mouthHeight),
                        )
                        if (smoothMouth > 0.48f) {
                            drawOval(
                                color = tongue,
                                topLeft = Offset(cx - mouthWidth * 0.30f, cy + radius * 0.28f + mouthHeight * 0.40f),
                                size = Size(mouthWidth * 0.60f, mouthHeight * 0.23f),
                            )
                        }
                    }
                } else {
                    when (renderState) {
                        EmmaVisualState.ERROR -> {
                            val path = Path().apply {
                                moveTo(cx - radius * 0.30f, cy + radius * 0.46f)
                                quadraticTo(cx, cy + radius * 0.25f, cx + radius * 0.30f, cy + radius * 0.46f)
                            }
                            drawPath(path, dark, style = Stroke(width = radius * 0.045f))
                        }
                        EmmaVisualState.ENDPOINT_WAIT -> {
                            drawOval(
                                color = dark,
                                topLeft = Offset(cx - radius * 0.16f, cy + radius * 0.27f),
                                size = Size(radius * 0.32f, radius * 0.27f),
                                style = Stroke(width = radius * 0.040f),
                            )
                        }
                        EmmaVisualState.UNDERSTOOD -> drawOpenHappyMouth(cx, cy, radius, mouth, tongue)
                        EmmaVisualState.THINKING -> drawSmile(cx, cy, radius, dark, widthScale = 0.82f, depthScale = 0.24f)
                        EmmaVisualState.LISTENING -> drawSmile(cx, cy, radius, dark, widthScale = 0.88f, depthScale = 0.26f)
                        EmmaVisualState.IDLE -> drawSmile(cx, cy, radius, dark, widthScale = 0.94f, depthScale = 0.28f)
                        EmmaVisualState.SPEAKING -> Unit
                    }
                }

                if (renderState == EmmaVisualState.THINKING) {
                    val baseX = cx + radius * 0.72f
                    val baseY = cy - radius * 0.69f
                    repeat(3) { index ->
                        val alpha = (0.25f + thinkingDot * (0.22f + index * 0.12f)).coerceAtMost(1f)
                        val dotRadius = radius * (0.035f + index * 0.010f)
                        drawCircle(
                            accent.copy(alpha = alpha),
                            dotRadius,
                            Offset(baseX + radius * 0.12f * index, baseY - radius * 0.055f * index),
                        )
                    }
                }
            }
        }

        Text("Emmaの色", style = MaterialTheme.typography.titleSmall)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
        ) {
            Box {
                OutlinedButton(onClick = { colorMenuExpanded = true }) {
                    Text(colorMode.label)
                }
                DropdownMenu(
                    expanded = colorMenuExpanded,
                    onDismissRequest = { colorMenuExpanded = false },
                ) {
                    EmmaColorMode.entries.forEach { option ->
                        DropdownMenuItem(
                            text = { Text(option.label) },
                            onClick = {
                                colorMode = option
                                preferences.edit().putString("emma_color_mode", option.savedValue).apply()
                                colorMenuExpanded = false
                            },
                        )
                    }
                }
            }

            if (colorMode == EmmaColorMode.VIVID) {
                Box {
                    OutlinedButton(onClick = { vividMenuExpanded = true }) {
                        Text(vividPalette.label)
                    }
                    DropdownMenu(
                        expanded = vividMenuExpanded,
                        onDismissRequest = { vividMenuExpanded = false },
                    ) {
                        EmmaVividPalette.entries.forEach { option ->
                            DropdownMenuItem(
                                text = { Text(option.label) },
                                onClick = {
                                    vividPalette = option
                                    preferences.edit().putString("emma_vivid_palette", option.savedValue).apply()
                                    vividMenuExpanded = false
                                },
                            )
                        }
                    }
                }
            }
        }
        Text(colorMode.description, style = MaterialTheme.typography.bodySmall)

    }
}

private fun DrawScope.drawOpenEye(
    centerX: Float,
    centerY: Float,
    eyeWidth: Float,
    eyeHeight: Float,
    scale: Float,
    dark: Color,
    pupilShift: Offset,
) {
    val height = (eyeHeight * scale).coerceAtLeast(3f)
    drawOval(
        color = Color.White.copy(alpha = 0.96f),
        topLeft = Offset(centerX - eyeWidth / 2f, centerY - height / 2f),
        size = Size(eyeWidth, height),
    )
    drawOval(
        color = dark.copy(alpha = 0.94f),
        topLeft = Offset(centerX - eyeWidth / 2f, centerY - height / 2f),
        size = Size(eyeWidth, height),
        style = Stroke(width = eyeWidth * 0.055f),
    )
    if (scale > 0.18f) {
        val pupilRadius = eyeWidth * 0.22f
        val pupilY = centerY + pupilShift.y
        val pupilX = centerX + pupilShift.x
        drawCircle(dark, pupilRadius, Offset(pupilX, pupilY))
        drawCircle(
            Color.White.copy(alpha = 0.94f),
            pupilRadius * 0.33f,
            Offset(pupilX - pupilRadius * 0.32f, pupilY - pupilRadius * 0.34f),
        )
    }
}

private fun DrawScope.drawHappyEye(
    centerX: Float,
    centerY: Float,
    eyeWidth: Float,
    radius: Float,
    dark: Color,
) {
    val path = Path().apply {
        moveTo(centerX - eyeWidth * 0.48f, centerY + radius * 0.035f)
        quadraticTo(centerX, centerY - radius * 0.16f, centerX + eyeWidth * 0.48f, centerY + radius * 0.035f)
    }
    drawPath(path, dark, style = Stroke(width = radius * 0.050f))
}

private fun DrawScope.drawSmile(
    cx: Float,
    cy: Float,
    radius: Float,
    dark: Color,
    widthScale: Float,
    depthScale: Float,
) {
    val width = radius * widthScale
    val height = radius * depthScale
    drawArc(
        color = dark,
        startAngle = 14f,
        sweepAngle = 152f,
        useCenter = false,
        topLeft = Offset(cx - width / 2f, cy + radius * 0.23f),
        size = Size(width, height),
        style = Stroke(width = radius * 0.045f),
    )
}

private fun DrawScope.drawOpenHappyMouth(
    cx: Float,
    cy: Float,
    radius: Float,
    mouth: Color,
    tongue: Color,
) {
    val width = radius * 0.92f
    val height = radius * 0.43f
    drawOval(
        color = mouth,
        topLeft = Offset(cx - width / 2f, cy + radius * 0.23f),
        size = Size(width, height),
    )
    drawOval(
        color = tongue,
        topLeft = Offset(cx - width * 0.30f, cy + radius * 0.47f),
        size = Size(width * 0.60f, height * 0.24f),
    )
}
