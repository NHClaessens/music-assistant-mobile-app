package io.music_assistant.client.support

import io.music_assistant.client.api.APICommands
import io.music_assistant.client.api.Answer
import io.music_assistant.client.api.ConnectionInfo
import io.music_assistant.client.api.Request
import io.music_assistant.client.api.ServiceClient
import io.music_assistant.client.data.model.client.MediaType
import io.music_assistant.client.data.model.server.AudioFormat
import io.music_assistant.client.data.model.server.AuthProvider
import io.music_assistant.client.data.model.server.DSPSettings
import io.music_assistant.client.data.model.server.EventType
import io.music_assistant.client.data.model.server.PlayerState
import io.music_assistant.client.data.model.server.ProviderManifest
import io.music_assistant.client.data.model.server.SearchResult
import io.music_assistant.client.data.model.server.ServerInfo
import io.music_assistant.client.data.model.server.ServerMediaItem
import io.music_assistant.client.data.model.server.ServerPlayer
import io.music_assistant.client.data.model.server.ServerPlayerMedia
import io.music_assistant.client.data.model.server.ServerQueue
import io.music_assistant.client.data.model.server.ServerQueueItem
import io.music_assistant.client.data.model.server.ServerUser
import io.music_assistant.client.data.model.server.ServerUserPreferences
import io.music_assistant.client.data.model.server.StreamDetails
import io.music_assistant.client.data.model.server.User
import io.music_assistant.client.data.model.server.events.Event
import io.music_assistant.client.data.model.server.events.PlayerUpdatedEvent
import io.music_assistant.client.data.model.server.events.QueueItemsUpdatedEvent
import io.music_assistant.client.data.model.server.events.QueueUpdatedEvent
import io.music_assistant.client.utils.AuthProcessState
import io.music_assistant.client.utils.ConnectionData
import io.music_assistant.client.utils.SessionState
import io.music_assistant.client.utils.UniqueIdGenerator
import io.music_assistant.client.utils.myJson
import io.music_assistant.client.utils.update
import io.music_assistant.client.webrtc.DataChannelWrapper
import io.music_assistant.client.webrtc.model.RemoteId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.encodeToJsonElement

class FakeServiceClient : ServiceClient {
    private var legacyVersion: LegacyVersion? = null
    private var requestErrors: Boolean = false
    private var connectionError: Exception? = null

    private val uniqueIdGenerator = UniqueIdGenerator()

    private val players = mutableListOf<ServerPlayer>()
    private val playerAudioFormats = mutableMapOf<String, AudioFormat>()
    private val queues = mutableListOf<ServerQueue>()
    private val queueItems = mutableMapOf<String, List<ServerQueueItem>>()
    private val mediaItems = mutableSetOf<ServerMediaItem>()
    private val libraryIds = mutableMapOf<Pair<String, String>, String>()

    private val playlistItems = mutableMapOf<String, List<String>>()
    private val shortcuts = mutableListOf<String>()

    val username = "user"
    val password = "password"
    var serverId = "serverId"

    private val _sessionState: MutableStateFlow<SessionState> =
        MutableStateFlow(SessionState.Disconnected.Initial)
    override val sessionState: StateFlow<SessionState> = _sessionState

    private val _isReadyForCommands = MutableStateFlow(false)
    override val isReadyForCommands: StateFlow<Boolean> = _isReadyForCommands

    override val externalConsumerActive: StateFlow<Boolean> = MutableStateFlow(false)

