package io.music_assistant.client.settings

/**
 * User-chosen action triggered by swiping a list-row item left or right.
 * Global preference (not per item kind / context) — UI mapping lives in SwipeActionUi.kt.
 */
enum class SwipeActionOption {
    NOTHING,
    PLAY_NOW,
    INSERT_NEXT_AND_PLAY,
    INSERT_NEXT,
    ADD_TO_QUEUE,
    START_RADIO,
    ADD_TO_LIBRARY,
    REMOVE_FROM_LIBRARY,
    FAVORITE,
    UNFAVORITE,
}

data class SwipeActionPrefs(
    /** Action triggered when the user swipes their finger to the left. */
    val onSwipeLeft: SwipeActionOption = SwipeActionOption.NOTHING,
    /** Action triggered when the user swipes their finger to the right. */
    val onSwipeRight: SwipeActionOption = SwipeActionOption.NOTHING,
) {
    val isEnabled: Boolean =
        onSwipeLeft != SwipeActionOption.NOTHING || onSwipeRight != SwipeActionOption.NOTHING
}
