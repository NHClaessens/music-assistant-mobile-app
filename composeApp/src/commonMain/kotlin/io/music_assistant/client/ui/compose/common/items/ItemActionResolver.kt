package io.music_assistant.client.ui.compose.common.items

import io.music_assistant.client.data.model.client.ClickContext
import io.music_assistant.client.data.model.client.QueueOption
import io.music_assistant.client.data.supportsInterleaveIntoQueue
import io.music_assistant.client.data.model.client.items.Album
import io.music_assistant.client.data.model.client.items.AppMediaItem
import io.music_assistant.client.data.model.client.items.Audiobook
import io.music_assistant.client.data.model.client.items.PodcastEpisode
import io.music_assistant.client.data.model.client.items.RadioStation
import io.music_assistant.client.data.model.client.items.Track
import io.music_assistant.client.settings.MenuActionOption

/** Item types accepted by playlist edits (server contract, not a UI choice). */
val AppMediaItem.supportsAddToPlaylist: Boolean
    get() = this is Track || this is Album || this is RadioStation ||
            this is PodcastEpisode || this is Audiobook

/**
 * Long-click menu shows the union: playback block (Play Now / Insert Next & Play / Insert Next /
 * Add to Bottom / Start Radio) followed by library, favorite, playlist and progress entries.
 */
fun resolveLongClickActions(
    item: AppMediaItem,
    clickContext: ClickContext? = null,
    librarySupported: Boolean,
    canAddToPlaylist: Boolean,
    canRemoveFromPlaylist: Boolean,
    progressSupported: Boolean,
    defaultAction: ItemAction? = null,
    customizationAllowed: Boolean = false,
): List<ItemAction> = buildList {
    if (item.isPlayable) addPlaybackActions(item, clickContext)
    if (customizationAllowed) add(ItemAction.Customize)
    if (librarySupported) {
        add(if (item.isInLibrary) ItemAction.RemoveFromLibrary else ItemAction.AddToLibrary)
        if (item.isInLibrary) {
            add(if (item.favorite == true) ItemAction.Unfavorite else ItemAction.Favorite)
        }
    }
    if (canAddToPlaylist) add(ItemAction.AddToPlaylist)
    if (canRemoveFromPlaylist) add(ItemAction.RemoveFromPlaylist)
    if (progressSupported && item.isFullyPlayed()) {
        add(ItemAction.MarkUnplayed)
    } else if (progressSupported) {
        add(ItemAction.MarkPlayed)
    }
    // The action a click performs is hoisted to the top (and labeled "Default" by the menu).
}.let { actions ->
    if (defaultAction == null) {
        actions
    } else {
        listOf(defaultAction) + actions.filterNot { it == defaultAction }
    }
}

/**
 * Item detail screen play-button split-button overflow — every applicable play action
 * except [default] (which the leading button performs).
 */
fun resolvePlayButtonActions(
    item: AppMediaItem,
    default: ItemAction?,
    customizationAllowed: Boolean = false,
): List<ItemAction> =
    buildList {
        if (item.isPlayable) addPlaybackActions(item)
        if (customizationAllowed) add(ItemAction.Customize)
    }.filterNot { it == default }

/** The playback block: Play Now / Insert Next & Play / Insert Next / Add to Bottom / Start Radio. */
private fun MutableList<ItemAction>.addPlaybackActions(
    item: AppMediaItem,
    context: ClickContext? = null,
) {
    add(ItemAction.Play(QueueOption.REPLACE))
    add(ItemAction.Play(QueueOption.PLAY))
    add(ItemAction.Play(QueueOption.NEXT))
    if (item.supportsInterleaveIntoQueue) {
        add(ItemAction.InterleaveIntoQueue)
    }
    add(ItemAction.Play(QueueOption.ADD))

    if (context == ClickContext.ALBUM || context == ClickContext.PLAYLIST) {
        add(ItemAction.PlayFromHere)
    }

    if (item.canStartRadio) add(ItemAction.StartRadio)
}

/**
 * Item detail screen three-dot overflow — library/favorite/playlist. Navigation entries
 * (Go to album / artist) are appended separately at the call site via [navigationOptions].
 */