    override suspend fun sendRequest(request: Request): Result<Answer> {
        if (requestErrors) {
            return Result.failure(Exception())
        }

        return when (request.command) {
            APICommands.PROVIDERS_MANIFESTS -> {
                Result.success(
                    answer(
                        request = request,
                        result = emptyList<ProviderManifest>(),
                    ),
                )
            }

            APICommands.AUTH_ME -> {
                if (legacyVersion == LegacyVersion.V2_8) {
                    Result.success(
                        answer(
                            request = request,
                            result = emptyMap<String, String>(),
                        ),
                    )
                } else {
                    Result.success(
                        answer(
                            request = request,
                            result = ServerUser(preferences = ServerUserPreferences(shortcuts)),
                        ),
                    )
                }
            }

            APICommands.AUTH_PROVIDERS -> {
                Result.success(
                    answer(
                        request = request,
                        result = listOf(
                            AuthProvider(
                                id = "builtin",
                                type = "builtin",
                                requiresRedirect = false,
                            ),
                        ),
                    ),
                )
            }

            APICommands.MUSIC_ITEM_BY_URI -> {
                val item = mediaItems.find { it.uri == request.getArg("uri") }!!
                Result.success(answer(request = request, result = item))
            }

            APICommands.MUSIC_RECOMMENDATIONS -> {
                Result.success(
                    answer(
                        request = request,
                        result = listOf(
                            ServerMediaItem(
                                itemId = "recently_added_albums",
                                provider = "library",
                                name = "Recently added albums",
                                mediaType = MediaType.FOLDER.serverValue,
                                items = mediaItems
                                    .filter { it.mediaType == MediaType.ALBUM.serverValue }
                                    .forResponse(),
                            ),
                            ServerMediaItem(
                                itemId = "recently_added_tracks",
                                provider = "library",
                                name = "Recently added tracks",
                                mediaType = MediaType.FOLDER.serverValue,
                                items = mediaItems
                                    .filter { it.mediaType == MediaType.TRACK.serverValue }
                                    .forResponse(),
                            ),
                            ServerMediaItem(
                                itemId = "recently_added_artists",
                                provider = "library",
                                name = "Recently added artists",
                                mediaType = MediaType.FOLDER.serverValue,
                                items = mediaItems
                                    .filter { it.mediaType == MediaType.ARTIST.serverValue }
                                    .forResponse(),
                            ),
                        ),
                    ),
                )
            }

            APICommands.MUSIC_SEARCH -> {
                val mediaTypes =
                    (request.args!!["media_types"] as JsonArray).map { (it as JsonPrimitive).content }
                val libraryOnly = request.getArgOrNull("library_only") == "true"

                val itemsToSearch = if (libraryOnly) {
                    mediaItems.dropNotInLibrary()
                } else {
                    mediaItems
                }

                val results = itemsToSearch.filter {
                    it.name.contains(request.getArg("search_query"), ignoreCase = true)
                }

                val resultsForType: (MediaType) -> List<ServerMediaItem> = {
                    val mediaTypeServerValue = it.serverValue
                    if (mediaTypes.isEmpty() || mediaTypes.contains(mediaTypeServerValue)) {
                        results.filter { it.mediaType == mediaTypeServerValue }.forResponse()
                    } else {
                        emptyList()
                    }
                }

                Result.success(
                    answer(
                        request = request,
                        result = SearchResult(
                            artists = resultsForType(MediaType.ARTIST),
                            albums = resultsForType(MediaType.ALBUM),
                            tracks = resultsForType(MediaType.TRACK),
                            playlists = resultsForType(MediaType.PLAYLIST),
                            podcasts = resultsForType(MediaType.PODCAST),
                            audiobooks = resultsForType(MediaType.AUDIOBOOK),
                            radio = resultsForType(MediaType.RADIO),
                            genres = resultsForType(MediaType.GENRE),
                        ),
                    ),
                )
            }

            APICommands.musicGet(APICommands.KIND_ALBUMS) -> {
                Result.success(
                    answer(
                        request = request,
                        result = findItem(request).forResponse(),
                    ),
                )
            }

            APICommands.MUSIC_ALBUMS_ALBUM_TRACKS -> {
                val album = findItem(request)

                Result.success(
                    answer(
                        request = request,
                        result = album.getAlbumTracks().forResponse(),
                    ),
                )
            }

            APICommands.MUSIC_ALBUMS_LIBRARY_ITEMS -> {
                Result.success(
                    answer(
                        request = request,
                        result = filterLibrary(request, MediaType.ALBUM).forResponse(),
                    ),
                )
            }

            APICommands.musicGet(APICommands.KIND_ARTISTS) -> {
                Result.success(
                    answer(
                        request = request,
                        result = findItem(request).forResponse(),
                    ),
                )
            }

            APICommands.MUSIC_ARTISTS_ARTIST_ALBUMS -> {
                val artist = findItem(request)

                Result.success(
                    answer(
                        request = request,
                        result = if (request.getArg("provider_instance_id_or_domain") == "library") {
                            mediaItems.dropNotInLibrary()
                        } else {
                            mediaItems
                        }.filter { it.mediaType == MediaType.ALBUM.serverValue }
                            .filter { it.artists?.contains(artist) ?: false }
                            .forResponse(),
                    ),
                )
            }

            APICommands.MUSIC_ARTISTS_LIBRARY_ITEMS -> {
                Result.success(
                    answer(
                        request = request,
                        result = filterLibrary(request, MediaType.ARTIST).forResponse(),
                    ),
                )
            }

            APICommands.MUSIC_PLAYLISTS_LIBRARY_ITEMS -> {
                Result.success(
                    answer(
                        request = request,
                        result = filterLibrary(request, MediaType.PLAYLIST).forResponse(),
                    ),
                )
            }

            APICommands.musicGet(APICommands.KIND_PLAYLISTS) -> {
                Result.success(
                    answer(
                        request = request,
                        result = findItem(request).forResponse(),
                    ),
                )
            }

            APICommands.MUSIC_PLAYLISTS_PLAYLIST_TRACKS -> {
                val playlist = findItem(request)

                Result.success(
                    answer(
                        request = request,
                        result = playlist.getPlaylistTracks().forResponse(),
                    ),
                )
            }

            APICommands.MUSIC_TRACKS_LIBRARY_ITEMS -> {
                Result.success(
                    answer(
                        request = request,
                        result = mediaItems
                            .dropNotInLibrary()
                            .filter {
                                it.mediaType == MediaType.TRACK.serverValue
                            }
                            .forResponse(),
                    ),
                )
            }

            APICommands.MUSIC_AUDIOBOOKS_LIBRARY_ITEMS -> {
                Result.success(
                    answer(
                        request = request,
                        result = filterLibrary(request, MediaType.AUDIOBOOK).forResponse(),
                    ),
                )
            }

            APICommands.MUSIC_PODCASTS_LIBRARY_ITEMS -> {
                Result.success(
                    answer(
                        request = request,
                        result = filterLibrary(request, MediaType.PODCAST).forResponse(),
                    ),
                )
            }

            APICommands.MUSIC_RADIOS_LIBRARY_ITEMS -> {
                Result.success(
                    answer(
                        request = request,
                        result = filterLibrary(request, MediaType.RADIO).forResponse(),
                    ),
                )
            }

            APICommands.MUSIC_GENRES_LIBRARY_ITEMS -> {
                Result.success(
                    answer(
                        request = request,
                        result = filterLibrary(request, MediaType.GENRE).forResponse(),
                    ),
                )
            }

            APICommands.PLAYERS_ALL -> {
                Result.success(
                    answer(
                        request = request,
                        result = players,
                    ),
                )
            }

            APICommands.PLAYER_QUEUES_PLAY_MEDIA -> {
                val mediaUri = ((request.args!!["media"] as JsonArray)[0] as JsonPrimitive).content
                val startItemId = request.getArgOrNull("start_item")
                val mediaTracks =
                    mediaItems.find { it.uri == mediaUri }?.let { item ->
                        when (MediaType.fromServer(item.mediaType)) {
                            MediaType.ALBUM -> {
                                val albumTracks = item.getAlbumTracks()
                                val startIndex = if (startItemId != null) {
                                    albumTracks.indexOfFirst { it.itemId == startItemId }
                                } else {
                                    0
                                }

                                albumTracks.drop(startIndex)
                            }

                            MediaType.TRACK -> listOf(item)
                            MediaType.PLAYLIST -> {
                                val playlistTracks = item.getPlaylistTracks()
                                val startIndex = if (startItemId != null) {
                                    playlistTracks.indexOfFirst { it.itemId == startItemId }
                                } else {
                                    0
                                }

                                playlistTracks.drop(startIndex)
                            }

                            else -> TODO()
                        }
                    } ?: emptyList()

                val queueId = request.getArg("queue_id")
                updateQueue(
                    queueId,
                    mediaTracks.map { ServerQueueItem(uniqueIdGenerator.nextInt().toString(), it) },
                )
                updatePlayer({ it.activeSource == queueId }) {
                    it.copy(
                        state = PlayerState.PLAYING,
                        currentMedia = mediaTracks.firstOrNull()?.let { track ->
                            ServerPlayerMedia(
                                uri = track.uri,
                                mediaType = track.mediaType,
                                title = track.name,
                                queueId = queueId,
                            )
                        },
                    )
                }

                Result.success(Answer(JsonObject(emptyMap())))
            }

            APICommands.PLAYER_QUEUES_ALL -> {
                Result.success(
                    answer(
                        request = request,
                        result = queues,
                    ),
                )
            }

            APICommands.PLAYER_QUEUES_ITEMS -> {
                val queueId = (request.args!!["queue_id"] as JsonPrimitive).content

                Result.success(
                    answer(
                        request = request,
                        result = queueItems[queueId],
                    ),
                )
            }

            APICommands.PLAYER_QUEUES_CLEAR -> {
                val queueId = (request.args!!["queue_id"] as JsonPrimitive).content
                updateQueue(queueId, emptyList())
                updatePlayer({ it.activeSource == queueId }) {
                    it.copy(
                        state = PlayerState.IDLE,
                        currentMedia = null,
                    )
                }

                Result.success(Answer(JsonObject(emptyMap())))
            }

            APICommands.playersCmd("play_pause") -> {
                val playerId = (request.args!!["player_id"] as JsonPrimitive).content
                updatePlayer({ it.playerId == playerId }) {
                    it.copy(state = PlayerState.PAUSED)
                }

                Result.success(Answer(JsonObject(emptyMap())))
            }

            APICommands.PLAYER_QUEUES_TRANSFER -> {
                val queueId = request.getArg("source_queue_id")
                val targetQueueId = request.getArg("target_queue_id")
                val autoPlay = request.getArg("auto_play").toBoolean()

                val queueItems = queueItems[queueId] ?: emptyList()
                updateQueue(queueId, emptyList())
                updatePlayer({ it.activeSource == queueId }) {
                    it.copy(
                        state = PlayerState.IDLE,
                        currentMedia = null,
                    )
                }

                updateQueue(targetQueueId, queueItems)
                updatePlayer({ it.activeSource == targetQueueId }) {
                    it.copy(
                        state = if (autoPlay) PlayerState.PLAYING else PlayerState.PAUSED,
                        currentMedia = queueItems.firstOrNull()?.mediaItem?.let { track ->
                            ServerPlayerMedia(
                                uri = track.uri,
                                mediaType = track.mediaType,
                                title = track.name,
                                queueId = queueId,
                            )
                        },
                    )
                }

                Result.success(Answer(JsonObject(emptyMap())))
            }

            else -> {
                Result.failure(UnsupportedOperationException())
            }
        }
    }

