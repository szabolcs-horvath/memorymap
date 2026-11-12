package com.szabolcshorvath.memorymap.ui.shell

import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ShortNavigationBarDefaults
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.WideNavigationRailDefaults
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuite
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteItem
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffoldDefaults
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffoldLayout
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteType
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.scene.SinglePaneSceneStrategy
import androidx.navigation3.ui.NavDisplay
import com.szabolcshorvath.memorymap.R
import com.szabolcshorvath.memorymap.ui.add.AddMemoryRoute
import com.szabolcshorvath.memorymap.ui.location.PickLocationRoute
import com.szabolcshorvath.memorymap.ui.location.PickLocationViewModel
import com.szabolcshorvath.memorymap.ui.map.MapRoute
import com.szabolcshorvath.memorymap.ui.media.MediaViewerContent
import com.szabolcshorvath.memorymap.ui.media.MediaViewerViewModel
import com.szabolcshorvath.memorymap.ui.memory.MemoryPagerRoute
import com.szabolcshorvath.memorymap.ui.memory.MissingMediaSheetRoute
import com.szabolcshorvath.memorymap.ui.memory.MissingMediaSheetViewModel
import com.szabolcshorvath.memorymap.ui.settings.SettingsRoute
import com.szabolcshorvath.memorymap.ui.theme.MemoryMapTheme
import com.szabolcshorvath.memorymap.ui.timeline.TimelineContent
import com.szabolcshorvath.memorymap.util.MediaRelinker
import com.szabolcshorvath.memorymap.util.PermissionUtil
import kotlinx.coroutines.launch

/**
 * The app's screen: the tabs under Material 3's navigation suite (a short navigation bar, a navigation rail when the
 * window is wide or short, as in landscape), and the screens over the tabs in a Navigation 3 `NavDisplay`, which
 * slide in and out and follow the predictive Back gesture. The tabs stay composed under them ([TabHost]). Back on a
 * tab other than the Map goes to the Map; on the Map it leaves the app.
 */
@Composable
fun AppShell(shell: ShellViewModel, tabs: TabViewModels) {
    val snackbar = remember { SnackbarHostState() }
    val actions = rememberShellActions(shell, tabs, snackbar)
    // Registered before the tabs' own Back handlers, so theirs (Settings' All backups page) come first.
    BackHandler(enabled = !shell.hasDetail && shell.tab != Tab.MAP) { actions.select(Tab.MAP) }
    ShellMessages(shell, snackbar)
    HideSystemBarsWhile(shell.mediaViewerShown)
    LaunchedEffect(actions.tabSwitches) {
        withFrameNanos {}
        actions.onTabDrawn()
    }

    val type = navigationType(shell.mediaViewerShown)
    // NavigationSuiteScaffold, taken apart only to give the rail its narrow width.
    Surface(
        // Edge to edge: each screen keeps clear of the system bars itself. The keyboard pushes the whole shell up, the tab
        // bar included; while it shows, the tab bar keeps no room for the navigation bar it covers.
        Modifier.windowInsetsPadding(WindowInsets.ime),
        color = NavigationSuiteScaffoldDefaults.containerColor,
        contentColor = NavigationSuiteScaffoldDefaults.contentColor,
    ) {
        NavigationSuiteScaffoldLayout(
            navigationSuite = {
                NavigationSuite(navigationSuiteType = type, modifier = railWidth(type)) { TabItems(shell.tab, type, actions) }
            },
            navigationSuiteType = type,
        ) {
            ShellContent(shell, tabs, actions, snackbar, Modifier.consumeWindowInsets(navigationInsets(type)))
        }
    }
}

