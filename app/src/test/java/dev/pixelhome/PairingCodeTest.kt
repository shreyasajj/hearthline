package dev.pixelhome

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class PairingCodeTest {
    @Test fun incompletePairingExplainsWhatIsMissing() {
        val draft = HaConnectionDraft()
        draft.updateCode("12345678")
        assertEquals("Enter your HTTPS Home Assistant server address above.", draft.pairingInputError())
        draft.url = "https://ha.example.com"
        draft.updateCode("123")
        assertEquals("Enter all 8 digits of the pairing code from Home Assistant.", draft.pairingInputError())
        draft.updateCode("1234-5678")
        assertNull(draft.pairingInputError())
        draft.url = "http://ha.example.com"
        assertTrue(draft.pairingInputError()!!.contains("HTTPS"))
    }

    @Test fun codeEntryAndRequestAcceptWithOrWithoutHyphen() {
        val draft = HaConnectionDraft()
        for (value in listOf("12345678", "1234-5678", "0123-4567")) {
            draft.updateCode(value)
            assertTrue(draft.codeComplete)
            assertEquals(value.replace("-", ""), draft.code)
            assertEquals(draft.code, normalizePairingCode(value))
        }
    }

    @Test fun codeRequestRejectsMalformedValues() {
        for (value in listOf("1234", "1234-56789", "1234-abcd", "1234 5678", "１２３４５６７８")) {
            assertThrows(IllegalArgumentException::class.java) { normalizePairingCode(value) }
        }
    }

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
