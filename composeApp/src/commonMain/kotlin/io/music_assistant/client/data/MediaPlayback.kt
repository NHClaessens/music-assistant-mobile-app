package io.music_assistant.client.data

import io.music_assistant.client.api.Request
import io.music_assistant.client.api.ServiceClient
import io.music_assistant.client.data.model.client.PlayerData
import io.music_assistant.client.data.model.client.QueueOption
import io.music_assistant.client.data.model.client.items.Album
import io.music_assistant.client.data.model.client.items.AppMediaItem
import io.music_assistant.client.data.model.client.items.Artist
import io.music_assistant.client.data.model.client.items.Genre
import io.music_assistant.client.data.model.client.items.Playlist
import io.music_assistant.client.data.model.client.items.Podcast
import io.music_assistant.client.data.repository.MediaItemRepository

/** Whether [interleave into queue] is meaningful — multi-track containers only. */
val AppMediaItem.supportsInterleaveIntoQueue: Boolean
    get() = this is Album || this is Playlist || this is Artist || this is Podcast

/**
 * Alternates [newItems] and [existingItems]: new0, old0, new1, old1, … then appends the
 * remainder of whichever list is longer.
 */
fun interleaveLists(newItems: List<String>, existingItems: List<String>): List<String> {
    if (existingItems.isEmpty()) return newItems
    if (newItems.isEmpty()) return existingItems
    val result = ArrayList<String>(newItems.size + existingItems.size)
    var newIndex = 0
    var existingIndex = 0
    while (newIndex < newItems.size && existingIndex < existingItems.size) {
        result.add(newItems[newIndex++])
        result.add(existingItems[existingIndex++])
    }
    if (newIndex < newItems.size) {
        result.addAll(newItems.subList(newIndex, newItems.size))
    }
    if (existingIndex < existingItems.size) {
        result.addAll(existingItems.subList(existingIndex, existingItems.size))
    }
    return result
}

/** URIs of queue items after the currently playing/loaded item. */
fun upcomingQueueUris(player: PlayerData?): List<String> {
    val items = player?.queueItems.orEmpty()
    if (items.isEmpty()) return emptyList()
    val currentIndex = player?.queueInfo?.currentIndex
        ?: player?.queueInfo?.currentItem?.id?.let { currentId ->
            items.indexOfFirst { it.id == currentId }.takeIf { it >= 0 }
        }
        ?: return emptyList()
    return items.drop(currentIndex + 1).mapNotNull { it.track.uri }
}

/** Expands a browsable container into ordered playable URIs, or null when unsupported/empty. */
suspend fun expandContainerToPlayableUris(
    item: AppMediaItem,
    repository: MediaItemRepository,
): List<String>? {
    val request = when (item) {
        is Album -> Request.Album.getTracks(item.itemId, item.provider)
        is Playlist -> Request.Playlist.getTracks(item.itemId, item.provider)
        is Artist -> Request.Artist.getTracks(item.itemId, item.provider)
        is Podcast -> Request.Podcast.getEpisodes(item.itemId, item.provider)
        else -> return null
    }
    return repository.fetchMediaItems(request)
        .getOrNull()
        ?.mapNotNull { subItem -> subItem.mediaUri }
        ?.takeIf { it.isNotEmpty() }
}

/**
 * Plays or enqueues [item] on [player]'s queue. When [interleave] is true, expands a
 * multi-track container, zips it with the upcoming queue, and uses [QueueOption.REPLACE_NEXT].
 *
 * Shuffle is turned off first when enabled: the server shuffles any multi-item insert when
 * shuffle is on, which would destroy the intended alternating order.
 */
suspend fun playMediaItem(
    apiClient: ServiceClient,
    player: PlayerData?,
    mediaItemRepository: MediaItemRepository,
    item: AppMediaItem,
    option: QueueOption,
    radioMode: Boolean,
    startItem: String? = null,
    interleave: Boolean = false,
) {
    val queueId = player?.queueOrPlayerId ?: return
    val effectiveRadioMode = radioMode && item !is Genre

    if (interleave) {
        val newUris = expandContainerToPlayableUris(item, mediaItemRepository) ?: return
        // Capture display order before any server-side reordering.
        val upcoming = upcomingQueueUris(player)
        val media = interleaveLists(newUris, upcoming)
        if (player.queueInfo?.shuffleEnabled == true) {
            apiClient.sendRequest(Request.Queue.setShuffle(queueId = queueId, enabled = false))
        }
        apiClient.sendRequest(
            Request.Library.play(
                media = media,
                queueOrPlayerId = queueId,
                option = QueueOption.REPLACE_NEXT,
                radioMode = effectiveRadioMode,
            ),
        )
        return
    }

    val mediaUri = item.mediaUri ?: return
    apiClient.sendRequest(
        Request.Library.play(
            media = listOf(mediaUri),
            queueOrPlayerId = queueId,
            option = option,
            radioMode = effectiveRadioMode,
            startItem = startItem,
        ),
    )
}
