package com.szabolcshorvath.memorymap.ui.memory

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.szabolcshorvath.memorymap.AppServices
import com.szabolcshorvath.memorymap.R
import com.szabolcshorvath.memorymap.ui.common.ConfirmDialog
import com.szabolcshorvath.memorymap.ui.memory.MissingMediaSheetViewModel.Outcome
import com.szabolcshorvath.memorymap.ui.memory.MissingMediaSheetViewModel.State
import com.szabolcshorvath.memorymap.util.NearbyFile
import com.szabolcshorvath.memorymap.util.PermissionUtil

/**
 * The missing-photo sheet's content with its ViewModel, for item [itemId]: asking for media access, choosing another
 * photo, the questions before a suggestion replaces the photo or the photo is removed, and the messages. [onDismiss]
 * closes the sheet, once the photo is found again, replaced or removed.
 */
@Composable
fun MissingMediaSheetRoute(viewModel: MissingMediaSheetViewModel, itemId: Int, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsStateWithLifecycle()
    // The suggestion the user asked to use (its URI), and whether they asked to remove the photo: kept for a rotation.
    var askingToUse by rememberSaveable { mutableStateOf<String?>(null) }
    var askingToRemove by rememberSaveable { mutableStateOf(false) }

    val access = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        viewModel.onAccessChanged(itemId)
    }
    val pick = rememberLauncherForActivityResult(AppServices.mediaPicker) { uris ->
        uris.firstOrNull()?.let(viewModel::choose)
    }
    val actions = remember(viewModel, access, pick) {
        object : MissingMediaActions {
            override fun allowAccess() = access.launch(PermissionUtil.getRequiredMediaPermissions())
            override fun showMore() = viewModel.showMore()
            override fun use(file: NearbyFile) {
                askingToUse = file.uri
            }
            override fun chooseAnother() =
                pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo))
            override fun remove() {
                askingToRemove = true
            }
        }
    }

    LaunchedEffect(viewModel, itemId) { viewModel.start(itemId) }
    ShowOutcomes(viewModel, state, onDismiss)

    MissingMediaSheetContent(state, viewModel.item, viewModel.thumbnail, actions)

    askingToUse?.let { uri ->
        val nearby = (state as? State.NotFound)?.nearby?.firstOrNull { it.uri == uri }
        if (nearby == null) {
            // After process death the sheet looks again first, and asks once the suggestion is back; if the
            // suggestions no longer have it, there is nothing left to ask about.
            if (state is State.NotFound) askingToUse = null
            return@let
        }
        val missingTaken = viewModel.item?.dateTaken ?: nearby.dateTaken
        ConfirmDialog(
            title = stringResource(R.string.missing_media_use_title),
            message = stringResource(R.string.missing_media_use_message, whenTaken(context, nearby.dateTaken - missingTaken)),
            confirm = stringResource(R.string.missing_media_use),
            cancel = stringResource(R.string.cancel),
            onConfirm = {
                askingToUse = null
                viewModel.use(uri)
            },
            onCancel = { askingToUse = null },
        )
    }
    if (askingToRemove) {
        ConfirmDialog(
            title = stringResource(R.string.missing_media_remove_title),
            message = stringResource(R.string.missing_media_remove_message),
            confirm = stringResource(R.string.missing_media_remove_confirm),
            cancel = stringResource(R.string.cancel),
            onConfirm = {
                askingToRemove = false
                viewModel.remove()
            },
            onCancel = { askingToRemove = false },
        )
    }
}

/** Says what came of the user's actions, and closes the sheet once the photo is found again, replaced or removed. */
@Composable
private fun ShowOutcomes(viewModel: MissingMediaSheetViewModel, state: State, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val dismiss by rememberUpdatedState(onDismiss)
    LaunchedEffect(state) {
        when (state) {
            State.Found -> {
                Toast.makeText(context, R.string.missing_media_found, Toast.LENGTH_SHORT).show()
                dismiss()
            }
            State.Done -> dismiss()
            else -> Unit
        }
    }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(viewModel, lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            viewModel.outcomes.collect { outcome ->
                val text = when (outcome) {
                    Outcome.CHANGED -> R.string.missing_media_changed
                    Outcome.ALREADY_IN_MEMORY -> R.string.missing_media_already_in_memory
                    Outcome.UNREADABLE -> R.string.missing_media_unreadable
                }
                Toast.makeText(context, text, Toast.LENGTH_LONG).show()
                if (outcome == Outcome.CHANGED) dismiss()
            }
        }
    }
}
