package com.szabolcshorvath.memorymap.ui.memory

import android.content.Context
import android.text.format.Formatter
import androidx.annotation.DrawableRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.rememberNestedScrollInteropConnection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import coil3.video.VideoFrameDecoder
import coil3.video.videoFrameMicros
import com.szabolcshorvath.memorymap.R
import com.szabolcshorvath.memorymap.data.MediaItem
import com.szabolcshorvath.memorymap.data.MediaType
import com.szabolcshorvath.memorymap.fragment.MissingMediaSheetViewModel.State
import com.szabolcshorvath.memorymap.util.DateTimeFormatterUtil
import com.szabolcshorvath.memorymap.util.NearbyFile
import com.szabolcshorvath.memorymap.util.NearbyMedia
import java.time.Instant
import java.time.ZoneId
import kotlin.math.abs

// The UI tests find these elements by these names (screens/MissingPhotoSheet), the names the View version's ids had.
private const val TAG_MESSAGE = "tvMissingMessage"
private const val TAG_FILE = "tvMissingFile"

private const val MILLIS_PER_SECOND = 1000L
private val SHEET_PADDING = 24.dp
private val THUMBNAIL_SIZE = 72.dp
private val SUGGESTION_SIZE = 56.dp

/** What the user can do on the missing-photo sheet. Using a suggestion and removing the photo ask first. */
interface MissingMediaActions {
    fun allowAccess()
    fun showMore()
    fun use(file: NearbyFile)
    fun chooseAnother()
    fun remove()
}

/** "7 sec later", "3 min earlier" or "at the same time", for a file taken [deltaMs] after the missing one. */
fun whenTaken(context: Context, deltaMs: Long): String = when {
    abs(deltaMs) < MILLIS_PER_SECOND -> context.getString(R.string.nearby_same_time)
    deltaMs > 0 -> context.getString(R.string.nearby_later, NearbyMedia.span(deltaMs))
    else -> context.getString(R.string.nearby_earlier, NearbyMedia.span(deltaMs))
}

/**
 * The missing-photo sheet: what the lost photo or video was (its stored thumbnail, name, date and size), the search
 * for it, then what was taken around the same time, and the actions: choose another photo or remove it. Dragging the
 * sheet down, tapping outside it or Back closes it.
 */
@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun MissingMediaSheetContent(state: State, item: MediaItem?, thumbnail: ByteArray?, actions: MissingMediaActions) {
    Column(
        Modifier
            .fillMaxWidth()
            .semantics { testTagsAsResourceId = true }
            // The sheet itself is a View: it is dragged down once this column is scrolled to its top.
            .nestedScroll(rememberNestedScrollInteropConnection())
            .verticalScroll(rememberScrollState())
            .padding(bottom = 16.dp),
    ) {
        BottomSheetDefaults.DragHandle(Modifier.align(Alignment.CenterHorizontally))
        if (item != null) Header(item, thumbnail)
        Search(state)
        if (state == State.NeedsAccess) AllowAccessButton(actions::allowAccess)
        (state as? State.NotFound)?.let { notFound ->
            if (item != null) Suggestions(notFound, item, actions)
        }
        HorizontalDivider(Modifier.padding(horizontal = SHEET_PADDING, vertical = 8.dp))
        Action(R.drawable.ic_photo_library, R.string.missing_media_choose_another, MaterialTheme.colorScheme.onSurface, actions::chooseAnother)
        Action(R.drawable.ic_delete, R.string.missing_media_remove, MaterialTheme.colorScheme.error, actions::remove)
    }
}