    private suspend fun updateQueue(
        queueId: String,
        items: List<ServerQueueItem>,
    ) {
        val queueIndex = queues.indexOfFirst { it.queueId == queueId }
        val player = findPlayer { it.activeSource == queueId }.second

        val dsp = legacyVersion.let {
            if (it != null && it <= LegacyVersion.V2_9) {
                mapOf(player.playerId to DSPSettings(outputFormat = playerAudioFormats[player.playerId]))
            } else {
                null
            }
        }

        val firstItem = items.firstOrNull()
        val currentItem = firstItem?.copy(
            streamDetails = firstItem.streamDetails.let { streamDetails ->
                streamDetails?.copy(dsp = dsp) ?: StreamDetails(
                    audioFormat = AudioFormat(),
                    dsp = dsp,
                )
            },
        ) ?: firstItem

        queues[queueIndex] =
            queues[queueIndex].copy(currentItem = currentItem)
        queueItems[queueId] = items

        val queue = queues[queueIndex]
        _events.emit(
            QueueUpdatedEvent(
                event = EventType.QUEUE_UPDATED,
                objectId = queue.queueId,
                data = queue,
            ),
        )

        _events.emit(
            QueueItemsUpdatedEvent(
                event = EventType.QUEUE_ITEMS_UPDATED,
                objectId = queue.queueId,
                data = queue,
            ),
        )
    }

