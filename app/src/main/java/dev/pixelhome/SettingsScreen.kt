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

/** The Settings page: assistant display, dock/lock-screen and hub options. Cards with their own state live in their own files. */
@Composable internal fun SettingsScreen(vm: HubViewModel) {
    val host = LocalHubHost.current
    var displayId by remember { mutableStateOf(vm.assistant.displayId) }
    Title("Make it yours", "Home Assistant manages the dashboard content")
    AmbientSettingsCard(LocalAmbient.current)
    Tile(Modifier.widthIn(max = 800.dp).fillMaxWidth()) {
        Text("Assistant display", style = MaterialTheme.typography.titleLarge)
        Text(vm.assistant.status)
        OutlinedTextField(displayId, { displayId = it }, label = { Text("Display ID") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = { vm.assistant.configure(true, displayId) }) { Text("Enable display bridge") }
            OutlinedButton(onClick = { vm.assistant.configure(false, displayId) }) { Text("Disable") }
        }
        Text("Install the included pixel_home integration on your Home Assistant server first. Answers appear only while this app is visible and unlocked. Display ID, bridge enablement and credentials are session-only.")
        if(vm.demo) OutlinedButton(onClick = vm.assistant::preview) { Text("Preview an assistant answer") }
        if(vm.demo) {
            val context=androidx.compose.ui.platform.LocalContext.current
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                listOf("journey","weather","home_status").forEach { kind->
                    OutlinedButton(onClick={ vm.assistant.previewTemplate(context.assets.open("demo-$kind.json").bufferedReader().use { it.readText() }) }) { Text("Preview ${kind.replace('_',' ')}") }
                }
            }
        }
        var mapStyle by remember { mutableStateOf("") }
        var mapOrigins by remember { mutableStateOf("") }
        var mapAttribution by remember { mutableStateOf("") }
        Text("Journey map provider",style=MaterialTheme.typography.titleLarge)
        Text("Session-only. Supply your licensed HTTPS style, every tile/glyph/sprite origin and required attribution. No default public tile service is used.")
        OutlinedTextField(mapStyle,{mapStyle=it},label={Text("HTTPS style URL")},modifier=Modifier.fillMaxWidth())
        OutlinedTextField(mapOrigins,{mapOrigins=it},label={Text("Allowed HTTPS origins, comma separated")},modifier=Modifier.fillMaxWidth())
        OutlinedTextField(mapAttribution,{mapAttribution=it},label={Text("Map/data attribution")},modifier=Modifier.fillMaxWidth())
        OutlinedButton(onClick={vm.assistant.configureMap(mapStyle,mapOrigins,mapAttribution)}) { Text("Use map provider for this session") }

        Text("Voice listening, speech playback and follow-up buttons are not included in this build.", style = MaterialTheme.typography.bodySmall)
    }
    Tile(Modifier.widthIn(max = 800.dp).fillMaxWidth()) {
        Text("Startup & lock screen", style = MaterialTheme.typography.titleLarge)
        Text(if (host.isDefaultHome) "Hearthline is your default Home app" else "Default Home app: another launcher")
        Text("Choose Hearthline as Android's Home app to open this interface when Android returns Home, including after startup and the first unlock.")
        Button(onClick = host::makeHome, enabled = !host.isDefaultHome) { Text("Make Hearthline my Home app") }
        OutlinedButton(onClick = host::homeSettings) { Text("Change default Home app") }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Show over lock screen", Modifier.weight(1f))
            Switch(host.showWhenLocked, host::showOverLock)
        }
        Text(host.dock.status, style = MaterialTheme.typography.titleMedium)
        Text("Battery ${host.dock.batteryPercent?.let { "$it%" } ?: "unavailable"} · Power: ${host.dock.powerSource}")
        Text("Normal mode requires a confirmed dock connection; undocked or unconfirmed means no dock lock-screen view.")
        if(host.dockTestActive) {
            Text("Assumed dock · test (10 minutes)", style = MaterialTheme.typography.labelLarge)
            OutlinedButton(onClick = host::endDockTest, modifier = Modifier.heightIn(min = 56.dp)) { Text("End dock test") }
        } else {
            OutlinedButton(onClick = { host.assumeDockForTesting(); vm.open(Page.HOME); host.autoReturn(true); }, modifier = Modifier.heightIn(min = 56.dp)) { Text("Assume docked for testing") }
        }
        Text("Assume docked opens the main Home screen immediately. The test lasts 10 minutes or until the app process restarts; it can test return on lock once Android permissions are allowed. Actual dock readings are unchanged.")
        OutlinedButton(onClick = { host.previewDashboard(true) }, modifier = Modifier.heightIn(min=56.dp)) { Text("Preview locked dock dashboard") }
        Row(verticalAlignment=Alignment.CenterVertically) { Text("Show main dashboard while locked",Modifier.weight(1f)); Switch(host.showLockedDashboard,{host.lockedLayout(dashboard=it)}) }
        Text("The dock dashboard shows configured device and music information. Its controls stay disabled until Android is unlocked. Turn this off for the private clock-only view.")
        Row(verticalAlignment=Alignment.CenterVertically) { Text("Wake dock display immediately on lock",Modifier.weight(1f)); Switch(host.wakeScreenOnLock,{host.lockedLayout(wake=it)}) }
        Text("Dock detection", style = MaterialTheme.typography.titleMedium)
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            DockDetectionMode.entries.filter { it != DockDetectionMode.FORCE_DOCKED }.forEach { mode ->
                OutlinedButton(onClick = { host.detectionMode(mode) }, modifier = Modifier.heightIn(min = 56.dp)) {
                    Text("${if(host.dock.mode == mode) "✓ " else ""}${when(mode) { DockDetectionMode.AUTO -> "Automatic dock signals"; DockDetectionMode.FORCE_DOCKED -> "Assume docked (manual)"; DockDetectionMode.FORCE_UNDOCKED -> "Assume undocked (manual)" }}")
                }
            }
        }
        Text("Automatic mode uses Android dock/audio/power evidence. Ordinary AC, USB or wireless charging does not confirm a dock. A manual undocked setting can disable presentation. Automatic return requires real docking, except during the explicitly activated 10-minute local dock test.")
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Show dock view again on next lock", Modifier.weight(1f))
            Switch(host.rearmOnLock, { host.configureDock(rearm = it) })
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Keep dock view awake while locked", Modifier.weight(1f))
            Switch(host.keepDockAwake, { host.configureDock(awake = it) })
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Show music information while locked", Modifier.weight(1f))
            Switch(host.showLockedMusic, { host.configureDock(music = it) })
        }
        OutlinedButton(onClick = host::resumeDockDisplay, modifier = Modifier.heightIn(min = 56.dp)) { Text("Resume dismissed dock view") }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Automatically return on lock from other apps", Modifier.weight(1f))
            Switch(host.autoReturnOnLock, host::autoReturn)
        }
        Text(if(host.autoReturnOnLock && DockReturnService.running) "Monitoring locks · unlocked use is never interrupted" else "Automatic lock monitoring is off or not running")
        Text("For automatic return, Android requires Display over other apps and a visible monitoring notification. Enable this while unlocked. It only opens the protected view after locking and when a dock is confirmed or the temporary local test is active; dismissing does not reopen it during that lock cycle.")
        OutlinedButton(onClick = host::overlaySettings, modifier = Modifier.heightIn(min = 56.dp)) { Text(if(host.overlayPermission) "Review automatic-return permission" else "Grant automatic-return permission") }

        Text("Dismiss the protected view to use Android's lock screen, or choose Unlock controls to authenticate. When enabled, the next lock re-arms the view while docked. Without automatic return, this applies while Hearthline is the foreground/Home activity. With the optional permission and monitor enabled, it can return from other apps only after locking. Android authentication remains required.")
        Text("All dock display preferences persist. With keep-awake off, Power wakes the screen normally. No PIN, lock bypass, boot auto-launch or vendor cloud service is used.", style = MaterialTheme.typography.bodySmall)
    }
    SendspinSettingsCard(LocalSendspinReceiver.current)
    HomeAssistantConnectionCard(vm)
    Tile(Modifier.widthIn(max = 800.dp).fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) { Text("Hub mode", Modifier.weight(1f)); Switch(vm.hubEnabled, vm::hub) }
        Text("Keeps Hearthline awake while visible. Starts Music when the selected player begins playback; simplifies Music after ${vm.config.idleSeconds} seconds. This is independent of the dock-only lock-screen preference.")
        Row(verticalAlignment = Alignment.CenterVertically) { Text("Dark appearance", Modifier.weight(1f)); Switch(vm.dark, { vm.dark = it }) }
        Text("If Hearthline is your default Home app, the Home gesture returns here. Use Change default Home app above to restore your normal launcher. Dock presentation and automatic detection are configured above.")
    }
}
