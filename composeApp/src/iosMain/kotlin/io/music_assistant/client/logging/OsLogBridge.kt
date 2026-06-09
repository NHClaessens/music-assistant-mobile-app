package io.music_assistant.client.logging

import co.touchlab.kermit.LogWriter
import co.touchlab.kermit.Severity

/**
 * Swift-side `os.Logger` sink. Implemented in `iosApp` and registered via
 * [OsLogSinkProvider] at startup, mirroring `PlatformPlayerProvider`.
 *
 * Kotlin can't express `os.Logger`'s `privacy:` interpolation, so the Swift side
 * owns the actual logging and privacy decision; Kotlin only forwards the line.
 *
 * @param severity Kermit [Severity.name] (e.g. "Info"); Swift maps it to an `OSLogType`.
 */
interface OsLogSink {
    fun log(severity: String, tag: String, message: String)
}

/** Bridge point: Swift assigns its [OsLogSink] here (DEBUG builds only). */
object OsLogSinkProvider {
    var sink: OsLogSink? = null
}

/** Kermit [LogWriter] forwarding to the Swift [OsLogSink]; no-ops until a sink is set. */
object OsLogBridgeWriter : LogWriter() {
    override fun log(severity: Severity, message: String, tag: String, throwable: Throwable?) {
        val sink = OsLogSinkProvider.sink ?: return
        val line = if (throwable != null) "$message\n${throwable.stackTraceToString()}" else message
        sink.log(severity.name, tag, line)
    }
}
