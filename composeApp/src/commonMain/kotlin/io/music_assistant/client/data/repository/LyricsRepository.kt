package io.music_assistant.client.data.repository

import io.music_assistant.client.api.Request
import io.music_assistant.client.api.ServiceClient
import io.music_assistant.client.data.model.client.Lyrics
import io.music_assistant.client.data.model.client.items.Track
import io.music_assistant.client.utils.LrcParser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

/**
 * Resolves per-track lyrics, preferring the lyrics the server already ships in the
 * track's own [io.music_assistant.client.data.model.client.Metadata]; only when those
 * are absent does it fall back to a `metadata/get_track_lyrics` request. Both sources
 * feed the same `(lyrics, lrc_lyrics)` → [Lyrics] mapping (`null` == no lyrics).
 *
 * The API fallback is memoized for the app session, keyed by track identity, and
 * concurrent requests for the same track share one in-flight fetch. Transport failures
 * are evicted so a later track-change can retry rather than caching an error forever.
 */
class LyricsRepository(
    private val apiClient: ServiceClient,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val mutex = Mutex()
    private val cache = mutableMapOf<String, Deferred<Lyrics?>>()

    suspend fun lyrics(track: Track): Lyrics? {
        // Fast path: lyrics embedded in the item's metadata — no round-trip, no caching needed.
        fromMetadata(track)?.let { return it }

        val key = track.uri ?: "${track.provider}:${track.itemId}"
        val deferred = mutex.withLock {
            cache.getOrPut(key) { scope.async { fetch(track) } }
        }
        return try {
            deferred.await()
        } catch (_: Throwable) {
            mutex.withLock { cache.remove(key) } // transient failure → retryable
            null
        }
    }

    private fun fromMetadata(track: Track): Lyrics? =
        track.metadata?.let { toLyrics(plain = it.lyrics, lrc = it.lrcLyrics) }

    private suspend fun fetch(track: Track): Lyrics? {
        // getOrThrow so transport failures propagate to the caller's evict path;
        // a successful-but-empty response is a legitimate "no lyrics" (cached).
        val answer = apiClient.sendRequest(Request.Metadata.getTrackLyrics(track)).getOrThrow()
        val tuple = answer.result as? JsonArray ?: return null
        return toLyrics(
            plain = tuple.getOrNull(0)?.jsonPrimitive?.contentOrNull,
            lrc = tuple.getOrNull(1)?.jsonPrimitive?.contentOrNull,
        )
    }

    /** Maps a server `(lyrics, lrc_lyrics)` pair into [Lyrics], preferring timestamped LRC. */
    private fun toLyrics(plain: String?, lrc: String?): Lyrics? = when {
        !lrc.isNullOrBlank() ->
            LrcParser.parse(lrc).takeIf { it.isNotEmpty() }
                ?.let { Lyrics.Synced(it) }
                ?: Lyrics.Plain(lrc)
        !plain.isNullOrBlank() -> Lyrics.Plain(plain)
        else -> null
    }
}
