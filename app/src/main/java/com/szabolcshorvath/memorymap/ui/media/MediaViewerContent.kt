package com.szabolcshorvath.memorymap.ui.media

import android.text.format.DateUtils
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SliderState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.compose.material3.Player
import androidx.media3.ui.compose.material3.PlayerDefaults
import androidx.media3.ui.compose.state.rememberProgressStateWithTickCount
import androidx.media3.ui.compose.state.rememberProgressStateWithTickInterval
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.szabolcshorvath.memorymap.AppServices
import com.szabolcshorvath.memorymap.R
import com.szabolcshorvath.memorymap.data.MediaItem
import com.szabolcshorvath.memorymap.data.MediaType
import me.saket.telephoto.zoomable.coil3.ZoomableAsyncImage
import androidx.media3.common.MediaItem as PlayerItem

// The UI tests find these elements by these names (screens/MediaViewerScreen), the names the View version's ids had.
private const val TAG_PAGER = "mediaViewPager"
private const val TAG_SCRUBBER = "videoScrubber"

/** How often the position slider tells a screen reader where the video is. */
private const val POSITION_TICK_MS = 1_000L
private const val MS_PER_SECOND = 1_000L

// The position slider: slimmer than a settings slider, as a video's position bar usually is.
private val POSITION_TRACK_HEIGHT = 4.dp
private val POSITION_HANDLE_SIZE = DpSize(4.dp, 20.dp)
private val POSITION_HANDLE_GAP = 3.dp

/** The scrim behind the top bar, so its arrow and text read over a light photo. */
private const val TOP_BAR_SCRIM_ALPHA = 0.5f

/**
 * A memory's photos and videos, full screen on black, one per page. Photos zoom (pinch or double-tap) and load sharper
 * as they do. Tapping shows or hides a top bar (a back arrow and which item this is) and a video's controls. [items] is
 * null until they are loaded; the viewer opens on [initialItemId] and reports each item it turns to.
 */
@Composable
fun MediaViewerContent(items: List<MediaItem>?, initialItemId: Int, onCurrentItem: (Int) -> Unit, onBack: () -> Unit) {
    // The surface gives the player's times and icons their colour, light on the black.
    Surface(
        modifier = Modifier
            .fillMaxSize()
            .semantics { testTagsAsResourceId = true },
        color = Color.Black,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Box {
            if (items != null) Pages(items, initialItemId, onCurrentItem, onBack)
        }
    }
}

@Composable
private fun Pages(items: List<MediaItem>, initialItemId: Int, onCurrentItem: (Int) -> Unit, onBack: () -> Unit) {
    // Pages are keyed by item, so the viewer stays on its item when the list changes (an item removed before it).
    val pagerState = rememberPagerState(initialPage = items.indexOfFirst { it.id == initialItemId }.coerceAtLeast(0)) {
        items.size
    }
    var controlsShown by rememberSaveable { mutableStateOf(true) }
    val toggleControls = { controlsShown = !controlsShown }
    LaunchedEffect(pagerState, items) {
        snapshotFlow { pagerState.currentPage }.collect { page -> items.getOrNull(page)?.let { onCurrentItem(it.id) } }
    }
    HorizontalPager(
        state = pagerState,
        modifier = Modifier
            .fillMaxSize()
            .testTag(TAG_PAGER),
        key = { items[it].id },
    ) { page ->
        val item = items[page]
        if (item.type == MediaType.VIDEO) {
            VideoPage(item, isCurrent = page == pagerState.settledPage, controlsShown, toggleControls)
        } else {
            PhotoPage(item, toggleControls)
        }
    }
    if (items.isNotEmpty()) {
        ViewerTopBar(
            visible = controlsShown,
            position = stringResource(R.string.media_position, pagerState.currentPage + 1, items.size),
            onBack = onBack,
        )
    }
}

/** The top bar over the media: a back arrow and "2 of 6", on a scrim so they read over a light photo. */
@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun ViewerTopBar(visible: Boolean, position: String, onBack: () -> Unit) {
    AnimatedVisibility(visible = visible, enter = fadeIn(), exit = fadeOut()) {
        TopAppBar(
            title = { Text(position) },
            modifier = Modifier.fillMaxWidth(),
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(painterResource(R.drawable.ic_arrow_back), stringResource(R.string.navigate_back))
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = Color.Black.copy(alpha = TOP_BAR_SCRIM_ALPHA),
                titleContentColor = Color.White,
                navigationIconContentColor = Color.White,
            ),
        )
    }
}

/** A photo that zooms with a pinch or a double tap, loading sharper tiles of the file as it does. */
@Composable
private fun PhotoPage(item: MediaItem, onTap: () -> Unit) {
    val context = LocalContext.current
    val request = remember(item.uri) { ImageRequest.Builder(context).data(item.uri).crossfade(true).build() }
    ZoomableAsyncImage(
        model = request,
        contentDescription = stringResource(R.string.photo),
        modifier = Modifier.fillMaxSize(),
        onClick = { onTap() },
    )
}

