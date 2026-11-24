package com.nuvio.app.features.player.skip

import com.nuvio.app.core.ui.netflixFaceActive
import com.nuvio.app.core.ui.netflixFocusHighlight
import com.nuvio.app.core.ui.NetflixSans
import com.nuvio.app.core.ui.NuvioDevice
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.dismiss
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.nuvio.app.core.ui.PlatformBackHandler
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.compose_player_episode_title_format
import nuvio.composeapp.generated.resources.detail_btn_play
import nuvio.composeapp.generated.resources.player_next_episode
import nuvio.composeapp.generated.resources.player_next_episode_finding_source
import nuvio.composeapp.generated.resources.player_next_episode_playing_via_countdown
import nuvio.composeapp.generated.resources.player_next_episode_thumbnail
import nuvio.composeapp.generated.resources.player_next_episode_unaired
import org.jetbrains.compose.resources.stringResource

@Composable
fun NextEpisodeCard(
    nextEpisode: NextEpisodeInfo?,
    visible: Boolean,
    isAutoPlaySearching: Boolean,
    autoPlaySourceName: String?,
    autoPlayCountdownSec: Int?,
    blurred: Boolean,
    onPlayNext: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (nextEpisode == null) return

    PlatformBackHandler(enabled = visible, onBack = onDismiss)
    val isPlayable = nextEpisode.hasAired
    var dragOffsetX by remember(nextEpisode.videoId) { mutableFloatStateOf(0f) }
    var dragging by remember(nextEpisode.videoId) { mutableStateOf(false) }
    val animatedOffsetX by animateFloatAsState(
        targetValue = dragOffsetX,
        animationSpec = if (dragging) snap() else tween(160),
        label = "next_episode_swipe",
    )
    val dismissThreshold = with(LocalDensity.current) { 60.dp.toPx() }
    val currentOnDismiss by rememberUpdatedState(onDismiss)

    LaunchedEffect(nextEpisode.videoId, visible) {
        if (visible) {
            dragging = false
            dragOffsetX = 0f
        }
    }

    AnimatedVisibility(
        visible = visible,
        enter = slideInHorizontally(animationSpec = tween(260), initialOffsetX = { it / 2 }) +
            fadeIn(animationSpec = tween(220)),
        exit = slideOutHorizontally(animationSpec = tween(200), targetOffsetX = { it / 2 }) +
            fadeOut(animationSpec = tween(160)),
        modifier = modifier,
    ) {
        if (netflixFaceActive()) {
            NetflixNextEpisode(
                nextEpisode = nextEpisode,
                isPlayable = isPlayable,
                isAutoPlaySearching = isAutoPlaySearching,
                autoPlaySourceName = autoPlaySourceName,
                autoPlayCountdownSec = autoPlayCountdownSec,
                onPlayNext = onPlayNext,
                onDismiss = onDismiss,
            )
            return@AnimatedVisibility
        }
        val shape = RoundedCornerShape(16.dp)
        Row(
            modifier = Modifier
                .widthIn(max = 292.dp)
                .graphicsLayer { translationX = animatedOffsetX }
                .clip(shape)
                .background(Color(0xFF191919).copy(alpha = 0.89f))
                .border(1.dp, Color.White.copy(alpha = 0.12f), shape)
                .pointerInput(nextEpisode.videoId, visible, dismissThreshold) {
                    if (!visible) return@pointerInput
                    detectHorizontalDragGestures(
                        onDragStart = {
                            dragOffsetX = animatedOffsetX
                            dragging = true
                        },
                        onHorizontalDrag = { change, amount ->
                            change.consume()
                            dragOffsetX = (dragOffsetX + amount).coerceAtLeast(0f)
                        },
                        onDragEnd = {
                            dragging = false
                            if (dragOffsetX >= dismissThreshold) {
                                currentOnDismiss()
                            } else {
                                dragOffsetX = 0f
                            }
                        },
                        onDragCancel = {
                            dragging = false
                            dragOffsetX = 0f
                        },
                    )
                }
                .semantics {
                    if (visible) dismiss { currentOnDismiss(); true }
                }
                .clickable(enabled = visible) { if (isPlayable) onPlayNext() }
                .padding(horizontal = 9.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Thumbnail
            Box(
                modifier = Modifier
                    .size(width = 78.dp, height = 44.dp)
                    .clip(RoundedCornerShape(9.dp)),
            ) {
                AsyncImage(
                    model = nextEpisode.thumbnail,
                    contentDescription = stringResource(Res.string.player_next_episode_thumbnail),
                    modifier = Modifier
                        .fillMaxSize()
                        .then(if (blurred) Modifier.blur(18.dp) else Modifier),
                    contentScale = ContentScale.Crop,
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    Color.Black.copy(alpha = 0.32f),
                                ),
                            ),
                        ),
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Info
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = stringResource(Res.string.player_next_episode),
                    color = Color.White.copy(alpha = 0.8f),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = stringResource(
                        Res.string.compose_player_episode_title_format,
                        nextEpisode.season,
                        nextEpisode.episode,
                        nextEpisode.title,
                    ),
                    color = Color.White,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontWeight = FontWeight.SemiBold,
                )
                val autoPlayStatus = when {
                    !isPlayable && !nextEpisode.unairedMessage.isNullOrBlank() -> nextEpisode.unairedMessage
                    isAutoPlaySearching -> stringResource(Res.string.player_next_episode_finding_source)
                    !autoPlaySourceName.isNullOrBlank() && autoPlayCountdownSec != null ->
                        stringResource(
                            Res.string.player_next_episode_playing_via_countdown,
                            autoPlaySourceName,
                            autoPlayCountdownSec,
                        )
                    else -> null
                }
                if (autoPlayStatus != null) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = autoPlayStatus,
                        color = Color.White.copy(alpha = 0.78f),
                        fontSize = 10.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            // Play badge
            Row(
                modifier = Modifier
                    .padding(start = 4.dp)
                    .clip(CircleShape)
                    .border(1.dp, Color.White.copy(alpha = 0.2f), CircleShape)
                    .padding(horizontal = 8.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = null,
                    tint = if (isPlayable) Color.White else Color.White.copy(alpha = 0.65f),
                    modifier = Modifier.size(13.dp),
                )
                Text(
                    text = if (isPlayable) {
                        stringResource(Res.string.detail_btn_play)
                    } else {
                        stringResource(Res.string.player_next_episode_unaired)
                    },
                    color = if (isPlayable) Color.White else Color.White.copy(alpha = 0.72f),
                    fontSize = 11.sp,
                    modifier = Modifier.padding(start = 3.dp),
                )
            }
        }
    }
}


