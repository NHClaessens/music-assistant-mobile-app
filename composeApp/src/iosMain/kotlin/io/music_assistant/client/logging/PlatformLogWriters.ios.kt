@file:OptIn(kotlin.experimental.ExperimentalNativeApi::class)

package io.music_assistant.client.logging

import co.touchlab.kermit.LogWriter
import co.touchlab.kermit.platformLogWriter
import kotlin.native.Platform

/**
 * DEBUG: the public `os.Logger` bridge ([OsLogBridgeWriter]) replaces Kermit's
 * stock writer, whose `NSLog("%s", …)` renders every line as `<private>`.
 *
 * Release/TestFlight: keep the stock writer unchanged
 **/

actual fun platformLogWriters(): List<LogWriter> =
    if (Platform.isDebugBinary) listOf(OsLogBridgeWriter) else listOf(platformLogWriter())
