package io.music_assistant.client.ui.compose.common.items

import io.music_assistant.client.data.model.client.ClickContext
import io.music_assistant.client.data.model.client.QueueOption
import io.music_assistant.client.data.model.client.items.AppMediaItem
import io.music_assistant.client.data.model.client.items.Audiobook
import io.music_assistant.client.data.model.client.items.Genre
import io.music_assistant.client.data.model.client.items.PodcastEpisode
import io.music_assistant.client.data.supportsInterleaveIntoQueue
import io.music_assistant.client.settings.MenuActionOption

/** Static mapping for settings labels (no concrete item). */
fun MenuActionOption.toDisplayItemAction(context: ClickContext? = null): ItemAction = when (this) {
    MenuActionOption.PLAY_NOW -> ItemAction.Play(QueueOption.REPLACE)
    MenuActionOption.INSERT_NEXT_AND_PLAY -> ItemAction.Play(QueueOption.PLAY)
    MenuActionOption.INSERT_NEXT -> ItemAction.Play(QueueOption.NEXT)
    MenuActionOption.INTERLEAVE -> ItemAction.InterleaveIntoQueue
    MenuActionOption.ADD_TO_QUEUE -> ItemAction.Play(QueueOption.ADD)
    MenuActionOption.PLAY_FROM_HERE -> ItemAction.PlayFromHere
    MenuActionOption.START_RADIO -> ItemAction.StartRadio
    MenuActionOption.LIBRARY -> ItemAction.AddToLibrary
    MenuActionOption.FAVORITE -> ItemAction.Favorite
    MenuActionOption.ADD_TO_PLAYLIST -> ItemAction.AddToPlaylist
    MenuActionOption.REMOVE_FROM_PLAYLIST -> ItemAction.RemoveFromPlaylist
    MenuActionOption.MARK_PLAYED -> ItemAction.MarkPlayed
    MenuActionOption.CUSTOMIZE -> ItemAction.Customize
}

/** Maps a configured action to a concrete menu entry for [item], or null when not applicable. */
fun MenuActionOption.toItemAction(
    item: AppMediaItem,
    clickContext: ClickContext?,
): ItemAction? = when (this) {
    MenuActionOption.PLAY_NOW -> ItemAction.Play(QueueOption.REPLACE)
    MenuActionOption.INSERT_NEXT_AND_PLAY -> ItemAction.Play(QueueOption.PLAY)
    MenuActionOption.INSERT_NEXT -> ItemAction.Play(QueueOption.NEXT)
    MenuActionOption.INTERLEAVE ->
        if (item.supportsInterleaveIntoQueue) ItemAction.InterleaveIntoQueue else null
    MenuActionOption.ADD_TO_QUEUE -> ItemAction.Play(QueueOption.ADD)
    MenuActionOption.PLAY_FROM_HERE ->
        if (clickContext == ClickContext.ALBUM || clickContext == ClickContext.PLAYLIST) {
            ItemAction.PlayFromHere
        } else {
            null
        }
    MenuActionOption.START_RADIO -> if (item.canStartRadio) ItemAction.StartRadio else null
    MenuActionOption.LIBRARY ->
        if (item is Genre) {
            null
        } else if (item.isInLibrary) {
            ItemAction.RemoveFromLibrary
        } else {
            ItemAction.AddToLibrary
        }
    MenuActionOption.FAVORITE -> when {
        item is Genre || !item.isInLibrary -> null
        item.favorite == true -> ItemAction.Unfavorite
        else -> ItemAction.Favorite
    }
    MenuActionOption.ADD_TO_PLAYLIST -> ItemAction.AddToPlaylist
    MenuActionOption.REMOVE_FROM_PLAYLIST -> ItemAction.RemoveFromPlaylist
    MenuActionOption.MARK_PLAYED -> when (item) {
        is PodcastEpisode ->
            if (item.fullyPlayed == true) ItemAction.MarkUnplayed else ItemAction.MarkPlayed
        is Audiobook ->
            if (item.fullyPlayed == true) ItemAction.MarkUnplayed else ItemAction.MarkPlayed
        else -> null
    }
    MenuActionOption.CUSTOMIZE -> ItemAction.Customize
}
