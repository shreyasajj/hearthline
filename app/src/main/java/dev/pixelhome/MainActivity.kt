package dev.pixelhome

import android.os.Bundle
import android.content.Intent
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.withResumed
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.drop
import android.view.MotionEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.ViewModelProvider
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import org.json.JSONObject
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import dev.pixelhome.PlaybackNavigation.Page

class MainActivity : ComponentActivity() {
    var tabletDiscovery: TabletDiscovery? = null; private set
    private lateinit var model: HubViewModel
    private lateinit var host: HubHost
    private lateinit var receiver: SendspinReceiver
    private lateinit var ambient: AmbientHost
    private var wakingGesture = false
    private var unlockSwipeStart: Offset? = null
    private var consumingUnlockSwipe = false
    private var activityResumed = false
    private val tokenCameraPermission = registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.RequestPermission()) { granted ->
        if(::model.isInitialized && ::host.isInitialized) model.connectionDraft.cameraResult(granted, android.os.SystemClock.elapsedRealtime(), !host.isLockedNow() && model.page == Page.SETTINGS)
    }
    private val tokenImagePicker = registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.OpenDocument()) { uri ->
        if(::model.isInitialized && model.connectionDraft.consumeImageRequest(android.os.SystemClock.elapsedRealtime()) && uri != null) {
            lifecycleScope.launch { lifecycle.withResumed {
                if(::host.isInitialized && !host.isLockedNow() && model.page == Page.SETTINGS) model.importTokenQrImage(applicationContext.contentResolver, uri)
            } }
        }
    }
    private val ambientCameraPermission = registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.RequestPermission()) { granted ->
        if(::ambient.isInitialized && ::host.isInitialized && !host.isLockedNow() && model.page == Page.SETTINGS) {
            if(granted) ambient.change(ambient.settings.copy(faceWake=true)) else ambient.permissionNotice("Camera")
        }
    }
    private val ambientMicrophonePermission = registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.RequestPermission()) { granted ->
        if(::ambient.isInitialized && ::host.isInitialized && !host.isLockedNow() && model.page == Page.SETTINGS) {
            if(granted) ambient.change(ambient.settings.copy(voiceWake=true)) else ambient.permissionNotice("Microphone")
        }
    }
    fun enableFaceWake(enabled: Boolean) {
        if(!::host.isInitialized || host.isLockedNow()) return
        if(!enabled || checkSelfPermission(android.Manifest.permission.CAMERA)==android.content.pm.PackageManager.PERMISSION_GRANTED) ambient.change(ambient.settings.copy(faceWake=enabled))
        else ambientCameraPermission.launch(android.Manifest.permission.CAMERA)
    }
    fun enableVoiceWake(enabled: Boolean) {
        if(!::host.isInitialized || host.isLockedNow()) return
        if(!enabled || checkSelfPermission(android.Manifest.permission.RECORD_AUDIO)==android.content.pm.PackageManager.PERMISSION_GRANTED) ambient.change(ambient.settings.copy(voiceWake=enabled))
        else ambientMicrophonePermission.launch(android.Manifest.permission.RECORD_AUDIO)
    }
    fun scanHomeAssistantToken() {
        if(!::host.isInitialized || host.isLockedNow() || model.page != Page.SETTINGS) return
        model.connectionDraft.requestCamera(android.os.SystemClock.elapsedRealtime())
        if(checkSelfPermission(android.Manifest.permission.CAMERA) == android.content.pm.PackageManager.PERMISSION_GRANTED)
            model.connectionDraft.cameraResult(true, android.os.SystemClock.elapsedRealtime(), true)
        else tokenCameraPermission.launch(android.Manifest.permission.CAMERA)
    }
    fun chooseHomeAssistantTokenImage() {
        if(!::host.isInitialized || host.isLockedNow() || model.page != Page.SETTINGS) return
        model.connectionDraft.requestImage(android.os.SystemClock.elapsedRealtime())
        tokenImagePicker.launch(arrayOf("image/*"))
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val automaticReturn = intent.getBooleanExtra(DockReturnService.AUTO_RETURN, false)
        val launchGuard = getSystemService(android.app.KeyguardManager::class.java)
        if(automaticReturn && !launchGuard.isKeyguardLocked && !launchGuard.isDeviceLocked) {
            finish(); return
        }
        intent.removeExtra(DockReturnService.AUTO_RETURN)
        enableEdgeToEdge()
        model = ViewModelProvider(this)[HubViewModel::class.java]
        receiver = ViewModelProvider(this)[SendspinReceiver::class.java]
        host = HubHost(this)
        ambient = AmbientHost(this)
        host.onDeviceLocked = ambient::wake
        val keyguard = applicationContext.getSystemService(android.app.KeyguardManager::class.java)
        model.attachAccessCheck { !keyguard.isKeyguardLocked && !keyguard.isDeviceLocked }
        model.attachPairing(this,host) {
            when {
                ambient.asleep -> "clock"
                host.isLockedNow() && !host.lockScreenEligible -> "unknown"
                host.isLockedNow() -> if(host.showLockedDashboard) "home" else "clock"
                host.dashboardPreview -> "home"
                model.page == Page.IDLE -> "clock"
                else -> model.page.name.lowercase()
            }
        }
        tabletDiscovery=TabletDiscovery(this,{ model.tabletIdentity },{ model.paired })
        lifecycleScope.launch { snapshotFlow { model.paired }.drop(1).collect { tabletDiscovery?.pairingChanged() } }
        host.start()
        if(automaticReturn && (!host.lockScreenEligible || !host.dockEligible())) { finish(); return }
        host.restorePreferences(model)
        host.refresh()
        host.applyWindowPolicy()
        if(automaticReturn) host.prepareDockReturn()
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                while (true) { host.refresh(); model.assistant.concealIfLocked(); ambient.tick(model,receiver,host); delay(if(ambient.asleep) 1000 else 200) }
            }
        }
        setContent {
            SideEffect {
                if (ambient.keepScreenOn(model,host)) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                host.persistPreferences(model)
            }
            CompositionLocalProvider(LocalHubHost provides host, LocalSendspinReceiver provides receiver, LocalAmbient provides ambient) {
                PixelTheme(ambient.dark(model.dark),ambient.black(model.dark)) {
                    if(ambient.asleep && (!host.locked || host.lockScreenEligible)) AmbientClock(ambient::wake)
                    else if (host.locked) { if(host.lockScreenEligible) LockedHubScreen(model, host) else Surface(Modifier.fillMaxSize()) {} }
                    else if(host.dashboardPreview) DockDashboard(model,host,preview=true)
                    else PixelHome(model)
                    if(!host.locked) SendspinPairingDialog(receiver)
                }
            }
        }
    }
    override fun onStart() {
        super.onStart()
        if(::receiver.isInitialized) receiver.visible(true)
    }
    override fun onStop() {
        if(::receiver.isInitialized) receiver.visible(false)
        super.onStop()
    }
    override fun onDestroy() {
        tabletDiscovery?.close(); tabletDiscovery=null
        if (::ambient.isInitialized) ambient.close()
        if (::host.isInitialized) host.close()
        super.onDestroy()
    }
    override fun onResume() {
        super.onResume()
        activityResumed = true
        tabletDiscovery?.visible(true)
        if(::ambient.isInitialized) ambient.resume()
        if (::host.isInitialized) { host.refresh(); host.applyWindowPolicy(); host.startLockWatcherIfConfigured() }
        if (::model.isInitialized) model.foreground(true)
    }
    override fun onPause() {
        activityResumed = false
        tabletDiscovery?.visible(false)
        unlockSwipeStart = null; consumingUnlockSwipe = false
        if(::ambient.isInitialized) ambient.pause()
        if (::host.isInitialized) host.conceal()
        if (::model.isInitialized) model.foreground(false)
        super.onPause()
    }
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus && ::host.isInitialized) host.refresh()
    }
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        val automaticReturn = intent.getBooleanExtra(DockReturnService.AUTO_RETURN, false)
        val launchGuard = getSystemService(android.app.KeyguardManager::class.java)
        if(automaticReturn && !launchGuard.isKeyguardLocked && !launchGuard.isDeviceLocked) {
            if(!activityResumed) moveTaskToBack(true)
            return
        }
        intent.removeExtra(DockReturnService.AUTO_RETURN)
        if(::host.isInitialized) host.refresh()
        if(automaticReturn && ::host.isInitialized && (!host.lockScreenEligible || !host.dockEligible())) {
            if(!activityResumed) moveTaskToBack(true)
            return
        }
        if(automaticReturn && ::host.isInitialized) { if(::ambient.isInitialized) ambient.wake();host.prepareDockReturn() }
        setIntent(intent)
        if (::model.isInitialized && intent.hasCategory(Intent.CATEGORY_HOME)) model.open(Page.HOME)
    }
    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if(::ambient.isInitialized && ev.actionMasked == MotionEvent.ACTION_DOWN && ambient.asleep) {
            wakingGesture = true; ambient.wake(); return true
        }
        if(wakingGesture) { if(ev.actionMasked == MotionEvent.ACTION_UP || ev.actionMasked == MotionEvent.ACTION_CANCEL) wakingGesture=false; return true }
        if(::ambient.isInitialized) ambient.interaction()
        if (::host.isInitialized && host.isLockedNow() && !host.locked) { host.refresh(); return true }
        if (::model.isInitialized) model.touch()
        // Android owns the bottom navigation gesture. Just above it, the dock also accepts
        // an upward edge swipe and asks Android to present authentication.
        val swipeAllowed=::host.isInitialized && host.locked && host.lockScreenEligible && host.showLockedDashboard
        if(!swipeAllowed) { unlockSwipeStart=null;consumingUnlockSwipe=false }
        if(consumingUnlockSwipe) {
            if(ev.actionMasked==MotionEvent.ACTION_UP || ev.actionMasked==MotionEvent.ACTION_CANCEL) consumingUnlockSwipe=false
            return true
        }
        if(swipeAllowed) when(ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> unlockSwipeStart=Offset(ev.x,ev.y)
            MotionEvent.ACTION_POINTER_DOWN,MotionEvent.ACTION_CANCEL -> unlockSwipeStart=null
            MotionEvent.ACTION_MOVE,MotionEvent.ACTION_UP -> {
                val start=unlockSwipeStart
                if(start!=null && isDockUnlockSwipe(start.x,start.y,ev.x,ev.y,window.decorView.height.toFloat(),resources.displayMetrics.density)) {
                    val cancel=MotionEvent.obtain(ev).apply { action=MotionEvent.ACTION_CANCEL }
                    super.dispatchTouchEvent(cancel);cancel.recycle()
                    unlockSwipeStart=null;consumingUnlockSwipe=ev.actionMasked!=MotionEvent.ACTION_UP
                    host.unlock()
                    return true
                }
                if(ev.actionMasked==MotionEvent.ACTION_UP) unlockSwipeStart=null
            }
        }
        return super.dispatchTouchEvent(ev)
    }
}
@Composable private fun LockedHubScreen(vm: HubViewModel, host: HubHost) {
    if(host.showLockedDashboard) DockDashboard(vm,host) else PrivateLockedClock(vm,host)
}
@Composable private fun PrivateLockedClock(vm: HubViewModel, host: HubHost) {
    var clock by remember { mutableStateOf(LocalDateTime.now()) }
    LaunchedEffect(Unit) { while (true) { delay(1000); clock = LocalDateTime.now() } }
    BackHandler { host.systemLockScreen() }
    Surface(Modifier.fillMaxSize()) {
        Column(Modifier.safeDrawingPadding().verticalScroll(rememberScrollState()).padding(32.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Hearthline", style = MaterialTheme.typography.titleLarge)
                Icon(Icons.Outlined.Lock, "Device locked")
            }
            Text(clock.format(DateTimeFormatter.ofPattern("h:mm")), fontSize = 76.sp, fontWeight = FontWeight.Light)
            Text(clock.format(DateTimeFormatter.ofPattern("EEEE, MMMM d")), style = MaterialTheme.typography.headlineSmall)
            if(host.dockTestActive) {
                Text("Assumed dock · test", style = MaterialTheme.typography.labelMedium)
                OutlinedButton(onClick = host::endDockTest, modifier = Modifier.heightIn(min = 56.dp)) { Text("End dock test") }
            }
            Text(host.dock.status, style = MaterialTheme.typography.labelLarge)
            if (vm.playing && host.showLockedMusic) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                AlbumArt(vm, Modifier.size(128.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) { TrackText(vm); Text("Unlock to use music controls") }
            }
            if (vm.demo) Text("Demo mode · no audio playback", style = MaterialTheme.typography.labelLarge)
            Text("Your tablet is locked. Unlock to open home controls and settings.")
            Button(onClick = host::unlock, modifier = Modifier.heightIn(min = 56.dp)) { Text("Unlock controls") }
            TextButton(onClick = host::systemLockScreen) { Text("Dismiss · use Android lock screen") }
            host.notice?.let { Text(it) }
        }
    }
}
internal val LocalDockReadOnly = staticCompositionLocalOf { false }
@Composable private fun DockDashboard(vm: HubViewModel, host: HubHost, preview: Boolean = false) {
    var clock by remember { mutableStateOf(LocalDateTime.now()) }
    LaunchedEffect(Unit) { while(true) { delay(1000);clock=LocalDateTime.now() } }
    BackHandler { if(preview) host.previewDashboard(false) else host.systemLockScreen() }
    val status=buildList {
        if(preview) add("Preview")
        if(vm.demo) add("Design demo · sample devices · no audio playback")
        else if(!vm.connected) add("Disconnected · displayed values may be stale")
        if(host.dockTestActive) add("Assumed dock · test")
    }.joinToString(" · ").ifBlank { null }
    Surface(Modifier.fillMaxSize().semantics {
        customActions=listOf(CustomAccessibilityAction(if(preview) "Exit preview" else "Unlock tablet") {
            if(preview) host.previewDashboard(false) else host.unlock()
            true
        })
    }) {
        Box(Modifier.safeDrawingPadding()) {
            CompositionLocalProvider(LocalDockReadOnly provides true) {
                HomeOverview(vm,clock,showMusic=host.showLockedMusic,status=status)
            }
        }
    }
}