    private suspend fun updatePlayer(
        search: (ServerPlayer) -> Boolean,
        update: (ServerPlayer) -> ServerPlayer,
    ) {
        val (playerIndex, originalPlayer) = findPlayer(search)
        val updatedPlayer = update(originalPlayer)
        players[playerIndex] = updatedPlayer
        _events.emit(
            PlayerUpdatedEvent(
                event = EventType.PLAYER_UPDATED,
                objectId = updatedPlayer.playerId,
                data = updatedPlayer,
            ),
        )
    }

    private fun findPlayer(search: (ServerPlayer) -> Boolean): Pair<Int, ServerPlayer> {
        val playerIndex = players.indexOfFirst(search)
        val originalPlayer = players[playerIndex]
        return Pair(playerIndex, originalPlayer)
    }

    override suspend fun login(username: String, password: String) {
        if (username == this.username && password == this.password) {
            authorize("token", true)
            _isReadyForCommands.value = true
        } else {
            _sessionState.update { state ->
                (state as SessionState.Connected).update(
                    authProcessState = AuthProcessState.Failed("Invalid username or password"),
                )
            }
        }
    }

    override suspend fun authorize(token: String, isAutoLogin: Boolean) {
        _sessionState.update {
            when (it) {
                is SessionState.Connected.Direct -> {
                    SessionState.Connected.Direct(
                        it.connectionInfo,
                        it.connectionData.copy(
                            authProcessState = AuthProcessState.NotStarted,
                            user = User("-1", username, username, "user"),
                            wasAutoLogin = true,
                            token = token,
                        ),
                    )
                }

                else -> error("Unhandled request type in FakeServiceClient")
            }
        }
    }

