// Compose layout values (dot size, spacing) are visual design tokens.
@file:Suppress("MagicNumber")

package io.music_assistant.client.ui.compose.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.music_assistant.client.data.model.client.PlayerData
import io.music_assistant.client.data.model.client.PlayerDataFixtures
import io.music_assistant.client.player.sendspin.SendspinState
import io.music_assistant.client.ui.compose.home.players.PlayerSelectionButton
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sign

@Composable
fun HorizontalPagerIndicator(
    modifier: Modifier = Modifier,
    pagerState: PagerState,
) {
    val pageCount = pagerState.pageCount
    val effectivePosition = pagerState.currentPage + pagerState.currentPageOffsetFraction

    Row(
        modifier = modifier.fillMaxWidth().defaultMinSize(minHeight = 12.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (pageCount <= 15) {
            repeat(pageCount) { index ->
                PlayerPageDot(
                    index = index,
                    effectivePosition = effectivePosition,
                )
            }
        } else {
            Text(
                text = "${effectivePosition.roundToInt() + 1} / $pageCount",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
fun PlayerSwitcherRow(
    pagerState: PagerState,
    players: List<PlayerData>,
    controlTintFor: @Composable (PlayerData) -> Color,
    sendSpinState: SendspinState?,
    onSelectPlayer: (PlayerData) -> Unit,
    onGroupButton: (PlayerData) -> Unit,
    modifier: Modifier = Modifier,
) {
    val pageCount = pagerState.pageCount
    val effectivePosition = pagerState.currentPage + pagerState.currentPageOffsetFraction
    var playerWidthPx by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current

    Box(
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (pageCount > 15) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "${effectivePosition.roundToInt() + 1} / $pageCount",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.width(8.dp))
                AnimatedPlayerSelectionSlot(
                    pagerState = pagerState,
                    players = players,
                    controlTintFor = controlTintFor,
                    sendSpinState = sendSpinState,
                    onSelectPlayer = onSelectPlayer,
                    onGroupButton = onGroupButton,
                )
            }
        } else {
            if (pageCount > 1 && playerWidthPx > 0) {
                val playerHalfPx = playerWidthPx / 2f
                val gapPx = with(density) { 4.dp.toPx() }
                // Matches PlayerPageDot: 3.dp horizontal padding each side + 4.dp size.
                val dotPitchPx = with(density) { 10.dp.toPx() }

                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) {
                    for (index in 0 until pageCount) {
                        val relative = index - effectivePosition
                        val absRelative = abs(relative)
                        // Selected page is represented by the centered player button.
                        if (absRelative <= 0.5f) continue

                        val translationX = if (relative < 0f) {
                            -(playerHalfPx + gapPx + dotPitchPx / 2f) +
                                (relative + 1f) * dotPitchPx
                        } else {
                            (playerHalfPx + gapPx + dotPitchPx / 2f) +
                                (relative - 1f) * dotPitchPx
                        }
                        val appear =
                            ((absRelative - 0.5f) / 0.25f).coerceIn(0f, 1f)

                        Box(
                            modifier = Modifier.graphicsLayer {
                                this.translationX = translationX
                                alpha = appear
                            },
                        ) {
                            PlayerPageDot(
                                index = index,
                                effectivePosition = effectivePosition,
                            )
                        }
                    }
                }
            }

            AnimatedPlayerSelectionSlot(
                modifier = Modifier.onSizeChanged { playerWidthPx = it.width },
                pagerState = pagerState,
                players = players,
                controlTintFor = controlTintFor,
                sendSpinState = sendSpinState,
                onSelectPlayer = onSelectPlayer,
                onGroupButton = onGroupButton,
            )
        }
    }
}

@Composable
private fun AnimatedPlayerSelectionSlot(
    pagerState: PagerState,
    players: List<PlayerData>,
    controlTintFor: @Composable (PlayerData) -> Color,
    sendSpinState: SendspinState?,
    onSelectPlayer: (PlayerData) -> Unit,
    onGroupButton: (PlayerData) -> Unit,
    modifier: Modifier = Modifier,
) {
    val offsetFraction = pagerState.currentPageOffsetFraction
    val progress = abs(offsetFraction).coerceIn(0f, 1f)
    val swipeDirection = sign(offsetFraction).toInt()
    val outgoingPage = pagerState.currentPage
    val outgoingPlayer = players.getOrNull(outgoingPage) ?: return
    val incomingPage = outgoingPage + swipeDirection
    val incomingPlayer = players.getOrNull(incomingPage)
    val slidePx = with(LocalDensity.current) { 24.dp.toPx() }
    // Outgoing exits during the first half of the swipe; incoming enters during the second half.
    val outgoingPhase = (progress * 2f).coerceIn(0f, 1f)
    val incomingPhase = ((progress - 0.5f) * 2f).coerceIn(0f, 1f)

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        if (outgoingPhase < 1f) {
            Box(
                modifier = Modifier.graphicsLayer {
                    alpha = 1f - outgoingPhase
                    val scale = 0.92f + 0.08f * (1f - outgoingPhase)
                    scaleX = scale
                    scaleY = scale
                    translationX = -outgoingPhase * slidePx * swipeDirection
                },
            ) {
                PlayerSelectionButton(
                    player = outgoingPlayer,
                    controlTint = controlTintFor(outgoingPlayer),
                    sendSpinState = sendSpinState,
                    onSelectPlayer = { onSelectPlayer(outgoingPlayer) },
                    onGroupButton = { onGroupButton(outgoingPlayer) },
                )
            }
        }

        if (incomingPhase > 0f && incomingPlayer != null) {
            Box(
                modifier = Modifier.graphicsLayer {
                    alpha = incomingPhase
                    val scale = 0.92f + 0.08f * incomingPhase
                    scaleX = scale
                    scaleY = scale
                    translationX = (1f - incomingPhase) * slidePx * swipeDirection
                },
            ) {
                PlayerSelectionButton(
                    player = incomingPlayer,
                    controlTint = controlTintFor(incomingPlayer),
                    sendSpinState = sendSpinState,
                    onSelectPlayer = { onSelectPlayer(incomingPlayer) },
                    onGroupButton = { onGroupButton(incomingPlayer) },
                )
            }
        }
    }
}

