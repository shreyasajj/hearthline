package dev.pixelhome

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class TabletDiscoveryProtocolTest {
    private val path=TabletDiscoveryProtocol.PATH
    private fun request(method: String="GET",target: String=path,extra: String="")="$method $target HTTP/1.1\r\nHost: 192.168.1.2:8787\r\n$extra\r\n"
    @Test fun acceptsOnlyBoundedReadOnlyIdentification() {
        assertTrue(TabletDiscoveryProtocol.validRequest(request()))
        assertFalse(TabletDiscoveryProtocol.validRequest(request("POST")))
        assertFalse(TabletDiscoveryProtocol.validRequest(request(target="/commands")))
        assertFalse(TabletDiscoveryProtocol.validRequest(request(target="$path?token=secret")))
        assertFalse(TabletDiscoveryProtocol.validRequest(request(extra="Content-Length: 1\r\n")))
        assertFalse(TabletDiscoveryProtocol.validRequest(request(extra="Transfer-Encoding: chunked\r\n")))
        assertFalse(TabletDiscoveryProtocol.validRequest(request(extra="X-Large: ${"x".repeat(2048)}\r\n")))
        assertFalse(TabletDiscoveryProtocol.validRequest("GET $path HTTP/1.1\r\n"))
    }
    @Test fun advertisesNoCredentialsOrDeviceState() {
        val data=JSONObject(TabletDiscoveryProtocol.metadata("10000000-0000-4000-8000-000000000001","0.7.3"))
        assertEquals(setOf("schema_version","tablet_id","app_version","name"),data.keys().asSequence().toSet())
        assertEquals("Hearthline tablet",data.getString("name"))
        assertEquals(1,data.getInt("schema_version"))
    }
    @Test fun invalidIdentityAndVersionRejected() {
        assertThrows(IllegalArgumentException::class.java) { TabletDiscoveryProtocol.metadata("arbitrary-device-name","0.7.3") }
        assertThrows(IllegalArgumentException::class.java) { TabletDiscoveryProtocol.metadata("10000000-0000-4000-8000-000000000001","x\r\nsecret") }
    }
}
