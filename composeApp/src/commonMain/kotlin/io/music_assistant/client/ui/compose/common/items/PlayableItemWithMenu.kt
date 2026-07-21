package io.music_assistant.client.ui.compose.common.items

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.DropdownMenu
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import io.music_assistant.client.data.model.client.QueueOption
import io.music_assistant.client.data.model.client.itemKind
import io.music_assistant.client.data.model.client.items.AppMediaItem
import io.music_assistant.client.data.model.client.items.PlayableItem
import io.music_assistant.client.data.model.client.items.PodcastEpisode
import io.music_assistant.client.data.model.client.items.RadioStation
import io.music_assistant.client.data.model.client.items.Track
import io.music_assistant.client.api.ToastBus
import io.music_assistant.client.ui.compose.common.ConfirmationDialog
import io.music_assistant.client.ui.compose.common.RemoveFromLibraryConfirmationDialog
import musicassistantclient.composeapp.generated.resources.Res
import musicassistantclient.composeapp.generated.resources.action_remove
import musicassistantclient.composeapp.generated.resources.dialog_remove_from_playlist_message
import musicassistantclient.composeapp.generated.resources.dialog_remove_from_playlist_title
import musicassistantclient.composeapp.generated.resources.toast_swipe_action
import io.music_assistant.client.settings.ViewMode
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

typealias PlayHandler<T> = (
    item: T,
    queueOption: QueueOption,
    radio: Boolean,
    fromHereInParent: Boolean,
    interleave: Boolean,
) -> Unit

@Composable
fun TrackWithMenu(
    item: Track,
    viewMode: ViewMode = ViewMode.GRID,
    showTrackNumber: Boolean = false,
    onPlayOption: PlayHandler<Track>,
    playlistActions: PlaylistActions? = null,
    onRemoveFromPlaylist: (() -> Unit)? = null,
    libraryActions: LibraryActions,
    providerIconFetcher: (@Composable (Modifier, String) -> Unit)?,
) {
    PlayableItemWithMenu(
        modifier = when (viewMode) {
            ViewMode.GRID -> Modifier
            ViewMode.LIST -> Modifier.fillMaxWidth()
        },
        viewMode = viewMode,
        item = item,
        onPlayOption = onPlayOption,
        playlistActions = playlistActions,
        onRemoveFromPlaylist = onRemoveFromPlaylist,
        libraryActions = libraryActions,
        itemComposable = { mod, onClick, onLongClick ->
            when (viewMode) {
                ViewMode.LIST -> TrackRowItem(
                    modifier = mod,
                    item = item,
                    showTrackNumber = showTrackNumber,
                    onClick = onClick,
                    onLongClick = onLongClick,
                    providerIconFetcher = providerIconFetcher,
                )

                ViewMode.GRID -> TrackGridItem(
                    modifier = mod,
                    item = item,
                    onClick = onClick,
                    onLongClick = onLongClick,
                    providerIconFetcher = providerIconFetcher,
                )
            }
        },
    )
}

@Composable
fun PodcastEpisodeWithMenu(
    item: PodcastEpisode,
    viewMode: ViewMode = ViewMode.GRID,
    onPlayOption: PlayHandler<PodcastEpisode>,
    playlistActions: PlaylistActions? = null,
    onRemoveFromPlaylist: (() -> Unit)? = null,
    libraryActions: LibraryActions,
    progressActions: ProgressActions? = null,
    providerIconFetcher: (@Composable (Modifier, String) -> Unit)?,
) {
    PlayableItemWithMenu(
        modifier = when (viewMode) {
            ViewMode.GRID -> Modifier
            ViewMode.LIST -> Modifier.fillMaxWidth()
        },
        viewMode = viewMode,
        item = item,
        onPlayOption = onPlayOption,
        playlistActions = playlistActions,
        onRemoveFromPlaylist = onRemoveFromPlaylist,
        libraryActions = libraryActions,
        progressActions = progressActions,
        itemComposable = { mod, onClick, onLongClick ->
            when (viewMode) {
                ViewMode.LIST -> PodcastEpisodeRowItem(
                    modifier = mod,
                    item = item,
                    onClick = onClick,
                    onLongClick = onLongClick,
                    providerIconFetcher = providerIconFetcher,
                )

                ViewMode.GRID -> PodcastEpisodeGridItem(
                    modifier = mod,
                    item = item,
                    onClick = onClick,
                    onLongClick = onLongClick,
                    providerIconFetcher = providerIconFetcher,
                )
            }
        },
    )
}

