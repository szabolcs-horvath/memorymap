package com.szabolcshorvath.memorymap.ui.memory

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.szabolcshorvath.memorymap.R
import com.szabolcshorvath.memorymap.data.MediaType
import com.szabolcshorvath.memorymap.ui.common.SquareMediaTile
import com.szabolcshorvath.memorymap.ui.common.TileMedia

/**
 * A photo or video in the grid, square with rounded corners. A video shows a play icon. A missing one (or one that
 * fails to load) shows what it was instead: its type, size and date, over its stored thumbnail when it has one.
 */
@Composable
internal fun MediaTileView(tile: MediaTile, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val item = tile.item
    SquareMediaTile(
        media = TileMedia(item.uri, item.type, item.fileSize, item.dateTaken, unavailable = tile.missing, thumbnail = tile.thumbnail),
        modifier = modifier,
        onClick = onClick,
        description = stringResource(if (item.type == MediaType.VIDEO) R.string.open_video else R.string.open_photo),
    )
}

/**
 * Says that some media couldn't be loaded, with a button that re-links the memories' media. While that runs, it says
 * how far it got.
 */
@Composable
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
