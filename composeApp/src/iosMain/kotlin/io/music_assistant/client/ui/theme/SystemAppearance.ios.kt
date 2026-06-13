package io.music_assistant.client.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import platform.UIKit.UIApplication
import platform.UIKit.UIUserInterfaceStyle
import platform.UIKit.UIWindow
import platform.UIKit.UIWindowScene

@Composable
actual fun isSystemInDarkTheme(): Boolean {
    var isDark by remember { mutableStateOf(currentSystemDarkTheme()) }
    LaunchedEffect(Unit) {
        while (true) {
            val current = currentSystemDarkTheme()
            if (isDark != current) isDark = current
            delay(SYSTEM_THEME_POLL_MS)
        }
    }
    return isDark
}

@Composable
actual fun SystemAppearance(isDarkTheme: Boolean, followsSystem: Boolean) {
    LaunchedEffect(isDarkTheme, followsSystem) {
        val style = when {
            followsSystem -> UIUserInterfaceStyle.UIUserInterfaceStyleUnspecified
            isDarkTheme -> UIUserInterfaceStyle.UIUserInterfaceStyleDark
            else -> UIUserInterfaceStyle.UIUserInterfaceStyleLight
        }
        UIApplication.sharedApplication.connectedScenes.forEach { scene ->
            (scene as? UIWindowScene)?.windows?.forEach { window ->
                (window as? UIWindow)?.overrideUserInterfaceStyle = style
            }
        }
    }
}

private fun currentSystemDarkTheme(): Boolean = UIApplication.sharedApplication.connectedScenes
    .asSequence()
    .mapNotNull { it as? UIWindowScene }
    .flatMap { it.windows.asSequence() }
    .mapNotNull { it as? UIWindow }
    .firstOrNull { it.isKeyWindow() }
    ?.traitCollection
    ?.userInterfaceStyle == UIUserInterfaceStyle.UIUserInterfaceStyleDark

private const val SYSTEM_THEME_POLL_MS = 500L
