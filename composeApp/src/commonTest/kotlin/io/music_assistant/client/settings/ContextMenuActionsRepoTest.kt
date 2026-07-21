package io.music_assistant.client.settings

import com.russhwolf.settings.MapSettings
import io.music_assistant.client.data.model.client.ItemKind
import kotlin.test.Test
import kotlin.test.assertEquals

class ContextMenuActionsRepoTest {
    @Test
    fun `defaults to empty when nothing stored`() {
        val repo = SettingsRepository(MapSettings())
        assertEquals(emptyMap(), repo.contextMenuActions.value)
    }

    @Test
    fun `set then read back actions for kind`() {
        val repo = SettingsRepository(MapSettings())
        repo.setContextMenuActions(
            ItemKind.TRACK,
            listOf(MenuActionOption.ADD_TO_QUEUE, MenuActionOption.PLAY_NOW),
        )
        assertEquals(
            listOf(MenuActionOption.ADD_TO_QUEUE, MenuActionOption.PLAY_NOW),
            repo.contextMenuActions.value[ItemKind.TRACK],
        )
    }

    @Test
    fun `saving one kind preserves other kinds`() {
        val repo = SettingsRepository(MapSettings())
        repo.setContextMenuActions(ItemKind.TRACK, listOf(MenuActionOption.PLAY_NOW))
        repo.setContextMenuActions(ItemKind.ALBUM, listOf(MenuActionOption.INTERLEAVE))

        assertEquals(
            listOf(MenuActionOption.PLAY_NOW),
            repo.contextMenuActions.value[ItemKind.TRACK],
        )
        assertEquals(
            listOf(MenuActionOption.INTERLEAVE),
            repo.contextMenuActions.value[ItemKind.ALBUM],
        )
    }

    @Test
    fun `legacy nested json migrates to flat per-kind lists`() {
        val settings = MapSettings().apply {
            putString(
                "context_menu_actions",
                """{"TRACK":{"HOME":["ADD_TO_QUEUE","PLAY_NOW"],"SEARCH":["INSERT_NEXT"]},"ALBUM":{"DETAIL":["INTERLEAVE"]}}""",
            )
        }
        val repo = SettingsRepository(settings)
        assertEquals(
            listOf(MenuActionOption.ADD_TO_QUEUE, MenuActionOption.PLAY_NOW),
            repo.contextMenuActions.value[ItemKind.TRACK],
        )
        assertEquals(
            listOf(MenuActionOption.INTERLEAVE),
            repo.contextMenuActions.value[ItemKind.ALBUM],
        )
    }

    @Test
    fun `malformed stored json degrades to empty`() {
        val settings = MapSettings().apply { putString("context_menu_actions", "{bad json") }
        assertEquals(emptyMap(), SettingsRepository(settings).contextMenuActions.value)
    }
}
