package com.example.cloudrive.ui.components.music

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Tiny animated "now playing" indicator: a few vertical bars pulsing at staggered phases.
 * Renders as static short bars when [isPlaying] is false (paused, not hidden).
 */
@Composable
fun EqBars(isPlaying: Boolean, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "eqBars")
    val bars = 3
    Row(
        modifier = modifier.height(14.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        repeat(bars) { index ->
            val heightFraction by transition.animateFloat(
                initialValue = 0.3f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = 450 + index * 120, easing = LinearEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "bar$index"
            )
            val fraction = if (isPlaying) heightFraction else 0.3f
            androidx.compose.foundation.layout.Box(
                modifier = Modifier
                    .width(3.dp)
                    .height((14 * fraction).dp)
                    .background(MaterialTheme.colorScheme.primary)
            )
            if (index != bars - 1) {
                androidx.compose.foundation.layout.Spacer(Modifier.width(2.dp))
            }
        }
    }
}