    override fun logout() {
        _sessionState.update {
            (it as? SessionState.Connected)?.update(
                authProcessState = AuthProcessState.LoggedOut,
                user = null,
            ) ?: it
        }
    }

    override fun resolveImageUrl(
        path: String,
        provider: String,
        isRemotelyAccessible: Boolean,
        proxyId: String?,
    ): String? = null

    override fun rebaseServerImageUrl(rawUrl: String): String? = null

    override val webRTCHttpProxy: io.music_assistant.client.webrtc.WebRTCHttpProxy? = null

    override fun forceWebRTCReconnect() {
        TODO("Not yet implemented")
    }

    private val _events = MutableSharedFlow<Event<out Any>>()
    override val events: Flow<Event<out Any>> = _events
    override val webrtcSendspinChannel: DataChannelWrapper
        get() = TODO("Not yet implemented")

    override fun onAppForeground() {
    }

    override fun onAppBackground() {
    }

    override val foregroundEvents: Flow<Unit> = emptyFlow()

    override fun disconnectByUser() {
        _sessionState.update {
            SessionState.Disconnected.ByUser
        }
    }

    override fun connect(connection: ConnectionInfo) {
        connectionError.let {
            if (it == null) {
                val connectionData = ConnectionData(
                    serverInfo = ServerInfo(
                        serverId = serverId,
                        serverVersion = "fake",
                        schemaVersion = -1,
                        baseUrl = "http://homeassistant.example",
                    ),
                )
                _sessionState.value = SessionState.Connected.Direct(connection, connectionData)
            } else {
                _sessionState.value = SessionState.Disconnected.Error(it)
            }
        }
    }

    override fun connectWebRTC(remoteId: RemoteId) {
        TODO("Not yet implemented")
    }

