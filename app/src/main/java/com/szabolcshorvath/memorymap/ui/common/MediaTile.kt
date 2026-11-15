package com.szabolcshorvath.memorymap.ui.common

import android.text.format.Formatter
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import com.szabolcshorvath.memorymap.util.DateTimeFormatterUtil

// The UI tests read an unavailable tile's details by this name (screens/MemoryScreen, screens/AddScreen).
private const val TAG_TILE_DETAILS = "media_tile_details"

/** How much of an unavailable photo's stored thumbnail shows through: enough to recognise it, faint enough to read on. */
private const val THUMBNAIL_ALPHA = 0.45f

/** The scrim on a video's thumbnail behind its play icon. */
private const val PLAY_SCRIM_ALPHA = 0.45f

/** The picture of photo or video [uri], for an [AsyncImage]: a video's first frame. */
@Composable
fun rememberMediaRequest(uri: String, type: MediaType): ImageRequest {
    val context = LocalContext.current
    return remember(uri, type) {
        ImageRequest.Builder(context).data(uri).crossfade(true).apply {
            if (type == MediaType.VIDEO) {
                videoFrameMicros(0)
                decoderFactory { result, options, _ -> VideoFrameDecoder(result.source, options) }
            }
        }.build()
    }
}

/** What a tile shows of a photo or video: its file, and what it was, for when the file is unavailable. */
class TileMedia(
    val uri: String,
    val type: MediaType,
    val fileSize: Long,
    val dateTaken: Long,
    val unavailable: Boolean,
    val thumbnail: ByteArray? = null,
)

/**
 * A photo or video as a square tile with rounded corners; a video shows a play icon. One that is unavailable ([TileMedia.unavailable]: from
 * another phone, or missing), or that fails to load, shows what it was instead: its type, size and date taken, over
 * its stored thumbnail if it has one. [onClick] makes it a button named [description]. [content] is drawn over it.
 */
@Composable
fun SquareMediaTile(
    media: TileMedia,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    description: String? = null,
    content: @Composable BoxScope.() -> Unit = {},
) {
    // A photo deleted from the gallery while the app runs is found missing only when it fails to load.
    var failed by remember(media.uri) { mutableStateOf(false) }
    Box(
        modifier
            .aspectRatio(1f)
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .then(
                if (onClick == null) {
                    Modifier
                } else {
                    Modifier
                        .clickable(onClick = onClick)
                        .semantics { if (description != null) contentDescription = description }
                }
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (media.unavailable || failed) {
            UnavailableMedia(media)
        } else {
            AsyncImage(
                model = rememberMediaRequest(media.uri, media.type),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                onError = { failed = true },
            )
            if (media.type == MediaType.VIDEO) PlayIcon()
        }
        content()
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
private fun UnavailableMedia(media: TileMedia) {
    val context = LocalContext.current
    media.thumbnail?.let {
        AsyncImage(
            model = it,
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
            alpha = THUMBNAIL_ALPHA,
        )
    }
    val kind = stringResource(if (media.type == MediaType.VIDEO) R.string.video else R.string.photo)
    val size = if (media.fileSize > 0) Formatter.formatFileSize(context, media.fileSize) else stringResource(R.string.unknown_size)
    val date = if (media.dateTaken > 0) DateTimeFormatterUtil.twoLineDateTime(context, media.dateTaken) else stringResource(R.string.unknown_date)
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
            stringResource(R.string.media_tile_details, kind, size, date),
            modifier = Modifier.testTag(TAG_TILE_DETAILS),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
    }
}