@Composable
fun RadioWithMenu(
    item: RadioStation,
    viewMode: ViewMode = ViewMode.GRID,
    onPlayOption: PlayHandler<RadioStation>,
    playlistActions: PlaylistActions? = null,
    onRemoveFromPlaylist: (() -> Unit)? = null,
    libraryActions: LibraryActions,
    providerIconFetcher: (@Composable (Modifier, String) -> Unit)?,
) {
    PlayableItemWithMenu(
        modifier = when (viewMode) {
            ViewMode.GRID -> Modifier
            ViewMode.LIST -> Modifier.fillMaxWidth()
        },
        viewMode = viewMode,
        item = item,
        onPlayOption = onPlayOption,
        playlistActions = playlistActions,
        onRemoveFromPlaylist = onRemoveFromPlaylist,
        libraryActions = libraryActions,
        itemComposable = { mod, onClick, onLongClick ->
            when (viewMode) {
                ViewMode.LIST -> RadioRowItem(
                    modifier = mod,
                    item = item,
                    onClick = onClick,
                    onLongClick = onLongClick,
                    providerIconFetcher = providerIconFetcher,
                )

                ViewMode.GRID -> RadioGridItem(
                    modifier = mod,
                    item = item,
                    onClick = onClick,
                    onLongClick = onLongClick,
                    providerIconFetcher = providerIconFetcher,
                )
            }
        },
    )
}

/**
 * Playable item with a long-press dropdown menu. Default click plays the item now.
 */