    override fun onExternalConsumerActive() {
        TODO("Not yet implemented")
    }

    override fun onPlaybackActive() {
    }

    override fun onExternalConsumerInactive() {
        TODO("Not yet implemented")
    }

    override fun onPlaybackInactive() {
    }

    override fun forceDisconnect(reason: Exception) {
        _sessionState.update {
            SessionState.Disconnected.Error(reason)
        }
    }

    override fun noServer() {
        _sessionState.update { SessionState.Disconnected.NoServerData }
    }

    fun addItems(vararg items: ServerMediaItem) {
        val itemsToAdd = items.toList()
        itemsToAdd.forEach { item ->
            item.artists?.let {
                mediaItems.addAll(it)
            }

            item.album?.let {
                mediaItems.add(it)
            }
        }

        mediaItems.addAll(itemsToAdd)
    }

    fun addToLibrary(vararg items: ServerMediaItem) {
        items.forEach { addToLibrary(it) }
    }

    fun addToLibrary(item: ServerMediaItem) {
        libraryIds[item.globalId()] = uniqueIdGenerator.nextInt().toString()

        when (MediaType.fromServer(item.mediaType)) {
            MediaType.ALBUM -> {
                item.getAlbumTracks().forEach {
                    addToLibrary(it)
                }

                item.artists?.forEach {
                    addToLibrary(it)
                }
            }

            MediaType.PLAYLIST -> Unit
            MediaType.ARTIST -> Unit
            MediaType.TRACK -> Unit
            MediaType.RADIO -> Unit
            MediaType.AUDIOBOOK -> Unit
            MediaType.PODCAST -> Unit
            MediaType.PODCAST_EPISODE -> Unit
            MediaType.GENRE -> Unit
            MediaType.FOLDER -> Unit
            MediaType.FLOW_STREAM -> Unit
            MediaType.ANNOUNCEMENT -> Unit
            MediaType.UNKNOWN -> Unit
            null -> Unit
        }
    }

    fun Set<ServerMediaItem>.dropNotInLibrary(): List<ServerMediaItem> {
        return this.filter { libraryIds.containsKey(it.globalId()) }
    }

    fun ServerMediaItem.forResponse(): ServerMediaItem {
        val libraryId = libraryIds[this.globalId()]
        return if (libraryId != null) {
            this.copy(
                itemId = libraryId,
                provider = "library",
                album = album.let { it?.forResponse() },
                artists = artists.let { it?.forResponse() },
            )
        } else {
            this
        }
    }

    fun Collection<ServerMediaItem>.forResponse(): List<ServerMediaItem> {
        return this.map { it.forResponse() }
    }

    fun addPlayers(vararg players: ServerPlayer) {
        players.forEach { player ->
            player.activeSource?.let {
                this.queues.add(ServerQueue(queueId = it, available = true))
            }
        }

        this.players.addAll(players)
    }

    fun addShortcut(item: ServerMediaItem) {
        shortcuts.add(item.uri!!)
    }

    fun getState(playerId: String): PlayerState? {
        val player = players.find { it.playerId == playerId }
        return player?.state
    }

    fun getCurrentlyPlaying(playerId: String): ServerMediaItem? {
        val player = players.find { it.playerId == playerId }
        return if (player != null) {
            queues.find { it.queueId == player.activeSource }?.currentItem?.mediaItem
        } else {
            null
        }
    }

    private fun findItem(request: Request): ServerMediaItem {
        val itemId = request.getArg("item_id")
        val provider = request.getArg("provider_instance_id_or_domain")

        return if (provider == "library") {
            val globalId = libraryIds.entries.first { it.value == itemId }.key
            mediaItems.first { it.globalId() == globalId }
        } else {
            mediaItems.first {
                it.providerMappings?.any { mapping ->
                    (mapping.providerInstance == provider || mapping.providerDomain == provider) && mapping.itemId == itemId
                } ?: false
            }
        }
    }

