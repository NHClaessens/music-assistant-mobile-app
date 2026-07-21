package io.music_assistant.client.ui.compose.settings

import androidx.lifecycle.ViewModel
import io.music_assistant.client.data.model.client.ItemKind
import io.music_assistant.client.settings.MenuActionOption
import io.music_assistant.client.settings.SettingsRepository

class ContextMenuActionsViewModel(
    private val settingsRepository: SettingsRepository,
) : ViewModel() {
    val actions = settingsRepository.contextMenuActions

    fun save(kind: ItemKind, actions: List<MenuActionOption>) =
        settingsRepository.setContextMenuActions(kind, actions)
}