/**
 * A video, played by Media3 with its own Material 3 controls: play and pause, seeking back and forward, and the
 * position with the time played and the length. It stops when another page is turned to.
 */
@Composable
@androidx.annotation.OptIn(UnstableApi::class)
private fun VideoPage(item: MediaItem, isCurrent: Boolean, controlsShown: Boolean, onTap: () -> Unit) {
    val context = LocalContext.current
    val player = remember(item.uri) {
        val renderers = DefaultRenderersFactory(context).setMediaCodecSelector(AppServices.videoDecoders)
        ExoPlayer.Builder(context, renderers).build().apply {
            setMediaItem(PlayerItem.fromUri(item.uri))
            prepare()
        }
    }
    DisposableEffect(player) { onDispose { player.release() } }
    LaunchedEffect(isCurrent) {
        if (!isCurrent) {
            player.pause()
            player.seekTo(0)
        }
    }
    val description = stringResource(R.string.video)
    // The description goes on a box around the player: on the player itself it doesn't reach accessibility services.
    Box(
        Modifier
            .fillMaxSize()
            .semantics { contentDescription = description }
    ) {
        Player(
            player = player,
            modifier = Modifier
                .fillMaxSize()
                .clickable(interactionSource = null, indication = null, onClick = onTap),
            showControls = controlsShown,
            // The viewer's own top bar is above every page.
            topControls = null,
            centerControls = { player, visible ->
                // One video per page: the viewer, not the player, moves to the next item.
                PlayerDefaults.CenterControls(
                    player,
                    visible,
                    Modifier.fillMaxWidth(),
                    backSecondary = {},
                    forwardSecondary = {},
                )
            },
            bottomControls = { player, visible ->
                PlayerDefaults.BottomControls(
                    player,
                    visible,
                    progressSlider = { VideoPositionSlider(it) },
                )
            },
        )
    }
}

/**
 * The video's position, read out as "Video position, 0:02 of 0:05" and moved by a screen reader in seconds. Material
 * 3's slider keeps its own accessibility node apart from any name set on it or around it, so this box stands in for
 * it with the name, the position and the length.
 */
@Composable
@androidx.annotation.OptIn(UnstableApi::class)
private fun VideoPositionSlider(player: Player?) {
    val name = stringResource(R.string.video_position)
    val progress = rememberProgressStateWithTickInterval(player, tickIntervalMs = POSITION_TICK_MS)
    val lengthMs = progress.durationMs.takeIf { it != C.TIME_UNSET }?.coerceAtLeast(0L) ?: 0L
    val positionMs = progress.currentPositionMs.coerceIn(0L, lengthMs)
    val state = stringResource(
        R.string.video_time_of_length,
        DateUtils.formatElapsedTime(positionMs / MS_PER_SECOND),
        DateUtils.formatElapsedTime(lengthMs / MS_PER_SECOND),
    )
    Box(
        Modifier.clearAndSetSemantics {
            testTag = TAG_SCRUBBER
            contentDescription = name
            stateDescription = state
            progressBarRangeInfo = ProgressBarRangeInfo(positionMs.toFloat(), 0f..lengthMs.toFloat())
            if (player != null) {
                setProgress { targetMs ->
                    player.seekTo(targetMs.toLong())
                    true
                }
            }
        }
    ) {
        PositionSlider(player)
    }
}

/**
 * Media3's position slider, as its `ProgressSlider` builds it (Media3's progress state driving Material 3's slider),
 * with a slim track and handle for a video and no dot at the track's end: every position along it can be picked.
 */
@Composable
@androidx.annotation.OptIn(UnstableApi::class)
private fun PositionSlider(player: Player?) {
    // One step per pixel of the track, so the position moves on as often as the handle can.
    var widthPx by remember { mutableIntStateOf(0) }
    val progress = rememberProgressStateWithTickCount(player, totalTickCount = widthPx)
    var draggedTo by remember { mutableStateOf<Float?>(null) }
    val interactionSource = remember { MutableInteractionSource() }
    val enabled = progress.changingProgressEnabled
    val sliderState = remember { SliderState() }
    sliderState.value = draggedTo ?: progress.currentPositionProgress
    Slider(
        state = sliderState,
        onValueChange = { draggedTo = it },
        onValueChangeFinished = {
            draggedTo?.let { progress.updateCurrentPositionProgress(it) }
            draggedTo = null
        },
        modifier = Modifier.onSizeChanged { widthPx = it.width },
        enabled = enabled,
        interactionSource = interactionSource,
        thumb = { SliderDefaults.Thumb(interactionSource, enabled = enabled, thumbSize = POSITION_HANDLE_SIZE) },
        track = {
            SliderDefaults.Track(
                it,
                Modifier.height(POSITION_TRACK_HEIGHT),
                enabled = enabled,
                drawStopIndicator = null,
                thumbTrackGapSize = POSITION_HANDLE_GAP,
            )
        },
    )
}