    private fun filterLibrary(
        request: Request,
        mediaType: MediaType,
    ): List<ServerMediaItem> {
        return mediaItems.dropNotInLibrary().filter { it.mediaType == mediaType.serverValue }
            .filter {
                val nameMatches = it.name.contains(request.getArg("search"), ignoreCase = true)
                val favoriteMatches = if (request.getArgOrNull("favorite") == "true") {
                    it.favorite ?: false
                } else {
                    true
                }

                nameMatches && favoriteMatches
            }
    }

    fun ServerMediaItem.getAlbumTracks(): List<ServerMediaItem> {
        return mediaItems.filter { it.album == this }
    }

    fun ServerMediaItem.getPlaylistTracks(): List<ServerMediaItem> {
        return mediaItems.filter { playlistItems[this.itemId]?.contains(it.itemId) ?: false }
    }

    fun getQueueForPlayer(player: ServerPlayer): List<ServerMediaItem> {
        return queueItems[player.activeSource]!!.map { it.mediaItem!! }
    }

    fun setPlaylist(playlist: ServerMediaItem, vararg tracks: ServerMediaItem) {
        playlistItems[playlist.itemId] = tracks.map { it.itemId }
    }

    fun setRequestErrors(reachable: Boolean) {
        this.requestErrors = reachable
    }

    fun setLegacyVersion(version: LegacyVersion) {
        this.legacyVersion = version
    }

    fun setReconnecting(reconnecting: Boolean) {
        if (reconnecting) {
            _sessionState.update {
                when (it) {
                    is SessionState.Connected.Direct -> {
                        SessionState.Reconnecting.Direct(
                            attempt = 1,
                            connectionInfo = it.connectionInfo,
                            connectionData = it.connectionData,
                        )
                    }

                    else -> error("Unhandled SessionState: $it")
                }
            }
        } else {
            _sessionState.update {
                when (it) {
                    is SessionState.Reconnecting.Direct -> {
                        SessionState.Connected.Direct(
                            connectionInfo = it.connectionInfo,
                            connectionData = it.connectionData,
                        )
                    }

                    else -> error("Unhandled SessionState: $it")
                }
            }
        }
    }

    fun setConnectionError(error: Exception?) {
        this.connectionError = error

        if (error != null) {
            _sessionState.value = SessionState.Disconnected.Error(error)
        }
    }

    fun setNetworkAvailable(available: Boolean) {
        if (available) {
            _sessionState.update {
                when (it) {
                    is SessionState.Reconnecting.Direct -> {
                        SessionState.Connected.Direct(
                            connectionInfo = it.connectionInfo,
                            connectionData = it.connectionData,
                        )
                    }

                    else -> error("Unhandled SessionState: $it")
                }
            }
        } else {
            _sessionState.update {
                when (it) {
                    is SessionState.Connected.Direct -> {
                        SessionState.Reconnecting.Direct(
                            attempt = 1,
                            connectionInfo = it.connectionInfo,
                            connectionData = it.connectionData,
                            isOnline = false,
                        )
                    }

                    else -> error("Unhandled SessionState: $it")
                }
            }
        }
    }

    fun setPlayerAudioFormat(player: ServerPlayer, audioFormat: AudioFormat) {
        playerAudioFormats[player.playerId] = audioFormat
    }

    enum class LegacyVersion {
        V2_8,
        V2_9,
    }
}

private fun answer(request: Request, result: JsonElement): Answer {
    return Answer(
        JsonObject(
            mapOf(
                "message_id" to JsonPrimitive(request.messageId),
                "result" to result,
            ),
        ),
    )
}

private inline fun <reified T> answer(request: Request, result: T): Answer {
    return answer(request, myJson.encodeToJsonElement(result))
}

private fun Request.getArg(arg: String): String {
    return getArgOrNull(arg)!!
}

private fun Request.getArgOrNull(arg: String): String? {
    return (args!![arg] as JsonPrimitive?)?.content
}

private fun ServerMediaItem.globalId(): Pair<String, String> {
    val providerMapping = this.providerMappings!![0]
    return Pair(providerMapping.providerInstance, providerMapping.itemId)
}
