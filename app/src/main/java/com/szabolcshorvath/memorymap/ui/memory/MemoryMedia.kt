package com.szabolcshorvath.memorymap.ui.memory

import android.text.format.Formatter
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import coil3.video.VideoFrameDecoder
import coil3.video.videoFrameMicros
import com.szabolcshorvath.memorymap.R
import com.szabolcshorvath.memorymap.data.MediaType
import com.szabolcshorvath.memorymap.fragment.MediaTile
import com.szabolcshorvath.memorymap.fragment.MemoryUiState
import com.szabolcshorvath.memorymap.util.DateTimeFormatterUtil

// The UI tests read a missing tile's details by this name (screens/MemoryScreen), the name the View version's id had.
private const val TAG_TILE_DETAILS = "tvErrorDetails"

/** How much of a missing photo's stored thumbnail shows through: enough to recognise it, faint enough to read on. */
private const val THUMBNAIL_ALPHA = 0.45f

/** The scrim on a video's thumbnail behind its play icon. */
private const val PLAY_SCRIM_ALPHA = 0.45f

/**
 * A photo or video in the grid, square with rounded corners. A video shows a play icon. A missing one (or one that
 * fails to load) shows what it was instead: its type, size and date, over its stored thumbnail when it has one.
 */
@Composable
internal fun MediaTileView(tile: MediaTile, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val item = tile.item
    val isVideo = item.type == MediaType.VIDEO
    // A photo deleted from the gallery while the app runs is found missing only when it fails to load.
    var failed by remember(item.uri) { mutableStateOf(false) }
    val description = stringResource(if (isVideo) R.string.open_video else R.string.open_photo)
    Box(
        modifier
            .aspectRatio(1f)
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .clickable(onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        if (tile.missing || failed) {
            MissingTile(tile)
        } else {
            val context = LocalContext.current
            val request = remember(item.uri, isVideo) {
                ImageRequest.Builder(context).data(item.uri).crossfade(true).apply {
                    if (isVideo) {
                        videoFrameMicros(0)
                        decoderFactory { result, options, _ -> VideoFrameDecoder(result.source, options) }
                    }
                }.build()
            }
            AsyncImage(
                model = request,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                onError = { failed = true },
            )
            if (isVideo) PlayIcon()
        }
    }
}

@Composable
private fun PlayIcon() {
    Box(
        Modifier
            .size(40.dp)
            .background(Color.Black.copy(alpha = PLAY_SCRIM_ALPHA), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(painterResource(R.drawable.ic_play_arrow), contentDescription = null, tint = Color.White)
    }
}

@Composable
private fun MissingTile(tile: MediaTile) {
    val context = LocalContext.current
    val item = tile.item
    tile.thumbnail?.let { thumbnail ->
        AsyncImage(
            model = thumbnail,
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
            alpha = THUMBNAIL_ALPHA,
        )
    }
    val type = stringResource(if (item.type == MediaType.VIDEO) R.string.video else R.string.photo)
    val size = if (item.fileSize > 0) Formatter.formatFileSize(context, item.fileSize) else stringResource(R.string.unknown_size)
    val date = if (item.dateTaken > 0) {
        DateTimeFormatterUtil.twoLineDateTime(context, item.dateTaken)
    } else {
        stringResource(R.string.unknown_date)
    }
    Column(
        Modifier
            .fillMaxSize()
            .padding(8.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            painterResource(R.drawable.ic_warning),
            contentDescription = null,
            modifier = Modifier.size(24.dp),
            tint = MaterialTheme.colorScheme.error,
        )
        Text(
            stringResource(R.string.media_tile_details, type, size, date),
            modifier = Modifier.testTag(TAG_TILE_DETAILS),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * Says that some media couldn't be loaded, with a button that re-links the memories' media. While that runs, it says
 * how far it got.
 */
@Composable
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
internal fun MissingMediaBanner(state: MemoryUiState, onRelink: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Surface(
        color = colors.errorContainer,
        contentColor = colors.onErrorContainer,
        shape = MaterialTheme.shapes.large,
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = GAP),
    ) {
        Column(Modifier.padding(start = 16.dp, top = 16.dp, end = 8.dp, bottom = 8.dp)) {
            Row(Modifier.padding(end = 8.dp)) {
                Icon(painterResource(R.drawable.ic_warning), contentDescription = null, tint = colors.error)
                Spacer(Modifier.width(12.dp))
                Text(
                    state.relinkStatus ?: stringResource(R.string.media_could_not_load),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (state.relinking) CircularWavyProgressIndicator(Modifier.size(24.dp))
                TextButton(
                    onClick = onRelink,
                    enabled = !state.relinking,
                    colors = ButtonDefaults.textButtonColors(contentColor = colors.onErrorContainer),
                ) {
                    Text(stringResource(R.string.relink_media_files))
                }
            }
        }
    }
}
