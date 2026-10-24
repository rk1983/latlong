package no.madival.latlong.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import kotlinx.coroutines.launch
import no.madival.latlong.data.SamplePoint

private val OceanColor = Color(0xFF0F172A) // Deep slate navy
private val LandColor = Color(0xFF334155)  // Neutral slate
private val BorderColor = Color(0xFF1E293B) // Dark border stroke
private val CorrectColor = Color(0xFF22C55E) // Emerald green
private val CorrectBorderColor = Color(0xFF15803D)
private val WrongColor = Color(0xFFEF4444)   // High contrast red
private val WrongBorderColor = Color(0xFFB91C1C)

@Composable
fun MapCanvas(
    cachedCountries: List<CachedCountry>,
    targetCoordinate: SamplePoint?,
    selectedCountryCode: String?,
    correctCountryCode: String?,
    isRevealed: Boolean,
    cameraTarget: CameraTarget?,
    modifier: Modifier = Modifier,
    onViewportSizeChanged: (width: Float, height: Float) -> Unit = { _, _ -> }
) {
    val coroutineScope = rememberCoroutineScope()

    val panX = remember { Animatable(0f) }
    val panY = remember { Animatable(0f) }
    val zoom = remember { Animatable(1.2f) }

    // Smoothly animate camera when target changes (e.g. on new question)
    LaunchedEffect(cameraTarget) {
        cameraTarget?.let { target ->
            launch {
                panX.animateTo(
                    target.panX,
                    animationSpec = tween(durationMillis = 750, easing = FastOutSlowInEasing)
                )
            }
            launch {
                panY.animateTo(
                    target.panY,
                    animationSpec = tween(durationMillis = 750, easing = FastOutSlowInEasing)
                )
            }
            launch {
                zoom.animateTo(
                    target.zoom,
                    animationSpec = tween(durationMillis = 750, easing = FastOutSlowInEasing)
                )
            }
        }
    }

    // Pulsing radar animation for mystery pin
    val infiniteTransition = rememberInfiniteTransition()
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 2.4f,
        animationSpec = infiniteRepeatable(
            animation = tween(1300, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        )
    )
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 0.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1300, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        )
    )

    Box(
        modifier = modifier
            .background(OceanColor)
            .onSizeChanged { size ->
                onViewportSizeChanged(size.width.toFloat(), size.height.toFloat())
            }
            .pointerInput(Unit) {
                detectTransformGestures { centroid, gesturePan, gestureZoom, _ ->
                    coroutineScope.launch {
                        val newZoom = (zoom.value * gestureZoom).coerceIn(1.0f, 12.0f)
                        val zoomFactor = newZoom / zoom.value

                        // Zoom around gesture centroid
                        val newPanX = centroid.x - (centroid.x - panX.value) * zoomFactor + gesturePan.x
                        val newPanY = centroid.y - (centroid.y - panY.value) * zoomFactor + gesturePan.y

                        zoom.snapTo(newZoom)
                        panX.snapTo(newPanX)
                        panY.snapTo(newPanY)
                    }
                }
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val currentZoom = zoom.value
            val currentPanX = panX.value
            val currentPanY = panY.value

            // 1. Vector Map Geometry (Hardware-Accelerated Skia Matrix Transform)
            withTransform({
                translate(currentPanX, currentPanY)
                scale(currentZoom, currentZoom, pivot = Offset.Zero)
            }) {
                val baseStrokeWidth = 1.2f / currentZoom
                val highlightStrokeWidth = 2.4f / currentZoom

                for (cached in cachedCountries) {
                    val country = cached.country
                    val isCorrect = country.code == correctCountryCode
                    val isSelected = country.code == selectedCountryCode

                    val fillColor = when {
                        isRevealed && isCorrect -> CorrectColor
                        isRevealed && isSelected && !isCorrect -> WrongColor
                        else -> LandColor
                    }

                    val borderColor = when {
                        isRevealed && isCorrect -> CorrectBorderColor
                        isRevealed && isSelected && !isCorrect -> WrongBorderColor
                        else -> BorderColor
                    }

                    val strokeWidth = if (isRevealed && (isCorrect || isSelected)) {
                        highlightStrokeWidth
                    } else {
                        baseStrokeWidth
                    }

                    for (path in cached.paths) {
                        drawPath(
                            path = path,
                            color = fillColor,
                            style = Fill
                        )
                        drawPath(
                            path = path,
                            color = borderColor,
                            style = Stroke(width = strokeWidth)
                        )
                    }
                }
            }

            // 2. Target Mystery Pin (Drawn in Screen-Space to ensure consistent pixel size)
            targetCoordinate?.let { coord ->
                val basePt = MapProjection.project(coord.lon, coord.lat)
                val screenX = (basePt.x * currentZoom) + currentPanX
                val screenY = (basePt.y * currentZoom) + currentPanY

                // Cull if outside viewport
                if (screenX in -60f..(size.width + 60f) && screenY in -60f..(size.height + 60f)) {
                    val pinCenter = Offset(screenX, screenY)

                    // Outer pulsing radar wave
                    drawCircle(
                        color = WrongColor.copy(alpha = pulseAlpha),
                        radius = 14f * pulseScale,
                        center = pinCenter
                    )

                    // Soft glow halo
                    drawCircle(
                        color = WrongColor.copy(alpha = 0.35f),
                        radius = 12f,
                        center = pinCenter
                    )

                    // Pin border ring
                    drawCircle(
                        color = Color.White,
                        radius = 6.5f,
                        center = pinCenter
                    )

                    // High-contrast center core
                    drawCircle(
                        color = WrongColor,
                        radius = 5f,
                        center = pinCenter
                    )
                }
            }
        }
    }
}
