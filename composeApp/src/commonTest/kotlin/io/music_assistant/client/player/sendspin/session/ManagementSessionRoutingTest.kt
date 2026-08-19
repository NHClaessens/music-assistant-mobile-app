package io.music_assistant.client.player.sendspin.session

import io.music_assistant.client.player.sendspin.noise.SendspinPsk
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Covers the session-level routing of management requests: activity/trust
 * scoping, the single management/result reply, and the respond-then-goodbye
 * sequence when the requester removes its own record. Command semantics
 * themselves are covered by the management handler's own tests.
 */
internal class ManagementSessionRoutingTest : EncryptedSessionTestHarness() {
    @Test
    fun managementOnSessionWithoutManagementActivityIsPermissionDenied() = runRealTime {
        val f = fixture(this)
        val server = FakeServer(f, SendspinPsk.SENTINEL_PSK)
        f.session.start()
        server.establish()
        server.completeHelloExchange()
        server.activate() // playback only
        assertIs<SessionEvent.ProtocolReady>(f.nextEventSkippingNegotiation())
        assertIs<SessionEvent.Activated>(f.nextEvent())

        server.sendJson("""{"type":"management/list-records","payload":{}}""")
        val reply = Json.parseToJsonElement(server.receiveJson()).jsonObject
        assertEquals("management/result", reply.getValue("type").jsonPrimitive.content)
        assertEquals(
            "permission_denied",
            reply.getValue("payload").jsonObject.getValue("result").jsonPrimitive.content,
        )
        assertEquals(0, f.transport.disconnectCount, "connection stays open")
    }

    @Test
    fun managementOnLongTermSessionSucceedsAndOwnRecordRemovalCloses() = runRealTime {
        val f = fixture(this)
        val longTermPsk = ByteArray(32) { 0x33 }
        f.trustStore.recordLongTermPsk(longTermPsk, f.serverId)
        val server = FakeServer(f, longTermPsk)
        f.session.start()
        server.establish()
        server.completeHelloExchange()
        server.activate(activities = """["management"]""", activeRoles = "[]")
        assertIs<SessionEvent.ProtocolReady>(f.nextEventSkippingNegotiation())
        assertIs<SessionEvent.Activated>(f.nextEvent())

        server.sendJson("""{"type":"management/list-records","payload":{}}""")
        val listReply = Json.parseToJsonElement(server.receiveJson()).jsonObject
        assertEquals(
            "ok",
            listReply.getValue("payload").jsonObject.getValue("result").jsonPrimitive.content,
        )

        // Removing the session's own record: response first, then goodbye
        // unauthorized and close.
        val ownPskId = f.trustStore.pskIdOf(longTermPsk)
        server.sendJson(
            """{"type":"management/remove-record","payload":{"psk_id":"$ownPskId"}}""",
        )
        val removeReply = Json.parseToJsonElement(server.receiveJson()).jsonObject
        assertEquals(
            "ok",
            removeReply.getValue("payload").jsonObject.getValue("result").jsonPrimitive.content,
        )
        val goodbye = Json.parseToJsonElement(server.receiveJson()).jsonObject
        assertEquals("client/goodbye", goodbye.getValue("type").jsonPrimitive.content)
        assertEquals(
            "unauthorized",
            goodbye.getValue("payload").jsonObject.getValue("reason").jsonPrimitive.content,
        )
        assertTrue(f.trustStore.records().none { it.serverId == f.serverId })
        withTimeout(5_000) {
            while (f.transport.disconnectCount == 0) delay(10)
        }
    }
}
