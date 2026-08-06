package io.music_assistant.client.ui.compose.home

import io.music_assistant.client.data.model.client.Shortcut
import io.music_assistant.client.data.model.client.items.AppMediaItem
import io.music_assistant.client.data.model.client.items.RecommendationFolder
import io.music_assistant.client.data.model.client.testTrack
import io.music_assistant.client.data.repository.RecommendationRow
import io.music_assistant.client.ui.compose.common.DataState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Pins the home-page row visibility rules of [getCategories]:
 * rows whose items are still loading stay visible (as placeholders), rows that
 * resolved without renderable items are hidden, duplicates collapse, and the
 * shortcuts row is appended from its own state.
 */
class HomeScreenCategoriesTest {
    private fun folder(
        itemId: String,
        provider: String = "library",
        items: List<AppMediaItem>? = null,
    ) = RecommendationFolder(
        itemId = itemId,
        provider = provider,
        name = itemId,
        uri = null,
        images = emptyMap(),
        items = items,
    )

    private fun categories(
        rows: List<RecommendationRow>,
        shortcuts: DataState<List<Shortcut>> = DataState.NoData(),
    ) = getCategories(DataState.Data(rows), shortcuts, homeRowsConfig = emptyList())

    @Test
    fun loadingRowsStayVisibleAsPlaceholders() {
        val result = categories(listOf(RecommendationRow(folder("a"), itemsLoading = true)))

        assertEquals(listOf("a"), result.map { it.first.category.id })
        assertTrue(result.single().first.loading)
    }

    @Test
    fun rowsResolvedWithoutRenderableItemsAreHidden() {
        val result = categories(
            listOf(
                RecommendationRow(folder("empty", items = emptyList()), itemsLoading = false),
                RecommendationRow(folder("itemless", items = null), itemsLoading = false),
            ),
        )

        assertTrue(result.isEmpty())
    }

    @Test
    fun rowsResolvedWithPlayableItemsAreShown() {
        val result = categories(
            listOf(
                RecommendationRow(
                    folder("a", items = listOf(testTrack())),
                    itemsLoading = false,
                ),
            ),
        )

        val row = result.single().first
        assertFalse(row.loading)
        assertEquals(1, row.category.items.size)
    }

    @Test
    fun rowsWithTheSameIdentityCollapseToOne() {
        val duplicated = RecommendationRow(
            folder("a", items = listOf(testTrack())),
            itemsLoading = false,
        )

        val result = categories(listOf(duplicated, duplicated))

        assertEquals(1, result.size)
    }

    @Test
    fun sameItemIdFromDifferentProvidersStaysDistinct() {
        val result = categories(
            listOf(
                RecommendationRow(folder("a", provider = "library"), itemsLoading = true),
                RecommendationRow(folder("a", provider = "spotify"), itemsLoading = true),
            ),
        )

        assertEquals(2, result.size)
    }

    @Test
    fun shortcutsRowIsAppendedOnTopWhenUnconfigured() {
        val result = categories(
            rows = listOf(
                RecommendationRow(
                    folder("a", items = listOf(testTrack())),
                    itemsLoading = false,
                ),
            ),
            shortcuts = DataState.Data(listOf(Shortcut(testTrack()))),
        )

        assertEquals(listOf("shortcuts", "a"), result.map { it.first.category.id })
    }

    @Test
    fun nonDataRecommendationsYieldNoRows() {
        val result = getCategories(
            recommendationsState = DataState.Loading(),
            shortcutsState = DataState.Data(listOf(Shortcut(testTrack()))),
            homeRowsConfig = emptyList(),
        )

        assertTrue(result.isEmpty())
    }
}
