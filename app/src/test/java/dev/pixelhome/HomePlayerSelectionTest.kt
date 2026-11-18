package dev.pixelhome

import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class HomePlayerSelectionTest {
    @Test fun emptyPlayerIsAnExplicitChoiceAndLockedSelectionsAreIgnored() {
        val vm=HubViewModel()
        var unlocked=true
        vm.attachAccessCheck { unlocked }
        val first=vm.config.players.first()
        vm.select(first)
        assertNotNull(vm.player)
        vm.select("")
        assertEquals("",vm.selectedPlayer)
        assertNull(vm.player)
        unlocked=false
        vm.select(first)
        assertEquals("",vm.selectedPlayer)
        vm.openHomeEditor()
        assertFalse(vm.editorOpen)
    }
}