@Composable
private fun Header(item: MediaItem, thumbnail: ByteArray?) {
    val context = LocalContext.current
    Row(Modifier.padding(horizontal = SHEET_PADDING, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        if (thumbnail != null) {
            val description = stringResource(R.string.missing_media_thumbnail)
            AsyncImage(
                model = thumbnail,
                contentDescription = description,
                modifier = Modifier
                    .size(THUMBNAIL_SIZE)
                    .clip(MaterialTheme.shapes.medium),
                contentScale = ContentScale.Crop,
            )
            Spacer(Modifier.width(16.dp))
        }
        Column {
            Text(
                stringResource(if (item.type == MediaType.VIDEO) R.string.missing_video_title else R.string.missing_photo_title),
                style = MaterialTheme.typography.titleLarge,
            )
            fileLine(context, item)?.let {
                Text(it, Modifier.testTag(TAG_FILE), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            val date = if (item.dateTaken > 0) {
                DateTimeFormatterUtil.dateTime(context, Instant.ofEpochMilli(item.dateTaken).atZone(ZoneId.systemDefault()))
            } else {
                stringResource(R.string.missing_media_unknown_date)
            }
            val size = if (item.fileSize > 0) Formatter.formatFileSize(context, item.fileSize) else stringResource(R.string.missing_media_unknown_size)
            Text(
                stringResource(R.string.missing_media_details, date, size),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** The file's name and size in pixels, as far as they were stored. */
private fun fileLine(context: Context, item: MediaItem): String? {
    val name = item.displayName ?: return null
    val width = item.width
    val height = item.height
    return if (width != null && height != null) context.getString(R.string.missing_media_file, name, width, height) else name
}

/** While the sheet looks for the photo: how far it got. Then what it found, or that it needs access to look. */
@Composable
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
private fun Search(state: State) {
    if (state is State.Looking || state == State.Loading) {
        Row(Modifier.padding(horizontal = SHEET_PADDING, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            CircularWavyProgressIndicator(Modifier.size(24.dp))
            Spacer(Modifier.width(16.dp))
            Text(
                (state as? State.Looking)?.progress ?: stringResource(R.string.missing_media_looking),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
    val message = when (state) {
        State.NeedsAccess -> stringResource(R.string.missing_media_needs_access)
        is State.NotFound -> stringResource(
            when {
                !state.dateKnown -> R.string.missing_media_no_date
                state.nearby.isNotEmpty() -> R.string.missing_media_not_found
                state.wider -> R.string.missing_media_none_nearby_wider
                else -> R.string.missing_media_none_nearby
            }
        )
        else -> null
    }
    if (message != null) {
        Text(
            message,
            Modifier
                .padding(horizontal = SHEET_PADDING, vertical = 8.dp)
                .testTag(TAG_MESSAGE),
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}

@Composable
private fun Suggestions(state: State.NotFound, item: MediaItem, actions: MissingMediaActions) {
    state.nearby.forEach { file -> Suggestion(file, item, onClick = { actions.use(file) }) }
    if (state.dateKnown && !state.wider) {
        TextButton(onClick = actions::showMore, modifier = Modifier.padding(start = SHEET_PADDING - 12.dp)) {
            Text(stringResource(R.string.missing_media_show_more))
        }
    }
}

/** A file taken around the time of the missing one: how much earlier or later, its size, and whether it looks alike. */
@Composable
private fun Suggestion(file: NearbyFile, item: MediaItem, onClick: () -> Unit) {
    val context = LocalContext.current
    val taken = whenTaken(context, file.dateTaken - item.dateTaken)
    val description = stringResource(if (file.looksAlike) R.string.nearby_use_alike else R.string.nearby_use, taken)
    val request = ImageRequest.Builder(context).data(file.uri).crossfade(true).apply {
        if (item.type == MediaType.VIDEO) {
            videoFrameMicros(0)
            decoderFactory { result, options, _ -> VideoFrameDecoder(result.source, options) }
        }
    }.build()
    ListItem(
        headlineContent = { Text(taken.replaceFirstChar { it.titlecase() }) },
        modifier = Modifier
            .clickable(onClick = onClick)
            .semantics { contentDescription = description }
            .padding(horizontal = SHEET_PADDING - 16.dp),
        supportingContent = {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(Formatter.formatFileSize(context, file.size))
                if (file.looksAlike) Text(stringResource(R.string.nearby_looks_alike), color = MaterialTheme.colorScheme.primary)
            }
        },
        leadingContent = {
            AsyncImage(
                model = request,
                contentDescription = null,
                modifier = Modifier
                    .size(SUGGESTION_SIZE)
                    .clip(MaterialTheme.shapes.medium),
                contentScale = ContentScale.Crop,
            )
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    )
}

/** One of the sheet's actions, as a list item with an icon; [color] marks a destructive one. */
@Composable
private fun Action(@DrawableRes icon: Int, label: Int, color: Color, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(stringResource(label)) },
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(horizontal = SHEET_PADDING - 16.dp),
        leadingContent = { Icon(painterResource(icon), contentDescription = null) },
        colors = ListItemDefaults.colors(
            containerColor = Color.Transparent,
            headlineColor = color,
            leadingIconColor = color,
        ),
    )
}

/** Shown when the sheet needs full media access to look: Android's permission dialog follows. */
@Composable
internal fun AllowAccessButton(onClick: () -> Unit) {
    Button(onClick = onClick, modifier = Modifier.padding(horizontal = SHEET_PADDING)) {
        Text(stringResource(R.string.relink_more_allow))
    }
}
