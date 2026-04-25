// Compose Multiplatform internals are `internal` to their module; this file deliberately
// reaches into them. The bridge is small and confined here so the rest of the codebase
// stays clean.
@file:Suppress("INVISIBLE_REFERENCE", "INVISIBLE_MEMBER")

package io.music_assistant.client.utils

import androidx.compose.ui.window.MetalView
import co.touchlab.kermit.Logger
import platform.UIKit.UIView
import platform.UIKit.UIWindow

private val logger = Logger.withTag("ComposeRendererBridge")

/**
 * Pauses or resumes Compose Multiplatform's Metal render loop for every CMP-backed view
 * inside [window].
 *
 * Why this exists:
 *
 * iOS denies GPU command-buffer submission to background processes
 * (`kIOGPUCommandBufferCallbackErrorBackgroundExecutionNotPermitted`). Most apps never
 * notice because their main run loop pauses on backgrounding, which incidentally pauses
 * `CADisplayLink`. We declare `UIBackgroundModes: audio` so the run loop keeps running for
 * playback — and CMP's `CADisplayLink` keeps ticking with it, submitting Metal work that iOS
 * rejects, flooding the console.
 *
 * CMP 1.10.1 *does* try to pause its renderer on `UISceneDidEnterBackgroundNotification`
 * via `SceneForegroundStateListener`, but the listener filters notifications by
 * `notification.object == view.window?.windowScene`. Inside a SwiftUI `WindowGroup`, that
 * scene reference can race the notification timing and the equality check misses, so the
 * built-in pause never fires for our hosting topology. This bridge is a deterministic
 * backstop driven from the Swift scene-notification observer.
 *
 * Implementation notes:
 *  - `MetalView` is `internal` in CMP, hence the `@file:Suppress`. `redrawer` and
 *    `redrawer.isActive` are public properties on a public type (`MetalRedrawer`); the
 *    suppression only covers the `is MetalView` cast.
 *  - Setting `redrawer.isActive = false` is idempotent (CMP no-ops if already at the target
 *    value) and synchronously pauses the underlying `CADisplayLink` plus drains in-flight
 *    command buffers. Safe to call from the main thread; not safe from any other thread.
 *  - Pinned to Compose Multiplatform 1.10.1. If `MetalView` is renamed, repackaged, or made
 *    fully private in a future CMP version, this bridge will fail to compile — that's the
 *    intended early-warning signal to revisit the integration.
 */
fun setComposeRendererPaused(window: UIWindow, paused: Boolean) {
    var count = 0
    walkMetalViews(window) { metalView ->
        metalView.redrawer.isActive = !paused
        count++
    }
    val state = if (paused) "paused" else "resumed"
    logger.d { "Compose renderer $state ($count MetalView${if (count == 1) "" else "s"})" }
}

private fun walkMetalViews(view: UIView, action: (MetalView) -> Unit) {
    if (view is MetalView) action(view)
    @Suppress("UNCHECKED_CAST")
    val children = view.subviews as List<UIView>
    for (child in children) {
        walkMetalViews(child, action)
    }
}
