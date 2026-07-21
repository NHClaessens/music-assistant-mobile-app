package io.music_assistant.client.settings

import io.music_assistant.client.data.model.client.ItemKind

/** Stable IDs for persisted context-menu configuration. */
enum class MenuActionOption {
    PLAY_NOW,
    INSERT_NEXT_AND_PLAY,
    INSERT_NEXT,
    INTERLEAVE,
    ADD_TO_QUEUE,
    PLAY_FROM_HERE,
    START_RADIO,
    LIBRARY,
    FAVORITE,
    ADD_TO_PLAYLIST,
    REMOVE_FROM_PLAYLIST,
    MARK_PLAYED,
    CUSTOMIZE,
    ;

    /** Whether this action can appear in the editor for [kind]. */
    fun isAvailableFor(kind: ItemKind): Boolean {
        if (kind !in configurableContextMenuKinds) return false
        return when (this) {
            PLAY_FROM_HERE -> kind == ItemKind.TRACK
            INTERLEAVE -> kind in setOf(
                ItemKind.ALBUM,
                ItemKind.ARTIST,
                ItemKind.PLAYLIST,
                ItemKind.PODCAST,
            )
            START_RADIO -> kind in setOf(
                ItemKind.TRACK,
                ItemKind.ALBUM,
                ItemKind.ARTIST,
                ItemKind.PLAYLIST,
            )
            CUSTOMIZE -> kind in setOf(ItemKind.TRACK, ItemKind.RADIO, ItemKind.PODCAST_EPISODE)
            MARK_PLAYED -> kind in setOf(ItemKind.PODCAST_EPISODE, ItemKind.AUDIOBOOK)
            REMOVE_FROM_PLAYLIST -> kind == ItemKind.TRACK
            ADD_TO_PLAYLIST -> kind in setOf(
                ItemKind.TRACK,
                ItemKind.ALBUM,
                ItemKind.RADIO,
                ItemKind.PODCAST_EPISODE,
                ItemKind.AUDIOBOOK,
            )
            FAVORITE -> kind != ItemKind.RADIO
            else -> true
        }
    }
}

/** Item kinds exposed in Settings → Context menus. */
val configurableContextMenuKinds: List<ItemKind> = listOf(
    ItemKind.TRACK,
    ItemKind.RADIO,
    ItemKind.PODCAST_EPISODE,
    ItemKind.ALBUM,
    ItemKind.ARTIST,
    ItemKind.PLAYLIST,
    ItemKind.PODCAST,
    ItemKind.AUDIOBOOK,
)

/** Canonical default order — filtered per kind via [isAvailableFor]. */
private val defaultMenuActionOrder: List<MenuActionOption> = listOf(
    MenuActionOption.PLAY_NOW,
    MenuActionOption.INSERT_NEXT_AND_PLAY,
    MenuActionOption.INSERT_NEXT,
    MenuActionOption.INTERLEAVE,
    MenuActionOption.ADD_TO_QUEUE,
    MenuActionOption.PLAY_FROM_HERE,
    MenuActionOption.START_RADIO,
    MenuActionOption.CUSTOMIZE,
    MenuActionOption.LIBRARY,
    MenuActionOption.FAVORITE,
    MenuActionOption.ADD_TO_PLAYLIST,
    MenuActionOption.REMOVE_FROM_PLAYLIST,
    MenuActionOption.MARK_PLAYED,
)

/** Default enabled actions for a kind when nothing is stored. */
fun defaultContextMenuActions(kind: ItemKind): List<MenuActionOption> =
    defaultMenuActionOrder.filter { it.isAvailableFor(kind) }

/** All actions that can be edited for this kind. */
fun contextMenuEditorUniverse(kind: ItemKind): List<MenuActionOption> =
    MenuActionOption.entries.filter { it.isAvailableFor(kind) }

/**
 * Builds the reorderable editor rows: stored enabled order first, newly added defaults
 * appended enabled, then disabled applicable actions.
 */
fun reconcileContextMenuActions(
    stored: List<MenuActionOption>?,
    kind: ItemKind,
): List<Pair<MenuActionOption, Boolean>> {
    val universe = contextMenuEditorUniverse(kind)
    val defaults = defaultContextMenuActions(kind)
    if (stored == null) {
        return universe.map { option -> option to (option in defaults) }
    }
    val enabled = stored.filter { it in universe }
    val newlyAdded = defaults.filter { it !in enabled && it !in stored }
    val disabled = universe.filter { it !in enabled && it !in newlyAdded }
    return enabled.map { it to true } + newlyAdded.map { it to true } + disabled.map { it to false }
}

/** Resolved enabled action order for runtime; falls back to defaults when unset. */
fun contextMenuActionsFor(
    prefs: Map<ItemKind, List<MenuActionOption>>,
    kind: ItemKind,
): List<MenuActionOption> = prefs[kind] ?: defaultContextMenuActions(kind)
