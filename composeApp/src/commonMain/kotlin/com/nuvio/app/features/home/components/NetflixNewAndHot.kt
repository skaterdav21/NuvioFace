package com.nuvio.app.features.home.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.nuvio.app.core.ui.NetflixColors
import com.nuvio.app.core.ui.NetflixDisplayFamily
import com.nuvio.app.core.ui.NetflixIconAction
import com.nuvio.app.core.ui.NetflixPlayButton
import com.nuvio.app.core.ui.NetflixSans
import com.nuvio.app.core.ui.NuvioDevice
import com.nuvio.app.core.ui.NuvioToastController
import com.nuvio.app.core.ui.netflixFocusHighlight
import com.nuvio.app.features.home.MetaPreview
import com.nuvio.app.features.home.stableKey
import com.nuvio.app.features.library.LibraryRepository
import com.nuvio.app.features.library.toLibraryItem
import com.nuvio.app.features.watchprogress.ContinueWatchingItem
import kotlinx.coroutines.launch

private enum class NewHotTab(val label: String) {
    ComingSoon("🍿  Coming Soon"),
    Everyone("🔥  Everyone's Watching"),
}

private val MonthNames = listOf("JAN", "FEB", "MAR", "APR", "MAY", "JUN", "JUL", "AUG", "SEP", "OCT", "NOV", "DEC")

/** "2026-12-04..." -> ("DEC", "04"), or null if it isn't a date. */
private fun releaseMonthDay(released: String?): Pair<String, String>? {
    val date = released?.trim()?.take(10) ?: return null
    if (date.length < 10 || date[4] != '-' || date[7] != '-') return null
    val month = date.substring(5, 7).toIntOrNull()?.takeIf { it in 1..12 } ?: return null
    return MonthNames[month - 1] to date.substring(8, 10)
}

/**
 * Netflix's New & Hot: "Coming Soon" (upcoming episodes of your shows, by date) and
 * "Everyone's Watching" (big cards from the top of your catalogs).
 */
@Composable
internal fun NetflixNewAndHotContent(
    rows: List<NetflixHomeRow>,
    upcomingItems: List<ContinueWatchingItem>,
    wide: Boolean,
    onPosterClick: ((MetaPreview) -> Unit)?,
    onUpcomingClick: ((ContinueWatchingItem) -> Unit)?,
    modifier: Modifier = Modifier,
) {
    var tab by remember { mutableStateOf(if (upcomingItems.isEmpty()) NewHotTab.Everyone else NewHotTab.ComingSoon) }
    val everyone = remember(rows) {
        rows.filterIsInstance<NetflixHomeRow.Catalog>()
            .take(3)
            .flatMap { it.entries.take(8) }
            .distinctBy { it.stableKey() }
            .take(20)
    }
    val upcoming = remember(upcomingItems) { upcomingItems.sortedBy { it.released ?: "9999" } }
    val side = if (wide) 48.dp else 12.dp
    val listState = rememberLazyListState()

    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize().background(NetflixColors.Black),
        contentPadding = PaddingValues(bottom = 32.dp),
    ) {
        item(key = "nh_header") {
            Column(
                Modifier
                    .padding(WindowInsets.statusBars.asPaddingValues())
                    .padding(start = side, end = side, top = if (wide) 84.dp else 12.dp, bottom = 8.dp),
            ) {
                Text(
                    text = "New & Hot",
                    style = TextStyle(fontFamily = NetflixSans, fontWeight = FontWeight.Bold, fontSize = 24.sp, color = Color.White),
                )
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NewHotTab.entries.forEach { t ->
                        val selected = t == tab
                        Box(
                            modifier = Modifier
                                .netflixFocusHighlight(RoundedCornerShape(18.dp))
                                .clip(RoundedCornerShape(18.dp))
                                .background(if (selected) Color.White else Color.Transparent)
                                .combinedClickable(onClick = {
                                    NuvioDevice.performTick()
                                    tab = t
                                })
                                .padding(horizontal = 14.dp, vertical = 8.dp),
                        ) {
                            Text(
                                text = t.label,
                                style = TextStyle(
                                    fontFamily = NetflixSans,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = if (selected) Color.Black else Color.White,
                                ),
                            )
                        }
                    }
                }
            }
        }
        when (tab) {
            NewHotTab.ComingSoon -> {
                if (upcoming.isEmpty()) {
                    item(key = "nh_cs_empty") { NewHotEmpty("Nothing coming up from your shows yet. Add series to your library and their next episodes show up here.", side) }
                } else {
                    items(upcoming, key = { "cs_${it.parentMetaId}_${it.seasonNumber}_${it.episodeNumber}_${it.videoId}" }) { item ->
                        ComingSoonEntry(item, wide, side, onUpcomingClick)
                    }
                }
            }
            NewHotTab.Everyone -> {
                if (everyone.isEmpty()) {
                    item(key = "nh_ev_empty") { NewHotEmpty("Your catalogs are still loading.", side) }
                } else {
                    items(everyone, key = { "ev_" + it.stableKey() }) { item ->
                        EveryoneEntry(item, wide, side, onPosterClick)
                    }
                }
            }
        }
    }
}

