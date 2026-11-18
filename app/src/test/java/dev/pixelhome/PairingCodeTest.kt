package dev.pixelhome

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class PairingCodeTest {
    @Test fun codeKeepsOnlyEightDigitsAndClearsWithToken() {
        val draft=HaConnectionDraft()
        draft.updateCode("1234-56 78 99"); assertEquals("12345678",draft.code); assertTrue(draft.codeComplete)
        draft.updateCode("123"); assertFalse(draft.codeComplete)
        draft.updateCode("12345678"); draft.clearToken(); assertEquals("",draft.code)
    }
    private fun result(entry: String="abc123", credential: String="a".repeat(64)) =
        JSONObject().put("schema_version",1).put("entry_id",entry).put("display_id","tablet_1").put("credential",credential)
    @Test fun codePairingAcceptsTheEntryHaChose() {
        assertEquals("abc123",validatePairing(result(),null).getString("entry_id"))
        assertThrows(IllegalArgumentException::class.java) { validatePairing(result(),"other") }
    }
    @Test fun malformedPairingResultsAreRejected() {
        for(bad in listOf(result(entry="../x"),result(credential="short"),result().put("extra",1),result().put("schema_version",2)))
            assertThrows(IllegalArgumentException::class.java) { validatePairing(bad,null) }
    }
    @Test fun codePairingNeedsHttpsAndAnEightDigitCode() {
        assertThrows(IllegalArgumentException::class.java) { HomeAssistant("http://192.168.30.3:8123","") }
        val ha=HomeAssistant("https://ha.example.com","")
        assertThrows(IllegalArgumentException::class.java) { kotlinx.coroutines.runBlocking { ha.pairWithCode("id","1234") } }
    }
}
