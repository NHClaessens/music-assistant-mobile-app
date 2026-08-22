package io.music_assistant.client.ui.compose.home.players

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import io.music_assistant.client.data.model.client.Player
import io.music_assistant.client.sharedicons.SharedIcons
import org.jetbrains.compose.resources.painterResource
import kotlin.collections.get

/**
 * Canonical renderer for a player's icon across all surfaces.
 *
 * The on-device player ([isLocal]) keeps its client-role smartphone glyph. Real players
 * and groups use the server-provided icon ID ([Player.icon]) mapped to vector drawables
 * via [SharedIcons], falling back to speaker icon when the name is empty/unknown.
 */
@Composable
fun PlayerIcon(
    player: Player,
    isLocal: Boolean,
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current,
) {
    if (isLocal) {
        Icon(Icons.Default.Smartphone, contentDescription = null, modifier = modifier, tint = tint)
        return
    }

    val iconRes = SharedIcons.getIcon(player.icon)
    Icon(
        painter = painterResource(iconRes),
        contentDescription = null,
        modifier = modifier,
        tint = tint,
    )
}
