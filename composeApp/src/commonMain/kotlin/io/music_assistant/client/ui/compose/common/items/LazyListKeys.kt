package io.music_assistant.client.ui.compose.common.items

import io.music_assistant.client.data.model.client.MediaType
import io.music_assistant.client.data.model.client.items.AppMediaItem
import io.music_assistant.client.data.model.client.items.PlayableItem
import kotlin.jvm.JvmName

/**
 * Stable canonical identity for an [AppMediaItem] inside a Compose `LazyColumn`,
 * `LazyRow`, or `LazyVerticalGrid`'s `key = { ... }` lambda when the rendered
 * collection cannot contain the same canonical item twice.
 *
 * Combines `mediaType`, `provider`, and `itemId` so the same canonical
 * `itemId` carried by two different providers (e.g. a track with the same
 * server-side `itemId` from Spotify and Apple Music) produces distinct
 * keys. Without this, Compose's `SubcomposeLayout.subcompose` precondition
 * (`SubcomposeLayout.kt:614` — "Key X was already used") fires during
 * fling/prefetch on iOS and the app crashes.
 *
 * Returns a structured [Triple] rather than a delimited string so the
 * encoding cannot accidentally collide on field contents — e.g. without
 * this, `(provider="apple_music", itemId="xyz")` would generate the same
 * underscore-joined string as `(provider="apple", itemId="music_xyz")`.
 * Compose's slot identity uses `equals`/`hashCode`, both of which Triple
 * implements structurally.
 *
 * Contract pinned by `LazyListKeysTest`.
 */
fun AppMediaItem.lazyListKey(): Triple<MediaType, String, String> =
    Triple(mediaType, provider, itemId)

/**
 * Keys a lazy-layout list of media items that may legitimately contain the same
 * canonical [lazyListKey] more than once, such as folders, playlists, queues, or
 * other occurrence-based server data.
 */
fun List<AppMediaItem>.lazyListOccurrenceKeys(): List<Pair<Triple<MediaType, String, String>, Int>> =
    occurrenceKeys { it.lazyListKey() }

/**
 * [PlayableItem] does not expose [AppMediaItem.mediaType], but every concrete
 * [PlayableItem] in this codebase is also an [AppMediaItem], so each item is
 * narrowed here to reuse the canonical [lazyListKey].
 */
@JvmName("lazyListOccurrenceKeysPlayable")
fun List<PlayableItem>.lazyListOccurrenceKeys(): List<Pair<Triple<MediaType, String, String>, Int>> =
    occurrenceKeys { (it as AppMediaItem).lazyListKey() }

private fun <T, K> List<T>.occurrenceKeys(keyOf: (T) -> K): List<Pair<K, Int>> {
    val occurrences = mutableMapOf<K, Int>()
    return map { item ->
        val key = keyOf(item)
        val occurrence = occurrences.getOrElse(key) { 0 }
        occurrences[key] = occurrence + 1
        key to occurrence
    }
}