@Composable
private fun NewHotEmpty(message: String, side: androidx.compose.ui.unit.Dp) {
    Text(
        text = message,
        modifier = Modifier.padding(horizontal = side, vertical = 32.dp),
        style = TextStyle(fontFamily = NetflixSans, fontSize = 15.sp, color = NetflixColors.TextSecondary),
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ComingSoonEntry(
    item: ContinueWatchingItem,
    wide: Boolean,
    side: androidx.compose.ui.unit.Dp,
    onClick: ((ContinueWatchingItem) -> Unit)?,
) {
    val date = releaseMonthDay(item.released)
    Row(Modifier.fillMaxWidth().padding(start = side - 4.dp, end = side, top = 14.dp, bottom = 14.dp)) {
        Column(Modifier.width(if (wide) 72.dp else 52.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(date?.first ?: "SOON", style = TextStyle(fontFamily = NetflixSans, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = NetflixColors.TextSecondary))
            Text(date?.second ?: "", style = TextStyle(fontFamily = NetflixSans, fontWeight = FontWeight.Black, fontSize = 30.sp, color = Color.White))
        }
        Column(Modifier.weight(1f).widthIn(max = 720.dp)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .netflixFocusHighlight(RoundedCornerShape(6.dp), focusedScale = 1.03f)
                    .clip(RoundedCornerShape(6.dp))
                    .background(NetflixColors.Card)
                    .combinedClickable(onClick = { onClick?.invoke(item) }),
            ) {
                AsyncImage(
                    model = item.episodeThumbnail ?: item.background ?: item.rawBackgroundUrl ?: item.imageUrl ?: item.poster,
                    contentDescription = item.title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            }
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) {
                    var logoFailed by remember(item.logo) { mutableStateOf(item.logo.isNullOrBlank()) }
                    if (!logoFailed) {
                        AsyncImage(
                            model = item.logo,
                            contentDescription = item.title,
                            modifier = Modifier.height(44.dp).widthIn(max = 200.dp),
                            contentScale = ContentScale.Fit,
                            alignment = Alignment.CenterStart,
                            onError = { logoFailed = true },
                        )
                    } else {
                        Text(
                            item.title.uppercase(),
                            style = TextStyle(fontFamily = NetflixDisplayFamily, fontSize = 30.sp, color = Color.White),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                NetflixIconAction(icon = Icons.Rounded.Notifications, label = "Remind Me", onClick = { onClick?.invoke(item) })
                NetflixIconAction(icon = Icons.Rounded.Info, label = "Info", onClick = { onClick?.invoke(item) })
            }
            Spacer(Modifier.height(6.dp))
            val whenText = date?.let { "Coming ${it.first.lowercase().replaceFirstChar { c -> c.uppercase() }} ${it.second.trimStart('0')}" } ?: "Coming soon"
            Text(whenText, style = TextStyle(fontFamily = NetflixSans, fontSize = 13.sp, color = NetflixColors.TextSecondary))
            val ep = buildString {
                if (item.seasonNumber != null && item.episodeNumber != null) append("S${item.seasonNumber}:E${item.episodeNumber}  ")
                append(item.episodeTitle ?: item.subtitle)
            }
            if (ep.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(ep, style = TextStyle(fontFamily = NetflixSans, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color.White), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (!item.pauseDescription.isNullOrBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    item.pauseDescription.orEmpty(),
                    style = TextStyle(fontFamily = NetflixSans, fontSize = 13.sp, lineHeight = 18.sp, color = NetflixColors.TextSecondary),
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun EveryoneEntry(
    item: MetaPreview,
    wide: Boolean,
    side: androidx.compose.ui.unit.Dp,
    onClick: ((MetaPreview) -> Unit)?,
) {
    val scope = rememberCoroutineScope()
    val libraryState by LibraryRepository.uiState.collectAsStateWithLifecycle()
    val saved = remember(libraryState, item.id, item.type) { LibraryRepository.isSaved(item.id, item.type) }
    Column(Modifier.fillMaxWidth().widthIn(max = 820.dp).padding(horizontal = side, vertical = 14.dp)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .netflixFocusHighlight(RoundedCornerShape(6.dp), focusedScale = 1.03f)
                .clip(RoundedCornerShape(6.dp))
                .background(NetflixColors.Card)
                .combinedClickable(onClick = { onClick?.invoke(item) }),
        ) {
            AsyncImage(
                model = item.banner ?: item.landscapePoster ?: item.poster,
                contentDescription = item.name,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(80.dp)
                    .align(Alignment.BottomCenter)
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.75f)))),
            )
            var logoFailed by remember(item.logo) { mutableStateOf(item.logo.isNullOrBlank()) }
            if (!logoFailed) {
                AsyncImage(
                    model = item.logo,
                    contentDescription = null,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(12.dp)
                        .height(if (wide) 64.dp else 44.dp)
                        .widthIn(max = 240.dp),
                    contentScale = ContentScale.Fit,
                    alignment = Alignment.BottomStart,
                    onError = { logoFailed = true },
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = item.name,
                modifier = Modifier.weight(1f),
                style = TextStyle(fontFamily = NetflixSans, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Color.White),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            NetflixIconAction(
                icon = if (saved) Icons.Rounded.Check else Icons.Rounded.Add,
                label = "My List",
                onClick = {
                    NuvioDevice.performTick()
                    scope.launch {
                        runCatching { LibraryRepository.toggleSaved(item.toLibraryItem(savedAtEpochMs = 0L)) }
                            .onFailure { NuvioToastController.show(it.message ?: "Couldn't update My List") }
                    }
                },
            )
            NetflixPlayButton(label = "Play", onClick = { onClick?.invoke(item) }, modifier = Modifier.width(110.dp), height = 36.dp)
        }
        val meta = (listOfNotNull(item.releaseInfo?.takeIf { it.isNotBlank() }) + item.genres.take(3))
        if (meta.isNotEmpty()) {
            Spacer(Modifier.height(4.dp))
            Text(meta.joinToString("  •  "), style = TextStyle(fontFamily = NetflixSans, fontSize = 13.sp, color = NetflixColors.TextSecondary), maxLines = 1)
        }
        if (!item.description.isNullOrBlank()) {
            Spacer(Modifier.height(6.dp))
            Text(
                item.description.orEmpty(),
                style = TextStyle(fontFamily = NetflixSans, fontSize = 14.sp, lineHeight = 19.sp, color = NetflixColors.TextSecondary),
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
