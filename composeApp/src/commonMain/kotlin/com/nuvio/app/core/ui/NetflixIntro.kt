package com.nuvio.app.core.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/** Played once per app launch. */
private object NetflixIntroState {
    var played = false
}

/**
 * Netflix-style launch sting: the red wordmark settles in, then the camera dives into it as
 * red ribbons streak past, and the app fades up underneath. About 1.8 seconds, silent.
 */
@Composable
fun NetflixIntro() {
    var visible by remember { mutableStateOf(!NetflixIntroState.played) }
    if (!visible) return

    val logoAlpha = remember { Animatable(0f) }
    val logoScale = remember { Animatable(0.82f) }
    val ribbons = remember { Animatable(0f) }
    val curtain = remember { Animatable(1f) }

    LaunchedEffect(Unit) {
        NetflixIntroState.played = true
        coroutineScope {
            launch { logoAlpha.animateTo(1f, tween(380, easing = LinearEasing)) }
            logoScale.animateTo(1f, tween(650, easing = FastOutSlowInEasing))
        }
        kotlinx.coroutines.delay(260)
        coroutineScope {
            launch { logoScale.animateTo(9f, tween(620, easing = FastOutSlowInEasing)) }
            launch { ribbons.animateTo(1f, tween(620, easing = LinearEasing)) }
            launch {
                kotlinx.coroutines.delay(260)
                logoAlpha.animateTo(0f, tween(360))
            }
        }
        curtain.animateTo(0f, tween(320))
        visible = false
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer { alpha = curtain.value }
            .background(Color.Black),
        contentAlignment = Alignment.Center,
    ) {
        // Ribbons: vertical red and pink streaks that widen and fade as the camera dives in.
        Canvas(Modifier.fillMaxSize()) {
            val p = ribbons.value
            if (p <= 0f) return@Canvas
            val colors = listOf(
                Color(0xFFE50914), Color(0xFFB20710), Color(0xFFFF2A55),
                Color(0xFF8B0A14), Color(0xFFE50914), Color(0xFFFF6B6B),
                Color(0xFFB20710), Color(0xFFE50914),
            )
            val count = 16
            val centerX = size.width / 2f
            val spread = size.width * (0.15f + p * 1.4f)
            for (i in 0 until count) {
                val t = i / (count - 1f) - 0.5f
                val x = centerX + t * spread
                val w = (size.width / count) * (0.25f + p * 1.6f) * (if (i % 3 == 0) 1.3f else 0.8f)
                val a = (1f - p) * (if (i % 2 == 0) 0.95f else 0.6f)
                drawRect(
                    brush = Brush.verticalGradient(
                        listOf(Color.Transparent, colors[i % colors.size].copy(alpha = a), Color.Transparent),
                    ),
                    topLeft = Offset(x - w / 2f, 0f),
                    size = Size(w, size.height),
                )
            }
        }
        Text(
            text = "NUVIO",
            modifier = Modifier.graphicsLayer {
                alpha = logoAlpha.value
                scaleX = logoScale.value
                scaleY = logoScale.value
            },
            style = TextStyle(
                fontFamily = NetflixDisplayFamily,
                fontSize = 96.sp,
                letterSpacing = 2.sp,
                color = NetflixColors.Red,
            ),
        )
    }
}
