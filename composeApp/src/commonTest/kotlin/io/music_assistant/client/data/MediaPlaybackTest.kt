package io.music_assistant.client.data

import io.music_assistant.client.data.model.client.Player
import io.music_assistant.client.data.model.client.PlayerData
import io.music_assistant.client.data.model.client.PlayerType
import io.music_assistant.client.data.model.client.Queue
import io.music_assistant.client.data.model.client.QueueInfo
import io.music_assistant.client.data.model.client.QueueTrack
import io.music_assistant.client.data.model.client.RepeatMode
import io.music_assistant.client.data.model.client.testAlbum
import io.music_assistant.client.data.model.client.testArtist
import io.music_assistant.client.data.model.client.testPlaylist
import io.music_assistant.client.data.model.client.testPodcast
import io.music_assistant.client.data.model.client.testTrack
import io.music_assistant.client.ui.compose.common.DataState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MediaPlaybackTest {
    @Test
    fun `interleaveLists alternates then appends longer tail`() {
        assertEquals(
            listOf("n0", "o0", "n1", "o1", "n2"),
            interleaveLists(listOf("n0", "n1", "n2"), listOf("o0", "o1")),
        )
    }

    @Test
    fun `interleaveLists returns new items when upcoming is empty`() {
        assertEquals(listOf("n0", "n1"), interleaveLists(listOf("n0", "n1"), emptyList()))
    }

    @Test
    fun `interleaveLists returns upcoming when new items is empty`() {
        assertEquals(listOf("o0", "o1"), interleaveLists(emptyList(), listOf("o0", "o1")))
    }

    @Test
    fun `upcomingQueueUris drops current and earlier items`() {
        val player = playerWithQueue(
            currentIndex = 1,
            uris = listOf("past", "now", "next0", "next1"),
        )
        assertEquals(listOf("next0", "next1"), upcomingQueueUris(player))
    }

    @Test
    fun `upcomingQueueUris returns empty when current position is unknown`() {
        val tracks = listOf("a", "b", "c").mapIndexed { index, uri ->
            QueueTrack(
                id = "q$index",
                track = testTrack().copy(itemId = "t$index", uri = uri),
                isPlayable = true,
                format = null,
                dsp = null,
                provider = null,
            )
        }
        val player = playerWithQueue(currentIndex = 1, uris = listOf("a", "b", "c")).let { base ->
            base.copy(
                queue = DataState.Data(
                    Queue(
                        info = QueueInfo(
                            id = "queue",
                            available = true,
                            currentIndex = null,
                            shuffleEnabled = false,
                            repeatMode = RepeatMode.OFF,
                            autoPlayEnabled = false,
                            elapsedTime = null,
                            elapsedTimeLastUpdated = null,
                            currentItem = null,
                            radioSource = emptyList(),
                        ),
                        items = DataState.Data(tracks),
                    ),
                ),
            )
        }
        assertEquals(emptyList(), upcomingQueueUris(player))
    }

    @Test
    fun `supportsInterleaveIntoQueue is true for multi-track containers only`() {
        assertTrue(testAlbum().supportsInterleaveIntoQueue)
        assertTrue(testPlaylist().supportsInterleaveIntoQueue)
        assertTrue(testArtist().supportsInterleaveIntoQueue)
        assertTrue(testPodcast().supportsInterleaveIntoQueue)
        assertFalse(testTrack().supportsInterleaveIntoQueue)
    }

    private fun playerWithQueue(currentIndex: Int, uris: List<String>): PlayerData {
        val tracks = uris.mapIndexed { index, uri ->
            QueueTrack(
                id = "q$index",
                track = testTrack().copy(itemId = "t$index", uri = uri),
                isPlayable = true,
                format = null,
                dsp = null,
                provider = null,
            )
        }
        val queueInfo = QueueInfo(
            id = "queue",
            available = true,
            currentIndex = currentIndex,
            shuffleEnabled = false,
            repeatMode = RepeatMode.OFF,
            autoPlayEnabled = false,
            elapsedTime = null,
            elapsedTimeLastUpdated = null,
            currentItem = tracks[currentIndex],
            radioSource = emptyList(),
        )
        return PlayerData(
            player = Player(
                id = "player",
                name = "Player",
                provider = "test",
                type = PlayerType.PLAYER,
                shouldBeShown = true,
                canSetVolume = true,
                canPower = false,
                isPowered = true,
                volumeLevel = 0.5f,
                volumeControl = null,
                volumeMuted = false,
                canMute = false,
                queueId = "queue",
                isPlaying = true,
                isAnnouncing = false,
                canGroupWith = null,
                groupMembers = null,
                staticGroupMembers = null,
                activeGroup = null,
                syncedTo = null,
                groupVolume = null,
                groupVolumeMuted = false,
                currentMedia = null,
            ),
            queue = DataState.Data(
                Queue(
                    info = queueInfo,
                    items = DataState.Data(tracks),
                ),
            ),
            parentBind = null,
            childrenBinds = emptyList(),
        )
    }
}
