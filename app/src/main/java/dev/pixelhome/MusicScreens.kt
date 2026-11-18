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

/** Player controls, the Music page and the simplified idle player. */
@Composable internal fun AlbumArt(vm: HubViewModel, modifier: Modifier = Modifier) {
    val bitmap = vm.artwork
    Box(modifier.clip(RoundedCornerShape(24.dp)).background(MaterialTheme.colorScheme.surfaceVariant)) {
        if (bitmap != null) Image(bitmap.asImageBitmap(), "Current album artwork", Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        else Canvas(Modifier.fillMaxSize()) {
            drawRect(Color(0xFFF0E8CF)); drawCircle(Color(0xFFEBC986), size.width * .15f, Offset(size.width * .5f, size.height * .3f))
            listOf(.64f to Color(0xFFB9C7A2), .78f to Color(0xFF91A784), .9f to Color(0xFF6D8664)).forEach { (y, color) ->
                val path = Path().apply { moveTo(0f, size.height * y); cubicTo(size.width * .3f, size.height * (y - .25f), size.width * .6f, size.height * (y + .12f), size.width, size.height * (y - .18f)); lineTo(size.width, size.height); lineTo(0f, size.height); close() }
                drawPath(path, color)
            }
        }
    }
}
@Composable internal fun TrackText(vm: HubViewModel, large: Boolean = false) {
    Text(vm.player?.attrs?.optString("media_title")?.takeIf { it.isNotBlank() } ?: "Nothing playing", fontSize = if (large) 40.sp else 28.sp, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
    Text(vm.player?.attrs?.optString("media_artist")?.takeIf { it.isNotBlank() } ?: "Select music to begin", style = MaterialTheme.typography.titleMedium)
}
@Composable internal fun Transport(vm: HubViewModel, large: Boolean = false) {
    val p = vm.player
    val enabled = !LocalDockReadOnly.current && vm.canControl && p?.available == true
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { vm.command(vm.selectedPlayer, "media_previous_track") }, enabled = enabled && p?.supports(16) == true, modifier = Modifier.size(if (large) 80.dp else 56.dp)) { Icon(Icons.Outlined.SkipPrevious, "Previous track", Modifier.size(if (large) 48.dp else 32.dp)) }
        Spacer(Modifier.width(if (large) 40.dp else 12.dp))
        FilledIconButton(onClick = { vm.command(vm.selectedPlayer, if (vm.playing) "media_pause" else "media_play") }, enabled = enabled && p?.supports(if (vm.playing) 1 else 16384) == true, modifier = Modifier.size(if (large) 112.dp else 76.dp)) {
            Icon(if (vm.playing) Icons.Outlined.Pause else Icons.Outlined.PlayArrow, if (vm.playing) "Pause" else "Play", Modifier.size(if (large) 64.dp else 40.dp))
        }
        Spacer(Modifier.width(if (large) 40.dp else 12.dp))
        IconButton(onClick = { vm.command(vm.selectedPlayer, "media_next_track") }, enabled = enabled && p?.supports(32) == true, modifier = Modifier.size(if (large) 80.dp else 56.dp)) { Icon(Icons.Outlined.SkipNext, "Next track", Modifier.size(if (large) 48.dp else 32.dp)) }
    }
}
@Composable internal fun ValueSlider(label: String, value: Float, enabled: Boolean, onCommit: (Float) -> Unit) {
    val safeValue = if (value.isFinite()) value.coerceIn(0f, 1f) else 0f
    var draft by remember { mutableFloatStateOf(safeValue) }
    var dragging by remember { mutableStateOf(false) }
    LaunchedEffect(safeValue, dragging) { if (!dragging) draft = safeValue }
    Column {
        Text("$label · ${(draft * 100).toInt()}%", style = MaterialTheme.typography.labelLarge)
        Slider(value = draft, onValueChange = { dragging = true; draft = it }, onValueChangeFinished = { onCommit(draft); dragging = false }, enabled = enabled, modifier = Modifier.fillMaxWidth().height(56.dp).semantics { contentDescription = label })
    }
}
@Composable internal fun Volume(vm: HubViewModel) {
    ValueSlider("Volume", vm.player?.attrs?.optDouble("volume_level", 0.0)?.toFloat() ?: 0f, !LocalDockReadOnly.current && vm.canControl && vm.player?.available == true && vm.player?.supports(4) == true) {
        vm.command(vm.selectedPlayer, "volume_set", JSONObject().put("volume_level", it.toDouble()))
    }
}
@Composable internal fun SpeakerPicker(vm: HubViewModel) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { expanded = true }, enabled = !LocalDockReadOnly.current, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
            Icon(Icons.Outlined.Speaker, null); Spacer(Modifier.width(10.dp)); Text(vm.player?.name ?: "Select speaker", Modifier.weight(1f),maxLines=1,overflow=TextOverflow.Ellipsis); Icon(Icons.Outlined.ExpandMore, null)
        }
        DropdownMenu(expanded, onDismissRequest = { expanded = false }) {
            vm.config.players.forEach { id -> DropdownMenuItem(text = { Text(vm.entities[id]?.name ?: id) }, onClick = { vm.select(id); expanded = false }) }
        }
    }
}
@Composable internal fun PlayerCard(vm: HubViewModel, modifier: Modifier = Modifier) {
    Tile(modifier.fillMaxWidth(), true) {
        Text("NOW PLAYING", style = MaterialTheme.typography.labelLarge)
        AlbumArt(vm, Modifier.size(144.dp).clickable(enabled = !LocalDockReadOnly.current, onClickLabel = "Open music") { vm.open(Page.MUSIC) })
        TrackText(vm)
        Text(vm.player?.state ?: "No player configured", style = MaterialTheme.typography.labelLarge)
        Transport(vm); SpeakerPicker(vm); Volume(vm)
        TextButton(onClick = { vm.open(Page.MUSIC) }, enabled = !LocalDockReadOnly.current) { Text("Open music") }
    }
}
@Composable internal fun MusicScreen(vm: HubViewModel, wide: Boolean) {
    Title("Music", "Music Assistant · ${if (vm.demo) "demo collection" else "Home Assistant player controls"}")
    val receiver = LocalSendspinReceiver.current
    if(receiver.enabled) Text("Tablet receiver: ${receiver.status} · ${receiver.output}", style = MaterialTheme.typography.bodyLarge)
    val browse: @Composable () -> Unit = {
        Text("Your collection", style = MaterialTheme.typography.headlineSmall)
        Text("Music shortcuts configured in Home Assistant", color = MaterialTheme.colorScheme.onSurfaceVariant)
        vm.config.shortcuts.forEach { shortcut ->
            Tile(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.LibraryMusic, null, Modifier.size(32.dp)); Spacer(Modifier.width(16.dp))
                    Text(shortcut.title, Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
                    IconButton(onClick = { vm.playShortcut(shortcut) }, enabled = vm.canControl && vm.player?.available == true && vm.player?.supports(512) == true) { Icon(Icons.Outlined.PlayArrow, "Play ${shortcut.title}") }
                }
            }
        }
        if (vm.config.shortcuts.isEmpty()) Text("No music shortcuts configured yet.")
        Text("Full library search and queue editing are planned; this version plays configured music shortcuts.", style = MaterialTheme.typography.bodySmall)
        OutlinedButton(onClick = { vm.open(Page.IDLE) }) { Text("Simple player") }
        TextButton(onClick = { vm.open(Page.HOME) }) { Text("Back to Home") }
    }
    if (wide) Row(horizontalArrangement = Arrangement.spacedBy(28.dp)) {
        Column(Modifier.weight(1.2f), verticalArrangement = Arrangement.spacedBy(16.dp)) { browse() }
        PlayerCard(vm, Modifier.weight(1f))
    } else { PlayerCard(vm); browse() }
}
@Composable internal fun IdleScreen(vm: HubViewModel, wide: Boolean, clock: LocalDateTime) {
    if (wide) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(28.dp), verticalAlignment = Alignment.CenterVertically) {
        AlbumArt(vm, Modifier.size(210.dp).clickable(onClickLabel = "Open music") { vm.open(Page.MUSIC) })
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) { TrackText(vm, true); Text(vm.player?.name.orEmpty(), style = MaterialTheme.typography.titleLarge) }
        Text(clock.format(DateTimeFormatter.ofPattern("h:mm")), fontSize = 48.sp)
    } else { AlbumArt(vm, Modifier.size(160.dp).clickable(onClickLabel = "Open music") { vm.open(Page.MUSIC) }); TrackText(vm) }
    Spacer(Modifier.height(24.dp))
    Transport(vm, true)
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { Column(Modifier.widthIn(max = 560.dp).fillMaxWidth()) { Volume(vm) } }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        FilledTonalButton(onClick = { vm.open(Page.HOME) }, modifier = Modifier.heightIn(min = 64.dp)) { Icon(Icons.Outlined.Home, null); Spacer(Modifier.width(12.dp)); Text("Home", fontSize = 22.sp) }
    }
}
