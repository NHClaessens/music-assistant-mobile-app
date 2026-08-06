package io.music_assistant.client.data.repository

import io.music_assistant.client.api.APICommands
import io.music_assistant.client.api.Answer
import io.music_assistant.client.api.Request
import io.music_assistant.client.data.factory.MediaItemFactory
import io.music_assistant.client.data.model.client.items.Track
import io.music_assistant.client.data.model.server.StubServiceClient
import io.music_assistant.client.data.model.server.events.Event
import io.music_assistant.client.utils.myJson
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * [MediaItemRepository.fetchRecommendationFolders] must bridge both shapes of
 * the `music/recommendations` response: rows with their items embedded (servers
 * before 2.10), and item-less rows (2.10+) whose contents come from a per-row
 * `music/recommendations/items` call.
 */
class RecommendationFoldersCompatTest {
    private fun folderJson(itemId: String, provider: String, itemsJson: String?) = """
        {"item_id":"$itemId","provider":"$provider","name":"Row $itemId",
         "media_type":"folder","uri":null${itemsJson?.let { ""","items":$it""" } ?: ""}}
    """.trimIndent()

    private fun trackJson(itemId: String) = """
        {"item_id":"$itemId","provider":"library","name":"Track $itemId",
         "media_type":"track","is_playable":true}
    """.trimIndent()

    private class FakeClient(
        private val rowsJson: String,
        private val itemsJsonFor: (provider: String, itemId: String) -> Result<String>,
        private val rowsError: Exception? = null,
    ) : StubServiceClient() {
        val itemsRequests = mutableListOf<Pair<String, String>>()

        override val events: Flow<Event<out Any>> = emptyFlow()

        override suspend fun sendRequest(request: Request): Result<Answer> =
            when (request.command) {
                APICommands.MUSIC_RECOMMENDATIONS ->
                    rowsError?.let { Result.failure(it) } ?: Result.success(answer(rowsJson))

                APICommands.MUSIC_RECOMMENDATIONS_ITEMS -> {
                    val args = request.args ?: fail("items request missing args")
                    val provider = args["provider"]?.jsonPrimitive?.content
                        ?: fail("items request missing provider arg")
                    val itemId = args["item_id"]?.jsonPrimitive?.content
                        ?: fail("items request missing item_id arg")
                    itemsRequests += provider to itemId
                    itemsJsonFor(provider, itemId).map(::answer)
                }

                else -> fail("unexpected command ${request.command}")
            }

        private fun answer(resultJson: String) = Answer(
            buildJsonObject {
                put("message_id", "test")
                put("result", myJson.parseToJsonElement(resultJson))
            },
        )
    }

    private fun repository(client: FakeClient) =
        MediaItemRepository(client, MediaItemFactory(client))

    @Test
    fun embeddedRowItemsAreUsedDirectly() = runTest {
        val client = FakeClient(
            rowsJson = """
                [${folderJson("row1", "library", "[${trackJson("t1")}]")},
                 ${folderJson("row2", "spotify", "[]")}]
            """.trimIndent(),
            itemsJsonFor = { _, _ -> fail("embedded-items response must not trigger items calls") },
        )

        val folders = repository(client).fetchRecommendationFolders().getOrThrow()

        assertEquals(listOf("row1", "row2"), folders.map { it.itemId })
        assertEquals(listOf("t1"), folders[0].items?.map { it.itemId })
        assertTrue(client.itemsRequests.isEmpty())
    }

    @Test
    fun itemLessRowsGetItemsFetchedPerRow() = runTest {
        val client = FakeClient(
            rowsJson = """
                [${folderJson("row1", "library", "[]")},
                 ${folderJson("row2", "spotify", null)}]
            """.trimIndent(),
            itemsJsonFor = { _, itemId -> Result.success("[${trackJson("item-of-$itemId")}]") },
        )

        val folders = repository(client).fetchRecommendationFolders().getOrThrow()

        assertEquals(
            setOf("library" to "row1", "spotify" to "row2"),
            client.itemsRequests.toSet(),
        )
        assertEquals(listOf("item-of-row1"), folders[0].items?.map { it.itemId })
        assertEquals(listOf("item-of-row2"), folders[1].items?.map { it.itemId })
        assertTrue(folders[0].items?.single() is Track)
    }

    @Test
    fun failedItemsFetchDegradesToEmptyRow() = runTest {
        val client = FakeClient(
            rowsJson = """
                [${folderJson("row1", "library", "[]")},
                 ${folderJson("row2", "spotify", "[]")}]
            """.trimIndent(),
            itemsJsonFor = { provider, itemId ->
                if (provider == "spotify") {
                    Result.failure(IllegalStateException("row fetch failed"))
                } else {
                    Result.success("[${trackJson("item-of-$itemId")}]")
                }
            },
        )

        val folders = repository(client).fetchRecommendationFolders().getOrThrow()

        assertEquals(listOf("item-of-row1"), folders[0].items?.map { it.itemId })
        assertEquals(emptyList(), folders[1].items)
    }

