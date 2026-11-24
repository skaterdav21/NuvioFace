package com.nuvio.app.features.home.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nuvio.app.core.build.AppFeaturePolicy
import com.nuvio.app.core.build.TrailerPlaybackMode
import com.nuvio.app.core.ui.NuvioDevice
import com.nuvio.app.core.ui.netflixFocusHighlight
import com.nuvio.app.features.details.HeroTrailerAudioState
import com.nuvio.app.features.details.MetaDetailsRepository
import com.nuvio.app.features.details.components.HeroTrailerPlayerSurface
import com.nuvio.app.features.details.selectHeroTrailer
import com.nuvio.app.features.details.youtubePlaybackUrl
import com.nuvio.app.features.home.MetaPreview
import com.nuvio.app.features.settings.ThemeSettingsRepository
import com.nuvio.app.features.trailer.TrailerPlaybackResolver
import com.nuvio.app.features.trailer.TrailerPlaybackSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

/** True when muted previews may play here: setting on, in-app trailers supported, not on mobile data. */
@Composable
internal fun rememberNetflixPreviewsAllowed(): Boolean {
    val setting by ThemeSettingsRepository.netflixAutoplayPreviews.collectAsStateWithLifecycle()
    return setting &&
        AppFeaturePolicy.heroTrailerPlaybackSupported &&
        AppFeaturePolicy.trailerPlaybackMode == TrailerPlaybackMode.IN_APP &&
        !NuvioDevice.isMeteredNetwork()
}

/**
 * Netflix billboard preview: after the title has been on screen for a moment, its trailer
 * fades in over the backdrop, muted. Fades back out when it ends or fails.
 */
@Composable
internal fun NetflixBillboardPreview(
    item: MetaPreview,
    modifier: Modifier = Modifier,
    onPlayingChange: (Boolean) -> Unit,
) {
    if (!rememberNetflixPreviewsAllowed()) return
    var source by remember(item.id) { mutableStateOf<TrailerPlaybackSource?>(null) }
    var ready by remember(item.id) { mutableStateOf(false) }
    var finished by remember(item.id) { mutableStateOf(false) }
    val muted by HeroTrailerAudioState.muted.collectAsStateWithLifecycle()

    LaunchedEffect(item.id, item.type) {
        source = null
        ready = false
        finished = false
        onPlayingChange(false)
        delay(2_500)
        val resolved = try {
            val meta = MetaDetailsRepository.fetch(item.type, item.id, cacheResult = true)
            val trailer = meta?.trailers?.let(::selectHeroTrailer)
            trailer?.let { TrailerPlaybackResolver.resolveFromYouTubeUrl(it.youtubePlaybackUrl()) }
        } catch (error: CancellationException) {
            throw error
        } catch (_: Throwable) {
            null
        }
        if (resolved == null) finished = true else source = resolved
    }

    val url = source?.videoUrl?.takeIf { it.isNotBlank() && !finished } ?: return
    val alpha by animateFloatAsState(if (ready) 1f else 0f, tween(700), label = "nf_preview_alpha")
    Box(modifier) {
        HeroTrailerPlayerSurface(
            sourceUrl = url,
            sourceAudioUrl = source?.audioUrl?.takeIf { it.isNotBlank() },
            playWhenReady = true,
            muted = muted,
            modifier = Modifier.fillMaxSize().alpha(alpha),
            onReady = {
                ready = true
                onPlayingChange(true)
            },
            onEnded = {
                finished = true
                ready = false
                onPlayingChange(false)
            },
            onError = {
                finished = true
                ready = false
                onPlayingChange(false)
            },
        )
    }
}

/** Netflix's round mute toggle shown while a preview plays. */
@Composable
internal fun NetflixMuteButton(modifier: Modifier = Modifier) {
    val muted by HeroTrailerAudioState.muted.collectAsStateWithLifecycle()
    Box(
        modifier = modifier
            .size(40.dp)
            .netflixFocusHighlight(CircleShape)
            .clip(CircleShape)
            .border(1.dp, Color.White.copy(alpha = 0.7f), CircleShape)
            .background(Color.Black.copy(alpha = 0.35f))
            .clickable { HeroTrailerAudioState.toggleMuted() },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = if (muted) Icons.AutoMirrored.Rounded.VolumeOff else Icons.AutoMirrored.Rounded.VolumeUp,
            contentDescription = if (muted) "Unmute" else "Mute",
            tint = Color.White,
            modifier = Modifier.size(20.dp),
        )
    }
}