private val Sage = Color(0xFFDCE8C4)
private val Olive = Color(0xFF30372A)
@Composable private fun PixelTheme(dark: Boolean, black: Boolean = false, content: @Composable () -> Unit) {
    val colors = if (dark) darkColorScheme(primary = Sage, onPrimary = Olive,
        background = if(black) Color.Black else Color(0xFF191D17), surface = if(black) Color.Black else Color(0xFF191D17),
        surfaceVariant = Color(0xFF30372A), onSurface = Color(0xFFE9EDDF),
        primaryContainer = Color(0xFF3D4C2D), onPrimaryContainer = Color(0xFFE5EED5))
    else lightColorScheme(primary = Color(0xFF5C703D), onPrimary = Color.White,
        background = Color(0xFFF7F3E8), surface = Color(0xFFF7F3E8),
        surfaceVariant = Color(0xFFEDE9DC), onSurface = Olive, onSurfaceVariant = Color(0xFF535A4B),
        primaryContainer = Sage, onPrimaryContainer = Olive)
    MaterialTheme(colorScheme = colors, shapes = Shapes(medium = RoundedCornerShape(24.dp), large = RoundedCornerShape(28.dp)), content = content)
}
@Composable private fun PixelHome(vm: HubViewModel) {
    var clock by remember { mutableStateOf(LocalDateTime.now()) }
    LaunchedEffect(Unit) { while (true) { delay(1000); vm.tick(); clock = LocalDateTime.now() } }
    val nativeAnswer = vm.assistant.answer?.takeIf { it.template != null }
    if (nativeAnswer != null) { NativeTemplateScreen(vm, nativeAnswer); return }
    AssistantSheet(vm)
    BackHandler(vm.page != Page.HOME && vm.assistant.answer == null) { vm.open(Page.HOME) }
    Surface(Modifier.fillMaxSize()) {
        Column(Modifier.safeDrawingPadding()) {
            val host = LocalHubHost.current
            val showConnection = host.debuggingMode || vm.demo || !vm.connected
            if (showConnection || host.dockTestActive) Surface(color = if (vm.demo) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    if(LocalHubHost.current.dockTestActive) Text("Assumed dock · test",style=MaterialTheme.typography.labelSmall,modifier=Modifier.padding(end=16.dp))
                    if (showConnection) {
                        Text(if (vm.demo) "Design demo · sample devices · no audio playback" else if (vm.connected) "Home Assistant connected" else "Disconnected · displayed values may be stale", Modifier.weight(1f), style = MaterialTheme.typography.labelMedium)
                        TextButton(onClick = { vm.open(Page.SETTINGS) }) { Text("Connection") }
                    }
                }
            }
            if (host.debuggingMode) vm.message?.let { message ->
                Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(message, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                    TextButton(onClick = vm::clearMessage) { Text("Dismiss") }
                }
            }
            if (host.debuggingMode && vm.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            BoxWithConstraints(Modifier.weight(1f)) {
                val wide = maxWidth >= 840.dp
                Row(Modifier.fillMaxSize()) {
                    if (wide && vm.page != Page.IDLE) NavigationRail(containerColor = MaterialTheme.colorScheme.surface) {
                        Spacer(Modifier.height(20.dp))
                        listOf(Triple(Page.HOME, Icons.Outlined.Home, "Home"), Triple(Page.MUSIC, Icons.Outlined.MusicNote, "Music"), Triple(Page.SETTINGS, Icons.Outlined.Settings, "Settings")).forEach { (page, icon, label) ->
                            NavigationRailItem(vm.page == page, { vm.open(page) }, { Icon(icon, label) }, label = { Text(label) }, modifier = Modifier.padding(vertical = 8.dp))
                        }
                    }
                    Column(Modifier.weight(1f)) {
                        if (!wide && vm.page != Page.IDLE) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                            TextButton({ vm.open(Page.HOME) }) { Text("Home") }
                            TextButton({ vm.open(Page.MUSIC) }) { Text("Music") }
                            TextButton({ vm.open(Page.SETTINGS) }) { Text("Settings") }
                        }
                        AnimatedContent(targetState = vm.page, label = "Page transition", modifier = Modifier.weight(1f)) { page ->
                            if(page == Page.HOME) HomeOverview(vm,clock)
                            else Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(if (wide) 28.dp else 16.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                                when (page) {
                                    Page.MUSIC -> MusicScreen(vm, wide)
                                    Page.IDLE -> IdleScreen(vm, wide, clock)
                                    Page.SETTINGS -> SettingsScreen(vm)
                                    Page.HOME -> Unit
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
