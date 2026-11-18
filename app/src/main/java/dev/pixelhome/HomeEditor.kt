package dev.pixelhome

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import org.json.JSONArray
import org.json.JSONObject

internal val homeSelectionLimits = mapOf("favorites" to 8, "scenes" to 6, "players" to 8)
internal fun eligibleHomeEntity(group: String, id: String) = id.substringBefore('.') in when(group) {
    "favorites" -> setOf("light", "switch", "climate", "lock")
    "scenes" -> setOf("scene")
    "players" -> setOf("media_player")
    else -> emptySet()
}

data class HomeEditDraft(val revision: String, val title: String, val favorites: List<String>, val scenes: List<String>, val players: List<String>) {
    fun selected(group: String) = when(group) { "favorites" -> favorites; "scenes" -> scenes; "players" -> players; else -> error("Unknown selection") }
    fun choose(group: String, id: String, checked: Boolean): HomeEditDraft {
        require(eligibleHomeEntity(group,id))
        val next = if(checked) (selected(group) + id).distinct() else selected(group) - id
        require(next.size <= homeSelectionLimits.getValue(group)) { "Choose up to ${homeSelectionLimits.getValue(group)} $group." }
        return when(group) { "favorites" -> copy(favorites=next); "scenes" -> copy(scenes=next); else -> copy(players=next) }
    }
    fun move(group: String, id: String, direction: Int): HomeEditDraft {
        val list=selected(group).toMutableList(); val index=list.indexOf(id); val target=index+direction
        if(index !in list.indices || target !in list.indices) return this
        java.util.Collections.swap(list,index,target)
        return when(group) { "favorites" -> copy(favorites=list); "scenes" -> copy(scenes=list); else -> copy(players=list) }
    }
    fun payload(): JSONObject {
        require(title.isNotBlank() && title.length<=80)
        return JSONObject().put("schema_version",1).put("revision",revision).put("title",title.trim())
            .put("favorites",JSONArray(favorites)).put("scenes",JSONArray(scenes)).put("players",JSONArray(players))
    }
    companion object {
        fun from(config: JSONObject): HomeEditDraft {
            val parsed=parseConfig(config.getJSONObject("dashboard"))
            return HomeEditDraft(config.getString("revision"),parsed.title,parsed.favorites,parsed.scenes,parsed.players)
        }
    }
}
data class HomeChoice(val id: String, val name: String, val state: String)
internal fun parseHomeChoices(response: JSONObject): List<HomeChoice> {
    require(response.getInt("schema_version")==1)
    val rows=response.getJSONArray("choices"); require(rows.length()<=200)
    return (0 until rows.length()).map { rows.getJSONObject(it) }.map {
        val id=it.getString("entity_id")
        require(id.matches(Regex("[a-z_]+\\.[a-z0-9_]+")) && homeSelectionLimits.keys.any { group -> eligibleHomeEntity(group,id) })
        HomeChoice(id,it.getString("name").take(160),it.getString("state").take(80))
    }.also { require(it.map { row -> row.id }.distinct().size==it.size) }
}

@Composable internal fun HomeEditorScreen(vm: HubViewModel) {
    if(!vm.editorOpen) return
    DisposableEffect(vm) { onDispose { vm.closeHomeEditor() } }
    Dialog(onDismissRequest=vm::closeHomeEditor,properties=DialogProperties(usePlatformDefaultWidth=false)) {
        Surface(Modifier.fillMaxSize()) {
            LazyColumn(Modifier.fillMaxSize().padding(24.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                item {
                    Row(verticalAlignment=Alignment.CenterVertically) {
                        Text("Edit Home",style=MaterialTheme.typography.headlineMedium,modifier=Modifier.weight(1f))
                        TextButton(onClick=vm::closeHomeEditor,enabled=!vm.editorSaving) { Text("Cancel") }
                        Button(onClick=vm::saveHomeEditor,enabled=vm.editorDraft?.title?.isNotBlank()==true && !vm.editorLoading && !vm.editorSaving && vm.connected) { Text(if(vm.editorSaving) "Saving…" else "Save Home") }
                    }
                    Text("Choose lights, switches, thermostats, locks, scenes and media players from Home Assistant. Changes are saved to this tablet’s dashboard in Home Assistant.")
                    if(vm.editorLoading || vm.editorSaving) LinearProgressIndicator(Modifier.fillMaxWidth())
                    vm.editorError?.let { Text(it,color=MaterialTheme.colorScheme.error) }
                    TextButton(onClick={ vm.loadHomeEditor(reset=true) },enabled=!vm.editorLoading && !vm.editorSaving) { Text("Reload saved layout") }
                }
                vm.editorDraft?.let { draft ->
                    item { OutlinedTextField(draft.title,{vm.updateHomeTitle(it)},label={Text("Home title")},singleLine=true,enabled=!vm.editorSaving,modifier=Modifier.fillMaxWidth()) }
                    item {
                        Row(horizontalArrangement=Arrangement.spacedBy(12.dp),verticalAlignment=Alignment.CenterVertically) {
                            OutlinedTextField(vm.editorQuery,vm::updateHomeQuery,label={Text("Search Home Assistant")},singleLine=true,modifier=Modifier.weight(1f),enabled=!vm.editorSaving)
                            Button(onClick={ vm.loadHomeEditor() },enabled=!vm.editorLoading && !vm.editorSaving) { Text("Search") }
                        }
                        if(vm.editorTruncated) Text("Showing the first 200 matches. Search by name or entity ID to find more.")
                    }
                    homeSelectionLimits.forEach { (group,limit) ->
                        item { Text("${group.replaceFirstChar { it.uppercase() }} · ${draft.selected(group).size}/$limit",style=MaterialTheme.typography.titleLarge) }
                        if(group=="players") item { Text("These players are available in the player picker. Choose ‘Sendspin only’ there to leave the fallback player unselected.") }
                        items(draft.selected(group),key={"selected:$group:$it"}) { id ->
                            Row(verticalAlignment=Alignment.CenterVertically) {
                                Text(vm.editorChoices.find { it.id==id }?.name ?: vm.entities[id]?.name ?: id,Modifier.weight(1f))
                                TextButton(onClick={vm.moveHomeChoice(group,id,-1)},enabled=!vm.editorSaving && draft.selected(group).firstOrNull()!=id) { Text("Up") }
                                TextButton(onClick={vm.moveHomeChoice(group,id,1)},enabled=!vm.editorSaving && draft.selected(group).lastOrNull()!=id) { Text("Down") }
                                TextButton(onClick={vm.chooseHomeEntity(group,id,false)},enabled=!vm.editorSaving) { Text("Remove") }
                            }
                        }
                        items(vm.editorChoices.filter { eligibleHomeEntity(group,it.id) && it.id !in draft.selected(group) },key={"choice:$group:${it.id}"}) { choice ->
                            Row(verticalAlignment=Alignment.CenterVertically) {
                                Checkbox(false,{vm.chooseHomeEntity(group,choice.id,it)},enabled=!vm.editorSaving && draft.selected(group).size<limit)
                                Column { Text(choice.name); Text("${choice.id} · ${choice.state}",style=MaterialTheme.typography.bodySmall) }
                            }
                        }
                        if(vm.editorChoices.none { eligibleHomeEntity(group,it.id) }) item { Text("No matching $group. Try a different search.") }
                    }
                }
            }
        }
    }
}
