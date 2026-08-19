package io.music_assistant.client.player.sendspin.session

import app.cash.turbine.test
import io.music_assistant.client.player.sendspin.transport.InboundTransportEvent
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Pins the legacy (unencrypted) session's wire behavior: the session must be
 * a pure extraction of the pre-session connection flow — same first frames,
 * byte-identical audio pass-through, same auth_ok handling — so older Music
 * Assistant servers see an unchanged client.
 */
class LegacySessionGoldenTest {
    private val authJson = """{"type":"auth","token":"tok-1","client_id":"client-1"}"""
    private val helloJson = """{"type":"client/hello","payload":{"client_id":"client-1"}}"""

    private val serverHelloJson =
        """{"type":"server/hello","payload":{"server_id":"srv-1","name":"Server One",""" +
            """"version":1,"active_roles":[],"connection_reason":"playback"}}"""

    private fun session(
        transport: FakeSendspinTransport,
        requiresAuth: Boolean,
    ): LegacySession = LegacySession(
        transport = transport,
        config = LegacySessionConfig(
            requiresAuth = requiresAuth,
            authJson = authJson,
            helloJson = helloJson,
        ),
    )

    @Test
    fun proxyModeFirstFrameIsAuthThenHelloAfterAuthOk() = runTest {
        val transport = FakeSendspinTransport()
        val session = session(transport, requiresAuth = true)
        session.start()

        assertEquals(authJson, transport.textOut.receive(), "first frame must be the auth message")
        transport.emit(InboundTransportEvent.Text(1, """{"type":"auth_ok"}"""))
        assertEquals(helloJson, transport.textOut.receive(), "hello follows auth_ok")
        session.close()
    }

    @Test
    fun directModeFirstFrameIsHello() = runTest {
        val transport = FakeSendspinTransport()
        val session = session(transport, requiresAuth = false)
        session.start()

        assertEquals(helloJson, transport.textOut.receive())
        session.close()
    }

    @Test
    fun binaryAudioFramesPassThroughByteIdentical() = runTest {
        val transport = FakeSendspinTransport()
        val session = session(transport, requiresAuth = false)
        session.audioFrames.test {
            session.start()
            val chunk = Random(1).nextBytes(4096)
            transport.emit(InboundTransportEvent.Binary(1, chunk))
            assertContentEquals(chunk, awaitItem())
            val second = byteArrayOf(4, 0, 1, 2, 3)
            transport.emit(InboundTransportEvent.Binary(1, second))
            assertContentEquals(second, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
        session.close()
    }

    @Test
    fun serverHelloIsForwardedVerbatimAndEmitsProtocolReady() = runTest {
        val transport = FakeSendspinTransport()
        val session = session(transport, requiresAuth = false)
        session.start()
        transport.textOut.receive()

        transport.emit(InboundTransportEvent.Text(1, serverHelloJson))
        assertEquals(serverHelloJson, session.applicationMessages.first())

        val ready = session.events.first { it is SessionEvent.ProtocolReady }
        assertIs<SessionEvent.ProtocolReady>(ready)
        assertEquals("srv-1", ready.serverId)
        assertEquals("Server One", ready.serverName)
        assertEquals(false, ready.isReconnectEpoch)
        session.close()
    }

    @Test
    fun reconnectEpochRepeatsHandshakeAndFlagsProtocolReady() = runTest {
        val transport = FakeSendspinTransport()
        val session = session(transport, requiresAuth = true)
        session.start()
        assertEquals(authJson, transport.textOut.receive())
        transport.emit(InboundTransportEvent.Text(1, """{"type":"auth_ok"}"""))
        assertEquals(helloJson, transport.textOut.receive())

        transport.emit(InboundTransportEvent.Reconnecting(1, attempt = 2))
        transport.emit(InboundTransportEvent.Connected(2, isReconnect = true))
        assertEquals(authJson, transport.textOut.receive(), "reconnect epoch re-runs auth")
        transport.emit(InboundTransportEvent.Text(2, """{"type":"auth_ok"}"""))
        assertEquals(helloJson, transport.textOut.receive())

        transport.emit(InboundTransportEvent.Text(2, serverHelloJson))
        val ready = session.events.first { it is SessionEvent.ProtocolReady }
        assertTrue(assertIs<SessionEvent.ProtocolReady>(ready).isReconnectEpoch)
        session.close()
    }

    @Test
    fun staleEpochFramesAreDropped() = runTest {
        val transport = FakeSendspinTransport()
        val session = session(transport, requiresAuth = false)
        session.start()
        transport.textOut.receive()

        transport.emit(InboundTransportEvent.Connected(2, isReconnect = true))
        transport.textOut.receive() // epoch-2 hello

        // A stale listener's frame from epoch 1 arriving after epoch 2 began.
        transport.emit(InboundTransportEvent.Text(1, """{"type":"server/time"}"""))
        transport.emit(InboundTransportEvent.Binary(1, byteArrayOf(1, 2, 3)))
        // A current-epoch frame afterwards must be the first thing forwarded.
        transport.emit(InboundTransportEvent.Text(2, """{"type":"server/state"}"""))

        assertEquals("""{"type":"server/state"}""", session.applicationMessages.first())
        session.close()
    }

    @Test
    fun staleControlEventsFromADeadEpochAreIgnored() = runTest {
        val transport = FakeSendspinTransport()
        val session = session(transport, requiresAuth = false)
        session.start()
        transport.textOut.receive()

        transport.emit(InboundTransportEvent.Connected(2, isReconnect = true))
        transport.textOut.receive() // epoch-2 hello

        // A cancelled epoch-1 listener emitting its teardown late must not
        // end the healthy epoch 2.
        transport.emit(InboundTransportEvent.Disconnected(1))
        transport.emit(InboundTransportEvent.Error(1, Exception("stale"), permanent = true))
        transport.emit(InboundTransportEvent.Text(2, serverHelloJson))

        val ready = session.events.first { it is SessionEvent.ProtocolReady }
        assertIs<SessionEvent.ProtocolReady>(ready)
        session.close()
    }

    @Test
    fun outboundSenderWritesTextFrames() = runTest {
        val transport = FakeSendspinTransport()
        val session = session(transport, requiresAuth = false)
        session.start()
        transport.textOut.receive()

        session.sender.sendJson("""{"type":"client/time"}""")
        assertEquals("""{"type":"client/time"}""", transport.textOut.receive())
        session.close()
    }
}
