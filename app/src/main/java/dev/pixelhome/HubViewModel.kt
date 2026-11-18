package dev.pixelhome

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.SystemClock
import androidx.compose.runtime.*
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject

class HubViewModel : ViewModel() {
    private val navigation = PlaybackNavigation()
    private val access = ActionAccess()
    val connectionDraft = HaConnectionDraft()
    private var tokenQrJob: Job? = null
    fun attachAccessCheck(check: () -> Boolean) { access.install(check) }
    private fun unlocked() = access.allowed
    val assistant = AssistantDisplay(viewModelScope, { unlocked() }, { page.name.lowercase() })
    fun assistantNotice() { message = "No browser is available to open this source." }
    var page by mutableStateOf(PlaybackNavigation.Page.HOME); private set
    var demo by mutableStateOf(true); private set
    var config by mutableStateOf(DashboardConfig()); private set
    var entities by mutableStateOf<Map<String, Entity>>(emptyMap()); private set
    var selectedPlayer by mutableStateOf(""); private set
    var connected by mutableStateOf(false); private set
    var lastStateAt by mutableStateOf<Long?>(null); private set
    var busy by mutableStateOf(false); private set
    var pairingWithCode by mutableStateOf(false); private set
    var message by mutableStateOf<String?>(null); private set
    var hubEnabled by mutableStateOf(true); private set
    var dark by mutableStateOf(false)
    var artwork by mutableStateOf<Bitmap?>(null); private set
    private var artworkKey = ""
    var pairingChoices by mutableStateOf<List<Pair<String,String>>>(emptyList()); private set
    var pairingStatus by mutableStateOf("No remembered server"); private set
    var configRevision by mutableStateOf(""); private set
    var telemetryEnabled by mutableStateOf(false); private set
    var lastPush by mutableStateOf<String?>(null); private set
    var tabletIdentity by mutableStateOf(""); private set
    var paired by mutableStateOf(false); private set
    private var pairingStore: TabletPairingStore? = null
    private var remembered: JSONObject? = null
    private var telemetrySnapshot: (() -> JSONObject)? = null
    private var telemetrySession = ""
    private var telemetrySequence = 0L
    private var lastTelemetryShape = ""
    private var lastPushMonotonic = 0L
    private var failures = 0
    private var authRevoked = false
    fun attachPairing(context: android.content.Context, host: HubHost, screen: () -> String) {
        telemetrySnapshot = { tabletTelemetry(context,host,screen(),tabletIdentity,telemetrySession,telemetrySequence) }
        if(pairingStore != null) return
        try {
            val store=TabletPairingStore(context.applicationContext); pairingStore=store; tabletIdentity=store.tabletId
            remembered=store.load()
            remembered?.let { record ->
                api=HomeAssistant(record.getString("origin"),record.getString("credential"),record.getString("entry_id"))
                paired=true; telemetryEnabled=record.optBoolean("telemetry",false); demo=false; entities=emptyMap(); config=DashboardConfig(); selectedPlayer=""
                pairingStatus="Restoring remembered pairing"; foreground(foreground)
            }
        } catch(_: Exception) { pairingStatus="Stored pairing unavailable. Forget server and pair again." }
    }
    fun loadPairingChoices() {
        if(!unlocked() || busy) return
        busy=true
        viewModelScope.launch {
            try {
                val choices=HomeAssistant(connectionDraft.url,connectionDraft.token).pairingChoices()
                pairingChoices=(0 until choices.length()).map { choices.getJSONObject(it) }.filter { !it.getBoolean("paired") && (it.isNull("tablet_id") || it.optString("tablet_id")==tabletIdentity) }
                    .map { it.getString("entry_id") to it.getString("name") }
                connectionDraft.note=if(pairingChoices.isEmpty()) "Add a Hearthline tablet in HA, or revoke its previous pairing in options." else "Choose the tablet dashboard to remember."
            } catch(e: CancellationException) { throw e }
            catch(_: Exception) { connectionDraft.note="Could not load tablets. Check HTTPS, administrator token and integration installation." }
            finally { busy=false }
        }
    }
    fun pairTablet(entry: String) {
        if(!unlocked() || busy || pairingChoices.none { it.first==entry }) return
        val store=pairingStore ?: return
        busy=true
        viewModelScope.launch {
            try {
                val setup=HomeAssistant(connectionDraft.url,connectionDraft.token)
                remember(store,setup,setup.pair(store.tabletId,entry))
            } catch(e: CancellationException) { throw e }
            catch(_: Exception) { connectionDraft.note="Pairing failed. Check the tablet entry and revoke a previous pairing before retrying." }
            finally { busy=false }
        }
    }
    /** Pairs with the one-time code shown in HA → hearthline-ha → Configure → Pair a tablet with a code. */
    fun pairWithCode() {
        if (busy) return
        if (!unlocked()) { connectionDraft.note="Unlock the tablet before pairing."; return }
        connectionDraft.pairingInputError()?.let { connectionDraft.note=it; return }
        val store=pairingStore ?: run {
            connectionDraft.note="Tablet pairing storage is unavailable. Close and reopen the app, then try again."
            return
        }
        connectionDraft.note="Pairing with Home Assistant…"
        busy=true; pairingWithCode=true
        viewModelScope.launch {
            try {
                val setup=HomeAssistant(connectionDraft.url,"")
                remember(store,setup,setup.pairWithCode(store.tabletId,connectionDraft.code))
            } catch(e: CancellationException) { throw e }
            catch(_: HaAuthorizationException) { connectionDraft.note="Code not accepted. Codes work once and expire after 5 minutes; open Pair a tablet with a code in HA for a new one." }
            catch(_: HaRateLimitedException) { connectionDraft.note="Too many wrong codes. Wait 5 minutes, then create a new code in HA." }
            catch(e: IllegalArgumentException) { connectionDraft.note=e.message ?: "Check the HTTPS server address." }
            catch(_: javax.net.ssl.SSLException) { connectionDraft.note="Secure connection failed. Use an HTTPS address with a certificate trusted by this tablet." }
            catch(_: java.net.UnknownHostException) { connectionDraft.note="Home Assistant address could not be found. Check the hostname and tablet network connection." }
            catch(_: java.net.SocketTimeoutException) { connectionDraft.note="Home Assistant did not respond in time. Check the address and network, then try again." }
            catch(_: java.io.IOException) { connectionDraft.note="Could not reach Home Assistant. Check the HTTPS address, port and tablet network connection." }
            catch(_: Exception) { connectionDraft.note="Pairing failed. Check that hearthline-ha 0.7.5 or later is installed and the tablet entry is not already paired." }
            finally { busy=false; pairingWithCode=false }
        }
    }
    private suspend fun remember(store: TabletPairingStore, setup: HomeAssistant, result: org.json.JSONObject) {
        val entry=result.getString("entry_id"); val credential=result.getString("credential")
        val server=HomeAssistant(setup.origin.toString(),credential,entry)
        try { store.save(server.origin.toString(),entry,credential,false) }
        catch(e: Exception) { server.unpair(); throw e }
        remembered=store.load(); api=server; paired=true; authRevoked=false; failures=0
        demo=false; connected=false; entities=emptyMap(); config=DashboardConfig(); selectedPlayer=""
        telemetrySession=""; lastPush=null; telemetryEnabled=false; pairingStatus="Paired successfully · connecting to your dashboard"
        connectionDraft.clearToken(); pairingChoices=emptyList()
        assistant.configure(assistant.enabled,result.getString("display_id"))
        foreground(foreground)
    }
    fun telemetry(value: Boolean) {
        if(!unlocked()) return
        val record=remembered ?: return
        try {
            pairingStore?.save(record.getString("origin"),record.getString("entry_id"),record.getString("credential"),value)
            telemetryEnabled=value; telemetrySession=""; lastTelemetryShape=""
        } catch(_: Exception) { pairingStatus="Could not save telemetry preference" }
    }
    fun reconnect() { if(unlocked()) { authRevoked=false; failures=0; telemetrySession=""; foreground(foreground) } }
    fun forgetServer() {
        if(!unlocked() || busy) return
        val server=api?.takeIf { it.pairedEntry!=null }
        busy=true
        viewModelScope.launch {
            try {
                if(server!=null) { try { server.unpair() } catch(_: HaAuthorizationException) { /* Already revoked */ } }
                pairingStore?.forget(); remembered=null; paired=false; telemetryEnabled=false; telemetrySession=""
                api=null; stopSync(); assistant.lifecycle(foreground,null); connected=false
                demo=false; entities=emptyMap(); config=DashboardConfig(); artwork=null; selectedPlayer=""
                pairingStatus="Server forgotten and pairing revoked"; configRevision=""; lastPush=null
            } catch(e: CancellationException) { throw e }
            catch(_: Exception) { pairingStatus="Could not revoke. Restore connection and retry, or revoke in HA options and use Forget locally." }
            finally { busy=false }
        }
    }
    fun forgetLocally() {
        if(!unlocked()) return
        pairingStore?.forget(); remembered=null; paired=false; telemetryEnabled=false
        api=null; stopSync(); connected=false; demo=false; entities=emptyMap(); config=DashboardConfig(); artwork=null
        assistant.lifecycle(foreground,null); pairingStatus="Forgot locally. Revoke the tablet in HA options to invalidate its server credential."
    }
    private suspend fun sendTelemetry(server: HomeAssistant) {
        if(!telemetryEnabled || server.pairedEntry==null) return
        if(telemetrySession.isEmpty()) {
            val session=server.telemetrySession()
            if(api!==server) return
            telemetrySession=session; telemetrySequence=0; lastTelemetryShape=""
        }
        val session=telemetrySession
        val payload=telemetrySnapshot?.invoke() ?: return
        val shape=JSONObject(payload.toString()).apply { remove("sequence"); remove("observed_at"); remove("session_id") }.toString()
        if(shape==lastTelemetryShape && now()-lastPushMonotonic<30000) return
        server.pushTelemetry(payload)
        // A pause or reconnect during the push started a new session; this result belongs to the old one.
        if(telemetrySession!=session || api!==server) return
        telemetrySequence++; lastTelemetryShape=shape; lastPushMonotonic=now(); lastPush=java.time.OffsetDateTime.now().toString()
    }
    private var configEntity = "sensor.pixel_home_dashboard"
    private var api: HomeAssistant? = null
    private var poll: Job? = null
    private var telemetryJob: Job? = null
    private var artworkJob: Job? = null
    /** True while HA's push stream is delivering; false while polling or reconnecting. */
    private var streaming = false
    private var foreground = false
    private fun stopSync() {
        poll?.cancel(); poll = null; telemetryJob?.cancel(); telemetryJob = null
        artworkJob?.cancel(); artworkJob = null; streaming = false
    }
    private val mutex = Mutex()
    val player get() = entities[selectedPlayer]
    val playing get() = player?.state == "playing"
    val canControl get() = unlocked() && !busy && (demo || connected)
    init { demoData() }
    private fun now() = SystemClock.elapsedRealtime()
    fun touch() { navigation.touch(now()) }
    fun open(value: PlaybackNavigation.Page) { if (!unlocked()) return; if(value != PlaybackNavigation.Page.SETTINGS) { connectionDraft.clearToken(); connectionDraft.cancelRequests(); tokenQrJob?.cancel() }; navigation.open(value, now()); page = navigation.page() }
    fun hub(value: Boolean) { hubEnabled = value; if (!value) navigation.leaveHub(now()); page = navigation.page() }
    fun tick() { if (!foreground) return; if (assistant.answer != null) touch(); navigation.tick(hubEnabled, playing, now(), config.idleSeconds * 1000L); page = navigation.page() }
    fun clearMessage() { message = null }
    fun select(id: String) {
        if (!unlocked()) return
        if (id !in config.players) return
        selectedPlayer = id; artwork = null; artworkKey = ""; touch()
    }
    fun foreground(value: Boolean) {
        foreground = value; stopSync()
        if(!value) {
            connectionDraft.scannerRequested = false
            if(connectionDraft.importingImage) connectionDraft.note = "QR image import cancelled. Try again."
            tokenQrJob?.cancel(); connectionDraft.importingImage = false
            if(!unlocked()) { connectionDraft.clearToken(); connectionDraft.cancelRequests() }
        }
        touch()
        if(!value) telemetrySession=""
        assistant.lifecycle(value && !authRevoked, api)
        val server = api
        if (value && server != null) {
            poll = viewModelScope.launch { syncLoop(server) }
            telemetryJob = viewModelScope.launch { telemetryLoop() }
        }
    }
    fun acceptTokenQr(value: HaQrToken) {
        if(!foreground || !unlocked() || page != PlaybackNavigation.Page.SETTINGS) return
        connectionDraft.importToken(value)
    }
    fun importTokenQrImage(resolver: android.content.ContentResolver, uri: android.net.Uri) {
        if(!foreground || !unlocked() || page != PlaybackNavigation.Page.SETTINGS) return
        tokenQrJob?.cancel()
        val session = QrImportSession(now())
        connectionDraft.importingImage = true; connectionDraft.note = "Reading QR image on this tablet…"
        tokenQrJob = viewModelScope.launch {
            try {
                val raw = withContext(Dispatchers.IO) { TokenQrImage.read(resolver, uri) }
                val token = session.accept(raw, now(), unlocked(), foreground && page == PlaybackNavigation.Page.SETTINGS)
                acceptTokenQr(token)
            } catch(e: CancellationException) { throw e }
            catch(_: Exception) { connectionDraft.note = "Could not import this QR image. Select Home Assistant's long-lived token QR, or scan/paste it instead." }
            finally { if(tokenQrJob === coroutineContext[Job]) connectionDraft.importingImage = false }
        }
    }
    fun connect(url: String, token: String, sensor: String) {
        if (!unlocked()) return
        if (token.isBlank() || !sensor.matches(Regex("sensor\\.[a-z0-9_]+"))) {
            message = "Enter a token and a valid sensor entity ID."; return
        }
        try {
            api = HomeAssistant(url, token.trim())
            paired=false; authRevoked=false; failures=0; telemetrySession=""
            demo = false; connected = false; message = null; entities = emptyMap(); config = DashboardConfig()
            selectedPlayer = ""; configEntity = sensor; artwork = null; artworkKey = ""
            navigation.reset(now()); page = navigation.page(); foreground(foreground)
        } catch (e: IllegalArgumentException) { message = e.message ?: "Invalid server URL" }
    }
    fun useDemo() {
        if (!unlocked()) return
        api = null; paired=false; stopSync(); connected = false; artwork = null; artworkKey = ""
        assistant.lifecycle(foreground, null)
        demo = true; message = null; navigation.reset(now()); page = navigation.page(); demoData()
    }
    /** One full fetch: the polling path, and the fallback while the push stream is unavailable. */
    private suspend fun refresh() = mutex.withLock {
        val server = api ?: return@withLock
        try {
            val managed = if(server.pairedEntry!=null) server.tabletConfig() else null
            val states = server.states()
            val resolved = if(managed!=null) parseConfig(managed.getJSONObject("dashboard"))
                else parseConfig((states[configEntity]?.takeIf { it.available } ?: error("Dashboard config missing")).attrs)
            if (api !== server) return@withLock
            applySnapshot(server, managed, resolved, states)
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { syncFailed(server, e) }
    }
    private fun applySnapshot(server: HomeAssistant, managed: JSONObject?, resolved: DashboardConfig, states: Map<String, Entity>) {
        managed?.let {
            configRevision=it.getString("revision")
            if(assistant.displayId!=it.getString("display_id")) assistant.configure(assistant.enabled,it.getString("display_id"))
        }
        config = resolved; entities = states; lastStateAt = now()
        if (!connected) message = null
        connected = true; failures=0
        pairingStatus=when { !paired -> "Legacy session connection"; streaming -> "Connected · live updates"; else -> "Connected · remembered pairing" }
        if (selectedPlayer !in resolved.players) selectedPlayer = resolved.players.firstOrNull().orEmpty()
        statesChanged(server)
    }
    private fun statesChanged(server: HomeAssistant) {
        lastStateAt = now()
        navigation.playback(playing, hubEnabled, now()); page = navigation.page()
        val path = player?.attrs?.optString("entity_picture").orEmpty()
        val key = selectedPlayer + path
        if (key == artworkKey) return
        artworkKey = key; artwork = null; artworkJob?.cancel()
        // Outside the sync mutex: a slow image must not delay state updates.
        if (path.isNotBlank()) artworkJob = viewModelScope.launch {
            val bitmap = server.artwork(path,selectedPlayer)
            if (api === server && artworkKey == key) artwork = bitmap
        }
    }
    private fun syncFailed(server: HomeAssistant, error: Exception) {
        if (api !== server) return
        if (error is HaAuthorizationException) {
            authRevoked=true; connected=false; streaming=false; telemetrySession=""; assistant.lifecycle(false,null)
            pairingStatus="Authorization revoked or expired. Revoke/forget the pairing and pair again."
            message=pairingStatus
        } else {
            failures++; connected = false; streaming = false
            message = "Offline. Retrying with bounded backoff; displayed values may be stale."
            pairingStatus="Offline · reconnecting"
        }
    }
    private fun pollDelay() = if(failures==0) 2000L else (2000L shl failures.coerceAtMost(5)).coerceAtMost(60000L) + kotlin.random.Random.nextLong(0,1000)

    /**
     * Paired tablets hold HA's push stream open while visible, so state changes arrive as they happen.
     * Legacy connections, and integrations older than the stream, poll every two seconds.
     */
    private suspend fun syncLoop(server: HomeAssistant) {
        var streamRetryAt = if(server.pairedEntry != null) 0L else Long.MAX_VALUE
        var streamFailures = 0
        while (currentCoroutineContext().isActive && !authRevoked && api === server) {
            if (now() < streamRetryAt) { refresh(); delay(pollDelay()); continue }
            val end = try { streamOnce(server) } catch (e: CancellationException) { throw e } catch (_: Exception) { StreamEnd(false, null) }
            streaming = false
            when {
                end.code == 401 || end.code == 403 -> { syncFailed(server, HaAuthorizationException()); return }
                // Older integration without the stream: poll, and look again occasionally after an HA update.
                end.code == 404 -> streamRetryAt = now() + 10 * 60_000L
                end.received -> { streamFailures = 0; delay(250) }  // Normal end (options reload, HA restart): reconnect.
                else -> {
                    // Unreachable or a proxy without WebSocket support. Polling keeps the dashboard current meanwhile.
                    streamFailures++
                    if (streamFailures >= 3) streamRetryAt = now() + 60_000L
                    refresh(); delay(pollDelay())
                }
            }
        }
    }
    private data class StreamEnd(val received: Boolean, val code: Int?)
    private suspend fun streamOnce(server: HomeAssistant): StreamEnd {
        val entry = requireNotNull(server.pairedEntry)
        val events = kotlinx.coroutines.channels.Channel<Any>(kotlinx.coroutines.channels.Channel.UNLIMITED)
        val socket = server.stream(object : okhttp3.WebSocketListener() {
            override fun onMessage(webSocket: okhttp3.WebSocket, text: String) { events.trySend(text) }
            override fun onClosing(webSocket: okhttp3.WebSocket, code: Int, reason: String) { webSocket.close(1000, null); events.close() }
            override fun onClosed(webSocket: okhttp3.WebSocket, code: Int, reason: String) { events.close() }
            override fun onFailure(webSocket: okhttp3.WebSocket, t: Throwable, response: okhttp3.Response?) {
                events.trySend(StreamEnd(false, response?.code)); events.close()
            }
        })
        var received = false
        try {
            while (true) {
                // HA pings every ten seconds; three missed pings means the socket is dead.
                val item = withTimeoutOrNull(30_000) { events.receiveCatching() } ?: return StreamEnd(received, null)
                when (val value = item.getOrNull() ?: return StreamEnd(received, null)) {
                    is StreamEnd -> return value.copy(received = received)
                    is String -> {
                        val event = withContext(Dispatchers.Default) { StreamEvent.parse(value, entry) }
                        mutex.withLock {
                            if (api !== server) return StreamEnd(received, null)
                            when (event) {
                                is StreamEvent.Snapshot -> { streaming = true; applySnapshot(server, event.config, parseConfig(event.config.getJSONObject("dashboard")), event.states) }
                                is StreamEvent.State -> { entities = entities + (event.entity.id to event.entity); statesChanged(server) }
                                is StreamEvent.Removed -> { entities = entities - event.id; statesChanged(server) }
                                StreamEvent.Ping -> lastStateAt = now()
                            }
                        }
                        received = true
                    }
                }
            }
        } finally { socket.cancel() }
    }

    /**
     * Samples the tablet once a second and pushes when something changed, so HA learns about dock, lock
     * and power changes within about a second. Failures here never affect the dashboard connection.
     */
    private suspend fun telemetryLoop() {
        var failures = 0
        while (currentCoroutineContext().isActive) {
            val server = api
            if (server != null && connected && !authRevoked) {
                try { sendTelemetry(server); failures = 0 }
                catch (e: CancellationException) { throw e }
                catch (_: Exception) { failures++; telemetrySession = "" }  // Start a fresh session on the next attempt.
            }
            delay(if (failures == 0) 1000L else (1000L shl failures.coerceAtMost(5)).coerceAtMost(30000L))
        }
    }
    fun command(id: String, action: String, values: JSONObject = JSONObject()) {
        if (!canControl || entities[id] == null || (entities[id]?.available != true && !(id.startsWith("scene.") && entities[id]?.state != "unavailable"))) return
        touch()
        if (demo) { demoCommand(id, action, values); return }
        val server = api ?: return
        busy = true
        viewModelScope.launch {
            try {
                val sent = mutex.withLock {
                    if (api === server) access.dispatch { server.service(id.substringBefore('.'), action, id, values) } else false
                }
                if (!sent) return@launch
                // While streaming, the resulting state change arrives on its own; otherwise fetch now.
                if (api === server) { message = "Command sent; waiting for device state."; if(!streaming) refresh() }
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { if (api === server) message = "Command failed. Device state has not been changed locally." }
            finally { busy = false }
        }
    }
    fun playShortcut(shortcut: MusicShortcut) {
        command(selectedPlayer, "play_media", JSONObject().put("media_content_id", shortcut.uri).put("media_content_type", shortcut.type))
    }
    private fun demoCommand(id: String, action: String, values: JSONObject) {
        val old = entities[id] ?: return
        val attrs = JSONObject(old.attrs.toString())
        var state = old.state
        when (action) {
            "media_play_pause" -> state = if (state == "playing") "paused" else "playing"
            "media_play", "play_media" -> state = "playing"
            "media_pause" -> state = "paused"
            "media_next_track", "media_previous_track" -> attrs.put("media_title", if (attrs.optString("media_title") == "Slow Morning") "Quiet Hours" else "Slow Morning")
            "volume_set" -> attrs.put("volume_level", values.getDouble("volume_level"))
            "turn_on" -> { state = "on"; if (values.has("brightness_pct")) attrs.put("brightness", values.getInt("brightness_pct") * 255 / 100) }
            "turn_off" -> state = "off"
            "set_temperature" -> attrs.put("temperature", values.getDouble("temperature"))
            "unlock" -> state = "unlocked"
            "lock" -> state = "locked"
        }
        entities = entities + (id to Entity(id, state, attrs))
        navigation.playback(playing, hubEnabled, now()); page = navigation.page()
        message = "Demo only — no device was contacted."
    }
    private fun demoData() {
        config = DashboardConfig("Good morning", listOf("light.living_room", "climate.living_room", "switch.kitchen", "lock.front_door"),
            listOf("scene.good_morning", "scene.movie_time"), listOf("media_player.living_room"),
            listOf(MusicShortcut("Slow mornings", "demo:morning", "music"), MusicShortcut("Evening unwind", "demo:evening", "music")))
        fun entity(id: String, state: String, name: String, extras: String = "{}") =
            Entity(id, state, JSONObject(extras).put("friendly_name", name))
        entities = listOf(
            entity("light.living_room", "on", "Living room lights", "{\"brightness\":166,\"supported_color_modes\":[\"brightness\"]}"),
            entity("climate.living_room", "heat", "Climate", "{\"temperature\":72,\"current_temperature\":70,\"min_temp\":50,\"max_temp\":86,\"target_temp_step\":1,\"supported_features\":1}"),
            entity("switch.kitchen", "off", "Kitchen plug"), entity("lock.front_door", "locked", "Front door"),
            entity("scene.good_morning", "unknown", "Good morning"), entity("scene.movie_time", "unknown", "Movie time"),
            entity("media_player.living_room", "paused", "Living room speakers", "{\"media_title\":\"Slow Morning\",\"media_artist\":\"Mira Vale\",\"volume_level\":0.4,\"supported_features\":16777215}")
        ).associateBy { it.id }
        selectedPlayer = config.players.first()
    }
}
