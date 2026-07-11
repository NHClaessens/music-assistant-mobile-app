package io.music_assistant.client.ui.compose.common

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

enum class ToastDuration(val millis: Long) {
    SHORT(2000L),
    LONG(3500L),
}

data class ToastData(
    val id: Long,
    val message: String,
    val duration: ToastDuration = ToastDuration.SHORT,
)

class ToastState {
    private val _toasts = mutableStateListOf<ToastData>()
    val toasts: List<ToastData> get() = _toasts

    private var nextId = 0L

    fun showToast(message: String, duration: ToastDuration = ToastDuration.SHORT) {
        _toasts.add(ToastData(id = nextId++, message = message, duration = duration))
        while (_toasts.size > MAX_VISIBLE_TOASTS) {
            _toasts.removeAt(0)
        }
    }

    fun dismissToast(id: Long) {
        _toasts.removeAll { it.id == id }
    }

    /** @deprecated Prefer [dismissToast]; clears all queued toasts. */
    fun hideToast() {
        _toasts.clear()
    }

    private companion object {
        private const val MAX_VISIBLE_TOASTS = 5
    }
}

@Composable
fun rememberToastState(): ToastState = remember { ToastState() }

@Composable
fun ToastHost(
    toastState: ToastState,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom)),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            toastState.toasts.forEach { toast ->
                key(toast.id) {
                    ToastEntry(
                        toast = toast,
                        onDismiss = { toastState.dismissToast(toast.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun ToastEntry(
    toast: ToastData,
    onDismiss: () -> Unit,
) {
    var visible by remember(toast.id) { mutableStateOf(true) }

    LaunchedEffect(toast.id) {
        delay(toast.duration.millis)
        visible = false
    }

    LaunchedEffect(visible) {
        if (!visible) {
            delay(EXIT_ANIMATION_MS)
            onDismiss()
        }
    }

    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically(initialOffsetY = { it / 2 }) + fadeIn(),
        exit = slideOutHorizontally { it } + fadeOut(),
    ) {
        SwipeDismissibleToast(
            message = toast.message,
            onDismiss = { visible = false },
        )
    }
}

@Composable
private fun SwipeDismissibleToast(
    message: String,
    onDismiss: () -> Unit,
) {
    val offsetX = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()

    ToastItem(
        message = message,
        modifier = Modifier
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragEnd = {
                        scope.launch {
                            if (abs(offsetX.value) > DISMISS_THRESHOLD_PX) {
                                onDismiss()
                            } else {
                                offsetX.animateTo(0f, animationSpec = tween(200))
                            }
                        }
                    },
                    onHorizontalDrag = { _, dragAmount ->
                        scope.launch {
                            offsetX.snapTo(offsetX.value + dragAmount)
                        }
                    },
                )
            }
            .offset { IntOffset(offsetX.value.roundToInt(), 0) },
    )
}
@Composable
private fun ToastItem(
    message: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .background(
                color = Color.Black.copy(alpha = 0.8f),
                shape = RoundedCornerShape(8.dp),
            )
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Text(
            text = message,
            color = Color.White,
        )
    }
}

private const val EXIT_ANIMATION_MS = 250L
private const val DISMISS_THRESHOLD_PX = 96f
