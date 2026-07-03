package io.music_assistant.client.ui.compose.home.players

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.music_assistant.client.data.model.client.Lyrics
import io.music_assistant.client.data.model.client.items.Track
import io.music_assistant.client.data.repository.LyricsRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Holds lyrics for the *displayed* player's current track. Fetching is driven by
 * [onDisplayedTrackChanged]; a `null` [lyrics] value means "unknown / none" and
 * the caller hides the lyrics button. Repository-level caching makes repeated
 * calls for the same track cheap, so this VM only guards against re-issuing while
 * the track is unchanged.
 */
class LyricsViewModel(
    private val repository: LyricsRepository,
) : ViewModel() {
    private val _lyrics = MutableStateFlow<Lyrics?>(null)
    val lyrics: StateFlow<Lyrics?> = _lyrics.asStateFlow()

    private var currentKey: String? = null
    private var job: Job? = null

    fun onDisplayedTrackChanged(track: Track?) {
        val key = track?.let { it.uri ?: "${it.provider}:${it.itemId}" }
        if (key == currentKey) return
        currentKey = key
        job?.cancel()
        _lyrics.value = null
        val target = track ?: return
        job = viewModelScope.launch { _lyrics.value = repository.lyrics(target) }
    }
}