fun resolveDetailOverflowActions(
    item: AppMediaItem,
    librarySupported: Boolean,
    canAddToPlaylist: Boolean,
): List<ItemAction> = buildList {
    if (librarySupported) {
        add(if (item.isInLibrary) ItemAction.RemoveFromLibrary else ItemAction.AddToLibrary)
        if (item.isInLibrary) {
            add(if (item.favorite == true) ItemAction.Unfavorite else ItemAction.Favorite)
        }
    }
    if (canAddToPlaylist) add(ItemAction.AddToPlaylist)
}

enum class ContextMenuCallSite { LONG_PRESS, PLAY_OVERFLOW }

data class ContextMenuCallSiteFlags(
    val librarySupported: Boolean,
    val canAddToPlaylist: Boolean,
    val canRemoveFromPlaylist: Boolean,
    val progressSupported: Boolean,
    val customizationAllowed: Boolean,
)

fun applyContextMenuConfig(
    configured: List<MenuActionOption>,
    item: AppMediaItem,
    clickContext: ClickContext?,
    callSite: ContextMenuCallSite,
    flags: ContextMenuCallSiteFlags,
    defaultAction: ItemAction? = null,
): List<ItemAction> {
    val mapped = configured.mapNotNull { option ->
        if (!option.isApplicableAtCallSite(callSite, flags)) return@mapNotNull null
        option.toItemAction(item, clickContext)?.takeIf { action ->
            action.isAllowedAtCallSite(flags)
        }
    }
    val filtered = if (callSite == ContextMenuCallSite.PLAY_OVERFLOW) {
        mapped.filter { it != defaultAction }
    } else {
        mapped
    }
    return if (defaultAction == null || callSite == ContextMenuCallSite.PLAY_OVERFLOW) {
        filtered
    } else {
        listOf(defaultAction) + filtered.filterNot { it == defaultAction }
    }
}

fun resolveConfiguredLongClickActions(
    item: AppMediaItem,
    clickContext: ClickContext?,
    menuConfig: List<MenuActionOption>,
    flags: ContextMenuCallSiteFlags,
    defaultAction: ItemAction?,
): List<ItemAction> = applyContextMenuConfig(
    configured = menuConfig,
    item = item,
    clickContext = clickContext,
    callSite = ContextMenuCallSite.LONG_PRESS,
    flags = flags,
    defaultAction = defaultAction,
)

fun resolveConfiguredPlayButtonActions(
    item: AppMediaItem,
    clickContext: ClickContext?,
    menuConfig: List<MenuActionOption>,
    defaultAction: ItemAction?,
    customizationAllowed: Boolean,
): List<ItemAction> = applyContextMenuConfig(
    configured = menuConfig,
    item = item,
    clickContext = clickContext,
    callSite = ContextMenuCallSite.PLAY_OVERFLOW,
    flags = ContextMenuCallSiteFlags(
        librarySupported = false,
        canAddToPlaylist = false,
        canRemoveFromPlaylist = false,
        progressSupported = false,
        customizationAllowed = customizationAllowed,
    ),
    defaultAction = defaultAction,
)

private fun MenuActionOption.isApplicableAtCallSite(
    callSite: ContextMenuCallSite,
    flags: ContextMenuCallSiteFlags,
): Boolean = when (callSite) {
    ContextMenuCallSite.PLAY_OVERFLOW -> when (this) {
        MenuActionOption.CUSTOMIZE -> flags.customizationAllowed
        else -> toDisplayItemAction().kind == ItemAction.Kind.PLAYBACK
    }
    ContextMenuCallSite.LONG_PRESS -> true
}

private fun ItemAction.isAllowedAtCallSite(flags: ContextMenuCallSiteFlags): Boolean = when (this) {
    ItemAction.AddToPlaylist -> flags.canAddToPlaylist
    ItemAction.RemoveFromPlaylist -> flags.canRemoveFromPlaylist
    ItemAction.AddToLibrary, ItemAction.RemoveFromLibrary,
    ItemAction.Favorite, ItemAction.Unfavorite,
    -> flags.librarySupported
    ItemAction.MarkPlayed, ItemAction.MarkUnplayed -> flags.progressSupported
    ItemAction.Customize -> flags.customizationAllowed
    else -> true
}

private fun AppMediaItem.isFullyPlayed(): Boolean = when (this) {
    is PodcastEpisode -> fullyPlayed == true
    is Audiobook -> fullyPlayed == true
    else -> false
}
