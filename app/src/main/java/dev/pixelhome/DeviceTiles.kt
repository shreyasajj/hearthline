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

/** Device and scene tiles shared by Home, the dock overview and the details sheet. */
@Composable internal fun Title(text: String, subtitle: String? = null) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(text, fontSize = 36.sp, fontWeight = FontWeight.Medium)
        subtitle?.let { Text(it, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}
@Composable internal fun Tile(modifier: Modifier = Modifier, active: Boolean = false, content: @Composable ColumnScope.() -> Unit) {
    Surface(modifier, shape = RoundedCornerShape(28.dp), color = if (active) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant) {
        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
    }
}
@Composable internal fun DeviceGrid(vm: HubViewModel, wide: Boolean) {
    vm.config.favorites.chunked(if (wide) 2 else 1).forEach { pair ->
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            pair.forEach { id -> DeviceTile(vm, id, Modifier.weight(1f)) }
            if (wide && pair.size == 1) Spacer(Modifier.weight(1f))
        }
    }
}
@Composable internal fun DeviceTile(vm: HubViewModel, id: String, modifier: Modifier) {
    val entity = vm.entities[id]
    val domain = id.substringBefore('.')
    val enabled = !LocalDockReadOnly.current && vm.canControl && entity?.available == true
    var confirm by remember { mutableStateOf(false) }
    val icon = when (domain) { "light" -> Icons.Outlined.Lightbulb; "climate" -> Icons.Outlined.Thermostat; "lock" -> Icons.Outlined.Lock; else -> Icons.Outlined.PowerSettingsNew }
    Tile(modifier, entity?.state == "on") {
        Icon(icon, null, Modifier.size(28.dp))
        Text(entity?.name ?: id, style = MaterialTheme.typography.titleLarge)
        Text(if (entity?.available != true) "Unavailable" else entity.state.replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.bodyMedium)
        when (domain) {
            "light", "switch", "fan" -> {
                Switch(checked = entity?.state == "on", onCheckedChange = { vm.command(id, if (it) "turn_on" else "turn_off") }, enabled = enabled)
                if (domain == "light" && entity?.attrs?.has("brightness") == true) ValueSlider("Brightness", entity.attrs.optDouble("brightness", 0.0).toFloat() / 255f, enabled) {
                    vm.command(id, "turn_on", JSONObject().put("brightness_pct", (it * 100).toInt()))
                }
            }
            "climate" -> {
                val target = entity?.attrs?.optDouble("temperature", Double.NaN) ?: Double.NaN
                Text(if (target.isFinite()) "${target.toInt()}°" else "—", fontSize = 34.sp)
                val current = entity?.attrs?.optDouble("current_temperature", Double.NaN) ?: Double.NaN
                if (current.isFinite()) Text("Inside ${current.toInt()}°")
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    listOf(-1, 1).forEach { direction ->
                        OutlinedButton(onClick = {
                            val step = entity?.attrs?.optDouble("target_temp_step", 1.0) ?: 1.0
                            val min = entity?.attrs?.optDouble("min_temp", 7.0) ?: 7.0
                            val max = entity?.attrs?.optDouble("max_temp", 35.0) ?: 35.0
                            vm.command(id, "set_temperature", JSONObject().put("temperature", (target + direction * step).coerceIn(min, max)))
                        }, enabled = enabled && target.isFinite() && entity?.supports(1) == true) { Text(if (direction < 0) "−" else "+", fontSize = 24.sp) }
                    }
                }
            }
            "lock" -> OutlinedButton(onClick = { if (entity?.state == "locked") confirm = true else vm.command(id, "lock") }, enabled = enabled) { Text(if (entity?.state == "locked") "Unlock…" else "Lock") }
            else -> Text("Read-only in this version", style = MaterialTheme.typography.labelMedium)
        }
    }
    if (confirm) AlertDialog(onDismissRequest = { confirm = false }, title = { Text("Unlock ${entity?.name}?") }, text = { Text("This will unlock the physical door.") },
        confirmButton = { TextButton(onClick = { confirm = false; vm.command(id, "unlock") }, enabled = enabled) { Text("Unlock") } },
        dismissButton = { TextButton(onClick = { confirm = false }) { Text("Cancel") } })
}
@Composable internal fun Scenes(vm: HubViewModel) {
    if (vm.config.scenes.isNotEmpty()) {
        Text("Scenes", style = MaterialTheme.typography.titleLarge)
        vm.config.scenes.forEach { id ->
            OutlinedButton(onClick = { vm.command(id, "turn_on") }, enabled = !LocalDockReadOnly.current && vm.canControl && vm.entities[id] != null && vm.entities[id]?.state != "unavailable", modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                Icon(Icons.Outlined.AutoAwesome, null); Spacer(Modifier.width(12.dp)); Text(vm.entities[id]?.name ?: id)
            }
        }
    }
}
