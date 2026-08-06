package io.music_assistant.client.data.repository

import co.touchlab.kermit.Logger
import io.music_assistant.client.api.Request
import io.music_assistant.client.api.ServiceClient
import io.music_assistant.client.data.factory.MediaItemFactory
import io.music_assistant.client.data.model.client.items.AppMediaItem
import io.music_assistant.client.data.model.client.items.RecommendationFolder
import io.music_assistant.client.data.model.server.SearchResult
import io.music_assistant.client.data.model.server.ServerMediaItem
import io.music_assistant.client.data.model.server.events.MediaItemAddedEvent
import io.music_assistant.client.data.model.server.events.MediaItemDeletedEvent
import io.music_assistant.client.data.model.server.events.MediaItemUpdatedEvent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.last
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.scan
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Single seam between RPC + DTO land and the UI's typed `AppMediaItem` world.
 *
 * Wraps [ServiceClient.sendRequest] + [MediaItemFactory] so ViewModels stop
 * seeing [ServerMediaItem], [SearchResult], or `MediaItem*Event` directly,
 * and centralizes the "decode payload + map to client model" boilerplate
 * that was duplicated across every list/get/search call site.
 */
class MediaItemRepository(
    private val apiClient: ServiceClient,
    private val factory: MediaItemFactory,
) {
    // Singleton-scoped app-lifetime job; only used to keep [itemChanges]
    // hot. Survives subscriber turnover so quick navigation between screens
    // doesn't churn the upstream `apiClient.events` subscription.
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /**
     * Issue [request] and decode its payload as a list of client media items.
     * Failures (RPC error, decode failure, missing payload) surface as a
     * failed [Result] so callers can still log via `result.exceptionOrNull()`.
     */
    suspend fun fetchMediaItems(request: Request): Result<List<AppMediaItem>> =
        apiClient.sendRequest(request).mapCatching { answer ->
            answer.resultAs<List<ServerMediaItem>>()
                ?.let(factory::createList)
                ?: error("Missing or undecodable media list payload")
        }

    suspend fun fetchMediaItems(request: Request, observer: (List<AppMediaItem>) -> Unit) {
        val result = fetchMediaItems(request)
        val items = result.getOrNull()

        if (items != null) {
            observer(items)
            itemChanges
                .scan(items) { items, change -> items.replacing(change.item) }
                .collect { observer(it) }
        }
    }

    /**
     * Stream the home-page recommendation rows as snapshots, bridging the two
     * response shapes `music/recommendations` can have:
     * - rows with their items embedded (servers before 2.10): a single snapshot,
     *   no row is ever [RecommendationRow.itemsLoading];
     * - rows without items (2.10+), whose contents come from a per-row
     *   `music/recommendations/items` call: an immediate snapshot with every row
     *   loading, then one snapshot per row as its items resolve.
     */
    fun recommendationRows(): Flow<List<RecommendationRow>> = channelFlow {
        val folders = fetchMediaItems(Request.Library.recommendations())
            .getOrThrow()
            .filterIsInstance<RecommendationFolder>()
        val itemLess = folders.isNotEmpty() && folders.all { it.items.isNullOrEmpty() }
        if (!itemLess) {
            send(folders.map { RecommendationRow(it, itemsLoading = false) })
            return@channelFlow
        }

        val rows = MutableList(folders.size) { RecommendationRow(folders[it], itemsLoading = true) }
        val lock = Mutex()
        suspend fun resolve(index: Int, items: List<AppMediaItem>?) = lock.withLock {
            val folder = rows[index].folder
            rows[index] = RecommendationRow(
                folder = if (items == null) folder else folder.copy(items = items),
                itemsLoading = false,
            )
            send(rows.toList())
        }

        send(rows.toList())
        val probeItems = fetchRecommendationRowItems(folders.first())
        if (probeItems == null) {
            lock.withLock {
                for (index in rows.indices) {
                    rows[index] = rows[index].copy(itemsLoading = false)
                }
                send(rows.toList())
            }
            return@channelFlow
        }
        resolve(0, probeItems)
        coroutineScope {
            for (index in 1 until folders.size) {
                launch { resolve(index, fetchRecommendationRowItems(folders[index])) }
            }
        }
    }

    /**
     * One-shot, fully-resolved variant of [recommendationRows] for consumers
     * without progressive UI (e.g. CarPlay lists).
     */
    suspend fun fetchRecommendationFolders(): Result<List<RecommendationFolder>> = try {
        Result.success(recommendationRows().last().map { it.folder })
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e)
    }

    /** Items of one recommendation row, or null when the fetch failed (logged). */
    private suspend fun fetchRecommendationRowItems(
        folder: RecommendationFolder,
    ): List<AppMediaItem>? =
        fetchMediaItems(
            Request.Library.recommendationItems(folder.provider, folder.itemId),
        ).getOrElse { error ->
            if (error is CancellationException) throw error
            Logger.w(
                "Failed fetching recommendation items for " +
                        "${folder.provider}/${folder.itemId}: $error",
            )
            null
        }

    /**
     * Issue [request] and decode its payload as a single client media item.
     * Returns `Result.success(null)` for an absent payload so a 404-style
     * "not found" stays distinguishable from a transport failure.
     */
    suspend fun fetchMediaItem(request: Request): Result<AppMediaItem?> =
        apiClient.sendRequest(request).map { answer ->
            answer.resultAs<ServerMediaItem>()?.let(factory::create)
        }

    /** Issue [request] and decode its payload as a typed [SearchResultData]. */
    suspend fun search(request: Request): Result<SearchResultData> =
        apiClient.sendRequest(request).mapCatching { answer ->
            answer.resultAs<SearchResult>()
                ?.let(factory::createSearchResult)
                ?: error("Missing or undecodable search payload")
        }

    // Client-originated changes for mutations the server doesn't echo back as an
    // event (e.g. mark played/unplayed only writes the playlog server-side). Merged
    // into [itemChanges] so the same subscribers reconcile them like server events.
    private val localChanges = MutableSharedFlow<MediaItemChange>(extraBufferCapacity = 16)

    /** Publish an optimistic, client-originated [change] to [itemChanges] subscribers. */
    fun publishLocalChange(change: MediaItemChange) {
        localChanges.tryEmit(change)
    }

    /**
     * Hot stream of library lifecycle changes mapped from the corresponding
     * server events (plus client-originated [localChanges]). ViewModels collect
     * this instead of filtering `apiClient.events` and re-running
     * `mediaItemFactory.create(...)` themselves. Replay is zero — late
     * subscribers only see future changes.
     */
    val itemChanges: SharedFlow<MediaItemChange> = merge(
        localChanges,
        apiClient.events.mapNotNull { event ->
            when (event) {
                is MediaItemAddedEvent ->
                    factory.create(event.data)?.let(MediaItemChange::Added)

                is MediaItemUpdatedEvent ->
                    factory.create(event.data)?.let(MediaItemChange::Updated)

                is MediaItemDeletedEvent ->
                    factory.create(event.data.withLibraryStripped())
                        ?.let(MediaItemChange::Deleted)

                else -> null
            }
        },
    ).shareIn(scope, SharingStarted.WhileSubscribed(replayExpirationMillis = 0), replay = 0)

    // When the server announces the deletion of a library record, the
    // *underlying* provider item still exists. Re-key the DTO to its first
    // non-library provider mapping so subscribers can fall through to the
    // source identity instead of dangling on a library id that's about to
    // 404. No-op when there's no fallback mapping.
    private fun ServerMediaItem.withLibraryStripped(): ServerMediaItem {
        val fallback = providerMappings?.firstOrNull() ?: return this
        return copy(
            itemId = fallback.itemId,
            provider = fallback.providerInstance,
            favorite = null,
            uri = "${fallback.providerInstance}://$mediaType/${fallback.itemId}",
        )
    }
}

private fun <T : AppMediaItem> List<T>.replacing(changed: T): List<T> =
    map { if (it.itemId == changed.itemId) changed else it }

/**
 * One home-page recommendation row snapshot: the folder (with whatever items are
 * known so far) plus whether its items are still being fetched. Rows from a
 * server that embeds items are never [itemsLoading].
 */
data class RecommendationRow(
    val folder: RecommendationFolder,
    val itemsLoading: Boolean,
)