@Composable
private fun <T> PlayableItemWithMenu(
    modifier: Modifier = Modifier,
    viewMode: ViewMode = ViewMode.GRID,
    item: T,
    onPlayOption: PlayHandler<T>,
    playlistActions: PlaylistActions? = null,
    onRemoveFromPlaylist: (() -> Unit)? = null,
    libraryActions: LibraryActions,
    progressActions: ProgressActions? = null,
    itemComposable: @Composable (
        modifier: Modifier,
        onClick: (T) -> Unit,
        onLongClick: (T) -> Unit,
    ) -> Unit,
) where T : PlayableItem, T : AppMediaItem {
    var expandedItemId by remember { mutableStateOf<String?>(null) }
    var showPlaylistDialog by rememberSaveable { mutableStateOf(false) }
    var showCustomizeDialog by rememberSaveable { mutableStateOf(false) }
    var showRemoveConfirmation by remember { mutableStateOf(false) }
    var showRemovePlaylistConfirmation by remember { mutableStateOf(false) }

    // The tap action for this item's (kind, context) pair (PLAY_NOW outside customizable
    // screens). Null when the item isn't playable — then a tap opens the menu instead.
    val clickActionConfig = LocalClickActionConfig.current
    val effectiveDefault = clickActionConfig.effectiveActionFor(item)
    val menuFlags = ContextMenuCallSiteFlags(
        librarySupported = true,
        canAddToPlaylist = playlistActions != null && item.supportsAddToPlaylist,
        canRemoveFromPlaylist = onRemoveFromPlaylist != null,
        progressSupported = progressActions != null && item is PodcastEpisode,
        customizationAllowed = true,
    )
    val actions = resolveConfiguredLongClickActions(
        item = item,
        clickContext = clickActionConfig.context,
        menuConfig = clickActionConfig.menuActionsFor(item),
        flags = menuFlags,
        defaultAction = effectiveDefault,
    )

    val runPlayAction: (ItemAction) -> Unit = { action ->
        when (action) {
            is ItemAction.Play -> onPlayOption(item, action.queueOption, false, false, false)
            ItemAction.InterleaveIntoQueue -> onPlayOption(item, QueueOption.NEXT, false, false, true)
            ItemAction.StartRadio -> onPlayOption(item, QueueOption.REPLACE, true, false, false)
            is ItemAction.PlayFromHere -> onPlayOption(item, QueueOption.REPLACE, false, true, false)
            else -> Unit
        }
    }

    val runSwipeAction: (ItemAction) -> Unit = { action ->
        when (action) {
            is ItemAction.Play,
            ItemAction.InterleaveIntoQueue,
            ItemAction.StartRadio,
            is ItemAction.PlayFromHere,
            -> runPlayAction(action)
            ItemAction.AddToLibrary -> libraryActions.onLibraryClick(item)
            ItemAction.RemoveFromLibrary -> showRemoveConfirmation = true
            ItemAction.Favorite,
            ItemAction.Unfavorite,
            -> libraryActions.onFavoriteClick(item)
            else -> Unit
        }
    }

    val swipePrefs = LocalSwipeActionPrefs.current
    val swipeOnLeftAction = swipePrefs.onSwipeLeft.effectiveFor(item)
    val swipeOnRightAction = swipePrefs.onSwipeRight.effectiveFor(item)
    val swipeEnabled = viewMode == ViewMode.LIST && (swipeOnLeftAction != null || swipeOnRightAction != null)
    val toastBus = koinInject<ToastBus>()
    val scope = rememberCoroutineScope()

    // Non-playable items keep the long-press menu (favorite, library, …) but can't be played:
    // dim them and route a tap to the menu instead of starting playback.
    val playable = item.isPlayable
    Box(modifier = modifier) {
        val rowContent: @Composable (Modifier) -> Unit = { rowModifier ->
            itemComposable(
                rowModifier
                    .then(if (playable) Modifier else Modifier.alpha(DISABLED_ITEM_ALPHA)),
                { effectiveDefault?.let(runPlayAction) ?: run { expandedItemId = item.itemId } },
                { expandedItemId = item.itemId },
            )
        }

        if (swipeEnabled) {
            SwipeableListRow(
                onSwipeLeftAction = swipeOnLeftAction,
                onSwipeRightAction = swipeOnRightAction,
                onAction = { action ->
                    runSwipeAction(action)
                    if (action != ItemAction.RemoveFromLibrary) {
                        scope.launch {
                            val actionLabel = getString(action.title(null))
                            toastBus.show(
                                getString(
                                    Res.string.toast_swipe_action,
                                    item.displayName,
                                    actionLabel,
                                ),
                            )
                        }
                    }
                },
            ) {
                rowContent(Modifier)
            }
        } else {
            rowContent(Modifier.align(Alignment.Center))
        }
        DropdownMenu(
            modifier = Modifier.semantics {
                role = Role.DropdownList
            },
            expanded = expandedItemId == item.itemId,
            onDismissRequest = { expandedItemId = null },
        ) {
            ItemActionMenuItems(clickActionConfig.context, actions, defaultAction = effectiveDefault) { action ->
                expandedItemId = null
                when (action) {
                    is ItemAction.Play,
                    ItemAction.InterleaveIntoQueue,
                    ItemAction.StartRadio,
                    is ItemAction.PlayFromHere,
                    -> runPlayAction(action)
                    ItemAction.AddToLibrary -> libraryActions.onLibraryClick(item)
                    ItemAction.RemoveFromLibrary -> showRemoveConfirmation = true

                    ItemAction.Favorite,
                    ItemAction.Unfavorite,
                        -> libraryActions.onFavoriteClick(item)

                    ItemAction.AddToPlaylist -> showPlaylistDialog = true
                    ItemAction.RemoveFromPlaylist -> showRemovePlaylistConfirmation = true
                    ItemAction.MarkPlayed -> progressActions?.onMarkPlayed(item)
                    ItemAction.MarkUnplayed -> progressActions?.onMarkUnplayed(item)
                    ItemAction.Customize -> showCustomizeDialog = true
                }
            }
        }

        if (showPlaylistDialog && playlistActions != null) {
            AddToPlaylistDialog(
                item = item,
                playlistActions = playlistActions,
                onDismiss = { showPlaylistDialog = false },
            )
        }

        if (showCustomizeDialog) {
            item.itemKind()?.let { kind ->
                DefaultClickActionsDialog(
                    itemKind = kind,
                    onDismiss = { showCustomizeDialog = false },
                )
            }
        }

        if (showRemoveConfirmation) {
            RemoveFromLibraryConfirmationDialog(
                item = item,
                onConfirm = { libraryActions.onLibraryClick(item) },
                onDismiss = { showRemoveConfirmation = false },
            )
        }

        if (showRemovePlaylistConfirmation && onRemoveFromPlaylist != null) {
            ConfirmationDialog(
                title = stringResource(Res.string.dialog_remove_from_playlist_title),
                message = stringResource(Res.string.dialog_remove_from_playlist_message, item.displayName),
                confirmLabel = stringResource(Res.string.action_remove),
                onConfirm = { onRemoveFromPlaylist.invoke() },
                onDismiss = { showRemovePlaylistConfirmation = false },
            )
        }
    }
}
