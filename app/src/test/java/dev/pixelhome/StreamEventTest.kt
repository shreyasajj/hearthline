package dev.pixelhome

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class StreamEventTest {
    private val entry = "01M7M4MT4PMJDZPGZSR764J5GK"
    private fun config() = JSONObject("""{"schema_version":1,"revision":"0123456789abcdef","entry_id":"$entry","display_id":"01m7m4mt4pmjdzpgzsr764j5gk",
        "dashboard":{"schema_version":1,"title":"Kitchen","favorites":["light.test"],"scenes":[],"players":[],"home_entities":[],"weather_entity":null,"music":[],"idle_seconds":45},
        "capabilities":{"telemetry":1,"display_protocols":["1.0","1.1"],"remote_configuration":false}}""")
    private val light = """{"entity_id":"light.test","state":"on","attributes":{"friendly_name":"Test light"}}"""

    @Test fun snapshotCarriesValidatedConfigAndStates() {
        val text = JSONObject().put("type","snapshot").put("schema_version",1).put("config",config())
            .put("states",org.json.JSONArray().put(JSONObject(light))).toString()
        val event = StreamEvent.parse(text, entry) as StreamEvent.Snapshot
        assertEquals("Kitchen", event.config.getJSONObject("dashboard").getString("title"))
        assertEquals("on", event.states.getValue("light.test").state)
    }

    @Test fun incrementalMessages() {
        val state = StreamEvent.parse("""{"type":"state","state":$light}""", entry) as StreamEvent.State
        assertEquals("light.test", state.entity.id)
        assertEquals(StreamEvent.Removed("light.test"), StreamEvent.parse("""{"type":"removed","entity_id":"light.test"}""", entry))
        assertEquals(StreamEvent.Ping, StreamEvent.parse("""{"type":"ping"}""", entry))
    }

    @Test fun rejectsAnotherEntryUnknownTypesAndBadIds() {
        val wrongEntry = JSONObject().put("type","snapshot").put("schema_version",1).put("config",config().put("entry_id","OTHER"))
            .put("states",org.json.JSONArray()).toString()
        for(text in listOf(wrongEntry, """{"type":"command"}""", """{"type":"removed","entity_id":"../states"}""",
            """{"type":"state","state":{"entity_id":"Light.Test","state":"on","attributes":{}}}""", "not json"))
            assertThrows(Exception::class.java) { StreamEvent.parse(text, entry) }
    }
}
