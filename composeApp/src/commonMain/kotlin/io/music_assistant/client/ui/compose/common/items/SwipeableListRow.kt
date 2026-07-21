package io.music_assistant.client.ui.compose.common.items

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.stevdza_san.swipeable.Swipeable
import com.stevdza_san.swipeable.domain.ActionCustomization
import com.stevdza_san.swipeable.domain.HapticFeedbackConfig
import com.stevdza_san.swipeable.domain.SwipeAction
import com.stevdza_san.swipeable.domain.SwipeBackground
import com.stevdza_san.swipeable.domain.SwipeBehavior
import com.stevdza_san.swipeable.domain.SwipeDirection
import io.music_assistant.client.data.model.client.QueueOption
import org.jetbrains.compose.resources.stringResource

/**
 * Wraps a list row with swipe-to-action gestures.
 *
 * [onSwipeLeftAction] / [onSwipeRightAction] follow the user's finger direction. The underlying
 * Swipeable-KMP API names actions by the screen edge they appear on (opposite to the gesture).
 */
@Composable
fun SwipeableListRow(
    onSwipeLeftAction: ItemAction?,
    onSwipeRightAction: ItemAction?,
    onAction: (ItemAction) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    if (onSwipeLeftAction == null && onSwipeRightAction == null) {
        content()
        return
    }

    // Swipe right (finger →) reveals the left-edge action; swipe left (←) reveals the right-edge action.
    val leftEdgeAction = onSwipeRightAction?.let { toSwipeAction(it, onAction) }
    val rightEdgeAction = onSwipeLeftAction?.let { toSwipeAction(it, onAction) }

    // Swipeable-KMP names directions after the screen edge revealed, not the finger:
    // SwipeDirection.LEFT  = finger moves right (→), reveals leftDismissAction
    // SwipeDirection.RIGHT = finger moves left  (←), reveals rightDismissAction
    val direction = when {
        leftEdgeAction != null && rightEdgeAction != null -> SwipeDirection.BOTH
        leftEdgeAction != null -> SwipeDirection.LEFT
        else -> SwipeDirection.RIGHT
    }

    Swipeable(
        modifier = modifier.fillMaxWidth(),
        behavior = SwipeBehavior.DISMISS,
        direction = direction,
        threshold = 0.25f,
        hapticFeedbackConfig = HapticFeedbackConfig.Default,
        leftDismissAction = leftEdgeAction,
        rightDismissAction = rightEdgeAction,
        leftBackground = SwipeBackground.solid(
            leftEdgeAction?.customization?.containerColor ?: Color.Transparent,
        ),
        rightBackground = SwipeBackground.solid(
            rightEdgeAction?.customization?.containerColor ?: Color.Transparent,
        ),
    ) {
        content()
    }
}

@Composable
private fun toSwipeAction(action: ItemAction, onAction: (ItemAction) -> Unit): SwipeAction {
    val color = swipeActionColor(action)
    return SwipeAction(
        customization = ActionCustomization(
            icon = action.icon(null),
            iconColor = MaterialTheme.colorScheme.onPrimary,
            containerColor = color,
        ),
        label = stringResource(action.title(null)),
        onAction = { onAction(action) },
    )
}

@Composable
private fun swipeActionColor(action: ItemAction): Color = when (action) {
    is ItemAction.Play -> when (action.queueOption) {
        QueueOption.REPLACE -> MaterialTheme.colorScheme.primary
        QueueOption.PLAY -> MaterialTheme.colorScheme.tertiary
        QueueOption.NEXT -> MaterialTheme.colorScheme.secondary
        QueueOption.REPLACE_NEXT -> MaterialTheme.colorScheme.secondary
        QueueOption.ADD -> MaterialTheme.colorScheme.primaryContainer
    }
    ItemAction.InterleaveIntoQueue -> MaterialTheme.colorScheme.secondary
    ItemAction.StartRadio -> MaterialTheme.colorScheme.tertiary
    ItemAction.AddToLibrary -> MaterialTheme.colorScheme.secondary
    ItemAction.RemoveFromLibrary -> MaterialTheme.colorScheme.error
    ItemAction.Favorite -> MaterialTheme.colorScheme.errorContainer
    ItemAction.Unfavorite -> MaterialTheme.colorScheme.surfaceVariant
    else -> MaterialTheme.colorScheme.primary
}
