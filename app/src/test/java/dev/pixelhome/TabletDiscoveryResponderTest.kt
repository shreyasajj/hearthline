package dev.pixelhome

import kotlinx.coroutines.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket

class TabletDiscoveryResponderTest {
    private val payload = TabletDiscoveryProtocol.metadata("10000000-0000-4000-8000-000000000001", "0.7.4").toByteArray()
    private lateinit var server: ServerSocket
    private lateinit var responder: TabletDiscoveryResponder
    private lateinit var job: Job
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Before fun start() {
        server = ServerSocket().apply { bind(InetSocketAddress(InetAddress.getLoopbackAddress(), 0)) }
        responder = TabletDiscoveryResponder(server, payload, maxClients = 2, readTimeoutMs = 3000, deadlineMs = 3000)
        job = scope.launch { responder.run() }
    }
    @After fun stop() { responder.close(); runBlocking { withTimeout(3000) { job.join() } }; scope.cancel() }

    private fun connect() = Socket(InetAddress.getLoopbackAddress(), server.localPort).apply { soTimeout = 3000 }
    private fun request(path: String = TabletDiscoveryProtocol.PATH): String = connect().use { socket ->
        socket.getOutputStream().write("GET $path HTTP/1.1\r\nHost: tablet\r\n\r\n".toByteArray())
        socket.getInputStream().readBytes().toString(Charsets.UTF_8)
    }

    @Test fun answersIdentityAndRejectsOtherPaths() {
        val ok = request()
        assertTrue(ok.startsWith("HTTP/1.1 200 OK\r\n"))
        assertTrue(ok.endsWith(String(payload)))
        assertTrue(request("/commands").startsWith("HTTP/1.1 400 Bad Request\r\n"))
    }

    @Test fun slowClientDoesNotBlockAnotherRequest() {
        connect().use { slow ->
            slow.getOutputStream().write("GET ".toByteArray())  // Holds one slot without finishing.
            val started = System.nanoTime()
            assertTrue(request().startsWith("HTTP/1.1 200 OK"))
            assertTrue("second request waited for the slow client", System.nanoTime() - started < 1_000_000_000)
        }
    }

    @Test fun connectionsBeyondTheLimitAreClosedNotQueued() {
        val held = List(2) { connect().apply { getOutputStream().write("G".toByteArray()) } }
        try {
            Thread.sleep(200)
            connect().use { extra -> assertEquals(-1, extra.getInputStream().read()) }
        } finally { held.forEach { it.close() } }
        Thread.sleep(200)
        assertTrue(request().startsWith("HTTP/1.1 200 OK"))  // Slots are released afterwards.
    }

    @Test fun closeEndsRunAndDropsActiveClients() {
        connect().use { active ->
            active.getOutputStream().write("GET ".toByteArray())
            Thread.sleep(200)
            responder.close()
            runBlocking { withTimeout(3000) { job.join() } }
            assertEquals(-1, active.getInputStream().read())
        }
    }

    @Test fun privateAddressPreference() {
        fun ip(value: String) = InetAddress.getByName(value)
        assertEquals(ip("192.168.1.20"), preferredPrivateAddress(sequenceOf(ip("fd00::5"), ip("192.168.1.20"))))
        assertEquals(ip("fd00::5"), preferredPrivateAddress(sequenceOf(ip("fe80::1"), ip("fd00::5"))))
        assertNull(preferredPrivateAddress(sequenceOf(ip("127.0.0.1"), ip("8.8.8.8"), ip("fe80::1"), ip("2001:db8::1"))))
    }
}
