package com.nuvio.app.features.search

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.nuvio.app.core.ui.NetflixColors
import com.nuvio.app.core.ui.NetflixSans
import com.nuvio.app.core.ui.netflixFocusHighlight
import com.nuvio.app.features.home.MetaPreview

private fun LazyListScope.netflixSectionTitle(key: String, title: String) {
    item(key = key) {
        Text(
            text = title,
            modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 6.dp, bottom = 10.dp),
            style = TextStyle(fontFamily = NetflixSans, fontWeight = FontWeight.Bold, fontSize = 19.sp, color = Color.White),
        )
    }
}

/** Netflix's "Top Searches": wide thumbnail, title, and a round play icon on the right. */
@OptIn(ExperimentalFoundationApi::class)
internal fun LazyListScope.netflixTopSearchesSection(
    items: List<MetaPreview>,
    onPosterClick: ((MetaPreview) -> Unit)?,
    onPosterLongClick: ((MetaPreview) -> Unit)?,
) {
    netflixSectionTitle("nf_top_searches_title", "Top Searches")
    items(items, key = { "nf_top_" + it.type + ":" + it.id }) { item ->
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 2.dp)
                .netflixFocusHighlight(RoundedCornerShape(4.dp), focusedScale = 1.02f)
                .background(NetflixColors.Surface)
                .combinedClickable(
                    onClick = { onPosterClick?.invoke(item) },
                    onLongClick = onPosterLongClick?.let { { it(item) } },
                ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AsyncImage(
                model = item.banner ?: item.landscapePoster ?: item.poster,
                contentDescription = item.name,
                modifier = Modifier
                    .width(146.dp)
                    .aspectRatio(16f / 9f)
                    .background(NetflixColors.Card),
                contentScale = ContentScale.Crop,
            )
            Spacer(Modifier.width(12.dp))
            Text(
                text = item.name,
                modifier = Modifier.weight(1f),
                style = TextStyle(fontFamily = NetflixSans, fontWeight = FontWeight.Medium, fontSize = 15.sp, color = Color.White),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Box(
                modifier = Modifier
                    .padding(horizontal = 14.dp)
                    .size(34.dp)
                    .clip(CircleShape)
                    .border(1.5.dp, Color.White, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.PlayArrow, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
            }
        }
    }
    item(key = "nf_top_searches_gap") { Spacer(Modifier.height(18.dp)) }
}

/** Netflix's search results: one "Movies & TV" poster grid. */
@OptIn(ExperimentalFoundationApi::class)
internal fun LazyListScope.netflixSearchResultsGrid(
    items: List<MetaPreview>,
    columns: Int,
    onPosterClick: ((MetaPreview) -> Unit)?,
    onPosterLongClick: ((MetaPreview) -> Unit)?,
) {
    netflixSectionTitle("nf_results_title", "Movies & TV")
    val rows = items.chunked(columns)
    items(rows.size, key = { "nf_results_row_$it" }) { rowIndex ->
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            rows[rowIndex].forEach { item ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .aspectRatio(2f / 3f)
                        .netflixFocusHighlight(RoundedCornerShape(4.dp))
                        .clip(RoundedCornerShape(4.dp))
                        .background(NetflixColors.Card)
                        .combinedClickable(
                            onClick = { onPosterClick?.invoke(item) },
                            onLongClick = onPosterLongClick?.let { { it(item) } },
                        ),
                ) {
                    AsyncImage(
                        model = item.poster ?: item.rawPosterUrl,
                        contentDescription = item.name,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                    )
                    if (item.poster.isNullOrBlank() && item.rawPosterUrl.isNullOrBlank()) {
                        Text(
                            text = item.name,
                            modifier = Modifier.align(Alignment.Center).padding(6.dp),
                            style = TextStyle(fontFamily = NetflixSans, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color.White),
                            maxLines = 3,
                        )
                    }
                }
            }
            repeat(columns - rows[rowIndex].size) { Spacer(Modifier.weight(1f)) }
        }
    }
}
