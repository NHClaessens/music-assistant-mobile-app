package io.music_assistant.client.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Server-synced `audiobook_chapter_progress` preference; defaults to true.
 * Refresh from `auth/me` on connect so all chapter surfaces share one gate.
 */
class ChapterProgressPreference {
    private val _enabled = MutableStateFlow(true)

    /** Reactive view for UI and flow pipelines. */
    val enabled: StateFlow<Boolean> = _enabled

    /** Synchronous read for command-path call sites. */
    val isEnabled: Boolean get() = _enabled.value

    /** Update from a fetched server value; absent means the default (true). */
    fun update(value: Boolean?) {
        _enabled.value = value ?: true
    }
}
