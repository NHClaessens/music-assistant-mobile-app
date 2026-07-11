package io.music_assistant.client.settings

import com.russhwolf.settings.MapSettings
import io.music_assistant.client.settings.SwipeActionOption.ADD_TO_QUEUE
import io.music_assistant.client.settings.SwipeActionOption.INSERT_NEXT
import io.music_assistant.client.settings.SwipeActionOption.NOTHING
import kotlin.test.Test
import kotlin.test.assertEquals

class SwipeActionsRepoTest {
    @Test
    fun `defaults to nothing on both sides when nothing stored`() {
        val repo = SettingsRepository(MapSettings())
        assertEquals(SwipeActionPrefs(), repo.swipeActions.value)
    }

    @Test
    fun `set then read back swipe prefs`() {
        val repo = SettingsRepository(MapSettings())
        repo.setSwipeActions(SwipeActionPrefs(onSwipeLeft = INSERT_NEXT, onSwipeRight = ADD_TO_QUEUE))
        assertEquals(INSERT_NEXT, repo.swipeActions.value.onSwipeLeft)
        assertEquals(ADD_TO_QUEUE, repo.swipeActions.value.onSwipeRight)
    }

    @Test
    fun `survives a fresh repository over the same storage`() {
        val settings = MapSettings()
        SettingsRepository(settings).setSwipeActions(
            SwipeActionPrefs(onSwipeLeft = NOTHING, onSwipeRight = ADD_TO_QUEUE),
        )
        val reopened = SettingsRepository(settings)
        assertEquals(ADD_TO_QUEUE, reopened.swipeActions.value.onSwipeRight)
    }

    @Test
    fun `malformed stored value degrades to nothing`() {
        val settings = MapSettings().apply {
            putString("swipe_action_on_swipe_left", "NOT_A_REAL_ACTION")
            putString("swipe_action_on_swipe_right", ADD_TO_QUEUE.name)
        }
        val repo = SettingsRepository(settings)
        assertEquals(NOTHING, repo.swipeActions.value.onSwipeLeft)
        assertEquals(ADD_TO_QUEUE, repo.swipeActions.value.onSwipeRight)
    }

    @Test
    fun `legacy keys are swapped on read so gesture mapping stays consistent`() {
        val settings = MapSettings().apply {
            putString("swipe_action_left", ADD_TO_QUEUE.name)
            putString("swipe_action_right", INSERT_NEXT.name)
        }
        val repo = SettingsRepository(settings)
        assertEquals(INSERT_NEXT, repo.swipeActions.value.onSwipeLeft)
        assertEquals(ADD_TO_QUEUE, repo.swipeActions.value.onSwipeRight)
    }
}
