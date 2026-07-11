package io.music_assistant.client.ui.compose.settings

import androidx.lifecycle.ViewModel
import io.music_assistant.client.settings.SettingsRepository
import io.music_assistant.client.settings.SwipeActionPrefs

class SwipeActionsViewModel(
    private val settingsRepository: SettingsRepository,
) : ViewModel() {
    val prefs = settingsRepository.swipeActions

    fun save(prefs: SwipeActionPrefs) = settingsRepository.setSwipeActions(prefs)
}
