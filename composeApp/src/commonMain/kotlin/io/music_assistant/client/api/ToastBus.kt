package io.music_assistant.client.api

import io.music_assistant.client.ui.compose.common.ToastDuration
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow

/**
 * App-wide bus for user-visible confirmation toasts.
 *
 * Backed by a buffered [Channel] (same rationale as [ErrorMessageBus]) so messages
 * emitted before the UI collector attaches are not dropped.
 */
class ToastBus {
    data class Message(
        val text: String,
        val duration: ToastDuration = ToastDuration.SHORT,
    )

    private val channel = Channel<Message>(capacity = Channel.BUFFERED)
    val messages = channel.receiveAsFlow()

    fun show(message: String, duration: ToastDuration = ToastDuration.SHORT) {
        channel.trySend(Message(message, duration))
    }
}
