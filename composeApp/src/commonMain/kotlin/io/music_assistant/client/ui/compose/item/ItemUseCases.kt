package io.music_assistant.client.ui.compose.item

import io.music_assistant.client.api.Request
import io.music_assistant.client.data.model.client.items.AppMediaItem
import io.music_assistant.client.data.model.client.items.Artist
import io.music_assistant.client.data.model.server.ProviderMapping
import io.music_assistant.client.data.repository.MediaItemRepository

object ItemUseCases {
    suspend inline fun <reified T : AppMediaItem> fetchArtistItemsAcrossProviders(
        mediaItemRepository: MediaItemRepository,
        artist: Artist,
        request: (itemId: String, providerInstance: String) -> Request,
    ): ItemsWithMappings<T>? {
        val results = artist.providerMappings!!.map {
            val itemId = it.itemId
            val providerInstance = it.providerInstance
            val result = mediaItemRepository.fetchMediaItems(request(itemId, providerInstance))

            val albums = result.getOrNull()?.filterIsInstance<T>() ?: emptyList()
            it to albums
        }.firstOrNull { it.second.isNotEmpty() }

        return if (results != null) {
            ItemsWithMappings(results.second, results.first)
        } else {
            null
        }
    }

    data class ItemsWithMappings<T : AppMediaItem>(
        val items: List<T>,
        val mapping: ProviderMapping,
    )
}
