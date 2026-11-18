package dev.pixelhome

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class HomeEditorTest {
    private fun draft()=HomeEditDraft("0123456789abcdef","Kitchen",listOf("light.a","switch.b"),emptyList(),listOf("media_player.room"))
    @Test fun selectionsKeepOrderCanBeRemovedAndRespectLimits() {
        val moved=draft().move("favorites","switch.b",-1)
        assertEquals(listOf("switch.b","light.a"),moved.favorites)
        assertEquals(listOf("light.a"),moved.choose("favorites","switch.b",false).favorites)
        var full=draft()
        for(i in 1..6) full=full.choose("favorites","light.extra_$i",true)
        val atLimit=full
        assertThrows(IllegalArgumentException::class.java) { atLimit.choose("favorites","light.extra_7",true) }
        assertThrows(IllegalArgumentException::class.java) { draft().choose("favorites","sensor.temperature",true) }
        assertThrows(IllegalArgumentException::class.java) { draft().choose("players","light.a",true) }
    }
    @Test fun savedBodyIsBoundedAndKeepsTheOptimisticRevision() {
        val body=draft().choose("players","media_player.room",false).payload()
        assertEquals(setOf("schema_version","revision","title","favorites","scenes","players"),body.keys().asSequence().toSet())
        assertEquals("0123456789abcdef",body.getString("revision"))
        assertEquals(0,body.getJSONArray("players").length())
        assertThrows(IllegalArgumentException::class.java) { draft().copy(title=" ").payload() }
    }
    @Test fun catalogueNeverAcceptsUnsupportedOrDuplicateEntities() {
        fun result(ids: List<String>)=JSONObject().put("schema_version",1).put("choices",JSONArray(ids.map { JSONObject().put("entity_id",it).put("name","Device").put("state","unavailable") }))
        assertEquals("unavailable",parseHomeChoices(result(listOf("light.kitchen"))).single().state)
        assertThrows(IllegalArgumentException::class.java) { parseHomeChoices(result(listOf("sensor.private"))) }
        assertThrows(IllegalArgumentException::class.java) { parseHomeChoices(result(listOf("light.a","light.a"))) }
        assertThrows(IllegalArgumentException::class.java) { parseHomeChoices(result((1..201).map { "light.a_$it" })) }
    }
    @Test fun activeSendspinWinsAndEmptySelectionNeverFallsBackToHa() {
        assertEquals(HomePlayerSource.SENDSPIN,homePlayerSource(true,"media_player.room"))
        assertEquals(HomePlayerSource.HOME_ASSISTANT,homePlayerSource(false,"media_player.room"))
        assertEquals(HomePlayerSource.SENDSPIN,homePlayerSource(false,""))
        assertEquals(HomePlayerSource.SENDSPIN,homePlayerSource(true,""))
    }
}
