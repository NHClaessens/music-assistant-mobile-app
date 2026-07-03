package io.music_assistant.client.data.model.client

/**
 * Lyrics for a track, as returned by the server's `metadata/get_track_lyrics`.
 *
 * [Synced] carries timestamped LRC lines (highlight/auto-scroll with playback);
 * [Plain] is an unsynced text block. The absence of lyrics is modelled as a
 * `null` result at the repository boundary rather than a variant here.
 */
sealed interface Lyrics {
    data class Synced(val lines: List<LrcLine>) : Lyrics
    data class Plain(val text: String) : Lyrics
}

/** A single timestamped LRC line. [timeMs] is the offset from track start. */
data class LrcLine(val timeMs: Long, val text: String)
