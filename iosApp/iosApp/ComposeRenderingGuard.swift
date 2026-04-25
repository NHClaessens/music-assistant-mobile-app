import UIKit
import ComposeApp

/// Halts Compose Multiplatform's Metal render loop while the default app scene is in the
/// background, then resumes it on foreground.
///
/// Why we need this:
///   Music Assistant declares `UIBackgroundModes: audio` so playback continues when the user
///   leaves the app. iOS keeps our main run loop alive for that, which means CMP's
///   `CADisplayLink` keeps ticking too. Each tick submits a Metal command buffer, which iOS
///   denies in the background with `kIOGPUCommandBufferCallbackErrorBackgroundExecutionNot
///   Permitted`, flooding the console. CMP 1.10.1 has a built-in pause path that listens for
///   the same scene notifications, but inside SwiftUI's `WindowGroup` hosting its scene
///   matching can race the notification timing — so this observer is a deterministic
///   backstop that toggles the renderer directly via `ComposeRendererBridgeKt`.
///
/// Lifecycle:
///   The observer is created once at app launch (via `iOSApp.init()`) and lives for the
///   process lifetime. `NotificationCenter` holds non-owning references, so the singleton
///   pattern is what keeps the observer alive.
final class ComposeRenderingGuard {
    static let shared = ComposeRenderingGuard()

    private init() {
        let center = NotificationCenter.default
        center.addObserver(
            self,
            selector: #selector(sceneDidEnterBackground(_:)),
            name: UIScene.didEnterBackgroundNotification,
            object: nil
        )
        center.addObserver(
            self,
            selector: #selector(sceneWillEnterForeground(_:)),
            name: UIScene.willEnterForegroundNotification,
            object: nil
        )
    }

    @objc private func sceneDidEnterBackground(_ notification: Notification) {
        applyPause(true, from: notification)
    }

    @objc private func sceneWillEnterForeground(_ notification: Notification) {
        applyPause(false, from: notification)
    }

    /// CarPlay runs its own UI loop inside `CPTemplateApplicationScene`, which is *not* a
    /// `UIWindowScene` — so the `as? UIWindowScene` cast already filters it out. The same
    /// guard skips any non-window UIScene we don't manage.
    private func applyPause(_ paused: Bool, from notification: Notification) {
        guard let scene = notification.object as? UIWindowScene else { return }

        for window in scene.windows {
            ComposeRendererBridgeKt.setComposeRendererPaused(window: window, paused: paused)
        }
    }
}