/** Netflix's end-of-episode buttons: "Watch Credits" and a white "Next Episode" with the countdown. */
@Composable
private fun NetflixNextEpisode(
    nextEpisode: NextEpisodeInfo,
    isPlayable: Boolean,
    isAutoPlaySearching: Boolean,
    autoPlaySourceName: String?,
    autoPlayCountdownSec: Int?,
    onPlayNext: () -> Unit,
    onDismiss: () -> Unit,
) {
    val nextFocus = remember { FocusRequester() }
    if (NuvioDevice.isTv) {
        LaunchedEffect(nextEpisode.videoId) {
            kotlinx.coroutines.delay(150)
            runCatching { nextFocus.requestFocus() }
        }
    }
    Column(horizontalAlignment = Alignment.End) {
        val status = when {
            !isPlayable && !nextEpisode.unairedMessage.isNullOrBlank() -> nextEpisode.unairedMessage
            isAutoPlaySearching -> stringResource(Res.string.player_next_episode_finding_source)
            else -> "S${nextEpisode.season}:E${nextEpisode.episode}  ${nextEpisode.title}"
        }
        Text(
            text = status.orEmpty(),
            color = Color.White.copy(alpha = 0.85f),
            fontSize = 13.sp,
            fontFamily = NetflixSans,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 360.dp),
        )
        Spacer(modifier = Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .netflixFocusHighlight(RoundedCornerShape(4.dp))
                    .clip(RoundedCornerShape(4.dp))
                    .clickable(onClick = onDismiss)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                Text(
                    text = "Watch Credits",
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = NetflixSans,
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            Row(
                modifier = Modifier
                    .netflixFocusHighlight(RoundedCornerShape(4.dp))
                    .clip(RoundedCornerShape(4.dp))
                    .background(if (isPlayable) Color.White else Color.White.copy(alpha = 0.4f))
                    .focusRequester(nextFocus)
                    .clickable(enabled = isPlayable, onClick = onPlayNext)
                    .padding(start = 12.dp, end = 18.dp, top = 9.dp, bottom = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = null,
                    tint = Color.Black,
                    modifier = Modifier.size(26.dp),
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = if (autoPlayCountdownSec != null && !autoPlaySourceName.isNullOrBlank()) {
                        "Next Episode ($autoPlayCountdownSec)"
                    } else {
                        "Next Episode"
                    },
                    color = Color.Black,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = NetflixSans,
                )
            }
        }
    }
}
