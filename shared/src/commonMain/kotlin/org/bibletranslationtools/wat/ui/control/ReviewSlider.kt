package org.bibletranslationtools.wat.ui.control

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/**
 * Position in the word list, and a handle to move it with.
 *
 * The thumb only goes as far as [reachable] — the last word the reader has
 * unlocked. The track shows all three stretches: covered up to the thumb, still
 * reachable beyond it, and the rest of the list.
 */
@Composable
fun ReviewSlider(
    position: Int,
    reachable: Int,
    total: Int,
    onSeek: (Int) -> Unit,
    enabled: Boolean = true,
    modifier: Modifier = Modifier
) {
    val coveredColor = MaterialTheme.colorScheme.primary
    val reachableColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
    val restColor = MaterialTheme.colorScheme.outline

    val lastIndex = (total - 1).coerceAtLeast(1)
    val positionFraction = (position.toFloat() / lastIndex).coerceIn(0f, 1f)
    val reachableFraction = (reachable.toFloat() / lastIndex).coerceIn(0f, 1f)

    val density = LocalDensity.current

    BoxWithConstraints(modifier = modifier.height(THUMB_RADIUS * 3)) {
        val thumbRadius = with(density) { THUMB_RADIUS.toPx() }
        val trackHeight = with(density) { TRACK_HEIGHT.toPx() }
        // The thumb is centred on the ends of the track, so the track stops short
        // of the edges by its radius.
        val trackWidth = (with(density) { maxWidth.toPx() } - thumbRadius * 2)
            .coerceAtLeast(1f)

        fun indexAt(x: Float): Int {
            val fraction = ((x - thumbRadius) / trackWidth).coerceIn(0f, 1f)
            return (fraction * lastIndex).roundToInt().coerceIn(0, reachable)
        }

        Canvas(
            modifier = Modifier.fillMaxSize()
                .pointerInput(enabled, lastIndex, reachable) {
                    if (!enabled) return@pointerInput
                    detectTapGestures { offset -> onSeek(indexAt(offset.x)) }
                }
                .pointerInput(enabled, lastIndex, reachable) {
                    if (!enabled) return@pointerInput
                    detectHorizontalDragGestures { change, _ ->
                        onSeek(indexAt(change.position.x))
                    }
                }
        ) {
            val centerY = size.height / 2
            val corner = CornerRadius(trackHeight / 2)

            fun stretch(color: Color, fraction: Float) {
                if (fraction <= 0f) return
                drawRoundRect(
                    color = color,
                    topLeft = Offset(thumbRadius, centerY - trackHeight / 2),
                    size = Size(trackWidth * fraction, trackHeight),
                    cornerRadius = corner
                )
            }

            stretch(restColor, 1f)
            stretch(reachableColor, reachableFraction)
            stretch(coveredColor, positionFraction)

            drawCircle(
                color = coveredColor,
                radius = thumbRadius,
                center = Offset(thumbRadius + trackWidth * positionFraction, centerY)
            )
        }
    }
}

private val TRACK_HEIGHT = 6.dp
private val THUMB_RADIUS = 8.dp
