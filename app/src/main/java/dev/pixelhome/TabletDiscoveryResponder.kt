package dev.pixelhome

import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Semaphore
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketTimeoutException

/**
 * Serves the read-only identity response on an already-bound socket. Plain JVM so it is unit-testable.
 * Up to [maxClients] requests are answered at once; further connections are closed immediately, so one
 * slow client cannot delay HA's probe.
 */
internal class TabletDiscoveryResponder(
    private val server: ServerSocket,
    private val payload: ByteArray,
    private val maxClients: Int = 4,
    private val readTimeoutMs: Int = 1500,
    private val deadlineMs: Long = 2000,
) {
    private val slots = Semaphore(maxClients)
    private val clients = java.util.Collections.newSetFromMap(java.util.concurrent.ConcurrentHashMap<Socket, Boolean>())

    /** Accepts until cancelled or the socket closes. Returns normally when closed by [close]. */
    suspend fun run() = coroutineScope {
        server.soTimeout = 1000
        while (isActive && !server.isClosed) {
            val client = try { server.accept() } catch (_: SocketTimeoutException) { continue } catch (e: java.io.IOException) {
                if (server.isClosed) break else throw e
            }
            if (!slots.tryAcquire()) { runCatching { client.close() }; continue }
            clients.add(client)
            launch(Dispatchers.IO) {
                try { client.use { respond(it) } } finally { clients.remove(client); slots.release() }
            }
        }
    }

    fun close() {
        runCatching { server.close() }
        // Iterate the concurrent set directly: Kotlin's toList() size-one shortcut can
        // call next() after the last client concurrently removes itself.
        clients.forEach { runCatching { it.close() } }
    }

    private fun respond(client: Socket) {
        try {
            client.soTimeout = readTimeoutMs
            val input = client.getInputStream(); val header = StringBuilder()
            val deadline = System.nanoTime() + deadlineMs * 1_000_000
            while (header.length < TabletDiscoveryProtocol.MAX_HEADER && System.nanoTime() < deadline) {
                val value = input.read(); if (value < 0) return
                header.append(value.toChar())
                if (header.endsWith("\r\n\r\n")) break
            }
            val valid = TabletDiscoveryProtocol.validRequest(header.toString())
            val body = if (valid) payload else "{}".toByteArray()
            val response = "HTTP/1.1 ${if (valid) "200 OK" else "400 Bad Request"}\r\nContent-Type: application/json\r\nContent-Length: ${body.size}\r\nCache-Control: no-store\r\nConnection: close\r\n\r\n"
            client.getOutputStream().apply { write(response.toByteArray(Charsets.US_ASCII)); write(body); flush() }
        } catch (_: Exception) { /* Malformed/slow clients are closed; no payload/header logging. */ }
    }
}

/** Prefers IPv4 site-local, then IPv6 ULA (fc00::/7). Never loopback, link-local, wildcard or public. */
internal fun preferredPrivateAddress(candidates: Sequence<InetAddress>): InetAddress? =
    candidates.filter { it.isSiteLocalAddress || (it.address.size == 16 && (it.address[0].toInt() and 0xfe) == 0xfc) }
        .sortedBy { it.address.size }.firstOrNull()
