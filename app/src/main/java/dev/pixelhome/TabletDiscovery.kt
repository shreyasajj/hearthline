package dev.pixelhome

import android.app.KeyguardManager
import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.*
import kotlinx.coroutines.*
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket

/**
 * Activity-resumed LAN advertisement and bounded read-only identity responder.
 *
 * On by default so HA can find a new tablet. Once the tablet is paired it has been found and HA talks to
 * it over the paired HTTPS channel, so advertising stops until the pairing is forgotten.
 */
class TabletDiscovery(context: Context, private val tabletId: () -> String, private val paired: () -> Boolean) {
    private val app = context.applicationContext
    private val preferences = app.getSharedPreferences("hearthline_discovery", 0)
    private val keyguard = app.getSystemService(KeyguardManager::class.java)
    private val nsd = app.getSystemService(NsdManager::class.java)
    private val connectivity = app.getSystemService(ConnectivityManager::class.java)
    private val main = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val version = app.packageManager.getPackageInfo(app.packageName, 0).versionName ?: "unknown"
    private var responder: TabletDiscoveryResponder? = null
    private var listener: NsdManager.RegistrationListener? = null
    private var job: Job? = null
    private var visible = false
    private var watchingNetwork = false
    /** Address the responder is bound to; network callbacks that leave it unchanged are ignored. */
    private var boundAddress: InetAddress? = null
    @Volatile private var generation = 0
    var enabled by mutableStateOf(preferences.getBoolean("enabled", true)); private set
    var status by mutableStateOf("Discovery waits for the visible app"); private set
    var addresses by mutableStateOf(""); private set

    private val networkChanged = Runnable { if(visible && privateAddress() != boundAddress) restart() }
    private fun changedNetwork() { main.post { if(visible) { main.removeCallbacks(networkChanged); main.postDelayed(networkChanged, 250) } } }
    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) = changedNetwork()
        override fun onLost(network: Network) = changedNetwork()
        override fun onLinkPropertiesChanged(network: Network, properties: LinkProperties) = changedNetwork()
    }

    fun changeEnabled(value: Boolean) {
        if(keyguard.isKeyguardLocked || keyguard.isDeviceLocked) return
        enabled = value; preferences.edit().putBoolean("enabled", value).apply()
        restart()
    }
    /** Call when the pairing state changes. */
    fun pairingChanged() { if(visible) restart() }
    fun visible(value: Boolean) {
        visible = value
        if(value && !watchingNetwork) {
            // Registering delivers onAvailable for current networks at once; networkChanged ignores those
            // because the address is unchanged, so resuming starts discovery exactly once.
            watchingNetwork = runCatching {
                connectivity.registerNetworkCallback(NetworkRequest.Builder()
                    .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                    .addTransportType(NetworkCapabilities.TRANSPORT_ETHERNET).build(), networkCallback)
                true
            }.getOrDefault(false)
        } else if(!value && watchingNetwork) {
            runCatching { connectivity.unregisterNetworkCallback(networkCallback) }; watchingNetwork = false
            main.removeCallbacks(networkChanged)
        }
        restart()
    }
    private fun privateAddress(): InetAddress? = preferredPrivateAddress(connectivity.allNetworks.asSequence().filter { network ->
        val caps = connectivity.getNetworkCapabilities(network)
        caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true || caps?.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) == true
    }.flatMap { connectivity.getLinkProperties(it)?.linkAddresses.orEmpty().asSequence() }.map { it.address })

    private fun restart() {
        stop()
        status = when {
            !enabled -> "Tablet discovery off"
            paired() -> "Paired · HA already knows this tablet, so discovery is paused. Forget the server to make it discoverable again."
            !visible -> "Discovery pauses when the app is not visible"
            else -> ""
        }
        if(status.isNotEmpty()) return
        val ticket = generation
        val payload = runCatching { TabletDiscoveryProtocol.metadata(tabletId(), version).toByteArray(Charsets.UTF_8) }.getOrElse {
            status = "Tablet installation identity unavailable"; return
        }
        val address = privateAddress() ?: run {
            boundAddress = null
            status = "Discovery unavailable. Connect private Wi-Fi/Ethernet; discovery starts automatically."; return
        }
        boundAddress = address
        status = "Starting tablet discovery…"
        job = scope.launch {
            val server = ServerSocket()
            try {
                server.reuseAddress = true
                server.bind(InetSocketAddress(address, TabletDiscoveryProtocol.PORT), 8)
                val next = TabletDiscoveryResponder(server, payload)
                val current = synchronized(this@TabletDiscovery) {
                    if(ticket != generation) null else next.also { responder = it }
                } ?: run { server.close(); return@launch }
                val local = address.hostAddress.orEmpty()
                main.post { if(ticket == generation) { addresses = local; advertise(ticket) } }
                current.run()
            } catch(e: CancellationException) { throw e }
            catch(_: Exception) { main.post { if(ticket == generation) status = "Discovery unavailable. Check that nothing else uses port 8787, then toggle discovery to retry." } }
            finally { runCatching { server.close() } }
        }
    }
    private fun advertise(ticket: Int) {
        val registration = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(info: NsdServiceInfo) {
                main.post {
                    if(ticket != generation) runCatching { nsd.unregisterService(this) }
                    else status = "Advertising tablet identity · TCP 8787"
                }
            }
            override fun onRegistrationFailed(info: NsdServiceInfo, error: Int) { main.post { if(ticket == generation) status = "mDNS advertisement unavailable; enter the tablet IP in HA (TCP 8787)" } }
            override fun onServiceUnregistered(info: NsdServiceInfo) {}
            override fun onUnregistrationFailed(info: NsdServiceInfo, error: Int) {}
        }
        listener = registration
        val info = NsdServiceInfo().apply {
            serviceName = "Hearthline-" + tabletId().take(8)
            serviceType = TabletDiscoveryProtocol.SERVICE
            port = TabletDiscoveryProtocol.PORT
            setAttribute("schema_version", "1"); setAttribute("tablet_id", tabletId())
        }
        runCatching { nsd.registerService(info, NsdManager.PROTOCOL_DNS_SD, registration) }.onFailure {
            status = "mDNS unavailable; enter the tablet IP in HA (TCP 8787)"
        }
    }
    private fun stop() {
        generation++
        listener?.let { runCatching { nsd.unregisterService(it) } }; listener = null
        synchronized(this) { responder?.close(); responder = null }
        job?.cancel(); job = null; addresses = ""; boundAddress = null
    }
    fun close() { visible(false); scope.cancel() }
}