@Composable
private fun PlayerPageDot(
    index: Int,
    effectivePosition: Float,
) {
    val distance = abs(index - effectivePosition)
    val prominence = (1f - distance).coerceIn(0f, 1f)
    val inactiveAlpha = 0.3f
    val dotColor = MaterialTheme.colorScheme.onSurfaceVariant

    Box(
        modifier = Modifier
            .padding(horizontal = 3.dp)
            .graphicsLayer {
                alpha = inactiveAlpha + (1f - inactiveAlpha) * prominence
                scaleX = 0.5f + 0.5f * prominence
                scaleY = scaleX
            }
            .size(4.dp)
            .clip(CircleShape)
            .background(dotColor),
    )
}

@Preview
@Composable
fun HorizontalPagerIndicatorNumbersPreview() {
    MaterialTheme {
        HorizontalPagerIndicator(
            modifier = Modifier.fillMaxWidth().wrapContentHeight(),
            pagerState = rememberPagerState(pageCount = { 16 }, initialPage = 2),
        )
    }
}

@Preview
@Composable
fun HorizontalPagerIndicatorDotsPreview() {
    MaterialTheme {
        HorizontalPagerIndicator(
            modifier = Modifier.fillMaxWidth().wrapContentHeight(),
            pagerState = rememberPagerState(pageCount = { 9 }, initialPage = 2),
        )
    }
}

@Preview
@Composable
fun PlayerSwitcherRowPreview() {
    MaterialTheme {
        val players = List(5) { PlayerDataFixtures.playerData() }
        PlayerSwitcherRow(
            modifier = Modifier.fillMaxWidth().wrapContentHeight(),
            pagerState = rememberPagerState(pageCount = { 5 }, initialPage = 2),
            players = players,
            controlTintFor = { MaterialTheme.colorScheme.primary },
            sendSpinState = null,
            onSelectPlayer = {},
            onGroupButton = {},
        )
    }
}

@Preview
@Composable
fun PlayerSwitcherRowSinglePlayerPreview() {
    MaterialTheme {
        PlayerSwitcherRow(
            modifier = Modifier.fillMaxWidth().wrapContentHeight(),
            pagerState = rememberPagerState(pageCount = { 1 }, initialPage = 0),
            players = listOf(PlayerDataFixtures.playerData()),
            controlTintFor = { MaterialTheme.colorScheme.primary },
            sendSpinState = null,
            onSelectPlayer = {},
            onGroupButton = {},
        )
    }
}
