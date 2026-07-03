package io.music_assistant.client.utils

import io.music_assistant.client.data.model.client.LrcLine

/**
 * Minimal LRC parser. Handles standard line-level timestamps
 * `[mm:ss]`, `[mm:ss.xx]`, `[mm:ss.xxx]` (also tolerating the `:` fraction
 * separator some encoders emit), including multiple timestamps on one line
 * (`[00:12.00][00:47.00] chorus` → two lines). ID3-style metadata tags
 * (`[ar:]`, `[ti:]`, `[offset:]`, …) don't match the numeric time shape and
 * are ignored. Enhanced word-level `<..>` tags are not interpreted (their
 * surrounding text is kept verbatim).
 *
 * Returns lines sorted by [LrcLine.timeMs]. An empty result signals "not
 * parseable as synced LRC" — callers should fall back to plain text.
 */
object LrcParser {

    // Leading timestamp tag: minutes:seconds with an optional 1–3 digit fraction.
    private val timeTag = Regex("""\[(\d{1,2}):(\d{2})(?:[.:](\d{1,3}))?]""")

    fun parse(lrc: String): List<LrcLine> {
        val lines = mutableListOf<LrcLine>()
        for (raw in lrc.lineSequence()) {
            // Collect the run of timestamp tags anchored at the line start.
            val stamps = mutableListOf<Long>()
            var cursor = 0
            while (true) {
                val match = timeTag.matchAt(raw, cursor) ?: break
                stamps += match.timeMs()
                cursor = match.range.last + 1
            }
            if (stamps.isEmpty()) continue // metadata tag or plain line — skip
            val text = raw.substring(cursor).trim()
            stamps.forEach { ms -> lines += LrcLine(ms, text) }
        }
        return lines.sortedBy { it.timeMs }
    }

    private fun MatchResult.timeMs(): Long {
        val (min, sec, frac) = destructured
        val fractionMs = when (frac.length) {
            0 -> 0
            1 -> frac.toInt() * 100
            2 -> frac.toInt() * 10
            else -> frac.take(3).toInt()
        }
        return min.toLong() * 60_000 + sec.toLong() * 1_000 + fractionMs
    }
}
