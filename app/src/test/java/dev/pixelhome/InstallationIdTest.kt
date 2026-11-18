package dev.pixelhome

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class InstallationIdTest {
    @get:Rule val folder = TemporaryFolder()
    private fun file() = java.io.File(folder.root, "hearthline_installation_v1")

    @Test fun createsOnceAndThenKeepsTheSameId() {
        val first = installationId(file(), paired = false)
        assertTrue(validInstallationId(first))
        assertEquals(first, installationId(file(), paired = false))
        assertFalse(java.io.File(folder.root, "hearthline_installation_v1.new").exists())
    }

    @Test fun corruptOrEmptyIdIsReplacedWhenUnpaired() {
        for(content in listOf("", "not-a-uuid", "10000000-0000-4000-8000-00000000000Z")) {
            file().writeText(content)
            val replaced = installationId(file(), paired = false)
            assertTrue(validInstallationId(replaced))
            assertEquals(replaced, file().readText())
        }
    }

    @Test fun corruptIdFailsClosedWhilePaired() {
        file().writeText("")
        assertThrows(IllegalStateException::class.java) { installationId(file(), paired = true) }
        assertEquals("", file().readText())
    }

    @Test fun trailingWhitespaceFromOlderWritesIsAccepted() {
        file().writeText("10000000-0000-4000-8000-000000000001\n")
        assertEquals("10000000-0000-4000-8000-000000000001", installationId(file(), paired = true))
    }
}
