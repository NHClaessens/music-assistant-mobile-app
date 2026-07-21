package io.music_assistant.client.settings

import io.music_assistant.client.data.model.client.ItemKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ContextMenuActionPrefsTest {
    @Test
    fun `default track menu includes playback and library actions`() {
        val defaults = defaultContextMenuActions(ItemKind.TRACK)
        assertTrue(MenuActionOption.PLAY_NOW in defaults)
        assertTrue(MenuActionOption.ADD_TO_QUEUE in defaults)
        assertTrue(MenuActionOption.LIBRARY in defaults)
        assertFalse(MenuActionOption.INTERLEAVE in defaults)
        assertFalse(MenuActionOption.PLAY_FROM_HERE in defaults)
    }

    @Test
    fun `default album menu includes interleave and start radio`() {
        val defaults = defaultContextMenuActions(ItemKind.ALBUM)
        assertTrue(MenuActionOption.INTERLEAVE in defaults)
        assertTrue(MenuActionOption.START_RADIO in defaults)
        assertFalse(MenuActionOption.CUSTOMIZE in defaults)
    }

    @Test
    fun `reconcile appends newly added defaults when stored config exists`() {
        val stored = listOf(
            MenuActionOption.PLAY_NOW,
            MenuActionOption.ADD_TO_QUEUE,
        )
        val reconciled = reconcileContextMenuActions(stored, ItemKind.ALBUM)
        val enabled = reconciled.filter { it.second }.map { it.first }
        assertTrue(MenuActionOption.INTERLEAVE in enabled)
        assertEquals(MenuActionOption.PLAY_NOW, enabled.first())
    }

    @Test
    fun `reconcile without stored config enables defaults only`() {
        val reconciled = reconcileContextMenuActions(null, ItemKind.TRACK)
        val disabled = reconciled.filterNot { it.second }.map { it.first }
        assertFalse(MenuActionOption.PLAY_NOW in disabled)
        assertFalse(MenuActionOption.INTERLEAVE in reconciled.map { it.first })
    }

    @Test
    fun `contextMenuActionsFor falls back to defaults`() {
        val actions = contextMenuActionsFor(emptyMap(), ItemKind.TRACK)
        assertEquals(defaultContextMenuActions(ItemKind.TRACK), actions)
    }
}
