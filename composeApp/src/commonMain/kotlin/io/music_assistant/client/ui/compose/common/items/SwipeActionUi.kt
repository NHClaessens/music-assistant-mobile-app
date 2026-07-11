package io.music_assistant.client.ui.compose.common.items

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.music_assistant.client.data.model.client.QueueOption
import io.music_assistant.client.data.model.client.items.AppMediaItem
import io.music_assistant.client.settings.SwipeActionOption
import io.music_assistant.client.settings.SwipeActionPrefs
import io.music_assistant.client.ui.compose.settings.SwipeActionsViewModel
import org.koin.compose.viewmodel.koinViewModel

/** Saved swipe-action prefs, loaded once from Koin near the app root. */
val LocalSwipeActionPrefs = compositionLocalOf { SwipeActionPrefs() }

@Composable
fun ProvideSwipeActionPrefs(content: @Composable () -> Unit) {
    val prefs by koinViewModel<SwipeActionsViewModel>().prefs.collectAsStateWithLifecycle()
    CompositionLocalProvider(LocalSwipeActionPrefs provides prefs, content)
}

fun SwipeActionOption.toItemAction(): ItemAction? = when (this) {
    SwipeActionOption.NOTHING -> null
    SwipeActionOption.PLAY_NOW -> ItemAction.Play(QueueOption.REPLACE)
    SwipeActionOption.INSERT_NEXT_AND_PLAY -> ItemAction.Play(QueueOption.PLAY)
    SwipeActionOption.INSERT_NEXT -> ItemAction.Play(QueueOption.NEXT)
    SwipeActionOption.ADD_TO_QUEUE -> ItemAction.Play(QueueOption.ADD)
    SwipeActionOption.START_RADIO -> ItemAction.StartRadio
    SwipeActionOption.ADD_TO_LIBRARY -> ItemAction.AddToLibrary
    SwipeActionOption.REMOVE_FROM_LIBRARY -> ItemAction.RemoveFromLibrary
    SwipeActionOption.FAVORITE -> ItemAction.Favorite
    SwipeActionOption.UNFAVORITE -> ItemAction.Unfavorite
}

/** The action a swipe actually performs for [item], or null when it does not apply. */
fun SwipeActionOption.effectiveFor(item: AppMediaItem): ItemAction? {
    val base = toItemAction() ?: return null
    return when (base) {
        is ItemAction.Play -> if (item.isPlayable) base else null
        ItemAction.StartRadio ->
            if (item.isPlayable && item.canStartRadio) base else null
        ItemAction.AddToLibrary -> if (!item.isInLibrary) base else null
        ItemAction.RemoveFromLibrary -> if (item.isInLibrary) base else null
        ItemAction.Favorite ->
            if (item.isInLibrary && item.favorite != true) base else null
        ItemAction.Unfavorite ->
            if (item.isInLibrary && item.favorite == true) base else null
        else -> null
    }
}