@Composable
private fun ShellContent(
    shell: ShellViewModel,
    tabs: TabViewModels,
    actions: ShellActions,
    snackbar: SnackbarHostState,
    modifier: Modifier,
) {
    // The tabs show through the first entry of the back stack, which draws nothing; once a screen covers them they
    // are hidden (and paused), and they show again when it starts to leave.
    var tabsCovered by remember { mutableStateOf(shell.hasDetail) }
    Box(modifier.fillMaxSize()) {
        TabHost(shell.tab, shell.visited, covered = tabsCovered, Modifier.fillMaxSize()) { tab -> TabContent(tab, shell, tabs, actions) }
        NavDisplay(
            backStack = shell.details,
            onBack = shell::back,
            entryDecorators = listOf(rememberSaveableStateHolderNavEntryDecorator(), rememberViewModelStoreNavEntryDecorator()),
            sceneStrategies = listOf(BottomSheetSceneStrategy(), SinglePaneSceneStrategy()),
            transitionSpec = { slideInHorizontally { it } togetherWith ExitTransition.KeepUntilTransitionsFinished },
            popTransitionSpec = { EnterTransition.None togetherWith slideOutHorizontally { it } },
            predictivePopTransitionSpec = { EnterTransition.None togetherWith slideOutHorizontally { it } },
        ) { detail ->
            when (detail) {
                Detail.None -> NavEntry(detail) {
                    DisposableEffect(Unit) {
                        tabsCovered = false
                        onDispose { tabsCovered = true }
                    }
                }

                else -> detailEntry(detail, shell, tabs, actions)
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
    }
}

/**
 * The rail at Material's narrow width (80 dp, instead of the collapsed rail's 96 dp): a phone in landscape keeps more
 * of its width for the map. The rail's own padding for the system bars and the camera cutout beside it comes on top.
 */
@Composable
private fun railWidth(type: NavigationSuiteType): Modifier {
    if (type != NavigationSuiteType.WideNavigationRailCollapsed) return Modifier
    val start = WideNavigationRailDefaults.windowInsets.asPaddingValues().calculateStartPadding(LocalLayoutDirection.current)
    return Modifier.width(NARROW_RAIL_WIDTH + start)
}

/** What the navigation suite takes care of, so the content doesn't keep room for it again (as the scaffold does). */
@Composable
private fun navigationInsets(type: NavigationSuiteType): WindowInsets = when (type) {
    NavigationSuiteType.ShortNavigationBarCompact, NavigationSuiteType.ShortNavigationBarMedium ->
        ShortNavigationBarDefaults.windowInsets.only(WindowInsetsSides.Bottom).add(WindowInsets(bottom = SHORT_BAR_HEIGHT))

    NavigationSuiteType.WideNavigationRailCollapsed, NavigationSuiteType.WideNavigationRailExpanded ->
        WideNavigationRailDefaults.windowInsets.only(WindowInsetsSides.Start)

    else -> WindowInsets(0)
}

private val NARROW_RAIL_WIDTH = 80.dp
private val SHORT_BAR_HEIGHT = 64.dp

@Composable
private fun rememberShellActions(shell: ShellViewModel, tabs: TabViewModels, snackbar: SnackbarHostState): ShellActions {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    return remember(shell, tabs, scope) {
        ShellActions(context, shell, tabs, scope, snackbar) {
            // A hidden tab stays composed, and with it a focused text field.
            focus.clearFocus(force = true)
            keyboard?.hide()
        }
    }
}

/** The short navigation bar, or a collapsed navigation rail when the window is wide or short; none while [immersive]. */
@Composable
private fun navigationType(immersive: Boolean): NavigationSuiteType {
    val recommended = NavigationSuiteScaffoldDefaults.navigationSuiteType(currentWindowAdaptiveInfo())
    return when {
        immersive -> NavigationSuiteType.None
        recommended == NavigationSuiteType.ShortNavigationBarCompact -> recommended
        // A phone in landscape is short: Material suggests a wider bar there, but a rail leaves the map its height.
        else -> NavigationSuiteType.WideNavigationRailCollapsed
    }
}

@Composable
private fun TabItems(selected: Tab, type: NavigationSuiteType, actions: ShellActions) {
    Tab.entries.forEach { tab ->
        val isSelected = tab == selected
        NavigationSuiteItem(
            selected = isSelected,
            onClick = { if (isSelected) actions.reselect(tab) else actions.select(tab) },
            icon = { Icon(painterResource(if (isSelected) tab.selectedIcon else tab.icon), contentDescription = null) },
            label = { Text(stringResource(tab.label)) },
            navigationSuiteType = type,
        )
    }
}

@Composable
private fun TabContent(tab: Tab, shell: ShellViewModel, tabs: TabViewModels, actions: ShellActions) {
    when (tab) {
        Tab.MAP -> MapRoute(tabs.map, tabs.common, onOpenMemory = actions::openMemory)
        Tab.TIMELINE -> {
            val state by tabs.timeline.uiState.collectAsStateWithLifecycle()
            TimelineContent(
                state = state,
                onQueryChange = tabs.timeline::onQueryChange,
                onMemoryClick = actions::openMemory,
                onScrollHandled = tabs.timeline::onScrollHandled,
                onScrollToTopHandled = tabs.timeline::onScrollToTopHandled,
            )
        }

        Tab.ADD -> {
            // A save that finishes while the form isn't shown must not open the memory once the form is shown again.
            val shown = shell.tab == Tab.ADD && !shell.hasDetail
            LaunchedEffect(shown) { tabs.add.isScreenShown = shown }
            AddMemoryRoute(tabs.add, tabs.common, onPickLocation = actions::pickLocation, onSaved = actions::openMemory)
        }

        Tab.SETTINGS -> SettingsRoute(tabs.settings)
    }
}

/** The entry for [detail], a screen over the tabs. Each has its own ViewModels and saved state while it is open. */
private fun detailEntry(detail: Detail, shell: ShellViewModel, tabs: TabViewModels, actions: ShellActions): NavEntry<Detail> =
    when (detail) {
        is Detail.MissingMedia -> NavEntry(detail, metadata = BottomSheetSceneStrategy.bottomSheet()) {
            val viewModel: MissingMediaSheetViewModel = viewModel(factory = MissingMediaSheetViewModel.Factory)
            MissingMediaSheetRoute(viewModel, detail.itemId, onDismiss = { shell.close(detail) })
        }

        else -> NavEntry(detail) {
            // Opaque: the tabs under it stay drawn while it slides in.
            Box(
                Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
            ) {
                when (detail) {
                    is Detail.Memory -> MemoryPagerRoute(detail.memoryId, tabs.common, actions.memoryNavigation(detail))
                    is Detail.MediaViewer -> MediaViewer(detail, shell)
                    is Detail.PickLocation -> PickLocation(detail, actions, onClose = { shell.close(detail) })
                    Detail.None, is Detail.MissingMedia -> Unit
                }
            }
        }
    }

@Composable
private fun MediaViewer(detail: Detail.MediaViewer, shell: ShellViewModel) {
    val viewModel: MediaViewerViewModel = viewModel(factory = MediaViewerViewModel.factory(detail.memoryId, detail.itemId))
    val items by viewModel.mediaItems.collectAsStateWithLifecycle()
    // While it is started: the tab bar and the system bars hide.
    LifecycleStartEffect(Unit) {
        shell.mediaViewerShown = true
        onStopOrDispose { shell.mediaViewerShown = false }
    }
    // Always dark, like the black viewer, so the video controls read on it.
    MemoryMapTheme(dark = true) {
        MediaViewerContent(
            items = items,
            initialItemId = viewModel.currentItemId,
            onCurrentItem = { viewModel.currentItemId = it },
            onBack = { shell.close(detail) },
        )
    }
}

@Composable
private fun PickLocation(detail: Detail.PickLocation, actions: ShellActions, onClose: () -> Unit) {
    val viewModel: PickLocationViewModel = viewModel()
    // Launched before the picker's own start, which picks where the phone is if nothing is picked yet.
    LaunchedEffect(viewModel) { viewModel.open(detail.place) }
    PickLocationRoute(
        viewModel,
        onClose = onClose,
        onConfirm = { lat, lng, name, address -> actions.locationPicked(detail, lat, lng, name, address) },
    )
}

/** The shell's own messages: at start, an offer of media access when photos need re-linking. */
@Composable
private fun ShellMessages(shell: ShellViewModel, snackbar: SnackbarHostState) {
    val context = LocalContext.current
    val text = stringResource(R.string.relink_needs_access_message)
    val grant = stringResource(R.string.relink_grant)
    val scope = rememberCoroutineScope()
    val mediaAccess = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { _ ->
        // Partial ("selected photos") access is enough to re-link the photos the user selected.
        if (PermissionUtil.hasAnyMediaPermission(context)) scope.launch { MediaRelinker.relink(context.applicationContext) }
    }
    LaunchedEffect(shell) {
        shell.messages.collect { message ->
            when (message) {
                ShellMessage.RelinkNeedsAccess -> launch {
                    val result = snackbar.showSnackbar(
                        text,
                        actionLabel = grant,
                        duration = SnackbarDuration.Long,
                    )
                    if (result == SnackbarResult.ActionPerformed) mediaAccess.launch(PermissionUtil.getRequiredMediaPermissions())
                }
            }
        }
    }
}

/** Hides the status and navigation bars while [hidden]; a swipe from an edge shows them for a moment. */
@Composable
private fun HideSystemBarsWhile(hidden: Boolean) {
    val window = LocalActivity.current?.window ?: return
    LaunchedEffect(hidden) {
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        if (hidden) {
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(WindowInsetsCompat.Type.systemBars())
        } else {
            controller.show(WindowInsetsCompat.Type.systemBars())
        }
    }
}