    // A server that embeds items has no items command; a failing RPC there raises
    // a user-visible error toast per call. The first row is probed alone so that
    // worst case is a single failing call, not one per row.
    @Test
    fun failedProbeReturnsRowsAsIsWithoutFurtherCalls() = runTest {
        val client = FakeClient(
            rowsJson = """
                [${folderJson("row1", "library", "[]")},
                 ${folderJson("row2", "spotify", "[]")}]
            """.trimIndent(),
            itemsJsonFor = { _, _ -> Result.failure(IllegalStateException("Unknown command")) },
        )

        val folders = repository(client).fetchRecommendationFolders().getOrThrow()

        assertEquals(listOf("library" to "row1"), client.itemsRequests)
        assertEquals(listOf("row1", "row2"), folders.map { it.itemId })
        assertTrue(folders.all { it.items.isNullOrEmpty() })
    }

    // A single populated row marks the whole response as the embedded shape;
    // its empty siblings stay empty instead of triggering per-row fetches.
    @Test
    fun mixedRowsAreTreatedAsEmbedded() = runTest {
        val client = FakeClient(
            rowsJson = """
                [${folderJson("row1", "library", "[]")},
                 ${folderJson("row2", "spotify", "[${trackJson("t1")}]")}]
            """.trimIndent(),
            itemsJsonFor = { _, _ -> fail("mixed rows must not trigger items calls") },
        )

        val folders = repository(client).fetchRecommendationFolders().getOrThrow()

        assertTrue(client.itemsRequests.isEmpty())
        assertEquals(emptyList(), folders[0].items)
        assertEquals(listOf("t1"), folders[1].items?.map { it.itemId })
    }

    @Test
    fun emptyRowListIssuesNoItemCalls() = runTest {
        val client = FakeClient(
            rowsJson = "[]",
            itemsJsonFor = { _, _ -> fail("empty row list must not trigger items calls") },
        )

        val folders = repository(client).fetchRecommendationFolders().getOrThrow()

        assertTrue(folders.isEmpty())
        assertTrue(client.itemsRequests.isEmpty())
    }

    // --- streaming (recommendationRows) behavior ---

    @Test
    fun embeddedRowsStreamASingleResolvedSnapshot() = runTest {
        val client = FakeClient(
            rowsJson = "[${folderJson("row1", "library", "[${trackJson("t1")}]")}]",
            itemsJsonFor = { _, _ -> fail("embedded-items response must not trigger items calls") },
        )

        val snapshots = repository(client).recommendationRows().toList()

        assertEquals(1, snapshots.size)
        assertTrue(snapshots.single().none { it.itemsLoading })
        assertEquals(listOf("t1"), snapshots.single()[0].folder.items?.map { it.itemId })
    }

    @Test
    fun itemLessRowsStreamLoadingThenPerRowResolution() = runTest {
        val client = FakeClient(
            rowsJson = """
                [${folderJson("row1", "library", "[]")},
                 ${folderJson("row2", "spotify", "[]")}]
            """.trimIndent(),
            itemsJsonFor = { _, itemId -> Result.success("[${trackJson("item-of-$itemId")}]") },
        )

        val snapshots = repository(client).recommendationRows().toList()

        // initial (all loading) + probe row resolved + remaining row resolved
        assertEquals(3, snapshots.size)
        // Asserted after collection completed: also pins that emitted snapshots are
        // immutable copies, not views of the mutable list later resolutions write to.
        assertTrue(snapshots.first().all { it.itemsLoading })
        val final = snapshots.last()
        assertTrue(final.none { it.itemsLoading })
        assertEquals(listOf("item-of-row1"), final[0].folder.items?.map { it.itemId })
        assertEquals(listOf("item-of-row2"), final[1].folder.items?.map { it.itemId })
    }

    @Test
    fun failedProbeStreamResolvesAllRowsAsReceived() = runTest {
        val client = FakeClient(
            rowsJson = """
                [${folderJson("row1", "library", "[]")},
                 ${folderJson("row2", "spotify", "[]")}]
            """.trimIndent(),
            itemsJsonFor = { _, _ -> Result.failure(IllegalStateException("Unknown command")) },
        )

        val snapshots = repository(client).recommendationRows().toList()

        assertEquals(2, snapshots.size)
        assertTrue(snapshots.first().all { it.itemsLoading })
        assertTrue(snapshots.last().none { it.itemsLoading })
        assertTrue(snapshots.last().all { it.folder.items.isNullOrEmpty() })
        assertEquals(listOf("library" to "row1"), client.itemsRequests)
    }

    @Test
    fun rowsCallFailureSurfacesAsErrorWithoutItemCalls() = runTest {
        val client = FakeClient(
            rowsJson = "unused",
            rowsError = IllegalStateException("rows call failed"),
            itemsJsonFor = { _, _ -> fail("failed rows call must not trigger items calls") },
        )
        val repo = repository(client)

        assertFailsWith<IllegalStateException> { repo.recommendationRows().toList() }
        assertTrue(repo.fetchRecommendationFolders().isFailure)
        assertTrue(client.itemsRequests.isEmpty())
    }
}
